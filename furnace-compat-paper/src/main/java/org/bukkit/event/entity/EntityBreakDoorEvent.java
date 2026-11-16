package org.bukkit.event.entity;

import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.LivingEntity;

/** 좀비류가 문을 부순다. 처리기 목록은 {@link EntityChangeBlockEvent} 와 같다(Bukkit 처럼 getHandlerList 를 따로 두지 않는다). */
public class EntityBreakDoorEvent extends EntityChangeBlockEvent {

    public EntityBreakDoorEvent(LivingEntity entity, Block targetBlock, BlockData to) {
        super(entity, targetBlock, to);
    }

    @Override
    public LivingEntity getEntity() { return (LivingEntity) entity; }
}
