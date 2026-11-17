package org.domi.furnace.compat.realworld;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.domi.furnace.compat.nms.NmsCompatReport;
import org.domi.furnace.compat.nms.NmsPaperMembers;
import org.domi.furnace.compat.nms.NmsSymbols;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * NMS shim 점검 도구(2026-11-17): 실제 플러그인 jar 들이 부르는 {@code net.minecraft}·CraftBukkit 심볼 중 shim 에 없는 것을 모아
 * <b>어떤 종류의 구멍인지</b> 가르고, 몇 개 플러그인이 같은 구멍을 만나는지 센다.
 *
 * <p>구분(멤버 기준):
 * <ul>
 *   <li><b>서명 다름</b> — shim 에 같은 이름의 멤버가 있는데 서술자(인자·반환 타입)가 플러그인이 컴파일한 Paper 와 다르다. 링크가 끊기는데 이름만 보면 있어 보이는 가장 헷갈리는 구멍.</li>
 *   <li><b>없음(Paper 에 있음)</b> — shim 에 그 이름이 아예 없고, Paper 1.21.1 멤버 이름표({@link NmsPaperMembers})에는 있다. 진짜 빠진 것.</li>
 *   <li><b>없음(Paper 에도 없음)</b> — 이름표에 그 이름이 없다: 다른 버전용 코드·Spigot 난독화 이름·리플렉션 후보. 구멍이 아니다.</li>
 *   <li><b>표 모름</b> — 소유 클래스가 이름표에 없다(shim 에 있는 클래스인데 이름표를 다시 만들 때 빠진 것 등).</li>
 * </ul>
 * 클래스가 통째로 없는 것(종류 C)·shim 클래스를 상속하는 것(X)은 따로 센다. 정적 분석이라 리플렉션 문자열로만 부르는 것은 보이지 않는다
 * (그쪽은 T-205 의 멤버 이름표 경고가 담당한다). Spigot 난독화 이름 jar(매니페스트에 {@code paperweight-mappings-namespace: mojang} 없음)는 이름 대응표가
 * 필요해 여기서는 세지 않고 표에 "건너뜀"으로 적는다.
 *
 * <p>사용: {@code ./gradlew :furnace-compat-paper:nmsShimAudit -PauditJars=<jar 폴더 또는 경로>} → {@code build/nms-shim-audit.md}, {@code build/nms-shim-audit.tsv}.
 */
public final class NmsShimAudit {

    private NmsShimAudit() {}

    /** 한 심볼 구멍. */
    record Miss(String kind, String owner, String name, String desc) {
        String key() { return kind + "|" + owner + "|" + name + "|" + desc; }
    }

    enum Category {
        SIGNATURE("서명 다름"), MISSING("없음(Paper 에 있음)"), NOT_PAPER("없음(Paper 에도 없음)"), UNKNOWN("표 모름");

        final String label;

        Category(String label) { this.label = label; }
    }

    /** 점검 결과 한 줄(분류까지 끝난 구멍). */
    record Row(Miss miss, Category category, String shimSigs, Set<String> jars) {}

    record JarLine(String jar, String namespace, int symbols, int present, int missingSymbols, boolean skipped) {}

    public static void main(String[] args) throws IOException {
        Path md = Paths.get(args[0]);
        Path tsv = Paths.get(args[1]);
        List<Path> jars = new ArrayList<>();
        for (String p : System.getProperty("furnace.realworld.jars", "").split(java.io.File.pathSeparator)) {
            if (!p.isBlank()) jars.add(Paths.get(p));
        }
        jars.sort(Comparator.comparing(j -> j.getFileName().toString().toLowerCase()));
        Result r = audit(jars);
        String paper = System.getProperty("furnace.audit.paperJar", "");
        if (!paper.isBlank()) r = new Result(r.jars(), r.rows(), compareWithPaper(Paths.get(paper), r));
        Files.writeString(md, render(r), StandardCharsets.UTF_8);
        Files.writeString(tsv, tsv(r), StandardCharsets.UTF_8);
        System.out.println("jar " + jars.size() + "개 → " + md + " , " + tsv);
    }

    /** {@code paper} 는 Paper jar 전수 대조 결과(jar 를 주지 않았으면 null). */
    record Result(List<JarLine> jars, List<Row> rows, PaperCompare paper) {
        Result(List<JarLine> jars, List<Row> rows) { this(jars, rows, null); }
    }

    /** jar 목록을 훑어 구멍을 모으고 분류한다. 서버·Gradle 없이 호출할 수 있다(시험이 이 길을 쓴다). */
    public static Result audit(List<Path> jars) throws IOException {
        Map<String, Miss> misses = new LinkedHashMap<>();
        Map<String, Set<String>> who = new LinkedHashMap<>();
        List<JarLine> lines = new ArrayList<>();
        for (Path jar : jars) {
            String name = jar.getFileName().toString();
            NmsCompatReport.Read read = NmsCompatReport.read(jar);
            if (!read.nmsReference()) {
                lines.add(new JarLine(name, read.namespace(), 0, 0, 0, false));
                continue;
            }
            if (read.needsSpigotMappings()) {
                lines.add(new JarLine(name, read.namespace(), 0, 0, 0, true));
                continue;
            }
            Set<String> mine = new TreeSet<>();
            NmsCompatReport rep = NmsCompatReport.analyze(name, read, NmsSymbols.RUNTIME, null, a -> {
                Miss m = new Miss(a[0], a[1], a[2], a[3]);
                misses.putIfAbsent(m.key(), m);
                who.computeIfAbsent(m.key(), k -> new TreeSet<>()).add(name);
                mine.add(m.key());
            });
            lines.add(new JarLine(name, read.namespace(), rep.symbols(), rep.present(), mine.size(), false));
        }
        List<Row> rows = new ArrayList<>();
        for (Miss m : misses.values()) rows.add(classify(m, who.get(m.key())));
        rows.sort(Comparator.<Row>comparingInt(x -> -x.jars().size())
                .thenComparing(x -> x.category().ordinal()).thenComparing(x -> x.miss().key()));
        return new Result(lines, rows);
    }

    static Row classify(Miss m, Set<String> jars) {
        if (!m.kind().equals("M") && !m.kind().equals("F")) return new Row(m, Category.MISSING, "", jars);
        boolean field = m.kind().equals("F");
        String sigs = shimSignatures(m.owner(), m.name(), field);
        if (!sigs.isEmpty()) return new Row(m, Category.SIGNATURE, sigs, jars);
        if (m.name().equals("<init>")) return new Row(m, Category.MISSING, "", jars);   // 생성자는 이름표에 없다 — 클래스에 있는 이상 진짜 빠진 것
        Boolean paper = field ? NmsPaperMembers.hasField(m.owner(), m.name()) : NmsPaperMembers.hasMethod(m.owner(), m.name());
        if (paper == null) return new Row(m, Category.UNKNOWN, "", jars);
        return new Row(m, paper ? Category.MISSING : Category.NOT_PAPER, "", jars);
    }

    /** shim 클래스 사슬에 이름이 같은 멤버가 있으면 그 서술자들(없으면 빈 문자열). */
    static String shimSignatures(String owner, String name, boolean field) {
        Class<?> c;
        try {
            c = Class.forName(owner.replace('/', '.'), false, NmsShimAudit.class.getClassLoader());
        } catch (Throwable t) {
            return "";
        }
        Set<String> out = new TreeSet<>();
        collect(c, name, field, out, new java.util.HashSet<>());
        return String.join(" ; ", out);
    }

    private static void collect(Class<?> c, String name, boolean field, Set<String> out, Set<Class<?>> seen) {
        if (c == null || !seen.add(c)) return;
        try {
            if (field) {
                for (Field f : c.getDeclaredFields()) if (f.getName().equals(name)) out.add(Type.getDescriptor(f.getType()));
            } else {
                for (Method me : c.getDeclaredMethods()) if (me.getName().equals(name) && !me.isSynthetic()) out.add(Type.getMethodDescriptor(me));
            }
        } catch (Throwable ignored) {
            // 링크 안 되는 클래스는 건너뛴다
        }
        collect(c.getSuperclass(), name, field, out, seen);
        for (Class<?> i : c.getInterfaces()) collect(i, name, field, out, seen);
    }

    // ------------------------------------------------------------------ 출력

    static String simple(String internal) {
        return internal.substring(internal.lastIndexOf('/') + 1);
    }

    static String readable(Miss m) {
        return switch (m.kind()) {
            case "C" -> m.owner().replace('/', '.');
            case "X" -> m.name().replace('/', '.') + " extends " + m.desc().replace('/', '.');
            case "F" -> simple(m.owner()) + "." + m.name() + " : " + Type.getType(m.desc()).getClassName();
            default -> simple(m.owner()) + "." + m.name() + sig(m.desc());
        };
    }

    static String sig(String desc) {
        Type t = Type.getMethodType(desc);
        StringBuilder b = new StringBuilder("(");
        Type[] a = t.getArgumentTypes();
        for (int i = 0; i < a.length; i++) b.append(i == 0 ? "" : ", ").append(shortName(a[i]));
        return b.append(") → ").append(shortName(t.getReturnType())).toString();
    }

    static String shortName(Type t) {
        String n = t.getClassName();
        return n.substring(n.lastIndexOf('.') + 1);
    }

    static String render(Result r) {
        StringBuilder b = new StringBuilder();
        b.append("# NMS shim 점검 결과 (자동 생성 — NmsShimAudit)\n\n");
        long n = r.jars().stream().filter(j -> j.symbols() > 0).count();
        b.append("점검한 jar ").append(r.jars().size()).append("개 중 Mojang 이름으로 NMS 를 직접 부르는 것 ").append(n).append("개. 건너뜀(Spigot 이름 대응표 필요) ")
                .append(r.jars().stream().filter(JarLine::skipped).count()).append("개.\n\n");
        b.append("| jar | 이름 체계 | 심볼 | 있음 | 없는 심볼 |\n|---|---|--:|--:|--:|\n");
        for (JarLine j : r.jars()) {
            b.append("| ").append(j.jar()).append(" | ").append(j.namespace()).append(" | ");
            if (j.skipped()) b.append("건너뜀 | | |\n");
            else b.append(j.symbols()).append(" | ").append(j.present()).append(" | ").append(j.missingSymbols()).append(" |\n");
        }
        Map<Category, Integer> cat = new TreeMap<>();
        int classes = 0, extend = 0;
        for (Row x : r.rows()) {
            if (x.miss().kind().equals("C")) classes++;
            else if (x.miss().kind().equals("X")) extend++;
            else cat.merge(x.category(), 1, Integer::sum);
        }
        b.append("\n## 요약\n\n| 종류 | 서로 다른 심볼 수 |\n|---|--:|\n");
        b.append("| 클래스 통째로 없음 | ").append(classes).append(" |\n| shim 클래스를 상속(정의 거부) | ").append(extend).append(" |\n");
        for (Category c : Category.values()) b.append("| 멤버 — ").append(c.label).append(" | ").append(cat.getOrDefault(c, 0)).append(" |\n");

        section(b, "클래스 통째로 없음", r, x -> x.miss().kind().equals("C"), 60);
        section(b, "서명 다름 (shim 에 같은 이름이 있는데 서술자가 다르다)", r, x -> x.category() == Category.SIGNATURE, 80);
        section(b, "멤버 없음 (Paper 1.21.1 에는 있다)", r, x -> x.category() == Category.MISSING && !x.miss().kind().equals("C"), 120);
        section(b, "상속(정의 거부)", r, x -> x.miss().kind().equals("X"), 30);
        if (r.paper() != null) renderPaper(b, r);
        b.append("\n전체 목록은 같은 이름의 `.tsv` 파일에 있다. \"Paper 에도 없음\"·\"표 모름\"은 위에 싣지 않았다.\n");
        return b.toString();
    }

    private static void section(StringBuilder b, String title, Result r, java.util.function.Predicate<Row> f, int limit) {
        List<Row> rows = r.rows().stream().filter(f).toList();
        b.append("\n## ").append(title).append(" — ").append(rows.size()).append("건\n\n");
        if (rows.isEmpty()) return;
        b.append("| 심볼 | 플러그인 수 | shim 서명 | 쓰는 jar |\n|---|--:|---|---|\n");
        int shown = 0;
        for (Row x : rows) {
            if (shown++ >= limit) break;
            String jars = x.jars().size() <= 3 ? String.join(", ", x.jars()) : String.join(", ", x.jars().stream().limit(3).toList()) + " 외";
            b.append("| `").append(readable(x.miss())).append("` | ").append(x.jars().size()).append(" | ")
                    .append(x.shimSigs().isEmpty() ? "" : "`" + x.shimSigs().replace("|", "\\|") + "`").append(" | ").append(jars).append(" |\n");
        }
        if (rows.size() > limit) b.append("\n(위 ").append(limit).append("건만 — 나머지는 .tsv)\n");
    }

    // ------------------------------------------------------------------ Paper jar 전수 대조

    /** Paper 클래스 하나의 공개·보호 멤버(이름 + 서술자). */
    record PClass(String name, boolean isInterface, Set<String> fields, Set<String> methods) {}

    /** shim 에 있는 Paper 클래스 하나의 대조 결과. */
    record ClassGap(String owner, int paperMethods, int paperFields, List<String> missingMethods, List<String> missingFields,
                    List<String> signatureMethods, List<String> signatureFields, int usedGaps) {
        int gaps() { return missingMethods.size() + missingFields.size() + signatureMethods.size() + signatureFields.size(); }
    }

    record PaperCompare(int paperClasses, int shimClasses, List<String> classesMissing, List<ClassGap> gaps, int shimOnlyMembers) {}

    /**
     * Paper 1.21.1 jar(Mojang 이름, paperclip 이 만드는 {@code versions/1.21.1/paper-1.21.1.jar}) 의 {@code net.minecraft}·CraftBukkit 클래스를 읽어 shim 과 견준다.
     * shim 에 있는 클래스마다 Paper 가 선언한 공개·보호 멤버가 shim 에서(클래스 사슬을 따라) 같은 서술자로 풀리는지 본다: 없으면 "없음", 같은 이름이 다른 서술자로
     * 있으면 "서명 다름"(링크가 끊긴다). jar 는 읽기만 한다(실행하지 않는다).
     */
    static PaperCompare compareWithPaper(Path paperJar, Result usage) throws IOException {
        Map<String, PClass> paper = readPaper(paperJar);
        Set<String> used = new HashSet<>();
        for (Row x : usage.rows()) used.add(x.miss().owner() + "#" + x.miss().name() + x.miss().desc());
        NmsSymbols idx = NmsSymbols.RUNTIME;
        List<String> classesMissing = new ArrayList<>();
        List<ClassGap> gaps = new ArrayList<>();
        int shimClasses = 0;
        for (PClass pc : paper.values()) {
            if (!idx.hasClass(pc.name())) {
                classesMissing.add(pc.name());
                continue;
            }
            shimClasses++;
            List<String> mm = new ArrayList<>(), mf = new ArrayList<>(), sm = new ArrayList<>(), sf = new ArrayList<>();
            int usedGaps = 0;
            for (String m : pc.methods()) {
                int sp = m.indexOf(' ');
                String name = m.substring(0, sp), desc = m.substring(sp + 1);
                if (idx.hasMethod(pc.name(), name, desc)) continue;
                if (used.contains(pc.name() + "#" + name + desc)) usedGaps++;
                String shim = name.equals("<init>") ? "" : shimSignatures(pc.name(), name, false);
                (shim.isEmpty() ? mm : sm).add(name + sig(desc) + (shim.isEmpty() ? "" : "   [shim: " + shim + "]"));
            }
            for (String f : pc.fields()) {
                int sp = f.indexOf(' ');
                String name = f.substring(0, sp), desc = f.substring(sp + 1);
                if (idx.hasField(pc.name(), name, desc)) continue;
                if (used.contains(pc.name() + "#" + name + desc)) usedGaps++;
                String shim = shimSignatures(pc.name(), name, true);
                (shim.isEmpty() ? mf : sf).add(name + " : " + Type.getType(desc).getClassName() + (shim.isEmpty() ? "" : "   [shim: " + shim + "]"));
            }
            gaps.add(new ClassGap(pc.name(), pc.methods().size(), pc.fields().size(), mm, mf, sm, sf, usedGaps));
        }
        classesMissing.sort(null);
        gaps.sort(Comparator.<ClassGap>comparingInt(g -> -(g.signatureMethods().size() + g.signatureFields().size()))
                .thenComparingInt(g -> -g.usedGaps()).thenComparingInt(g -> -g.gaps()).thenComparing(ClassGap::owner));
        return new PaperCompare(paper.size(), shimClasses, classesMissing, gaps, 0);
    }

    static Map<String, PClass> readPaper(Path jar) throws IOException {
        Map<String, PClass> out = new TreeMap<>();
        try (java.util.jar.JarFile jf = new java.util.jar.JarFile(jar.toFile())) {
            var en = jf.entries();
            while (en.hasMoreElements()) {
                var e = en.nextElement();
                String n = e.getName();
                if (!n.endsWith(".class") || !(n.startsWith("net/minecraft/") || n.startsWith("org/bukkit/craftbukkit/"))) continue;
                byte[] b;
                try (var in = jf.getInputStream(e)) { b = in.readAllBytes(); }
                PClass pc = readClass(b);
                if (pc != null) out.put(pc.name(), pc);
            }
        }
        return out;
    }

    /** 공개·보호 멤버만(합성·브리지 제외). 공개 클래스가 아니어도 읽는다 — 플러그인이 접근 가능한 건 공개 멤버뿐이라 멤버 가시성만 거른다. */
    static PClass readClass(byte[] bytes) {
        Set<String> fields = new TreeSet<>(), methods = new TreeSet<>();
        String[] name = new String[1];
        boolean[] itf = new boolean[1];
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(int version, int access, String n, String sig, String sup, String[] itfs) {
                name[0] = n;
                itf[0] = (access & Opcodes.ACC_INTERFACE) != 0;
            }

            @Override
            public FieldVisitor visitField(int access, String n, String desc, String sig, Object value) {
                if ((access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0 && (access & Opcodes.ACC_SYNTHETIC) == 0) fields.add(n + " " + desc);
                return null;
            }

            @Override
            public MethodVisitor visitMethod(int access, String n, String desc, String sig, String[] ex) {
                if ((access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0 && (access & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE)) == 0
                        && !n.equals("<clinit>")) methods.add(n + " " + desc);
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return name[0] == null ? null : new PClass(name[0], itf[0], fields, methods);
    }

    static void renderPaper(StringBuilder b, Result r) {
        PaperCompare p = r.paper();
        b.append("\n## Paper jar 전수 대조\n\nPaper 의 NMS·CraftBukkit 클래스 ").append(p.paperClasses()).append("개 중 shim 에 있는 것 ").append(p.shimClasses())
                .append("개, 없는 것 ").append(p.classesMissing().size()).append("개.\n\n");
        int sigs = p.gaps().stream().mapToInt(g -> g.signatureMethods().size() + g.signatureFields().size()).sum();
        int miss = p.gaps().stream().mapToInt(g -> g.missingMethods().size() + g.missingFields().size()).sum();
        b.append("shim 에 있는 클래스의 Paper 공개·보호 멤버 중 **서명 다름 ").append(sigs).append("건**(같은 이름이 있으나 서술자가 다르다), 없음 ").append(miss).append("건.\n\n");
        b.append("### 서명 다름 (전부)\n\n");
        for (ClassGap g : p.gaps()) {
            if (g.signatureMethods().isEmpty() && g.signatureFields().isEmpty()) continue;
            b.append("- `").append(g.owner().replace('/', '.')).append("`\n");
            for (String s : g.signatureMethods()) b.append("  - 메서드 `").append(s).append("`\n");
            for (String s : g.signatureFields()) b.append("  - 필드 `").append(s).append("`\n");
        }
        b.append("\n### 클래스별 빠진 멤버 수 (상위 60 — 플러그인이 쓴 것이 많은 순)\n\n| 클래스 | Paper 메서드 | 필드 | 메서드 없음 | 필드 없음 | 코퍼스가 쓴 구멍 |\n|---|--:|--:|--:|--:|--:|\n");
        p.gaps().stream().sorted(Comparator.<ClassGap>comparingInt(g -> -g.usedGaps()).thenComparingInt(g -> -g.gaps())).limit(60).forEach(g ->
                b.append("| `").append(g.owner().replace('/', '.')).append("` | ").append(g.paperMethods()).append(" | ").append(g.paperFields()).append(" | ")
                        .append(g.missingMethods().size()).append(" | ").append(g.missingFields().size()).append(" | ").append(g.usedGaps()).append(" |\n"));
        b.append("\n### shim 에 없는 Paper 클래스 (패키지별 개수)\n\n");
        Map<String, Integer> byPkg = new TreeMap<>();
        for (String c : p.classesMissing()) {
            if (c.contains("$")) continue;
            byPkg.merge(c.substring(0, c.lastIndexOf('/')).replace('/', '.'), 1, Integer::sum);
        }
        byPkg.entrySet().stream().sorted((x, y) -> y.getValue() - x.getValue()).limit(40)
                .forEach(e -> b.append("- `").append(e.getKey()).append("` ").append(e.getValue()).append("개\n"));
    }

    static String tsv(Result r) {
        StringBuilder b = new StringBuilder("분류\t종류\t소유\t이름\t서술자\tshim서명\t플러그인수\tjar\n");
        for (Row x : r.rows()) {
            b.append(x.category().label).append('\t').append(x.miss().kind()).append('\t').append(x.miss().owner()).append('\t')
                    .append(x.miss().name()).append('\t').append(x.miss().desc()).append('\t').append(x.shimSigs()).append('\t')
                    .append(x.jars().size()).append('\t').append(String.join(",", x.jars())).append('\n');
        }
        return b.toString();
    }
}
