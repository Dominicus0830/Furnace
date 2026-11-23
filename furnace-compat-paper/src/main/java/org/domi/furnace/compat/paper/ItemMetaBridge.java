package org.domi.furnace.compat.paper;

import org.bukkit.inventory.meta.ItemMeta;
import org.domi.furnace.api.item.ItemStack;

/**
 * T-264 P2b: Bukkit {@code ItemStack} 처럼 craftbukkit 꾸러미 밖에서 아이템 메타(패키지 전용 {@code CraftMetaItem} 계층)를 쓰는 길.
 * Paper 의 공개 진입점(CraftItemStack#getItemMeta·setItemMeta)은 NMS 스택을 받으므로, Furnace 스택용 길은 여기 하나로 둔다(핸들은 CraftAccess).
 */
public final class ItemMetaBridge {
    private ItemMetaBridge() {}

    /** 스택 종류에 맞는 메타 사본(머리면 SkullMeta 등). 공기는 null. */
    public static ItemMeta of(ItemStack stack) { return CraftAccess.metaOf(stack); }

    /** 메타를 붙인 스택(비었으면 구성 요소를 모두 뺀 스택), 이 아이템에 붙일 수 없는 종류면 null. 이 계층이 만든 메타가 아니면 IllegalArgumentException. */
    public static ItemStack putInto(ItemMeta meta, ItemStack cur) { return CraftAccess.metaPutInto(meta, cur); }

    /** 이 계층(CraftMetaItem)이 만든 메타인가. */
    public static boolean isCraftMeta(Object meta) { return CraftAccess.CRAFT_META_ITEM.isInstance(meta); }
}
