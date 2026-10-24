// 바닐라 BreedGoal: 사랑 모드인 같은 종류를 찾아가 붙으면 새끼를 낳는다.
package org.domi.furnace.server.entity.ai.goal;

import java.util.EnumSet;
import org.domi.furnace.server.entity.Animal;
import org.domi.furnace.server.entity.ai.Goal;
import org.domi.furnace.server.entity.ai.targeting.TargetingConditions;

public class BreedGoal extends Goal {
    private static final TargetingConditions PARTNER_TARGETING = TargetingConditions.forNonCombat().range(8.0).ignoreLineOfSight();
    protected final Animal animal;
    private final Class<? extends Animal> partnerClass;
    protected Animal partner;
    private int loveTime;
    private final double speedModifier;

    public BreedGoal(Animal animal, double speed) { this(animal, speed, animal.getClass()); }

    public BreedGoal(Animal animal, double speed, Class<? extends Animal> partnerClass) {
        this.animal = animal;
        this.partnerClass = partnerClass;
        this.speedModifier = speed;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!animal.isInLove()) return false;
        partner = getFreePartner();
        return partner != null;
    }

    @Override
    public boolean canContinueToUse() {
        return partner.isAlive() && partner.isInLove() && loveTime < 60 && !partner.isPanicking();
    }

    @Override
    public void stop() {
        partner = null;
        loveTime = 0;
    }

    @Override
    public void tick() {
        animal.getLookControl().setLookAt(partner, 10.0F, animal.getMaxHeadXRot());
        animal.getNavigation().moveTo(partner, speedModifier);
        loveTime++;
        if (loveTime >= adjustedTickDelay(60) && distSqr(partner) < 9.0) breed();
    }

    private double distSqr(Animal a) {
        double dx = animal.x() - a.x(), dy = animal.y() - a.y(), dz = animal.z() - a.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private Animal getFreePartner() {
        var list = animal.level().getEntitiesOfClass(partnerClass, animal.boundingBox().inflate(8.0), e -> PARTNER_TARGETING.test(animal, e));
        double best = Double.MAX_VALUE;
        Animal found = null;
        for (Animal a : list) {
            if (animal.canMate(a) && !a.isPanicking() && distSqr(a) < best) {
                found = a;
                best = distSqr(a);
            }
        }
        return found;
    }

    protected void breed() { animal.spawnChildFromBreeding(animal.level(), partner); }
}
