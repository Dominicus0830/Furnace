// /spectate: 오류(자기 자신·관전 모드 아님), 시점 전환의 패킷 순서(위치 → set_camera → 메시지), 틱마다 대상 따라가기,
// 웅크리기·대상 사라짐·관전 모드 해제로 되돌아오기, 대상 생략 시 해제.
package org.domi.furnace.server.command.builtin;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.entity.Cow;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.play.SetCamera;
import org.domi.furnace.server.net.protocol.play.SyncPlayerPosition;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpectateCommandTest {

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

    /** 나간 패킷을 순서대로 "pos"·"camera:<id>"·"msg:<번역 키 일부>"·"id:<hex>" 로 푼다. */
    private static List<String> outbound(EmbeddedChannel ch, String... msgKeys) {
        ch.runPendingTasks();
        List<String> out = new ArrayList<>();
        Object o;
        // 알 수 없는 패킷(game_event 등 게임 모드 변경이 보내는 것)은 순서 비교에서 뺀다
        while ((o = ch.readOutbound()) != null) {
            if (!(o instanceof ByteBuf bb)) continue;
            String text = bb.toString(StandardCharsets.ISO_8859_1);
            int id = Types.readVarInt(bb);
            if (id == SetCamera.ID) {
                out.add("camera:" + Types.readVarInt(bb));
            } else if (id == SyncPlayerPosition.ID) {
                out.add("pos");
            } else {
                String found = null;
                for (String k : msgKeys) if (text.contains(k)) found = k;
                if (found != null) out.add("msg:" + found);
            }
            bb.release();
        }
        return out;
    }

    /** 마지막 보정 텔레포트 {x,y,z,yaw,pitch}. */
    volatile double[] lastTp;

    private Player spectator(EmbeddedChannel ch, double x, double z) throws Exception { return spectator(ch, "Spec", x, z); }

    private Player spectator(EmbeddedChannel ch, String name, double x, double z) throws Exception {
        var world = core.world();
        var p = new Player(world.entityIds().next(), UUID.randomUUID(), name, ch);
        p.vitals().setGameMode(GameMode.SPECTATOR);
        p.applyMove(x, -60, z, 0, 0, true);
        // 연결의 보정 텔레포트 대신 위치 패킷을 보내고 서버 위치를 맞춘다(연결 스레드가 하는 일)
        p.teleporter(t -> {
            lastTp = t;
            p.send(new SyncPlayerPosition(t[0], t[1], t[2], t.length > 3 ? (float) t[3] : 0f, t.length > 4 ? (float) t[4] : 0f, 1));
        });
        world.playerOnline(p);
        world.submitAtChunk(ChunkPos.ofBlock(x, z), r -> {
            r.adopt(p);
            return null;
        }).get();
        return p;
    }

    private Cow cow(double x, double z) throws Exception {
        return core.world().submitAtChunk(ChunkPos.ofBlock(x, z), r -> {
            var lvl = r.level();
            var c = new Cow(lvl, lvl.nextEntityId());
            c.moveTo(x, -60, z, 90f, 10f);
            lvl.addFreshEntity(c);
            return c;
        }).get();
    }

    private static void awaitTrue(java.util.function.BooleanSupplier c) throws InterruptedException {
        long deadline = System.nanoTime() + 30_000_000_000L;
        while (!c.getAsBoolean()) {
            assertTrue(System.nanoTime() < deadline, "조건이 30초 안에 참이 되지 않았다");
            Thread.sleep(5);
        }
    }

    @Test
    void errorsMatchVanilla() throws Exception {
        var ch = new ConcurrentEmbeddedChannel();
        var p = spectator(ch, 0.5, 0.5);
        var other = cow(2.5, 0.5);
        var src = CommandSource.player(core, p).withPermission(2);
        outbound(ch);
        // 자기 자신 — 대상 선택자 @s 가 관전자 자신
        core.commands().performPrefixedCommandAsync(src, "spectate @s").join();
        assertEquals(List.of("msg:commands.spectate.self"), outbound(ch, "commands.spectate.self"));
        assertNull(p.cameraTarget());
        // 관전 모드가 아니면 실패(표시 이름이 인자)
        p.vitals().setGameMode(GameMode.SURVIVAL);
        core.commands().performPrefixedCommandAsync(src, "spectate @e[type=cow,limit=1]").join();
        assertEquals(List.of("msg:commands.spectate.not_spectator"), outbound(ch, "commands.spectate.not_spectator"));
        assertNull(p.cameraTarget());
        // 플레이어가 아닌 실행 주체(콘솔)는 대상 생략형을 쓸 수 없다
        var console = CommandSource.console(core);
        assertFalse(core.commands().performPrefixedCommandAsync(console, "spectate").join().success());
        assertNotNull(other);
    }

    @Test
    void switchSendsPositionThenCameraThenMessage_andStopRestoresSelf() throws Exception {
        var ch = new ConcurrentEmbeddedChannel();
        var p = spectator(ch, 0.5, 0.5);
        var cow = cow(4.5, 0.5);
        var src = CommandSource.player(core, p).withPermission(2);
        outbound(ch);
        core.commands().performPrefixedCommandAsync(src, "spectate @e[type=cow,limit=1]").join();
        assertSame(cow, p.cameraTarget());
        assertSame(cow, p.cameraOrSelf());
        // 바닐라 순서: 보정 텔레포트 → set_camera(대상 id) → 시작 메시지
        assertEquals(List.of("pos", "camera:" + cow.id(), "msg:commands.spectate.success.started"),
                outbound(ch, "commands.spectate.success.started"));
        // 같은 대상을 또 지정해도 카메라가 그대로라 위치·카메라 패킷이 없다(메시지만)
        core.commands().performPrefixedCommandAsync(src, "spectate @e[type=cow,limit=1]").join();
        assertEquals(List.of("msg:commands.spectate.success.started"), outbound(ch, "commands.spectate.success.started"));
        // 대상 생략 = 해제: 자기 자리로 텔레포트 → set_camera(자기 id) → 해제 메시지
        core.commands().performPrefixedCommandAsync(src, "spectate").join();
        assertNull(p.cameraTarget());
        assertEquals(List.of("pos", "camera:" + p.entityId(), "msg:commands.spectate.success.stopped"),
                outbound(ch, "commands.spectate.success.stopped"));
        // 이미 자기 시점이면 메시지만
        core.commands().performPrefixedCommandAsync(src, "spectate").join();
        assertEquals(List.of("msg:commands.spectate.success.stopped"), outbound(ch, "commands.spectate.success.stopped"));
    }

    @Test
    void correctionTeleportKeepsTheSpectatorsOwnRotation() throws Exception {
        var ch = new ConcurrentEmbeddedChannel();
        var p = spectator(ch, 0.5, 0.5);
        p.applyMove(0.5, -60, 0.5, 33f, 7f, true);
        var cow = cow(4.5, 0.5);   // 소의 회전은 90/10
        var src = CommandSource.player(core, p).withPermission(2);
        core.commands().performPrefixedCommandAsync(src, "spectate @e[type=cow,limit=1]").join();
        assertSame(cow, p.cameraTarget());
        assertEquals(4.5, lastTp[0], 1e-9);
        assertEquals(33.0, lastTp[3], 1e-6, "대상(90)이 아니라 관전자 자신의 회전");
        assertEquals(7.0, lastTp[4], 1e-6);
        core.commands().performPrefixedCommandAsync(src, "spectate").join();
        assertEquals(33.0, lastTp[3], 1e-6);
    }

    @Test
    void thirdArgumentSelectsWhichPlayerSpectates() throws Exception {
        var a = spectator(new ConcurrentEmbeddedChannel(), "Alpha", 0.5, 0.5);
        var chB = new ConcurrentEmbeddedChannel();
        var b = spectator(chB, "Bravo", 1.5, 0.5);
        var cow = cow(3.5, 0.5);
        var console = CommandSource.console(core).withPermission(2);
        outbound(chB);
        core.commands().performPrefixedCommandAsync(console, "spectate @e[type=cow,limit=1] Bravo").join();
        assertSame(cow, b.cameraTarget());
        assertNull(a.cameraTarget(), "인자로 고른 플레이어만 시점이 바뀐다");
        assertEquals(List.of("pos", "camera:" + cow.id()), outbound(chB));
        // 관전 모드가 아닌 플레이어를 고르면 그 플레이어 이름으로 실패
        a.vitals().setGameMode(GameMode.SURVIVAL);
        assertFalse(core.commands().performPrefixedCommandAsync(console, "spectate @e[type=cow,limit=1] Alpha").join().success());
        assertNull(a.cameraTarget());
    }

    @Test
    void cameraFollowsTargetEveryTick_andSneakReturnsToSelf() throws Exception {
        var world = core.world();
        var ch = new ConcurrentEmbeddedChannel();
        var p = spectator(ch, 0.5, 0.5);
        var cow = cow(4.5, 0.5);
        var src = CommandSource.player(core, p).withPermission(2);
        core.commands().performPrefixedCommandAsync(src, "spectate @e[type=cow,limit=1]").join();
        outbound(ch);
        world.startTicking();
        // 대상이 움직이면 관전자의 서버 쪽 위치가 따라간다(패킷 없이)
        world.submitAtChunk(ChunkPos.ofBlock(cow.x(), cow.z()), r -> {
            cow.moveTo(6.5, -60, 2.5, 45f, 5f);
            return null;
        }).get();
        awaitTrue(() -> Math.abs(p.x() - 6.5) < 1e-9 && Math.abs(p.z() - 2.5) < 1e-9);
        assertSame(cow, p.cameraTarget());
        assertTrue(outbound(ch).stream().noneMatch(s -> s.equals("pos") || s.startsWith("camera:")), "따라가는 동안 위치·카메라 패킷은 없다");
        // 웅크리면 자기 시점으로: 텔레포트 → set_camera(자기 id)
        p.sneaking(true);
        awaitTrue(() -> p.cameraTarget() == null);
        awaitTrue(() -> {
            List<String> sent = outbound(ch);
            return sent.contains("camera:" + p.entityId());
        });
    }

    @Test
    void cameraReturnsWhenTargetDiesOrModeChanges() throws Exception {
        var world = core.world();
        var ch = new ConcurrentEmbeddedChannel();
        var p = spectator(ch, 0.5, 0.5);
        var cow = cow(4.5, 0.5);
        var src = CommandSource.player(core, p).withPermission(2);
        core.commands().performPrefixedCommandAsync(src, "spectate @e[type=cow,limit=1]").join();
        assertSame(cow, p.cameraTarget());
        world.startTicking();
        // 관전 모드가 아니게 되면 되돌아온다
        p.vitals().setGameMode(GameMode.CREATIVE);
        awaitTrue(() -> p.cameraTarget() == null);
        // 다시 관전 모드로 대상을 정하고, 대상이 사라지면 되돌아온다
        p.vitals().setGameMode(GameMode.SPECTATOR);
        core.commands().performPrefixedCommandAsync(src, "spectate @e[type=cow,limit=1]").join();
        assertSame(cow, p.cameraTarget());
        world.submitAtChunk(ChunkPos.ofBlock(cow.x(), cow.z()), r -> {
            cow.discard();
            return null;
        }).get();
        awaitTrue(() -> p.cameraTarget() == null);
    }
}
