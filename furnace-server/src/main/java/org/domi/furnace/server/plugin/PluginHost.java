// 플러그인이 보는 서버(Furnace 파사드) — 이벤트·명령·권한·플러그인 관리자·스케줄러를 한데 묶는다.
package org.domi.furnace.server.plugin;

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
    private final ExecutorService async = Executors.newVirtualThreadPerTaskExecutor();
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
            new org.domi.furnace.server.plugin.packet.Protocol767(() -> core().world().blockRegistry()));

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
        return worldHandles.compute(w.dimension(), (k, old) -> old != null && old.handle() == w ? old : new WorldHandle(this, w));
    }

    @Override
    public WorldHandle world(String dimension) { return worldHandle(dimension); }

    // ------------------------------------------------------------------ 다중 월드(W1)

    @Override
    public java.util.concurrent.CompletableFuture<org.domi.furnace.api.world.World> createWorld(org.domi.furnace.api.world.WorldCreator creator) {
        if (creator == null) throw new NullPointerException("creator");
        var m = core.worldManager();
        if (m == null) return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException("이 서버는 다중 월드를 켜지 않았다"));
        return m.create(creator).thenApply(this::handleFor);
    }

    @Override
    public java.util.concurrent.CompletableFuture<Boolean> unloadWorld(org.domi.furnace.api.world.World world, boolean save) {
        if (world == null) throw new NullPointerException("world");
        var m = core.worldManager();
        if (m == null) return java.util.concurrent.CompletableFuture.failedFuture(new UnsupportedOperationException("이 서버는 다중 월드를 켜지 않았다"));
        if (!(world instanceof WorldHandle h) || h.handle().isUnloaded()) return java.util.concurrent.CompletableFuture.completedFuture(false);
        return m.unload(h.handle(), save);
    }

    /** 추가 월드가 올라왔다(전역 스레드) — WorldLoadEvent. */
    public void worldLoaded(org.domi.furnace.server.world.World w) {
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
        if (id == null || !id.toString().equals(channel)) throw new IllegalArgumentException("잘못된 채널 이름: " + channel);
        if (channel.equals(org.domi.furnace.server.net.PluginChannels.REGISTER) || channel.equals(org.domi.furnace.server.net.PluginChannels.UNREGISTER)
                || channel.equals("minecraft:brand")) throw new IllegalArgumentException("예약된 채널: " + channel);
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
    public String minecraftVersion() { return "1.21.1"; }

    @Override
    public Collection<PlayerHandle> onlinePlayers() {
        List<PlayerHandle> out = new ArrayList<>();
        for (Player p : core.players()) out.add(player(p));
        return out;
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
    public EventBus events() { return events; }

    @Override
    public CommandRegistry commands() { return commands; }

    @Override
    public PermissionManager permissions() { return permissions; }

    @Override
    public PluginManager plugins() { return plugins; }

    @Override
    public void shutdown() { core.halt(); }

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

    /** 서버 종료 마지막 — 비동기 실행기를 닫는다. */
    public void close() { async.shutdown(); }
}
