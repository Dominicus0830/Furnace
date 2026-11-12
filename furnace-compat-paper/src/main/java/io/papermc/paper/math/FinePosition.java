package io.papermc.paper.math;

/** 실수 좌표 위치(Paper {@code FinePosition}). {@code Location} 이 구현한다. */
public interface FinePosition extends Position {

    @Override
    default boolean isBlock() { return false; }

    @Override
    default boolean isFine() { return true; }

    @Override
    default FinePosition offset(double x, double y, double z) {
        return x == 0 && y == 0 && z == 0 ? this : new FinePositionImpl(x() + x, y() + y, z() + z);
    }

    @Override
    default FinePosition offset(int x, int y, int z) { return offset((double) x, (double) y, (double) z); }
}
