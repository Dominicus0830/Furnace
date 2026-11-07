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
import org.domi.furnace.api.world.BlockSnapshot;
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
        if (world.levelName() != null) return world.levelName();   // 추가 월드: 폴더 이름
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
    public long seed() { return core().seedOf(world); }

    @Override
    public String dimensionType() { return world.dimensionType().typeKey(); }

    @Override
    public org.domi.furnace.api.world.WorldEnvironment environment() {
        return switch (world.dimensionType().gen()) {
            case NETHER -> org.domi.furnace.api.world.WorldEnvironment.NETHER;
            case END -> org.domi.furnace.api.world.WorldEnvironment.THE_END;
            default -> org.domi.furnace.api.world.WorldEnvironment.NORMAL;
        };
    }

    @Override
    public boolean isLoaded() { return !world.isUnloaded(); }

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
                i.maxChunkX(), i.maxChunkZ(), i.thread(), v.tps(), i.lastTickNanos(), i.maxTickNanos(), i.players());
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

    private Optional<BlockSnapshot> read(Region r, BlockPos pos) {
        ChunkPos cp = new ChunkPos(pos.chunkX(), pos.chunkZ());
        if (!inHeight(pos.y()) || !r.chunkLoaded(cp)) return Optional.empty();
        int state = r.getBlock(pos.x(), pos.y(), pos.z());
        return Optional.of(new BlockSnapshot(world.dimension(), pos, ApiConvert.block(state, world.blockRegistry())));
    }

    @Override
    public Block block(BlockPos pos) { return new BlockHandle(this, pos); }

    @Override
    public CompletableFuture<Optional<BlockSnapshot>> blockAt(BlockPos pos) {
        CompletableFuture<Optional<BlockSnapshot>> f = world.submitAtOwnedChunk(new ChunkPos(pos.chunkX(), pos.chunkZ()), r -> read(r, pos));
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
        CompletableFuture<Boolean> f = world.submitAtOwnedChunk(new ChunkPos(pos.chunkX(), pos.chunkZ()), r -> placeOwned(r, pos, state));
        return RegionFuture.wrap(f != null ? f : CompletableFuture.completedFuture(false));
    }

    /** 소유 스레드에서: 그 칸을 state 로 바꾸고 방송한다(청크가 없거나 높이 밖이면 false). */
    private boolean placeOwned(Region r, BlockPos pos, int state) {
        if (!inHeight(pos.y()) || !r.chunkLoaded(new ChunkPos(pos.chunkX(), pos.chunkZ()))) return false;
        var level = r.level();
        boolean ok = level.setBlock(new Pos(pos.x(), pos.y(), pos.z()), state, 11);
        level.broadcastChanges();
        return ok;
    }

    /** 이 스레드가 그 칸을 가진 region 스레드여야 한다(아니면 ThreadOwnershipException). 그 region 을 돌려준다(없으면 null). */
    Region ownedRegion(BlockPos pos, String what) {
        ThreadOwner owner = (ThreadOwner) world.chunkScheduler(pos.chunkX(), pos.chunkZ());
        if (!owner.isOwnerThread()) {
            throw new ThreadOwnershipException(what + " 는 그 칸을 가진 region 스레드에서만 부를 수 있다 (현재 " + Thread.currentThread().getName() + ") — Async 판을 쓴다");
        }
        return world.regionizer().ownerOf(pos.chunkX(), pos.chunkZ());
    }

    /** 소유 스레드 전용 동기 쓰기(Block.set). */
    boolean setNow(BlockPos pos, BlockData data) {
        int state = ApiConvert.stateId(data, world.blockRegistry());
        Region r = ownedRegion(pos, "Block.set(" + pos + ")");
        return r != null && placeOwned(r, pos, state);
    }

    @Override
    public Optional<BlockSnapshot> blockNow(BlockPos pos) {
        Region r = ownedRegion(pos, "blockNow(" + pos + ")");
        return r == null ? Optional.empty() : read(r, pos);
    }

    @Override
    public TaskScheduler scheduler(BlockPos pos) { return host.profiled().wrap(world.chunkScheduler(pos.chunkX(), pos.chunkZ())); }

    // ------------------------------------------------------------------ 바이옴

    private List<String> biomeNames() {
        var regs = core().registries();
        if (regs == null) {
            var dp = core().dataPacks();
            return dp != null && dp.loot() != null ? dp.loot().registries().entries(GameRegistryStore.BIOME_REGISTRY) : List.of();
        }
        return regs.entries(GameRegistryStore.BIOME_REGISTRY);
    }

    @Override
    public CompletableFuture<Optional<String>> biomeAt(BlockPos pos) {
        ChunkPos cp = new ChunkPos(pos.chunkX(), pos.chunkZ());
        List<String> names = biomeNames();
        CompletableFuture<Optional<String>> f = world.submitAtOwnedChunk(cp, r -> {
            if (!inHeight(pos.y()) || !r.chunkLoaded(cp)) return Optional.<String>empty();
            int id = r.noiseBiomeIdAtBlock(pos.x(), pos.y(), pos.z());
            return id >= 0 && id < names.size() ? Optional.of(names.get(id)) : Optional.<String>empty();
        });
        return RegionFuture.wrap(f != null ? f : CompletableFuture.completedFuture(Optional.empty()));
    }

    /** 바이옴 이름의 레지스트리 번호. 모르거나 꺼진 플러그인의 바이옴이면 IllegalArgumentException. */
    int biomeId(String biome) {
        if (biome == null) throw new NullPointerException("biome");
        String key = biome.indexOf(':') < 0 ? "minecraft:" + biome : biome;
        int id = biomeNames().indexOf(key);
        if (id < 0) throw new IllegalArgumentException("알 수 없는 바이옴: " + biome);
        GameRegistryStore store = host.pluginsImpl().gameRegistries();
        GameRegistryStore.Entry<?> custom = store == null ? null : store.biome(key);
        if (custom != null && !custom.active()) throw new IllegalArgumentException("꺼진 플러그인의 바이옴: " + biome);
        return id;
    }

    /** 바이옴 번호의 이름, 범위 밖이면 null. */
    String biomeName(int id) {
        List<String> names = biomeNames();
        return id >= 0 && id < names.size() ? names.get(id) : null;
    }

    @Override
    public CompletableFuture<Boolean> setBiome(BlockPos pos, String biome) {
        int id;
        try {
            id = biomeId(biome);
        } catch (IllegalArgumentException e) {
            return RegionFuture.wrap(CompletableFuture.failedFuture(e));
        }
        ChunkPos cp = new ChunkPos(pos.chunkX(), pos.chunkZ());
        CompletableFuture<Boolean> f = world.submitAtOwnedChunk(cp, r -> {
            if (!inHeight(pos.y()) || !r.chunkLoaded(cp)) return false;
            int qx = (pos.x() >> 2) << 2, qy = (pos.y() >> 2) << 2, qz = (pos.z() >> 2) << 2;
            return r.fillBiomes(qx, qy, qz, qx, qy, qz, id, b -> true) > 0;
        });
        return RegionFuture.wrap(f != null ? f : CompletableFuture.completedFuture(false));
    }

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
            if (!level.addFreshEntityWithPassengers(e.spawnReason("CUSTOM"))) return null;   // EntitySpawnEvent 취소(또는 UUID 겹침)
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

    // ------------------------------------------------------------------ 블록 엔티티 NBT(/data block 경로)

    /** 그 칸 주인 region 에서: 로드돼 있고 블록 엔티티가 있으면 op, 아니면 none. 주인 region 이 없으면 none(region 을 만들지 않는다). */
    private <T> CompletableFuture<T> atBlockEntity(BlockPos pos, T none,
                                                   java.util.function.BiFunction<org.domi.furnace.server.world.RegionLevel, org.domi.furnace.server.block.entity.BlockEntity, T> op) {
        Pos p = new Pos(pos.x(), pos.y(), pos.z());
        CompletableFuture<T> f = world.submitAtOwnedChunk(new ChunkPos(pos.chunkX(), pos.chunkZ()), r -> {
            var level = r.level();
            if (level == null || !inHeight(pos.y()) || !level.hasChunkAt(p)) return none;
            return level.getBlockEntity(p) instanceof org.domi.furnace.server.block.entity.BlockEntity be ? op.apply(level, be) : none;
        });
        return RegionFuture.wrap(f != null ? f : CompletableFuture.completedFuture(none));
    }

    @Override
    public CompletableFuture<Optional<org.domi.furnace.api.nbt.NbtCompound>> blockEntityNbt(BlockPos pos) {
        Pos p = new Pos(pos.x(), pos.y(), pos.z());
        return atBlockEntity(pos, Optional.empty(),
                (level, be) -> Optional.of(ApiNbt.toApi(org.domi.furnace.server.command.builtin.DataCommands.blockEntityData(level, be, p))));
    }

    @Override
    public CompletableFuture<Boolean> editBlockEntityNbt(BlockPos pos, java.util.function.Consumer<org.domi.furnace.api.nbt.NbtCompound> edit) {
        java.util.Objects.requireNonNull(edit, "edit");
        Pos p = new Pos(pos.x(), pos.y(), pos.z());
        return atBlockEntity(pos, false, (level, be) -> {
            var nbt = ApiNbt.toApi(org.domi.furnace.server.command.builtin.DataCommands.blockEntityData(level, be, p));
            var before = nbt.copy();
            edit.accept(nbt);
            if (nbt.equals(before)) return false;
            org.domi.furnace.server.command.builtin.DataCommands.setBlockEntityData(level, be, p, ApiNbt.toServer(nbt));
            level.broadcastChanges();
            return true;
        });
    }
}
