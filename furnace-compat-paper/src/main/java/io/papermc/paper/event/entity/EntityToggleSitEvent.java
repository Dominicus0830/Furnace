package io.papermc.paper.event.entity;

import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityEvent;

/**
 * 길들인 동물(늑대·고양이·앵무새)이나 여우가 앉거나 일어서기 직전(Paper 1.21.1 과 같다). 취소하면 자세가 그대로다.
 * 저장된 엔티티를 불러올 때와 NMS {@code setInSittingPose(sit, false)}·{@code setSitting(sit, false)} 는 내지 않는다.
 */
public class EntityToggleSitEvent extends EntityEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private final boolean isSitting;
    private boolean cancelled;

    public EntityToggleSitEvent(Entity entity, boolean isSitting) {
        super(entity);
        this.isSitting = isSitting;
    }

    /** 앉으려 하면 true, 일어서려 하면 false. */
    public boolean getSittingState() { return isSitting; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
