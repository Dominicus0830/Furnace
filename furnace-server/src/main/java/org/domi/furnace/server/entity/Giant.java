// 바닐라 Giant: 6배 크기의 좀비 모양 몹(자연 스폰 안 함). 체력 100, 공격력 50, 속도 0.5.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;

public class Giant extends Monster {

    public Giant(ChunkLevel level, int id) { super(EntityType.GIANT, level, id); }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MAX_HEALTH, 100.0).add(Attribute.MOVEMENT_SPEED, 0.5).add(Attribute.ATTACK_DAMAGE, 50.0);
    }

    @Override
    public float getWalkTargetValue(Pos pos, ChunkLevel level) {
        float f = level.getMaxLocalRawBrightness(pos) / 15.0F;
        return f / (4.0F - 3.0F * f) - 0.5F;
    }
}
