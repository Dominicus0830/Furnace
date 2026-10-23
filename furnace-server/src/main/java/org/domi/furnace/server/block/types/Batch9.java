// 이식 묶음 9 의 블록 종류 등록(묶음마다 파일을 나눠 병렬 이식이 서로 부딪히지 않게).
package org.domi.furnace.server.block.types;

final class Batch9 {
    private Batch9() {}

    static void register() {
        Reg.type("minecraft:chest", ChestBlock::new);
        Reg.type("minecraft:trapped_chest", TrappedChestBlock::new);
        Reg.type("minecraft:ender_chest", EnderChestBlock::new);
        Reg.type("minecraft:redstone_wire", RedStoneWireBlock::new);
        Reg.type("minecraft:campfire", CampfireBlock::new);
        Reg.type("minecraft:beehive", BeehiveBlock::new);
        Reg.type("minecraft:decorated_pot", DecoratedPotBlock::new);
        Reg.type("minecraft:banner", BannerBlock::new);
        Reg.type("minecraft:wall_banner", WallBannerBlock::new);
        Reg.type("minecraft:note", NoteBlock::new);
        Reg.type("minecraft:moving_piston", MovingPistonBlock::new);
        Reg.type("minecraft:infested", InfestedBlock::new);
        Reg.type("minecraft:infested_rotated_pillar", InfestedRotatedPillarBlock::new);
        Reg.type("minecraft:flower", FlowerBlock::new);
        Reg.type("minecraft:wither_rose", WitherRoseBlock::new);
        Reg.type("minecraft:sniffer_egg", SnifferEggBlock::new);
        Reg.type("minecraft:hanging_roots", HangingRootsBlock::new);
        Reg.type("minecraft:sculk_catalyst", SculkCatalystBlock::new);
        Reg.type("minecraft:waterlily", WaterlilyBlock::new);
        Reg.type("minecraft:moss", MossBlock::new);
        Reg.type("minecraft:web", WebBlock::new);
    }
}
