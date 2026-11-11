package org.bukkit.event.block;

/** 플레이어 상호작용 종류(PlayerInteractEvent#getAction). PHYSICAL 은 압력판·농지 밟기 — Furnace 는 아직 알리지 않는다. */
public enum Action {
    LEFT_CLICK_BLOCK, RIGHT_CLICK_BLOCK, LEFT_CLICK_AIR, RIGHT_CLICK_AIR, PHYSICAL;

    public boolean isLeftClick() { return this == LEFT_CLICK_AIR || this == LEFT_CLICK_BLOCK; }

    public boolean isRightClick() { return this == RIGHT_CLICK_AIR || this == RIGHT_CLICK_BLOCK; }
}
