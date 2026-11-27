// Internals(플러그인의 공식 "NMS" 다리) 서버 구현. 내부 객체는 그 객체를 가진 스레드에서만 건네주고, 형이 틀리면 실제 클래스 이름을 알려 준다.
// 처음 부른 플러그인 이름을 로그에 한 번 남긴다(운영자가 MC·Furnace 버전을 올릴 때 다시 빌드가 필요한 플러그인을 알 수 있게).
package org.domi.furnace.server.plugin;

import org.domi.furnace.api.GameVersion;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.domi.furnace.api.concurrent.ThreadOwnershipException;
import org.domi.furnace.api.entity.Entity;
import org.domi.furnace.api.internal.Internals;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.Region;
import org.domi.furnace.server.world.World;
import org.domi.furnace.server.i18n.Lang;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class InternalsImpl implements Internals {

    private static final Logger log = LoggerFactory.getLogger("Furnace");
    public static final int PROTOCOL_VERSION = GameVersion.PROTOCOL;
    public static final int DATA_VERSION = GameVersion.DATA_VERSION;

    private final PluginHost host;
    private final Set<String> users = ConcurrentHashMap.newKeySet();
    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    private final ChunkInternalsImpl chunks;
    private final BlockStateInternalsImpl blockStates = new BlockStateInternalsImpl();

    InternalsImpl(PluginHost host) {
        this.host = host;
        this.chunks = new ChunkInternalsImpl(host);
    }

    /** 청크 거울 훅을 꽂는다 — 다리를 거치지 않고 청크에 거울을 붙이는 시험·벤치용(서버는 다리가 올라올 때 스스로 꽂는다). */
    public static void installChunkMirrorOps() { ChunkInternalsImpl.installMirrorOps(); }

    /** 내부 접근을 쓴 플러그인 이름들(진단용). */
    public Set<String> users() { return Set.copyOf(users); }

    /**
     * 부른 쪽이 플러그인이면 처음 한 번 로그. 서버·공개 API 프레임을 건너뛴 첫 프레임이 플러그인 클래스로더의 클래스일 때만 남긴다 — 호환 계층이
     * NMS shim 을 위해 다리를 꺼낼 때(첫 프레임이 호환 계층) 그 순간 스택 아래에 있던 플러그인을 "내부 접근을 쓴다"로 잘못 남기지 않게.
     */
    void noteCaller() {
        ClassLoader cl = WALKER.walk(s -> s.filter(f -> !f.getClassName().startsWith("org.domi.furnace.server.")
                        && !f.getClassName().startsWith("org.domi.furnace.api."))
                .findFirst().map(f -> f.getDeclaringClass().getClassLoader())
                .filter(l -> l instanceof PluginManagerImpl.PluginClassLoader).orElse(null));
        if (cl == null) return;
        String name = host.pluginsImpl().pluginNameOf(cl);
        if (name != null && users.add(name)) {
            log.info(Lang.t("internals.used", name));
        }
    }

    // ------------------------------------------------------------------ 버전

    @Override
    public String minecraftVersion() { return host.minecraftVersion(); }

    @Override
    public int protocolVersion() { return PROTOCOL_VERSION; }

    @Override
    public int dataVersion() { return DATA_VERSION; }

    // ------------------------------------------------------------------ 호환 어댑터 설정

    @Override
    public String compatOption(String key) { return host.compatOption(key); }

    // ------------------------------------------------------------------ 공용

    static <H> H cast(Object o, Class<H> type, String what) {
        java.util.Objects.requireNonNull(type, "type");
        if (!type.isInstance(o)) {
            throw new ClassCastException("the internal object of " + what + " is " + o.getClass().getName() + ", not a " + type.getName());
        }
        return type.cast(o);
    }

    private static <T> CompletableFuture<T> failed(Throwable t) { return RegionFuture.wrap(CompletableFuture.failedFuture(t)); }

    private WorldHandle worldHandle(org.domi.furnace.api.world.World w) {
        if (w instanceof WorldHandle wh) return wh;
        throw new IllegalArgumentException("not a world handle created by the Furnace server: " + w);
    }

    // ------------------------------------------------------------------ 엔티티

    @Override
    public <H, T> CompletableFuture<T> withEntity(Entity entity, Class<H> type, Function<? super H, ? extends T> action) {
        java.util.Objects.requireNonNull(type, "type");
        java.util.Objects.requireNonNull(action, "action");
        if (entity instanceof PlayerHandle ph) {
            Player p = ph.handle();
            if (p.isDisconnected()) return failed(new IllegalStateException("disconnected player: " + p.name()));
            RegionFuture<T> f = new RegionFuture<>();
            p.runOnOwner(() -> {
                try {
                    f.complete(action.apply(cast(p, type, "player " + p.name())));
                } catch (Throwable t) {
                    f.completeExceptionally(t);
                }
            });
            return f;
        }
        if (entity instanceof EntityHandle eh) {
            return eh.onEntity(e -> action.apply(cast(e, type, "entity " + eh.type())), null, true);
        }
        return failed(ForeignEntity.reject(entity, "not an entity handle created by the Furnace server"));
    }

    @Override
    public <H> H entityNow(Entity entity, Class<H> type) {
        if (entity instanceof PlayerHandle ph) {
            Player p = ph.handle();
            org.domi.furnace.server.world.RegionBorrow.claim(() -> p.ownerRegion);   // 전역 빌림 구간이면 그 플레이어의 region 을 빌린다
            Region r = p.ownerRegion;
            if (r == null || !r.ownsPlayer(p)) throw notOwner("player " + p.name());
            return cast(p, type, "player " + p.name());
        }
        if (entity instanceof EntityHandle eh) {
            if (!(eh.tasks().owner() instanceof org.domi.furnace.server.entity.Entity e) || e.isRemoved()) {
                throw new IllegalStateException("the entity is gone: " + eh.uniqueId());
            }
            if (!ownsOnCurrentThread(e)) throw notOwner("entity " + eh.type());
            return cast(e, type, "entity " + eh.type());
        }
        throw ForeignEntity.reject(entity, "not an entity handle created by the Furnace server");
    }

    private static ThreadOwnershipException notOwner(String what) {
        return new ThreadOwnershipException("not on the region thread that owns " + what + " (current thread: " + Thread.currentThread().getName()
                + ") - use withEntity or call it inside an entityScheduler() task");
    }

    /** 지금 스레드가 e 를 가진 region 을 돌리는 중인가 — e 가 있는 청크의 주인 region 이 이 스레드이고 그 region 에 e 가 들어 있어야 한다. */
    private boolean ownsOnCurrentThread(org.domi.furnace.server.entity.Entity e) {
        World w = host.core().world(e.dimensionKey());
        if (w == null) return false;
        ChunkPos cp = ChunkPos.ofBlock(e.x(), e.z());
        org.domi.furnace.server.world.RegionBorrow.claimChunk(w, cp.x(), cp.z());   // 전역 빌림 구간이면 그 region 을 빌린다
        Region r = w.regionizer().ownerOf(cp.x(), cp.z());
        return r != null && r.isOwnerThread() && r.level() != null && r.level().entities().get(e.uuid()) == e;
    }

    @Override
    public Entity wrapEntity(Object internalEntity) {
        if (internalEntity instanceof Player p) return host.player(p);   // 플레이어 핸들은 아무 스레드에서 안전하다
        if (internalEntity instanceof org.domi.furnace.server.entity.Entity e) {
            if (!ownsOnCurrentThread(e)) throw notOwner("entity " + e.type().name());
            return EntityHandle.of(host, e);
        }
        throw new IllegalArgumentException("not a server entity: " + (internalEntity == null ? "null" : internalEntity.getClass().getName()));
    }

    // ------------------------------------------------------------------ 칸·region

    @Override
    public <H, T> CompletableFuture<T> withLevel(org.domi.furnace.api.world.World world, BlockPos pos, Class<H> type, Function<? super H, ? extends T> action) {
        java.util.Objects.requireNonNull(type, "type");
        java.util.Objects.requireNonNull(action, "action");
        World w = worldHandle(world).handle();
        ChunkPos cp = new ChunkPos(pos.chunkX(), pos.chunkZ());
        CompletableFuture<T> f = w.submitAtOwnedChunk(cp, r -> {
            if (!r.chunkLoaded(cp) || r.level() == null) throw new IllegalStateException("the chunk is not loaded: " + cp);
            return action.apply(cast(r.level(), type, "the level at " + pos));
        });
        return f != null ? RegionFuture.wrap(f) : failed(new IllegalStateException("the chunk is not loaded: " + cp));
    }

    @Override
    public <H> H levelNow(org.domi.furnace.api.world.World world, BlockPos pos, Class<H> type) {
        World w = worldHandle(world).handle();
        ChunkPos cp = new ChunkPos(pos.chunkX(), pos.chunkZ());
        org.domi.furnace.server.world.RegionBorrow.claimChunk(w, cp.x(), cp.z());   // 전역 빌림 구간이면 그 region 을 빌린다
        Region r = w.regionizer().ownerOf(cp.x(), cp.z());
        if (r == null || !r.isOwnerThread()) {
            throw new ThreadOwnershipException("not on the region thread that owns the cell " + pos + " (current thread: " + Thread.currentThread().getName() + ") - use withLevel");
        }
        if (!r.chunkLoaded(cp) || r.level() == null) throw new IllegalStateException("the chunk is not loaded: " + cp);
        return cast(r.level(), type, "the level at " + pos);
    }

    // ------------------------------------------------------------------ 월드·서버

    @Override
    public <H> H world(org.domi.furnace.api.world.World world, Class<H> type) {
        return cast(worldHandle(world).handle(), type, "world " + world.dimension());
    }

    @Override
    public <H> H server(Class<H> type) { return cast(host.core(), type, "server"); }

    @Override
    public <H, T> CompletableFuture<T> withServer(Class<H> type, Function<? super H, ? extends T> action) {
        java.util.Objects.requireNonNull(type, "type");
        java.util.Objects.requireNonNull(action, "action");
        var core = host.core();
        return RegionFuture.wrap(core.scheduler().global().submit(() -> action.apply(cast(core, type, "server"))));
    }


    @Override
    public org.domi.furnace.api.internal.ChunkInternals chunks() {
        noteCaller();   // 플러그인이 다리를 직접 꺼내 쓰면 남긴다(호환 계층이 꺼낼 때는 남기지 않는다)
        return chunks;
    }

    @Override
    public org.domi.furnace.api.internal.BlockStateInternals blockStates() { return blockStates; }

    private final ScratchWorldsImpl scratchWorlds = new ScratchWorldsImpl(this::hostRef);

    private PluginHost hostRef() { return host; }

    @Override
    public org.domi.furnace.api.internal.ScratchWorlds scratchWorlds() { return scratchWorlds; }

    private final FeatureInternalsImpl features = new FeatureInternalsImpl();

    @Override
    public org.domi.furnace.api.internal.FeatureInternals features() { return features; }

    @Override
    public org.domi.furnace.api.world.World wrapWorld(Object internalWorld) {
        if (internalWorld instanceof World w) {
            WorldHandle h = host.worldHandle(w.dimension());
            if (h != null && h.handle() == w) return h;
            h = scratchWorlds.handleOf(w);
            if (h != null) return h;
        }
        throw new IllegalArgumentException("not a world of this server: " + (internalWorld == null ? "null" : internalWorld.getClass().getName()));
    }
}
