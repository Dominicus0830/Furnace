package org.bukkit.inventory.meta;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.persistence.PersistentDataHolder;

/**
 * 아이템 데이터(이름·설명·마법 부여·표시 항목·영속 데이터). Bukkit 처럼 사본이다 — ItemStack#getItemMeta 가 사본을 주고,
 * 바꾼 뒤 ItemStack#setItemMeta 로 돌려놓아야 아이템에 반영된다.
 * 이름·설명은 § 서식 문자열이다. 이름·설명이 없으면 get 은 null 을 돌려준다.
 * ConfigurationSerializable 이라 설정 파일·객체 스트림에 저장된다(별칭 "ItemMeta", 맵 모양은 Paper 1.21.1 을 따른다).
 */
public interface ItemMeta extends Cloneable, ConfigurationSerializable, PersistentDataHolder {

    boolean hasDisplayName();

    String getDisplayName();

    void setDisplayName(String name);

    /** 기본 아이템 이름(minecraft:item_name). */
    boolean hasItemName();

    String getItemName();

    void setItemName(String name);

    boolean hasLore();

    List<String> getLore();

    void setLore(List<String> lore);

    boolean hasCustomModelData();

    int getCustomModelData();

    void setCustomModelData(Integer data);

    boolean hasEnchants();

    boolean hasEnchant(Enchantment ench);

    int getEnchantLevel(Enchantment ench);

    Map<Enchantment, Integer> getEnchants();

    /**
     * 최대 레벨 검사는 하지 않는다(서버가 최대 레벨을 알려 주지 않는다): ignoreLevelRestriction 이 거짓이어도 1~255 이면 넣는다.
     * 바뀌었으면 true.
     */
    boolean addEnchant(Enchantment ench, int level, boolean ignoreLevelRestriction);

    boolean removeEnchant(Enchantment ench);

    void addItemFlags(ItemFlag... flags);

    void removeItemFlags(ItemFlag... flags);

    Set<ItemFlag> getItemFlags();

    boolean hasItemFlag(ItemFlag flag);

    boolean isUnbreakable();

    void setUnbreakable(boolean unbreakable);

    boolean isFireResistant();

    void setFireResistant(boolean fireResistant);

    boolean isHideTooltip();

    void setHideTooltip(boolean hideTooltip);

    boolean hasEnchantmentGlintOverride();

    /** 없으면 IllegalStateException(Bukkit 과 같다) — hasEnchantmentGlintOverride 로 먼저 본다. */
    Boolean getEnchantmentGlintOverride();

    void setEnchantmentGlintOverride(Boolean override);

    ItemMeta clone();
}
