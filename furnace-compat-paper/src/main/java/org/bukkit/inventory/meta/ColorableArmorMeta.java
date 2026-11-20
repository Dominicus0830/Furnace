package org.bukkit.inventory.meta;

/** 염색할 수 있는 방어구(가죽 투구·갑옷·바지·신발·늑대 갑옷)의 메타(Paper 1.21.1 ColorableArmorMeta). */
public interface ColorableArmorMeta extends ArmorMeta, LeatherArmorMeta {

    @Override
    ColorableArmorMeta clone();
}
