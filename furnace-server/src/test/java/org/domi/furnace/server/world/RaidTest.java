// 습격: 파도 수(난이도·징조 단계), 파도별 기본 수, 추가 수 난수 순서, 마을 밖이면 중지, 대기 막대.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.world.raid.Raid;
import org.domi.furnace.server.world.raid.Raid.RaiderType;
import org.junit.jupiter.api.Test;

class RaidTest {

    @Test
    void waveCounts() {
        assertEquals(3, Raid.getNumGroups(1));
        assertEquals(5, Raid.getNumGroups(2));
        assertEquals(7, Raid.getNumGroups(3));
        assertEquals(0, Raid.getNumGroups(0));
        assertEquals(5, Raid.totalWaves(2, 1), "징조 I 은 보너스 없음");
        assertEquals(6, Raid.totalWaves(2, 2));
        assertEquals(8, Raid.totalWaves(3, 5));
        assertEquals(4, Raid.totalWaves(1, 3));
    }

    @Test
    void defaultSpawnsPerWave() {
        Raid r = new Raid(1, new PoiManagerTest.Lvl(), Pos.ZERO);   // 기본 난이도
        int n = r.numGroups();
        assertTrue(n > 0);
        assertEquals(4, r.getDefaultNumSpawns(RaiderType.PILLAGER, 1, false));
        assertEquals(1, r.getDefaultNumSpawns(RaiderType.RAVAGER, 3, false));
        assertEquals(3, r.getDefaultNumSpawns(RaiderType.WITCH, 4, false));
        // 보너스 파도는 numGroups 칸
        assertEquals(new int[] {0, 0, 2, 0, 1, 4, 2, 5}[n], r.getDefaultNumSpawns(RaiderType.VINDICATOR, 99, true));
    }

    @Test
    void bonusSpawnsConsumeRandomLikeVanilla() {
        // 쉬움: 습격자(약탈자·변명자) nextInt(2) 다음 그 값이 0 보다 크면 nextInt(j+1). 마녀·파괴수는 난수를 쓰지 않는다.
        RandomSource a = RandomSource.create(42), b = RandomSource.create(42);
        int got = Raid.getPotentialBonusSpawns(RaiderType.PILLAGER, a, 1, 1, false);
        int j = b.nextInt(2);
        int want = j > 0 ? b.nextInt(j + 1) : 0;
        assertEquals(want, got);
        assertEquals(b.nextInt(), a.nextInt(), "같은 수만큼 소비");

        RandomSource c = RandomSource.create(7), d = RandomSource.create(7);
        assertEquals(0, Raid.getPotentialBonusSpawns(RaiderType.WITCH, c, 4, 3, false), "4파도 마녀 없음");
        assertEquals(0, Raid.getPotentialBonusSpawns(RaiderType.RAVAGER, c, 3, 3, false), "보너스 아닐 때 파괴수 0");
        assertEquals(0, Raid.getPotentialBonusSpawns(RaiderType.EVOKER, c, 5, 3, true));
        assertEquals(c.nextInt(), d.nextInt(), "위 셋은 난수를 쓰지 않는다");

        // 어려움 약탈자: j = 2 → nextInt(3)
        RandomSource e = RandomSource.create(9), f = RandomSource.create(9);
        assertEquals(f.nextInt(3), Raid.getPotentialBonusSpawns(RaiderType.PILLAGER, e, 2, 3, false));
        // 보통 마녀 3파도: j = 1 → nextInt(2)
        assertEquals(f.nextInt(2), Raid.getPotentialBonusSpawns(RaiderType.WITCH, e, 3, 2, false));
    }

    @Test
    void stopsOutsideVillageBeforeFirstWave() {
        var l = new PoiManagerTest.Lvl();
        Raid r = new Raid(1, l, new Pos(0, -50, 0));
        if (r.numGroups() == 0) return;   // 기본 난이도가 평화로움이면 시험 대상 아님
        r.tick();
        assertTrue(r.isStopped(), "마을 POI 가 없으면 첫 파도 전에 멈춘다");
    }

    @Test
    void preRaidCountdownFillsBar() {
        var l = new PoiManagerTest.Lvl() {
            @Override protected LevelServices services() {
                return new LevelServices.Delegating(LevelServices.DEFAULT) {
                    @Override public int difficulty() { return 2; }
                };
            }
        };
        l.setBlock(new Pos(0, -50, 0), B.defaultState("bell"), 3);
        Raid r = new Raid(1, l, new Pos(0, -50, 0));
        assertEquals(5, r.numGroups());
        for (int i = 0; i < 150; i++) r.tick();
        assertEquals(Raid.Status.ONGOING, r.status());
        assertEquals(150, r.raidCooldownTicks());
        assertEquals(0.5F, r.barProgress(), 1e-6);
    }
}
