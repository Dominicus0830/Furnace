// 헤드리스 시험 서버(B7): 플러그인 시험용으로 실제 Furnace 서버를 한 JVM 안에 띄운다.
// - 메모리 월드: 평지(또는 고른 생성기) 오버월드 하나. 청크를 디스크에 쓰지 않고 내리지도 않는다(시험이 만진 칸이 사라지지 않게).
//   플레이어 데이터·설정 같은 파일은 임시 폴더에 쓰고 close 때 지운다.
// - 결정적 틱: 서버는 스스로 시간을 진행하지 않는다. tick(n) 을 부를 때만 전역 틱(시간·날씨·전역 지연 작업) → 모든 region 틱이
//   한 번씩 돌고, 다 끝나야 돌아온다. 그 사이에도 region 은 작업 큐를 비우므로 API future(블록 읽기·쓰기, 명령)는 틱 없이 끝난다.
// - 플러그인: 시험 클래스패스의 플러그인(설명 파일을 찾아 실제 부트스트랩·onLoad·onEnable 경로로)이나 jar 를 그대로 올린다.
// 쓰는 법은 docs/api/testing.md.
package org.domi.furnace.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.api.world.World;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.config.ServerPropertiesFile;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.EntityIdAllocator;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.NetworkServer;
import org.domi.furnace.server.net.ServerLoginContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.players.GameProfileCache;
import org.domi.furnace.server.players.PlayerLists;
import org.domi.furnace.server.players.ProfileLookup;
import org.domi.furnace.server.plugin.PluginBootstrapper;
import org.domi.furnace.server.plugin.PluginHost;
import org.domi.furnace.server.plugin.PluginSettings;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.sched.GlobalThread;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.Difficulty;
import org.domi.furnace.server.world.GameMode;
import org.domi.furnace.server.world.ItemRegistry;
import org.domi.furnace.server.world.SpawnPoint;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.domi.furnace.server.world.gen.WorldSource;
import org.domi.furnace.server.world.storage.LevelData;

public final class FurnaceTestServer implements AutoCloseable {

    /** 틱·future 를 기다리는 기본 한도. 넘으면 시험이 멈춘 것으로 보고 던진다. */
    public static final long DEFAULT_TIMEOUT_MILLIS = 30_000L;

    private final Builder options;
    private final Path root;
    private final boolean deleteRoot;
    private FurnaceSchedulerImpl scheduler;
    private GlobalThread global;
    private ChunkWorkerPool chunkPool;
    private ExecutorService ioPool;
    private ExecutorService genPool;
    private org.domi.furnace.server.world.World world;
    private ServerCore core;
    private PluginHost host;
    private final List<Text> consoleLog = new ArrayList<>();
    private final List<TestPlayer> players = new CopyOnWriteArrayList<>();
    private boolean ownsFurnaceHolder;
    private NetworkServer network;
    private int port = -1;
    private long ticks;
    private volatile boolean closed;

    // ------------------------------------------------------------------ 만들기

    private static java.util.Map<String, String> compatOptions(Builder b) {
        java.util.Map<String, String> m = new java.util.HashMap<>(PluginSettings.DEFAULT_COMPAT_OPTIONS);
        m.put("plugins.compat.nms.strict", String.valueOf(b.compatNmsStrict));
        m.put("plugins.compat.nms.version-package", b.compatNmsVersionPackage);
        m.putAll(b.compatExtra);
        return m;
    }

    public static Builder builder() { return new Builder(); }

    /** 기본 설정(평지, 플러그인 없음)으로 띄운다. */
    public static FurnaceTestServer start() { return builder().start(); }

    private FurnaceTestServer(Builder b) throws IOException {
        this.options = b;
        this.deleteRoot = b.directory == null;
        this.root = b.directory != null ? Files.createDirectories(b.directory) : Files.createTempDirectory("furnace-test-");
        Path pluginsDir = Files.createDirectories(root.resolve("plugins"));
        Path worldDir = Files.createDirectories(root.resolve("world"));
        boolean started = false;
        FurnaceSchedulerImpl sched = null;
        ChunkWorkerPool chunks = null;
        ExecutorService io = null, gen = null;
        try {
            PluginJars.stage(b.classpathPlugins, b.pluginJars, pluginsDir);
            sched = new FurnaceSchedulerImpl(50_000_000L, b.regionThreads);
            this.scheduler = sched;
            this.global = (GlobalThread) sched.global();
            global.useManualClock();
            // 플러그인 부트스트랩: 레지스트리·월드보다 먼저(FurnaceMain 과 같은 자리). 시험에서는 실패를 숨기지 않는다(fail-fast).
            PluginSettings pluginSettings = new PluginSettings(b.failFast, null, java.util.Map.of()).withCompatNms(b.compatNms).withLibraries(b.libraries)
                    .withExposeCompatToNative(b.exposeCompatToNative)
                    .withCompatOptions(compatOptions(b));
            PluginBootstrapper boot = PluginBootstrapper.bootstrap(pluginsDir, pluginSettings);
            BlockRegistry blocks = BlockRegistry.load("blocks/1.21.1.txt");
            ItemRegistry items = ItemRegistry.load("items/1.21.1.txt");
            org.domi.furnace.server.entity.PlayerData.itemRegistry(items);
            RegistrySet registries = boot.collectGameRegistries(RegistrySet.load("registries/1.21.1.txt"));
            RegistrySet.install(registries);
            ItemContext.install(new TagRegistries(registries));
            int plains = Math.max(0, registries.indexOf("minecraft:worldgen/biome", "minecraft:plains"));
            int biomeCount = registries.entries("minecraft:worldgen/biome").size();
            int biomeBits = Math.max(1, 32 - Integer.numberOfLeadingZeros(biomeCount - 1));
            WorldSource source = b.generator != null ? b.generator.create(blocks, registries)
                    : new SuperflatWorldSource(blocks, b.flatLayers, plains);
            chunks = new ChunkWorkerPool(Math.max(1, b.chunkThreads));
            this.chunkPool = chunks;
            io = Executors.newSingleThreadExecutor(r -> daemon(r, "furnace-test-io"));
            gen = Executors.newFixedThreadPool(Math.max(1, b.chunkThreads), r -> daemon(r, "furnace-test-gen"));
            this.ioPool = io;
            this.genPool = gen;
            var w = new org.domi.furnace.server.world.World(source, chunks, sched, b.regionThreads, 4, 1,
                    b.viewDistance, b.viewDistance, blocks.directBits(), biomeBits, io, gen);
            this.world = w;
            w.worldDir(worldDir);
            w.useManualClock();
            // 메모리 월드: 주기 저장도, 구독자 없는 청크 내리기도 하지 않는다
            w.configurePersistence(Integer.MAX_VALUE, Integer.MAX_VALUE);
            w.setRegistries(blocks, items);
            w.entityIds(new EntityIdAllocator());
            w.seed(b.seed);
            int spawnY = source instanceof SuperflatWorldSource flat ? flat.surfaceY() : b.spawnY;
            SpawnPoint spawn = new SpawnPoint(0, spawnY, 0, 0f);
            w.defaultSpawn(spawn);
            LevelData level = LevelData.create("world", b.seed, false, false, b.gameMode, false, b.difficulty, List.of("vanilla"), List.of());
            level.spawnX = spawn.x();
            level.spawnY = spawn.y();
            level.spawnZ = spawn.z();
            level.initialized = true;
            for (var e : b.gameRules.entrySet()) level.gameRules.put(e.getKey(), e.getValue());
            Path props = root.resolve("server.properties");
            Files.writeString(props, "online-mode=false\n" + b.serverProperties);
            ServerPropertiesFile settings = new ServerPropertiesFile(props);
            PlayerLists lists = new PlayerLists(root, settings);
            this.core = new ServerCore(settings, lists, new GameProfileCache(ProfileLookup.NONE, root.resolve("usercache.json")), w, level, sched);
            core.registries(registries);   // 바이옴 API(WorldHandle.biomeNames)·접속 레지스트리가 보는 세트(FurnaceMain 은 데이터 팩 판을 건다)
            this.host = core.plugins();
            core.addConsoleListener(t -> { synchronized (consoleLog) { consoleLog.add(t); } });
            this.ownsFurnaceHolder = TestHolder.claim(host);
            host.pluginsImpl().directory(pluginsDir);
            host.pluginsImpl().settings(pluginSettings);
            host.pluginsImpl().loadAll(boot);
            var dispatcher = core.commands().getDispatcher();
            org.domi.furnace.server.command.builtin.FurnaceCommands.register(dispatcher, host);
            org.domi.furnace.server.command.builtin.FurnaceAdminCommands.register(dispatcher);
            org.domi.furnace.server.command.builtin.TpsCommands.register(dispatcher);
            w.startTicking();
            // 스폰 청크를 미리 올린다(접속·블록 시험이 곧바로 쓰게)
            for (int dz = -b.spawnChunkRadius; dz <= b.spawnChunkRadius; dz++)
                for (int dx = -b.spawnChunkRadius; dx <= b.spawnChunkRadius; dx++) loadChunkBlocking(dx, dz);
            host.pluginsImpl().enableAll();
            core.events().call(new org.domi.furnace.api.event.server.ServerLoadEvent());
            started = true;
        } finally {
            if (!started) {
                try {
                    if (host != null) host.pluginsImpl().closeAll();
                    if (host != null) host.close();
                } catch (Throwable ignored) { }
                if (chunks != null) chunks.shutdown();
                if (io != null) io.shutdownNow();
                if (gen != null) gen.shutdownNow();
                if (sched != null) sched.close();
                if (ownsFurnaceHolder) TestHolder.release(host);
                if (deleteRoot) deleteQuietly(root);
            }
        }
    }

    private static Thread daemon(Runnable r, String name) {
        Thread t = new Thread(r, name);
        t.setDaemon(true);
        return t;
    }

    // ------------------------------------------------------------------ 결정적 틱

    /** 한 틱: 전역 지연 작업 → 전역 틱(함수·보더·날씨·시간) → 모든 region 틱. 다 끝나야 돌아온다. */
    public void tick() { tick(1); }

    /** n 틱을 차례로 돈다. */
    public void tick(int n) {
        checkOpen();
        if (n < 0) throw new IllegalArgumentException("음수 틱: " + n);
        for (int i = 0; i < n; i++) tickOnce();
    }

    private void tickOnce() {
        if (global.isOwnerThread() || org.domi.furnace.server.world.Regionizer.isRegionWorkerThread()) {
            throw new IllegalStateException("tick() 은 시험 스레드에서 부른다(서버 스레드 안에서는 기다릴 수 없다)");
        }
        // 틱 전에 모든 region 이 앞서 받은 작업(시험 스레드가 건 지연 작업 등)을 비우게 한다 — 그 작업은 "지난 틱 뒤"로 세어져,
        // 몇 번째 틱에 붙을지가 벽시계와 상관없이 정해진다
        for (var f : world.forEachRegion(r -> { })) await(f);
        await(global.submit(() -> {
            global.advanceManualTick();
            core.scheduledTick();
            return null;
        }));
        long target = world.advanceManualTick();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(options.timeoutMillis);
        while (!world.manualTickDone(target)) {
            if (System.nanoTime() - deadline > 0) throw new IllegalStateException("region 틱 " + target + " 이 " + options.timeoutMillis + "ms 안에 끝나지 않았다");
            LockSupport.parkNanos(20_000L);
        }
        ticks++;
    }

    /** 이 서버에서 tick() 으로 진행한 틱 수. */
    public long currentTick() { return ticks; }

    /** 월드 게임 시간(바닐라 gameTime — 시작 값에서 틱마다 1). */
    public long gameTime() { return world.gameTime(); }

    // ------------------------------------------------------------------ 기다리기

    /**
     * future 가 끝날 때까지 기다린다(틱은 진행하지 않는다). 서버 스레드에 보낸 일은 틱 사이에도 처리된다.
     * 예외로 끝나면 그 원인을 던진다.
     */
    public <T> T await(CompletableFuture<T> future) {
        try {
            return future.get(options.timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            Throwable c = e.getCause();
            if (c instanceof RuntimeException re) throw re;
            if (c instanceof Error er) throw er;
            throw new IllegalStateException(c);
        } catch (TimeoutException e) {
            throw new IllegalStateException(options.timeoutMillis + "ms 안에 끝나지 않았다", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** 그 칸을 가진 region 스레드에서 task 를 돌리고 결과를 돌려준다(블록·엔티티를 동기 API 로 만질 때). 틱은 진행하지 않는다. */
    public <T> T onRegion(BlockPos pos, Supplier<T> task) {
        return await(overworld().scheduler(pos).submit(task));
    }

    /** 전역 스레드에서 task 를 돌린다. */
    public <T> T onGlobal(Supplier<T> task) {
        return await(global.submit(task));
    }

    /** 청크를 올린다(이미 있으면 그대로). 메모리 월드라 올린 청크는 서버를 닫을 때까지 남는다. */
    public void loadChunk(int chunkX, int chunkZ) {
        checkOpen();
        loadChunkBlocking(chunkX, chunkZ);
    }

    private void loadChunkBlocking(int chunkX, int chunkZ) {
        ChunkPos cp = new ChunkPos(chunkX, chunkZ);
        await(world.submitAtChunk(cp, r -> r.chunkAtBlocking(cp)));
    }

    // ------------------------------------------------------------------ 명령

    /**
     * 콘솔(이름 "Server", 레벨 4 — 운영 서버 콘솔과 같은 발신자)로 명령을 끝까지 돌리고, 그 사이 콘솔에 나온 출력과 결과를 돌려준다.
     * 앞의 '/' 는 있어도 된다. 틱은 진행하지 않는다. 같은 때 다른 스레드가 콘솔에 쓴 메시지(비동기 플러그인 작업)도 섞일 수 있다.
     */
    public CommandOutput execute(String command) {
        checkOpen();
        int from;
        synchronized (consoleLog) { from = consoleLog.size(); }
        var result = await(core.commands().performPrefixedCommandAsync(CommandSource.console(core), command));
        List<Text> out;
        synchronized (consoleLog) { out = List.copyOf(consoleLog.subList(from, consoleLog.size())); }
        return new CommandOutput(result.success(), result.result(), out);
    }

    /** 서버 콘솔에 나온 메시지(브로드캐스트·콘솔 대상 명령 출력)를 시작부터 차례로. */
    public List<Text> consoleMessages() {
        synchronized (consoleLog) { return List.copyOf(consoleLog); }
    }

    // ------------------------------------------------------------------ 플레이어

    /**
     * 가짜 클라이언트로 실제 접속 흐름(핸드셰이크 → 로그인 → 구성 → 플레이)을 거쳐 들어온다. 처음 부르면 루프백 포트를 연다.
     * 접속 처리가 끝나 서버 접속자 목록에 들 때까지 기다린다(틱은 내지 않는다). 기본은 op 가 아니다 — 명령·채팅을 짧은 사이에 10번 넘게
     * 보내면 바닐라 스팸 감지(벽시계)에 쫓겨나니, 그런 시험은 먼저 execute("op 이름") 를 부른다.
     */
    public TestPlayer join(String name) { return join(name, new String[0]); }

    /** {@link #join(String)} 과 같고, 구성 단계에서 그 채널들을 minecraft:register 로 알린다(모드 클라이언트·프록시처럼). */
    public TestPlayer join(String name, String... configChannels) {
        checkOpen();
        ensureNetwork();
        TestPlayer p = TestPlayer.connect(this, port, name, configChannels);
        players.add(p);
        return p;
    }

    private synchronized void ensureNetwork() {
        if (network != null) return;
        ServerLoginContext login = new ServerLoginContext(false, false, false, true, false, false, false, options.maxPlayers, "Furnace test",
                null, null, core.lists(), null, null).core(core);
        network = new NetworkServer(0, -1, login, scheduler, world, world.entityIds()).bindHost("127.0.0.1");   // 오프라인 서버 — 밖에서 못 닿게
        port = network.start();
    }

    /** 지금 접속해 있는 가짜 플레이어. */
    public List<TestPlayer> players() {
        List<TestPlayer> out = new ArrayList<>();
        for (TestPlayer p : players) if (p.isConnected()) out.add(p);
        return out;
    }

    // ------------------------------------------------------------------ API

    /** 플러그인이 보는 서버(Furnace API). */
    public Furnace api() { return host; }

    public World overworld() { return host.world("minecraft:overworld"); }

    /** 불러온 플러그인(이름). 없으면 null. */
    public FurnacePlugin plugin(String name) { return host.pluginsImpl().plugin(name); }

    /** 불러온 플러그인을 메인 클래스로 찾는다. 클래스패스 플러그인은 시험 코드와 같은 클래스라 그대로 캐스트할 수 있다. */
    public <P extends FurnacePlugin> P plugin(Class<P> type) {
        for (FurnacePlugin p : host.pluginsImpl().plugins()) if (type.isInstance(p)) return type.cast(p);
        throw new IllegalArgumentException("불러온 플러그인 중 " + type.getName() + " 이 없다");
    }

    /** 이 서버의 임시 폴더(설정·플레이어 데이터·plugins). */
    public Path directory() { return root; }

    /** 서버 내부(시험 전용 — 공개 API 로 안 되는 확인). 버전 사이 호환은 보장하지 않는다. */
    public ServerCore internals() { return core; }

    long timeoutMillis() { return options.timeoutMillis; }

    void playerLeft(TestPlayer p) { players.remove(p); }

    private void checkOpen() {
        if (closed) throw new IllegalStateException("이미 닫힌 서버");
    }

    // ------------------------------------------------------------------ 닫기

    /**
     * 종료 순서(FurnaceMain ShutdownSequence 와 같다): 종료 이벤트 → 플러그인 끄기(접속자 살아 있음) → 네트워크(접속자를 서버가 끊는다)
     * → region 저장 왕복 → 플레이어 데이터 IO → 저장 풀 → 스케줄러 → 클래스로더 → 임시 폴더. 한 단계가 던져도 나머지는 돈다
     * (시험마다 서버를 새로 띄우므로 스레드가 남으면 쌓인다). 첫 예외는 끝에 다시 던진다.
     */
    @Override
    public void close() {
        if (closed) return;
        closed = true;
        RuntimeException[] first = new RuntimeException[1];
        step(first, host::beginShutdown);
        step(first, () -> host.pluginsImpl().disableAll());
        step(first, () -> { if (network != null) network.stop(); });
        step(first, () -> {   // 서버가 끊은 접속자가 목록에서 빠질 때까지(퇴장 저장이 IO 에 들어간다)
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!core.players().isEmpty() && System.nanoTime() - deadline < 0) LockSupport.parkNanos(1_000_000L);
        });
        step(first, () -> { for (TestPlayer p : List.copyOf(players)) p.closeQuietly(); });
        step(first, () -> { for (var f : world.flushAll()) await(f); });
        step(first, () -> {
            try {
                org.domi.furnace.server.entity.PlayerData.awaitIo(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        step(first, () -> {
            ioPool.shutdown();
            genPool.shutdown();
            try {
                ioPool.awaitTermination(30, TimeUnit.SECONDS);
                genPool.awaitTermination(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        step(first, chunkPool::shutdown);
        step(first, scheduler::close);
        step(first, () -> host.pluginsImpl().closeAll());
        step(first, host::close);
        if (ownsFurnaceHolder) step(first, () -> TestHolder.release(host));
        if (deleteRoot) deleteQuietly(root);
        if (first[0] != null) throw first[0];
    }

    private static void step(RuntimeException[] first, Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            if (first[0] == null) first[0] = e;
            else first[0].addSuppressed(e);
        }
    }

    private static void deleteQuietly(Path dir) {
        try (var s = Files.walk(dir)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }

    // ------------------------------------------------------------------ 설정

    /** 월드 생성기를 직접 고를 때(기본은 평지). */
    @FunctionalInterface
    public interface Generator {
        WorldSource create(BlockRegistry blocks, RegistrySet registries);
    }

    public static final class Builder {
        private final List<Class<? extends FurnacePlugin>> classpathPlugins = new ArrayList<>();
        private final List<Path> pluginJars = new ArrayList<>();
        private List<SuperflatWorldSource.Layer> flatLayers = SuperflatWorldSource.DEFAULT_LAYERS;
        private Generator generator;
        private int spawnY = 64;
        private long seed = 1234L;
        private GameMode gameMode = GameMode.SURVIVAL;
        private Difficulty difficulty = Difficulty.NORMAL;
        private final java.util.Map<String, String> gameRules = new java.util.LinkedHashMap<>();
        private int viewDistance = 2;
        private int spawnChunkRadius = 1;
        private int regionThreads = 1;
        private int chunkThreads = 1;
        private int maxPlayers = 20;
        private boolean failFast = true;
        private boolean compatNms = true;
        private boolean exposeCompatToNative = false;
        /** 시험은 기본으로 네트워크에 나가지 않는다 — 호환 플러그인용 서버 제공 라이브러리·plugin.yml libraries 도 끈다. */
        private org.domi.furnace.server.plugin.library.LibrarySettings libraries = org.domi.furnace.server.plugin.library.LibrarySettings.DEFAULT
                .withCompat(org.domi.furnace.server.plugin.library.LibrarySettings.CompatMode.OFF).withCompatDeclared(false);
        private boolean compatNmsStrict = false;
        private final java.util.Map<String, String> compatExtra = new java.util.HashMap<>();
        private String compatNmsVersionPackage = "plain";
        private long timeoutMillis = DEFAULT_TIMEOUT_MILLIS;
        private String serverProperties = "";
        private Path directory;

        private Builder() {}

        /**
         * 시험 클래스패스에 있는 플러그인. 설명 파일(furnace-plugin.yml 등) 중 main 이 이 클래스인 것을 찾아 쓰고, 없으면
         * 이름=클래스 단순 이름, 버전=test 로 만든다. 클래스는 시험 코드와 같은 것이라 server.plugin(MyPlugin.class) 로 바로 받는다.
         */
        public Builder plugin(Class<? extends FurnacePlugin> main) {
            classpathPlugins.add(Objects.requireNonNull(main, "main"));
            return this;
        }

        /** 빌드한 플러그인 jar 를 그대로(운영 서버와 같은 클래스로더 격리로) 올린다. */
        public Builder pluginJar(Path jar) {
            pluginJars.add(Objects.requireNonNull(jar, "jar"));
            return this;
        }

        /** 평지 층(아래부터). 기본은 바닐라 평지(기반암·흙 2·잔디). */
        public Builder flatLayers(List<SuperflatWorldSource.Layer> layers) {
            this.flatLayers = List.copyOf(layers);
            this.generator = null;
            return this;
        }

        /** 다른 생성기(예: NoiseWorldSource). spawnY 는 그 생성기의 스폰 높이. */
        public Builder generator(Generator g, int spawnY) {
            this.generator = Objects.requireNonNull(g);
            this.spawnY = spawnY;
            return this;
        }

        public Builder seed(long seed) { this.seed = seed; return this; }

        public Builder gameMode(GameMode m) { this.gameMode = Objects.requireNonNull(m); return this; }

        public Builder difficulty(Difficulty d) { this.difficulty = Objects.requireNonNull(d); return this; }

        /** 시작 게임 규칙(예: "doMobSpawning", "false"). */
        public Builder gameRule(String rule, String value) { gameRules.put(rule, value); return this; }

        public Builder viewDistance(int chunks) { this.viewDistance = Math.max(2, chunks); return this; }

        /** 시작할 때 미리 올릴 스폰 둘레 청크 반경(기본 1 = 3×3). */
        public Builder spawnChunkRadius(int r) { this.spawnChunkRadius = Math.max(0, r); return this; }

        /** region 작업 스레드 수(기본 1). 늘려도 틱 결과는 같다 — 떨어진 region 이 병렬로 돌 뿐이다. */
        public Builder regionThreads(int n) { this.regionThreads = Math.max(1, n); return this; }

        public Builder chunkThreads(int n) { this.chunkThreads = Math.max(1, n); return this; }

        public Builder maxPlayers(int n) { this.maxPlayers = n; return this; }

        /** 플러그인 불러오기·켜기 실패로 시작을 멈출지(기본 참 — 시험이 실패를 바로 본다). */
        public Builder failFast(boolean v) { this.failFast = v; return this; }

        /** Bukkit 호환 플러그인의 NMS·CraftBukkit 이름 변환(furnace.properties plugins.compat.nms.enabled, 기본 참). */
        public Builder compatNms(boolean v) { this.compatNms = v; return this; }

        /**
         * true 면 Furnace 네이티브 플러그인의 클래스로더에도 호환 클래스(org.bukkit.*·호환 코어)가 보인다. 기본은 숨김(서버와 같다) —
         * 호환 코어를 네이티브 시험 플러그인으로 직접 부리는 호환 모듈 시험만 켠다.
         */
        public Builder exposeCompatToNative(boolean v) { this.exposeCompatToNative = v; return this; }

        /**
         * 라이브러리 내려받기 설정(furnace.properties plugins.libraries.*·plugins.compat.libraries*). 기본은 모두 꺼짐 — 시험이 네트워크에 기대지 않게
         * 실제 서버의 기본(plugins.compat.libraries=needed, declared=true)과 다르다. Paper 플러그인이 Guava 등을 쓰면 캐시를 채우고 offline 으로 켜라.
         */
        public Builder libraries(org.domi.furnace.server.plugin.library.LibrarySettings v) { this.libraries = java.util.Objects.requireNonNull(v); return this; }
        /** 그 밖의 plugins.compat.* 설정 한 줄(접두어 포함 키, 예: plugins.compat.nms.spigot-mappings=files). */
        public Builder compatOption(String key, String value) {
            if (!key.startsWith("plugins.compat.")) throw new IllegalArgumentException("plugins.compat.* 키만: " + key);
            compatExtra.put(key, java.util.Objects.requireNonNull(value));
            return this;
        }

        /** 미지원 NMS 심볼이 있는 호환 플러그인은 불러오지 않는다(plugins.compat.nms.strict, 기본 거짓). */
        public Builder compatNmsStrict(boolean v) { this.compatNmsStrict = v; return this; }

        /** 플러그인이 읽는 CraftBukkit 패키지 이름: plain(기본, Paper) 또는 versioned(Spigot, v1_21_R1) — plugins.compat.nms.version-package. */
        public Builder compatNmsVersionPackage(String v) { this.compatNmsVersionPackage = java.util.Objects.requireNonNull(v); return this; }

        /** 틱·future 기다림 한도. */
        public Builder timeout(long millis) { this.timeoutMillis = Math.max(1, millis); return this; }

        /** server.properties 에 덧붙일 줄(예: "enable-command-block=true"). online-mode 는 늘 false. */
        public Builder serverProperty(String key, String value) {
            this.serverProperties += key + "=" + value + "\n";
            return this;
        }

        /** 임시 폴더 대신 이 폴더를 쓴다(닫아도 지우지 않는다 — 실패한 시험의 파일을 볼 때). */
        public Builder directory(Path dir) { this.directory = dir; return this; }

        public FurnaceTestServer start() {
            try {
                return new FurnaceTestServer(this);
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }
    }
}
