// 블록 모양(바닐라 VoxelShape 를 상자 목록으로). 바닐라에서 뽑은 상자는 이미 최적화된(겹치지 않는) 꼴이다.
// 설치 가능 판정(엔티티 겹침)·충돌·지지 판정에 필요한 연산만 둔다.
package org.domi.furnace.server.block;

import java.util.Arrays;

public final class Shape {

    public static final Shape EMPTY = new Shape(new double[0]);
    public static final Shape BLOCK = new Shape(new double[] {0, 0, 0, 1, 1, 1});

    /** 상자마다 6개(minX,minY,minZ,maxX,maxY,maxZ). */
    private final double[] boxes;

    public Shape(double[] boxes) { this.boxes = boxes; }

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
        return new Shape(b);
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

    /** 같은 상자 목록인가(바닐라에서 뽑은 최적화 꼴끼리). */
    public boolean sameAs(Shape o) { return this == o || Arrays.equals(boxes, o.boxes); }

    @Override
    public String toString() { return Arrays.toString(boxes); }
}
