package org.bukkit.craftbukkit.block;

import org.bukkit.DyeColor;
import org.bukkit.Material;

/** 블록 엔티티 bed 의 블록 상태(Paper CraftBed 이름). 색은 블록 종류에서 나온다. */
public class CraftBed extends CraftBlockEntityState implements org.bukkit.block.Bed {

    public CraftBed(String dimension, org.domi.furnace.api.world.BlockPos pos, org.bukkit.block.data.BlockData data) { super(dimension, pos, data); }

    @Override
    public DyeColor getColor() {
        Material type = getType();
        String name = type.name();
        if (name.endsWith("_BED")) {
            try {
                return DyeColor.valueOf(name.substring(0, name.length() - 4));
            } catch (IllegalArgumentException ignored) {
                // 아래에서 Paper 와 같은 말로 던진다.
            }
        }
        throw new IllegalArgumentException("Unknown DyeColor for " + type);
    }

    @Override
    public void setColor(DyeColor color) {
        throw new UnsupportedOperationException("Must set block type to appropriate bed colour");
    }
}
