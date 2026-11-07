// 원인 사슬(B4): 플레이어→TNT→TNT→폭발, 레드스톤·발사기 점화, 피해, 침대 폭발, 구독이 없을 때 기록하지 않음.
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.event.block.BlockBreakEvent;
import org.domi.furnace.api.event.block.BlockExplodeEvent;
import org.domi.furnace.api.event.cause.CauseChain;
import org.domi.furnace.api.event.cause.CauseEntry;
import org.domi.furnace.api.event.entity.EntityDamageEvent;
import org.domi.furnace.api.event.entity.EntityExplodeEvent;
import org.domi.furnace.api.event.entity.EntitySpawnEvent;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.PlayerActor;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.AABB;
import org.domi.furnace.server.entity.Cow;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.PlayerInventory;
import org.domi.furnace.server.entity.PrimedTnt;
import org.domi.furnace.server.entity.Snowball;
import org.domi.furnace.server.entity.Zombie;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.Region;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CauseChainTest {

    private static final ChunkPos C00 = new ChunkPos(0, 0);
    private static final int TNT = B.defaultState("minecraft:tnt");
    private static final int STONE = B.defaultState("minecraft:stone");

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

    private <T> T at(java.util.function.Function<Region, T> f) throws Exception {
        return core.world().submitAtChunk(C00, f).get(30, TimeUnit.SECONDS);
    }

    private <E extends org.domi.furnace.api.event.Event> void on(Class<E> type, java.util.function.Consumer<? super E> h) {
        host.eventsImpl().subscribe(plugin, type, h);
    }

    private Player player(double x, double y, double z) {
        Player p = new Player(core.world().entityIds().next(), UUID.randomUUID(), "Steve", new EmbeddedChannel());
        p.inventory(new PlayerInventory());
        p.vitals().env(core.vitalsEnv());
        p.applyMove(x, y, z, 0, 0, true);
        return p;
    }

    /** "종류:행동" 목록(가까운 쪽부터). */
    private static List<String> shape(CauseChain c) {
        return c.entries().stream().map(e -> e.type() + ":" + e.action()).toList();
    }

    private static PrimedTnt tntNear(ChunkLevel level, Pos pos) {
        var found = level.getEntities(null, new AABB(pos.x() - 1, pos.y() - 1, pos.z() - 1, pos.x() + 2, pos.y() + 2, pos.z() + 2), e -> e instanceof PrimedTnt);
        return found.isEmpty() ? null : (PrimedTnt) found.get(0);
    }

    private static void floor(ChunkLevel level, int y) {
        for (int x = 2; x <= 14; x++) for (int z = 2; z <= 14; z++) level.setBlock(new Pos(x, y, z), STONE, 3);
    }

    // ------------------------------------------------------------------ 플레이어 → TNT → TNT → 폭발

    @Test
    void playerLitTntChainReachesSecondExplosion() throws Exception {
        List<CauseChain> explodes = new CopyOnWriteArrayList<>();
        List<CauseChain> spawns = new CopyOnWriteArrayList<>();
        on(EntityExplodeEvent.class, e -> explodes.add(e.causeChain()));
        on(EntitySpawnEvent.class, e -> { if (e.entity().type().equals("minecraft:tnt")) spawns.add(e.causeChain()); });
        Player p = player(8.5, -49, 4.5);
        Object owner2 = at(r -> {
            ChunkLevel level = r.level();
            floor(level, -51);
            Pos a = new Pos(8, -50, 8), b = new Pos(8, -50, 10);
            level.setBlock(a, TNT, 3);
            level.setBlock(b, TNT, 3);
            var flint = B.stack("minecraft:flint_and_steel", 1);
            var actor = new PlayerActor(p, flint, org.domi.furnace.server.entity.ItemStack.EMPTY, 0);
            B.behavior(TNT).useItemOn(flint, TNT, level, a, actor, 0, new Hit(a, Dir.UP, 8.5, -49, 8.5, false));
            PrimedTnt t1 = tntNear(level, a);
            t1.setFuse(1);
            t1.tick();   // 터지며 b 의 TNT 를 휘말리게 한다
            PrimedTnt t2 = tntNear(level, b);
            Object o = t2.getOwner();
            t2.setFuse(1);
            t2.tick();
            return o;
        });
        assertEquals(2, spawns.size(), "TNT 두 개가 스폰 이벤트를 거친다: " + spawns);
        assertEquals(List.of("minecraft:player:ignite"), shape(spawns.get(0)));
        assertEquals(List.of("minecraft:tnt:explode", "minecraft:player:ignite"), shape(spawns.get(1)), "휘말린 TNT 의 원인 = 앞 폭발");
        assertEquals(2, explodes.size());
        assertEquals(List.of("minecraft:tnt:explode", "minecraft:player:ignite"), shape(explodes.get(0)));
        assertEquals(List.of("minecraft:tnt:explode", "minecraft:tnt:explode", "minecraft:player:ignite"), shape(explodes.get(1)));
        CauseEntry who = explodes.get(1).player().orElseThrow();
        assertEquals("Steve", who.name());
        assertEquals(p.uuid(), who.uuid());
        assertEquals(new org.domi.furnace.api.world.BlockPos(8, -50, 8), explodes.get(1).entries().get(1).pos(), "앞 TNT 가 터진 칸");
        // 바닐라 TntBlock.wasExploded: 휘말린 TNT 의 주인 = 폭발의 간접 원인(앞 TNT 의 주인)
        assertSame(p, owner2);
    }

    // ------------------------------------------------------------------ 레드스톤·발사기

    @Test
    void redstoneAndDispenserIgnitionNameTheBlock() throws Exception {
        List<CauseChain> spawns = new CopyOnWriteArrayList<>();
        on(EntitySpawnEvent.class, e -> { if (e.entity().type().equals("minecraft:tnt")) spawns.add(e.causeChain()); });
        at(r -> {
            ChunkLevel level = r.level();
            floor(level, -51);
            Pos a = new Pos(5, -50, 5);
            level.setBlock(a, TNT, 3);
            level.setBlock(a.relative(Dir.EAST), B.defaultState("minecraft:redstone_block"), 3);
            return null;
        });
        assertEquals(1, spawns.size(), spawns.toString());
        assertEquals(List.of("minecraft:tnt:redstone"), shape(spawns.get(0)));
        assertEquals(new org.domi.furnace.api.world.BlockPos(5, -50, 5), spawns.get(0).direct().orElseThrow().pos());

        spawns.clear();
        at(r -> {
            ChunkLevel level = r.level();
            Pos d = new Pos(11, -50, 11);
            level.setBlock(d, B.defaultState("minecraft:dispenser"), 3);   // 기본 facing=north
            var be = (org.domi.furnace.server.block.entity.DispenserBlockEntity) level.getBlockEntity(d);
            var stack = B.stack("minecraft:tnt", 1);
            be.items[0] = stack;
            int st = level.getBlockState(d);
            org.domi.furnace.server.block.dispenser.DispenseBehaviors.get(stack)
                    .dispense(new org.domi.furnace.server.block.dispenser.BlockSource(level, d, st, be, 0), stack);
            return null;
        });
        assertEquals(1, spawns.size(), spawns.toString());
        assertEquals(List.of("minecraft:dispenser:dispense"), shape(spawns.get(0)));
    }

    // ------------------------------------------------------------------ 피해

    @Test
    void damageChainNamesProjectileShooterAndTntIgniter() throws Exception {
        List<String> log = new CopyOnWriteArrayList<>();
        on(EntityDamageEvent.class, e -> log.add(e.entity().type() + "|" + e.cause() + "|" + String.join(",", shape(e.causeChain()))));
        Player p = player(8.5, -49, 2.5);
        at(r -> {
            ChunkLevel level = r.level();
            floor(level, -51);
            Zombie z = new Zombie(level, level.nextEntityId());
            z.moveTo(3.5, -50, 3.5, 0, 0);
            level.addFreshEntity(z);
            Cow cow = new Cow(level, level.nextEntityId());
            cow.moveTo(12.5, -50, 12.5, 0, 0);
            level.addFreshEntity(cow);
            Snowball s = new Snowball(level, level.nextEntityId());
            s.moveTo(12.5, -49, 12.5, 0, 0);
            s.setOwner(z);
            cow.hurtByVia(new Entity.Damage("thrown", false, false, false), 1.0F, z, s);
            cow.invulnerableTime = 0;
            // 플레이어가 켠 TNT 가 소 옆에서 터진다
            level.spawnPrimedTnt(new Pos(12, -50, 11), new PlayerActor(p), 1, Causes.of(p, CauseEntry.IGNITE));
            PrimedTnt t = tntNear(level, new Pos(12, -50, 11));
            t.tick();
            return null;
        });
        assertTrue(log.contains("minecraft:cow|minecraft:thrown|minecraft:snowball:damage,minecraft:zombie:shoot"), log.toString());
        assertTrue(log.stream().anyMatch(l -> l.startsWith("minecraft:cow|minecraft:explosion|minecraft:tnt:damage,minecraft:player:ignite")), log.toString());
    }

    // ------------------------------------------------------------------ 블록 폭발

    @Test
    void bedExplosionNamesTheUser() throws Exception {
        List<CauseChain> log = new CopyOnWriteArrayList<>();
        on(BlockExplodeEvent.class, e -> {
            log.add(e.causeChain());
            e.setCancelled(true);
        });
        Player p = player(8.5, -49, 4.5);
        at(r -> {
            int bed = B.defaultState("minecraft:red_bed");
            r.level().explodeBadRespawnPoint(8.5, -48.5, 8.5, null, 0.0F, bed, Causes.of(p, CauseEntry.USE));
            r.level().explodeBadRespawnPoint(8.5, -48.5, 8.5, null, 0.0F, bed);
            return null;
        });
        assertEquals(List.of("minecraft:red_bed:explode", "minecraft:player:use"), shape(log.get(0)));
        assertEquals(List.of("minecraft:red_bed:explode"), shape(log.get(1)), "쓴 쪽을 모르면 블록만");
    }

    // ------------------------------------------------------------------ 구독이 없으면 기록하지 않는다

    @Test
    void nothingIsRecordedWithoutCauseSubscribers() throws Exception {
        on(BlockBreakEvent.class, e -> {});   // 원인 사슬이 있지만 엔티티 기록이 필요 없는 이벤트
        Object[] res = at(r -> {
            ChunkLevel level = r.level();
            boolean tracking = level.gameEvents().tracksCause();
            Pos a = new Pos(5, -50, 5);
            level.setBlock(a, TNT, 3);
            level.setBlock(a.relative(Dir.EAST), B.defaultState("minecraft:redstone_block"), 3);
            PrimedTnt t = tntNear(level, a);
            return new Object[] {tracking, t.origin()};
        });
        assertFalse((Boolean) res[0]);
        assertSame(CauseChain.NONE, res[1], "기록하지 않았고 주인도 없다");

        on(EntityExplodeEvent.class, e -> {});
        assertTrue(at(r -> (Boolean) r.level().gameEvents().tracksCause()).booleanValue(), "폭발 이벤트를 받으면 켜진다");
        host.eventsImpl().unregisterAll(plugin);
        assertFalse(at(r -> (Boolean) r.level().gameEvents().tracksCause()).booleanValue(), "다 풀면 다시 꺼진다");
    }

    @Test
    void untrackedTntStillKnowsVanillaOwner() throws Exception {
        Player p = player(8.5, -49, 4.5);
        CauseChain c = at(r -> {
            ChunkLevel level = r.level();
            level.spawnPrimedTnt(new Pos(8, -50, 8), new PlayerActor(p), -1, null);
            PrimedTnt t = tntNear(level, new Pos(8, -50, 8));
            return t.origin();
        });
        assertEquals(List.of("minecraft:player:ignite"), shape(c), "적힌 원인이 없으면 바닐라 주인으로");
    }
}
