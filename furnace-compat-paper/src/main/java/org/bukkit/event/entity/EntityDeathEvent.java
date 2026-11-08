package org.bukkit.event.entity;

import java.util.List;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/**
 * 생물이 죽었다(플레이어는 하위 클래스 PlayerDeathEvent). 떨어뜨릴 아이템·경험치·취소·되살릴 체력은 Furnace 사망 이벤트에 잇는다(바꾸면 반영된다 — Paper 1.21.1 과 같다).
 * 취소하면 죽지 않고 체력이 getReviveHealth 가 된다. Paper 의 사망 소리 설정(shouldPlayDeathSound·setDeathSound 등)은 아직 없다.
 */
public class EntityDeathEvent extends EntityEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private final List<ItemStack> drops;
    private int dropExp;
    private boolean cancelled;
    private double reviveHealth;

    public EntityDeathEvent(LivingEntity entity, List<ItemStack> drops) { this(entity, drops, 0); }

    public EntityDeathEvent(LivingEntity what, List<ItemStack> drops, int droppedExp) {
        super(what);
        this.drops = drops;
        this.dropExp = droppedExp;
    }

    @Override
    public LivingEntity getEntity() { return (LivingEntity) entity; }

    public int getDroppedExp() {
        if (drops == null) throw unknown("getDroppedExp");
        return dropExp;
    }

    public void setDroppedExp(int exp) {
        if (drops == null) throw unknown("setDroppedExp");
        this.dropExp = exp;
    }

    public List<ItemStack> getDrops() {
        if (drops == null) throw unknown("getDrops");
        return drops;
    }

    /** Paper: 취소했을 때 되살릴 체력(서버가 부른 이벤트는 기본이 최대 체력). */
    public double getReviveHealth() { return reviveHealth; }

    /** Paper: 0 초과(최대 체력이 0 이 아닐 때) 최대 체력 이하여야 한다. */
    public void setReviveHealth(double reviveHealth) {
        double max = getEntity().getMaxHealth();
        if ((max != 0 && reviveHealth <= 0) || reviveHealth > max) {
            throw new IllegalArgumentException("Health must be between 0 (exclusive) and " + max + " (inclusive), but was " + reviveHealth);
        }
        this.reviveHealth = reviveHealth;
    }

    /** 호환 계층 전용: 검사 없이 Furnace 값을 옮긴다. */
    public void initReviveHealth(double reviveHealth) { this.reviveHealth = reviveHealth; }

    /** Paper: 취소하면 죽지 않는다. */
    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) {
        if (cancel && drops == null) throw unknown("setCancelled");
        this.cancelled = cancel;
    }

    /** Furnace 가 그 값을 알리지 않는 이벤트(드롭 목록 null)에서 쓴다. */
    static org.domi.furnace.compat.paper.UnsupportedFeature unknown(String what) {
        return new org.domi.furnace.compat.paper.UnsupportedFeature("EntityDeathEvent#" + what, "Furnace 사망 이벤트는 떨어뜨릴 아이템·경험치·취소를 알리지 않는다");
    }

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
