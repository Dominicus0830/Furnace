package org.domi.furnace.compat.paper;

/** 호환 모듈 전용 — Bukkit {@link org.bukkit.permissions.PermissibleBase} 가 값을 물어볼 Furnace 권한 대상을 가진 객체(플레이어). */
public interface FurnacePermissibleSource {

    /** 이 객체의 Furnace 권한 대상. 생성자 도중에도 불릴 수 있다. */
    org.domi.furnace.api.permission.Permissible furnacePermissible();
}
