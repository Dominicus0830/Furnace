// 플러그인이 보는 월드 — 서버 World 를 감싼다. 블록 접근은 모두 그 칸을 가진 region 스레드에서 실행된다.
package org.domi.furnace.server.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.domi.furnace.api.entity.Entity;
import org.domi.furnace.api.inventory.Inventory;
import org.domi.furnace.api.world.Location;
import org.domi.furnace.api.concurrent.ThreadOwner;
import org.domi.furnace.api.concurrent.ThreadOwnershipException;
import org.domi.furnace.api.sched.TaskScheduler;
import org.domi.furnace.api.world.Block;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.GameRules;
import org.domi.furnace.server.world.Region;
import org.domi.furnace.server.world.World;

public final class WorldHandle implements org.domi.furnace.api.world.World {

    private final PluginHost host;
    private final World world;

    WorldHandle(PluginHost host, World world) {
        this.host = host;
        this.world = world;
    }

    public World handle() { return world; }

    private ServerCore core() { return host.core(); }

    @Override
    public String name() {
        String base = core().levelData().levelName;
        return switch (world.dimension()) {
            case "minecraft:overworld" -> base;
            case "minecraft:the_nether" -> base + "_nether";
            case "minecraft:the_end" -> base + "_the_end";
            default -> world.dimension();
        };
    }

    @Override
    public String dimension() { return world.dimension(); }

    @Override
    public long seed() { return core().levelData().seed; }

    @Override
    public String gameRule(String name) {
        GameRules.Rule r = GameRules.RULES.get(name);
        return r == null ? null : GameRules.serialize(r, core().gameRules().raw(name));
    }

    @Override
    public int loadedChunkCount() { return world.loadedChunkCount(); }

    private static final long REGION_TPS_WINDOW = 5_000_000_000L, REGION_TPS_MIN_HISTORY = 1_000_000_000L;

    private org.domi.furnace.api.world.RegionInfo toApi(org.domi.furnace.server.world.Regionizer.RegionView v) {
        var i = v.info();
        return new org.domi.furnace.api.world.RegionInfo(i.id(), world.dimension(), i.chunks(), i.minChunkX(), i.minChunkZ(),
                i.maxChunkX(), i.maxChunkZ(), i.thread(), v.tps(), i.lastTickNanos(), i.maxTickNanos());
    }

    @Override
    public Optional<org.domi.furnace.api.world.RegionInfo> regionAt(int chunkX, int chunkZ) {
        var v = world.regionizer().regionViewAt(chunkX, chunkZ, REGION_TPS_WINDOW, REGION_TPS_MIN_HISTORY);
        return Optional.ofNullable(v == null ? null : toApi(v));
    }

    @Override
    public List<org.domi.furnace.api.world.RegionInfo> regions() {
        List<org.domi.furnace.api.world.RegionInfo> out = new ArrayList<>();
        for (var v : world.regionizer().regionViews(REGION_TPS_WINDOW, REGION_TPS_MIN_HISTORY)) out.add(toApi(v));
        return out;
    }

    private boolean inHeight(int y) { return y >= world.dimensionType().minY() && y < world.dimensionType().maxY(); }

    private Optional<Block> read(Region r, BlockPos pos) {
        ChunkPos cp = new ChunkPos(pos.chunkX(), pos.chunkZ());
        if (!inHeight(pos.y()) || !r.chunkLoaded(cp)) return Optional.empty();
        int state = r.getBlock(pos.x(), pos.y(), pos.z());
        return Optional.of(new Block(world.dimension(), pos, ApiConvert.block(state, world.blockRegistry())));
    }

    @Override
    public CompletableFuture<Optional<Block>> blockAt(BlockPos pos) {
        CompletableFuture<Optional<Block>> f = world.submitAtOwnedChunk(new ChunkPos(pos.chunkX(), pos.chunkZ()), r -> read(r, pos));
        return RegionFuture.wrap(f != null ? f : CompletableFuture.completedFuture(Optional.empty()));
    }

    @Override
    public CompletableFuture<Boolean> setBlock(BlockPos pos, BlockData data) {
        int state;
        try {
            state = ApiConvert.stateId(data, world.blockRegistry());
        } catch (IllegalArgumentException e) {
            return RegionFuture.wrap(CompletableFuture.failedFuture(e));
        }
        CompletableFuture<Boolean> f = world.submitAtOwnedChunk(new ChunkPos(pos.chunkX(), pos.chunkZ()), r -> {
            if (!inHeight(pos.y()) || !r.chunkLoaded(new ChunkPos(pos.chunkX(), pos.chunkZ()))) return false;
            var level = r.level();
            boolean ok = level.setBlock(new Pos(pos.x(), pos.y(), pos.z()), state, 11);
            level.broadcastChanges();
            return ok;
        });
        return RegionFuture.wrap(f != null ? f : CompletableFuture.completedFuture(false));
    }

    @Override
    public Optional<Block> blockNow(BlockPos pos) {
        ThreadOwner owner = (ThreadOwner) world.chunkScheduler(pos.chunkX(), pos.chunkZ());
        if (!owner.isOwnerThread()) {
            throw new ThreadOwnershipException("blockNow(" + pos + ") 는 그 칸을 가진 region 스레드에서만 부를 수 있다 (현재 " + Thread.currentThread().getName() + ") — blockAt 을 쓴다");
        }
        Region r = world.regionizer().ownerOf(pos.chunkX(), pos.chunkZ());
        return r == null ? Optional.empty() : read(r, pos);
    }

    @Override
    public TaskScheduler scheduler(BlockPos pos) { return world.chunkScheduler(pos.chunkX(), pos.chunkZ()); }

    // ------------------------------------------------------------------ 청크

    @Override
    public ChunkHandle chunkAt(int chunkX, int chunkZ) { return new ChunkHandle(host, this, chunkX, chunkZ); }

    @Override
    public boolean isChunkLoaded(int chunkX, int chunkZ) { return world.isChunkLoaded(chunkX, chunkZ); }

    // ------------------------------------------------------------------ 엔티티

    @Override
    public CompletableFuture<Entity> spawnEntity(String type, Location at) {
        String id = type.indexOf(':') < 0 ? "minecraft:" + type : type;
        if (!org.domi.furnace.server.command.arguments.EntityTypes.isKnown(id) || id.equals("minecraft:player") || id.equals("minecraft:fishing_bobber")) {
            return RegionFuture.wrap(CompletableFuture.failedFuture(new IllegalArgumentException("소환할 수 없는 엔티티 종류: " + type)));
        }
        if (!at.world().equals(world.dimension())) {
            return RegionFuture.wrap(CompletableFuture.failedFuture(new IllegalArgumentException("다른 차원의 위치: " + at.world())));
        }
        if (!Double.isFinite(at.x()) || !Double.isFinite(at.y()) || !Double.isFinite(at.z()) || Math.abs(at.x()) >= 3.0E7 || Math.abs(at.z()) >= 3.0E7) {
            return RegionFuture.wrap(CompletableFuture.failedFuture(new IllegalArgumentException("잘못된 위치: " + at)));
        }
        ChunkPos cp = ChunkPos.ofBlock(at.x(), at.z());
        CompletableFuture<Entity> f = world.submitAtOwnedChunk(cp, r -> {
            if (!r.chunkLoaded(cp)) throw new IllegalStateException("청크가 로드돼 있지 않다: " + cp);
            var level = r.level();
            // /summon 과 같은 경로(SummonCommand.summonAt): 종류 id 만 든 태그로 만들고, 몹은 finalizeSpawn(COMMAND)
            var tag = new org.domi.furnace.server.net.io.nbt.NbtCompound();
            tag.putString("id", id);
            var e = org.domi.furnace.server.entity.Entities.load(level, tag);
            if (e == null) throw new IllegalArgumentException("만들 수 없는 엔티티 종류: " + id);
            e.moveTo(at.x(), at.y(), at.z(), at.yaw(), at.pitch());
            if (e instanceof org.domi.furnace.server.entity.Mob mob) {
                if (mob instanceof org.domi.furnace.server.entity.Villager v) v.setFinalizeReason(org.domi.furnace.server.entity.spawn.SpawnReason.COMMAND);
                mob.finalizeSpawnGroup(level.random(), level.getCurrentDifficultyAt(mob.blockPosition()), null);
            }
            if (!level.addFreshEntityWithPassengers(e)) return null;   // EntitySpawnEvent 취소(또는 UUID 겹침)
            return EntityHandle.of(host, e);
        });
        return RegionFuture.wrap(f != null ? f : CompletableFuture.failedFuture(new IllegalStateException("청크가 로드돼 있지 않다: " + cp)));
    }

    @Override
    public CompletableFuture<List<Entity>> nearbyEntities(Location center, double radius) {
        if (!(radius >= 0 && radius <= 128)) return RegionFuture.wrap(CompletableFuture.failedFuture(new IllegalArgumentException("radius 는 0..128: " + radius)));
        if (!center.world().equals(world.dimension())) return RegionFuture.wrap(CompletableFuture.completedFuture(List.of()));
        ChunkPos cp = ChunkPos.ofBlock(center.x(), center.z());
        double r2 = radius * radius;
        CompletableFuture<List<Entity>> f = world.submitAtOwnedChunk(cp, r -> {
            List<Entity> out = new ArrayList<>();
            var box = new org.domi.furnace.server.entity.AABB(center.x() - radius, center.y() - radius, center.z() - radius,
                    center.x() + radius, center.y() + radius, center.z() + radius);
            for (var e : r.level().getEntities(null, box, null)) {
                if (e.isRemoved()) continue;
                double dx = e.x() - center.x(), dy = e.y() - center.y(), dz = e.z() - center.z();
                if (dx * dx + dy * dy + dz * dz <= r2) out.add(EntityHandle.of(host, e));
            }
            for (var p : r.players().values()) {
                if (p.isDisconnected()) continue;
                double dx = p.x() - center.x(), dy = p.y() - center.y(), dz = p.z() - center.z();
                if (dx * dx + dy * dy + dz * dz <= r2) out.add(host.player(p));
            }
            return out;
        });
        return RegionFuture.wrap(f != null ? f : CompletableFuture.completedFuture(List.of()));
    }

    // ------------------------------------------------------------------ 블록 칸

    @Override
    public CompletableFuture<Optional<Inventory>> inventoryAt(BlockPos pos) {
        ChunkPos cp = new ChunkPos(pos.chunkX(), pos.chunkZ());
        CompletableFuture<Optional<Inventory>> f = world.submitAtOwnedChunk(cp, r -> {
            if (!inHeight(pos.y())) return Optional.empty();
            var col = r.ownedColumn(cp);
            if (col == null || !(col.blockEntityAt(pos.x(), pos.y(), pos.z()) instanceof org.domi.furnace.server.block.entity.ContainerBlockEntity be)
                    || be instanceof org.domi.furnace.server.block.entity.CampfireBlockEntity) {   // 모닥불은 Bukkit 에서도 Inventory 가 아니다
                return Optional.empty();
            }
            return Optional.<Inventory>of(new OwnedInventory(host, be.type(), be.size(), blockRunner(pos, be)));
        });
        return RegionFuture.wrap(f != null ? f : CompletableFuture.completedFuture(Optional.empty()));
    }

    /** 그 칸의 region 에서 처음 찾은 그 블록 엔티티가 아직 있으면 메뉴 문맥(MenuBlockContext) 안에서 op 를 돌린다 — 바뀐 칸은 저장 표시·비교기 갱신까지 메뉴 클릭과 같다. */
    private OwnedInventory.Runner blockRunner(BlockPos pos, org.domi.furnace.server.block.entity.ContainerBlockEntity original) {
        ChunkPos cp = new ChunkPos(pos.chunkX(), pos.chunkZ());
        return new OwnedInventory.Runner() {
            @Override
            public <T> CompletableFuture<T> run(java.util.function.Function<OwnedInventory.Slots, T> op) {
                CompletableFuture<T> f = world.submitAtOwnedChunk(cp, r -> {
                    var col = r.ownedColumn(cp);
                    if (col == null || col.blockEntityAt(pos.x(), pos.y(), pos.z()) != original) throw new IllegalStateException("그 칸의 용기가 사라졌다: " + pos);
                    var c = new org.domi.furnace.server.world.BlockEntityContainer(original);
                    Object[] out = new Object[1];
                    org.domi.furnace.server.world.MenuBlockContext.runIn(r, () -> out[0] = op.apply(new OwnedInventory.Slots() {
                        public int size() { return c.size(); }

                        public org.domi.furnace.server.entity.ItemStack get(int slot) { return c.get(slot); }

                        public void set(int slot, org.domi.furnace.server.entity.ItemStack s) { c.set(slot, s); }
                    }));
                    @SuppressWarnings("unchecked") T t = (T) out[0];
                    return t;
                });
                return f != null ? f : CompletableFuture.failedFuture(new IllegalStateException("청크가 로드돼 있지 않다: " + cp));
            }
        };
    }
}
