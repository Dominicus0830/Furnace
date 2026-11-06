// 블록 좌표(바닐라 BlockPos, 불변).
package org.domi.furnace.server.block;

public record Pos(int x, int y, int z) {

    public static final Pos ZERO = new Pos(0, 0, 0);

    public Pos relative(int dir) { return new Pos(x + Dir.DX[dir], y + Dir.DY[dir], z + Dir.DZ[dir]); }

    public Pos relative(int dir, int n) { return new Pos(x + Dir.DX[dir] * n, y + Dir.DY[dir] * n, z + Dir.DZ[dir] * n); }

    public Pos above() { return new Pos(x, y + 1, z); }

    public Pos above(int n) { return new Pos(x, y + n, z); }

    public Pos below() { return new Pos(x, y - 1, z); }

    public Pos below(int n) { return new Pos(x, y - n, z); }

    public Pos north() { return new Pos(x, y, z - 1); }

    public Pos south() { return new Pos(x, y, z + 1); }

    public Pos west() { return new Pos(x - 1, y, z); }

    public Pos east() { return new Pos(x + 1, y, z); }

    public Pos offset(int dx, int dy, int dz) { return new Pos(x + dx, y + dy, z + dz); }

    /** BlockPos.asLong. */
    public long asLong() { return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (long) y & 0xFFF; }

    public static Pos of(long packed) {
        return new Pos((int) (packed >> 38), (int) (packed << 52 >> 52), (int) (packed << 26 >> 38));
    }

    /** distManhattan. */
    public int distManhattan(Pos o) { return Math.abs(x - o.x) + Math.abs(y - o.y) + Math.abs(z - o.z); }

    /** BlockPos.toShortString. */
    public String toShortString() { return x + ", " + y + ", " + z; }
}
