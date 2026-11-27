package net.minecraft.world.damagesource;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Fireball;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.phys.Vec3;

/**
 * 피해 출처 만들기(Paper 1.21.1 DamageSources 와 같은 메서드·형식). 엔티티가 없는 출처는 만들 때 한 번 만들어 두고 같은 객체를 돌려준다(바닐라와 같다 —
 * 그래서 {@code sweep()} 처럼 자기를 고치는 Paper 메서드를 그 객체에 부르면 공유 객체가 바뀐다. Paper 도 그렇다).
 * {@code explosion(Explosion)} 은 호환 계층 Explosion 에 폭발 원인 읽기가 아직 없어 손 구현하지 않았다(껍데기가 던진다).
 */
public class DamageSources {

    private final Registry<DamageType> damageTypes;
    private final DamageSource inFire;
    private final DamageSource campfire;
    private final DamageSource lightningBolt;
    private final DamageSource onFire;
    private final DamageSource lava;
    private final DamageSource hotFloor;
    private final DamageSource inWall;
    private final DamageSource cramming;
    private final DamageSource drown;
    private final DamageSource starve;
    private final DamageSource cactus;
    private final DamageSource fall;
    private final DamageSource flyIntoWall;
    private final DamageSource fellOutOfWorld;
    private final DamageSource generic;
    private final DamageSource magic;
    private final DamageSource wither;
    private final DamageSource dragonBreath;
    private final DamageSource dryOut;
    private final DamageSource sweetBerryBush;
    private final DamageSource freeze;
    private final DamageSource stalagmite;
    private final DamageSource outsideBorder;
    private final DamageSource genericKill;
    private final DamageSource melting;
    private final DamageSource poison;

    public DamageSources(RegistryAccess registries) {
        this.damageTypes = registries.registryOrThrow(Registries.DAMAGE_TYPE);
        this.melting = source(DamageTypes.ON_FIRE).melting();
        this.poison = source(DamageTypes.MAGIC).poison();
        this.inFire = source(DamageTypes.IN_FIRE);
        this.campfire = source(DamageTypes.CAMPFIRE);
        this.lightningBolt = source(DamageTypes.LIGHTNING_BOLT);
        this.onFire = source(DamageTypes.ON_FIRE);
        this.lava = source(DamageTypes.LAVA);
        this.hotFloor = source(DamageTypes.HOT_FLOOR);
        this.inWall = source(DamageTypes.IN_WALL);
        this.cramming = source(DamageTypes.CRAMMING);
        this.drown = source(DamageTypes.DROWN);
        this.starve = source(DamageTypes.STARVE);
        this.cactus = source(DamageTypes.CACTUS);
        this.fall = source(DamageTypes.FALL);
        this.flyIntoWall = source(DamageTypes.FLY_INTO_WALL);
        this.fellOutOfWorld = source(DamageTypes.FELL_OUT_OF_WORLD);
        this.generic = source(DamageTypes.GENERIC);
        this.magic = source(DamageTypes.MAGIC);
        this.wither = source(DamageTypes.WITHER);
        this.dragonBreath = source(DamageTypes.DRAGON_BREATH);
        this.dryOut = source(DamageTypes.DRY_OUT);
        this.sweetBerryBush = source(DamageTypes.SWEET_BERRY_BUSH);
        this.freeze = source(DamageTypes.FREEZE);
        this.stalagmite = source(DamageTypes.STALAGMITE);
        this.outsideBorder = source(DamageTypes.OUTSIDE_BORDER);
        this.genericKill = source(DamageTypes.GENERIC_KILL);
    }

    private DamageSource source(ResourceKey<DamageType> key) { return new DamageSource(damageTypes.getHolderOrThrow(key)); }

    private DamageSource source(ResourceKey<DamageType> key, Entity attacker) { return new DamageSource(damageTypes.getHolderOrThrow(key), attacker); }

    private DamageSource source(ResourceKey<DamageType> key, Entity source, Entity attacker) {
        return new DamageSource(damageTypes.getHolderOrThrow(key), source, attacker);
    }

    public DamageSource melting() { return melting; }

    public DamageSource poison() { return poison; }

    public DamageSource inFire() { return inFire; }

    public DamageSource campfire() { return campfire; }

    public DamageSource lightningBolt() { return lightningBolt; }

    public DamageSource onFire() { return onFire; }

    public DamageSource lava() { return lava; }

    public DamageSource hotFloor() { return hotFloor; }

    public DamageSource inWall() { return inWall; }

    public DamageSource cramming() { return cramming; }

    public DamageSource drown() { return drown; }

    public DamageSource starve() { return starve; }

    public DamageSource cactus() { return cactus; }

    public DamageSource fall() { return fall; }

    public DamageSource flyIntoWall() { return flyIntoWall; }

    public DamageSource fellOutOfWorld() { return fellOutOfWorld; }

    public DamageSource generic() { return generic; }

    public DamageSource magic() { return magic; }

    public DamageSource wither() { return wither; }

    public DamageSource dragonBreath() { return dragonBreath; }

    public DamageSource dryOut() { return dryOut; }

    public DamageSource sweetBerryBush() { return sweetBerryBush; }

    public DamageSource freeze() { return freeze; }

    public DamageSource stalagmite() { return stalagmite; }

    public DamageSource fallingBlock(Entity attacker) { return source(DamageTypes.FALLING_BLOCK, attacker); }

    public DamageSource anvil(Entity attacker) { return source(DamageTypes.FALLING_ANVIL, attacker); }

    public DamageSource fallingStalactite(Entity attacker) { return source(DamageTypes.FALLING_STALACTITE, attacker); }

    public DamageSource sting(LivingEntity attacker) { return source(DamageTypes.STING, attacker); }

    public DamageSource mobAttack(LivingEntity attacker) { return source(DamageTypes.MOB_ATTACK, attacker); }

    public DamageSource noAggroMobAttack(LivingEntity attacker) { return source(DamageTypes.MOB_ATTACK_NO_AGGRO, attacker); }

    public DamageSource playerAttack(Player attacker) { return source(DamageTypes.PLAYER_ATTACK, attacker); }

    public DamageSource arrow(AbstractArrow source, Entity attacker) { return source(DamageTypes.ARROW, source, attacker); }

    public DamageSource trident(Entity source, Entity attacker) { return source(DamageTypes.TRIDENT, source, attacker); }

    public DamageSource mobProjectile(Entity source, LivingEntity attacker) { return source(DamageTypes.MOB_PROJECTILE, source, attacker); }

    public DamageSource spit(Entity source, LivingEntity attacker) { return source(DamageTypes.SPIT, source, attacker); }

    public DamageSource windCharge(Entity source, LivingEntity attacker) { return source(DamageTypes.WIND_CHARGE, source, attacker); }

    public DamageSource fireworks(FireworkRocketEntity source, Entity attacker) { return source(DamageTypes.FIREWORKS, source, attacker); }

    public DamageSource fireball(Fireball source, Entity attacker) {
        return attacker == null ? source(DamageTypes.UNATTRIBUTED_FIREBALL, source) : source(DamageTypes.FIREBALL, source, attacker);
    }

    public DamageSource witherSkull(WitherSkull source, Entity attacker) { return source(DamageTypes.WITHER_SKULL, source, attacker); }

    public DamageSource thrown(Entity source, Entity attacker) { return source(DamageTypes.THROWN, source, attacker); }

    public DamageSource indirectMagic(Entity source, Entity attacker) { return source(DamageTypes.INDIRECT_MAGIC, source, attacker); }

    public DamageSource thorns(Entity attacker) { return source(DamageTypes.THORNS, attacker); }

    public DamageSource explosion(Entity source, Entity attacker) {
        return source(attacker != null && source != null ? DamageTypes.PLAYER_EXPLOSION : DamageTypes.EXPLOSION, source, attacker);
    }

    public DamageSource sonicBoom(Entity attacker) { return source(DamageTypes.SONIC_BOOM, attacker); }

    public DamageSource badRespawnPointExplosion(Vec3 position) { return badRespawnPointExplosion(position, null); }

    /** Paper: 터진 블록 상태를 직접 블록 상태로 싣는다. */
    public DamageSource badRespawnPointExplosion(Vec3 position, org.bukkit.block.BlockState state) {
        return new DamageSource(damageTypes.getHolderOrThrow(DamageTypes.BAD_RESPAWN_POINT), position).directBlockState(state);
    }

    public DamageSource outOfBorder() { return outsideBorder; }

    public DamageSource genericKill() { return genericKill; }
}
