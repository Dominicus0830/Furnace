// 이식 묶음 7 의 블록 종류 등록(묶음마다 파일을 나눠 병렬 이식이 서로 부딪히지 않게).
package org.domi.furnace.server.block.types;

final class Batch7 {
    private Batch7() {}

    static void register() {
        Reg.type("minecraft:pointed_dripstone", PointedDripstoneBlock::new);
        Reg.type("minecraft:composter", ComposterBlock::new);
        Reg.type("minecraft:cauldron", CauldronBlock::new);
        Reg.type("minecraft:lava_cauldron", LavaCauldronBlock::new);
        Reg.type("minecraft:layered_cauldron", LayeredCauldronBlock::new);
        Reg.type("minecraft:blast_furnace", BlastFurnaceBlock::new);
        Reg.type("minecraft:furnace", FurnaceBlock::new);
        Reg.type("minecraft:smoker", SmokerBlock::new);
        Reg.type("minecraft:chiseled_book_shelf", ChiseledBookShelfBlock::new);
        Reg.type("minecraft:grass", GrassBlock::new);
        Reg.type("minecraft:mycelium", MyceliumBlock::new);
        Reg.type("minecraft:snowy_dirt", SnowyDirtBlock::new);
        Reg.type("minecraft:snow_layer", SnowLayerBlock::new);
        Reg.type("minecraft:drop_experience", DropExperienceBlock::new);
        Reg.type("minecraft:sculk", SculkBlock::new);
        Reg.type("minecraft:daylight_detector", DaylightDetectorBlock::new);
        Reg.type("minecraft:flower_pot", FlowerPotBlock::new);
        Reg.type("minecraft:light", LightBlock::new);
        Reg.type("minecraft:wet_sponge", WetSpongeBlock::new);
        Reg.type("minecraft:dirt_path", DirtPathBlock::new);
        Reg.type("minecraft:magma", MagmaBlock::new);
        Reg.type("minecraft:roots", RootsBlock::new);
        Reg.type("minecraft:powered", PoweredBlock::new);
    }
}
