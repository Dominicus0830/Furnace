// /damage: 플레이어 대상(PlayerVitals.hurt 경로·무적이면 invulnerable), 실제 region 의 소 대상(at·by), 없는 유형 오류 키.
package org.domi.furnace.server.command.builtin;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.entity.Cow;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.PlayerVitals;
import org.domi.furnace.server.entity.damage.DamageSource;
import org.domi.furnace.server.entity.damage.DamageType;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.Difficulty;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DamageCommandTest {

    @TempDir Path tmp;
    FurnaceSchedulerImpl sched;
    ServerCore core;

    @BeforeEach
    void setUp() {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
    }

    @AfterEach
    void tearDown() { sched.close(); }

    private static String key(CommandSyntaxException e) { return e.getRawMessage().translationKey(); }

    private static final java.util.List<Text> broadcasts = new java.util.concurrent.CopyOnWriteArrayList<>();

    private static Player player(GameMode mode) {
        Player p = new Player(21, UUID.randomUUID(), "Steve", new ConcurrentEmbeddedChannel());
        p.vitals().env(new PlayerVitals.Env() {
            public Difficulty difficulty() { return Difficulty.NORMAL; }
            public boolean gameRule(String name) { return !name.equals("keepInventory") && !name.equals("doImmediateRespawn"); }
            public boolean pvp() { return true; }
            public void broadcastSystemMessage(Text message) { broadcasts.add(message); }
            public void broadcastAll(Packet packet) {}
            public void broadcastToTrackers(int entityId, Packet packet) {}
        });
        p.vitals().loadGameTypes(null, mode, false);
        return p;
    }

    /** 플레이어 대상은 주인 홉이 없어 끝난 future — 껍데기로 값/오류를 바로 꺼낸다. */
    private static int applyNow(CommandSource s, org.domi.furnace.server.entity.Rider t, float amount, DamageSource src) throws CommandSyntaxException {
        return org.domi.furnace.server.command.EntityOps.result(s, DamageCommand.apply(s, t, amount, src));
    }

    @Test
    void playerTargetGoesThroughVitals() throws Exception {
        var console = CommandSource.console(core);
        Player p = player(GameMode.SURVIVAL);
        // 막 들어온 플레이어는 스폰 무적(60틱)이라 pvp 서버의 낙하만 통한다(바닐라 ServerPlayer.isInvulnerableTo)
        assertEquals("commands.damage.invulnerable",
                key(assertThrows(CommandSyntaxException.class, () -> applyNow(console, p, 4.0f, DamageSource.of(DamageType.MAGIC)))));
        assertEquals(1, applyNow(console, p, 4.0f, DamageSource.of(DamageType.FALL)));
        assertEquals(16.0f, p.vitals().health());
        // 크리에이티브는 막힌다 → invulnerable
        Player c = player(GameMode.CREATIVE);
        assertEquals("commands.damage.invulnerable",
                key(assertThrows(CommandSyntaxException.class, () -> applyNow(console, c, 4.0f, DamageSource.of(DamageType.MAGIC)))));
        // generic_kill 은 무적도 뚫는다
        assertEquals(1, applyNow(console, c, 4.0f, DamageSource.of(DamageType.GENERIC_KILL)));
    }

    @Test
    void deathByEntityUsesItsName() throws Exception {
        var console = CommandSource.console(core);
        broadcasts.clear();
        Player p = player(GameMode.SURVIVAL), killer = player(GameMode.SURVIVAL);
        applyNow(console, p, 100.0f, DamageSource.by(DamageType.FALL, killer));
        Text m = broadcasts.get(broadcasts.size() - 1);
        assertEquals("death.attack.fall", m.translationKey());
        assertEquals(java.util.List.of(p.displayName(), killer.displayName()), ((org.domi.furnace.api.text.Content.Translatable) m.content()).args());
    }

    @Test
    void cowTargetOnRegion() throws Exception {
        Cow[] cow = new Cow[1];
        var done = new CountDownLatch(1);
        core.world().runAt(0.5, 0.5, region -> {
            var lvl = region.level();
            cow[0] = new Cow(lvl, lvl.nextEntityId());
            cow[0].moveTo(0.5, -60, 0.5, 0, 0);
            lvl.addFreshEntity(cow[0]);
            cow[0].addTag("t");
            done.countDown();
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        var d = core.commands().getDispatcher();
        var console = CommandSource.console(core);
        float h = cow[0].health();
        core.commands().performPrefixedCommandAsync(console, "damage @e[tag=t,limit=1] 3 minecraft:magic at 5 -60 0.5").join();
        TeleportCommandTest.awaitTrue(() -> cow[0].health() == h - 3);
    }

    @Test
    void killCreditExpiresAfter100Ticks() throws Exception {
        var console = CommandSource.console(core);
        Player killer = player(GameMode.SURVIVAL);
        // 100틱 안: 원인 없는 피해(굶주림)로 죽어도 .player
        broadcasts.clear();
        Player p = player(GameMode.SURVIVAL);
        applyNow(console, p, 1.0f, DamageSource.by(DamageType.FALL, killer));
        for (int i = 0; i < 50; i++) p.vitals().tick(SURROUNDINGS);
        p.vitals().hurt(DamageType.GENERIC_KILL, 100.0f);
        assertEquals("death.attack.genericKill.player", broadcasts.get(broadcasts.size() - 1).translationKey());
        // 100틱 넘게 지나면 기록이 지워져 .player 가 붙지 않는다
        broadcasts.clear();
        Player q = player(GameMode.SURVIVAL);
        applyNow(console, q, 1.0f, DamageSource.by(DamageType.FALL, killer));
        for (int i = 0; i < 120; i++) q.vitals().tick(SURROUNDINGS);
        assertNull(q.getKillCredit());
        q.vitals().hurt(DamageType.GENERIC_KILL, 100.0f);
        assertEquals("death.attack.genericKill", broadcasts.get(broadcasts.size() - 1).translationKey());
    }

    private static final PlayerVitals.Surroundings SURROUNDINGS = new PlayerVitals.Surroundings(true, false, false, false, null, false, -64);
}
