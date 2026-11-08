package org.bukkit.inventory;

/** 아이템 설명에서 숨기는 항목(Paper 1.21.1 과 같은 상수·순서). 옛 이름 HIDE_POTION_EFFECTS 는 클래스를 올릴 때 HIDE_ADDITIONAL_TOOLTIP 으로 바뀐다. */
public enum ItemFlag {
    HIDE_ENCHANTS, HIDE_ATTRIBUTES, HIDE_UNBREAKABLE, HIDE_DESTROYS, HIDE_PLACED_ON, HIDE_ADDITIONAL_TOOLTIP, HIDE_DYE, HIDE_ARMOR_TRIM,
    HIDE_STORED_ENCHANTS;

    /** Paper: 1.20.5 전 이름. */
    @Deprecated(since = "1.20.5")
    public static final ItemFlag HIDE_ITEM_SPECIFICS = HIDE_ADDITIONAL_TOOLTIP;
}
