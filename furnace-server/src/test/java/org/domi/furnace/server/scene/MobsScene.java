// 몹 장면(O05-14 `EntityIdentityBench`): 소·양·돼지·닭·좀비·해골·크리퍼·거미가 섞인 몹 N마리가 평평한 땅에서 움직이고, 가운데에 플레이어 한 명이 서 있다.
// 매 점검마다 모든 엔티티(위치·속도·회전·체력·두뇌 기억을 포함한 저장 NBT 전체)와 레벨 난수의 해시를 적는다.
// O05 의 OPT 대부분(플레이어 배열·근접 색인·충돌 수집 재작성·속성·추적)의 "결과 동일" 증명 기준이다. 엔티티가 있으므로 새 JVM 모드.
// 한계: region 하나·시험 스레드가 직접 틱(자연 스폰·추적기·네트워크는 안 돈다 — 그쪽은 RegionTickBench).
package org.domi.furnace.server.scene;

import org.domi.furnace.server.entity.Entities;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.identity.IdentityHash;
import org.domi.furnace.server.identity.SceneHash;

public final class MobsScene extends TickScene {
    private static final String[] TYPES = {"minecraft:cow", "minecraft:sheep", "minecraft:pig", "minecraft:chicken", "minecraft:zombie",
            "minecraft:skeleton", "minecraft:creeper", "minecraft:spider"};

    private SceneLevel level;

    @Override public String name() { return "mobs"; }
    @Override public boolean freshJvm() { return true; }
    @Override public String covers() { return "O05-14 몹 결과 동일(O05 OPT 전반), 충돌 수집·플레이어 근접 색인·속성"; }

    @Override
    protected void setup(Size size, int round) {
        SceneKit.install();
        level = new SceneLevel("mobs", 3);
        level.dayTime = 18000;   // 밤: 적대 몹이 해에 타지 않고 목표를 잡는다
        level.gameTime = 18000;
        SceneKit.addPlayer(level, "A", 0.5, SceneLevel.FLOOR_Y, 0.5);
        java.util.Random r = new java.util.Random(1);
        int n = size.pick(120, 1000);
        for (int i = 0; i < n; i++) {
            Entity e = Entities.create(level, TYPES[i % TYPES.length]);
            e.moveTo(r.nextInt(60) - 30 + 0.5, SceneLevel.FLOOR_Y + 2, r.nextInt(60) - 30 + 0.5, r.nextFloat() * 360, 0);
            level.addFreshEntity(e);
        }
    }

    @Override protected int ticks(Size size) { return size.pick(100, 600); }

    @Override protected int checkEvery(Size size) { return 50; }

    @Override protected void tick(int t) { level.tick(); }

    @Override
    protected void checkpoint(SceneHash.Recorder rec, String label) {
        rec.line("mobs " + label + " entities", SceneKit.entities(level));
        rec.line("mobs " + label + " random", new IdentityHash().add(level.random().nextLong()));
        rec.text("mobs " + label + " count " + level.entities().tickSnapshot().size());
    }
}
