// /kill: 일반 엔티티(소)를 소유 region 에서 죽이고(generic_kill), @e 여럿·하나·실행 주체 엔티티, 반환값(죽인 수)과 메시지 키.
package org.domi.furnace.server.command.builtin;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
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
        var ch = new EmbeddedChannel();
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
}
