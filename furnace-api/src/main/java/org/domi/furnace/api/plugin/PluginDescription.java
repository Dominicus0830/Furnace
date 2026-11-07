// 플러그인 설명 파일(furnace-plugin.yml / .toml / .json) 내용. 키 이름은 Bukkit plugin.yml 과 같게 맞췄다.
package org.domi.furnace.api.plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.domi.furnace.api.config.ConfigSection;
import org.domi.furnace.api.permission.Permission;
import org.domi.furnace.api.permission.PermissionDefault;

/**
 * @param depend     없으면 켜지지 않는 플러그인
 * @param softDepend 있으면 먼저 켜지는 플러그인
 * @param loadBefore 이 플러그인보다 나중에 켜져야 하는 플러그인
 * @param prefix     로그 접두어(없으면 이름)
 * @param bootstrapper 부트스트랩 클래스(PluginBootstrap 구현, 없으면 null) — 레지스트리·데이터 팩·월드 전에 불린다
 * @param libraries  자동으로 받아 올 Maven 라이브러리(서버 설정 plugins.libraries.enabled=true 일 때만 받는다)
 */
public record PluginDescription(String name, String version, String main, String description, List<String> authors, String website,
                                String apiVersion, List<String> depend, List<String> softDepend, List<String> loadBefore, String prefix,
                                Map<String, CommandInfo> commands, List<Permission> permissions,
                                String bootstrapper, List<Library> libraries) {

    /**
     * 설명 파일의 라이브러리 항목(Maven Central 좌표). 설명 파일에는 문자열 {@code "그룹:아티팩트:버전"}(분류자가 있으면 {@code :분류자})
     * 또는 {@code coordinate}·{@code sha256} 를 가진 항목으로 적는다.
     *
     * @param coordinate 그룹:아티팩트:버전[:분류자]
     * @param sha256     jar 의 SHA-256(소문자 16진수) — 있으면 이 값과 다른 파일은 받지 않는다(없으면 null)
     */
    public record Library(String coordinate, String sha256) {
        private static final java.util.regex.Pattern PART = java.util.regex.Pattern.compile("[A-Za-z0-9_.+-]+");
        private static final java.util.regex.Pattern HEX64 = java.util.regex.Pattern.compile("[0-9a-f]{64}");

        public Library {
            Objects.requireNonNull(coordinate, "coordinate");
            String[] p = coordinate.split(":", -1);
            if (p.length < 3 || p.length > 4) throw new IllegalArgumentException("라이브러리 좌표는 그룹:아티팩트:버전[:분류자] 이어야 한다: " + coordinate);
            for (String x : p) {
                if (!PART.matcher(x).matches() || x.equals(".") || x.equals("..") || x.contains(".."))
                    throw new IllegalArgumentException("라이브러리 좌표에 쓸 수 없는 글자가 있다: " + coordinate);
            }
            if (sha256 != null) {
                sha256 = sha256.strip().toLowerCase(java.util.Locale.ROOT);
                if (!HEX64.matcher(sha256).matches()) throw new IllegalArgumentException("sha256 은 16진수 64자리여야 한다: " + coordinate);
            }
        }

        public Library(String coordinate) { this(coordinate, null); }

        public String group() { return coordinate.split(":", -1)[0]; }

        public String artifact() { return coordinate.split(":", -1)[1]; }

        public String version() { return coordinate.split(":", -1)[2]; }

        /** 분류자(없으면 null). */
        public String classifier() {
            String[] p = coordinate.split(":", -1);
            return p.length > 3 ? p[3] : null;
        }
    }

    /** 라이브러리 없이 만든 설명(옛 생성자). */
    public PluginDescription(String name, String version, String main, String description, List<String> authors, String website,
                             String apiVersion, List<String> depend, List<String> softDepend, List<String> loadBefore, String prefix,
                             Map<String, CommandInfo> commands, List<Permission> permissions, String bootstrapper) {
        this(name, version, main, description, authors, website, apiVersion, depend, softDepend, loadBefore, prefix, commands, permissions,
                bootstrapper, List.of());
    }

    /** 설명 파일의 명령 항목(실제 실행기는 코드에서 등록한다 — 설명·별칭·권한은 여기서 채워진다). */
    public record CommandInfo(String description, List<String> aliases, String permission, String usage) {}

    public PluginDescription {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(main, "main");
        authors = List.copyOf(authors);
        depend = List.copyOf(depend);
        softDepend = List.copyOf(softDepend);
        loadBefore = List.copyOf(loadBefore);
        commands = Map.copyOf(commands);
        permissions = List.copyOf(permissions);
        libraries = List.copyOf(libraries);
    }

    /** 로그에 쓰는 이름(prefix 가 있으면 그것). */
    public String logPrefix() { return prefix != null ? prefix : name; }

    /** 키 namespace — 이름을 소문자로, 공백은 '_' 로(영속 데이터·플러그인 레지스트리·플러그인 데이터 팩이 쓴다). */
    public String namespace() { return name.toLowerCase(java.util.Locale.ROOT).replace(' ', '_'); }

    /** "이름 v버전". */
    public String fullName() { return name + " v" + version; }

    /** 설정 트리에서 읽는다. 필수 키(name, version, main)가 없거나 이름이 잘못되면 IllegalArgumentException. */
    public static PluginDescription read(ConfigSection s) {
        String name = required(s, "name");
        if (!name.matches("[A-Za-z0-9 _.-]+")) throw new IllegalArgumentException("플러그인 이름에 쓸 수 없는 글자가 있다: " + name);
        String version = required(s, "version");
        String main = required(s, "main");
        List<String> authors = new ArrayList<>(strings(s, "authors"));
        String author = s.getString("author", null);
        if (author != null) authors.add(0, author);
        Map<String, CommandInfo> commands = new LinkedHashMap<>();
        ConfigSection cs = s.getSection("commands");
        if (cs != null) {
            for (String label : cs.keys(false)) {
                ConfigSection c = cs.getSection(label);
                if (c == null) {
                    commands.put(label.toLowerCase(java.util.Locale.ROOT), new CommandInfo("", List.of(), null, null));
                    continue;
                }
                commands.put(label.toLowerCase(java.util.Locale.ROOT), new CommandInfo(c.getString("description", ""), strings(c, "aliases"),
                        c.getString("permission", null), c.getString("usage", null)));
            }
        }
        List<Permission> permissions = new ArrayList<>();
        PermissionDefault fallback = PermissionDefault.OP;
        String dflt = s.getString("default-permission", null);
        if (dflt != null && PermissionDefault.byName(dflt) != null) fallback = PermissionDefault.byName(dflt);
        ConfigSection ps = s.getSection("permissions");
        if (ps != null) {
            for (String node : ps.keys(false)) readPermission(node, ps.getSection(node), fallback, permissions);
        }
        return new PluginDescription(name, version, main, s.getString("description", ""), authors, s.getString("website", null),
                s.getString("api-version", null), strings(s, "depend"), strings(s, "softdepend"), strings(s, "loadbefore"),
                s.getString("prefix", null), commands, permissions, s.getString("bootstrapper", null), readLibraries(s));
    }

    /** libraries: 문자열 또는 {coordinate, sha256} 항목의 목록. 잘못된 항목은 IllegalArgumentException. */
    private static List<Library> readLibraries(ConfigSection s) {
        Object v = s.get("libraries");
        if (v == null) return List.of();
        List<?> items = v instanceof List<?> l ? l : List.of(v);
        List<Library> out = new ArrayList<>(items.size());
        for (Object o : items) {
            if (o == null) continue;
            if (o instanceof ConfigSection c) {
                String coord = c.getString("coordinate", c.getString("id", null));
                if (coord == null) throw new IllegalArgumentException("libraries 항목에 coordinate 가 없다");
                out.add(new Library(coord.strip(), c.getString("sha256", null)));
            } else if (o instanceof Map<?, ?> m) {
                Object coord = m.containsKey("coordinate") ? m.get("coordinate") : m.get("id");
                if (coord == null) throw new IllegalArgumentException("libraries 항목에 coordinate 가 없다");
                Object sha = m.get("sha256");
                out.add(new Library(String.valueOf(coord).strip(), sha == null ? null : String.valueOf(sha)));
            } else {
                out.add(new Library(String.valueOf(o).strip()));
            }
        }
        return out;
    }

    private static void readPermission(String node, ConfigSection p, PermissionDefault fallback, List<Permission> out) {
        if (p == null) {
            out.add(new Permission(node, fallback));
            return;
        }
        PermissionDefault d = fallback;
        Object dv = p.get("default");
        if (dv != null && PermissionDefault.byName(String.valueOf(dv)) != null) d = PermissionDefault.byName(String.valueOf(dv));
        Map<String, Boolean> children = new LinkedHashMap<>();
        Object ch = p.get("children");
        if (ch instanceof ConfigSection cs) {
            for (String k : cs.keys(false)) {
                Object v = cs.get(k);
                if (v instanceof ConfigSection sub) {
                    readPermission(k, sub, d, out);   // Bukkit: 자식 정의를 품을 수 있다
                    children.put(k, true);
                } else {
                    children.put(k, !(v instanceof Boolean b) || b);
                }
            }
        } else if (ch instanceof List<?> l) {
            for (Object o : l) children.put(String.valueOf(o), true);
        }
        out.add(new Permission(node, p.getString("description", ""), d, children));
    }

    private static String required(ConfigSection s, String key) {
        Object v = s.get(key);
        if (v == null || String.valueOf(v).isBlank()) throw new IllegalArgumentException("설명 파일에 '" + key + "' 가 없다");
        return String.valueOf(v);
    }

    /** 문자열 하나 또는 목록. */
    private static List<String> strings(ConfigSection s, String key) {
        Object v = s.get(key);
        if (v == null) return List.of();
        if (v instanceof List<?> l) {
            List<String> out = new ArrayList<>(l.size());
            for (Object o : l) if (o != null) out.add(String.valueOf(o));
            return out;
        }
        return List.of(String.valueOf(v));
    }
}
