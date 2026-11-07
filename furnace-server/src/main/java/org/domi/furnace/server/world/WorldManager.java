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
                    log.error("[WORLD] 저장소 닫기 실패 {}", s.regionDir(), e);
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
        if (reservedName(spec.name())) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("쓸 수 없는 월드 이름: " + spec.name()));
        }
        CompletableFuture<World> out = new CompletableFuture<>();
        try {
            worker.execute(() -> {
                try {
                    World w = createNow(spec);
                    if (w == null) return;   // 이미 out 을 끝냈다
                    parts.scheduler().global().execute(() -> {
                        try {
                            hooks.loaded(w);
                        } catch (Throwable t) {
                            log.error("[WORLD] 월드 불러오기 알림 실패 {}", spec.name(), t);
                        }
                        out.complete(w);
                    });
                } catch (Throwable t) {
                    out.completeExceptionally(t);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            out.completeExceptionally(new IllegalStateException("서버 종료 중"));
        }
        return out.thenApply(w -> w);   // hooks.loaded 이전 상태를 밖에서 complete 하지 못하게 사본
    }

    /** 이미 올라와 있으면 그 월드를(설명서가 다르면 예외), 아니면 새로 올린다. 월드 스레드 전용. */
    private World createNow(WorldCreator spec) throws Exception {
        Extra existing = extras.get(spec.name());
        if (existing != null) {
            if (!sameWorld(existing.creator(), spec)) {
                throw new IllegalArgumentException("이미 다른 설명서로 올라온 월드: " + spec.name());
            }
            return existing.world();
        }
        World byKey = dimensions.get(spec.key());
        if (byKey != null) throw new IllegalArgumentException("이미 쓰는 레벨 키: " + spec.key());
        for (Extra e : extras.values()) {
            if (e.name().equalsIgnoreCase(spec.name())) throw new IllegalArgumentException("대소문자만 다른 월드가 이미 있다: " + e.name());
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
                    case FLAT -> ld.useFlatOverworld(flatSettings(spec));
                    case LARGE_BIOMES -> ld.useNoiseOverworld("minecraft:large_biomes");
                    case AMPLIFIED -> ld.useNoiseOverworld("minecraft:amplified");
                    case SINGLE_BIOME_SURFACE -> ld.useSingleBiomeOverworld("minecraft:plains");
                    case DEBUG_ALL_BLOCK_STATES -> ld.useDebugOverworld();
                    default -> { }
                }
            }
            ld.furnaceWorld(specNbt(spec));
            long seed = ld.seed;
            WorldSource gen = generator(spec, type, family, seed);
            if (!spec.generationHooks().isEmpty()) {
                // 훅은 키로만 잡는다(플러그인이 꺼지면 저장소에서 빠져 바닐라로) — 첫 청크(스폰 계산) 전에 건다
                final Hooks h = hooks;
                org.domi.furnace.server.world.gen.GenHooks.attach(gen, spec.generationHooks(), h::generationHooks, spec.key(), seed,
                        parts.blocks(), parts.registries());
            }
            Built built = build(type, dir, gen);
            World w = built.world();
            w.levelRoot(dir);                                            // 청크·data 는 <이름>/
            w.worldDir(parts.universe().resolve(mainLevelName));         // playerdata·stats 등 서버 공용은 주 월드 폴더(Paper 와 같다)
            w.markExtraWorld(spec.name());
            w.levelKind(spec.generator() == WorldGenerator.FLAT || spec.generator() == WorldGenerator.VOID, spec.generator() == WorldGenerator.DEBUG_ALL_BLOCK_STATES);
            w.seed(seed);
            if (!ld.initialized) {
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
                hooks.attach(w);
                w.startTicking();
            } catch (RuntimeException e) {
                dimensions.unregister(w);
                w.closeForUnload();
                built.closeStores();
                throw e;
            }
            extras.put(spec.name(), new Extra(spec.name(), w, storage, ld, built, spec));
            log.info("[WORLD] 추가 월드 '{}' 올림 — 키 {}, 차원 타입 {}, 생성기 {}, 시드 {}{}", spec.name(), spec.key(), type.typeKey(),
                    spec.generator(), seed, fresh ? " (새 월드)" : "");
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
        return a.key().equals(b.key()) && a.environment() == b.environment() && a.generator() == b.generator()
                && a.dimensionType().equals(b.dimensionType()) && a.generationHooks().equals(b.generationHooks()) && seedOk;
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
        if (def == null) throw new IllegalArgumentException("모르는 차원 타입(바닐라도, 켜진 플러그인 등록도 아니다): " + key);
        if (parts.registries().indexOf("minecraft:dimension_type", key) < 0) {
            throw new IllegalArgumentException("클라이언트 레지스트리에 없는 차원 타입(서버 시작 뒤 등록?): " + key);
        }
        return DimensionType.fromDefinition(spec.key(), key, def, family);
    }

    /** 노이즈 생성기는 그 계열 원래 높이(오버월드 -64..320, 네더·엔드 0..256)에서만 바닐라와 같은 지형을 만든다. 평지·빈 월드는 아무 높이. */
    private static void checkHeight(WorldCreator spec, DimensionType type, NoiseWorldSource.Dimension family) {
        boolean noise = spec.generator() != WorldGenerator.FLAT && spec.generator() != WorldGenerator.VOID
                && spec.generator() != WorldGenerator.DEBUG_ALL_BLOCK_STATES;
        if (noise && (type.minY() != family.minY || type.sectionCount() != family.sectionCount)) {
            throw new IllegalArgumentException("노이즈 생성기(" + spec.environment() + ")는 높이 " + family.minY + ".." + (family.minY + family.sectionCount * 16)
                    + " 차원 타입만 쓸 수 있다: " + type.typeKey() + " 는 " + type.minY() + ".." + type.maxY() + " — FLAT·VOID 를 쓰거나 높이를 맞춘다");
        }
    }

    private WorldSource generator(WorldCreator spec, DimensionType type, NoiseWorldSource.Dimension family, long seed) {
        return switch (spec.generator()) {
            case FLAT -> {
                List<SuperflatWorldSource.Layer> layers = new ArrayList<>();
                for (var l : spec.flatLayers()) layers.add(new SuperflatWorldSource.Layer(l.block(), l.height()));
                if (layers.isEmpty()) layers = SuperflatWorldSource.DEFAULT_LAYERS;
                for (var l : layers) parts.blocks().defaultStateId(l.block());   // 모르는 블록이면 IllegalArgumentException
                yield new SuperflatWorldSource(parts.blocks(), layers, biomeId(spec.flatBiome()), type.minY(), type.sectionCount());
            }
            case VOID -> new SuperflatWorldSource(parts.blocks(), List.of(), biomeId("minecraft:the_void"), type.minY(), type.sectionCount());
            case LARGE_BIOMES -> structures(NoiseWorldSource.overworld(seed, parts.blocks(), parts.registries(), "minecraft:large_biomes"), spec);
            case AMPLIFIED -> structures(NoiseWorldSource.overworld(seed, parts.blocks(), parts.registries(), "minecraft:amplified"), spec);
            case SINGLE_BIOME_SURFACE -> structures(NoiseWorldSource.overworld(seed, parts.blocks(), parts.registries(), "minecraft:overworld",
                    "minecraft:plains"), spec);
            case DEBUG_ALL_BLOCK_STATES -> new DebugWorldSource(parts.blocks(), biomeId("minecraft:plains"), type.minY(), type.sectionCount());
            case DEFAULT -> structures(NoiseWorldSource.create(family, seed, parts.blocks(), parts.registries()), spec);
        };
    }

    private static NoiseWorldSource structures(NoiseWorldSource n, WorldCreator spec) {
        n.structureEngine().setStructuresEnabled(spec.generateStructures());
        return n;
    }

    private int biomeId(String biome) {
        int id = parts.registries().indexOf("minecraft:worldgen/biome", biome);
        if (id < 0) throw new IllegalArgumentException("모르는 바이옴: " + biome);
        return id;
    }

    /** 새 월드의 스폰: 오버월드 노이즈는 바닐라 setInitialSpawn(생성기 기본 스폰), 평지는 (0, 맨 위 층 위, 0), 그 밖은 (0, 64 를 높이 안으로, 0). */
    private static SpawnPoint initialSpawn(WorldCreator spec, DimensionType type, WorldSource gen) {
        if (gen instanceof DebugWorldSource) return new SpawnPoint(0, DebugWorldSource.SPAWN_Y, 0, 0f);   // 바닐라 setInitialSpawn(debug)
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
            if (c == null) return CompletableFuture.failedFuture(new IllegalArgumentException("월드 폴더(level.dat)가 없다: " + name));
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
        if (!w.isExtraWorld()) return CompletableFuture.failedFuture(new IllegalArgumentException("기본 차원은 내릴 수 없다: " + w.dimension()));
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
            out.completeExceptionally(new IllegalStateException("서버 종료 중"));
        }
        return out;
    }

    private boolean unloadNow(World w, boolean save) throws Exception {
        Extra e = extras.get(w.levelName());
        if (e == null || e.world() != w) return false;   // 이미 내렸다
        // 전역 스레드에서: 플레이어 확인과 알림, 목록에서 떼기를 한 번에(그 사이 명령이 이 월드로 보내지 못하게)
        boolean go = parts.scheduler().global().<Boolean>submit(() -> {
            if (!w.playersHere().isEmpty() || w.incomingTravels() > 0) return false;
            if (!hooks.beforeUnload(w, save)) return false;
            if (!w.playersHere().isEmpty() || w.incomingTravels() > 0) return false;   // 알림 처리기가 누군가 들여보냈다
            dimensions.unregister(w);
            return true;
        }).get(30, TimeUnit.SECONDS);
        if (!go) return false;
        extras.remove(e.name(), e);
        try {
            hooks.detached(w);
        } catch (Throwable t) {
            log.warn("[WORLD] 월드 떼기 알림 실패 {}", e.name(), t);
        }
        w.stopTicking();
        if (save) {
            w.forceSave(true);
            awaitAll(w.flushAll(), "저장");
            // 저장 중 끝난 청크 읽기·생성이 다시 dirty 를 만들 수 있다 — 진행 중 로드가 빠질 때까지 잠깐 기다린 뒤 한 번 더
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (w.pendingChunkLoads() > 0 && System.nanoTime() < deadline) Thread.sleep(20);
            awaitAll(w.flushAll(), "저장(마무리)");
        }
        try {
            w.ioBarrier().get(60, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException te) {
            log.error("[WORLD] '{}' IO 레인이 60초 안에 비지 않았다 — 저장소를 닫는다", e.name());
        }
        w.closeForUnload();
        e.built().closeStores();
        if (save) {
            e.levelData().gameTime = w.gameTime();
            e.levelData().dayTime = w.dayTime();
            e.storage().saveLevelData(e.levelData());
        }
        e.storage().close();
        if (w.saveFailures() > 0) log.error("[WORLD] '{}' 저장 실패 {}건", e.name(), w.saveFailures());
        log.info("[WORLD] 추가 월드 '{}' 내림{}", e.name(), save ? "(저장)" : "(저장 안 함)");
        return true;
    }

    private static void awaitAll(List<CompletableFuture<Void>> fs, String what) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        for (var f : fs) {
            try {
                f.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException ex) {
                log.error("[WORLD] {} 실패/지연", what, ex);
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
                e.storage().saveLevelData(e.levelData());
                e.storage().close();
            } catch (Exception ex) {
                log.error("[STOP] 추가 월드 '{}' 닫기 실패", e.name(), ex);
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
                try {
                    e.storage().saveLevelData(e.levelData());
                } catch (java.io.IOException ex) {
                    log.error("[SAVE] 추가 월드 '{}' level.dat 저장 실패", e.name(), ex);
                }
            }
        }
    }
}
