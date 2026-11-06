// /tp 차원 간: 좌표 이동은 실행 소스의 차원(/execute in)으로, 엔티티 이동은 도착 엔티티의 차원으로. 일반 엔티티는 목적 차원 region 에
// 새로 만들어지고(같은 UUID, 원래 것은 CHANGED_DIMENSION), 승객은 원래 차원에 내려놓는다(바닐라 unRide). 플레이어는 차원 변경 경로로.
package org.domi.furnace.server.command.builtin;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.entity.Cow;
import org.domi.furnace.server.entity.DimensionTransition;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.world.DimensionType;
import org.domi.furnace.server.world.Dimensions;
import org.domi.furnace.server.world.World;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TeleportAcrossDimensionsTest {

    /** 시험용 네더: 슈퍼플랫 높이를 쓰되 레벨 키는 네더. */
    static final DimensionType TEST_NETHER = new DimensionType("minecraft:the_nether", "minecraft:the_nether", -64, 384, 384,
            false, true, true, false, 8.0, 18000L, false, true, true, NoiseWorldSource.Dimension.NETHER);
    static final String NETHER = "minecraft:the_nether";

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
        nether = new World(TEST_NETHER, new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, 1, 4, 1, 2, 2, reg.directBits(), 7, io, io);
        nether.worldDir(tmp);
        Dimensions dims = new Dimensions();
        dims.register(core.world());
        dims.register(nether);
        core.dimensions(dims);
    }

    @AfterEach
    void tearDown() { sched.close(); }

    private Cow cowIn(World w, double x, double z, String tag) throws InterruptedException {
        Cow[] out = new Cow[1];
        var done = new CountDownLatch(1);
        w.runAt(x, z, region -> {
            var lvl = region.level();
            out[0] = new Cow(lvl, lvl.nextEntityId());
            out[0].moveTo(x, -60, z, 0, 0);
            out[0].addTag(tag);
            lvl.addFreshEntity(out[0]);
            done.countDown();
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        return out[0];
    }

    /** 그 차원 (cx, cz) 청크 region 에서 UUID 로 엔티티를 찾는다(없으면 null). */
    private Entity find(World w, int cx, int cz, UUID id) throws Exception {
        return w.submitAtChunk(new ChunkPos(cx, cz), r -> {
            Entity e = r.level().entities().get(id);
            return e != null && !e.isRemoved() ? e : null;
        }).get(10, TimeUnit.SECONDS);
    }

    private Entity awaitIn(World w, int cx, int cz, UUID id) throws Exception {
        long deadline = System.nanoTime() + 30_000_000_000L;
        while (System.nanoTime() < deadline) {
            Entity e = find(w, cx, cz, id);
            if (e != null) return e;
            java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L);
        }
        fail("목적 차원에 엔티티가 나타나지 않았다");
        return null;
    }

    @Test
    void executeInMovesMobToSourceDimension_withRotation_andSameUuid() throws Exception {
        Cow cow = cowIn(core.world(), 0.5, 0.5, "traveler");
        UUID id = cow.uuid();
        var console = CommandSource.console(core);
        assertEquals(1, core.commands().performPrefixedCommandAsync(console,
                "execute in minecraft:the_nether run tp @e[type=cow,tag=traveler] 4.5 -60 4.5 30 10").join().result());
        assertEquals(Entity.RemovalReason.CHANGED_DIMENSION, cow.removalReason());
        Entity moved = awaitIn(nether, 0, 0, id);
        assertNotSame(cow, moved, "바닐라처럼 목적 레벨에 새로 만든다");
        assertEquals(NETHER, moved.dimensionKey());
        assertEquals(4.5, moved.x(), 1e-9);
        assertEquals(-60, moved.y(), 1e-9);
        assertEquals(30, moved.yRot(), 1e-6);
        assertEquals(10, moved.xRot(), 1e-6);
        assertTrue(moved.getTags().contains("traveler"), "저장 데이터(태그)를 옮긴다");
        // 새 몹의 길 찾기는 목적 레벨에 묶여 있다(옛 레벨을 가리키지 않는다)
        if (moved instanceof org.domi.furnace.server.entity.PathfinderMob m) {
            assertSame(moved.level(), nether.submitAtChunk(new ChunkPos(0, 0), r -> r.level()).get());
            assertTrue(m.getNavigation().isDone());
        }
        // 다시 오버월드로: 좌표 tp 의 기본 차원은 실행 소스(콘솔 = 오버월드)
        assertEquals(1, core.commands().performPrefixedCommandAsync(console, "tp @e[type=cow,tag=traveler] 1.5 -60 1.5").join().result());
        Entity back = awaitIn(core.world(), 0, 0, id);
        assertEquals("minecraft:overworld", back.dimensionKey());
        assertEquals(1.5, back.x(), 1e-9);
    }

    @Test
    void vehicleWithPassengerIsMovedAlone_passengerStaysBehind() throws Exception {
        Cow vehicle = cowIn(core.world(), 0.5, 0.5, "vehicle");
        Cow rider = cowIn(core.world(), 0.5, 0.5, "rider");
        var mounted = new CountDownLatch(1);
        core.world().runAt(0.5, 0.5, r -> {
            rider.startRiding(vehicle, true);
            mounted.countDown();
        });
        assertTrue(mounted.await(10, TimeUnit.SECONDS));
        assertSame(vehicle, rider.vehicle());
        assertEquals(1, core.commands().performPrefixedCommandAsync(CommandSource.console(core),
                "execute in minecraft:the_nether run tp @e[tag=vehicle] 2.5 -60 2.5").join().result());
        Entity moved = awaitIn(nether, 0, 0, vehicle.uuid());
        assertTrue(moved.passengers().isEmpty(), "승객 없이 넘어간다(바닐라 unRide)");
        Entity left = find(core.world(), 0, 0, rider.uuid());
        assertNotNull(left, "승객은 원래 차원에 남는다");
        assertNull(left.vehicle());
    }

    @Test
    void entityDestinationInOtherDimensionAndPlayerDimensionChange() throws Exception {
        // 도착 엔티티가 다른 차원: 소를 네더의 소에게로
        Cow over = cowIn(core.world(), 0.5, 0.5, "a");
        Cow there = cowIn(nether, 6.5, 6.5, "b");
        var console = CommandSource.console(core);
        assertEquals(1, TeleportCommand.toEntityAsync(console, List.of(over), there).join());
        Entity moved = awaitIn(nether, 0, 0, over.uuid());
        assertEquals(6.5, moved.x(), 1e-9);
        assertEquals(6.5, moved.z(), 1e-9);

        // 플레이어: 차원 변경 경로(속도 0, 도착 뒤 처리 없음)로 넘긴다
        var p = new Player(core.world().entityIds().next(), UUID.randomUUID(), "Steve", new ConcurrentEmbeddedChannel());
        p.applyMove(0.5, -60, 0.5, 0, 0, true);
        var changes = new LinkedBlockingQueue<Object[]>();
        p.dimensionChanger((target, t, cd) -> changes.add(new Object[] {target, t}));
        p.teleporter(m -> fail("같은 차원 보정 텔레포트가 아니어야 한다"));
        var adopted = new CountDownLatch(1);
        core.world().runAt(0.5, 0.5, r -> {
            r.adopt(p);
            adopted.countDown();
        });
        assertTrue(adopted.await(10, TimeUnit.SECONDS));
        var src = CommandSource.player(core, p).withPermission(2);
        assertEquals(1, core.commands().performPrefixedCommandAsync(src, "execute in minecraft:the_nether run tp @s 3.5 -59 -2.5 90 5").join().result());
        Object[] c = changes.poll(10, TimeUnit.SECONDS);
        assertNotNull(c, "차원 변경이 오지 않았다");
        assertSame(nether, c[0]);
        DimensionTransition t = (DimensionTransition) c[1];
        assertEquals(3.5, t.x(), 1e-9);
        assertEquals(-59, t.y(), 1e-9);
        assertEquals(-2.5, t.z(), 1e-9);
        assertEquals(0, t.vx(), 0);
        assertEquals(90, t.yRot(), 1e-6);
        assertEquals(5, t.xRot(), 1e-6);
        assertFalse(t.portalSound());
        assertNull(t.ticketPos());
        assertFalse(core.world().submitAtChunk(new ChunkPos(0, 0), r -> r.players().containsKey(p.entityId())).get(), "원래 region 에서 떼어 냈다");
    }
}
