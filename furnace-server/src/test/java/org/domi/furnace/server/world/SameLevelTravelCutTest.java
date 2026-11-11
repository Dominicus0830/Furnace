// PR #185 검토 R1 회귀(검토 재현 시험을 고친 경로로): 같은 레벨 이동(엔드 관문 — EndGatewayBlock.travelWithinLevel →
// DimensionTravel.withLoadedChunks)은 목적 월드 incoming 을 세지 않는다. Entity.moveAcross 가 떠나기 전 departing, 도착 뒤 settled 로
// 원천 월드가 세므로 holdForShutdown 은 출구 청크를 기다리는 이동이 도착한 뒤에 끝난다(그 뒤 저장해도 엔티티가 사라지지 않는다).
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.anvil.AnvilChunkCodec;
import org.domi.furnace.server.world.anvil.RegionFileStore;
import org.domi.furnace.server.world.gen.AnvilWorldSource;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SameLevelTravelCutTest {

    private static final BlockRegistry REG = BlockRegistry.load("blocks/1.21.1.txt");
    private static final List<String> BIOMES = RegistrySet.load("registries/1.21.1.txt").entries("minecraft:worldgen/biome");
    private static final int PLAINS = BIOMES.indexOf("minecraft:plains");
    private static final ChunkPos CP = new ChunkPos(0, 0);
    private static final ChunkPos FAR = new ChunkPos(40, 40);

    @TempDir Path tmp;

    @Test
    void cutWaitsForSameLevelTravelInFlight() throws Exception {
        ExecutorService io = Executors.newSingleThreadExecutor();
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        Path d = DimensionType.END.storageDir(tmp);
        AnvilWorldSource src = new AnvilWorldSource(tmp, new RegionFileStore(d),
                new AnvilChunkCodec(REG, ItemRegistry.load("items/1.21.1.txt"), BIOMES, PLAINS, ChunkColumn.MIN_Y, ChunkColumn.SECTION_COUNT),
                new SuperflatWorldSource(REG, PLAINS));
        src.entityStore(new RegionFileStore(d, "entities"));
        World end = new World(DimensionType.END, src, new ChunkWorkerPool(1), sched, 1, 4, 1, 2, 2, REG.directBits(), 7, io, io);
        end.setRegistries(REG, ItemRegistry.load("items/1.21.1.txt"));
        end.worldDir(tmp);
        Dimensions dims = new Dimensions();
        dims.register(end);
        end.setChunkForced(CP.x(), CP.z(), true);
        end.awaitChunk(CP);
        end.startTicking();
        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch arrived = new CountDownLatch(1);
        try {
            io.execute(() -> { try { gate.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });   // IO 레인을 잠깐 막아 출구 청크 준비가 날아가는 중이게
            // 관문 이동의 도착 쪽(EndGatewayBlock.travelWithinLevel 과 같은 호출) — 엔티티는 이 시점에 이미 원천에서 빠져 있다(moveAcross.depart)
            var travel = end.travelService();
            travel.departing();   // moveAcross 가 depart 직전에 부른다
            travel.withLoadedChunks(FAR.x() * 16 + 8, FAR.z() * 16 + 8, 0, lvl -> {
                travel.settled();   // moveAcross 도착 콜백의 finally
                arrived.countDown();
            });
            assertEquals(0, end.incomingTravels(), "같은 레벨 이동은 incoming 에 잡히지 않는다");
            // 기준점이 기다리는 동안 IO 를 풀어 준다(실제로는 레인이 스스로 비워진다)
            new Thread(() -> { try { Thread.sleep(300); } catch (InterruptedException e) { } gate.countDown(); }).start();
            boolean ok = World.holdForShutdown(List.of(end), null, null, System.nanoTime() + TimeUnit.SECONDS.toNanos(10));
            long leftAtCut = arrived.getCount();
            assertTrue(arrived.await(20, TimeUnit.SECONDS), "도착이 끝내 오지 않았다");
            assertTrue(ok, "기준점이 마감 안에 끝나야 한다");
            assertEquals(0, leftAtCut, "holdForShutdown 이 날아가는 같은 레벨 이동(관문)을 기다리지 않고 끝났다 — 이 뒤 저장하면 엔티티가 사라진다");
        } finally {
            gate.countDown();
            sched.close();
            io.shutdown();
            io.awaitTermination(10, TimeUnit.SECONDS);
        }
    }
}
