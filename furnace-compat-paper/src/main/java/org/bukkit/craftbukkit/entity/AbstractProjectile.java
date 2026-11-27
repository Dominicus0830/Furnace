package org.bukkit.craftbukkit.entity;

import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Projectile;
import org.bukkit.projectiles.ProjectileSource;
import org.domi.furnace.api.event.cause.CauseEntry;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.compat.paper.UnsupportedFeature;

/** 투사체 엔티티 위의 Bukkit {@link Projectile} 공통 몸통(Paper AbstractProjectile 자리 — 화살·화염구·라마 침·셜커 탄환도 이 사슬). 쏜 쪽은 핸들의 원인 사슬에서 SHOOT 항목을 찾아 읽는다(핸들을 만든 순간 값). */
public abstract class AbstractProjectile extends CraftEntity implements Projectile {

    protected AbstractProjectile(org.domi.furnace.api.entity.Entity handle) { super(handle); }

    @Override
    public UUID getOwnerUniqueId() {
        Optional<CauseEntry> shoot = furnaceHandle().origin().first(e -> CauseEntry.SHOOT.equals(e.action()) && e.uuid() != null);
        return shoot.map(CauseEntry::uuid).orElse(null);
    }

    /**
     * 쏜 쪽: 플레이어는 어디서나, 플레이어가 아닌 쪽은 투사체의 월드에서 UUID 로 찾는다({@link Compat#entityByUuid} — 틱 스레드에서는 그 엔티티를 가진
     * region 에서만 보이고, 실행 이력과 상관없이 같은 답이다). 쏜 쪽이 사라졌거나 다른 region 에 있으면 null.
     */
    @Override
    public ProjectileSource getShooter() {
        UUID id = getOwnerUniqueId();
        if (id == null) return null;
        var player = org.bukkit.Bukkit.getPlayer(id);
        if (player != null) return player;
        // 투사체의 지금 월드(getWorld 와 같은 길 — 오래된 감싸개도 차원을 옮긴 뒤의 월드에서 찾는다, 바닐라 Projectile.getOwner 와 같다)
        var world = getWorld() instanceof org.bukkit.craftbukkit.CraftWorld cw ? cw.furnaceHandle() : Compat.furnace().world(furnaceHandle().location().world());
        var fake = Compat.detachedPlayer(id, world.dimension());   // NMS 로 만든 월드에 없는 플레이어(FAWE 가짜)가 쏜 것
        if (fake != null) return fake;
        return Compat.entityByUuid(world, id) instanceof ProjectileSource s ? s : null;
    }

    @Override
    public void setShooter(ProjectileSource source) { throw new UnsupportedFeature("Projectile#setShooter", "the Furnace handle has no way yet to change the owner of a projectile"); }

    @Override
    public boolean hasLeftShooter() { throw new UnsupportedFeature("Projectile#hasLeftShooter", "the Furnace handle does not report whether the projectile has left its shooter"); }

    @Override
    public void setHasLeftShooter(boolean leftShooter) { throw new UnsupportedFeature("Projectile#setHasLeftShooter", "the Furnace handle has no way to write this yet"); }

    @Override
    public boolean hasBeenShot() { throw new UnsupportedFeature("Projectile#hasBeenShot", "the Furnace handle does not report whether it has been shot"); }

    @Override
    public void setHasBeenShot(boolean beenShot) { throw new UnsupportedFeature("Projectile#setHasBeenShot", "the Furnace handle has no way to write this yet"); }

    @Override
    public boolean canHitEntity(org.bukkit.entity.Entity entity) {
        // Paper: getHandle().canHitEntity(((CraftEntity) entity).getHandle()) — 투사체 종류마다의 바닐라 재정의를 서버 투사체가 따른다. 사라진 투사체는 거짓
        org.domi.furnace.api.entity.Entity target = ((CraftEntity) entity).furnaceHandle();
        return org.domi.furnace.compat.paper.EntityStates.read(furnaceHandle(), org.domi.furnace.api.entity.ProjectileState.class,
                s -> s.canHitEntity(target), false, "Projectile#canHitEntity");
    }

    @Override
    public void hitEntity(org.bukkit.entity.Entity entity) { throw new UnsupportedFeature("Projectile#hitEntity", "the Furnace handle has no way yet to make a projectile hit"); }

    @Override
    public void hitEntity(org.bukkit.entity.Entity entity, org.bukkit.util.Vector vector) { throw new UnsupportedFeature("Projectile#hitEntity", "the Furnace handle has no way yet to make a projectile hit"); }

    @Override
    @Deprecated
    public boolean doesBounce() { return false; }

    @Override
    @Deprecated
    public void setBounce(boolean doesBounce) {}
}
