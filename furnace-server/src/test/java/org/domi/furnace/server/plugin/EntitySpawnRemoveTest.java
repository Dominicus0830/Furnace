// A16-01 검토(PR #230): 스폰 이벤트 안에서 EntityState.remove() 로 지운 엔티티는 추적을 시작하지 않고 넣었다고 답하지 않는다(유령 엔티티 방지).
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.event.entity.EntitySpawnEvent;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.Cow;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EntitySpawnRemoveTest {

    private static final ChunkPos C00 = new ChunkPos(0, 0);

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private PluginHost host;
    private final TestPlugins.Gamma plugin = new TestPlugins.Gamma();

    @BeforeAll
    static void install() { ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt"))); }

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

    @Test
    void removeInsideSpawnEventStillStartsTracking() throws Exception {
        host.eventsImpl().subscribe(plugin, EntitySpawnEvent.class, e -> e.entity().stateNow().ifPresent(s -> s.remove()));
        Object[] out = core.world().submitAtChunk(C00, r -> {
            var level = r.level();
            Cow cow = new Cow(level, level.nextEntityId());
            cow.moveTo(8, -50, 8, 0, 0);
            boolean added = level.addFreshEntity(cow);
            return new Object[] {added, cow.isRemoved(), cow.tracker != null, level.entities().get(cow.uuid()) != null};
        }).get(30, TimeUnit.SECONDS);
        assertEquals(true, out[1], "remove() 로 지워졌다");
        assertEquals(false, out[3], "레벨 목록에는 없다");
        assertEquals(false, out[2], "지워진(목록에 없는) 엔티티에 추적기가 붙었다 — 보는 플레이어에게 스폰 패킷이 가고 지움 패킷은 영영 안 간다");
        assertEquals(false, out[0], "지워진 엔티티를 넣었다고 답했다(Paper: isRemoved 면 넣지 않음)");
    }

    /** A16-01 재검토(R6): dieFromApi 는 die(GENERIC) 인데 die 안의 damageSourceEntity()=lastDamageAttacker 가 예전 공격자 그대로라, 좀비가 전에 때린 주민을 setHealth(0) 하면 감염(바닐라 generic 은 원인 엔티티 없음). */
    @Test
    void dieFromApiCreditsStaleAttacker() throws Exception {
        core.setDifficulty(org.domi.furnace.server.world.Difficulty.HARD, true);
        Object[] out = core.world().submitAtChunk(C00, r -> {
            var level = r.level();
            var z = new org.domi.furnace.server.entity.Zombie(level, level.nextEntityId());
            z.moveTo(4, -59, 4, 0, 0);
            level.addFreshEntity(z);
            int converted = 0;
            String reasons = "";
            for (int i = 0; i < 10; i++) {
                var v = new org.domi.furnace.server.entity.Villager(level, level.nextEntityId());
                v.moveTo(6 + i, -59, 6, 0, 0);
                level.addFreshEntity(v);
                v.hurtBy(new org.domi.furnace.server.entity.Entity.Damage("mob_attack", false, false, false), 1f, z);
                v.dieFromApi();
                reasons += v.removalReason() + ",";
                if (v.isRemoved() && String.valueOf(v.removalReason()).contains("DISCARD")) converted++;
            }
            // 대조군: 좀비가 직접 죽이면(바닐라 감염) 어려움에서 늘 변한다 — 시험이 감염을 잡아내는지 확인
            var c = new org.domi.furnace.server.entity.Villager(level, level.nextEntityId());
            c.moveTo(6, -59, 9, 0, 0);
            level.addFreshEntity(c);
            c.hurtBy(new org.domi.furnace.server.entity.Entity.Damage("mob_attack", false, false, false), 1000f, z);
            reasons += " control=" + c.removalReason();
            return new Object[] {converted, reasons, level.difficulty()};
        }).get(30, TimeUnit.SECONDS);
        assertEquals(0, out[0], "generic 죽음인데 예전 공격자(좀비)의 killedEntity 가 불려 주민이 감염됐다");
    }
}
