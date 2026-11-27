package net.minecraft.world.phys;

import net.minecraft.util.Mth;

/** 실수 2차원 벡터(Mojang 이름 shim, 바닐라 1.21.1 바이트코드와 같은 멤버·식). 명령 출처의 회전(x=피치, y=요)에 쓴다. */
public class Vec2 {

    public static final Vec2 ZERO = new Vec2(0.0F, 0.0F);
    public static final Vec2 ONE = new Vec2(1.0F, 1.0F);
    public static final Vec2 UNIT_X = new Vec2(1.0F, 0.0F);
    public static final Vec2 NEG_UNIT_X = new Vec2(-1.0F, 0.0F);
    public static final Vec2 UNIT_Y = new Vec2(0.0F, 1.0F);
    public static final Vec2 NEG_UNIT_Y = new Vec2(0.0F, -1.0F);
    public static final Vec2 MAX = new Vec2(Float.MAX_VALUE, Float.MAX_VALUE);
    public static final Vec2 MIN = new Vec2(Float.MIN_VALUE, Float.MIN_VALUE);

    public final float x;
    public final float y;

    public Vec2(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public Vec2 scale(float f) { return new Vec2(this.x * f, this.y * f); }

    public float dot(Vec2 o) { return this.x * o.x + this.y * o.y; }

    public Vec2 add(Vec2 o) { return new Vec2(this.x + o.x, this.y + o.y); }

    public Vec2 add(float f) { return new Vec2(this.x + f, this.y + f); }

    public boolean equals(Vec2 o) { return this.x == o.x && this.y == o.y; }

    public Vec2 normalized() {
        float f = Mth.sqrt(this.x * this.x + this.y * this.y);
        return f < 1.0E-4F ? ZERO : new Vec2(this.x / f, this.y / f);
    }

    public float length() { return Mth.sqrt(this.x * this.x + this.y * this.y); }

    public float lengthSquared() { return this.x * this.x + this.y * this.y; }

    public float distanceToSqr(Vec2 o) {
        float dx = o.x - this.x;
        float dy = o.y - this.y;
        return dx * dx + dy * dy;
    }

    public Vec2 negated() { return new Vec2(-this.x, -this.y); }
}
