package org.bukkit.event.entity;

import java.util.Collections;
import java.util.List;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * 엔티티가 다른 엔티티로 바뀌기 직전(좀비 주민 치료·번개 맞은 돼지 등). Furnace 에는 맞는 이벤트가 아직 없어 불리지 않는다
 * (리스너 클래스가 이 이벤트를 써도 올라가게 클래스만 둔다).
 */
public class EntityTransformEvent extends EntityEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private boolean cancelled;
    private final Entity converted;
    private final List<Entity> convertedList;
    private final TransformReason transformReason;

    public EntityTransformEvent(Entity original, List<Entity> convertedList, TransformReason transformReason) {
        super(original);
        this.convertedList = Collections.unmodifiableList(convertedList);
        this.converted = convertedList.get(0);
        this.transformReason = transformReason;
    }

    /** 바뀐 엔티티(여럿이면 첫째). */
    public Entity getTransformedEntity() { return converted; }

    public List<Entity> getTransformedEntities() { return convertedList; }

    public TransformReason getTransformReason() { return transformReason; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }

    public enum TransformReason {
        CURED, FROZEN, INFECTION, DROWNED, SHEARED, LIGHTNING, SPLIT, PIGLIN_ZOMBIFIED, METAMORPHOSIS, UNKNOWN
    }
}
