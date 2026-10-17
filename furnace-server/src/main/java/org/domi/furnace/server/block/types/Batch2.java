// 이식 묶음 2 의 블록 종류 등록(묶음마다 파일을 나눠 병렬 이식이 서로 부딪히지 않게).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.BlockBehavior;

final class Batch2 {
    private Batch2() {}

    static void register() {
        Reg.type("minecraft:end_rod", EndRodBlock::new);
        Reg.type("minecraft:lightning_rod", LightningRodBlock::new);
        Reg.type("minecraft:observer", ObserverBlock::new);
        Reg.type("minecraft:piston_base", PistonBaseBlock::new);
        Reg.type("minecraft:piston_head", PistonHeadBlock::new);
        Reg.type("minecraft:base_coral_fan", BaseCoralFanBlock::new);
        Reg.type("minecraft:base_coral_plant", BaseCoralPlantBlock::new);
        Reg.type("minecraft:base_coral_wall_fan", BaseCoralWallFanBlock::new);
        Reg.type("minecraft:coral_fan", CoralFanBlock::new);
        Reg.type("minecraft:coral_plant", CoralPlantBlock::new);
        Reg.type("minecraft:coral_wall_fan", CoralWallFanBlock::new);
        Reg.type("minecraft:bell", BellBlock::new);
        Reg.type("minecraft:dispenser", DispenserBlock::new);
        Reg.type("minecraft:dropper", DropperBlock::new);
        Reg.type("minecraft:chorus_plant", ChorusPlantBlock::new);
        Reg.type("minecraft:jack_o_lantern", CarvedPumpkinBlock::new);
        Reg.type("minecraft:carved_pumpkin", CarvedPumpkinBlock::new);
        Reg.type("minecraft:cake", CakeBlock::new);
        Reg.type("minecraft:tnt", TntBlock::new);
        Reg.type("minecraft:attached_stem", AttachedStemBlock::new);
        Reg.type("minecraft:structure", StructureBlock::new);
        Reg.type("minecraft:huge_mushroom", HugeMushroomBlock::new);
        Reg.type("minecraft:soul_sand", SoulSandBlock::new);
        Reg.type("minecraft:redstone_lamp", RedstoneLampBlock::new);
        Reg.type("minecraft:rooted_dirt", RootedDirtBlock::new);
        Reg.type("minecraft:block", BlockBehavior::new);
    }
}
