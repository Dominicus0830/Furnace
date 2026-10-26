// 바닐라 Vec3 의 필요한 부분(탑승 부착점 계산용).
package org.domi.furnace.server.entity;

import org.domi.furnace.server.entity.ai.control.Mth;

public record Vec3(double x, double y, double z) {

    public static final Vec3 ZERO = new Vec3(0.0, 0.0, 0.0);

    public Vec3 add(Vec3 o) { return new Vec3(x + o.x, y + o.y, z + o.z); }

    public Vec3 add(double dx, double dy, double dz) { return new Vec3(x + dx, y + dy, z + dz); }

    public Vec3 scale(double s) { return new Vec3(x * s, y * s, z * s); }

    /** Vec3.multiply(sx, sy, sz). */
    public Vec3 multiply(double sx, double sy, double sz) { return new Vec3(x * sx, y * sy, z * sz); }

    /** Vec3.yRot(rad): Mth 표로 y 축 회전. */
    public Vec3 yRot(float rad) {
        float c = Mth.cos(rad), s = Mth.sin(rad);
        return new Vec3(x * c + z * s, y, z * c - x * s);
    }
}
