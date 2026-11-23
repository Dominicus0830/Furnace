package org.bukkit.craftbukkit.entity;

import org.bukkit.entity.Projectile;

/** Paper CraftProjectile 자리(상위 AbstractProjectile). 몸통은 {@link AbstractProjectile} 에 있다. */
public class CraftProjectile extends AbstractProjectile implements Projectile {

    /** Bukkit 에서 Projectile 인 1.21.1 종류(낚시찌·불꽃놀이 로켓 포함). */
    public static final java.util.Set<String> TYPES = java.util.Set.of(
            "minecraft:arrow", "minecraft:spectral_arrow", "minecraft:trident", "minecraft:snowball", "minecraft:egg", "minecraft:ender_pearl",
            "minecraft:experience_bottle", "minecraft:potion", "minecraft:fireball", "minecraft:small_fireball", "minecraft:dragon_fireball",
            "minecraft:wither_skull", "minecraft:wind_charge", "minecraft:breeze_wind_charge", "minecraft:shulker_bullet", "minecraft:llama_spit",
            "minecraft:fishing_bobber", "minecraft:firework_rocket");

    public CraftProjectile(org.domi.furnace.api.entity.Entity handle) { super(handle); }
}
