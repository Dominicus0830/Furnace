// 바닐라 CarvingMask 포팅 — 중복 carving 방지용 비트셋
package org.domi.furnace.server.world.gen.carver;

public final class CarvingMask {

    private final boolean[] carved;
    private final int minY;

    public CarvingMask(int minY, int height) {
        this.minY   = minY;
        this.carved = new boolean[height * 16 * 16];
    }

    private int index(int lx, int y, int lz) {
        return (y - minY) * 256 + lz * 16 + lx;
    }

    public boolean get(int lx, int y, int lz) {
        int idx = index(lx, y, lz);
        return idx >= 0 && idx < carved.length && carved[idx];
    }

    public void set(int lx, int y, int lz) {
        int idx = index(lx, y, lz);
        if (idx >= 0 && idx < carved.length) carved[idx] = true;
    }
}
