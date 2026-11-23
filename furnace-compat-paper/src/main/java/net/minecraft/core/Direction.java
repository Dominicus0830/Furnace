package net.minecraft.core;

/**
 * 여섯 방향(Mojang 이름 shim, H7b, 등급 A). 상수 순서·3D/2D 번호·반대 방향·회전·{@code toString}(소문자 이름)은 바닐라 1.21.1 과 같다
 * (javap 로 정적 초기화의 인자를 확인: DOWN(0,1,-1) UP(1,0,-1) NORTH(2,3,2) SOUTH(3,2,0) WEST(4,5,1) EAST(5,4,3)).
 */
public enum Direction implements net.minecraft.util.StringRepresentable {
    DOWN(0, 1, -1, "down", AxisDirection.NEGATIVE, Axis.Y, new Vec3i(0, -1, 0)),
    UP(1, 0, -1, "up", AxisDirection.POSITIVE, Axis.Y, new Vec3i(0, 1, 0)),
    NORTH(2, 3, 2, "north", AxisDirection.NEGATIVE, Axis.Z, new Vec3i(0, 0, -1)),
    SOUTH(3, 2, 0, "south", AxisDirection.POSITIVE, Axis.Z, new Vec3i(0, 0, 1)),
    WEST(4, 5, 1, "west", AxisDirection.NEGATIVE, Axis.X, new Vec3i(-1, 0, 0)),
    EAST(5, 4, 3, "east", AxisDirection.POSITIVE, Axis.X, new Vec3i(1, 0, 0));

    private static final Direction[] BY_3D = values();
    private static final Direction[] BY_2D = {SOUTH, WEST, NORTH, EAST};

    private final int data3d;
    private final int oppositeIndex;
    private final int data2d;
    private final String name;
    private final AxisDirection axisDirection;
    private final Axis axis;
    private final Vec3i normal;

    Direction(int data3d, int opposite, int data2d, String name, AxisDirection dir, Axis axis, Vec3i normal) {
        this.data3d = data3d;
        this.oppositeIndex = opposite;
        this.data2d = data2d;
        this.name = name;
        this.axisDirection = dir;
        this.axis = axis;
        this.normal = normal;
    }

    public int get3DDataValue() { return data3d; }

    public int get2DDataValue() { return data2d; }

    public AxisDirection getAxisDirection() { return axisDirection; }

    public Direction getOpposite() { return from3DDataValue(oppositeIndex); }

    /** Y 축 시계 방향(위·아래는 IllegalStateException — 바닐라와 같은 문구). */
    public Direction getClockWise() {
        return switch (this) {
            case NORTH -> EAST;
            case SOUTH -> WEST;
            case WEST -> NORTH;
            case EAST -> SOUTH;
            default -> throw new IllegalStateException("Unable to get Y-rotated facing of " + this);
        };
    }

    public Direction getCounterClockWise() {
        return switch (this) {
            case NORTH -> WEST;
            case SOUTH -> EAST;
            case WEST -> SOUTH;
            case EAST -> NORTH;
            default -> throw new IllegalStateException("Unable to get CCW facing of " + this);
        };
    }

    public int getStepX() { return normal.getX(); }

    public int getStepY() { return normal.getY(); }

    public int getStepZ() { return normal.getZ(); }

    public Vec3i getNormal() { return normal; }

    public String getName() { return name; }

    public String getSerializedName() { return name; }

    public Axis getAxis() { return axis; }

    /** 바닐라: (2D 번호 & 3) × 90. */
    public float toYRot() { return (float) ((data2d & 3) * 90); }

    public static Direction from3DDataValue(int i) { return BY_3D[Math.abs(i % BY_3D.length)]; }

    public static Direction from2DDataValue(int i) { return BY_2D[Math.abs(i % BY_2D.length)]; }

    /** 바닐라: from2DDataValue(floor(rot / 90 + 0.5) & 3). */
    /** 내림은 Mth.floor(아주 큰 음수에서 Math.floor 와 다르다 — T-235 차분 시험이 찾음). */
    public static Direction fromYRot(double rot) { return from2DDataValue(net.minecraft.util.Mth.floor(rot / 90.0 + 0.5) & 3); }

    /** 이름(소문자)으로, 없으면 null. */
    public static Direction byName(String name) {
        if (name == null) return null;
        for (Direction d : BY_3D) if (d.name.equals(name)) return d;
        return null;
    }

    public static Direction fromAxisAndDirection(Axis axis, AxisDirection dir) {
        return switch (axis) {
            case X -> dir == AxisDirection.POSITIVE ? EAST : WEST;
            case Y -> dir == AxisDirection.POSITIVE ? UP : DOWN;
            case Z -> dir == AxisDirection.POSITIVE ? SOUTH : NORTH;
        };
    }

    public static Direction get(AxisDirection dir, Axis axis) { return fromAxisAndDirection(axis, dir); }

    /** 바닐라 getNearest: 인자를 float 로 바꾼 뒤 float 판으로(큰 값에서 double 곱과 결과가 다르다 — T-235 차분 시험이 찾음). */
    public static Direction getNearest(double x, double y, double z) { return getNearest((float) x, (float) y, (float) z); }

    /** 법선과의 내적(float 셈)이 가장 큰 방향, 같으면 앞 순서. 모두 Float.MIN_VALUE 이하면 NORTH. */
    public static Direction getNearest(float x, float y, float z) {
        Direction best = NORTH;
        float max = Float.MIN_VALUE;
        for (Direction d : BY_3D) {
            float dot = x * (float) d.normal.getX() + y * (float) d.normal.getY() + z * (float) d.normal.getZ();
            if (dot > max) {
                max = dot;
                best = d;
            }
        }
        return best;
    }

    @Override
    public String toString() { return name; }

    /** 축(바닐라 Direction.Axis). toString 은 소문자 이름. */
    public enum Axis implements net.minecraft.util.StringRepresentable {
        // 상수마다 몸통(바닐라처럼 Direction$Axis$1~3 이 X·Y·Z 의 클래스, choose 는 Axis 에서 추상) — T-261
        X("x") {
            @Override
            public int choose(int x, int y, int z) { return x; }

            @Override
            public double choose(double x, double y, double z) { return x; }
        },
        Y("y") {
            @Override
            public int choose(int x, int y, int z) { return y; }

            @Override
            public double choose(double x, double y, double z) { return y; }
        },
        Z("z") {
            @Override
            public int choose(int x, int y, int z) { return z; }

            @Override
            public double choose(double x, double y, double z) { return z; }
        };

        private final String name;

        Axis(String name) { this.name = name; }

        public static Axis byName(String name) {
            if (name == null) return null;
            for (Axis a : values()) if (a.name.equals(name)) return a;
            return null;
        }

        public String getName() { return name; }

        public String getSerializedName() { return name; }

        public boolean isVertical() { return this == Y; }

        public boolean isHorizontal() { return this != Y; }

        public abstract int choose(int x, int y, int z);

        public abstract double choose(double x, double y, double z);

        @Override
        public String toString() { return name; }
    }

    /** 축 위의 방향(바닐라 Direction.AxisDirection). toString 은 설명 문구. */
    public enum AxisDirection {
        POSITIVE(1, "Towards positive"), NEGATIVE(-1, "Towards negative");

        private final int step;
        private final String name;

        AxisDirection(int step, String name) {
            this.step = step;
            this.name = name;
        }

        public int getStep() { return step; }

        public String getName() { return name; }

        public AxisDirection opposite() { return this == POSITIVE ? NEGATIVE : POSITIVE; }

        @Override
        public String toString() { return name; }
    }
}
