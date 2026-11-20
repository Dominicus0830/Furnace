package net.minecraft.world.entity;

import java.util.UUID;
import net.minecraft.world.level.Level;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.domi.furnace.compat.nms.UnsupportedNmsCall;

/**
 * 엔티티 shim(Mojang 이름, H6 최소 표면): {@code CraftEntity#getHandle()} 이 돌려준다. 서버 엔티티 객체가 아니라 그 Bukkit 객체를 거쳐
 * Furnace 엔티티 핸들(만든 시점의 스냅샷)을 읽는다 — 위치는 그 스냅샷 값이다. 같은 엔티티는 같은 shim 이다(CraftEntity 마다 하나).
 * 바닐라처럼 equals 는 같은 엔티티인가(바닐라는 네트워크 id, 여기는 UUID)다.
 */
public class Entity implements net.minecraft.network.syncher.SyncedDataHolder, net.minecraft.world.level.entity.EntityAccess {

    private final CraftEntity bukkit;

    /** 호환 모듈 전용(CraftEntity 가 만든다). 플러그인이 상속해 새 엔티티를 만드는 것은 지원하지 않는다(설계 등급 C). */
    public Entity(CraftEntity bukkit) { this.bukkit = java.util.Objects.requireNonNull(bukkit, "bukkit"); }

    /** CraftBukkit 이 더한 메서드: 이 엔티티의 Bukkit 객체. */
    public CraftEntity getBukkitEntity() { return bukkit; }

    private org.domi.furnace.api.entity.Entity f() { return bukkit.furnaceHandle(); }

    public UUID getUUID() { return f().uniqueId(); }

    public String getStringUUID() { return f().uniqueId().toString(); }

    public double getX() { return f().location().x(); }

    public double getY() { return f().location().y(); }

    public double getZ() { return f().location().z(); }

    public float getYRot() { return f().location().yaw(); }

    public float getXRot() { return f().location().pitch(); }

    /** 바닐라 Entity#getYHeadRot: 살아 있는 엔티티의 머리 방향(그 밖은 0). */
    public float getYHeadRot() { return f() instanceof org.domi.furnace.api.entity.LivingEntity ? f().headYaw() : 0.0F; }

    /** CraftBukkit 이 더한 메서드: Bukkit Location 의 yaw 가 되는 값(살아 있는 엔티티는 머리 방향, 그 밖은 몸 방향). */
    public float getBukkitYaw() { return f().headYaw(); }

    /** 바닐라: 위치(스냅샷 값). */
    public net.minecraft.world.phys.Vec3 position() {
        var l = f().location();
        return new net.minecraft.world.phys.Vec3(l.x(), l.y(), l.z());
    }

    /** 바닐라: 발 밑 칸 좌표(각 축 내림). */
    public net.minecraft.core.BlockPos blockPosition() { return net.minecraft.core.BlockPos.containing(getX(), getY(), getZ()); }

    /** 바닐라: 있는 청크 좌표. */
    public net.minecraft.world.level.ChunkPos chunkPosition() { return new net.minecraft.world.level.ChunkPos(blockPosition()); }

    /** 바닐라: 엔티티 종류(키로 같은 객체). */
    public EntityType<?> getType() { return EntityType.of(net.minecraft.resources.ResourceLocation.parse(f().type())); }

    /** 이 엔티티가 있는 월드의 shim. */
    public Level level() { return ((CraftWorld) bukkit.getWorld()).getHandle(); }

    /** 바닐라: 지워졌는가(스냅샷 시점, 이 shim 으로 {@link #discard} 했으면 참). */
    public boolean isRemoved() { return discarded || !f().isValid(); }

    /** 이 shim 의 {@link #discard} 가 엔티티를 지웠다(스냅샷은 그 뒤에도 살아 있는 값이다). */
    private volatile boolean discarded;

    private org.domi.furnace.api.world.World furnaceWorld() { return ((CraftWorld) bukkit.getWorld()).furnaceHandle(); }

    /**
     * 바닐라 {@code Entity#save}(T-212): 그 엔티티를 가진 스레드에서 서버가 저장한 NBT("id" 포함, 승객까지)를 tag 에 넣고 참. 승객·플레이어·저장하지 않는
     * 상태·이미 사라진 엔티티는 거짓(tag 는 그대로). 스레드 규칙은 {@link org.domi.furnace.compat.nms.NmsEntities}(FAWE 작업 스레드는 주인을 기다린다).
     * 내용은 서버가 월드에 저장하는 꼴과 같다 — Paper 가 더하는 Bukkit·Spigot·Paper 키(WorldUUID·Spigot.ticksLived·Paper.Origin 등)는 없다.
     */
    public boolean save(net.minecraft.nbt.CompoundTag tag) {
        var h = f();
        if (h instanceof org.domi.furnace.api.entity.Player) return false;   // 플레이어는 저장하지 않는 종류(바닐라 getEncodeId null)
        var b = org.domi.furnace.compat.nms.NmsChunks.bridge();
        org.domi.furnace.api.nbt.NbtCompound saved = org.domi.furnace.compat.nms.NmsEntities.call(furnaceWorld(), h, "Entity#save", () -> b.entitySaveNow(h));
        if (saved == null) return false;
        net.minecraft.nbt.CompoundTag t = net.minecraft.nbt.CompoundTag.fromFurnace(saved);
        for (String k : t.getAllKeys()) tag.put(k, t.get(k));
        return true;
    }

    /**
     * 바닐라 {@code Entity#discard}(T-212): 지움 이유 DISCARDED 로 지운다 — Paper 도 원인 없는 discard 에는 EntityRemoveEvent 를 내지 않는다. 이미 사라졌으면
     * 아무 일 없다. 스레드 규칙은 {@link #save} 와 같다(FAWE 는 동기 작업 = 전역 스레드의 빌림 구간에서 부른다).
     */
    public void discard() {
        var h = f();
        if (h instanceof org.domi.furnace.api.entity.Player) {
            throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("Entity#discard", "discarding a player entity is not supported - kick the player instead");
        }
        var b = org.domi.furnace.compat.nms.NmsChunks.bridge();
        org.domi.furnace.compat.nms.NmsEntities.call(furnaceWorld(), h, "Entity#discard", () -> {
            b.entityDiscardNow(h);
            return null;
        });
        discarded = true;
    }

    /** 바닐라 Entity: 지워지지 않았는가. LivingEntity 는 체력도 본다. */
    public boolean isAlive() { return !isRemoved(); }

    /** 네트워크 엔티티 id(바닐라 getId — Bukkit getEntityId 와 같은 번호). */
    public int getId() { return f().entityId(); }

    @Override
    public boolean equals(Object o) { return o instanceof Entity e && e.getUUID().equals(getUUID()); }

    @Override
    public int hashCode() { return getUUID().hashCode(); }

    @Override
    public String toString() {
        var l = f().location();
        return String.format(java.util.Locale.ROOT, "%s['%s'/%s, l='%s', x=%.2f, y=%.2f, z=%.2f]", getClass().getSimpleName(), bukkit.getName(), getUUID(), l.world(), l.x(), l.y(), l.z());
    }
}
