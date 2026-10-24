// 바닐라 OwnerHurtTargetGoal: 주인이 때린 상대를 공격 대상으로 삼는다.
package org.domi.furnace.server.entity.ai.goal;

import java.util.EnumSet;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.TamableAnimal;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.ai.targeting.TargetingConditions;

public class OwnerHurtTargetGoal extends TargetGoal {
    private final TamableAnimal tameAnimal;
    private Targetable ownerLastHurt;
    private int timestamp;

    public OwnerHurtTargetGoal(TamableAnimal mob) {
        super(mob, false);
        this.tameAnimal = mob;
        setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (!tameAnimal.isTame() || tameAnimal.isInSittingPose()) return false;
        Player owner = tameAnimal.getOwner();
        if (owner == null) return false;
        ownerLastHurt = owner.getLastHurtMob();
        int t = owner.getLastHurtMobTimestamp();
        return t != timestamp && canAttack(ownerLastHurt, TargetingConditions.forCombat()) && tameAnimal.wantsToAttack(ownerLastHurt, owner);
    }

    @Override
    public void start() {
        mob.setTarget(ownerLastHurt);
        Player owner = tameAnimal.getOwner();
        if (owner != null) timestamp = owner.getLastHurtMobTimestamp();
        super.start();
    }
}
