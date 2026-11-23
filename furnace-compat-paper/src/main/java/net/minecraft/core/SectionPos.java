package net.minecraft.core;

import net.minecraft.world.level.ChunkPos;

/**
 * 섹션 좌표(16×16×16, Mojang 이름 shim, N0). 묶기(x 22비트 &lt;&lt;42, y 20비트, z 22비트 &lt;&lt;20)·섹션 안 상대 좌표 short(x&lt;&lt;8 | z&lt;&lt;4 | y)·
 * 블록 ↔ 섹션 좌표 변환은 바닐라 1.21.1 {@code kf} 바이트코드와 같다. 좌표 스트림(blocksInside·cube·aroundChunk·betweenClosedStream)과 주변 섹션 훑기(aroundAndAtBlockPos)는 영역 9 에서 채웠다. 엔티티·청크 객체를 받는 생성 메서드는 아직 없다(없는 멤버 — 호출 위치에서 실패).
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

    /** 이 섹션 안의 모든 블록 칸(x → y → z 순서). */
    public java.util.stream.Stream<BlockPos> blocksInside() {
        return BlockPos.betweenClosedStream(minBlockX(), minBlockY(), minBlockZ(), maxBlockX(), maxBlockY(), maxBlockZ());
    }

    /** 가운데 섹션 둘레 {@code radius} 안 정육면체의 섹션들. */
    public static java.util.stream.Stream<SectionPos> cube(SectionPos center, int radius) {
        return betweenClosedStream(center.x() - radius, center.y() - radius, center.z() - radius,
                center.x() + radius, center.y() + radius, center.z() + radius);
    }

    public static java.util.stream.Stream<SectionPos> aroundChunk(ChunkPos pos, int radius, int minSection, int maxSection) {
        return betweenClosedStream(pos.x - radius, minSection, pos.z - radius, pos.x + radius, maxSection - 1, pos.z + radius);
    }

    /** 상자 안 섹션을 x → y → z 순서로. 크기 추정은 int 곱셈 뒤 long 으로 넓힌다(바닐라와 같음). */
    public static java.util.stream.Stream<SectionPos> betweenClosedStream(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        long size = (long) ((maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1));
        return java.util.stream.StreamSupport.stream(new java.util.Spliterators.AbstractSpliterator<SectionPos>(size, 64) {
            final Cursor3D cursor = new Cursor3D(minX, minY, minZ, maxX, maxY, maxZ);

            @Override
            public boolean tryAdvance(java.util.function.Consumer<? super SectionPos> consumer) {
                if (!this.cursor.advance()) return false;
                consumer.accept(SectionPos.of(this.cursor.nextX(), this.cursor.nextY(), this.cursor.nextZ()));
                return true;
            }
        }, false);
    }

    public static void aroundAndAtBlockPos(BlockPos pos, it.unimi.dsi.fastutil.longs.LongConsumer consumer) {
        aroundAndAtBlockPos(pos.getX(), pos.getY(), pos.getZ(), consumer);
    }

    public static void aroundAndAtBlockPos(long packed, it.unimi.dsi.fastutil.longs.LongConsumer consumer) {
        aroundAndAtBlockPos(BlockPos.getX(packed), BlockPos.getY(packed), BlockPos.getZ(packed), consumer);
    }

    /** 블록 칸 둘레 ±1 이 걸치는 섹션들의 묶음 좌표를 x → y → z 순서로(한 섹션뿐이면 그것 하나). */
    public static void aroundAndAtBlockPos(int x, int y, int z, it.unimi.dsi.fastutil.longs.LongConsumer consumer) {
        int minX = blockToSectionCoord(x - 1);
        int maxX = blockToSectionCoord(x + 1);
        int minY = blockToSectionCoord(y - 1);
        int maxY = blockToSectionCoord(y + 1);
        int minZ = blockToSectionCoord(z - 1);
        int maxZ = blockToSectionCoord(z + 1);
        if (minX == maxX && minY == maxY && minZ == maxZ) {
            consumer.accept(asLong(minX, minY, minZ));
        } else {
            for (int sx = minX; sx <= maxX; sx++) {
                for (int sy = minY; sy <= maxY; sy++) {
                    for (int sz = minZ; sz <= maxZ; sz++) consumer.accept(asLong(sx, sy, sz));
                }
            }
        }
    }
}
