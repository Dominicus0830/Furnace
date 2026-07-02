// 구조물 조각 회전용 수평 방향 — 바닐라 Direction.Plane.HORIZONTAL(NORTH,EAST,SOUTH,WEST) 순서 재현(RNG 인덱스 일치).
package org.domi.furnace.server.world.gen.structure;

import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public enum Direction {
    NORTH, EAST, SOUTH, WEST;

    private static final Direction[] HORIZONTAL = {NORTH, EAST, SOUTH, WEST};

    /** 바닐라 Direction.Plane.HORIZONTAL.getRandomDirection — random.nextInt(4) 1회 소비. */
    public static Direction randomHorizontal(WorldgenRandom random) {
        return HORIZONTAL[random.nextInt(4)];
    }

    /** west/east 는 X축 방향(=바닐라 orientBox의 axis 판정과 동일). */
    public boolean isXAxis() {
        return this == WEST || this == EAST;
    }

    /** 바닐라 Direction.get2DDataValue() — SOUTH=0,WEST=1,NORTH=2,EAST=3. */
    public int get2DDataValue() {
        return switch (this) {
            case SOUTH -> 0;
            case WEST -> 1;
            case NORTH -> 2;
            case EAST -> 3;
        };
    }

    /** 바닐라 Direction.getStepX(). */
    public int stepX() {
        return this == WEST ? -1 : this == EAST ? 1 : 0;
    }

    /** 바닐라 Direction.getStepZ(). */
    public int stepZ() {
        return this == NORTH ? -1 : this == SOUTH ? 1 : 0;
    }

    /** 바닐라 Direction.Plane.HORIZONTAL 순회 순서(선언 순서) — NORTH,SOUTH,WEST,EAST. */
    public static final Direction[] PLANE_HORIZONTAL_ORDER = {NORTH, SOUTH, WEST, EAST};
}
