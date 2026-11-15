package org.bukkit;

/**
 * 나무·큰 버섯 등 World#generateTree 가 키울 종류(Paper 1.21.1 과 같은 25 상수·같은 순서).
 * 어느 configured feature 를 쓰는지는 {@code CraftWorld#treeFeature} 표(Paper CraftRegionAccessor 와 같다).
 */
public enum TreeType {
    TREE,
    BIG_TREE,
    REDWOOD,
    TALL_REDWOOD,
    BIRCH,
    JUNGLE,
    SMALL_JUNGLE,
    COCOA_TREE,
    JUNGLE_BUSH,
    RED_MUSHROOM,
    BROWN_MUSHROOM,
    SWAMP,
    ACACIA,
    DARK_OAK,
    MEGA_REDWOOD,
    MEGA_PINE,
    TALL_BIRCH,
    CHORUS_PLANT,
    CRIMSON_FUNGUS,
    WARPED_FUNGUS,
    AZALEA,
    MANGROVE,
    TALL_MANGROVE,
    CHERRY,
    CUSTOM
}
