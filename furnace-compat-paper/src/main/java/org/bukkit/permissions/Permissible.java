package org.bukkit.permissions;

/** 권한을 가질 수 있는 대상. 첨부(PermissionAttachment)·유효 권한 목록은 아직 없다. */
public interface Permissible extends ServerOperator {

    boolean isPermissionSet(String name);

    /** Bukkit 처럼 이름(소문자)으로 본다. */
    default boolean isPermissionSet(Permission perm) {
        if (perm == null) throw new IllegalArgumentException("Permission cannot be null");
        return isPermissionSet(perm.getName());
    }

    boolean hasPermission(String name);

    /** 값이 정해져 있으면 그 값, 아니면 그 권한 객체의 기본값(Bukkit PermissibleBase 와 같다 — 등록된 기본값이 아니라 이 객체의 것). */
    default boolean hasPermission(Permission perm) {
        if (perm == null) throw new IllegalArgumentException("Permission cannot be null");
        String name = perm.getName().toLowerCase(java.util.Locale.ROOT);
        if (isPermissionSet(name)) return hasPermission(name);
        return perm.getDefault().getValue(isOp());
    }

    /** Furnace 는 권한 등록소·첨부·OP 가 바뀌면 다음 확인 때 저절로 다시 계산한다 — 부를 일이 없다. */
    default void recalculatePermissions() {}
}
