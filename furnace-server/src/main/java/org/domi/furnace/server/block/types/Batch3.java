// 이식 묶음 3 의 블록 종류 등록(묶음마다 파일을 나눠 병렬 이식이 서로 부딪히지 않게).
package org.domi.furnace.server.block.types;

final class Batch3 {
    private Batch3() {}

    static void register() {
        Reg.type("minecraft:fire", FireBlock::new);
        Reg.type("minecraft:soul_fire", SoulFireBlock::new);
        Reg.type("minecraft:fence", FenceBlock::new);
        Reg.type("minecraft:iron_bars", IronBarsBlock::new);
        Reg.type("minecraft:stained_glass_pane", StainedGlassPaneBlock::new);
        Reg.type("minecraft:shulker_box", ShulkerBoxBlock::new);
        Reg.type("minecraft:pressure_plate", PressurePlateBlock::new);
        Reg.type("minecraft:weighted_pressure_plate", WeightedPressurePlateBlock::new);
        Reg.type("minecraft:cherry_leaves", CherryLeavesBlock::new);
        Reg.type("minecraft:leaves", LeavesBlock::new);
        Reg.type("minecraft:mangrove_leaves", MangroveLeavesBlock::new);
        Reg.type("minecraft:scaffolding", ScaffoldingBlock::new);
        Reg.type("minecraft:slab", SlabBlock::new);
        Reg.type("minecraft:weathering_copper_slab", WeatheringCopperSlabBlock::new);
        Reg.type("minecraft:frogspawn", FrogspawnBlock::new);
        Reg.type("minecraft:redstone_ore", RedStoneOreBlock::new);
        Reg.type("minecraft:barrel", BarrelBlock::new);
        Reg.type("minecraft:lantern", LanternBlock::new);
        Reg.type("minecraft:conduit", ConduitBlock::new);
        Reg.type("minecraft:beacon", BeaconBlock::new);
        Reg.type("minecraft:mud", MudBlock::new);
        Reg.type("minecraft:dead_bush", DeadBushBlock::new);
        Reg.type("minecraft:glazed_terracotta", GlazedTerracottaBlock::new);
    }
}
