package org.domi.furnace.compat.nms;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

/**
 * shim 에 없는 NMS·CraftBukkit 심볼을 가리키는 명령을 그 자리의 {@link NmsTrap#call} 로 바꾼다(설계 H5 5.1, H7a).
 *
 * <p>바꾸는 법: 원래 명령이 스택에서 꺼낼 값(인자·받는 객체)을 pop 으로 비우고, 덫을 부른 뒤, 원래 명령이 남길 값 자리에 기본값
 * (null·0)을 넣는다. 그래서 그 지점 뒤의 스택 모양이 원래와 같고 스택 맵 프레임을 다시 계산할 필요가 없다(덫이 늘 던지므로 기본값은
 * 실행되지 않는다). 없는 생성자({@code new X; dup; …; invokespecial X.<init>})는 {@code new} 를 {@code aconst_null} 로 바꾸고
 * 그 미초기화 값을 가리키던 프레임 칸을 null 로 고친다.
 *
 * <p>클래스 정의를 막는 경우(설계 5.4): 상위 클래스가 {@code net.minecraft.*} 이거나(shim 은 상속을 지원하지 않는다 — 등급 C),
 * 없는 CraftBukkit 클래스·없는 NMS 인터페이스를 상속하면 정의할 때 {@link UnsupportedNmsCall} 을 던진다.
 */
final class NmsLinker extends ClassVisitor {

    private final NmsSymbols symbols;
    private final String plugin;
    private final Set<String> own;
    private final boolean[] changed;
    private final int changedIndex;
    private String self;

    /**
     * @param own     이 플러그인 jar 에 들어 있는 클래스(내부 이름) — NMS 이름이어도 플러그인 것이므로 판정하지 않는다
     * @param changed 바꾼 것이 있으면 {@code changed[changedIndex] = true}
     */
    NmsLinker(ClassVisitor next, NmsSymbols symbols, String plugin, Set<String> own, boolean[] changed, int changedIndex) {
        super(Opcodes.ASM9, next);
        this.symbols = symbols;
        this.plugin = plugin;
        this.own = own;
        this.changed = changed;
        this.changedIndex = changedIndex;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        self = name;
        if (superName != null && !own.contains(superName) && NmsSymbols.isNms(superName)
                && (superName.startsWith("net/minecraft/") || !symbols.hasClass(superName))) {
            throw refusal(name, superName, superName.startsWith("net/minecraft/") && symbols.hasClass(superName)
                    ? "등급 C — NMS 클래스 상속은 지원하지 않는다(서버가 그 가상 메서드를 부르지 않는다). 대체: Furnace 엔티티·몹 AI·메뉴 API"
                    : NmsGrades.reason(superName));
        }
        if (interfaces != null) {
            for (String itf : interfaces) {
                if (!own.contains(itf) && NmsSymbols.isNms(itf) && !symbols.hasClass(itf)) throw refusal(name, itf, NmsGrades.reason(itf));
            }
        }
        super.visit(version, access, name, signature, superName, interfaces);
    }

    private UnsupportedNmsCall refusal(String cls, String parent, String why) {
        String target = "상속 " + dots(cls) + " → " + dots(parent);
        org.slf4j.LoggerFactory.getLogger("Furnace").warn("플러그인 {}: 클래스 {} 를 정의하지 않는다 — {} 를 상속한다: {}", plugin, dots(cls), dots(parent), why);
        return new UnsupportedNmsCall(target, why + " (플러그인=" + plugin + ")");
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor next = super.visitMethod(access, name, descriptor, signature, exceptions);
        if (next == null) return null;
        return new MethodNode(Opcodes.ASM9, access, name, descriptor, signature, exceptions) {
            @Override
            public void visitEnd() {
                if (link(this)) changed[changedIndex] = true;
                accept(next);
            }
        };
    }

    // ------------------------------------------------------------------ 한 메서드

    /** 바꾼 것이 있으면 true. */
    private boolean link(MethodNode mn) {
        InsnList code = mn.instructions;
        if (code.size() == 0) return false;
        boolean any = false;
        int line = -1;
        Deque<TypeInsnNode> pendingNew = new ArrayDeque<>();
        Map<TypeInsnNode, Boolean> nullNews = new IdentityHashMap<>();   // aconst_null 로 바꿀 new (값: 덫을 거기서 부르는가)
        List<Runnable> edits = new ArrayList<>();
        for (AbstractInsnNode insn = code.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof LineNumberNode ln) {
                line = ln.line;
                continue;
            }
            String site = dots(self) + "#" + mn.name + (line >= 0 ? ":" + line : "");
            final AbstractInsnNode at = insn;
            switch (insn) {
                case TypeInsnNode t when t.getOpcode() == Opcodes.NEW -> {
                    pendingNew.push(t);
                    if (missingClass(t.desc)) nullNews.put(t, Boolean.TRUE);
                }
                case TypeInsnNode t -> {
                    if (!missingClass(t.desc)) break;
                    String target = dots(NmsSymbols.element(t.desc));
                    String why = NmsGrades.reason(t.desc);
                    Type pushes = t.getOpcode() == Opcodes.INSTANCEOF ? Type.INT_TYPE : Type.getObjectType("java/lang/Object");
                    edits.add(() -> replace(code, at, popsFor(t.getOpcode()), site, target, why, pushes));
                    any = true;
                }
                case MethodInsnNode m -> {
                    TypeInsnNode matched = null;
                    if (m.getOpcode() == Opcodes.INVOKESPECIAL && m.name.equals("<init>")) {
                        for (TypeInsnNode n : pendingNew) {
                            if (n.desc.equals(m.owner)) { matched = n; break; }
                        }
                        if (matched != null) pendingNew.removeFirstOccurrence(matched);
                    }
                    String target = missingMethod(m.owner, m.name, m.desc);
                    if (target == null) break;
                    if (m.getOpcode() == Opcodes.INVOKESPECIAL && m.name.equals("<init>")) {
                        if (matched == null) break;   // 상위 생성자 호출 — NMS 상속은 visit 에서 이미 막았다
                        nullNews.putIfAbsent(matched, Boolean.FALSE);
                    }
                    List<Type> pops = new ArrayList<>(List.of(Type.getArgumentTypes(m.desc)));
                    if (m.getOpcode() != Opcodes.INVOKESTATIC) pops.addFirst(Type.getObjectType("java/lang/Object"));
                    Type ret = m.name.equals("<init>") ? Type.VOID_TYPE : Type.getReturnType(m.desc);
                    String why = NmsGrades.reason(m.owner);
                    edits.add(() -> replace(code, at, pops, site, target, why, ret));
                    any = true;
                }
                case FieldInsnNode f -> {
                    String target = missingField(f.owner, f.name, f.desc);
                    if (target == null) break;
                    Type ft = Type.getType(f.desc);
                    List<Type> pops = new ArrayList<>();
                    Type ret = Type.VOID_TYPE;
                    switch (f.getOpcode()) {
                        case Opcodes.GETFIELD -> { pops.add(Type.getObjectType("java/lang/Object")); ret = ft; }
                        case Opcodes.PUTFIELD -> { pops.add(Type.getObjectType("java/lang/Object")); pops.add(ft); }
                        case Opcodes.GETSTATIC -> ret = ft;
                        default -> pops.add(ft);
                    }
                    String why = NmsGrades.reason(f.owner);
                    Type r = ret;
                    edits.add(() -> replace(code, at, pops, site, target, why, r));
                    any = true;
                }
                case LdcInsnNode l when l.cst instanceof Type ty && (ty.getSort() == Type.OBJECT || ty.getSort() == Type.ARRAY) -> {
                    if (!missingClass(ty.getInternalName())) break;
                    String target = dots(NmsSymbols.element(ty.getInternalName()));
                    String why = NmsGrades.reason(ty.getInternalName());
                    edits.add(() -> replace(code, at, List.of(), site, target, why, Type.getObjectType("java/lang/Class")));
                    any = true;
                }
                case MultiANewArrayInsnNode a -> {
                    if (!missingClass(a.desc)) break;
                    List<Type> pops = new ArrayList<>();
                    for (int i = 0; i < a.dims; i++) pops.add(Type.INT_TYPE);
                    String target = dots(NmsSymbols.element(a.desc));
                    String why = NmsGrades.reason(a.desc);
                    edits.add(() -> replace(code, at, pops, site, target, why, Type.getObjectType("java/lang/Object")));
                    any = true;
                }
                case InvokeDynamicInsnNode d -> {
                    String target = null;
                    String why = null;
                    for (Object arg : d.bsmArgs) {
                        if (!(arg instanceof Handle h)) continue;
                        String t = h.getTag() <= Opcodes.H_PUTSTATIC ? missingField(h.getOwner(), h.getName(), h.getDesc())
                                : missingMethod(h.getOwner(), h.getName(), h.getDesc());
                        if (t != null) {
                            target = t;
                            why = NmsGrades.reason(h.getOwner());
                            break;
                        }
                    }
                    if (target == null) break;
                    List<Type> pops = List.of(Type.getArgumentTypes(d.desc));
                    Type ret = Type.getReturnType(d.desc);
                    String tg = target, w = why;
                    edits.add(() -> replace(code, at, pops, site, tg, w, ret));
                    any = true;
                }
                default -> { }
            }
        }
        if (!any) return false;
        // new 바꾸기는 다른 편집보다 먼저(같은 목록을 고친다 — 순서와 상관없이 노드 단위라 안전)
        Set<LabelNode> nulled = new HashSet<>();
        String siteOfNew = dots(self) + "#" + mn.name;
        for (Map.Entry<TypeInsnNode, Boolean> e : nullNews.entrySet()) {
            TypeInsnNode n = e.getKey();
            for (AbstractInsnNode p = n.getPrevious(); p != null && p.getOpcode() < 0; p = p.getPrevious()) {
                if (p instanceof LabelNode l) nulled.add(l);
            }
            InsnList rep = new InsnList();
            if (e.getValue()) emitTrap(rep, siteOfNew + lineOf(n), dots(n.desc), NmsGrades.reason(n.desc));
            rep.add(new InsnNode(Opcodes.ACONST_NULL));
            code.insertBefore(n, rep);
            code.remove(n);
        }
        for (Runnable r : edits) r.run();
        if (!nulled.isEmpty()) {
            for (AbstractInsnNode p = code.getFirst(); p != null; p = p.getNext()) {
                if (p instanceof FrameNode f) {
                    replaceUninit(f.local, nulled);
                    replaceUninit(f.stack, nulled);
                }
            }
        }
        return true;
    }

    private static String lineOf(AbstractInsnNode n) {
        for (AbstractInsnNode p = n.getPrevious(); p != null; p = p.getPrevious()) {
            if (p instanceof LineNumberNode ln) return ":" + ln.line;
        }
        return "";
    }

    private static void replaceUninit(List<Object> types, Set<LabelNode> nulled) {
        if (types == null) return;
        for (int i = 0; i < types.size(); i++) {
            if (types.get(i) instanceof LabelNode l && nulled.contains(l)) types.set(i, Opcodes.NULL);
        }
    }

    private static List<Type> popsFor(int opcode) {
        return switch (opcode) {
            case Opcodes.ANEWARRAY -> List.of(Type.INT_TYPE);
            default -> List.of(Type.getObjectType("java/lang/Object"));   // CHECKCAST·INSTANCEOF: 객체 하나
        };
    }

    /** at 을 [pops 비우기, 덫, 기본값 넣기] 로 바꾼다. pops 는 스택 아래→위 순서. */
    private void replace(InsnList code, AbstractInsnNode at, List<Type> pops, String site, String target, String why, Type push) {
        InsnList rep = new InsnList();
        for (int i = pops.size() - 1; i >= 0; i--) rep.add(new InsnNode(pops.get(i).getSize() == 2 ? Opcodes.POP2 : Opcodes.POP));
        emitTrap(rep, site, target, why);
        switch (push.getSort()) {
            case Type.VOID -> { }
            case Type.BOOLEAN, Type.CHAR, Type.BYTE, Type.SHORT, Type.INT -> rep.add(new InsnNode(Opcodes.ICONST_0));
            case Type.LONG -> rep.add(new InsnNode(Opcodes.LCONST_0));
            case Type.FLOAT -> rep.add(new InsnNode(Opcodes.FCONST_0));
            case Type.DOUBLE -> rep.add(new InsnNode(Opcodes.DCONST_0));
            default -> rep.add(new InsnNode(Opcodes.ACONST_NULL));
        }
        code.insertBefore(at, rep);
        code.remove(at);
    }

    private void emitTrap(InsnList rep, String site, String target, String why) {
        rep.add(new LdcInsnNode(plugin));
        rep.add(new LdcInsnNode(site));
        rep.add(new LdcInsnNode(target));
        rep.add(new LdcInsnNode(why));
        rep.add(new MethodInsnNode(Opcodes.INVOKESTATIC, Type.getInternalName(NmsTrap.class), "call",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", false));
    }

    // ------------------------------------------------------------------ 판정

    private boolean judged(String owner) {
        String e = NmsSymbols.element(owner);
        return e != null && NmsSymbols.isNms(e) && !own.contains(e);
    }

    private boolean missingClass(String internalName) {
        return judged(internalName) && !symbols.hasClass(internalName);
    }

    /** 없으면 대상 이름, 있으면(또는 판정 대상 아님) null. */
    private String missingMethod(String owner, String name, String desc) {
        if (!judged(owner)) return null;
        if (!symbols.hasClass(owner)) return dots(NmsSymbols.element(owner));
        if (symbols.hasMethod(owner, name, desc)) return null;
        StringJoiner args = new StringJoiner(", ", "(", ")");
        for (Type t : Type.getArgumentTypes(desc)) args.add(simple(t.getClassName()));
        return dots(owner) + "#" + name + args;
    }

    private String missingField(String owner, String name, String desc) {
        if (!judged(owner)) return null;
        if (!symbols.hasClass(owner)) return dots(NmsSymbols.element(owner));
        if (symbols.hasField(owner, name, desc)) return null;
        return dots(owner) + "#" + name + " : " + simple(Type.getType(desc).getClassName());
    }

    private static String simple(String className) {
        int d = className.lastIndexOf('.');
        return d < 0 ? className : className.substring(d + 1);
    }

    static String dots(String internalName) { return internalName == null ? "?" : internalName.replace('/', '.'); }
}
