// 추격 장면(O06-15 `chase`): 좀비 떼가 가만히 서 있는 플레이어를 쫓는다. 돌 기둥·벽(틈이 있는)이 길을 막아 길찾기·경로 다시 찾기가 계속 돈다.
// O06-05(길찾기 표)·06·10 과 O05 충돌 수집의 결과 동일 증명 기준. 엔티티가 있으므로 새 JVM 모드.
package org.domi.furnace.server.scene;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Entities;
import org.domi.furnace.server.identity.IdentityHash;
import org.domi.furnace.server.identity.SceneHash;

public final class ChaseScene extends TickScene {
    private static final int FLOOR = SceneLevel.FLOOR_Y;

    private SceneLevel level;

    @Override public String name() { return "chase"; }
    @Override public boolean freshJvm() { return true; }
    @Override public String covers() { return "O06-15 추격, O06-05(길찾기 표)·06·10, O05-07 충돌 수집"; }

    @Override
    protected void setup(Size size, int round) {
        SceneKit.install();
        level = new SceneLevel("chase", 3);
        level.dayTime = 18000;
        level.gameTime = 18000;
        SceneKit.addPlayer(level, "A", 0.5, FLOOR, 0.5);
        int stone = B.defaultState("minecraft:stone");
        java.util.Random r = new java.util.Random(5);
        // 기둥 60개와 틈 있는 벽 둘(원점 둘레 반지름 ~15·25)
        for (int i = 0; i < 60; i++) {
            int x = r.nextInt(70) - 35, z = r.nextInt(70) - 35;
            if (Math.abs(x) < 3 && Math.abs(z) < 3) continue;
            for (int y = FLOOR; y < FLOOR + 3; y++) level.setBlock(new Pos(x, y, z), stone, 2);
        }
        for (int ring : new int[]{15, 25})
            for (int a = -ring; a <= ring; a++) {
                if (Math.abs(a) < 3 || Math.abs(a % 10) == 5) continue;   // 틈
                for (int y = FLOOR; y < FLOOR + 3; y++) {
                    level.setBlock(new Pos(a, y, ring), stone, 2);
                    level.setBlock(new Pos(a, y, -ring), stone, 2);
                    level.setBlock(new Pos(ring, y, a), stone, 2);
                    level.setBlock(new Pos(-ring, y, a), stone, 2);
                }
            }
        int n = size.pick(24, 200);
        for (int i = 0; i < n; i++) {
            var z = Entities.create(level, "minecraft:zombie");
            double ang = r.nextDouble() * Math.PI * 2, dist = 12 + r.nextInt(22);
            z.moveTo(Math.cos(ang) * dist + 0.5, FLOOR, Math.sin(ang) * dist + 0.5, r.nextFloat() * 360, 0);
            level.addFreshEntity(z);
        }
    }

    @Override protected int ticks(Size size) { return size.pick(300, 600); }

    @Override protected int checkEvery(Size size) { return 100; }

    @Override protected void tick(int t) { level.tick(); }

    @Override
    protected void checkpoint(SceneHash.Recorder rec, String label) {
        rec.line("chase " + label + " entities", SceneKit.entities(level));
        rec.line("chase " + label + " random", new IdentityHash().add(level.random().nextLong()));
        rec.text("chase " + label + " count " + level.entities().tickSnapshot().size() + " player-health " + level.players.get(0).vitals().health());
    }
}
