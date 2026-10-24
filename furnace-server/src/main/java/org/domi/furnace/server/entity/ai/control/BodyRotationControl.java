// 바닐라 BodyRotationControl: 이동 중이면 몸통을 시선(yRot)에 맞추고 머리를 몸통 쪽으로 끌어오며, 서 있을 때는 머리가 15도 넘게 돌면 몸통이 따라간다.
package org.domi.furnace.server.entity.ai.control;

import org.domi.furnace.server.entity.Mob;

public class BodyRotationControl {
    private static final int HEAD_STABLE_ANGLE = 15;
    private static final int DELAY_UNTIL_STARTING_TO_FACE_FORWARD = 10;
    private static final int HOW_LONG_IT_TAKES_TO_FACE_FORWARD = 10;
    private final Mob mob;
    private int headStableTime;
    private float lastStableYHeadRot;

    public BodyRotationControl(Mob mob) { this.mob = mob; }

    public void clientTick() {
        if (isMoving()) {
            mob.setYBodyRot(mob.yRot());
            rotateHeadIfNecessary();
            lastStableYHeadRot = mob.yHeadRot();
            headStableTime = 0;
        } else if (notCarryingMobPassengers()) {
            if (Math.abs(mob.yHeadRot() - lastStableYHeadRot) > HEAD_STABLE_ANGLE) {
                headStableTime = 0;
                lastStableYHeadRot = mob.yHeadRot();
                rotateBodyIfNecessary();
            } else {
                headStableTime++;
                if (headStableTime > DELAY_UNTIL_STARTING_TO_FACE_FORWARD) rotateHeadTowardsFront();
            }
        }
    }

    private void rotateBodyIfNecessary() { mob.setYBodyRot(Mth.rotateIfNecessary(mob.yBodyRot(), mob.yHeadRot(), mob.getMaxHeadYRot())); }

    private void rotateHeadIfNecessary() { mob.setYHeadRot(Mth.rotateIfNecessary(mob.yHeadRot(), mob.yBodyRot(), mob.getMaxHeadYRot())); }

    private void rotateHeadTowardsFront() {
        int t = headStableTime - DELAY_UNTIL_STARTING_TO_FACE_FORWARD;
        float f = Math.clamp(t / 10.0F, 0.0F, 1.0F);
        float max = mob.getMaxHeadYRot() * (1.0F - f);
        mob.setYBodyRot(Mth.rotateIfNecessary(mob.yBodyRot(), mob.yHeadRot(), max));
    }

    private boolean notCarryingMobPassengers() { return true; }

    private boolean isMoving() {
        double dx = mob.x() - mob.xo, dz = mob.z() - mob.zo;
        return dx * dx + dz * dz > 2.5000003E-7F;
    }
}
