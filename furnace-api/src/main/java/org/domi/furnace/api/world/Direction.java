// 여섯 면 방향(바닐라 Direction). 이름은 블록 속성 값(facing=north)과 같다.
package org.domi.furnace.api.world;

public enum Direction {
    DOWN(0, -1, 0), UP(0, 1, 0), NORTH(0, 0, -1), SOUTH(0, 0, 1), WEST(-1, 0, 0), EAST(1, 0, 0);

    private final int dx, dy, dz;

    Direction(int dx, int dy, int dz) {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
    }

    public int dx() { return dx; }

    public int dy() { return dy; }

    public int dz() { return dz; }

    /** 속성 값 이름(소문자). */
    public String id() { return name().toLowerCase(java.util.Locale.ROOT); }

    public Direction opposite() {
        return switch (this) {
            case DOWN -> UP;
            case UP -> DOWN;
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case WEST -> EAST;
            case EAST -> WEST;
        };
    }

    public boolean isHorizontal() { return dy == 0; }

    /** 속성 값 이름에서(대소문자 무시), 모르면 IllegalArgumentException. */
    public static Direction byId(String id) { return valueOf(id.toUpperCase(java.util.Locale.ROOT)); }
}
