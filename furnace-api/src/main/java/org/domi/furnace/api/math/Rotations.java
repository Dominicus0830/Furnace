// 세 축 각도(도) — 갑옷 거치대 머리·몸·팔·다리 자세(바닐라 Rotations). 불변.
package org.domi.furnace.api.math;

public record Rotations(float x, float y, float z) {

    public static final Rotations ZERO = new Rotations(0, 0, 0);
}
