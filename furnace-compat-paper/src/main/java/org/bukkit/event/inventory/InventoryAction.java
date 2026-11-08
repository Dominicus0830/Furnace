package org.bukkit.event.inventory;

/** 클릭이 일으킬 결과(Paper 1.21.1 과 같은 상수 — 묶음 주머니 동작은 1.21.2 부터라 없다). */
public enum InventoryAction {
    NOTHING, PICKUP_ALL, PICKUP_SOME, PICKUP_HALF, PICKUP_ONE, PLACE_ALL, PLACE_SOME, PLACE_ONE, SWAP_WITH_CURSOR, DROP_ALL_CURSOR,
    DROP_ONE_CURSOR, DROP_ALL_SLOT, DROP_ONE_SLOT, MOVE_TO_OTHER_INVENTORY,
    @Deprecated(since = "1.20.6", forRemoval = true)
    HOTBAR_MOVE_AND_READD,
    HOTBAR_SWAP, CLONE_STACK, COLLECT_TO_CURSOR, UNKNOWN
}
