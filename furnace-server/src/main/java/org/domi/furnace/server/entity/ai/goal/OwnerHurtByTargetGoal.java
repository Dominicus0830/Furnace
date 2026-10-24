// 바닐라 OwnerHurtByTargetGoal: 주인을 때린 상대를 공격 대상으로 삼는다.
package org.domi.furnace.server.entity.ai.goal;

import java.util.EnumSet;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.TamableAnimal;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.ai.targeting.TargetingConditions;

public class OwnerHurtByTargetGoal extends TargetGoal {
    private final TamableAnimal tameAnimal;
    private Targetable ownerLastHurtBy;
    private int timestamp;

    public OwnerHurtByTargetGoal(TamableAnimal mob) {
        super(mob, false);
        this.tameAnimal = mob;
        setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (!tameAnimal.isTame() || tameAnimal.isInSittingPose()) return false;
        Player owner = tameAnimal.getOwner();
        if (owner == null) return false;
        ownerLastHurtBy = owner.getLastHurtByMob();
        int t = owner.getLastHurtByMobTimestamp();
        return t != timestamp && canAttack(ownerLastHurtBy, TargetingConditions.forCombat()) && tameAnimal.wantsToAttack(ownerLastHurtBy, owner);
    }

    @Override
    public void start() {
        mob.setTarget(ownerLastHurtBy);
        Player owner = tameAnimal.getOwner();
        if (owner != null) timestamp = owner.getLastHurtByMobTimestamp();
        super.start();
    }
}
