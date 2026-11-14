// 좀비류가 문을 부수기 직전(EntityChangeBlockEvent 의 하위 — 그 이벤트 구독자도 받는다). 취소하면 문은 그대로이고 부수기를 처음부터 다시 센다.
package org.domi.furnace.api.event.entity;

import org.domi.furnace.api.entity.LivingEntity;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;

public final class EntityBreakDoorEvent extends EntityChangeBlockEvent {

    public EntityBreakDoorEvent(LivingEntity entity, String world, BlockPos pos, BlockData oldData, BlockData newData) {
        super(entity, world, pos, oldData, newData);
    }

    @Override
    public LivingEntity entity() { return (LivingEntity) super.entity(); }
}
