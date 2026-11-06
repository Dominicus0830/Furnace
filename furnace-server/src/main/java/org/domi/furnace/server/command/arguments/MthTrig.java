// 바닐라 Mth.sin/cos — 65536칸 사인 표(명령 로컬 좌표 계산이 바닐라와 같은 값이 나오게).
package org.domi.furnace.server.command.arguments;

final class MthTrig {

    private static final float[] SIN = new float[65536];

    static {
        for (int i = 0; i < SIN.length; i++) SIN[i] = (float) Math.sin(i * Math.PI * 2.0 / 65536.0);
    }

    private MthTrig() {}

    static float sin(float v) { return SIN[(int) (v * 10430.378f) & 65535]; }

    static float cos(float v) { return SIN[(int) (v * 10430.378f + 16384.0f) & 65535]; }
}
