// 바닐라 BreathAirGoal: 숨(공기)이 140 미만이면 가까운 공기 칸(위로 8칸까지)으로 올라가 숨을 쉰다.
package org.domi.furnace.server.entity.ai.goal;

import java.util.EnumSet;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehaviors;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.Pathfinding;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.PathfinderMob;
import org.domi.furnace.server.entity.ai.Goal;

public class BreathAirGoal extends Goal {
    private final PathfinderMob mob;

    public BreathAirGoal(PathfinderMob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override public boolean canUse() { return mob.getAirSupply() < 140; }

    @Override public boolean canContinueToUse() { return canUse(); }

    @Override public boolean isInterruptable() { return false; }

    @Override public void start() { findAirPosition(); }

    private void findAirPosition() {
        int x0 = (int) Math.floor(mob.x() - 1.0), x1 = (int) Math.floor(mob.x() + 1.0);
        int z0 = (int) Math.floor(mob.z() - 1.0), z1 = (int) Math.floor(mob.z() + 1.0);
        int y0 = mob.blockY(), y1 = (int) Math.floor(mob.y() + 8.0);
        Pos found = null;
        search:
        for (int z = z0; z <= z1; z++) {
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    if (givesAir(new Pos(x, y, z))) {
                        found = new Pos(x, y, z);
                        break search;
                    }
                }
            }
        }
        if (found == null) found = new Pos((int) Math.floor(mob.x()), (int) Math.floor(mob.y() + 8.0), (int) Math.floor(mob.z()));
        mob.getNavigation().moveTo(found.x(), found.y() + 1, found.z(), 1.0);
    }

    @Override
    public void tick() {
        findAirPosition();
        mob.moveRelative(0.02F, mob.getXxa(), mob.getYya(), mob.getZza());
        mob.move(org.domi.furnace.server.entity.Entity.MOVER_SELF, mob.vx(), mob.vy(), mob.vz());
    }

    private boolean givesAir(Pos pos) {
        int state = mob.level().getBlockState(pos);
        return (Fluids.isEmpty(mob.level().getFluidState(pos)) || B.is(state, "minecraft:bubble_column")) && Pathfinding.isPathfindable(state, Pathfinding.LAND);
    }
}
