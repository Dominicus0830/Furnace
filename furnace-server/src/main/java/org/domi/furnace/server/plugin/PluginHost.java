// 플러그인이 보는 서버(Furnace 파사드) — 이벤트·명령·권한·플러그인 관리자·스케줄러를 한데 묶는다.
package org.domi.furnace.server.plugin;

import org.domi.furnace.api.GameVersion;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.api.command.CommandRegistry;
import org.domi.furnace.api.command.CommandSender;
import org.domi.furnace.api.event.EventBus;
import org.domi.furnace.api.permission.PermissionManager;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.plugin.PluginManager;
import org.domi.furnace.api.sched.FurnaceScheduler;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.entity.Player;

public final class PluginHost implements Furnace {

    private final ServerCore core;
    /** 플러그인별 시간(/timings). 전역 스레드 판별은 core 가 생긴 뒤에 쓰이므로 람다로 미룬다. */
    private final PluginProfilerImpl profiler = new PluginProfilerImpl(this::onGlobalThread);
    private final ProfiledScheduling profiled = new ProfiledScheduling(profiler, PluginManagerImpl::pluginOf);
    private final EventBusImpl events = new EventBusImpl(profiler);
    private final PermissionManagerImpl permissions = new PermissionManagerImpl();
    private final CommandRegistryImpl commands = new CommandRegistryImpl(this);
    private final ConsoleSender console;
    private final PluginManagerImpl plugins;
    /** 플러그인 비동기 실행기: plugins.async-threads(platform 기본 | virtual) — T-034, 결정 D1. */
    private final PluginAsyncExecutor async = new PluginAsyncExecutor();
    /**
     * 서버가 내는 접속 단계 비동기 이벤트(AsyncPlayerPreLoginEvent·AsyncPlayerConfigureEvent) 전용 플랫폼 풀(T-034).
     * Paper 1.21.1 ServerLoginPacketListenerImpl.authenticatorPool 과 같은 모양: 접속마다 놀고 있는 스레드가 없으면 새 스레드
     * "User Authenticator #N"(0부터), 60초 놀면 정리. 플러그인 비동기 작업과 나눠 두어, 그쪽이 밀리거나 운반 스레드가 붙잡혀도
     * 접속이 줄 서지 않는다(B6 1.7: 접속 전 처리기 2초 + 16명 = 4명씩 4물결 9.1초 → 모두 약 2초).
     */
    private final ExecutorService loginEvents = newLoginEventPool();
    /** 플러그인에 주는 실행기·스케줄러 — 작업 시간을 만든 플러그인 몫으로 잰다. */
    private final ExecutorService profiledAsync = profiled.wrap(async);
    private volatile FurnaceScheduler profiledScheduler;

    public PluginHost(ServerCore core, Path pluginsDir) {
        this.core = core;
        org.domi.furnace.api.item.ItemSupport.install(ItemSupportImpl.INSTANCE);
        this.console = new ConsoleSender(core, permissions);
        this.plugins = new PluginManagerImpl(this, pluginsDir);
        if (PluginProfilerImpl.enabledByConfig()) profiler.start();
    }

    private static ExecutorService newLoginEventPool() {
        java.util.concurrent.atomic.AtomicInteger n = new java.util.concurrent.atomic.AtomicInteger();
        return Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, LOGIN_EVENT_THREAD_PREFIX + n.getAndIncrement());
            t.setDaemon(true);   // 처리기가 멈춰도 JVM 종료를 붙잡지 않게
            return t;
        });
    }

    /** 접속 단계 이벤트 스레드 이름 앞부분(Paper 와 같은 이름 — 플러그인 로그·스레드 덤프에서 같게 보인다). */
    public static final String LOGIN_EVENT_THREAD_PREFIX = "User Authenticator #";

    /** 접속 단계 비동기 이벤트(사전 로그인·구성)를 부를 실행기 — 서버 내부 전용, 플러그인에 주지 않는다. 닫혔으면 RejectedExecutionException. */
    public ExecutorService loginEventExecutor() { return loginEvents; }

    /** 플러그인 비동기 실행기 본체(감싸지 않은 것) — 설정 적용·시험용. */
    public PluginAsyncExecutor asyncPool() { return async; }

    private boolean onGlobalThread() {
        return core != null && core.scheduler() != null && core.scheduler().global() instanceof org.domi.furnace.api.concurrent.ThreadOwner o
                && o.isOwnerThread();
    }

    @Override
    public PluginProfilerImpl profiler() { return profiler; }

    /** 플러그인에 주는 스케줄러·실행기를 감싸는 곳(핸들들이 쓴다). */
    ProfiledScheduling profiled() { return profiled; }

    public ServerCore core() { return core; }

    @Override
    public org.domi.furnace.api.world.ServerTps tps() { return core.serverTps(); }

    public EventBusImpl eventsImpl() { return events; }

    PermissionManagerImpl permissionsImpl() { return permissions; }

    CommandRegistryImpl commandsImpl() { return commands; }

    public PluginManagerImpl pluginsImpl() { return plugins; }

    private final org.domi.furnace.server.plugin.packet.PacketServiceImpl packets = new org.domi.furnace.server.plugin.packet.PacketServiceImpl(this,
            new org.domi.furnace.server.plugin.packet.ProtocolTable(() -> core().world().blockRegistry(), () -> core().world().itemRegistry()));

    /** 패킷 API 구현(연결에 가로채기 지점을 붙이는 쪽). */
    public org.domi.furnace.server.plugin.packet.PacketServiceImpl packetsImpl() { return packets; }

    @Override
    public org.domi.furnace.api.packet.PacketService packets() { return packets; }

    private final VirtualEntitiesImpl virtualEntities = new VirtualEntitiesImpl(this);

    /** 가상 엔티티·가짜 플레이어·가짜 탭 줄 구현(플러그인이 꺼질 때 정리를 부르는 쪽). */
    public VirtualEntitiesImpl virtualImpl() { return virtualEntities; }

    @Override
    public org.domi.furnace.api.virtual.VirtualEntities virtualEntities() { return virtualEntities; }

    private final LightSourcesImpl lightSources = new LightSourcesImpl(this);

    /** 가짜 광원 구현(플러그인이 꺼질 때 정리를 부르는 쪽). */
    public LightSourcesImpl lightSourcesImpl() { return lightSources; }

    @Override
    public org.domi.furnace.api.light.LightSources lightSources() { return lightSources; }

    private final ChunkTicketsImpl chunkTickets = new ChunkTicketsImpl(this);

    /** 청크 티켓 구현(플러그인이 꺼질 때 정리를 부르는 쪽). */
    public ChunkTicketsImpl chunkTicketsImpl() { return chunkTickets; }

    @Override
    public org.domi.furnace.api.world.ChunkTickets chunkTickets() { return chunkTickets; }

    private final StructuresImpl structures = new StructuresImpl(this);

    /** 구조물 템플릿 구현. */
    public StructuresImpl structuresImpl() { return structures; }

    @Override
    public org.domi.furnace.api.structure.Structures structures() { return structures; }

    private final org.domi.furnace.api.nbt.NbtDataFixer dataFixer = new NbtDataFixerImpl();

    @Override
    public org.domi.furnace.api.nbt.NbtDataFixer dataFixer() { return dataFixer; }

    private final EntityViewsImpl entityViews = new EntityViewsImpl(this);

    /** 보이기·숨기기·변장 구현(플러그인이 꺼질 때 정리를 부르는 쪽). */
    public EntityViewsImpl entityViewsImpl() { return entityViews; }

    @Override
    public org.domi.furnace.api.entity.EntityViews entityViews() { return entityViews; }

    private final PlayerViewsImpl playerViews = new PlayerViewsImpl(this);

    /** 플레이어별 가상 블록·바이옴·보더·장비 구현(플러그인이 꺼질 때 정리를 부르는 쪽). */
    public PlayerViewsImpl playerViewsImpl() { return playerViews; }

    @Override
    public org.domi.furnace.api.view.PlayerViews playerViews() { return playerViews; }

    private final PluginGameEvents gameEvents = new PluginGameEvents(this);
    private final java.util.concurrent.ConcurrentHashMap<String, WorldHandle> worldHandles = new java.util.concurrent.ConcurrentHashMap<>();

    /** 서버 게임 코드가 부르는 이벤트 입구. */
    public GameEvents gameEvents() { return gameEvents; }

    /** 그 차원의 월드 핸들(없는 차원이면 null). */
    public WorldHandle worldHandle(String dimension) {
        org.domi.furnace.server.world.World w = core.world(dimension);
        return w == null ? null : handleFor(w);
    }

    /** 서버 월드의 손잡이 — 같은 키로 다시 올린 추가 월드면 새 손잡이(내린 월드의 손잡이는 isLoaded() = false 로 남는다). */
    WorldHandle handleFor(org.domi.furnace.server.world.World w) {
        // 빠른 길(T-071): 이미 이 월드의 손잡이가 있으면 compute 의 bin 잠금 없이 읽기만 한다. 어댑터 호출 거의 전부가 이 길을
        // 지나서 compute 는 스레드를 늘릴수록 처리량이 줄었다(O13 보고서 실서버 측정 1→4 스레드 17→2.8 M/s, 이 PR 의 HandleForBench 는 따로 잰 값). 없거나 다른(내린) 월드의 낡은 손잡이일
        // 때만 compute — 결과는 전과 같다(같은 World 면 늘 같은 손잡이, 새로 올린 월드면 새 손잡이로 교체, 경합해도 하나로 수렴).
        String dim = w.dimension();
        WorldHandle cur = worldHandles.get(dim);
        if (cur != null && cur.handle() == w) return cur;
        return worldHandles.compute(dim, (k, old) -> old != null && old.handle() == w ? old : new WorldHandle(this, w));
    }

    @Override
    public WorldHandle world(String dimension) { return worldHandle(dimension); }

    // ------------------------------------------------------------------ 다중 월드(W1)

    @Override
    public java.util.concurrent.CompletableFuture<org.domi.furnace.api.world.World> createWorld(org.domi.furnace.api.world.WorldCreator creator) {
        if (creator == null) throw new NullPointerException("creator");
        var m = core.worldManager();
        if (m == null) return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException("multiple worlds are not enabled on this server"));
        return m.create(creator).thenApply(this::handleFor);
    }

    @Override
    public org.domi.furnace.api.world.World createWorldNow(org.domi.furnace.api.world.WorldCreator creator) {
        if (creator == null) throw new NullPointerException("creator");
        var m = core.worldManager();
        if (m == null) throw new UnsupportedOperationException("multiple worlds are not enabled on this server");
        try {
            return handleFor(m.createAndWait(creator));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("world '" + creator.name() + "' was interrupted while waiting", e);
        }
    }

    @Override
    public org.domi.furnace.api.worldgen.WorldGeneratorSetting configuredWorldGenerator(String worldName) {
        if (worldName == null) return org.domi.furnace.api.worldgen.WorldGeneratorSetting.NONE;
        var s = core.worldSettings();
        return new org.domi.furnace.api.worldgen.WorldGeneratorSetting(s.generatorByName(worldName), s.biomeProviderByName(worldName));
    }

    @Override
    public org.domi.furnace.api.world.WorldCreator storedWorldCreator(String name) {
        if (name == null) throw new NullPointerException("name");
        var m = core.worldManager();
        if (m == null || !name.matches("[A-Za-z0-9_.-]{1,64}") || name.equals(".") || name.equals("..")) return null;   // 폴더 밖을 읽지 않게
        try {
            return m.storedCreator(name);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("world '" + name + "': could not read level.dat", e);
        }
    }

    @Override
    public java.util.concurrent.CompletableFuture<Boolean> unloadWorld(org.domi.furnace.api.world.World world, boolean save) {
        if (world == null) throw new NullPointerException("world");
        var m = core.worldManager();
        if (m == null) return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException("multiple worlds are not enabled on this server"));
        if (!(world instanceof WorldHandle h) || h.handle().isUnloaded()) return java.util.concurrent.CompletableFuture.completedFuture(false);
        return m.unload(h.handle(), save);
    }

    /** 추가 월드가 올라왔다(전역 스레드) — WorldInitEvent 뒤 WorldLoadEvent(Paper initWorld → loadWorld 순서). */
    public void worldLoaded(org.domi.furnace.server.world.World w) {
        events.call(new org.domi.furnace.api.event.world.WorldInitEvent(w.dimension(), w.levelName()));
        events.call(new org.domi.furnace.api.event.world.WorldLoadEvent(w.dimension(), w.levelName()));
    }

    /** 추가 월드를 내리기 직전(전역 스레드) — WorldUnloadEvent. 취소되면 false. */
    public boolean worldUnloading(org.domi.furnace.server.world.World w, boolean save) {
        var ev = new org.domi.furnace.api.event.world.WorldUnloadEvent(w.dimension(), w.levelName(), save);
        events.call(ev);
        return !ev.isCancelled();
    }

    /** 내린 월드의 손잡이를 걷는다(다시 같은 키로 올리면 새 손잡이). */
    public void worldDetached(org.domi.furnace.server.world.World w) {
        worldHandles.computeIfPresent(w.dimension(), (k, h) -> h.handle() == w ? null : h);
    }

    /** 켜져 있는 플러그인이 등록한 차원 타입 정의(W2), 없거나 꺼졌으면 null. */
    public org.domi.furnace.api.worldgen.DimensionTypeDefinition pluginDimensionType(String key) {
        var store = plugins.gameRegistries();
        var e = store == null ? null : store.dimensionType(key);
        return e != null && e.active() ? e.definition() : null;
    }

    /** 그 키의 월드젠 단계 훅(W3) — 없거나 플러그인이 꺼졌으면 null. 아무 스레드. */
    public org.domi.furnace.api.worldgen.ChunkGenerationHooks generationHooks(String key) {
        var store = plugins.generationHooks();
        return store == null ? null : store.get(key);
    }

    private final PluginRecipes recipes = new PluginRecipes(this);
    private final GameRegistriesImpl gameRegistries = new GameRegistriesImpl();
    private final GameRuleRegistryImpl gameRules = new GameRuleRegistryImpl(this);
    private final DifficultySettingsImpl difficulty = new DifficultySettingsImpl(this);
    private final DataPacksImpl dataPacks = new DataPacksImpl(this);

    @Override
    public org.domi.furnace.api.datapack.DataPacks dataPacks() { return dataPacks; }

    @Override
    public org.domi.furnace.api.registry.GameRegistries gameRegistries() { return gameRegistries; }

    @Override
    public GameRuleRegistryImpl gameRules() { return gameRules; }

    @Override
    public DifficultySettingsImpl difficulty() { return difficulty; }

    @Override
    public PluginRecipes recipes() { return recipes; }

    private final ServiceRegistryImpl services = new ServiceRegistryImpl(this);
    private final AccessListsImpl access = new AccessListsImpl(this);
    private final TranslationServiceImpl translations = new TranslationServiceImpl();

    @Override
    public TranslationServiceImpl translations() { return translations; }

    @Override
    public ServiceRegistryImpl services() { return services; }

    @Override
    public AccessListsImpl access() { return access; }

    @Override
    public org.domi.furnace.api.entity.OfflinePlayer offlinePlayer(UUID id) { return new OfflinePlayerImpl(this, id); }

    @Override
    public org.domi.furnace.api.entity.OfflinePlayer offlinePlayerCached(String name) {
        Player online = core.playerByName(name);
        if (online != null) return new OfflinePlayerImpl(this, online.uuid());
        // get(String) 은 모르는 이름이면 인증 서버를 부르거나 오프라인 프로필을 만들어 캐시에 넣는다 — 캐시에 있는 이름만 본다
        return core.profileCache().peek(name).map(p -> (org.domi.furnace.api.entity.OfflinePlayer) new OfflinePlayerImpl(this, p.id())).orElse(null);
    }

    // ------------------------------------------------------------------ 프로필 조회(사용자 캐시·세션 서버)

    private static org.domi.furnace.api.entity.ProfileData profileData(org.domi.furnace.server.players.GameProfile p) {
        return new org.domi.furnace.api.entity.ProfileData(p.id(), p.name(), p.properties().stream()
                .map(x -> new org.domi.furnace.api.entity.ProfileProperty(x.name(), x.value(), x.signature())).toList());
    }

    @Override
    public java.util.Optional<org.domi.furnace.api.entity.ProfileData> profileByName(String name) {
        Player online = core.playerByName(name);
        if (online != null) return java.util.Optional.of(profileData(ServerCore.profileOf(online)));
        // GameProfileCache.get: 캐시, 없으면 오프라인 모드에서만 오프라인 프로필을 만들어 넣는다(조회기는 NONE 이라 인증 서버에 묻지 않는다)
        return core.profileCache().get(name).map(PluginHost::profileData);
    }

    @Override
    public java.util.Optional<org.domi.furnace.api.entity.ProfileData> profileById(UUID id) {
        Player online = core.playerByUuid(id);
        if (online != null) return java.util.Optional.of(profileData(ServerCore.profileOf(online)));
        return core.profileCache().get(id).map(PluginHost::profileData);
    }

    @Override
    public java.util.concurrent.CompletableFuture<java.util.Optional<org.domi.furnace.api.entity.ProfileData>> fetchProfileByName(String name) {
        // 접속자·사용자 캐시에 있으면 그것(오프라인 프로필을 만들어 넣지 않는다 — 프록시 뒤 오프라인 서버도 모르는 이름은 Mojang 에 물어야 한다)
        Player online = core.playerByName(name);
        var cached = online != null ? java.util.Optional.of(profileData(ServerCore.profileOf(online)))
                : core.profileCache().peek(name).map(PluginHost::profileData);
        if (cached.isPresent()) return java.util.concurrent.CompletableFuture.completedFuture(cached);
        if (!org.domi.furnace.server.players.GameProfile.isValidPlayerName(name)) {
            return java.util.concurrent.CompletableFuture.completedFuture(java.util.Optional.empty());
        }
        return core.sessionService().lookupByName(name).thenApply(found -> {
            found.ifPresent(core.profileCache()::add);
            return found.map(PluginHost::profileData);
        });
    }

    @Override
    public java.util.concurrent.CompletableFuture<java.util.Optional<org.domi.furnace.api.entity.ProfileData>> fetchProfile(UUID id) {
        return core.sessionService().fetchProfile(id).thenApply(found -> {
            found.ifPresent(core.profileCache()::add);
            return found.map(PluginHost::profileData);
        });
    }

    @Override
    public boolean verifyProfileProperty(String name, String value, String signature) {
        return core.servicesKeys().validProfileProperty(value, signature);
    }

    @Override
    public java.util.concurrent.CompletableFuture<List<org.domi.furnace.api.entity.OfflinePlayer>> knownPlayers() {
        RegionFuture<List<org.domi.furnace.api.entity.OfflinePlayer>> out = new RegionFuture<>();
        org.domi.furnace.server.entity.PlayerData.io().execute(() -> {
            try {
                List<org.domi.furnace.api.entity.OfflinePlayer> all = new ArrayList<>();
                for (UUID id : org.domi.furnace.server.entity.PlayerData.knownIds(core.world().worldDir())) all.add(new OfflinePlayerImpl(this, id));
                out.complete(List.copyOf(all));
            } catch (Throwable t) {
                out.completeExceptionally(t);
            }
        });
        return out;
    }

    private volatile BlockTypesImpl blockTypes;

    @Override
    public BlockTypesImpl blockTypes() {
        BlockTypesImpl b = blockTypes;
        if (b == null) blockTypes = b = new BlockTypesImpl(core.world().blockRegistry());
        return b;
    }

    @Override
    public List<WorldHandle> worlds() {
        List<WorldHandle> out = new ArrayList<>();
        for (var w : core.worlds()) out.add(worldHandle(w.dimension()));
        return out;
    }

    /** 서버 플레이어의 API 손잡이(처음 요청할 때 만든다). */
    public PlayerHandle player(Player p) {
        Object h = p.apiHandle();
        if (h instanceof PlayerHandle ph) return ph;
        synchronized (p) {
            if (!(p.apiHandle() instanceof PlayerHandle)) p.apiHandle(new PlayerHandle(p, this));
            return (PlayerHandle) p.apiHandle();
        }
    }

    /** 명령 소스 → API 발신자. */
    CommandSender sender(CommandSource src) {
        return src.getPlayer() != null ? player(src.getPlayer()) : console;
    }

    /** API 발신자 → 명령 소스. */
    CommandSource source(CommandSender sender) {
        if (sender instanceof PlayerHandle ph) return CommandSource.player(core, ph.handle());
        return CommandSource.console(core);
    }

    // ------------------------------------------------------------------ 플러그인 채널(minecraft:register)

    /** 플러그인 → 그 플러그인이 받겠다고 등록한 채널. */
    private final java.util.concurrent.ConcurrentHashMap<FurnacePlugin, java.util.Set<String>> channelOwners = new java.util.concurrent.ConcurrentHashMap<>();

    private static String checkChannel(String channel) {
        org.domi.furnace.server.data.ResourceId id = org.domi.furnace.server.data.ResourceId.tryParse(channel);
        if (id == null || !id.toString().equals(channel)) throw new IllegalArgumentException("invalid channel name: " + channel);
        if (channel.equals(org.domi.furnace.server.net.PluginChannels.REGISTER) || channel.equals(org.domi.furnace.server.net.PluginChannels.UNREGISTER)
                || channel.equals("minecraft:brand")) throw new IllegalArgumentException("reserved channel: " + channel);
        return channel;
    }

    @Override
    public int viewDistance() { return core.viewDistance(); }

    @Override
    public void setViewDistance(int chunks) { core.setViewDistance(chunks); }

    @Override
    public int simulationDistance() { return core.simulationDistance(); }

    @Override
    public void setSimulationDistance(int chunks) { core.setSimulationDistance(chunks); }

    @Override
    public synchronized void registerPluginChannel(FurnacePlugin owner, String channel) {
        java.util.Objects.requireNonNull(owner, "owner");
        checkChannel(channel);
        boolean fresh = !pluginChannels().contains(channel);
        channelOwners.computeIfAbsent(owner, k -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(channel);
        if (fresh) broadcastChannels(org.domi.furnace.server.net.PluginChannels.REGISTER, java.util.List.of(channel));
    }

    @Override
    public synchronized void unregisterPluginChannel(FurnacePlugin owner, String channel) {
        java.util.Set<String> mine = channelOwners.get(owner);
        if (mine == null || !mine.remove(channel)) return;
        if (mine.isEmpty()) channelOwners.remove(owner);
        if (!pluginChannels().contains(channel)) broadcastChannels(org.domi.furnace.server.net.PluginChannels.UNREGISTER, java.util.List.of(channel));
    }

    @Override
    public java.util.SortedSet<String> pluginChannels() {
        java.util.TreeSet<String> all = new java.util.TreeSet<>();
        for (java.util.Set<String> s : channelOwners.values()) all.addAll(s);
        return java.util.Collections.unmodifiableSortedSet(all);
    }

    /** 플러그인이 꺼질 때 그 플러그인의 채널을 모두 내린다(다른 플러그인이 같은 채널을 쓰면 남는다). */
    synchronized void removeChannels(FurnacePlugin plugin) {
        java.util.Set<String> mine = channelOwners.remove(plugin);
        if (mine == null) return;
        java.util.Set<String> left = pluginChannels();
        java.util.List<String> gone = new java.util.ArrayList<>();
        for (String c : mine) if (!left.contains(c)) gone.add(c);
        if (!gone.isEmpty()) broadcastChannels(org.domi.furnace.server.net.PluginChannels.UNREGISTER, gone);
    }

    /** 접속 직후 한 번: 서버가 받는 채널이 있으면 minecraft:register 로 알린다(없으면 보내지 않는다). */
    public void sendRegisteredChannels(Player p) {
        java.util.Set<String> all = pluginChannels();
        if (all.isEmpty()) return;
        p.send(new org.domi.furnace.server.net.protocol.play.CustomPayloadPacket(org.domi.furnace.server.net.PluginChannels.REGISTER,
                org.domi.furnace.server.net.PluginChannels.encode(all), true));
    }

    private void broadcastChannels(String which, java.util.Collection<String> names) {
        byte[] body = org.domi.furnace.server.net.PluginChannels.encode(names);
        for (Player p : core.players()) p.send(new org.domi.furnace.server.net.protocol.play.CustomPayloadPacket(which, body, true));
    }

    /** 플러그인이 꺼질 때 그 플러그인이 남긴 권한 첨부를 모두 뗀다. */
    void removeAttachments(FurnacePlugin plugin) {
        console.perms().removeAttachments(plugin);
        for (Player p : core.players()) if (p.apiHandle() instanceof PlayerHandle ph) ph.perms().removeAttachments(plugin);
    }

    // ------------------------------------------------------------------ Furnace

    @Override
    public String name() { return "Furnace"; }

    @Override
    public String version() {
        String v = PluginHost.class.getPackage().getImplementationVersion();
        return v != null ? v : "dev";
    }

    @Override
    public String minecraftVersion() { return GameVersion.MINECRAFT; }

    /** 접속자 목록 캐시 한 칸: 만든 때의 서버 접속자 배열(동일성)·roster 번호와 그 결과(고칠 수 없는 목록). */
    private record OnlineSnapshot(Player[] source, long epoch, List<PlayerHandle> handles) {}

    private volatile OnlineSnapshot onlineSnapshot;

    /**
     * 접속 중(끊기지 않은) 플레이어, 서버 접속 순(Paper getOnlinePlayers 와 같다 — 리스폰한 플레이어는 끝으로).
     *
     * <p>고칠 수 없는 목록이고, 접속자 집합이 그대로이면 <b>같은 객체</b>를 돌려준다(부른 쪽이 객체 동일성으로 "안 바뀜"을 알 수 있다).
     * 접속·퇴장·리스폰은 서버 접속자 배열을 바꾸고, 끊김 표시({@link Player#markDisconnected})는 {@link Player#rosterEpoch} 를 올리므로
     * 둘 중 하나라도 달라지면 다시 만든다 — 다시 만들기는 번호를 먼저 읽은 뒤 훑으므로 그 사이 바뀐 것은 다음 호출에서 반영된다.
     * 끊김 표시와 번호 상승 사이의 한 걸음 동안에는 방금 끊긴 플레이어가 한 번 더 보일 수 있다(퇴장 처리가 비동기인 것과 같은 성질).
     */
    @Override
    public List<PlayerHandle> onlinePlayers() {
        long epoch = Player.rosterEpoch();   // 먼저 읽는다: 이 뒤에 바뀌면 번호가 앞서가 다음 호출이 다시 만든다
        Player[] src = core.world().onlineArray();
        OnlineSnapshot s = onlineSnapshot;
        if (s != null && s.source == src && s.epoch == epoch) return s.handles;
        PlayerHandle[] out = new PlayerHandle[src.length];
        int n = 0;
        for (Player p : src) if (!p.isDisconnected()) out[n++] = player(p);
        List<PlayerHandle> handles = java.util.Collections.unmodifiableList(java.util.Arrays.asList(java.util.Arrays.copyOf(out, n)));
        onlineSnapshot = new OnlineSnapshot(src, epoch, handles);
        return handles;
    }

    @Override
    public PlayerHandle player(UUID id) {
        Player p = core.playerByUuid(id);
        return p == null ? null : player(p);
    }

    @Override
    public PlayerHandle player(String name) {
        Player p = core.playerByName(name);
        return p == null ? null : player(p);
    }

    @Override
    public int maxPlayers() { return core.settings().get().maxPlayers; }

    @Override
    public Text motd() { return org.domi.furnace.server.net.Motd.parse(core.settings().get().motd); }

    @Override
    public CommandSender console() { return console; }

    /** RCON 명령 하나의 발신자 — 보낸 메시지는 out 으로(그 요청의 응답 버퍼). */
    public org.domi.furnace.api.command.RemoteConsoleSender rconSender(java.util.function.Consumer<org.domi.furnace.api.text.Text> out) {
        return new RconSender(permissions, out);
    }

    @Override
    public void broadcast(Text message) { core.broadcastSystemMessage(message, false); }

    @Override
    public boolean dispatchCommand(CommandSender sender, String commandLine) { return commands.dispatch(sender, commandLine); }

    @Override
    public FurnaceScheduler scheduler() {
        FurnaceScheduler s = profiledScheduler;
        if (s == null) profiledScheduler = s = profiled.wrap(core.scheduler());
        return s;
    }

    @Override
    public ExecutorService asyncExecutor() { return profiledAsync; }

    @Override
    public boolean bungeeCordForwarding() { return core.bungeeCordForwarding(); }

    @Override
    public void consoleInputHandler(java.util.function.Consumer<String> handler) { core.consoleInputHandler(handler); }

    @Override
    public boolean profileLookups() { return core.profileCache().profileLookups(); }

    @Override
    public boolean onlineMode() {
        var s = core.settings();
        return s != null && s.get().onlineMode;   // server.properties 가 없는 시험 서버는 오프라인 접속만 받는다
    }

    /** {@link org.domi.furnace.api.internal.Internals#compatOption} 의 값(다리 {@link InternalsImpl} 가 부른다). */
    public String compatOption(String key) {
        return key != null && key.startsWith("plugins.compat.") ? pluginsImpl().settings().compatOption(key) : null;
    }

    @Override
    public EventBus events() { return events; }

    @Override
    public CommandRegistry commands() { return commands; }

    @Override
    public PermissionManager permissions() { return permissions; }

    @Override
    public PluginManager plugins() { return plugins; }

    @Override
    public void shutdown() { core.halt(); }

    @Override
    public long currentTick() { return core.serverTickCount(); }

    private volatile boolean stopping;

    @Override
    public boolean isStopping() { return stopping; }

    /** 종료 1단계: 종료 중 표시를 켜고 ServerShutdownEvent 를 한 번 부른다(두 번째 호출은 아무것도 안 한다). */
    public void beginShutdown() {
        synchronized (this) {
            if (stopping) return;
            stopping = true;
        }
        events.call(new org.domi.furnace.api.event.server.ServerShutdownEvent());
    }

    private final InternalsImpl internals = new InternalsImpl(this);

    /** 공식 내부 접근 다리. 처음 부른 플러그인은 로그에 남는다. */
    @Override
    public InternalsImpl internals() {
        internals.noteCaller();
        return internals;
    }

    /** 서버 종료 마지막 — 비동기 실행기·접속 이벤트 실행기를 닫는다(도는 작업은 끝까지 간다). */
    public void close() {
        async.shutdown();
        loginEvents.shutdown();
    }
}
