package org.bukkit.craftbukkit.util;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.bukkit.Material;
import org.domi.furnace.compat.nms.NmsBlocks;

/**
 * CraftBukkit Material ↔ NMS 블록·아이템 변환(N0). CraftBukkit 처럼 블록이 아닌 Material 이면 {@code getBlock} 이 null,
 * 아이템이 아니면 {@code getItem} 이 null 이다. UnsafeValues 구현(INSTANCE)은 이 클래스에 아직 없다.
 */
public final class CraftMagicNumbers {

    private CraftMagicNumbers() {}

    public static Block getBlock(Material material) {
        if (material == null || !material.isBlock()) return null;
        return NmsBlocks.registry().getOptional(net.minecraft.resources.ResourceLocation.parse(material.getKey().toString())).orElse(null);
    }

    public static Item getItem(Material material) {
        if (material == null || !material.isItem()) return null;
        return Item.byKey(material.getKey().toString());
    }

    /** Bukkit Material 열거형에 없는 블록(데이터 팩·플러그인 블록)이면 null. */
    public static Material getMaterial(Block block) { return block == null ? null : Material.matchMaterial(block.furnaceKey().toString()); }

    /** CraftBukkit 처럼 null·Bukkit Material 열거형에 없는 아이템이면 AIR(블록 쪽은 null). */
    public static Material getMaterial(Item item) {
        Material m = item == null ? null : Material.matchMaterial(item.furnaceKey());
        return m != null ? m : Material.AIR;
    }
}
