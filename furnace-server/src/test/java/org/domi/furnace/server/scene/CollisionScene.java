// 밀기 상한 장면(T-122): 돌담으로 두른 1×1 우리 셋에 소·돼지·양을 20마리씩 몰아넣고, 열린 땅에 좀비 무리를 둔 채 밀기 상한을 **Paper 기본값(8)으로 켜고** 돌린다.
// 금 파일 scene-collisions 는 Paper 방식 동작을 못 박는다. 상한을 끈(바닐라) 같은 장면은 첫 점검부터 엔티티 줄이 달라야 한다 — SceneLibraryTest 가 확인한다.
// maxEntityCramming(24)은 넘지 않게 20마리 — cramming 피해는 상한과 무관하게 바닐라대로라 섞지 않는다.
package org.domi.furnace.server.scene;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.config.EntityHandleSettings;
import org.domi.furnace.server.entity.Entities;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.identity.IdentityHash;
import org.domi.furnace.server.identity.SceneHash;

public class CollisionScene extends TickScene {
    private static final String[] PENS = {"minecraft:cow", "minecraft:pig", "minecraft:sheep"};

    private SceneLevel level;

    @Override public String name() { return "collisions"; }
    @Override public boolean freshJvm() { return true; }
    @Override public String covers() { return "T-122 밀기 상한(collisions.max-entity-collisions, Paper 기본 8 켬) — 1×1 우리 몹 20마리"; }

    @Override
    protected EntityHandleSettings entityHandles() { return EntityHandleSettings.DEFAULT; }

    @Override
    protected void setup(Size size, int round) {
        SceneKit.install();
        level = new SceneLevel("collisions", 3);
        level.dayTime = 18000;
        level.gameTime = 18000;
        SceneKit.addPlayer(level, "A", 0.5, SceneLevel.FLOOR_Y, 40.5);
        int stone = B.defaultState("minecraft:stone");
        java.util.Random r = new java.util.Random(11);
        int per = size.pick(20, 20), packs = size.pick(1, 6);
        for (int k = 0; k < packs; k++) {
            for (int p = 0; p < PENS.length; p++) {
                int cx = -20 + p * 10, cz = -20 + k * 8;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) continue;
                        for (int dy = 0; dy < 3; dy++) level.setBlock(new Pos(cx + dx, SceneLevel.FLOOR_Y + dy, cz + dz), stone, 2);
                    }
                }
                for (int i = 0; i < per; i++) {
                    Entity e = Entities.create(level, PENS[p]);
                    e.moveTo(cx + 0.45 + r.nextInt(11) * 0.01, SceneLevel.FLOOR_Y, cz + 0.45 + r.nextInt(11) * 0.01, r.nextFloat() * 360, 0);   // 몸통(0.9)이 담에 걸리지 않게
                    level.addFreshEntity(e);
                }
            }
            for (int i = 0; i < per; i++) {   // 열린 땅의 좀비 무리(우리 없이 한데 모여 서로 민다)
                Entity e = Entities.create(level, "minecraft:zombie");
                e.moveTo(15 + r.nextInt(3) * 0.2, SceneLevel.FLOOR_Y, -20 + k * 8 + r.nextInt(3) * 0.2, r.nextFloat() * 360, 0);
                level.addFreshEntity(e);
            }
        }
    }

    @Override protected int ticks(Size size) { return size.pick(200, 600); }

    @Override protected int checkEvery(Size size) { return 50; }

    @Override protected void tick(int t) { level.tick(); }

    @Override
    protected void checkpoint(SceneHash.Recorder rec, String label) {
        rec.line("collisions " + label + " entities", SceneKit.entities(level));
        rec.line("collisions " + label + " random", new IdentityHash().add(level.random().nextLong()));
        rec.text("collisions " + label + " count " + level.entities().tickSnapshot().size());
    }

    /** 같은 장면을 밀기 상한 없이(바닐라) — 금 파일 없이 SceneLibraryTest 가 켠 쪽과 비교만 한다. */
    public static final class Off extends CollisionScene {
        @Override public String name() { return "collisions-off"; }
        @Override protected EntityHandleSettings entityHandles() { return EntityHandleSettings.VANILLA; }
    }
}
