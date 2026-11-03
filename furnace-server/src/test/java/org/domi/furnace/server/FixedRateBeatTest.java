package org.domi.furnace.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** 전역 틱 고정 박자의 과부하 처리 — 바닐라 MinecraftServer.runServer 와 같은 조건(1초 + 20틱 넘게 밀림, 경고 간격 10초 + 100틱). */
class FixedRateBeatTest {

    private static final long MS = 1_000_000L;

    private static FurnaceMain.FixedRateBeat beat(long next) {
        var b = new FurnaceMain.FixedRateBeat(null, () -> {}, 50 * MS);
        b.next = next;
        b.lastWarning = next;   // 기동 때처럼
        return b;
    }

    @Test
    void smallDelaysAreCaughtUpNotSkipped() {
        long d = 100_000 * MS;
        var b = beat(d);
        assertEquals(0, b.checkOverload(d + 1_900 * MS));   // 2초 미만 — 따라잡는다(마감 그대로)
        assertEquals(d, b.next);
    }

    @Test
    void largeDelaySkipsOnlyAfterWarningInterval() {
        long d = 100_000 * MS;
        var b = beat(d);
        assertEquals(0, b.checkOverload(d + 3_000 * MS));   // 기동 직후: 경고 간격(15초)이 안 지나 건너뛰지 않는다
        b.next = d + 15_000 * MS;   // 15초 뒤 마감
        assertEquals(60, b.checkOverload(b.next + 3_000 * MS));   // 3초 밀림 = 60틱 건너뜀
        assertEquals(d + 18_000 * MS, b.next);
        assertEquals(b.next, b.lastWarning);
        assertEquals(0, b.checkOverload(b.next + 3_000 * MS));   // 방금 경고했으니 다음 15초는 따라잡기
    }
}
