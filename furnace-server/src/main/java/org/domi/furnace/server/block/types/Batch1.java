// 이식 묶음 1 의 블록 종류 등록(묶음마다 파일을 나눠 병렬 이식이 서로 부딪히지 않게).
package org.domi.furnace.server.block.types;

final class Batch1 {
    private Batch1() {}

    static void register() {
        Reg.type("minecraft:rail", RailBlock::new);
        Reg.type("minecraft:powered_rail", PoweredRailBlock::new);
        Reg.type("minecraft:detector_rail", DetectorRailBlock::new);
        Reg.type("minecraft:vine", VineBlock::new);
        Reg.type("minecraft:wall", WallBlock::new);
        Reg.type("minecraft:bamboo_stalk", BambooStalkBlock::new);
        Reg.type("minecraft:tripwire", TripWireBlock::new);
        Reg.type("minecraft:sea_pickle", SeaPickleBlock::new);
        Reg.type("minecraft:sculk_shrieker", SculkShriekerBlock::new);
        Reg.type("minecraft:brushable", BrushableBlock::new);
        Reg.type("minecraft:target", TargetBlock::new);
        Reg.type("minecraft:end_portal_frame", EndPortalFrameBlock::new);
        Reg.type("minecraft:vault", VaultBlock::new);
        Reg.type("minecraft:nether_wart", NetherWartBlock::new);
        Reg.type("minecraft:trial_spawner", TrialSpawnerBlock::new);
        Reg.type("minecraft:air", AirBlock::new);
    }
}
