package org.bukkit.event.entity;

import java.util.List;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/**
 * 생물이 죽었다(플레이어는 하위 클래스 PlayerDeathEvent). Furnace 사망 이벤트는 떨어뜨릴 아이템·경험치를 싣지 않고 취소할 수 없어 —
 * getDrops·getDroppedExp·setDroppedExp·setCancelled(true) 는 플레이어·몹 모두 UnsupportedFeature 다(지어내지 않는다).
 */
public class EntityDeathEvent extends EntityEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private final List<ItemStack> drops;
    private int dropExp;
    private boolean cancelled;

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
