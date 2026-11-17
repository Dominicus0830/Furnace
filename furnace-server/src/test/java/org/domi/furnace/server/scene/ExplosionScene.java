// 폭발 장면(O05-13·O07-09: "지금 벤치 없음"): 흙·돌·모래·흑요석·유리·물이 섞인 땅에 TNT 블록 묶음을 깔고, 점화된 TNT 몇 개를 도화선 길이를
// 달리해 떨어뜨린다. 연쇄 폭발(휘말린 TNT 의 짧은 도화선)·블록 부서짐 순서·드롭·소·좀비의 피해·밀림·가림 비율(getSeenPercent)을 모두 본다.
// 폭발 가림 비율 기억(O07-09/T-063)·폭발 칸 기억이 결과를 안 바꿨는지가 이 장면의 질문이다. 난수 소비도 해시에 넣는다.
// 엔티티가 있으므로 새 JVM 모드.
package org.domi.furnace.server.scene;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Entities;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.PrimedTnt;
import org.domi.furnace.server.identity.IdentityHash;
import org.domi.furnace.server.identity.SceneHash;

public final class ExplosionScene extends TickScene {
    private static final int FLOOR = SceneLevel.FLOOR_Y;

    private SceneLevel level;
    private int tnt;

    @Override public String name() { return "explosion"; }
    @Override public boolean freshJvm() { return true; }
    @Override public String covers() { return "O05-13·O07-09 폭발(가림 비율 기억·폭발 칸 기억), T-063 비교 기준"; }

    @Override
    protected void setup(Size size, int round) {
        SceneKit.install();
        level = new SceneLevel("explosion", 3);
        Blocks b = Blocks.get();
        String[] ground = {"minecraft:stone", "minecraft:dirt", "minecraft:sand", "minecraft:obsidian", "minecraft:glass", "minecraft:oak_planks",
                "minecraft:gravel", "minecraft:water", "minecraft:cobblestone", "minecraft:netherrack"};
        java.util.Random r = new java.util.Random(7);
        int half = size.pick(14, 30);
        for (int x = -half; x <= half; x++) for (int z = -half; z <= half; z++) for (int y = FLOOR; y < FLOOR + 5; y++)
            level.setBlock(new Pos(x, y, z), b.defaultState(ground[r.nextInt(ground.length)]), 2);
        int side = size.pick(10, 20);   // TNT 블록 side×side 한 층(PROOF 100, FULL 400)
        int tntState = b.defaultState("minecraft:tnt");
        for (int x = 0; x < side; x++) for (int z = 0; z < side; z++) level.setBlock(new Pos(x - side / 2, FLOOR + 5, z - side / 2), tntState, 2);
        // 점화된 TNT: 도화선 20~59틱, 묶음 위·옆
        tnt = size.pick(8, 40);
        for (int i = 0; i < tnt; i++) {
            PrimedTnt t = new PrimedTnt(level, level.nextEntityId(), r.nextInt(side) - side / 2 + 0.5, FLOOR + 7 + r.nextInt(3), r.nextInt(side) - side / 2 + 0.5, null);
            t.setFuse(20 + r.nextInt(40));
            level.addFreshEntity(t);
        }
        // 피해·밀림을 받을 몹: 소·좀비·양이 폭발 둘레에
        String[] mobs = {"minecraft:cow", "minecraft:zombie", "minecraft:sheep"};
        int n = size.pick(20, 90);
        for (int i = 0; i < n; i++) {
            Entity e = Entities.create(level, mobs[i % mobs.length]);
            e.moveTo(r.nextInt(2 * half - 4) - half + 2.5, FLOOR + 5 + 0.0, r.nextInt(2 * half - 4) - half + 2.5, r.nextInt(360), 0);
            level.addFreshEntity(e);
        }
    }

    @Override protected int ticks(Size size) { return size.pick(120, 300); }

    @Override protected int checkEvery(Size size) { return 30; }

    @Override protected void tick(int t) { level.tick(); }

    @Override
    protected void checkpoint(SceneHash.Recorder rec, String label) {
        IdentityHash h = new IdentityHash();
        SceneKit.blocks(level, h, -34, FLOOR - 1, -34, 34, FLOOR + 14, 34);
        rec.line("explosion " + label + " blocks", h);
        rec.line("explosion " + label + " entities", SceneKit.entities(level));
        rec.line("explosion " + label + " random", new IdentityHash().add(level.random().nextLong()));
        rec.text("explosion " + label + " tnt-blocks=" + SceneKit.count(level, "minecraft:tnt", -34, FLOOR, -34, 34, FLOOR + 14, 34)
                + " entities=" + level.entities().tickSnapshot().size());
    }
}
