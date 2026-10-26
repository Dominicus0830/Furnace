// 바닐라 DoorInteractGoal: 길 위(앞 두 칸) 나무문이 있고 부딪혔으면 그 문 곁에서 문 앞뒤를 지나갈 때까지 붙잡는다.
package org.domi.furnace.server.entity.ai.goal;

import org.domi.furnace.server.block.BlockBehaviors;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.types.DoorBlock;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.Goal;
import org.domi.furnace.server.entity.ai.navigation.GroundPathNavigation;
import org.domi.furnace.server.entity.ai.util.GoalUtils;

public abstract class DoorInteractGoal extends Goal {
    protected final Mob mob;
    protected Pos doorPos = Pos.ZERO;
    protected boolean hasDoor;
    private boolean passed;
    private float doorOpenDirX, doorOpenDirZ;

    protected DoorInteractGoal(Mob mob) {
        this.mob = mob;
        if (!GoalUtils.hasGroundPathNavigation(mob)) throw new IllegalArgumentException("Unsupported mob type for DoorInteractGoal");
    }

    protected boolean isOpen() {
        if (!hasDoor) return false;
        int state = mob.level().getBlockState(doorPos);
        if (!(BlockBehaviors.of(state) instanceof DoorBlock door)) {
            hasDoor = false;
            return false;
        }
        return door.isOpen(state);
    }

    protected void setOpen(boolean open) {
        if (!hasDoor) return;
        int state = mob.level().getBlockState(doorPos);
        if (BlockBehaviors.of(state) instanceof DoorBlock door) door.setOpen(null, mob.level(), state, doorPos, open);
    }

    @Override
    public boolean canUse() {
        if (!GoalUtils.hasGroundPathNavigation(mob)) return false;
        if (!mob.horizontalCollision) return false;
        GroundPathNavigation nav = (GroundPathNavigation) mob.getNavigation();
        var path = nav.getPath();
        if (path == null || path.isDone() || !nav.canOpenDoors()) return false;
        for (int i = 0; i < Math.min(path.getNextNodeIndex() + 2, path.getNodeCount()); i++) {
            var node = path.getNode(i);
            doorPos = new Pos(node.x, node.y + 1, node.z);
            double dx = mob.x() - doorPos.x(), dz = mob.z() - doorPos.z();
            if (!(dx * dx + 0.0 + dz * dz > 2.25)) {   // distanceToSqr(doorX, mob.getY(), doorZ): y 차이는 0
                hasDoor = DoorBlock.isWoodenDoor(mob.level(), doorPos);
                if (hasDoor) return true;
            }
        }
        doorPos = mob.blockPosition().above();
        hasDoor = DoorBlock.isWoodenDoor(mob.level(), doorPos);
        return hasDoor;
    }

    @Override
    public boolean canContinueToUse() { return !passed; }

    @Override
    public void start() {
        passed = false;
        doorOpenDirX = (float) (doorPos.x() + 0.5 - mob.x());
        doorOpenDirZ = (float) (doorPos.z() + 0.5 - mob.z());
    }

    @Override
    public boolean requiresUpdateEveryTick() { return true; }

    @Override
    public void tick() {
        float dx = (float) (doorPos.x() + 0.5 - mob.x());
        float dz = (float) (doorPos.z() + 0.5 - mob.z());
        float dot = doorOpenDirX * dx + doorOpenDirZ * dz;
        if (dot < 0.0F) passed = true;
    }
}
