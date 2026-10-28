// T4: 1칸 미만 블록(흙길·경작지·눈 층·반 블록·계단) 위에서 몹이 경로를 따라 한 칸 높은 블록에 오른다(Entity.collide 걸어 오르기).
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MobStepUpTest {

    @TempDir Path tmp;

    private static final int G = -60;   // 평지 위 첫 빈 칸

    private static int state(String name, String prop, String value) {
        int s = B.defaultState(name);
        return prop == null ? s : B.prop(prop).with(s, value);
    }

    /** 레인 z 에서: x=0 낮은 블록(low), x=1~3 일반 블록 한 칸. 몹은 low 위에서 x=3.5 의 블록 윗면으로 간다. */
    private static double climb(World world, int z, int low, boolean zombie) throws Exception {
        Mob[] holder = new Mob[1];
        var done = new CountDownLatch(1);
        world.runAt(0.5, z + 0.5, r -> {
            var lvl = r.level();
            for (int x = -1; x <= 4; x++) for (int y = 0; y < 4; y++) lvl.setBlock(new Pos(x, G + y, z), B.defaultState("minecraft:air"), 3);
            lvl.setBlock(new Pos(0, G, z), low, 3);
            for (int x = 1; x <= 3; x++) lvl.setBlock(new Pos(x, G, z), B.defaultState("minecraft:stone"), 3);
            Mob m = zombie ? new Zombie(lvl, lvl.nextEntityId()) : new Cow(lvl, lvl.nextEntityId());
            m.moveTo(0.5, G + 1.0, 0.5 + z, -90.0F, 0.0F);   // 위에서 떨어져 low 위에 선다
            lvl.addFreshEntity(m);
            holder[0] = m;
            done.countDown();
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        Mob m = holder[0];
        for (int i = 0; i < 60 && !(m.y() >= G + 1.0 - 1e-6 && m.onGround() && m.x() > 1.0); i++) {
            world.runAt(m.x(), m.z(), r -> m.getNavigation().moveTo(3.5, G + 1.0, z + 0.5, 1.0));
            Thread.sleep(100);
        }
        return m.y();
    }

    @Test
    void mobsClimbFromPartialBlocksOntoFullBlock() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        try {
            ServerCore core = TestServers.core(tmp, sched);
            World world = core.world();
            world.setChunkForced(0, 0, true);
            Thread.sleep(1500);   // 강제 청크 적재 대기
            world.startTicking();
            Object[][] cases = {
                {"dirt_path", null, null}, {"farmland", null, null}, {"snow", "layers", "8"},
                {"oak_slab", null, null}, {"oak_stairs", "facing", "east"},
            };
            int z = 1;
            for (Object[] c : cases) {
                int low = state("minecraft:" + c[0], (String) c[1], (String) c[2]);
                for (boolean zombie : new boolean[] {true, false}) {
                    double y = climb(world, z, low, zombie);
                    assertEquals(G + 1.0, y, 1e-6, c[0] + (zombie ? " 좀비" : " 소") + " 가 블록 위에 올라야 한다");
                    z += 2;
                }
            }
        } finally {
            sched.close();
        }
    }
}
