package org.bukkit.permissions;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.domi.furnace.compat.paper.FurnacePermissibleSource;

/**
 * Bukkit 의 권한 계산 기반(Paper 1.21.1 과 같은 필드·메서드·검사 문구, 공개 메서드는 Paper 처럼 synchronized).
 * {@code attachments}·{@code permissions} 필드 이름·종류는 Bukkit 그대로다 — LuckPerms 가 리플렉션으로 읽고 바꾼다.
 *
 * <p><b>Furnace 쪽 대상:</b> opable 이 {@link FurnacePermissibleSource}(플레이어)면 값은 Furnace 권한 계산에서 온다.
 * (정확히 이 클래스일 때만 — 하위 클래스는 Bukkit 그대로 동작한다.) 그러면 Furnace 플러그인의 첨부와 Bukkit 플러그인의 첨부가 한 계산에 함께 든다 — 이 클래스의 Bukkit 첨부는 같은 플러그인의
 * Furnace 첨부로 거울처럼 옮겨지고(다시 계산할 때 맞춘다), {@code hasPermission}·{@code isPermissionSet} 은 Furnace 대상에 묻는다.
 * {@code permissions}(유효 권한 목록)는 Bukkit 규칙대로 Bukkit 쪽 첨부와 기본값만으로 계산한다.
 */
public class PermissibleBase implements Permissible {

    private final ServerOperator opable;
    private final Permissible parent;
    private final List<PermissionAttachment> attachments = new LinkedList<>();
    private final Map<String, PermissionAttachmentInfo> permissions = new HashMap<>();
    /** Furnace 쪽 대상이면 그 대상, 아니면 null. */
    private final org.domi.furnace.api.permission.Permissible furnace;
    /** Bukkit 첨부 → 그것을 옮긴 Furnace 첨부(Furnace 쪽 대상일 때만). */
    private final Map<PermissionAttachment, org.domi.furnace.api.permission.PermissionAttachment> mirrors = new IdentityHashMap<>();

    public PermissibleBase(ServerOperator opable) {
        this.opable = opable;
        this.parent = opable instanceof Permissible p ? p : this;
        // 하위 클래스(LuckPerms 의 LuckPermsPermissible 등)는 Bukkit 그대로의 동작을 기대하므로 Furnace 쪽 대상으로 쓰지 않는다
        this.furnace = getClass() == PermissibleBase.class && opable instanceof FurnacePermissibleSource s ? s.furnacePermissible() : null;
        recalculatePermissions();
    }

    @Override
    public boolean isOp() { return opable != null && opable.isOp(); }

    @Override
    public void setOp(boolean value) {
        if (opable == null) throw new UnsupportedOperationException("Cannot change op value as no ServerOperator is set");
        opable.setOp(value);
    }

    @Override
    public boolean isPermissionSet(String name) {
        if (name == null) throw new IllegalArgumentException("Permission name cannot be null");
        if (furnace != null) return furnace.isPermissionSet(name);
        return permissions.containsKey(name.toLowerCase(Locale.ROOT));
    }

    @Override
    public boolean isPermissionSet(Permission perm) {
        if (perm == null) throw new IllegalArgumentException("Permission cannot be null");
        return isPermissionSet(perm.getName());
    }

    @Override
    public boolean hasPermission(String inName) {
        if (inName == null) throw new IllegalArgumentException("Permission name cannot be null");
        if (furnace != null) return furnace.hasPermission(inName);
        String name = inName.toLowerCase(Locale.ROOT);
        PermissionAttachmentInfo info = permissions.get(name);
        if (info != null) return info.getValue();
        Permission perm = Bukkit.getServer().getPluginManager().getPermission(name);
        return (perm != null ? perm.getDefault() : Permission.DEFAULT_PERMISSION).getValue(isOp());
    }

    @Override
    public boolean hasPermission(Permission perm) {
        if (perm == null) throw new IllegalArgumentException("Permission cannot be null");
        String name = perm.getName().toLowerCase(Locale.ROOT);
        if (furnace != null) {
            if (furnace.isPermissionSet(name)) return furnace.hasPermission(name);
            return perm.getDefault().getValue(isOp());
        }
        PermissionAttachmentInfo info = permissions.get(name);
        if (info != null) return info.getValue();
        return perm.getDefault().getValue(isOp());
    }

    @Override
    public synchronized PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) {
        if (name == null) throw new IllegalArgumentException("Permission name cannot be null");
        if (plugin == null) throw new IllegalArgumentException("Plugin cannot be null");
        if (!plugin.isEnabled()) throw new IllegalArgumentException("Plugin " + plugin.getDescription().getFullName() + " is disabled");
        PermissionAttachment result = addAttachment(plugin);
        result.setPermission(name, value);
        recalculatePermissions();
        return result;
    }

    @Override
    public synchronized PermissionAttachment addAttachment(Plugin plugin) {
        if (plugin == null) throw new IllegalArgumentException("Plugin cannot be null");
        if (!plugin.isEnabled()) throw new IllegalArgumentException("Plugin " + plugin.getDescription().getFullName() + " is disabled");
        PermissionAttachment result = new PermissionAttachment(plugin, parent);
        attachments.add(result);
        recalculatePermissions();
        return result;
    }

    @Override
    public synchronized void removeAttachment(PermissionAttachment attachment) {
        if (attachment == null) throw new IllegalArgumentException("Attachment cannot be null");
        if (attachments.contains(attachment)) {
            attachments.remove(attachment);
            PermissionRemovedExecutor ex = attachment.getRemovalCallback();
            if (ex != null) ex.attachmentRemoved(attachment);
            recalculatePermissions();
        } else {
            throw new IllegalArgumentException("Given attachment is not part of Permissible object " + parent);
        }
    }

    @Override
    public synchronized void recalculatePermissions() {
        clearPermissions();
        PluginManager pm = pluginManager();
        if (pm != null) {
            Set<Permission> defaults = pm.getDefaultPermissions(isOp());
            pm.subscribeToDefaultPerms(isOp(), parent);
            for (Permission perm : defaults) {
                String name = perm.getName().toLowerCase(Locale.ROOT);
                permissions.put(name, new PermissionAttachmentInfo(parent, name, null, true));
                pm.subscribeToPermission(name, parent);
                calculateChildPermissions(pm, perm.getChildren(), false, null);
            }
            for (PermissionAttachment attachment : attachments) calculateChildPermissions(pm, attachment.getPermissions(), false, attachment);
        }
        syncMirrors();
    }

    public synchronized void clearPermissions() {
        PluginManager pm = pluginManager();
        if (pm != null) {
            for (String name : permissions.keySet()) pm.unsubscribeFromPermission(name, parent);
            pm.unsubscribeFromDefaultPerms(false, parent);
            pm.unsubscribeFromDefaultPerms(true, parent);
        }
        permissions.clear();
        dropDetachedMirrors();
    }

    private void calculateChildPermissions(PluginManager pm, Map<String, Boolean> children, boolean invert, PermissionAttachment attachment) {
        for (Map.Entry<String, Boolean> e : children.entrySet()) {
            String name = e.getKey();
            Permission perm = pm.getPermission(name);
            boolean value = e.getValue() ^ invert;
            String lname = name.toLowerCase(Locale.ROOT);
            permissions.put(lname, new PermissionAttachmentInfo(parent, lname, attachment, value));
            pm.subscribeToPermission(name, parent);
            if (perm != null) calculateChildPermissions(pm, perm.getChildren(), !value, attachment);
        }
    }

    @Override
    public synchronized PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks) {
        if (name == null) throw new IllegalArgumentException("Permission name cannot be null");
        if (plugin == null) throw new IllegalArgumentException("Plugin cannot be null");
        if (!plugin.isEnabled()) throw new IllegalArgumentException("Plugin " + plugin.getDescription().getFullName() + " is disabled");
        PermissionAttachment result = addAttachment(plugin, ticks);
        if (result != null) result.setPermission(name, value);
        return result;
    }

    @Override
    public synchronized PermissionAttachment addAttachment(Plugin plugin, int ticks) {
        if (plugin == null) throw new IllegalArgumentException("Plugin cannot be null");
        if (!plugin.isEnabled()) throw new IllegalArgumentException("Plugin " + plugin.getDescription().getFullName() + " is disabled");
        PermissionAttachment result = addAttachment(plugin);
        if (Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new RemoveAttachmentRunnable(result), ticks) == -1) {
            Bukkit.getServer().getLogger().log(Level.WARNING, "Could not add PermissionAttachment to " + parent + " for plugin "
                    + plugin.getDescription().getFullName() + ": Scheduler returned -1");
            result.remove();
            return null;
        }
        return result;
    }

    @Override
    public synchronized Set<PermissionAttachmentInfo> getEffectivePermissions() { return new HashSet<>(permissions.values()); }

    private static PluginManager pluginManager() {
        var server = Bukkit.getServer();
        return server == null ? null : server.getPluginManager();
    }

    // ---- Furnace 쪽 대상: Bukkit 첨부를 같은 플러그인의 Furnace 첨부로 옮긴다

    private void syncMirrors() {
        if (furnace == null) return;
        dropDetachedMirrors();
        for (PermissionAttachment a : attachments) {
            org.domi.furnace.api.permission.PermissionAttachment m = mirrors.get(a);
            if (m == null) {
                var owner = org.domi.furnace.compat.paper.CompatPluginManager.adapterOrNull(a.getPlugin());
                if (owner == null) continue;   // 이 서버가 불러오지 않은 Plugin 객체 — Bukkit 쪽 계산에만 든다
                m = furnace.addAttachment(owner);
                mirrors.put(a, m);
            }
            Map<String, Boolean> want = a.getPermissions();
            for (String k : m.permissions().keySet()) if (!want.containsKey(k)) m.unsetPermission(k);
            for (Map.Entry<String, Boolean> e : want.entrySet()) {
                if (!e.getValue().equals(m.permissions().get(e.getKey()))) m.setPermission(e.getKey(), e.getValue());
            }
        }
    }

    private void dropDetachedMirrors() {
        if (furnace == null || mirrors.isEmpty()) return;
        var it = mirrors.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (!attachments.contains(e.getKey())) {
                e.getValue().remove();
                it.remove();
            }
        }
    }

    private static final class RemoveAttachmentRunnable implements Runnable {
        private final PermissionAttachment attachment;

        RemoveAttachmentRunnable(PermissionAttachment attachment) { this.attachment = attachment; }

        @Override
        public void run() { attachment.remove(); }
    }
}
