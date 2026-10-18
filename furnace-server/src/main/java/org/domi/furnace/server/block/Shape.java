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
                    double f = ca[p] + oa - maxA;
                    if (f >= -1.0E-7) d = Math.min(d, f);
                    return d;
                }
            }
        } else if (d < 0.0) {
            for (int p = i - 1; p >= 0; p--) {
                if (layerFull(g, axis, p, k, l, m, n)) {
                    double f = ca[p + 1] + oa - minA;
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
