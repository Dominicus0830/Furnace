// 바닐라 TryFindWaterGoal: 물 밖 땅 위에 있으면 근처(±2칸)의 물 칸을 찾아 그쪽으로 움직인다.
package org.domi.furnace.server.entity.ai.goal;

import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.PathfinderMob;
import org.domi.furnace.server.entity.ai.Goal;

public class TryFindWaterGoal extends Goal {
    private final PathfinderMob mob;

    public TryFindWaterGoal(PathfinderMob mob) { this.mob = mob; }

    @Override
    public boolean canUse() { return mob.onGround() && !Fluids.isWater(mob.level().getFluidState(mob.blockPosition())); }

    @Override
    public void start() {
        Pos found = null;
        int x0 = (int) Math.floor(mob.x() - 2.0), x1 = (int) Math.floor(mob.x() + 2.0);
        int y0 = (int) Math.floor(mob.y() - 2.0), y1 = mob.blockY();
        int z0 = (int) Math.floor(mob.z() - 2.0), z1 = (int) Math.floor(mob.z() + 2.0);
        search:
        for (int z = z0; z <= z1; z++) {
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    if (Fluids.isWater(mob.level().getFluidState(new Pos(x, y, z)))) {
                        found = new Pos(x, y, z);
                        break search;
                    }
                }
            }
        }
        if (found != null) mob.getMoveControl().setWantedPosition(found.x(), found.y(), found.z(), 1.0);
    }
}
