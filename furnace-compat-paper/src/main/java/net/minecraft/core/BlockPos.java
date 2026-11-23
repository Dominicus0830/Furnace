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

    /** 시계 방향 돌림(Y 축). NONE 은 자기 자신. */
    public BlockPos rotate(net.minecraft.world.level.block.Rotation rotation) {
        return switch (rotation) {
            case NONE -> this;
            case CLOCKWISE_90 -> new BlockPos(-getZ(), getY(), getX());
            case CLOCKWISE_180 -> new BlockPos(-getX(), getY(), -getZ());
            case COUNTERCLOCKWISE_90 -> new BlockPos(getZ(), getY(), -getX());
        };
    }

    /** 칸 안쪽(위쪽 끝은 1e-5 만큼 안으로)으로 실수 좌표를 끼운다. float 덧셈 뒤 double 로 넓히는 것까지 바닐라와 같다. */
    public net.minecraft.world.phys.Vec3 clampLocationWithin(net.minecraft.world.phys.Vec3 pos) {
        return new net.minecraft.world.phys.Vec3(
                net.minecraft.util.Mth.clamp(pos.x, (double) ((float) getX() + 1.0E-5F), (double) getX() + 1.0 - 9.999999747378752E-6),
                net.minecraft.util.Mth.clamp(pos.y, (double) ((float) getY() + 1.0E-5F), (double) getY() + 1.0 - 9.999999747378752E-6),
                net.minecraft.util.Mth.clamp(pos.z, (double) ((float) getZ() + 1.0E-5F), (double) getZ() + 1.0 - 9.999999747378752E-6));
    }

    /** 가운데 기준 정육면체 안의 무작위 칸 {@code limit} 개(난수는 x·y·z 순서로 하나씩). */
    public static Iterable<BlockPos> randomInCube(net.minecraft.util.RandomSource random, int limit, BlockPos center, int radius) {
        return randomBetweenClosed(random, limit, center.getX() - radius, center.getY() - radius, center.getZ() - radius,
                center.getX() + radius, center.getY() + radius, center.getZ() + radius);
    }

    public static java.util.stream.Stream<BlockPos> squareOutSouthEast(BlockPos pos) {
        return java.util.stream.Stream.of(pos, pos.south(), pos.east(), pos.south().east());
    }

    /** 상자 안 무작위 칸 {@code limit} 개. 돌려주는 객체는 하나를 계속 고쳐 쓴다. */
    public static Iterable<BlockPos> randomBetweenClosed(net.minecraft.util.RandomSource random, int limit, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        int depth = maxZ - minZ + 1;
        return () -> new com.google.common.collect.AbstractIterator<BlockPos>() {
            final MutableBlockPos nextPos = new MutableBlockPos();
            int counter = limit;

            @Override
            protected BlockPos computeNext() {
                if (this.counter <= 0) return this.endOfData();
                BlockPos pos = this.nextPos.set(minX + random.nextInt(width), minY + random.nextInt(height), minZ + random.nextInt(depth));
                this.counter--;
                return pos;
            }
        };
    }

    /** 맨해튼 거리 순으로(0, 1, 2…) 훑는다. x·y 는 거리가 같은 칸끼리 x 오름, y 오름, z 는 양수 다음 음수. */
    public static Iterable<BlockPos> withinManhattan(BlockPos origin, int reachX, int reachY, int reachZ) {
        int maxDepth = reachX + reachY + reachZ;
        int originX = origin.getX();
        int originY = origin.getY();
        int originZ = origin.getZ();
        return () -> new com.google.common.collect.AbstractIterator<BlockPos>() {
            private final MutableBlockPos cursor = new MutableBlockPos();
            private int currentDepth;
            private int maxX;
            private int maxY;
            private int x;
            private int y;
            private boolean zMirror;

            @Override
            protected BlockPos computeNext() {
                if (this.zMirror) {
                    this.zMirror = false;
                    this.cursor.setZ(originZ - (this.cursor.getZ() - originZ));
                    return this.cursor;
                }
                BlockPos found = null;
                while (found == null) {
                    if (this.y > this.maxY) {
                        this.x++;
                        if (this.x > this.maxX) {
                            this.currentDepth++;
                            if (this.currentDepth > maxDepth) return this.endOfData();
                            this.maxX = Math.min(reachX, this.currentDepth);
                            this.x = -this.maxX;
                        }
                        this.maxY = Math.min(reachY, this.currentDepth - Math.abs(this.x));
                        this.y = -this.maxY;
                    }
                    int dx = this.x;
                    int dy = this.y;
                    int dz = this.currentDepth - Math.abs(dx) - Math.abs(dy);
                    if (dz <= reachZ) {
                        this.zMirror = dz != 0;
                        found = this.cursor.set(originX + dx, originY + dy, originZ + dz);
                    }
                    this.y++;
                }
                return found;
            }
        };
    }

    public static java.util.Optional<BlockPos> findClosestMatch(BlockPos origin, int reachX, int reachY, java.util.function.Predicate<BlockPos> predicate) {
        for (BlockPos pos : withinManhattan(origin, reachX, reachY, reachX)) {
            if (predicate.test(pos)) return java.util.Optional.of(pos);
        }
        return java.util.Optional.empty();
    }

    public static java.util.stream.Stream<BlockPos> withinManhattanStream(BlockPos origin, int reachX, int reachY, int reachZ) {
        return java.util.stream.StreamSupport.stream(withinManhattan(origin, reachX, reachY, reachZ).spliterator(), false);
    }

    public static Iterable<BlockPos> betweenClosed(BlockPos a, BlockPos b) {
        return betweenClosed(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
    }

    public static java.util.stream.Stream<BlockPos> betweenClosedStream(BlockPos a, BlockPos b) {
        return java.util.stream.StreamSupport.stream(betweenClosed(a, b).spliterator(), false);
    }

    public static java.util.stream.Stream<BlockPos> betweenClosedStream(net.minecraft.world.level.levelgen.structure.BoundingBox box) {
        return betweenClosedStream(Math.min(box.minX(), box.maxX()), Math.min(box.minY(), box.maxY()), Math.min(box.minZ(), box.maxZ()),
                Math.max(box.minX(), box.maxX()), Math.max(box.minY(), box.maxY()), Math.max(box.minZ(), box.maxZ()));
    }

    public static java.util.stream.Stream<BlockPos> betweenClosedStream(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return java.util.stream.StreamSupport.stream(betweenClosed(minX, minY, minZ, maxX, maxY, maxZ).spliterator(), false);
    }

    /** 상자 안 모든 칸을 x → y → z 순서로. 돌려주는 객체는 하나를 계속 고쳐 쓴다. */
    public static Iterable<BlockPos> betweenClosed(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        int depth = maxZ - minZ + 1;
        int total = width * height * depth;
        return () -> new com.google.common.collect.AbstractIterator<BlockPos>() {
            private final MutableBlockPos cursor = new MutableBlockPos();
            private int index;

            @Override
            protected BlockPos computeNext() {
                if (this.index == total) return this.endOfData();
                int x = this.index % width;
                int rest = this.index / width;
                int y = rest % height;
                int z = rest / height;
                this.index++;
                return this.cursor.set(minX + x, minY + y, minZ + z);
            }
        };
    }

    /** 가운데에서 바깥으로 도는 나선(반지름 {@code radius} 칸). 두 방향은 같은 축이면 IllegalStateException. */
    public static Iterable<MutableBlockPos> spiralAround(BlockPos center, int radius, Direction first, Direction second) {
        if (first.getAxis() == second.getAxis()) throw new IllegalStateException("The two directions cannot be on the same axis");
        return () -> new com.google.common.collect.AbstractIterator<MutableBlockPos>() {
            private final Direction[] directions = new Direction[] {first, second, first.getOpposite(), second.getOpposite()};
            private final MutableBlockPos cursor = center.mutable().move(second);
            private final int legs = 4 * radius;
            private int leg = -1;
            private int legSize;
            private int legIndex;
            private int lastX = this.cursor.getX();
            private int lastY = this.cursor.getY();
            private int lastZ = this.cursor.getZ();

            @Override
            protected MutableBlockPos computeNext() {
                this.cursor.set(this.lastX, this.lastY, this.lastZ).move(this.directions[(this.leg + 4) % 4]);
                this.lastX = this.cursor.getX();
                this.lastY = this.cursor.getY();
                this.lastZ = this.cursor.getZ();
                if (this.legIndex >= this.legSize) {
                    if (this.leg >= this.legs) return this.endOfData();
                    this.leg++;
                    this.legIndex = 0;
                    this.legSize = this.leg / 2 + 1;
                }
                this.legIndex++;
                return this.cursor;
            }
        };
    }

    /** 너비 우선 탐색. 방문한 칸 수를 돌려준다({@code maxCount} 에 닿으면 거기서 멈춘다). */
    public static int breadthFirstTraversal(BlockPos start, int maxDepth, int maxCount,
            java.util.function.BiConsumer<BlockPos, java.util.function.Consumer<BlockPos>> neighbors, java.util.function.Predicate<BlockPos> shouldVisit) {
        java.util.ArrayDeque<Object[]> queue = new java.util.ArrayDeque<>();
        it.unimi.dsi.fastutil.longs.LongOpenHashSet visited = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        queue.add(new Object[] {start, 0});
        int count = 0;
        while (!queue.isEmpty()) {
            Object[] entry = queue.poll();
            BlockPos pos = (BlockPos) entry[0];
            int depth = (Integer) entry[1];
            if (!visited.add(pos.asLong())) continue;
            if (!shouldVisit.test(pos)) continue;
            count++;
            if (count >= maxCount) return count;
            if (depth >= maxDepth) continue;
            neighbors.accept(pos, next -> queue.add(new Object[] {next, depth + 1}));
        }
        return count;
    }

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

        public MutableBlockPos set(AxisCycle cycle, int x, int y, int z) {
            return set(cycle.cycle(x, y, z, Direction.Axis.X), cycle.cycle(x, y, z, Direction.Axis.Y), cycle.cycle(x, y, z, Direction.Axis.Z));
        }

        public MutableBlockPos clamp(Direction.Axis axis, int min, int max) {
            return switch (axis) {
                case X -> set(net.minecraft.util.Mth.clamp(getX(), min, max), getY(), getZ());
                case Y -> set(getX(), net.minecraft.util.Mth.clamp(getY(), min, max), getZ());
                case Z -> set(getX(), getY(), net.minecraft.util.Mth.clamp(getZ(), min, max));
            };
        }

        @Override
        public BlockPos rotate(net.minecraft.world.level.block.Rotation rotation) { return super.rotate(rotation).immutable(); }

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
