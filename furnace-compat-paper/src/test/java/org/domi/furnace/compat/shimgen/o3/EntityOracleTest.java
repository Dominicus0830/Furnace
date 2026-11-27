package org.domi.furnace.compat.shimgen.o3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * T-263 S3 월드 밖 엔티티 정답기(O3, 설계 3.3). 연결 줄(links-&lt;판&gt;/*.links)이 엔티티 클래스에 단 멤버마다, 바닐라(가짜 레벨의 월드 밖 엔티티)와
 * shim(실행 때와 같은 로더의 월드 밖 엔티티)에 같은 무작위 상태(칸 값)·같은 인자를 넣고 불러 반환값과 바뀐 칸을 견준다.
 *
 * <p>상태는 바이트·정수·실수·참거짓·열거형 칸만 무작위로 채운다(그 밖 칸은 처음 값 그대로 — 바뀐 칸만 견주므로 처음 값 차이는 섞이지 않는다).
 * 바닐라가 상태 넣기·부르기에서 던지면 그 경우는 "견줄 수 없음"으로 센다. 멤버마다 바닐라 결과가 상태에 따라 갈리지 않으면(늘 같은 답)
 * "구분력 없음"으로 센다(설계 3.4-2) — 그 멤버는 이 정답기로 증명된 것이 아니다.
 * 결과에 비교할 수 없는 값(OPAQUE)이 있으면 증명으로 세지 않는다.
 * 돌연변이 카나리: 같은 종류·서술자의 증명된 멤버 둘을 엇갈려 부르는 "틀린 연결"을 정답기가 늘 잡아야 한다.
 * CI 로그에는 숫자만, 이름은 실패 메시지에만.
 */
@Tag("full-shells")
class EntityOracleTest {
    static final int VARIANTS = 12;
    static final String UNSUPPORTED = "THREW:org.domi.furnace.compat.nms.UnsupportedNmsCall";
    static final float[] FLOATS = {0.0F, 0.5F, 1.0F, 2.5F, 20.0F, -1.0F};
    static final String[] ITEMS = {"minecraft:stone", "minecraft:diamond", "minecraft:egg", "minecraft:fire_charge"};
    static final String[] BLOCKS = {"minecraft:stone", "minecraft:sand", "minecraft:tnt", "minecraft:air"};
    static final String[] TEXTS = {"a", "Hello", "x y"};

    static String pos(Random rnd) { return (rnd.nextInt(33) - 16) + "," + (rnd.nextInt(33) - 64) + "," + (rnd.nextInt(33) - 16); }

    record Member(String owner, String name, String desc, boolean write) {}

    /** 증명된 멤버(카나리 짝 고르기용) — 이름은 실행 중에 푼 실제 이름. */
    record Proved(String owner, String name, String desc, String type, String slots) {}

    /** 탐침의 비교할 수 없는 값 표기(두 탐침의 OPAQUE 와 같다). */
    static final String OPAQUE = "<?>";

    @Test
    void linkedEntityMembersMatchVanilla() throws Exception {
        Path tmp = Files.createTempDirectory("o3-vanilla");
        List<URL> vanUrls = new ArrayList<>();
        List<String> vanPaths = new ArrayList<>();
        for (String line : Files.readAllLines(Path.of(System.getProperty("furnace.vdiff.classpath")), StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            vanUrls.add(Path.of(line.trim()).toUri().toURL());
            vanPaths.add(line.trim());
        }
        compileVanillaProbe(tmp, vanPaths);
        vanUrls.add(0, tmp.toUri().toURL());
        // shim: 실행 때처럼 껍데기 jar 가 앞, 그 뒤 시험 실행 클래스패스(호환 계층·서버·시험 서버) — 이 클래스도 그 로더에서 따로 올린다
        List<URL> shimUrls = new ArrayList<>();
        shimUrls.add(Path.of(System.getProperty("furnace.fullShells.jar")).toUri().toURL());
        for (String p : System.getProperty("java.class.path").split(File.pathSeparator)) if (!p.isBlank()) shimUrls.add(Path.of(p).toUri().toURL());

        Map<String, Member> members = members(Path.of("src/shimspec/links-" + System.getProperty("furnace.vdiff.mc", "1.21.1")));
        int opaque = 0;
        int same = 0, differ = 0, vanillaThrew = 0, shimThrew = 0, shimUnsupported = 0, unsupportedMembers = 0, noType = 0, privateUnresolved = 0, undiscriminating = 0, canaryCaught = 0, canaryMissed = 0, provedMembers = 0;
        List<String> failures = new ArrayList<>();
        // 이름 들어간 표는 build/ 에만(멤버마다 결과)
        StringBuilder table = new StringBuilder("# T-263 O3 entity oracle (member results, names - build output only)\n\n| member | type | result |\n|---|---|---|\n");
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader van = new URLClassLoader("vanilla", vanUrls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader());
             URLClassLoader shim = new URLClassLoader("shim", shimUrls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader())) {
            Class<?> vp = Class.forName("furnaceo3.VanillaEntityProbe", true, van);
            Class<?> sp = Class.forName(ShimEntityProbe.class.getName(), true, shim);
            assertTrue(sp.getClassLoader() == shim, "shim probe must come from the isolated loader");
            java.io.PrintStream out = System.out, err = System.err;
            Thread.currentThread().setContextClassLoader(van);
            try {
                vp.getMethod("init").invoke(null);
            } finally {
                System.setOut(out);   // 바닐라 Bootstrap 이 표준 출력을 자기 로거로 바꾼다
                System.setErr(err);
            }
            Thread.currentThread().setContextClassLoader(shim);
            sp.getMethod("init").invoke(null);
            try {
                Method vTypeFor = vp.getMethod("typeFor", String.class, String.class, String.class), vSlots = vp.getMethod("slots", String.class);
                Method vProbe = vp.getMethod("probe", String.class, String.class, String.class, String.class, String.class, String.class);
                Method sProbe = sp.getMethod("probe", String.class, String.class, String.class, String.class, String.class, String.class);
                Map<String, String> typeOf = new HashMap<>(), slotsOf = new HashMap<>();
                List<Proved> proved = new ArrayList<>();
                for (Member m0 : members.values()) {
                    Class<?> vc;
                    try {
                        vc = Class.forName(m0.owner(), false, van);
                    } catch (ClassNotFoundException x) {
                        failures.add(m0.owner() + ": no vanilla class");
                        continue;
                    }
                    if (!Class.forName("net.minecraft.world.entity.Entity", false, van).isAssignableFrom(vc)) continue;
                    String name = m0.name();
                    if (name.startsWith("#")) {
                        name = resolve(vc, name, m0.desc());
                        if (name == null) {
                            privateUnresolved++;
                            continue;
                        }
                    }
                    Thread.currentThread().setContextClassLoader(van);
                    String fname = name;
                    String type = typeOf.computeIfAbsent(m0.owner() + "." + name + m0.desc(), k -> {
                        try {
                            return (String) vTypeFor.invoke(null, m0.owner(), fname, m0.desc());
                        } catch (ReflectiveOperationException e) {
                            throw new IllegalStateException(e);
                        }
                    });
                    if (type.equals("-")) {
                        noType++;
                        continue;
                    }
                    String slots = slotsOf.computeIfAbsent(type, t -> call(vSlots, t));
                    Random rnd = new Random((m0.owner() + "." + name + m0.desc()).hashCode());
                    Set<String> vanillaResults = new LinkedHashSet<>();
                    List<String[]> pairs = new ArrayList<>();
                    boolean unsupported = false;
                    String wrong = null, why = "";
                    for (int v = 0; v < VARIANTS; v++) {
                        String state = state(slots, rnd), args = args(m0.desc(), rnd);
                        Thread.currentThread().setContextClassLoader(van);
                        String vr = (String) vProbe.invoke(null, type, m0.owner(), name, m0.desc(), state, args);
                        if (vr.startsWith("SKIP") || vr.startsWith("THREW")) {
                            vanillaThrew++;
                            continue;
                        }
                        Thread.currentThread().setContextClassLoader(shim);
                        String sr = (String) sProbe.invoke(null, type, m0.owner(), name, m0.desc(), state, args);
                        vanillaResults.add(vr);
                        pairs.add(new String[] {vr, sr});
                        if (sr.equals(vr)) same++;
                        else if (sr.startsWith(UNSUPPORTED)) {
                            // 바닐라는 값을 내는데 shim 은 "아직 지원 안 함"으로 던진다 — 틀린 값은 아니지만 증명도 아니다(멤버를 증명에서 뺀다)
                            shimUnsupported++;
                            unsupported = true;
                            why = sr.substring(UNSUPPORTED.length());
                        } else {
                            if (sr.startsWith("THREW") || sr.startsWith("NO") || sr.startsWith("SKIP")) shimThrew++;
                            else differ++;
                            String f = m0.owner() + "." + name + m0.desc() + " type=" + type + " state=" + state + " args=" + args + ": vanilla=" + vr + " shim=" + sr;
                            if (wrong == null) wrong = f;
                            if (failures.size() < 40) failures.add(f);
                        }
                    }
                    String label = m0.owner() + "." + name + m0.desc();
                    if (wrong != null) {
                        table.append("| ").append(label).append(" | ").append(type).append(" | DIFFERS |\n");
                        continue;
                    }
                    if (unsupported) {
                        unsupportedMembers++;
                        table.append("| ").append(label).append(" | ").append(type).append(" | shim unsupported off-world").append(why.replace('|', '/').replace('\n', ' ')).append(" |\n");
                        continue;
                    }
                    if (vanillaResults.stream().anyMatch(r -> r.contains(OPAQUE))) {
                        // 결과에 비교할 수 없는 값이 있으면 같아도 증명이 아니다(검토 권고)
                        opaque++;
                        table.append("| ").append(label).append(" | ").append(type).append(" | same, but holds a value the oracle cannot compare |\n");
                        continue;
                    }
                    if (vanillaResults.size() < 2) {
                        undiscriminating++;
                        table.append("| ").append(label).append(" | ").append(type).append(" | ").append(pairs.isEmpty() ? "vanilla not comparable" : "same, undiscriminating").append(" |\n");
                        continue;
                    }
                    table.append("| ").append(label).append(" | ").append(type).append(" | proved (").append(vanillaResults.size()).append(" distinct) |\n");
                    provedMembers++;
                    proved.add(new Proved(m0.owner(), name, m0.desc(), type, slots));
                }
                // 돌연변이 카나리(검토 권고): 일부러 틀린 연결 — 같은 엔티티 종류·같은 서술자의 증명된 멤버 A 자리에 shim 은 다른 멤버 B 의 연결 몸통을
                // 부른다(엉뚱한 칸에 이은 줄과 같다). 바닐라 A·B 결과가 어느 상태에서든 갈리면, 정답기는 바닐라 A 와 shim B 가 다르다고 해야 한다.
                Map<String, List<Proved>> groups = new TreeMap<>();
                for (Proved p : proved) groups.computeIfAbsent(p.type() + " " + p.desc(), k -> new ArrayList<>()).add(p);
                for (List<Proved> g : groups.values()) {
                    for (int i = 0; i + 1 < g.size(); i++) {
                        Proved a = g.get(i), b = g.get(i + 1);
                        Random rnd = new Random((a.owner() + a.name() + "/" + b.owner() + b.name()).hashCode());
                        boolean distinguishable = false, caught = false;
                        for (int v = 0; v < VARIANTS; v++) {
                            String state = state(a.slots(), rnd), args = args(a.desc(), rnd);
                            Thread.currentThread().setContextClassLoader(van);
                            String va = (String) vProbe.invoke(null, a.type(), a.owner(), a.name(), a.desc(), state, args);
                            String vb = (String) vProbe.invoke(null, b.type(), b.owner(), b.name(), b.desc(), state, args);
                            if (va.startsWith("SKIP") || va.startsWith("THREW") || vb.startsWith("SKIP") || vb.startsWith("THREW") || va.equals(vb)) continue;
                            distinguishable = true;
                            Thread.currentThread().setContextClassLoader(shim);
                            String sb = (String) sProbe.invoke(null, b.type(), b.owner(), b.name(), b.desc(), state, args);
                            if (!sb.equals(va)) caught = true;
                        }
                        if (!distinguishable) continue;
                        if (caught) canaryCaught++;
                        else {
                            canaryMissed++;
                            failures.add("mutation canary missed: " + a.owner() + "." + a.name() + a.desc() + " linked to " + b.owner() + "." + b.name());
                        }
                    }
                }
            } finally {
                Thread.currentThread().setContextClassLoader(shim);
                sp.getMethod("close").invoke(null);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
        Path report = Path.of(System.getProperty("furnace.o3.report", "build/reports/entity-oracle/report.md"));
        Files.createDirectories(report.getParent());
        Files.writeString(report, table.toString(), StandardCharsets.UTF_8);
        System.out.println("[entity-oracle] members=" + members.size() + " proved=" + provedMembers + " undiscriminating=" + undiscriminating + " noType=" + noType
                + " shimUnsupportedMembers=" + unsupportedMembers + " (cases " + shimUnsupported + ")"
                + " privateUnresolved=" + privateUnresolved + " cases: same=" + same + " differ=" + differ + " shimThrew=" + shimThrew + " vanillaNotComparable=" + vanillaThrew
                + " opaque=" + opaque + " mutation canary caught=" + canaryCaught + " missed=" + canaryMissed);
        assertEquals(List.of(), failures, "linked entity members must match vanilla on off-world entities");
        // 증명 수가 조용히 줄면 잡는다(2026-11-27 측정: 249 멤버 중 증명 211)
        assertTrue(provedMembers >= 200, "members proved by the oracle: " + provedMembers);
        assertEquals(0, canaryMissed, "mutation canary: a wrongly linked member must not compare equal");
        assertTrue(canaryCaught >= 30, "mutation canaries that could be told apart: " + canaryCaught);
    }

    static String call(Method m, String a) {
        try {
            return (String) m.invoke(null, a);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 칸 표("번호:종류,…")에서 무작위 상태("번호=값;…"). */
    static String state(String slots, Random rnd) {
        StringBuilder sb = new StringBuilder();
        for (String s : slots.split(",")) {
            String[] p = s.split(":", 2);
            String v = value(p[1], rnd);
            if (v == null) continue;
            if (sb.length() > 0) sb.append(';');
            sb.append(p[0]).append('=').append(v);
        }
        // 자리·방향(별칭이 읽는 위치 게터용) — 0.25 단위, 방향은 바닐라가 거르지 않는 범위
        if (sb.length() > 0) sb.append(';');
        sb.append("P=").append(rnd.nextInt(64) * 0.25 - 8).append(',').append(rnd.nextInt(64) * 0.25 - 60).append(',').append(rnd.nextInt(64) * 0.25 - 8)
                .append(',').append(rnd.nextInt(8) * 45.0F - 180.0F).append(',').append(rnd.nextInt(7) * 15.0F - 45.0F);
        return sb.toString();
    }

    static String value(String kind, Random rnd) {
        return switch (kind.startsWith("enum:") ? "enum" : kind) {
            case "byte" -> String.valueOf(rnd.nextInt(256) - 128);
            case "int" -> String.valueOf(rnd.nextInt(16));
            case "float" -> String.valueOf(FLOATS[rnd.nextInt(FLOATS.length)]);
            case "boolean" -> String.valueOf(rnd.nextBoolean());
            case "enum" -> String.valueOf(rnd.nextInt(Integer.parseInt(kind.substring(kind.lastIndexOf(':') + 1))));
            case "item" -> "item:" + ITEMS[rnd.nextInt(ITEMS.length)] + "*" + (1 + rnd.nextInt(3));
            case "block" -> "block:" + BLOCKS[rnd.nextInt(BLOCKS.length)];
            case "pos" -> "pos:" + pos(rnd);
            case "opos" -> "opos:" + (rnd.nextInt(3) == 0 ? "-" : pos(rnd));
            case "text" -> "text:" + TEXTS[rnd.nextInt(TEXTS.length)];
            case "otext" -> "otext:" + (rnd.nextInt(3) == 0 ? "-" : TEXTS[rnd.nextInt(TEXTS.length)]);
            default -> null;
        };
    }

    /** 인자: 원시형·열거형(서수, 받는 쪽이 개수로 나머지)·아이템 더미·블록 상태·블록 자리·글. 그 밖은 받는 쪽이 못 풀어 SKIP. */
    static String args(String desc, Random rnd) {
        org.objectweb.asm.Type[] ts = org.objectweb.asm.Type.getArgumentTypes(desc);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ts.length; i++) {
            String v = switch (ts[i].getSort()) {
                case org.objectweb.asm.Type.BOOLEAN -> value("boolean", rnd);
                case org.objectweb.asm.Type.BYTE -> value("byte", rnd);
                case org.objectweb.asm.Type.INT, org.objectweb.asm.Type.SHORT, org.objectweb.asm.Type.LONG -> value("int", rnd);
                case org.objectweb.asm.Type.FLOAT, org.objectweb.asm.Type.DOUBLE -> value("float", rnd);
                case org.objectweb.asm.Type.OBJECT -> switch (ts[i].getInternalName()) {
                    case "net/minecraft/world/item/ItemStack" -> value("item", rnd);
                    case "net/minecraft/world/level/block/state/BlockState" -> value("block", rnd);
                    case "net/minecraft/core/BlockPos" -> value("pos", rnd);
                    case "net/minecraft/network/chat/Component" -> value("text", rnd);
                    default -> String.valueOf(rnd.nextInt(64));   // 열거형이면 서수, 아니면 받는 쪽이 못 풀어 SKIP
                };
                default -> String.valueOf(rnd.nextInt(64));
            };
            if (i > 0) sb.append(';');
            sb.append(v);
        }
        return sb.toString();
    }

    /** private 멤버 지문(#SHA-256("a.b.C.이름") 앞 10자리)을 바닐라 클래스의 선언 메서드에서 푼다. 이름은 실행 중에만 쓴다. */
    static String resolve(Class<?> vc, String fp, String desc) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        for (Method m : vc.getDeclaredMethods()) {
            if (!org.objectweb.asm.Type.getMethodDescriptor(m).equals(desc)) continue;
            String h = HexFormat.of().formatHex(md.digest((vc.getName() + "." + m.getName()).getBytes(StandardCharsets.UTF_8))).substring(0, 10);
            if (("#" + h).equals(fp)) return m.getName();
        }
        return null;
    }

    /** 연결 파일 전부에서 (소유 클래스, 멤버) — 대상이 무엇이든(관용구·별칭·손 줄). */
    static Map<String, Member> members(Path dir) throws Exception {
        Map<String, Member> out = new TreeMap<>();
        List<Path> files;
        try (var s = Files.list(dir)) {
            files = s.filter(p -> p.getFileName().toString().endsWith(".links")).sorted().toList();
        }
        for (Path f : files) {
            String owner = null;
            for (String raw : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                String line = raw.contains(" #") ? raw.substring(0, raw.indexOf(" #")) : raw;
                if (line.startsWith("#") || line.isBlank()) continue;
                if (line.startsWith("class ")) {
                    owner = line.substring(6).trim();
                    continue;
                }
                String t = line.trim();
                int arrow = t.indexOf(" -> ");
                if (owner == null || arrow < 0) continue;
                String sig = t.substring(0, arrow);
                int paren = sig.indexOf('(');
                boolean write = t.substring(arrow).contains(" write");
                out.put(owner + "." + sig, new Member(owner, sig.substring(0, paren), sig.substring(paren), write));
            }
        }
        return out;
    }

    static void compileVanillaProbe(Path outDir, List<String> classpath) throws Exception {
        Path src = Files.createDirectories(outDir.resolve("src/furnaceo3")).resolve("VanillaEntityProbe.java");
        try (var in = EntityOracleTest.class.getResourceAsStream("/o3/VanillaEntityProbe.java")) {
            Files.write(src, in.readAllBytes());
        }
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        java.io.ByteArrayOutputStream log = new java.io.ByteArrayOutputStream();
        int rc = javac.run(null, log, log, "-nowarn", "-encoding", "UTF-8", "--release", "21", "-proc:none",
                "-cp", String.join(File.pathSeparator, classpath), "-d", outDir.toString(), src.toString());
        assertEquals(0, rc, "vanilla probe does not compile against the vanilla jar:\n" + log.toString(StandardCharsets.UTF_8));
    }
}
