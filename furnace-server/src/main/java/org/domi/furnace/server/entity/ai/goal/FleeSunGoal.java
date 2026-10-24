// 바닐라 FleeSunGoal: 낮에 불이 붙은 채 햇빛 아래 있으면 그늘진 칸으로 달아난다.
package org.domi.furnace.server.entity.ai.goal;

import java.util.EnumSet;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.EquipmentSlot;
import org.domi.furnace.server.entity.PathfinderMob;
import org.domi.furnace.server.entity.ai.Goal;

public class FleeSunGoal extends Goal {
    protected final PathfinderMob mob;
    private double wantedX, wantedY, wantedZ;
    private final double speedModifier;

    public FleeSunGoal(PathfinderMob mob, double speed) {
        this.mob = mob;
        this.speedModifier = speed;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (mob.getTarget() != null) return false;
        if (!mob.level().isDay()) return false;
        if (!mob.isOnFire()) return false;
        if (!mob.level().canSeeSky(mob.blockPosition())) return false;
        return mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty() ? setWantedPos() : false;
    }

    protected boolean setWantedPos() {
        double[] p = getHidePos();
        if (p == null) return false;
        wantedX = p[0];
        wantedY = p[1];
        wantedZ = p[2];
        return true;
    }

    @Override public boolean canContinueToUse() { return !mob.getNavigation().isDone(); }

    @Override public void start() { mob.getNavigation().moveTo(wantedX, wantedY, wantedZ, speedModifier); }

    protected double[] getHidePos() {
        var r = mob.random();
        Pos here = mob.blockPosition();
        for (int i = 0; i < 10; i++) {
            Pos p = new Pos(here.x() + r.nextInt(20) - 10, here.y() + r.nextInt(6) - 3, here.z() + r.nextInt(20) - 10);
            if (!mob.level().canSeeSky(p) && mob.getWalkTargetValue(p) < 0.0F) return new double[] {p.x() + 0.5, p.y(), p.z() + 0.5};
        }
        return null;
    }
}
