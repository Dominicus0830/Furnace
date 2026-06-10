// Furnace 서버 진입점. 설정 로드 → 스케줄러 기동 → 셧다운 훅 배선.
package org.domi.furnace.server;

import org.domi.furnace.server.config.Config;
import org.domi.furnace.server.config.ConfigLoader;
import org.domi.furnace.server.net.NetworkServer;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.entity.EntityIdAllocator;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ItemRegistry;
import org.domi.furnace.server.world.World;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

public final class FurnaceMain {

    private static final Logger log = LoggerFactory.getLogger(FurnaceMain.class);

    public static void main(String[] args) throws InterruptedException {
        Config config = ConfigLoader.load(Path.of("furnace.properties"));
        log.info("Furnace 부팅: name='{}', tickNanos={}", config.serverName(), config.tickNanos());

        FurnaceSchedulerImpl scheduler = new FurnaceSchedulerImpl(config.tickNanos(), config.regionThreads());
        BlockRegistry blockRegistry = BlockRegistry.load("blocks/1.21.1.txt");
        ItemRegistry itemRegistry = ItemRegistry.load("items/1.21.1.txt");
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");
        int plainsBiome = Math.max(0,
                registries.indexOf("minecraft:worldgen/biome", "minecraft:plains"));
        int biomeCount = registries.entries("minecraft:worldgen/biome").size();
        int biomeDirectBits = Math.max(1, 32 - Integer.numberOfLeadingZeros(biomeCount - 1));
        ChunkWorkerPool chunkPool = new ChunkWorkerPool(Runtime.getRuntime().availableProcessors());
        Path worldDir = Path.of(config.levelName());
        var regionStore = new org.domi.furnace.server.world.anvil.RegionFileStore(worldDir);
        var chunkCodec = new org.domi.furnace.server.world.anvil.AnvilChunkCodec(blockRegistry, plainsBiome);
        var superflat = new SuperflatWorldSource(blockRegistry, plainsBiome);
        var anvilSource = new org.domi.furnace.server.world.gen.AnvilWorldSource(
                worldDir, regionStore, chunkCodec, superflat);
        java.util.concurrent.ExecutorService ioPool =
                java.util.concurrent.Executors.newFixedThreadPool(config.ioThreads());
        World world = new World(anvilSource, chunkPool,
                scheduler, config.regionThreads(), config.regionSize(), config.viewDistance(),
                config.entityViewDistance(), blockRegistry.directBits(), biomeDirectBits, ioPool);
        world.worldDir(worldDir);
        world.configurePersistence(config.autosaveTicks(), config.chunkEvictionGraceTicks());
        world.setRegistries(blockRegistry, itemRegistry);
        scheduler.setRegionTick(world::tickThread);
        EntityIdAllocator idAllocator = new EntityIdAllocator();
        NetworkServer network = new NetworkServer(
                config.port(), config.compressionThreshold(), config.serverName(), scheduler,
                world, idAllocator);
        CountDownLatch shutdown = new CountDownLatch(1);

        // start() 전에 훅 등록 — start()가 던져도 non-daemon 스케줄러 스레드가 JVM을 붙잡지 않게.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("종료 중 — dirty 청크/플레이어 flush");
            network.stop();
            try {
                // 각 region 스레드서 dirty 청크 + 접속 플레이어 flush(scheduler 살아있을 때).
                java.util.List<java.util.concurrent.CompletableFuture<Void>> fs = new java.util.ArrayList<>();
                for (int i = 0; i < config.regionThreads(); i++) {
                    final int idx = i;
                    fs.add(scheduler.region(regionPosForThread(idx, config)).execute(() -> world.flushThread(idx)));
                }
                for (var f : fs) f.get();
                ioPool.shutdown();
                ioPool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS);
                regionStore.closeAll();
            } catch (Exception e) {
                log.error("[STOP] flush 실패", e);
            }
            chunkPool.shutdown();
            scheduler.close();
            shutdown.countDown();
        }, "furnace-shutdown"));

        network.start();

        // TPS 하트비트: 5초마다 측정 TPS 로그(M0 가시성).
        scheduler.global().executeDelayed(new TpsHeartbeat(scheduler), 100);

        log.info("Furnace 기동 완료. Ctrl+C로 종료.");
        shutdown.await();
    }

    /** idx 스레드가 소유하는 RegionPos 하나(flush 타겟팅용). */
    private static org.domi.furnace.api.sched.RegionPos regionPosForThread(int idx, Config cfg) {
        for (int x = 0; x < cfg.regionThreads() * 2 + 1; x++) {
            org.domi.furnace.api.sched.RegionPos rp = new org.domi.furnace.api.sched.RegionPos(x, 0);
            if (org.domi.furnace.server.world.RegionGrid.threadIndex(rp, cfg.regionThreads()) == idx) return rp;
        }
        return new org.domi.furnace.api.sched.RegionPos(idx, 0);
    }

    /** 5초(100틱)마다 region TPS를 로그하고 자신을 재예약하는 하트비트. */
    private record TpsHeartbeat(FurnaceSchedulerImpl scheduler) implements Runnable {
        @Override
        public void run() {
            log.info("region TPS = {}", String.format("%.1f", scheduler.regionThread().measuredTps()));
            scheduler.global().executeDelayed(this, 100);
        }
    }
}
