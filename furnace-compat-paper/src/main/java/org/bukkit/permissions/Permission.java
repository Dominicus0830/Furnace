package org.bukkit.permissions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.PluginManager;

/**
 * 권한 노드 하나(Bukkit Permission). 서버에 올리면({@link PluginManager#addPermission}) Furnace 권한 등록소에 같은 노드가 올라가고,
 * 자식·기본값을 바꾼 뒤 {@link #recalculatePermissibles()}(setDefault 는 스스로 부른다)를 부르면 등록소에도 반영된다 — Bukkit 과 같은 순서다.
 */
public class Permission {

    public static final PermissionDefault DEFAULT_PERMISSION = PermissionDefault.OP;

    private final String name;
    private final Map<String, Boolean> children = new LinkedHashMap<>();
    private PermissionDefault defaultValue = DEFAULT_PERMISSION;
    private String description;

    public Permission(String name) { this(name, null, null, null); }

    public Permission(String name, String description) { this(name, description, null, null); }

    public Permission(String name, PermissionDefault defaultValue) { this(name, null, defaultValue, null); }

    public Permission(String name, String description, PermissionDefault defaultValue) { this(name, description, defaultValue, null); }

    public Permission(String name, Map<String, Boolean> children) { this(name, null, null, children); }

    public Permission(String name, String description, Map<String, Boolean> children) { this(name, description, null, children); }

    public Permission(String name, PermissionDefault defaultValue, Map<String, Boolean> children) { this(name, null, defaultValue, children); }

    public Permission(String name, String description, PermissionDefault defaultValue, Map<String, Boolean> children) {
        if (name == null) throw new IllegalArgumentException("Name cannot be null");
        this.name = name;
        this.description = description == null ? "" : description;
        if (defaultValue != null) this.defaultValue = defaultValue;
        if (children != null) this.children.putAll(children);
        recalculatePermissibles();
    }

    public String getName() { return name; }

    /** 바꿀 수 있는 자식 지도. 바꾼 뒤에는 {@link #recalculatePermissibles()} 를 불러야 반영된다. */
    public Map<String, Boolean> getChildren() { return children; }

    public PermissionDefault getDefault() { return defaultValue; }

    public void setDefault(PermissionDefault value) {
        if (value == null) throw new IllegalArgumentException("Default value cannot be null");
        defaultValue = value;
        recalculatePermissibles();
    }

    public String getDescription() { return description; }

    public void setDescription(String value) { description = value == null ? "" : value; }

    /** 이 권한을 구독한(값이 정해진) 대상들. */
    public Set<Permissible> getPermissibles() {
        PluginManager pm = pluginManager();
        return pm == null ? Collections.emptySet() : pm.getPermissionSubscriptions(name);
    }

    /** 기본값 묶음을 다시 계산하고 구독한 대상의 권한을 다시 계산한다. */
    public void recalculatePermissibles() {
        PluginManager pm = pluginManager();
        if (pm == null) return;   // 서버가 아직 없다(설명 파일을 읽는 중 등)
        Set<Permissible> perms = pm.getPermissionSubscriptions(name);
        pm.recalculatePermissionDefaults(this);
        for (Permissible p : perms) p.recalculatePermissions();
    }

    /** 이름의 권한(없으면 새로 만들어 올린다)에 이 권한을 자식으로 단다. */
    public Permission addParent(String name, boolean value) {
        PluginManager pm = Bukkit.getServer().getPluginManager();
        String lname = name.toLowerCase(Locale.ROOT);
        Permission perm = pm.getPermission(lname);
        if (perm == null) {
            perm = new Permission(lname);
            pm.addPermission(perm);
        }
        addParent(perm, value);
        return perm;
    }

    public void addParent(Permission perm, boolean value) {
        perm.getChildren().put(getName(), value);
        perm.recalculatePermissibles();
    }

    private static PluginManager pluginManager() {
        Server s = Bukkit.getServer();
        return s == null ? null : s.getPluginManager();
    }

    // ------------------------------------------------------------------ plugin.yml 의 permissions: 읽기 (Bukkit 과 같은 규칙)

    public static List<Permission> loadPermissions(Map<?, ?> data, String error, PermissionDefault def) {
        List<Permission> result = new ArrayList<>();
        for (Map.Entry<?, ?> entry : data.entrySet()) {
            try {
                result.add(loadPermission(entry.getKey().toString(), (Map<?, ?>) entry.getValue(), def, result));
            } catch (Throwable ex) {
                Bukkit.getServer().getLogger().log(java.util.logging.Level.SEVERE, String.format(error, entry.getKey()), ex);
            }
        }
        return result;
    }

    public static Permission loadPermission(String name, Map<String, Object> data) {
        return loadPermission(name, data, DEFAULT_PERMISSION, null);
    }

    public static Permission loadPermission(String name, Map<?, ?> data, PermissionDefault def, List<Permission> output) {
        if (name == null) throw new IllegalArgumentException("Name cannot be null");
        if (data == null) throw new IllegalArgumentException("Data cannot be null");
        String desc = null;
        Map<String, Boolean> children = null;
        if (data.get("default") != null) {
            PermissionDefault value = PermissionDefault.getByName(data.get("default").toString());
            if (value == null) throw new IllegalArgumentException("'default' key contained unknown value");
            def = value;
        }
        if (data.get("children") != null) {
            Object childrenNode = data.get("children");
            if (childrenNode instanceof Iterable<?> it) {
                children = new LinkedHashMap<>();
                for (Object child : it) if (child != null) children.put(child.toString(), Boolean.TRUE);
            } else if (childrenNode instanceof Map<?, ?> m) {
                children = extractChildren(m, name, def, output);
            } else {
                throw new IllegalArgumentException("'children' key is of wrong type");
            }
        }
        if (data.get("description") != null) desc = data.get("description").toString();
        return new Permission(name, desc, def, children);
    }

    private static Map<String, Boolean> extractChildren(Map<?, ?> input, String name, PermissionDefault def, List<Permission> output) {
        Map<String, Boolean> children = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : input.entrySet()) {
            if (entry.getValue() instanceof Boolean b) {
                children.put(entry.getKey().toString(), b);
            } else if (entry.getValue() instanceof Map<?, ?> m) {
                try {
                    Permission perm = loadPermission(entry.getKey().toString(), m, def, output);
                    children.put(perm.getName(), Boolean.TRUE);
                    if (output != null) output.add(perm);
                } catch (Throwable ex) {
                    throw new IllegalArgumentException("Permission node '" + entry.getKey() + "' in child of " + name + " is invalid", ex);
                }
            } else {
                throw new IllegalArgumentException("Child '" + entry.getKey() + "' contains invalid value");
            }
        }
        return children;
    }
}
