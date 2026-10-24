// 바닐라 LookControl: 바라볼 점을 받아 머리(yHeadRot)·상하각(xRot)을 틱마다 최대 속도로 돌린다.
package org.domi.furnace.server.entity.ai.control;

import java.util.OptionalDouble;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.LivingEntity;
import org.domi.furnace.server.entity.Mob;

public class LookControl {
    protected final Mob mob;
    protected float yMaxRotSpeed, xMaxRotAngle;
    protected int lookAtCooldown;
    protected double wantedX, wantedY, wantedZ;

    public LookControl(Mob mob) { this.mob = mob; }

    public void setLookAt(double x, double y, double z) { setLookAt(x, y, z, mob.getHeadRotSpeed(), mob.getMaxHeadXRot()); }

    public void setLookAt(Entity e) { setLookAt(e.x(), wantedY(e), e.z()); }

    public void setLookAt(Entity e, float yMax, float xMax) { setLookAt(e.x(), wantedY(e), e.z(), yMax, xMax); }

    public void setLookAt(double x, double y, double z, float yMax, float xMax) {
        wantedX = x;
        wantedY = y;
        wantedZ = z;
        yMaxRotSpeed = yMax;
        xMaxRotAngle = xMax;
        lookAtCooldown = 2;
    }

    public void tick() {
        if (resetXRotOnTick()) mob.setXRot(0.0F);
        if (lookAtCooldown > 0) {
            lookAtCooldown--;
            getYRotD().ifPresent(v -> mob.setYHeadRot(rotateTowards(mob.yHeadRot(), (float) v, yMaxRotSpeed)));
            getXRotD().ifPresent(v -> mob.setXRot(rotateTowards(mob.xRot(), (float) v, xMaxRotAngle)));
        } else {
            mob.setYHeadRot(rotateTowards(mob.yHeadRot(), mob.yBodyRot(), 10.0F));
        }
        clampHeadRotationToBody();
    }

    protected void clampHeadRotationToBody() {
        if (mob.navigationIsInProgress()) {
            mob.setYHeadRot(Mth.rotateIfNecessary(mob.yHeadRot(), mob.yBodyRot(), mob.getMaxHeadYRot()));
        }
    }

    protected boolean resetXRotOnTick() { return true; }

    public boolean isLookingAtTarget() { return lookAtCooldown > 0; }

    public double getWantedX() { return wantedX; }

    public double getWantedY() { return wantedY; }

    public double getWantedZ() { return wantedZ; }

    protected OptionalDouble getXRotD() {
        double dx = wantedX - mob.x(), dy = wantedY - mob.eyeY(), dz = wantedZ - mob.z();
        double h = Math.sqrt(dx * dx + dz * dz);
        return Math.abs(dy) > 1.0E-5F || Math.abs(h) > 1.0E-5F ? OptionalDouble.of((float) (-(Math.atan2(dy, h) * 180.0F / (float) Math.PI))) : OptionalDouble.empty();
    }

    protected OptionalDouble getYRotD() {
        double dx = wantedX - mob.x(), dz = wantedZ - mob.z();
        return Math.abs(dz) > 1.0E-5F || Math.abs(dx) > 1.0E-5F ? OptionalDouble.of((float) (Math.atan2(dz, dx) * 180.0F / (float) Math.PI) - 90.0F) : OptionalDouble.empty();
    }

    protected float rotateTowards(float from, float to, float max) {
        float diff = Mth.degreesDifference(from, to);
        float step = Math.clamp(diff, -max, max);
        return from + step;
    }

    private static double wantedY(Entity e) { return e instanceof LivingEntity le ? le.eyeY() : (e.boundingBox().minY() + e.boundingBox().maxY()) / 2.0; }
}
