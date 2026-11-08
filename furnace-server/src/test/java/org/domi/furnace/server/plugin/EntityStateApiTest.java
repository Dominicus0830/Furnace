// 엔티티 상태 손잡이(EntityState·LivingEntityState·ArmorStandState)를 실제 region 스레드로: stateNow 는 소유 스레드에서만,
// editState 는 소유 스레드로 넘겨 돈다, 효과·밀림(collides)·탑승·NoAI 가 서버 엔티티에 그대로 들어간다.
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.concurrent.ThreadOwnershipException;
import org.domi.furnace.api.entity.ArmorStandState;
import org.domi.furnace.api.entity.EntityState;
import org.domi.furnace.api.entity.LivingEntityState;
import org.domi.furnace.api.entity.Mob;
import org.domi.furnace.api.entity.MobEffect;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.ArmorStand;
import org.domi.furnace.server.entity.Pig;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EntityStateApiTest {

    private static final ChunkPos C00 = new ChunkPos(0, 0);

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private PluginHost host;

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    @BeforeEach
    void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        host = core.plugins();
        core.world().setRegistries(org.domi.furnace.server.world.BlockRegistry.load("blocks/1.21.1.txt"), org.domi.furnace.server.world.ItemRegistry.load("items/1.21.1.txt"));
        core.world().submitAtChunk(C00, r -> r.chunkAtBlocking(C00)).get(30, TimeUnit.SECONDS);
    }

    @AfterEach
    void tearDown() throws Exception {
        host.close();
        sched.close();
        TestServers.drainIoPools();
    }

    private <T> T onRegion(java.util.function.Supplier<T> task) throws Exception {
        return core.world().submitAtChunk(C00, r -> task.get()).get(30, TimeUnit.SECONDS);
    }

    /** 자동 틱이 없으니 region 을 직접 틱해 엔티티 작업 대기열을 돌리고 결과를 기다린다. */
    private <T> T run(java.util.concurrent.CompletableFuture<T> f) throws Exception {
        core.world().submitAtChunk(C00, r -> {
            for (int i = 0; i < 2; i++) r.tick();
            return null;
        }).get(30, TimeUnit.SECONDS);
        return f.get(10, TimeUnit.SECONDS);
    }

    private record Spawned(org.domi.furnace.api.entity.Entity stand, Mob pig, ArmorStand rawStand, Pig rawPig) {}

    private Spawned spawn() throws Exception {
        return onRegion(() -> {
            var level = core.world().regionizer().ownerOf(0, 0).level();
            ArmorStand st = new ArmorStand(level, level.nextEntityId());
            st.moveTo(4.5, -60, 4.5, 0, 0);
            level.addFreshEntity(st);
            Pig pig = new Pig(level, level.nextEntityId());
            pig.moveTo(6.5, -60, 6.5, 0, 0);
            level.addFreshEntity(pig);
            return new Spawned(EntityHandle.of(host, st), (Mob) EntityHandle.of(host, pig), st, pig);
        });
    }

    @Test
    void stateNowOnlyOnTheOwnerThread_editStateHopsThere() throws Exception {
        Spawned s = spawn();
        assertThrows(ThreadOwnershipException.class, s.stand()::stateNow, "시험 스레드는 소유 스레드가 아니다");
        assertInstanceOf(ArmorStandState.class, onRegion(() -> s.stand().stateNow().orElseThrow()));
        assertInstanceOf(LivingEntityState.class, onRegion(() -> s.pig().stateNow().orElseThrow()));
        assertFalse(onRegion(() -> s.pig().stateNow().orElseThrow()) instanceof ArmorStandState);

        String thread = run(s.stand().editState(st -> {
            var as = (ArmorStandState) st;
            as.setSmall(true);
            as.setMarker(true);
            as.setBasePlate(false);
            as.setInvisible(true);
            st.setCustomName(Text.text("Hi"));
            st.setCustomNameVisible(true);
            st.setNoGravity(true);
            st.setInvulnerable(true);
            st.setSilent(true);
            return Thread.currentThread().getName();
        }));
        assertNotEquals(Thread.currentThread().getName(), thread);
        assertEquals(List.of(true, true, false, true, true, true, true, true), onRegion(() -> {
            ArmorStand a = s.rawStand();
            return List.of(a.isSmall(), a.isMarker(), !a.noBasePlate(), a.isInvisible(), a.isCustomNameVisible(), a.isNoGravity(), a.isInvulnerable(), a.isSilent());
        }));
        assertEquals("Hi", onRegion(() -> s.stand().stateNow().orElseThrow().customName().plainText()));
        // 손잡이를 다른 스레드로 빼돌려도 쓸 수 없다(호출마다 확인한다)
        EntityState leaked = onRegion(() -> s.stand().stateNow().orElseThrow());
        assertThrows(ThreadOwnershipException.class, leaked::isSilent);
    }

    @Test
    void effectsCollisionNoAiAndRiding() throws Exception {
        Spawned s = spawn();
        assertTrue(run(s.pig().<Boolean>editState(st -> ((LivingEntityState) st).addEffect(new MobEffect("invisibility", Integer.MAX_VALUE, 0, false, false, false)))));
        run(s.pig().editState(st -> {
            ((LivingEntityState) st).setCollidable(false);
            return null;
        }));
        run(s.pig().editAi(ai -> {
            ai.setNoAi(true);
            return null;
        }));
        assertEquals(List.of(true, false, false, true), onRegion(() -> {
            Pig p = s.rawPig();
            return List.of(p.isInvisible(), p.isPushable(), p.collides, p.isNoAi());
        }));
        var effect = onRegion(() -> ((LivingEntityState) s.pig().stateNow().orElseThrow()).effect("minecraft:invisibility").orElseThrow());
        assertEquals(0, effect.amplifier());
        assertFalse(effect.particles());
        assertTrue(effect.duration() > 2_000_000_000);

        assertTrue(run(s.stand().<Boolean>editState(st -> st.addPassenger(s.pig()))));
        assertEquals(List.of(s.pig().uniqueId()), onRegion(() -> s.stand().stateNow().orElseThrow().passengers().stream().map(e -> e.uniqueId()).toList()));
        assertEquals(s.stand().uniqueId(), onRegion(() -> s.pig().stateNow().orElseThrow().vehicle().orElseThrow().uniqueId()));
        assertFalse(onRegion(() -> s.stand().stateNow().orElseThrow().addPassenger(s.stand())), "자기 자신은 못 태운다");
        assertTrue(run(s.stand().<Boolean>editState(st -> st.removePassenger(s.pig()))));
        assertTrue(onRegion(() -> s.rawStand().passengers().isEmpty()));
        // 밀림을 되돌리면 다시 밀린다
        run(s.pig().editState(st -> {
            ((LivingEntityState) st).setCollidable(true);
            return null;
        }));
        assertTrue(onRegion(() -> s.rawPig().isPushable()));
    }
}
