package org.bukkit.permissions;

/** 권한을 가질 수 있는 대상. 첨부(PermissionAttachment)·유효 권한 목록은 H2. */
public interface Permissible extends ServerOperator {

    boolean isPermissionSet(String name);

    boolean hasPermission(String name);
}
