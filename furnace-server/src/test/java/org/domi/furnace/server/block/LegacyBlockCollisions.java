// LEGACY-ORACLE since 7b2d6b77: ChunkLevel 칸별 블록 충돌 수집(collectCollisions·hasLargeCollisionShape·blockCollisions·noBlockCollision·findSupportingBlock)과 entityCollisions(T-018 이전)
// 옮길 때 바꾼 것은 클래스 밖에서 돌게 하는 부분뿐이다: 인스턴스 메서드 → 레벨을 첫 인자로 받는 정적 메서드, this → level,
// largeCollision 정적 캐시 → 이 클래스의 정적 캐시, entities 필드 → level.entities(). 칸 순서·판정·모양 계산은 그 커밋 그대로다.
package org.domi.furnace.server.block;

import org.domi.furnace.server.entity.AABB;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.identity.LegacyOracle;
import org.domi.furnace.server.world.ChunkColumn;

@LegacyOracle(since = "7b2d6b77",
        copyOf = "ChunkLevel#collectCollisions, #hasLargeCollisionShape, #blockCollisions, #noBlockCollision, #findSupportingBlock, #entityCollisions, #canCollide")
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

    public static void collectCollisions(ChunkLevel level, AABB box, org.domi.furnace.server.entity.Rider entity, java.util.function.BiPredicate<Pos, Shape> out) {
        // 문맥 블록 모양은 새 코드와 같은 문맥 의미(비계·액체 문맥 이식 뒤)로 묻는다 — 이 사본은 칸 순서·건너뛰기를 대조하려는 것이다
        CollisionContext ctx = CollisionContext.of(entity);
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
                    Shape shape = BlockBehaviors.of(state).getCollisionShape(state, level, p, ctx);
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

    public static java.util.List<Shape> blockCollisions(ChunkLevel level, org.domi.furnace.server.entity.Rider entity, AABB box) {
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

    public static java.util.List<Shape> entityCollisions(ChunkLevel level, org.domi.furnace.server.entity.Rider self, AABB box) {
        if (box.size() < 1.0E-7) return java.util.List.of();
        AABB q = box.inflate(1.0E-7);
        Entity selfEntity = self instanceof Entity e ? e : null;
        boolean hardOnly = selfEntity == null || selfEntity.collidesOnlyWithHardColliders();
        java.util.List<Shape> out = new java.util.ArrayList<>(2);
        if (!level.entities().isEmpty()) {
            java.util.function.Predicate<Entity> ok = e -> e != self && !e.isSpectator() && canCollide(self, e);
            var found = hardOnly ? level.entities().getEntitiesOfType(org.domi.furnace.server.entity.HardCollider.class, q, ok)
                    : level.entities().getEntities(selfEntity, q, ok);
            for (Entity e : found) {
                AABB b = e.boundingBox();
                out.add(Shape.box(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()));
            }
        }
        if (!hardOnly) {
            level.forEachPlayerIn(q, p -> {
                if (p != self && !p.isSpectator() && selfEntity.canCollideWithPlayer(p)) {
                    AABB b = p.boundingBox();
                    out.add(Shape.box(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()));
                }
            });
        }
        return out.isEmpty() ? java.util.List.of() : out;
    }

    /** self.canCollideWith(e): self 없음 = canBeCollidedWith, 엔티티 = 그 덮어쓰기, 플레이어 = Entity 기본(단단하고 같은 탈것 무리가 아님). */
    private static boolean canCollide(org.domi.furnace.server.entity.Rider self, Entity e) {
        if (self == null) return e.canBeCollidedWith();
        if (self instanceof Entity s) return s.canCollideWith(e);
        return e.canBeCollidedWith() && !e.isPassengerOfSameVehicle(self);
    }
}
