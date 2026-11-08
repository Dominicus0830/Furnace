// A07-02·03·08 회귀: 블록 틱(예약 틱·블록 이벤트·블록 엔티티)이 도는 청크 범위가 바닐라 1.21.1 과 같은가.
// 바닐라(바이트코드 aqk/ara/aqb/eyq/aqu): 플레이어 TickingTracker 레벨 max(0, 31 − sim) + 청크 거리, 블록 틱은 레벨 ≤ 32 → 체비셰프 ≤ sim + 1.
// 청크 단계는 시야 거리 안 레벨 31 티켓 + 1 → 체비셰프 ≤ 시야 + 1 이어야 BLOCK_TICKING. /forceload(레벨 31)는 그 청크와 둘레 1칸.
// 범위 밖 청크의 예약 틱은 꺼내지 않고(LevelTicks), 블록 이벤트는 다음 틱으로 미룬다(runBlockEvents) — 범위에 들면 그때 돈다.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BlockTickRangeTest {

    private static final int Y = -60;   // 슈퍼플랫 기본 층(-64~-61) 바로 위
    private static final String EXTENDED = "piston[extended=true,facing=east] | piston_head[facing=east,short=false,type=normal] | repeater[delay=1,facing=north,locked=false,powered=true]";
    private static final String IDLE = "piston[extended=false,facing=east] | air | repeater[delay=1,facing=north,locked=false,powered=false]";

    private FurnaceSchedulerImpl sched;
    private ExecutorService io;
    private World w;

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    @AfterEach
    void close() {
        if (io != null) io.shutdown();
        if (sched != null) sched.close();
    }

    private void world(int viewDistance, int simulationDistance) {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        io = Executors.newSingleThreadExecutor();
        w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, sched.regionThreadCount(), 16, viewDistance, viewDistance,
                reg.directBits(), 7, io);
        w.setRegistries(reg, ItemRegistry.load("items/1.21.1.txt"));
        w.configurePersistence(1_000_000, 1_000_000);   // 시험 중 청크가 내려가지 않게(장치 청크는 미리 올린다)
        LevelServices base = w.levelServices();
        w.levelServices(new LevelServices.Delegating(base) {
            @Override
            public int simulationDistance() { return simulationDistance; }
        });
    }

    private Player join(double x, double z) throws Exception {
        Player p = new Player(1, UUID.randomUUID(), "A", new EmbeddedChannel());
        p.viewDistance(2);   // 클라이언트 렌더 거리 — 보낼 청크만 줄인다(블록 틱 범위는 서버 시야 거리로 정해진다)
        p.applyMove(x, Y, z, 0, 0, true);
        ChunkPos cp = new ChunkPos((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);
        w.submitAtChunk(cp, r -> { r.adopt(p); return null; }).get(30, TimeUnit.SECONDS);
        return p;
    }

    private void ticks(int n) throws Exception {
        for (int i = 0; i < n; i++) w.tickOnce().get(30, TimeUnit.SECONDS);
    }

    /** 청크 (cx, 0) 에 피스톤(동쪽, 뒤에 레드스톤 블록)과 중계기(북쪽, 뒤에 레드스톤 블록)를 놓는다 — 놓을 때 예약 틱·블록 이벤트가 걸린다. */
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

    /**
     * 시뮬레이션 거리 4, 시야 10, 플레이어 청크 (0,0): 거리 ≤ 5 는 피스톤이 끝까지 늘어나고 중계기가 켜진다. 거리 6·8 은 예약 틱·블록 이벤트가
     * 남아 있을 뿐 아무것도 돌지 않는다(예전: 거리 4·5 피스톤이 moving_piston 에서 멈추고, 거리 8 은 이벤트만 돌아 반쯤 움직였다).
     * 플레이어가 다가가면 남아 있던 것이 그때 돈다.
     */
    @Test
    void blockTicksFollowSimulationDistancePlusOne() throws Exception {
        world(10, 4);
        join(8.5, 8.5);
        int[] ds = {2, 3, 4, 5, 6, 8};
        for (int d : ds) placeDevice(d);
        ticks(10);
        Map<Integer, String> want = new LinkedHashMap<>();
        for (int d : ds) want.put(d, d <= 5 ? EXTENDED : IDLE);
        assertEquals(want, devices(ds));

        // 플레이어가 동쪽으로 4 청크 가면(청크 4) 거리 6·8 도 범위(≤ 5) 안 — 미뤄 둔 예약 틱·블록 이벤트가 그때 돈다
        w.submitAtChunk(new ChunkPos(0, 0), r -> {
            r.players().values().forEach(p -> p.applyMove(4 * 16 + 8.5, Y, 8.5, 0, 0, true));
            return null;
        }).get(30, TimeUnit.SECONDS);
        ticks(10);
        assertEquals(EXTENDED, device(6));
        assertEquals(EXTENDED, device(8));
    }

    /** 시야 거리가 시뮬레이션 거리보다 작으면 청크 단계(시야 + 1 까지 BLOCK_TICKING)가 범위를 정한다: 시야 3, sim 10 → 거리 ≤ 4. */
    @Test
    void viewDistanceCapsBlockTickingRange() throws Exception {
        world(3, 10);
        join(8.5, 8.5);
        int[] ds = {3, 4, 5};
        for (int d : ds) placeDevice(d);
        ticks(10);
        assertEquals(EXTENDED, device(3));
        assertEquals(EXTENDED, device(4));
        assertEquals(IDLE, device(5), "시야 3 이면 거리 5 청크는 바닐라에서 BLOCK_TICKING 단계가 아니다(올라와 있어도)");
    }

    /** /forceload 청크(레벨 31)는 그 청크와 둘레 1칸이 블록 틱 범위다(A07-08). 플레이어는 멀리. */
    @Test
    void forcedChunkNeighboursAreBlockTicking() throws Exception {
        world(4, 4);
        join(8.5, 8.5);
        w.setChunkForced(12, 0, true);
        w.awaitChunk(new ChunkPos(12, 0));
        for (int d : new int[] {11, 12, 13, 14}) placeDevice(d);
        ticks(10);
        assertEquals(EXTENDED, device(11));
        assertEquals(EXTENDED, device(12));
        assertEquals(EXTENDED, device(13));
        assertEquals(IDLE, device(14));
    }
}
