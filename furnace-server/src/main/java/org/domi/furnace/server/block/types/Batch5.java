// 이식 묶음 5 의 블록 종류 등록(묶음마다 파일을 나눠 병렬 이식이 서로 부딪히지 않게).
package org.domi.furnace.server.block.types;

final class Batch5 {
    private Batch5() {}

    static void register() {
        Reg.type("minecraft:cave_vines", CaveVinesBlock::new);
        Reg.type("minecraft:cave_vines_plant", CaveVinesPlantBlock::new);
        Reg.type("minecraft:kelp", KelpBlock::new);
        Reg.type("minecraft:kelp_plant", KelpPlantBlock::new);
        Reg.type("minecraft:twisting_vines", TwistingVinesBlock::new);
        Reg.type("minecraft:twisting_vines_plant", TwistingVinesPlantBlock::new);
        Reg.type("minecraft:weeping_vines", WeepingVinesBlock::new);
        Reg.type("minecraft:weeping_vines_plant", WeepingVinesPlantBlock::new);
        Reg.type("minecraft:piglinwallskull", PiglinWallSkullBlock::new);
        Reg.type("minecraft:player_head", PlayerHeadBlock::new);
        Reg.type("minecraft:player_wall_head", PlayerWallHeadBlock::new);
        Reg.type("minecraft:skull", SkullBlock::new);
        Reg.type("minecraft:wall_skull", WallSkullBlock::new);
        Reg.type("minecraft:wither_skull", WitherSkullBlock::new);
        Reg.type("minecraft:wither_wall_skull", WitherWallSkullBlock::new);
        Reg.type("minecraft:bed", BedBlock::new);
        Reg.type("minecraft:trip_wire_hook", TripWireHookBlock::new);
        Reg.type("minecraft:trapdoor", TrapDoorBlock::new);
        Reg.type("minecraft:weathering_copper_trap_door", WeatheringCopperTrapDoorBlock::new);
        Reg.type("minecraft:respawn_anchor", RespawnAnchorBlock::new);
        Reg.type("minecraft:powder_snow", PowderSnowBlock::new);
        Reg.type("minecraft:stem", StemBlock::new);
        Reg.type("minecraft:mushroom", MushroomBlock::new);
        Reg.type("minecraft:pink_petals", PinkPetalsBlock::new);
        Reg.type("minecraft:stonecutter", StonecutterBlock::new);
        Reg.type("minecraft:netherrack", NetherrackBlock::new);
        Reg.type("minecraft:heavy_core", HeavyCoreBlock::new);
        Reg.type("minecraft:spawner", SpawnerBlock::new);
        Reg.type("minecraft:nether_sprouts", NetherSproutsBlock::new);
    }
}
