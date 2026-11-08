package org.domi.furnace.compat.nms;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.objectweb.asm.Type;

/**
 * Spigot 이름 → Mojang 이름 대응표(H7d). Spigot 이름으로 컴파일된 플러그인(매니페스트에 {@code paperweight-mappings-namespace: mojang}
 * 이 없는 jar)의 NMS 참조를 이 서버의 Mojang 이름 shim 으로 잇는다 — Paper 가 Spigot 이름 플러그인을 올릴 때 하는 변환과 같은 일이다.
 *
 * <p>표는 저장소·서버 jar 에 싣지 않는다(Mojang·Spigot 에서 뽑은 자료, docs/policy/licensing.md). 서버가 운영자 설정
 * {@code plugins.compat.nms.spigot-mappings=download} 일 때 운영자 컴퓨터에서 받은 세 파일을 읽어 메모리에 만든다:
 * <ul>
 *   <li>Mojang 공식 매핑 {@code server.txt}(ProGuard 형식, Mojang 이름 → 난독화 이름, 멤버 포함)</li>
 *   <li>Spigot BuildData 클래스 매핑 {@code bukkit-1.21.1-cl.csrg}(난독화 클래스 → Spigot 클래스)</li>
 *   <li>Spigot BuildData 멤버 매핑 {@code bukkit-1.21.1-members.csrg}(Spigot 이 이름을 붙인 몇 안 되는 멤버. 나머지 멤버는 난독화 이름 그대로)</li>
 * </ul>
 * Spigot 의 플러그인 재매핑(SpecialSource: Mojang → 난독화 → Spigot)을 거꾸로 따라간다: Spigot 이름 → 난독화 이름 → Mojang 이름.
 *
 * <p>멤버가 참조한 클래스에 선언돼 있지 않으면(상속된 멤버) {@code supers} 로 받은 위 클래스들을 따라 찾고, 그래도 없으면 같은 난독화 이름·서술자를
 * 선언한 모든 클래스가 <b>같은 Mojang 이름</b>으로 모일 때만(재정의 관계) 그 이름을 쓴다. 갈리면 이름을 그대로 둔다 — 그러면 shim 에 없는
 * 이름이라 그 호출만 덫({@link NmsLinker})에 걸린다(조용히 엉뚱한 멤버로 잇지 않는다).
 */
public final class SpigotMappings {

    private static final Map<String, SpigotMappings> BY_PLUGIN = new ConcurrentHashMap<>();
    private static final Map<String, SpigotMappings> LOADED = new ConcurrentHashMap<>();

    /** Spigot 클래스 → 난독화 클래스. */
    private final Map<String, String> spigotToObf = new HashMap<>();
    /** 난독화 클래스 → Mojang 클래스. */
    private final Map<String, String> obfToMojang = new HashMap<>();
    /** Mojang 클래스 → 난독화 클래스. */
    private final Map<String, String> mojangToObf = new HashMap<>();
    /** 난독화 클래스 → Spigot 클래스. */
    private final Map<String, String> obfToSpigot = new HashMap<>();
    /** "obfOwner name(args)ret" → Mojang 메서드 이름. */
    private final Map<String, String> methods = new HashMap<>();
    /** "obfOwner name(args)" → Mojang 메서드 이름(반환형 모르는 리플렉션용, 여럿이면 null 값). */
    private final Map<String, String> methodsByArgs = new HashMap<>();
    /** "obfOwner name" → Mojang 메서드 이름(서술자 모름 — 람다 인터페이스 메서드, 여럿이면 ""). */
    private final Map<String, String> methodsByName = new HashMap<>();
    /** Spigot 이 이름을 바꾼 메서드(서술자 없이): "obfOwner spigotName" → 난독화 이름(여럿이면 ""). */
    private final Map<String, String> spigotMethodsByName = new HashMap<>();
    /** "obfOwner name" → Mojang 필드 이름. */
    private final Map<String, String> fields = new HashMap<>();
    /** "name(args)ret" → 그 메서드를 선언한 클래스들의 Mojang 이름(하나로 모이면 그 이름, 갈리면 ""). */
    private final Map<String, String> methodsAnywhere = new HashMap<>();
    /** "obfOwner obfName" → 필드 난독화 서술자. */
    private final Map<String, String> fieldDescs = new HashMap<>();
    /** "name desc" → 필드 Mojang 이름(모이면 그 이름, 갈리면 ""). */
    private final Map<String, String> fieldsAnywhere = new HashMap<>();
    /** Spigot 이 이름을 바꾼 멤버: "obfOwner spigotName(args)ret" / "obfOwner spigotName" → 난독화 이름. */
    private final Map<String, String> spigotMembers = new HashMap<>();
    /** Spigot 이 이름을 바꾼 메서드(반환형 없이): "obfOwner spigotName(args)" → 난독화 이름. */
    private final Map<String, String> spigotMembersByArgs = new HashMap<>();
    /** Spigot 이 이름을 바꾼 멤버를 클래스 없이: "spigotName(args)ret" / "spigotName desc" → 난독화 이름들. */
    private final Map<String, Set<String>> spigotMembersAnywhere = new HashMap<>();

    private SpigotMappings() {}

    // ------------------------------------------------------------------ 읽기

    /**
     * 세 파일을 읽는다(같은 파일·같은 수정 시각이면 한 번만 읽어 모든 플러그인이 같이 쓴다).
     */
    public static SpigotMappings load(Path mojang, Path spigotClasses, Path spigotMembers) throws IOException {
        String key = mojang.toAbsolutePath() + "|" + Files.getLastModifiedTime(mojang) + "|" + spigotClasses.toAbsolutePath() + "|"
                + Files.getLastModifiedTime(spigotClasses) + "|" + spigotMembers.toAbsolutePath() + "|" + Files.getLastModifiedTime(spigotMembers);
        SpigotMappings cached = LOADED.get(key);
        if (cached != null) return cached;
        SpigotMappings m;
        try (Reader a = Files.newBufferedReader(mojang, StandardCharsets.UTF_8);
             Reader b = Files.newBufferedReader(spigotClasses, StandardCharsets.UTF_8);
             Reader c = Files.newBufferedReader(spigotMembers, StandardCharsets.UTF_8)) {
            m = parse(a, b, c);
        }
        LOADED.clear();   // 파일이 바뀌었으면 옛 표는 버린다
        LOADED.put(key, m);
        return m;
    }

    /** 시험·도구용: 세 글을 읽는다. */
    public static SpigotMappings parse(Reader mojang, Reader spigotClasses, Reader spigotMembers) throws IOException {
        SpigotMappings m = new SpigotMappings();
        List<String[]> raw = m.readProguard(new BufferedReader(mojang));
        m.readClasses(new BufferedReader(spigotClasses));
        m.indexMojangMembers(raw);
        m.readMembers(new BufferedReader(spigotMembers));
        if (m.obfToMojang.isEmpty()) throw new IOException("Mojang 매핑에 클래스가 하나도 없다(ProGuard 형식이 아니다)");
        if (m.spigotToObf.isEmpty()) throw new IOException("Spigot 클래스 매핑에 줄이 하나도 없다(csrg 형식이 아니다)");
        return m;
    }

    /** ProGuard: {@code a.b.C -> x:} 와 그 아래 들여쓴 멤버 줄. 멤버는 모든 클래스를 읽은 뒤 서술자를 난독화 이름으로 바꿔 색인한다. */
    private List<String[]> readProguard(BufferedReader in) throws IOException {
        List<String[]> members = new ArrayList<>();   // {obfOwner, mojangName, javaType 또는 "ret(args)", obfName, "F"|"M"}
        String obfOwner = null;
        for (String line; (line = in.readLine()) != null; ) {
            if (line.isBlank() || line.stripLeading().startsWith("#")) continue;
            int arrow = line.indexOf(" -> ");
            if (arrow < 0) continue;
            if (!Character.isWhitespace(line.charAt(0))) {
                if (!line.endsWith(":")) continue;
                String mojang = line.substring(0, arrow).strip().replace('.', '/');
                String obf = line.substring(arrow + 4, line.length() - 1).strip().replace('.', '/');
                obfToMojang.put(obf, mojang);
                mojangToObf.put(mojang, obf);
                obfOwner = obf;
                continue;
            }
            if (obfOwner == null) continue;
            String left = line.substring(0, arrow).strip();
            String obfName = line.substring(arrow + 4).strip();
            // 줄 번호 앞머리 "12:34:" (여러 개일 수 있다)
            while (!left.isEmpty() && Character.isDigit(left.charAt(0))) {
                int colon = left.indexOf(':');
                if (colon < 0) break;
                left = left.substring(colon + 1);
            }
            int sp = left.indexOf(' ');
            if (sp < 0) continue;
            String type = left.substring(0, sp);
            String rest = left.substring(sp + 1);
            int paren = rest.indexOf('(');
            if (paren < 0) {
                members.add(new String[] {obfOwner, rest, type, obfName, "F"});
            } else {
                int close = rest.indexOf(')', paren);
                if (close < 0) continue;
                String name = rest.substring(0, paren);
                if (name.startsWith("<")) continue;
                members.add(new String[] {obfOwner, name, type + "(" + rest.substring(paren + 1, close) + ")", obfName, "M"});
            }
        }
        return members;
    }

    private void indexMojangMembers(List<String[]> raw) {
        for (String[] r : raw) {
            String owner = r[0];
            String mojangName = r[1];
            String obfName = r[3];
            if (r[4].equals("F")) {
                String desc = obfDesc(javaToDesc(r[2]));
                fields.put(owner + " " + obfName, mojangName);
                fieldDescs.put(owner + " " + obfName, desc);
                merge(fieldsAnywhere, obfName + " " + desc, mojangName);
            } else {
                String sig = r[2];
                int open = sig.indexOf('(');
                StringBuilder d = new StringBuilder("(");
                String args = sig.substring(open + 1, sig.length() - 1);
                if (!args.isEmpty()) for (String a : args.split(",")) d.append(javaToDesc(a.strip()));
                String argsDesc = obfDesc(d.append(')').toString());
                String desc = argsDesc + obfDesc(javaToDesc(sig.substring(0, open)));
                methods.put(owner + " " + obfName + desc, mojangName);
                String byArgs = owner + " " + obfName + argsDesc;
                methodsByArgs.merge(byArgs, mojangName, (x, y) -> x.equals(y) ? x : "");
                merge(methodsByName, owner + " " + obfName, mojangName);
                merge(methodsAnywhere, obfName + desc, mojangName);
            }
        }
    }

    private static void merge(Map<String, String> m, String key, String value) {
        m.merge(key, value, (x, y) -> x.equals(y) ? x : "");
    }

    /** csrg 클래스 줄 {@code obf spigot}. 패키지 줄(끝이 '/')은 1.17 이후 쓰지 않는다 — 건너뛴다. */
    private void readClasses(BufferedReader in) throws IOException {
        for (String line; (line = in.readLine()) != null; ) {
            line = line.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] t = line.split("\\s+");
            if (t.length != 2 || t[0].endsWith("/")) continue;
            spigotToObf.put(t[1], t[0]);
            obfToSpigot.put(t[0], t[1]);
        }
    }

    /**
     * csrg 멤버 줄: 필드 {@code owner obf spigot}, 메서드 {@code owner obf desc spigot}. owner·desc 는 Spigot 클래스 이름이다
     * (BuildTools 가 클래스 매핑을 먼저 적용한 jar 에 멤버 매핑을 건다) — 난독화 이름으로 적혀 있어도 받아 준다.
     */
    private void readMembers(BufferedReader in) throws IOException {
        for (String line; (line = in.readLine()) != null; ) {
            line = line.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] t = line.split("\\s+");
            if (t.length != 3 && t.length != 4) continue;
            String owner = toObfClass(t[0]);
            if (t.length == 3) {
                spigotMembers.put(owner + " " + t[2], t[1]);
                String desc = fieldDescOf(owner, t[1]);
                if (desc != null) spigotMembersAnywhere.computeIfAbsent(t[2] + " " + desc, k -> new HashSet<>()).add(t[1]);
            } else {
                String desc = obfDesc(t[2]);
                spigotMembers.put(owner + " " + t[3] + desc, t[1]);
                spigotMembersByArgs.merge(owner + " " + t[3] + desc.substring(0, desc.indexOf(')') + 1), t[1], (x, y) -> x.equals(y) ? x : "");
                spigotMembersAnywhere.computeIfAbsent(t[3] + desc, k -> new HashSet<>()).add(t[1]);
                merge(spigotMethodsByName, owner + " " + t[3], t[1]);
            }
        }
    }

    /** 필드 서술자는 ProGuard 쪽에만 있다 — 클래스 사이 찾기용(없으면 null). */
    private String fieldDescOf(String obfOwner, String obfName) { return fieldDescs.get(obfOwner + " " + obfName); }

    // ------------------------------------------------------------------ 이름 바꾸기 (바이트코드)

    /** Spigot 내부 이름(배열·기본형 아닌 클래스) → Mojang 내부 이름. Spigot 클래스가 아니면 null. */
    public String mapClass(String spigotInternal) {
        String obf = spigotToObf.get(spigotInternal);
        if (obf == null) {
            int d = spigotInternal.lastIndexOf('$');
            if (d < 0) return null;
            String outer = mapClassObf(spigotInternal.substring(0, d));   // 표에 없는 안쪽 클래스: 바깥 + 같은 꼬리
            if (outer == null) return null;
            obf = outer + spigotInternal.substring(d);
        }
        return obfToMojang.get(obf);
    }

    private String mapClassObf(String spigotInternal) {
        String obf = spigotToObf.get(spigotInternal);
        if (obf != null) return obf;
        int d = spigotInternal.lastIndexOf('$');
        if (d < 0) return null;
        String outer = mapClassObf(spigotInternal.substring(0, d));
        return outer == null ? null : outer + spigotInternal.substring(d);
    }

    /** Spigot(또는 난독화 그대로인) 클래스 이름 → 난독화 이름. 모르는 이름은 그대로. */
    private String toObfClass(String internal) {
        String obf = mapClassObf(internal);
        return obf != null ? obf : internal;
    }

    /** 서술자의 클래스 이름을 Spigot → 난독화로. */
    String obfDescFromSpigot(String desc) { return remapDesc(desc, this::toObfClass); }

    /** 서술자의 클래스 이름을 Mojang → 난독화로. */
    private String obfDesc(String mojangDesc) { return remapDesc(mojangDesc, n -> mojangToObf.getOrDefault(n, n)); }

    private static String remapDesc(String desc, Function<String, String> f) {
        StringBuilder out = new StringBuilder(desc.length());
        for (int i = 0; i < desc.length(); i++) {
            char ch = desc.charAt(i);
            out.append(ch);
            if (ch == 'L') {
                int end = desc.indexOf(';', i);
                out.append(f.apply(desc.substring(i + 1, end))).append(';');
                i = end;
            }
        }
        return out.toString();
    }

    /** Java 소스 표기 형 → Mojang 서술자. */
    private static String javaToDesc(String t) {
        int dims = 0;
        while (t.endsWith("[]")) {
            dims++;
            t = t.substring(0, t.length() - 2);
        }
        String base = switch (t) {
            case "void" -> "V";
            case "boolean" -> "Z";
            case "byte" -> "B";
            case "char" -> "C";
            case "short" -> "S";
            case "int" -> "I";
            case "long" -> "J";
            case "float" -> "F";
            case "double" -> "D";
            default -> "L" + t.replace('.', '/') + ";";
        };
        return "[".repeat(dims) + base;
    }

    /**
     * 메서드 이름. {@code spigotOwner}·{@code spigotDesc} 는 플러그인 바이트코드의 원래(Spigot) 이름이다. {@code supers} 는 난독화 클래스의
     * 바로 위 클래스·인터페이스(난독화 이름)를 준다. 못 찾으면 원래 이름.
     */
    public String mapMethod(String spigotOwner, String name, String spigotDesc, Function<String, List<String>> supers) {
        if (name.startsWith("<")) return name;
        String obfOwner = ownerObf(spigotOwner);
        if (obfOwner == null) return name;
        String desc = obfDescFromSpigot(spigotDesc);
        String found = walk(obfOwner, supers, c -> {
            String obfName = spigotMembers.getOrDefault(c + " " + name + desc, name);
            return methods.get(c + " " + obfName + desc);
        });
        if (found != null) return found;
        return anywhere(methodsAnywhere, spigotMembersAnywhere, name, desc, name + desc);
    }

    /**
     * 서술자를 모르는 메서드 이름(람다가 구현하는 NMS 함수형 인터페이스의 메서드 — ASM 이 SAM 서술자를 주지 않는다). 그 클래스 사슬에서 이 이름이
     * 한 Mojang 이름으로만 풀릴 때 그 이름, 아니면 원래 이름.
     */
    public String mapMethodAnyDesc(String spigotOwner, String name, Function<String, List<String>> supers) {
        String obfOwner = ownerObf(spigotOwner);
        if (obfOwner == null) return name;
        String found = walk(obfOwner, supers, c -> {
            String renamed = spigotMethodsByName.get(c + " " + name);
            String r = methodsByName.get(c + " " + (renamed == null || renamed.isEmpty() ? name : renamed));
            return r == null || r.isEmpty() ? null : r;
        });
        return found != null ? found : name;
    }

    /** 필드 이름. 위와 같다. */
    public String mapField(String spigotOwner, String name, String spigotDesc, Function<String, List<String>> supers) {
        String obfOwner = ownerObf(spigotOwner);
        if (obfOwner == null) return name;
        String desc = obfDescFromSpigot(spigotDesc);
        String found = walk(obfOwner, supers, c -> fields.get(c + " " + spigotMembers.getOrDefault(c + " " + name, name)));
        if (found != null) return found;
        return anywhere(fieldsAnywhere, spigotMembersAnywhere, name, " " + desc, name + " " + desc);
    }

    /** 클래스 사이 찾기: 같은 이름·서술자를 선언한 곳들이 한 Mojang 이름으로 모일 때만. */
    private static String anywhere(Map<String, String> table, Map<String, Set<String>> renamed, String name, String descPart, String renamedKey) {
        Set<String> names = new HashSet<>();
        String direct = table.get(name + descPart);
        if (direct != null) names.add(direct);
        Set<String> obfNames = renamed.get(renamedKey);
        if (obfNames != null) for (String o : obfNames) {
            String v = table.get(o + descPart);
            if (v != null) names.add(v);
        }
        if (names.size() == 1) {
            String only = names.iterator().next();
            if (!only.isEmpty()) return only;
        }
        return name;
    }

    private String ownerObf(String spigotOwner) {
        String e = NmsSymbols.element(spigotOwner);
        if (e == null) return null;
        String obf = mapClassObf(e);
        if (obf != null && obfToMojang.containsKey(obf)) return obf;
        return null;
    }

    private static String walk(String start, Function<String, List<String>> supers, Function<String, String> at) {
        java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            String c = queue.poll();
            if (!seen.add(c)) continue;
            String r = at.apply(c);
            if (r != null) return r;
            for (String s : supers.apply(c)) if (s != null) queue.add(s);
        }
        return null;
    }

    // ------------------------------------------------------------------ 이름 바꾸기 (리플렉션, 실행 때 — 클래스는 이미 Mojang 이름)

    /** Spigot 점 표기 클래스 이름 → Mojang 점 표기(Spigot 클래스가 아니면 null). */
    public String mapClassName(String dotted) {
        String m = mapClass(dotted.replace('.', '/'));
        return m == null ? null : m.replace('/', '.');
    }

    /** 실행 중인 Mojang 이름 클래스 → 난독화 이름(모르면 null). */
    String obfOf(Class<?> c) { return c == null ? null : mojangToObf.get(c.getName().replace('.', '/')); }

    /** 난독화 이름 → Mojang 이름(모르면 null). */
    String mojangOf(String obf) { return obfToMojang.get(obf); }

    /** Mojang 이름 → 난독화 이름(모르면 null). */
    String obfOfMojang(String mojang) { return mojangToObf.get(mojang); }

    /** 리플렉션 필드 이름. declared 면 그 클래스만, 아니면 상위 클래스·인터페이스(실행 중 Class 사슬)도. */
    public String reflectField(Class<?> owner, String name, boolean declared) {
        for (Class<?> c : chain(owner, declared)) {
            String obf = obfOf(c);
            if (obf == null) continue;
            String r = fields.get(obf + " " + spigotMembers.getOrDefault(obf + " " + name, name));
            if (r != null) return r;
        }
        return name;
    }

    /** 리플렉션 메서드 이름(인자형만 안다). */
    public String reflectMethod(Class<?> owner, String name, Class<?>[] params, boolean declared) {
        StringBuilder d = new StringBuilder("(");
        if (params != null) for (Class<?> p : params) d.append(Type.getDescriptor(p));
        String args = obfDesc(d.append(')').toString());
        for (Class<?> c : chain(owner, declared)) {
            String obf = obfOf(c);
            if (obf == null) continue;
            String renamed = spigotMembersByArgs.get(obf + " " + name + args);
            String obfName = renamed == null || renamed.isEmpty() ? name : renamed;
            String r = methodsByArgs.get(obf + " " + obfName + args);
            if (r != null && !r.isEmpty()) return r;
        }
        return name;
    }

    /** 리플렉션 메서드 이름(서술자 전체를 안다 — MethodHandles.Lookup). */
    public String reflectMethod(Class<?> owner, String name, String mojangDesc, boolean declared) {
        String desc = obfDesc(mojangDesc);
        for (Class<?> c : chain(owner, declared)) {
            String obf = obfOf(c);
            if (obf == null) continue;
            String obfName = spigotMembers.getOrDefault(obf + " " + name + desc, name);
            String r = methods.get(obf + " " + obfName + desc);
            if (r != null) return r;
        }
        return name;
    }

    private static List<Class<?>> chain(Class<?> owner, boolean declared) {
        List<Class<?>> out = new ArrayList<>();
        if (owner == null) return out;
        if (declared) {
            out.add(owner);
            return out;
        }
        java.util.ArrayDeque<Class<?>> q = new java.util.ArrayDeque<>();
        Set<Class<?>> seen = new HashSet<>();
        q.add(owner);
        while (!q.isEmpty()) {
            Class<?> c = q.poll();
            if (!seen.add(c)) continue;
            out.add(c);
            if (c.getSuperclass() != null) q.add(c.getSuperclass());
            q.addAll(List.of(c.getInterfaces()));
        }
        return out;
    }

    /** 표 크기(로그용): 클래스 수. */
    public int classCount() { return obfToSpigot.size(); }

    // ------------------------------------------------------------------ 플러그인별 등록 (리플렉션 다리가 찾는다)

    /** 이 플러그인의 클래스는 Spigot 이름으로 읽는다(null 이면 지운다). */
    public static void register(String plugin, SpigotMappings m) {
        if (plugin == null) return;
        if (m == null) BY_PLUGIN.remove(plugin);
        else BY_PLUGIN.put(plugin, m);
    }

    /** 이 플러그인이 Spigot 이름 대응표를 쓰면 그 표, 아니면 null. */
    public static SpigotMappings forPlugin(String plugin) { return plugin == null ? null : BY_PLUGIN.get(plugin); }
}
