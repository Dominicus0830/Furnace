package net.minecraft.core;

/**
 * 블록 좌표(Mojang 이름 shim, H7b, 등급 A). long 묶기(X 26비트·Z 26비트·Y 12비트, X 가 위)와 offset·relative 의 "0 이면 자기 자신" 규칙은
 * 바닐라 1.21.1 과 같다. Furnace 공개 API 값({@link org.domi.furnace.api.world.BlockPos})과는 {@link #fromFurnace}·{@link #furnace} 로 오간다.
 */
public class BlockPos extends Vec3i {

    public static final BlockPos ZERO = new BlockPos(0, 0, 0);

    private static final int PACKED_X_LENGTH = 26;   // 1 + log2(smallestEncompassingPowerOfTwo(30000000))
    private static final int PACKED_Z_LENGTH = PACKED_X_LENGTH;
    public static final int PACKED_Y_LENGTH = 64 - PACKED_X_LENGTH - PACKED_Z_LENGTH;
    private static final long PACKED_X_MASK = (1L << PACKED_X_LENGTH) - 1L;
    private static final long PACKED_Y_MASK = (1L << PACKED_Y_LENGTH) - 1L;
    private static final long PACKED_Z_MASK = (1L << PACKED_Z_LENGTH) - 1L;
    private static final int Z_OFFSET = PACKED_Y_LENGTH;
    private static final int X_OFFSET = PACKED_Y_LENGTH + PACKED_Z_LENGTH;

    public BlockPos(int x, int y, int z) { super(x, y, z); }

    public BlockPos(Vec3i v) { this(v.getX(), v.getY(), v.getZ()); }

    /** 호환 모듈 전용. */
    public static BlockPos fromFurnace(org.domi.furnace.api.world.BlockPos p) { return new BlockPos(p.x(), p.y(), p.z()); }

    /** 호환 모듈 전용 — Furnace 공개 API 값. */
    public org.domi.furnace.api.world.BlockPos furnace() { return new org.domi.furnace.api.world.BlockPos(getX(), getY(), getZ()); }

    public static long offset(long packed, Direction d) { return offset(packed, d.getStepX(), d.getStepY(), d.getStepZ()); }

    public static long offset(long packed, int dx, int dy, int dz) { return asLong(getX(packed) + dx, getY(packed) + dy, getZ(packed) + dz); }

    public static int getX(long packed) { return (int) (packed << 64 - X_OFFSET - PACKED_X_LENGTH >> 64 - PACKED_X_LENGTH); }

    public static int getY(long packed) { return (int) (packed << 64 - PACKED_Y_LENGTH >> 64 - PACKED_Y_LENGTH); }

    public static int getZ(long packed) { return (int) (packed << 64 - Z_OFFSET - PACKED_Z_LENGTH >> 64 - PACKED_Z_LENGTH); }

    public static BlockPos of(long packed) { return new BlockPos(getX(packed), getY(packed), getZ(packed)); }

    /** 바닥 내림 좌표(바닐라 containing = Mth.floor). */
    public static BlockPos containing(double x, double y, double z) { return new BlockPos(floor(x), floor(y), floor(z)); }

    public static BlockPos containing(Position p) { return containing(p.x(), p.y(), p.z()); }

    public static BlockPos min(BlockPos a, BlockPos b) {
        return new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
    }

    public static BlockPos max(BlockPos a, BlockPos b) {
        return new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    public long asLong() { return asLong(getX(), getY(), getZ()); }

    public static long asLong(int x, int y, int z) {
        long l = 0L;
        l |= ((long) x & PACKED_X_MASK) << X_OFFSET;
        l |= ((long) y & PACKED_Y_MASK);
        return l | ((long) z & PACKED_Z_MASK) << Z_OFFSET;
    }

    /** 바닐라 getFlatIndex: 아래 4비트(섹션 안 y)를 지운다. */
    public static long getFlatIndex(long packed) { return packed & -16L; }

    @Override
    public BlockPos offset(int dx, int dy, int dz) { return dx == 0 && dy == 0 && dz == 0 ? this : new BlockPos(getX() + dx, getY() + dy, getZ() + dz); }

    @Override
    public BlockPos offset(Vec3i v) { return offset(v.getX(), v.getY(), v.getZ()); }

    @Override
    public BlockPos subtract(Vec3i v) { return offset(-v.getX(), -v.getY(), -v.getZ()); }

    @Override
    public BlockPos multiply(int s) {
        if (s == 1) return this;
        return s == 0 ? ZERO : new BlockPos(getX() * s, getY() * s, getZ() * s);
    }

    /** 블록 가운데의 실수 좌표. */
    public net.minecraft.world.phys.Vec3 getCenter() { return net.minecraft.world.phys.Vec3.atCenterOf(this); }

    public net.minecraft.world.phys.Vec3 getBottomCenter() { return net.minecraft.world.phys.Vec3.atBottomCenterOf(this); }

    @Override
    public BlockPos above() { return relative(Direction.UP); }

    @Override
    public BlockPos above(int n) { return relative(Direction.UP, n); }

    @Override
    public BlockPos below() { return relative(Direction.DOWN); }

    @Override
    public BlockPos below(int n) { return relative(Direction.DOWN, n); }

    @Override
    public BlockPos north() { return relative(Direction.NORTH); }

    @Override
    public BlockPos north(int n) { return relative(Direction.NORTH, n); }

    @Override
    public BlockPos south() { return relative(Direction.SOUTH); }

    @Override
    public BlockPos south(int n) { return relative(Direction.SOUTH, n); }

    @Override
    public BlockPos west() { return relative(Direction.WEST); }

    @Override
    public BlockPos west(int n) { return relative(Direction.WEST, n); }

    @Override
    public BlockPos east() { return relative(Direction.EAST); }

    @Override
    public BlockPos east(int n) { return relative(Direction.EAST, n); }

    @Override
    public BlockPos relative(Direction d) { return new BlockPos(getX() + d.getStepX(), getY() + d.getStepY(), getZ() + d.getStepZ()); }

    @Override
    public BlockPos relative(Direction d, int n) {
        return n == 0 ? this : new BlockPos(getX() + d.getStepX() * n, getY() + d.getStepY() * n, getZ() + d.getStepZ() * n);
    }

    @Override
    public BlockPos relative(Direction.Axis axis, int n) {
        if (n == 0) return this;
        int dx = axis == Direction.Axis.X ? n : 0, dy = axis == Direction.Axis.Y ? n : 0, dz = axis == Direction.Axis.Z ? n : 0;
        return new BlockPos(getX() + dx, getY() + dy, getZ() + dz);
    }

    @Override
    public BlockPos cross(Vec3i o) {
        return new BlockPos(getY() * o.getZ() - getZ() * o.getY(), getZ() * o.getX() - getX() * o.getZ(), getX() * o.getY() - getY() * o.getX());
    }

    public BlockPos atY(int y) { return new BlockPos(getX(), y, getZ()); }

    /** 불변 사본(이미 불변이면 자기 자신). */
    public BlockPos immutable() { return this; }

    public MutableBlockPos mutable() { return new MutableBlockPos(getX(), getY(), getZ()); }

    /** 가변 블록 좌표(바닐라 BlockPos.MutableBlockPos). 바꾸는 메서드는 자기 자신을 돌려준다. */
    public static class MutableBlockPos extends BlockPos {

        public MutableBlockPos() { this(0, 0, 0); }

        public MutableBlockPos(int x, int y, int z) { super(x, y, z); }

        public MutableBlockPos(double x, double y, double z) { this(floor(x), floor(y), floor(z)); }

        @Override
        public BlockPos offset(int dx, int dy, int dz) { return super.offset(dx, dy, dz).immutable(); }

        @Override
        public BlockPos multiply(int s) { return super.multiply(s).immutable(); }

        @Override
        public BlockPos relative(Direction d, int n) { return super.relative(d, n).immutable(); }

        @Override
        public BlockPos relative(Direction.Axis axis, int n) { return super.relative(axis, n).immutable(); }

        public MutableBlockPos set(int x, int y, int z) {
            setX(x);
            setY(y);
            setZ(z);
            return this;
        }

        public MutableBlockPos set(double x, double y, double z) { return set(floor(x), floor(y), floor(z)); }

        public MutableBlockPos set(Vec3i v) { return set(v.getX(), v.getY(), v.getZ()); }

        public MutableBlockPos set(long packed) { return set(getX(packed), getY(packed), getZ(packed)); }

        public MutableBlockPos setWithOffset(Vec3i v, int dx, int dy, int dz) { return set(v.getX() + dx, v.getY() + dy, v.getZ() + dz); }

        public MutableBlockPos setWithOffset(Vec3i v, Direction d) { return set(v.getX() + d.getStepX(), v.getY() + d.getStepY(), v.getZ() + d.getStepZ()); }

        public MutableBlockPos setWithOffset(Vec3i v, Vec3i o) { return set(v.getX() + o.getX(), v.getY() + o.getY(), v.getZ() + o.getZ()); }

        public MutableBlockPos move(Direction d) { return move(d, 1); }

        public MutableBlockPos move(Direction d, int n) { return set(getX() + d.getStepX() * n, getY() + d.getStepY() * n, getZ() + d.getStepZ() * n); }

        public MutableBlockPos move(int dx, int dy, int dz) { return set(getX() + dx, getY() + dy, getZ() + dz); }

        public MutableBlockPos move(Vec3i v) { return set(getX() + v.getX(), getY() + v.getY(), getZ() + v.getZ()); }

        @Override
        public MutableBlockPos setX(int x) { super.setX(x); return this; }

        @Override
        public MutableBlockPos setY(int y) { super.setY(y); return this; }

        @Override
        public MutableBlockPos setZ(int z) { super.setZ(z); return this; }

        @Override
        public BlockPos immutable() { return new BlockPos(this); }
    }
}
