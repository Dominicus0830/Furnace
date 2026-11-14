// 생성됨: tools/compat/gen-bukkit-block-state-classes.py — 손으로 고치려면 이 줄을 지운다.
package org.bukkit.craftbukkit.block;

/** Paper CraftLootable 이름의 추상 칸. */
public abstract class CraftLootable extends org.bukkit.craftbukkit.block.CraftContainer implements org.bukkit.Nameable, org.bukkit.loot.Lootable {

    protected CraftLootable(String dimension, org.domi.furnace.api.world.BlockPos pos, org.bukkit.block.data.BlockData data) { super(dimension, pos, data); }
}
