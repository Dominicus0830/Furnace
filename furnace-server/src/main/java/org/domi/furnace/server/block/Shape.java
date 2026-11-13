// 블록 모양(바닐라 VoxelShape 를 상자 목록으로). 바닐라에서 뽑은 상자는 이미 최적화된(겹치지 않는) 꼴이다.
// 설치 가능 판정(엔티티 겹침)·충돌·지지 판정에 필요한 연산만 둔다.
package org.domi.furnace.server.block;

import java.util.Arrays;

public final class Shape {

    public static final Shape EMPTY = new Shape(new double[0]);
    public static final Shape BLOCK = new Shape(new double[] {0, 0, 0, 1, 1, 1});

    /** 상자마다 6개(minX,minY,minZ,maxX,maxY,maxZ). */
    private final double[] boxes;
    /** 옮기기 전 모양과 옮긴 거리 — 충돌 격자는 옮기기 전 모양에서 한 번 만들어 같이 쓴다. */
    private final Shape base;
    private final double ox, oy, oz;
    private Grid grid;
    /** 격자(coords)가 바닐라 VoxelShape 와 똑같은가(Shape.box·outsideBorder). */
    private boolean exactCoords;

    public Shape(double[] boxes) {
        this.boxes = boxes;
        this.base = this;
        this.ox = this.oy = this.oz = 0;
    }

    private Shape(double[] boxes, Shape base, double ox, double oy, double oz) {
        this.boxes = boxes;
        this.base = base;
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
    }

    public static Shape parse(String s) {
        if (s.equals("-")) return EMPTY;
        String[] parts = s.split(";");
        double[] b = new double[parts.length * 6];
        for (int i = 0; i < parts.length; i++) {
            String[] v = parts[i].split(",");
            for (int k = 0; k < 6; k++) b[i * 6 + k] = Double.parseDouble(v[k]);
        }
        return b.length == 6 && Arrays.equals(b, BLOCK.boxes) ? BLOCK : new Shape(b);
    }

    /**
     * Shapes.create(minX, minY, minZ, maxX, maxY, maxZ)(엔티티 충돌 상자): 한 변이라도 1e-7 미만이면 빈 모양. 세 축이 모두 [0,1] 안의 1/2^b(b ≤ 3)
     * 배수면(findBits) 바닐라처럼 축마다 그 간격의 큐브 격자에 맞춰 넣고(모두 b = 0 이면 BLOCK), 아니면 격자 {min, max} 의 칸 하나.
     * 격자를 바닐라와 똑같이 두는 것은 걸어 오르기 후보(getCoords(Y))가 격자 좌표 전부를 쓰기 때문이다(exactCoords).
     */
    public static Shape box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (maxX - minX < 1.0E-7 || maxY - minY < 1.0E-7 || maxZ - minZ < 1.0E-7) return EMPTY_BOX;
        int bx = findBits(minX, maxX), by = findBits(minY, maxY), bz = findBits(minZ, maxZ);
        if (bx >= 0 && by >= 0 && bz >= 0) {
            if (bx == 0 && by == 0 && bz == 0) return BLOCK;
            int rx = 1 << bx, ry = 1 << by, rz = 1 << bz;
            int x0 = (int) Math.round(minX * rx), y0 = (int) Math.round(minY * ry), z0 = (int) Math.round(minZ * rz);
            int x1 = (int) Math.round(maxX * rx), y1 = (int) Math.round(maxY * ry), z1 = (int) Math.round(maxZ * rz);
            Shape s = new Shape(new double[] {(double) x0 / rx, (double) y0 / ry, (double) z0 / rz, (double) x1 / rx, (double) y1 / ry, (double) z1 / rz});
            boolean[] full = new boolean[rx * ry * rz];
            for (int x = x0; x < x1; x++) for (int y = y0; y < y1; y++) for (int z = z0; z < z1; z++) full[(x * ry + y) * rz + z] = true;
            s.grid = new Grid(new double[][] {unitCoords(rx), unitCoords(ry), unitCoords(rz)}, full, ry, rz);
            s.exactCoords = true;
            return s;
        }
        Shape s = new Shape(new double[] {minX, minY, minZ, maxX, maxY, maxZ});
        s.grid = new Grid(new double[][] {{minX, maxX}, {minY, maxY}, {minZ, maxZ}}, new boolean[] {true}, 1, 1);
        s.exactCoords = true;
        return s;
    }

    /**
     * Shapes.empty(): 비었지만 격자 좌표가 {0} 인 ArrayVoxelShape. 크기 0 인 엔티티 몸이 충돌 목록에 들면 걸어 오르기 후보에 (0 − 상자 minY)가
     * 생기는 바닐라 버릇까지 맞추려고 EMPTY 와 따로 둔다(충돌·겹침은 EMPTY 와 같다).
     */
    private static final Shape EMPTY_BOX = emptyBox();

    private static Shape emptyBox() {
        Shape s = new Shape(new double[0]);
        s.grid = new Grid(new double[][] {{0.0}, {0.0}, {0.0}}, new boolean[0], 0, 0);
        s.exactCoords = true;
        return s;
    }

    /** Shapes.findBits(min, max): 둘 다 [0,1] 안이고 1/2^b 배수(오차 1e-7·2^b 미만)인 가장 작은 b(0..3), 없으면 -1. */
    private static int findBits(double lo, double hi) {
        if (lo < -1.0E-7 || hi > 1.0000001) return -1;
        for (int b = 0; b <= 3; b++) {
            int res = 1 << b;
            double d0 = lo * res, d1 = hi * res;
            if (Math.abs(d0 - Math.round(d0)) < 1.0E-7 * res && Math.abs(d1 - Math.round(d1)) < 1.0E-7 * res) return b;
        }
        return -1;
    }

    private static double[] unitCoords(int res) {
        double[] r = new double[res + 1];
        for (int i = 0; i <= res; i++) r[i] = (double) i / res;
        return r;
    }

    /**
     * 걸어 오르기 후보에 쓸 Y 격자 좌표(VoxelShape.getCoords(Y), 옮긴 거리 포함). 격자를 바닐라와 똑같이 만든 모양(엔티티 상자·보더)만 돌려주고,
     * 블록 모양(바닐라에서 뽑은 상자 목록 — 격자가 바닐라와 다를 수 있다)은 null.
     */
    public double[] exactYCoords() {
        if (!base.exactCoords) return null;
        double[] c = base.grid().coords[1].clone();
        if (oy != 0) for (int i = 0; i < c.length; i++) c[i] += oy;
        return c;
    }

    /**
     * 월드 보더 충돌 모양(바닐라 BorderExtent.getCollisionShape): Shapes.join(INFINITY, box(x0, -inf, z0, x1, +inf, z1), ONLY_FIRST) —
     * 보더 안(정수로 넓힌 x0..x1 × z0..z1 기둥) 밖의 모든 공간. 바닐라 결과는 ArrayVoxelShape 로 격자 x {-inf, x0, x1, +inf}, y {-inf, +inf},
     * z {-inf, z0, z1, +inf} 에 가운데 칸만 빈다. y 칸 가운데가 NaN 이 되므로 격자를 직접 만든다.
     */
    public static Shape outsideBorder(double x0, double z0, double x1, double z1) {
        double ni = Double.NEGATIVE_INFINITY, pi = Double.POSITIVE_INFINITY;
        Shape s = new Shape(new double[] {
                ni, ni, ni, x0, pi, pi,     // x < x0
                x1, ni, ni, pi, pi, pi,     // x > x1
                x0, ni, ni, x1, pi, z0,     // 가운데 x, z < z0
                x0, ni, z1, x1, pi, pi});   // 가운데 x, z > z1
        boolean[] full = new boolean[3 * 1 * 3];
        java.util.Arrays.fill(full, true);
        full[(1 * 1 + 0) * 3 + 1] = false;
        s.grid = new Grid(new double[][] {{ni, x0, x1, pi}, {ni, pi}, {ni, z0, z1, pi}}, full, 1, 3);
        s.exactCoords = true;
        return s;
    }

    public boolean isEmpty() { return boxes.length == 0; }

    public int boxCount() { return boxes.length / 6; }

    public double[] box(int i) { return Arrays.copyOfRange(boxes, i * 6, i * 6 + 6); }

    /** Shapes.or(a, b): 상자 목록을 잇는다(최적화는 하지 않는다 — 면·교차 판정에는 같다). */
    public static Shape or(Shape a, Shape b) {
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;
        double[] r = Arrays.copyOf(a.boxes, a.boxes.length + b.boxes.length);
        System.arraycopy(b.boxes, 0, r, a.boxes.length, b.boxes.length);
        return new Shape(r);
    }

    /**
     * Shapes.joinUnoptimized(a, b, ONLY_SECOND): b 에만 있는 부분. a 가 비면 b 그대로, a == b 거나 b 가 비면 빈 모양.
     * 격자는 바닐라 IndexMerger 처럼 잡는다 — 둘 다 큐브 격자면 더 고운 쪽(DiscreteCubeMerger, 2 의 거듭제곱이라 최소공배수 = 큰 쪽),
     * 아니면 두 격자 좌표의 합집합(IndirectMerger). 엔티티가 모양에 걸쳐 있을 때 collide 결과가 칸 경계에 달려서다.
     */
    public static Shape joinOnlySecond(Shape a, Shape b) {
        if (a == b || b.isEmpty()) return EMPTY;
        if (a.isEmpty()) return b;
        Grid ga = a.base.grid(), gb = b.base.grid();
        double[][] coords = new double[3][];
        for (int axis = 0; axis < 3; axis++) {
            double[] ca = ga.coords[axis], cb = gb.coords[axis];
            double oa = a.offset(axis), ob = b.offset(axis);
            int ra = cubeRes(ca, oa), rb = cubeRes(cb, ob);
            if (ra > 0 && rb > 0) {
                int res = Math.max(ra, rb);
                double[] r = new double[res + 1];
                for (int i = 0; i <= res; i++) r[i] = (double) i / res;
                coords[axis] = r;
            } else {
                double[] all = new double[ca.length + cb.length];
                for (int i = 0; i < ca.length; i++) all[i] = ca[i] + oa;
                for (int i = 0; i < cb.length; i++) all[ca.length + i] = cb[i] + ob;
                Arrays.sort(all);
                double[] r = new double[all.length];
                int n = 0;
                for (double v : all) if (n == 0 || v > r[n - 1] + 1.0E-7) r[n++] = v;   // IndirectMerger: EPS 안의 좌표는 하나로
                coords[axis] = Arrays.copyOf(r, n);
            }
        }
        int sx = coords[0].length - 1, sy = coords[1].length - 1, sz = coords[2].length - 1;
        boolean[] full = new boolean[Math.max(0, sx * sy * sz)];
        double[] out = new double[0];
        int n = 0;
        for (int x = 0; x < sx; x++) {
            double cx = (coords[0][x] + coords[0][x + 1]) / 2;
            for (int y = 0; y < sy; y++) {
                double cy = (coords[1][y] + coords[1][y + 1]) / 2;
                for (int z = 0; z < sz; z++) {
                    double cz = (coords[2][z] + coords[2][z + 1]) / 2;
                    if (b.containsPoint(cx, cy, cz) && !a.containsPoint(cx, cy, cz)) {
                        full[(x * sy + y) * sz + z] = true;
                        if (n + 6 > out.length) out = Arrays.copyOf(out, Math.max(12, out.length * 2));
                        out[n++] = coords[0][x]; out[n++] = coords[1][y]; out[n++] = coords[2][z];
                        out[n++] = coords[0][x + 1]; out[n++] = coords[1][y + 1]; out[n++] = coords[2][z + 1];
                    }
                }
            }
        }
        if (n == 0) return EMPTY;
        Shape r = new Shape(Arrays.copyOf(out, n));
        r.grid = new Grid(coords, full, sy, sz);
        return r;
    }

    /** 격자가 [0,1] 의 1/res 등간격(res = 2^b, b ≤ 3, 큐브 격자)이면 res, 아니면 0. */
    private static int cubeRes(double[] c, double off) {
        int res = c.length - 1;
        if (off != 0 || res < 1 || res > 8 || Integer.bitCount(res) != 1) return 0;
        for (int i = 0; i <= res; i++) if (Math.abs(c[i] - (double) i / res) >= 1.0E-7) return 0;
        return res;
    }

    /** 점이 어느 상자 안인가(아래 경계 포함, 위 경계 제외 — 격자 칸 가운데 판정용). */
    private boolean containsPoint(double x, double y, double z) {
        for (int i = 0; i < boxes.length; i += 6) {
            if (boxes[i] <= x && x < boxes[i + 3] && boxes[i + 1] <= y && y < boxes[i + 4] && boxes[i + 2] <= z && z < boxes[i + 5]) return true;
        }
        return false;
    }

    public Shape move(double dx, double dy, double dz) {
        if (boxes.length == 0 || dx == 0 && dy == 0 && dz == 0) return this;
        double[] b = boxes.clone();
        for (int i = 0; i < b.length; i += 6) {
            b[i] += dx; b[i + 3] += dx;
            b[i + 1] += dy; b[i + 4] += dy;
            b[i + 2] += dz; b[i + 5] += dz;
        }
        return new Shape(b, base, ox + dx, oy + dy, oz + dz);
    }

    /** 상자 하나와 부피 있게 겹치는가(Shapes.joinIsNotEmpty(this, box, AND)). */
    public boolean intersects(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        for (int i = 0; i < boxes.length; i += 6) {
            if (boxes[i] < maxX && boxes[i + 3] > minX && boxes[i + 1] < maxY && boxes[i + 4] > minY && boxes[i + 2] < maxZ && boxes[i + 5] > minZ) {
                return true;
            }
        }
        return false;
    }

    /**
     * (x, y, z) 만큼 옮긴 이 모양이 상자와 겹치는가 — Shapes.joinIsNotEmpty(옮긴 모양, Shapes.create(box), AND). 좌표는 1e-7 안이면
     * 하나로 합쳐지므로 1e-7 넘게 겹쳐야 한다.
     */
    public boolean overlaps(org.domi.furnace.server.entity.AABB box, double x, double y, double z) {
        for (int i = 0; i < boxes.length; i += 6) {
            if (box.maxX() - (boxes[i] + x) > 1.0E-7 && boxes[i + 3] + x - box.minX() > 1.0E-7
                    && box.maxY() - (boxes[i + 1] + y) > 1.0E-7 && boxes[i + 4] + y - box.minY() > 1.0E-7
                    && box.maxZ() - (boxes[i + 2] + z) > 1.0E-7 && boxes[i + 5] + z - box.minZ() > 1.0E-7) {
                return true;
            }
        }
        return false;
    }

    /** 두 모양이 부피 있게 겹치는가. */
    public boolean intersects(Shape o) {
        for (int i = 0; i < o.boxes.length; i += 6) {
            if (intersects(o.boxes[i], o.boxes[i + 1], o.boxes[i + 2], o.boxes[i + 3], o.boxes[i + 4], o.boxes[i + 5])) return true;
        }
        return false;
    }

    /** 감싸는 상자(빈 모양이면 null). */
    public double[] bounds() {
        if (boxes.length == 0) return null;
        double[] r = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (int i = 0; i < boxes.length; i += 6) {
            for (int k = 0; k < 3; k++) {
                r[k] = Math.min(r[k], boxes[i + k]);
                r[k + 3] = Math.max(r[k + 3], boxes[i + k + 3]);
            }
        }
        return r;
    }

    /**
     * Block.isFaceFull(shape, dir): 그 면 바로 안쪽(1e-7) 단면이 면 전체를 덮는가(getFaceShape + 꽉 참 비교).
     */
    public boolean isFaceFull(int dir) {
        if (this == BLOCK) return true;
        return faceCovers(dir, 0, 0, 1, 1);
    }

    /**
     * 그 면 단면(getFaceShape)이 면 위의 사각형 [u0,u1)×[v0,v1) 을 다 덮는가 — !Shapes.joinIsNotEmpty(사각기둥, faceShape, ONLY_FIRST).
     * 면의 두 축은 x→(y,z), y→(x,z), z→(x,y) 순서.
     */
    public boolean faceCovers(int dir, double u0, double v0, double u1, double v1) {
        if (this == BLOCK) return true;
        java.util.List<double[]> rects = new java.util.ArrayList<>();
        faceRects(dir, rects);
        return coversUnit(rects, u0, v0, u1, v1);
    }

    /**
     * Shapes.mergedFaceOccludes(shape, adjacent, dir): 이 모양의 dir 쪽 면 단면과 이웃 모양의 반대쪽 면 단면을 합치면
     * 면 전체(1×1)를 덮는가 — 유체가 두 칸 사이를 지나갈 수 없는지(FlowingFluid.canPassThroughWall).
     */
    public static boolean mergedFaceOccludes(Shape shape, Shape adjacent, int dir) {
        if (shape == BLOCK && adjacent == BLOCK) return true;
        if (shape.isEmpty() && adjacent.isEmpty()) return false;
        java.util.List<double[]> rects = new java.util.ArrayList<>();
        shape.faceRects(dir, rects);
        adjacent.faceRects(Dir.opposite(dir), rects);
        return coversUnit(rects, 0, 0, 1, 1);
    }

    /** 그 면 바로 안쪽(1e-7) 단면에 걸친 상자들의 면 위 사각형 {u0, v0, u1, v1}(면의 두 축은 faceCovers 와 같다). */
    private void faceRects(int dir, java.util.List<double[]> out) {
        int axis = Dir.axis(dir);
        int a = axis == Dir.AXIS_X ? 1 : 0, b = axis == Dir.AXIS_Z ? 1 : 2;
        double cut = Dir.positive(dir) ? 1 - 1e-7 : 1e-7;
        for (int i = 0; i < boxes.length; i += 6) {
            if (boxes[i + axis] <= cut && boxes[i + 3 + axis] > cut) {
                out.add(new double[] {boxes[i + a], boxes[i + b], boxes[i + 3 + a], boxes[i + 3 + b]});
            }
        }
    }

    /** 사각형들이 [u0,u1)×[v0,v1) 을 빈틈없이 덮는가(좌표 압축 후 칸마다 가운데 점 검사). */
    private static boolean coversUnit(java.util.List<double[]> rects, double u0, double v0, double u1, double v1) {
        java.util.TreeSet<Double> us = new java.util.TreeSet<>(), vs = new java.util.TreeSet<>();
        us.add(u0); us.add(u1); vs.add(v0); vs.add(v1);
        for (double[] r : rects) { us.add(r[0]); us.add(r[2]); vs.add(r[1]); vs.add(r[3]); }
        Double[] u = us.toArray(new Double[0]), v = vs.toArray(new Double[0]);
        for (int i = 0; i + 1 < u.length; i++) {
            if (u[i + 1] <= u0 || u[i] >= u1) continue;
            double cu = (u[i] + u[i + 1]) / 2;
            for (int j = 0; j + 1 < v.length; j++) {
                if (v[j + 1] <= v0 || v[j] >= v1) continue;
                double cv = (v[j] + v[j + 1]) / 2;
                boolean covered = false;
                for (double[] r : rects) {
                    if (r[0] <= cu && cu < r[2] && r[1] <= cv && cv < r[3]) { covered = true; break; }
                }
                if (!covered) return false;
            }
        }
        return true;
    }

    /** getFaceShape(dir).isEmpty(): 그 면 바로 안쪽(1e-7) 단면에 걸친 상자가 없는가. */
    public boolean isFaceEmpty(int dir) {
        int axis = Dir.axis(dir);
        double cut = Dir.positive(dir) ? 1 - 1e-7 : 1e-7;
        for (int i = 0; i < boxes.length; i += 6) {
            if (boxes[i + axis] <= cut && boxes[i + 3 + axis] > cut) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ 충돌(바닐라 VoxelShape.collide)

    /**
     * 바닐라 복셀 격자: 축마다 칸 경계 좌표와 칸이 찼는지. 상자 좌표가 모두 [0,1] 안의 1/2^b(b ≤ 3) 배수면 CubeVoxelShape 처럼
     * 1/2^b 간격 격자, 아니면 ArrayVoxelShape 처럼 상자 경계들의 합집합. 엔티티가 이미 모양에 걸쳐 있을 때 결과가 이 격자에 달렸다.
     */
    private record Grid(double[][] coords, boolean[] full, int sy, int sz) {
        boolean full(int x, int y, int z) { return full[(x * sy + y) * sz + z]; }
    }

    private Grid grid() {
        Grid g = grid;
        if (g == null) grid = g = buildGrid();
        return g;
    }

    private Grid buildGrid() {
        double[][] coords = new double[3][];
        for (int axis = 0; axis < 3; axis++) coords[axis] = axisCoords(axis);
        int sx = coords[0].length - 1, sy = coords[1].length - 1, sz = coords[2].length - 1;
        boolean[] full = new boolean[Math.max(0, sx * sy * sz)];
        for (int x = 0; x < sx; x++) {
            double cx = (coords[0][x] + coords[0][x + 1]) / 2;
            for (int y = 0; y < sy; y++) {
                double cy = (coords[1][y] + coords[1][y + 1]) / 2;
                for (int z = 0; z < sz; z++) {
                    double cz = (coords[2][z] + coords[2][z + 1]) / 2;
                    for (int i = 0; i < boxes.length; i += 6) {
                        if (boxes[i] <= cx && cx < boxes[i + 3] && boxes[i + 1] <= cy && cy < boxes[i + 4] && boxes[i + 2] <= cz && cz < boxes[i + 5]) {
                            full[(x * sy + y) * sz + z] = true;
                            break;
                        }
                    }
                }
            }
        }
        return new Grid(coords, full, sy, sz);
    }

    private double[] axisCoords(int axis) {
        java.util.TreeSet<Double> set = new java.util.TreeSet<>();
        boolean unit = true;
        for (int i = 0; i < boxes.length; i += 6) {
            double lo = boxes[i + axis], hi = boxes[i + 3 + axis];
            set.add(lo);
            set.add(hi);
            if (lo < -1.0E-7 || hi > 1.0 + 1.0E-7) unit = false;
        }
        if (unit) {
            for (int bits = 0; bits <= 3; bits++) {
                int res = 1 << bits;
                boolean aligned = true;
                for (double v : set) {
                    double s = v * res;
                    if (Math.abs(s - Math.round(s)) >= 1.0E-7 * res) { aligned = false; break; }
                }
                if (aligned) {
                    double[] r = new double[res + 1];
                    for (int i = 0; i <= res; i++) r[i] = (double) i / res;
                    return r;
                }
            }
        }
        double[] r = new double[set.size()];
        int n = 0;
        for (double v : set) r[n++] = v;
        return r;
    }

    private double offset(int axis) { return axis == 0 ? ox : axis == 1 ? oy : oz; }

    /** Mth.binarySearch(0, n, value < coords[i]) - 1 — 값이 든 칸 번호(-1..칸 수). */
    private static int findIndex(double[] coords, double off, double value) {
        int lo = 0, hi = coords.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (value < coords[mid] + off) hi = mid;
            else lo = mid + 1;
        }
        return lo - 1;
    }

    /**
     * VoxelShape.collide(axis, box, d): box 가 axis(0 x·1 y·2 z) 쪽으로 d 만큼 움직일 때 이 모양(월드 좌표)에 막혀 줄어든 거리.
     * 다른 두 축에서 겹치는 칸들 중 움직이는 쪽으로 처음 만나는 찬 칸 층에서 멈춘다.
     */
    public double collide(int axis, org.domi.furnace.server.entity.AABB box, double d) {
        if (boxes.length == 0) return d;
        if (Math.abs(d) < 1.0E-7) return 0.0;
        Grid g = base.grid();
        int b = (axis + 1) % 3, c = (axis + 2) % 3;
        double[] ca = g.coords[axis], cb = g.coords[b], cc = g.coords[c];
        double oa = offset(axis), ob = offset(b), oc = offset(c);
        double maxA = box.max(axis), minA = box.min(axis);
        int i = findIndex(ca, oa, minA + 1.0E-7), j = findIndex(ca, oa, maxA - 1.0E-7);
        int k = Math.max(0, findIndex(cb, ob, box.min(b) + 1.0E-7)), l = Math.min(cb.length - 1, findIndex(cb, ob, box.max(b) - 1.0E-7) + 1);
        int m = Math.max(0, findIndex(cc, oc, box.min(c) + 1.0E-7)), n = Math.min(cc.length - 1, findIndex(cc, oc, box.max(c) - 1.0E-7) + 1);
        int o = ca.length - 1;
        if (d > 0.0) {
            for (int p = j + 1; p < o; p++) {
                if (layerFull(g, axis, p, k, l, m, n)) {
                    double f = (oa == 0.0 ? ca[p] : ca[p] + oa) - maxA;   // 옮기지 않은 모양은 더하지 않는다(보더 좌표 -0.0 의 부호 — 바닐라와 같게)
                    if (f >= -1.0E-7) d = Math.min(d, f);
                    return d;
                }
            }
        } else if (d < 0.0) {
            for (int p = i - 1; p >= 0; p--) {
                if (layerFull(g, axis, p, k, l, m, n)) {
                    double f = (oa == 0.0 ? ca[p + 1] : ca[p + 1] + oa) - minA;
                    if (f <= 1.0E-7) d = Math.max(d, f);
                    return d;
                }
            }
        }
        return d;
    }

    private static boolean layerFull(Grid g, int axis, int p, int k, int l, int m, int n) {
        for (int q = k; q < l; q++) {
            for (int r = m; r < n; r++) {
                boolean full = switch (axis) {
                    case 0 -> g.full(p, q, r);
                    case 1 -> g.full(r, p, q);
                    default -> g.full(q, r, p);
                };
                if (full) return true;
            }
        }
        return false;
    }

    /** 같은 상자 목록인가(바닐라에서 뽑은 최적화 꼴끼리). */
    public boolean sameAs(Shape o) { return this == o || Arrays.equals(boxes, o.boxes); }

    @Override
    public String toString() { return Arrays.toString(boxes); }
}
