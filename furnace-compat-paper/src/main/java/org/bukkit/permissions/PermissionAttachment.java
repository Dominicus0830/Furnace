package org.bukkit.permissions;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.plugin.Plugin;

/**
 * 한 플러그인이 한 대상에게 준 권한 값 묶음(Bukkit 과 같은 모양). {@code permissions} 필드 이름은 Bukkit 그대로 —
 * LuckPerms 가 리플렉션으로 읽는다.
 */
public class PermissionAttachment {

    private PermissionRemovedExecutor removed;
    private final Map<String, Boolean> permissions = new LinkedHashMap<>();
    private final Permissible permissible;
    private final Plugin plugin;

    public PermissionAttachment(Plugin plugin, Permissible permissible) {
        if (plugin == null) throw new IllegalArgumentException("Plugin cannot be null");
        if (!plugin.isEnabled()) throw new IllegalArgumentException("Plugin " + plugin.getDescription().getFullName() + " is disabled");
        this.permissible = permissible;
        this.plugin = plugin;
    }

    public Plugin getPlugin() { return plugin; }

    public void setRemovalCallback(PermissionRemovedExecutor ex) { removed = ex; }

    public PermissionRemovedExecutor getRemovalCallback() { return removed; }

    public Permissible getPermissible() { return permissible; }

    /** 복사본. */
    public Map<String, Boolean> getPermissions() { return new LinkedHashMap<>(permissions); }

    public void setPermission(String name, boolean value) {
        permissions.put(name.toLowerCase(Locale.ROOT), value);
        permissible.recalculatePermissions();
    }

    public void setPermission(Permission perm, boolean value) { setPermission(perm.getName(), value); }

    public void unsetPermission(String name) {
        permissions.remove(name.toLowerCase(Locale.ROOT));
        permissible.recalculatePermissions();
    }

    public void unsetPermission(Permission perm) { unsetPermission(perm.getName()); }

    /** 떼었으면 true, 이미 붙어 있지 않으면 false. */
    public boolean remove() {
        try {
            permissible.removeAttachment(this);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
