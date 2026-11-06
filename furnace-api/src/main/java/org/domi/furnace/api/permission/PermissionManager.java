// 권한 등록소 — 플러그인이 권한 노드와 기본값을 등록한다(설명 파일의 permissions: 도 여기 들어온다).
package org.domi.furnace.api.permission;

import java.util.Collection;

public interface PermissionManager {

    /** 이미 있으면 IllegalArgumentException. */
    void register(Permission permission);

    void unregister(String name);

    Permission permission(String name);

    Collection<Permission> permissions();
}
