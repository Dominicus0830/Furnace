package net.minecraft.server.level;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import net.minecraft.world.entity.Entity;
import org.domi.furnace.compat.paper.Compat;

/**
 * 서버 월드 shim(Mojang 이름, H6 최소 표면): {@code ((CraftWorld) w).getHandle()}. 같은 CraftWorld 는 같은 shim 이다.
 * 블록 읽기·쓰기·청크 얻기(N1·N2a)는 {@link Level} 에 있다.
 */
@org.domi.furnace.compat.nms.NmsInheritable   // 편집 내용을 답하는 레벨 프록시(FAWE PaperweightLevelProxy — Unsafe 로 만들어 필드는 null, 읽기만 재정의), //regen 임시 레벨(PaperweightRegen — 15 인자 생성자, save 재정의)
public class ServerLevel extends Level {

    private final ServerChunkCache chunkSource = new ServerChunkCache(this);

    /** 바닐라(CraftBukkit 이 연) 레벨 데이터 칸. 서버 월드는 그 월드에서 그때그때 읽는 값({@link net.minecraft.world.level.storage.PrimaryLevelData#of}). */
    public final net.minecraft.world.level.storage.PrimaryLevelData serverLevelData;
    /** 바닐라 저장 끔 칸(/save-off). Furnace 서버 월드의 저장은 서버가 정한다 — 이 칸은 {@link #save} 의 기본 인자일 뿐이다. */
    public boolean noSave;
    /** 플러그인이 만든 따로 떨어진 레벨(T-203 임시 월드)이면 받은 차원 키, 아니면 null. */
    private final net.minecraft.resources.ResourceKey<Level> stemKey;
    private final boolean scratch;
    private final boolean debug;
    private volatile net.minecraft.world.RandomSequences randomSequences;
    private volatile net.minecraft.core.Holder<net.minecraft.world.level.dimension.DimensionType> dimensionType;

    /** 호환 모듈 전용(CraftWorld 가 만든다). */
    public ServerLevel(CraftWorld world) {
        super(world);
        this.serverLevelData = net.minecraft.world.level.storage.PrimaryLevelData.of(world);
        this.stemKey = null;
        this.scratch = false;
        this.debug = false;
    }

    /**
     * Paper 의 레벨 생성자(15 인자, CraftBukkit 이 환경·Bukkit 생성기·바이옴 공급자를 더한 것) — 플러그인이 따로 떨어진 레벨을 만들 때 쓴다
     * (FAWE {@code //regen} 의 임시 레벨). Furnace 는 stem 의 생성기가 가리키는 월드와 같은 차원 타입·생성 설정, 시드 seed 의 <b>임시 월드</b>를 만든다:
     * 처음 상태의 생성기로 청크를 만들고, 게임 틱이 돌지 않으며(Paper 의 그 레벨도 서버 레벨 목록에 없어 틱을 돌지 않는다), 저장하지 않고,
     * 월드 목록·플러그인 이벤트에 나오지 않는다. 다 쓰면 {@code getChunkSource().close(false)}. 이름은 data 의 레벨 이름({@code checkName} 으로 바뀐다),
     * {@link #dimension()} 은 받은 키다. server·executor·session·progress·spawners·tickTime·environment 는 쓰지 않는다(차원 타입은 원래 월드 것).
     *
     * @throws org.domi.furnace.compat.nms.UnsupportedNmsCall 생성기가 불러온 월드의 것({@link ServerChunkCache#getGenerator})이 아니거나, Bukkit 생성기·바이옴 공급자를 줄 때
     */
    public ServerLevel(MinecraftServer server, java.util.concurrent.Executor executor,
                       net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess session,
                       net.minecraft.world.level.storage.PrimaryLevelData data, net.minecraft.resources.ResourceKey<Level> dimension,
                       net.minecraft.world.level.dimension.LevelStem stem, net.minecraft.server.level.progress.ChunkProgressListener progress,
                       boolean debug, long seed, List<?> spawners, boolean tickTime, net.minecraft.world.RandomSequences randomSequences,
                       org.bukkit.World.Environment environment, org.bukkit.generator.ChunkGenerator generator,
                       org.bukkit.generator.BiomeProvider biomeProvider) {
        this(openScratch(data, stem, seed, environment, generator, biomeProvider), data, dimension, debug, randomSequences);
    }

    private ServerLevel(Object[] opened, net.minecraft.world.level.storage.PrimaryLevelData data, net.minecraft.resources.ResourceKey<Level> dimension,
                        boolean debug, net.minecraft.world.RandomSequences randomSequences) {
        super((CraftWorld) opened[0]);
        this.serverLevelData = data;
        this.stemKey = dimension;
        this.scratch = true;
        this.debug = debug;
        this.randomSequences = randomSequences;
        this.scratchBiomeProvider = opened[2];
        getWorld().bindScratch(this);
        if (opened[1] instanceof org.domi.furnace.compat.paper.BukkitBiomeProviders.LateWorldInfo info) info.bind(getWorld());
        // 닫지 않고 버린 레벨도 GC 가 거둘 때 닫는다(#486 검토 1). 동작은 이 레벨·CraftWorld 를 쥐지 않는다.
        this.scratchCleanup = SCRATCH_CLEANER.register(this, new ScratchClose(getWorld().furnaceHandle()));
    }

    /** 임시 월드 생성기가 약하게 쥐는 플러그인 바이옴 공급자를 이 레벨이 쥔다(레벨이 살아 있는 동안 공급자도 산다). */
    @SuppressWarnings("unused")
    private Object scratchBiomeProvider;

    private static final java.lang.ref.Cleaner SCRATCH_CLEANER = java.lang.ref.Cleaner.create();
    private java.lang.ref.Cleaner.Cleanable scratchCleanup;

    /** 임시 월드 닫기(명시적 close 와 GC 안전망이 같이 쓴다 — 두 번 불러도 된다). */
    private record ScratchClose(org.domi.furnace.api.world.World world) implements Runnable {
        @Override
        public void run() {
            Compat.scratchClosed(world.dimension());
            // 서버가 이미 내려갔거나 바뀌었다(시험 서버 교체 등) — 종료 훅이 닫았다. 그 밖의 실패는 부른 쪽(명시적 close)에 그대로 간다.
            var server = org.domi.furnace.api.Furnace.serverOrNull();
            if (server == null) return;
            var scratchWorlds = server.internals().scratchWorlds();
            if (scratchWorlds.isScratch(world)) scratchWorlds.close(world);
        }
    }

    /** 임시 월드를 연다 — [CraftWorld, 바이옴 공급자에 넘긴 WorldInfo 또는 null]. */
    private static Object[] openScratch(net.minecraft.world.level.storage.PrimaryLevelData data, net.minecraft.world.level.dimension.LevelStem stem,
                                        long seed, org.bukkit.World.Environment environment, org.bukkit.generator.ChunkGenerator generator,
                                        org.bukkit.generator.BiomeProvider biomeProvider) {
        if (generator != null) {
            throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("ServerLevel#<init>", "a level with a Bukkit ChunkGenerator is not supported - Furnace worlds use their own generator");
        }
        var original = stem == null || stem.generator() == null ? null : stem.generator().furnaceWorld();
        if (original == null) {
            throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("ServerLevel#<init>",
                    "only the generator of a loaded world (ServerLevel#getChunkSource().getGenerator()) can make a separate level");
        }
        String name = data == null ? null : data.getLevelName();
        org.domi.furnace.api.worldgen.BiomeProvider biomes = null;
        org.domi.furnace.compat.paper.BukkitBiomeProviders.LateWorldInfo info = null;
        if (biomeProvider != null) {
            // CraftBukkit: 바이옴 공급자가 있으면 생성기의 바이옴 원천을 CustomWorldChunkManager(그 레벨의 CraftWorld 를 WorldInfo 로)로 바꾼다
            org.bukkit.World ow = Compat.world(original.dimension());
            info = new org.domi.furnace.compat.paper.BukkitBiomeProviders.LateWorldInfo(name == null ? original.name() : name,
                    environment != null ? environment : ow.getEnvironment(), seed, ow.getMinHeight(), ow.getMaxHeight());
            biomes = org.domi.furnace.compat.paper.BukkitBiomeProviders.adaptWeak(biomeProvider, info);   // 이 레벨이 공급자를 쥔다(아래 필드)
        }
        org.domi.furnace.api.world.World fw;
        try {
            fw = org.domi.furnace.api.Furnace.server().internals().scratchWorlds().create(original, seed, name, biomes);
        } catch (org.domi.furnace.compat.paper.UnsupportedFeature e) {
            throw e;
        } catch (UnsupportedOperationException e) {
            throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("ServerLevel#<init>", e.getMessage());   // 바이옴 공급자를 받지 않는 생성기(평지 등)
        }
        CraftWorld cw = new CraftWorld(fw);
        Compat.scratchOpened(cw);
        return new Object[] {cw, info, biomeProvider};
    }

    /** 플러그인이 만든 따로 떨어진 레벨(임시 월드)인가(호환 모듈 전용). */
    public boolean furnaceScratch() { return scratch; }

    /** 임시 월드를 버린다(호환 모듈 전용 — {@link ServerChunkCache#close}). 두 번 불러도 된다. */
    void furnaceCloseScratch() {
        scratchCleanup.clean();
    }

    @Override
    public net.minecraft.resources.ResourceKey<Level> dimension() { return stemKey != null ? stemKey : super.dimension(); }

    /** 바닐라 dimensionTypeRegistration: 이 월드의 차원 타입(같은 레벨은 같은 손잡이). */
    public net.minecraft.core.Holder<net.minecraft.world.level.dimension.DimensionType> dimensionTypeRegistration() {
        var h = dimensionType;
        if (h == null) dimensionType = h = net.minecraft.core.Holder.direct(new net.minecraft.world.level.dimension.DimensionType(getWorld().furnaceHandle()));
        return h;
    }

    /** 바닐라 isDebug: 모든 블록 상태 격자 월드인가. */
    public boolean isDebug() { return scratch ? debug : serverLevelData.isDebugWorld(); }

    /** 바닐라 getRandomSequences(T-203 — {@link net.minecraft.world.RandomSequences} 설명). */
    public net.minecraft.world.RandomSequences getRandomSequences() {
        var r = randomSequences;
        if (r == null) randomSequences = r = new net.minecraft.world.RandomSequences(getSeed());
        return r;
    }

    /** 바닐라(spigot) save(진행, flush, 저장 끔): 임시 월드는 저장하지 않는다. 서버 월드 저장은 서버가 한다(/save-all) — 이 길은 없다. */
    public void save(net.minecraft.util.ProgressListener progress, boolean flush, boolean savingDisabled) {
        if (scratch || savingDisabled) return;
        throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("ServerLevel#save", "server worlds are saved by the server (/save-all)");
    }

    /** Paper save(진행, flush, 저장 끔, 닫기). */
    public void save(net.minecraft.util.ProgressListener progress, boolean flush, boolean savingDisabled, boolean close) {
        save(progress, flush, savingDisabled);
    }

    /** 청크 공급원(N2a) — 서버 청크의 보기를 준다. */
    @Override
    public ServerChunkCache getChunkSource() { return chunkSource; }

    /** 바닐라 WorldGenLevel#getLevel: 자기 자신. */
    public ServerLevel getLevel() { return this; }

    /** 바닐라 ServerLevelAccessor#getMinecraftWorld: 자기 자신. */
    public ServerLevel getMinecraftWorld() { return this; }

    public long getSeed() { return getWorld().getSeed(); }

    public MinecraftServer getServer() { return MinecraftServer.getServer(); }

    /** 대기 한도(ms) — 네트워크·비동기 스레드가 id 로 엔티티를 찾을 때 소유 region 의 응답을 기다리는 시간. */
    private static final long LOOKUP_WAIT_MILLIS = Long.getLong("furnace.compat.entityLookupWaitMillis", 1_000L);

    @Override
    public Entity getEntity(int id) {
        var w = getWorld().furnaceHandle();
        java.util.Optional<org.domi.furnace.api.entity.Entity> found;
        try {
            found = w.entityByIdNow(id);
        } catch (org.domi.furnace.api.concurrent.ThreadOwnershipException notOwner) {
            // Paper·Folia 의 EntityLookup#get(int) 는 어느 스레드에서나 기다림 없이 돌려준다 — 그 엔티티의 Bukkit 객체를 이미 알면(UUID 캐시)
            // 소유 region 을 기다리지 않고 그것(마지막 스냅샷)을 준다. 상태를 읽을 때의 스레드 검사는 지금처럼 그 호출이 한다.
            var uuid = w.entityUuidById(id);
            if (uuid.isEmpty()) return null;   // 그 사이 사라졌다
            var cached = Compat.cachedEntity(uuid.get());
            if (cached != null) return cached.getHandle();
            // 처음 보는 엔티티: 스냅샷은 그 엔티티를 가진 region 이 떠야 한다. 틱 스레드가 아닌 쪽만 기다린다
            if (org.domi.furnace.compat.thread.PrimaryThread.isPrimary() || org.domi.furnace.api.Furnace.server().scheduler().isTickThread()) throw notOwner;
            try {
                found = w.entityById(id).get(LOOKUP_WAIT_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw notOwner;
            } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException late) {
                throw notOwner;
            }
        }
        return found.map(e -> ((org.bukkit.craftbukkit.entity.CraftEntity) Compat.entity(e)).getHandle()).orElse(null);
    }

    /**
     * 바닐라 ServerLevel#getEntity(UUID): 이 월드의 그 엔티티(플레이어 포함), 없으면 null. 스레드 규칙은 {@link #getEntity(int)} 와 같다
     * (그 엔티티의 Bukkit 객체를 이미 알면 어느 스레드에서나 그 스냅샷, 처음 보는 것은 소유 region 스레드이거나 틱 스레드가 아닌 스레드가 최대 1초 기다린다).
     */
    public Entity getEntity(java.util.UUID uuid) {
        var w = getWorld().furnaceHandle();
        java.util.Optional<org.domi.furnace.api.entity.Entity> found;
        try {
            found = w.entityByUuidNow(uuid);
        } catch (org.domi.furnace.api.concurrent.ThreadOwnershipException notOwner) {
            var cached = Compat.cachedEntity(uuid);
            if (cached != null) return cached.getWorld() == getWorld() ? cached.getHandle() : null;
            if (org.domi.furnace.compat.thread.PrimaryThread.isPrimary() || org.domi.furnace.api.Furnace.server().scheduler().isTickThread()) throw notOwner;
            try {
                found = w.entityByUuid(uuid).get(LOOKUP_WAIT_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw notOwner;
            } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException late) {
                throw notOwner;
            }
        }
        return found.map(e -> ((org.bukkit.craftbukkit.entity.CraftEntity) Compat.entity(e)).getHandle()).orElse(null);
    }

    /** 이 월드에 있는 접속 플레이어. */
    public List<ServerPlayer> players() {
        List<ServerPlayer> out = new ArrayList<>();
        for (var p : getWorld().getPlayers()) out.add(((CraftPlayer) p).getHandle());
        return out;
    }
}
