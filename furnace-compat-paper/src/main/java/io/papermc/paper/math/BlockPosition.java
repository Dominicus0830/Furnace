package io.papermc.paper.math;

/** 정수 칸 좌표 위치(Paper {@code BlockPosition}). */
public interface BlockPosition extends Position {

    @Override
    int blockX();

    @Override
    int blockY();

    @Override
    int blockZ();

    @Override
    default double x() { return blockX(); }

    @Override
    default double y() { return blockY(); }

    @Override
    default double z() { return blockZ(); }

    @Override
    default boolean isBlock() { return true; }

    @Override
    default boolean isFine() { return false; }

    @Override
    default BlockPosition toBlock() { return this; }

    @Override
    default BlockPosition offset(int x, int y, int z) {
        return x == 0 && y == 0 && z == 0 ? this : new BlockPositionImpl(blockX() + x, blockY() + y, blockZ() + z);
    }

    @Override
    default FinePosition offset(double x, double y, double z) {
        return x == 0 && y == 0 && z == 0 ? toFine() : new FinePositionImpl(x() + x, y() + y, z() + z);
    }

    private FinePosition toFine() { return new FinePositionImpl(x(), y(), z()); }
}
