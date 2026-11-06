// 바닐라 RandomSwimmingGoal: 물속을 무작위로 헤엄친다. 목표 칸은 LandRandomPos 로 뽑되 물 통행 가능한 칸이 아니면 최대 10번 다시 뽑는다.
package org.domi.furnace.server.entity.ai.goal;

import org.domi.furnace.server.block.Pathfinding;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.PathfinderMob;
import org.domi.furnace.server.entity.ai.util.LandRandomPos;

public class RandomSwimmingGoal extends RandomStrollGoal {

    public RandomSwimmingGoal(PathfinderMob mob, double speed, int interval) { super(mob, speed, interval); }

    @Override
    protected double[] getPosition() {
        double[] p = LandRandomPos.getPos(mob, 10, 7);
        for (int i = 0; p != null && !isWaterPathfindable(p) && i++ < 10; ) p = LandRandomPos.getPos(mob, 10, 7);
        return p;
    }

    private boolean isWaterPathfindable(double[] p) {
        Pos pos = new Pos((int) Math.floor(p[0]), (int) Math.floor(p[1]), (int) Math.floor(p[2]));
        return Pathfinding.isPathfindable(mob.level().getBlockState(pos), Pathfinding.WATER);
    }
}
