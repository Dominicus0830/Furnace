// 이식 묶음 10 의 블록 종류 등록(묶음마다 파일을 나눠 병렬 이식이 서로 부딪히지 않게).
package org.domi.furnace.server.block.types;

final class Batch10 {
    private Batch10() {}

    static void register() {
        Reg.type("minecraft:double_plant", DoublePlantBlock::new);
        Reg.type("minecraft:pitcher_crop", PitcherCropBlock::new);
        Reg.type("minecraft:small_dripleaf", SmallDripleafBlock::new);
        Reg.type("minecraft:tall_flower", TallFlowerBlock::new);
        Reg.type("minecraft:tall_seagrass", TallSeagrassBlock::new);
        Reg.type("minecraft:frosted_ice", FrostedIceBlock::new);
        Reg.type("minecraft:half_transparent", HalfTransparentBlock::new);
        Reg.type("minecraft:honey", HoneyBlock::new);
        Reg.type("minecraft:ice", IceBlock::new);
        Reg.type("minecraft:slime", SlimeBlock::new);
        Reg.type("minecraft:stained_glass", StainedGlassBlock::new);
        Reg.type("minecraft:tinted_glass", TintedGlassBlock::new);
        Reg.type("minecraft:transparent", TransparentBlock::new);
        Reg.type("minecraft:waterlogged_transparent", WaterloggedTransparentBlock::new);
        Reg.type("minecraft:weathering_copper_grate", WeatheringCopperGrateBlock::new);
        Reg.type("minecraft:lectern", LecternBlock::new);
        Reg.type("minecraft:door", DoorBlock::new);
        Reg.type("minecraft:weathering_copper_door", WeatheringCopperDoorBlock::new);
        Reg.type("minecraft:crafter", CrafterBlock::new);
        Reg.type("minecraft:liquid", LiquidBlock::new);
        Reg.type("minecraft:turtle_egg", TurtleEggBlock::new);
        Reg.type("minecraft:sweet_berry_bush", SweetBerryBushBlock::new);
        Reg.type("minecraft:copper_bulb_block", CopperBulbBlock::new);
        Reg.type("minecraft:weathering_copper_bulb", WeatheringCopperBulbBlock::new);
        Reg.type("minecraft:sugar_cane", SugarCaneBlock::new);
        Reg.type("minecraft:jigsaw", JigsawBlock::new);
        Reg.type("minecraft:barrier", BarrierBlock::new);
        Reg.type("minecraft:spore_blossom", SporeBlossomBlock::new);
        Reg.type("minecraft:azalea", AzaleaBlock::new);
        Reg.type("minecraft:structure_void", StructureVoidBlock::new);
    }
}
