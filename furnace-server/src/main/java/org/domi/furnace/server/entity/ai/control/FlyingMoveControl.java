// 바닐라 FlyingMoveControl: 중력을 끄고(setNoGravity) 대상 점을 향해 위아래(xRot)와 앞(speed)·위(yya) 입력으로 난다.
package org.domi.furnace.server.entity.ai.control;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.entity.Mob;

public class FlyingMoveControl extends MoveControl {
    private final int maxTurn;
    private final boolean hoversInPlace;

    public FlyingMoveControl(Mob mob, int maxTurn, boolean hoversInPlace) {
        super(mob);
        this.maxTurn = maxTurn;
        this.hoversInPlace = hoversInPlace;
    }

    @Override
    public void tick() {
        if (operation == Operation.MOVE_TO) {
            operation = Operation.WAIT;
            mob.setNoGravity(true);
            double dx = wantedX - mob.x(), dy = wantedY - mob.y(), dz = wantedZ - mob.z();
            double d = dx * dx + dy * dy + dz * dz;
            if (d < 2.5000003E-7F) {
                mob.setYya(0.0F);
                mob.setZza(0.0F);
                return;
            }
            float target = (float) (Mth.atan2(dz, dx) * 180.0F / (float) Math.PI) - 90.0F;
            mob.setYRot(rotlerp(mob.yRot(), target, 90.0F));
            float speed;
            if (mob.onGround()) speed = (float) (speedModifier * mob.attributeValue(Attribute.MOVEMENT_SPEED));
            else speed = (float) (speedModifier * mob.attributeValue(Attribute.FLYING_SPEED));
            mob.setSpeed(speed);
            double h = Math.sqrt(dx * dx + dz * dz);
            if (Math.abs(dy) > 1.0E-5F || Math.abs(h) > 1.0E-5F) {
                float xr = (float) (-(Mth.atan2(dy, h) * 180.0F / (float) Math.PI));
                mob.setXRot(rotlerp(mob.xRot(), xr, maxTurn));
                mob.setYya(dy > 0.0 ? speed : -speed);
            }
        } else {
            if (!hoversInPlace) mob.setNoGravity(false);
            mob.setYya(0.0F);
            mob.setZza(0.0F);
        }
    }
}
