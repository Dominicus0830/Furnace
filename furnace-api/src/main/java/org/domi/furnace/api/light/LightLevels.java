// 한 칸의 저장된 빛 값: 블록빛(발광에서 번진 빛)과 하늘빛. 둘 다 0..15.
package org.domi.furnace.api.light;

public record LightLevels(int block, int sky) {
    /** 낮(하늘 어둠 0) 기준 밝기 — 둘 중 큰 값. */
    public int max() { return Math.max(block, sky); }
}
