package org.domi.furnace.tools.extract;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * 채움 추출기(T-262). 바닐라 서버 jar(Mojang 이름)에서 {@link FillTable} 을 뽑는다. 정확히 정해진 몸통 모양에만 반응하고,
 * 조금이라도 다르면(분기·호출·계산 하나라도) 그 멤버는 표에 넣지 않는다 — 모르면 채우지 않는다(손으로 남는다).
 * 몸통의 명령은 저장하지 않고 값과 이름만 남긴다. 알아보는 모양:
 * <ul>
 *   <li>단순 생성자: {@code aload0; (인자 적재|리터럴)*; invokespecial 상위.<init>; (aload0; 인자 적재|리터럴; putfield 자기.필드)*; return}</li>
 *   <li>단순 읽기: {@code aload0; getfield 자기.필드; xreturn}</li>
 *   <li>정적 리터럴: 정적 초기화의 {@code 리터럴; putstatic 자기.필드}, 그 필드에 대한 putstatic 이 클래스 전체에 이것 하나뿐</li>
 *   <li>열거형 상수: {@code new E; dup; ldc 이름; 서수; 리터럴*; invokespecial E.<init>; putstatic E.이름}</li>
 *   <li>레코드 기본 메서드: {@code aload0; (aload1;) invokedynamic ObjectMethods; xreturn}</li>
 *   <li>짧은 값 몸통(T-263 G0, 인터페이스의 default·정적 메서드 포함): {@code return}(빈 void), {@code 리터럴; xreturn},
 *       {@code xload 인자|aload0; xreturn}, {@code getstatic 필드; xreturn} — 명령 두 개(빈 몸통은 하나)가 전부일 때만</li>
 * </ul>
 */
public final class FillExtractor {
    private FillExtractor() {}

    public static FillTable fromJar(Path jar, List<String> prefixes, String label) throws IOException {
        FillTable t = new FillTable(label);
        try (ZipFile zf = new ZipFile(jar.toFile())) {
            List<String> names = new ArrayList<>();
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String n = e.getName();
                if (e.isDirectory() || !n.endsWith(".class") || n.startsWith("META-INF/")) continue;
                if (!SigTable.matches(n, prefixes)) continue;
                names.add(n);
            }
            names.sort(null);
            for (String n : names) {
                try (InputStream in = zf.getInputStream(zf.getEntry(n))) {
                    extract(in.readAllBytes(), t);
                }
            }
        }
        return t;
    }

    /** 클래스 하나를 표에 더한다(시험이 직접 부른다). */
    public static void extract(byte[] bytes, FillTable t) {
        ClassNode cn = new ClassNode();
        new ClassReader(bytes).accept(cn, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        if ((cn.access & Opcodes.ACC_SYNTHETIC) != 0) return;
        String h = shapeClinitHash(cn);
        if (h != null) t.addClinitHash(cn.name, h);
        if ((cn.access & Opcodes.ACC_INTERFACE) != 0) {
            // 인터페이스는 짧은 값 몸통(default·정적 메서드)만
            for (MethodNode m : cn.methods) {
                if ((m.access & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE | Opcodes.ACC_ABSTRACT)) != 0 || m.name.startsWith("<")) continue;
                FillTable.Method v = valueBody(cn, m, real(m));
                if (v != null) t.add(v);
            }
            return;
        }
        boolean isEnum = (cn.access & Opcodes.ACC_ENUM) != 0;
        boolean isRecord = (cn.access & 0x10000) != 0 || "java/lang/Record".equals(cn.superName);
        Map<String, String> fieldDescs = new HashMap<>();
        for (var f : cn.fields) fieldDescs.put(f.name, f.desc);
        for (MethodNode m : cn.methods) {
            if ((m.access & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE)) != 0) continue;
            List<AbstractInsnNode> ins = real(m);
            if (m.name.equals("<init>")) {
                FillTable.Ctor c = ctor(cn, m, ins);
                if (c != null) t.add(c);
            } else if (m.name.equals("<clinit>")) {
                // 첫 분기·switch·throw·try 범위 앞의 직선 구간만 — 조건·삼항·반복·예외 안의 대입은 값이 실행에 달렸다
                List<AbstractInsnNode> line = straightPrefix(m);
                statics(cn, line, t, fieldDescs);
                if (isEnum) enumConsts(cn, line, t);
            } else {
                FillTable.Getter g = (m.access & Opcodes.ACC_STATIC) == 0 && m.desc.startsWith("()") && !m.desc.equals("()V") ? getter(cn, m, ins) : null;
                if (g != null) t.add(g);
                else if ((m.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0) {
                    FillTable.Method v = valueBody(cn, m, ins);
                    if (v != null) t.add(v);
                }
            }
        }
        if (isRecord) {
            List<String> plain = new ArrayList<>();
            for (MethodNode m : cn.methods) {
                String key = m.name + m.desc;
                boolean eq = key.equals("equals(Ljava/lang/Object;)Z");
                if (!eq && !key.equals("toString()Ljava/lang/String;") && !key.equals("hashCode()I")) continue;
                if (objectMethods(m, real(m), eq)) plain.add(m.name);
            }
            if (!plain.isEmpty()) {
                plain.sort(null);
                t.addObjectMethods(cn.name, plain);
            }
        }
    }

    static final String VOXEL_SHAPE = "net/minecraft/world/phys/shapes/VoxelShape";

    /** 모양 정적 필드(서술자·제네릭 서명에 VoxelShape)가 있는 클래스인가(T-263 S5). */
    public static boolean hasShapeStatics(ClassNode cn) {
        for (var f : cn.fields) {
            if ((f.access & Opcodes.ACC_STATIC) != 0 && (f.signature != null ? f.signature : f.desc).contains(VOXEL_SHAPE)) return true;
        }
        return false;
    }

    /**
     * 모양 정적 필드가 있는 클래스의 정적 초기화 해시(T-263 S5): 정적 초기화와, 거기서 (람다로도) 부르는 같은 클래스의 정적 메서드를 차례로
     * 명령 단위로 적어(줄 번호·프레임 없이, 분기 대상은 순번) sha256 앞 16자리. 값은 바닐라를 띄워 읽고, 이 해시가 바닐라·Paper 에서
     * 같은 클래스만 채운다. 다른 클래스의 도우미(Block.box·Shapes)는 보지 않는다 — 가정: Paper 의 모양 패치는 성능 몫이고 값을 바꾸지 않는다
     * (모양 표 오라클은 바닐라와 견준다).
     */
    static String shapeClinitHash(ClassNode cn) {
        if (!hasShapeStatics(cn)) return null;
        Map<String, MethodNode> own = new HashMap<>();
        for (MethodNode m : cn.methods) own.put(m.name + m.desc, m);
        StringBuilder b = new StringBuilder();
        java.util.ArrayDeque<String> todo = new java.util.ArrayDeque<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        todo.add("<clinit>()V");
        while (!todo.isEmpty()) {
            String k = todo.poll();
            MethodNode m = own.get(k);
            // 같은 클래스의 정적 메서드(람다 몸통·도우미)만 따라간다 — 생성자 참조(코덱의 X::new)는 모양 값과 상관없고 Paper 가 자주 바꾼다
            if (m == null || (m.access & Opcodes.ACC_STATIC) == 0 || !seen.add(k)) continue;
            b.append('#').append(k).append('\n');
            normalize(cn.name, m, b, todo);
        }
        try {
            byte[] d = java.security.MessageDigest.getInstance("SHA-256").digest(b.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(d).substring(0, 16);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void normalize(String owner, MethodNode m, StringBuilder b, java.util.ArrayDeque<String> calls) {
        Map<org.objectweb.asm.tree.LabelNode, Integer> lab = new HashMap<>();
        for (AbstractInsnNode in : m.instructions) if (in instanceof org.objectweb.asm.tree.LabelNode l) lab.put(l, lab.size());
        for (AbstractInsnNode in : m.instructions) {
            if (in instanceof org.objectweb.asm.tree.LabelNode l) {
                b.append('L').append(lab.get(l)).append(';');
                continue;
            }
            if (in.getOpcode() < 0) continue;
            b.append(in.getOpcode());
            switch (in) {
                case MethodInsnNode x -> {
                    b.append(' ').append(x.owner).append('.').append(x.name).append(x.desc);
                    if (x.owner.equals(owner)) calls.add(x.name + x.desc);
                }
                case FieldInsnNode x -> b.append(' ').append(x.owner).append('.').append(x.name).append(':').append(x.desc);
                case LdcInsnNode x -> b.append(' ').append(x.cst.getClass().getSimpleName()).append(':').append(x.cst);
                case IntInsnNode x -> b.append(' ').append(x.operand);
                case VarInsnNode x -> b.append(' ').append(x.var);
                case TypeInsnNode x -> b.append(' ').append(x.desc);
                case org.objectweb.asm.tree.IincInsnNode x -> b.append(' ').append(x.var).append(',').append(x.incr);
                case org.objectweb.asm.tree.JumpInsnNode x -> b.append(" L").append(lab.get(x.label));
                case org.objectweb.asm.tree.TableSwitchInsnNode x -> {
                    b.append(' ').append(x.min).append('-').append(x.max).append(" L").append(lab.get(x.dflt));
                    for (var l : x.labels) b.append(" L").append(lab.get(l));
                }
                case org.objectweb.asm.tree.LookupSwitchInsnNode x -> {
                    b.append(' ').append(x.keys).append(" L").append(lab.get(x.dflt));
                    for (var l : x.labels) b.append(" L").append(lab.get(l));
                }
                case org.objectweb.asm.tree.MultiANewArrayInsnNode x -> b.append(' ').append(x.desc).append(',').append(x.dims);
                case InvokeDynamicInsnNode x -> {
                    b.append(' ').append(x.name).append(x.desc).append(' ').append(x.bsm);
                    for (Object a : x.bsmArgs) {
                        b.append(' ').append(a);
                        if (a instanceof org.objectweb.asm.Handle h && h.getOwner().equals(owner)) calls.add(h.getName() + h.getDesc());
                    }
                }
                default -> { }
            }
            b.append(';');
        }
        for (var tc : m.tryCatchBlocks) b.append(" T").append(lab.get(tc.start)).append(',').append(lab.get(tc.end)).append(',').append(lab.get(tc.handler)).append(',').append(tc.type);
        b.append('\n');
    }

    /** 메서드 앞쪽의 직선 구간(실제 명령만): 첫 점프·switch·athrow, 또는 try 범위가 시작하는 자리 앞까지. */
    static List<AbstractInsnNode> straightPrefix(MethodNode m) {
        java.util.Set<org.objectweb.asm.tree.LabelNode> tryStarts = new java.util.HashSet<>();
        for (var tc : m.tryCatchBlocks) tryStarts.add(tc.start);
        List<AbstractInsnNode> r = new ArrayList<>();
        for (AbstractInsnNode i : m.instructions) {
            if (i instanceof org.objectweb.asm.tree.LabelNode l) {
                if (tryStarts.contains(l)) break;
                continue;
            }
            int op = i.getOpcode();
            if (op < 0) continue;
            if (i instanceof org.objectweb.asm.tree.JumpInsnNode || i instanceof org.objectweb.asm.tree.TableSwitchInsnNode
                    || i instanceof org.objectweb.asm.tree.LookupSwitchInsnNode || op == Opcodes.ATHROW) break;
            r.add(i);
        }
        return r;
    }

    private static List<AbstractInsnNode> real(MethodNode m) {
        List<AbstractInsnNode> r = new ArrayList<>();
        for (AbstractInsnNode i : m.instructions) if (i.getOpcode() >= 0) r.add(i);
        return r;
    }

    // ───────────────────────── 리터럴·적재 ─────────────────────────

    /** 리터럴을 쌓는 명령이면 값, 아니면 null. */
    static FillTable.Val literal(AbstractInsnNode i) {
        int op = i.getOpcode();
        if (op >= Opcodes.ICONST_M1 && op <= Opcodes.ICONST_5) return FillTable.Val.lit('i', op - Opcodes.ICONST_0);
        if (op == Opcodes.LCONST_0 || op == Opcodes.LCONST_1) return FillTable.Val.lit('j', (long) (op - Opcodes.LCONST_0));
        if (op >= Opcodes.FCONST_0 && op <= Opcodes.FCONST_2) return FillTable.Val.lit('f', (float) (op - Opcodes.FCONST_0));
        if (op == Opcodes.DCONST_0 || op == Opcodes.DCONST_1) return FillTable.Val.lit('d', (double) (op - Opcodes.DCONST_0));
        if (op == Opcodes.ACONST_NULL) return FillTable.Val.lit('n', null);
        if (op == Opcodes.BIPUSH || op == Opcodes.SIPUSH) return FillTable.Val.lit('i', ((IntInsnNode) i).operand);
        if (op == Opcodes.LDC) {
            Object c = ((LdcInsnNode) i).cst;
            if (c instanceof Integer x) return FillTable.Val.lit('i', x);
            if (c instanceof Long x) return FillTable.Val.lit('j', x);
            if (c instanceof Float x) return FillTable.Val.lit('f', x);
            if (c instanceof Double x) return FillTable.Val.lit('d', x);
            if (c instanceof String x) return FillTable.Val.lit('s', x);
        }
        return null;
    }

    /** 인자 적재(this 가 아닌 것)이면 지역변수 번호 값. */
    static FillTable.Val param(AbstractInsnNode i) {
        int op = i.getOpcode();
        if (op >= Opcodes.ILOAD && op <= Opcodes.ALOAD) {
            int v = ((VarInsnNode) i).var;
            return v == 0 ? null : FillTable.Val.param(v);
        }
        return null;
    }

    private static boolean isThis(AbstractInsnNode i) {
        return i.getOpcode() == Opcodes.ALOAD && ((VarInsnNode) i).var == 0;
    }

    private static FillTable.Val value(AbstractInsnNode i) {
        FillTable.Val v = literal(i);
        return v != null ? v : param(i);
    }

    /** 적재한 값의 종류가 서술자와 맞는지(리터럴 종류·인자 적재 명령 종류). 맞지 않으면 모르는 모양으로 본다. */
    private static boolean fits(FillTable.Val v, AbstractInsnNode src, Type want) {
        if (v.isParam()) {
            int op = src.getOpcode();
            return switch (want.getSort()) {
                case Type.LONG -> op == Opcodes.LLOAD;
                case Type.FLOAT -> op == Opcodes.FLOAD;
                case Type.DOUBLE -> op == Opcodes.DLOAD;
                case Type.OBJECT, Type.ARRAY -> op == Opcodes.ALOAD;
                default -> op == Opcodes.ILOAD;
            };
        }
        return switch (want.getSort()) {
            case Type.LONG -> v.kind() == 'j';
            case Type.FLOAT -> v.kind() == 'f';
            case Type.DOUBLE -> v.kind() == 'd';
            case Type.OBJECT -> v.kind() == 'n' || v.kind() == 's' && want.getDescriptor().equals("Ljava/lang/String;");
            case Type.ARRAY -> v.kind() == 'n';
            default -> v.kind() == 'i';
        };
    }

    // ───────────────────────── 생성자 ─────────────────────────

    private static FillTable.Ctor ctor(ClassNode cn, MethodNode m, List<AbstractInsnNode> ins) {
        int p = 0;
        if (ins.isEmpty() || !isThis(ins.get(p++))) return null;
        Type[] superArgsT = null;
        // 상위 호출 인자: invokespecial 까지의 값들
        List<FillTable.Val> superArgs = new ArrayList<>();
        List<AbstractInsnNode> superSrc = new ArrayList<>();
        while (p < ins.size() && ins.get(p).getOpcode() != Opcodes.INVOKESPECIAL) {
            FillTable.Val v = value(ins.get(p));
            if (v == null) return null;
            superArgs.add(v);
            superSrc.add(ins.get(p));
            p++;
        }
        if (p >= ins.size()) return null;
        MethodInsnNode sup = (MethodInsnNode) ins.get(p++);
        if (!sup.name.equals("<init>")) return null;
        if (!sup.owner.equals(cn.superName)) return null;   // this(...) 위임(같은 클래스)은 모르는 모양
        superArgsT = Type.getArgumentTypes(sup.desc);
        if (superArgsT.length != superArgs.size()) return null;
        for (int k = 0; k < superArgsT.length; k++) if (!fits(superArgs.get(k), superSrc.get(k), superArgsT[k])) return null;
        // 파라미터 번호가 이 생성자의 실제 자리인지(타입 폭 포함)는 서술자로 따진다
        Map<Integer, Type> slots = slots(m);
        for (int k = 0; k < superArgs.size(); k++) if (!slotFits(superArgs.get(k), superArgsT[k], slots)) return null;
        List<FillTable.Step> steps = new ArrayList<>();
        while (p < ins.size() && ins.get(p).getOpcode() != Opcodes.RETURN) {
            if (p + 2 >= ins.size()) return null;
            if (!isThis(ins.get(p))) return null;
            AbstractInsnNode src = ins.get(p + 1);
            FillTable.Val v = value(src);
            if (v == null || !(ins.get(p + 2) instanceof FieldInsnNode f) || f.getOpcode() != Opcodes.PUTFIELD || !f.owner.equals(cn.name)) return null;
            Type ft = Type.getType(f.desc);
            if (!fits(v, src, ft) || !slotFits(v, ft, slots)) return null;
            steps.add(new FillTable.Step(f.name, f.desc, v));
            p += 3;
        }
        if (p != ins.size() - 1) return null;   // return 뒤에 더 있거나 return 이 없다
        return new FillTable.Ctor(cn.name, m.desc, sup.owner, sup.desc, superArgs, steps);
    }

    /** 지역변수 번호 → 그 자리의 인자 타입(this 제외). */
    private static Map<Integer, Type> slots(MethodNode m) {
        Map<Integer, Type> r = new HashMap<>();
        int s = 1;
        for (Type t : Type.getArgumentTypes(m.desc)) {
            r.put(s, t);
            s += t.getSize();
        }
        return r;
    }

    private static boolean slotFits(FillTable.Val v, Type want, Map<Integer, Type> slots) {
        if (!v.isParam()) return true;
        Type have = slots.get((Integer) v.value());
        if (have == null) return false;
        return have.equals(want) || (isRef(have) && isRef(want)) || (isIntLike(have) && isIntLike(want));
    }

    private static boolean isRef(Type t) { return t.getSort() == Type.OBJECT || t.getSort() == Type.ARRAY; }

    private static boolean isIntLike(Type t) { return t.getSort() >= Type.BOOLEAN && t.getSort() <= Type.INT; }

    // ───────────────────────── 읽기 ─────────────────────────

    private static FillTable.Getter getter(ClassNode cn, MethodNode m, List<AbstractInsnNode> ins) {
        if (ins.size() != 3 || !isThis(ins.get(0))) return null;
        if (!(ins.get(1) instanceof FieldInsnNode f) || f.getOpcode() != Opcodes.GETFIELD || !f.owner.equals(cn.name)) return null;
        int ret = ins.get(2).getOpcode();
        Type rt = Type.getReturnType(m.desc);
        int want = switch (rt.getSort()) {
            case Type.LONG -> Opcodes.LRETURN;
            case Type.FLOAT -> Opcodes.FRETURN;
            case Type.DOUBLE -> Opcodes.DRETURN;
            case Type.OBJECT, Type.ARRAY -> Opcodes.ARETURN;
            default -> Opcodes.IRETURN;
        };
        if (ret != want) return null;
        Type ft = Type.getType(f.desc);
        // 반환형과 필드형이 다르면(상위형으로 돌려주기 등) 그대로 둔다 — 참조끼리만 허용
        if (!(ft.equals(rt) || isRef(ft) && isRef(rt))) return null;
        return new FillTable.Getter(cn.name, m.name, m.desc, f.name, f.desc);
    }

    // ───────────────────────── 짧은 값 몸통(T-263 G0) ─────────────────────────

    /** 빈 void · 리터럴 반환 · 인자(this) 반환 · 정적 필드 반환. 다른 모양이면 null. */
    static FillTable.Method valueBody(ClassNode cn, MethodNode m, List<AbstractInsnNode> ins) {
        boolean isStatic = (m.access & Opcodes.ACC_STATIC) != 0;
        Type rt = Type.getReturnType(m.desc);
        if (m.tryCatchBlocks != null && !m.tryCatchBlocks.isEmpty()) return null;
        if (ins.size() == 1) {
            return ins.get(0).getOpcode() == Opcodes.RETURN && rt.getSort() == Type.VOID ? new FillTable.Method(cn.name, m.name, m.desc, isStatic, 'E', "-") : null;
        }
        if (ins.size() != 2 || rt.getSort() == Type.VOID || ins.get(1).getOpcode() != rt.getOpcode(Opcodes.IRETURN)) return null;
        AbstractInsnNode a = ins.get(0);
        FillTable.Val lit = literal(a);
        if (lit != null) return fits(lit, a, rt) ? new FillTable.Method(cn.name, m.name, m.desc, isStatic, 'K', lit.encode()) : null;
        if (a instanceof VarInsnNode v && a.getOpcode() >= Opcodes.ILOAD && a.getOpcode() <= Opcodes.ALOAD) {
            Type have;
            if (!isStatic && v.var == 0) have = Type.getObjectType(cn.name);   // return this
            else {
                Map<Integer, Type> sl = new HashMap<>();
                int s = isStatic ? 0 : 1;
                for (Type t : Type.getArgumentTypes(m.desc)) {
                    sl.put(s, t);
                    s += t.getSize();
                }
                have = sl.get(v.var);
            }
            if (have == null || a.getOpcode() != have.getOpcode(Opcodes.ILOAD)) return null;
            boolean ok = have.equals(rt) || isRef(have) && isRef(rt) || isIntLike(have) && isIntLike(rt);   // 명령 두 개면 javac 가 변환 없이 대입 가능한 꼴
            return ok ? new FillTable.Method(cn.name, m.name, m.desc, isStatic, 'A', "p:" + v.var) : null;
        }
        if (a instanceof FieldInsnNode f && a.getOpcode() == Opcodes.GETSTATIC) {
            Type ft = Type.getType(f.desc);
            if (!(ft.equals(rt) || isRef(ft) && isRef(rt))) return null;
            return new FillTable.Method(cn.name, m.name, m.desc, isStatic, 'R', f.owner + "." + f.name + ":" + f.desc);
        }
        return null;
    }

    // ───────────────────────── 정적 리터럴 ─────────────────────────

    private static void statics(ClassNode cn, List<AbstractInsnNode> ins, FillTable t, Map<String, String> fieldDescs) {
        // 클래스 전체의 putstatic 개수(자기 필드)
        Map<String, Integer> writes = new HashMap<>();
        for (MethodNode m : cn.methods) {
            for (AbstractInsnNode i : m.instructions) {
                if (i.getOpcode() == Opcodes.PUTSTATIC && i instanceof FieldInsnNode f && f.owner.equals(cn.name)) writes.merge(f.name, 1, Integer::sum);
            }
        }
        for (int k = 1; k < ins.size(); k++) {
            if (!(ins.get(k) instanceof FieldInsnNode f) || f.getOpcode() != Opcodes.PUTSTATIC || !f.owner.equals(cn.name)) continue;
            if (writes.getOrDefault(f.name, 0) != 1) continue;
            if ((cn.access & Opcodes.ACC_ENUM) != 0 && f.name.equals("$VALUES")) continue;
            FillTable.Val v = literal(ins.get(k - 1));
            if (v == null || !fits(v, ins.get(k - 1), Type.getType(f.desc))) continue;
            // 열거형 상수 만들기(new … invokespecial)의 값은 여기 오지 않는다 — 앞이 리터럴이 아니다
            t.add(new FillTable.Static(cn.name, f.name, f.desc, v));
        }
    }

    // ───────────────────────── 열거형 상수 ─────────────────────────

    private static void enumConsts(ClassNode cn, List<AbstractInsnNode> ins, FillTable t) {
        for (int k = 0; k < ins.size(); k++) {
            if (!(ins.get(k) instanceof FieldInsnNode put) || put.getOpcode() != Opcodes.PUTSTATIC || !put.owner.equals(cn.name)) continue;
            if ((cn.fields.stream().filter(f -> f.name.equals(put.name)).findFirst().map(f -> f.access & Opcodes.ACC_ENUM).orElse(0)) == 0) continue;
            // 거꾸로: invokespecial, 인자들, 서수, 이름, dup, new
            if (k == 0 || !(ins.get(k - 1) instanceof MethodInsnNode init) || init.getOpcode() != Opcodes.INVOKESPECIAL || !init.name.equals("<init>")) continue;
            int ordinal = -1;
            int j = k - 2;
            Type[] at = Type.getArgumentTypes(init.desc);
            int extra = at.length - 2;
            boolean ok = init.owner.equals(cn.name) && extra >= 0;
            List<FillTable.Val> args = new ArrayList<>();
            if (ok) {
                // 인자 extra 개는 리터럴이어야 한다(단순 하나 = 명령 하나)
                for (int a = extra - 1; a >= 0; a--) {
                    FillTable.Val v = j >= 0 ? literal(ins.get(j)) : null;
                    if (v == null || !fits(v, ins.get(j), at[a + 2])) {
                        ok = false;
                        break;
                    }
                    args.add(0, v);
                    j--;
                }
            }
            // 서수·이름·dup·new 는 모양이 맞는지만 본다(앞쪽 값은 인자 수에 상관없이 고정 자리)
            int start = ok ? j : k - 2 - Math.max(extra, 0);
            // 인자 자리를 못 읽었어도 이름·서수는 필요 — 앞 4개 명령의 위치를 찾기 위해 인자 수를 알 때만 시도
            if (!ok) {
                // 리터럴이 아닌 인자가 섞인 상수: 이름·서수만 찾는다(명령 개수를 몰라 뒤로 훑는다)
                int q = k - 2;
                while (q >= 0 && !(ins.get(q).getOpcode() == Opcodes.NEW)) q--;
                if (q < 0 || q + 4 > k || !(ins.get(q) instanceof TypeInsnNode tn) || !tn.desc.equals(cn.name) || ins.get(q + 1).getOpcode() != Opcodes.DUP) continue;
                if (!(ins.get(q + 2) instanceof LdcInsnNode l) || !(l.cst instanceof String nm) || !nm.equals(put.name)) continue;
                FillTable.Val o = literal(ins.get(q + 3));
                if (o == null || o.kind() != 'i') continue;
                t.add(new FillTable.EnumConst(cn.name, (Integer) o.value(), put.name, init.owner.equals(cn.name) ? init.desc : "?", null));
                continue;
            }
            if (start < 3) continue;
            FillTable.Val o = literal(ins.get(start));
            if (o == null || o.kind() != 'i') continue;
            ordinal = (Integer) o.value();
            if (!(ins.get(start - 1) instanceof LdcInsnNode l) || !(l.cst instanceof String nm) || !nm.equals(put.name)) continue;
            if (ins.get(start - 2).getOpcode() != Opcodes.DUP || !(ins.get(start - 3) instanceof TypeInsnNode tn) || tn.getOpcode() != Opcodes.NEW || !tn.desc.equals(cn.name)) continue;
            t.add(new FillTable.EnumConst(cn.name, ordinal, put.name, init.desc, args));
        }
    }

    // ───────────────────────── 레코드 기본 메서드 ─────────────────────────

    private static boolean objectMethods(MethodNode m, List<AbstractInsnNode> ins, boolean eq) {
        int n = eq ? 4 : 3;
        if (ins.size() != n || !isThis(ins.get(0))) return false;
        if (eq && ins.get(1).getOpcode() != Opcodes.ALOAD) return false;
        if (!(ins.get(n - 2) instanceof InvokeDynamicInsnNode d) || !d.bsm.getOwner().equals("java/lang/runtime/ObjectMethods")) return false;
        int ret = ins.get(n - 1).getOpcode();
        return eq ? ret == Opcodes.IRETURN : (m.name.equals("hashCode") ? ret == Opcodes.IRETURN : ret == Opcodes.ARETURN);
    }

}
