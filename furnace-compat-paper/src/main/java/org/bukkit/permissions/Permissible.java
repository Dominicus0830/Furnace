package org.bukkit.permissions;

import java.util.Set;
import org.bukkit.plugin.Plugin;

/**
 * 권한을 가질 수 있는 대상. 플레이어는 Bukkit 처럼 {@link PermissibleBase}(CraftHumanEntity 의 {@code perm} 필드)에 맡긴다 —
 * 권한 플러그인이 그 필드를 바꿔 끼우면 Furnace 쪽 권한 확인도 그리로 간다(docs/api/compat-paper.md "권한").
 * 첨부를 받지 않는 대상(엔티티·명령 블록 등)의 첨부 메서드는 UnsupportedOperationException.
 */
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

    default PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) { throw noAttachments(this); }

    default PermissionAttachment addAttachment(Plugin plugin) { throw noAttachments(this); }

    default PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks) { throw noAttachments(this); }

    default PermissionAttachment addAttachment(Plugin plugin, int ticks) { throw noAttachments(this); }

    default void removeAttachment(PermissionAttachment attachment) { throw noAttachments(this); }

    /** Furnace 쪽 권한은 등록소·첨부·OP 가 바뀌면 다음 확인 때 저절로 다시 계산된다. 첨부를 받는 대상은 Bukkit 처럼 다시 계산한다. */
    default void recalculatePermissions() {}

    default Set<PermissionAttachmentInfo> getEffectivePermissions() { throw noAttachments(this); }

    private static UnsupportedOperationException noAttachments(Permissible who) {
        return new org.domi.furnace.compat.paper.UnsupportedFeature("권한 첨부(" + who.getClass().getSimpleName() + ")",
                "이 대상은 Bukkit 권한 첨부를 받지 않는다 — 플레이어만 받는다");
    }
}
