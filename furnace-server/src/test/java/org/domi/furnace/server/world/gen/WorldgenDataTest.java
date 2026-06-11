// WorldgenData biomeTemperature 메서드 단위 검증
package org.domi.furnace.server.world.gen;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class WorldgenDataTest {

    @Test void biomeTemperatureKnownValues() {
        WorldgenData data = WorldgenData.load("worldgen/1.21.1");
        assertEquals(0.0f, data.biomeTemperature("minecraft:frozen_ocean"), 1e-6f);
        assertEquals(2.0f, data.biomeTemperature("minecraft:desert"), 1e-6f);
        assertEquals(0.8f, data.biomeTemperature("minecraft:plains"), 1e-6f);
    }

    @Test void biomeTemperatureUnknownBiome() {
        WorldgenData data = WorldgenData.load("worldgen/1.21.1");
        assertEquals(0.5f, data.biomeTemperature("minecraft:unknown_biome"), 1e-6f);
    }

    @Test void biomeTemperatureColdBiomes() {
        WorldgenData data = WorldgenData.load("worldgen/1.21.1");
        // Biomes with temperature < 0.15 should be cold
        assertTrue(data.biomeTemperature("minecraft:frozen_ocean") < 0.15f);
        assertTrue(data.biomeTemperature("minecraft:snowy_beach") < 0.15f);
        assertTrue(data.biomeTemperature("minecraft:ice_spikes") < 0.15f);
    }

    @Test void biomeTemperatureHotBiomes() {
        WorldgenData data = WorldgenData.load("worldgen/1.21.1");
        // Biomes with temperature >= 2.0 should be hot
        assertEquals(2.0f, data.biomeTemperature("minecraft:desert"), 1e-6f);
        assertEquals(2.0f, data.biomeTemperature("minecraft:badlands"), 1e-6f);
        assertEquals(2.0f, data.biomeTemperature("minecraft:savanna"), 1e-6f);
    }
}
