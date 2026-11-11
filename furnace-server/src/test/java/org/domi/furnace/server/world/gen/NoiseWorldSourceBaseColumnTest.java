// baseBlock(바닐라 getBaseColumn = iterateNoiseColumn) 은 노이즈 단계 블록만 — 표면 규칙이 바꾼 블록(영혼 모래·현무암·잔디·심층암)은 보이지 않는다.
package org.domi.furnace.server.world.gen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkSection;
import org.junit.jupiter.api.Test;

class NoiseWorldSourceBaseColumnTest {

    static final BlockRegistry BLOCKS = BlockRegistry.load("blocks/1.21.1.txt");
    static final RegistrySet REGISTRIES = RegistrySet.load("registries/1.21.1.txt");

    static int id(String name) { return BLOCKS.defaultStateId(name); }

    @Test
    void netherBaseColumnIsNoiseOnly() {
        NoiseWorldSource src = NoiseWorldSource.nether(12345L, BLOCKS, REGISTRIES);
        int netherrack = id("minecraft:netherrack"), lava = id("minecraft:lava");
        Set<Integer> noiseStates = Set.of(netherrack, lava, ChunkSection.AIR);
        int surfaceReplaced = 0;
        // 여러 네더 바이옴(영혼 모래 골짜기·현무암 삼각주·숲)을 지나게 흩어 둔다
        for (int i = 0; i < 24; i++) {
            int cx = (i * 977) % 400 - 200, cz = (i * 613) % 400 - 200;
            ChunkColumn full = src.generate(cx, cz);
            for (int y = full.minY(); y <= full.maxY(); y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int base = src.baseBlock(cx * 16 + x, y, cz * 16 + z);
                        int fin = full.getBlock(x, y, z);
                        assertTrue(noiseStates.contains(base), "노이즈 단계가 아닌 블록 " + base + " @" + cx + "," + cz);
                        if (base != fin) {
                            // 표면 규칙은 기본 블록(네더랙)만 바꾼다 — 공기·용암은 그대로
                            assertEquals(netherrack, base, "표면이 공기·용암을 바꿀 수 없다 @" + cx + "," + cz + " y" + y);
                            surfaceReplaced++;
                        }
                    }
                }
            }
        }
        assertTrue(surfaceReplaced > 1000, "표면 규칙이 바꾼 블록이 너무 적다(바이옴이 안 섞였다): " + surfaceReplaced);
        // 생성 높이(0..127) 밖은 공기(바닐라 NoiseColumn.getBlock)
        assertEquals(ChunkSection.AIR, src.baseBlock(5, 200, 5));
        assertEquals(ChunkSection.AIR, src.baseBlock(5, -1, 5));
    }

    @Test
    void overworldBaseColumnHasNoSurfaceBlocks() {
        NoiseWorldSource src = NoiseWorldSource.overworld(42L, BLOCKS, REGISTRIES);
        Set<Integer> surfaceOnly = Set.of(id("minecraft:grass_block"), id("minecraft:dirt"), id("minecraft:sand"),
            id("minecraft:deepslate"), id("minecraft:bedrock"), id("minecraft:snow_block"), id("minecraft:packed_ice"));
        int stone = id("minecraft:stone");
        int replaced = 0;
        for (int i = 0; i < 8; i++) {
            int cx = (i * 977) % 600 - 300, cz = (i * 613) % 600 - 300;
            ChunkColumn full = src.generate(cx, cz);
            for (int y = full.minY(); y <= full.maxY(); y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int base = src.baseBlock(cx * 16 + x, y, cz * 16 + z);
                        assertFalse(surfaceOnly.contains(base), "표면 규칙 블록이 바닥 열에 보인다 @" + cx + "," + cz + " y" + y);
                        if (base == stone && full.getBlock(x, y, z) != stone) replaced++;
                    }
                }
            }
        }
        assertTrue(replaced > 1000, "표면 규칙이 바꾼 블록이 너무 적다: " + replaced);
    }
}
