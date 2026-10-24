// 바닐라 SmoothSwimmingMoveControl: 물속에서 상하(xRot)·좌우(yRot)를 부드럽게 돌려 대상 쪽으로 헤엄친다. 물 밖에서는 느리게 걷는다.
package org.domi.furnace.server.entity.ai.control;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.entity.Mob;

public class SmoothSwimmingMoveControl extends MoveControl {
    private static final float FULL_SPEED_TURN_THRESHOLD = 10.0F;
    private static final float STOP_TURN_THRESHOLD = 60.0F;
    private final int maxTurnX;
    private final int maxTurnY;
    private final float inWaterSpeedModifier;
    private final float outsideWaterSpeedModifier;
    private final boolean applyGravity;

    public SmoothSwimmingMoveControl(Mob mob, int maxTurnX, int maxTurnY, float inWaterSpeedModifier, float outsideWaterSpeedModifier, boolean applyGravity) {
        super(mob);
        this.maxTurnX = maxTurnX;
        this.maxTurnY = maxTurnY;
        this.inWaterSpeedModifier = inWaterSpeedModifier;
        this.outsideWaterSpeedModifier = outsideWaterSpeedModifier;
        this.applyGravity = applyGravity;
    }

    @Override
    public void tick() {
        if (applyGravity && mob.isInWater()) mob.setDeltaMovement(mob.vx(), mob.vy() + 0.005, mob.vz());
        if (operation == Operation.MOVE_TO && !mob.getNavigation().isDone()) {
            double dx = wantedX - mob.x(), dy = wantedY - mob.y(), dz = wantedZ - mob.z();
            double d = dx * dx + dy * dy + dz * dz;
            if (d < 2.5000003E-7F) {
                mob.setZza(0.0F);
            } else {
                float target = (float) (Mth.atan2(dz, dx) * 180.0F / (float) Math.PI) - 90.0F;
                mob.setYRot(rotlerp(mob.yRot(), target, maxTurnY));
                mob.setYBodyRot(mob.yRot());
                mob.setYHeadRot(mob.yRot());
                float speed = (float) (speedModifier * mob.attributeValue(Attribute.MOVEMENT_SPEED));
                if (mob.isInWater()) {
                    mob.setSpeed(speed * inWaterSpeedModifier);
                    double h = Math.sqrt(dx * dx + dz * dz);
                    if (Math.abs(dy) > 1.0E-5F || Math.abs(h) > 1.0E-5F) {
                        float xr = -((float) (Mth.atan2(dy, h) * 180.0F / (float) Math.PI));
                        xr = Math.clamp(Mth.wrapDegrees(xr), (float) -maxTurnX, (float) maxTurnX);
                        mob.setXRot(rotlerp(mob.xRot(), xr, 5.0F));
                    }
                    float cos = Mth.cos(mob.xRot() * (float) (Math.PI / 180.0));
                    float sin = Mth.sin(mob.xRot() * (float) (Math.PI / 180.0));
                    mob.setZza(cos * speed);
                    mob.setYya(-sin * speed);
                } else {
                    float diff = Math.abs(Mth.wrapDegrees(mob.yRot() - target));
                    float f = 1.0F - Math.clamp((diff - FULL_SPEED_TURN_THRESHOLD) / 50.0F, 0.0F, 1.0F);
                    mob.setSpeed(speed * outsideWaterSpeedModifier * f);
                }
            }
        } else {
            mob.setSpeed(0.0F);
            mob.setYya(0.0F);
            mob.setXxa(0.0F);
            mob.setZza(0.0F);
        }
    }
}
