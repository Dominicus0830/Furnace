// 이식 묶음 6 의 블록 종류 등록(묶음마다 파일을 나눠 병렬 이식이 서로 부딪히지 않게).
package org.domi.furnace.server.block.types;

final class Batch6 {
    private Batch6() {}

    static void register() {
        Reg.type("minecraft:button", ButtonBlock::new);
        Reg.type("minecraft:grindstone", GrindstoneBlock::new);
        Reg.type("minecraft:lever", LeverBlock::new);
        Reg.type("minecraft:redstone_torch", RedstoneTorchBlock::new);
        Reg.type("minecraft:redstone_wall_torch", RedstoneWallTorchBlock::new);
        Reg.type("minecraft:torch", TorchBlock::new);
        Reg.type("minecraft:wall_torch", WallTorchBlock::new);
        Reg.type("minecraft:calibrated_sculk_sensor", CalibratedSculkSensorBlock::new);
        Reg.type("minecraft:sculk_sensor", SculkSensorBlock::new);
        Reg.type("minecraft:stair", StairBlock::new);
        Reg.type("minecraft:weathering_copper_stair", WeatheringCopperStairBlock::new);
        Reg.type("minecraft:nether_portal", NetherPortalBlock::new);
        Reg.type("minecraft:fence_gate", FenceGateBlock::new);
        Reg.type("minecraft:chain", ChainBlock::new);
        Reg.type("minecraft:hay", HayBlock::new);
        Reg.type("minecraft:rotated_pillar", RotatedPillarBlock::new);
        Reg.type("minecraft:farm", FarmBlock::new);
        Reg.type("minecraft:cactus", CactusBlock::new);
        Reg.type("minecraft:brewing_stand", BrewingStandBlock::new);
        Reg.type("minecraft:bamboo_sapling", BambooSaplingBlock::new);
        Reg.type("minecraft:sponge", SpongeBlock::new);
        Reg.type("minecraft:mangrove_roots", MangroveRootsBlock::new);
        Reg.type("minecraft:pumpkin", PumpkinBlock::new);
        Reg.type("minecraft:cartography_table", CartographyTableBlock::new);
    }
}
