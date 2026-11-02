// player-idle-timeout 경계와 마지막 행동 시각 갱신.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PlayerIdleTest {

    @Test
    void disabledWhenZeroOrNegative() {
        assertFalse(PlayerIdle.timedOut(10_000_000L, 0L, 0));
        assertFalse(PlayerIdle.timedOut(10_000_000L, 0L, -5));
    }

    @Test
    void strictlyGreaterThanTimeout() {
        assertFalse(PlayerIdle.timedOut(60_000L, 0L, 1));   // 정확히 1분 — 아직
        assertTrue(PlayerIdle.timedOut(60_001L, 0L, 1));
        assertFalse(PlayerIdle.timedOut(5L * 60_000L, 0L, 5));
        assertTrue(PlayerIdle.timedOut(5L * 60_000L + 1, 0L, 5));
    }

    @Test
    void largeMinutesDoNotOverflow() {
        assertFalse(PlayerIdle.timedOut(1_000_000_000_000L, 0L, Integer.MAX_VALUE));   // 오버플로 없이 long 곱셈
    }
}
