// T4: Entity.collide 걸어 오르기(1.21.1 후보 높이 방식). 기대값은 바닐라 알고리즘을 손으로 계산한 것.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MobStepUpTest {

    @TempDir Path tmp;

    private static final int G = -60;   // 평지 위 첫 빈 칸
    private static final double EPS = 1e-9;

    private static int st(String name) { return B.defaultState(name); }

    private static int st(String name, String prop, String value) { return B.prop(prop).with(st(name), value); }

    /** x=0 에 low, x=1 에 obstacle 을 놓고 좀비를 x 에 세워 (dx, dy, 0) 으로 collide. */
    private static double[] collide(int low, double lowTop, int[] obstacle, double x, double y, double dx, double dy, boolean ground, boolean noStep) {
        var level = new BlockPlacementTest.TestLevel();
        if (low >= 0) level.setBlock(new Pos(0, G, 0), low, 2);
        for (int i = 0; i < obstacle.length; i++) level.setBlock(new Pos(1, G + i, 0), obstacle[i], 2);
        Zombie z = noStep ? new Zombie(level, 1) {
            @Override public float maxUpStep() { return 0.0F; }
        } : new Zombie(level, 1);
        z.moveTo(x, Double.isNaN(y) ? G + lowTop : y, 0.5, 0.0F, 0.0F);
        z.setOnGround(ground);
        return z.collide(dx, dy, 0.0);
    }

    private static void assertVec(double ex, double ey, double[] r, String msg) {
        assertEquals(ex, r[0], 1e-6, msg + " x");   // 좀비 폭 0.6F 의 float 오차
        assertEquals(ey, r[1], EPS, msg + " y");
        assertEquals(0.0, r[2], EPS, msg + " z");
    }

    @Test
    void stepsFromPartialBlocksOntoFullBlock() {
        int stone = st("minecraft:stone");
        // 좀비(폭 0.6) maxX 0.9 → 돌 옆면까지 0.1. 후보 높이 = 돌 윗면 - 발 높이(바닥 윗면 0 은 r.y 와 같아 제외).
        Object[][] cases = {
            {"흙길", st("minecraft:dirt_path"), 15 / 16.0},
            {"경작지", st("minecraft:farmland"), 15 / 16.0},
            {"눈 층 8", st("minecraft:snow", "layers", "8"), 7 / 8.0},
            {"반 블록", st("minecraft:oak_slab"), 0.5},
        };
        for (Object[] c : cases) {
            double top = (double) c[2];
            double[] r = collide((int) c[1], top, new int[] {stone}, 0.6, Double.NaN, 0.2, 0.0, true, false);
            assertVec(0.2, (float) (1.0 - top), r, (String) c[0]);
        }
        // 평지에서 반 블록으로
        double[] r = collide(-1, 0.0, new int[] {st("minecraft:oak_slab")}, 0.6, Double.NaN, 0.2, 0.0, true, false);
        assertVec(0.2, 0.5, r, "땅→반 블록");
    }

    @Test
    void stairsLowSideClimbsHighSideBlocks() {
        int east = st("minecraft:oak_stairs", "facing", "east");   // 높은 쪽이 동쪽(x 1.5~2)
        double[] r = collide(-1, 0.0, new int[] {east}, 0.6, Double.NaN, 0.2, 0.0, true, false);
        assertVec(0.2, 0.5, r, "계단 낮은 면(서쪽)에서 오른다");
        // 동쪽에서 높은 면(1칸)으로: 0.5 로 올라도 윗단에 막혀 더 못 가고 1.0 은 step 초과
        r = collide(-1, 0.0, new int[] {east}, 2.4, Double.NaN, -0.2, 0.0, true, false);
        assertVec(-0.1, 0.0, r, "계단 높은 면은 못 오른다");
    }

    @Test
    void landingWhileFallingStepsUp() {
        // 흙길 위 0.05 에서 떨어지며(dy -0.1) 돌 쪽으로: 기준 상자를 -0.05 로 내린 뒤 후보 0(바닥, r.y 와 다름)·0.0625.
        double y = G + 15 / 16.0 + 0.05;
        double[] r = collide(st("minecraft:dirt_path"), 0.0, new int[] {st("minecraft:stone")}, 0.6, y, 0.2, -0.1, false, false);
        double baseMinY = G + 15 / 16.0;
        assertVec(0.2, (double) (float) (G + 1.0 - baseMinY) - (y - baseMinY), r, "착지하며 오른다");
        assertEquals(G + 1.0, y + r[1], 1e-6);
    }

    @Test
    void cannotStepTooHighOrWithoutStep() {
        int stone = st("minecraft:stone");
        double[] r = collide(-1, 0.0, new int[] {stone, stone}, 0.6, Double.NaN, 0.2, 0.0, true, false);
        assertVec(0.1, 0.0, r, "2칸 벽");
        r = collide(-1, 0.0, new int[] {st("minecraft:oak_fence")}, 0.975, Double.NaN, 0.2, 0.0, true, false);   // maxX 1.275, 기둥 1.375
        assertVec(0.1, 0.0, r, "울타리 1.5칸");
        r = collide(st("minecraft:dirt_path"), 15 / 16.0, new int[] {stone}, 0.6, Double.NaN, 0.2, 0.0, true, true);
        assertVec(0.1, 0.0, r, "step 0 엔티티");
        r = collide(st("minecraft:dirt_path"), 15 / 16.0, new int[] {stone}, 0.6, Double.NaN, 0.2, 0.0, false, false);
        assertVec(0.1, 0.0, r, "공중(바닥 아님·낙하 아님)");
    }

    /** 실서버 통합: 흙길 위 좀비가 경로를 따라 블록 위로 오른다. 위치는 소유 스레드에서 읽는다. */
    @Test
    void zombieOnDirtPathPathfindsOntoBlock() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        try {
            ServerCore core = TestServers.core(tmp, sched);
            World world = core.world();
            world.setChunkForced(0, 0, true);
            world.startTicking();
            int z = 1;
            Mob m = null;
            for (int i = 0; i < 120 && m == null; i++) {   // 강제 청크가 올라올 때까지 다시 시도
                CompletableFuture<Mob> spawned = new CompletableFuture<>();
                world.runAt(0.5, z + 0.5, r -> {
                    var lvl = r.level();
                    lvl.setBlock(new Pos(0, G, z), st("minecraft:dirt_path"), 3);
                    if (!B.is(lvl.getBlockState(new Pos(0, G, z)), "minecraft:dirt_path")) {
                        spawned.complete(null);
                        return;
                    }
                    for (int x = 1; x <= 3; x++) lvl.setBlock(new Pos(x, G, z), st("minecraft:stone"), 3);
                    Mob mob = new Zombie(lvl, lvl.nextEntityId());
                    mob.moveTo(0.5, G + 1.0, z + 0.5, -90.0F, 0.0F);
                    lvl.addFreshEntity(mob);
                    spawned.complete(mob);
                });
                m = spawned.get(60, TimeUnit.SECONDS);
                if (m == null) Thread.sleep(250);
            }
            assertNotNull(m, "청크 적재");
            Mob mob = m;
            double[] pos = null;
            for (int i = 0; i < 40; i++) {
                CompletableFuture<double[]> f = new CompletableFuture<>();
                world.runAt(0.5, z + 0.5, r -> {
                    mob.getNavigation().moveTo(3.5, G + 1.0, z + 0.5, 1.0);
                    f.complete(new double[] {mob.x(), mob.y(), mob.onGround() ? 1 : 0});
                });
                pos = f.get(30, TimeUnit.SECONDS);
                if (pos[0] > 1.0 && pos[2] == 1 && Math.abs(pos[1] - (G + 1.0)) < 1e-6) break;
                Thread.sleep(250);
            }
            assertEquals(G + 1.0, pos[1], 1e-6, "흙길에서 블록 위로 올라야 한다");
        } finally {
            sched.close();
        }
    }
}
