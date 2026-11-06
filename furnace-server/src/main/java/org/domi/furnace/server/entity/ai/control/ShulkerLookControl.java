// 바닐라 Shulker.ShulkerLookControl: 붙은 면에 맞춰 머리 방향을 구한다(몸에 맞춘 제한은 없음).
package org.domi.furnace.server.entity.ai.control;

import java.util.OptionalDouble;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Shulker;

public class ShulkerLookControl extends LookControl {
    public ShulkerLookControl(Mob mob) { super(mob); }

    @Override protected void clampHeadRotationToBody() { }

    @Override
    protected OptionalDouble getYRotD() {
        int opposite = Dir.opposite(((Shulker) mob).getAttachFace());
        // opposite.getRotation() 으로 (0,0,1) 을 돌린 벡터: 위 (0,0,1) · 아래 (0,0,-1) · 옆면 (0,-1,0)
        float rx = 0.0F, ry = 0.0F, rz = 0.0F;
        if (opposite == Dir.UP) rz = 1.0F;
        else if (opposite == Dir.DOWN) rz = -1.0F;
        else ry = -1.0F;
        float nx = Dir.DX[opposite], ny = Dir.DY[opposite], nz = Dir.DZ[opposite];
        // cross = normal × rotated
        float cx = ny * rz - nz * ry, cy = nz * rx - nx * rz, cz = nx * ry - ny * rx;
        float dx = (float) (wantedX - mob.x()), dy = (float) (wantedY - mob.eyeY()), dz = (float) (wantedZ - mob.z());
        float a = cx * dx + cy * dy + cz * dz;
        float b = rx * dx + ry * dy + rz * dz;
        return !(Math.abs(a) > 1.0E-5F) && !(Math.abs(b) > 1.0E-5F) ? OptionalDouble.empty() : OptionalDouble.of((float) (Mth.atan2(-a, b) * 180.0F / (float) Math.PI));
    }

    @Override protected OptionalDouble getXRotD() { return OptionalDouble.of(0.0F); }
}
