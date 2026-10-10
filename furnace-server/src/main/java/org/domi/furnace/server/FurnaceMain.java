// Furnace 서버 진입점. 설정 로드 → 스케줄러 기동 → 셧다운 훅 배선.
package org.domi.furnace.server;

import org.domi.furnace.server.config.Config;
import org.domi.furnace.server.config.ConfigLoader;
import org.domi.furnace.server.config.Eula;
import org.domi.furnace.server.config.ServerProperties;
import org.domi.furnace.server.config.ServerPropertiesFile;
import org.domi.furnace.server.players.GameProfileCache;
import org.domi.furnace.server.players.PlayerLists;
import org.domi.furnace.server.players.ProfileLookup;
import org.domi.furnace.server.net.NetworkServer;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.entity.EntityIdAllocator;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ItemRegistry;
import org.domi.furnace.server.world.World;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

public final class FurnaceMain {

    private static final Logger log = LoggerFactory.getLogger(FurnaceMain.class);

    public static void main(String[] args) throws InterruptedException {
        try {
            run(Options.parse(args));
        } catch (InterruptedException e) {
            throw e;
        } catch (Throwable t) {
            // 기동 실패 시 비데몬 스케줄러/Netty 스레드가 JVM을 붙잡아 좀비 프로세스가 되지 않게 비정상 종료한다.
            // (종료 훅이 이미 등록됐다면 exit가 훅을 돌려 flush한다.)
            log.error("기동 실패 — 종료합니다", t);
            System.exit(1);
        }
    }

    /** 바닐라 Main 의 명령줄 옵션 중 서버가 쓰는 것. */
    record Options(boolean help, boolean initSettings, int port, String world, String universe) {
        static Options parse(String[] args) {
            boolean help = false, init = false;
            int port = -1;
            String world = null, universe = ".";
            for (int i = 0; i < args.length; i++) {
                String a = args[i].startsWith("--") ? args[i].substring(2) : args[i];
                switch (a) {
                    case "help" -> help = true;
                    case "initSettings" -> init = true;
                    case "nogui" -> { }
                    case "port" -> port = Integer.parseInt(args[++i]);
                    case "world" -> world = args[++i];
                    case "universe" -> universe = args[++i];
                    default -> throw new IllegalArgumentException(args[i] + " is not a recognized option");
                }
            }
            return new Options(help, init, port, world, universe);
        }
    }

    private static void run(Options options) throws InterruptedException {
        if (options.help()) {
            System.err.println("Options: --help --initSettings --nogui --port <int> --universe <dir> --world <name>");
            return;
        }
        // 바닐라 Main 순서: server.properties 를 읽어 기본값을 채워 다시 쓰고 → eula.txt 확인.
        Path propertiesPath = Path.of("server.properties");
        ServerPropertiesFile settings = new ServerPropertiesFile(propertiesPath);
        settings.forceSave();
        Path eulaPath = Path.of("eula.txt");
        Eula eula = new Eula(eulaPath);
        if (options.initSettings()) {
            log.info("Initialized '{}' and '{}'", propertiesPath.toAbsolutePath(), eulaPath.toAbsolutePath());
            return;
        }
        if (!eula.hasAgreedToEULA()) {
            log.info("You need to agree to the EULA in order to run the server. Go to eula.txt for more info.");
            return;
        }
        ServerProperties props = settings.get();
        Path universe = Path.of(options.universe());
        var profileCache = new GameProfileCache(ProfileLookup.NONE, universe.resolve("usercache.json"));
        profileCache.setUsesAuthentication(props.onlineMode);
        // TODO(2-2): 빈 level-seed 의 임의 시드는 level.dat 에 저장돼야 재시작 후에도 유지된다.
        long seed = props.levelSeed.orElseGet(() -> new java.util.Random().nextLong());
        Path furnaceProps = Path.of("furnace.properties");
        for (String k : ConfigLoader.ignoredVanillaKeys(furnaceProps)) {
            log.warn("furnace.properties 의 '{}' 는 무시됩니다 — server.properties 값을 씁니다", k);
        }
        Config config = ConfigLoader.load(furnaceProps)
                .withServerProperties(props, options.port(), options.world(), seed);
        long startNanos = System.nanoTime();
        log.info("Starting minecraft server version 1.21.1");
        log.info("Loading properties");
        log.info("Default game type: {}", props.gameMode.name());
        log.info("Furnace 부팅: motd='{}', tickNanos={}", config.serverName(), config.tickNanos());
        log.info("Starting Minecraft server on {}:{}", props.serverIp.isEmpty() ? "*" : props.serverIp, config.port());
        if (!props.onlineMode) {
            log.warn("**** SERVER IS RUNNING IN OFFLINE/INSECURE MODE!");
            log.warn("The server will make no attempt to authenticate usernames. Beware.");
            log.warn("While this makes the game possible to play without internet access, it also opens up the ability for hackers to connect with any username they choose.");
            log.warn("To change this, set \"online-mode\" to \"true\" in the server.properties file.");
        }
        log.info("Preparing level \"{}\"", config.levelName());
        var playerLists = new PlayerLists(Path.of("."), settings);
        profileCache.save();

        FurnaceSchedulerImpl scheduler = new FurnaceSchedulerImpl(config.tickNanos(), config.regionThreads());
        BlockRegistry blockRegistry = BlockRegistry.load("blocks/1.21.1.txt");
        ItemRegistry itemRegistry = ItemRegistry.load("items/1.21.1.txt");
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");
        int plainsBiome = Math.max(0,
                registries.indexOf("minecraft:worldgen/biome", "minecraft:plains"));
        int biomeCount = registries.entries("minecraft:worldgen/biome").size();
        int biomeDirectBits = Math.max(1, 32 - Integer.numberOfLeadingZeros(biomeCount - 1));
        ChunkWorkerPool chunkPool = new ChunkWorkerPool(Runtime.getRuntime().availableProcessors());
        Path worldDir = universe.resolve(config.levelName());
        var regionStore = new org.domi.furnace.server.world.anvil.RegionFileStore(worldDir);
        var chunkCodec = new org.domi.furnace.server.world.anvil.AnvilChunkCodec(blockRegistry, plainsBiome, itemRegistry);
        var noiseSource = NoiseWorldSource.overworld(config.levelSeed(), blockRegistry, registries);
        var anvilSource = new org.domi.furnace.server.world.gen.AnvilWorldSource(
                worldDir, regionStore, chunkCodec, noiseSource);
        java.util.concurrent.ExecutorService ioPool =
                java.util.concurrent.Executors.newFixedThreadPool(config.ioThreads());
        int genThreads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));
        java.util.concurrent.ExecutorService genPool =
                java.util.concurrent.Executors.newFixedThreadPool(genThreads);
        World world = new World(anvilSource, chunkPool,
                scheduler, config.regionThreads(), config.regionSize(), config.viewDistance(),
                config.entityViewDistance(), blockRegistry.directBits(), biomeDirectBits, ioPool, genPool);
        world.worldDir(worldDir);
        var defaultSpawn = noiseSource.defaultSpawn();
        world.defaultSpawn(defaultSpawn);
        log.info("월드 시드={}, 기본 스폰=({}, {}, {})",
                config.levelSeed(), defaultSpawn.x(), defaultSpawn.y(), defaultSpawn.z());
        world.configurePersistence(config.autosaveTicks(), config.chunkEvictionGraceTicks());
        world.configureStreaming(config.spawnChunkRadius(), config.chunksPerTick());
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
                    fs.add(scheduler.regionThread(idx).execute(() -> world.flushThread(idx)));
                }
                // region 스레드가 멈춰 있어도 종료가 영원히 안 끝나지 않게 타임아웃을 둔다. 하나가 실패/지연돼도 나머지 정리는 계속한다.
                long flushDeadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
                for (var f : fs) {
                    try {
                        f.get(Math.max(1, flushDeadline - System.nanoTime()), java.util.concurrent.TimeUnit.NANOSECONDS);
                    } catch (java.util.concurrent.TimeoutException | java.util.concurrent.ExecutionException e) {
                        log.error("[STOP] region flush 실패/지연", e);
                    }
                }
                ioPool.shutdown();
                genPool.shutdown();
                ioPool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS);
                genPool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS);
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

        log.info("Done ({})! For help, type \"help\"",
                String.format(java.util.Locale.ROOT, "%.3fs", (System.nanoTime() - startNanos) / 1.0E9));
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
