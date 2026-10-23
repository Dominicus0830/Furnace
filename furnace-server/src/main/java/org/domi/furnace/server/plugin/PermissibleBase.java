// 대상 하나(플레이어·콘솔)의 권한 상태 — 첨부 목록과 계산 캐시(OP 여부·등록 버전·첨부 버전이 같으면 재사용).
package org.domi.furnace.server.plugin;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import org.domi.furnace.api.permission.Permission;
import org.domi.furnace.api.permission.PermissionAttachment;
import org.domi.furnace.api.plugin.FurnacePlugin;

public final class PermissibleBase {

    private final PermissionManagerImpl manager;
    private final BooleanSupplier op;
    private final List<Attachment> attachments = new CopyOnWriteArrayList<>();
    private volatile long attachmentVersion;
    private volatile Cache cache;

    private record Cache(boolean op, long registry, long attachments, Map<String, Boolean> values) {}

    public PermissibleBase(PermissionManagerImpl manager, BooleanSupplier op) {
        this.manager = manager;
        this.op = op;
    }

    private Map<String, Boolean> values() {
        boolean o = op.getAsBoolean();
        long r = manager.version(), a = attachmentVersion;
        Cache c = cache;
        if (c == null || c.op() != o || c.registry() != r || c.attachments() != a) {
            List<Map<String, Boolean>> maps = new java.util.ArrayList<>(attachments.size());
            for (Attachment at : attachments) maps.add(at.values);
            cache = c = new Cache(o, r, a, manager.compute(o, maps));
        }
        return c.values();
    }

    public boolean isPermissionSet(String node) { return values().containsKey(node.toLowerCase(Locale.ROOT)); }

    /** 명시값 → 등록 기본값 → 미등록이면 OP 만(Bukkit 과 같다). */
    public boolean hasPermission(String node) {
        String n = node.toLowerCase(Locale.ROOT);
        Boolean v = values().get(n);
        if (v != null) return v;
        Permission p = manager.permission(n);
        return (p != null ? p.defaultValue() : Permission.DEFAULT).value(op.getAsBoolean());
    }

    public PermissionAttachment addAttachment(FurnacePlugin plugin) {
        Attachment a = new Attachment(plugin);
        attachments.add(a);
        attachmentVersion++;
        return a;
    }

    /** 플러그인이 꺼질 때 그 플러그인의 첨부를 뗀다. */
    public void removeAttachments(FurnacePlugin plugin) {
        if (attachments.removeIf(a -> a.plugin == plugin)) attachmentVersion++;
    }

    private final class Attachment implements PermissionAttachment {
        private final FurnacePlugin plugin;
        private final Map<String, Boolean> values = new ConcurrentHashMap<>();

        Attachment(FurnacePlugin plugin) { this.plugin = plugin; }

        public FurnacePlugin plugin() { return plugin; }

        public void setPermission(String node, boolean value) {
            values.put(node.toLowerCase(Locale.ROOT), value);
            attachmentVersion++;
        }

        public void unsetPermission(String node) {
            if (values.remove(node.toLowerCase(Locale.ROOT)) != null) attachmentVersion++;
        }

        public Map<String, Boolean> permissions() { return Map.copyOf(values); }

        public void remove() {
            if (attachments.remove(this)) attachmentVersion++;
        }
    }
}
