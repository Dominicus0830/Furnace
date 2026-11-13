package org.bukkit.projectiles;

import org.bukkit.entity.Projectile;
import org.bukkit.util.Vector;

/** 투사체를 쏠 수 있는 것(살아 있는 엔티티). 쏘기는 Furnace 에 아직 이을 곳이 없어 UnsupportedFeature 로 큰 소리로 실패한다. */
public interface ProjectileSource {

    <T extends Projectile> T launchProjectile(Class<? extends T> projectile);

    <T extends Projectile> T launchProjectile(Class<? extends T> projectile, Vector velocity);
}
