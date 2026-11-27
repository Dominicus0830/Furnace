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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 채움 표(T-262): 전체 껍데기(T-260)의 동작 없는 멤버 가운데 "값만으로 정해지는 것"을 바닐라 jar 에서 기계로 뽑은 표.
 * 몸통의 명령·식은 어디에도 남기지 않는다 — 남는 것은 값(상수·필드 이름·인자 자리)뿐이다. 표는 캐시에만 두고 커밋하지 않는다
 * (Mojang 이름이 든다). 커밋하는 것은 지문과 이름 없는 기준선뿐이다(docs/feature-parity/compat-shell-fills-t262-2026-11-23.md).
 *
 * <p>줄 종류(탭 구분, UTF-8, gzip, 같은 입력이면 같은 바이트):
 * <pre>
 * #furnace-fill-table 1 &lt;이름표&gt;
 * P 소유자 서술자 상위소유자 상위서술자 상위인자,… 단계,…     단순 생성자: 상위 호출 뒤 필드 대입만 (없는 칸은 -)
 * G 소유자 이름 서술자 필드 필드서술자                       단순 읽기: this.필드 를 돌려줌
 * S 소유자 필드 필드서술자 값                                정적 초기화가 리터럴 하나를 한 번 대입하는 정적 필드
 * V 소유자 서수 이름 생성자서술자 값,…|!                     열거형 상수의 생성자 인자(전부 리터럴이면 값들, 아니면 !)
 * T 소유자 toString,hashCode,equals                         레코드 기본 메서드(ObjectMethods) 가운데 기본인 것
 * M 소유자 이름 서술자 s|i 종류 값                           짧은 값 몸통(T-263 G0): 종류 K 리터럴 반환(값), E 빈 void(값 -),
 *                                                           A 인자·this 반환(값 p:지역변수번호), R 정적 필드 반환(값 소유자.필드:서술자). s|i 는 정적·인스턴스
 * </pre>
 * 값 부호화: {@code i:10진}(B·C·S·Z·I), {@code j:10진}, {@code f:0x비트}, {@code d:0x비트}, {@code s:퍼센트부호화},
 * {@code n:}(null), 인자 자리는 {@code p:지역변수번호}. 단계는 {@code 필드:서술자=값}.
 */
public final class FillTable {
    public static final String MAGIC = "#furnace-fill-table";
    public static final String FORMAT = "2";   // 2: M 줄(T-263 G0)

    /** 값. kind 는 i·j·f·d·s·n(리터럴) 또는 p(생성자 인자 자리, 값은 지역변수 번호). */
    public record Val(char kind, Object value) {
        public static Val lit(char kind, Object value) { return new Val(kind, value); }
        public static Val param(int slot) { return new Val('p', slot); }
        public boolean isParam() { return kind == 'p'; }

        public String encode() {
            return switch (kind) {
                case 'i', 'j', 'p' -> kind + ":" + value;
                case 'f' -> "f:0x" + Integer.toHexString(Float.floatToRawIntBits((Float) value));
                case 'd' -> "d:0x" + Long.toHexString(Double.doubleToRawLongBits((Double) value));
                case 's' -> "s:" + pct((String) value);
                case 'n' -> "n:";
                default -> throw new IllegalStateException("kind " + kind);
            };
        }

        public static Val decode(String s) {
            char k = s.charAt(0);
            String v = s.substring(2);
            return switch (k) {
                case 'i' -> lit('i', Integer.parseInt(v));
                case 'j' -> lit('j', Long.parseLong(v));
                case 'p' -> param(Integer.parseInt(v));
                case 'f' -> lit('f', Float.intBitsToFloat(Integer.parseUnsignedInt(v.substring(2), 16)));
                case 'd' -> lit('d', Double.longBitsToDouble(Long.parseUnsignedLong(v.substring(2), 16)));
                case 's' -> lit('s', unpct(v));
                case 'n' -> lit('n', null);
                default -> throw new IllegalArgumentException("값 종류: " + s);
            };
        }
    }

    /** 대입 한 단계: this.field = value. */
    public record Step(String field, String desc, Val value) {}

    /** 단순 생성자. 상위 호출 인자와 단계의 값은 인자 자리 또는 리터럴. */
    public record Ctor(String owner, String desc, String superOwner, String superDesc, List<Val> superArgs, List<Step> steps) {}

    public record Getter(String owner, String name, String desc, String field, String fieldDesc) {}

    public record Static(String owner, String field, String desc, Val value) {}

    /** args 가 null 이면 리터럴이 아닌 인자가 있다(채우지 않음). */
    public record EnumConst(String owner, int ordinal, String name, String ctorDesc, List<Val> args) {}

    /**
     * 짧은 값 몸통(T-263 G0). kind: K(리터럴 반환, value=Val 부호), E(빈 void, value="-"), A(인자·this 반환, value="p:번호"),
     * R(정적 필드 반환, value="소유자.필드:서술자"). 몸통의 명령은 남기지 않는다 — 값만.
     */
    public record Method(String owner, String name, String desc, boolean isStatic, char kind, String value) {}

    private final String label;
    private final Map<String, List<Ctor>> ctors = new TreeMap<>();
    private final Map<String, List<Getter>> getters = new TreeMap<>();
    private final Map<String, List<Static>> statics = new TreeMap<>();
    private final Map<String, List<EnumConst>> enums = new TreeMap<>();
    private final Map<String, List<String>> objectMethods = new TreeMap<>();
    private final Map<String, List<Method>> methods = new TreeMap<>();

    public FillTable(String label) { this.label = label; }

    public String label() { return label; }
    public List<Ctor> ctors(String owner) { return ctors.getOrDefault(owner, List.of()); }
    public List<Getter> getters(String owner) { return getters.getOrDefault(owner, List.of()); }
    public List<Static> statics(String owner) { return statics.getOrDefault(owner, List.of()); }
    public List<EnumConst> enumConsts(String owner) { return enums.getOrDefault(owner, List.of()); }
    /** 레코드 기본 메서드 이름(toString·hashCode·equals 가운데 기본인 것). */
    public List<String> objectMethods(String owner) { return objectMethods.getOrDefault(owner, List.of()); }
    /** 짧은 값 몸통(G0). */
    public List<Method> methods(String owner) { return methods.getOrDefault(owner, List.of()); }

    public void add(Ctor c) { ctors.computeIfAbsent(c.owner(), k -> new ArrayList<>()).add(c); }
    public void add(Getter g) { getters.computeIfAbsent(g.owner(), k -> new ArrayList<>()).add(g); }
    public void add(Static s) { statics.computeIfAbsent(s.owner(), k -> new ArrayList<>()).add(s); }
    public void add(EnumConst e) { enums.computeIfAbsent(e.owner(), k -> new ArrayList<>()).add(e); }
    public void addObjectMethods(String owner, List<String> names) { objectMethods.put(owner, List.copyOf(names)); }
    public void add(Method m) { methods.computeIfAbsent(m.owner(), k -> new ArrayList<>()).add(m); }

    /** 두 표(바닐라·Paper)에서 줄이 똑같은 것만 남긴 표. 한쪽에 없거나 다르면 채우지 않는다("모르면 채우지 않는다"). */
    public FillTable intersect(FillTable other) {
        FillTable r = new FillTable(label + "&" + other.label);
        for (var e : ctors.entrySet()) for (Ctor c : e.getValue()) if (other.ctors(e.getKey()).contains(c)) r.add(c);
        for (var e : getters.entrySet()) for (Getter c : e.getValue()) if (other.getters(e.getKey()).contains(c)) r.add(c);
        for (var e : statics.entrySet()) for (Static c : e.getValue()) if (other.statics(e.getKey()).contains(c)) r.add(c);
        for (var e : enums.entrySet()) for (EnumConst c : e.getValue()) if (other.enumConsts(e.getKey()).contains(c)) r.add(c);
        for (var e : methods.entrySet()) for (Method c : e.getValue()) if (other.methods(e.getKey()).contains(c)) r.add(c);
        for (var e : objectMethods.entrySet()) {
            List<String> keep = new ArrayList<>();
            for (String n : e.getValue()) if (other.objectMethods(e.getKey()).contains(n)) keep.add(n);
            if (!keep.isEmpty()) r.addObjectMethods(e.getKey(), keep);
        }
        return r;
    }

    public int size() {
        int n = 0;
        for (var m : List.of(ctors, getters, statics, enums, methods)) for (List<?> l : m.values()) n += l.size();
        return n + objectMethods.size();
    }

    // ───────────────────────── 쓰기·읽기 ─────────────────────────

    public void write(Path out) throws IOException {
        Files.createDirectories(out.toAbsolutePath().getParent());
        Path tmp = out.resolveSibling(out.getFileName() + ".part");
        try (OutputStream os = Files.newOutputStream(tmp);
             GZIPOutputStream gz = new GZIPOutputStream(os);
             BufferedWriter w = new BufferedWriter(new OutputStreamWriter(gz, StandardCharsets.UTF_8))) {
            w.write(MAGIC + "\t" + FORMAT + "\t" + label + "\n");
            for (String c : classNames()) {
                for (Ctor x : ctors(c)) {
                    List<String> steps = new ArrayList<>();
                    for (Step s : x.steps()) steps.add(s.field() + ":" + s.desc() + "=" + s.value().encode());
                    List<String> sa = new ArrayList<>();
                    for (Val v : x.superArgs()) sa.add(v.encode());
                    w.write(String.join("\t", "P", c, x.desc(), x.superOwner(), x.superDesc(), sa.isEmpty() ? "-" : String.join(",", sa),
                            steps.isEmpty() ? "-" : String.join(",", steps)) + "\n");
                }
                for (Getter g : getters(c)) w.write(String.join("\t", "G", c, g.name(), g.desc(), g.field(), g.fieldDesc()) + "\n");
                for (Static s : statics(c)) w.write(String.join("\t", "S", c, s.field(), s.desc(), s.value().encode()) + "\n");
                for (EnumConst e : enumConsts(c)) {
                    String a = "!";
                    if (e.args() != null) {
                        List<String> l = new ArrayList<>();
                        for (Val v : e.args()) l.add(v.encode());
                        a = l.isEmpty() ? "-" : String.join(",", l);
                    }
                    w.write(String.join("\t", "V", c, Integer.toString(e.ordinal()), e.name(), e.ctorDesc(), a) + "\n");
                }
                if (objectMethods.containsKey(c)) w.write("T\t" + c + "\t" + String.join(",", objectMethods.get(c)) + "\n");
                for (Method x : methods(c)) {
                    w.write(String.join("\t", "M", c, x.name(), x.desc(), x.isStatic() ? "s" : "i", String.valueOf(x.kind()), x.value()) + "\n");
                }
            }
        }
        Files.move(tmp, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private List<String> classNames() {
        java.util.TreeSet<String> s = new java.util.TreeSet<>();
        s.addAll(ctors.keySet());
        s.addAll(getters.keySet());
        s.addAll(statics.keySet());
        s.addAll(enums.keySet());
        s.addAll(objectMethods.keySet());
        s.addAll(methods.keySet());
        return new ArrayList<>(s);
    }

    public static FillTable read(Path file) throws IOException {
        try (InputStream in = new GZIPInputStream(Files.newInputStream(file));
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return read(r);
        }
    }

    public static FillTable read(BufferedReader r) throws IOException {
        String head = r.readLine();
        if (head == null || !head.startsWith(MAGIC + "\t")) throw new IOException("채움 표가 아니다");
        String[] h = head.split("\t", -1);
        if (h.length < 3 || !h[1].equals(FORMAT)) throw new IOException("지원하지 않는 채움 표 형식: " + (h.length > 1 ? h[1] : "?"));
        FillTable t = new FillTable(h[2]);
        String line;
        while ((line = r.readLine()) != null) {
            if (line.isEmpty()) continue;
            String[] f = line.split("\t", -1);
            switch (f[0]) {
                case "P" -> {
                    List<Val> sa = new ArrayList<>();
                    if (!f[5].equals("-")) for (String s : f[5].split(",")) sa.add(Val.decode(s));
                    List<Step> steps = new ArrayList<>();
                    if (!f[6].equals("-")) {
                        for (String s : f[6].split(",")) {
                            int c = s.indexOf(':');
                            int eq = s.lastIndexOf('=');
                            // 서술자에 ':' 는 없다. '=' 도 없다.
                            steps.add(new Step(s.substring(0, c), s.substring(c + 1, s.indexOf('=')), Val.decode(s.substring(eq + 1))));
                        }
                    }
                    t.add(new Ctor(f[1], f[2], f[3], f[4], sa, steps));
                }
                case "G" -> t.add(new Getter(f[1], f[2], f[3], f[4], f[5]));
                case "S" -> t.add(new Static(f[1], f[2], f[3], Val.decode(f[4])));
                case "V" -> {
                    List<Val> a = null;
                    if (!f[5].equals("!")) {
                        a = new ArrayList<>();
                        if (!f[5].equals("-")) for (String s : f[5].split(",")) a.add(Val.decode(s));
                    }
                    t.add(new EnumConst(f[1], Integer.parseInt(f[2]), f[3], f[4], a));
                }
                case "T" -> t.addObjectMethods(f[1], List.of(f[2].split(",")));
                case "M" -> t.add(new Method(f[1], f[2], f[3], f[4].equals("s"), f[5].charAt(0), f[6]));
                default -> throw new IOException("알 수 없는 줄: " + line);
            }
        }
        return t;
    }

    static String pct(String s) {
        StringBuilder b = new StringBuilder();
        for (byte x : s.getBytes(StandardCharsets.UTF_8)) {
            int c = x & 0xff;
            if (c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '.' || c == '-') b.append((char) c);
            else b.append('%').append(String.format("%02X", c));
        }
        return b.toString();
    }

    static String unpct(String s) {
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '%') {
                o.write(Integer.parseInt(s.substring(i + 1, i + 3), 16));
                i += 2;
            } else {
                o.write(c);
            }
        }
        return o.toString(StandardCharsets.UTF_8);
    }
}
