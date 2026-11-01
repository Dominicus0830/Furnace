// 바닐라 LookAtPlayerGoal(플레이어 대상): 가까운 플레이어를 40~79틱 바라본다. TODO(타게팅 조건): TargetingConditions 의 투명·팀·보임 검사.
package org.domi.furnace.server.entity.ai.goal;

import java.util.EnumSet;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.ai.Goal;
import org.domi.furnace.server.world.GameMode;

public class LookAtPlayerGoal extends Goal {
    protected final Mob mob;
    protected Player lookAt;
    protected final float lookDistance;
    private int lookTime;
    protected final float probability;
    private final boolean onlyHorizontal;

    public LookAtPlayerGoal(Mob mob, float lookDistance) { this(mob, lookDistance, 0.02F, false); }

    public LookAtPlayerGoal(Mob mob, float lookDistance, float probability) { this(mob, lookDistance, probability, false); }

    public LookAtPlayerGoal(Mob mob, float lookDistance, float probability, boolean onlyHorizontal) {
        this.mob = mob;
        this.lookDistance = lookDistance;
        this.probability = probability;
        this.onlyHorizontal = onlyHorizontal;
        setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (mob.random().nextFloat() >= probability) return false;
        lookAt = mob.level().getNearestPlayer(mob, lookDistance);
        return lookAt != null && lookAt.gameMode() != GameMode.SPECTATOR;
    }

    @Override
    public boolean canContinueToUse() {
        if (lookAt == null || lookAt.vitals().isDeadOrDying() || lookAt.gameMode() == GameMode.SPECTATOR) return false;
        double dx = lookAt.x() - mob.x(), dy = lookAt.y() - mob.y(), dz = lookAt.z() - mob.z();
        return dx * dx + dy * dy + dz * dz <= (double) (lookDistance * lookDistance) && lookTime > 0;
    }

    @Override public void start() { lookTime = adjustedTickDelay(40 + mob.random().nextInt(40)); }

    @Override public void stop() { lookAt = null; }

    @Override
    public void tick() {
        if (lookAt != null && !lookAt.vitals().isDeadOrDying()) {
            double y = onlyHorizontal ? mob.eyeY() : lookAt.eyeY();
            mob.getLookControl().setLookAt(lookAt.x(), y, lookAt.z());
            lookTime--;
        }
    }
}
