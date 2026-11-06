// 바닐라 Vex.VexMoveControl: 가고 싶은 점 쪽으로 속도를 직접 더해 날아간다(벽 통과). 닿으면 속도를 반으로.
package org.domi.furnace.server.entity.ai.control;

import org.domi.furnace.server.entity.Mob;

public class VexMoveControl extends MoveControl {
    public VexMoveControl(Mob mob) { super(mob); }

    @Override
    public void tick() {
        if (operation == Operation.MOVE_TO) {
            double dx = wantedX - mob.x(), dy = wantedY - mob.y(), dz = wantedZ - mob.z();
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < mob.boundingBox().size()) {
                operation = Operation.WAIT;
                mob.setDeltaMovement(mob.vx() * 0.5, mob.vy() * 0.5, mob.vz() * 0.5);
            } else {
                double s = speedModifier * 0.05 / len;
                mob.setDeltaMovement(mob.vx() + dx * s, mob.vy() + dy * s, mob.vz() + dz * s);
                if (mob.getTarget() == null) {
                    mob.setYRot(-((float) Mth.atan2(mob.vx(), mob.vz())) * (180.0F / (float) Math.PI));
                    mob.setYBodyRot(mob.yRot());
                } else {
                    double tx = mob.getTarget().x() - mob.x(), tz = mob.getTarget().z() - mob.z();
                    mob.setYRot(-((float) Mth.atan2(tx, tz)) * (180.0F / (float) Math.PI));
                    mob.setYBodyRot(mob.yRot());
                }
            }
        }
    }
}
