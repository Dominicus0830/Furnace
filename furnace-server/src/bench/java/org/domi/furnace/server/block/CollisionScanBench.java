// T-018 충돌 수집 마이크로 벤치: 같은 JVM 에서 옛 수집기 사본(LegacyEntityCollide·LegacyBlockCollisions)과 새 구현을 교대로 돌려 호출당 시간을 잰다.
// 장면 둘: (가) 차분 시험의 무작위 질의(모양 블록 밭), (나) 평지에 서서 걷는 몹(0.6×1.8, 땅 위, 작은 이동) — 실서버 몹 대부분.
// 실행: ./gradlew -PskipOracle=true :furnace-server:bench --tests '*CollisionScanBench*'
package org.domi.furnace.server.block;

import java.util.SplittableRandom;
import org.domi.furnace.server.entity.AABB;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.LegacyEntityCollide;
import org.junit.jupiter.api.Test;

class CollisionScanBench {

    private static final int N = 4096;

    @Test
    void collisionScan() {
        CollisionScanParityTest.build();
        CollisionScanParityTest.ParityLevel field = levelField();
        CollisionScanParityTest.Query[] mixed = new CollisionScanParityTest.Query[N];
        for (int i = 0; i < N; i++) mixed[i] = CollisionScanParityTest.query(Differential(i));
        // 평지(장애물 없음): 땅(-61 위)에 선 몹이 걷는다 — 공기 칸·흙 칸만
        BlockPlacementTest.TestLevel flat = new BlockPlacementTest.TestLevel();
        AABB[] boxes = new AABB[N];
        double[][] moves = new double[N][];
        SplittableRandom r = new SplittableRandom(7);
        for (int i = 0; i < N; i++) {
            double x = -20 + r.nextDouble() * 40, z = -20 + r.nextDouble() * 40;
            boxes[i] = new AABB(x - 0.3, -60.0, z - 0.3, x + 0.3, -58.2, z + 0.3);
            moves[i] = new double[] {(r.nextDouble() - 0.5) * 0.3, -0.0784000015258789, (r.nextDouble() - 0.5) * 0.3};
        }
        long sink = 0;
        for (int round = 0; round < 12; round++) {
            boolean report = round >= 4;
            for (int variant = 0; variant < 2; variant++) {
                boolean legacy = variant == 0;
                long t0 = System.nanoTime();
                for (int rep = 0; rep < 5; rep++) {
                    for (CollisionScanParityTest.Query q : mixed) {
                        Entity self = q.self();
                        double[] m = legacy ? LegacyEntityCollide.collideMove(field, self, q.box(), q.onGround(), q.step(), q.dx(), q.dy(), q.dz())
                                : Entity.collideMove(field, self, q.box(), q.onGround(), q.step(), q.dx(), q.dy(), q.dz());
                        sink += Double.doubleToRawLongBits(m[0]);
                    }
                }
                long t1 = System.nanoTime();
                for (int rep = 0; rep < 20; rep++) {
                    for (int i = 0; i < N; i++) {
                        AABB b = boxes[i];
                        double[] mv = moves[i];
                        double[] m = legacy ? LegacyEntityCollide.collideMove(flat, null, b, true, 0.6F, mv[0], mv[1], mv[2])
                                : Entity.collideMove(flat, null, b, true, 0.6F, mv[0], mv[1], mv[2]);
                        AABB below = new AABB(b.minX(), b.minY() - 1.0E-6, b.minZ(), b.maxX(), b.minY(), b.maxZ());
                        Pos p = legacy ? LegacyBlockCollisions.findSupportingBlock(flat, null, below, b.minX() + 0.3, b.minY(), b.minZ() + 0.3)
                                : flat.findSupportingBlock(null, below, b.minX() + 0.3, b.minY(), b.minZ() + 0.3);
                        sink += Double.doubleToRawLongBits(m[1]) + (p == null ? 0 : p.y());
                    }
                }
                long t2 = System.nanoTime();
                if (report) {
                    System.out.printf("CSCAN round=%d %s mixed=%.0fns/call flatWalk=%.0fns/(collide+support)%n", round, legacy ? "legacy" : "new",
                            (t1 - t0) / (5.0 * N), (t2 - t1) / (20.0 * N));
                }
            }
        }
        System.out.println("CSCAN sink " + sink);
    }

    private static SplittableRandom Differential(int i) { return org.domi.furnace.server.identity.Differential.randomFor(0x7018_0001L, i); }

    private static CollisionScanParityTest.ParityLevel levelField() {
        try {
            var f = CollisionScanParityTest.class.getDeclaredField("level");
            f.setAccessible(true);
            return (CollisionScanParityTest.ParityLevel) f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
