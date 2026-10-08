// 유체 종류 — vanilla FluidState.getType()에 대응(source/flowing 구분).
package org.domi.furnace.server.world.gen.feature;

public enum FluidKind {
    EMPTY, WATER, FLOWING_WATER, LAVA, FLOWING_LAVA;

    public static FluidKind byName(String name) {
        return switch (name) {
            case "minecraft:empty" -> EMPTY;
            case "minecraft:water" -> WATER;
            case "minecraft:flowing_water" -> FLOWING_WATER;
            case "minecraft:lava" -> LAVA;
            case "minecraft:flowing_lava" -> FLOWING_LAVA;
            default -> throw new IllegalArgumentException("알 수 없는 유체: " + name);
        };
    }

    public boolean isWater() { return this == WATER || this == FLOWING_WATER; }
    public boolean isLava() { return this == LAVA || this == FLOWING_LAVA; }
}
