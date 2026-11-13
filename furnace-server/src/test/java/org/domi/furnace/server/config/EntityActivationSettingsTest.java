// 엔티티 활성 범위 설정(D11): 기본 켜짐·Paper 기본값, villagers 가 animals 를 따르는 Spigot 규칙, 잘못된 값, 키 표·바닐라 프리셋.
package org.domi.furnace.server.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class EntityActivationSettingsTest {

    private static EntityActivationSettings parse(String... kv) {
        Properties p = new Properties();
        for (int i = 0; i < kv.length; i += 2) p.setProperty(kv[i], kv[i + 1]);
        return EntityActivationSettings.fromProperties(p, w -> {});
    }

    @Test
    void defaultIsOnWithPaperValues() {
        var s = parse();
        assertTrue(s.enabled(), "D11: 기본 켜짐");
        assertEquals(EntityActivationSettings.DEFAULT, s);
        assertEquals(List.of(32, 32, 64, 16, 16, 32, 32), List.of(s.animals(), s.monsters(), s.raiders(), s.misc(), s.water(), s.villagers(), s.flyingMonsters()));
        assertEquals(List.of(4, 1200, 100, 8, 400, 100, 4, 600, 100, 8, 200, 100, 100, 20),
                List.of(s.wakeAnimalsMax(), s.wakeAnimalsEvery(), s.wakeAnimalsFor(), s.wakeMonstersMax(), s.wakeMonstersEvery(), s.wakeMonstersFor(),
                        s.wakeVillagersMax(), s.wakeVillagersEvery(), s.wakeVillagersFor(), s.wakeFlyingMax(), s.wakeFlyingEvery(), s.wakeFlyingFor(),
                        s.villagersWorkImmunityAfter(), s.villagersWorkImmunityFor()));
        assertTrue(s.tickInactiveVillagers());
        assertFalse(s.ignoreSpectators());
        assertTrue(s.villagersActiveForPanic());
        assertFalse(EntityActivationSettings.OFF.enabled());
    }

    @Test
    void canTurnOff() { assertFalse(parse("entity-activation-range.enabled", "false").enabled()); }

    @Test
    void villagersFollowAnimalsLikeSpigot() {
        assertEquals(24, parse("entity-activation-range.animals", "24").villagers());
        assertEquals(40, parse("entity-activation-range.animals", "24", "entity-activation-range.villagers", "40").villagers());
        assertEquals(32, parse("entity-activation-range.animals", "32").villagers());
    }

    @Test
    void warnsWhenOnAndRejectsBadValues() {
        List<String> warns = new ArrayList<>();
        EntityActivationSettings.fromProperties(new Properties(), warns::add);
        assertEquals(1, warns.size());
        assertTrue(warns.get(0).contains("entity-activation-range.enabled=false"));
        warns.clear();
        Properties off = new Properties();
        off.setProperty("entity-activation-range.enabled", "false");
        EntityActivationSettings.fromProperties(off, warns::add);
        assertTrue(warns.isEmpty());
        assertThrows(IllegalArgumentException.class, () -> parse("entity-activation-range.animals", "far"));
        assertThrows(IllegalArgumentException.class, () -> parse("entity-activation-range.enabled", "yes"));
    }

    @Test
    void keysAreKnownAndVanillaPresetTurnsItOff() {
        assertTrue(OptionKeys.isKnown("entity-activation-range.enabled"));
        assertTrue(OptionKeys.isKnown("entity-activation-range.flying-monsters"));
        assertTrue(OptionKeys.isKnown("entity-activation-range.wake-up-inactive.villagers-every"));
        assertTrue(OptionKeys.isKnown("entity-activation-range.villagers-active-for-panic"));
        Properties p = new Properties();
        p.setProperty(ConfigPresets.KEY, "vanilla");
        ConfigPresets.apply(p);
        assertFalse(EntityActivationSettings.fromProperties(p, w -> {}).enabled());
    }
}
