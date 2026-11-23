// T-264: CraftBukkit 계층 잠금. Paper 서버 서명표에서 org/bukkit/craftbukkit 클래스의 선언(접근·상위·직접 인터페이스)과 구실(API 구현·정적 도구·내부)만
// 뽑아 커밋하는 잠금 파일로 쓴다 — 빌드·시험은 이 파일만 읽고(서명표 없이 돈다), CI vanilla-gates 가 서명표와 같은지 본다(craftLockCheck).
// 이름은 CraftBukkit·Bukkit·Paper 것뿐이다. Mojang 이름(net/minecraft·com/mojang)은 지문(nms: + sha256 앞 16자리)으로만 적는다(검토 1).
// 서명(이름·상속)만 옮기고 CraftBukkit 의 몸통·원문은 읽지 않는다(서명표에 몸통이 없다).
package org.domi.furnace.compat.shimgen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.domi.furnace.tools.extract.SigTable;
import org.objectweb.asm.Opcodes;

public final class CraftHierarchyLock {
    public static final String PREFIX = "org/bukkit/craftbukkit/";
    static final String HEADER = "# T-264 CraftBukkit hierarchy lock - names only, Mojang names as nms:<sha256/16>. Rewrite: ./gradlew -PskipOracle=true :furnace-compat-paper:craftLock\n"
            + "# K <class> <access hex (inner access if nested)> <super> <interfaces, or -> <role: impl|static|internal|paper-internal>\n";

    /** 잠금 한 줄. */
    public record Entry(String name, int access, String superName, List<String> interfaces, String role) {
        String line() {
            return "K " + name + " " + Integer.toHexString(access) + " " + superName + " " + (interfaces.isEmpty() ? "-" : String.join(",", interfaces)) + " " + role;
        }
    }

    private CraftHierarchyLock() {}

    /** Mojang 이름이면 지문, 아니면 그대로. */
    public static String publicName(String internalName) {
        if (internalName == null) return "-";
        if (!isMojang(internalName)) return internalName;
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(internalName.getBytes(StandardCharsets.UTF_8));
            return "nms:" + HexFormat.of().formatHex(h).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Paper 서버에만 있는 Paper 내부 형식(API 아님·CraftBukkit 아님). */
    static boolean isPaperInternal(String n, SigTable paper, Set<String> apiClasses) {
        return (n.startsWith("io/papermc/") || n.startsWith("com/destroystokyo/")) && !apiClasses.contains(n) && paper.classes().containsKey(n);
    }

    public static boolean isMojang(String internalName) {
        return internalName.startsWith("net/minecraft/") || internalName.startsWith("com/mojang/");
    }

    /** 서명표 두 개(Paper 서버·paper-api)에서 잠금 항목을 만든다(이름 순). */
    public static Map<String, Entry> resolve(SigTable paper, SigTable api) {
        Map<String, SigTable.Cls> all = new LinkedHashMap<>(api.classes());
        all.putAll(paper.classes());
        Set<String> apiClasses = api.classes().keySet();
        Map<String, Entry> out = new TreeMap<>();
        List<String> todo = new ArrayList<>();
        for (SigTable.Cls c : paper.classes().values()) if (c.name().startsWith(PREFIX)) todo.add(c.name());
        // Craft 계층이 기대는 Paper 내부 형식(com/destroystokyo/paper/entity/CraftRangedEntity·io/papermc/paper/entity/PaperShearable 등 —
        // paper-api 에 없는 Paper 서버 클래스)도 같은 줄로 적는다(구실 paper-internal). 그래야 생성기가 Paper 와 같은 사슬을 세운다.
        while (!todo.isEmpty()) {
            String n = todo.remove(todo.size() - 1);
            if (out.containsKey(n)) continue;
            SigTable.Cls c = paper.classes().get(n);
            if (c == null) continue;
            List<String> itf = new ArrayList<>();
            for (String i : c.interfaces()) itf.add(publicName(i));
            String role = n.startsWith(PREFIX) ? role(c, paper, apiClasses, all) : "paper-internal";
            out.put(n, new Entry(n, c.effectiveAccess(), publicName(c.superName()), itf, role));
            List<String> refs = new ArrayList<>(c.interfaces());
            if (c.superName() != null) refs.add(c.superName());
            for (String r : refs) if (isPaperInternal(r, paper, apiClasses)) todo.add(r);
        }
        return out;
    }

    /** impl = 상위 사슬에 API 형식이 있음, static = 생성자 밖 멤버가 모두 정적, internal = 그 밖. 스캔 도구(craft-identity-scan.py)와 같은 규칙. */
    static String role(SigTable.Cls c, SigTable paper, Set<String> apiClasses, Map<String, SigTable.Cls> all) {
        Set<String> seen = new HashSet<>();
        List<String> st = new ArrayList<>(List.of(c.name()));
        while (!st.isEmpty()) {
            String x = st.remove(st.size() - 1);
            if (x == null || !seen.add(x)) continue;
            if (!x.equals(c.name()) && apiClasses.contains(x)) return "impl";
            SigTable.Cls k = all.get(x);
            if (k == null) continue;
            st.add(k.superName());
            st.addAll(k.interfaces());
        }
        boolean any = false;
        for (SigTable.Member m : paper.members(c.name())) {
            if (m.kind() != 'M' || m.name().equals("<init>")) continue;
            any = true;
            if ((m.access() & Opcodes.ACC_STATIC) == 0) return "internal";
        }
        return any ? "static" : "internal";
    }

    public static String text(Map<String, Entry> entries) {
        StringBuilder sb = new StringBuilder(HEADER);
        for (Entry e : entries.values()) sb.append(e.line()).append('\n');
        return sb.toString();
    }

    /** 커밋된 잠금 읽기(시험·생성기용 — 서명표 없이). */
    public static Map<String, Entry> read(Path lock) throws IOException {
        Map<String, Entry> out = new TreeMap<>();
        for (String raw : Files.readAllLines(lock, StandardCharsets.UTF_8)) {
            String l = raw.strip();
            if (l.isEmpty() || l.startsWith("#")) continue;
            String[] t = l.split(" ");
            if (t.length != 6 || !t[0].equals("K")) throw new IllegalArgumentException("bad craft lock line: " + l);
            List<String> itf = t[4].equals("-") ? List.of() : List.of(t[4].split(","));
            out.put(t[1], new Entry(t[1], Integer.parseInt(t[2], 16), t[3], itf, t[5]));
        }
        return out;
    }

    /** args: write|check, 판, 서명표 캐시 폴더(&lt;캐시&gt;/&lt;판&gt;), 잠금 파일. */
    public static void main(String[] args) throws IOException {
        String mode = args[0];
        String mc = args[1];
        Path cache = Path.of(args[2]);
        Path lockFile = Path.of(args[3]);
        List<SigTable> tables = new ArrayList<>();
        for (String f : List.of("sig-table-paper-server.tsv.gz", "sig-table-paper-api.tsv.gz")) {
            Path p = cache.resolve(f);
            if (!Files.isRegularFile(p)) {
                System.err.println("[craftLock] missing signature table: " + p + " (tools/compat/sig-tables.sh " + mc + ") - failing instead of skipping");
                System.exit(1);
            }
            tables.add(SigTable.read(p));
        }
        Map<String, Entry> entries = resolve(tables.get(0), tables.get(1));
        String text = text(entries);
        if (mode.equals("write")) {
            Files.writeString(lockFile, text, StandardCharsets.UTF_8);
            System.out.println("[craftLock] " + entries.size() + " classes -> " + lockFile);
            return;
        }
        String now = Files.exists(lockFile) ? Files.readString(lockFile, StandardCharsets.UTF_8) : "";
        if (ShimLock.same(text, now)) {
            System.out.println("[craftLock] the craft hierarchy lock matches the signature tables (" + entries.size() + " classes)");
            return;
        }
        System.err.println("[craftLock] stale craft hierarchy lock - rewrite it with ./gradlew -PskipOracle=true :furnace-compat-paper:craftLock. Differing lines (+ expected, - present):");
        for (String l : ShimLock.diff(text, now)) System.err.println("  " + l);
        System.exit(1);
    }
}
