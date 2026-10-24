// 각도 계산(바닐라 Mth 의 필요한 부분).
package org.domi.furnace.server.entity.ai.control;

public final class Mth {
    private Mth() { }

    public static float wrapDegrees(float v) {
        float f = v % 360.0F;
        if (f >= 180.0F) f -= 360.0F;
        if (f < -180.0F) f += 360.0F;
        return f;
    }

    public static float degreesDifference(float a, float b) { return wrapDegrees(b - a); }

    /** Mth.rotateIfNecessary(from, to, max): to 에서 최대 max 도 안으로 from 을 끌어온 값. */
    public static float rotateIfNecessary(float from, float to, float max) {
        float diff = degreesDifference(from, to);
        float clamped = Math.clamp(diff, -max, max);
        return to - clamped;
    }
}
