package org.domi.furnace.server.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Properties;
import org.junit.jupiter.api.Test;

class WorldgenGuardSettingsTest {

    private static Properties props(String... kv) {
        Properties p = new Properties();
        for (int i = 0; i < kv.length; i += 2) p.setProperty(kv[i], kv[i + 1]);
        return p;
    }

    @Test
    void offByDefault() {
        assertFalse(WorldgenGuardSettings.fromProperties(props(), null, w -> {}).tickGuard());
        assertFalse(WorldgenGuardSettings.DEFAULT.tickGuard());
    }

    @Test
    void keyTurnsItOn() {
        assertTrue(WorldgenGuardSettings.fromProperties(props("worldgen.tick-guard", " TRUE "), null, w -> {}).tickGuard());
        assertFalse(WorldgenGuardSettings.fromProperties(props("worldgen.tick-guard", "false"), null, w -> {}).tickGuard());
    }

    @Test
    void jvmSwitchWins() {
        assertFalse(WorldgenGuardSettings.fromProperties(props("worldgen.tick-guard", "true"), "false", w -> {}).tickGuard());
        assertTrue(WorldgenGuardSettings.fromProperties(props(), "true", w -> {}).tickGuard());
    }

    @Test
    void badValueFails() {
        assertThrows(IllegalArgumentException.class, () -> WorldgenGuardSettings.fromProperties(props("worldgen.tick-guard", "yes"), null, w -> {}));
        assertThrows(IllegalArgumentException.class, () -> WorldgenGuardSettings.fromProperties(props(), "1", w -> {}));
    }
}
