// 이식 묶음 4 의 블록 종류 등록(묶음마다 파일을 나눠 병렬 이식이 서로 부딪히지 않게).
package org.domi.furnace.server.block.types;

final class Batch4 {
    private Batch4() {}

    static void register() {
        Reg.type("minecraft:ceiling_hanging_sign", CeilingHangingSignBlock::new);
        Reg.type("minecraft:standing_sign", StandingSignBlock::new);
        Reg.type("minecraft:wall_hanging_sign", WallHangingSignBlock::new);
        Reg.type("minecraft:wall_sign", WallSignBlock::new);
        Reg.type("minecraft:beetroot", BeetrootBlock::new);
        Reg.type("minecraft:carrot", CarrotBlock::new);
        Reg.type("minecraft:crop", CropBlock::new);
        Reg.type("minecraft:potato", PotatoBlock::new);
        Reg.type("minecraft:torchflower_crop", TorchflowerCropBlock::new);
        Reg.type("minecraft:anvil", AnvilBlock::new);
        Reg.type("minecraft:colored_falling", ColoredFallingBlock::new);
        Reg.type("minecraft:concrete_powder", ConcretePowderBlock::new);
        Reg.type("minecraft:dragon_egg", DragonEggBlock::new);
        Reg.type("minecraft:big_dripleaf", BigDripleafBlock::new);
        Reg.type("minecraft:command", CommandBlock::new);
        Reg.type("minecraft:hopper", HopperBlock::new);
        Reg.type("minecraft:cocoa", CocoaBlock::new);
        Reg.type("minecraft:big_dripleaf_stem", BigDripleafStemBlock::new);
        Reg.type("minecraft:jukebox", JukeboxBlock::new);
        Reg.type("minecraft:end_gateway", EndGatewayBlock::new);
        Reg.type("minecraft:seagrass", SeagrassBlock::new);
        Reg.type("minecraft:nylium", NyliumBlock::new);
        Reg.type("minecraft:fungus", FungusBlock::new);
        Reg.type("minecraft:tall_grass", TallGrassBlock::new);
        Reg.type("minecraft:weathering_copper_full", WeatheringCopperFullBlock::new);
    }
}
