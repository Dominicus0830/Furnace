package org.bukkit.event.entity;

import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

public class EntityDamageEvent extends EntityEvent implements Cancellable {

    private final DamageCause cause;
    private double damage;
    private boolean cancelled;

    public EntityDamageEvent(Entity damagee, DamageCause cause, double damage) {
        super(damagee);
        this.cause = cause;
        this.damage = damage;
    }

    public double getDamage() { return damage; }

    /** 받은 피해(0 이상). 방어·효과 계산 뒤의 최종 값은 Furnace 의 EntityDamageCalculateEvent 단계가 따로 있다. */
    public void setDamage(double damage) {
        if (!(damage >= 0)) throw new IllegalArgumentException("damage 는 0 이상: " + damage);
        this.damage = damage;
    }

    public DamageCause getCause() { return cause; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    public enum DamageCause {
        CONTACT, ENTITY_ATTACK, ENTITY_SWEEP_ATTACK, PROJECTILE, SUFFOCATION, FALL, FIRE, FIRE_TICK, MELTING, LAVA, DROWNING,
        BLOCK_EXPLOSION, ENTITY_EXPLOSION, VOID, LIGHTNING, SUICIDE, STARVATION, POISON, MAGIC, WITHER, FALLING_BLOCK, THORNS,
        DRAGON_BREATH, CUSTOM, FLY_INTO_WALL, HOT_FLOOR, CRAMMING, DRYOUT, FREEZE, SONIC_BOOM
    }

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
