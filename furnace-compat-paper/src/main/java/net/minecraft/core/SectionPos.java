package net.minecraft.core;

import net.minecraft.world.level.ChunkPos;

/**
 * 섹션 좌표(16×16×16, Mojang 이름 shim, N0). 묶기(x 22비트 &lt;&lt;42, y 20비트, z 22비트 &lt;&lt;20)·섹션 안 상대 좌표 short(x&lt;&lt;8 | z&lt;&lt;4 | y)·
 * 블록 ↔ 섹션 좌표 변환은 바닐라 1.21.1 {@code kf} 바이트코드와 같다. 엔티티·청크 객체를 받는 생성 메서드와 좌표 스트림(blocksInside·cube·betweenClosedStream)은 아직 없다(없는 멤버 — 호출 위치에서 실패).
 */
public class SectionPos extends Vec3i {

    public static final int SECTION_BITS = 4;
    public static final int SECTION_SIZE = 16;
    public static final int SECTION_MASK = 15;
    public static final int SECTION_HALF_SIZE = 8;
    public static final int SECTION_MAX_INDEX = 15;

    SectionPos(int x, int y, int z) { super(x, y, z); }

    public static SectionPos of(int x, int y, int z) { return new SectionPos(x, y, z); }

    public static SectionPos of(BlockPos pos) { return new SectionPos(blockToSectionCoord(pos.getX()), blockToSectionCoord(pos.getY()), blockToSectionCoord(pos.getZ())); }

    public static SectionPos of(ChunkPos chunk, int y) { return new SectionPos(chunk.x, y, chunk.z); }

    public static SectionPos of(Position pos) { return new SectionPos(blockToSectionCoord(pos.x()), blockToSectionCoord(pos.y()), blockToSectionCoord(pos.z())); }

    public static SectionPos of(long packed) { return new SectionPos(x(packed), y(packed), z(packed)); }

    public static long offset(long packed, Direction d) { return offset(packed, d.getStepX(), d.getStepY(), d.getStepZ()); }

    public static long offset(long packed, int dx, int dy, int dz) { return asLong(x(packed) + dx, y(packed) + dy, z(packed) + dz); }

    private static int floor(double d) {
        int i = (int) d;
        return d < i ? i - 1 : i;
    }

    public static int posToSectionCoord(double coord) { return blockToSectionCoord(floor(coord)); }

    public static int blockToSectionCoord(int coord) { return coord >> 4; }

    public static int blockToSectionCoord(double coord) { return floor(coord) >> 4; }

    public static int sectionRelative(int coord) { return coord & 15; }

    public static short sectionRelativePos(BlockPos pos) {
        int x = sectionRelative(pos.getX());
        int y = sectionRelative(pos.getY());
        int z = sectionRelative(pos.getZ());
        return (short) (x << 8 | z << 4 | y);
    }

    public static int sectionRelativeX(short rel) { return rel >>> 8 & 15; }

    public static int sectionRelativeY(short rel) { return rel & 15; }

    public static int sectionRelativeZ(short rel) { return rel >>> 4 & 15; }

    public int relativeToBlockX(short rel) { return minBlockX() + sectionRelativeX(rel); }

    public int relativeToBlockY(short rel) { return minBlockY() + sectionRelativeY(rel); }

    public int relativeToBlockZ(short rel) { return minBlockZ() + sectionRelativeZ(rel); }

    public BlockPos relativeToBlockPos(short rel) { return new BlockPos(relativeToBlockX(rel), relativeToBlockY(rel), relativeToBlockZ(rel)); }

    public static int sectionToBlockCoord(int section) { return section << 4; }

    public static int sectionToBlockCoord(int section, int offset) { return sectionToBlockCoord(section) + offset; }

    public static int x(long packed) { return (int) (packed << 0 >> 42); }

    public static int y(long packed) { return (int) (packed << 44 >> 44); }

    public static int z(long packed) { return (int) (packed << 22 >> 42); }

    public int x() { return getX(); }

    public int y() { return getY(); }

    public int z() { return getZ(); }

    public int minBlockX() { return sectionToBlockCoord(x()); }

    public int minBlockY() { return sectionToBlockCoord(y()); }

    public int minBlockZ() { return sectionToBlockCoord(z()); }

    public int maxBlockX() { return sectionToBlockCoord(x(), 15); }

    public int maxBlockY() { return sectionToBlockCoord(y(), 15); }

    public int maxBlockZ() { return sectionToBlockCoord(z(), 15); }

    public static long blockToSection(long blockPos) {
        return asLong(blockToSectionCoord(BlockPos.getX(blockPos)), blockToSectionCoord(BlockPos.getY(blockPos)), blockToSectionCoord(BlockPos.getZ(blockPos)));
    }

    public static long getZeroNode(int x, int z) { return getZeroNode(asLong(x, 0, z)); }

    public static long getZeroNode(long packed) { return packed & -1048576L; }

    public BlockPos origin() { return new BlockPos(sectionToBlockCoord(x()), sectionToBlockCoord(y()), sectionToBlockCoord(z())); }

    public BlockPos center() { return origin().offset(8, 8, 8); }

    public ChunkPos chunk() { return new ChunkPos(x(), z()); }

    public static long asLong(BlockPos pos) { return asLong(blockToSectionCoord(pos.getX()), blockToSectionCoord(pos.getY()), blockToSectionCoord(pos.getZ())); }

    public static long asLong(int x, int y, int z) {
        long l = 0L;
        l |= ((long) x & 4194303L) << 42;
        l |= ((long) y & 1048575L) << 0;
        return l | ((long) z & 4194303L) << 20;
    }

    public long asLong() { return asLong(x(), y(), z()); }

    @Override
    public SectionPos offset(int dx, int dy, int dz) { return dx == 0 && dy == 0 && dz == 0 ? this : new SectionPos(x() + dx, y() + dy, z() + dz); }
}
