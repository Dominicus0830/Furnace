package org.bukkit.inventory.meta;

import org.bukkit.Color;

/**
 * 가죽 방어구·가죽 말 갑옷의 염색 색(Paper 1.21.1 LeatherArmorMeta). 색이 없으면 {@link #getColor} 는 기본 가죽 색(0xA06540)이다.
 * 가죽 투구·갑옷·바지·신발·늑대 갑옷의 메타는 {@link ColorableArmorMeta}(이 인터페이스에 방어구 장식 {@link ArmorMeta} 를 더한 것)이고,
 * 가죽 말 갑옷의 메타는 이 인터페이스뿐이다(Paper 와 같다).
 */
public interface LeatherArmorMeta extends ItemMeta {

    /** 염색 색(염색하지 않았으면 기본 가죽 색). 알파는 늘 255. */
    Color getColor();

    /** 염색 색을 정한다. null 이면 염색을 지운다. 알파는 버려진다. */
    void setColor(Color color);

    /** 염색했는가(Paper 확장). */
    boolean isDyed();

    @Override
    LeatherArmorMeta clone();
}
