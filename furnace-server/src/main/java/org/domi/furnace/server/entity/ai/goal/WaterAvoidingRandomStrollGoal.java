// 바닐라 WaterAvoidingRandomStrollGoal: 물 속이면 DefaultRandomPos(15, 7)로, 아니면, 아니면 확률(0.001 미만)로 기본 위치 선택 대신 (10, 7) DefaultRandomPos.
package org.domi.furnace.server.entity.ai.goal;

import org.domi.furnace.server.entity.PathfinderMob;
import org.domi.furnace.server.entity.ai.util.DefaultRandomPos;

public class WaterAvoidingRandomStrollGoal extends RandomStrollGoal {
    public static final float PROBABILITY = 0.001F;
    protected final float probability;

    public WaterAvoidingRandomStrollGoal(PathfinderMob mob, double speed) { this(mob, speed, PROBABILITY); }

    public WaterAvoidingRandomStrollGoal(PathfinderMob mob, double speed, float probability) {
        super(mob, speed);
        this.probability = probability;
    }

    @Override
    protected double[] getPosition() {
        if (mob.isInWaterOrBubble()) {
            double[] p = DefaultRandomPos.getPos(mob, 15, 7);
            return p == null ? super.getPosition() : p;
        }
        return mob.random().nextFloat() >= probability ? DefaultRandomPos.getPos(mob, 10, 7) : super.getPosition();
    }
}
