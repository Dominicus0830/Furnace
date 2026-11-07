// W3 월드젠 단계 훅: 기본 훅은 바닐라와 같은 블록, 단계 끄기·대체·구조물/지형지물 거르기·장식 영역 쓰기 범위·예외 격리.
package org.domi.furnace.server.world.gen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.worldgen.ChunkGenerationHooks;
import org.domi.furnace.api.worldgen.DecorationRegion;
import org.domi.furnace.api.worldgen.GenerationChunk;
import org.domi.furnace.api.worldgen.GenerationStage;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.structure.StructureStart;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GenHooksTest {

    static final long SEED = 12345L;
    static BlockRegistry blocks;
    static RegistrySet registries;

    @BeforeAll
    static void load() {
        blocks = BlockRegistry.load("blocks/1.21.1.txt");
        registries = RegistrySet.load("registries/1.21.1.txt");
    }

    static NoiseWorldSource source(ChunkGenerationHooks hooks) {
        NoiseWorldSource n = NoiseWorldSource.overworld(SEED, blocks, registries);
        if (hooks != null) GenHooks.attach(n, "test:hooks", k -> hooks, "minecraft:overworld", SEED, blocks, registries);
        return n;
    }

    static void assertSameBlocks(ChunkColumn a, ChunkColumn b, String what) {
        for (int y = a.minY(); y <= a.maxY(); y++)
            for (int z = 0; z < 16; z++)
                for (int x = 0; x < 16; x++)
                    if (a.getBlock(x, y, z) != b.getBlock(x, y, z)) {
                        fail(what + ": (" + x + "," + y + "," + z + ") " + blocks.nameOf(a.getBlock(x, y, z)) + " != " + blocks.nameOf(b.getBlock(x, y, z)));
                    }
    }

    static boolean sameBlocks(ChunkColumn a, ChunkColumn b) {
        for (int y = a.minY(); y <= a.maxY(); y++)
            for (int z = 0; z < 16; z++)
                for (int x = 0; x < 16; x++)
                    if (a.getBlock(x, y, z) != b.getBlock(x, y, z)) return false;
        return true;
    }

    @Test
    void defaultHooksGiveVanillaBlocks() {
        NoiseWorldSource vanilla = source(null);
        AtomicInteger calls = new AtomicInteger();
        NoiseWorldSource hooked = source(new ChunkGenerationHooks() {
            @Override public void noise(GenerationChunk c) { calls.incrementAndGet(); }
            @Override public void decorate(DecorationRegion r) { calls.incrementAndGet(); }
        });
        assertSameBlocks(vanilla.generateIsolated(0, 0), hooked.generateIsolated(0, 0), "훅(아무것도 안 함) 단 청크는 바닐라와 같아야");
        assertTrue(calls.get() > 0, "훅이 불려야 한다");
        assertEquals(vanilla.applyStructureStarts(0, 0).size(), hooked.applyStructureStarts(0, 0).size());
    }

    @Test
    void replacedNoiseStillGetsVanillaSurfaceRules() {
        BlockData stone = BlockData.of("minecraft:stone");
        NoiseWorldSource n = source(new ChunkGenerationHooks() {
            @Override public boolean runVanilla(GenerationStage stage, int cx, int cz) { return stage != GenerationStage.NOISE; }
            @Override public void noise(GenerationChunk c) {
                assertEquals("minecraft:air", c.blockType(3, 40, 3), "바닐라 노이즈를 끄면 공기에서 시작");
                assertEquals(c.minY() - 1, c.highestBlockY(3, 3));
                c.fill(0, c.minY(), 0, 15, 60, 15, stone);
                assertEquals(60, c.highestBlockY(3, 3));
                assertNotNull(c.biome(3, 60, 3));
            }
        });
        ChunkColumn col = n.generate(0, 0);
        assertEquals("minecraft:bedrock", blocks.nameOf(col.getBlock(5, -64, 5)), "표면 규칙(기반암 바닥)은 플러그인 돌에도 적용");
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++) assertTrue(ChunkSectionAir.isAir(col.getBlock(x, 61, z)), "60 위는 공기");
        boolean surfaced = false;
        for (int x = 0; x < 16 && !surfaced; x++)
            for (int z = 0; z < 16; z++) if (!blocks.nameOf(col.getBlock(x, 60, z)).equals("minecraft:stone")) { surfaced = true; break; }
        assertTrue(surfaced, "맨 위 칸은 표면 규칙으로 바뀐 칸이 있어야 한다");
    }

    @Test
    void carversCanBeReplaced() {
        BlockData gold = BlockData.of("minecraft:gold_block");
        NoiseWorldSource n = source(new ChunkGenerationHooks() {
            @Override public boolean runVanilla(GenerationStage stage, int cx, int cz) { return stage != GenerationStage.CARVERS; }
            @Override public void carvers(GenerationChunk c) { c.setBlock(8, 0, 8, gold); }
        });
        NoiseWorldSource plain = source(null);
        for (int[] c : new int[][] {{0, 0}, {5, -3}}) {
            ChunkColumn carvedOff = n.generateWithCarvers(c[0], c[1]);
            ChunkColumn terrain = plain.generate(c[0], c[1]);
            assertEquals("minecraft:gold_block", blocks.nameOf(carvedOff.getBlock(8, 0, 8)));
            carvedOff.setBlock(8, 0, 8, terrain.getBlock(8, 0, 8));
            assertSameBlocks(terrain, carvedOff, "카버를 끄면 깎기 전 지형 그대로");
        }
    }

    /** 반경 안에서 시작점이 있는 첫 청크. */
    static StructureStart findStart(NoiseWorldSource n) {
        for (int r = 0; r < 48; r++)
            for (int x = -r; x <= r; x++)
                for (int z = -r; z <= r; z++) {
                    if (Math.max(Math.abs(x), Math.abs(z)) != r) continue;
                    List<StructureStart> s = n.applyStructureStarts(x, z);
                    if (!s.isEmpty()) return s.get(0);
                }
        return null;
    }

    @Test
    void structuresCanBeFilteredOrDisabled() {
        StructureStart found = findStart(source(null));
        assertNotNull(found, "구조물 시작점을 찾지 못함");
        String id = found.structureId();
        int cx = found.chunkX(), cz = found.chunkZ();
        NoiseWorldSource filtered = source(new ChunkGenerationHooks() {
            @Override public boolean structure(String structureId, int x, int z) { return !structureId.equals(id); }
        });
        assertTrue(filtered.applyStructureStarts(cx, cz).stream().noneMatch(s -> s.structureId().equals(id)), id + " 가 걸러져야");
        NoiseWorldSource off = source(new ChunkGenerationHooks() {
            @Override public boolean runVanilla(GenerationStage stage, int x, int z) { return stage != GenerationStage.STRUCTURES; }
        });
        assertTrue(off.applyStructureStarts(cx, cz).isEmpty(), "STRUCTURES 를 끄면 시작점이 없다");
    }

    @Test
    void featuresOffEqualsRejectingEveryFeature() {
        NoiseWorldSource off = source(new ChunkGenerationHooks() {
            @Override public boolean runVanilla(GenerationStage stage, int x, int z) { return stage != GenerationStage.FEATURES; }
        });
        AtomicInteger asked = new AtomicInteger();
        NoiseWorldSource reject = source(new ChunkGenerationHooks() {
            @Override public boolean feature(String id, int x, int z) { asked.incrementAndGet(); return false; }
        });
        ChunkColumn a = off.generateIsolated(0, 0), b = reject.generateIsolated(0, 0);
        assertSameBlocks(a, b, "FEATURES 끄기 = 지형지물 모두 거절");
        assertTrue(asked.get() > 0);
        assertFalse(sameBlocks(a, source(null).generateIsolated(0, 0)), "바닐라 장식이 빠졌으면 블록이 달라야 한다");
    }

    @Test
    void decorateWritesTheThreeByThreeAroundOrigin() {
        BlockData gold = BlockData.of("minecraft:gold_block");
        AtomicLong seenSeed = new AtomicLong();
        AtomicInteger refused = new AtomicInteger();
        NoiseWorldSource n = source(new ChunkGenerationHooks() {
            @Override public void decorate(DecorationRegion r) {
                if (r.originChunkX() != 0 || r.originChunkZ() != 0) return;
                seenSeed.set(r.decorationSeed());
                assertTrue(r.canWrite(16 + 3, 200, 3));
                assertTrue(r.setBlock(16 + 3, 200, 3, gold), "이웃 청크(1,0)에 쓸 수 있다");
                assertFalse(r.canWrite(32 + 3, 200, 3));
                if (!r.setBlock(32 + 3, 200, 3, gold)) refused.incrementAndGet();
                assertEquals("minecraft:gold_block", r.blockType(16 + 3, 200, 3));
                assertTrue(r.highestBlockY(16 + 3, 3) >= 200);
            }
        });
        ChunkColumn east = n.generateIsolated(1, 0);
        assertEquals("minecraft:gold_block", blocks.nameOf(east.getBlock(3, 200, 3)), "원점 (0,0) 장식이 (1,0) 으로 넘친다");
        assertEquals(1, refused.get(), "두 칸 떨어진 청크에는 못 쓴다");
        assertEquals(GenHooks.decorationSeed(SEED, 0, 0), seenSeed.get());
        ChunkColumn far = n.generateIsolated(2, 0);
        assertNotEquals("minecraft:gold_block", blocks.nameOf(far.getBlock(3, 200, 3)));
    }

    @Test
    void sharedPathDecoratesEachOriginOnce() {
        AtomicInteger origin00 = new AtomicInteger();
        NoiseWorldSource n = source(new ChunkGenerationHooks() {
            @Override public void decorate(DecorationRegion r) {
                if (r.originChunkX() == 0 && r.originChunkZ() == 0) origin00.incrementAndGet();
            }
        });
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) n.generateWithFeatures(x, z);
        assertEquals(1, origin00.get(), "서버 경로는 원점마다 장식 훅을 한 번 부른다");
    }

    @Test
    void throwingHooksAreIsolated() {
        NoiseWorldSource bad = source(new ChunkGenerationHooks() {
            @Override public boolean runVanilla(GenerationStage stage, int x, int z) { throw new IllegalStateException("boom"); }
            @Override public void noise(GenerationChunk c) { throw new IllegalStateException("boom"); }
            @Override public void decorate(DecorationRegion r) { throw new IllegalStateException("boom"); }
            @Override public boolean feature(String id, int x, int z) { throw new IllegalStateException("boom"); }
        });
        assertSameBlocks(source(null).generateIsolated(0, 0), bad.generateIsolated(0, 0), "던지는 훅은 건너뛰고 바닐라 그대로");
    }

    @Test
    void missingHookFallsBackToVanilla() {
        NoiseWorldSource n = NoiseWorldSource.overworld(SEED, blocks, registries);
        GenHooks.attach(n, "test:gone", k -> null, "minecraft:overworld", SEED, blocks, registries);
        assertSameBlocks(source(null).generate(0, 0), n.generate(0, 0), "등록되지 않은 키는 바닐라");
    }

    @Test
    void superflatHooks() {
        BlockData glass = BlockData.of("minecraft:glass");
        SuperflatWorldSource flat = new SuperflatWorldSource(blocks, 0);
        GenHooks.attach(flat, "test:flat", k -> new ChunkGenerationHooks() {
            @Override public boolean runVanilla(GenerationStage stage, int cx, int cz) { return false; }
            @Override public void noise(GenerationChunk c) {
                assertEquals(-63, c.seaLevel());
                c.setBlock(0, 10, 0, glass);
            }
            @Override public void decorate(DecorationRegion r) {
                assertTrue(r.setBlock(r.originChunkX() * 16 + 1, 11, r.originChunkZ() * 16, glass));
                assertFalse(r.canWrite(r.originChunkX() * 16 + 16, 11, r.originChunkZ() * 16), "평지 장식은 그 청크 안만");
            }
        }, "minecraft:overworld", SEED, blocks, registries);
        ChunkColumn c = flat.generate(2, 3);
        assertEquals("minecraft:air", blocks.nameOf(c.getBlock(0, -64, 0)), "NOISE 를 끄면 층이 없다");
        assertEquals("minecraft:glass", blocks.nameOf(c.getBlock(0, 10, 0)));
        assertEquals("minecraft:glass", blocks.nameOf(c.getBlock(1, 11, 0)));
        assertEquals(11, c.surfaceBlockY(1, 0));
    }

    /** ChunkSection.isAir 를 테스트에서 짧게. */
    private static final class ChunkSectionAir {
        static boolean isAir(int s) { return org.domi.furnace.server.world.ChunkSection.isAir(s); }
    }
}
