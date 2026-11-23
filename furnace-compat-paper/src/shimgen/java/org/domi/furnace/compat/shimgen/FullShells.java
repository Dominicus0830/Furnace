// T-260: 전체 껍데기 생성기. Paper 1.21.1 의 내부 클래스(net.minecraft·CraftBukkit 7,139개 + Paper 서버 전용 내부 클래스) 가운데
// 호환 계층에 아직 없는 것 전부를 "모양만" 바이트코드로 만든다. 입력은 개발자·CI 캐시의 서명표(커밋·배포 금지)와 이미 컴파일된 호환 계층 클래스다.
//
// 만드는 것(클래스마다, 서명표 그대로)
//  - 선언: 접근·final·abstract·종류(클래스·인터페이스·열거형·레코드·애노테이션)·상위·인터페이스·제네릭 서명·안쪽 클래스 관계.
//  - 멤버 전부(private 포함): 추상 메서드는 추상 그대로, 나머지 메서드·생성자는 던지는 몸통(NmsShell.unsupported — 계측 + UnsupportedNmsCall).
//    상수 필드(ConstantValue)는 값 그대로, 열거형 상수는 진짜 상수(이름·서수·values()·valueOf 는 바닐라와 같다), 나머지 필드는 선언만.
//  - 동작이 없는 멤버에는 보이지 않는 표지(@NmsShellMember)를 단다 → NmsSymbols 가 "없음"으로 보고 플러그인 호출 자리에 덫을 건다(기존 방침).
// 만들지 않는 것: 호환 계층에 이미 있는 클래스(손 shim·명세 생성·패킷 생성), 상위 형식이 없거나 final 인 손 shim 위에 서야 하는 클래스(보고서에 이유).
// 바닐라·Paper 의 몸통은 읽지도 옮기지도 않는다(서명표에 몸통이 없다). 결과물은 커밋하지 않는다.
package org.domi.furnace.compat.shimgen;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.domi.furnace.tools.extract.SigTable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public final class FullShells {
    /** 동작 없는 멤버 표지(보이지 않는 애노테이션 — 리플렉션의 getAnnotations 에는 안 나온다). */
    public static final String SHELL_MEMBER = "Lorg/domi/furnace/compat/nms/NmsShellMember;";
    static final String SHELL_HELPER = "org/domi/furnace/compat/nms/NmsShell";
    /** 껍데기를 만드는 이름공간. 7,139 = 앞 둘(Paper 감사 기준, 2026-11-17). */
    static final List<String> NMS_PREFIXES = List.of("net/minecraft/", "org/bukkit/craftbukkit/");
    /** Paper 서버 전용 내부 클래스(paper-api 에 없는 것만). */
    static final List<String> PAPER_INTERNAL_PREFIXES = List.of("io/papermc/", "com/destroystokyo/");
    /** Paper 내부 패치 인터페이스(moonrise 청크·추적) — 플러그인이 쓰는 형식이 아니고 Furnace 에 없다. 상속 목록에서 뺀다(ShimLock 과 같은 규칙). */
    static final String DROPPED_SUPER_PREFIX = "ca/spottedleaf/";

    /** 범위 종류(보고용). API = paper-api 의 클래스 가운데 호환 계층이 아직 재구현하지 않은 것(CraftBukkit 이 그것을 구현하므로 함께 필요하다). */
    enum Scope { NMS, MOJANG, PAPER_INTERNAL, API }

    final SigTable paper;
    final SigTable vanilla;
    final SigTable api;
    final Set<String> apiClasses;
    final Existing existing;

    /** 범위 안 클래스 → 종류. */
    final Map<String, Scope> scope = new TreeMap<>();
    /** 만들 클래스. */
    final Set<String> shells = new TreeSet<>();
    /** 못 만든 클래스 → 이유. */
    final Map<String, String> skipped = new TreeMap<>();
    /** 만들었지만 모양을 조금 바꾼 것(이유별 목록). */
    final Map<String, List<String>> deviations = new TreeMap<>();
    int shellMembers;
    int liveMembers;
    /** NMS 밖 범위에서 값 없는 필드가 있어 클래스 초기화가 던지는 껍데기 수. */
    int initTrapped;

    FullShells(SigTable paper, SigTable vanilla, SigTable api, Existing existing) {
        this.paper = paper;
        this.vanilla = vanilla;
        this.api = api;
        this.apiClasses = api.classes().keySet();
        this.existing = existing;
    }

    /**
     * args: 출력 클래스 폴더, 보고서 파일, 지문 파일(커밋된 것), 지문 쓰기(true/false), 이미 있는 클래스 경로(경로 구분자로 여럿 — 폴더·jar),
     * Paper 서버 서명표, 바닐라 서명표, paper-api 서명표, 바닐라 라이브러리 jar 폴더(vanilla-extract 캐시의 work/libs — DFU·brigadier·Guava·netty 등,
     * Paper 처럼 서버 라이브러리로 실행 때 있다), [선택] 합칠 클래스 폴더(호환 jar 자신의 클래스 폴더, 경로 구분자로 여럿 — T-261: 그 안의 범위 클래스에
     * 빠진 멤버를 껍데기 멤버로 덧붙여 같은 이름으로 함께 낸다).
     */
    public static void main(String[] args) throws IOException {
        Path out = Path.of(args[0]);
        Path report = Path.of(args[1]);
        Path fp = Path.of(args[2]);
        boolean writeFp = Boolean.parseBoolean(args[3]);
        List<Path> existingRoots = new ArrayList<>();
        for (String p : args[4].split(java.io.File.pathSeparator)) if (!p.isBlank()) existingRoots.add(Path.of(p));
        Path paperTable = Path.of(args[5]);
        Path vanillaTable = Path.of(args[6]);
        Path apiTable = Path.of(args[7]);
        Path libsDir = Path.of(args[8]);
        if (!Files.isDirectory(libsDir)) throw new IllegalStateException("vanilla library folder missing: " + libsDir + " - run tools/compat/sig-tables.sh");
        existingRoots.add(libsDir);
        for (Path t : List.of(paperTable, vanillaTable, apiTable)) {
            if (!Files.isRegularFile(t)) {
                throw new IllegalStateException("signature table missing: " + t + " - run tools/compat/sig-tables.sh (needs Mojang and Paper hosts),"
                        + " or build without the full shells with -PnoFullShells=true");
            }
        }
        String fingerprint = fingerprint(paperTable, vanillaTable, apiTable);
        if (writeFp) {
            Files.writeString(fp, fingerprint, StandardCharsets.UTF_8);
            System.out.println("[full-shells] wrote " + fp);
        } else {
            String want = Files.isRegularFile(fp) ? Files.readString(fp, StandardCharsets.UTF_8).replace("\r\n", "\n") : "";
            if (!want.equals(fingerprint)) {
                throw new IllegalStateException("signature tables differ from the committed fingerprint " + fp + ":\n--- committed\n" + want + "--- now\n" + fingerprint
                        + "If the tables were rebuilt on purpose (new format or version), rerun with -PfullShellsFpWrite=true and commit the file.");
            }
        }
        SigTable paper = SigTable.read(paperTable);
        SigTable vanilla = SigTable.read(vanillaTable);
        FullShells g = new FullShells(paper, vanilla, SigTable.read(apiTable), new Existing(existingRoots));
        Map<String, byte[]> classes = g.generate();
        ShellMerge merge = null;
        if (args.length > 9 && !args[9].isBlank()) {
            Set<String> overlay = new HashSet<>();
            for (String p : args[9].split(java.io.File.pathSeparator)) if (!p.isBlank()) overlay.addAll(Existing.classesIn(Path.of(p)));
            merge = new ShellMerge(g, overlay);
            classes.putAll(merge.merge());
        }
        deleteTree(out);
        for (Map.Entry<String, byte[]> e : classes.entrySet()) {
            Path p = out.resolve(e.getKey() + ".class");
            Files.createDirectories(p.getParent());
            Files.write(p, e.getValue());
        }
        if (merge != null) {
            write(out.resolve(ShellMerge.INSTANCE_FIELDS), String.join("\n", merge.instanceFields) + "\n");
            List<String> merged = new ArrayList<>();
            for (String n : classes.keySet()) if (!g.shells.contains(n)) merged.add(n);
            write(out.resolve(ShellMerge.MERGED), String.join("\n", merged) + "\n");
        }
        Files.createDirectories(report.toAbsolutePath().getParent());
        StringBuilder rep = new StringBuilder(g.report());
        if (merge != null) merge.report(rep);
        Files.writeString(report, rep.toString(), StandardCharsets.UTF_8);
        System.out.println("[full-shells] " + g.summary());
        if (merge != null) System.out.println("[full-shells] " + merge.summary());
    }

    private static void write(Path p, String text) throws IOException {
        Files.createDirectories(p.getParent());
        Files.writeString(p, text, StandardCharsets.UTF_8);
    }

    /** 지문: 서명표 두 개의 sha256(이름 원문 없이 커밋한다). 표가 바뀌면 껍데기의 바탕이 바뀐 것이다. */
    static String fingerprint(Path paperTable, Path vanillaTable, Path apiTable) throws IOException {
        return "# T-260 full shells: sha256 of the signature tables the shells are built from (no names - the tables are never committed)\n"
                + "paper-server " + sha256(paperTable) + "\n"
                + "vanilla " + sha256(vanillaTable) + "\n"
                + "paper-api " + sha256(apiTable) + "\n";
    }

    static String sha256(Path p) throws IOException {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            // gzip 은 시각 0·같은 입력 같은 바이트(SigTable.write)지만, 압축기 차이를 피하려고 풀린 글로 잰다
            try (InputStream in = new java.util.zip.GZIPInputStream(Files.newInputStream(p))) {
                byte[] buf = new byte[1 << 16];
                for (int n; (n = in.read(buf)) > 0; ) d.update(buf, 0, n);
            }
            return HexFormat.of().formatHex(d.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ───────────────────────── 범위 ─────────────────────────

    /** 범위를 정하고 만들 클래스를 고른다. */
    void select() {
        for (String n : paper.classes().keySet()) {
            Scope s = null;
            if (startsWithAny(n, NMS_PREFIXES)) s = Scope.NMS;
            else if (n.startsWith("com/mojang/") && vanilla.classes().containsKey(n)) s = Scope.MOJANG;   // 바닐라 jar 의 com/mojang(수학 등) — 라이브러리는 뺀다
            else if (startsWithAny(n, PAPER_INTERNAL_PREFIXES) && !apiClasses.contains(n)) s = Scope.PAPER_INTERNAL;
            if (s != null) scope.put(n, s);
        }
        for (String n : api.classes().keySet()) scope.putIfAbsent(n, Scope.API);
        for (String n : scope.keySet()) if (!existing.has(n)) shells.add(n);
        // 상위 형식 검사 — 고칠 수 없는 것은 빼고(그 하위도), 이유를 남긴다
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String n : List.copyOf(shells)) {
                String why = blocker(n);
                if (why != null) {
                    shells.remove(n);
                    skipped.put(n, why);
                    changed = true;
                }
            }
        }
    }

    /** 이 클래스의 선언이 있는 표(API 범위는 paper-api, 나머지는 Paper 서버). */
    SigTable table(String n) { return scope.get(n) == Scope.API ? api : paper; }

    SigTable.Cls decl(String n) { return table(n).classes().get(n); }

    /** 이 클래스를 만들 수 없는 이유(없으면 null). */
    String blocker(String n) {
        SigTable.Cls c = decl(n);
        List<String> supers = new ArrayList<>();
        if (c.superName() != null) supers.add(c.superName());
        for (String i : c.interfaces()) if (!i.startsWith(DROPPED_SUPER_PREFIX)) supers.add(i);
        for (String s : supers) {
            boolean wantInterface = !s.equals(c.superName());
            if (shells.contains(s)) {
                SigTable.Cls sc = decl(s);
                if (isInterface(sc.access()) != wantInterface) return "supertype kind differs: " + s;
                if (!wantInterface && (sc.access() & Opcodes.ACC_FINAL) != 0) return "final superclass: " + s;
                continue;
            }
            if (skipped.containsKey(s)) return "supertype not generated: " + s;
            Existing.Info info = existing.info(s);
            if (info == null) return "supertype not in compat, the vanilla libraries or the JDK: " + s;
            if (info.isInterface() != wantInterface) return "supertype kind differs from the hand shim: " + s + (info.isInterface() ? " (interface)" : " (class)");
            if (!wantInterface && info.isFinal()) return "hand shim superclass is final (vanilla/Paper: not final): " + s;
        }
        // 바깥 클래스가 있어야 안쪽 클래스의 모양(InnerClasses)이 맞는다 — 바깥이 없으면 안쪽도 만들지 않는다
        int d = n.lastIndexOf('$');
        if (d > 0) {
            String outer = n.substring(0, d);
            if (skipped.containsKey(outer)) return "outer class not generated: " + outer;
            if (!shells.contains(outer) && !existing.has(outer) && scope.containsKey(outer)) return "outer class missing: " + outer;
        }
        return null;
    }

    // ───────────────────────── 생성 ─────────────────────────

    Map<String, byte[]> generate() {
        select();
        Map<String, byte[]> out = new TreeMap<>();
        Map<String, List<String>> nested = new HashMap<>();
        for (String n : shells) {
            int d = n.lastIndexOf('$');
            if (d > 0) nested.computeIfAbsent(n.substring(0, d), k -> new ArrayList<>()).add(n);
        }
        for (String n : shells) out.put(n, shell(n, nested.getOrDefault(n, List.of())));
        return out;
    }

    byte[] shell(String n, List<String> nestedShells) {
        SigTable.Cls c = decl(n);
        SigTable t = table(n);
        boolean isEnum = (c.access() & Opcodes.ACC_ENUM) != 0 && !isInterface(c.access());
        boolean isRecord = (c.access() & 0x10000) != 0;   // ASM 의 ACC_RECORD(서명표가 ASM 접근 값을 그대로 적는다)
        int access = c.access() & 0xFFFF;                 // ACC_RECORD·ACC_DEPRECATED 같은 ASM 전용 비트는 클래스 파일 플래그가 아니다
        List<String> enumConstants = isEnum ? enumOrder(n) : List.of();
        if (isEnum && (access & Opcodes.ACC_ABSTRACT) != 0) {
            // 상수별 몸통이 있는 열거형: 상수의 실제 클래스(익명 하위)를 선언만으로는 짝지을 수 없다 → 클래스의 abstract 만 떼고 상수를 이 클래스로 만든다
            access &= ~Opcodes.ACC_ABSTRACT;
            deviation("abstract enum made concrete (constants are instances of the enum class itself, not of their anonymous bodies)", n);
        }
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        List<String> itfs = new ArrayList<>();
        for (String i : c.interfaces()) if (!i.startsWith(DROPPED_SUPER_PREFIX)) itfs.add(i);
        if (itfs.size() != c.interfaces().size()) deviation("Paper-internal ca/spottedleaf interfaces dropped", n);
        String sig = c.signature();
        if (sig != null && sig.contains("L" + DROPPED_SUPER_PREFIX)) sig = null;   // 위에서 뺀 인터페이스를 서명이 가리키면 서명을 버린다(서술자는 같다)
        cw.visit(Opcodes.V21, access, n, sig, c.superName(), itfs.toArray(String[]::new));
        // 안쪽 클래스 관계: 자기 자신(안쪽이면)과 만들어지는 안쪽 클래스들
        if (c.innerAccess() >= 0 || n.indexOf('$') > 0) innerClass(cw, n);
        for (String inner : nestedShells) innerClass(cw, inner);
        if (n.indexOf('$') > 0 && isAnonymousOrLocal(n)) cw.visitOuterClass(n.substring(0, n.lastIndexOf('$')), null, null);
        // 클래스 표지: 껍데기임(없는 이벤트 처리기 건너뛰기 등), NMS 밖 범위에 값 없는 필드가 있으면 초기화 덫 표시(Class.forName 다리가 "없음"으로 바꿈)
        boolean trapInit = scope.get(n) != Scope.NMS && hasShellField(n, t, isEnum);
        org.objectweb.asm.AnnotationVisitor ca = cw.visitAnnotation(SHELL_MEMBER, false);
        if (trapInit) ca.visit("initTrap", Boolean.TRUE);
        ca.visitEnd();
        if (isRecord) {
            for (String rc : recordComponents(n)) {
                int k = rc.indexOf(':');
                cw.visitRecordComponent(rc.substring(0, k), rc.substring(k + 1), null).visitEnd();
            }
        }
        Set<String> finals = isInterface(access) ? Set.of() : existing.finalMethodsAbove(c.superName(), this);
        boolean madeConcrete = isEnum && (c.access() & Opcodes.ACC_ABSTRACT) != 0;
        // NMS 밖 범위(Bukkit API·Paper 내부·Mojang)는 연결기 덫이 필드 참조를 보지 않는다(플러그인 클래스를 다 바꾸지 않으려고 NMS 이름이 든 클래스만 변환).
        // 값 없는 필드가 있으면 클래스 초기화에서 던진다 — 필드가 조용히 null/0 을 주지 않게, 예전의 "클래스 없음"처럼 크게 실패하고 계측에 센다.
        if (trapInit) initTrapped++;
        String enumCtor = null;
        Set<String> declared = new HashSet<>();
        for (SigTable.Member m : t.members(n)) {
            if (m.kind() == 'F') field(cw, n, m, isEnum);
            else {
                if (finals.contains(m.name() + m.desc())) {
                    deviation("method left out: a hand shim superclass declares it final", n + "#" + m.name() + m.desc());
                    continue;
                }
                if (isEnum && m.name().equals("<init>") && enumCtor == null && m.desc().startsWith("(Ljava/lang/String;I")) enumCtor = m.desc();
                method(cw, n, m, isEnum, access, madeConcrete);
                declared.add(m.name() + m.desc());
            }
        }
        // 구체로 바꾼 추상 열거형: 상수 몸통에만 있던 인터페이스 구현도 던지는 몸통으로(AbstractMethodError 대신 같은 오류·계측)
        if (madeConcrete) for (String[] im : abstractInterfaceMethods(c, declared)) shellBody(cw, n, Opcodes.ACC_PUBLIC, im[0], im[1], null);
        if (isEnum) enumSupport(cw, n, enumConstants, enumCtor, trapInit);
        else if (trapInit) initTrap(cw, n);
        cw.visitEnd();
        return cw.toByteArray();
    }

    private void innerClass(ClassWriter cw, String inner) {
        SigTable.Cls ic = decl(inner);
        int d = inner.lastIndexOf('$');
        String simple = inner.substring(d + 1);
        boolean anonOrLocal = isAnonymousOrLocal(inner);
        String innerName = anonOrLocal ? stripDigits(simple) : simple;
        if (innerName != null && innerName.isEmpty()) innerName = null;
        int ia = ic.innerAccess() >= 0 ? ic.innerAccess() : ic.access() & 0xFFFF;
        cw.visitInnerClass(inner, anonOrLocal ? null : inner.substring(0, d), innerName, ia & 0xFFFF);
    }

    static boolean isAnonymousOrLocal(String n) {
        String simple = n.substring(n.lastIndexOf('$') + 1);
        return !simple.isEmpty() && Character.isDigit(simple.charAt(0));
    }

    private static String stripDigits(String s) {
        int i = 0;
        while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
        return s.substring(i);
    }

    private void field(ClassWriter cw, String owner, SigTable.Member m, boolean isEnum) {
        Object value = null;
        if ((m.access() & (Opcodes.ACC_STATIC | Opcodes.ACC_FINAL)) == (Opcodes.ACC_STATIC | Opcodes.ACC_FINAL)) value = constant(owner, m.name());
        boolean enumConstant = isEnum && (m.access() & Opcodes.ACC_ENUM) != 0;
        FieldVisitor fv = cw.visitField(m.access() & 0xFFFF, m.name(), m.desc(), m.signature(), value);
        if (value == null && !enumConstant) {
            fv.visitAnnotation(SHELL_MEMBER, false).visitEnd();
            shellMembers++;
        } else {
            liveMembers++;
        }
        fv.visitEnd();
    }

    private void method(ClassWriter cw, String owner, SigTable.Member m, boolean isEnum, int classAccess, boolean madeConcrete) {
        int acc = m.access() & 0xFFFF & ~Opcodes.ACC_NATIVE;
        if (madeConcrete && (acc & Opcodes.ACC_ABSTRACT) != 0) {
            // 상수가 이 클래스의 진짜 객체이므로 추상 메서드가 실제로 불린다 — 추상으로 두면 AbstractMethodError(덫·계측 없음)
            shellBody(cw, owner, acc & ~Opcodes.ACC_ABSTRACT, m.name(), m.desc(), m.signature());
            return;
        }
        boolean isAbstract = (acc & Opcodes.ACC_ABSTRACT) != 0;
        boolean enumHelper = isEnum && (acc & Opcodes.ACC_STATIC) != 0
                && (m.name().equals("values") && m.desc().equals("()[L" + owner + ";")
                    || m.name().equals("valueOf") && m.desc().equals("(Ljava/lang/String;)L" + owner + ";"));
        if (enumHelper) return;   // enumSupport 가 진짜 몸통으로 쓴다
        MethodVisitor mv = cw.visitMethod(acc, m.name(), m.desc(), m.signature(), null);
        if (isAbstract) {
            liveMembers++;   // 추상 선언은 모양 그대로 — 해석은 바닐라와 같다
            mv.visitEnd();
            return;
        }
        mv.visitAnnotation(SHELL_MEMBER, false).visitEnd();
        shellMembers++;
        mv.visitCode();
        if (isEnum && m.name().equals("<init>") && m.desc().startsWith("(Ljava/lang/String;I")) {
            // 열거형 생성자: 상수를 만들 때만 쓰인다(플러그인은 열거형 생성자를 부를 수 없다) — Enum(String,int) 로 이름·서수만 넣는다
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitVarInsn(Opcodes.ILOAD, 2);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, decl(owner).superName(), "<init>", "(Ljava/lang/String;I)V", false);
            mv.visitInsn(Opcodes.RETURN);
        } else {
            mv.visitLdcInsn(owner.replace('/', '.') + "#" + m.name() + m.desc());
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, SHELL_HELPER, "unsupported", "(Ljava/lang/String;)Ljava/lang/RuntimeException;", false);
            mv.visitInsn(Opcodes.ATHROW);
        }
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    /** 열거형: 상수 만들기(<clinit>), values(), valueOf(String). 바닐라와 같은 이름·서수·순서. */
    private void enumSupport(ClassWriter cw, String n, List<String> constants, String ctor, boolean trapInit) {
        String desc = "L" + n + ";";
        String arr = "[" + desc;
        if (ctor == null && !constants.isEmpty()) throw new IllegalStateException("enum without a (String,int...) constructor in the table: " + n);
        cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC, "$VALUES", arr, null, null).visitEnd();
        MethodVisitor ci;
        if (trapInit) {
            initTrap(cw, n);   // 상수를 만들지 않는다(뒤 코드는 닿지 않음 — 프레임 없이 쓰려고 아예 내지 않는다)
            ci = null;
        } else {
            ci = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        }
        if (ci != null) enumConstants(ci, n, constants, ctor, desc, arr);
        enumHelpers(cw, n, constants, desc, arr);
    }

    private void enumConstants(MethodVisitor ci, String n, List<String> constants, String ctor, String desc, String arr) {
        ci.visitCode();
        Type[] args = ctor == null ? new Type[0] : Type.getArgumentTypes(ctor);
        for (int i = 0; i < constants.size(); i++) {
            ci.visitTypeInsn(Opcodes.NEW, n);
            ci.visitInsn(Opcodes.DUP);
            ci.visitLdcInsn(constants.get(i));
            ci.visitLdcInsn(i);
            for (int a = 2; a < args.length; a++) zero(ci, args[a]);
            ci.visitMethodInsn(Opcodes.INVOKESPECIAL, n, "<init>", ctor, false);
            ci.visitFieldInsn(Opcodes.PUTSTATIC, n, constants.get(i), desc);
        }
        ci.visitLdcInsn(constants.size());
        ci.visitTypeInsn(Opcodes.ANEWARRAY, n);
        for (int i = 0; i < constants.size(); i++) {
            ci.visitInsn(Opcodes.DUP);
            ci.visitLdcInsn(i);
            ci.visitFieldInsn(Opcodes.GETSTATIC, n, constants.get(i), desc);
            ci.visitInsn(Opcodes.AASTORE);
        }
        ci.visitFieldInsn(Opcodes.PUTSTATIC, n, "$VALUES", arr);
        ci.visitInsn(Opcodes.RETURN);
        ci.visitMaxs(0, 0);
        ci.visitEnd();
    }

    private void enumHelpers(ClassWriter cw, String n, List<String> constants, String desc, String arr) {
        MethodVisitor v = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "values", "()" + arr, null, null);
        v.visitCode();
        v.visitFieldInsn(Opcodes.GETSTATIC, n, "$VALUES", arr);
        v.visitMethodInsn(Opcodes.INVOKEVIRTUAL, arr, "clone", "()Ljava/lang/Object;", false);
        v.visitTypeInsn(Opcodes.CHECKCAST, arr);
        v.visitInsn(Opcodes.ARETURN);
        v.visitMaxs(0, 0);
        v.visitEnd();
        MethodVisitor vo = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "valueOf", "(Ljava/lang/String;)" + desc, null, null);
        vo.visitCode();
        vo.visitLdcInsn(Type.getObjectType(n));
        vo.visitVarInsn(Opcodes.ALOAD, 0);
        vo.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Enum", "valueOf", "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;", false);
        vo.visitTypeInsn(Opcodes.CHECKCAST, n);
        vo.visitInsn(Opcodes.ARETURN);
        vo.visitMaxs(0, 0);
        vo.visitEnd();
        liveMembers += 2 + constants.size();
    }

    /** 표지 달린 던지는 몸통 하나. */
    private void shellBody(ClassWriter cw, String owner, int acc, String name, String desc, String signature) {
        MethodVisitor mv = cw.visitMethod(acc, name, desc, signature, null);
        mv.visitAnnotation(SHELL_MEMBER, false).visitEnd();
        shellMembers++;
        mv.visitCode();
        mv.visitLdcInsn(owner.replace('/', '.') + "#" + name + desc);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, SHELL_HELPER, "unsupported", "(Ljava/lang/String;)Ljava/lang/RuntimeException;", false);
        mv.visitInsn(Opcodes.ATHROW);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    /** 클래스 초기화에서 던진다: {@code NmsShell.unsupportedClass("소유")} — Error 라 JVM 이 싸지 않고 첫 번부터 NoClassDefFoundError 로 낸다. */
    private void initTrap(ClassWriter cw, String n) {
        MethodVisitor ci = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        ci.visitCode();
        ci.visitLdcInsn(n.replace('/', '.'));
        ci.visitMethodInsn(Opcodes.INVOKESTATIC, SHELL_HELPER, "unsupportedClass", "(Ljava/lang/String;)Ljava/lang/Error;", false);
        ci.visitInsn(Opcodes.ATHROW);
        ci.visitMaxs(0, 0);
        ci.visitEnd();
    }

    /** 값 없는(표지가 붙을) 필드가 있나 — 상수 값 필드·열거형 상수는 진짜 값이 있다. */
    private boolean hasShellField(String n, SigTable t, boolean isEnum) {
        for (SigTable.Member m : t.members(n)) {
            if (m.kind() != 'F') continue;
            if (isEnum && (m.access() & Opcodes.ACC_ENUM) != 0) continue;
            boolean sf = (m.access() & (Opcodes.ACC_STATIC | Opcodes.ACC_FINAL)) == (Opcodes.ACC_STATIC | Opcodes.ACC_FINAL);
            if (sf && constant(n, m.name()) != null) continue;
            return true;
        }
        return false;
    }

    /** 이 클래스가 (상위 인터페이스까지) 구현해야 하나 선언하지 않은 추상 인스턴스 메서드(이름, 서술자). 서명표에 있는 인터페이스만 본다. */
    private List<String[]> abstractInterfaceMethods(SigTable.Cls c, Set<String> declared) {
        Map<String, String[]> out = new java.util.LinkedHashMap<>();
        Set<String> concrete = new HashSet<>();
        java.util.ArrayDeque<String> todo = new java.util.ArrayDeque<>(c.interfaces());
        Set<String> seen = new HashSet<>();
        while (!todo.isEmpty()) {
            String i = todo.poll();
            if (!seen.add(i)) continue;
            SigTable it = paper.classes().containsKey(i) ? paper : api.classes().containsKey(i) ? api : null;
            if (it == null) continue;
            for (SigTable.Member m : it.members(i)) {
                if (m.kind() != 'M' || (m.access() & Opcodes.ACC_STATIC) != 0 || m.name().startsWith("<")) continue;
                String k = m.name() + m.desc();
                if ((m.access() & Opcodes.ACC_ABSTRACT) != 0) out.putIfAbsent(k, new String[]{m.name(), m.desc()});
                else concrete.add(k);   // 기본 메서드
            }
            todo.addAll(it.classes().get(i).interfaces());
        }
        List<String[]> r = new ArrayList<>();
        for (var e : out.entrySet()) if (!declared.contains(e.getKey()) && !concrete.contains(e.getKey()) && !OBJECT_METHODS.contains(e.getKey())) r.add(e.getValue());
        return r;
    }

    private static final Set<String> OBJECT_METHODS = Set.of("equals(Ljava/lang/Object;)Z", "hashCode()I", "toString()Ljava/lang/String;",
            "compareTo(Ljava/lang/Object;)I", "name()Ljava/lang/String;", "ordinal()I", "getDeclaringClass()Ljava/lang/Class;");

    private static void zero(MethodVisitor mv, Type t) {
        switch (t.getSort()) {
            case Type.LONG -> mv.visitInsn(Opcodes.LCONST_0);
            case Type.FLOAT -> mv.visitInsn(Opcodes.FCONST_0);
            case Type.DOUBLE -> mv.visitInsn(Opcodes.DCONST_0);
            case Type.OBJECT, Type.ARRAY -> mv.visitInsn(Opcodes.ACONST_NULL);
            default -> mv.visitInsn(Opcodes.ICONST_0);
        }
    }

    List<String> enumOrder(String n) {
        List<String> e = table(n).enumConstants(n);
        if (e.isEmpty()) e = vanilla.enumConstants(n);
        if (e.isEmpty()) {
            // 형식 1 표이거나 상수 없는 열거형: 선언 순서를 모르면 만들지 않는다(서수가 틀리면 안 된다)
            boolean any = false;
            for (SigTable.Member m : table(n).members(n)) if (m.kind() == 'F' && (m.access() & Opcodes.ACC_ENUM) != 0) any = true;
            if (any) throw new IllegalStateException("no enum constant order for " + n + " - rebuild the signature tables (format 2) with tools/compat/sig-tables.sh");
        }
        return e;
    }

    List<String> recordComponents(String n) {
        List<String> r = table(n).recordComponents(n);
        return r.isEmpty() ? vanilla.recordComponents(n) : r;
    }

    Object constant(String owner, String name) {
        Object v = table(owner).constantValue(owner, name);
        return v != null ? v : vanilla.constantValue(owner, name);
    }

    void deviationPublic(String why, String what) { deviation(why, what); }

    private void deviation(String why, String what) {
        deviations.computeIfAbsent(why, k -> new ArrayList<>()).add(what);
    }

    // ───────────────────────── 보고 ─────────────────────────

    String summary() {
        Map<Scope, int[]> by = counts();
        StringBuilder sb = new StringBuilder();
        for (Scope s : Scope.values()) {
            int[] c = by.get(s);
            sb.append(s).append(": ").append(c[0]).append(" in scope, ").append(c[1]).append(" already in compat, ").append(c[2]).append(" generated, ")
                    .append(c[3]).append(" skipped; ");
        }
        sb.append(shellMembers).append(" shell members, ").append(liveMembers).append(" live members (abstract, constants, enum support), ").append(initTrapped).append(" non-NMS shells whose class init throws (fields without a value)");
        return sb.toString();
    }

    private Map<Scope, int[]> counts() {
        Map<Scope, int[]> by = new LinkedHashMap<>();
        for (Scope s : Scope.values()) by.put(s, new int[4]);
        for (Map.Entry<String, Scope> e : scope.entrySet()) {
            int[] c = by.get(e.getValue());
            c[0]++;
            if (shells.contains(e.getKey())) c[2]++;
            else if (skipped.containsKey(e.getKey())) c[3]++;
            else c[1]++;
        }
        return by;
    }

    String report() {
        StringBuilder sb = new StringBuilder("# T-260 full shells report (generated; contains Mojang/Paper names - do not commit or share)\n\n");
        sb.append(summary()).append("\n\n## skipped (").append(skipped.size()).append(")\n");
        Map<String, Integer> reasons = new TreeMap<>();
        for (String why : skipped.values()) reasons.merge(why.replaceAll(":.*", ""), 1, Integer::sum);
        reasons.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append('\n'));
        sb.append('\n');
        skipped.forEach((k, v) -> sb.append(k).append('\t').append(v).append('\n'));
        sb.append("\n## deviations\n");
        deviations.forEach((k, v) -> {
            sb.append("### ").append(k).append(" (").append(v.size()).append(")\n");
            for (String x : v) sb.append(x).append('\n');
        });
        return sb.toString();
    }

    // ───────────────────────── 이미 있는 클래스 ─────────────────────────

    /** 호환 계층(과 그 실행 의존)의 클래스 선언 — 클래스 파일 머리만 읽는다(몸통 건너뜀). JDK 클래스는 플랫폼 로더에서. */
    static final class Existing {
        record Info(boolean isInterface, boolean isFinal, String superName, Set<String> finalMethods) {}

        private final Map<String, Path> dirFiles = new HashMap<>();
        private final Map<String, ZipFile> jarOf = new HashMap<>();
        private final Map<String, Info> cache = new HashMap<>();
        /** 라이브러리(바닐라 서버가 싣는 jar)의 클래스 — 상위 형식으로는 있지만 "이미 있는 클래스"(껍데기 안 만듦)는 아니다. */
        private final Set<String> libraryClasses = new HashSet<>();

        /** roots: 호환 계층 클래스 폴더·jar. 폴더 안의 jar 는 라이브러리로 본다(work/libs). */
        Existing(List<Path> roots) throws IOException {
            for (Path r : roots) {
                if (Files.isDirectory(r)) {
                    try (var s = Files.walk(r)) {
                        for (Path p : s.filter(Files::isRegularFile).sorted().toList()) {
                            String f = p.toString();
                            if (f.endsWith(".class")) {
                                String n = r.relativize(p).toString().replace('\\', '/');
                                dirFiles.putIfAbsent(n.substring(0, n.length() - 6), p);
                            } else if (f.endsWith(".jar")) {
                                addJar(p, true);
                            }
                        }
                    }
                } else if (Files.isRegularFile(r) && r.toString().endsWith(".jar")) {
                    addJar(r, false);
                }
            }
        }

        private void addJar(Path jar, boolean library) throws IOException {
            ZipFile z = new ZipFile(jar.toFile());
            var en = z.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String n = e.getName();
                if (!n.endsWith(".class") || n.startsWith("META-INF/")) continue;
                String c = n.substring(0, n.length() - 6);
                if (jarOf.putIfAbsent(c, z) == null && library) libraryClasses.add(c);
            }
        }

        /** 호환 계층에 이미 있는 클래스(껍데기를 만들지 않는다). 라이브러리는 넣지 않는다. */
        boolean has(String n) { return dirFiles.containsKey(n) || jarOf.containsKey(n) && !libraryClasses.contains(n); }

        Info info(String n) {
            if (cache.containsKey(n)) return cache.get(n);
            Info i = null;
            try {
                byte[] b = bytes(n);
                if (b != null) i = read(b);
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
            cache.put(n, i);
            return i;
        }

        /** 폴더 안 클래스 이름 전부(합칠 대상 고르기). */
        static Set<String> classesIn(Path root) throws IOException {
            Set<String> out = new HashSet<>();
            if (!Files.isDirectory(root)) return out;
            try (var s = Files.walk(root)) {
                for (Path p : s.filter(Files::isRegularFile).toList()) {
                    String n = root.relativize(p).toString().replace('\\', '/');
                    if (n.endsWith(".class") && !n.startsWith("META-INF/")) out.add(n.substring(0, n.length() - 6));
                }
            }
            return out;
        }

        byte[] bytesOf(String n) {
            try {
                return bytes(n);
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }

        private byte[] bytes(String n) throws IOException {
            Path p = dirFiles.get(n);
            if (p != null) return Files.readAllBytes(p);
            ZipFile z = jarOf.get(n);
            if (z != null) try (InputStream in = z.getInputStream(z.getEntry(n + ".class"))) { return in.readAllBytes(); }
            if (n.startsWith("java/") || n.startsWith("javax/") || n.startsWith("jdk/") || n.startsWith("sun/")) {
                try (InputStream in = ClassLoader.getPlatformClassLoader().getResourceAsStream(n + ".class")) {
                    if (in != null) return in.readAllBytes();
                }
                try (InputStream in = Object.class.getResourceAsStream("/" + n + ".class")) {
                    if (in != null) return in.readAllBytes();
                }
            }
            return null;
        }

        private static Info read(byte[] b) {
            Object[] head = new Object[3];
            Set<String> finals = new HashSet<>();
            new ClassReader(b).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                    head[0] = (access & Opcodes.ACC_INTERFACE) != 0;
                    head[1] = (access & Opcodes.ACC_FINAL) != 0;
                    head[2] = superName;
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if ((access & Opcodes.ACC_FINAL) != 0 && (access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC)) == 0) finals.add(name + descriptor);
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return new Info((Boolean) head[0], (Boolean) head[1], (String) head[2], Set.copyOf(finals));
        }

        /** 상위 사슬에서 이미 있는 클래스(손 shim·JDK)가 final 로 선언한 인스턴스 메서드(이름+서술자). 껍데기 상위는 서명표로 본다. */
        Set<String> finalMethodsAbove(String superName, FullShells g) {
            Set<String> out = new HashSet<>();
            for (String s = superName; s != null; ) {
                if (g.shells.contains(s)) {
                    SigTable.Cls c = g.decl(s);
                    for (SigTable.Member m : g.table(s).members(s)) {
                        if (m.kind() == 'M' && (m.access() & Opcodes.ACC_FINAL) != 0 && (m.access() & (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC)) == 0) out.add(m.name() + m.desc());
                    }
                    s = c.superName();
                } else {
                    Info i = info(s);
                    if (i == null) break;
                    out.addAll(i.finalMethods());
                    s = i.superName();
                }
            }
            return out;
        }
    }

    // ───────────────────────── 도우미 ─────────────────────────

    static boolean isInterface(int access) { return (access & Opcodes.ACC_INTERFACE) != 0; }

    static boolean startsWithAny(String n, List<String> prefixes) {
        for (String p : prefixes) if (n.startsWith(p)) return true;
        return false;
    }

    static void deleteTree(Path p) throws IOException {
        if (!Files.exists(p)) return;
        try (var s = Files.walk(p)) {
            for (Path x : s.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(x);
        }
    }
}
