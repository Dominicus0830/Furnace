package org.bukkit.inventory;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * 아이템 메타를 만들고 비교하는 공장(Paper 1.21.1 ItemFactory, {@code Bukkit.getItemFactory()}). 종류별 메타(머리·현수막·가죽 방어구 …)는
 * 여기서 아이템 종류를 보고 골라 준다. Adventure·BungeeCord 내용물을 쓰는 메서드(asHoverEvent·displayName·hoverContentOf)와
 * RegistryKeySet 판 enchantWithLevels 는 이 계층에 그 형이 없어 빠져 있다.
 */
public interface ItemFactory {

    /** 그 재료의 새 빈 메타. 공기는 null, 아이템이 아닌 재료는 일반 메타. */
    ItemMeta getItemMeta(Material material);

    /** 이 메타를 그 스택에 붙일 수 있는가. 스택이 null 이면 false, 이 계층이 만들지 않은 메타면 IllegalArgumentException. */
    boolean isApplicable(ItemMeta meta, ItemStack stack) throws IllegalArgumentException;

    boolean isApplicable(ItemMeta meta, Material material) throws IllegalArgumentException;

    /** 두 메타가 같은가. 한쪽이 null 이면 다른 쪽이 비어 있는지 본다. */
    boolean equals(ItemMeta meta1, ItemMeta meta2) throws IllegalArgumentException;

    /** 그 스택 종류에 맞는 메타로 옮긴다(공기면 null). 종류가 맞으면 같은 객체일 수 있다. */
    ItemMeta asMetaFor(ItemMeta meta, ItemStack stack) throws IllegalArgumentException;

    ItemMeta asMetaFor(ItemMeta meta, Material material) throws IllegalArgumentException;

    /** 가죽 방어구의 기본 색(0xA06540). */
    Color getDefaultLeatherColor();

    /** 아이템 문자열({@code minecraft:stone[...]})로 스택을 만든다. 아직 지원하지 않는다. */
    ItemStack createItemStack(String input) throws IllegalArgumentException;

    /** 그 몹의 스폰 알. 알이 없는 몹과 UNKNOWN 은 null. */
    Material getSpawnEgg(EntityType type);

    /** 아직 지원하지 않는다(서버의 마법 부여 로직을 호출할 길이 없다). */
    ItemStack enchantItem(Entity entity, ItemStack item, int level, boolean allowTreasures);

    ItemStack enchantItem(World world, ItemStack item, int level, boolean allowTreasures);

    @Deprecated
    ItemStack enchantItem(ItemStack item, int level, boolean allowTreasures);

    /** 아직 지원하지 않는다. */
    ItemStack enchantWithLevels(ItemStack itemStack, int levels, boolean allowTreasure, java.util.Random random);

    /** 번역 이름(영어 기본)을 돌려주는 Paper 확장 — 번역 표가 없어 아직 지원하지 않는다. */
    @Deprecated
    String getI18NDisplayName(ItemStack item);

    /** 서버가 읽을 때 하는 변환을 거친 사본. Furnace 는 옛 형식을 따로 변환하지 않아 사본 그대로다. */
    ItemStack ensureServerConversions(ItemStack item);
}
