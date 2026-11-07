package org.domi.furnace.server.sched;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.domi.furnace.server.world.Regionizer;
import org.junit.jupiter.api.Test;

/** getTPS 의 1분·5분·15분 값(1초 표본의 창 평균)과 활성 region 요약(플레이어 가중 평균). */
class ActiveTpsHistoryTest {

    private static final long S = 1_000_000_000L;

    @Test
    void averagesSamplesInsideTheWindowOnly() {
        var h = new ActiveTpsHistory();
        assertTrue(Double.isNaN(h.average(0, 60 * S)), "표본이 없으면 NaN");
        for (int i = 0; i < 120; i++) h.record(i * S, i < 60 ? 10.0 : 20.0);   // 앞 60초 10, 뒤 60초 20
        long now = 119 * S;
        assertEquals(20.0, h.average(now, 30 * S), 1e-9);
        assertEquals(15.0, h.average(now, 119 * S), 0.3);   // 창이 두 구간에 걸치면 섞인다
        assertEquals(20.0, h.average(now, 59 * S), 1e-9);
    }

    @Test
    void dueEverySecondAndNanSamplesAreDropped() {
        var h = new ActiveTpsHistory();
        assertTrue(h.due(0));
        h.record(0, Double.NaN);
        assertFalse(h.due(S / 2));
        assertTrue(h.due(S));
        assertTrue(Double.isNaN(h.average(S, 60 * S)), "NaN 표본은 적지 않는다");
    }

    @Test
    void keepsFifteenMinutesAndOverwritesTheOldest() {
        var h = new ActiveTpsHistory();
        for (int i = 0; i < 2000; i++) h.record(i * S, 20.0);
        assertEquals(20.0, h.average(1999 * S, 900 * S), 1e-9);
    }

    @Test
    void activeSummaryWeightsByPlayersAndMergesDimensions() {
        var a = new Regionizer.ActiveSummary(1, 4, 4 * 10.0, 10.0, 10.0);    // 플레이어 4명, 10 TPS
        var b = new Regionizer.ActiveSummary(1, 1, 1 * 20.0, 20.0, 5.0);     // 플레이어 1명 20 TPS, 플레이어 없는 느린 region 5
        var m = a.plus(b);
        assertEquals(2, m.activeRegions());
        assertEquals(5, m.activePlayers());
        assertEquals((40.0 + 20.0) / 5, m.weightedAverage(), 1e-9);   // 12 — 플레이어 수 가중
        assertEquals(10.0, m.slowestActive(), 1e-9);
        assertEquals(5.0, m.slowestAny(), 1e-9);
        assertTrue(Double.isNaN(Regionizer.ActiveSummary.NONE.weightedAverage()));
        assertEquals(10.0, Regionizer.ActiveSummary.NONE.plus(a).slowestAny(), 1e-9, "NaN 은 건너뛰고 합친다");
    }
}
