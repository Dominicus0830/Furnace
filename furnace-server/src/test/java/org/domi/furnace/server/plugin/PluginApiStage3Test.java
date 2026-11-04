// 플러그인 API 3단계 이벤트: 스폰 하위 이벤트(투사체·아이템), 폭발(엔티티·블록), 투사체 적중, 길들이기, 번식, 대상 지정,
// 킥, 경험치 구슬, 레벨 변화, 서버 목록 핑. 패킷으로만 일어나는 것(웅크림·달리기·비행·칸·손 바꾸기)은 봇 시험(tools/bot-test/round4).
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.event.block.BlockExplodeEvent;
import org.domi.furnace.api.event.entity.EntityBreedEvent;
import org.domi.furnace.api.event.entity.EntityExplodeEvent;
import org.domi.furnace.api.event.entity.EntitySpawnEvent;
import org.domi.furnace.api.event.entity.EntityTameEvent;
import org.domi.furnace.api.event.entity.EntityTargetEvent;
import org.domi.furnace.api.event.entity.ItemSpawnEvent;
import org.domi.furnace.api.event.entity.ProjectileHitEvent;
import org.domi.furnace.api.event.entity.ProjectileLaunchEvent;
import org.domi.furnace.api.event.player.PlayerExpChangeEvent;
import org.domi.furnace.api.event.player.PlayerKickEvent;
import org.domi.furnace.api.event.server.ServerListPingEvent;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockInfo;
import org.domi.furnace.server.block.Explosion;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.Cow;
import org.domi.furnace.server.entity.ExperienceOrb;
import org.domi.furnace.server.entity.ItemEntity;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.PlayerInventory;
import org.domi.furnace.server.entity.Snowball;
import org.domi.furnace.server.entity.Wolf;
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

class PluginApiStage3Test {

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

    private <T> T at(java.util.function.Function<Region, T> f) throws Exception {
        return core.world().submitAtChunk(C00, f).get(30, TimeUnit.SECONDS);
    }

    private <E extends org.domi.furnace.api.event.Event> void on(Class<E> type, java.util.function.Consumer<? super E> h) {
        host.eventsImpl().subscribe(plugin, type, h);
    }

    private Player player(double x, double y, double z) {
        Player p = new Player(core.world().entityIds().next(), UUID.randomUUID(), "A", new EmbeddedChannel());
        p.inventory(new PlayerInventory());
        p.vitals().env(core.vitalsEnv());
        p.applyMove(x, y, z, 0, 0, true);
        return p;
    }

    // ------------------------------------------------------------------ 스폰 하위 이벤트

    @Test
    void projectileAndItemSpawnAreSubEventsOfEntitySpawn() throws Exception {
        List<String> log = new CopyOnWriteArrayList<>();
        on(EntitySpawnEvent.class, e -> log.add("spawn:" + e.getClass().getSimpleName() + ":" + e.entity().type()));
        on(ProjectileLaunchEvent.class, e -> {
            log.add("launch:" + e.entity().type());
            e.setCancelled(true);
        });
        on(ItemSpawnEvent.class, e -> log.add("item:" + e.entity().type()));
        boolean[] added = at(r -> {
            var level = r.level();
            Snowball s = new Snowball(level, level.nextEntityId());
            s.moveTo(8, -50, 8, 0, 0);
            boolean snow = level.addFreshEntity(s);
            var item = new ItemEntity(level, level.nextEntityId(), 8, -50, 8, B.stack("minecraft:stick", 1));
            boolean it = level.addFreshEntity(item);
            Cow cow = new Cow(level, level.nextEntityId());
            cow.moveTo(8, -50, 8, 0, 0);
            boolean c = level.addFreshEntity(cow);
            return new boolean[] {snow, it, c};
        });
        assertFalse(added[0], "ProjectileLaunchEvent 취소면 넣지 않는다");
        assertTrue(added[1]);
        assertTrue(added[2]);
        assertTrue(log.contains("launch:minecraft:snowball"));
        assertTrue(log.contains("spawn:ProjectileLaunchEvent:minecraft:snowball"), "EntitySpawnEvent 구독자도 투사체를 받는다: " + log);
        assertTrue(log.contains("item:minecraft:item"));
        assertTrue(log.contains("spawn:ItemSpawnEvent:minecraft:item"));
        assertTrue(log.contains("spawn:EntitySpawnEvent:minecraft:cow"));
        assertFalse(log.contains("item:minecraft:cow"));
    }

    // ------------------------------------------------------------------ 폭발

    private static final int STONE = B.defaultState("minecraft:stone");
    private static final int DIRT = B.defaultState("minecraft:dirt");

    private void fillStone(Region r, int cx, int cy, int cz) {
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            r.level().setBlock(new Pos(cx + dx, cy + dy, cz + dz), DIRT, 3);
        }
    }

    @Test
    void entityExplodeCanEditListAndCancel() throws Exception {
        List<String> log = new CopyOnWriteArrayList<>();
        List<BlockPos> rest = new CopyOnWriteArrayList<>();
        Pos keep = new Pos(8, -50, 8);
        on(EntityExplodeEvent.class, e -> {
            log.add(e.entity().type() + ":" + e.location().y());
            assertTrue(e.blocks().remove(new BlockPos(8, -50, 8)), "중심 칸이 목록에 있다");
            rest.addAll(e.blocks());
        });
        boolean[] res = at(r -> {
            fillStone(r, 8, -50, 8);
            Zombie z = new Zombie(r.level(), r.level().nextEntityId());
            z.moveTo(8.5, -50, 8.5, 0, 0);
            r.level().addFreshEntity(z);
            r.level().explode(z, 8.5, -49.5, 8.5, 4.0F, false, Explosion.BlockInteraction.DESTROY);
            boolean kept = r.level().getBlockState(keep) == DIRT;
            boolean othersGone = true;
            for (BlockPos b : rest) othersGone &= BlockInfo.get().isAir(r.level().getBlockState(new Pos(b.x(), b.y(), b.z())));
            return new boolean[] {kept, othersGone};
        });
        assertEquals(List.of("minecraft:zombie:-49.5"), log);
        assertFalse(rest.isEmpty(), "중심 말고도 부서질 칸이 있다");
        assertTrue(res[0], "목록에서 뺀 칸은 남는다");
        assertTrue(res[1], "목록의 나머지는 부서진다");

        host.eventsImpl().unregisterAll(plugin);
        on(EntityExplodeEvent.class, e -> e.setCancelled(true));
        boolean allKept = at(r -> {
            fillStone(r, 3, -50, 12);
            Zombie z = new Zombie(r.level(), r.level().nextEntityId());
            z.moveTo(3.5, -50, 12.5, 0, 0);
            r.level().addFreshEntity(z);
            r.level().explode(z, 3.5, -49.5, 12.5, 4.0F, false, Explosion.BlockInteraction.DESTROY);
            return r.level().getBlockState(new Pos(3, -49, 12)) == DIRT && r.level().getBlockState(new Pos(3, -50, 12)) == DIRT;
        });
        assertTrue(allKept, "취소하면 하나도 부서지지 않는다");
    }

    @Test
    void blockExplodeNamesTheBedAndKeepEntityExplodeQuiet() throws Exception {
        List<String> log = new CopyOnWriteArrayList<>();
        on(BlockExplodeEvent.class, e -> {
            log.add(e.blockType() + "@" + e.pos());
            e.setCancelled(true);
        });
        on(EntityExplodeEvent.class, e -> log.add("entity"));
        boolean kept = at(r -> {
            fillStone(r, 8, -50, 8);
            int bed = B.defaultState("minecraft:red_bed");
            r.level().explodeBadRespawnPoint(8.5, -48.5, 8.5, null, 0.0F, bed);
            return r.level().getBlockState(new Pos(8, -49, 8)) == DIRT;
        });
        assertEquals(List.of("minecraft:red_bed@" + new BlockPos(8, -49, 8)), log);
        assertTrue(kept);
    }

    // ------------------------------------------------------------------ 투사체 적중

    @Test
    void projectileHitCancelLetsSnowballFlyOn() throws Exception {
        List<String> log = new CopyOnWriteArrayList<>();
        on(ProjectileHitEvent.class, e -> {
            log.add(e.entity().type() + ":" + (e.hitBlock() == null ? "-" : e.hitBlock().type() + "/" + e.hitFace()));
            e.setCancelled(true);
        });
        boolean alive = at(r -> {
            r.level().setBlock(new Pos(8, -45, 8), STONE, 3);
            Snowball s = new Snowball(r.level(), r.level().nextEntityId());
            s.moveTo(8.5, -43.5, 8.5, 0, 0);
            s.setDeltaMovement(0, -1.0, 0);
            r.level().addFreshEntity(s);
            for (int i = 0; i < 3; i++) s.tick();
            return !s.isRemoved();
        });
        assertFalse(log.isEmpty());
        assertEquals("minecraft:snowball:minecraft:stone/up", log.get(0));
        assertTrue(alive, "취소하면 눈덩이가 사라지지 않는다");

        host.eventsImpl().unregisterAll(plugin);
        boolean gone = at(r -> {
            Snowball s = new Snowball(r.level(), r.level().nextEntityId());
            s.moveTo(8.5, -43.5, 8.5, 0, 0);
            s.setDeltaMovement(0, -1.0, 0);
            r.level().addFreshEntity(s);
            for (int i = 0; i < 3; i++) s.tick();
            return s.isRemoved();
        });
        assertTrue(gone, "구독자가 없으면 맞고 사라진다");
    }

    // ------------------------------------------------------------------ 길들이기·번식·대상

    @Test
    void tameCancelKeepsWolfWild() throws Exception {
        List<String> log = new CopyOnWriteArrayList<>();
        on(EntityTameEvent.class, e -> {
            log.add(e.entity().type() + ":" + e.owner().name());
            e.setCancelled(true);
        });
        Player p = player(8.5, -60, 8.5);
        p.vitals().setGameMode(org.domi.furnace.server.world.GameMode.CREATIVE);   // 뼈가 줄지 않는다
        boolean tamed = at(r -> {
            r.adopt(p);
            Wolf w = new Wolf(r.level(), r.level().nextEntityId());
            w.moveTo(9, -60, 9, 0, 0);
            r.level().addFreshEntity(w);
            var bone = B.stack("minecraft:bone", 1);
            for (int i = 0; i < 40; i++) w.interact(p, 0, bone, r);
            return w.isTame();
        });
        assertFalse(log.isEmpty(), "40번 중 한 번은 확률에 성공한다");
        assertEquals("minecraft:wolf:A", log.get(0));
        assertFalse(tamed, "취소하면 길들지 않는다");
    }

    @Test
    void breedCancelKeepsParentsInLoveAndNoChild() throws Exception {
        List<String> log = new CopyOnWriteArrayList<>();
        on(EntityBreedEvent.class, e -> {
            log.add(e.entity().type() + ":" + e.mother().type() + ":" + e.father().type() + ":" + (e.breeder() == null));
            e.setCancelled(true);
        });
        Object[] res = at(r -> {
            var level = r.level();
            Cow a = new Cow(level, level.nextEntityId());
            a.moveTo(8, -60, 8, 0, 0);
            Cow b = new Cow(level, level.nextEntityId());
            b.moveTo(9, -60, 8, 0, 0);
            level.addFreshEntity(a);
            level.addFreshEntity(b);
            a.setInLove(null);
            b.setInLove(null);
            int before = level.entities().size();
            a.spawnChildFromBreeding(level, b);
            return new Object[] {a.isInLove() && b.isInLove(), level.entities().size() - before};
        });
        assertEquals(List.of("minecraft:cow:minecraft:cow:minecraft:cow:true"), log);
        assertEquals(true, res[0], "취소하면 사랑 상태가 남는다");
        assertEquals(0, res[1], "새끼가 없다");
    }

    @Test
    void targetEventFiresOnChangeOnlyAndCancelKeepsOldTarget() throws Exception {
        List<String> log = new CopyOnWriteArrayList<>();
        boolean[] cancel = {false};
        on(EntityTargetEvent.class, e -> {
            log.add(e.entity().type() + "->" + (e.target() == null ? "null" : e.target().type()));
            e.setCancelled(cancel[0]);
        });
        Object[] res = at(r -> {
            var level = r.level();
            Zombie z = new Zombie(level, level.nextEntityId());
            z.moveTo(8, -60, 8, 0, 0);
            Cow c = new Cow(level, level.nextEntityId());
            c.moveTo(10, -60, 8, 0, 0);
            level.addFreshEntity(z);
            level.addFreshEntity(c);
            boolean first = z.setTarget(c);
            boolean same = z.setTarget(c);   // 같은 대상: 묻지 않는다
            cancel[0] = true;
            boolean cleared = z.setTarget(null);
            return new Object[] {first, same, cleared, z.getTarget() == c};
        });
        assertEquals(List.of("minecraft:zombie->minecraft:cow", "minecraft:zombie->null"), log);
        assertEquals(true, res[0]);
        assertEquals(true, res[1]);
        assertEquals(false, res[2], "취소되면 false");
        assertEquals(true, res[3], "취소되면 이전 대상이 남는다");
    }

    // ------------------------------------------------------------------ 플레이어

    @Test
    void kickEventChangesReasonOrCancels() throws Exception {
        on(PlayerKickEvent.class, e -> {
            if (e.reason().plainText().equals("stay")) e.setCancelled(true);
            else e.setReason(Text.text("bye!"));
        });
        var ch = new EmbeddedChannel();
        Player p = new Player(core.world().entityIds().next(), UUID.randomUUID(), "A", ch);
        p.inventory(new PlayerInventory());
        p.vitals().env(core.vitalsEnv());
        p.kick(new org.domi.furnace.server.net.protocol.play.Disconnect(Text.text("stay")));
        ch.runPendingTasks();
        assertTrue(ch.isOpen(), "취소하면 끊지 않는다");
        p.kick(new org.domi.furnace.server.net.protocol.play.Disconnect(Text.text("go")));
        ch.runPendingTasks();
        assertFalse(ch.isOpen());
        assertEquals("bye!", p.kickReason().plainText(), "바뀐 사유로 끊는다");
    }

    @Test
    void expOrbAmountCanBeChanged() throws Exception {
        on(PlayerExpChangeEvent.class, e -> e.setAmount(e.amount() * 10));
        Player p = player(8.5, -60, 8.5);
        int total = at(r -> {
            r.adopt(p);
            var orb = new ExperienceOrb(r.level(), r.level().nextEntityId(), 8.5, -60, 8.5, 3);
            r.level().addFreshEntity(orb);
            p.takeXpDelay = 0;
            orb.playerTouch(p);
            return p.vitals().xpTotal();
        });
        assertEquals(30, total);
    }

    // ------------------------------------------------------------------ 서버 목록

    @Test
    void serverListPingOverridesMotdAndCounts() throws Exception {
        var ctx = org.domi.furnace.server.net.ServerLoginContextAccess.offline("hello");
        String plain = org.domi.furnace.server.net.ServerLoginContextAccess.statusJson(ctx, host.gameEvents());
        assertTrue(plain.contains("\"description\":\"hello\""), plain);
        on(ServerListPingEvent.class, e -> {
            assertEquals("hello", e.motd().plainText());
            e.setMotd(Text.text("plugin motd"));
            e.setMaxPlayers(5);
            e.setNumPlayers(3);
        });
        String json = org.domi.furnace.server.net.ServerLoginContextAccess.statusJson(ctx, host.gameEvents());
        assertTrue(json.contains("plugin motd"), json);
        assertTrue(json.contains("\"max\":5"), json);
        assertTrue(json.contains("\"online\":3"), json);
    }
}
