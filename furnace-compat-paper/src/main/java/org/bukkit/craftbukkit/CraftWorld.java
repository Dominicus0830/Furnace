package org.bukkit.craftbukkit;

import org.domi.furnace.compat.i18n.CompatLang;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import net.minecraft.server.level.ServerLevel;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.compat.paper.CompatEffects;
import org.domi.furnace.compat.paper.UnsupportedFeature;

/**
 * Furnace 월드 위의 Bukkit World. 같은 차원이면 같은 객체(Compat 캐시) — equals 도 차원 키로 본다.
 * CraftBukkit 과 같은 이름이라 {@code (CraftWorld) world} 캐스트와 {@link #getHandle()} 이 된다(H6).
 */
public class CraftWorld extends CraftRegionAccessor implements World {

    private final org.domi.furnace.api.world.World handle;
    private volatile ServerLevel nms;

    /** 호환 모듈 전용(Compat 캐시가 만든다). */
    public CraftWorld(org.domi.furnace.api.world.World handle) { this.handle = handle; }

    /** 호환 모듈 전용 — 이 월드의 Furnace 핸들. */
    public org.domi.furnace.api.world.World furnaceHandle() { return handle; }

    /** Paper World#getEntity(UUID) — 찾는 규칙은 {@link Compat#entityByUuid}. */
    @Override
    public org.bukkit.entity.Entity getEntity(java.util.UUID uuid) {
        if (uuid == null) throw new IllegalArgumentException("UUID cannot be null");
        return Compat.entityByUuid(handle, uuid);
    }

    /** NMS 월드 shim. 같은 CraftWorld 는 같은 shim 을 준다(== 비교·Map 키). 멤버는 일부만 있다(docs/feature-parity/compat-h6-2026-11-07.md). */
    public ServerLevel getHandle() {
        ServerLevel l = nms;
        if (l == null) {
            synchronized (this) {
                l = nms;
                if (l == null) nms = l = new ServerLevel(this);
            }
        }
        return l;
    }

    /** 플러그인이 만든 따로 떨어진 레벨(T-203 임시 월드)이면 그 레벨 — 이름은 그 레벨 데이터를 따른다(Paper 와 같다, {@code checkName}). */
    private volatile ServerLevel scratchLevel;

    /** 호환 모듈 전용: 임시 월드의 CraftWorld 를 그것을 만든 ServerLevel(플러그인의 하위 클래스일 수 있다)에 묶는다. 한 번만. */
    public void bindScratch(ServerLevel level) {
        synchronized (this) {
            if (nms != null) throw new IllegalStateException("this world already has a level");
            nms = level;
            scratchLevel = level;
        }
    }

    @Override
    public String getName() {
        ServerLevel s = scratchLevel;
        return s != null ? s.serverLevelData.getLevelName() : handle.name();
    }

    @Override
    public UUID getUID() { return uidOf(handle.dimension()); }

    /** 레벨 키의 월드 UID(getUID 와 같은 값 — 월드가 생기기 전 바이옴 공급자의 WorldInfo 도 쓴다). */
    public static UUID uidOf(String levelKey) { return UUID.nameUUIDFromBytes(("furnace-world:" + levelKey).getBytes(StandardCharsets.UTF_8)); }

    /** Paper: 만들 때 준 바이옴 공급자(T-210). Bukkit 공급자로 만든 월드가 아니면 null. */
    @Override
    public org.bukkit.generator.BiomeProvider getBiomeProvider() {
        return handle.biomeProvider() instanceof org.domi.furnace.compat.paper.BukkitBiomeProviders.Adapted a ? a.bukkit() : null;
    }

    /** Paper: 만들 때 준 Bukkit 생성기(T-217). */
    @Override
    public org.bukkit.generator.ChunkGenerator getGenerator() {
        return handle.generationHooksObject() instanceof org.domi.furnace.compat.paper.BukkitChunkGenerators.Adapted a ? a.bukkit() : null;
    }

    private volatile java.util.List<org.bukkit.generator.BlockPopulator> populators;

    /** Paper CraftWorld#getPopulators — 이 월드(이 핸들)에 하나. 처음 부를 때 서버 장식기로 건다(T-217). */
    @Override
    public java.util.List<org.bukkit.generator.BlockPopulator> getPopulators() {
        var p = populators;
        if (p == null) {
            synchronized (this) {
                p = populators;
                if (p == null) populators = p = org.domi.furnace.compat.paper.BukkitChunkGenerators.populators(this, handle);
            }
        }
        return p;
    }

    @Override
    public NamespacedKey getKey() {
        NamespacedKey k = NamespacedKey.fromString(handle.dimension());
        return k != null ? k : NamespacedKey.minecraft(getName().toLowerCase(java.util.Locale.ROOT));
    }

    @Override
    public Environment getEnvironment() {
        return switch (handle.environment()) {
            case NORMAL -> Environment.NORMAL;
            case NETHER -> Environment.NETHER;
            case THE_END -> Environment.THE_END;
        };
    }

    @Override
    public long getSeed() { return handle.seed(); }

    @Override
    public org.bukkit.WorldType getWorldType() { return handle.isFlat() ? org.bukkit.WorldType.FLAT : org.bukkit.WorldType.NORMAL; }

    @Override
    public boolean canGenerateStructures() { return handle.generatesStructures(); }

    @Override
    public boolean isHardcore() { return handle.isHardcore(); }

    @Override
    public Block getBlockAt(int x, int y, int z) { return org.bukkit.craftbukkit.block.CraftBlock.of(handle.dimension(), new BlockPos(x, y, z)); }

    // Paper CraftRegionAccessor 의 칸 읽기·쓰기 — 같은 칸의 CraftBlock 과 같은 길(setBlockData 는 물리 적용)
    @Override
    public org.bukkit.block.data.BlockData getBlockData(int x, int y, int z) { return getBlockAt(x, y, z).getBlockData(); }

    @Override
    public org.bukkit.Material getType(int x, int y, int z) { return getBlockAt(x, y, z).getType(); }

    @Override
    public void setBlockData(int x, int y, int z, org.bukkit.block.data.BlockData blockData) {
        getBlockAt(x, y, z).setBlockData(blockData);
    }

    @Override
    public Chunk getChunkAt(int x, int z) { return scratchLevel != null ? new CraftChunk(this, x, z) : new CraftChunk(handle.dimension(), x, z); }

    @Override
    public org.bukkit.entity.Entity spawnEntity(org.bukkit.Location loc, org.bukkit.entity.EntityType type) {
        if (type == null || type == org.bukkit.entity.EntityType.UNKNOWN) throw new IllegalArgumentException("Cannot spawn an entity of type " + type);
        org.domi.furnace.api.entity.Entity e = handle.spawnEntityNow(type.getKey().toString(), Compat.location(loc));
        if (e == null) throw new IllegalStateException("entity spawn was cancelled (a plugin cancelled the spawn event): " + type);
        return Compat.entity(e);
    }

    /** Bukkit 형 → 종류 키. Compat 이 그 형으로 감싸는 종류만. */
    private static String spawnKey(Class<?> clazz) {
        if (clazz == org.bukkit.entity.ArmorStand.class) return "minecraft:armor_stand";
        if (clazz == org.bukkit.entity.Pig.class) return "minecraft:pig";
        return null;
    }

    @Override
    public <T extends org.bukkit.entity.Entity> T spawn(org.bukkit.Location location, Class<T> clazz) { return spawn(location, clazz, (java.util.function.Consumer<? super T>) null); }

    @Override
    public <T extends org.bukkit.entity.Entity> T spawn(org.bukkit.Location location, Class<T> clazz, java.util.function.Consumer<? super T> function) {
        if (location == null) throw new IllegalArgumentException("Location cannot be null");
        if (clazz == null) throw new IllegalArgumentException("Entity class cannot be null");
        String key = spawnKey(clazz);
        if (key == null) {
            if (!clazz.isInterface() || clazz.getPackageName().equals("org.bukkit.entity") && isAbstractKind(clazz)) {
                throw new IllegalArgumentException("Cannot spawn an entity for " + clazz.getName());
            }
            throw new UnsupportedFeature("World#spawn(" + clazz.getSimpleName() + ")", "there is no adapter yet that wraps this Bukkit entity type (only ArmorStand and Pig)");
        }
        org.domi.furnace.api.entity.Entity e = handle.spawnEntityNow(key, Compat.location(location));
        if (e == null) throw new IllegalStateException("entity spawn was cancelled (a plugin cancelled the spawn event): " + clazz.getSimpleName());
        T out = clazz.cast(Compat.entity(e));
        if (function != null) function.accept(out);
        return out;
    }

    /** 종류가 하나로 정해지지 않는 Bukkit 형(Paper 도 소환하지 못한다). */
    private static boolean isAbstractKind(Class<?> c) {
        return c == org.bukkit.entity.Entity.class || c == org.bukkit.entity.LivingEntity.class || c == org.bukkit.entity.Mob.class
                || c == org.bukkit.entity.Creature.class || c == org.bukkit.entity.Ageable.class || c == org.bukkit.entity.Breedable.class
                || c == org.bukkit.entity.Animals.class || c == org.bukkit.entity.Steerable.class || c == org.bukkit.entity.Vehicle.class
                || c == org.bukkit.entity.Enemy.class || c == org.bukkit.entity.Monster.class || c == org.bukkit.entity.HumanEntity.class
                || c == org.bukkit.entity.Player.class;
    }

    // ---- 메타데이터(월드 UID 로)

    @Override
    public void setMetadata(String metadataKey, org.bukkit.metadata.MetadataValue newMetadataValue) { Compat.worldMetadata().setMetadata(this, metadataKey, newMetadataValue); }

    @Override
    public java.util.List<org.bukkit.metadata.MetadataValue> getMetadata(String metadataKey) { return Compat.worldMetadata().getMetadata(this, metadataKey); }

    @Override
    public boolean hasMetadata(String metadataKey) { return Compat.worldMetadata().hasMetadata(this, metadataKey); }

    @Override
    public void removeMetadata(String metadataKey, org.bukkit.plugin.Plugin owningPlugin) { Compat.worldMetadata().removeMetadata(this, metadataKey, owningPlugin); }

    // ---- 주변 엔티티·광선 판정: Paper 1.21.1 CraftWorld 본문과 같은 규칙(검사 문구·순서 포함)

    @Override
    public java.util.Collection<org.bukkit.entity.Entity> getNearbyEntities(org.bukkit.Location loc, double x, double y, double z) {
        return getNearbyEntities(loc, x, y, z, null);
    }

    @Override
    public java.util.Collection<org.bukkit.entity.Entity> getNearbyEntities(org.bukkit.Location loc, double x, double y, double z,
            java.util.function.Predicate<? super org.bukkit.entity.Entity> filter) {
        check(loc != null, "Location cannot be null");
        check(this.equals(loc.getWorld()), "Location cannot be in a different world");
        return getNearbyEntities(org.bukkit.util.BoundingBox.of(loc, x, y, z), filter);
    }

    @Override
    public java.util.Collection<org.bukkit.entity.Entity> getNearbyEntities(org.bukkit.util.BoundingBox box) { return getNearbyEntities(box, null); }

    @Override
    public java.util.Collection<org.bukkit.entity.Entity> getNearbyEntities(org.bukkit.util.BoundingBox box,
            java.util.function.Predicate<? super org.bukkit.entity.Entity> filter) {
        check(box != null, "BoundingBox cannot be null");
        var found = handle.entitiesInBoxNow(new org.domi.furnace.api.world.Aabb(box.getMinX(), box.getMinY(), box.getMinZ(), box.getMaxX(), box.getMaxY(), box.getMaxZ()));
        java.util.List<org.bukkit.entity.Entity> out = new ArrayList<>(found.size());
        for (var e : found) {
            org.bukkit.entity.Entity b = Compat.entity(e);
            if (filter == null || filter.test(b)) out.add(b);
        }
        return out;
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new IllegalArgumentException(message);
    }

    @Override
    public org.bukkit.util.RayTraceResult rayTraceEntities(org.bukkit.Location start, org.bukkit.util.Vector direction, double maxDistance) {
        return rayTraceEntities(start, direction, maxDistance, null);
    }

    @Override
    public org.bukkit.util.RayTraceResult rayTraceEntities(org.bukkit.Location start, org.bukkit.util.Vector direction, double maxDistance, double raySize) {
        return rayTraceEntities(start, direction, maxDistance, raySize, null);
    }

    @Override
    public org.bukkit.util.RayTraceResult rayTraceEntities(org.bukkit.Location start, org.bukkit.util.Vector direction, double maxDistance,
            java.util.function.Predicate<? super org.bukkit.entity.Entity> filter) {
        return rayTraceEntities(start, direction, maxDistance, 0.0D, filter);
    }

    @Override
    public org.bukkit.util.RayTraceResult rayTraceEntities(org.bukkit.Location start, org.bukkit.util.Vector direction, double maxDistance, double raySize,
            java.util.function.Predicate<? super org.bukkit.entity.Entity> filter) {
        return rayTraceEntities((io.papermc.paper.math.Position) start, direction, maxDistance, raySize, filter);
    }

    @Override
    public org.bukkit.util.RayTraceResult rayTraceEntities(io.papermc.paper.math.Position start, org.bukkit.util.Vector direction, double maxDistance, double raySize,
            java.util.function.Predicate<? super org.bukkit.entity.Entity> filter) {
        checkRay(start, direction);
        if (maxDistance < 0.0D) return null;
        org.bukkit.util.Vector startPos = start.toVector();
        org.bukkit.util.Vector dir = direction.clone().normalize().multiply(maxDistance);
        org.bukkit.util.BoundingBox aabb = org.bukkit.util.BoundingBox.of(startPos, startPos).expandDirectional(dir).expand(raySize);
        java.util.Collection<org.bukkit.entity.Entity> entities = getNearbyEntities(aabb, filter);

        org.bukkit.entity.Entity nearestHitEntity = null;
        org.bukkit.util.RayTraceResult nearestHitResult = null;
        double nearestDistanceSq = Double.MAX_VALUE;
        for (org.bukkit.entity.Entity entity : entities) {
            org.bukkit.util.BoundingBox boundingBox = entity.getBoundingBox().expand(raySize);
            org.bukkit.util.RayTraceResult hitResult = boundingBox.rayTrace(startPos, direction, maxDistance);
            if (hitResult != null) {
                double distanceSq = startPos.distanceSquared(hitResult.getHitPosition());
                if (distanceSq < nearestDistanceSq) {
                    nearestHitEntity = entity;
                    nearestHitResult = hitResult;
                    nearestDistanceSq = distanceSq;
                }
            }
        }
        return nearestHitEntity == null ? null : new org.bukkit.util.RayTraceResult(nearestHitResult.getHitPosition(), nearestHitEntity, nearestHitResult.getHitBlockFace());
    }

    @Override
    public org.bukkit.util.RayTraceResult rayTraceBlocks(org.bukkit.Location start, org.bukkit.util.Vector direction, double maxDistance) {
        return rayTraceBlocks(start, direction, maxDistance, org.bukkit.FluidCollisionMode.NEVER, false);
    }

    @Override
    public org.bukkit.util.RayTraceResult rayTraceBlocks(org.bukkit.Location start, org.bukkit.util.Vector direction, double maxDistance,
            org.bukkit.FluidCollisionMode fluidCollisionMode) {
        return rayTraceBlocks(start, direction, maxDistance, fluidCollisionMode, false);
    }

    @Override
    public org.bukkit.util.RayTraceResult rayTraceBlocks(org.bukkit.Location start, org.bukkit.util.Vector direction, double maxDistance,
            org.bukkit.FluidCollisionMode fluidCollisionMode, boolean ignorePassableBlocks) {
        return rayTraceBlocks(start, direction, maxDistance, fluidCollisionMode, ignorePassableBlocks, null);
    }

    @Override
    public org.bukkit.util.RayTraceResult rayTraceBlocks(io.papermc.paper.math.Position start, org.bukkit.util.Vector direction, double maxDistance,
            org.bukkit.FluidCollisionMode fluidCollisionMode, boolean ignorePassableBlocks, java.util.function.Predicate<? super Block> canCollide) {
        checkRay(start, direction);
        check(fluidCollisionMode != null, "FluidCollisionMode cannot be null");
        if (maxDistance < 0.0D) return null;
        org.bukkit.util.Vector dir = direction.clone().normalize().multiply(maxDistance);
        var fluid = switch (fluidCollisionMode) {
            case ALWAYS -> org.domi.furnace.api.world.FluidPick.ANY;
            case SOURCE_ONLY -> org.domi.furnace.api.world.FluidPick.SOURCE_ONLY;
            case NEVER -> org.domi.furnace.api.world.FluidPick.NONE;
        };
        java.util.function.Predicate<BlockPos> pass = canCollide == null ? null : p -> canCollide.test(getBlockAt(p.x(), p.y(), p.z()));
        var hit = handle.rayTraceBlocksNow(start.x(), start.y(), start.z(), start.x() + dir.getX(), start.y() + dir.getY(), start.z() + dir.getZ(), fluid, ignorePassableBlocks, pass);
        if (hit.isEmpty()) return null;
        var h = hit.get();
        return new org.bukkit.util.RayTraceResult(new org.bukkit.util.Vector(h.x(), h.y(), h.z()), getBlockAt(h.pos().x(), h.pos().y(), h.pos().z()),
                org.bukkit.block.BlockFace.valueOf(h.face().name()));
    }

    @Override
    public org.bukkit.util.RayTraceResult rayTrace(org.bukkit.Location start, org.bukkit.util.Vector direction, double maxDistance,
            org.bukkit.FluidCollisionMode fluidCollisionMode, boolean ignorePassableBlocks, double raySize, java.util.function.Predicate<? super org.bukkit.entity.Entity> filter) {
        return rayTrace(start, direction, maxDistance, fluidCollisionMode, ignorePassableBlocks, raySize, filter, null);
    }

    @Override
    public org.bukkit.util.RayTraceResult rayTrace(io.papermc.paper.math.Position start, org.bukkit.util.Vector direction, double maxDistance,
            org.bukkit.FluidCollisionMode fluidCollisionMode, boolean ignorePassableBlocks, double raySize, java.util.function.Predicate<? super org.bukkit.entity.Entity> filter,
            java.util.function.Predicate<? super Block> canCollide) {
        org.bukkit.util.RayTraceResult blockHit = rayTraceBlocks(start, direction, maxDistance, fluidCollisionMode, ignorePassableBlocks, canCollide);
        org.bukkit.util.Vector startVec = null;
        double blockHitDistance = maxDistance;
        // 블록을 맞았으면 엔티티 탐색 거리를 거기까지로 줄인다
        if (blockHit != null) {
            startVec = start.toVector();
            blockHitDistance = startVec.distance(blockHit.getHitPosition());
        }
        org.bukkit.util.RayTraceResult entityHit = rayTraceEntities(start, direction, blockHitDistance, raySize, filter);
        if (blockHit == null) return entityHit;
        if (entityHit == null) return blockHit;
        double entityHitDistanceSquared = startVec.distanceSquared(entityHit.getHitPosition());
        return entityHitDistanceSquared < blockHitDistance * blockHitDistance ? entityHit : blockHit;
    }

    /** Paper CraftWorld 의 시작점·방향 검사(문구까지 같다). */
    private void checkRay(io.papermc.paper.math.Position start, org.bukkit.util.Vector direction) {
        check(start != null, "Location start cannot be null");
        check(!(start instanceof org.bukkit.Location location) || this.equals(location.getWorld()), "Location start cannot be in a different world");
        check(start.isFinite(), "Location start is not finite");
        check(direction != null, "Vector direction cannot be null");
        direction.checkFinite();
        check(direction.lengthSquared() > 0, "Direction's magnitude (" + direction.lengthSquared() + ") need to be greater than 0");
    }

    @Override
    public boolean isChunkLoaded(int x, int z) { return handle.isChunkLoaded(x, z); }

    @Override
    public boolean refreshChunk(int x, int z) { return handle.refreshChunk(x, z); }

    /** 결과는 그 청크의 region 스레드에서 완료된다. 저장된 청크가 없고 gen 이 거짓이면 null(Paper 와 같다). */
    @Override
    public java.util.concurrent.CompletableFuture<Chunk> getChunkAtAsync(int x, int z, boolean gen, boolean urgent) {
        return handle.loadChunkAsync(x, z, gen).thenApply(loaded -> loaded ? getChunkAt(x, z) : null);
    }

    @Override
    public List<Player> getPlayers() {
        List<Player> out = new ArrayList<>();
        for (var p : Furnace.server().onlinePlayers()) {
            if (p.location().world().equals(handle.dimension())) out.add(Compat.player(p));
        }
        return out;
    }

    // ---- 시간·날씨·스폰·폴더(T-141) — Paper CraftWorld 와 같은 계산. 시계 주인(오버월드)이 아닌 월드의 쓰기는 경고 한 번 뒤 무시.

    private static final java.util.Set<String> WARNED_CLOCK = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** 호환 모듈 전용 — 시계 주인이 아닌 월드의 시간·날씨 쓰기 경고(이름마다 한 번). NMS ServerLevel 도 같은 경고를 쓴다. */
    public void furnaceSharedClockWrite(boolean applied, String what) { sharedClockWrite(applied, what); }

    private void sharedClockWrite(boolean applied, String what) {
        if (applied || !WARNED_CLOCK.add(handle.dimension() + "|" + what)) return;
        System.getLogger("FurnaceCompat").log(System.Logger.Level.WARNING, org.domi.furnace.compat.i18n.CompatLang.t("compat.clock-shared", what, getName()));
    }

    // ---- 거리(T-141 E4c): Paper RegionizedPlayerChunkLoader 의 월드 값(tick = 시뮬레이션 거리, load = 시야 거리 + 1)

    @Override
    public int getViewDistance() { return Compat.furnace().viewDistance(); }

    @Override
    public int getSimulationDistance() { return Math.min(Compat.furnace().simulationDistance(), Compat.furnace().viewDistance() + 1); }

    @Override
    public int getSendViewDistance() { return Compat.furnace().viewDistance(); }

    @Override
    public void setViewDistance(int viewDistance) {
        if (viewDistance < 2 || viewDistance > 32) throw new IllegalArgumentException("View distance " + viewDistance + " is out of range of [2, 32]");
        if (handle.ownsClock()) Compat.furnace().setViewDistance(viewDistance);
        else serverWideWrite("setViewDistance");
    }

    @Override
    public void setSimulationDistance(int simulationDistance) {
        if (simulationDistance < 2 || simulationDistance > 32) {
            throw new IllegalArgumentException("Simulation distance " + simulationDistance + " is out of range of [2, 32]");
        }
        if (handle.ownsClock()) Compat.furnace().setSimulationDistance(simulationDistance);
        else serverWideWrite("setSimulationDistance");
    }

    /** Paper 는 월드별 보내는 거리를 둔다. Furnace 는 아직 없다(플레이어별 Player#setSendViewDistance 는 있다) — 경고 한 번 뒤 무시. */
    @Override
    public void setSendViewDistance(int viewDistance) {
        if (WARNED_CLOCK.add(handle.dimension() + "|setSendViewDistance")) {
            System.getLogger("FurnaceCompat").log(System.Logger.Level.WARNING, org.domi.furnace.compat.i18n.CompatLang.t("compat.send-distance-ignored", getName()));
        }
    }

    private void serverWideWrite(String what) {
        if (!WARNED_CLOCK.add(handle.dimension() + "|" + what)) return;
        System.getLogger("FurnaceCompat").log(System.Logger.Level.WARNING, org.domi.furnace.compat.i18n.CompatLang.t("compat.distance-shared", what, getName()));
    }

    @Override
    public long getTime() {
        long time = getFullTime() % 24000;
        if (time < 0) time += 24000;
        return time;
    }

    @Override
    public void setTime(long time) {
        long full = getFullTime();   // 한 번만 읽는다(사이에 전역 틱이 끼어도 한 값으로 계산)
        long margin = (time - full) % 24000;
        if (margin < 0) margin += 24000;
        setFullTime(full + margin);
    }

    @Override
    public long getFullTime() { return handle.dayTime(); }

    @Override
    public void setFullTime(long time) { sharedClockWrite(handle.setDayTime(time), "setFullTime"); }

    @Override
    public long getGameTime() { return handle.gameTime(); }

    @Override
    public boolean hasStorm() { return handle.weather().raining(); }

    @Override
    public void setStorm(boolean hasStorm) { sharedClockWrite(handle.setWeather(hasStorm, null, 0, null, 0), "setStorm"); }

    @Override
    public int getWeatherDuration() { return handle.weather().rainTime(); }

    @Override
    public void setWeatherDuration(int duration) { sharedClockWrite(handle.setWeather(null, null, duration, null, null), "setWeatherDuration"); }

    @Override
    public boolean isThundering() { return handle.weather().thundering(); }

    @Override
    public void setThundering(boolean thundering) { sharedClockWrite(handle.setWeather(null, thundering, null, 0, 0), "setThundering"); }

    @Override
    public int getThunderDuration() { return handle.weather().thunderTime(); }

    @Override
    public void setThunderDuration(int duration) { sharedClockWrite(handle.setWeather(null, null, null, duration, null), "setThunderDuration"); }

    @Override
    public boolean isClearWeather() { return !hasStorm() && !isThundering(); }

    @Override
    public void setClearWeatherDuration(int duration) { sharedClockWrite(handle.setWeather(null, null, null, null, duration), "setClearWeatherDuration"); }

    @Override
    public int getClearWeatherDuration() { return handle.weather().clearWeatherTime(); }

    @Override
    public org.bukkit.Location getSpawnLocation() {
        org.domi.furnace.api.world.Location l = handle.spawnLocation();
        return new org.bukkit.Location(this, l.x(), l.y(), l.z(), l.yaw(), 0.0F);
    }

    @Override
    public boolean setSpawnLocation(org.bukkit.Location location) {
        if (location == null) throw new IllegalArgumentException("location");
        return equals(location.getWorld()) && setSpawnLocation(location.getBlockX(), location.getBlockY(), location.getBlockZ(), location.getYaw());
    }

    @Override
    public boolean setSpawnLocation(int x, int y, int z, float angle) {
        boolean ok = handle.setSpawnLocation(x, y, z, angle);
        sharedClockWrite(ok, "setSpawnLocation");
        return ok;
    }

    @Override
    public java.io.File getWorldFolder() { return handle.folder().toFile(); }

    @Override
    public List<org.bukkit.entity.Entity> getEntities() {
        List<org.bukkit.entity.Entity> out = new ArrayList<>();
        for (var e : handle.entitiesSnapshot()) out.add(Compat.entity(e));
        return out;
    }

    @Override
    public List<org.bukkit.entity.LivingEntity> getLivingEntities() {
        List<org.bukkit.entity.LivingEntity> out = new ArrayList<>();
        for (var e : handle.entitiesSnapshot()) if (Compat.entity(e) instanceof org.bukkit.entity.LivingEntity l) out.add(l);
        return out;
    }

    @Override
    public org.bukkit.entity.Item dropItem(org.bukkit.Location loc, org.bukkit.inventory.ItemStack item, java.util.function.Consumer<? super org.bukkit.entity.Item> function) {
        return drop(loc, item, false, function);
    }

    @Override
    public org.bukkit.entity.Item dropItemNaturally(org.bukkit.Location loc, org.bukkit.inventory.ItemStack item, java.util.function.Consumer<? super org.bukkit.entity.Item> function) {
        return drop(loc, item, true, function);
    }

    private org.bukkit.entity.Item drop(org.bukkit.Location loc, org.bukkit.inventory.ItemStack item, boolean naturally, java.util.function.Consumer<? super org.bukkit.entity.Item> function) {
        if (loc == null) throw new IllegalArgumentException("Location cannot be null");
        if (item == null) throw new IllegalArgumentException("ItemStack cannot be null");
        org.bukkit.entity.Item[] out = new org.bukkit.entity.Item[1];
        org.domi.furnace.api.entity.Entity e = handle.dropItemNow(Compat.location(loc), org.domi.furnace.compat.paper.CompatItems.toFurnace(item), naturally, h -> {
            out[0] = (org.bukkit.entity.Item) Compat.entity(h);
            if (function != null) function.accept(out[0]);
        });
        // Paper 는 스폰이 취소돼도 만든 Item 을 돌려준다(isValid 거짓)
        return out[0] != null ? out[0] : e == null ? null : (org.bukkit.entity.Item) Compat.entity(e);
    }

    @Override
    public int getMinHeight() {
        return switch (handle.dimensionType()) {
            case "minecraft:overworld" -> -64;
            case "minecraft:the_nether", "minecraft:the_end" -> 0;
            default -> throw new UnsupportedFeature("World#getMinHeight(" + handle.dimensionType() + ")", "the height of a dimension type made by a plugin is not exposed yet");
        };
    }

    @Override
    public int getMaxHeight() {
        return switch (handle.dimensionType()) {
            case "minecraft:overworld" -> 320;
            case "minecraft:the_nether" -> 256;
            case "minecraft:the_end" -> 256;
            default -> throw new UnsupportedFeature("World#getMaxHeight(" + handle.dimensionType() + ")", "the height of a dimension type made by a plugin is not exposed yet");
        };
    }

    // ---- 게임 규칙(T-141 E4a): Paper 1.21.1 CraftWorld 와 같은 검사·이벤트 순서. 값은 Furnace 서버 게임 규칙(서버 전체 하나)에 읽고 쓴다

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("Furnace");
    private static final java.util.Map<String, org.domi.furnace.api.world.GameRule<?>> VANILLA_RULES = new java.util.HashMap<>();

    static {
        for (org.bukkit.GameRule<?> r : org.bukkit.GameRule.values())
            VANILLA_RULES.put(r.getName(), r.getType() == Boolean.class ? org.domi.furnace.api.world.GameRule.bool(r.getName()) : org.domi.furnace.api.world.GameRule.integer(r.getName()));
    }

    private static org.domi.furnace.api.world.GameRuleRegistry gameRules() { return Compat.furnace().gameRules(); }

    @Override
    public String getGameRuleValue(String rule) {
        if (rule == null) return null;
        var r = VANILLA_RULES.get(rule);
        return r == null ? "" : String.valueOf(gameRules().get(r));
    }

    @Override
    public boolean setGameRuleValue(String rule, String value) {
        if (rule == null || value == null) return false;
        if (!isGameRule(rule)) return false;
        var event = new io.papermc.paper.event.world.WorldGameRuleChangeEvent(this, null, org.bukkit.GameRule.getByName(rule), value);
        if (!event.callEvent()) return false;
        deserialize(VANILLA_RULES.get(rule), event.getValue());
        return true;
    }

    @Override
    public String[] getGameRules() { return VANILLA_RULES.keySet().toArray(new String[0]); }

    @Override
    public boolean isGameRule(String rule) {
        if (rule == null) throw new IllegalArgumentException("String rule cannot be null");
        if (rule.isEmpty()) throw new IllegalArgumentException("String rule cannot be empty");
        return VANILLA_RULES.containsKey(rule);
    }

    @Override
    public <T> T getGameRuleValue(org.bukkit.GameRule<T> rule) {
        if (rule == null) throw new IllegalArgumentException("GameRule cannot be null");
        return rule.getType().cast(gameRules().get(VANILLA_RULES.get(rule.getName())));
    }

    @Override
    public <T> T getGameRuleDefault(org.bukkit.GameRule<T> rule) {
        if (rule == null) throw new IllegalArgumentException("GameRule cannot be null");
        return rule.getType().cast(gameRules().defaultValue(VANILLA_RULES.get(rule.getName())));
    }

    @Override
    public <T> boolean setGameRule(org.bukkit.GameRule<T> rule, T newValue) {
        if (rule == null) throw new IllegalArgumentException("GameRule cannot be null");
        if (newValue == null) throw new IllegalArgumentException("GameRule value cannot be null");
        if (!isGameRule(rule.getName())) return false;
        var event = new io.papermc.paper.event.world.WorldGameRuleChangeEvent(this, null, rule, String.valueOf(newValue));
        if (!event.callEvent()) return false;
        deserialize(VANILLA_RULES.get(rule.getName()), event.getValue());
        return true;
    }

    /** 바닐라 GameRules.Value#deserialize: 불리언은 Boolean.parseBoolean, 정수는 빈 문자열·숫자 아님이면 0(숫자 아님은 경고 한 줄). */
    @SuppressWarnings("unchecked")
    private static void deserialize(org.domi.furnace.api.world.GameRule<?> rule, String value) {
        if (rule.type() == Boolean.class) {
            gameRules().set((org.domi.furnace.api.world.GameRule<Boolean>) rule, Boolean.parseBoolean(value));
            return;
        }
        int v = 0;
        if (!value.isEmpty()) {
            try {
                v = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                LOG.warn("Failed to parse integer {}", value);
            }
        }
        gameRules().set((org.domi.furnace.api.world.GameRule<Integer>) rule, v);
    }

    @Override
    public boolean getKeepSpawnInMemory() { return getGameRuleValue(org.bukkit.GameRule.SPAWN_CHUNK_RADIUS) > 0; }

    @Override
    public void setKeepSpawnInMemory(boolean keepLoaded) {
        // T141E4a-D1 결정 B: 게임 규칙은 서버 전체 하나라 주 오버월드(시계 주인)가 아닌 월드에서 바꾸면 무시하고 경고 한 번(시간·날씨와 같은 방침)
        if (!handle.ownsClock()) {
            if (WARNED_CLOCK.add(handle.dimension() + "|setKeepSpawnInMemory")) {
                System.getLogger("FurnaceCompat").log(System.Logger.Level.WARNING, org.domi.furnace.compat.i18n.CompatLang.t("compat.keep-spawn-shared", getName()));
            }
            return;
        }
        if (keepLoaded) setGameRule(org.bukkit.GameRule.SPAWN_CHUNK_RADIUS, getGameRuleDefault(org.bukkit.GameRule.SPAWN_CHUNK_RADIUS));
        else setGameRule(org.bukkit.GameRule.SPAWN_CHUNK_RADIUS, 0);
    }

    // ---- 나무 생성(T-141 E5) — Paper 1.21.1 CraftWorld·CraftRegionAccessor#generateTree

    /** CraftWorld.rand: generateTree(loc, type) 가 쓰는 서버 공용 난수(시드 없음). */
    private static final java.util.Random RAND = new java.util.Random();
    private static final java.util.Set<String> WARNED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** CraftRegionAccessor#generateTree 의 TreeType → TreeFeatures 표(CHORUS_PLANT 는 따로). 플러그인 월드는 늘 "보통 월드"라 균류는 planted. */
    public static String treeFeature(org.bukkit.TreeType type) {   // 호환 모듈 전용(시험이 표를 확인한다)
        return switch (type) {
            case BIG_TREE -> "minecraft:fancy_oak";
            case BIRCH -> "minecraft:birch";
            case REDWOOD -> "minecraft:spruce";
            case TALL_REDWOOD -> "minecraft:pine";
            case JUNGLE -> "minecraft:mega_jungle_tree";
            case SMALL_JUNGLE -> "minecraft:jungle_tree_no_vine";
            case COCOA_TREE -> "minecraft:jungle_tree";
            case JUNGLE_BUSH -> "minecraft:jungle_bush";
            case RED_MUSHROOM -> "minecraft:huge_red_mushroom";
            case BROWN_MUSHROOM -> "minecraft:huge_brown_mushroom";
            case SWAMP -> "minecraft:swamp_oak";
            case ACACIA -> "minecraft:acacia";
            case DARK_OAK -> "minecraft:dark_oak";
            case MEGA_REDWOOD -> "minecraft:mega_spruce";
            case MEGA_PINE -> "minecraft:mega_pine";
            case TALL_BIRCH -> "minecraft:super_birch_bees_0002";
            case CHORUS_PLANT -> null;
            case CRIMSON_FUNGUS -> "minecraft:crimson_fungus_planted";
            case WARPED_FUNGUS -> "minecraft:warped_fungus_planted";
            case AZALEA -> "minecraft:azalea_tree";
            case MANGROVE -> "minecraft:mangrove";
            case TALL_MANGROVE -> "minecraft:tall_mangrove";
            case CHERRY -> "minecraft:cherry";
            case TREE, CUSTOM -> "minecraft:oak";
        };
    }

    @Override
    public boolean generateTree(org.bukkit.Location location, org.bukkit.TreeType type) { return generateTree(location, RAND, type); }

    @Override
    public boolean generateTree(org.bukkit.Location location, java.util.Random random, org.bukkit.TreeType type) {
        return placeTree(location, random, type, true).placed();
    }

    @Override
    public boolean generateTree(org.bukkit.Location location, org.bukkit.TreeType type, org.bukkit.BlockChangeDelegate delegate) {
        var r = placeTree(location, RAND, type, false);
        if (!r.placed()) return false;
        // Paper: 기록한 칸마다 delegate.setBlockData 뒤 실제 월드 칸에 notifyAndUpdatePhysics — 그 칸이 안 바뀌었으면 Furnace 는 이웃 갱신을 하지 않는다(맥락 노트)
        for (var w : r.writes()) delegate.setBlockData(w.pos().x(), w.pos().y(), w.pos().z(), org.domi.furnace.compat.paper.CompatBlockData.of(w.data()));
        return true;
    }

    private org.domi.furnace.api.world.FeaturePlacement placeTree(org.bukkit.Location location, java.util.Random random, org.bukkit.TreeType type, boolean apply) {
        BlockPos pos = new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        String id = treeFeature(type);
        if (!handle.isChunkLoaded(pos.chunkX(), pos.chunkZ())) {
            // Paper 는 동기로 불러와 키운다. Furnace 는 나무가 닿지 못한 청크와 같이 실패로 둔다(맥락 노트)
            if (WARNED.add("unloaded")) LOG.warn(CompatLang.t("compat.generate-tree-unloaded", pos.chunkX(), pos.chunkZ()));
            return new org.domi.furnace.api.world.FeaturePlacement(false, List.of());
        }
        try {
            return id == null ? handle.growChorusPlantNow(pos, random, apply) : handle.placeFeatureNow(id, pos, random, apply);
        } catch (UnsupportedOperationException e) {
            // Furnace 가 아직 구현하지 않은 feature(Noop): 다른 나무로 바꿔 키우지 않고 실패로 둔다(AGENTS.md 규칙 3)
            if (WARNED.add(type.name())) LOG.warn(CompatLang.t("compat.generate-tree-failed", type, e.getMessage()));
            return new org.domi.furnace.api.world.FeaturePlacement(false, List.of());
        }
    }

    // ---- 소리·입자(H4 묶음 C) — Paper CraftWorld 와 같이 null 인자는 조용히 넘어간다

    @Override
    public void playSound(org.bukkit.Location location, org.bukkit.Sound sound, org.bukkit.SoundCategory category, float volume, float pitch, long seed) {
        if (sound == null) return;
        playSound(location, CompatEffects.key(sound), category, volume, pitch, seed);
    }

    @Override
    public void playSound(org.bukkit.Location location, String sound, org.bukkit.SoundCategory category, float volume, float pitch, long seed) {
        if (location == null || sound == null || category == null) return;
        handle.playSound(CompatEffects.sound(sound, category, volume, pitch), location.getX(), location.getY(), location.getZ(), seed);
    }

    @Override
    public void playSound(org.bukkit.entity.Entity entity, org.bukkit.Sound sound, org.bukkit.SoundCategory category, float volume, float pitch, long seed) {
        if (sound == null) return;
        playSound(entity, CompatEffects.key(sound), category, volume, pitch, seed);
    }

    @Override
    public void playSound(org.bukkit.entity.Entity entity, String sound, org.bukkit.SoundCategory category, float volume, float pitch, long seed) {
        if (!(entity instanceof org.bukkit.craftbukkit.entity.CraftEntity ce) || sound == null || category == null) return;
        org.domi.furnace.api.entity.Entity fe = ce.furnaceHandle();
        if (!fe.location().world().equals(handle.dimension())) return;   // Paper: 다른 월드의 엔티티면 아무 일도 없다
        handle.playSound(CompatEffects.sound(sound, category, volume, pitch), fe, seed);
    }

    // ---- 레벨 이벤트(Paper CraftWorld#playEffect) — 같은 월드에서 (int) 거리² ≤ radius² 인 플레이어에게 level_event

    @Override
    public <T> void playEffect(org.bukkit.Location loc, org.bukkit.Effect effect, T data, int radius) {
        CraftEffect.checkData(effect, data);
        playEffect(loc, effect, CraftEffect.getDataValue(effect, data), radius);
    }

    @Override
    public void playEffect(org.bukkit.Location location, org.bukkit.Effect effect, int data, int radius) {
        if (effect == null) throw new IllegalArgumentException("Effect cannot be null");
        if (location == null) throw new IllegalArgumentException("Location cannot be null");
        if (location.getWorld() == null) throw new IllegalArgumentException("World of Location cannot be null");
        var packet = CraftEffect.packet(location, effect, data);
        int r2 = radius * radius;
        for (Player player : getPlayers()) {
            if (!(player instanceof org.bukkit.craftbukkit.entity.CraftPlayer cp) || !cp.furnaceHandle().isOnline()) continue;
            if (!location.getWorld().equals(player.getWorld())) continue;
            if ((int) player.getLocation().distanceSquared(location) <= r2) cp.furnaceHandle().sendPacket(packet);
        }
    }

    @Override
    public <T> void spawnParticle(org.bukkit.Particle particle, List<Player> receivers, Player source, double x, double y, double z, int count,
                                  double offsetX, double offsetY, double offsetZ, double extra, T data, boolean force) {
        org.domi.furnace.api.world.ParticleEffect effect = CompatEffects.particle(particle, data);
        List<org.domi.furnace.api.entity.Player> to = null;
        if (receivers != null) {
            to = new ArrayList<>(receivers.size());
            for (Player p : receivers) {
                if (p instanceof org.bukkit.craftbukkit.entity.CraftPlayer cp) to.add(cp.furnaceHandle());
            }
        }
        handle.spawnParticle(effect, to, x, y, z, count, offsetX, offsetY, offsetZ, extra, force);
    }

    // ---- 바이옴(H4 묶음 C)

    @Override
    public org.bukkit.block.Biome getBiome(int x, int y, int z) {
        String key = handle.biomeNow(new BlockPos(x, y, z))
                .orElseThrow(() -> new UnsupportedFeature("World#getBiome(unloaded chunk)", "Furnace does not load a chunk synchronously to read a biome - call it on a loaded chunk"));
        NamespacedKey k = NamespacedKey.fromString(key);
        org.bukkit.block.Biome b = k == null ? null : org.bukkit.block.Biome.byKey(k);
        return b != null ? b : org.bukkit.block.Biome.CUSTOM;   // CraftBlock.biomeBaseToBiome: 레지스트리에 없는 바이옴은 CUSTOM
    }

    @Override
    public void setBiome(int x, int y, int z, org.bukkit.block.Biome biome) {
        if (biome == null) throw new IllegalArgumentException("Biome cannot be null");
        if (biome == org.bukkit.block.Biome.CUSTOM) throw new IllegalArgumentException("Cannot set the biome to CUSTOM");
        handle.setBiomeNow(new BlockPos(x, y, z), biome.getKey().toString());   // 로드되지 않은 청크면 Paper 처럼 아무 일도 없다
    }

    // ---- World 와 RegionAccessor 가 함께 기본 몸을 주는 멤버 — 월드는 World 쪽 몸을 쓴다(P2c-2)

    @Override
    public <T extends org.bukkit.entity.Entity> java.util.Collection<T> getEntitiesByClass(Class<T> cls) { return World.super.getEntitiesByClass(cls); }

    @Override
    public java.util.Collection<org.bukkit.entity.Entity> getEntitiesByClasses(Class<?>... classes) { return World.super.getEntitiesByClasses(classes); }

    @Override
    public org.bukkit.block.Biome getBiome(org.bukkit.Location location) { return World.super.getBiome(location); }

    @Override
    public void setBiome(org.bukkit.Location location, org.bukkit.block.Biome biome) { World.super.setBiome(location, biome); }

    // ---- 높이 지도(RegionAccessor, P2c-2)

    /**
     * Paper CraftWorld#getHighestBlockYAt: 그 청크 높이 지도의 첫 빈 칸 - 1(바닐라 ChunkAccess#getHeight). 다 만든 청크에 없는 생성 단계
     * 지도(*_WG)는 바닐라가 그 자리에서 지금 블록으로 만들어 답한다 — 술어가 같은 살아 있는 지도(WORLD_SURFACE_WG↔WORLD_SURFACE 는 NOT_AIR,
     * OCEAN_FLOOR_WG↔OCEAN_FLOOR 는 MATERIAL_MOTION_BLOCKING, 바닐라 1.21.1 Heightmap$Types 로 확인)와 그 순간 값이 같다. 남는 차이: 바닐라는 한 번
     * 만든 *_WG 지도를 청크가 내려갈 때까지 갱신하지 않아 그 뒤 블록 변화에 낡은 값을 주고, Furnace 는 늘 지금 값을 준다. Paper 는 로드 안 된
     * 청크를 그 자리에서 올리지만 Furnace 는 동기 로드를 하지 않는다(getBiome 과 같은 원칙, user-todo 2026-11-23-t264-unloaded-chunk-reads).
     */
    @Override
    public int getHighestBlockYAt(int x, int z, org.bukkit.HeightMap heightMap) {
        org.bukkit.HeightMap live = switchWorldgen(heightMap);
        int first = org.domi.furnace.compat.nms.NmsChunks.bridge().firstAvailableNow(handle, live.name(), x, z);
        if (first == Integer.MIN_VALUE) {
            throw new UnsupportedFeature("World#getHighestBlockYAt(unloaded chunk)", "Furnace does not load a chunk synchronously to read a heightmap - call it on a loaded chunk");
        }
        return first - 1;
    }

    /** 생성 단계 지도 → 술어가 같은 살아 있는 지도(if 사슬: enum switch 의 합성 클래스가 Paper 의 CraftWorld$N 이름과 부딪힌다). */
    private static org.bukkit.HeightMap switchWorldgen(org.bukkit.HeightMap heightMap) {
        if (heightMap == org.bukkit.HeightMap.WORLD_SURFACE_WG) return org.bukkit.HeightMap.WORLD_SURFACE;
        if (heightMap == org.bukkit.HeightMap.OCEAN_FLOOR_WG) return org.bukkit.HeightMap.OCEAN_FLOOR;
        return heightMap;
    }

    @Override
    public boolean equals(Object o) { return o instanceof CraftWorld w && w.handle.dimension().equals(handle.dimension()); }

    @Override
    public int hashCode() { return handle.dimension().hashCode(); }

    @Override
    public String toString() { return "CraftWorld{name=" + getName() + "}"; }
}
