package net.minecraft.core;

/**
 * 여섯 방향(Mojang 이름 shim, H7b, 등급 A). 상수 순서·3D/2D 번호·반대 방향·회전·{@code toString}(소문자 이름)은 바닐라 1.21.1 과 같다
 * (javap 로 정적 초기화의 인자를 확인: DOWN(0,1,-1) UP(1,0,-1) NORTH(2,3,2) SOUTH(3,2,0) WEST(4,5,1) EAST(5,4,3)).
 */
public enum Direction {
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
    public static Direction fromYRot(double rot) { return from2DDataValue((int) Math.floor(rot / 90.0 + 0.5) & 3); }

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

    /** 바닐라 getNearest: 법선과의 내적이 가장 큰 방향(같으면 앞 순서), 0 벡터면 NORTH. */
    public static Direction getNearest(double x, double y, double z) {
        Direction best = NORTH;
        float max = Float.MIN_VALUE;
        for (Direction d : BY_3D) {
            float dot = (float) (x * d.normal.getX() + y * d.normal.getY() + z * d.normal.getZ());
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
    public enum Axis {
        X("x"), Y("y"), Z("z");

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

        public int choose(int x, int y, int z) { return this == X ? x : this == Y ? y : z; }

        public double choose(double x, double y, double z) { return this == X ? x : this == Y ? y : z; }

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
