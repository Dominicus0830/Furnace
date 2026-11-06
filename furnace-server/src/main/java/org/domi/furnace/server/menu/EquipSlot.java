// 바닐라 EquipmentSlot(플레이어가 쓰는 것). index 는 Inventory 번호.
package org.domi.furnace.server.menu;

public enum EquipSlot {
    MAINHAND(-1), OFFHAND(40), FEET(36), LEGS(37), CHEST(38), HEAD(39);

    private final int inventoryIndex;

    EquipSlot(int inventoryIndex) { this.inventoryIndex = inventoryIndex; }

    public int inventoryIndex() { return inventoryIndex; }

    /** 방어구 칸인가. */
    public boolean isArmor() { return this == FEET || this == LEGS || this == CHEST || this == HEAD; }
}
