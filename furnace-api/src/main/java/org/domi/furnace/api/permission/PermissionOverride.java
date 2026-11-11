package org.domi.furnace.api.permission;

/**
 * 권한 판정을 통째로 맡는 공급자 — Bukkit 에서 권한 플러그인(LuckPerms 등)이 플레이어의 Permissible 을 바꿔 끼우는 것에 해당한다.
 * {@link PermissionManager#setOverride} 로 하나만 둔다. 서버는 대상(플레이어·콘솔)의 {@code hasPermission}·{@code isPermissionSet} 을
 * 계산하기 전에 먼저 이것을 묻고, null 이 오면 기본 규칙(첨부 → 등록 기본값 → 미등록이면 OP)으로 판정한다.
 * 아무 스레드에서나 불린다. 구현은 subject 의 {@code hasPermission} 을 다시 부르지 않는다(무한 재귀).
 */
public interface PermissionOverride {

    /** 그 대상의 권한 값, 맡지 않으면 null. */
    Boolean hasPermission(Permissible subject, String node);

    /** 그 대상에게 이 권한 값이 정해져 있는지, 맡지 않으면 null. */
    Boolean isPermissionSet(Permissible subject, String node);
}
