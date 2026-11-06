// 플러그인이 보는 청크 — 좌표뿐인 가벼운 불변 핸들. 읽기·쓰기는 그 청크를 가진 region 스레드에서 하고, 로드되지 않은 청크를 만들지 않는다.
// 영속 데이터는 청크 NBT 의 ChunkBukkitValues(Paper 와 같은 키) — 저장 코덱이 모르는 최상위 키를 보존하는 extraNbt 에 둔다.
package org.domi.furnace.server.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.domi.furnace.api.entity.Entity;
import org.domi.furnace.api.persistence.PersistentDataContainer;
import org.domi.furnace.api.persistence.PersistentDataView;
import org.domi.furnace.api.sched.TaskScheduler;
import org.domi.furnace.server.entity.AABB;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.Region;

final class ChunkHandle implements org.domi.furnace.api.world.Chunk {

    static final String PDC_KEY = "ChunkBukkitValues";

    private final PluginHost host;
    private final WorldHandle world;
    private final int x, z;

    ChunkHandle(PluginHost host, WorldHandle world, int x, int z) {
        this.host = host;
        this.world = world;
        this.x = x;
        this.z = z;
    }

    private ChunkPos pos() { return new ChunkPos(x, z); }

    @Override
    public WorldHandle world() { return world; }

    @Override
    public int x() { return x; }

    @Override
    public int z() { return z; }

    @Override
    public boolean isLoaded() { return world.handle().isChunkLoaded(x, z); }

    @Override
    public TaskScheduler scheduler() { return host.profiled().wrap(world.handle().chunkScheduler(x, z)); }

    /** 그 청크의 region 에서 task(로드되지 않았으면 그 region 에 넘기지 않고 notLoaded). */
    private <T> CompletableFuture<T> onChunk(java.util.function.BiFunction<Region, ChunkColumn, T> task, T notLoaded) {
        CompletableFuture<T> f = world.handle().submitAtOwnedChunk(pos(), r -> {
            ChunkColumn col = r.ownedColumn(pos());
            return col == null ? notLoaded : task.apply(r, col);
        });
        return RegionFuture.wrap(f != null ? f : CompletableFuture.completedFuture(notLoaded));
    }

    @Override
    public CompletableFuture<List<Entity>> entities() {
        return onChunk((r, col) -> {
            List<Entity> out = new ArrayList<>();
            double minY = world.handle().dimensionType().minY(), maxY = world.handle().dimensionType().maxY();
            AABB box = new AABB(x * 16, minY - 64, z * 16, x * 16 + 16, maxY + 64, z * 16 + 16);
            for (var e : r.level().getEntities(null, box, null)) {
                if (!e.isRemoved() && e.chunkX() == x && e.chunkZ() == z) out.add(EntityHandle.of(host, e));
            }
            for (var p : r.players().values()) {
                ChunkPos cp = p.chunkPos();
                if (cp.x() == x && cp.z() == z && !p.isDisconnected()) out.add(host.player(p));
            }
            return out;
        }, List.of());
    }

    @Override
    public CompletableFuture<Optional<PersistentDataView>> persistentData() {
        return onChunk((r, col) -> {
            NbtCompound extra = col.extraNbt();
            NbtCompound pdc = extra != null && extra.tags().get(PDC_KEY) instanceof NbtCompound c ? c : null;
            return Optional.<PersistentDataView>of(NbtPersistentData.snapshot(pdc));
        }, Optional.empty());
    }

    @Override
    public CompletableFuture<Boolean> editPersistentData(Consumer<PersistentDataContainer> edit) {
        return onChunk((r, col) -> {
            NbtCompound extra = col.extraNbt();
            NbtCompound pdc = extra != null && extra.tags().get(PDC_KEY) instanceof NbtCompound c ? c.copy() : new NbtCompound();
            edit.accept(new NbtPersistentData(pdc, false));   // 예외가 나면 원본은 그대로
            if (extra == null) {
                if (pdc.tags().isEmpty()) return true;
                col.extraNbt(extra = new NbtCompound());
            }
            if (pdc.tags().isEmpty()) extra.tags().remove(PDC_KEY);
            else extra.putCompound(PDC_KEY, pdc);
            r.markChunkChanged(pos());
            return true;
        }, false);
    }

    @Override
    public boolean equals(Object o) { return o instanceof ChunkHandle c && c.world == world && c.x == x && c.z == z; }

    @Override
    public int hashCode() { return (world.dimension().hashCode() * 31 + x) * 31 + z; }

    @Override
    public String toString() { return "Chunk{" + world.dimension() + " " + x + "," + z + "}"; }
}
