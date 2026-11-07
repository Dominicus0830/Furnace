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
public class Entity {

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

    /** 이 엔티티가 있는 월드의 shim. */
    public Level level() { return ((CraftWorld) bukkit.getWorld()).getHandle(); }

    /** 바닐라: 지워졌는가(스냅샷 시점). */
    public boolean isRemoved() { return !f().isValid(); }

    /** 바닐라 Entity: 지워지지 않았는가. LivingEntity 는 체력도 본다. */
    public boolean isAlive() { return !isRemoved(); }

    /**
     * 네트워크 엔티티 id. Furnace 공개 API 는 실제 엔티티의 이 번호를 내보내지 않는다 — 패킷을 직접 만드는 코드가 쓰므로 지어내지 않고 던진다.
     * 패킷으로만 보이는 가짜 엔티티라면 Furnace 패킷 API 의 {@code nextEntityId()} 로 번호를 받는다(docs/api/packets.md).
     */
    public int getId() {
        throw new UnsupportedNmsCall("Entity#getId", "네트워크 엔티티 id 는 아직 플러그인에 내보내지 않는다(H7 패킷 묶음에서 다룬다)");
    }

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
