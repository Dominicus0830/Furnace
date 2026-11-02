// 플러그인이 보는 엔티티 — 만든 시점(소유 region 스레드)의 필드를 복사한 스냅샷 핸들. 서버 Entity 객체는 들고 있지 않는다.
// 변경·재조회는 uuid 로 소유 region 에서 다시 찾아 그 스레드에서 한다.
package org.domi.furnace.server.plugin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.domi.furnace.api.sched.TaskScheduler;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.world.Location;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.LivingEntity;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.Region;
import org.domi.furnace.server.world.World;

public class EntityHandle implements org.domi.furnace.api.entity.Entity {

    final PluginHost host;
    private final UUID uuid;
    private final String type;
    private final Location location;
    private final Text customName;
    private final boolean valid;

    EntityHandle(PluginHost host, Entity e) {
        this.host = host;
        this.uuid = e.uuid();
        this.type = e.type().name();
        this.location = new Location(e.dimensionKey(), e.x(), e.y(), e.z(), e.yRot(), e.xRot());
        this.customName = e.hasCustomName() ? e.getCustomName() : null;
        this.valid = !e.isRemoved();
    }

    /** 소유 region 스레드에서 만든다. */
    public static EntityHandle of(PluginHost host, Entity e) {
        return e instanceof LivingEntity le ? new Living(host, le) : new EntityHandle(host, e);
    }

    @Override
    public UUID uniqueId() { return uuid; }

    @Override
    public String type() { return type; }

    @Override
    public Location location() { return location; }

    @Override
    public Text customName() { return customName; }

    @Override
    public boolean isValid() { return valid; }

    private World serverWorld() {
        WorldHandle w = host.worldHandle(location.world());
        return w == null ? null : w.handle();
    }

    private ChunkPos chunk() { return ChunkPos.ofBlock(location.x(), location.z()); }

    @Override
    public TaskScheduler scheduler() {
        ChunkPos cp = chunk();
        return serverWorld().chunkScheduler(cp.x(), cp.z());
    }

    /** 핸들 시점 위치의 region 에서 uuid 로 찾아 task 를 돈다. 그 region 에 없으면 null 로 완료. */
    <T> CompletableFuture<T> onOwner(java.util.function.BiFunction<Region, Entity, T> task) {
        World w = serverWorld();
        if (w == null) return CompletableFuture.completedFuture(null);
        CompletableFuture<T> f = w.submitAtOwnedChunk(chunk(), r -> {
            Entity cur = r.level().entities().get(uuid);
            return cur == null || cur.isRemoved() ? null : task.apply(r, cur);
        });
        return f != null ? f : CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<? extends org.domi.furnace.api.entity.Entity> refresh() {
        return onOwner((r, cur) -> of(host, cur));
    }

    @Override
    public CompletableFuture<Boolean> teleport(Location to) {
        if (!to.world().equals(location.world())) return CompletableFuture.completedFuture(false);
        World w = serverWorld();
        return onOwner((r, cur) -> {
            // 목적지 청크가 같은 region 것일 때만 — 다른 region 으로 넘기는 이동은 다음 단계(핸드오프 필요)
            ChunkPos tcp = ChunkPos.ofBlock(to.x(), to.z());
            if (w.regionizer().ownerOf(tcp.x(), tcp.z()) != r) return false;
            cur.moveTo(to.x(), to.y(), to.z(), to.yaw(), to.pitch());
            return true;
        }).thenApply(b -> b != null && b);
    }

    @Override
    public CompletableFuture<Boolean> remove() {
        return onOwner((r, cur) -> {
            cur.discard();
            return true;
        }).thenApply(b -> b != null && b);
    }

    static final class Living extends EntityHandle implements org.domi.furnace.api.entity.LivingEntity {

        private final float health, maxHealth;

        Living(PluginHost host, LivingEntity e) {
            super(host, e);
            this.health = e.health();
            this.maxHealth = e.maxHealth();
        }

        @Override
        public float health() { return health; }

        @Override
        public float maxHealth() { return maxHealth; }

        @Override
        public CompletableFuture<Void> updateHealth(float h) {
            return onOwner((r, cur) -> {
                if (!(cur instanceof LivingEntity le)) return Boolean.FALSE;
                if (h <= 0f) le.kill();
                else le.setHealth(h);
                return Boolean.TRUE;
            }).thenAccept(ok -> {
                if (ok == null || !ok) throw new IllegalStateException("엔티티를 소유 region 에서 찾지 못했다: " + uniqueId());
            });
        }
    }
}
