package org.domi.furnace.server.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Properties;
import org.junit.jupiter.api.Test;

class RconLimitsTest {

    @Test
    void defaultsMatchTheOldFixedValues() {
        RconLimits l = RconLimits.fromProperties(new Properties());
        assertEquals(new RconLimits(16, 4, 5, 10), l);
        assertEquals(RconLimits.DEFAULT, l);
    }

    @Test
    void keysOverrideDefaults() {
        Properties p = new Properties();
        p.setProperty("rcon.max-clients", "32");
        p.setProperty("rcon.max-clients-per-ip", " 8 ");
        p.setProperty("rcon.max-fails-per-connection", "3");
        p.setProperty("rcon.max-fails-per-ip", "20");
        assertEquals(new RconLimits(32, 8, 3, 20), RconLimits.fromProperties(p));
    }

    @Test
    void badValuesAreRejectedWithKeyName() {
        for (String bad : new String[] {"0", "-1", "abc", "10001"}) {
            Properties p = new Properties();
            p.setProperty("rcon.max-clients-per-ip", bad);
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> RconLimits.fromProperties(p));
            org.junit.jupiter.api.Assertions.assertTrue(e.getMessage().contains("rcon.max-clients-per-ip"), e.getMessage());
        }
    }

    @Test
    void missingFileGivesDefaults() {
        assertEquals(RconLimits.DEFAULT, RconLimits.load(java.nio.file.Path.of("no-such-furnace.properties")));
    }
}
