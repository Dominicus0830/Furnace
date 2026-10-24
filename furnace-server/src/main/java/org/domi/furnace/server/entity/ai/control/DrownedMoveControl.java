// 바닐라 Drowned.DrownedMoveControl: 물속에서 헤엄치려 할 때 속도를 직접 더해 대상 쪽으로 나아가고, 아니면 일반 이동.
package org.domi.furnace.server.entity.ai.control;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.entity.Drowned;
import org.domi.furnace.server.entity.Targetable;

public class DrownedMoveControl extends MoveControl {
    private final Drowned drowned;

    public DrownedMoveControl(Drowned drowned) {
        super(drowned);
        this.drowned = drowned;
    }

    @Override
    public void tick() {
        Targetable target = drowned.getTarget();
        if (drowned.wantsToSwim() && drowned.isInWater()) {
            if (target != null && target.y() > drowned.y() || drowned.isSearchingForLand()) drowned.setDeltaMovement(drowned.vx(), drowned.vy() + 0.002, drowned.vz());
            if (operation != Operation.MOVE_TO || drowned.getNavigation().isDone()) {
                drowned.setSpeed(0.0F);
                return;
            }
            double dx = wantedX - drowned.x(), dy = wantedY - drowned.y(), dz = wantedZ - drowned.z();
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            dy /= len;
            float yaw = (float) (Mth.atan2(dz, dx) * 180.0F / (float) Math.PI) - 90.0F;
            drowned.setYRot(rotlerp(drowned.yRot(), yaw, 90.0F));
            drowned.setYBodyRot(drowned.yRot());
            float speed = (float) (speedModifier * drowned.attributeValue(Attribute.MOVEMENT_SPEED));
            float f = Mth.lerp(0.125F, drowned.getSpeed(), speed);
            drowned.setSpeed(f);
            drowned.setDeltaMovement(drowned.vx() + f * dx * 0.005, drowned.vy() + f * dy * 0.1, drowned.vz() + f * dz * 0.005);
        } else {
            if (!drowned.onGround()) drowned.setDeltaMovement(drowned.vx(), drowned.vy() - 0.008, drowned.vz());
            super.tick();
        }
    }
}
