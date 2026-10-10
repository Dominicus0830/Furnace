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
 */
public record PluginDescription(String name, String version, String main, String description, List<String> authors, String website,
                                String apiVersion, List<String> depend, List<String> softDepend, List<String> loadBefore, String prefix,
                                Map<String, CommandInfo> commands, List<Permission> permissions) {

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
    }

    /** 로그에 쓰는 이름(prefix 가 있으면 그것). */
    public String logPrefix() { return prefix != null ? prefix : name; }

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
                s.getString("prefix", null), commands, permissions);
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
