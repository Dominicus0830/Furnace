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
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.World;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import org.domi.furnace.server.world.storage.DirectoryLock;
import org.domi.furnace.server.world.storage.LevelData;
import org.domi.furnace.server.world.storage.LevelStorage;
import java.util.concurrent.CountDownLatch;

public final class FurnaceMain {

    private static final Logger log = LoggerFactory.getLogger(FurnaceMain.class);
    /** 클라이언트 F3·level.dat ServerBrands 에 쓰이는 서버 브랜드. */
    public static final String SERVER_BRAND = "Furnace";

    public static void main(String[] args) throws InterruptedException {
        CrashReport.install(java.nio.file.Path.of("crash-reports"));
        try {
            run(Options.parse(args));
        } catch (InterruptedException e) {
            throw e;
        } catch (Throwable t) {
            // 기동 실패 시 비데몬 스케줄러/Netty 스레드가 JVM을 붙잡아 좀비 프로세스가 되지 않게 비정상 종료한다.
            // (종료 훅이 이미 등록됐다면 exit가 훅을 돌려 flush한다.)
            log.error("기동 실패 — 종료합니다", t);
            CrashReport.write(java.nio.file.Path.of("crash-reports"), "Failed to start server", t, Thread.currentThread());
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

    private static void run(Options options) throws Exception {
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
        if (!org.domi.furnace.server.world.anvil.Compression.configure(props.regionFileCompression)) {
            log.error("Invalid `region-file-compression` value `{}` in server.properties. Please use one of: deflate, gzip, none (lz4 는 Furnace 가 지원하지 않는다 — deflate 로 둔다)",
                    props.regionFileCompression);
        }
        if (!props.textFilteringConfig.isEmpty()) {
            log.warn("text-filtering-config 는 지원하지 않는다(원격 채팅 필터 서비스 미구현) — 무시한다");
        }
        Path universe = Path.of(options.universe());
        var profileCache = new GameProfileCache(ProfileLookup.NONE, universe.resolve("usercache.json"));
        profileCache.setUsesAuthentication(props.onlineMode);
        // 바닐라 Main: 월드 폴더를 잠그고 level.dat 을 읽는다. 없으면 server.properties 로 새 월드 설정을 만든다
        // (빈 level-seed 면 임의 시드 — level.dat 에 저장돼 이후 재시작에도 유지된다).
        String levelName = options.world() != null ? options.world() : props.levelName;
        Path worldDir = universe.resolve(levelName);
        LevelStorage storage;
        try {
            storage = LevelStorage.open(worldDir);
        } catch (DirectoryLock.LockException e) {
            log.error("Failed to start the minecraft server", e);
            return;
        }
        LevelData levelData;
        try {
            levelData = storage.loadLevelData();
        } catch (java.io.IOException e) {
            storage.close();
            return;
        }
        boolean newWorld = levelData == null;
        if (levelData == null) {
            log.info("No existing world data, creating new world");
            levelData = LevelData.create(props.levelName, props.levelSeed.orElseGet(() -> new java.util.Random().nextLong()),
                    props.generateStructures, false, props.gameMode, props.hardcore, props.difficulty,
                    props.initialEnabledPacks, List.of("bundle", "trade_rebalance"));
            if ("minecraft:flat".equals(props.levelType)) {
                levelData.useFlatOverworld(org.domi.furnace.server.world.gen.SuperflatWorldSource.settingsNbt(props.generatorSettings));
            }
        }
        levelData.setModdedInfo(SERVER_BRAND, true);
        long seed = levelData.seed;
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
        log.info("Generating keypair");
        java.security.KeyPair serverKeys = org.domi.furnace.server.net.auth.Crypt.generateKeyPair();
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
        var loginContext = org.domi.furnace.server.net.ServerLoginContext.fromProperties(props, serverKeys, playerLists,
                profileCache, Path.of("server-icon.png")).velocitySecret(config.velocitySecret());

        // region 은 동적(Folia 방식): 가까운 청크끼리 한 region, 떨어진 무리끼리는 작업 스레드 풀에서 병렬.
        FurnaceSchedulerImpl scheduler = new FurnaceSchedulerImpl(config.tickNanos(), config.regionThreads());
        // 롤백 스위치(기본 끔): 켜면 모든 차원이 region 워커 풀 하나를 공유하고 region-threads 는 서버 전체 워커 수가 된다
        boolean sharedRegionPool = Boolean.getBoolean("furnace.sharedRegionPool");
        org.domi.furnace.server.world.SharedScheduler regionPool = sharedRegionPool
                ? org.domi.furnace.server.world.SharedScheduler.startShared(config.regionThreads(), "furnace-region-") : null;
        BlockRegistry blockRegistry = BlockRegistry.load("blocks/1.21.1.txt");
        ItemRegistry itemRegistry = ItemRegistry.load("items/1.21.1.txt");
        org.domi.furnace.server.entity.PlayerData.itemRegistry(itemRegistry);
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");
        int plainsBiome = Math.max(0,
                registries.indexOf("minecraft:worldgen/biome", "minecraft:plains"));
        int biomeCount = registries.entries("minecraft:worldgen/biome").size();
        int biomeDirectBits = Math.max(1, 32 - Integer.numberOfLeadingZeros(biomeCount - 1));
        ChunkWorkerPool chunkPool = new ChunkWorkerPool(Runtime.getRuntime().availableProcessors());
        var dimension = NoiseWorldSource.Dimension.OVERWORLD;
        var regionStore = new org.domi.furnace.server.world.anvil.RegionFileStore(dimension.storageDir(worldDir), "region", props.syncChunkWrites);
        var chunkCodec = new org.domi.furnace.server.world.anvil.AnvilChunkCodec(blockRegistry, itemRegistry,
                registries.entries("minecraft:worldgen/biome"), plainsBiome, dimension.minY, dimension.sectionCount);
        var noiseSource = NoiseWorldSource.overworld(config.levelSeed(), blockRegistry, registries);
        applyWorldGenOptions(levelData, noiseSource);
        // 오버월드 생성기: level.dat 의 생성기가 minecraft:flat 이면 평지(바닐라 FlatLevelSource 층), 아니면 노이즈
        var generator = levelData.overworldGenerator();
        org.domi.furnace.server.world.gen.SuperflatWorldSource flatSource = null;
        if (generator != null && "minecraft:flat".equals(generator.tags().get("type"))) {
            var flatSettings = generator.contains("settings") ? generator.getCompound("settings") : null;
            String biome = flatSettings != null && flatSettings.tags().get("biome") instanceof String b ? b : "minecraft:plains";
            int biomeId = Math.max(0, registries.indexOf("minecraft:worldgen/biome", biome));
            flatSource = new org.domi.furnace.server.world.gen.SuperflatWorldSource(blockRegistry,
                    org.domi.furnace.server.world.gen.SuperflatWorldSource.layersOf(flatSettings), biomeId);
        }
        var anvilSource = new org.domi.furnace.server.world.gen.AnvilWorldSource(
                worldDir, regionStore, chunkCodec, flatSource != null ? flatSource : noiseSource);
        var entityStore = new org.domi.furnace.server.world.anvil.RegionFileStore(dimension.storageDir(worldDir), "entities", props.syncChunkWrites);
        anvilSource.entityStore(entityStore);
        var poiStore = new org.domi.furnace.server.world.anvil.RegionFileStore(dimension.storageDir(worldDir), "poi", props.syncChunkWrites);
        anvilSource.poiStore(poiStore);
        java.util.concurrent.ExecutorService ioPool =
                java.util.concurrent.Executors.newFixedThreadPool(config.ioThreads());
        // 생성 호출자 스레드 — 실제 계산은 ChunkPipeline 의 ForkJoin 풀(코어 수)에서 하고 여기서는 대부분 기다린다.
        // 동시에 진행하는 청크 수를 코어 수만큼 둬야 장식(청크마다 직렬 구간)이 겹쳐 돌며 풀이 놀지 않는다.
        int genThreads = Math.max(2, Runtime.getRuntime().availableProcessors());
        java.util.concurrent.ExecutorService genPool = java.util.concurrent.Executors.newFixedThreadPool(genThreads, r -> {
            Thread t = new Thread(r, "furnace-gen-wait");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
        World world = regionPool != null
                ? new World(org.domi.furnace.server.world.DimensionType.OVERWORLD, anvilSource, chunkPool, scheduler, regionPool,
                        config.regionSectionShift(), config.regionMergeRadius(), config.viewDistance(),
                        config.entityViewDistance(), blockRegistry.directBits(), biomeDirectBits, ioPool, genPool)
                : new World(anvilSource, chunkPool,
                        scheduler, config.regionThreads(), config.regionSectionShift(), config.regionMergeRadius(), config.viewDistance(),
                        config.entityViewDistance(), blockRegistry.directBits(), biomeDirectBits, ioPool, genPool);
        world.worldDir(worldDir);
        if (!levelData.initialized) {
            // 새 월드: 바닐라 setInitialSpawn 에 해당하는 생성기 기본 스폰을 정해 level.dat 에 남긴다.
            if (flatSource != null) {
                // 평지: 바닐라 setInitialSpawn 은 청크 (0,0) 에서 첫 서 있을 수 있는 칸 — 맨 위 층 바로 위
                levelData.spawnX = 0;
                levelData.spawnY = flatSource.surfaceY();
                levelData.spawnZ = 0;
                levelData.spawnAngle = 0f;
            } else {
                var spawn = noiseSource.defaultSpawn();
                levelData.spawnX = spawn.x();
                levelData.spawnY = spawn.y();
                levelData.spawnZ = spawn.z();
                levelData.spawnAngle = spawn.yaw();
            }
            levelData.initialized = true;
        }
        var defaultSpawn = new org.domi.furnace.server.world.SpawnPoint(
                levelData.spawnX, levelData.spawnY, levelData.spawnZ, levelData.spawnAngle);
        world.defaultSpawn(defaultSpawn);
        world.clock().restore(levelData.gameTime, levelData.dayTime, levelData.clearWeatherTime, levelData.rainTime,
                levelData.thunderTime, levelData.raining, levelData.thundering);
        final java.util.concurrent.atomic.AtomicReference<ServerCore> coreRef = new java.util.concurrent.atomic.AtomicReference<>();
        chunkCodec.gameTime(world::gameTime);
        final LevelData level = levelData;
        Runnable saveLevelData = () -> {
            synchronized (level) {
                ServerCore c = coreRef.get();
                if (c != null) {
                    c.writeLevelData();
                } else {
                    level.gameTime = world.gameTime();
                    level.dayTime = world.dayTime();
                }
                try {
                    storage.saveLevelData(level);
                } catch (java.io.IOException e) {
                    log.error("level.dat 저장 실패", e);
                }
            }
        };
        // 데이터 팩: 바닐라 내장 데이터(첫 실행 때 공식 서버 jar 에서 추출) + 기능 팩 + <월드>/datapacks.
        org.domi.furnace.server.data.DataPackManager dataPacks = null;
        try {
            Path vanillaRoot = org.domi.furnace.server.data.VanillaData.ensure(Path.of("."));
            dataPacks = org.domi.furnace.server.data.DataPackManager.start(vanillaRoot, worldDir, levelData,
                    org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt"), newWorld,
                    props.initialEnabledPacks, props.initialDisabledPacks);
        } catch (Exception e) {
            log.error("바닐라 데이터를 준비하지 못해 데이터 팩 없이 시작한다 — 태그는 내장 목록을 쓴다", e);
        }
        saveLevelData.run();
        log.info("월드 시드={}, 기본 스폰=({}, {}, {})",
                config.levelSeed(), defaultSpawn.x(), defaultSpawn.y(), defaultSpawn.z());
        world.configurePersistence(config.autosaveTicks(), config.chunkEvictionGraceTicks());
        world.configureStreaming(config.spawnChunkRadius(), config.chunksPerTick());
        world.setRegistries(blockRegistry, itemRegistry);
        EntityIdAllocator idAllocator = new EntityIdAllocator();
        world.entityIds(idAllocator);   // 플레이어와 다른 엔티티가 같은 id 공간을 쓴다
        // 다른 차원(바닐라 createLevels: 오버월드 → 네더 → 엔드). 차원마다 저장 폴더(DIM-1, DIM1)·생성기·region 이 따로이고,
        // 접속자·시계·엔티티 id 는 오버월드와 공유한다(Dimensions.register).
        org.domi.furnace.server.world.Dimensions dimensions = new org.domi.furnace.server.world.Dimensions();
        dimensions.register(world);
        java.util.List<org.domi.furnace.server.world.anvil.RegionFileStore> extraStores = new java.util.ArrayList<>();
        for (var type : java.util.List.of(org.domi.furnace.server.world.DimensionType.NETHER, org.domi.furnace.server.world.DimensionType.END)) {
            var dir = type.storageDir(worldDir);
            var store = new org.domi.furnace.server.world.anvil.RegionFileStore(dir, "region", props.syncChunkWrites);
            var codec = new org.domi.furnace.server.world.anvil.AnvilChunkCodec(blockRegistry, itemRegistry,
                    registries.entries("minecraft:worldgen/biome"), plainsBiome, type.minY(), type.sectionCount());
            var gen = NoiseWorldSource.create(type.gen(), config.levelSeed(), blockRegistry, registries);
            applyWorldGenOptions(levelData, gen);
            var src = new org.domi.furnace.server.world.gen.AnvilWorldSource(worldDir, store, codec, gen);
            var ents = new org.domi.furnace.server.world.anvil.RegionFileStore(dir, "entities", props.syncChunkWrites);
            var pois = new org.domi.furnace.server.world.anvil.RegionFileStore(dir, "poi", props.syncChunkWrites);
            src.entityStore(ents);
            src.poiStore(pois);
            extraStores.add(store);
            extraStores.add(ents);
            extraStores.add(pois);
            World w = regionPool != null
                    ? new World(type, src, chunkPool, scheduler, regionPool, config.regionSectionShift(), config.regionMergeRadius(),
                            config.viewDistance(), config.entityViewDistance(), blockRegistry.directBits(), biomeDirectBits, ioPool, genPool)
                    : new World(type, src, chunkPool, scheduler, config.regionThreads(), config.regionSectionShift(), config.regionMergeRadius(),
                            config.viewDistance(), config.entityViewDistance(), blockRegistry.directBits(), biomeDirectBits, ioPool, genPool);
            w.worldDir(worldDir);
            w.defaultSpawn(defaultSpawn);
            codec.gameTime(w::gameTime);
            w.configurePersistence(config.autosaveTicks(), config.chunkEvictionGraceTicks());
            w.configureStreaming(config.spawnChunkRadius(), config.chunksPerTick());
            w.setRegistries(blockRegistry, itemRegistry);
            if (type == org.domi.furnace.server.world.DimensionType.END) {   // ServerLevel: 엔드는 드래곤 전투를 가진다(level.dat DragonFight)
                w.enableDragonFight(level.dragonFightData());
                w.dragonFightSaver(d -> level.dragonFight = d);
            }
            dimensions.register(w);
        }
        dimensions.levelEnabled(k -> !k.equals(org.domi.furnace.server.world.DimensionType.NETHER.levelKey()) || props.allowNether);
        for (World w : dimensions.all()) w.startTicking();
        // 종료 훅은 등록 순: 월드들(자기 큐 분리) → 공유 풀 → fallback → global
        if (regionPool != null) scheduler.addCloseHook(regionPool::close);
        log.info("[SCHED] region 워커 {}개({}, 차원 {}개)", config.regionThreads(),
                regionPool != null ? "서버 전체 공유" : "차원별", dimensions.all().size());
        // 명령·서버 상태 핸들. /stop 은 종료 훅을 도는 별도 스레드에서 끝낸다(스케줄러 스레드에서 exit 하면 훅과 교착).
        ServerCore core = new ServerCore(settings, playerLists, profileCache, world, levelData, scheduler);
        core.dimensions(dimensions);
        if (dataPacks != null) core.dataPacks(dataPacks);
        coreRef.set(core);
        loginContext.core(core);
        core.servicesKeys().fetchAsync();
        core.onHalt(() -> new Thread(() -> System.exit(0), "Server Shutdown Thread").start());
        // 플러그인: 불러오기(onLoad) — 월드 준비 뒤·접속을 받기 전에 켠다(Bukkit POSTWORLD 와 같은 시점).
        org.domi.furnace.api.Furnace.setServer(core.plugins());
        core.plugins().pluginsImpl().loadAll();
        // 바닐라에 없는 명령 — 바닐라 트리 대조 대상(BuiltinCommands)과 따로 등록한다
        org.domi.furnace.server.command.builtin.FurnaceCommands.register(core.commands().getDispatcher(), core.plugins());
        org.domi.furnace.server.command.builtin.FurnaceAdminCommands.register(core.commands().getDispatcher());
        core.onSaveAll(() -> {
            for (World w : dimensions.all()) for (var f : w.flushAll()) f.join();
            saveLevelData.run();
        });
        NetworkServer network = new NetworkServer(
                config.port(), config.compressionThreshold(), loginContext, scheduler,
                world, idAllocator);
        network.useNativeTransport(props.useNativeTransport);
        CountDownLatch shutdown = new CountDownLatch(1);
        // 운영·관측 서비스(RCON·Query·워치독): 전용 IO/데몬 스레드. 종료 훅이 가장 먼저 닫는다.
        java.util.List<AutoCloseable> remoteServices = new java.util.concurrent.CopyOnWriteArrayList<>();

        // start() 전에 훅 등록 — start()가 던져도 non-daemon 스케줄러 스레드가 JVM을 붙잡지 않게.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("종료 중 — dirty 청크/플레이어 flush");
            // CraftBukkit 처럼 플러그인을 먼저 끈다(플레이어가 아직 접속해 있을 때 onDisable 이 돈다).
            try {
                core.plugins().pluginsImpl().disableAll();
            } catch (Throwable t) {
                log.error("[STOP] 플러그인 끄기 실패", t);
            }
            for (AutoCloseable c : remoteServices) {
                try {
                    c.close();
                } catch (Exception e) {
                    log.warn("[STOP] 원격 서비스 닫기 실패", e);
                }
            }
            network.stop();
            try {
                // 각 region 스레드서 dirty 청크 + 접속 플레이어 flush(scheduler 살아있을 때).
                java.util.List<java.util.concurrent.CompletableFuture<Void>> fs = new java.util.ArrayList<>();
                for (World w : dimensions.all()) fs.addAll(w.flushAll());
                // region 스레드가 멈춰 있어도 종료가 영원히 안 끝나지 않게 타임아웃을 둔다. 하나가 실패/지연돼도 나머지 정리는 계속한다.
                long flushDeadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
                for (var f : fs) {
                    try {
                        f.get(Math.max(1, flushDeadline - System.nanoTime()), java.util.concurrent.TimeUnit.NANOSECONDS);
                    } catch (java.util.concurrent.TimeoutException | java.util.concurrent.ExecutionException e) {
                        log.error("[STOP] region flush 실패/지연", e);
                    }
                }
                // 플레이어 데이터(퇴장 저장·통계·발전 과제)는 전용 직렬 실행기에서 돈다 — 데몬 스레드라 여기서 비운다
                if (!org.domi.furnace.server.entity.PlayerData.awaitIo(30, java.util.concurrent.TimeUnit.SECONDS)) {
                    log.error("[STOP] 플레이어 데이터 저장이 30초 안에 끝나지 않았다");
                }
                ioPool.shutdown();
                genPool.shutdown();
                ioPool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS);
                genPool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS);
                long failed = 0;
                for (World w : dimensions.all()) failed += w.saveFailures();
                if (failed > 0) log.error("[STOP] 재시도 끝에 실패한 저장 {}건 — 해당 청크/엔티티는 디스크에 최신이 아닐 수 있다", failed);
                else log.info("[STOP] 저장 실패 0건");
                regionStore.closeAll();
                entityStore.closeAll();
                poiStore.closeAll();
                for (var st : extraStores) st.closeAll();
                saveLevelData.run();
                storage.close();
            } catch (Exception e) {
                log.error("[STOP] flush 실패", e);
            }
            chunkPool.shutdown();
            scheduler.close();
            core.plugins().pluginsImpl().closeAll();
            core.plugins().close();
            shutdown.countDown();
        }, "furnace-shutdown"));

        prepareSpawnArea(world, defaultSpawn, config.spawnChunkRadius());
        core.plugins().pluginsImpl().enableAll();
        core.loadFunctions();   // 플러그인(onLoad·onEnable)·Furnace 명령이 모두 등록된 뒤 함수를 파싱한다 — 그 명령을 쓰는 함수가 시작 때 빠지지 않게
        network.start();

        // 바닐라 자동 저장 주기마다 level.dat 도 저장한다(/save-off 면 건너뜀).
        scheduler.global().executeDelayed(new Repeating(scheduler, () -> {
            if (world.autosaveEnabled()) saveLevelData.run();
        }, config.autosaveTicks()), config.autosaveTicks());
        ConsoleInput.start(core);
        // 전역 틱: 시간·날씨(바닐라 ServerLevel.tick 의 전역 부분).
        scheduler.global().executeDelayed(new Repeating(scheduler, core::scheduledTick, 1), 1);

        startRemoteServices(core, props, config, remoteServices);

        // TPS 하트비트: 5초마다 측정 TPS 로그(M0 가시성).
        scheduler.global().executeDelayed(new TpsHeartbeat(scheduler, world), 100);

        log.info("Done ({})! For help, type \"help\"",
                String.format(java.util.Locale.ROOT, "%.3fs", (System.nanoTime() - startNanos) / 1.0E9));
        core.events().call(new org.domi.furnace.api.event.server.ServerLoadEvent());
        shutdown.await();
    }

    /** 바닐라 DedicatedServer.initServer 끝: enable-query → enable-rcon → 워치독(max-tick-time &gt; 0). */
    private static void startRemoteServices(ServerCore core, ServerProperties props, Config config, java.util.List<AutoCloseable> out) {
        if (props.enableQuery) {
            log.info("Starting GS4 status listener");
            var q = org.domi.furnace.server.remote.QueryServer.create(props.serverIp, props.queryPort, new QueryInfo(core, props, config, hostIp(props)));
            if (q != null) out.add(q);
        }
        if (props.enableRcon) {
            log.info("Starting remote control listener");
            var r = org.domi.furnace.server.remote.RconServer.create(props.serverIp, props.rconPort, props.rconPassword, core::runRconCommand);
            if (r != null) out.add(r);
        }
        if (ServerWatchdog.enabled(props.maxTickTime)) {
            var w = new ServerWatchdog(props.maxTickTime, System::nanoTime, core::watchdogProbes,
                    ServerWatchdog.crashHandler(Path.of("crash-reports"), ServerWatchdog::exitOrHalt));
            w.start();
            out.add(w);
        }
    }

    /** 기동 때 한 번만 계산한다(질의마다 DNS 조회를 하지 않게). */
    private static String hostIp(ServerProperties props) {
        if (!props.serverIp.isEmpty()) return props.serverIp;
        try {
            return java.net.InetAddress.getLocalHost().getHostAddress();
        } catch (java.net.UnknownHostException e) {
            return "0.0.0.0";
        }
    }

    /** Query 응답이 읽는 서버 정보(기동 때 값 + 접속자 수·이름은 매번). */
    private record QueryInfo(ServerCore core, ServerProperties props, Config config, String hostIp) implements org.domi.furnace.server.remote.QueryHandler.Info {
        @Override public String motd() { return props.motd; }
        @Override public String levelName() { return config.levelName(); }
        @Override public String version() { return "1.21.1"; }
        @Override public String plugins() { return ""; }
        @Override public int playerCount() { return core.players().size(); }
        @Override public int maxPlayers() { return props.maxPlayers; }
        @Override public int hostPort() { return config.port(); }
        @Override public String hostIp() { return hostIp; }
        @Override public String[] playerNames() { return core.players().stream().map(p -> p.name()).toArray(String[]::new); }
    }

    /** period 틱마다 task 를 돌리고 자신을 재예약한다. */
    private record Repeating(FurnaceSchedulerImpl scheduler, Runnable task, int period) implements Runnable {
        @Override
        public void run() {
            try {
                task.run();
            } finally {
                scheduler.global().executeDelayed(this, period);
            }
        }
    }

    /** 5초(100틱)마다 region 수를 로그하고 자신을 재예약하는 하트비트. */
    private record TpsHeartbeat(FurnaceSchedulerImpl scheduler, World world) implements Runnable {
        @Override
        public void run() {
            log.debug("regions = {}", world.regionCount());
            scheduler.global().executeDelayed(this, 100);
        }
    }

    /**
     * 바닐라 MinecraftServer.prepareLevels — "Done" 전에 스폰 청크를 만들어 둔다. 첫 접속자가 빈 하늘에서 생성을 기다리지 않고,
     * 생성기 캐시(구조물 시작점·지형)도 미리 데워진다. 진행률 로그는 바닐라 LoggerChunkProgressListener 처럼 0.5초마다.
     */
    private static void prepareSpawnArea(World world, org.domi.furnace.server.world.SpawnPoint spawn, int radius) {
        log.info("Preparing start region for dimension minecraft:overworld");
        long t0 = System.nanoTime();
        org.domi.furnace.server.world.ChunkPos c = org.domi.furnace.server.world.ChunkPos.ofBlock(spawn.x(), spawn.z());
        java.util.List<java.util.concurrent.CompletableFuture<byte[]>> fs = new java.util.ArrayList<>();
        for (int dz = -radius; dz <= radius; dz++)
            for (int dx = -radius; dx <= radius; dx++)
                fs.add(world.serializedChunkBody(new org.domi.furnace.server.world.ChunkPos(c.x() + dx, c.z() + dz)));
        java.util.concurrent.CompletableFuture<Void> all = java.util.concurrent.CompletableFuture.allOf(fs.toArray(new java.util.concurrent.CompletableFuture[0]));
        while (true) {
            try {
                all.get(500, java.util.concurrent.TimeUnit.MILLISECONDS);
                break;
            } catch (java.util.concurrent.TimeoutException e) {
                long done = fs.stream().filter(java.util.concurrent.CompletableFuture::isDone).count();
                log.info("Preparing spawn area: {}%", done * 100 / fs.size());
            } catch (Exception e) {
                log.warn("스폰 청크 준비 실패 — 접속 시 다시 만든다", e);
                break;
            }
        }
        log.info("Time elapsed: {} ms", (System.nanoTime() - t0) / 1_000_000);
    }

    /**
     * 바닐라 WorldOptions.generateStructures(level.dat WorldGenSettings.generate_features): 끄면 STRUCTURE_STARTS 단계가 시작점을 만들지 않는다
     * (ChunkStatusTasks.generateStructureStarts). 모든 차원에 같은 값. 이미 저장된 청크의 구조물은 그대로다.
     */
    static void applyWorldGenOptions(LevelData levelData, NoiseWorldSource source) {
        source.structureEngine().setStructuresEnabled(levelData.generateFeatures);
    }
}
