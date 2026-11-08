package net.minecraft.world.level;

import net.minecraft.core.BlockPos;

/**
 * 청크 좌표(Mojang 이름 shim, H7b, 등급 A, 불변). long 묶기(x 아래 32비트, z 위 32비트)·hashCode(1664525 선형 합동 섞기)·toString
 * ({@code [x, z]})·region 좌표는 바닐라 1.21.1 바이트코드와 같다.
 */
public class ChunkPos {

    public static final long INVALID_CHUNK_POS = asLong(1875066, 1875066);
    public static final ChunkPos ZERO = new ChunkPos(0, 0);

    public final int x;
    public final int z;

    public ChunkPos(int x, int z) {
        this.x = x;
        this.z = z;
    }

    public ChunkPos(BlockPos p) {
        this.x = p.getX() >> 4;
        this.z = p.getZ() >> 4;
    }

    public ChunkPos(long packed) {
        this.x = (int) packed;
        this.z = (int) (packed >> 32);
    }

    public static ChunkPos minFromRegion(int rx, int rz) { return new ChunkPos(rx << 5, rz << 5); }

    public static ChunkPos maxFromRegion(int rx, int rz) { return new ChunkPos((rx << 5) + 31, (rz << 5) + 31); }

    public long toLong() { return asLong(x, z); }

    public static long asLong(int x, int z) { return (long) x & 4294967295L | ((long) z & 4294967295L) << 32; }

    public static long asLong(BlockPos p) { return asLong(p.getX() >> 4, p.getZ() >> 4); }

    public static int getX(long packed) { return (int) (packed & 4294967295L); }

    public static int getZ(long packed) { return (int) (packed >>> 32 & 4294967295L); }

    @Override
    public int hashCode() { return hash(x, z); }

    public static int hash(int x, int z) {
        int i = 1664525 * x + 1013904223;
        int j = 1664525 * (z ^ -559038737) + 1013904223;
        return i ^ j;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof ChunkPos c && x == c.x && z == c.z;
    }

    public int getMiddleBlockX() { return getBlockX(8); }

    public int getMiddleBlockZ() { return getBlockZ(8); }

    public int getMinBlockX() { return x << 4; }

    public int getMinBlockZ() { return z << 4; }

    public int getMaxBlockX() { return getBlockX(15); }

    public int getMaxBlockZ() { return getBlockZ(15); }

    public int getRegionX() { return x >> 5; }

    public int getRegionZ() { return z >> 5; }

    public int getRegionLocalX() { return x & 31; }

    public int getRegionLocalZ() { return z & 31; }

    public BlockPos getBlockAt(int dx, int y, int dz) { return new BlockPos(getBlockX(dx), y, getBlockZ(dz)); }

    public int getBlockX(int i) { return (x << 4) + i; }

    public int getBlockZ(int i) { return (z << 4) + i; }

    public BlockPos getMiddleBlockPosition(int y) { return new BlockPos(getMiddleBlockX(), y, getMiddleBlockZ()); }

    public BlockPos getWorldPosition() { return new BlockPos(getMinBlockX(), 0, getMinBlockZ()); }

    public int getChessboardDistance(ChunkPos o) { return Math.max(Math.abs(x - o.x), Math.abs(z - o.z)); }

    public int distanceSquared(ChunkPos o) { return distanceSquared(o.x, o.z); }

    public int distanceSquared(long packed) { return distanceSquared(getX(packed), getZ(packed)); }

    private int distanceSquared(int ox, int oz) {
        int dx = ox - x, dz = oz - z;
        return dx * dx + dz * dz;
    }

    @Override
    public String toString() { return "[" + x + ", " + z + "]"; }
}
