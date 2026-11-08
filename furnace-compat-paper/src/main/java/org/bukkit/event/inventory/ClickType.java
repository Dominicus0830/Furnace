package org.bukkit.event.inventory;

/** 창 클릭의 종류(Paper 1.21.1 과 같다). */
public enum ClickType {
    LEFT, SHIFT_LEFT, RIGHT, SHIFT_RIGHT, WINDOW_BORDER_LEFT, WINDOW_BORDER_RIGHT, MIDDLE, NUMBER_KEY, DOUBLE_CLICK, DROP, CONTROL_DROP,
    CREATIVE, SWAP_OFFHAND, UNKNOWN;

    public boolean isKeyboardClick() { return this == NUMBER_KEY || this == DROP || this == CONTROL_DROP || this == SWAP_OFFHAND; }

    public boolean isMouseClick() {
        return this == DOUBLE_CLICK || this == LEFT || this == RIGHT || this == MIDDLE || this == WINDOW_BORDER_LEFT || this == SHIFT_LEFT
                || this == SHIFT_RIGHT || this == WINDOW_BORDER_RIGHT;
    }

    public boolean isCreativeAction() { return this == MIDDLE || this == CREATIVE; }

    public boolean isRightClick() { return this == RIGHT || this == SHIFT_RIGHT; }

    public boolean isLeftClick() { return this == LEFT || this == SHIFT_LEFT || this == DOUBLE_CLICK || this == CREATIVE; }

    public boolean isShiftClick() { return this == SHIFT_LEFT || this == SHIFT_RIGHT; }
}
