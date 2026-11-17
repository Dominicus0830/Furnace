// 다중 월드(W1): 실행 중에 추가 월드를 올리고 내린다(Bukkit createWorld/unloadWorld 에 대응). 기본 세 차원도 같은 조립기(build)로 만든다.
// 추가 월드는 서버 폴더의 <이름>/ 에 Paper 와 같은 배치로 둔다(NORMAL = <이름>/region, NETHER = <이름>/DIM-1/region, THE_END = <이름>/DIM1/region).
// 시계·날씨·게임 규칙·접속자·엔티티 id 는 오버월드와 공유한다(바닐라 데이터 팩 차원과 같다 — Dimensions.register).
// 만들기·내리기는 전용 스레드 하나에서 차례로 돈다(같은 이름의 만들기·내리기가 엇갈리지 않게). region 작업 스레드는 월드마다 따로 뜨고 내릴 때 닫는다.
package org.domi.furnace.server.world;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.sched.FurnaceScheduler;
import org.domi.furnace.api.world.WorldCreator;
import org.domi.furnace.api.world.WorldEnvironment;
import org.domi.furnace.api.world.WorldGenerator;
import org.domi.furnace.server.config.Config;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.anvil.AnvilChunkCodec;
import org.domi.furnace.server.world.anvil.RegionFileStore;
import org.domi.furnace.server.world.gen.AnvilWorldSource;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.domi.furnace.server.world.gen.DebugWorldSource;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.domi.furnace.server.world.gen.WorldSource;
import org.domi.furnace.server.world.storage.BukkitWorldImport;
import org.domi.furnace.server.world.storage.LevelData;
import org.domi.furnace.server.world.storage.LevelStorage;
import org.domi.furnace.server.i18n.Lang;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WorldManager {

    private static final Logger log = LoggerFactory.getLogger("Worlds");

    /** 월드를 조립하는 데 드는 서버 공용 부품(FurnaceMain 이 한 번 만든다). regionPool 은 공유 풀 스위치가 꺼져 있으면 null. */
    public record Parts(Path universe, BlockRegistry blocks, ItemRegistry items, RegistrySet registries, int plainsBiome, int biomeDirectBits,
                        ChunkWorkerPool chunkPool, FurnaceScheduler scheduler, SharedScheduler regionPool, Config config, boolean syncChunkWrites,
                        ExecutorService ioPool, ExecutorService genPool) {}

    /** 조립한 월드와 닫아야 할 저장소들(region·entities·poi). */
    public record Built(World world, List<RegionFileStore> stores) {
        public void closeStores() {
            for (RegionFileStore s : stores) {
                try {
                    s.closeAll();
                } catch (Exception e) {
                    log.error(Lang.t("world.store-close-failed", s.regionDir()), e);
                }
            }
        }
    }

    /** 서버와 이어 주는 고리(ServerCore·플러그인 쪽). 시험은 NONE 에서 필요한 것만 바꾼다. */
    public interface Hooks {
        /** 등록 직후(틱 켜기 전): 레벨 서비스·/tick 상태를 붙인다. */
        default void attach(World w) {}

        /** 틱을 켠 뒤 — 전역 스레드에서 부른다(WorldLoadEvent). */
        default void loaded(World w) {}

        /** 내리기 전 — 전역 스레드에서 부른다. false 면 내리지 않는다(WorldUnloadEvent 취소). */
        default boolean beforeUnload(World w, boolean save) { return true; }

        /** 목록에서 뗀 직후(플러그인 월드 손잡이 걷기). */
        default void detached(World w) {}

        /** 플러그인이 등록한 차원 타입(W2) — 없거나 꺼졌으면 null. */
        default org.domi.furnace.api.worldgen.DimensionTypeDefinition pluginDimensionType(String key) { return null; }

        /** 플러그인 월드젠 단계 훅(W3) — 없거나 꺼졌으면 null. 생성 스레드에서 불린다. */
        default org.domi.furnace.api.worldgen.ChunkGenerationHooks generationHooks(String key) { return null; }

        Hooks NONE = new Hooks() {};
    }

    /**
     * 바이옴 공급자로 만든 월드를 공급자 없이 저장된 설명서 그대로 열려고 했다(T-210). 공급자 객체는 저장되지 않으므로, 그 공급자를 가진 플러그인이
     * 다시 만들 때(createWorld + 공급자) 열린다. 서버가 스스로 여는 길(worlds.autoload·/worlds load)은 이 예외로 그 월드를 건너뛴다.
     */
    public static final class NeedsBiomeProviderException extends IllegalStateException {
        private final String world;
        private final String provider;

        public NeedsBiomeProviderException(String world, String provider) {
            super("world '" + world + "' was created with the biome provider " + provider
                    + " - it opens when the plugin that owns the provider creates it again (createWorld with that provider)");
            this.world = world;
            this.provider = provider;
        }

        public String world() { return world; }

        public String provider() { return provider; }
    }

    /**
     * 월드젠 훅 객체로 만든 월드를 객체 없이 저장된 설명서 그대로 열려고 했다(T-217 — Bukkit ChunkGenerator 월드 등). 객체는 저장되지 않으므로,
     * 그 객체를 가진 플러그인이 다시 만들 때 열린다. 서버가 스스로 여는 길(worlds.autoload·/worlds load)은 이 예외로 그 월드를 건너뛴다.
     */
    public static final class NeedsGenerationHooksException extends IllegalStateException {
        private final String world;
        private final String hooks;

        public NeedsGenerationHooksException(String world, String hooks) {
            super("world '" + world + "' was created with the chunk generator " + hooks
                    + " - it opens when the plugin that owns the generator creates it again (createWorld with that generator)");
            this.world = world;
            this.hooks = hooks;
        }

        public String world() { return world; }

        public String hooks() { return hooks; }
    }

    /** 실행 중인 추가 월드 하나. */
    private record Extra(String name, World world, LevelStorage storage, LevelData levelData, Built built, WorldCreator creator) {}

    private final Parts parts;
    private final Dimensions dimensions;
    private final LevelData mainLevel;
    private final String mainLevelName;
    private volatile Hooks hooks = Hooks.NONE;
    private final Map<String, Extra> extras = new ConcurrentHashMap<>();   // 이름 → 월드
    private final ExecutorService worker;

    public WorldManager(Parts parts, Dimensions dimensions, LevelData mainLevel, String mainLevelName) {
        this.parts = parts;
        this.dimensions = dimensions;
        this.mainLevel = mainLevel;
        this.mainLevelName = mainLevelName;
        this.worker = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "furnace-worlds");
            t.setDaemon(true);
            return t;
        });
    }

    public void hooks(Hooks h) { this.hooks = h == null ? Hooks.NONE : h; }

    private volatile java.util.function.Supplier<CompletableFuture<Void>> saveBarrier = () -> CompletableFuture.completedFuture(null);

    /**
     * 진행 중 /save-all 이 끝나면 완료되는 future(저장 스레드에 넣은 빈 작업). 월드를 목록에서 뗀 뒤 저장소를 닫기 전에 기다린다 —
     * 저장 스레드가 이 월드를 저장하는 중에 닫히면 닫힌 저장소에 기록이 다시 핸들을 연다(PR #278 검토 R2).
     */
    public void saveBarrier(java.util.function.Supplier<CompletableFuture<Void>> barrier) {
        this.saveBarrier = barrier == null ? () -> CompletableFuture.completedFuture(null) : barrier;
    }

    public Dimensions dimensions() { return dimensions; }

    // ------------------------------------------------------------------ 조립(기본 차원과 추가 월드 공용)

    /** 차원 월드 하나를 조립한다 — 저장소(region·entities·poi), 청크 코덱, 생성기 위의 Anvil 원천, region 스케줄러. 틱은 켜지 않는다. */
    public Built build(DimensionType type, Path worldDir, WorldSource generator) {
        Path dir = type.storageDir(worldDir);
        var store = new RegionFileStore(dir, "region", parts.syncChunkWrites());
        var codec = new AnvilChunkCodec(parts.blocks(), parts.items(), parts.registries().entries("minecraft:worldgen/biome"),
                parts.plainsBiome(), type.minY(), type.sectionCount());
        var src = new AnvilWorldSource(worldDir, store, codec, generator);
        var ents = new RegionFileStore(dir, "entities", parts.syncChunkWrites());
        var pois = new RegionFileStore(dir, "poi", parts.syncChunkWrites());
        src.entityStore(ents);
        src.poiStore(pois);
        Config c = parts.config();
        World w = parts.regionPool() != null
                ? new World(type, src, parts.chunkPool(), parts.scheduler(), parts.regionPool(), c.regionSectionShift(), c.regionMergeRadius(),
                        c.viewDistance(), c.entityViewDistance(), parts.blocks().directBits(), parts.biomeDirectBits(), parts.ioPool(), parts.genPool())
                : new World(type, src, parts.chunkPool(), parts.scheduler(), c.regionThreads(), c.regionSectionShift(), c.regionMergeRadius(),
                        c.viewDistance(), c.entityViewDistance(), parts.blocks().directBits(), parts.biomeDirectBits(), parts.ioPool(), parts.genPool());
        w.worldDir(worldDir);
        codec.gameTime(w::gameTime);
        w.configurePersistence(c.autosaveTicks(), c.chunkEvictionGraceTicks());
        w.configureStreaming(c.spawnChunkRadius(), c.chunksPerTick());
        w.setRegistries(parts.blocks(), parts.items());
        return new Built(w, List.of(store, ents, pois));
    }

    // ------------------------------------------------------------------ 임시 월드(T-203)

    private final java.util.concurrent.atomic.AtomicLong scratchIds = new java.util.concurrent.atomic.AtomicLong();

    /**
     * 임시 월드(T-203 — Paper 플러그인이 {@code new ServerLevel(…)} 로 만드는 따로 떨어진 레벨, FAWE {@code //regen}): original 과 같은 차원 타입·생성 설정에
     * 시드만 seed 인 <b>처음 상태의</b> 생성기로 청크를 만든다. 디스크에 쓰지 않고(Anvil 원천 없음), 게임 틱을 켜지 않으며(region 은 작업 큐·청크 로드·
     * 공개 스냅샷만 — 무작위·예약 틱·엔티티가 돌지 않는다, Paper 의 임시 레벨도 {@code MinecraftServer#levels} 에 없어 틱을 돌지 않는다),
     * {@link Dimensions} 에 넣지 않는다(접속자·명령·포털·월드 목록에 안 보인다). 레벨 서비스를 붙이지 않아 플러그인 이벤트도 없다(결정 D1).
     * 레벨 키는 겹치지 않는 {@code furnace:scratch/<n>}, 이름은 name. 아무 스레드 — 디스크·잠금이 없어 그 자리에서 끝난다. 다 쓰면 {@link #closeScratch}.
     */
    public World createScratch(World original, long seed, String name) { return createScratch(original, seed, name, null); }

    /** {@link #createScratch(World, long, String)} 에 바이옴을 biomes 가 정하는 것(null 이면 원래 생성기의 바이옴 원천). 노이즈 생성기만. */
    public World createScratch(World original, long seed, String name, org.domi.furnace.api.worldgen.BiomeProvider biomes) {
        if (original == null || original.isUnloaded()) throw new IllegalArgumentException("the original world is not loaded");
        WorldSource os = original.worldSource();
        if (os instanceof AnvilWorldSource a) os = a.base();
        else if (os instanceof org.domi.furnace.server.world.gen.ScratchWorldSource t) os = t.base();
        WorldSource gen = new org.domi.furnace.server.world.gen.ScratchWorldSource(os.freshCopy(seed, biomes));   // 못 하는 생성기면 UnsupportedOperationException
        DimensionType ot = original.dimensionType();
        DimensionType type = ot.forLevel("furnace:scratch/" + scratchIds.incrementAndGet(), ot.gen());
        Config c = parts.config();
        World w = parts.regionPool() != null
                ? new World(type, gen, parts.chunkPool(), parts.scheduler(), parts.regionPool(), c.regionSectionShift(), c.regionMergeRadius(),
                        c.viewDistance(), c.entityViewDistance(), parts.blocks().directBits(), parts.biomeDirectBits(), parts.ioPool(), parts.genPool())
                : new World(type, gen, parts.chunkPool(), parts.scheduler(), c.regionThreads(), c.regionSectionShift(), c.regionMergeRadius(),
                        c.viewDistance(), c.entityViewDistance(), parts.blocks().directBits(), parts.biomeDirectBits(), parts.ioPool(), parts.genPool());
        w.markScratch(name);
        w.levelKind(original.isFlatLevel(), original.isDebugLevel());
        w.seed(seed);
        w.generatesStructures(original.generatesStructures());
        w.setRegistries(parts.blocks(), parts.items());
        w.defaultSpawn(original.defaultSpawn());
        // 접속자 목록·시계·엔티티 id 는 나누지 않는다 — 틱이 없어 쓰지 않고, 생성 때 생긴 엔티티(버려진다)가 서버 쪽 목록에 닿지 않게
        return w;
    }

    /** 임시 월드를 버린다(아무 스레드, 두 번 불러도 된다): 생성 대기열에서 떼고 region 을 닫는다. 청크는 저장하지 않는다. */
    public void closeScratch(World w) {
        if (w == null || !w.isScratch() || w.isUnloaded()) return;
        if (w.genQueue() != null) w.genQueue().removeWorld(w);
        w.closeForUnload();
        w.discardAfterClose();   // 진행 중 생성 결과는 닫힌 region 에 못 들어간다 — 기다리는 쪽을 풀고 청크를 놓는다(#486 검토 3)
    }

    // ------------------------------------------------------------------ 만들기

    /** 올라와 있는 추가 월드(이름순 아님, 올린 순서는 Dimensions). */
    public List<World> extraWorlds() {
        List<World> out = new ArrayList<>();
        for (World w : dimensions.all()) if (w.isExtraWorld()) out.add(w);
        return out;
    }

    /** 그 이름의 추가 월드(없으면 null). */
    public World extraWorld(String name) {
        Extra e = extras.get(name);
        return e == null ? null : e.world();
    }

    /** 이름으로 정할 수 없는 폴더(주 월드와 Paper 가져오기 폴더, 서버 파일 폴더). */
    private boolean reservedName(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        String main = mainLevelName.toLowerCase(Locale.ROOT);
        return n.equals(main) || n.equals(main + "_nether") || n.equals(main + "_the_end")
                || List.of("plugins", "logs", "libraries", "config", "cache", "crash-reports", "versions", "debug").contains(n);
    }

    /**
     * 추가 월드를 만들거나 그 폴더를 불러온다. 검사(이름·키·환경·생성기)는 부른 스레드에서 바로 하고, 폴더 잠금·level.dat·생성기 준비는
     * 월드 스레드에서 한다. 등록·틱 켜기 뒤 전역 스레드에서 hooks.loaded 를 부르고 완료된다.
     */
    public CompletableFuture<World> create(WorldCreator spec) {
        CompletableFuture<World> out = new CompletableFuture<>();
        CompletableFuture<Created> made = submitCreate(spec);
        made.whenComplete((c, t) -> {
            if (t != null) {
                out.completeExceptionally(t instanceof java.util.concurrent.CompletionException ce && ce.getCause() != null ? ce.getCause() : t);
            } else if (!c.fresh()) {
                out.complete(c.world());   // 이미 올라와 있던 월드 — 알림은 처음 올릴 때 한 번
            } else {
                try {
                    parts.scheduler().global().execute(() -> {
                        loaded(c.world(), spec.name());
                        out.complete(c.world());
                    });
                } catch (java.util.concurrent.RejectedExecutionException e) {
                    out.completeExceptionally(new IllegalStateException("the server is stopping"));
                }
            }
        });
        return out.thenApply(w -> w);   // hooks.loaded 이전 상태를 밖에서 complete 하지 못하게 사본
    }

    /**
     * {@link #create} 를 끝까지 기다린다(Bukkit 의 동기 createWorld). 전역 스레드에서 부르면 hooks.loaded 를 이 스레드에서 바로 부르고,
     * 기다리는 동안 월드 스레드가 전역 스레드에 맡긴 일(내리기의 플레이어 확인)을 대신 돌려 서로 기다리지 않게 한다. 다른 스레드면 전역 완료까지 기다린다.
     * 실패는 원래 예외(IllegalArgumentException 등)를 그대로 던진다.
     */
    public World createAndWait(WorldCreator spec) throws InterruptedException {
        if (!onGlobalThread()) {
            try (var m = org.domi.furnace.api.concurrent.GlobalWait.begin()) {
                return unwrap(() -> create(spec).get(5, TimeUnit.MINUTES), spec.name());
            }
        }
        if (WORKER_WAITING.get() != null) {
            // 월드 스레드가 이 전역 일(내리기 알림 등)이 끝나길 기다리는 중이라, 여기서 월드 스레드 차례를 기다리면 서로 멈춘다
            throw new IllegalStateException("cannot create world '" + spec.name() + "' synchronously inside " + WORKER_WAITING.get()
                    + " - use the future of createWorld(..) or create it on the next tick (scheduler runTask)");
        }
        CompletableFuture<Created> made = submitCreate(spec);
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(5);
        while (!made.isDone()) {
            if (System.nanoTime() - deadline > 0) throw new IllegalStateException("world '" + spec.name() + "' was not brought up within 5 minutes");
            Runnable r = globalHelp.poll(5, TimeUnit.MILLISECONDS);
            if (r != null) r.run();
        }
        Created c = unwrap(made::get, spec.name());
        if (c.fresh()) loaded(c.world(), spec.name());
        return c.world();
    }

    private interface Get<T> { T get() throws Exception; }

    private static <T> T unwrap(Get<T> g, String name) throws InterruptedException {
        try {
            return g.get();
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.CompletionException e) {
            Throwable c = e.getCause() != null ? e.getCause() : e;
            if (c instanceof RuntimeException r) throw r;
            if (c instanceof Error er) throw er;
            throw new IllegalStateException("bringing up world '" + name + "' failed", c);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new IllegalStateException("world '" + name + "' was not brought up within 5 minutes", e);
        } catch (InterruptedException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("bringing up world '" + name + "' failed", e);
        }
    }

    private void loaded(World w, String name) {
        try {
            hooks.loaded(w);
        } catch (Throwable t) {
            log.error(Lang.t("world.load-notify-failed", name), t);
        }
    }

    /** 월드 스레드에서 만든 결과: fresh 면 이번에 새로 올렸다(hooks.loaded 를 불러야 한다). */
    private record Created(World world, boolean fresh) {}

    private CompletableFuture<Created> submitCreate(WorldCreator spec) {
        if (reservedName(spec.name())) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("invalid world name: " + spec.name()));
        }
        CompletableFuture<Created> out = new CompletableFuture<>();
        try {
            worker.execute(() -> {
                try {
                    Extra existing = extras.get(spec.name());
                    World w = createNow(spec);
                    out.complete(new Created(w, existing == null || existing.world() != w));
                } catch (Throwable t) {
                    out.completeExceptionally(t);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            out.completeExceptionally(new IllegalStateException("the server is stopping"));
        }
        return out;
    }

    private boolean onGlobalThread() {
        return parts.scheduler().global() instanceof org.domi.furnace.api.concurrent.ThreadOwner o && o.isOwnerThread();
    }

    /**
     * 월드 스레드가 전역 스레드에서 돌려야 하는 일. 전역 대기열에 넣고, 전역 스레드가 {@link #createAndWait} 로 막혀 있으면 그쪽이 대신 돌린다
     * (FutureTask 라 먼저 잡은 쪽만 돈다). 도는 동안 전역 스레드에 {@code what} 을 표시해, 그 안의 동기 월드 만들기를 곧바로 거절한다.
     * 시간 초과면 아직 안 돈 일은 취소한다(늦게 돌지 않는다). 이미 도는 중인 몸체는 스스로 {@code abandoned} 를 보고 그만둬야 한다.
     */
    private <T> T onGlobal(String what, java.util.concurrent.Callable<T> body, long timeout, TimeUnit unit) throws Exception {
        java.util.concurrent.FutureTask<T> task = new java.util.concurrent.FutureTask<>(() -> {
            String prev = WORKER_WAITING.get();
            WORKER_WAITING.set(what);
            try {
                return body.call();
            } finally {
                WORKER_WAITING.set(prev);
            }
        });
        globalHelp.add(task);
        try {
            parts.scheduler().global().execute(() -> {
                globalHelp.remove(task);
                task.run();
            });
            try {
                return task.get(timeout, unit);
            } catch (java.util.concurrent.TimeoutException te) {
                task.cancel(false);
                throw te;
            }
        } finally {
            globalHelp.remove(task);
        }
    }

    /** 전역 스레드가 지금 월드 스레드가 기다리는 일({@link #onGlobal})을 돌리는 중이면 그 이름. */
    private static final ThreadLocal<String> WORKER_WAITING = new ThreadLocal<>();

    private final java.util.concurrent.LinkedBlockingQueue<Runnable> globalHelp = new java.util.concurrent.LinkedBlockingQueue<>();

    /** 이미 올라와 있으면 그 월드를(설명서가 다르면 예외), 아니면 새로 올린다. 월드 스레드 전용. */
    private World createNow(WorldCreator spec) throws Exception {
        Extra existing = extras.get(spec.name());
        if (existing != null) {
            if (!sameWorld(existing.creator(), spec)) {
                throw new IllegalArgumentException("world already brought up with another spec: " + spec.name());
            }
            return existing.world();
        }
        if (spec.needsBiomeProvider()) throw new NeedsBiomeProviderException(spec.name(), spec.biomeProviderName());
        if (spec.needsGenerationHooksObject()) throw new NeedsGenerationHooksException(spec.name(), spec.generationHooksObjectName());
        World byKey = dimensions.get(spec.key());
        if (byKey != null) throw new IllegalArgumentException("level key already in use: " + spec.key());
        for (Extra e : extras.values()) {
            if (e.name().equalsIgnoreCase(spec.name())) throw new IllegalArgumentException("a world differing only in case already exists: " + e.name());
        }
        NoiseWorldSource.Dimension family = family(spec.environment());
        DimensionType type = resolveType(spec, family);
        checkHeight(spec, type, family);

        Path dir = parts.universe().resolve(spec.name());
        LevelStorage storage = LevelStorage.open(dir);   // 다른 서버가 잡고 있으면 DirectoryLock.LockException
        try {
            LevelData ld = storage.loadLevelData();
            boolean fresh = ld == null;
            if (fresh) {
                long seed = spec.seed().orElse(mainLevel.seed);
                ld = LevelData.create(spec.name(), seed, spec.generateStructures(), false, mainLevel.gameType, mainLevel.hardcore,
                        mainLevel.difficulty, mainLevel.enabledPacks, mainLevel.disabledPacks);
                switch (spec.generator()) {
                    case FLAT -> ld.useFlatOverworld(spec.flatGeneratorSettings().isEmpty() ? flatSettings(spec) : flatSettingsJson(spec));
                    case LARGE_BIOMES -> ld.useNoiseOverworld("minecraft:large_biomes");
                    case AMPLIFIED -> ld.useNoiseOverworld("minecraft:amplified");
                    case SINGLE_BIOME_SURFACE -> ld.useSingleBiomeOverworld("minecraft:plains");
                    case DEBUG_ALL_BLOCK_STATES -> ld.useDebugOverworld();
                    default -> { }
                }
            }
            ld.furnaceWorld(specNbt(spec));
            long seed = ld.seed;
            if (spec.biomeProvider() != null) {
                // 생성기를 만들기 전에(공급자 biomes() 가 생성기를 만들 때 불린다) 월드 정보를 준다 — 시드는 level.dat 의 것
                spec.biomeProvider().prepare(new org.domi.furnace.api.worldgen.BiomeProvider.Context(spec.name(), spec.key(), spec.environment(), seed,
                        type.minY(), type.maxY()));
            }
            WorldSource gen = generator(spec, type, family, seed, ld);
            var hooksObject = spec.generationHooksObject();
            if (!spec.generationHooks().isEmpty()) {
                // 훅은 키로만 잡는다(플러그인이 꺼지면 저장소에서 빠져 바닐라로) — 첫 청크(스폰 계산) 전에 건다
                final Hooks h = hooks;
                org.domi.furnace.server.world.gen.GenHooks.attach(gen, spec.generationHooks(), h::generationHooks, spec.key(), seed,
                        parts.blocks(), parts.registries());
            } else if (hooksObject != null) {
                // 훅 객체(T-217): 월드 정보를 먼저 주고(시드는 level.dat 의 것) 월드가 사는 동안 세게 쥔다
                hooksObject.prepare(new org.domi.furnace.api.worldgen.BiomeProvider.Context(spec.name(), spec.key(), spec.environment(), seed,
                        type.minY(), type.maxY()));
                if (org.domi.furnace.server.world.gen.GenHooks.attachObject(gen, hooksObject, spec.key(), seed, parts.blocks(), parts.registries()) == null) {
                    throw new IllegalArgumentException("a chunk generator cannot be attached to the " + spec.generator() + " generator");
                }
            }
            org.domi.furnace.server.world.gen.ChunkDecorators.bind(gen, spec.key(), seed, parts.blocks(), parts.registries());
            Built built = build(type, dir, gen);
            World w = built.world();
            w.levelRoot(dir);                                            // 청크·data 는 <이름>/
            w.worldDir(parts.universe().resolve(mainLevelName));         // playerdata·stats 등 서버 공용은 주 월드 폴더(Paper 와 같다)
            w.markExtraWorld(spec.name());
            w.levelKind(spec.generator() == WorldGenerator.FLAT || spec.generator() == WorldGenerator.VOID, spec.generator() == WorldGenerator.DEBUG_ALL_BLOCK_STATES);
            w.seed(seed);
            w.generatesStructures(spec.generateStructures());   // 생성기도 설명서 값을 쓴다(structures(..))
            // 훅 객체 월드의 새 스폰은 월드를 목록에 올린 뒤 고정 스폰을 먼저 묻는다(CraftBukkit setInitialSpawn — 아래)
            boolean fixedSpawnPending = !ld.initialized && hooksObject != null;
            if (!ld.initialized && !fixedSpawnPending) {
                SpawnPoint sp = initialSpawn(spec, type, gen);
                ld.spawnX = sp.x();
                ld.spawnY = sp.y();
                ld.spawnZ = sp.z();
                ld.spawnAngle = sp.yaw();
                ld.initialized = true;
            }
            w.defaultSpawn(new SpawnPoint(ld.spawnX, ld.spawnY, ld.spawnZ, ld.spawnAngle));
            final LevelData level = ld;
            if ("minecraft:the_end".equals(type.typeKey())) {
                // 바닐라 ServerLevel: 엔드 타입 레벨은 드래곤 전투를 가진다. 데이터는 이 월드 자신의 level.dat(CraftBukkit 과 같다 — 주 엔드와 섞이지 않게)
                w.enableDragonFight(level.dragonFightData());
                w.dragonFightSaver(d -> level.dragonFight = d);
            }
            storage.saveLevelData(ld);
            dimensions.register(w);
            try {
                if (hooksObject != null) hooksObject.attached(spec.key());   // Bukkit: 기본 BlockPopulator 넣기(initWorld)
                hooks.attach(w);
                if (fixedSpawnPending) {
                    // CraftBukkit setInitialSpawn: getFixedSpawnLocation 이 있으면 그 칸·yaw 이고 바닐라 스폰 찾기를 하지 않는다
                    var fixed = hooksObject.fixedSpawn(spec.key());
                    SpawnPoint sp;
                    if (fixed != null) {
                        if (!spec.name().equals(fixed.world())) {
                            throw new IllegalStateException("Cannot set spawn point for " + spec.name() + " to be in another world (" + fixed.world() + ")");
                        }
                        sp = new SpawnPoint((int) Math.floor(fixed.x()), (int) Math.floor(fixed.y()), (int) Math.floor(fixed.z()), fixed.yaw());
                    } else {
                        sp = initialSpawn(spec, type, gen);
                    }
                    ld.spawnX = sp.x();
                    ld.spawnY = sp.y();
                    ld.spawnZ = sp.z();
                    ld.spawnAngle = sp.yaw();
                    ld.initialized = true;
                    w.defaultSpawn(sp);
                    storage.saveLevelData(ld);
                }
                w.startTicking();
            } catch (RuntimeException e) {
                dimensions.unregister(w);
                w.closeForUnload();
                built.closeStores();
                throw e;
            }
            extras.put(spec.name(), new Extra(spec.name(), w, storage, ld, built, spec));
            log.info(Lang.t("world.extra-up", spec.name(), spec.key(), type.typeKey(), spec.generator(), seed, fresh ? Lang.t("world.extra-new") : ""));
            return w;
        } catch (Exception | Error e) {
            try {
                storage.close();
            } catch (Exception ignored) {
                // 잠금 풀기 실패는 원래 오류 뒤에 묻는다
            }
            throw e;
        }
    }

    private static boolean sameWorld(WorldCreator a, WorldCreator b) {
        // 시드를 정하지 않은 쪽은 저장된 시드를 따르므로 비교하지 않는다
        boolean seedOk = a.seed().isEmpty() || b.seed().isEmpty() || a.seed().equals(b.seed());
        // 공급자를 주지 않은 쪽(저장된 설명서로 다시 부르기 등)은 지금 월드를 받는다. 다른 공급자 객체면 다른 설명서
        boolean providerOk = b.biomeProvider() == null || a.biomeProvider() == b.biomeProvider();
        boolean hooksOk = b.generationHooksObject() == null || a.generationHooksObject() == b.generationHooksObject();   // 공급자와 같은 규칙(T-217)
        return a.key().equals(b.key()) && a.environment() == b.environment() && a.generator() == b.generator()
                && a.dimensionType().equals(b.dimensionType()) && a.generationHooks().equals(b.generationHooks()) && seedOk && providerOk && hooksOk;
    }

    static NoiseWorldSource.Dimension family(WorldEnvironment env) {
        return switch (env) {
            case NORMAL -> NoiseWorldSource.Dimension.OVERWORLD;
            case NETHER -> NoiseWorldSource.Dimension.NETHER;
            case THE_END -> NoiseWorldSource.Dimension.END;
        };
    }

    /** 설명서의 차원 타입 → 서버 값(레벨 키는 그 월드 것). 바닐라 타입 또는 켜져 있는 플러그인 타입(W2)만. */
    private DimensionType resolveType(WorldCreator spec, NoiseWorldSource.Dimension family) {
        String key = spec.dimensionType();
        DimensionType vanilla = DimensionType.vanillaType(key);
        if (vanilla != null) return vanilla.forLevel(spec.key(), family);
        var def = hooks.pluginDimensionType(key);
        if (def == null) throw new IllegalArgumentException("unknown dimension type (neither vanilla nor registered by an enabled plugin): " + key);
        if (parts.registries().indexOf("minecraft:dimension_type", key) < 0) {
            throw new IllegalArgumentException("dimension type missing from the client registry (registered after server start?): " + key);
        }
        return DimensionType.fromDefinition(spec.key(), key, def, family);
    }

    /** 노이즈 생성기는 그 계열 원래 높이(오버월드 -64..320, 네더·엔드 0..256)에서만 바닐라와 같은 지형을 만든다. 평지·빈 월드는 아무 높이. */
    private static void checkHeight(WorldCreator spec, DimensionType type, NoiseWorldSource.Dimension family) {
        boolean noise = spec.generator() != WorldGenerator.FLAT && spec.generator() != WorldGenerator.VOID
                && spec.generator() != WorldGenerator.DEBUG_ALL_BLOCK_STATES;
        if (noise && (type.minY() != family.minY || type.sectionCount() != family.sectionCount)) {
            throw new IllegalArgumentException("the noise generator (" + spec.environment() + ") only works with dimension types of height " + family.minY + ".." + (family.minY + family.sectionCount * 16)
                    + ": " + type.typeKey() + " is " + type.minY() + ".." + type.maxY() + " - use FLAT or VOID, or match the height");
        }
    }

    /**
     * 그 월드의 생성기. 바이옴 공급자(T-210)는 노이즈 생성기(모든 환경)와 오버월드 높이의 FLAT 만 받는다 — CraftBukkit 이 바이옴 원천을 바꾸는
     * 생성기가 NoiseBasedChunkGenerator·FlatLevelSource 둘뿐인 것과 같다(디버그·빈 월드·다른 높이의 평지는 거절).
     */
    private WorldSource generator(WorldCreator spec, DimensionType type, NoiseWorldSource.Dimension family, long seed, LevelData ld) {
        var provider = spec.biomeProvider();
        if (provider != null && (spec.generator() == WorldGenerator.VOID || spec.generator() == WorldGenerator.DEBUG_ALL_BLOCK_STATES
                || (spec.generator() == WorldGenerator.FLAT && !overworldHeight(type)))) {
            throw new IllegalArgumentException("a biome provider needs a noise generator or a FLAT world of overworld height (-64..320): "
                    + spec.generator() + " " + type.typeKey() + " " + type.minY() + ".." + type.maxY());
        }
        return switch (spec.generator()) {
            case FLAT -> {
                if (overworldHeight(type)) {
                    // 바닐라 FlatLevelSource 와 같은 생성기(구조물·지형지물·호수·fill_layer) — 설정은 level.dat 의 평지 설정(바닐라의 진실)
                    yield structures(NoiseWorldSource.flat(seed, parts.blocks(), parts.registries(), flatSpec(spec, ld), provider), spec);
                }
                List<SuperflatWorldSource.Layer> layers = new ArrayList<>();
                for (var l : spec.flatLayers()) layers.add(new SuperflatWorldSource.Layer(l.block(), l.height()));
                if (layers.isEmpty()) layers = SuperflatWorldSource.DEFAULT_LAYERS;
                for (var l : layers) parts.blocks().defaultStateId(l.block());   // 모르는 블록이면 IllegalArgumentException
                yield new SuperflatWorldSource(parts.blocks(), layers, biomeId(spec.flatBiome()), type.minY(), type.sectionCount());
            }
            case VOID -> new SuperflatWorldSource(parts.blocks(), List.of(), biomeId("minecraft:the_void"), type.minY(), type.sectionCount());
            case LARGE_BIOMES -> structures(NoiseWorldSource.create(family, "minecraft:large_biomes", seed, parts.blocks(), parts.registries(), null, provider), spec);
            case AMPLIFIED -> structures(NoiseWorldSource.create(family, "minecraft:amplified", seed, parts.blocks(), parts.registries(), null, provider), spec);
            case SINGLE_BIOME_SURFACE -> structures(NoiseWorldSource.create(family, "minecraft:overworld", seed, parts.blocks(), parts.registries(),
                    "minecraft:plains", provider), spec);
            case DEBUG_ALL_BLOCK_STATES -> new DebugWorldSource(parts.blocks(), biomeId("minecraft:plains"), type.minY(), type.sectionCount());
            case DEFAULT -> structures(NoiseWorldSource.create(family, family.noiseSettingsId(), seed, parts.blocks(), parts.registries(), null, provider), spec);
        };
    }

    private static boolean overworldHeight(DimensionType type) {
        return type.minY() == NoiseWorldSource.Dimension.OVERWORLD.minY && type.sectionCount() == NoiseWorldSource.Dimension.OVERWORLD.sectionCount;
    }

    /**
     * 평지 설정: level.dat 의 오버월드 생성기가 minecraft:flat 이면 그 설정(바닐라·Paper 가 만든 폴더도 그대로), 아니면 설명서의 층·바이옴에
     * 지형지물·호수·구조물 없음({@link #flatSettings} 과 같은 뜻). 모르는 바이옴은 바닐라처럼 평원(오류 한 줄).
     */
    private NoiseWorldSource.FlatSpec flatSpec(WorldCreator spec, LevelData ld) {
        NbtCompound g = ld.overworldGenerator();
        NoiseWorldSource.FlatSpec fs;
        if (g != null && "minecraft:flat".equals(g.tags().get("type"))) {
            fs = SuperflatWorldSource.flatSpecOf(g.contains("settings") ? g.getCompound("settings") : null);
        } else {
            fs = SuperflatWorldSource.flatSpecOf(flatSettings(spec));
        }
        if (parts.registries().indexOf("minecraft:worldgen/biome", fs.biome()) < 0) {
            log.error("Unknown biome, defaulting to plains");   // 바닐라 FlatLevelGeneratorSettings 와 같은 줄
            fs = new NoiseWorldSource.FlatSpec(fs.layers(), "minecraft:plains", fs.features(), fs.lakes(), fs.structureOverrides());
        }
        return fs;
    }

    /** 설명서의 평지 JSON(바닐라 generator-settings 규칙) → level.dat 평지 설정. JSON 객체가 아니면 IllegalArgumentException(Paper 는 JsonParseException). */
    private static NbtCompound flatSettingsJson(WorldCreator spec) {
        com.google.gson.JsonElement e;
        try {
            e = com.google.gson.JsonParser.parseString(spec.flatGeneratorSettings());
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("flat generator settings of '" + spec.name() + "' are not JSON: " + ex.getMessage(), ex);
        }
        if (!e.isJsonObject()) throw new IllegalArgumentException("flat generator settings of '" + spec.name() + "' are not a JSON object");
        return SuperflatWorldSource.settingsNbt(e.getAsJsonObject());
    }

    private static NoiseWorldSource structures(NoiseWorldSource n, WorldCreator spec) {
        n.structureEngine().setStructuresEnabled(spec.generateStructures());
        return n;
    }

    private int biomeId(String biome) {
        int id = parts.registries().indexOf("minecraft:worldgen/biome", biome);
        if (id < 0) throw new IllegalArgumentException("unknown biome: " + biome);
        return id;
    }

    /** 새 월드의 스폰: 오버월드 노이즈는 바닐라 setInitialSpawn(생성기 기본 스폰), 평지는 (0, 맨 위 층 위, 0), 그 밖은 (0, 64 를 높이 안으로, 0). */
    private static SpawnPoint initialSpawn(WorldCreator spec, DimensionType type, WorldSource gen) {
        if (gen instanceof DebugWorldSource) return new SpawnPoint(0, DebugWorldSource.SPAWN_Y, 0, 0f);   // 바닐라 setInitialSpawn(debug)
        if (gen instanceof NoiseWorldSource n && n.isFlat()) {
            return new SpawnPoint(0, Math.min(n.flatSurfaceY(), type.maxY() - 1), 0, 0f);   // 주 월드 평지와 같다(FurnaceMain)
        }
        if (gen instanceof NoiseWorldSource n && spec.environment() == WorldEnvironment.NORMAL) return n.defaultSpawn();
        if (gen instanceof SuperflatWorldSource f && spec.generator() == WorldGenerator.FLAT) {
            return new SpawnPoint(0, Math.min(f.surfaceY(), type.maxY() - 1), 0, 0f);
        }
        return new SpawnPoint(0, Math.clamp(64, type.minY(), type.maxY() - 1), 0, 0f);
    }

    private static NbtCompound flatSettings(WorldCreator spec) {
        NbtCompound s = new NbtCompound();
        s.putString("biome", spec.flatBiome());
        s.putBoolean("features", false);
        s.putBoolean("lakes", false);
        NbtList layers = new NbtList(NbtIo.TAG_COMPOUND);
        var src = spec.flatLayers().isEmpty()
                ? SuperflatWorldSource.DEFAULT_LAYERS.stream().map(l -> new WorldCreator.FlatLayer(l.block(), l.height())).toList()
                : spec.flatLayers();
        for (var l : src) {
            NbtCompound c = new NbtCompound();
            c.putString("block", l.block());
            c.putInt("height", l.height());
            layers.add(c);
        }
        s.putList("layers", layers);
        s.putList("structure_overrides", new NbtList(NbtIo.TAG_STRING));
        return s;
    }

    // ------------------------------------------------------------------ level.dat 의 설명서(furnace:world)

    static NbtCompound specNbt(WorldCreator spec) {
        NbtCompound c = new NbtCompound();
        c.putString("key", spec.key());
        c.putString("environment", spec.environment().name());
        c.putString("generator", spec.generator().name());
        c.putString("dimension_type", spec.dimensionType());
        c.putBoolean("generate_structures", spec.generateStructures());
        c.putString("flat_biome", spec.flatBiome());
        if (!spec.generationHooks().isEmpty()) c.putString("generation_hooks", spec.generationHooks());
        if (!spec.biomeProviderName().isEmpty()) c.putString("biome_provider", spec.biomeProviderName());   // 공급자 객체는 저장할 수 없다(T-210)
        if (!spec.generationHooksObjectName().isEmpty()) c.putString("generation_hooks_object", spec.generationHooksObjectName());   // T-217
        NbtList layers = new NbtList(NbtIo.TAG_COMPOUND);
        for (var l : spec.flatLayers()) {
            NbtCompound lc = new NbtCompound();
            lc.putString("block", l.block());
            lc.putInt("height", l.height());
            layers.add(lc);
        }
        c.putList("flat_layers", layers);
        return c;
    }

    /** 저장된 설명서로 다시 만든다(시드는 level.dat 의 것을 쓰므로 비운다). 설명서가 없으면(Paper 등에서 가져온 폴더) 이름과 NORMAL 기본으로. */
    static WorldCreator specFrom(String name, NbtCompound c) {
        var b = WorldCreator.named(name);
        if (c == null) return b.build();
        if (c.tags().get("key") instanceof String k) b.key(k);
        if (c.tags().get("environment") instanceof String e) b.environment(WorldEnvironment.valueOf(e));
        if (c.tags().get("generator") instanceof String g) b.generator(WorldGenerator.valueOf(g));
        if (c.tags().get("dimension_type") instanceof String t) b.dimensionType(t);
        if (c.contains("generate_structures")) b.generateStructures(((Number) c.tags().get("generate_structures")).intValue() != 0);
        if (c.tags().get("flat_biome") instanceof String fb) b.flatBiome(fb);
        if (c.tags().get("generation_hooks") instanceof String gh) b.generationHooks(gh);
        if (c.tags().get("biome_provider") instanceof String bp) b.biomeProviderName(bp);
        if (c.tags().get("generation_hooks_object") instanceof String gho) b.generationHooksObjectName(gho);
        if (c.contains("flat_layers")) {
            List<WorldCreator.FlatLayer> layers = new ArrayList<>();
            for (Object o : c.getList("flat_layers").items()) {
                NbtCompound l = (NbtCompound) o;
                layers.add(new WorldCreator.FlatLayer(l.getString("block"), ((Number) l.tags().get("height")).intValue()));
            }
            b.flatLayers(layers);
        }
        return b.build();
    }

    /** 그 폴더의 level.dat 에 남은 설명서(폴더나 level.dat 이 없으면 null). 잠그지 않고 읽는다. */
    public WorldCreator storedCreator(String name) throws java.io.IOException {
        Path dat = parts.universe().resolve(name).resolve(LevelStorage.LEVEL_DAT);
        if (!java.nio.file.Files.isRegularFile(dat)) return null;
        NbtCompound root;
        try (var in = java.nio.file.Files.newInputStream(dat)) {
            root = NbtIo.readGzip(in);
        }
        NbtCompound data = root.contains("Data") ? root.getCompound("Data") : root;
        if (data.contains(LevelData.FURNACE_WORLD)) return specFrom(name, data.getCompound(LevelData.FURNACE_WORLD));
        // 설명서가 없는 폴더(Paper/Spigot/Multiverse 가 만든 월드): 폴더 배치와 level.dat 로 환경·생성기를 추론한다.
        return BukkitWorldImport.inferCreator(parts.universe().resolve(name), name, data);
    }

    /** 이름의 월드를 저장된 설명서로 불러온다(/worlds load, worlds.autoload). 폴더가 없으면 IllegalArgumentException. */
    public CompletableFuture<World> load(String name) {
        try {
            WorldCreator c = storedCreator(name);
            if (c == null) return CompletableFuture.failedFuture(new IllegalArgumentException("no world folder (level.dat): " + name));
            return create(c);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    // ------------------------------------------------------------------ 내리기

    /**
     * 추가 월드를 내린다. 기본 세 차원이면 IllegalArgumentException, 플레이어가 있거나 hooks.beforeUnload 가 거절하면 false.
     * 순서: (전역 스레드) 플레이어 확인 + 알림 → 목록에서 떼기 → 틱 끄기 → [저장: 모든 dirty 청크·엔티티 저장, IO 레인 비우기, level.dat] →
     * region 스레드 닫기 → 저장소 닫기 → 폴더 잠금 풀기.
     */
    public CompletableFuture<Boolean> unload(World w, boolean save) {
        if (!w.isExtraWorld()) return CompletableFuture.failedFuture(new IllegalArgumentException("the default dimension cannot be unloaded: " + w.dimension()));
        CompletableFuture<Boolean> out = new CompletableFuture<>();
        try {
            worker.execute(() -> {
                try {
                    out.complete(unloadNow(w, save));
                } catch (Throwable t) {
                    out.completeExceptionally(t);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            out.completeExceptionally(new IllegalStateException("the server is stopping"));
        }
        return out;
    }

    private boolean unloadNow(World w, boolean save) throws Exception {
        Extra e = extras.get(w.levelName());
        if (e == null || e.world() != w) return false;   // 이미 내렸다
        // 전역 스레드에서: 플레이어 확인과 알림, 목록에서 떼기를 한 번에(그 사이 명령이 이 월드로 보내지 못하게)
        // 0=기다림, 1·3=전역이 목록에서 떼는 중·뗌, 2=월드 스레드가 시간 초과로 포기 — 포기한 뒤 늦게 돈 몸체가 반쯤 내린 월드를 남기지 않게
        java.util.concurrent.atomic.AtomicInteger state = new java.util.concurrent.atomic.AtomicInteger();
        boolean go;
        try {
            go = onGlobal(Lang.t("world.what-unload-handler"), () -> {
                if (!w.playersHere().isEmpty() || w.incomingTravels() > 0) return false;
                if (!hooks.beforeUnload(w, save)) return false;
                if (!w.playersHere().isEmpty() || w.incomingTravels() > 0) return false;   // 알림 처리기가 누군가 들여보냈다
                if (!state.compareAndSet(0, 1)) return false;
                try {
                    dimensions.unregister(w);
                } finally {
                    state.set(3);
                }
                return true;
            }, 30, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException te) {
            if (state.compareAndSet(0, 2)) throw te;
            while (state.get() != 3) Thread.onSpinWait();   // 시간 초과와 같은 순간에 목록에서 떼는 중 — 끝나면 내리기를 마저 한다
            go = true;
        }
        if (!go) return false;
        // 목록에서 뗐으니 새 save-all 은 이 월드를 고르지 않는다. 이미 고른 저장(진행 중·대기 중)이 끝나길 기다린다
        try {
            saveBarrier.get().get(10, TimeUnit.MINUTES);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException | java.util.concurrent.CancellationException ex) {
            log.warn(Lang.t("world.save-all-wait-failed", e.name()), ex);
        }
        extras.remove(e.name(), e);
        if (w.genQueue() != null) w.genQueue().removeWorld(w);
        try {
            hooks.detached(w);
        } catch (Throwable t) {
            log.warn(Lang.t("world.unload-notify-failed", e.name()), t);
        }
        w.stopTicking();
        w.relight().shutdown();   // 빛 다시 계산의 붙잡기를 풀어 저장 전 빛 계산이 마저 하게(T-197 검토 R1)
        // 떼기 직전에 이 월드로 떠난 이동(목적 청크 준비 중)이 있으면 도착한 뒤 저장한다 — 저장 뒤에 도착해 사라지지 않게(A03-01)
        w.freezeTravel();
        long arriveBy = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (w.incomingTravels() + w.outgoingTravels() > 0 && System.nanoTime() - arriveBy < 0) Thread.sleep(5);   // 나간 이동의 되돌림까지
        if (save) {
            w.forceSave(true);
            awaitAll(w.flushAll(), Lang.t("world.what-save"));
            // 저장 중 끝난 청크 읽기·생성이 다시 dirty 를 만들 수 있다 — 진행 중 로드가 빠질 때까지 잠깐 기다린 뒤 한 번 더
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (w.pendingChunkLoads() > 0 && System.nanoTime() < deadline) Thread.sleep(20);
            awaitAll(w.flushAll(), Lang.t("world.what-save-final"));
        }
        try {
            w.ioBarrier().get(60, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException te) {
            log.error(Lang.t("world.io-lane-busy", e.name()));
        }
        w.closeForUnload();
        e.built().closeStores();
        if (save) {
            e.levelData().gameTime = w.gameTime();
            e.levelData().dayTime = w.dayTime();
            copySpawn(e.levelData(), w);
            e.storage().saveLevelData(e.levelData());
        }
        e.storage().close();
        if (w.saveFailures() > 0) log.error(Lang.t("world.save-failures", e.name(), w.saveFailures()));
        log.info(Lang.t(save ? "world.extra-down-saved" : "world.extra-down-unsaved", e.name()));
        return true;
    }

    /** 플러그인이 바꾼 추가 월드 스폰(World#setSpawnLocation)을 level.dat 칸으로 옮긴다. */
    private static void copySpawn(LevelData ld, World w) {
        var sp = w.defaultSpawn();
        ld.spawnX = sp.x();
        ld.spawnY = sp.y();
        ld.spawnZ = sp.z();
        ld.spawnAngle = sp.yaw();
    }

    private static void awaitAll(List<CompletableFuture<Void>> fs, String what) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        for (var f : fs) {
            try {
                f.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException ex) {
                log.error(Lang.t("world.step-failed", what), ex);
            }
        }
    }

    // ------------------------------------------------------------------ 서버 종료

    /** 서버 종료(저장 단계 뒤): 추가 월드의 저장소와 level.dat 을 닫는다. region 저장(flushAll)은 FurnaceMain 이 모든 차원에 이미 했다. */
    public void closeAllForShutdown() {
        worker.shutdown();
        for (Extra e : extras.values()) {
            try {
                e.built().closeStores();
                e.levelData().gameTime = e.world().gameTime();
                e.levelData().dayTime = e.world().dayTime();
                copySpawn(e.levelData(), e.world());
                e.storage().saveLevelData(e.levelData());
                e.storage().close();
            } catch (Exception ex) {
                log.error(Lang.t("world.extra-close-failed", e.name()), ex);
            }
        }
        extras.clear();
    }

    /** 자동 저장 박자마다: 추가 월드 level.dat(시각·드래곤 전투). 아무 스레드. */
    public void saveLevelData() {
        for (Extra e : extras.values()) {
            synchronized (e.levelData()) {
                e.levelData().gameTime = e.world().gameTime();
                e.levelData().dayTime = e.world().dayTime();
                copySpawn(e.levelData(), e.world());
                try {
                    e.storage().saveLevelData(e.levelData());
                } catch (java.io.IOException ex) {
                    log.error(Lang.t("world.extra-level-dat-failed", e.name()), ex);
                }
            }
        }
    }
}
