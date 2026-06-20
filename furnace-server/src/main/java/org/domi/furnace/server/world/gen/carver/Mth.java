// 바닐라 Mth.sin/cos 룩업 테이블 포팅 — carver 경로 계산에 비트정확 삼각함수 사용
package org.domi.furnace.server.world.gen.carver;

public final class Mth {

    private static final float[] SIN;

    static {
        SIN = new float[65536];
        for (int i = 0; i < 65536; i++) {
            SIN[i] = (float) Math.sin((double) i * Math.PI * 2.0 / 65536.0); // vanilla: double precision
        }
    }

    private Mth() {}

    public static float sin(float value) {
        return SIN[(int)(value * 10430.378f) & 65535];
    }

    public static float cos(float value) {
        return SIN[(int)(value * 10430.378f + 16384.0f) & 65535];
    }
}
