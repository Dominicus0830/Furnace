// 바닐라 ResetUniversalAngerTargetGoal: 모든 플레이어에게 화난 상태(universalAnger)에서 새 플레이어가 때리면 분노를 새로 한다.
package org.domi.furnace.server.entity.ai.goal;

import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.NeutralMob;
import org.domi.furnace.server.entity.ai.Goal;

public class ResetUniversalAngerTargetGoal<T extends Mob & NeutralMob> extends Goal {
    private final T mob;
    private final boolean alertOthersOfSameType;
    private int lastHurtByPlayerTimestamp;

    public ResetUniversalAngerTargetGoal(T mob, boolean alertOthers) {
        this.mob = mob;
        this.alertOthersOfSameType = alertOthers;
    }

    @Override
    public boolean canUse() {
        return mob.level().gameRule("universalAnger") && wasHurtByPlayer();
    }

    private boolean wasHurtByPlayer() {
        return mob.getLastHurtByMob() != null && mob.getLastHurtByMob().isPlayer() && mob.getLastHurtByMobTimestamp() > lastHurtByPlayerTimestamp;
    }

    @Override
    public void start() {
        lastHurtByPlayerTimestamp = mob.getLastHurtByMobTimestamp();
        mob.forgetCurrentTargetAndRefreshUniversalAnger();
        if (alertOthersOfSameType) {
            double range = mob.attributeValue(org.domi.furnace.server.attribute.Attribute.FOLLOW_RANGE);
            var box = mob.boundingBox().inflate(range, 10.0, range);
            for (var o : mob.level().getEntitiesOfClass(mob.getClass(), box, e -> e != mob && e.isAlive())) {
                if (o instanceof NeutralMob n) n.forgetCurrentTargetAndRefreshUniversalAnger();
            }
        }
        super.start();
    }
}
