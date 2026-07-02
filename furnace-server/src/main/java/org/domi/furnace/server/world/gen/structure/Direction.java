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
}
