package org.bukkit.craftbukkit;

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
import org.domi.furnace.compat.paper.CompatBlock;
import org.domi.furnace.compat.paper.CompatChunk;
import org.domi.furnace.compat.paper.CompatEffects;
import org.domi.furnace.compat.paper.UnsupportedFeature;

/**
 * Furnace 월드 위의 Bukkit World. 같은 차원이면 같은 객체(Compat 캐시) — equals 도 차원 키로 본다.
 * CraftBukkit 과 같은 이름이라 {@code (CraftWorld) world} 캐스트와 {@link #getHandle()} 이 된다(H6).
 */
public final class CraftWorld implements World {

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

    @Override
    public String getName() { return handle.name(); }

    @Override
    public UUID getUID() { return UUID.nameUUIDFromBytes(("furnace-world:" + handle.dimension()).getBytes(StandardCharsets.UTF_8)); }

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
    public Block getBlockAt(int x, int y, int z) { return new CompatBlock(handle.dimension(), new BlockPos(x, y, z)); }

    @Override
    public Chunk getChunkAt(int x, int z) { return new CompatChunk(handle.dimension(), x, z); }

    @Override
    public org.bukkit.entity.Entity spawnEntity(org.bukkit.Location loc, org.bukkit.entity.EntityType type) {
        if (type == null || type == org.bukkit.entity.EntityType.UNKNOWN) throw new IllegalArgumentException("Cannot spawn an entity of type " + type);
        org.domi.furnace.api.entity.Entity e = handle.spawnEntityNow(type.getKey().toString(), Compat.location(loc));
        if (e == null) throw new IllegalStateException("엔티티 스폰이 취소됐다(스폰 이벤트를 취소한 플러그인이 있다): " + type);
        return Compat.entity(e);
    }

    /** Bukkit 형 → 종류 키. Compat 이 그 형으로 감싸는 종류만. */
    private static String spawnKey(Class<?> clazz) {
        if (clazz == org.bukkit.entity.ArmorStand.class) return "minecraft:armor_stand";
        if (clazz == org.bukkit.entity.Pig.class) return "minecraft:pig";
        return null;
    }

    @Override
    public <T extends org.bukkit.entity.Entity> T spawn(org.bukkit.Location location, Class<T> clazz) { return spawn(location, clazz, null); }

    @Override
    public <T extends org.bukkit.entity.Entity> T spawn(org.bukkit.Location location, Class<T> clazz, java.util.function.Consumer<? super T> function) {
        if (location == null) throw new IllegalArgumentException("Location cannot be null");
        if (clazz == null) throw new IllegalArgumentException("Entity class cannot be null");
        String key = spawnKey(clazz);
        if (key == null) {
            if (!clazz.isInterface() || clazz.getPackageName().equals("org.bukkit.entity") && isAbstractKind(clazz)) {
                throw new IllegalArgumentException("Cannot spawn an entity for " + clazz.getName());
            }
            throw new UnsupportedFeature("World#spawn(" + clazz.getSimpleName() + ")", "이 Bukkit 엔티티 형으로 감싸는 어댑터가 아직 없다(ArmorStand·Pig 만)");
        }
        org.domi.furnace.api.entity.Entity e = handle.spawnEntityNow(key, Compat.location(location));
        if (e == null) throw new IllegalStateException("엔티티 스폰이 취소됐다(스폰 이벤트를 취소한 플러그인이 있다): " + clazz.getSimpleName());
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
    public List<Player> getPlayers() {
        List<Player> out = new ArrayList<>();
        for (var p : Furnace.server().onlinePlayers()) {
            if (p.location().world().equals(handle.dimension())) out.add(Compat.player(p));
        }
        return out;
    }

    @Override
    public int getMinHeight() {
        return switch (handle.dimensionType()) {
            case "minecraft:overworld" -> -64;
            case "minecraft:the_nether", "minecraft:the_end" -> 0;
            default -> throw new UnsupportedFeature("World#getMinHeight(" + handle.dimensionType() + ")", "플러그인이 만든 차원 타입의 높이는 아직 노출하지 않는다");
        };
    }

    @Override
    public int getMaxHeight() {
        return switch (handle.dimensionType()) {
            case "minecraft:overworld" -> 320;
            case "minecraft:the_nether" -> 256;
            case "minecraft:the_end" -> 256;
            default -> throw new UnsupportedFeature("World#getMaxHeight(" + handle.dimensionType() + ")", "플러그인이 만든 차원 타입의 높이는 아직 노출하지 않는다");
        };
    }

    @Override
    public String getGameRuleValue(String rule) { return handle.gameRule(rule); }

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
                .orElseThrow(() -> new UnsupportedFeature("World#getBiome(로드되지 않은 청크)", "Furnace 는 바이옴을 읽으려고 청크를 동기로 불러오지 않는다 — 로드된 청크에서 부른다"));
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

    @Override
    public boolean equals(Object o) { return o instanceof CraftWorld w && w.handle.dimension().equals(handle.dimension()); }

    @Override
    public int hashCode() { return handle.dimension().hashCode(); }

    @Override
    public String toString() { return "CraftWorld{name=" + getName() + "}"; }
}
