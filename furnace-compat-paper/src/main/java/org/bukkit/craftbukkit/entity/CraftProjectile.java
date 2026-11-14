package org.bukkit.craftbukkit.entity;

import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Projectile;
import org.bukkit.projectiles.ProjectileSource;
import org.domi.furnace.api.event.cause.CauseEntry;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.compat.paper.UnsupportedFeature;

/** 투사체 엔티티 위의 Bukkit {@link Projectile}. 쏜 쪽은 핸들의 원인 사슬에서 SHOOT 항목을 찾아 읽는다(핸들을 만든 순간 값). */
public class CraftProjectile extends CraftEntity implements Projectile {

    /** Bukkit 에서 Projectile 인 1.21.1 종류(낚시찌·불꽃놀이 로켓 포함). */
    public static final java.util.Set<String> TYPES = java.util.Set.of(
            "minecraft:arrow", "minecraft:spectral_arrow", "minecraft:trident", "minecraft:snowball", "minecraft:egg", "minecraft:ender_pearl",
            "minecraft:experience_bottle", "minecraft:potion", "minecraft:fireball", "minecraft:small_fireball", "minecraft:dragon_fireball",
            "minecraft:wither_skull", "minecraft:wind_charge", "minecraft:breeze_wind_charge", "minecraft:shulker_bullet", "minecraft:llama_spit",
            "minecraft:fishing_bobber", "minecraft:firework_rocket");

    public CraftProjectile(org.domi.furnace.api.entity.Entity handle) { super(handle); }

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
        return Compat.entityByUuid(world, id) instanceof ProjectileSource s ? s : null;
    }

    @Override
    public void setShooter(ProjectileSource source) { throw new UnsupportedFeature("Projectile#setShooter", "Furnace 핸들은 투사체의 주인을 바꾸는 길이 아직 없다"); }

    @Override
    public boolean hasLeftShooter() { throw new UnsupportedFeature("Projectile#hasLeftShooter", "Furnace 핸들은 투사체가 쏜 쪽을 벗어났는지 알려 주지 않는다"); }

    @Override
    public void setHasLeftShooter(boolean leftShooter) { throw new UnsupportedFeature("Projectile#setHasLeftShooter", "Furnace 핸들에는 쓰는 길이 아직 없다"); }

    @Override
    public boolean hasBeenShot() { throw new UnsupportedFeature("Projectile#hasBeenShot", "Furnace 핸들은 쏘아졌는지 알려 주지 않는다"); }

    @Override
    public void setHasBeenShot(boolean beenShot) { throw new UnsupportedFeature("Projectile#setHasBeenShot", "Furnace 핸들에는 쓰는 길이 아직 없다"); }

    @Override
    public boolean canHitEntity(org.bukkit.entity.Entity entity) { throw new UnsupportedFeature("Projectile#canHitEntity", "Furnace 핸들에는 맞춤 판정을 묻는 길이 아직 없다"); }

    @Override
    public void hitEntity(org.bukkit.entity.Entity entity) { throw new UnsupportedFeature("Projectile#hitEntity", "Furnace 핸들에는 투사체를 맞추는 길이 아직 없다"); }

    @Override
    public void hitEntity(org.bukkit.entity.Entity entity, org.bukkit.util.Vector vector) { throw new UnsupportedFeature("Projectile#hitEntity", "Furnace 핸들에는 투사체를 맞추는 길이 아직 없다"); }

    @Override
    @Deprecated
    public boolean doesBounce() { return false; }

    @Override
    @Deprecated
    public void setBounce(boolean doesBounce) {}
}
