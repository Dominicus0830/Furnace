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

    private final SpawnPolicyImpl spawnPolicy;

    WorldHandle(PluginHost host, World world) {
        this.host = host;
        this.world = world;
        this.spawnPolicy = new SpawnPolicyImpl(world.spawnPolicy());
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

    @Override
    public CompletableFuture<Boolean> regenerateChunk(int chunkX, int chunkZ, org.domi.furnace.api.world.ChunkRegeneration options) {
        java.util.Objects.requireNonNull(options, "options");
        return world.regenerateChunk(chunkX, chunkZ, options.removeEntities(), options.generationEntities());
    }

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

    @Override
    public Optional<String> biomeNow(BlockPos pos) {
        Region r = ownedRegion(pos, "biomeNow(" + pos + ")");
        ChunkPos cp = new ChunkPos(pos.chunkX(), pos.chunkZ());
        if (r == null || !r.chunkLoaded(cp)) return Optional.empty();   // 높이 밖은 끝 칸(LevelChunk.getNoiseBiome 처럼 죈다)
        return Optional.ofNullable(biomeName(r.noiseBiomeIdAtBlock(pos.x(), pos.y(), pos.z())));
    }

    @Override
    public boolean setBiomeNow(BlockPos pos, String biome) {
        int id = biomeId(biome);
        Region r = ownedRegion(pos, "setBiomeNow(" + pos + ")");
        ChunkPos cp = new ChunkPos(pos.chunkX(), pos.chunkZ());
        if (r == null || !r.chunkLoaded(cp)) return false;
        return r.setNoiseBiomeAtBlock(pos.x(), pos.y(), pos.z(), id);
    }

    // ------------------------------------------------------------------ 소리·입자

    @Override
    public void playSound(org.domi.furnace.api.audience.Sound sound, double x, double y, double z, long seed) {
        java.util.Objects.requireNonNull(sound, "sound");
        var pkt = ApiEffects.soundAt(sound, x, y, z, seed);
        double range = ApiEffects.range(sound.volume());
        // PlayerList.broadcast: 같은 차원에서 거리 제곱 < range²
        world.forEachPlayerHere(p -> {
            if (p.isDisconnected()) return;
            double dx = x - p.x(), dy = y - p.y(), dz = z - p.z();
            if (dx * dx + dy * dy + dz * dz < range * range) p.send(pkt);
        });
    }

    @Override
    public void playSound(org.domi.furnace.api.audience.Sound sound, Entity entity, long seed) {
        java.util.Objects.requireNonNull(sound, "sound");
        java.util.Objects.requireNonNull(entity, "entity");
        // Paper: TrackedEntity.broadcastAndSend — 그 엔티티를 보는 사람 + 플레이어면 자기 자신
        if (entity instanceof PlayerHandle ph) {
            var self = ph.handle();
            if (self.isDisconnected() || !world.dimension().equals(self.dimension())) return;
            var pkt = ApiEffects.soundOn(sound, self.entityId(), seed);
            world.broadcastToTrackers(self.entityId(), pkt);
            self.send(pkt);
        } else if (entity instanceof EntityHandle eh && eh.tasks().owner() instanceof org.domi.furnace.server.entity.Entity e) {
            if (e.isRemoved() || !world.dimension().equals(e.dimensionKey())) return;
            world.broadcastToTrackers(e.id(), ApiEffects.soundOn(sound, e.id(), seed));
        }
    }

    @Override
    public void spawnParticle(org.domi.furnace.api.world.ParticleEffect particle, java.util.Collection<? extends org.domi.furnace.api.entity.Player> receivers,
                              double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, double speed, boolean force) {
        var opts = ApiEffects.particle(particle, world.blockRegistry(), world.itemRegistry());
        var pkt = new org.domi.furnace.server.net.protocol.play.LevelParticles(force, x, y, z, (float) offsetX, (float) offsetY, (float) offsetZ,
                (float) speed, count, opts);
        java.util.function.Consumer<org.domi.furnace.server.entity.Player> send = p -> {
            // ServerLevel.sendParticles(player, …): 플레이어가 이 레벨에 있고 칸 가운데가 범위 안
            if (p.isDisconnected() || !world.dimension().equals(p.dimension())) return;
            if (org.domi.furnace.server.block.ChunkLevel.particleInRange(p, force, x, y, z)) p.send(pkt);
        };
        if (receivers == null) {
            world.forEachPlayerHere(send);
        } else {
            for (var r : receivers) if (r instanceof PlayerHandle ph) send.accept(ph.handle());
        }
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
        CompletableFuture<Entity> f = world.submitAtOwnedChunk(cp, r -> spawnOn(r, cp, id, at));
        return RegionFuture.wrap(f != null ? f : CompletableFuture.failedFuture(new IllegalStateException("청크가 로드돼 있지 않다: " + cp)));
    }

    /** 소유 스레드에서: /summon 과 같은 경로로 만든다. 스폰 이벤트가 취소되면 null. */
    private Entity spawnOn(Region r, ChunkPos cp, String id, Location at) {
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
    }

    private String spawnableId(String type, Location at) {
        String id = type.indexOf(':') < 0 ? "minecraft:" + type : type;
        if (!org.domi.furnace.server.command.arguments.EntityTypes.isKnown(id) || id.equals("minecraft:player") || id.equals("minecraft:fishing_bobber")) {
            throw new IllegalArgumentException("소환할 수 없는 엔티티 종류: " + type);
        }
        if (!at.world().equals(world.dimension())) throw new IllegalArgumentException("다른 차원의 위치: " + at.world());
        if (!Double.isFinite(at.x()) || !Double.isFinite(at.y()) || !Double.isFinite(at.z()) || Math.abs(at.x()) >= 3.0E7 || Math.abs(at.z()) >= 3.0E7) {
            throw new IllegalArgumentException("잘못된 위치: " + at);
        }
        return id;
    }

    @Override
    public Entity spawnEntityNow(String type, Location at) {
        String id = spawnableId(type, at);
        ChunkPos cp = ChunkPos.ofBlock(at.x(), at.z());
        Region r = ownedRegion(new BlockPos(cp.x() << 4, 0, cp.z() << 4), "spawnEntityNow");
        if (r == null) throw new IllegalStateException("청크가 로드돼 있지 않다: " + cp);
        return spawnOn(r, cp, id, at);
    }

    /** 소유 스레드에서: 구 안의 엔티티와 플레이어. */
    private List<Entity> nearbyOn(Region r, Location center, double radius) {
        double r2 = radius * radius;
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
    }

    @Override
    public List<Entity> nearbyEntitiesNow(Location center, double radius) {
        if (!(radius >= 0 && radius <= 128)) throw new IllegalArgumentException("radius 는 0..128: " + radius);
        if (!center.world().equals(world.dimension())) return List.of();
        ChunkPos cp = ChunkPos.ofBlock(center.x(), center.z());
        Region r = ownedRegion(new BlockPos(cp.x() << 4, 0, cp.z() << 4), "nearbyEntitiesNow");
        if (r == null || !r.chunkLoaded(cp)) return List.of();
        return nearbyOn(r, center, radius);
    }

    @Override
    public List<Entity> entitiesInBoxNow(org.domi.furnace.api.world.Aabb box) {
        if (!Double.isFinite(box.minX() + box.minY() + box.minZ() + box.maxX() + box.maxY() + box.maxZ())) return List.of();
        ChunkPos cp = ChunkPos.ofBlock(box.centerX(), box.centerZ());
        Region r = ownedRegion(new BlockPos(cp.x() << 4, 0, cp.z() << 4), "entitiesInBoxNow");
        if (r == null || !r.chunkLoaded(cp)) return List.of();
        var bb = ApiBoxes.of(box);
        List<Entity> out = new ArrayList<>();
        for (var e : r.level().getEntities(null, bb, null)) {
            if (!e.isRemoved()) out.add(EntityHandle.of(host, e));
        }
        for (var p : r.players().values()) {
            if (!p.isDisconnected() && p.boundingBox().intersects(bb)) out.add(host.player(p));
        }
        return out;
    }

    @Override
    public java.util.Optional<org.domi.furnace.api.world.BlockHit> rayTraceBlocksNow(double fx, double fy, double fz, double tx, double ty, double tz,
            org.domi.furnace.api.world.FluidPick fluid, boolean ignorePassable, java.util.function.Predicate<BlockPos> canCollide) {
        ChunkPos cp = ChunkPos.ofBlock(fx, fz);
        Region r = ownedRegion(new BlockPos(cp.x() << 4, 0, cp.z() << 4), "rayTraceBlocksNow");
        if (r == null || !Double.isFinite(fx + fy + fz + tx + ty + tz)) return java.util.Optional.empty();
        var mode = switch (fluid) {
            case NONE -> org.domi.furnace.server.block.Clip.FluidMode.NONE;
            case SOURCE_ONLY -> org.domi.furnace.server.block.Clip.FluidMode.SOURCE_ONLY;
            case ANY -> org.domi.furnace.server.block.Clip.FluidMode.ANY;
        };
        java.util.function.Predicate<org.domi.furnace.server.block.Pos> pass = canCollide == null ? null : p -> canCollide.test(new BlockPos(p.x(), p.y(), p.z()));
        var hit = org.domi.furnace.server.block.Clip.clipApi(r.level(), fx, fy, fz, tx, ty, tz, mode, ignorePassable, pass);
        if (hit.miss()) return java.util.Optional.empty();
        return java.util.Optional.of(new org.domi.furnace.api.world.BlockHit(new BlockPos(hit.pos().x(), hit.pos().y(), hit.pos().z()),
                org.domi.furnace.api.world.Direction.values()[hit.face()], hit.x(), hit.y(), hit.z(), hit.inside()));
    }

    @Override
    public CompletableFuture<List<Entity>> nearbyEntities(Location center, double radius) {
        if (!(radius >= 0 && radius <= 128)) return RegionFuture.wrap(CompletableFuture.failedFuture(new IllegalArgumentException("radius 는 0..128: " + radius)));
        if (!center.world().equals(world.dimension())) return RegionFuture.wrap(CompletableFuture.completedFuture(List.of()));
        ChunkPos cp = ChunkPos.ofBlock(center.x(), center.z());
        double r2 = radius * radius;
        CompletableFuture<List<Entity>> f = world.submitAtOwnedChunk(cp, r -> nearbyOn(r, center, radius));
        return RegionFuture.wrap(f != null ? f : CompletableFuture.completedFuture(List.of()));
    }

    // ------------------------------------------------------------------ id 로 찾기

    /** 이 월드에 접속해 있는 그 id 의 플레이어(플레이어는 엔티티 색인에 없다). */
    private Entity playerById(int id) {
        for (var p : host.core().players()) {
            if (p.entityId() == id && !p.isDisconnected() && world.dimension().equals(p.dimension())) return host.player(p);
        }
        return null;
    }

    @Override
    public Optional<java.util.UUID> entityUuidById(int entityId) {
        var e = world.entityIndex().get(entityId);
        if (e != null && !e.isRemoved()) return Optional.of(e.uuid());   // uuid 는 바뀌지 않는다(어느 스레드나 안전)
        Entity p = playerById(entityId);
        return p == null ? Optional.empty() : Optional.of(p.uniqueId());
    }

    @Override
    public Optional<Entity> entityByIdNow(int entityId) {
        var e = world.entityIndex().get(entityId);
        if (e == null || e.isRemoved()) return Optional.ofNullable(playerById(entityId));
        // 엔티티를 가진 region 스레드(빌림 구간이면 빌려서)에서만 — 아니면 낡은 값을 읽지 않고 소유 스레드가 아니라고 답한다.
        // region 사이를 넘어가는 중(옛 저장소에서 빠졌고 새 쪽이 아직 받기 전)에는 level 이 옛 쪽이라도 저장소 소속을 본다.
        boolean owned = (org.domi.furnace.server.world.RegionBorrow.claimEntity(e) || (e.level() != null && e.level().isOwnerThread()))
                && e.level() != null && e.level().entities().contains(e);
        if (!owned) {
            throw new ThreadOwnershipException("entityByIdNow 는 그 엔티티를 가진 region 스레드에서만 부를 수 있다 (현재 "
                    + Thread.currentThread().getName() + ") — entityById 를 쓴다");
        }
        return e.isRemoved() ? Optional.empty() : Optional.of(EntityHandle.of(host, e));
    }

    @Override
    public CompletableFuture<Optional<Entity>> entityById(int entityId) {
        return RegionFuture.wrap(entityByIdAttempt(entityId, 0));
    }

    /**
     * 색인에서 찾은 엔티티를 가진 region 에서 읽는다. 청크 경계를 넘은 틱에는 엔티티가 새 청크 쪽에 가 있어도 옛 region 저장소에서 아직
     * 안 빠졌다(틱 끝 handOffEntities) — 새 칸의 주인에 없으면 지난 틱 칸(xo, zo)의 주인에서 한 번 더 찾고, 그래도 없으면(그 사이 옮겨 갔으면) 몇 번 다시 찾는다.
     */
    private CompletableFuture<Optional<Entity>> entityByIdAttempt(int id, int attempt) {
        var e = world.entityIndex().get(id);
        if (e == null || e.isRemoved()) return CompletableFuture.completedFuture(Optional.ofNullable(playerById(id)));
        ChunkPos now = ChunkPos.ofBlock(e.x(), e.z());
        ChunkPos before = ChunkPos.ofBlock(e.xo, e.zo);
        CompletableFuture<Optional<Entity>> f = lookupAt(now, id);
        if (f != null && !before.equals(now)) {
            f = f.thenCompose(found -> {
                if (found != null) return CompletableFuture.completedFuture(found);
                CompletableFuture<Optional<Entity>> g = lookupAt(before, id);
                return g != null ? g : CompletableFuture.<Optional<Entity>>completedFuture(null);
            });
        }
        if (f == null) {
            if (attempt >= MAX_LOOKUP_ATTEMPTS) return CompletableFuture.completedFuture(Optional.empty());
            return entityByIdAttempt(id, attempt + 1);
        }
        return f.thenCompose(found -> {
            if (found != null) return CompletableFuture.completedFuture(found);
            if (attempt >= MAX_LOOKUP_ATTEMPTS) return CompletableFuture.completedFuture(Optional.<Entity>empty());
            return entityByIdAttempt(id, attempt + 1);
        });
    }

    /** 그 청크의 주인 region 에서 id 의 엔티티를 읽는다. 저장소에 없으면 null 로 완료, 그 청크에 주인이 없으면 null(future 없음). */
    private CompletableFuture<Optional<Entity>> lookupAt(ChunkPos cp, int id) {
        return world.submitAtOwnedChunk(cp, r -> {
            var cur = r.level().entities().get(id);
            if (cur == null) return null;
            return cur.isRemoved() ? Optional.<Entity>empty() : Optional.<Entity>of(EntityHandle.of(host, cur));
        });
    }

    private static final int MAX_LOOKUP_ATTEMPTS = 3;

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

    // ------------------------------------------------------------------ 스폰 정책·엔티티 수

    @Override
    public org.domi.furnace.api.world.SpawnPolicy spawnPolicy() { return spawnPolicy; }

    @Override
    public CompletableFuture<org.domi.furnace.api.world.EntityCounts> entityCounts() {
        EntityTally total = new EntityTally();
        List<CompletableFuture<Void>> fs = world.forEachRegion(r -> {
            EntityTally local = new EntityTally();
            for (var e : r.level().entities().all()) if (!e.isRemoved()) local.add(e);
            for (var p : r.players().values()) if (!p.isDisconnected()) local.addPlayer();
            total.merge(local);
        });
        return RegionFuture.wrap(CompletableFuture.allOf(fs.toArray(new CompletableFuture[0])).thenApply(v -> total.build()));
    }

    // ------------------------------------------------------------------ 마을·POI·습격

    private <T> CompletableFuture<T> onBlock(BlockPos pos, java.util.function.BiFunction<Region, Pos, T> task, T notLoaded) {
        ChunkPos cp = new ChunkPos(pos.chunkX(), pos.chunkZ());
        CompletableFuture<T> f = world.submitAtOwnedChunk(cp, r -> r.chunkLoaded(cp) ? task.apply(r, new Pos(pos.x(), pos.y(), pos.z())) : notLoaded);
        return RegionFuture.wrap(f != null ? f : CompletableFuture.completedFuture(notLoaded));
    }

    @Override
    public CompletableFuture<Boolean> isVillage(BlockPos pos) {
        return onBlock(pos, (r, p) -> r.level().poiManager().isVillage(p), false);
    }

    @Override
    public CompletableFuture<Integer> sectionsToVillage(BlockPos pos) {
        return onBlock(pos, (r, p) -> r.level().poiManager().sectionsToVillage(p.x() >> 4, p.y() >> 4, p.z() >> 4),
                org.domi.furnace.server.world.poi.PoiManager.MAX_VILLAGE_DISTANCE + 1);
    }

    @Override
    public CompletableFuture<List<org.domi.furnace.api.world.PointOfInterest>> pointsOfInterest(BlockPos center, int radius,
            java.util.Set<String> types, org.domi.furnace.api.world.PoiOccupancy occupancy) {
        if (radius < 0 || radius > 128) return RegionFuture.wrap(CompletableFuture.failedFuture(new IllegalArgumentException("radius 는 0..128: " + radius)));
        java.util.Objects.requireNonNull(types, "types");
        java.util.Objects.requireNonNull(occupancy, "occupancy");
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (String t : types) keys.add(t.indexOf(':') < 0 ? "minecraft:" + t : t);
        var occ = org.domi.furnace.server.world.poi.PoiManager.Occupancy.valueOf(occupancy.name());
        return onBlock(center, (r, p) -> {
            var found = r.level().poiManager().findAllClosestFirstWithType(t -> keys.isEmpty() || keys.contains(t.name()), q -> true, p, radius, occ);
            List<org.domi.furnace.api.world.PointOfInterest> out = new ArrayList<>(found.size());
            for (var rec : found) {
                out.add(new org.domi.furnace.api.world.PointOfInterest(rec.type().name(), new BlockPos(rec.pos().x(), rec.pos().y(), rec.pos().z()),
                        rec.freeTickets(), rec.type().maxTickets()));
            }
            return out;
        }, List.of());
    }

    private org.domi.furnace.api.world.RaidInfo toApi(org.domi.furnace.server.world.raid.Raid r) {
        var status = switch (r.status()) {
            case ONGOING -> org.domi.furnace.api.world.RaidStatus.ONGOING;
            case VICTORY -> org.domi.furnace.api.world.RaidStatus.VICTORY;
            case LOSS -> org.domi.furnace.api.world.RaidStatus.LOSS;
            case STOPPED -> org.domi.furnace.api.world.RaidStatus.STOPPED;
        };
        var c = r.center();
        return new org.domi.furnace.api.world.RaidInfo(r.id(), world.dimension(), new BlockPos(c.x(), c.y(), c.z()), status, r.isStarted(), r.isActive(),
                r.raidOmenLevel(), r.groupsSpawned(), r.numGroups() + (r.raidOmenLevel() > 1 ? 1 : 0), r.getTotalRaidersAlive(),
                r.getHealthOfLivingRaiders(), r.raidCooldownTicks(), r.isBetweenWaves(), r.heroesOfTheVillage());
    }

    @Override
    public CompletableFuture<List<org.domi.furnace.api.world.RaidInfo>> raids() {
        List<org.domi.furnace.api.world.RaidInfo> all = java.util.Collections.synchronizedList(new ArrayList<>());
        List<CompletableFuture<Void>> fs = world.forEachRegion(r -> {
            for (var raid : r.level().raids().all().values()) if (raid.isActive() && !raid.isStopped()) all.add(toApi(raid));
        });
        return RegionFuture.wrap(CompletableFuture.allOf(fs.toArray(new CompletableFuture[0])).thenApply(v -> {
            List<org.domi.furnace.api.world.RaidInfo> out = new ArrayList<>(all);
            out.sort(java.util.Comparator.comparingInt(org.domi.furnace.api.world.RaidInfo::id));
            return out;
        }));
    }

    @Override
    public CompletableFuture<Optional<org.domi.furnace.api.world.RaidInfo>> raidAt(BlockPos pos) {
        return onBlock(pos, (r, p) -> {
            var raid = r.level().raids().getRaidAt(p);
            return raid == null ? Optional.<org.domi.furnace.api.world.RaidInfo>empty() : Optional.of(toApi(raid));
        }, Optional.empty());
    }

    @Override
    public CompletableFuture<Boolean> stopRaid(int id) {
        java.util.concurrent.atomic.AtomicBoolean found = new java.util.concurrent.atomic.AtomicBoolean();
        List<CompletableFuture<Void>> fs = world.forEachRegion(r -> {
            var raid = r.level().raids().get(id);
            if (raid != null && !raid.isStopped()) {
                raid.stop();
                found.set(true);
            }
        });
        return RegionFuture.wrap(CompletableFuture.allOf(fs.toArray(new CompletableFuture[0])).thenApply(v -> found.get()));
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
