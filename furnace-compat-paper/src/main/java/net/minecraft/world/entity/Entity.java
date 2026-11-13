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
public class Entity implements net.minecraft.network.syncher.SyncedDataHolder {

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

    /** 이 엔티티가 있는 월드의 shim. */
    public Level level() { return ((CraftWorld) bukkit.getWorld()).getHandle(); }

    /** 바닐라: 지워졌는가(스냅샷 시점). */
    public boolean isRemoved() { return !f().isValid(); }

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
