// /spectate 대상이 다른 차원: 바닐라(teleportTo 6인자의 setCamera(this))대로 자기 자리 보정 → set_camera(자기 id) → 차원 이동 → set_camera(자기 id),
// 카메라는 자기 자신으로 끝나고 메시지는 started.
package org.domi.furnace.server.command.builtin;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.entity.Cow;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.play.SetCamera;
import org.domi.furnace.server.net.protocol.play.SyncPlayerPosition;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.Dimensions;
import org.domi.furnace.server.world.GameMode;
import org.domi.furnace.server.world.World;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpectateAcrossDimensionsTest {

    @TempDir Path tmp;
    FurnaceSchedulerImpl sched;
    ServerCore core;
    World nether;

    @BeforeEach
    void setUp() {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        var io = TestServers.ioPool();
        nether = new World(TeleportAcrossDimensionsTest.TEST_NETHER, new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, 1, 4, 1, 2, 2,
                reg.directBits(), 7, io, io);
        nether.worldDir(tmp);
        Dimensions dims = new Dimensions();
        dims.register(core.world());
        dims.register(nether);
        core.dimensions(dims);
    }

    @AfterEach
    void tearDown() { sched.close(); }

    @Test
    void otherDimensionTargetMovesDimensionButCameraEndsOnSelf() throws Exception {
        Cow[] cow = new Cow[1];
        var made = new CountDownLatch(1);
        nether.runAt(6.5, 6.5, region -> {
            var lvl = region.level();
            cow[0] = new Cow(lvl, lvl.nextEntityId());
            cow[0].moveTo(6.5, -60, 6.5, 0, 0);
            lvl.addFreshEntity(cow[0]);
            made.countDown();
        });
        assertTrue(made.await(10, TimeUnit.SECONDS));
        var ch = new ConcurrentEmbeddedChannel();
        var p = new Player(core.world().entityIds().next(), UUID.randomUUID(), "Spec", ch);
        p.vitals().setGameMode(GameMode.SPECTATOR);
        p.applyMove(0.5, -60, 0.5, 0, 0, true);
        var changes = new LinkedBlockingQueue<Object[]>();
        p.dimensionChanger((target, t, cd) -> changes.add(new Object[] {target, t}));
        p.teleporter(t -> p.send(new SyncPlayerPosition(t[0], t[1], t[2], 0f, 0f, 1)));
        var adopted = new CountDownLatch(1);
        core.world().runAt(0.5, 0.5, r -> {
            r.adopt(p);
            adopted.countDown();
        });
        assertTrue(adopted.await(10, TimeUnit.SECONDS));
        var src = CommandSource.player(core, p).withPermission(2);
        core.commands().performPrefixedCommandAsync(src, "spectate @e[type=cow,limit=1]").orTimeout(30, TimeUnit.SECONDS).join();
        assertNotNull(changes.poll(10, TimeUnit.SECONDS), "차원 변경이 와야 한다");
        assertNull(p.cameraTarget(), "카메라는 자기 자신으로 끝난다");
        ch.runPendingTasks();
        List<String> seq = new ArrayList<>();
        Object o;
        while ((o = ch.readOutbound()) != null) {
            if (!(o instanceof ByteBuf bb)) continue;
            String text = bb.toString(java.nio.charset.StandardCharsets.ISO_8859_1);
            int id = Types.readVarInt(bb);
            if (id == SetCamera.ID) seq.add("camera:" + (Types.readVarInt(bb) == p.entityId() ? "self" : "other"));
            else if (id == SyncPlayerPosition.ID) seq.add("pos");
            else if (text.contains("commands.spectate.success.started")) seq.add("started");
            bb.release();
        }
        assertEquals(List.of("pos", "camera:self", "camera:self", "started"), seq);
    }
}
