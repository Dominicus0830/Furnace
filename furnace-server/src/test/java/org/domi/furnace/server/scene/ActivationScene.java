// 엔티티 활성 범위 장면(D11): 플레이어 한 명을 가운데 두고 동물·적대 몹·주민·아이템·경험치 구슬을 0~88칸 거리에 흩어 놓고
// 활성 범위를 **켠 채로**(기본값) 돌린다. 금 파일 scene-activation 은 Paper 방식 동작을 못 박는다(바뀌면 활성 범위 동작이 바뀐 것).
// 첫 200틱은 새 엔티티 면역(tickCount < 200)으로 모두 활성이라 끈 것(Off)과 같고, 그 뒤로 달라진다 — SceneLibraryTest 가 확인한다.
package org.domi.furnace.server.scene;

import org.domi.furnace.server.config.EntityActivationSettings;
import org.domi.furnace.server.entity.Entities;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.EntityActivation;
import org.domi.furnace.server.identity.IdentityHash;
import org.domi.furnace.server.identity.SceneHash;

public class ActivationScene extends TickScene {
    private static final String[] TYPES = {"minecraft:cow", "minecraft:zombie", "minecraft:sheep", "minecraft:skeleton", "minecraft:chicken",
            "minecraft:creeper", "minecraft:pig", "minecraft:spider", "minecraft:villager", "minecraft:experience_orb"};

    private SceneLevel level;

    @Override public String name() { return "activation"; }
    @Override public boolean freshJvm() { return true; }
    @Override public String covers() { return "D11 엔티티 활성 범위(켜짐) — 범위·면역·깨우기·inactiveTick"; }

    @Override
    protected EntityActivationSettings entityActivation() { return EntityActivationSettings.DEFAULT; }

    @Override
    protected void setup(Size size, int round) {
        SceneKit.install();
        level = new SceneLevel("activation", 6);
        level.dayTime = 18000;   // 밤: 적대 몹이 해에 타지 않는다
        level.gameTime = 18000;
        SceneKit.addPlayer(level, "A", 0.5, SceneLevel.FLOOR_Y, 0.5);
        java.util.Random r = new java.util.Random(7);
        int n = size.pick(160, 800);
        for (int i = 0; i < n; i++) {
            double dist = 4 + r.nextInt(85), ang = r.nextDouble() * Math.PI * 2;
            double x = Math.floor(Math.cos(ang) * dist) + 0.5, z = Math.floor(Math.sin(ang) * dist) + 0.5;
            Entity e = Entities.create(level, TYPES[i % TYPES.length]);
            e.moveTo(x, SceneLevel.FLOOR_Y + 1, z, r.nextFloat() * 360, 0);
            level.addFreshEntity(e);
        }
    }

    @Override protected int ticks(Size size) { return size.pick(400, 1200); }

    @Override protected int checkEvery(Size size) { return 50; }

    @Override protected void tick(int t) { level.tick(); }

    @Override
    protected void checkpoint(SceneHash.Recorder rec, String label) {
        rec.line("activation " + label + " entities", SceneKit.entities(level));
        rec.line("activation " + label + " random", new IdentityHash().add(level.random().nextLong()));
        int active = 0, all = 0;
        for (Entity e : level.entities().tickSnapshot()) {
            all++;
            if (EntityActivation.isMarkedActive(e)) active++;
        }
        rec.text("activation " + label + " count " + all + " marked-active " + active);
    }

    /** 같은 장면을 활성 범위를 끄고(바닐라) — 금 파일 없이 SceneLibraryTest 가 켠 쪽과 비교만 한다. */
    public static final class Off extends ActivationScene {
        @Override public String name() { return "activation-off"; }
        @Override protected EntityActivationSettings entityActivation() { return EntityActivationSettings.OFF; }
    }
}
