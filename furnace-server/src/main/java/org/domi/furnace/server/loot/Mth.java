// 바닐라 Mth 중 전리품이 쓰는 것 — 난수 호출 유무까지 같아야 한다(min >= max 면 난수를 쓰지 않는다).
package org.domi.furnace.server.loot;

import org.domi.furnace.server.world.gen.noise.NoiseRandom;

final class Mth {
    private Mth() {}

    static int nextInt(NoiseRandom r, int min, int max) { return min >= max ? min : r.nextInt(max - min + 1) + min; }

    static float nextFloat(NoiseRandom r, float min, float max) { return min >= max ? min : r.nextFloat() * (max - min) + min; }

    static int floor(float f) { int i = (int) f; return f < i ? i - 1 : i; }

    static int clamp(int v, int lo, int hi) { return Math.min(Math.max(v, lo), hi); }

    static float clamp(float v, float lo, float hi) { return v < lo ? lo : Math.min(v, hi); }

    /** 바닐라 Util.shuffle. */
    static <T> void shuffle(java.util.List<T> list, NoiseRandom r) {
        for (int i = list.size(); i > 1; i--) {
            int j = r.nextInt(i);
            list.set(i - 1, list.set(j, list.get(i - 1)));
        }
    }
}
