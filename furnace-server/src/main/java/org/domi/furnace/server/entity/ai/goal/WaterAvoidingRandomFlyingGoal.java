// 바닐라 WaterAvoidingRandomFlyingGoal: 바라보는 방향 쪽 공중(AirRandomPos)으로, 안 되면 호버 위치로 무작위로 난다.
package org.domi.furnace.server.entity.ai.goal;

import org.domi.furnace.server.block.Clip;
import org.domi.furnace.server.entity.PathfinderMob;
import org.domi.furnace.server.entity.ai.util.FlyingRandomPos;

public class WaterAvoidingRandomFlyingGoal extends WaterAvoidingRandomStrollGoal {

    public WaterAvoidingRandomFlyingGoal(PathfinderMob mob, double speed) { super(mob, speed); }

    @Override
    protected double[] getPosition() {
        double[] view = Clip.viewVector(mob.xRot(), mob.yRot());
        double[] p = FlyingRandomPos.getAirPosTowards(mob, 8, 7, view[0], view[2], (float) (Math.PI / 2), 3, 1);
        return p != null ? p : FlyingRandomPos.getHoverPos(mob, 8, 4, -2, view[0], view[2], (float) (Math.PI / 2));
    }
}
