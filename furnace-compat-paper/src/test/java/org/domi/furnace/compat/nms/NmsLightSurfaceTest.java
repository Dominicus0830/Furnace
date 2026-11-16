package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftWorld;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * T-205 빛 표면 묶음 B: FAWE 2.16.2 {@code PaperweightGetBlocks#getSkyLight/getEmittedLight/fillLightNibble/removeSectionLighting}·NativeAccess
 * {@code updateLightingForBlock} 가 부르는 빛 엔진 면. 기대값은 Paper 1.21.1 Moonrise(Starlight) 원문의 규칙이다
 * ({@code LevelLightEngineMixin}·{@code ThreadedLevelLightEngineMixin}·{@code StarLightInterface} — 설계 docs/feature-parity/compat-nms-t205-light-surface-2026-11-16.md 2장).
 * 빛 값 자체(발광석 15, 한 칸마다 1 씩)는 바닐라·Starlight 가 같은 단순 장면만 쓴다.
 */
class NmsLightSurfaceTest {

    private static FurnaceTestServer server;
    private static ServerLevel level;
    private static org.domi.furnace.api.world.BlockPos at;

    @BeforeAll
    static void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE);
        var tp = server.join("Light");
        server.tick(3);
        var loc = tp.player().location();
        at = new org.domi.furnace.api.world.BlockPos((int) Math.floor(loc.x()), (int) Math.floor(loc.y()), (int) Math.floor(loc.z()));
        level = ((CraftWorld) org.domi.furnace.compat.paper.Compat.player(tp.player()).getWorld()).getHandle();
    }

    @AfterAll
    static void stop() { server.close(); }

    private static LevelLightEngine engine() { return level.getChunkSource().getLightEngine(); }

    private static <T> T onRegion(java.util.function.Supplier<T> task) { return server.onRegion(at, task); }

    /** 플레이어 머리 위 공중(평지 위) 칸. */
    private static BlockPos air(int dx, int dy, int dz) { return new BlockPos(at.x() + dx, at.y() + dy, at.z() + dz); }

    private static void setNormally(BlockPos p, net.minecraft.world.level.block.Block b) {
        onRegion(() -> level.setBlock(p, b.defaultBlockState(), 3));
        server.tick(2);
    }

    @Test
    void enginesAndListenersHaveVanillaShape() {
        LevelLightEngine e = engine();
        assertSame(e, level.getLightEngine(), "Level#getLightEngine = 청크 공급원의 엔진");
        assertSame(e, ((net.minecraft.world.level.chunk.ChunkSource) level.getChunkSource()).getLightEngine());
        assertSame(e.getLayerListener(LightLayer.BLOCK), e.getLayerListener(LightLayer.BLOCK), "Paper 는 같은 읽개를 준다");
        assertNotSame(LayerLightEventListener.DummyLightLayerEventListener.INSTANCE, e.getLayerListener(LightLayer.SKY), "오버월드는 하늘빛이 있다");
        assertEquals(-5, e.getMinLightSection());
        assertEquals(26, e.getLightSectionCount());
        assertEquals(21, e.getMaxLightSection());
        var dummy = LayerLightEventListener.DummyLightLayerEventListener.INSTANCE;
        assertNull(dummy.getDataLayerData(SectionPos.of(0, 0, 0)));
        assertEquals(0, dummy.getLightValue(BlockPos.ZERO));
    }

    /** Paper: getDataLayerData 는 새 사본(고쳐도 서버 빛은 그대로), queueSectionData 는 아무 일도 안 한다 — FAWE removeSectionLighting·fillLightNibble 은 서버 빛을 못 바꾼다. */
    @Test
    void dataLayerIsACopyAndQueueSectionDataIsIgnored() {
        BlockPos glow = air(3, 4, 3);
        setNormally(glow, Blocks.GLOWSTONE);
        SectionPos sp = SectionPos.of(glow);
        int lx = glow.getX() & 15, ly = glow.getY() & 15, lz = glow.getZ() & 15;
        onRegion(() -> {
            var block = engine().getLayerListener(LightLayer.BLOCK);
            assertEquals(15, block.getLightValue(glow));
            DataLayer d = block.getDataLayerData(sp);
            assertNotNull(d);
            assertEquals(15, d.get(lx, ly, lz));
            java.util.Arrays.fill(d.getData(), (byte) 0);   // FAWE removeSectionLighting 처럼 받은 배열을 지운다
            assertNotSame(d, block.getDataLayerData(sp), "부를 때마다 새 사본");
            assertEquals(15, block.getDataLayerData(sp).get(lx, ly, lz), "서버 빛은 그대로(Paper 도 사본)");
            engine().queueSectionData(LightLayer.BLOCK, sp, new DataLayer(0));
            engine().queueSectionData(LightLayer.SKY, sp, new DataLayer(0));
            return null;
        });
        server.tick(2);
        onRegion(() -> {
            assertEquals(15, engine().getLayerListener(LightLayer.BLOCK).getLightValue(glow), "queueSectionData 는 무시(Paper)");
            assertEquals(15, engine().getLayerListener(LightLayer.SKY).getLightValue(air(0, 10, 0)));
            return null;
        });
        setNormally(glow, Blocks.AIR);
    }

    /** 다른 스레드(FAWE 일꾼처럼)는 주인이 틱 끝에 낸 공개본을 읽는다 — 주인 스레드의 값과 같다. */
    @Test
    void otherThreadsReadTheSameLight() throws Exception {
        BlockPos glow = air(-3, 4, -3);
        setNormally(glow, Blocks.GLOWSTONE);
        SectionPos sp = SectionPos.of(glow);
        int[] owner = onRegion(() -> new int[] {
                engine().getLayerListener(LightLayer.BLOCK).getLightValue(glow),
                engine().getLayerListener(LightLayer.BLOCK).getLightValue(glow.east()),
                engine().getLayerListener(LightLayer.SKY).getLightValue(glow.above()),
                level.getRawBrightness(glow.east(), 4)});
        byte[] ownerLayer = onRegion(() -> engine().getLayerListener(LightLayer.BLOCK).getDataLayerData(sp).getData().clone());
        var f = new java.util.concurrent.CompletableFuture<Object[]>();
        Thread t = new Thread(() -> {
            try {
                f.complete(new Object[] {new int[] {
                        engine().getLayerListener(LightLayer.BLOCK).getLightValue(glow),
                        engine().getLayerListener(LightLayer.BLOCK).getLightValue(glow.east()),
                        engine().getLayerListener(LightLayer.SKY).getLightValue(glow.above()),
                        level.getRawBrightness(glow.east(), 4)},
                        engine().getLayerListener(LightLayer.BLOCK).getDataLayerData(sp).getData()});
            } catch (Throwable e) {
                f.completeExceptionally(e);
            }
        }, "fawe-like-worker");
        t.start();
        Object[] got = f.get(30, java.util.concurrent.TimeUnit.SECONDS);
        assertArrayEquals(new int[] {15, 14, 15, 14}, owner, "발광석 15, 옆 14, 위 하늘 15, raw(옆, 어둠 4) = max(15 − 4, 14)");
        assertArrayEquals(owner, (int[]) got[0], "다른 스레드 = 주인");
        assertArrayEquals(ownerLayer, (byte[]) got[1]);
        setNormally(glow, Blocks.AIR);
    }

    /** Starlight 규칙: 로드되지 않은 청크·빛 섹션 위는 하늘빛 15·블록빛 0, 층은 null. 빛 섹션 아래는 맨 아래 빛 섹션 바닥 값. */
    @Test
    void starlightRulesAtTheEdges() {
        LevelLightEngine e = engine();
        var sky = e.getLayerListener(LightLayer.SKY);
        var block = e.getLayerListener(LightLayer.BLOCK);
        BlockPos far = new BlockPos(1_600_000, 70, 1_600_000);
        assertEquals(15, sky.getLightValue(far));
        assertEquals(0, block.getLightValue(far));
        assertNull(sky.getDataLayerData(SectionPos.of(far)));
        assertNull(block.getDataLayerData(SectionPos.of(far)));
        assertEquals(15, e.getRawBrightness(far, 0));
        onRegion(() -> {
            int cx = at.x() >> 4, cz = at.z() >> 4;
            assertEquals(15, sky.getLightValue(new BlockPos(at.x(), 400, at.z())), "가장 높은 빛 섹션 위");
            assertEquals(0, block.getLightValue(new BlockPos(at.x(), 400, at.z())));
            assertNull(sky.getDataLayerData(SectionPos.of(cx, 21, cz)), "빛 섹션 범위 밖");
            assertNotNull(sky.getDataLayerData(SectionPos.of(cx, 20, cz)), "맨 위 빛 섹션(블록 섹션 + 1)");
            assertNotNull(block.getDataLayerData(SectionPos.of(cx, -5, cz)), "맨 아래 빛 섹션(블록 섹션 − 1)");
            assertEquals(sky.getLightValue(new BlockPos(at.x(), -80, at.z())), sky.getLightValue(new BlockPos(at.x(), -200, at.z())),
                    "맨 아래 빛 섹션 아래는 그 섹션 바닥 값");
            assertEquals(0, block.getLightValue(new BlockPos(at.x(), -200, at.z())));
            return null;
        });
    }

    /** Starlight: isLightCorrect 가 거짓인 청크의 하늘빛은 15·층 null, 블록빛은 그대로. */
    @Test
    void notLightCorrectChunkReadsLikePaper() {
        BlockPos p = air(0, 2, 0);
        onRegion(() -> {
            LevelChunk c = level.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4);
            BlockPos under = new BlockPos(p.getX(), level.getMinBuildHeight() + 1, p.getZ());
            int blockBefore = engine().getLayerListener(LightLayer.BLOCK).getLightValue(under);
            c.setLightCorrect(false);
            try {
                assertEquals(15, engine().getLayerListener(LightLayer.SKY).getLightValue(under));
                assertNull(engine().getLayerListener(LightLayer.SKY).getDataLayerData(SectionPos.of(p)));
                assertEquals(blockBefore, engine().getLayerListener(LightLayer.BLOCK).getLightValue(under));
                assertNotNull(engine().getLayerListener(LightLayer.BLOCK).getDataLayerData(SectionPos.of(p)));
            } finally {
                c.setLightCorrect(true);
            }
            return null;
        });
    }

    /**
     * Paper: LevelChunkSection#setBlockState 는 빛을 고치지 않고, checkBlock(아무 스레드)이 그 칸을 다시 본다 — FAWE NativeAccess 의
     * updateLightingForBlock. 다른 스레드에서 걸어도 다음 틱에 계산돼 보는 쪽에 보인다.
     */
    @Test
    void checkBlockFromAnotherThreadLightsARawWrite() throws Exception {
        BlockPos g = air(6, 3, -6);
        onRegion(() -> {
            LevelChunk c = level.getChunkSource().getChunkNow(g.getX() >> 4, g.getZ() >> 4);
            int idx = (g.getY() >> 4) - (level.getMinBuildHeight() >> 4);
            c.getSection(idx).setBlockState(g.getX() & 15, g.getY() & 15, g.getZ() & 15, Blocks.GLOWSTONE.defaultBlockState());
            return null;
        });
        server.tick(2);
        assertEquals(0, (int) onRegion(() -> engine().getLayerListener(LightLayer.BLOCK).getLightValue(g)), "원시 쓰기는 빛을 바꾸지 않는다(Paper 와 같다)");
        Thread t = new Thread(() -> engine().checkBlock(g), "native-access-like");
        t.start();
        t.join();
        server.tick(2);
        onRegion(() -> {
            var block = engine().getLayerListener(LightLayer.BLOCK);
            assertEquals(15, block.getLightValue(g));
            assertEquals(14, block.getLightValue(g.west()));
            assertEquals(13, block.getLightValue(g.west().north()));
            return null;
        });
        assertDoesNotThrow(() -> engine().checkBlock(new BlockPos(g.getX(), 10_000, g.getZ())), "높이 밖은 버린다");
        assertDoesNotThrow(() -> engine().checkBlock(new BlockPos(1_600_000, 70, 1_600_000)), "로드되지 않은 청크는 버린다");
        setNormally(g, Blocks.AIR);
    }

    /** Paper 가 아무 일도 안 하는 것은 아무 일도 안 하고, 세계 전체 빛 대기열을 묻거나 돌리는 것은 값을 지어내지 않고 던진다. */
    @Test
    void paperNoOpsAndUnsupportedParts() {
        LevelLightEngine e = engine();
        ChunkPos cp = new ChunkPos(at.x() >> 4, at.z() >> 4);
        assertDoesNotThrow(() -> e.setLightEnabled(cp, false));
        assertDoesNotThrow(() -> e.propagateLightSources(cp));
        assertDoesNotThrow(() -> e.retainData(cp, true));
        assertEquals("n/a", e.getDebugData(LightLayer.BLOCK, SectionPos.of(cp, 0)));
        assertThrows(UnsupportedNmsCall.class, e::hasLightWork);
        assertThrows(UnsupportedNmsCall.class, e::runLightUpdates);
        assertThrows(UnsupportedOperationException.class, () -> e.getLayerListener(LightLayer.BLOCK).runLightUpdates(), "Starlight 읽개");
        assertEquals(15, (int) onRegion(() -> engine().getLayerListener(LightLayer.SKY).getLightValue(air(0, 5, 0))), "켜짐 그대로");
    }
}
