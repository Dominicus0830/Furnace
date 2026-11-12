// O08 정확성: 스폰 청크(게임 규칙 spawnChunkRadius 의 START 티켓)와 빈 차원 300 틱 규칙(ServerLevel.emptyTime)이 바닐라 1.21.1 과 같은가.
// 바닐라 서버를 직접 띄워 확인한 것(2026-11-12, 평지, 스폰 (8, -60, 8), 플레이어 없음):
//  - 반경 2 면 스폰 청크에서 체비셰프 ≤ 3 청크가 올라와 있고, ≤ 2 는 예약 틱(관측기·중계기·공급기)이 계속 돈다. 3 은 멈춰 있다(보더).
//  - 플레이어도 /forceload 도 없이 300 틱이 지나면 블록 엔티티·엔티티가 쉰다: 호퍼가 멈추고, 피스톤은 moving_piston 에 머문다(중계기는 켜진다).
//    다른 곳에 /forceload 를 걸면 바로 다시 돈다(호퍼가 거리 ≤ 2 에서 움직이고, 떨어지는 아이템은 거리 ≤ 1 에서만 떨어진다).
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpawnChunkTicketTest {

    private static final int Y = -60;
    private static final String EXTENDED = "piston[extended=true,facing=east] | piston_head[facing=east,short=false,type=normal] | repeater[delay=1,facing=north,locked=false,powered=true]";
    private static final String IDLE = "piston[extended=false,facing=east] | air | repeater[delay=1,facing=north,locked=false,powered=false]";
    /** 블록 이벤트·예약 틱은 돌고 움직이는 피스톤 블록 엔티티만 쉰다(바닐라 빈 차원에서 본 그대로). */
    private static final String MOVING = "piston[extended=true,facing=east] | moving_piston[facing=east,type=normal] | repeater[delay=1,facing=north,locked=false,powered=true]";

    private FurnaceSchedulerImpl sched;
    private ExecutorService io;
    private World w;
    private volatile int spawnChunkRadius = 2;

    @TempDir java.nio.file.Path tmp;

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    @AfterEach
    void close() {
        if (io != null) io.shutdown();
        if (sched != null) sched.close();
    }

    private void world() {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        io = Executors.newSingleThreadExecutor();
        // 시야·시뮬레이션 거리 4 — 플레이어가 없으니 상관없다
        w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, sched.regionThreadCount(), 16, 4, 4, reg.directBits(), 7, io);
        w.setRegistries(reg, ItemRegistry.load("items/1.21.1.txt"));
        w.configurePersistence(1_000_000, 1);   // 퇴출 유예 1틱: 티켓 없는 청크는 곧 내려간다
        LevelServices base = w.levelServices();
        w.levelServices(new LevelServices.Delegating(base) {
            @Override
            public int gameRuleInt(String name) { return "spawnChunkRadius".equals(name) ? spawnChunkRadius : super.gameRuleInt(name); }
        });
        w.defaultSpawn(new SpawnPoint(8, Y, 8, 0f));
    }

    /** 운영 서버의 한 틱: 전역 틱이 emptyTime 을 정하고 region 들이 돈다. */
    private void ticks(int n) throws Exception {
        for (int i = 0; i < n; i++) {
            w.tickEmptyTime();
            w.tickOnce().get(30, TimeUnit.SECONDS);
        }
    }

    private void placeDevice(int cx) throws Exception {
        w.awaitChunk(new ChunkPos(cx, 0));
        int x = cx * 16 + 4, z = 4;
        int piston = B.prop("facing").with(B.defaultState(B.id("piston")), "east");
        int repeater = B.prop("facing").with(B.defaultState(B.id("repeater")), "north");
        int rb = B.defaultState(B.id("redstone_block"));
        w.submitAtChunk(new ChunkPos(cx, 0), r -> {
            var l = r.level();
            l.setBlock(new Pos(x, Y, z), piston, 3);
            l.setBlock(new Pos(x - 1, Y, z), rb, 3);
            l.setBlock(new Pos(x, Y, z + 4), repeater, 3);
            l.setBlock(new Pos(x, Y, z + 3), rb, 3);
            return null;
        }).get(30, TimeUnit.SECONDS);
    }

    private String device(int cx) throws Exception {
        int x = cx * 16 + 4, z = 4;
        Blocks bl = Blocks.get();
        return w.submitAtChunk(new ChunkPos(cx, 0), r -> {
            var l = r.level();
            return bl.describe(l.getBlockState(new Pos(x, Y, z))).replace("minecraft:", "") + " | "
                    + bl.describe(l.getBlockState(new Pos(x + 1, Y, z))).replace("minecraft:", "") + " | "
                    + bl.describe(l.getBlockState(new Pos(x, Y, z + 4))).replace("minecraft:", "");
        }).get(30, TimeUnit.SECONDS);
    }

    private Map<Integer, String> devices(int... cxs) throws Exception {
        Map<Integer, String> m = new LinkedHashMap<>();
        for (int cx : cxs) m.put(cx, device(cx));
        return m;
    }

    private void awaitLoaded(int cx, int cz) throws Exception {
        for (int i = 0; i < 400 && !w.isChunkLoaded(cx, cz); i++) Thread.sleep(25);
        assertTrue(w.isChunkLoaded(cx, cz), "청크 " + cx + "," + cz + " 가 올라와야 한다");
    }

    /** 반경 2: 체비셰프 ≤ 3 이 올라오고(구독자 없이도 내려가지 않는다), ≤ 2 의 피스톤·중계기가 돈다. 3 은 올라와 있기만 하다. */
    @Test
    void spawnChunksKeepRedstoneRunningWithoutPlayers() throws Exception {
        world();
        w.enableSpawnChunks();
        for (int d = 0; d <= 3; d++) {
            awaitLoaded(d, 0);
            awaitLoaded(-d, -d);
        }
        int[] ds = {0, 1, 2, 3};
        for (int d : ds) placeDevice(d);
        ticks(20);
        assertTrue(w.isChunkLoaded(3, 3), "거리 3 은 티켓으로 붙잡혀 있다");
        assertFalse(w.isChunkLoaded(4, 0), "거리 4 는 올리지 않는다");
        Map<Integer, String> want = new LinkedHashMap<>();
        for (int d : ds) want.put(d, d <= 2 ? EXTENDED : IDLE);
        assertEquals(want, devices(ds));
    }

    /** 규칙 spawnChunkRadius 0 이면 티켓이 없다(바닐라: 거리 1 은 걸지 않는다) — 장치 청크를 따로 올려도 플레이어 없이는 아무것도 안 돈다. */
    @Test
    void radiusZeroMeansNoSpawnChunks() throws Exception {
        spawnChunkRadius = 0;
        world();
        w.enableSpawnChunks();
        assertNull(w.spawnTicket());
        w.configurePersistence(1_000_000, 1_000_000);   // 장치 청크가 내려가지 않게
        placeDevice(0);
        ticks(20);
        assertEquals(IDLE, device(0));
    }

    /** 티켓 범위: 올라옴 ≤ d, 블록 틱 ≤ d − 1, 엔티티 틱 ≤ d − 2(d = 반경 + 1). 규칙·스폰이 바뀌면 옛 티켓을 놓고 새로 건다. */
    @Test
    void ticketFollowsGameRuleAndSpawnMoves() throws Exception {
        world();
        assertNull(w.spawnTicket(), "켜기 전(추가 차원·시험 월드)은 티켓이 없다");
        w.enableSpawnChunks();
        assertEquals(new World.SpawnTicket(0, 0, 3), w.spawnTicket());
        assertTrue(w.ticketed(Regionizer.chunkKey(-3, 3)));
        assertFalse(w.ticketed(Regionizer.chunkKey(-4, 0)));
        assertTrue(w.blockTicketed(2, -2));
        assertFalse(w.blockTicketed(3, 0));
        assertTrue(w.entityTicketed(1, 1));
        assertFalse(w.entityTicketed(2, 0));

        spawnChunkRadius = 5;
        w.refreshSpawnTicket();   // 규칙 콜백(ServerCore.onGameRuleChanged)
        assertEquals(new World.SpawnTicket(0, 0, 6), w.spawnTicket());
        assertTrue(w.entityTicketed(4, -4));
        assertFalse(w.entityTicketed(5, 0));

        w.defaultSpawn(new SpawnPoint(-100, Y, 40, 0f));   // /setworldspawn
        assertEquals(new World.SpawnTicket(-7, 2, 6), w.spawnTicket());
        assertFalse(w.ticketed(Regionizer.chunkKey(6, 0)), "옛 자리 티켓은 놓았다");

        spawnChunkRadius = 0;
        w.refreshSpawnTicket();
        assertNull(w.spawnTicket());
        assertFalse(w.ticketed(Regionizer.chunkKey(-7, 2)));
    }

    /**
     * 바닐라 {@code if (flag || emptyTime++ < 300)}: 빈 뒤 300 틱은 돌고 301 번째부터 쉰다. /forceload 청크가 있으면 비어도 돈다.
     * 엔티티가 차원을 넘으면(resetEmptyTime) 다시 300 틱.
     */
    @Test
    void emptyTimeCountsThreeHundredTicks() {
        world();
        for (int i = 0; i < 300; i++) w.tickEmptyTime();
        assertFalse(w.emptyIdle(), "300 틱째까지는 돈다");
        w.tickEmptyTime();
        assertTrue(w.emptyIdle(), "301 틱째부터 쉰다");
        w.resetEmptyTime();
        assertFalse(w.emptyIdle());
        for (int i = 0; i < 301; i++) w.tickEmptyTime();
        assertTrue(w.emptyIdle());
        w.setChunkForced(50, 50, true);
        w.tickEmptyTime();
        assertFalse(w.emptyIdle(), "/forceload 청크가 있으면 비어도 돈다");
        for (int i = 0; i < 400; i++) w.tickEmptyTime();
        assertFalse(w.emptyIdle());
        w.setChunkForced(50, 50, false);
        for (int i = 0; i < 300; i++) w.tickEmptyTime();
        assertFalse(w.emptyIdle(), "놓은 뒤부터 다시 300 틱");
        w.tickEmptyTime();
        assertTrue(w.emptyIdle());
    }

    /**
     * 빈 지 300 틱이 지난 차원: 스폰 청크의 예약 틱·블록 이벤트는 돌아 중계기가 켜지고 피스톤이 밀지만, 움직이는 피스톤(블록 엔티티)은 쉬어
     * moving_piston 에 머문다(바닐라에서 본 그대로). 다른 곳을 /forceload 하면 다시 돌아 끝까지 밀린다.
     */
    @Test
    void emptyDimensionPausesBlockEntitiesButNotScheduledTicks() throws Exception {
        world();
        w.enableSpawnChunks();
        for (int i = 0; i < 301; i++) w.tickEmptyTime();
        int[] ds = {0, 2};
        for (int d : ds) placeDevice(d);
        ticks(20);
        assertEquals(Map.of(0, MOVING, 2, MOVING), devices(ds));

        w.setChunkForced(60, 60, true);
        ticks(5);
        assertEquals(Map.of(0, EXTENDED, 2, EXTENDED), devices(ds));
    }

    /** 서버 배선: 전역 틱이 차원마다 emptyTime 을 세고, 게임 규칙 spawnChunkRadius 를 바꾸면(명령) 주 오버월드 티켓이 따라간다. */
    @Test
    void serverWiresGameRuleAndEmptyTime() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        var core = org.domi.furnace.server.TestServers.core(tmp, sched);
        World world = core.world();
        world.defaultSpawn(new SpawnPoint(40, Y, -20, 0f));
        world.enableSpawnChunks();
        assertEquals(new World.SpawnTicket(2, -2, 3), world.spawnTicket(), "기본 규칙 2");
        core.gameRules().set("spawnChunkRadius", 4);
        assertEquals(new World.SpawnTicket(2, -2, 5), world.spawnTicket());
        core.setWorldSpawn(-40, Y, 100, 0f);
        assertEquals(new World.SpawnTicket(-3, 6, 5), world.spawnTicket());
        core.gameRules().set("spawnChunkRadius", 0);
        assertNull(world.spawnTicket());

        for (int i = 0; i < 300; i++) core.tick();
        assertFalse(world.emptyIdle());
        core.tick();
        assertTrue(world.emptyIdle(), "접속자 없는 서버: 301 번째 전역 틱부터 엔티티·블록 엔티티를 쉰다");
    }
}
