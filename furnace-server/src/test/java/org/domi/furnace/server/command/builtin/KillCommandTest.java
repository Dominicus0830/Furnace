// /kill: 일반 엔티티(소)를 소유 region 에서 죽이고(generic_kill), @e 여럿·하나·실행 주체 엔티티, 반환값(죽인 수)과 메시지 키.
package org.domi.furnace.server.command.builtin;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.entity.ArmorStand;
import org.domi.furnace.server.entity.Cow;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KillCommandTest {

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

    private Cow[] cows(int n, String tag) throws InterruptedException {
        Cow[] out = new Cow[n];
        var done = new CountDownLatch(1);
        core.world().runAt(0.5, 0.5, region -> {
            var lvl = region.level();
            for (int i = 0; i < n; i++) {
                out[i] = new Cow(lvl, lvl.nextEntityId());
                out[i].moveTo(0.5 + i, -60, 0.5, 0, 0);
                if (tag != null) out[i].addTag(tag);
                lvl.addFreshEntity(out[i]);
            }
            done.countDown();
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        return out;
    }

    /** 플레이어 채널로 나간 패킷 바이트에 그 문자열이 있는가(시스템 채팅 본문의 번역 키). */
    private static boolean sent(EmbeddedChannel ch, String needle) {
        ch.runPendingTasks();
        boolean found = false;
        Object o;
        while ((o = ch.readOutbound()) != null) {
            if (o instanceof ByteBuf bb) {
                if (bb.toString(StandardCharsets.ISO_8859_1).contains(needle)) found = true;
                bb.release();
            }
        }
        return found;
    }

    @Test
    void killsOrdinaryEntitiesOnTheirRegion_returnsCount_andSendsVanillaMessages() throws Exception {
        Cow[] all = cows(3, "herd");
        var ch = new ConcurrentEmbeddedChannel();
        var p = new Player(10, UUID.randomUUID(), "Steve", ch);
        p.applyMove(0, -60, 0, 0, 0, true);
        var src = CommandSource.player(core, p).withPermission(2);
        sent(ch, "");
        // @e 여럿: 반환값 = 대상 수, multiple 메시지
        assertEquals(3, core.commands().performPrefixedCommandAsync(src, "kill @e[type=cow,tag=herd]").join().result());
        for (Cow c : all) {
            assertTrue(c.isDeadOrDying(), "generic_kill 피해로 체력 0");
            assertEquals("generic_kill", c.getLastDamageType());
        }
        assertTrue(sent(ch, "commands.kill.success.multiple"));
        // 하나: single 메시지
        Cow[] one = cows(1, "solo");
        assertEquals(1, core.commands().performPrefixedCommandAsync(src, "kill @e[type=cow,tag=solo]").join().result());
        assertTrue(one[0].isDeadOrDying());
        assertTrue(sent(ch, "commands.kill.success.single"));
        // 대상이 없으면 바닐라 "No entity was found"
        var r = core.commands().performPrefixedCommandAsync(src, "kill @e[type=cow,tag=nobody]").join();
        assertEquals(0, r.result());
    }

    @Test
    void killWithoutArgumentsKillsTheExecutingEntity() throws Exception {
        Cow[] c = cows(1, null);
        var src = CommandSource.console(core).withEntity(c[0]);
        assertEquals(1, core.commands().performPrefixedCommandAsync(src, "kill").join().result());
        assertTrue(c[0].isDeadOrDying());
        // 실행 주체가 엔티티가 아니면 permissions.requires.entity
        var d = core.commands().getDispatcher();
        assertEquals("permissions.requires.entity", assertThrows(org.domi.furnace.server.command.brigadier.CommandSyntaxException.class,
                () -> d.execute("kill", CommandSource.console(core))).getRawMessage().translationKey());
    }

    @Test
    void nonLivingEntityIsRemovedAsKilled() throws Exception {
        ArmorStand[] s = new ArmorStand[1];
        var done = new CountDownLatch(1);
        core.world().runAt(0.5, 0.5, region -> {
            var lvl = region.level();
            s[0] = new ArmorStand(lvl, lvl.nextEntityId());
            s[0].moveTo(2.5, -60, 2.5, 0, 0);
            lvl.addFreshEntity(s[0]);
            done.countDown();
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        assertEquals(1, core.commands().performPrefixedCommandAsync(CommandSource.console(core), "kill @e[type=armor_stand]").join().result());
        assertEquals(Entity.RemovalReason.KILLED, s[0].removalReason());
    }

    @Test
    void resistanceDoesNotSaveAMob_andEntityDieGameEventIsPosted() throws Exception {
        java.util.List<String> heard = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        Cow[] c = new Cow[1];
        var done = new CountDownLatch(1);
        core.world().runAt(0.5, 0.5, region -> {
            var lvl = region.level();
            // 진동 리스너 대신: 그 칸에서 듣는 블록 리스너(반경 8)
            var pos = new org.domi.furnace.server.block.Pos(0, -60, 0);
            region.chunkAtBlocking(new org.domi.furnace.server.world.ChunkPos(0, 0));
            org.domi.furnace.server.world.gameevent.GameEventDispatcher.registerBlockListener(lvl.listenerChunk(0, 0), -60,
                    new org.domi.furnace.server.world.gameevent.GameEventListener() {
                        public org.domi.furnace.server.world.gameevent.PositionSource getListenerSource() {
                            return new org.domi.furnace.server.world.gameevent.PositionSource.BlockSource(pos);
                        }
                        public int getListenerRadius() { return 8; }
                        public boolean handleGameEvent(org.domi.furnace.server.block.ChunkLevel l, org.domi.furnace.server.world.gameevent.GameEvent ev,
                                                       org.domi.furnace.server.world.gameevent.GameEventContext ctx, org.domi.furnace.server.entity.Vec3 at) {
                            heard.add(ev.name);
                            return false;
                        }
                    });
            c[0] = new Cow(lvl, lvl.nextEntityId());
            c[0].moveTo(1.5, -60, 1.5, 0, 0);
            lvl.addFreshEntity(c[0]);
            c[0].addEffect(new org.domi.furnace.server.effect.MobEffectInstance(org.domi.furnace.server.effect.MobEffect.RESISTANCE, 1000, 4));
            done.countDown();
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        assertEquals(1, core.commands().performPrefixedCommandAsync(CommandSource.console(core), "kill @e[type=cow]").join().result());
        assertTrue(c[0].isDeadOrDying(), "저항 V 도 generic_kill(bypasses_resistance) 을 막지 못한다");
        assertTrue(heard.stream().anyMatch(n -> n.endsWith("entity_die")), "죽음에 entity_die 게임 이벤트: " + heard);
    }

    @Test
    void creativePlayerIsKilledOnItsRegion_mixedWithEntities() throws Exception {
        Cow[] c = cows(1, null);
        var p = new Player(core.world().entityIds().next(), UUID.randomUUID(), "Steve", new ConcurrentEmbeddedChannel());
        p.inventory(new org.domi.furnace.server.entity.PlayerInventory());
        p.applyMove(0.5, -60, 0.5, 0, 0, true);
        var in = new CountDownLatch(1);
        Thread[] owner = new Thread[1];
        core.world().runAt(0.5, 0.5, r -> {
            r.adopt(p);
            p.vitals().setGameMode(org.domi.furnace.server.world.GameMode.CREATIVE);
            owner[0] = Thread.currentThread();
            in.countDown();
        });
        assertTrue(in.await(10, TimeUnit.SECONDS));
        var src = CommandSource.console(core);
        // 플레이어와 소를 함께: 둘 다 소유 region 에서 죽고 값은 2
        assertEquals(2, EntityOpsKill.kill(src, java.util.List.of(p, c[0])));
        assertTrue(p.vitals().isDeadOrDying(), "창조 모드여도 generic_kill 은 무적을 무시한다");
        assertTrue(c[0].isDeadOrDying());
    }

    /** PlayerStateCommands.kill 의 결과를 기다린다. */
    static final class EntityOpsKill {
        static int kill(CommandSource s, java.util.List<org.domi.furnace.server.entity.Rider> t) throws Exception {
            return PlayerStateCommands.kill(s, t).get(10, TimeUnit.SECONDS);
        }
    }
}
