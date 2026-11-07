// 바닐라 MoveControl: 가고 싶은 점(MOVE_TO)·옆걸음(STRAFE)을 받아 몹의 앞/옆 입력(zza/xxa)과 방향·점프를 만든다.
package org.domi.furnace.server.entity.ai.control;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Mob;

public class MoveControl {
    public static final float MIN_SPEED = 5.0E-4F, MIN_SPEED_SQR = 2.5000003E-7F;
    protected static final int MAX_TURN = 90;

    protected enum Operation { WAIT, MOVE_TO, STRAFE, JUMPING }

    protected final Mob mob;
    protected double wantedX, wantedY, wantedZ, speedModifier;
    protected float strafeForwards, strafeRight;
    protected Operation operation = Operation.WAIT;

    public MoveControl(Mob mob) { this.mob = mob; }

    public boolean hasWanted() { return operation == Operation.MOVE_TO; }

    public double getSpeedModifier() { return speedModifier; }

    public double getWantedX() { return wantedX; }

    public double getWantedY() { return wantedY; }

    public double getWantedZ() { return wantedZ; }

    public void setWantedPosition(double x, double y, double z, double speed) {
        wantedX = x;
        wantedY = y;
        wantedZ = z;
        speedModifier = speed;
        if (operation != Operation.JUMPING) operation = Operation.MOVE_TO;
    }

    public void strafe(float forward, float right) {
        operation = Operation.STRAFE;
        strafeForwards = forward;
        strafeRight = right;
        speedModifier = 0.25;
    }

    public void tick() {
        if (operation == Operation.STRAFE) {
            float speed = (float) mob.attributeValue(Attribute.MOVEMENT_SPEED);
            float f1 = (float) speedModifier * speed;
            float f2 = strafeForwards, f3 = strafeRight;
            float f4 = (float) Math.sqrt(f2 * f2 + f3 * f3);
            if (f4 < 1.0F) f4 = 1.0F;
            f4 = f1 / f4;
            f2 *= f4;
            f3 *= f4;
            float sin = Mth.sin(mob.yRot() * (float) (Math.PI / 180.0)), cos = Mth.cos(mob.yRot() * (float) (Math.PI / 180.0));
            float f7 = f2 * cos - f3 * sin, f8 = f3 * cos + f2 * sin;
            if (!isWalkable(f7, f8)) {
                strafeForwards = 1.0F;
                strafeRight = 0.0F;
            }
            mob.setSpeed(f1);
            mob.setZza(strafeForwards);
            mob.setXxa(strafeRight);
            operation = Operation.WAIT;
        } else if (operation == Operation.MOVE_TO) {
            operation = Operation.WAIT;
            double dx = wantedX - mob.x(), dz = wantedZ - mob.z(), dy = wantedY - mob.y();
            double d = dx * dx + dy * dy + dz * dz;
            if (d < MIN_SPEED_SQR) {
                mob.setZza(0.0F);
                return;
            }
            float target = (float) (Mth.atan2(dz, dx) * 180.0F / (float) Math.PI) - 90.0F;
            mob.setYRot(rotlerp(mob.yRot(), target, 90.0F));
            mob.setSpeed((float) (speedModifier * mob.attributeValue(Attribute.MOVEMENT_SPEED)));
            Pos pos = mob.blockPosition();
            int state = mob.level().getBlockState(pos);
            double[] b = B.INFO.collisionShape(state).bounds();
            boolean emptyShape = b == null;
            if (dy > mob.getStepHeight() && dx * dx + dz * dz < Math.max(1.0F, mob.bbWidth())
                    || !emptyShape && mob.y() < b[4] + pos.y() && !B.isTag(state, "minecraft:doors") && !B.isTag(state, "minecraft:fences")) {
                mob.getJumpControl().jump();
                operation = Operation.JUMPING;
            }
        } else if (operation == Operation.JUMPING) {
            mob.setSpeed((float) (speedModifier * mob.attributeValue(Attribute.MOVEMENT_SPEED)));
            if (mob.onGround()) operation = Operation.WAIT;
        } else {
            mob.setZza(0.0F);
        }
    }

    /** isWalkable: 내비게이션의 노드 평가기로, 몹 발밑 높이에서 그 만큼 옮긴 칸이 걸을 수 있는 칸(WALKABLE)인지. 내비게이션·평가기가 없으면 참. */
    private boolean isWalkable(float x, float z) {
        var nav = mob.getNavigation();
        if (nav != null) {
            var ev = nav.getNodeEvaluator();
            if (ev != null && ev.getPathType(mob, new Pos(Mth.floor(mob.x() + x), mob.blockY(), Mth.floor(mob.z() + z))) != org.domi.furnace.server.entity.ai.navigation.PathType.WALKABLE) return false;
        }
        return true;
    }

    protected float rotlerp(float from, float to, float max) {
        float diff = Mth.wrapDegrees(to - from);
        if (diff > max) diff = max;
        if (diff < -max) diff = -max;
        float r = from + diff;
        if (r < 0.0F) r += 360.0F;
        else if (r > 360.0F) r -= 360.0F;
        return r;
    }
}
