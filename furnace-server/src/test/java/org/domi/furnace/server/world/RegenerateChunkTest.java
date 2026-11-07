// W3 청크 재생성: 바뀐 블록·블록 엔티티가 생성 결과로 돌아오고, 이웃으로 번진 옛 빛이 지워지고, 보는 플레이어에게 다시 보내지며,
// 노이즈 월드는 같은 시드 새 생성기의 독립 경로 결과와 블록·바이옴이 같다. 재생성은 그때의 월드젠 훅을 쓴다.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.worldgen.ChunkGenerationHooks;
import org.domi.furnace.api.worldgen.GenerationChunk;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.play.LevelChunkWithLight;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.GenHooks;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class RegenerateChunkTest {

    private static final RegionPos R00 = new RegionPos(0, 0);
    private static final ChunkPos C00 = new ChunkPos(0, 0);

    private static List<Integer> ids(EmbeddedChannel ch) {
        ch.runPendingTasks();
        List<Integer> ids = new ArrayList<>();
        Object o;
        while ((o = ch.readOutbound()) != null) {
            ByteBuf b = (ByteBuf) o;
            ids.add(Types.readVarInt(b));
            b.release();
        }
        return ids;
    }

    @Test
    void flatChunkIsRestoredRelitAndResent() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, 1, 16, 2, 2, reg.directBits(), 7,
                Executors.newSingleThreadExecutor());
        int glow = reg.defaultStateId("minecraft:glowstone"), chest = reg.defaultStateId("minecraft:chest");
        int grass = reg.defaultStateId("minecraft:grass_block");
        int[] lightBefore = new int[1];
        EmbeddedChannel ch = new ConcurrentEmbeddedChannel();
        Player p = new Player(1, UUID.randomUUID(), "P", ch);
        w.subscribeChunk(new ChunkPos(1, 0), p).ready = true;   // 구독자가 있으면 내려가지 않는다
        sched.region(R00).execute(() -> {
            Region r = w.regionAt(R00);
            r.chunkAtBlocking(C00);
            r.chunkAtBlocking(new ChunkPos(1, 0));
            var lv = r.level();
            lv.setBlock(new Pos(15, -60, 5), glow, 3, 512);
            lv.setBlock(new Pos(2, -60, 2), chest, 3, 512);
            lv.setBlock(new Pos(0, -61, 0), 0, 3, 512);
            lv.runLightUpdates();
            lightBefore[0] = lv.getBlockLight(new Pos(16, -60, 5));
            assertNotNull(w.loadedChunk(0, 0).blockEntityAt(2, -60, 2), "상자 블록 엔티티");
        }).get();
        assertTrue(lightBefore[0] > 0, "빛이 이웃 청크로 번져 있어야 한다");

        w.subscribeChunk(C00, p).ready = true;
        ids(ch);

        assertTrue(w.regenerateChunk(0, 0, false, false).get(20, TimeUnit.SECONDS));

        sched.region(R00).execute(() -> {
            Region r = w.regionAt(R00);
            var lv = r.level();
            ChunkColumn col = w.loadedChunk(0, 0);
            assertEquals(grass, col.getBlock(0, -61, 0), "부순 잔디가 돌아온다");
            assertTrue(ChunkSection.isAir(col.getBlock(15, -60, 5)), "놓은 발광석이 사라진다");
            assertTrue(ChunkSection.isAir(col.getBlock(2, -60, 2)));
            assertNull(col.blockEntityAt(2, -60, 2), "상자 블록 엔티티가 사라진다");
            assertEquals(0, lv.getBlockLight(new Pos(16, -60, 5)), "이웃으로 번진 옛 빛도 지워진다");
            assertTrue(col.dirty(), "저장 대상");
        }).get();

        List<Integer> seen = new ArrayList<>();
        for (int i = 0; i < 40 && !seen.contains(LevelChunkWithLight.ID); i++) {
            Thread.sleep(50);
            sched.region(R00).execute(() -> { }).get();
            seen.addAll(ids(ch));
        }
        assertTrue(seen.contains(LevelChunkWithLight.ID), "보는 플레이어에게 새 청크를 다시 보낸다: " + seen);
        assertTrue(w.isSubscribed(C00, p, w.subscriptionOf(C00, p)) && w.subscriptionOf(C00, p).ready, "다시 보낸 뒤 구독은 ready");
        sched.close();
    }

    @Test
    void noiseChunkMatchesFreshIndependentGeneration() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");
        NoiseWorldSource src = NoiseWorldSource.overworld(777L, reg, registries);
        var gen = Executors.newFixedThreadPool(2);
        World w = new World(src, new ChunkWorkerPool(1), sched, 1, 16, 2, 2, reg.directBits(), 7, Executors.newSingleThreadExecutor(), gen);
        ChunkPos cp = new ChunkPos(2, -1);
        RegionPos rp = new RegionPos(2, -1);
        w.subscribeChunk(cp, new Player(1, UUID.randomUUID(), "P", new ConcurrentEmbeddedChannel())).ready = true;   // 내려가지 않게
        sched.region(rp).execute(() -> {
            Region r = w.regionAt(rp);
            r.chunkAtBlocking(cp);   // 이 월드 소스는 디스크가 없어 지형까지만 만든다 — 재생성은 장식까지 만든다
            r.level().setBlock(new Pos(cp.x() * 16 + 4, 100, cp.z() * 16 + 4), reg.defaultStateId("minecraft:diamond_block"), 3, 512);
        }).get();

        assertTrue(w.regenerateChunk(cp.x(), cp.z(), false, false).get(60, TimeUnit.SECONDS));
        ChunkColumn expected = NoiseWorldSource.overworld(777L, reg, registries).generateIsolated(cp.x(), cp.z());
        sched.region(rp).execute(() -> {
            ChunkColumn col = w.loadedChunk(cp.x(), cp.z());
            for (int y = col.minY(); y <= col.maxY(); y++)
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++)
                        if (col.getBlock(x, y, z) != expected.getBlock(x, y, z)) fail("(" + x + "," + y + "," + z + ") 가 새 생성과 다르다");
            for (int s = 0; s < col.sectionCount(); s++)
                for (int i = 0; i < 64; i++)
                    assertEquals(expected.sections()[s].getBiome(i & 3, i >> 4, (i >> 2) & 3), col.sections()[s].getBiome(i & 3, i >> 4, (i >> 2) & 3));
        }).get();

        // 재생성은 지금 걸린 월드젠 훅을 쓴다(플러그인을 고친 뒤 다시 만들기)
        BlockData gold = BlockData.of("minecraft:gold_block");
        GenHooks.attach(src, "test:regen", k -> new ChunkGenerationHooks() {
            @Override public void noise(GenerationChunk c) { c.setBlock(0, 250, 0, gold); }
        }, "minecraft:overworld", 777L, reg, registries);
        assertTrue(w.regenerateChunk(cp.x(), cp.z(), false, false).get(60, TimeUnit.SECONDS));
        sched.region(rp).execute(() ->
                assertEquals(reg.defaultStateId("minecraft:gold_block"), w.loadedChunk(cp.x(), cp.z()).getBlock(0, 250, 0))).get();
        gen.shutdown();
        sched.close();
    }
}
