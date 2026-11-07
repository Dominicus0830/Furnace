package org.domi.furnace.server.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class RegionPoolSettingsTest {

    private static Properties props(String... kv) {
        Properties p = new Properties();
        for (int i = 0; i < kv.length; i += 2) p.setProperty(kv[i], kv[i + 1]);
        return p;
    }

    @Test
    void defaultIsShared() {
        List<String> warns = new ArrayList<>();
        var s = RegionPoolSettings.fromProperties(props(), null, warns::add);
        assertTrue(s.shared());
        assertEquals(2, s.starveTicks());
        assertTrue(warns.isEmpty());
    }

    @Test
    void keyTurnsItOff() {
        var s = RegionPoolSettings.fromProperties(props("shared-region-pool", "false"), null, w -> {});
        assertFalse(s.shared());
    }

    @Test
    void jvmSwitchWinsAndWarnsOnConflict() {
        List<String> warns = new ArrayList<>();
        var s = RegionPoolSettings.fromProperties(props("shared-region-pool", "true"), "false", warns::add);
        assertFalse(s.shared());
        assertEquals(1, warns.size());
        // 파일에 키가 없으면 경고 없이 JVM 값
        warns.clear();
        assertFalse(RegionPoolSettings.fromProperties(props(), "false", warns::add).shared());
        assertTrue(warns.isEmpty());
    }

    @Test
    void numbersAreConfigurableAndValidated() {
        var s = RegionPoolSettings.fromProperties(props("region-pool-dim-cap", "3", "region-pool-starve-ticks", "5"), null, w -> {});
        assertEquals(3, s.dimCap());
        assertEquals(5, s.starveTicks());
        assertThrows(IllegalArgumentException.class,
                () -> RegionPoolSettings.fromProperties(props("region-pool-starve-ticks", "0"), null, w -> {}));
        assertThrows(IllegalArgumentException.class,
                () -> RegionPoolSettings.fromProperties(props("region-pool-dim-cap", "-1"), null, w -> {}));
        assertThrows(IllegalArgumentException.class,
                () -> RegionPoolSettings.fromProperties(props("shared-region-pool", "yes"), null, w -> {}));
    }

    @Test
    void unusedTuningKeysWarnWhenOff() {
        List<String> warns = new ArrayList<>();
        RegionPoolSettings.fromProperties(props("shared-region-pool", "false", "region-pool-dim-cap", "2"), null, warns::add);
        assertEquals(1, warns.size());
    }

    @Test
    void oversubscriptionWarning() {
        List<String> warns = new ArrayList<>();
        RegionPoolSettings.DEFAULT.warnOversubscription(4, 3, 4, warns::add);   // 공유: 워커 4 / 코어 4
        assertTrue(warns.isEmpty());
        RegionPoolSettings.DEFAULT.warnOversubscription(9, 3, 4, warns::add);   // 공유: 9 > 8
        assertEquals(1, warns.size());
        warns.clear();
        new RegionPoolSettings(false, 0, 2).warnOversubscription(4, 3, 4, warns::add);   // 차원별: 12 > 8
        assertEquals(1, warns.size());
    }
}
