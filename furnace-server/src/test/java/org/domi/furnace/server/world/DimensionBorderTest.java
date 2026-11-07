// M10 차원별 월드 보더(바닐라 1.21.1 바이트코드 대조): Level 생성자는 좌표 비가 1 이 아닌 차원(네더)에 중심만 비로 나누는 보더를 만들고(크기는 그대로),
// MinecraftServer.createLevels 는 오버월드 보더에 DelegateBorderChangeListener 를 달아 다른 차원 보더가 변경을 따라가게 한 뒤 저장된 설정을 적용한다.
// 접속자에게 보내는 보더 패킷(sendLevelInfo·PlayerList 리스너)은 차원과 무관하게 오버월드 보더다.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DimensionBorderTest {

    static final DimensionType TEST_NETHER = new DimensionType("minecraft:the_nether", "minecraft:the_nether", -64, 384, 384,
            false, true, true, false, 8.0, 18000L, false, true, true, NoiseWorldSource.Dimension.NETHER);

    @TempDir Path tmp;
    FurnaceSchedulerImpl sched;

    @AfterEach
    void tearDown() { if (sched != null) sched.close(); }

    // ------------------------------------------------------------------ WorldBorder 단위

    @Test
    void scaledBorderDividesOnlyTheCenter() {
        WorldBorder nether = new WorldBorder(8.0);
        nether.setCenter(800, -1600);
        nether.setSize(100);
        assertEquals(100.0, nether.getCenterX());
        assertEquals(-200.0, nether.getCenterZ());
        assertEquals(100.0, nether.getSize(), "크기는 나누지 않는다");
        assertEquals(50.0, nether.getMinX());
        assertEquals(150.0, nether.getMaxX());
        assertEquals(-250.0, nether.getMinZ());
        assertTrue(nether.isWithinBounds(149, -151));
        assertFalse(nether.isWithinBounds(150, -200));
        var st = nether.createSettings();
        assertEquals(100.0, st.centerX(), "Settings(WorldBorder) 는 getCenterX(나눈 값)를 뜬다");
        assertEquals(-200.0, st.centerZ());
    }

    @Test
    void followerCopiesStateAndTracksChanges() {
        AtomicLong ms = new AtomicLong(1_000L);
        WorldBorder over = new WorldBorder(ms::get, 1.0);
        over.setCenter(80, 16);
        over.setDamagePerBlock(0.5);
        over.setDamageSafeZone(2.0);
        over.setWarningBlocks(7);
        over.setWarningTime(30);
        over.setSize(400);
        WorldBorder nether = new WorldBorder(ms::get, 8.0);
        var follow = over.attachFollower(nether);
        assertEquals(10.0, nether.getCenterX(), "붙일 때의 중심(나누기 전 값을 넘기고 받는 쪽이 나눈다)");
        assertEquals(2.0, nether.getCenterZ());
        assertEquals(400.0, nether.getSize());
        assertEquals(0.5, nether.getDamagePerBlock());
        assertEquals(2.0, nether.getDamageSafeZone());
        assertEquals(7, nether.getWarningBlocks());
        assertEquals(30, nether.getWarningTime());

        over.setCenter(-800, 0);
        over.setWarningTime(5);
        assertEquals(-100.0, nether.getCenterX());
        assertEquals(5, nether.getWarningTime());

        over.lerpSizeBetween(400, 200, 10_000L);
        ms.addAndGet(5_000L);
        assertEquals(300.0, over.getSize(), 1e-9);
        assertEquals(300.0, nether.getSize(), 1e-9, "보간도 같이");
        assertEquals(WorldBorder.Status.SHRINKING, nether.getStatus());

        over.removeListener(follow);
        over.setSize(50);
        assertEquals(300.0, nether.getSize(), 1e-9, "뗀 뒤로는 따라가지 않는다");
    }

    @Test
    void followerAttachedMidLerpKeepsTheSameTimeline() {
        AtomicLong ms = new AtomicLong(0L);
        WorldBorder over = new WorldBorder(ms::get, 1.0);
        over.lerpSizeBetween(1000, 0, 1_000L);
        ms.set(250L);
        WorldBorder end = new WorldBorder(ms::get, 1.0);
        over.attachFollower(end);
        assertEquals(750.0, end.getSize(), 1e-9);
        ms.set(500L);
        assertEquals(over.getSize(), end.getSize(), 1e-9);
        assertEquals(over.getLerpRemainingTime(), end.getLerpRemainingTime());
    }

    // ------------------------------------------------------------------ 서버 연결

    private ServerCore coreWithNether(World[] out) {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        ServerCore core = TestServers.core(tmp, sched);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        var io = TestServers.ioPool();
        World nether = new World(TEST_NETHER, new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, 1, 4, 1, 2, 2, reg.directBits(), 7, io, io);
        nether.worldDir(tmp);
        Dimensions dims = new Dimensions();
        dims.register(core.world());
        dims.register(nether);
        core.dimensions(dims);
        out[0] = nether;
        return core;
    }

    @Test
    void worldborderCommandMovesNetherBorderAtEighthCenter() {
        World[] n = new World[1];
        ServerCore core = coreWithNether(n);
        World nether = n[0];
        assertNotSame(core.border(), nether.border(), "차원마다 보더가 따로");
        assertSame(core.border(), core.borderOf("minecraft:overworld"));
        assertSame(nether.border(), core.borderOf("minecraft:the_nether"));
        var console = CommandSource.console(core);
        assertTrue(core.commands().performPrefixedCommandAsync(console, "worldborder center 800.0 0.0").join().success());   // 바닐라 center 는 결과 0
        assertTrue(core.commands().performPrefixedCommandAsync(console, "worldborder set 200").join().success());
        // 오버월드: 700..900, 네더: 중심 100 → 0..200
        var over = core.world().levelServices();
        var nl = nether.levelServices();
        assertTrue(over.withinBorder(850, 0));
        assertFalse(over.withinBorder(150, 0));
        assertTrue(nl.withinBorder(150, 0), "네더는 중심 800/8 = 100, 크기 200");
        assertFalse(nl.withinBorder(850, 0));
        assertArrayEquals(new double[] {0, -100, 200, 100}, nl.borderBounds());
        assertArrayEquals(new double[] {0, -100, 200, 100}, over.borderBoundsOf("minecraft:the_nether"), "지옥문 출구는 목적 레벨 보더로 자른다");
        // 같은 명령을 네더에서 실행해도 바닐라 WorldBorderCommand 는 오버월드 보더를 쓴다
        assertTrue(core.commands().performPrefixedCommandAsync(console, "execute in minecraft:the_nether run worldborder center 1600.0 0.0").join().success());
        assertEquals(1600.0, core.border().getCenterX());
        assertEquals(200.0, nether.border().getCenterX());
    }

    @Test
    void netherBorderStartsFromSavedOverworldSettingsAndHasMaxWorldSize() {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        ServerCore core = TestServers.core(tmp, sched);
        core.border().setCenter(-4000, 4000);
        core.border().setSize(1000);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        var io = TestServers.ioPool();
        World nether = new World(TEST_NETHER, new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, 1, 4, 1, 2, 2, reg.directBits(), 7, io, io);
        nether.worldDir(tmp);
        Dimensions dims = new Dimensions();
        dims.register(core.world());
        dims.register(nether);
        core.dimensions(dims);
        // 바닐라: 리스너를 먼저 단 뒤 applySettings 가 넘긴다 — 재시작 직후에도 네더 보더는 오버월드 설정(중심 1/8)이다
        assertEquals(-500.0, nether.border().getCenterX());
        assertEquals(500.0, nether.border().getCenterZ());
        assertEquals(1000.0, nether.border().getSize());
        assertEquals(core.border().getAbsoluteMaxSize(), nether.border().getAbsoluteMaxSize(), "ServerLevel 생성자 setAbsoluteMaxSize(max-world-size)");
        core.attachWorld(nether);   // 다시 붙여도 고리는 하나
        core.border().setSize(10);
        assertEquals(10.0, nether.border().getSize());
        assertEquals(1, countDelegates(core.border(), nether.border()));
    }

    private static int countDelegates(WorldBorder over, WorldBorder target) {
        // 같은 대상에 고리가 둘이면 setSize 가 두 번 불린다 — 리스너로 센다
        int[] calls = {0};
        WorldBorder.Listener probe = new WorldBorder.Listener() {
            @Override public void onSizeSet(WorldBorder b, double size) { calls[0]++; }
        };
        target.addListener(probe);
        over.setSize(20);
        target.removeListener(probe);
        return calls[0];
    }
}
