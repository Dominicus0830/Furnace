// level.dat 의 generate_features(=generate-structures) 가 생성기 구조물 시작점을 켜고 끈다.
package org.domi.furnace.server;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.domi.furnace.server.world.gen.structure.StructureLocator;
import org.domi.furnace.server.world.gen.structure.StructureRegistry;
import org.domi.furnace.server.world.storage.LevelData;
import org.junit.jupiter.api.Test;

class WorldGenOptionsTest {

    private static LevelData level(boolean features) {
        return LevelData.create("world", 0L, features, false, org.domi.furnace.server.world.GameMode.SURVIVAL, false,
                org.domi.furnace.server.world.Difficulty.NORMAL, List.of("vanilla"), List.of());
    }

    @Test
    void generateFeaturesFalse_noStructureStarts() {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        RegistrySet regs = RegistrySet.load("registries/1.21.1.txt");
        NoiseWorldSource on = NoiseWorldSource.overworld(0L, reg, regs);
        FurnaceMain.applyWorldGenOptions(level(true), on);
        StructureLocator.Found f = on.structureLocator().findNearest(StructureRegistry.structureTag("minecraft:village"), 0, 70, 0, 100);
        assertNotNull(f);
        int cx = f.x() >> 4, cz = f.z() >> 4;
        assertFalse(on.structureStartsFresh(cx, cz).isEmpty());

        NoiseWorldSource off = NoiseWorldSource.overworld(0L, reg, regs);
        FurnaceMain.applyWorldGenOptions(level(false), off);
        assertTrue(off.structureStartsFresh(cx, cz).isEmpty());
        // 바닐라: 구조물이 꺼지면 STRUCTURE_STARTS 가 시작점을 만들지 않아 /locate(StructureCheck → 시작점 단계)도 못 찾는다(A06-07)
        assertNull(off.structureLocator().findNearest(StructureRegistry.structureTag("minecraft:village"), 0, 70, 0, 100));
    }

    @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp;

    private LevelData levelFor(String levelType) throws Exception {
        java.nio.file.Path f = tmp.resolve(levelType.replace(':', '_') + ".properties");
        java.nio.file.Files.writeString(f, "level-type=" + levelType.replace(":", "\\:") + "\n");
        var props = new org.domi.furnace.server.config.ServerPropertiesFile(f).get();
        LevelData d = level(true);
        FurnaceMain.applyLevelType(d, props);
        return d;
    }

    @Test
    void levelTypeSelectsOverworldGenerator() throws Exception {
        assertEquals("minecraft:amplified", FurnaceMain.overworldNoiseSettings(levelFor("minecraft:amplified")));
        assertEquals("minecraft:large_biomes", FurnaceMain.overworldNoiseSettings(levelFor("LARGE_BIOMES")));
        assertEquals("minecraft:overworld", FurnaceMain.overworldNoiseSettings(levelFor("minecraft:normal")));
        // 바닐라: largebiomes 는 minecraft:largebiomes 로 읽혀 없는 프리셋 → 경고 뒤 normal
        assertEquals("minecraft:overworld", FurnaceMain.overworldNoiseSettings(levelFor("largebiomes")));
        // 네임스페이스 없는 flat 도 평지(이전에는 minecraft:flat 만 알아봤다)
        assertEquals("minecraft:flat", levelFor("flat").overworldGenerator().tags().get("type"));
        // 디버그 월드·단일 바이옴 지표
        LevelData debug = levelFor("debug_all_block_states");
        assertTrue(debug.isDebugWorld());
        assertFalse(debug.isFlatWorld());
        assertEquals("minecraft:debug", debug.overworldGenerator().tags().get("type"));
        assertTrue(levelFor("flat").isFlatWorld());
        LevelData single = levelFor("single_biome_surface");
        assertFalse(single.isDebugWorld());
        assertEquals("minecraft:plains", single.overworldFixedBiome());
        assertEquals("minecraft:overworld", FurnaceMain.overworldNoiseSettings(single));
        assertNull(levelFor("minecraft:normal").overworldFixedBiome());
        // level.dat 를 다시 읽어도 같다
        assertEquals("minecraft:plains", LevelData.fromNbt(single.toNbt(0L)).overworldFixedBiome());
        assertTrue(LevelData.fromNbt(debug.toNbt(0L)).isDebugWorld());
    }
}
