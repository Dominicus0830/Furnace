// 이식 명세(.ports) 읽기와 검사 — 설계 docs/roadmap/port-bridge-2026-11-27.md 5장. 세 모듈의 생성(PortGen)이 이것 하나를 쓴다.
// 이 소스셋은 api jar 에 실리지 않는다(명세를 읽는 코드라 net.minecraft 글자를 가진다 — 어댑터 경계 ② 검사 대상이 아니다).
package org.domi.furnace.portgen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 한 영역 파일(<번호>-<이름>.ports)의 내용과 검사 문제. */
public final class PortSpec {

    public static final String MAGIC = "#furnace-ports";
    public static final int FORMAT = 1;

    /** 인자·반환의 kind(설계 3.2). P1 이 다루는 것: PRIM·STRING·STATE·POS·LEVEL·RANDOM. 나머지는 알아보기만 하고 거절한다. */
    public enum Kind { VOID, PRIM, STRING, STATE, POS, LEVEL, RANDOM, ITEM, OTHER }

    /** 검사 종류(설계 6장). P1 은 pure·pos 만 만든다. */
    public enum Check {
        PURE("pure"), POS("pos"), ANY("any"), MENU("menu"), ENTITY("entity"), DETACHED("detached");

        public final String text;

        Check(String text) { this.text = text; }

        static Check of(String s) {
            for (Check c : values()) if (c.text.equals(s)) return c;
            return null;
        }
    }

    /** 레벨 kind 로 보는 NMS 형식(내부 이름). 레벨+자리를 받는 블록 동작의 레벨 자리다. */
    static final Set<String> LEVEL_TYPES = Set.of(
            "net/minecraft/world/level/Level", "net/minecraft/server/level/ServerLevel", "net/minecraft/world/level/LevelAccessor",
            "net/minecraft/world/level/LevelReader", "net/minecraft/world/level/BlockGetter", "net/minecraft/world/level/WorldGenLevel",
            "net/minecraft/world/level/ServerLevelAccessor", "net/minecraft/world/level/CommonLevelAccessor", "net/minecraft/world/level/LevelWriter",
            "net/minecraft/world/level/SignalGetter", "net/minecraft/world/level/LevelSimulatedReader");
    static final String STATE_TYPE = "net/minecraft/world/level/block/state/BlockState";
    static final String POS_TYPE = "net/minecraft/core/BlockPos";
    static final String RANDOM_TYPE = "net/minecraft/util/RandomSource";
    static final String ITEM_TYPE = "net/minecraft/world/item/ItemStack";

    /** 받는 쪽 kind(머리줄 kind=). P1 에서 인스턴스 줄을 받는 것은 block 뿐. */
    static final Set<String> CLASS_KINDS = Set.of("block", "state", "menu", "slot", "container", "data", "blockentity", "entity", "player", "item", "value");

    /** 머리줄 하나. nms·server 는 점 이름(server 는 org.domi.furnace.server. 뒤). */
    public record Cls(String where, String nms, String server, String kind, Set<String> flags, List<Member> members) {
        public String nmsSimple() { return nms.substring(nms.lastIndexOf('.') + 1); }

        public String serverSimple() { return server.substring(server.lastIndexOf('.') + 1); }

        public String serverFqcn() { return "org.domi.furnace.server." + server; }
    }

    /** 멤버 줄 하나. name 은 평문 이름 또는 #지문, desc 는 NMS 서술자. */
    public record Member(String where, boolean isStatic, String name, String desc, String serverName, Check check, Set<String> flags,
            List<String> params, String ret) {
        public String text() { return (isStatic ? "static " : "") + name + desc; }
    }

    public final String file;
    public final int area;
    public final String areaName;
    public final String version;
    public final List<Cls> classes = new ArrayList<>();
    public final List<String> problems = new ArrayList<>();

    private PortSpec(String file, int area, String areaName, String version) {
        this.file = file;
        this.area = area;
        this.areaName = areaName;
        this.version = version;
    }

    /** 영역 이름에서 만든 형식 이름 조각: item-inventory → ItemInventory. */
    public String camel() {
        StringBuilder b = new StringBuilder();
        for (String p : areaName.split("-")) if (!p.isEmpty()) b.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        return b.toString();
    }

    /** 폴더의 *.ports 를 이름 순으로. 폴더가 없으면 빈 목록. */
    public static List<PortSpec> readDir(Path dir) throws IOException {
        List<PortSpec> out = new ArrayList<>();
        if (dir == null || !Files.isDirectory(dir)) return out;
        try (var s = Files.list(dir)) {
            for (Path p : s.filter(x -> x.toString().endsWith(".ports")).sorted().toList()) {
                out.add(parse(p.getFileName().toString(), Files.readAllLines(p, StandardCharsets.UTF_8)));
            }
        }
        return out;
    }

    private static final Pattern FILE = Pattern.compile("(\\d+)-([a-z][a-z0-9-]*)\\.ports");
    private static final Pattern IDENT = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern FINGERPRINT = Pattern.compile("#[0-9a-f]{10}");

    public static PortSpec parse(String file, List<String> text) {
        Matcher fm = FILE.matcher(file);
        PortSpec spec;
        if (!fm.matches()) {
            spec = new PortSpec(file, -1, "invalid", "?");
            spec.problems.add(file + ": file name must be <area number>-<area name>.ports (see fill-areas.txt)");
            return spec;
        }
        String version = "?";
        String first = text.isEmpty() ? "" : text.get(0).strip();
        String[] h = first.split("\\s+");
        spec = new PortSpec(file, Integer.parseInt(fm.group(1)), fm.group(2), h.length > 2 ? h[2] : version);
        if (h.length < 4 || !h[0].equals(MAGIC) || !h[1].equals(String.valueOf(FORMAT)) || !h[3].equals("area=" + spec.area)) {
            spec.problems.add(file + ":1: first line must be '" + MAGIC + " " + FORMAT + " <version> area=" + spec.area + "'");
            return spec;
        }
        Cls cls = null;
        for (int i = 1; i < text.size(); i++) {
            String where = file + ":" + (i + 1);
            String s = text.get(i).replace("\r", "").strip();
            if (s.equals("#") || s.startsWith("# ")) continue;
            int hash = s.indexOf(" # ");
            if (hash >= 0) s = s.substring(0, hash).strip();
            if (s.isEmpty()) continue;
            if (s.startsWith("class ")) {
                cls = spec.parseClass(where, s.substring(6).strip());
                if (cls != null) spec.classes.add(cls);
                continue;
            }
            if (cls == null) {
                spec.problems.add(where + ": member line before any (valid) class line");
                continue;
            }
            Member m = spec.parseMember(where, cls, s);
            if (m != null) cls.members.add(m);
        }
        spec.checkNames();
        return spec;
    }

    private Cls parseClass(String where, String s) {
        int eq = s.indexOf('=');
        if (eq <= 0) {
            problems.add(where + ": class line must be 'class <NMS class> = <server class> kind=<kind> [flags]'");
            return null;
        }
        String nms = s.substring(0, eq).strip();
        String[] rest = s.substring(eq + 1).strip().split("\\s+");
        String server = rest[0];
        if (server.startsWith("org.domi.") || !server.matches("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*\\.[A-Z][A-Za-z0-9]*(\\$[A-Z][A-Za-z0-9]*)*")) {
            problems.add(where + ": server class must be written after org.domi.furnace.server. (e.g. block.types.CropBlock): " + server);
            return null;
        }
        String kind = null;
        Set<String> flags = new LinkedHashSet<>();
        for (int k = 1; k < rest.length; k++) {
            if (rest[k].startsWith("kind=")) kind = rest[k].substring(5);
            else if (rest[k].equals("hook") || rest[k].equals("value")) flags.add(rest[k]);
            else {
                problems.add(where + ": unknown class word " + rest[k]);
                return null;
            }
        }
        if (kind == null || !CLASS_KINDS.contains(kind.startsWith("value") ? "value" : kind)) {
            problems.add(where + ": class line needs kind=<" + String.join("|", new java.util.TreeSet<>(CLASS_KINDS)) + ">: " + s);
            return null;
        }
        if (!flags.isEmpty()) {
            problems.add(where + ": '" + String.join(" ", flags) + "' (hooked subclasses, value records) is not available yet - port bridge P2/P4");
            return null;
        }
        return new Cls(where, nms, server, kind, flags, new ArrayList<>());
    }

    private Member parseMember(String where, Cls cls, String s) {
        int arrow = s.indexOf("->");
        if (arrow < 0) {
            problems.add(where + ": member line needs '->'");
            return null;
        }
        String left = s.substring(0, arrow).strip();
        boolean isStatic = left.startsWith("static ");
        if (isStatic) left = left.substring(7).strip();
        int p = left.indexOf('(');
        if (p <= 0) {
            problems.add(where + ": member needs a full descriptor: " + left);
            return null;
        }
        String name = left.substring(0, p), desc = left.substring(p);
        if (!IDENT.matcher(name).matches() && !FINGERPRINT.matcher(name).matches() && !name.equals("<init>")) {
            problems.add(where + ": member name must be a plain name or a #fingerprint: " + name);
            return null;
        }
        List<String> params = new ArrayList<>();
        String ret = parseDesc(desc, params);
        if (ret == null) {
            problems.add(where + ": bad descriptor " + desc);
            return null;
        }
        String[] right = s.substring(arrow + 2).strip().split("\\s+");
        String serverName = right[0];
        if (name.equals("<init>") || serverName.startsWith("new(")) {
            problems.add(where + ": constructor lines (new(...)) are not available yet - port bridge P2");
            return null;
        }
        if (!IDENT.matcher(serverName).matches()) {
            problems.add(where + ": server method must be a plain name: " + serverName);
            return null;
        }
        Check check = null;
        Set<String> flags = new LinkedHashSet<>();
        for (int k = 1; k < right.length; k++) {
            String w = right[k];
            if (w.startsWith("check=")) {
                check = Check.of(w.substring(6));
                if (check == null) {
                    problems.add(where + ": unknown check " + w.substring(6) + " (pure, pos, any, menu, entity, detached)");
                    return null;
                }
            } else if (w.equals("live") || w.equals("copy") || w.equals("same") || w.startsWith("inout=")) flags.add(w);
            else {
                problems.add(where + ": unknown word " + w);
                return null;
            }
        }
        if (check == null) {
            problems.add(where + ": member line needs check=<pure|pos|any|menu|entity|detached>");
            return null;
        }
        Member m = new Member(where, isStatic, name, desc, serverName, check, flags, List.copyOf(params), ret);
        return validate(cls, m) ? m : null;
    }

    /** 설계 5·6장 규칙. 어기면 문제를 적고 false. */
    private boolean validate(Cls cls, Member m) {
        int before = problems.size();
        String w = m.where();
        // [검토 5] ItemStack 을 돌려주면 live(바닐라가 든 객체) 또는 copy(새 객체)를 반드시 적는다
        boolean retItem = kindOf(m.ret()) == Kind.ITEM;
        boolean live = m.flags().contains("live"), copy = m.flags().contains("copy");
        if (retItem && !live && !copy) problems.add(w + ": a line returning ItemStack must say live (vanilla returns the object it holds) or copy (a new object)");
        if (live && copy) problems.add(w + ": live and copy together");
        if ((live || copy) && !retItem) problems.add(w + ": live/copy only go on lines returning ItemStack");
        for (String f : m.flags()) {
            if (f.equals("same") || f.startsWith("inout=")) problems.add(w + ": '" + f + "' is not available yet - port bridge P2/P4");
        }
        if (!m.isStatic() && !cls.kind().equals("block")) problems.add(w + ": instance lines on kind=" + cls.kind() + " are not available yet - port bridge P2/P3");
        // kind 들
        int levels = 0, poses = 0;
        for (String t : m.params()) {
            Kind k = kindOf(t);
            if (k == Kind.LEVEL) levels++;
            if (k == Kind.POS) poses++;
            if (k == Kind.ITEM || k == Kind.OTHER) problems.add(w + ": no port kind for argument type " + t + " yet");
        }
        Kind rk = kindOf(m.ret());
        if (rk != Kind.VOID && rk != Kind.PRIM && rk != Kind.STRING) problems.add(w + ": no port kind for return type " + m.ret() + " yet");
        switch (m.check()) {
            case PURE -> { if (levels > 0) problems.add(w + ": check=pure cannot take a level argument (it is not pure)"); }
            case POS -> {
                if (poses == 0 || levels != 1) problems.add(w + ": check=pos needs exactly one level argument and a BlockPos (the level is taken at the first BlockPos)");
            }
            default -> problems.add(w + ": check=" + m.check().text + " is not available yet - port bridge P2");
        }
        if (levels > 0 && m.check() != Check.POS) problems.add(w + ": a level argument needs check=pos");
        return problems.size() == before;
    }

    /** 같은 클래스 줄 겹침, 다리 이름 겹침. */
    private void checkNames() {
        Map<String, String> bridge = new HashMap<>();
        Map<String, String> serverOwner = new HashMap<>();
        for (Cls c : classes) {
            String prevOwner = serverOwner.putIfAbsent(c.serverSimple(), c.server());
            if (prevOwner != null && !prevOwner.equals(c.server())) problems.add(c.where() + ": two server classes named " + c.serverSimple() + " in one area - bridge names would clash");
            Set<String> seen = new java.util.HashSet<>();
            for (Member m : c.members()) {
                if (!seen.add(m.name() + m.desc())) problems.add(m.where() + ": duplicate line for " + c.nms() + "#" + m.name() + m.desc());
                String key = bridgeName(c, m) + neutralDesc(m);
                String prev = bridge.put(key, m.where());
                if (prev != null) problems.add(m.where() + ": bridge method " + bridgeName(c, m) + " clashes with " + prev + " (same server name and neutral arguments)");
            }
        }
    }

    /** api 인터페이스 메서드 이름: 서버 클래스 이름(첫 글자 소문자) + $ + 서버 메서드 이름. */
    public static String bridgeName(Cls c, Member m) {
        String s = c.serverSimple().replace('$', '_');
        return Character.toLowerCase(s.charAt(0)) + s.substring(1) + "$" + m.serverName();
    }

    /** 중립 인자 꼴(겹침 판정용). */
    static String neutralDesc(Member m) {
        StringBuilder b = new StringBuilder(m.isStatic() ? "(" : "(I");
        for (String t : m.params()) b.append(switch (kindOf(t)) {
            case POS -> "III";
            case STATE -> "I";
            case LEVEL -> "W";
            case RANDOM -> "R";
            default -> t;
        });
        return b.append(')').toString();
    }

    public static Kind kindOf(String t) {
        if (t.equals("V")) return Kind.VOID;
        if (t.length() == 1) return Kind.PRIM;
        if (t.equals("Ljava/lang/String;")) return Kind.STRING;
        if (!t.startsWith("L")) return Kind.OTHER;
        String n = t.substring(1, t.length() - 1);
        if (n.equals(STATE_TYPE)) return Kind.STATE;
        if (n.equals(POS_TYPE)) return Kind.POS;
        if (n.equals(RANDOM_TYPE)) return Kind.RANDOM;
        if (n.equals(ITEM_TYPE)) return Kind.ITEM;
        if (LEVEL_TYPES.contains(n)) return Kind.LEVEL;
        return Kind.OTHER;
    }

    /** 서술자를 인자 목록과 반환으로 나눈다. 틀리면 null. */
    static String parseDesc(String desc, List<String> params) {
        if (!desc.startsWith("(")) return null;
        int i = 1;
        while (i < desc.length() && desc.charAt(i) != ')') {
            int end = typeEnd(desc, i);
            if (end < 0) return null;
            params.add(desc.substring(i, end));
            i = end;
        }
        if (i >= desc.length()) return null;
        String ret = desc.substring(i + 1);
        if (ret.equals("V")) return ret;
        return typeEnd(ret, 0) == ret.length() ? ret : null;
    }

    private static int typeEnd(String s, int i) {
        if (i >= s.length()) return -1;
        char c = s.charAt(i);
        if ("ZBCSIJFD".indexOf(c) >= 0) return i + 1;
        if (c == '[') return typeEnd(s, i + 1);
        if (c == 'L') {
            int semi = s.indexOf(';', i);
            return semi < 0 ? -1 : semi + 1;
        }
        return -1;
    }

    /** 원시 서술자 글자 → 자바 형식 이름. */
    static String primName(String t) {
        return switch (t) {
            case "Z" -> "boolean";
            case "B" -> "byte";
            case "C" -> "char";
            case "S" -> "short";
            case "I" -> "int";
            case "J" -> "long";
            case "F" -> "float";
            case "D" -> "double";
            case "V" -> "void";
            default -> throw new IllegalArgumentException(t);
        };
    }
}
