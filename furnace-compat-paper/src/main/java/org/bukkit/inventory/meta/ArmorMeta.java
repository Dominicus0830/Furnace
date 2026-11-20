package org.bukkit.inventory.meta;

import org.bukkit.inventory.meta.trim.ArmorTrim;

/** 입을 수 있는 방어구의 메타(Paper 1.21.1 ArmorMeta): 방어구 장식(trim). */
public interface ArmorMeta extends ItemMeta {

    /** 장식이 있는가. */
    boolean hasTrim();

    /** 장식을 정한다. null 이면 지운다. */
    void setTrim(ArmorTrim trim);

    /** 장식(없으면 null). */
    ArmorTrim getTrim();

    @Override
    ArmorMeta clone();
}
