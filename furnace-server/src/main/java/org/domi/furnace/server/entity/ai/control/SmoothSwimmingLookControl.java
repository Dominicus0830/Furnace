// 바닐라 SmoothSwimmingLookControl: 물속 몹의 시선 제어 — 머리를 몸 기준 maxYRotFromCenter 안으로 두고, 바라볼 때 몸을 4도씩 따라 돌린다.
package org.domi.furnace.server.entity.ai.control;

import org.domi.furnace.server.entity.Mob;

public class SmoothSwimmingLookControl extends LookControl {
    private final int maxYRotFromCenter;

    public SmoothSwimmingLookControl(Mob mob, int maxYRotFromCenter) {
        super(mob);
        this.maxYRotFromCenter = maxYRotFromCenter;
    }

    @Override
    public void tick() {
        if (lookAtCooldown > 0) {
            lookAtCooldown--;
            getYRotD().ifPresent(v -> mob.setYHeadRot(rotateTowards(mob.yHeadRot(), (float) v + 20.0F, yMaxRotSpeed)));
            getXRotD().ifPresent(v -> mob.setXRot(rotateTowards(mob.xRot(), (float) v + 10.0F, xMaxRotAngle)));
        } else {
            if (mob.getNavigation().isDone()) mob.setXRot(rotateTowards(mob.xRot(), 0.0F, 5.0F));
            mob.setYHeadRot(rotateTowards(mob.yHeadRot(), mob.yBodyRot(), yMaxRotSpeed));
        }
        float diff = Mth.wrapDegrees(mob.yHeadRot() - mob.yBodyRot());
        if (diff < -maxYRotFromCenter) mob.setYBodyRot(mob.yBodyRot() - 4.0F);
        else if (diff > maxYRotFromCenter) mob.setYBodyRot(mob.yBodyRot() + 4.0F);
    }
}
