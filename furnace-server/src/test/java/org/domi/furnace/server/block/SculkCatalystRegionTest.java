// 사용자 버그 T8: 실제 region(RegionLevel) 경로에서 촉매 근처 몹 사망 → 경험치 → 커서 → 블록 엔티티 틱으로 스컬크 확산.
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.block.entity.SculkCatalystBlockEntity;
import org.domi.furnace.server.block.types.SculkSpreader;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.RegionLevel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SculkCatalystRegionTest {

    @TempDir static Path tmp;
    static FurnaceSchedulerImpl sched;
    static ServerCore core;

    @BeforeAll
    static void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        for (int cx = -1; cx <= 1; cx++) {
            for (int cz = -1; cz <= 1; cz++) {
                var cp = new org.domi.furnace.server.world.ChunkPos(cx, cz);
                var loaded = new CompletableFuture<CompletableFuture<?>>();
                core.world().runAtChunk(cp, r -> loaded.complete(r.loadChunk(cp)));
                loaded.get(30, TimeUnit.SECONDS).get(120, TimeUnit.SECONDS);
            }
        }
    }

    @AfterAll
    static void tearDown() { sched.close(); }

    private static <T> T onRegion(Function<RegionLevel, T> f) throws Exception {
        var fut = new CompletableFuture<T>();
        core.world().runAt(0.5, 0.5, region -> {
            try {
                fut.complete(f.apply(region.level()));
            } catch (Throwable t) {
                fut.completeExceptionally(t);
            }
        });
        return fut.get(60, TimeUnit.SECONDS);
    }

    @Test
    void zombieDeathNearCatalystSpreadsSculkOnRegionLevel() throws Exception {
        String out = onRegion(l -> {
            Pos cat = new Pos(4, -61, 4);
            l.setBlock(cat, B.defaultState(B.id("sculk_catalyst")), 3);
            var be = assertInstanceOf(SculkCatalystBlockEntity.class, l.getBlockEntity(cat));
            var z = new org.domi.furnace.server.entity.Zombie(l, l.nextEntityId());
            z.moveTo(6.5, -60.0, 4.5, 0, 0);
            l.addFreshEntity(z);
            // 플레이어가 때려 죽인다(실서버 경로: hurtBy → die, 처치 크레딧·통계 포함)
            var p = new org.domi.furnace.server.entity.Player(l.nextEntityId(), java.util.UUID.randomUUID(), "A", new org.domi.furnace.server.ConcurrentEmbeddedChannel());
            p.setPosRaw(8.5, -60.0, 4.5);
            z.hurtBy(new Entity.Damage("minecraft:player_attack", false, false, false), 1000.0F, p);
            assertTrue(z.isDeadOrDying(), "죽었다");
            assertTrue(z.wasExperienceConsumed(), "(2) 리스너가 entity_die 를 받았다");
            int charge = be.getSculkSpreader().getCursors().stream().mapToInt(SculkSpreader.ChargeCursor::getCharge).sum();
            assertEquals(5, charge, "(3) 커서 전하");
            for (int t = 0; t < 400; t++) l.tickBlockEntities(k -> true);
            assertTrue(be.getSculkSpreader().getCursors().isEmpty(), "(4) 틱에서 커서를 갱신했다");
            int sculk = 0;
            for (int x = -6; x <= 14; x++)
                for (int zz = -6; zz <= 14; zz++)
                    for (int y = -63; y <= -59; y++) if (B.is(l.getBlockState(new Pos(x, y, zz)), B.id("sculk"))) sculk++;
            return "sculk=" + sculk;
        });
        System.out.println("T8 region " + out);
        assertNotEquals("sculk=0", out, "(5) 블록이 바뀌었다");
    }

    /** 수동 틱 없이 실제 스케줄러의 region 틱(블록 엔티티 틱 판정 포함)만으로 퍼지는가. */
    @Test
    void spreadsOnScheduledRegionTicks() throws Exception {
        core.world().setChunkForced(1, 0, true);
        Pos cat = new Pos(20, -61, 4);
        var be = onRegion(l -> {
            l.setBlock(cat, B.defaultState(B.id("sculk_catalyst")), 3);
            var z = new org.domi.furnace.server.entity.Zombie(l, l.nextEntityId());
            z.moveTo(22.5, -60.0, 4.5, 0, 0);
            l.addFreshEntity(z);
            z.die(new Entity.Damage("minecraft:generic", false, false, false));
            return (SculkCatalystBlockEntity) l.getBlockEntity(cat);
        });
        boolean empty = false;
        for (int i = 0; i < 400 && !empty; i++) {
            core.world().tickOnce().get(30, TimeUnit.SECONDS);   // Region.tick 전체(블록 엔티티 틱 판정 포함)
            empty = onRegion(l -> be.getSculkSpreader().getCursors().isEmpty());
        }
        int sculk = onRegion(l -> {
            int n = 0;
            for (int x = 10; x <= 30; x++)
                for (int zz = -6; zz <= 14; zz++)
                    for (int y = -63; y <= -59; y++) if (B.is(l.getBlockState(new Pos(x, y, zz)), B.id("sculk"))) n++;
            return n;
        });
        String probe = onRegion(l -> "time=" + l.gameTime() + " bloom=" + B.prop("bloom").is(l.getBlockState(cat))
                + " cursors=" + be.getSculkSpreader().getCursors().size() + " removed=" + be.isRemoved()
                + " same=" + (l.getBlockEntity(cat) == be));
        System.out.println("T8 scheduled sculk=" + sculk + " cursorsEmpty=" + empty + " " + probe);
        assertTrue(empty, "(4) 실제 틱에서 커서를 다 썼다");
        assertTrue(sculk > 0, "(5) 실제 틱에서 블록이 바뀌었다");
    }
}
