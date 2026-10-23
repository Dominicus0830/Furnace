// 방향(바닐라 Direction) — 번호는 Direction.ordinal: 0 DOWN, 1 UP, 2 NORTH, 3 SOUTH, 4 WEST, 5 EAST.
// 이웃 갱신·모양 갱신·수평 순회 순서가 서로 다르니 상수 배열을 그대로 쓴다(바닐라와 같은 순서).
package org.domi.furnace.server.block;

public final class Dir {
    private Dir() {}

    public static final int DOWN = 0, UP = 1, NORTH = 2, SOUTH = 3, WEST = 4, EAST = 5;
    public static final int AXIS_X = 0, AXIS_Y = 1, AXIS_Z = 2;

    public static final int[] DX = {0, 0, 0, 0, -1, 1};
    public static final int[] DY = {-1, 1, 0, 0, 0, 0};
    public static final int[] DZ = {0, 0, -1, 1, 0, 0};
    public static final String[] NAME = {"down", "up", "north", "south", "west", "east"};

    /** NeighborUpdater.UPDATE_ORDER — neighborChanged 를 퍼뜨리는 순서. */
    public static final int[] UPDATE_ORDER = {WEST, EAST, DOWN, UP, NORTH, SOUTH};
    /** BlockBehaviour.UPDATE_SHAPE_ORDER — 모양 갱신 순서. */
    public static final int[] SHAPE_ORDER = {WEST, EAST, NORTH, SOUTH, DOWN, UP};
    /** Direction.Plane.HORIZONTAL 순회 순서. */
    public static final int[] HORIZONTAL = {NORTH, EAST, SOUTH, WEST};
    /** Direction.values() 순서. */
    public static final int[] ALL = {DOWN, UP, NORTH, SOUTH, WEST, EAST};
    /** 2D 값(from2DDataValue): 0 SOUTH, 1 WEST, 2 NORTH, 3 EAST. */
    private static final int[] BY_2D = {SOUTH, WEST, NORTH, EAST};

    public static int opposite(int d) { return d ^ 1; }

    public static int axis(int d) { return d <= UP ? AXIS_Y : d <= SOUTH ? AXIS_Z : AXIS_X; }

    public static boolean horizontal(int d) { return d >= NORTH; }

    /** 축 방향의 양수 쪽인가(UP·SOUTH·EAST). */
    public static boolean positive(int d) { return (d & 1) == 1; }

    public static int clockWise(int d) {
        return switch (d) { case NORTH -> EAST; case EAST -> SOUTH; case SOUTH -> WEST; case WEST -> NORTH; default -> d; };
    }

    public static int counterClockWise(int d) {
        return switch (d) { case NORTH -> WEST; case WEST -> SOUTH; case SOUTH -> EAST; case EAST -> NORTH; default -> d; };
    }

    public static int to2D(int d) {
        return switch (d) { case SOUTH -> 0; case WEST -> 1; case NORTH -> 2; case EAST -> 3; default -> -1; };
    }

    public static int from2D(int i) { return BY_2D[Math.abs(i % 4)]; }

    /** Direction.fromYRot(yRot) = from2DDataValue(floor(y/90 + 0.5) & 3). */
    public static int fromYRot(double yRot) { return from2D((int) Math.floor(yRot / 90.0 + 0.5) & 3); }

    public static int byName(String n) {
        for (int i = 0; i < 6; i++) if (NAME[i].equals(n)) return i;
        return -1;
    }

    /** 축 + 양음 → 방향. */
    public static int fromAxis(int axis, boolean positive) {
        return switch (axis) {
            case AXIS_X -> positive ? EAST : WEST;
            case AXIS_Y -> positive ? UP : DOWN;
            default -> positive ? SOUTH : NORTH;
        };
    }

    public static String axisName(int axis) { return axis == AXIS_X ? "x" : axis == AXIS_Y ? "y" : "z"; }

    // ------------------------------------------------------------------ 시선(바닐라 float 계산 그대로)

    private static final float[] SIN = new float[65536];
    static {
        for (int i = 0; i < SIN.length; i++) SIN[i] = (float) Math.sin(i * Math.PI * 2.0 / 65536.0);
    }
    public static final float DEG = (float) (Math.PI / 180.0);

    public static float sin(float f) { return SIN[(int) (f * 10430.378F) & 65535]; }

    public static float cos(float f) { return SIN[(int) (f * 10430.378F + 16384.0F) & 65535]; }

    /** Direction.orderedByNearest(entity) — 시선에 가까운 순서 6방향. */
    public static int[] orderedByNearest(float xRot, float yRot) {
        float p = xRot * DEG;
        float y = -yRot * DEG;
        float sp = sin(p), cp = cos(p), sy = sin(y), cy = cos(y);
        boolean east = sy > 0.0F, up = sp < 0.0F, south = cy > 0.0F;
        float ax = east ? sy : -sy;
        float ay = up ? -sp : sp;
        float az = south ? cy : -cy;
        float wx = ax * cp, wz = az * cp;
        int x = east ? EAST : WEST, yy = up ? UP : DOWN, z = south ? SOUTH : NORTH;
        if (ax > az) {
            if (ay > wx) return mk(yy, x, z);
            return wz > ay ? mk(x, z, yy) : mk(x, yy, z);
        }
        if (ay > wz) return mk(yy, z, x);
        return wx > ay ? mk(z, x, yy) : mk(z, yy, x);
    }

    private static int[] mk(int a, int b, int c) { return new int[] {a, b, c, opposite(c), opposite(b), opposite(a)}; }

    /** Entity.calculateViewVector(xRot, yRot). */
    public static double[] viewVector(float xRot, float yRot) {
        float f = xRot * DEG, g = -yRot * DEG;
        float h = cos(g), i = sin(g), j = cos(f), k = sin(f);
        return new double[] {i * j, -k, h * j};
    }

    /** Direction.getFacingAxis(entity, axis). */
    public static int facingAxis(float xRot, float yRot, int axis) {
        return switch (axis) {
            case AXIS_X -> isFacingAngle(EAST, yRot) ? EAST : WEST;
            case AXIS_Y -> xRot < 0 ? UP : DOWN;
            default -> isFacingAngle(SOUTH, yRot) ? SOUTH : NORTH;
        };
    }

    /** Direction.isFacingAngle: 방향 벡터와 각도의 시선 벡터(수평)의 내적 > 0. */
    static boolean isFacingAngle(int d, float yRot) {
        float f = yRot * DEG;
        float sx = -sin(f), cz = cos(f);
        return DX[d] * sx + DZ[d] * cz > 0.0F;
    }
}
