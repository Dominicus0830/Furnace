// 바닐라 LongJumpUtil: 목표 지점까지 포물선으로 뛰기 위한 속도 벡터(각도 지정, 선택적으로 경로 충돌 검사).
package org.domi.furnace.server.entity.ai.behavior;

import java.util.Optional;
import org.domi.furnace.server.entity.AABB;
import org.domi.furnace.server.entity.PathfinderMob;
import org.domi.furnace.server.attribute.Attribute;

public final class LongJumpUtil {
    private LongJumpUtil() {}

    public static Optional<double[]> calculateJumpVectorForAngle(PathfinderMob mob, double[] target, float maxVelocity, int angleDeg, boolean checkCollision) {
        double mx = mob.x(), my = mob.y(), mz = mob.z();
        double dirX = target[0] - mx, dirZ = target[2] - mz;
        double dl = Math.sqrt(dirX * dirX + dirZ * dirZ);
        double nx = dl < 1.0E-5F ? 0.0 : dirX / dl * 0.5, nz = dl < 1.0E-5F ? 0.0 : dirZ / dl * 0.5;
        double ax = target[0] - nx, ay = target[1], az = target[2] - nz;
        double dx = ax - mx, dy = ay - my, dz = az - mz;
        float angle = angleDeg * (float) Math.PI / 180.0F;
        double horizAngle = Math.atan2(dz, dx);
        double horizSqr = dx * dx + dz * dz;
        double horiz = Math.sqrt(horizSqr);
        double gravity = mob.attributeValue(Attribute.GRAVITY);
        double sin2a = Math.sin(2.0F * angle);
        double cos2a = Math.pow(Math.cos(angle), 2.0);
        double sina = Math.sin(angle);
        double cosa = Math.cos(angle);
        double sinH = Math.sin(horizAngle);
        double cosH = Math.cos(horizAngle);
        double v2 = horizSqr * gravity / (horiz * sin2a - 2.0 * dy * cos2a);
        if (v2 < 0.0) return Optional.empty();
        double v = Math.sqrt(v2);
        if (v > maxVelocity) return Optional.empty();
        double vh = v * cosa, vy = v * sina;
        if (checkCollision) {
            int steps = (int) Math.ceil(horiz / vh) * 2;
            double d = 0.0;
            double[] prev = null;
            for (int i = 0; i < steps - 1; i++) {
                d += horiz / steps;
                double h = sina / cosa * d - Math.pow(d, 2.0) * gravity / (2.0 * v2 * Math.pow(cosa, 2.0));
                double[] p = {mx + d * cosH, my + h, mz + d * sinH};
                if (prev != null && !isClearTransition(mob, prev, p)) return Optional.empty();
                prev = p;
            }
        }
        return Optional.of(new double[] {vh * cosH * 0.95F, vy * 0.95F, vh * sinH * 0.95F});
    }

    private static boolean isClearTransition(PathfinderMob mob, double[] from, double[] to) {
        double dx = to[0] - from[0], dy = to[1] - from[1], dz = to[2] - from[2];
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double d = Math.min(mob.bbWidth(), mob.bbHeight());
        int steps = (int) Math.ceil(len / d);
        double ux = len < 1.0E-5F ? 0.0 : dx / len, uy = len < 1.0E-5F ? 0.0 : dy / len, uz = len < 1.0E-5F ? 0.0 : dz / len;
        double[] cur = from;
        for (int i = 0; i < steps; i++) {
            cur = i == steps - 1 ? to : new double[] {cur[0] + ux * d * 0.9F, cur[1] + uy * d * 0.9F, cur[2] + uz * d * 0.9F};
            float hw = mob.bbWidth() / 2.0F;
            AABB box = new AABB(cur[0] - hw, cur[1], cur[2] - hw, cur[0] + hw, cur[1] + mob.bbHeight(), cur[2] + hw);
            if (!mob.level().noCollision(mob, box)) return false;
        }
        return true;
    }
}
