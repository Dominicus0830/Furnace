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
 * 덫은 JVM 이 냈을 링크 오류 종류({@link UnsupportedNmsLink})를 던진다. 없는 클래스로의 {@code checkcast}·{@code instanceof} 는
 * JVM 처럼 값이 null 이면 통과한다({@link NmsTrap#castNull}·{@link NmsTrap#instanceOfNull} — 분기가 없어 프레임은 그대로다).
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

    private static final java.util.Set<String> REFUSAL_WARNED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private UnsupportedNmsCall refusal(String cls, String parent, String why) {
        String target = "상속 " + dots(cls) + " → " + dots(parent);
        // 정의 거부는 LinkageError 가 아니라 JVM 이 실패를 기억하지 않는다 — 다시 가리킬 때마다 오므로 WARN 은 (플러그인, 클래스)당 한 번
        var log = org.slf4j.LoggerFactory.getLogger("Furnace");
        if (NmsCompatReport.otherVersion(cls)) {
            // 다른 버전용 모듈(예: FAWE 의 v1_21_4·v26_1 어댑터)은 1.21.1 에서 쓰이지 않는다 — 버전 고르기가 차례로 찔러 보는 것이라 경고하지 않는다
            // (Paper 1.21.1 에서도 그 클래스는 링크에 실패한다). 시작 보고서도 이 클래스들을 "다른 버전"으로 빼고 센다.
            log.debug("플러그인 {}: 다른 버전 모듈 클래스 {} 정의 거부 — {} 상속", plugin, dots(cls), dots(parent));
        } else if (REFUSAL_WARNED.add(plugin + '\0' + cls)) {
            log.warn("플러그인 {}: 클래스 {} 를 정의하지 않는다 — {} 를 상속한다: {}", plugin, dots(cls), dots(parent), why);
        } else {
            log.debug("플러그인 {}: 클래스 {} 정의 거부(다시)", plugin, dots(cls));
        }
        return new UnsupportedNmsCall(target, why + " (플러그인=" + plugin + ")");
    }

    /** 시험용. */
    static void resetRefusalWarnings() { REFUSAL_WARNED.clear(); }

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
                    switch (t.getOpcode()) {
                        case Opcodes.CHECKCAST -> edits.add(() -> nullPass(code, at, site, target, why, false));
                        case Opcodes.INSTANCEOF -> edits.add(() -> nullPass(code, at, site, target, why, true));
                        default -> edits.add(() -> replace(code, at, List.of(Type.INT_TYPE), site, target, why, CLASS,
                                Type.getObjectType("java/lang/Object")));   // ANEWARRAY: 개수 하나
                    }
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
                    Miss target = missingMethod(m.owner, m.name, m.desc);
                    if (target == null) break;
                    if (m.getOpcode() == Opcodes.INVOKESPECIAL && m.name.equals("<init>")) {
                        if (matched == null) break;   // 상위 생성자 호출 — NMS 상속은 visit 에서 이미 막았다
                        nullNews.putIfAbsent(matched, Boolean.FALSE);
                    }
                    List<Type> pops = new ArrayList<>(List.of(Type.getArgumentTypes(m.desc)));
                    if (m.getOpcode() != Opcodes.INVOKESTATIC) pops.addFirst(Type.getObjectType("java/lang/Object"));
                    Type ret = m.name.equals("<init>") ? Type.VOID_TYPE : Type.getReturnType(m.desc);
                    String why = NmsGrades.reason(m.owner);
                    edits.add(() -> replace(code, at, pops, site, target.target(), why, target.kind(), ret));
                    any = true;
                }
                case FieldInsnNode f -> {
                    Miss target = missingField(f.owner, f.name, f.desc);
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
                    edits.add(() -> replace(code, at, pops, site, target.target(), why, target.kind(), r));
                    any = true;
                }
                case LdcInsnNode l when l.cst instanceof Type ty && (ty.getSort() == Type.OBJECT || ty.getSort() == Type.ARRAY) -> {
                    if (!missingClass(ty.getInternalName())) break;
                    String target = dots(NmsSymbols.element(ty.getInternalName()));
                    String why = NmsGrades.reason(ty.getInternalName());
                    edits.add(() -> replace(code, at, List.of(), site, target, why, CLASS, Type.getObjectType("java/lang/Class")));
                    any = true;
                }
                case LdcInsnNode l when l.cst instanceof Type ty && ty.getSort() == Type.METHOD -> {
                    Miss miss = missingInMethodType(ty.getDescriptor());
                    if (miss == null) break;
                    edits.add(() -> replace(code, at, List.of(), site, miss.target(), NmsGrades.reason(miss.owner()), CLASS,
                            Type.getObjectType("java/lang/invoke/MethodType")));
                    any = true;
                }
                case LdcInsnNode l when l.cst instanceof Handle h -> {
                    Miss miss = missingInHandle(h);
                    if (miss == null) break;
                    edits.add(() -> replace(code, at, List.of(), site, miss.target(), NmsGrades.reason(miss.owner()), miss.kind(),
                            Type.getObjectType("java/lang/invoke/MethodHandle")));
                    any = true;
                }
                case MultiANewArrayInsnNode a -> {
                    if (!missingClass(a.desc)) break;
                    List<Type> pops = new ArrayList<>();
                    for (int i = 0; i < a.dims; i++) pops.add(Type.INT_TYPE);
                    String target = dots(NmsSymbols.element(a.desc));
                    String why = NmsGrades.reason(a.desc);
                    edits.add(() -> replace(code, at, pops, site, target, why, CLASS, Type.getObjectType("java/lang/Object")));
                    any = true;
                }
                case InvokeDynamicInsnNode d -> {
                    // 호출 자리 서술자·부트스트랩 인자(메서드 핸들·MethodType)가 가리키는 없는 클래스·멤버 — JVM 은 링크 때 그것들을 해석한다
                    Miss miss = missingInMethodType(d.desc);
                    for (Object arg : d.bsmArgs) {
                        if (miss != null) break;
                        if (arg instanceof Handle h) miss = missingInHandle(h);
                        else if (arg instanceof Type ty && ty.getSort() == Type.METHOD) miss = missingInMethodType(ty.getDescriptor());
                        else if (arg instanceof Type ty && (ty.getSort() == Type.OBJECT || ty.getSort() == Type.ARRAY) && missingClass(ty.getInternalName())) {
                            miss = new Miss(dots(NmsSymbols.element(ty.getInternalName())), CLASS, ty.getInternalName());
                        }
                    }
                    if (miss == null) break;
                    List<Type> pops = List.of(Type.getArgumentTypes(d.desc));
                    Type ret = Type.getReturnType(d.desc);
                    Miss m = miss;
                    edits.add(() -> replace(code, at, pops, site, m.target(), NmsGrades.reason(m.owner()), m.kind(), ret));
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
            if (e.getValue()) emitTrap(rep, siteOfNew + lineOf(n), dots(n.desc), NmsGrades.reason(n.desc), CLASS);
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

    private static final int CLASS = UnsupportedNmsLink.CLASS;

    /** 없는 클래스 판정 결과: 대상 이름, 덫 종류({@link UnsupportedNmsLink}), 이유를 고를 소유 클래스. */
    private record Miss(String target, int kind, String owner) {}

    /**
     * checkcast·instanceof 를 [값 그대로 두고 문자열 4개, 정적 호출] 로 바꾼다. checkcast 는 뒤에 aconst_null(결과 자리), instanceof 는
     * 호출이 int 를 돌려준다. 분기가 없어 스택 맵 프레임을 고칠 필요가 없다.
     */
    private void nullPass(InsnList code, AbstractInsnNode at, String site, String target, String why, boolean instanceOf) {
        InsnList rep = new InsnList();
        rep.add(new LdcInsnNode(plugin));
        rep.add(new LdcInsnNode(site));
        rep.add(new LdcInsnNode(target));
        rep.add(new LdcInsnNode(why));
        String d = "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)" + (instanceOf ? "I" : "V");
        rep.add(new MethodInsnNode(Opcodes.INVOKESTATIC, Type.getInternalName(NmsTrap.class), instanceOf ? "instanceOfNull" : "castNull", d, false));
        if (!instanceOf) rep.add(new InsnNode(Opcodes.ACONST_NULL));
        code.insertBefore(at, rep);
        code.remove(at);
    }

    /** at 을 [pops 비우기, 덫, 기본값 넣기] 로 바꾼다. pops 는 스택 아래→위 순서. */
    private void replace(InsnList code, AbstractInsnNode at, List<Type> pops, String site, String target, String why, int kind, Type push) {
        InsnList rep = new InsnList();
        for (int i = pops.size() - 1; i >= 0; i--) rep.add(new InsnNode(pops.get(i).getSize() == 2 ? Opcodes.POP2 : Opcodes.POP));
        emitTrap(rep, site, target, why, kind);
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

    private void emitTrap(InsnList rep, String site, String target, String why, int kind) {
        rep.add(new LdcInsnNode(plugin));
        rep.add(new LdcInsnNode(site));
        rep.add(new LdcInsnNode(target));
        rep.add(new LdcInsnNode(why));
        rep.add(new InsnNode(Opcodes.ICONST_0 + kind));
        rep.add(new MethodInsnNode(Opcodes.INVOKESTATIC, Type.getInternalName(NmsTrap.class), "call",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;I)V", false));
    }

    // ------------------------------------------------------------------ 판정

    private boolean judged(String owner) {
        String e = NmsSymbols.element(owner);
        return e != null && NmsSymbols.isNms(e) && !own.contains(e);
    }

    private boolean missingClass(String internalName) {
        return judged(internalName) && !symbols.hasClass(internalName);
    }

    /** 없으면 판정 결과, 있으면(또는 판정 대상 아님) null. 소유 클래스가 없으면 JVM 처럼 클래스 없음 종류. */
    private Miss missingMethod(String owner, String name, String desc) {
        if (!judged(owner)) return null;
        if (!symbols.hasClass(owner)) return new Miss(dots(NmsSymbols.element(owner)), CLASS, owner);
        if (symbols.hasMethod(owner, name, desc)) return null;
        StringJoiner args = new StringJoiner(", ", "(", ")");
        for (Type t : Type.getArgumentTypes(desc)) args.add(simple(t.getClassName()));
        return new Miss(dots(owner) + "#" + name + args, UnsupportedNmsLink.METHOD, owner);
    }

    private Miss missingField(String owner, String name, String desc) {
        if (!judged(owner)) return null;
        if (!symbols.hasClass(owner)) return new Miss(dots(NmsSymbols.element(owner)), CLASS, owner);
        if (symbols.hasField(owner, name, desc)) return null;
        return new Miss(dots(owner) + "#" + name + " : " + simple(Type.getType(desc).getClassName()), UnsupportedNmsLink.FIELD, owner);
    }

    /** 메서드 서술자의 인자·반환형 중 없는 클래스. */
    private Miss missingInMethodType(String methodDesc) {
        Type mt = Type.getMethodType(methodDesc);
        List<Type> types = new ArrayList<>(List.of(mt.getArgumentTypes()));
        types.add(mt.getReturnType());
        for (Type t : types) {
            if ((t.getSort() == Type.OBJECT || t.getSort() == Type.ARRAY) && missingClass(t.getInternalName())) {
                return new Miss(dots(NmsSymbols.element(t.getInternalName())), CLASS, t.getInternalName());
            }
        }
        return null;
    }

    /** 메서드 핸들 상수: 멤버가 없거나, 그 서술자에 없는 클래스가 있으면. */
    private Miss missingInHandle(Handle h) {
        Miss m = h.getTag() <= Opcodes.H_PUTSTATIC ? missingField(h.getOwner(), h.getName(), h.getDesc())
                : missingMethod(h.getOwner(), h.getName(), h.getDesc());
        if (m != null) return m;
        if (h.getTag() <= Opcodes.H_PUTSTATIC) {
            Type ft = Type.getType(h.getDesc());
            if ((ft.getSort() == Type.OBJECT || ft.getSort() == Type.ARRAY) && missingClass(ft.getInternalName())) {
                return new Miss(dots(NmsSymbols.element(ft.getInternalName())), CLASS, ft.getInternalName());
            }
            return null;
        }
        return missingInMethodType(h.getDesc());
    }

    private static String simple(String className) {
        int d = className.lastIndexOf('.');
        return d < 0 ? className : className.substring(d + 1);
    }

    static String dots(String internalName) { return internalName == null ? "?" : internalName.replace('/', '.'); }
}
