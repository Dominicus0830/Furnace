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
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");
        int plainsBiome = Math.max(0,
                registries.indexOf("minecraft:worldgen/biome", "minecraft:plains"));
        int biomeCount = registries.entries("minecraft:worldgen/biome").size();
        int biomeDirectBits = Math.max(1, 32 - Integer.numberOfLeadingZeros(biomeCount - 1));
        ChunkWorkerPool chunkPool = new ChunkWorkerPool(Runtime.getRuntime().availableProcessors());
        World world = new World(new SuperflatWorldSource(blockRegistry, plainsBiome), chunkPool,
                scheduler, config.regionThreads(), config.regionSize(), config.viewDistance(),
                blockRegistry.directBits(), biomeDirectBits);
        scheduler.setRegionTick(world::tickThread);
        EntityIdAllocator idAllocator = new EntityIdAllocator();
        NetworkServer network = new NetworkServer(
                config.port(), config.compressionThreshold(), config.serverName(), scheduler,
                world, idAllocator);
        CountDownLatch shutdown = new CountDownLatch(1);

        // start() 전에 훅 등록 — start()가 던져도 non-daemon 스케줄러 스레드가 JVM을 붙잡지 않게.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("종료 중...");
            network.stop();
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

    /** 5초(100틱)마다 region TPS를 로그하고 자신을 재예약하는 하트비트. */
    private record TpsHeartbeat(FurnaceSchedulerImpl scheduler) implements Runnable {
        @Override
        public void run() {
            log.info("region TPS = {}", String.format("%.1f", scheduler.regionThread().measuredTps()));
            scheduler.global().executeDelayed(this, 100);
        }
    }
}
