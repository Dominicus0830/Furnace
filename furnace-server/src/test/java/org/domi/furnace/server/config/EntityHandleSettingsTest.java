// 엔티티 손잡이 설정(T-122): 기본은 Paper 1.21.1 값, 잘못된 값은 키 이름과 함께 멈춤, 기동 경고, 키 표·바닐라 프리셋.
package org.domi.furnace.server.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class EntityHandleSettingsTest {

    private static EntityHandleSettings parse(String... kv) {
        Properties p = new Properties();
        for (int i = 0; i < kv.length; i += 2) p.setProperty(kv[i], kv[i + 1]);
        return EntityHandleSettings.fromProperties(p, w -> {});
    }

    @Test
    void defaultIsPaper() {
        var s = parse();
        assertEquals(EntityHandleSettings.DEFAULT, s);
        assertEquals(0.5, s.itemMerge());
        assertEquals(-1.0, s.expMerge());
        assertEquals(8, s.maxEntityCollisions());
        assertTrue(s.trackingEnabled());
        assertEquals(List.of(128, 96, 96, 96, 128, 64),
                List.of(s.trackPlayers(), s.trackAnimals(), s.trackMonsters(), s.trackMisc(), s.trackDisplay(), s.trackOther()));
        assertTrue(EntityHandleSettings.VANILLA.vanillaDifferences().isEmpty());
    }

    @Test
    void readsKeys() {
        var s = parse("merge-radius.item", "2.5", "merge-radius.exp", "3", "collisions.max-entity-collisions", "-1",
                "entity-tracking-range.enabled", "false", "entity-tracking-range.players", "48");
        assertEquals(2.5, s.itemMerge());
        assertEquals(3.0, s.expMerge());
        assertEquals(EntityHandleSettings.UNLIMITED, s.maxEntityCollisions());
        assertFalse(s.trackingEnabled());
        assertEquals(48, s.trackPlayers());
        assertEquals(EntityHandleSettings.VANILLA_PLAYER_RANGE, s.playerRange(), "꺼져 있으면 플레이어도 바닐라 512");
    }

    @Test
    void warnsOnlyWhenDifferentFromVanilla() {
        List<String> warns = new ArrayList<>();
        EntityHandleSettings.fromProperties(new Properties(), warns::add);
        assertEquals(1, warns.size());
        assertTrue(warns.get(0).contains("collisions.max-entity-collisions=-1"), warns.get(0));
        assertTrue(warns.get(0).contains("entity-tracking-range.enabled=false"), warns.get(0));
        warns.clear();
        Properties v = new Properties();
        v.setProperty("collisions.max-entity-collisions", "-1");
        v.setProperty("entity-tracking-range.enabled", "false");
        EntityHandleSettings.fromProperties(v, warns::add);
        assertTrue(warns.isEmpty(), warns.toString());
    }

    @Test
    void rejectsBadValuesNamingTheKey() {
        var e = assertThrows(IllegalArgumentException.class, () -> parse("collisions.max-entity-collisions", "-2"));
        assertTrue(e.getMessage().contains("collisions.max-entity-collisions") && e.getMessage().contains("-1"), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> parse("merge-radius.item", "-0.5"));
        assertThrows(IllegalArgumentException.class, () -> parse("merge-radius.item", "far"));
        assertThrows(IllegalArgumentException.class, () -> parse("merge-radius.exp", "NaN"));
        assertThrows(IllegalArgumentException.class, () -> parse("entity-tracking-range.animals", "-1"));
        assertThrows(IllegalArgumentException.class, () -> parse("entity-tracking-range.enabled", "yes"));
    }

    @Test
    void keysAreKnownAndVanillaPresetIsVanilla() {
        for (String k : new String[]{"merge-radius.item", "merge-radius.exp", "collisions.max-entity-collisions", "entity-tracking-range.enabled",
                "entity-tracking-range.players", "entity-tracking-range.other"}) {
            assertTrue(OptionKeys.isKnown(k), k);
        }
        Properties p = new Properties();
        p.setProperty(ConfigPresets.KEY, "vanilla");
        ConfigPresets.apply(p);
        var s = EntityHandleSettings.fromProperties(p, w -> {});
        assertTrue(s.vanillaDifferences().isEmpty(), s.vanillaDifferences().toString());
    }
}
