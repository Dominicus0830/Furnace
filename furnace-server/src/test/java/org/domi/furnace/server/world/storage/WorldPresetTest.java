// level-type → 월드 프리셋 해석(바닐라 WorldDimensionData.create)과 level.dat 오버월드 생성기 기록.
package org.domi.furnace.server.world.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.world.Difficulty;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.Test;

class WorldPresetTest {

    @Test
    void resolvesLikeVanilla() {
        assertEquals(WorldPreset.NORMAL, WorldPreset.resolve("minecraft:normal"));
        assertEquals(WorldPreset.AMPLIFIED, WorldPreset.resolve("minecraft:amplified"));
        assertEquals(WorldPreset.AMPLIFIED, WorldPreset.resolve("amplified"));
        assertEquals(WorldPreset.LARGE_BIOMES, WorldPreset.resolve("large_biomes"));
        assertEquals(WorldPreset.LARGE_BIOMES, WorldPreset.resolve(":large_biomes"));
        assertEquals(WorldPreset.FLAT, WorldPreset.resolve("flat"));
        // 올바른 id 로 읽히므로 옛 이름표까지 가지 않는다 → 프리셋 없음(바닐라: 경고 뒤 normal)
        assertNull(WorldPreset.resolve("largebiomes"));
        assertNull(WorldPreset.resolve("default"));
        assertNull(WorldPreset.resolve("large biomes"));
        assertNull(WorldPreset.resolve("minecraft:customized"));
    }

    private static LevelData newLevel() {
        return LevelData.create("world", 7L, true, false, GameMode.SURVIVAL, false, Difficulty.NORMAL,
                List.of("vanilla"), List.of());
    }

    @Test
    void noiseOverworldIsRecordedAndReadBack() {
        LevelData d = newLevel();
        assertEquals("minecraft:overworld", d.overworldNoiseSettings());
        d.useNoiseOverworld("minecraft:amplified");
        NbtCompound nbt = d.toNbt(0L);
        NbtCompound ow = nbt.getCompound("WorldGenSettings").getCompound("dimensions").getCompound("minecraft:overworld");
        NbtCompound gen = ow.getCompound("generator");
        assertEquals("minecraft:noise", gen.tags().get("type"));
        assertEquals("minecraft:amplified", gen.tags().get("settings"));
        assertEquals("minecraft:overworld", gen.getCompound("biome_source").tags().get("preset"));
        assertEquals("minecraft:multi_noise", gen.getCompound("biome_source").tags().get("type"));
        assertEquals("minecraft:overworld", ow.tags().get("type"));
        assertTrue(nbt.getCompound("WorldGenSettings").getCompound("dimensions").contains("minecraft:the_nether"));
        assertEquals("minecraft:amplified", d.overworldNoiseSettings());
    }

    @Test
    void flatOverworldHasNoNoiseSettings() {
        LevelData d = newLevel();
        d.useFlatOverworld(new NbtCompound());
        assertNull(d.overworldNoiseSettings());
    }
}
