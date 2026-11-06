// 블록 면(바닐라 Direction 순서: 아래·위·북·남·서·동).
package org.domi.furnace.api.world;

public enum BlockFace {
    DOWN(0, -1, 0), UP(0, 1, 0), NORTH(0, 0, -1), SOUTH(0, 0, 1), WEST(-1, 0, 0), EAST(1, 0, 0);

    private final int dx, dy, dz;

    BlockFace(int dx, int dy, int dz) {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
    }

    public int dx() { return dx; }

    public int dy() { return dy; }

    public int dz() { return dz; }

    /** 이 면 쪽 이웃 칸. */
    public BlockPos relative(BlockPos pos) { return pos.offset(dx, dy, dz); }
}
