// LEGACY-ORACLE since 7b2d6b77: Entity 이동 충돌(collideMove·collideBoundingBox·collectColliders·stepHeightCandidates·collideWithShapes, T-018 이전)
// 옮길 때 바꾼 것은 클래스 밖에서 돌게 하는 부분뿐이다: level.blockCollisions(e, box)·level.entityCollisions(self, box) → LegacyBlockCollisions 의 같은 이름(level 첫 인자)
// (그 커밋의 블록 수집 사본). 모양 목록 순서·충돌·오르기 계산은 그 커밋 그대로다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.LegacyBlockCollisions;
import org.domi.furnace.server.block.Shape;
import org.domi.furnace.server.identity.LegacyOracle;

@LegacyOracle(since = "7b2d6b77",
        copyOf = "Entity#collideMove, #collideBoundingBox, #collectColliders, #stepHeightCandidates, #collideWithShapes, #collideAxis")
public final class LegacyEntityCollide {

    private LegacyEntityCollide() {}

    public static double[] collideMove(org.domi.furnace.server.block.ChunkLevel level, Rider self, AABB box, boolean onGround, float step,
                                       double dx, double dy, double dz) {
        // 엔티티 모양은 처음 이동 상자로 한 번만 모아 오르기 탐색에도 그대로 쓴다(바닐라 collide 의 var3)
        var entityShapes = LegacyBlockCollisions.entityCollisions(level, self, box.expandTowards(dx, dy, dz));
        double[] r = dx * dx + dy * dy + dz * dz == 0.0 ? new double[] {dx, dy, dz}
                : collideWithShapes(dx, dy, dz, box, collectColliders(level, self, entityShapes, box.expandTowards(dx, dy, dz)));
        boolean cx = dx != r[0], cy = dy != r[1], cz = dz != r[2];
        boolean below = cy && dy < 0.0;
        if (step > 0.0F && (below || onGround) && (cx || cz)) {
            AABB base = below ? box.move(0.0, r[1], 0.0) : box;
            AABB search = base.expandTowards(dx, step, dz);
            if (!below) search = search.expandTowards(0.0, -1.0E-5F, 0.0);
            var stepShapes = collectColliders(level, self, entityShapes, search);
            for (float h : stepHeightCandidates(base, stepShapes, step, (float) r[1])) {
                double[] c = collideWithShapes(dx, h, dz, base, stepShapes);
                if (c[0] * c[0] + c[2] * c[2] > r[0] * r[0] + r[2] * r[2]) {
                    c[1] -= box.minY() - base.minY();
                    return c;
                }
            }
        }
        return r;
    }

    /**
     * Entity.collideBoundingBox(self, movement, box, level, getEntityCollisions(self, box + movement)): 오르기 없이 엔티티·보더·블록 충돌만
     * (플레이어 겉날개 비행이 쓴다).
     */
    public static double[] collideBoundingBox(org.domi.furnace.server.block.ChunkLevel level, Rider self, AABB box, double dx, double dy, double dz) {
        AABB swept = box.expandTowards(dx, dy, dz);
        return collideWithShapes(dx, dy, dz, box, collectColliders(level, self, LegacyBlockCollisions.entityCollisions(level, self, swept), swept));
    }

    /** Entity.collectColliders(self, level, entityShapes, box): 엔티티 모양 → (self 가 보더 가까이면) 보더 모양 → 블록 모양, 이 순서. */
    static java.util.List<Shape> collectColliders(org.domi.furnace.server.block.ChunkLevel level, Rider self, java.util.List<Shape> entityShapes, AABB box) {
        Shape border = self == null ? null : level.borderCollision(self.x(), self.z(), box);
        var blocks = LegacyBlockCollisions.blockCollisions(level, self, box);
        if (entityShapes.isEmpty() && border == null) return blocks;
        java.util.List<Shape> out = new java.util.ArrayList<>(entityShapes.size() + 1 + blocks.size());
        out.addAll(entityShapes);
        if (border != null) out.add(border);
        out.addAll(blocks);
        return out;
    }

    /**
     * collectCandidateStepUpHeights: 모양들의 Y 좌표 - base.minY 중 0 이상·step 이하·(float)dy 아닌 값, 오름차순.
     * 바닐라는 VoxelShape 격자 좌표 전부를 쓴다. 엔티티 상자·보더 모양은 격자가 바닐라와 같아 그대로 쓰고(exactYCoords),
     * 블록 모양은 상자들의 minY/maxY 만 쓴다(오를 높이인 윗면은 둘 다 포함).
     */
    static float[] stepHeightCandidates(AABB base, java.util.List<Shape> shapes, float step, float dy) {
        java.util.TreeSet<Float> set = new java.util.TreeSet<>();
        for (Shape s : shapes) {
            double[] ys = s.exactYCoords();
            if (ys != null) {
                for (double y : ys) {
                    float h = (float) (y - base.minY());
                    if (!(h < 0.0F) && h != dy && !(h > step)) set.add(h);
                }
                continue;
            }
            for (int i = 0; i < s.boxCount(); i++) {
                double[] b = s.box(i);
                for (double y : new double[] {b[1], b[4]}) {
                    float h = (float) (y - base.minY());
                    if (!(h < 0.0F) && h != dy && !(h > step)) set.add(h);
                }
            }
        }
        float[] out = new float[set.size()];
        int i = 0;
        for (float h : set) out[i++] = h;
        return out;
    }

    /** Entity.collideWithShapes: y 먼저, 그다음 |x| < |z| 면 z·x, 아니면 x·z. */
    public static double[] collideWithShapes(double dx, double dy, double dz, AABB box, java.util.List<Shape> shapes) {
        if (shapes.isEmpty()) return new double[] {dx, dy, dz};
        if (dy != 0.0) {
            dy = collideAxis(1, box, shapes, dy);
            if (dy != 0.0) box = box.move(0.0, dy, 0.0);
        }
        boolean zFirst = Math.abs(dx) < Math.abs(dz);
        if (zFirst && dz != 0.0) {
            dz = collideAxis(2, box, shapes, dz);
            if (dz != 0.0) box = box.move(0.0, 0.0, dz);
        }
        if (dx != 0.0) {
            dx = collideAxis(0, box, shapes, dx);
            if (!zFirst && dx != 0.0) box = box.move(dx, 0.0, 0.0);
        }
        if (!zFirst && dz != 0.0) dz = collideAxis(2, box, shapes, dz);
        return new double[] {dx, dy, dz};
    }

    /** Shapes.collide(axis, box, shapes, d). */
    private static double collideAxis(int axis, AABB box, java.util.List<Shape> shapes, double d) {
        for (Shape s : shapes) {
            if (Math.abs(d) < 1.0E-7) return 0.0;
            d = s.collide(axis, box, d);
        }
        return d;
    }
}
