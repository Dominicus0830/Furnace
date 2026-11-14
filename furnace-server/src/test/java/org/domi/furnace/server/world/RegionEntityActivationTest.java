// 엔티티 활성 범위(D11)의 RegionLevel 경로(activateRange): 실제 Region.tick 이 그 region 의 플레이어 둘레만 활성으로 표시하고,
// 끄면 아무것도 표시하지 않으며, 정지(/tick freeze) 중에는 활성 범위 계산도 건너뛴다(PR #326 검토 R4).
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.config.EntityActivationSettings;
import org.domi.furnace.server.entity.Entities;
import org.domi.furnace.server.entity.EntityActivation;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegionEntityActivationTest {

    private static final ChunkPos C00 = new ChunkPos(0, 0);

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;

    @BeforeEach
    void setUp() {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        core.world().setRegistries(BlockRegistry.load("blocks/1.21.1.txt"), ItemRegistry.load("items/1.21.1.txt"));
        core.gameRules().set("doMobSpawning", 0);
        core.setSimulationDistance(10);
        core.world().viewDistance(10);
    }

    @AfterEach
    void tearDown() throws Exception {
        EntityActivationSettings.install(EntityActivationSettings.OFF);   // 시험 JVM 기본(EntityActivationOffExtension)
        core.plugins().close();
        sched.close();
        TestServers.drainIoPools();
    }

    private <T> T onRegion(Function<Region, T> fn) throws Exception {
        return core.world().submitAtChunk(C00, fn::apply).get(120, TimeUnit.SECONDS);
    }

    private Player player(Region r) {
        Player p = new Player(core.world().entityIds().next(), UUID.randomUUID(), "A", new ConcurrentEmbeddedChannel());
        p.inventory(new org.domi.furnace.server.entity.PlayerInventory());
        p.applyMove(8.5, -60, 8.5, 0, 0, true);
        r.adopt(p);
        for (int x = 0; x <= 5; x++) {
            ChunkPos cp = new ChunkPos(x, 0);
            r.chunkAtBlocking(cp);
            core.world().subscribeChunk(cp, p);
        }
        return p;
    }

    private static Mob zombie(Region r, double x) {
        Mob m = (Mob) Entities.create(r.level(), "minecraft:zombie");
        m.moveTo(x, -60, 8.5, 0, 0);
        r.level().addFreshEntity(m);
        return m;
    }

    /** 켜면: 적대 몹 범위 32 안의 좀비만 활성으로 표시되고, 먼 좀비는 새 엔티티 면역(200틱)이 끝난 뒤 20틱에 한 번만 AI 를 돈다. */
    @Test
    void regionTickMarksOnlyAroundItsPlayers() throws Exception {
        EntityActivationSettings.install(EntityActivationSettings.DEFAULT);
        boolean[] out = onRegion(r -> {
            player(r);
            Mob near = zombie(r, 8.5 + 20), far = zombie(r, 8.5 + 60);
            r.tick();
            boolean on = r.level().activation().on(), nearMarked = EntityActivation.isMarkedActive(near), farMarked = EntityActivation.isMarkedActive(far);
            return new boolean[] {on, nearMarked, farMarked};
        });
        assertTrue(out[0], "켜져 있으면 region 틱이 활성 범위를 계산한다");
        assertTrue(out[1], "플레이어 20칸 좀비는 활성");
        assertFalse(out[2], "플레이어 60칸 좀비는 비활성");
    }

    /** 끄면: region 틱이 활성 범위를 끈 상태로 두고, 먼 좀비도 늘 활성(바닐라). */
    @Test
    void offLeavesEverythingActive() throws Exception {
        EntityActivationSettings.install(EntityActivationSettings.OFF);
        boolean[] out = onRegion(r -> {
            player(r);
            Mob far = zombie(r, 8.5 + 60);
            r.tick();
            return new boolean[] {r.level().activation().on(), EntityActivation.isMarkedActive(far)};
        });
        assertFalse(out[0]);
        assertTrue(out[1]);
    }

    /** /tick freeze 중에는 엔티티 틱과 함께 활성 범위 계산도 건너뛴다(시계가 움직이지 않는다). */
    @Test
    void frozenRegionSkipsActivation() throws Exception {
        EntityActivationSettings.install(EntityActivationSettings.DEFAULT);
        core.tickState().setFrozen(true);
        boolean on = onRegion(r -> {
            player(r);
            zombie(r, 8.5 + 20);
            r.tick();
            return r.level().activation().on();
        });
        assertFalse(on, "정지 중에는 activateRange 까지 가지 않는다(처음 상태 그대로 꺼짐)");
    }
}
