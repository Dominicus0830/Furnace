// 권한 등록소 + 계산. 바뀔 때마다 버전이 올라가 대상별 캐시가 무효가 된다.
package org.domi.furnace.server.plugin;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.domi.furnace.api.permission.Permission;
import org.domi.furnace.api.permission.PermissionManager;

public final class PermissionManagerImpl implements PermissionManager {

    private final ConcurrentHashMap<String, Permission> permissions = new ConcurrentHashMap<>();
    private final AtomicLong version = new AtomicLong();

    @Override
    public void register(Permission permission) {
        if (permissions.putIfAbsent(permission.name(), permission) != null) {
            throw new IllegalArgumentException("이미 등록된 권한: " + permission.name());
        }
        version.incrementAndGet();
    }

    @Override
    public void replace(Permission permission) {
        permissions.put(permission.name(), permission);
        version.incrementAndGet();
    }

    @Override
    public void unregister(String name) {
        if (permissions.remove(name.toLowerCase(Locale.ROOT)) != null) version.incrementAndGet();
    }

    @Override
    public Permission permission(String name) { return permissions.get(name.toLowerCase(Locale.ROOT)); }

    @Override
    public Collection<Permission> permissions() { return List.copyOf(permissions.values()); }

    long version() { return version.get(); }

    /**
     * Bukkit PermissibleBase.recalculatePermissions 와 같은 결과: 기본값이 참인 등록 권한(과 그 자식)을 먼저 깔고,
     * 첨부를 순서대로 덮는다. 자식 값은 (부모 값이 참이면 자식에 적힌 값, 거짓이면 그 반대).
     */
    Map<String, Boolean> compute(boolean op, List<Map<String, Boolean>> attachments) {
        Map<String, Boolean> out = new HashMap<>();
        for (Permission p : permissions.values()) {
            if (p.defaultValue().value(op)) {
                out.put(p.name(), true);
                children(p, true, out, 0);
            }
        }
        for (Map<String, Boolean> a : attachments) {
            for (var e : a.entrySet()) {
                String name = e.getKey().toLowerCase(Locale.ROOT);
                out.put(name, e.getValue());
                Permission p = permissions.get(name);
                if (p != null) children(p, e.getValue(), out, 0);
            }
        }
        return out;
    }

    private void children(Permission p, boolean parentValue, Map<String, Boolean> out, int depth) {
        if (depth > 32) return;   // 순환 방어
        for (var c : p.children().entrySet()) {
            boolean v = c.getValue() ^ !parentValue;
            String name = c.getKey().toLowerCase(Locale.ROOT);
            out.put(name, v);
            Permission cp = permissions.get(name);
            if (cp != null) children(cp, v, out, depth + 1);
        }
    }
}
