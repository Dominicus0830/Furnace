package org.domi.furnace.tools.extract;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * 서명표(T-227): jar 안 클래스의 "선언"만 모은 표. 서명 관문(T-228)·차분 시험(T-235)·표류 보고(T-236)가 읽는다.
 *
 * <p>법적 경계: 클래스 파일을 {@link ClassReader#SKIP_CODE}·{@code SKIP_DEBUG}·{@code SKIP_FRAMES} 로만 읽고 모든 방문자가
 * 메서드 몸통·애노테이션을 받지 않는다. 그래서 이 도구는 구조상 바이트코드 몸통에 손이 닿지 않는다. 표는 개발자·CI 컴퓨터의 캐시
 * 폴더에만 두며 커밋·배포하지 않는다(Mojang 대응표 조건: 완전하고 수정되지 않은 형태로 재배포 금지).
 *
 * <p>형식(탭 구분, UTF-8, gzip, 같은 입력이면 같은 바이트 — gzip 시각 0, 정렬 고정):
 * <pre>
 * #furnace-sig-table 1 &lt;이름표&gt;
 * C 접근 클래스 상위 인터페이스(쉼표) 제네릭서명 안쪽접근     (없는 칸은 -)
 * F 접근 소유자 이름 서술자 제네릭서명
 * M 접근 소유자 이름 서술자 제네릭서명
 * E 소유자 상수1,상수2,…                                  (형식 2 — 열거형 상수, 클래스 파일 선언 순서 = 서수)
 * K 소유자 이름 서술자 값                                  (형식 2 — static final 상수 필드의 ConstantValue, 아래 부호화)
 * R 소유자 이름:서술자,…                                   (형식 2 — 레코드 성분, 선언 순서)
 * </pre>
 * 상수 값 부호화: 정수형(B·C·S·Z·I·J)은 10진수, F·D 는 비트 그대로 16진수({@code floatToRawIntBits}·{@code doubleToRawLongBits}),
 * 문자열은 역슬래시·탭·줄바꿈·복귀 문자를 역슬래시 두 글자로, 나머지 제어 문자를 역슬래시+u+16진 4자리로만 바꾼 글. 셋 다 선언 속성이라 몸통을 읽지 않는다(T-260 전체 껍데기가 쓴다).
 * 읽기는 형식 1(E·K·R 없음)도 받는다.
 * 접근은 10진수 ACC_* 플래그. 안쪽접근은 중첩 클래스의 InnerClasses 접근 플래그(static·private·protected 는 여기에만 있다), 최상위는 -.
 * 합성(synthetic) 멤버(람다·접근자·브리지)는 뺀다 — 소스에 선언된 서명만 본다.
 */
public final class SigTable {
    public static final String MAGIC = "#furnace-sig-table";
    public static final String FORMAT = "2";

    /** 클래스 한 줄. */
    public record Cls(int access, String name, String superName, List<String> interfaces, String signature, int innerAccess) {
        /** 접근 범위 판정에 쓰는 실제 접근 플래그(중첩 클래스는 안쪽 접근이 정답). */
        public int effectiveAccess() {
            return innerAccess >= 0 ? innerAccess : access;
        }
    }

    /** 멤버 한 줄. kind 는 'F'(필드) 또는 'M'(메서드·생성자·static 초기화 제외). */
    public record Member(char kind, int access, String owner, String name, String desc, String signature) {}

    private final Map<String, Cls> classes = new TreeMap<>();
    private final Map<String, List<Member>> members = new TreeMap<>();
    private final Map<String, List<String>> enumConstants = new TreeMap<>();
    private final Map<String, Map<String, Object>> constants = new TreeMap<>();
    private final Map<String, List<String>> recordComponents = new TreeMap<>();
    private final String label;

    /** 시험·도구용: 빈 표. */
    public static SigTable empty(String label) {
        return new SigTable(label);
    }

    private SigTable(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public Map<String, Cls> classes() {
        return classes;
    }

    public List<Member> members(String owner) {
        return members.getOrDefault(owner, List.of());
    }

    /** 열거형 상수 이름(선언 순서 = 서수). 열거형이 아니거나 형식 1 표면 빈 목록. */
    public List<String> enumConstants(String owner) {
        return enumConstants.getOrDefault(owner, List.of());
    }

    /** static final 상수 필드의 ConstantValue(Integer·Long·Float·Double·String — 자바 상수 풀 형식 그대로). 없으면 null. */
    public Object constantValue(String owner, String field) {
        Map<String, Object> m = constants.get(owner);
        return m == null ? null : m.get(field);
    }

    /** 레코드 성분 {@code 이름:서술자}(선언 순서). 레코드가 아니거나 형식 1 표면 빈 목록. */
    public List<String> recordComponents(String owner) {
        return recordComponents.getOrDefault(owner, List.of());
    }

    public int memberCount() {
        int n = 0;
        for (List<Member> l : members.values()) n += l.size();
        return n;
    }

    // ───────────────────────── 만들기 ─────────────────────────

    /** jar 에서 prefixes 로 시작하는 내부 이름(예: net/minecraft/)의 클래스만 모은다. 멀티 릴리스 폴더(META-INF/versions)는 건너뛴다. */
    public static SigTable fromJar(Path jar, List<String> prefixes, String label) throws IOException {
        SigTable t = new SigTable(label);
        t.addJar(jar, prefixes);
        return t;
    }

    /** jar 하나의 선언을 이 표에 더한다(라이브러리 jar 여러 개를 한 표로 모을 때). */
    public void addJar(Path jar, List<String> prefixes) throws IOException {
        SigTable t = this;
        try (ZipFile zf = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String n = e.getName();
                if (e.isDirectory() || !n.endsWith(".class") || n.startsWith("META-INF/")) continue;
                if (!matches(n, prefixes)) continue;
                try (InputStream in = zf.getInputStream(e)) {
                    t.add(in.readAllBytes());
                }
            }
        }
    }

    /** 컴파일된 클래스 폴더(예: gradle 의 classes/java/main)에서 prefixes 로 시작하는 클래스만 모은다(서명 관문이 shim 을 읽는다). */
    public static SigTable fromDirectory(Path dir, List<String> prefixes, String label) throws IOException {
        SigTable t = new SigTable(label);
        try (var s = Files.walk(dir)) {
            for (Path p : s.filter(x -> x.toString().endsWith(".class")).sorted().toList()) {
                String rel = dir.relativize(p).toString().replace('\\', '/');
                if (matches(rel, prefixes)) t.add(Files.readAllBytes(p));
            }
        }
        return t;
    }

    static boolean matches(String entryName, List<String> prefixes) {
        if (prefixes.isEmpty()) return true;
        for (String p : prefixes) if (entryName.startsWith(p)) return true;
        return false;
    }

    /** 클래스 파일 하나(바이트)를 표에 더한다. 시험이 직접 부른다. */
    public void add(byte[] classBytes) {
        ClassReader r = new ClassReader(classBytes);
        Collector c = new Collector();
        r.accept(c, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        classes.put(c.name, new Cls(c.access, c.name, c.superName, List.copyOf(c.interfaces), c.signature, c.innerAccess));
        if (!c.enumOrder.isEmpty()) enumConstants.put(c.name, List.copyOf(c.enumOrder));
        if (!c.constants.isEmpty()) constants.put(c.name, new TreeMap<>(c.constants));
        if (!c.records.isEmpty()) recordComponents.put(c.name, List.copyOf(c.records));
        c.members.sort(Comparator.comparing((Member m) -> m.kind()).thenComparing(Member::name).thenComparing(Member::desc));
        members.put(c.name, List.copyOf(c.members));
    }

    /** 방문자는 선언 머리만 모은다. 메서드·필드·애노테이션 방문자는 null 을 돌려 속성 몸통을 받지 않는다. */
    private static final class Collector extends ClassVisitor {
        String name, superName, signature;
        int access;
        int innerAccess = -1;
        List<String> interfaces = new ArrayList<>();
        final List<Member> members = new ArrayList<>();
        final List<String> enumOrder = new ArrayList<>();
        final Map<String, Object> constants = new TreeMap<>();
        final List<String> records = new ArrayList<>();

        Collector() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.access = access;
            this.name = name;
            this.signature = signature;
            this.superName = superName;
            if (interfaces != null) this.interfaces.addAll(List.of(interfaces));
        }

        @Override
        public void visitInnerClass(String name, String outerName, String innerName, int access) {
            if (name.equals(this.name)) innerAccess = access;
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            if ((access & Opcodes.ACC_SYNTHETIC) == 0) members.add(new Member('F', access, this.name, name, descriptor, signature));
            if ((access & Opcodes.ACC_ENUM) != 0) enumOrder.add(name);   // 클래스 파일의 필드 순서 = 소스 선언 순서(javac)
            if (value != null && (access & (Opcodes.ACC_STATIC | Opcodes.ACC_FINAL)) == (Opcodes.ACC_STATIC | Opcodes.ACC_FINAL)
                    && (access & Opcodes.ACC_SYNTHETIC) == 0) constants.put(name, value);
            return null;
        }

        @Override
        public org.objectweb.asm.RecordComponentVisitor visitRecordComponent(String name, String descriptor, String signature) {
            records.add(name + ":" + descriptor);
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            if ((access & Opcodes.ACC_SYNTHETIC) == 0 && !name.equals("<clinit>")) {
                members.add(new Member('M', access, this.name, name, descriptor, signature));
            }
            return null;
        }
    }

    // ───────────────────────── 쓰기·읽기 ─────────────────────────

    /** 같은 표는 같은 바이트가 된다(gzip 헤더 시각 0, 클래스·멤버 정렬 고정). */
    public void write(Path out) throws IOException {
        Files.createDirectories(out.toAbsolutePath().getParent());
        Path tmp = out.resolveSibling(out.getFileName() + ".part");
        try (OutputStream os = Files.newOutputStream(tmp);
             GZIPOutputStream gz = new GZIPOutputStream(os);
             BufferedWriter w = new BufferedWriter(new OutputStreamWriter(gz, StandardCharsets.UTF_8))) {
            w.write(MAGIC + "\t" + FORMAT + "\t" + label + "\n");
            for (Cls c : classes.values()) {
                w.write(String.join("\t", "C", Integer.toString(c.access()), c.name(), dash(c.superName()),
                        c.interfaces().isEmpty() ? "-" : String.join(",", c.interfaces()), dash(c.signature()),
                        c.innerAccess() < 0 ? "-" : Integer.toString(c.innerAccess())) + "\n");
                for (Member m : members(c.name())) {
                    w.write(String.join("\t", Character.toString(m.kind()), Integer.toString(m.access()), m.owner(), m.name(), m.desc(), dash(m.signature())) + "\n");
                }
                List<String> en = enumConstants(c.name());
                if (!en.isEmpty()) w.write("E\t" + c.name() + "\t" + String.join(",", en) + "\n");
                Map<String, Object> k = constants.get(c.name());
                if (k != null) {
                    for (Member m : members(c.name())) {
                        if (m.kind() == 'F' && k.containsKey(m.name())) {
                            w.write("K\t" + c.name() + "\t" + m.name() + "\t" + m.desc() + "\t" + encodeConstant(m.desc(), k.get(m.name())) + "\n");
                        }
                    }
                }
                List<String> rc = recordComponents(c.name());
                if (!rc.isEmpty()) w.write("R\t" + c.name() + "\t" + String.join(",", rc) + "\n");
            }
        }
        Files.move(tmp, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    public static SigTable read(Path file) throws IOException {
        try (InputStream in = new GZIPInputStream(Files.newInputStream(file));
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return read(r);
        }
    }

    public static SigTable read(BufferedReader r) throws IOException {
        String head = r.readLine();
        if (head == null || !head.startsWith(MAGIC + "\t")) throw new IOException("서명표가 아니다: 머리말이 " + MAGIC + " 으로 시작하지 않는다");
        String[] h = head.split("\t", -1);
        if (h.length < 3 || !(h[1].equals(FORMAT) || h[1].equals("1"))) throw new IOException("지원하지 않는 서명표 형식: " + (h.length > 1 ? h[1] : "?") + " (이 도구는 " + FORMAT + ")");
        SigTable t = new SigTable(h[2]);
        Map<String, List<Member>> acc = new LinkedHashMap<>();
        String line;
        while ((line = r.readLine()) != null) {
            if (line.isEmpty()) continue;
            String[] f = line.split("\t", -1);
            switch (f[0]) {
                case "C" -> {
                    List<String> itf = f[4].equals("-") ? List.of() : List.of(f[4].split(","));
                    t.classes.put(f[2], new Cls(Integer.parseInt(f[1]), f[2], undash(f[3]), itf, undash(f[5]), f[6].equals("-") ? -1 : Integer.parseInt(f[6])));
                }
                case "F", "M" -> acc.computeIfAbsent(f[2], k -> new ArrayList<>())
                        .add(new Member(f[0].charAt(0), Integer.parseInt(f[1]), f[2], f[3], f[4], undash(f[5])));
                case "E" -> t.enumConstants.put(f[1], List.of(f[2].split(",")));
                case "K" -> t.constants.computeIfAbsent(f[1], k -> new TreeMap<>()).put(f[2], decodeConstant(f[3], f[4]));
                case "R" -> t.recordComponents.put(f[1], List.of(f[2].split(",")));
                default -> throw new IOException("알 수 없는 줄: " + line);
            }
        }
        acc.forEach((k, v) -> t.members.put(k, List.copyOf(v)));
        return t;
    }

    /** ConstantValue 를 한 칸 글로(탭·줄바꿈 없이, 되읽으면 같은 값). */
    static String encodeConstant(String desc, Object v) {
        return switch (desc) {
            case "F" -> "0x" + Integer.toHexString(Float.floatToRawIntBits((Float) v));
            case "D" -> "0x" + Long.toHexString(Double.doubleToRawLongBits((Double) v));
            case "Ljava/lang/String;" -> escape((String) v);
            default -> v.toString();   // B·C·S·Z·I 는 Integer, J 는 Long
        };
    }

    static Object decodeConstant(String desc, String s) {
        return switch (desc) {
            case "F" -> Float.intBitsToFloat(Integer.parseUnsignedInt(s.substring(2), 16));
            case "D" -> Double.longBitsToDouble(Long.parseUnsignedLong(s.substring(2), 16));
            case "J" -> Long.parseLong(s);
            case "Ljava/lang/String;" -> unescape(s);
            default -> Integer.parseInt(s);
        };
    }

    private static String escape(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '\\' -> b.append("\\\\");
                case '\t' -> b.append("\\t");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                default -> {
                    if (ch < 0x20 || ch == 0x7f) b.append(String.format("\\u%04x", (int) ch));
                    else b.append(ch);
                }
            }
        }
        return b.toString();
    }

    private static String unescape(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch != '\\' || i + 1 >= s.length()) { b.append(ch); continue; }
            char n = s.charAt(++i);
            switch (n) {
                case 't' -> b.append('\t');
                case 'n' -> b.append('\n');
                case 'r' -> b.append('\r');
                case 'u' -> { b.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16)); i += 4; }
                default -> b.append(n);
            }
        }
        return b.toString();
    }

    private static String dash(String s) {
        return s == null ? "-" : s;
    }

    private static String undash(String s) {
        return s.equals("-") ? null : s;
    }
}
