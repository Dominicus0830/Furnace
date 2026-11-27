// T-263 S1b: 연결 추론 도구(설계 docs/roadmap/link-inferrer-t263-2026-11-23.md 3.1·3.2). 결정적이다 — 같은 입력이면 같은 후보·같은 파일. LLM 을 쓰지 않는다.
//  1) 전체 껍데기를 만들어(FullShells 와 같은 인자) 아직 던지는 몸통인 멤버(@NmsShellMember)를 모은다.
//  2) 그 멤버의 바닐라 몸통(Mojang 이름 jar, 캐시)을 읽어 모양으로 나눈다 — 명령을 복사하지 않고 분류와 값 뽑기(칸 필드·마스크·대상 이름)에만 쓴다(이식 금지, 3.6).
//  3) 후보: G1 관용구(엔티티 데이터 읽기·쓰기·깃발), G3 별칭(같은 사슬 멤버로 인자 그대로 넘김).
//  4) 후보 줄을 연결 파일로 만들어 생성기를 한 번 더 돌려 빌드 검사(이름 규칙·지문·대상 살아 있음·검사 종류)를 통과한 줄만 남긴다.
// 출력은 이름이 들어가므로 build/ 에만 둔다(커밋 안 함): candidates/<영역>.links, rejected.tsv, summary.md(숫자만 — 이것은 문서에 옮겨도 된다).
// 이 도구는 줄을 채택하지 않는다. 채택(정답기·증명)은 S2.
package org.domi.furnace.compat.shimgen;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.domi.furnace.tools.extract.SigTable;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public final class LinkInfer {
    static final String SYNCHED = "net/minecraft/network/syncher/SynchedEntityData";
    static final String ACCESSOR_DESC = "Lnet/minecraft/network/syncher/EntityDataAccessor;";
    static final String CANDIDATES = "candidates.links";

    /** 명령 하나(라벨·줄 번호·프레임은 뺀다. 분기 대상은 라벨 번호로). */
    record Insn(int op, String owner, String name, String desc, Object cst, int label) {
        static Insn of(int op) { return new Insn(op, null, null, null, null, -1); }
    }

    /** 바닐라 몸통 하나: 명령들과 라벨 자리(라벨 번호 → 그 라벨 뒤 첫 명령 번호). */
    record Body(List<Insn> insns, Map<Integer, Integer> labels) {}

    /** 껍데기 멤버 하나. */
    record Shell(String owner, String name, String desc, int access) {
        String key() { return owner + "#" + name + desc; }
    }

    /** 후보 줄. */
    record Candidate(Shell m, String target, String check, String via, String kind) {
        String member() { return (Links.visible(m.access()) ? m.name() : Links.fingerprint(m.owner(), m.name())) + m.desc(); }

        String line() { return line("none"); }

        String line(String proof) { return "  " + member() + " -> " + target + " " + check + " via=" + via + " proof=" + proof; }
    }

    final FullShells g;
    final List<Shell> shells = new ArrayList<>();
    final Map<String, Body> bodies = new HashMap<>();
    /**
     * 값이 바뀔 때 엔티티가 반응하는 칸(선언 클래스 + " " + 이름): 바닐라·Paper 의 onSyncedDataUpdated(EntityDataAccessor) 몸통이 읽는 칸 필드 전부.
     * 그 칸에 쓰는 줄은 모양만으로는 같다고 할 수 없다(반응이 Furnace 쪽에도 있어야 한다) — 모양 증명에서 뺀다.
     */
    final Set<String> reactingSlots = new HashSet<>();
    /** Mojang 이름 Paper 서버 jar 의 몸통(T-262 와 같은 원칙: 바닐라와 Paper 가 같은 꼴일 때만 후보). null 이면 대조 안 함(시험용). */
    Map<String, Body> paperBodies;
    /** 분류 → {전체, 공개·보호}. */
    final Map<String, int[]> classes = new TreeMap<>();
    final List<Candidate> candidates = new ArrayList<>();
    /** 생성기 검사 전에 걸러진 후보(칸 값 종류가 멤버 형식과 다름 등) → 이유. */
    final Map<Candidate, String> typeRejected = new LinkedHashMap<>();

    LinkInfer(FullShells g) { this.g = g; }

    /**
     * args: FullShells 와 같은 18개(0~17) + [18] 바닐라 Mojang 이름 서버 jar(캐시 work/server-mojmap.jar)
     * + [19] Mojang 이름 Paper 서버 jar(캐시 work/paper-server-mojmap.jar, tools/compat/sig-tables.sh 가 둔다) + [20] 보고서 폴더
     * + [21](선택) 채택 모드: 연결 파일 폴더 — 모양 증명 줄을 &lt;영역&gt;.auto.links 로 다시 쓴다(adoptLinks).
     */
    public static void main(String[] args) throws IOException {
        String[] fullArgs = java.util.Arrays.copyOf(args, 18);
        Path vanillaJar = Path.of(args[18]);
        Path paperJar = Path.of(args[19]);
        Path out = Path.of(args[20]);
        Path adoptDir = args.length > 21 && !args[21].isBlank() ? Path.of(args[21]) : null;
        for (Path j : List.of(vanillaJar, paperJar)) {
            if (!Files.isRegularFile(j)) throw new IllegalStateException("server jar missing: " + j + " - run tools/compat/sig-tables.sh where the Paper repository is reachable");
        }
        // 채택 모드: 기계가 쓴 줄(*.auto.links)은 빼고 손 줄만으로 찾는다 — 그래야 지난번 채택 줄이 다시 후보로 나와 파일을 통째로 다시 쓸 수 있다
        String[] shellArgs = adoptDir == null ? fullArgs : withoutAutoLinks(fullArgs, out);
        FullShells g = FullShells.prepare(shellArgs);
        Map<String, byte[]> built = g.generate();
        g.mergeFromArgs(shellArgs, built);
        LinkInfer inf = new LinkInfer(g);
        inf.collectShells(built);
        try (ZipFile jar = new ZipFile(vanillaJar.toFile())) {
            inf.readBodies(jar, inf.bodies);
            inf.readReactions(jar);
        }
        inf.paperBodies = new HashMap<>();
        try (ZipFile jar = new ZipFile(paperJar.toFile())) {
            inf.readBodies(jar, inf.paperBodies);
            inf.readReactions(jar);
        }
        inf.classify();
        // 생성기로 검사: 어긴 줄은 빼고 다시(빠진 줄 때문에 다른 줄이 달라지지 않으므로 보통 한 번이면 된다)
        List<Candidate> ok = new ArrayList<>(inf.candidates);
        Map<Candidate, String> rejected = new LinkedHashMap<>(inf.typeRejected);
        ok.removeAll(inf.typeRejected.keySet());
        // 빈 결과가 나올 때까지(매번 하나 이상 빠지므로 끝난다) — 검사하지 않은 줄이 통과로 남지 않게
        while (!ok.isEmpty()) {
            Map<Candidate, String> bad = verify(shellArgs, ok);
            if (bad.isEmpty()) break;
            rejected.putAll(bad);
            ok.removeAll(bad.keySet());
        }
        List<String[]> areas = readAreas(shellArgs.length > 11 && !shellArgs[11].equals("-") ? Path.of(shellArgs[11]) : null);
        write(out, inf, ok, rejected, areas);
        try (ZipFile jar = new ZipFile(paperJar.toFile())) {   // 보고서만(채택 없음) — 정답기 순서를 정하려고 센다
            LinkG2Census g2 = new LinkG2Census(inf, jar);
            g2.run();
            g2.write(out);
        }
        // G4 서버 이식 짝(작업 목록, 연결 없음) — 서버 소스·클래스 폴더가 주어졌을 때만
        String g4Src = System.getProperty("furnace.g4.serverSrc"), g4Classes = System.getProperty("furnace.g4.serverClasses");
        if (g4Src != null && g4Classes != null) {
            try (ZipFile jar = new ZipFile(vanillaJar.toFile())) {
                LinkG4Ports g4 = new LinkG4Ports(inf, jar, Path.of(g4Src), Path.of(g4Classes), areas);
                g4.run();
                g4.write(out);
            }
        }
        if (adoptDir != null) {
            int n = adopt(adoptDir, ok, areas);
            // 손 줄 + 새 기계 줄 전부로 생성기 검사를 한 번 더(빌드와 같은 길)
            FullShells check = FullShells.prepare(fullArgs);
            Map<String, byte[]> all = check.generate();
            check.checkLinks(check.mergeFromArgs(fullArgs, all));
            System.out.println("[link-infer] adopted " + n + " lines (proof=shape) -> " + adoptDir);
        }
        System.out.println("[link-infer] " + inf.shells.size() + " shell methods, " + inf.bodies.size() + " with a vanilla body; "
                + inf.candidates.size() + " candidates, " + ok.size() + " pass the build checks, " + rejected.size() + " rejected -> " + out);
    }

    // ───────────────────────── 1) 껍데기 멤버 ─────────────────────────

    void collectShells(Map<String, byte[]> built) {
        for (Map.Entry<String, byte[]> e : new TreeMap<>(built).entrySet()) {
            String owner = e.getKey();
            new ClassReader(e.getValue()).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public AnnotationVisitor visitAnnotation(String d, boolean visible) {
                            if (d.equals(FullShells.SHELL_MEMBER)) shells.add(new Shell(owner, name, desc, paperAccess(owner, name, desc, access)));
                            return null;
                        }
                    };
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
    }

    /** 이름 규칙은 Paper 서명표의 접근 값으로 본다(껍데기가 바꾼 접근이 아니라). */
    private int paperAccess(String owner, String name, String desc, int fallback) {
        for (SigTable.Member m : g.table(owner).members(owner)) if (m.kind() == 'M' && m.name().equals(name) && m.desc().equals(desc)) return m.access();
        return fallback;
    }

    // ───────────────────────── 2) 바닐라 몸통 ─────────────────────────

    void readBodies(ZipFile jar, Map<String, Body> into) throws IOException {
        Map<String, Set<String>> want = new TreeMap<>();
        for (Shell s : shells) want.computeIfAbsent(s.owner(), k -> new HashSet<>()).add(s.name() + s.desc());
        for (Map.Entry<String, Set<String>> e : want.entrySet()) {
            ZipEntry ze = jar.getEntry(e.getKey() + ".class");
            if (ze == null) continue;   // Paper·CraftBukkit 클래스는 바닐라 jar 에 없다
            byte[] b;
            try (InputStream in = jar.getInputStream(ze)) {
                b = in.readAllBytes();
            }
            String owner = e.getKey();
            Set<String> keys = e.getValue();
            new ClassReader(b).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                    if (!keys.contains(name + desc) || (access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) return null;
                    return recorder(owner + "#" + name + desc, into);
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
    }

    static final String ON_SYNCED = "onSyncedDataUpdated";

    /** jar 전체의 onSyncedDataUpdated(EntityDataAccessor) 가 읽는 칸 필드를 reactingSlots 에 더한다(선언 클래스로 푼다). */
    void readReactions(ZipFile jar) throws IOException {
        for (ZipEntry ze : java.util.Collections.list(jar.entries())) {
            if (!ze.getName().startsWith("net/minecraft/") || !ze.getName().endsWith(".class")) continue;
            byte[] b;
            try (InputStream in = jar.getInputStream(ze)) {
                b = in.readAllBytes();
            }
            new ClassReader(b).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                    if (!name.equals(ON_SYNCED) || !desc.equals("(" + ACCESSOR_DESC + ")V")) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitFieldInsn(int op, String o, String n, String d) {
                            if (op != Opcodes.GETSTATIC || !d.equals(ACCESSOR_DESC)) return;
                            String[] decl = slotDecl(new Insn(op, o, n, d, null, -1));
                            reactingSlots.add(decl == null ? o + " " + n : decl[0] + " " + decl[1]);
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
    }

    boolean reacts(Insn slotGetstatic) {
        String[] d = slotDecl(slotGetstatic);
        return reactingSlots.contains(d == null ? slotGetstatic.owner() + " " + slotGetstatic.name() : d[0] + " " + d[1]);
    }

    private MethodVisitor recorder(String key, Map<String, Body> into) {
        List<Insn> insns = new ArrayList<>();
        Map<Label, Integer> ids = new HashMap<>();
        Map<Integer, Integer> at = new HashMap<>();
        return new MethodVisitor(Opcodes.ASM9) {
            int id(Label l) { return ids.computeIfAbsent(l, k -> ids.size()); }

            @Override public void visitLabel(Label l) { at.put(id(l), insns.size()); }
            @Override public void visitInsn(int op) { insns.add(Insn.of(op)); }
            @Override public void visitIntInsn(int op, int v) { insns.add(new Insn(op, null, null, null, v, -1)); }
            @Override public void visitVarInsn(int op, int v) { insns.add(new Insn(op, null, null, null, v, -1)); }
            @Override public void visitTypeInsn(int op, String t) { insns.add(new Insn(op, t, null, null, null, -1)); }
            @Override public void visitFieldInsn(int op, String o, String n, String d) { insns.add(new Insn(op, o, n, d, null, -1)); }
            @Override public void visitMethodInsn(int op, String o, String n, String d, boolean itf) { insns.add(new Insn(op, o, n, d, null, -1)); }
            @Override public void visitJumpInsn(int op, Label l) { insns.add(new Insn(op, null, null, null, null, id(l))); }
            @Override public void visitLdcInsn(Object v) { insns.add(new Insn(Opcodes.LDC, null, null, null, v, -1)); }
            @Override public void visitIincInsn(int v, int inc) { insns.add(new Insn(Opcodes.IINC, null, null, null, v, -1)); }
            @Override public void visitTableSwitchInsn(int a, int b, Label d, Label... ls) { insns.add(Insn.of(Opcodes.TABLESWITCH)); }
            @Override public void visitLookupSwitchInsn(Label d, int[] k, Label[] ls) { insns.add(Insn.of(Opcodes.LOOKUPSWITCH)); }
            @Override public void visitInvokeDynamicInsn(String n, String d, org.objectweb.asm.Handle h, Object... a) { insns.add(new Insn(Opcodes.INVOKEDYNAMIC, null, n, d, null, -1)); }
            @Override public void visitMultiANewArrayInsn(String d, int n) { insns.add(new Insn(Opcodes.MULTIANEWARRAY, null, null, d, n, -1)); }
            @Override public void visitTryCatchBlock(Label s, Label e, Label h, String t) { insns.add(Insn.of(-2)); }   // try 가 있으면 어느 틀에도 안 맞게

            @Override public void visitEnd() { into.put(key, new Body(List.copyOf(insns), Map.copyOf(at))); }
        };
    }

    // ───────────────────────── 3) 분류·후보 ─────────────────────────

    void classify() {
        for (Shell s : shells) {
            Body b = bodies.get(s.key());
            String kind;
            if (b == null) kind = s.name().equals("<init>") ? "no vanilla body: constructor" : "no vanilla body (Paper/CraftBukkit, abstract or not in vanilla)";
            else kind = shape(s, b);
            int[] c = classes.computeIfAbsent(kind, k -> new int[2]);
            c[0]++;
            if (Links.visible(s.access())) c[1]++;
        }
    }

    private String shape(Shell s, Body b) {
        List<Insn> in = b.insns();
        boolean isStatic = (s.access() & Opcodes.ACC_STATIC) != 0;
        Type ret = Type.getReturnType(s.desc());
        if (s.name().equals("<init>")) return "constructor";
        if (in.size() == 1 && in.get(0).op() == Opcodes.RETURN) return "G0 empty body";
        if (in.size() == 2 && isReturn(in.get(1)) && isConst(in.get(0))) return "G0 constant";
        if (in.size() == 2 && isReturn(in.get(1)) && in.get(0).op() == Opcodes.GETSTATIC) return "G0 static field";
        if (!isStatic && in.size() == 3 && isReturn(in.get(2)) && isAload0(in.get(0)) && in.get(1).op() == Opcodes.GETFIELD) return "field getter (T-262)";
        if (in.size() == 2 && isReturn(in.get(1)) && isLoad(in.get(0)) && !(isAload0(in.get(0)) && !isStatic)) return "G0 return argument";
        String[] ed;
        try {
            ed = entityData(s, b);
        } catch (IndexOutOfBoundsException e) {
            ed = null;   // 몸통이 틀보다 짧다
        }
        if (ed != null) {
            Candidate c = new Candidate(s, ed[0], ed[1], "G1", ed[2]);
            candidates.add(c);
            String bad = paperDiffers(s, ed, this::entityDataOrNull);
            if (bad == null) bad = slotTypeProblem(s, ed[2], in.get(dataRef(in, 0)));
            if (bad == null && ed[2].endsWith("-set") && reacts(in.get(dataRef(in, 0)))) bad = "entity data slot has a reaction (onSyncedDataUpdated) - a write needs a separate check";
            if (bad != null) typeRejected.put(c, bad);
            return "G1 " + ed[2];
        }
        String alias = alias(s, b);
        if (alias != null) {
            Candidate c = new Candidate(s, alias, "pure", "G3", "alias");
            candidates.add(c);
            String bad = paperDiffers(s, new String[] {alias}, (m, pb) -> {
                String a = alias(m, pb);
                return a == null ? null : new String[] {a};
            });
            if (bad != null) typeRejected.put(c, bad);
            return "G3 forward to a member of the same chain";
        }
        int n = in.size();
        return n <= 12 ? "other: short body (<=12)" : n <= 40 ? "other: middle body (13-40)" : "other: long body (>40)";
    }

    /** Paper 몸통이 바닐라 몸통과 같은 후보를 내는가 — 다르면 이유(Paper 가 바꾼 멤버는 Paper 쪽이 호환 면이다). */
    private String paperDiffers(Shell s, String[] vanilla, java.util.function.BiFunction<Shell, Body, String[]> classify) {
        if (paperBodies == null) return null;
        Body pb = paperBodies.get(s.key());
        if (pb == null) return "Paper server jar has no body for the member";
        String[] p = classify.apply(s, pb);
        return p != null && java.util.Arrays.equals(vanilla, p) ? null : "Paper body differs from the vanilla body";
    }

    private String[] entityDataOrNull(Shell s, Body b) {
        try {
            return entityData(s, b);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    // ── G1 엔티티 데이터 관용구 ──

    /** {대상, 검사, 종류} 또는 null. 받는 쪽 칸 읽기: aload0 + (getfield entityData | getEntityData()). */
    String[] entityData(Shell s, Body b) {
        if ((s.access() & Opcodes.ACC_STATIC) != 0) return null;
        List<Insn> in = b.insns();
        Type ret = Type.getReturnType(s.desc());
        Type[] args = Type.getArgumentTypes(s.desc());
        int i = dataRef(in, 0);
        if (i < 0 || i >= in.size() || in.get(i).op() != Opcodes.GETSTATIC || !ACCESSOR_DESC.equals(in.get(i).desc())) return null;
        String slot = slotValue(in.get(i));
        if (slot == null) return null;
        i++;
        // 읽기: get → [checkcast → [xxxValue]] → 반환 / 깃발: get → checkcast Byte → byteValue → 마스크 → iand → ifeq → 1 → goto → 0 → ireturn
        if (args.length == 0 && isInvoke(in, i, SYNCHED, "get", "(" + ACCESSOR_DESC + ")Ljava/lang/Object;")) {
            i++;
            if (ret.getSort() == Type.BOOLEAN && in.size() == i + 9 && isCast(in, i, "java/lang/Byte") && isInvoke(in, i + 1, "java/lang/Byte", "byteValue", "()B")) {
                Integer mask = intConst(in.get(i + 2));
                if (mask != null && in.get(i + 3).op() == Opcodes.IAND && in.get(i + 4).op() == Opcodes.IFEQ && intConst(in.get(i + 5)) != null && intConst(in.get(i + 5)) == 1
                        && in.get(i + 6).op() == Opcodes.GOTO && intConst(in.get(i + 7)) != null && intConst(in.get(i + 7)) == 0 && in.get(i + 8).op() == Opcodes.IRETURN
                        && b.labels().get(in.get(i + 4).label()) == i + 7 && b.labels().get(in.get(i + 6).label()) == i + 8) {
                    return new String[] {"@idiom(entity-data-flag-get, " + slot + ", " + mask + ")", "read=owner", "entity-data-flag-get"};
                }
                return null;
            }
            if (ret.getSort() == Type.OBJECT && ret.getDescriptor().equals("Ljava/lang/Object;") && in.size() == i + 1 && in.get(i).op() == Opcodes.ARETURN) {
                return new String[] {"@idiom(entity-data-get, " + slot + ")", "read=owner", "entity-data-get"};
            }
            if (ret.getSort() == Type.OBJECT || ret.getSort() == Type.ARRAY) {
                String cast = ret.getSort() == Type.ARRAY ? ret.getDescriptor() : ret.getInternalName();
                if (in.size() == i + 2 && isCast(in, i, cast) && in.get(i + 1).op() == Opcodes.ARETURN) {
                    return new String[] {"@idiom(entity-data-get, " + slot + ")", "read=owner", "entity-data-get"};
                }
                return null;
            }
            if (ret.getSort() != Type.VOID) {
                String box = Links.box(ret);
                if (in.size() == i + 3 && isCast(in, i, box) && isInvoke(in, i + 1, box, ret.getClassName() + "Value", "()" + ret.getDescriptor())
                        && in.get(i + 2).op() == ret.getOpcode(Opcodes.IRETURN)) {
                    return new String[] {"@idiom(entity-data-get, " + slot + ")", "read=owner", "entity-data-get"};
                }
            }
            return null;
        }
        // 쓰기: 인자 하나 → [박싱] → set → return
        if (args.length == 1 && ret.getSort() == Type.VOID) {
            if (i >= in.size() || !isLoadOf(in.get(i), args[0], 1)) return flagSet(s, b, slot);
            i++;
            if (args[0].getSort() != Type.OBJECT && args[0].getSort() != Type.ARRAY) {
                String box = Links.box(args[0]);
                if (!isInvoke(in, i, box, "valueOf", "(" + args[0].getDescriptor() + ")L" + box + ";")) return null;
                i++;
            }
            if (isInvoke(in, i, SYNCHED, "set", "(" + ACCESSOR_DESC + "Ljava/lang/Object;)V") && in.size() == i + 2 && in.get(i + 1).op() == Opcodes.RETURN) {
                return new String[] {"@idiom(entity-data-set, " + slot + ")", "write", "entity-data-set"};
            }
        }
        return null;
    }

    /**
     * 깃발 쓰기(Z)V: b = get(칸) 바이트; if (v) set(칸, (byte)(b | m)) else set(칸, (byte)(b & ~m)); return.
     * 명령 모양이 정확히 이 꼴이고 두 칸·두 마스크가 같을 때만.
     */
    private String[] flagSet(Shell s, Body b, String slot) {
        Type[] args = Type.getArgumentTypes(s.desc());
        if (args[0].getSort() != Type.BOOLEAN) return null;
        List<Insn> in = b.insns();
        int i = dataRef(in, 0) + 1;   // 칸 getstatic 다음
        if (!isInvoke(in, i, SYNCHED, "get", "(" + ACCESSOR_DESC + ")Ljava/lang/Object;") || !isCast(in, i + 1, "java/lang/Byte")
                || !isInvoke(in, i + 2, "java/lang/Byte", "byteValue", "()B") || in.get(i + 3).op() != Opcodes.ISTORE) return null;
        int local = (Integer) in.get(i + 3).cst();
        i += 4;
        if (!isLoadOf(in.get(i), Type.BOOLEAN_TYPE, 1) || in.get(i + 1).op() != Opcodes.IFEQ) return null;
        int elseLabel = in.get(i + 1).label();
        i += 2;
        Integer setMask = setBranch(in, i, slot, local, Opcodes.IOR);
        if (setMask == null) return null;
        i += setBranchLength(in, i);
        if (in.get(i).op() != Opcodes.GOTO) return null;
        int endLabel = in.get(i).label();
        i++;
        if (b.labels().get(elseLabel) == null || b.labels().get(elseLabel) != i) return null;
        Integer clearMask = setBranch(in, i, slot, local, Opcodes.IAND);
        if (clearMask == null || clearMask != ~setMask) return null;
        i += setBranchLength(in, i);
        if (b.labels().get(endLabel) == null || b.labels().get(endLabel) != i || in.size() != i + 1 || in.get(i).op() != Opcodes.RETURN) return null;
        return new String[] {"@idiom(entity-data-flag-set, " + slot + ", " + setMask + ")", "write", "entity-data-flag-set"};
    }

    /** aload0 · 칸참조 · getstatic 같은 칸 · iload b · 마스크 · op · i2b · Byte.valueOf · set — 마스크 또는 null. */
    private Integer setBranch(List<Insn> in, int i, String slot, int local, int op) {
        int j = dataRef(in, i);
        if (j < 0 || j + 7 > in.size() || in.get(j).op() != Opcodes.GETSTATIC || !slot.equals(slotValue(in.get(j)))) return null;
        if (in.get(j + 1).op() != Opcodes.ILOAD || (Integer) in.get(j + 1).cst() != local) return null;
        Integer mask = intConst(in.get(j + 2));
        if (mask == null || in.get(j + 3).op() != op || in.get(j + 4).op() != Opcodes.I2B
                || !isInvoke(in, j + 5, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;")
                || !isInvoke(in, j + 6, SYNCHED, "set", "(" + ACCESSOR_DESC + "Ljava/lang/Object;)V")) return null;
        return mask;
    }

    private int setBranchLength(List<Insn> in, int i) { return dataRef(in, i) - i + 7; }

    /** i 에서 받는 쪽 칸 묶음 참조(aload0 getfield …entityData | aload0 invokevirtual …getEntityData()). 다음 명령 번호, 아니면 -1. */
    private int dataRef(List<Insn> in, int i) {
        if (i + 1 >= in.size() || !isAload0(in.get(i))) return -1;
        Insn r = in.get(i + 1);
        String sd = "L" + SYNCHED + ";";
        if (r.op() == Opcodes.GETFIELD && r.name().equals("entityData") && sd.equals(r.desc())) return i + 2;
        if (r.op() == Opcodes.INVOKEVIRTUAL && r.name().equals("getEntityData") && ("()" + sd).equals(r.desc())) return i + 2;
        return -1;
    }

    /** 칸 정적 필드를 연결 줄의 값으로: 공개·보호면 a.b.C.F, 아니면 #지문(선언 클래스로). 서명표에 없으면 null. */
    String slotValue(Insn getstatic) {
        String[] d = slotDecl(getstatic);
        if (d == null) return null;
        return d[2].equals("v") ? d[0].replace('/', '.') + "." + d[1] : Links.fingerprint(d[0], d[1]);
    }

    /** {선언 클래스, 이름, v(공개·보호)|h}. 바닐라 명령의 소유자는 참조한 클래스 — 실제 선언 클래스는 사슬에서 찾는다. */
    private String[] slotDecl(Insn getstatic) {
        String name = getstatic.name();
        for (String c = getstatic.owner(); c != null; ) {
            for (SigTable.Member m : g.table(c).members(c)) {
                if (m.kind() == 'F' && m.name().equals(name) && (m.access() & Opcodes.ACC_STATIC) != 0) {
                    return new String[] {c, name, Links.visible(m.access()) ? "v" : "h"};
                }
            }
            SigTable.Cls d = g.decl(c);
            c = d == null ? null : d.superName();
        }
        return null;
    }

    static final String SERIALIZERS = "net/minecraft/network/syncher/EntityDataSerializers";

    /**
     * 칸 값 종류 검사(S2): 칸 번호표의 값 종류(A 줄) → Paper 서명표의 EntityDataSerializers 필드 제네릭 인자(값 형식)가
     * 멤버의 반환(읽기)·인자(쓰기) 형식과 같아야 한다. 깃발은 BYTE 칸만. 기본형은 박싱 형식으로, Object 반환은 통과.
     * 모양이 맞아도 다른 칸을 가리키는 몸통(사람이 고친 바닐라 등)을 잡는 값싼 판별 검사다. 문제가 없으면 null.
     */
    String slotTypeProblem(Shell s, String kind, Insn getstatic) {
        String[] d = slotDecl(getstatic);
        String serializer = d == null ? null : g.dataSlots.get(d[0].replace('/', '.') + " " + Links.fingerprint(d[0], d[1]).substring(1));
        if (serializer == null) return "entity data slot missing from the id table";
        String value = serializerValue(serializer);
        if (value == null) return "entity data serializer has no value type";
        if (kind.startsWith("entity-data-flag")) return value.equals("java/lang/Byte") ? null : "entity data flag on a non-byte slot";
        Type t = kind.equals("entity-data-set") ? Type.getArgumentTypes(s.desc())[0] : Type.getReturnType(s.desc());
        if (kind.equals("entity-data-get") && t.getDescriptor().equals("Ljava/lang/Object;")) return null;
        String member = t.getSort() == Type.OBJECT ? t.getInternalName() : t.getSort() == Type.ARRAY ? t.getDescriptor() : Links.box(t);
        if (!member.equals(value)) return "entity data slot value type differs from the member type";
        // 제네릭 값(Optional<글> 과 Optional<UUID> 처럼 지운 꼴이 같은 것): 멤버 서명의 제네릭 형식까지 같아야 한다
        String full = serializerValueSignature(serializer);
        if (full != null && full.indexOf('<') >= 0) {
            String mine = memberTypeSignature(s, kind.equals("entity-data-set"));
            if (!full.equals(mine)) return "entity data slot generic value type differs from the member type";
        }
        return null;
    }

    /** 값 종류의 제네릭 값 형식 서명 전체(예: Ljava/util/Optional&lt;Ljava/util/UUID;&gt;;). */
    String serializerValueSignature(String serializer) {
        for (SigTable.Member m : g.table(SERIALIZERS).members(SERIALIZERS)) {
            if (m.kind() != 'F' || !m.name().equals(serializer) || m.signature() == null) continue;
            int lt = m.signature().indexOf('<');
            if (lt < 0) return null;
            int end = typeEnd(m.signature(), lt + 1);
            return end < 0 ? null : m.signature().substring(lt + 1, end);
        }
        return null;
    }

    /** Paper 서명표의 멤버 제네릭 서명에서 반환(읽기) 또는 첫 인자(쓰기) 형식. 서명이 없으면 서술자 형식. */
    String memberTypeSignature(Shell s, boolean firstArg) {
        String sig = null;
        for (SigTable.Member m : g.table(s.owner()).members(s.owner())) {
            if (m.kind() == 'M' && m.name().equals(s.name()) && m.desc().equals(s.desc())) sig = m.signature();
        }
        if (sig == null) sig = s.desc();
        int open = sig.indexOf('(');
        if (open < 0) return null;
        if (firstArg) {
            int end = typeEnd(sig, open + 1);
            return end < 0 ? null : sig.substring(open + 1, end);
        }
        int close = sig.indexOf(')', open);
        int end = close < 0 ? -1 : typeEnd(sig, close + 1);
        return end < 0 ? null : sig.substring(close + 1, end);
    }

    /** 서명 sig 의 i 에서 시작하는 형식 하나의 끝(다음 글자 번호). 기본형·배열·L…&lt;…&gt;;·T…; 를 안다. 모르면 -1. */
    static int typeEnd(String sig, int i) {
        if (i >= sig.length()) return -1;
        char c = sig.charAt(i);
        if ("ZBCSIJFDV".indexOf(c) >= 0) return i + 1;
        if (c == '[') return typeEnd(sig, i + 1);
        if (c == 'T') {
            int semi = sig.indexOf(';', i);
            return semi < 0 ? -1 : semi + 1;
        }
        if (c == '*') return i + 1;
        if (c == '+' || c == '-') return typeEnd(sig, i + 1);
        if (c != 'L') return -1;
        int depth = 0;
        for (int j = i + 1; j < sig.length(); j++) {
            char d = sig.charAt(j);
            if (d == '<') depth++;
            else if (d == '>') depth--;
            else if (d == ';' && depth == 0) return j + 1;
        }
        return -1;
    }

    /** 값 종류 이름 → 값 형식(지운 내부 이름). EntityDataSerializers 필드 서명 EntityDataSerializer<값> 에서. */
    String serializerValue(String serializer) {
        for (SigTable.Member m : g.table(SERIALIZERS).members(SERIALIZERS)) {
            if (m.kind() != 'F' || !m.name().equals(serializer) || m.signature() == null) continue;
            String sig = m.signature();
            int lt = sig.indexOf('<');
            if (lt < 0 || lt + 1 >= sig.length()) return null;
            char c = sig.charAt(lt + 1);
            if (c == '[') {   // 배열 값: 끝 '>' 앞까지
                return sig.substring(lt + 1, sig.lastIndexOf('>'));
            }
            if (c != 'L') return null;
            int end = lt + 2;
            while (end < sig.length() && sig.charAt(end) != '<' && sig.charAt(end) != ';') end++;
            return sig.substring(lt + 2, end);
        }
        return null;
    }

    // ── G3 별칭 ──

    /** 인자를 그대로 넘겨 같은 사슬의 다른 멤버를 부르고 그 값을 그대로 돌려주는 몸통 → this.대상(공개·보호 평문, 아니면 #지문). */
    String alias(Shell s, Body b) {
        List<Insn> in = b.insns();
        boolean isStatic = (s.access() & Opcodes.ACC_STATIC) != 0;
        Type[] args = Type.getArgumentTypes(s.desc());
        Type ret = Type.getReturnType(s.desc());
        int i = 0;
        if (!isStatic) {
            if (in.isEmpty() || !isAload0(in.get(0))) return null;
            i = 1;
        }
        int slot = isStatic ? 0 : 1;
        for (Type a : args) {
            if (i >= in.size() || !isLoadOf(in.get(i), a, slot)) return null;
            slot += a.getSize();
            i++;
        }
        if (i >= in.size()) return null;
        Insn call = in.get(i);
        boolean ok = isStatic ? call.op() == Opcodes.INVOKESTATIC && call.owner().equals(s.owner())
                : (call.op() == Opcodes.INVOKEVIRTUAL || call.op() == Opcodes.INVOKEINTERFACE) && inChain(s.owner(), call.owner());
        if (!ok || !java.util.Arrays.equals(Type.getArgumentTypes(call.desc()), args)) return null;
        if (call.name().equals(s.name()) && call.desc().equals(s.desc())) return null;
        i++;
        Type cret = Type.getReturnType(call.desc());
        if (ret.getSort() == Type.VOID) {
            if (cret.getSort() != Type.VOID || in.size() != i + 1 || in.get(i).op() != Opcodes.RETURN) return null;
        } else {
            if (!cret.equals(ret)) {
                // 좁히는 checkcast 는 별칭이 아니다(반환형이 같아야 한다 — 생성기는 대입 가능만 받는다)
                return null;
            }
            if (in.size() != i + 1 || in.get(i).op() != ret.getOpcode(Opcodes.IRETURN)) return null;
        }
        // 대상 이름: 선언 클래스의 접근으로
        for (String c : chain(s.owner())) {
            for (SigTable.Member m : g.table(c).members(c)) {
                if (m.kind() == 'M' && m.name().equals(call.name()) && m.desc().equals(call.desc())) {
                    return "this." + (Links.visible(m.access()) ? m.name() : Links.fingerprint(c, m.name()));
                }
            }
        }
        return null;
    }

    private boolean inChain(String cls, String other) { return chain(cls).contains(other); }

    private List<String> chain(String cls) {
        List<String> out = new ArrayList<>();
        java.util.ArrayDeque<String> todo = new java.util.ArrayDeque<>(List.of(cls));
        while (!todo.isEmpty()) {
            String c = todo.poll();
            if (out.contains(c)) continue;
            out.add(c);
            SigTable.Cls d = g.decl(c);
            if (d == null) continue;
            if (d.superName() != null) todo.add(d.superName());
            todo.addAll(d.interfaces());
        }
        return out;
    }

    // ── 명령 도움 ──

    static boolean isAload0(Insn x) { return x.op() == Opcodes.ALOAD && x.cst() instanceof Integer v && v == 0; }

    static boolean isLoad(Insn x) { return x.op() >= Opcodes.ILOAD && x.op() <= Opcodes.ALOAD; }

    static boolean isLoadOf(Insn x, Type t, int slot) { return x.op() == t.getOpcode(Opcodes.ILOAD) && x.cst() instanceof Integer v && v == slot; }

    static boolean isReturn(Insn x) { return x.op() >= Opcodes.IRETURN && x.op() <= Opcodes.ARETURN; }

    static boolean isConst(Insn x) {
        int op = x.op();
        return op >= Opcodes.ACONST_NULL && op <= Opcodes.DCONST_1 || op == Opcodes.BIPUSH || op == Opcodes.SIPUSH || op == Opcodes.LDC;
    }

    static Integer intConst(Insn x) {
        int op = x.op();
        if (op >= Opcodes.ICONST_M1 && op <= Opcodes.ICONST_5) return op - Opcodes.ICONST_0;
        if ((op == Opcodes.BIPUSH || op == Opcodes.SIPUSH) && x.cst() instanceof Integer v) return v;
        if (op == Opcodes.LDC && x.cst() instanceof Integer v) return v;
        return null;
    }

    static boolean isInvoke(List<Insn> in, int i, String owner, String name, String desc) {
        if (i < 0 || i >= in.size()) return false;
        Insn x = in.get(i);
        return (x.op() == Opcodes.INVOKEVIRTUAL || x.op() == Opcodes.INVOKESTATIC || x.op() == Opcodes.INVOKEINTERFACE)
                && owner.equals(x.owner()) && name.equals(x.name()) && desc.equals(x.desc());
    }

    static boolean isCast(List<Insn> in, int i, String type) { return i < in.size() && in.get(i).op() == Opcodes.CHECKCAST && type.equals(in.get(i).owner()); }

    // ───────────────────────── 4) 생성기로 검사 ─────────────────────────

    /** 후보 줄을 연결 파일 하나로 만들어 생성기를 돌리고, 문제가 난 후보와 그 이유. */
    // ───────────────────────── 채택(S2) ─────────────────────────

    static final String AUTO = ".auto.links";

    /** 모양 증명으로 채택하는 후보: G1 엔티티 데이터 관용구(모양이 바닐라와 정확히 같고 칸 값 종류가 맞고 빌드 검사를 통과한 것). */
    /**
     * 모양 증명으로 채택하는 갈래: G1 엔티티 데이터 관용구, G3 별칭(바닐라·Paper 몸통이 정확히 "인자 그대로 → 같은 사슬 멤버 가상 호출 → 그대로 반환",
     * 대상은 빌드 검사가 손 shim·JDK·T-262 채움으로 확인 — 결정 2026-11-27-t263-s3-alias-proof).
     */
    static boolean adoptable(Candidate c) {
        return c.via().equals("G1") && c.kind().startsWith("entity-data-") || c.via().equals("G3") && c.kind().equals("alias");
    }

    /** 영역마다 &lt;영역&gt;.auto.links 를 통째로 다시 쓰고(줄이 없으면 지운다) 채택 줄 수를 돌려준다. 손 줄 파일은 건드리지 않는다. */
    static int adopt(Path dir, List<Candidate> ok, List<String[]> areas) throws IOException {
        Files.createDirectories(dir);
        Map<String, List<Candidate>> byArea = byArea(ok.stream().filter(LinkInfer::adoptable).toList(), areas);
        try (var old = Files.list(dir)) {
            for (Path p : old.filter(x -> x.getFileName().toString().endsWith(AUTO)).toList()) {
                String area = p.getFileName().toString();
                if (!byArea.containsKey(area.substring(0, area.length() - AUTO.length()))) Files.delete(p);
            }
        }
        int n = 0;
        for (Map.Entry<String, List<Candidate>> e : byArea.entrySet()) {
            String head = "# T-263 machine-adopted links (adoptLinks): exact vanilla and Paper body shape (reviewed idiom + slot value type, or a plain forward to a live member). Do not edit -\n"
                    + "# rerun adoptLinks. Hand lines go in " + e.getKey() + ".links; a member with a hand line is never adopted here.\n";
            Files.writeString(dir.resolve(e.getKey() + AUTO), areaFile(e.getKey(), e.getValue(), head, "shape"), StandardCharsets.UTF_8);
            n += e.getValue().size();
        }
        return n;
    }

    /** 인자 16(연결 폴더)을 손 줄 파일만 복사한 임시 폴더로 바꾼 인자. */
    static String[] withoutAutoLinks(String[] args, Path work) throws IOException {
        String[] a = args.clone();
        if (args[16].isBlank() || args[16].equals("-") || !Files.isDirectory(Path.of(args[16]))) return a;
        Path tmp = work.resolveSibling(work.getFileName() + "-hand-links");   // 보고서 폴더는 write 가 지우므로 옆에
        if (Files.isDirectory(tmp)) {
            try (var old = Files.list(tmp)) {
                for (Path p : old.toList()) Files.delete(p);
            }
        }
        Files.createDirectories(tmp);
        try (var s = Files.list(Path.of(args[16]))) {
            for (Path p : s.filter(x -> x.toString().endsWith(".links") && !x.toString().endsWith(AUTO)).toList()) Files.copy(p, tmp.resolve(p.getFileName()));
        }
        a[16] = tmp.toString();
        return a;
    }

    static Map<Candidate, String> verify(String[] shellArgs, List<Candidate> cands) throws IOException {
        List<String> text = new ArrayList<>();
        List<List<Candidate>> byLine = new ArrayList<>();   // 줄 번호(1부터) - 1 → 그 줄이 맡은 후보(클래스 줄은 그 클래스의 후보 전부, 머리는 빈 목록)
        text.add(Links.MAGIC + " " + Links.FORMAT + " candidates area=0");
        byLine.add(List.of());
        String cls = null;
        List<Candidate> ofClass = null;
        for (Candidate c : sorted(cands)) {
            if (!c.m().owner().equals(cls)) {
                cls = c.m().owner();
                text.add("class " + cls.replace('/', '.'));
                ofClass = new ArrayList<>();
                byLine.add(ofClass);
            }
            text.add(c.line());
            byLine.add(List.of(c));
            ofClass.add(c);
        }
        FullShells g = FullShells.prepare(shellArgs);
        g.links.parse(CANDIDATES, text);
        Map<String, byte[]> built = g.generate();
        ShellMerge merge = g.mergeFromArgs(shellArgs, built);
        List<String> problems = new ArrayList<>(g.links.problems);
        problems.addAll(g.links.unconsumed(ln -> "already has behaviour or not generated"));
        Map<Candidate, String> bad = new LinkedHashMap<>();
        List<String> other = new ArrayList<>();
        for (String p : problems) {
            if (!p.startsWith(CANDIDATES + ":")) {
                other.add(p);
                continue;
            }
            int a = CANDIDATES.length() + 1, z = p.indexOf(':', a);
            int line = Integer.parseInt(p.substring(a, z));
            List<Candidate> cs = line - 1 < byLine.size() ? byLine.get(line - 1) : List.of();
            // 어느 후보에도 대응하지 않는 문제(머리 등)는 도구 잘못 — 통과로 두지 않고 멈춘다
            if (cs.isEmpty()) throw new IllegalStateException("a candidates-file problem matches no candidate: " + p);
            for (Candidate c : cs) bad.putIfAbsent(c, p.substring(z + 1).trim());
        }
        if (!other.isEmpty()) throw new IllegalStateException("the committed links have problems - fix them first:\n" + String.join("\n", other));
        if (merge == null) System.out.println("[link-infer] note: merging is off - members of hand shims are not checked");
        return bad;
    }

    static List<Candidate> sorted(List<Candidate> cs) {
        List<Candidate> out = new ArrayList<>(cs);
        out.sort(java.util.Comparator.comparing((Candidate c) -> c.m().owner()).thenComparing(c -> c.m().name() + c.m().desc()));
        return out;
    }

    // ───────────────────────── 출력 ─────────────────────────

    static List<String[]> readAreas(Path areasPath) throws IOException {
        List<String[]> areas = new ArrayList<>();
        if (areasPath != null) {
            for (String l : Files.readAllLines(areasPath, StandardCharsets.UTF_8)) {
                if (l.isBlank() || l.startsWith("#")) continue;
                areas.add(l.split("\t", -1));
            }
        }
        return areas;
    }

    static Map<String, List<Candidate>> byArea(List<Candidate> cs, List<String[]> areas) {
        Map<String, List<Candidate>> out = new TreeMap<>();
        for (Candidate c : sorted(cs)) out.computeIfAbsent(area(areas, c.m().owner()), k -> new ArrayList<>()).add(c);
        return out;
    }

    /** 영역 연결 파일 글: 머리 + 주석 + 클래스별 줄(정렬된 후보). */
    static String areaFile(String area, List<Candidate> cs, String comment, String proof) {
        StringBuilder sb = new StringBuilder(Links.MAGIC + " " + Links.FORMAT + " 1.21.1 area=" + area.substring(0, area.indexOf('-')) + "\n");
        sb.append(comment);
        String cls = null;
        for (Candidate c : cs) {
            if (!c.m().owner().equals(cls)) {
                cls = c.m().owner();
                sb.append("class ").append(cls.replace('/', '.')).append('\n');
            }
            sb.append(c.line(proof)).append('\n');
        }
        return sb.toString();
    }

    static void write(Path out, LinkInfer inf, List<Candidate> ok, Map<Candidate, String> rejected, List<String[]> areas) throws IOException {
        FullShells.deleteTree(out);
        Files.createDirectories(out.resolve("candidates"));
        for (Map.Entry<String, List<Candidate>> e : byArea(ok, areas).entrySet()) {
            String comment = "# T-263 inferLinks candidates (not adopted - S2 proves and adopts). Names inside: build/ only, never commit this file as is.\n";
            Files.writeString(out.resolve("candidates/" + e.getKey() + ".links"), areaFile(e.getKey(), e.getValue(), comment, "none"), StandardCharsets.UTF_8);
        }
        StringBuilder rj = new StringBuilder("member\ttarget\treason\n");
        for (Map.Entry<Candidate, String> e : rejected.entrySet()) {
            rj.append(e.getKey().m().key()).append('\t').append(e.getKey().target()).append('\t').append(e.getValue()).append('\n');
        }
        Files.writeString(out.resolve("rejected.tsv"), rj.toString(), StandardCharsets.UTF_8);
        Files.writeString(out.resolve("summary.md"), summary(inf, ok, rejected, areas), StandardCharsets.UTF_8);
    }

    /** 이유 글에서 이름(클래스·멤버·지문)이 나오기 전까지만 — 요약에 이름을 남기지 않는다. */
    static String reasonKind(String r) {
        int cut = r.length();
        for (String stop : new String[] {":", " net.", " org.", " com.", " io.", " #", " this.", " @", " java."}) {
            int i = r.indexOf(stop);
            if (i >= 0 && i < cut) cut = i;
        }
        return r.substring(0, cut).trim();
    }

    static String area(List<String[]> areas, String cls) {
        String best = "0-other";
        int len = -1;
        for (String[] a : areas) {
            for (String p : a[2].split(",")) if (cls.startsWith(p) && p.length() > len) { len = p.length(); best = a[0] + "-" + a[1]; }
        }
        return best;
    }

    /** 숫자만(이름 없음) — 문서·채팅에 옮겨도 된다. */
    static String summary(LinkInfer inf, List<Candidate> ok, Map<Candidate, String> rejected, List<String[]> areas) {
        StringBuilder sb = new StringBuilder("# T-263 inferLinks summary (numbers only)\n\n");
        sb.append("shell methods: ").append(inf.shells.size()).append(", with a vanilla body: ").append(inf.bodies.size()).append("\n\n");
        sb.append("| shape | all | public/protected |\n|---|---:|---:|\n");
        for (Map.Entry<String, int[]> e : inf.classes.entrySet()) sb.append("| ").append(e.getKey()).append(" | ").append(e.getValue()[0]).append(" | ").append(e.getValue()[1]).append(" |\n");
        Map<String, int[]> kinds = new TreeMap<>();
        for (Candidate c : inf.candidates) kinds.computeIfAbsent(c.kind(), k -> new int[2])[0]++;
        for (Candidate c : ok) kinds.get(c.kind())[1]++;
        sb.append("\n| candidate kind | found | pass the build checks |\n|---|---:|---:|\n");
        for (Map.Entry<String, int[]> e : kinds.entrySet()) sb.append("| ").append(e.getKey()).append(" | ").append(e.getValue()[0]).append(" | ").append(e.getValue()[1]).append(" |\n");
        Map<String, Integer> byArea = new TreeMap<>();
        for (Candidate c : ok) byArea.merge(area(areas, c.m().owner()), 1, Integer::sum);
        sb.append("\n| area | candidates passing |\n|---|---:|\n");
        for (Map.Entry<String, Integer> e : byArea.entrySet()) sb.append("| ").append(e.getKey()).append(" | ").append(e.getValue()).append(" |\n");
        Map<String, Integer> reasons = new TreeMap<>();
        for (String r : rejected.values()) reasons.merge(reasonKind(r), 1, Integer::sum);
        sb.append("\n| rejected because | count |\n|---|---:|\n");
        for (Map.Entry<String, Integer> e : reasons.entrySet()) sb.append("| ").append(e.getKey().replace("|", "/")).append(" | ").append(e.getValue()).append(" |\n");
        return sb.toString();
    }
}
