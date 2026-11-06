// 권한을 가질 수 있는 대상(플레이어·콘솔). 확인 순서: 직접 부여(첨부) → 등록된 기본값 → 미등록이면 OP 만.
package org.domi.furnace.api.permission;

import org.domi.furnace.api.plugin.FurnacePlugin;

public interface Permissible {

    boolean isOp();

    boolean hasPermission(String node);

    default boolean hasPermission(Permission perm) { return hasPermission(perm.name()); }

    /** 이 권한 값이 명시적으로(첨부·자식 전파로) 정해졌는가. */
    boolean isPermissionSet(String node);

    /** 플러그인이 권한을 주거나 빼는 첨부. 플러그인이 꺼지면 자동으로 지워진다. */
    PermissionAttachment addAttachment(FurnacePlugin plugin);
}
