// Internals(플러그인의 공식 "NMS" 다리) 서버 구현. 내부 객체는 그 객체를 가진 스레드에서만 건네주고, 형이 틀리면 실제 클래스 이름을 알려 준다.
// 처음 부른 플러그인 이름을 로그에 한 번 남긴다(운영자가 MC·Furnace 버전을 올릴 때 다시 빌드가 필요한 플러그인을 알 수 있게).
package org.domi.furnace.server.plugin;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class InternalsImpl implements Internals {

    private static final Logger log = LoggerFactory.getLogger("Furnace");
    public static final int PROTOCOL_VERSION = 767;
    public static final int DATA_VERSION = 3955;

    private final PluginHost host;
    private final Set<String> users = ConcurrentHashMap.newKeySet();
    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    private final ChunkInternalsImpl chunks;

    InternalsImpl(PluginHost host) {
        this.host = host;
        this.chunks = new ChunkInternalsImpl(host);
    }

    /** 내부 접근을 쓴 플러그인 이름들(진단용). */
    public Set<String> users() { return Set.copyOf(users); }

    /**
     * 부른 쪽 스택에서 첫 플러그인 클래스로더를 찾아 처음이면 로그. 서버 밖 첫 프레임이 호환 계층이면 남기지 않는다 — 호환 계층이 NMS shim 을 위해
     * 다리를 꺼낼 때 그 순간 스택에 있던 플러그인을 "내부 접근을 쓴다"로 잘못 남기지 않게(그 플러그인은 NMS 호환 보고에 따로 나온다).
     */
    void noteCaller() {
        ClassLoader cl = WALKER.walk(s -> {
            var it = s.iterator();
            while (it.hasNext()) {
                var f = it.next();
                String n = f.getClassName();
                if (n.startsWith("org.domi.furnace.server.")) continue;
                if (n.startsWith("org.domi.furnace.compat.")) return null;
                if (f.getDeclaringClass().getClassLoader() instanceof PluginManagerImpl.PluginClassLoader pl) return pl;
                while (it.hasNext()) {
                    ClassLoader l = it.next().getDeclaringClass().getClassLoader();
                    if (l instanceof PluginManagerImpl.PluginClassLoader) return l;
                }
                return null;
            }
            return null;
        });
        if (cl == null) return;
        String name = host.pluginsImpl().pluginNameOf(cl);
        if (name != null && users.add(name)) {
            log.info("플러그인 {} 가 서버 내부 접근(Internals)을 쓴다 — 마인크래프트·Furnace 버전을 올리면 그 플러그인도 새 버전에 맞는지 확인한다", name);
        }
    }

    // ------------------------------------------------------------------ 버전

    @Override
    public String minecraftVersion() { return host.minecraftVersion(); }

    @Override
    public int protocolVersion() { return PROTOCOL_VERSION; }

    @Override
    public int dataVersion() { return DATA_VERSION; }

    // ------------------------------------------------------------------ 공용

    static <H> H cast(Object o, Class<H> type, String what) {
        java.util.Objects.requireNonNull(type, "type");
        if (!type.isInstance(o)) {
            throw new ClassCastException(what + " 의 내부 객체는 " + o.getClass().getName() + " 이다 — " + type.getName() + " 가 아니다");
        }
        return type.cast(o);
    }

    private static <T> CompletableFuture<T> failed(Throwable t) { return RegionFuture.wrap(CompletableFuture.failedFuture(t)); }

    private WorldHandle worldHandle(org.domi.furnace.api.world.World w) {
        if (w instanceof WorldHandle wh) return wh;
        throw new IllegalArgumentException("Furnace 서버가 만든 월드 핸들이 아니다: " + w);
    }

    // ------------------------------------------------------------------ 엔티티

    @Override
    public <H, T> CompletableFuture<T> withEntity(Entity entity, Class<H> type, Function<? super H, ? extends T> action) {
        java.util.Objects.requireNonNull(type, "type");
        java.util.Objects.requireNonNull(action, "action");
        if (entity instanceof PlayerHandle ph) {
            Player p = ph.handle();
            if (p.isDisconnected()) return failed(new IllegalStateException("접속이 끊긴 플레이어: " + p.name()));
            RegionFuture<T> f = new RegionFuture<>();
            p.runOnOwner(() -> {
                try {
                    f.complete(action.apply(cast(p, type, "플레이어 " + p.name())));
                } catch (Throwable t) {
                    f.completeExceptionally(t);
                }
            });
            return f;
        }
        if (entity instanceof EntityHandle eh) {
            return eh.onEntity(e -> action.apply(cast(e, type, "엔티티 " + eh.type())), null, true);
        }
        return failed(new IllegalArgumentException("Furnace 서버가 만든 엔티티 핸들이 아니다: " + entity));
    }

    @Override
    public <H> H entityNow(Entity entity, Class<H> type) {
        if (entity instanceof PlayerHandle ph) {
            Player p = ph.handle();
            org.domi.furnace.server.world.RegionBorrow.claim(() -> p.ownerRegion);   // 전역 빌림 구간이면 그 플레이어의 region 을 빌린다
            Region r = p.ownerRegion;
            if (r == null || !r.ownsPlayer(p)) throw notOwner("플레이어 " + p.name());
            return cast(p, type, "플레이어 " + p.name());
        }
        if (entity instanceof EntityHandle eh) {
            if (!(eh.tasks().owner() instanceof org.domi.furnace.server.entity.Entity e) || e.isRemoved()) {
                throw new IllegalStateException("엔티티가 사라졌다: " + eh.uniqueId());
            }
            if (!ownsOnCurrentThread(e)) throw notOwner("엔티티 " + eh.type());
            return cast(e, type, "엔티티 " + eh.type());
        }
        throw new IllegalArgumentException("Furnace 서버가 만든 엔티티 핸들이 아니다: " + entity);
    }

    private static ThreadOwnershipException notOwner(String what) {
        return new ThreadOwnershipException(what + " 를 가진 region 스레드가 아니다(현재 " + Thread.currentThread().getName()
                + ") — withEntity 를 쓰거나 entityScheduler() 작업 안에서 부른다");
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
            if (!ownsOnCurrentThread(e)) throw notOwner("엔티티 " + e.type().name());
            return EntityHandle.of(host, e);
        }
        throw new IllegalArgumentException("서버 엔티티가 아니다: " + (internalEntity == null ? "null" : internalEntity.getClass().getName()));
    }

    // ------------------------------------------------------------------ 칸·region

    @Override
    public <H, T> CompletableFuture<T> withLevel(org.domi.furnace.api.world.World world, BlockPos pos, Class<H> type, Function<? super H, ? extends T> action) {
        java.util.Objects.requireNonNull(type, "type");
        java.util.Objects.requireNonNull(action, "action");
        World w = worldHandle(world).handle();
        ChunkPos cp = new ChunkPos(pos.chunkX(), pos.chunkZ());
        CompletableFuture<T> f = w.submitAtOwnedChunk(cp, r -> {
            if (!r.chunkLoaded(cp) || r.level() == null) throw new IllegalStateException("청크가 로드돼 있지 않다: " + cp);
            return action.apply(cast(r.level(), type, "칸 " + pos + " 의 레벨"));
        });
        return f != null ? RegionFuture.wrap(f) : failed(new IllegalStateException("청크가 로드돼 있지 않다: " + cp));
    }

    @Override
    public <H> H levelNow(org.domi.furnace.api.world.World world, BlockPos pos, Class<H> type) {
        World w = worldHandle(world).handle();
        ChunkPos cp = new ChunkPos(pos.chunkX(), pos.chunkZ());
        org.domi.furnace.server.world.RegionBorrow.claimChunk(w, cp.x(), cp.z());   // 전역 빌림 구간이면 그 region 을 빌린다
        Region r = w.regionizer().ownerOf(cp.x(), cp.z());
        if (r == null || !r.isOwnerThread()) {
            throw new ThreadOwnershipException("칸 " + pos + " 를 가진 region 스레드가 아니다(현재 " + Thread.currentThread().getName() + ") — withLevel 을 쓴다");
        }
        if (!r.chunkLoaded(cp) || r.level() == null) throw new IllegalStateException("청크가 로드돼 있지 않다: " + cp);
        return cast(r.level(), type, "칸 " + pos + " 의 레벨");
    }

    // ------------------------------------------------------------------ 월드·서버

    @Override
    public <H> H world(org.domi.furnace.api.world.World world, Class<H> type) {
        return cast(worldHandle(world).handle(), type, "월드 " + world.dimension());
    }

    @Override
    public <H> H server(Class<H> type) { return cast(host.core(), type, "서버"); }

    @Override
    public <H, T> CompletableFuture<T> withServer(Class<H> type, Function<? super H, ? extends T> action) {
        java.util.Objects.requireNonNull(type, "type");
        java.util.Objects.requireNonNull(action, "action");
        var core = host.core();
        return RegionFuture.wrap(core.scheduler().global().submit(() -> action.apply(cast(core, type, "서버"))));
    }


    @Override
    public org.domi.furnace.api.internal.ChunkInternals chunks() {
        noteCaller();   // 플러그인이 다리를 직접 꺼내 쓰면 남긴다(호환 계층이 꺼낼 때는 남기지 않는다)
        return chunks;
    }

    @Override
    public org.domi.furnace.api.world.World wrapWorld(Object internalWorld) {
        if (internalWorld instanceof World w) {
            WorldHandle h = host.worldHandle(w.dimension());
            if (h != null && h.handle() == w) return h;
        }
        throw new IllegalArgumentException("이 서버의 월드가 아니다: " + (internalWorld == null ? "null" : internalWorld.getClass().getName()));
    }
}
