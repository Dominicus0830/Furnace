// 밤 건너뛰기 계산 시험: 필요 인원(playersSleepingPercentage), 다시 세기의 알림 조건, 100틱 깊은 잠, 다음 날 아침 시각과 날씨 초기화.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Player;
import org.junit.jupiter.api.Test;

class SleepStatusTest {

    static Player player(int id, GameMode mode) {
        var p = new Player(id, UUID.randomUUID(), "P" + id, new EmbeddedChannel());
        p.vitals().loadGameTypes(null, mode, false);
        return p;
    }

    @Test
    void sleepersNeededRoundsUpAndIsAtLeastOne() {
        assertEquals(1, SleepStatus.neededFor(0, 100));
        assertEquals(1, SleepStatus.neededFor(4, 0));
        assertEquals(4, SleepStatus.neededFor(4, 100));
        assertEquals(2, SleepStatus.neededFor(4, 50));
        assertEquals(2, SleepStatus.neededFor(3, 50), "1.5 → 2");
        assertEquals(1, SleepStatus.neededFor(3, 33), "0.99 → 1");
        assertEquals(8, SleepStatus.neededFor(4, 200), "100 넘으면 아무도 다 못 채운다(전원보다 많이 필요)");
    }

    @Test
    void updateCountsNonSpectatorsAndReportsChanges() {
        var a = player(1, GameMode.SURVIVAL);
        var b = player(2, GameMode.SURVIVAL);
        var c = player(3, GameMode.SPECTATOR);
        var st = new SleepStatus();
        assertFalse(st.update(List.of(a, b, c)), "아무도 안 자면 알리지 않는다");
        assertEquals(2, st.activePlayers());
        a.setSleepingPos(new Pos(0, 0, 0));
        assertTrue(st.update(List.of(a, b, c)));
        assertEquals(1, st.amountSleeping());
        assertFalse(st.update(List.of(a, b, c)), "바뀐 게 없으면 알리지 않는다");
        assertFalse(st.areEnoughSleeping(100));
        assertTrue(st.areEnoughSleeping(50));
        a.setSleepingPos(null);
        assertTrue(st.update(List.of(a, b, c)), "자던 사람이 깨면 알린다");
        assertEquals(0, st.amountSleeping());
    }

    @Test
    void deepSleepNeedsHundredTicks() {
        var a = player(1, GameMode.SURVIVAL);
        var st = new SleepStatus();
        a.setSleepingPos(new Pos(0, 0, 0));
        st.update(List.of(a));
        assertTrue(st.areEnoughSleeping(100));
        a.sleepCounter(99);
        assertFalse(st.areEnoughDeepSleeping(100, List.of(a)));
        a.sleepCounter(100);
        assertTrue(st.areEnoughDeepSleeping(100, List.of(a)));
        st.removeAllSleepers();
        assertFalse(st.areEnoughSleeping(100));
    }

    @Test
    void skipNightGoesToNextMorningAndClearsRain() {
        assertEquals(24000, WorldClock.nextMorning(13000));
        assertEquals(48000, WorldClock.nextMorning(24000 + 18000));
        assertEquals(48000, WorldClock.nextMorning(24000), "자정 넘어 0 이어도 다음 날");
        var clock = new WorldClock();
        clock.restore(100, 13000, 0, 5000, 7000, true, true);
        var rules = new GameRules();
        int[] woke = {0};
        clock.skipNight(rules, () -> woke[0]++);
        assertEquals(1, woke[0]);
        assertEquals(24000, clock.dayTime());
        assertEquals(100, clock.gameTime(), "게임 시간은 그대로");
        assertFalse(clock.rainingFlag());
        assertFalse(clock.thunderingFlag());
        assertEquals(0, clock.rainTime());
        assertEquals(0, clock.thunderTime());
        // 비가 안 오면 날씨는 건드리지 않는다
        var dry = new WorldClock();
        dry.restore(0, 13000, 0, 5000, 7000, false, false);
        dry.skipNight(rules, () -> {});
        assertEquals(5000, dry.rainTime());
        // doDaylightCycle 이 꺼져 있으면 시간은 그대로
        rules.set("doDaylightCycle", 0);
        var still = new WorldClock();
        still.restore(0, 13000, 0, 0, 0, false, false);
        still.skipNight(rules, () -> {});
        assertEquals(13000, still.dayTime());
    }
}
