// LEGACY-ORACLE since 7b2d6b77: ChunkLevel 칸별 블록 충돌 수집(collectCollisions·hasLargeCollisionShape·blockCollisions·noBlockCollision·findSupportingBlock, T-018 이전)
// 옮길 때 바꾼 것은 클래스 밖에서 돌게 하는 부분뿐이다: 인스턴스 메서드 → 레벨을 첫 인자로 받는 정적 메서드, this → level,
// largeCollision 정적 캐시 → 이 클래스의 정적 캐시. 칸 순서·판정·모양 계산은 그 커밋 그대로다.
package org.domi.furnace.server.block;

import org.domi.furnace.server.entity.AABB;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.identity.LegacyOracle;
import org.domi.furnace.server.world.ChunkColumn;

@LegacyOracle(since = "7b2d6b77",
        copyOf = "ChunkLevel#collectCollisions, #hasLargeCollisionShape, #blockCollisions, #noBlockCollision, #findSupportingBlock")
public final class LegacyBlockCollisions {

    private LegacyBlockCollisions() {}

    private static final int MOVING_PISTON = B.id("moving_piston"), AIR_STATE = B.defaultState("air");
    private static byte[] largeCollision;

    private static boolean hasLargeCollisionShape(ChunkLevel level, int state) {
        byte[] c = largeCollision;
        if (c == null) largeCollision = c = new byte[level.blocks.stateCount];
        byte v = c[state];
        if (v == 0) {
            boolean large = level.info.dynamicShape(level.blocks.blockOf(state));
            double[] b = level.info.collisionShape(state).bounds();
            if (b != null) large |= b[0] < 0 || b[1] < 0 || b[2] < 0 || b[3] > 1 || b[4] > 1 || b[5] > 1;
            c[state] = v = (byte) (large ? 2 : 1);
        }
        return v == 2;
    }

    public static void collectCollisions(ChunkLevel level, AABB box, Entity entity, java.util.function.BiPredicate<Pos, Shape> out) {
        int x0 = Entity.floor(box.minX() - 1.0E-7) - 1, x1 = Entity.floor(box.maxX() + 1.0E-7) + 1;
        int y0 = Entity.floor(box.minY() - 1.0E-7) - 1, y1 = Entity.floor(box.maxY() + 1.0E-7) + 1;
        int z0 = Entity.floor(box.minZ() - 1.0E-7) - 1, z1 = Entity.floor(box.maxZ() + 1.0E-7) + 1;
        int lastCx = Integer.MIN_VALUE, lastCz = Integer.MIN_VALUE;
        ChunkColumn c = null;
        for (int z = z0; z <= z1; z++) {
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    int edges = (x == x0 || x == x1 ? 1 : 0) + (y == y0 || y == y1 ? 1 : 0) + (z == z0 || z == z1 ? 1 : 0);
                    if (edges == 3) continue;
                    if ((x >> 4) != lastCx || (z >> 4) != lastCz) {
                        lastCx = x >> 4;
                        lastCz = z >> 4;
                        c = level.chunk(lastCx, lastCz);
                    }
                    if (c == null) continue;
                    int state = y < level.minBuildHeight() || y >= level.maxBuildHeight() ? AIR_STATE : c.getBlock(x & 15, y, z & 15);
                    if (state == AIR_STATE) continue;
                    if (edges == 1 && !hasLargeCollisionShape(level, state) || edges == 2 && level.blocks.blockOf(state) != MOVING_PISTON) continue;
                    Pos p = new Pos(x, y, z);
                    Shape shape = entity == null ? BlockBehaviors.of(state).getCollisionShape(state, level, p, null)
                            : BlockBehaviors.of(state).getEntityCollisionShape(state, level, p, entity);
                    if (shape.isEmpty()) continue;
                    if (shape == Shape.BLOCK) {
                        if (!box.intersects(x, y, z, x + 1.0, y + 1.0, z + 1.0)) continue;
                    } else if (!shape.overlaps(box, x, y, z)) {
                        continue;
                    }
                    if (!out.test(p, shape.move(x, y, z))) return;
                }
            }
        }
    }

    public static java.util.List<Shape> blockCollisions(ChunkLevel level, Entity entity, AABB box) {
        java.util.List<Shape> out = new java.util.ArrayList<>();
        collectCollisions(level, box, entity, (p, s) -> out.add(s));
        return out;
    }

    public static boolean noBlockCollision(ChunkLevel level, Entity self, AABB box) {
        boolean[] hit = {false};
        collectCollisions(level, box, self, (p, s) -> {
            hit[0] = true;
            return false;
        });
        return !hit[0];
    }

    public static Pos findSupportingBlock(ChunkLevel level, Entity entity, AABB box, double ex, double ey, double ez) {
        Pos[] best = {null};
        double[] bestDist = {Double.MAX_VALUE};
        collectCollisions(level, box, entity, (p, s) -> {
            double dx = p.x() + 0.5 - ex, dy = p.y() + 0.5 - ey, dz = p.z() + 0.5 - ez;
            double d = dx * dx + dy * dy + dz * dz;
            if (d < bestDist[0] || d == bestDist[0] && (best[0] == null || compareVec(best[0], p) < 0)) {
                best[0] = p;
                bestDist[0] = d;
            }
            return true;
        });
        return best[0];
    }

    private static int compareVec(Pos a, Pos b) {
        if (a.y() != b.y()) return Integer.compare(a.y(), b.y());
        if (a.z() != b.z()) return Integer.compare(a.z(), b.z());
        return Integer.compare(a.x(), b.x());
    }
}
