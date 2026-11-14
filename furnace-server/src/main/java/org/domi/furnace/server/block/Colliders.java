// 엔티티 이동 충돌 목록(바닐라 Entity.collectColliders 의 List<VoxelShape>) — 모양을 칸마다 옮겨 새로 만들지 않고 (옮기지 않은 모양, 옮길 거리)로 든다.
// 항목 i 는 shape(i).move(dx, dy, dz) 와 같은 모양이고, 충돌·오르기 후보 계산은 그 옮긴 모양으로 한 것과 비트 단위로 같다(T-018).
package org.domi.furnace.server.block;

import java.util.Arrays;
import org.domi.furnace.server.entity.AABB;

public final class Colliders implements ChunkLevel.CollisionSink {

    private Shape[] shapes;
    /** 항목마다 옮길 거리 3개. */
    private double[] off;
    private int size;

    public Colliders() { this(8); }

    public Colliders(int capacity) {
        capacity = Math.max(capacity, 2);
        shapes = new Shape[capacity];
        off = new double[capacity * 3];
    }

    /** 이미 월드 좌표인 모양들(엔티티 몸·보더·옮긴 블록 모양)로 만든다. */
    public static Colliders of(java.util.List<Shape> list) {
        Colliders c = new Colliders(list.size());
        for (Shape s : list) c.add(s);
        return c;
    }

    /** 이미 월드 좌표인 모양(옮기지 않음). */
    public void add(Shape s) { add(s, 0.0, 0.0, 0.0); }

    /** s.move(dx, dy, dz) 를 덧붙인다. */
    public void add(Shape s, double dx, double dy, double dz) {
        if (size == shapes.length) {
            shapes = Arrays.copyOf(shapes, size * 2);
            off = Arrays.copyOf(off, size * 6);
        }
        shapes[size] = s;
        off[size * 3] = dx;
        off[size * 3 + 1] = dy;
        off[size * 3 + 2] = dz;
        size++;
    }

    /** 블록 충돌 수집(ChunkLevel.scanCollisions)의 칸 하나: 옮기지 않은 모양을 칸 좌표만큼 옮긴 것으로 넣는다. */
    @Override
    public boolean accept(int x, int y, int z, Shape shape) {
        add(shape, x, y, z);
        return true;
    }

    public int size() { return size; }

    public boolean isEmpty() { return size == 0; }

    /** 항목 i 를 옮긴 모양 객체로(시험·느린 길용). */
    public Shape shape(int i) {
        return shapes[i].move(off[i * 3], off[i * 3 + 1], off[i * 3 + 2]);
    }

    /** Shapes.collide(axis, box, shapes, d): 모양마다 앞에서 1e-7 미만이면 0 으로 끝낸다. */
    public double collide(int axis, AABB box, double d) {
        for (int i = 0; i < size; i++) {
            if (Math.abs(d) < 1.0E-7) return 0.0;
            d = shapes[i].collide(axis, box, d, off[i * 3], off[i * 3 + 1], off[i * 3 + 2]);
        }
        return d;
    }

    /**
     * Entity.collectCandidateStepUpHeights 의 후보 넣기: 항목마다 Y 좌표 − base.minY 중 0 이상·step 이하·(float)dy 아닌 값을 out 에 덧붙이고
     * 새 길이를 돌려준다. 격자를 바닐라와 똑같이 둔 모양(exactYCoords)은 격자 좌표 전부, 아니면 상자들의 minY·maxY.
     */
    public int stepHeights(double baseMinY, float step, float dy, float[][] buf, int n) {
        for (int i = 0; i < size; i++) {
            Shape s = shapes[i];
            double ex = off[i * 3], ey = off[i * 3 + 1], ez = off[i * 3 + 2];
            double[] ys = s.exactYCoords(ex, ey, ez);
            if (ys != null) {
                for (double y : ys) n = putHeight(buf, n, (float) (y - baseMinY), step, dy);
                continue;
            }
            boolean moved = !(ex == 0 && ey == 0 && ez == 0) && !s.isEmpty();   // Shape.move 가 자기 자신을 돌려주는 경우는 더하지 않는다
            for (int b = 0; b < s.boxCount(); b++) {
                double lo = s.boxValue(b, 1), hi = s.boxValue(b, 4);
                if (moved) {
                    lo += ey;
                    hi += ey;
                }
                n = putHeight(buf, n, (float) (lo - baseMinY), step, dy);
                n = putHeight(buf, n, (float) (hi - baseMinY), step, dy);
            }
        }
        return n;
    }

    private static int putHeight(float[][] buf, int n, float h, float step, float dy) {
        if (h < 0.0F || h == dy || h > step) return n;
        float[] a = buf[0];
        if (n == a.length) buf[0] = a = Arrays.copyOf(a, n * 2 + 8);
        a[n] = h;
        return n + 1;
    }
}
