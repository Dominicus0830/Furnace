// 권한 등록소 — 플러그인이 권한 노드와 기본값을 등록한다(설명 파일의 permissions: 도 여기 들어온다).
package org.domi.furnace.api.permission;

import java.util.Collection;

public interface PermissionManager {

    /** 이미 있으면 IllegalArgumentException. */
    void register(Permission permission);

    void unregister(String name);

    /** 같은 이름의 노드를 바꾼다(없으면 올린다). 서버 구현은 한 번에 바꿔 사이에 노드가 빈 순간이 없다 — 이 기본 구현은 지우고 올리는 두 걸음이다. */
    default void replace(Permission permission) {
        unregister(permission.name());
        register(permission);
    }

    Permission permission(String name);

    Collection<Permission> permissions();

    /**
     * 판정 공급자를 둔다(null 이면 뗀다). 하나만 — 새로 두면 이전 것을 바꾼다. 이 기본 구현은 지원하지 않는다(서버 구현이 지원한다).
     * Bukkit 호환 계층이 LuckPerms 같은 권한 플러그인의 판정을 Furnace 쪽 확인에도 돌려 주려고 쓴다.
     */
    default void setOverride(PermissionOverride override) { throw new UnsupportedOperationException("이 권한 등록소는 판정 공급자를 받지 않는다"); }

    /** 지금 둔 판정 공급자, 없으면 null. */
    default PermissionOverride override() { return null; }
}
