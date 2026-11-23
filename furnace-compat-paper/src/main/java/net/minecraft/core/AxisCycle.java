package net.minecraft.core;

/**
 * 축 돌림(Mojang 이름 shim, 영역 9). 상수 순서 NONE·FORWARD·BACKWARD, 정수·실수 좌표를 돌려 고르는 방식은 바닐라 1.21.1 과 같다.
 * 상수마다 몸통이 달라 추상 열거형이다(바닐라도 같다).
 */
public enum AxisCycle {

    NONE {
        @Override
        public int cycle(int x, int y, int z, Direction.Axis axis) { return axis.choose(x, y, z); }

        @Override
        public double cycle(double x, double y, double z, Direction.Axis axis) { return axis.choose(x, y, z); }

        @Override
        public Direction.Axis cycle(Direction.Axis axis) { return axis; }

        @Override
        public AxisCycle inverse() { return this; }
    },
    FORWARD {
        @Override
        public int cycle(int x, int y, int z, Direction.Axis axis) { return axis.choose(z, x, y); }

        @Override
        public double cycle(double x, double y, double z, Direction.Axis axis) { return axis.choose(z, x, y); }

        @Override
        public Direction.Axis cycle(Direction.Axis axis) { return AXIS_VALUES[Math.floorMod(axis.ordinal() + 1, 3)]; }

        @Override
        public AxisCycle inverse() { return BACKWARD; }
    },
    BACKWARD {
        @Override
        public int cycle(int x, int y, int z, Direction.Axis axis) { return axis.choose(y, z, x); }

        @Override
        public double cycle(double x, double y, double z, Direction.Axis axis) { return axis.choose(y, z, x); }

        @Override
        public Direction.Axis cycle(Direction.Axis axis) { return AXIS_VALUES[Math.floorMod(axis.ordinal() - 1, 3)]; }

        @Override
        public AxisCycle inverse() { return FORWARD; }
    };

    public static final Direction.Axis[] AXIS_VALUES = Direction.Axis.values();
    public static final AxisCycle[] VALUES = values();

    public abstract int cycle(int x, int y, int z, Direction.Axis axis);

    public abstract double cycle(double x, double y, double z, Direction.Axis axis);

    public abstract Direction.Axis cycle(Direction.Axis axis);

    public abstract AxisCycle inverse();

    public static AxisCycle between(Direction.Axis from, Direction.Axis to) { return VALUES[Math.floorMod(to.ordinal() - from.ordinal(), 3)]; }
}
