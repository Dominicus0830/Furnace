// 전역 시간·날씨 틱: 바닐라 advanceWeatherCycle·tickTime 의 순서와 패킷.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.play.GameEvent;
import org.domi.furnace.server.net.protocol.play.SetTime;
import org.junit.jupiter.api.Test;

class WorldClockTest {

    @Test
    void timeAdvancesAndSyncsEvery20Ticks() {
        WorldClock c = new WorldClock();
        c.restore(100, 5000, 0, 1000, 1000, false, false);
        GameRules rules = new GameRules();
        List<Packet> out = new ArrayList<>();
        for (int i = 0; i < 20; i++) c.tick(rules, out::add);
        assertEquals(120, c.gameTime());
        assertEquals(5020, c.dayTime());
        assertEquals(1, out.stream().filter(p -> p instanceof SetTime).count());
        rules.set("doDaylightCycle", 0);
        c.tick(rules, out::add);
        assertEquals(121, c.gameTime());
        assertEquals(5020, c.dayTime(), "doDaylightCycle=false 면 하루 시간 정지");
        assertEquals(new SetTime(121, -5020), c.timePacket(rules));
    }

    @Test
    void rainStartsAfterCountdownAndFadesIn() {
        WorldClock c = new WorldClock();
        c.restore(0, 0, 0, 3, 50000, false, false);
        GameRules rules = new GameRules();
        List<Packet> out = new ArrayList<>();
        for (int i = 0; i < 3; i++) c.tick(rules, out::add);
        assertTrue(c.rainingFlag(), "rainTime 이 0 이 되는 틱에 비가 시작");
        assertFalse(c.isRaining(), "강도는 아직 0.2 이하");
        out.clear();
        for (int i = 0; i < 30 && !c.isRaining(); i++) c.tick(rules, out::add);
        assertTrue(c.isRaining());
        assertTrue(out.stream().anyMatch(p -> p instanceof GameEvent g && g.event() == GameEvent.START_RAINING));
    }

    @Test
    void clearWeatherTimeHoldsClear() {
        WorldClock c = new WorldClock();
        c.restore(0, 0, 0, 0, 0, true, true);
        c.setWeatherParameters(100, 0, false, false);
        GameRules rules = new GameRules();
        for (int i = 0; i < 99; i++) c.tick(rules, p -> {});
        assertFalse(c.rainingFlag());
        assertEquals(1, c.clearWeatherTime());
        assertEquals(1, c.rainTime(), "맑음 유지 중에는 rainTime=1 로 고정");
    }

    @Test
    void gameRulesRoundTrip() {
        GameRules g = GameRules.fromStrings(java.util.Map.of("keepInventory", "true", "randomTickSpeed", "abc", "unknownRule", "1"));
        assertTrue(g.getBoolean("keepInventory"));
        assertEquals(0, g.getInt("randomTickSpeed"), "잘못된 정수는 경고 후 0(바닐라)");
        assertEquals(51, GameRules.RULES.size());
        assertEquals("true", g.toStrings().get("keepInventory"));
        assertEquals(org.domi.furnace.server.world.storage.LevelData.DEFAULT_GAME_RULES.keySet(), new GameRules().toStrings().keySet());
    }
}
