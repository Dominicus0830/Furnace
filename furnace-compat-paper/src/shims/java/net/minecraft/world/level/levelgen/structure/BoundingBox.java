package net.minecraft.world.level.levelgen.structure;

import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;

/**
 * 구조물 상자(Mojang 이름 shim, T-211 2단계) — 바닐라 1.21.1 그대로: 양 끝 칸을 포함하는 정수 상자. 직렬화 코덱은 없다.
 * 거꾸로 된 경계는 바닐라처럼 오류 로그를 남기고 두 끝을 맞바꾼다.
 */
class BoundingBox {

    private int minX;
    private int minY;
    private int minZ;
    private int maxX;
    private int maxY;
    private int maxZ;

    public BoundingBox(BlockPos pos) {
        this(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ());
    }

    public BoundingBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        if (maxX < minX || maxY < minY || maxZ < minZ) {
            org.slf4j.LoggerFactory.getLogger(BoundingBox.class).error("Invalid bounding box data, inverted bounds for: " + this);
            this.minX = Math.min(minX, maxX);
            this.minY = Math.min(minY, maxY);
            this.minZ = Math.min(minZ, maxZ);
            this.maxX = Math.max(minX, maxX);
            this.maxY = Math.max(minY, maxY);
            this.maxZ = Math.max(minZ, maxZ);
        }
    }

    public static BoundingBox fromCorners(Vec3i first, Vec3i second) {
        return new BoundingBox(Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()), Math.min(first.getZ(), second.getZ()),
                Math.max(first.getX(), second.getX()), Math.max(first.getY(), second.getY()), Math.max(first.getZ(), second.getZ()));
    }

    public static BoundingBox infinite() {
        return new BoundingBox(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    public static BoundingBox orientBox(int x, int y, int z, int offsetX, int offsetY, int offsetZ, int sizeX, int sizeY, int sizeZ, Direction facing) {
        return switch (facing) {
            case NORTH -> new BoundingBox(x + offsetX, y + offsetY, z - sizeZ + 1 + offsetZ, x + sizeX - 1 + offsetX, y + sizeY - 1 + offsetY, z + offsetZ);
            case WEST -> new BoundingBox(x - sizeZ + 1 + offsetZ, y + offsetY, z + offsetX, x + offsetZ, y + sizeY - 1 + offsetY, z + sizeX - 1 + offsetX);
            case EAST -> new BoundingBox(x + offsetZ, y + offsetY, z + offsetX, x + sizeZ - 1 + offsetZ, y + sizeY - 1 + offsetY, z + sizeX - 1 + offsetX);
            default -> new BoundingBox(x + offsetX, y + offsetY, z + offsetZ, x + sizeX - 1 + offsetX, y + sizeY - 1 + offsetY, z + sizeZ - 1 + offsetZ);
        };
    }

    public Stream<ChunkPos> intersectingChunks() {
        return ChunkPos.rangeClosed(new ChunkPos(SectionPos.blockToSectionCoord(minX()), SectionPos.blockToSectionCoord(minZ())),
                new ChunkPos(SectionPos.blockToSectionCoord(maxX()), SectionPos.blockToSectionCoord(maxZ())));
    }

    public boolean intersects(BoundingBox other) {
        return maxX >= other.minX && minX <= other.maxX && maxZ >= other.minZ && minZ <= other.maxZ && maxY >= other.minY && minY <= other.maxY;
    }

    public boolean intersects(int minX, int minZ, int maxX, int maxZ) {
        return this.maxX >= minX && this.minX <= maxX && this.maxZ >= minZ && this.minZ <= maxZ;
    }

    public static Optional<BoundingBox> encapsulatingPositions(Iterable<BlockPos> positions) {
        Iterator<BlockPos> it = positions.iterator();
        if (!it.hasNext()) return Optional.empty();
        BoundingBox box = new BoundingBox(it.next());
        it.forEachRemaining(box::encapsulate);
        return Optional.of(box);
    }

    public static Optional<BoundingBox> encapsulatingBoxes(Iterable<BoundingBox> boxes) {
        Iterator<BoundingBox> it = boxes.iterator();
        if (!it.hasNext()) return Optional.empty();
        BoundingBox first = it.next();
        BoundingBox box = new BoundingBox(first.minX, first.minY, first.minZ, first.maxX, first.maxY, first.maxZ);
        it.forEachRemaining(box::encapsulate);
        return Optional.of(box);
    }

    @Deprecated
    public BoundingBox encapsulate(BoundingBox box) {
        minX = Math.min(minX, box.minX);
        minY = Math.min(minY, box.minY);
        minZ = Math.min(minZ, box.minZ);
        maxX = Math.max(maxX, box.maxX);
        maxY = Math.max(maxY, box.maxY);
        maxZ = Math.max(maxZ, box.maxZ);
        return this;
    }

    @Deprecated
    public BoundingBox encapsulate(BlockPos pos) {
        minX = Math.min(minX, pos.getX());
        minY = Math.min(minY, pos.getY());
        minZ = Math.min(minZ, pos.getZ());
        maxX = Math.max(maxX, pos.getX());
        maxY = Math.max(maxY, pos.getY());
        maxZ = Math.max(maxZ, pos.getZ());
        return this;
    }

    @Deprecated
    public BoundingBox move(int dx, int dy, int dz) {
        minX += dx;
        minY += dy;
        minZ += dz;
        maxX += dx;
        maxY += dy;
        maxZ += dz;
        return this;
    }

    @Deprecated
    public BoundingBox move(Vec3i vec) { return move(vec.getX(), vec.getY(), vec.getZ()); }

    public BoundingBox moved(int x, int y, int z) { return new BoundingBox(minX + x, minY + y, minZ + z, maxX + x, maxY + y, maxZ + z); }

    public BoundingBox inflatedBy(int offset) { return inflatedBy(offset, offset, offset); }

    public BoundingBox inflatedBy(int x, int y, int z) {
        return new BoundingBox(minX() - x, minY() - y, minZ() - z, maxX() + x, maxY() + y, maxZ() + z);
    }

    public boolean isInside(Vec3i pos) { return isInside(pos.getX(), pos.getY(), pos.getZ()); }

    public boolean isInside(int x, int y, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ && y >= minY && y <= maxY;
    }

    public Vec3i getLength() { return new Vec3i(maxX - minX, maxY - minY, maxZ - minZ); }

    public int getXSpan() { return maxX - minX + 1; }

    public int getYSpan() { return maxY - minY + 1; }

    public int getZSpan() { return maxZ - minZ + 1; }

    public BlockPos getCenter() {
        return new BlockPos(minX + (maxX - minX + 1) / 2, minY + (maxY - minY + 1) / 2, minZ + (maxZ - minZ + 1) / 2);
    }

    public void forAllCorners(Consumer<BlockPos> consumer) {
        consumer.accept(new BlockPos(maxX, maxY, maxZ));
        consumer.accept(new BlockPos(minX, maxY, maxZ));
        consumer.accept(new BlockPos(maxX, minY, maxZ));
        consumer.accept(new BlockPos(minX, minY, maxZ));
        consumer.accept(new BlockPos(maxX, maxY, minZ));
        consumer.accept(new BlockPos(minX, maxY, minZ));
        consumer.accept(new BlockPos(maxX, minY, minZ));
        consumer.accept(new BlockPos(minX, minY, minZ));
    }

    @Override
    public String toString() {
        return "BoundingBox{minX=" + minX + ", minY=" + minY + ", minZ=" + minZ + ", maxX=" + maxX + ", maxY=" + maxY + ", maxZ=" + maxZ + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof BoundingBox b && minX == b.minX && minY == b.minY && minZ == b.minZ && maxX == b.maxX && maxY == b.maxY && maxZ == b.maxZ;
    }

    @Override
    public int hashCode() { return Objects.hash(minX, minY, minZ, maxX, maxY, maxZ); }

    public int minX() { return minX; }

    public int minY() { return minY; }

    public int minZ() { return minZ; }

    public int maxX() { return maxX; }

    public int maxY() { return maxY; }

    public int maxZ() { return maxZ; }
}
