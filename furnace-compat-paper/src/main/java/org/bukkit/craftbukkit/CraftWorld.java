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

    @Override
    public java.util.Collection<org.bukkit.entity.Entity> getNearbyEntities(org.bukkit.Location loc, double x, double y, double z) {
        org.domi.furnace.api.world.Location c = Compat.location(loc);
        double r = Math.sqrt(x * x + y * y + z * z);
        java.util.List<org.bukkit.entity.Entity> out = new ArrayList<>();
        for (var e : handle.nearbyEntitiesNow(c, r)) {
            var l = e.location();
            if (Math.abs(l.x() - c.x()) <= x && Math.abs(l.y() - c.y()) <= y && Math.abs(l.z() - c.z()) <= z) out.add(Compat.entity(e));
        }
        return out;
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

    @Override
    public boolean equals(Object o) { return o instanceof CraftWorld w && w.handle.dimension().equals(handle.dimension()); }

    @Override
    public int hashCode() { return handle.dimension().hashCode(); }

    @Override
    public String toString() { return "CraftWorld{name=" + getName() + "}"; }
}
