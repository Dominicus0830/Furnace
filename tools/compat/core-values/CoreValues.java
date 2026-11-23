package org.domi.furnace.tools.core;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.function.Supplier;
import net.minecraft.core.AxisCycle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Cursor3D;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * 영역 9 좌표 계열(BlockPos 반복자·AxisCycle·Cursor3D·Direction·SectionPos·ResourceLocation 읽기·Mth.lerp3)의 기대값 줄을 만든다.
 * 같은 입력을 바닐라 서버 jar 와 Furnace 호환 계층에 먹여 두 출력이 같아야 한다. 입력만 담고 바닐라 코드·이름은 담지 않는다
 * (Mojang 이름의 공개 메서드 호출뿐). 난수는 시드를 정해 호출 순서까지 견준다. 절차는 README.md.
 */
public final class CoreValues {
    private CoreValues() {}

    private static List<String> out;

    /** 제어·비 ASCII 문자는 \\uXXXX 로 적어 기대값 파일이 글 파일로 남게 한다. */
    private static String clean(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c < 0x20 || c >= 0x7f) b.append(String.format("\\u%04x", (int) c)); else b.append(c);
        }
        return b.toString();
    }

    private static void p(String name, Object value) { out.add(clean(name + "=" + value)); }

    private static void guard(String name, Supplier<Object> f) {
        try { p(name, f.get()); } catch (RuntimeException e) { p(name, e.getClass().getSimpleName() + ":" + e.getMessage()); }
    }

    /** 인자가 있으면 그 파일에, 없으면 표준 출력에 쓴다(바닐라가 로그를 표준 출력에 섞을 수 있어 파일을 권한다). */
    public static void main(String[] args) throws java.io.IOException {
        List<String> ls = lines();
        if (args.length > 0) java.nio.file.Files.write(java.nio.file.Path.of(args[0]), ls, java.nio.charset.StandardCharsets.UTF_8);
        else ls.forEach(System.out::println);
    }

    public static List<String> lines() {
        out = new ArrayList<>();
        blockPos();
        manhattan();
        random();
        spiral();
        bfs();
        axisCycle();
        cursor();
        direction();
        sectionPos();
        resource();
        p("lerp3", Mth.lerp3(0.25, 0.5, 0.75, 1.0, 3.0, -2.0, 8.0, 10.0, 11.0, -5.0, 5.0));
        p("lerp3.nan", Mth.lerp3(Double.NaN, 0.5, 0.5, 1.0, 3.0, -2.0, 8.0, 10.0, 11.0, -5.0, 5.0));
        return out;
    }

    private static String f(BlockPos b) { return b.getX() + "," + b.getY() + "," + b.getZ(); }

    /** 순서 있는 목록의 길이·앞쪽 40개·전체 검사합(한계 {@code limit} 개까지). */
    private static void seq(String name, Iterable<? extends BlockPos> it, int limit) {
        int n = 0;
        long sum = 17;
        StringBuilder head = new StringBuilder();
        for (BlockPos b : it) {
            if (n < 40) head.append('[').append(f(b)).append(']');
            sum = sum * 31 + b.asLong();
            if (++n >= limit) break;
        }
        p(name, n + " " + sum + " " + head);
    }

    private static void blockPos() {
        int[][] boxes = {
            {0, 0, 0, 0, 0, 0}, {0, 0, 0, 2, 3, 4}, {-3, -2, -1, 1, 2, 3}, {5, 5, 5, 4, 5, 5}, {2, 2, 2, 1, 5, 5},
            {29999990, -64, -29999999, 30000001, -62, -29999997}, {-30000001, 318, 29999998, -29999999, 320, 30000000},
            {0, 0, 0, 40, 3, 2}, {Integer.MAX_VALUE - 1, 0, 0, Integer.MAX_VALUE, 0, 0},
        };
        for (int[] b : boxes) {
            String key = java.util.Arrays.toString(b);
            seq("bc6 " + key, BlockPos.betweenClosed(b[0], b[1], b[2], b[3], b[4], b[5]), 5000);
            BlockPos lo = new BlockPos(b[0], b[1], b[2]);
            BlockPos hi = new BlockPos(b[3], b[4], b[5]);
            seq("bc2 " + key, BlockPos.betweenClosed(lo, hi), 5000);
            seq("bc2r " + key, BlockPos.betweenClosed(hi, lo), 5000);
            List<String> sl = new ArrayList<>();
            BlockPos.betweenClosedStream(b[0], b[1], b[2], b[3], b[4], b[5]).limit(60).forEach(x -> sl.add(f(x)));
            p("bcs6 " + key, sl);
            Spliterator<BlockPos> sp = BlockPos.betweenClosedStream(lo, hi).spliterator();
            p("bcs2.spl " + key, sp.characteristics() + " " + sp.estimateSize());
            if (b[0] <= b[3] && b[1] <= b[4] && b[2] <= b[5]) {   // 뒤집힌 상자는 바닐라 BoundingBox 생성자가 오류 로그를 남긴다
                List<String> st = new ArrayList<>();
                BlockPos.betweenClosedStream(new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5])).limit(60).forEach(x -> st.add(f(x)));
                p("bcsBox " + key, st);
            }
        }
        // 반복자가 같은 객체를 고쳐 쓰는지(바닐라는 하나를 계속 고쳐 쓴다)
        BlockPos first = null;
        boolean same = true;
        for (BlockPos x : BlockPos.betweenClosed(0, 0, 0, 3, 3, 3)) {
            if (first == null) first = x; else if (first != x) same = false;
        }
        p("bc.reuse", same);
        int[][] ps = {{0, 0, 0}, {-1, 64, 7}, {29999999, 319, -30000000}, {Integer.MAX_VALUE, Integer.MIN_VALUE, 0}};
        for (int[] q : ps) {
            BlockPos o = new BlockPos(q[0], q[1], q[2]);
            List<String> sq = new ArrayList<>();
            BlockPos.squareOutSouthEast(o).forEach(x -> sq.add(f(x)));
            p("sq " + f(o), sq);
            for (Rotation r : Rotation.values()) {
                p("rot " + f(o) + " " + r, f(o.rotate(r)));
                p("mrot " + f(o) + " " + r, f(o.mutable().rotate(r)));
            }
            for (Direction.Axis a : Direction.Axis.values()) {
                p("clamp " + f(o) + " " + a, f(o.mutable().clamp(a, -5, 5)));
                p("clampInv " + f(o) + " " + a, f(o.mutable().clamp(a, 5, -5)));
            }
            for (AxisCycle c : AxisCycle.values()) p("mset " + f(o) + " " + c, f(new BlockPos.MutableBlockPos().set(c, q[0], q[1], q[2])));
        }
        double[] ds = {0.0, -0.0, 0.5, 1.0, -1.0, 1.0E-6, 0.99999999, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 1.0E9, -1.0E9, 3.0000000000000004};
        BlockPos[] origins = {new BlockPos(0, 0, 0), new BlockPos(-7, 70, 3), new BlockPos(29999999, 0, -30000000)};
        for (BlockPos o : origins) {
            for (double a : ds) {
                for (double b : ds) {
                    Vec3 v = o.clampLocationWithin(new Vec3(a, b, a));
                    p("clw " + f(o) + " " + a + "," + b, v.x + "," + v.y + "," + v.z);
                }
            }
        }
    }

    private static void manhattan() {
        int[][] rs = {{0, 0, 0}, {1, 1, 1}, {2, 1, 3}, {3, 0, 2}, {0, 4, 0}, {2, 2, 2}, {5, 5, 0}, {-1, 2, 2}, {2, -1, 2}, {2, 2, -1}, {6, 3, 8}};
        BlockPos[] origins = {new BlockPos(0, 0, 0), new BlockPos(-100, 63, 250), new BlockPos(29999990, 100, -30000000)};
        for (BlockPos o : origins) {
            for (int[] r : rs) {
                String k = f(o) + " " + java.util.Arrays.toString(r);
                seq("wm " + k, BlockPos.withinManhattan(o, r[0], r[1], r[2]), 4000);
                List<String> s = new ArrayList<>();
                BlockPos.withinManhattanStream(o, r[0], r[1], r[2]).limit(30).forEach(x -> s.add(f(x)));
                p("wms " + k, s);
                for (int mod = 2; mod <= 7; mod += 5) {
                    int m = mod;
                    Optional<BlockPos> hit = BlockPos.findClosestMatch(o, r[0], r[1], b -> Math.floorMod(b.getX() + 2 * b.getY() + 3 * b.getZ(), m + 9) == 0);
                    p("fcm " + k + " " + m, hit.map(CoreValues::f).orElse("-"));
                }
            }
        }
        BlockPos o = new BlockPos(0, 0, 0);
        boolean same = true;
        BlockPos first = null;
        for (BlockPos b : BlockPos.withinManhattan(o, 2, 2, 2)) {
            if (first == null) first = b; else if (first != b) same = false;
        }
        p("wm.reuse", same);
    }

    private static void random() {
        long[] seeds = {0L, 1L, -1L, 42L, 123456789012345L, Long.MIN_VALUE};
        for (long seed : seeds) {
            for (int limit : new int[] {0, 1, 5, 40}) {
                RandomSource r1 = RandomSource.create(seed);
                seq("rbc " + seed + " " + limit, BlockPos.randomBetweenClosed(r1, limit, -3, 60, 100, 3, 70, 110), 100);
                p("rbc.next " + seed + " " + limit, r1.nextInt());
                RandomSource r2 = RandomSource.create(seed);
                seq("ric " + seed + " " + limit, BlockPos.randomInCube(r2, limit, new BlockPos(10, -20, 30), 4), 100);
                p("ric.next " + seed + " " + limit, r2.nextInt());
            }
        }
        for (long seed : seeds) {
            RandomSource a = RandomSource.create(seed);
            p("dir.random " + seed, java.util.stream.IntStream.range(0, 20).mapToObj(i -> Direction.getRandom(a).getName()).toList());
            RandomSource b = RandomSource.create(seed);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) sb.append(Direction.allShuffled(b)).append(';');
            p("dir.shuffled " + seed, sb);
            p("dir.shuffled.next " + seed, b.nextInt());
        }
    }

    private static void spiral() {
        Direction[] ds = Direction.values();
        for (Direction a : ds) {
            for (Direction b : ds) {
                for (int radius : new int[] {0, 1, 3}) {
                    String k = a + " " + b + " " + radius;
                    try {
                        List<String> s = new ArrayList<>();
                        int n = 0;
                        for (BlockPos.MutableBlockPos m : BlockPos.spiralAround(new BlockPos(5, -3, 9), radius, a, b)) {
                            s.add(f(m));
                            if (++n > 80) break;
                        }
                        p("spiral " + k, s);
                    } catch (RuntimeException e) {
                        p("spiral " + k, e.getClass().getSimpleName() + ":" + e.getMessage());
                    }
                }
            }
        }
    }

    private static void bfs() {
        for (int[] c : new int[][] {{0, 100}, {1, 100}, {3, 100}, {3, 10}, {5, 1}, {4, 0}, {-1, 5}}) {
            for (int box : new int[] {1, 2, 5}) {
                List<String> order = new ArrayList<>();
                int n = BlockPos.breadthFirstTraversal(new BlockPos(0, 0, 0), c[0], c[1], (pos, add) -> {
                    for (Direction d : Direction.values()) add.accept(pos.relative(d));
                }, pos -> {
                    order.add(f(pos));
                    return Math.abs(pos.getX()) <= box && Math.abs(pos.getY()) <= box && Math.abs(pos.getZ()) <= box;
                });
                p("bfs " + c[0] + "," + c[1] + " box" + box, n + " " + order);
            }
        }
    }

    private static void axisCycle() {
        for (AxisCycle c : AxisCycle.values()) {
            p("ac.name " + c, c.ordinal() + " " + c.inverse());
            for (Direction.Axis a : Direction.Axis.values()) {
                p("ac.i " + c + " " + a, c.cycle(10, 20, 30, a) + " " + c.cycle(1.5, 2.5, 3.5, a) + " " + c.cycle(a));
            }
            for (Direction.Axis b : Direction.Axis.values()) {
                p("ac.between " + c + " " + b, AxisCycle.between(Direction.Axis.X, b) + " " + AxisCycle.between(b, Direction.Axis.Z));
            }
        }
        p("ac.values", java.util.Arrays.toString(AxisCycle.VALUES) + java.util.Arrays.toString(AxisCycle.AXIS_VALUES));
        for (Direction.Axis a : Direction.Axis.values()) for (Direction.Axis b : Direction.Axis.values()) p("ac.b " + a + b, AxisCycle.between(a, b));
        guard("ac.valueOf", () -> AxisCycle.valueOf("FORWARD"));
        guard("ac.valueOf.bad", () -> AxisCycle.valueOf("SIDEWAYS"));
    }

    private static void cursor() {
        int[][] boxes = {{0, 0, 0, 0, 0, 0}, {0, 0, 0, 2, 1, 3}, {-2, 5, 7, 1, 7, 8}, {3, 3, 3, 2, 3, 3}};
        for (int[] b : boxes) {
            Cursor3D c = new Cursor3D(b[0], b[1], b[2], b[3], b[4], b[5]);
            StringBuilder sb = new StringBuilder();
            int guardN = 0;
            while (c.advance() && guardN++ < 100) sb.append(c.nextX()).append(',').append(c.nextY()).append(',').append(c.nextZ()).append(':').append(c.getNextType()).append(' ');
            p("cur " + java.util.Arrays.toString(b), sb);
            p("cur.end " + java.util.Arrays.toString(b), c.advance());
        }
        p("cur.consts", Cursor3D.TYPE_INSIDE + "" + Cursor3D.TYPE_FACE + Cursor3D.TYPE_EDGE + Cursor3D.TYPE_CORNER);
    }

    private static void direction() {
        for (int x = -2; x <= 2; x++) for (int y = -2; y <= 2; y++) for (int z = -2; z <= 2; z++) {
            Direction d = Direction.fromDelta(x, y, z);
            p("fd " + x + y + z, d == null ? "null" : d.getName());
        }
        p("fd.ext", String.valueOf(Direction.fromDelta(Integer.MAX_VALUE, 0, 0)) + Direction.fromDelta(0, Integer.MIN_VALUE, 0) + Direction.fromDelta(0, 0, Integer.MIN_VALUE));
        for (Direction d : Direction.values()) {
            for (Direction.Axis a : Direction.Axis.values()) {
                guard("cw " + d + " " + a, () -> d.getClockWise(a));
                guard("ccw " + d + " " + a, () -> d.getCounterClockWise(a));
            }
        }
        double[] ds = {0.0, -0.0, 1.0, -1.0, 0.3, -0.3, 1.0E-300, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 1.0E30, -1.0E30, Double.MAX_VALUE};
        for (double x : ds) for (double y : ds) {
            p("near " + x + "," + y, Direction.getNearest(new Vec3(x, y, -x)) + " " + Direction.getNearest(new Vec3(-y, x, y)));
        }
        float[] fs = {0F, 90F, -90F, 180F, 45F, 135F, -45F, 360F, 720.5F, -1E9F, 1E30F, Float.NaN, Float.POSITIVE_INFINITY, Float.MIN_VALUE, 89.99F, 90.01F};
        for (Direction d : Direction.values()) {
            StringBuilder sb = new StringBuilder();
            for (float a : fs) sb.append(d.isFacingAngle(a) ? '1' : '0');
            p("facing " + d, sb);
        }
        p("stream", Direction.stream().map(Direction::getName).toList());
    }

    private static void sectionPos() {
        SectionPos[] ss = {SectionPos.of(0, 0, 0), SectionPos.of(-1, 4, 7), SectionPos.of(1874999, -4, -1875000)};
        for (SectionPos s : ss) {
            String k = s.x() + "," + s.y() + "," + s.z();
            List<String> bl = new ArrayList<>();
            s.blocksInside().limit(20).forEach(b -> bl.add(f(b)));
            p("sp.inside " + k, bl);
            p("sp.inside.n " + k, s.blocksInside().count());
            for (int r : new int[] {0, 1, 2}) {
                List<String> c = new ArrayList<>();
                SectionPos.cube(s, r).forEach(q -> c.add(q.x() + "," + q.y() + "," + q.z()));
                p("sp.cube " + k + " " + r, c);
                Spliterator<SectionPos> sp = SectionPos.cube(s, r).spliterator();
                p("sp.cube.spl " + k + " " + r, sp.characteristics() + " " + sp.estimateSize() + " " + sp.getExactSizeIfKnown());
            }
        }
        for (ChunkPos cp : new ChunkPos[] {new ChunkPos(0, 0), new ChunkPos(-5, 9)}) {
            for (int r : new int[] {0, 2}) {
                List<String> c = new ArrayList<>();
                SectionPos.aroundChunk(cp, r, -4, 20).forEach(q -> c.add(q.x() + "," + q.y() + "," + q.z()));
                p("sp.around " + cp.x + "," + cp.z + " " + r, c.size() + " " + c.subList(0, Math.min(10, c.size())) + " " + c.get(c.size() - 1));
            }
        }
        p("sp.bcs.empty", SectionPos.betweenClosedStream(0, 0, 0, -1, 0, 0).count());
        p("sp.bcs.size", SectionPos.betweenClosedStream(0, 0, 0, 2000, 2000, 2000).spliterator().estimateSize());
        int[][] coords = {{0, 0, 0}, {-1, -1, -1}, {15, 15, 15}, {16, 16, 16}, {8, 8, 8}, {14, 8, 8}, {1, 1, 1}, {-17, 31, 0}, {29999999, 319, -30000000}};
        for (int[] q : coords) {
            LongArrayList l = new LongArrayList();
            SectionPos.aroundAndAtBlockPos(q[0], q[1], q[2], l::add);
            p("sp.aab " + java.util.Arrays.toString(q), l);
            LongArrayList l2 = new LongArrayList();
            SectionPos.aroundAndAtBlockPos(new BlockPos(q[0], q[1], q[2]), l2::add);
            LongArrayList l3 = new LongArrayList();
            SectionPos.aroundAndAtBlockPos(BlockPos.asLong(q[0], q[1], q[2]), l3::add);
            p("sp.aab.same " + java.util.Arrays.toString(q), l.equals(l2) + " " + l.equals(l3));
        }
    }

    private static void resource() {
        String[] ins = {"stone", "minecraft:stone", ":stone", "a:b/c.d-e_f", "A:b", "a:B", "a:b:c", "", ":", "a b", "ünï:x", "x:y/z/../w", "\u0000", "very:long/" + "q".repeat(300), "-:-", "a/b:c"};
        for (String in : ins) {
            var r = ResourceLocation.read(in);
            p("rl.read " + in.replace('\u0000', '0'), r.result().map(Object::toString) + " | " + r.error().map(e -> e.message()));
        }
        ResourceLocation base = ResourceLocation.fromNamespaceAndPath("mod", "dir/name");
        guard("rl.wp1", () -> base.withPath(s -> s + "_x"));
        guard("rl.wp2", () -> base.withPath(s -> s.toUpperCase()));
        guard("rl.wp3", () -> base.withPath(s -> ""));
        guard("rl.wp4", () -> base.withPath(s -> "a b"));
        guard("rl.wp5", () -> base.withPath(java.util.function.UnaryOperator.identity()));
    }
}
