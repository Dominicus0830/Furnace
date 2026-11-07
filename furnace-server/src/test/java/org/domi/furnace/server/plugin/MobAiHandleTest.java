// 몹 AI API(E1)를 실제 region 스레드로: 몹 핸들(Mob)·editAi 가 소유 스레드에서 돈다·aiNow 는 소유 스레드에서만·스폰 이벤트 안에서 바로 바꾸기.
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.concurrent.ThreadOwnershipException;
import org.domi.furnace.api.entity.Mob;
import org.domi.furnace.api.entity.ai.GoalKey;
import org.domi.furnace.api.entity.ai.GoalSet;
import org.domi.furnace.api.entity.ai.MobAi;
import org.domi.furnace.api.entity.ai.MobGoal;
import org.domi.furnace.api.entity.ai.VanillaGoal;
import org.domi.furnace.api.event.entity.EntitySpawnEvent;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.Cow;
import org.domi.furnace.server.entity.Villager;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MobAiHandleTest {

    private static final ChunkPos C00 = new ChunkPos(0, 0);

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private PluginHost host;
    private final TestPlugins.Gamma plugin = new TestPlugins.Gamma();

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
        plugin.setEnabled(true);
        core.world().submitAtChunk(C00, r -> r.chunkAtBlocking(C00)).get(30, TimeUnit.SECONDS);
    }

    @AfterEach
    void tearDown() throws Exception {
        host.eventsImpl().unregisterAll(plugin);
        host.close();
        sched.close();
        TestServers.drainIoPools();
    }

    /** 자동 틱이 없으니 region 을 직접 틱해 엔티티 작업 대기열을 돌리고 결과를 기다린다. */
    private <T> T run(java.util.concurrent.CompletableFuture<T> f) throws Exception {
        core.world().submitAtChunk(C00, r -> {
            for (int i = 0; i < 2; i++) r.tick();
            return null;
        }).get(30, TimeUnit.SECONDS);
        return f.get(10, TimeUnit.SECONDS);
    }

    private Mob spawnCow() throws Exception {
        return core.world().submitAtChunk(C00, r -> {
            var level = r.level();
            Cow cow = new Cow(level, level.nextEntityId());
            cow.moveTo(8, -60, 8, 0, 0);
            level.addFreshEntity(cow);
            return (Mob) EntityHandle.of(host, cow);
        }).get(30, TimeUnit.SECONDS);
    }

    @Test
    void editAiRunsOnOwningRegionThreadAndAiNowRefusesOtherThreads() throws Exception {
        Mob cow = spawnCow();
        assertEquals("minecraft:cow", cow.type());
        assertThrows(ThreadOwnershipException.class, cow::aiNow, "시험 스레드는 소유 스레드가 아니다");

        MobGoal g = new MobGoal() {
            @Override public GoalKey key() { return GoalKey.of("gamma", "idle"); }
            @Override public boolean shouldActivate(MobAi mob) { return false; }
        };
        List<String> keys = run(cow.editAi(ai -> {
            assertTrue(ai.isValid());
            ai.addGoal(plugin, GoalSet.GOALS, 9, g);
            ai.removeGoals(GoalSet.GOALS, GoalKey.minecraft("panic"));
            assertTrue(cow.aiNow().isPresent(), "editAi 안은 소유 스레드다");
            return ai.goals(GoalSet.GOALS).stream().map(i -> i.key().toString()).toList();
        }));
        assertTrue(keys.contains("gamma:idle"), keys.toString());
        assertFalse(keys.contains("minecraft:panic"), keys.toString());

        // 다음 편집에서도 그대로(같은 살아 있는 몹)
        Boolean still = run(cow.editAi(ai -> ai.hasGoal(GoalSet.GOALS, g.key())));
        assertTrue(still);
        // 붙잡아 둔 MobAi 를 다른 스레드에서 쓰면 거부
        MobAi kept = run(cow.editAi(ai -> ai));
        assertThrows(ThreadOwnershipException.class, () -> kept.goals(GoalSet.GOALS));

        assertTrue(cow.remove().get(10, TimeUnit.SECONDS));
        var ex = assertThrows(ExecutionException.class, () -> run(cow.editAi(ai -> 1)));
        assertInstanceOf(IllegalStateException.class, ex.getCause(), "사라진 몹");
    }

    @Test
    void spawnEventHandlerCanRewireAiImmediately() throws Exception {
        host.eventsImpl().subscribe(plugin, EntitySpawnEvent.class, e -> {
            if (!(e.entity() instanceof Mob m)) return;
            MobAi ai = m.aiNow().orElseThrow();
            ai.removeAllGoals(GoalSet.TARGETS);
            ai.addGoal(GoalSet.TARGETS, 1, VanillaGoal.nearestAttackableTarget("minecraft:player", true));
        });
        List<String> targets = core.world().submitAtChunk(C00, r -> {
            var level = r.level();
            Cow cow = new Cow(level, level.nextEntityId());
            cow.moveTo(8, -60, 8, 0, 0);
            level.addFreshEntity(cow);
            List<String> out = new java.util.ArrayList<>();
            for (var w : cow.targetSelector().getAvailableGoals()) out.add(GoalKeys.of(w.getGoal()).toString());
            return out;
        }).get(30, TimeUnit.SECONDS);
        assertEquals(List.of("minecraft:nearest_attackable_target"), targets, "스폰 이벤트 안에서 넣은 목표가 첫 틱 전부터 걸린다");
    }

    @Test
    void merchantHandleIsAlsoMob() throws Exception {
        var h = core.world().submitAtChunk(C00, r -> {
            var level = r.level();
            Villager v = new Villager(level, level.nextEntityId());
            v.moveTo(8, -60, 8, 0, 0);
            level.addFreshEntity(v);
            return EntityHandle.of(host, v);
        }).get(30, TimeUnit.SECONDS);
        assertInstanceOf(org.domi.furnace.api.entity.Merchant.class, h);
        Mob m = assertInstanceOf(Mob.class, h);
        Boolean brain = run(m.editAi(MobAi::usesBrain));
        assertTrue(brain, "주민은 두뇌로 움직인다");

        // 두뇌 손잡이도 같은 소유 스레드 규칙: editAi 안에서 붙잡은 활동은 region 틱이 지나도 남고, 밖으로 가져가면 거부
        var forced = run(m.editAi(ai -> {
            var b = ai.brain().orElseThrow();
            b.forceActivity(org.domi.furnace.api.entity.ai.brain.ActivityKey.REST);
            return b;
        }));
        assertThrows(ThreadOwnershipException.class, forced::activeActivities);
        var active = run(m.editAi(ai -> ai.brain().orElseThrow().activeActivities()));
        assertEquals(java.util.Set.of(org.domi.furnace.api.entity.ai.brain.ActivityKey.CORE, org.domi.furnace.api.entity.ai.brain.ActivityKey.REST), active);
    }
}
