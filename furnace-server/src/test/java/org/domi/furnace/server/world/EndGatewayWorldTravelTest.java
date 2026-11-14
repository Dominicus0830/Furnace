// 엔드 관문 서버 경로(EndGatewayBlock.travelWithinLevel): 출구 청크를 비동기로 차례로 올려 walk 하고, 섬·출구 관문을 만든 뒤 원천 관문에
// 기록하고, 도착 칸을 출구 region 스레드에서 정한다. 같은 관문을 동시에 두 번 써도 출구는 하나만 생긴다.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.entity.EndGatewayBlockEntity;
import org.domi.furnace.server.block.types.EndGatewayBlock;
import org.domi.furnace.server.entity.DimensionTransition;
import org.domi.furnace.server.entity.Traveler;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.anvil.AnvilChunkCodec;
import org.domi.furnace.server.world.anvil.RegionFileStore;
import org.domi.furnace.server.world.gen.AnvilWorldSource;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EndGatewayWorldTravelTest {

    /** 시험용 엔드: 슈퍼플랫 높이를 쓰되 레벨 키는 엔드(출구 만들기는 엔드에서만). */
    static final DimensionType TEST_END = new DimensionType("minecraft:the_end", "minecraft:the_end", -64, 384, 384,
            false, false, false, false, 1.0, 6000L, false, false, false, NoiseWorldSource.Dimension.END);

    @BeforeAll
    static void items() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private java.util.concurrent.ExecutorService io;
    private World end;

    @AfterEach
    void close() throws InterruptedException {
        if (sched != null) sched.close();
        if (io != null) {
            io.shutdown();
            io.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private void start() {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        io = org.domi.furnace.server.TestServers.ioPool();
        var src = new AnvilWorldSource(tmp, new RegionFileStore(TEST_END.storageDir(tmp)), new AnvilChunkCodec(reg, 0), new SuperflatWorldSource(reg, 0));
        end = new World(TEST_END, src, new org.domi.furnace.server.net.ChunkWorkerPool(1), sched, 1, 4, 1, 2, 2, reg.directBits(), 7, io, io);
        end.configurePersistence(Integer.MAX_VALUE, 100);
        end.worldDir(tmp);
    }

    private static Traveler traveler(boolean pearl) {
        return new Traveler(96.5, 75.0, 0.5, 0.25, 0.0, 0.0, 12.0F, 5.0F, 0.9, 0.9, false, true, pearl, org.domi.furnace.server.block.CollisionContext.EMPTY);
    }

    @Test
    void firstUseBuildsExitAsyncAndBothTravelersShareIt() throws Exception {
        start();
        Pos gw = new Pos(96, 75, 0);
        ChunkPos cp = new ChunkPos(6, 0);
        end.submitAtChunk(cp, r -> r.loadChunk(cp)).get(10, TimeUnit.SECONDS).get(10, TimeUnit.SECONDS);
        var a = new CompletableFuture<DimensionTransition>();
        var b = new CompletableFuture<DimensionTransition>();
        end.submitAtChunk(cp, r -> {
            var lvl = r.level();
            EndGatewayBlockEntity.placeGatewayPortal(lvl, gw, null, false);
            var block = (EndGatewayBlock) B.behavior(lvl.getBlockState(gw));
            block.travelWithinLevel(lvl, gw, traveler(false), (l, t) -> a.complete(t));
            block.travelWithinLevel(lvl, gw, traveler(true), (l, t) -> b.complete(t));   // 만드는 중 — 기다린다
            return null;
        }).get(10, TimeUnit.SECONDS);
        DimensionTransition ta = a.get(60, TimeUnit.SECONDS), tb = b.get(60, TimeUnit.SECONDS);
        assertNotNull(ta);
        assertNotNull(tb);
        // 슈퍼플랫은 모든 청크가 차 있어 1024 에서 안쪽으로 16번(768) 간 청크에 섬(가운데 (768, 75, 0))을 만든다
        Pos exit = end.submitAtChunk(cp, r -> ((EndGatewayBlockEntity) r.level().getBlockEntity(gw)).exitPortal()).get(10, TimeUnit.SECONDS);
        assertNotNull(exit, "원천 관문에 출구가 기록됐다");
        assertEquals(768.0, exit.x(), 7.0);
        assertEquals(85, exit.y());
        ChunkPos ecp = ChunkPos.ofBlock(exit.x(), exit.z());
        var built = end.submitAtChunk(ecp, r -> new Object[] {
                r.level().getBlockState(exit), r.level().getBlockEntity(exit), r.level().getBlockState(new Pos(768, 75, 0))}).get(10, TimeUnit.SECONDS);
        assertTrue(B.is((Integer) built[0], "minecraft:end_gateway"), "출구 관문");
        assertEquals(gw, ((EndGatewayBlockEntity) built[1]).exitPortal(), "출구 관문은 처음 관문으로");
        assertTrue(B.is((Integer) built[2], "minecraft:end_stone"), "섬");
        // 도착: 출구 둘레 5 의 가장 높은 블록(섬 꼭대기 75) 위, 바닥 가운데
        assertEquals(76.0, ta.y(), 1e-9);
        assertTrue(Math.abs(ta.x() - (exit.x() + 0.5)) <= 5.0 && Math.abs(ta.z() - (exit.z() + 0.5)) <= 5.0);
        assertEquals(0.5, ta.x() - Math.floor(ta.x()), 1e-9);
        assertEquals(0.25, ta.vx(), 1e-9, "속도는 그대로");
        assertEquals(12.0F, ta.yRot(), 1e-6);
        assertFalse(ta.portalSound(), "관문은 소리를 내지 않는다(PLACE_PORTAL_TICKET 만)");
        assertNotNull(ta.ticketPos());
        assertEquals(-1.0, tb.vy(), 1e-9, "엔더 진주는 아래로");
        assertEquals(0.0, tb.vx(), 1e-9);
        assertEquals(ta.x(), tb.x(), 1e-9, "같은 출구");

        // 두 번째 사용: 새 관문을 만들지 않고 기록된 출구로
        var c = new CompletableFuture<DimensionTransition>();
        end.submitAtChunk(cp, r -> {
            var lvl = r.level();
            ((EndGatewayBlock) B.behavior(lvl.getBlockState(gw))).travelWithinLevel(lvl, gw, traveler(false), (l, t) -> c.complete(t));
            return null;
        }).get(10, TimeUnit.SECONDS);
        DimensionTransition tc = c.get(30, TimeUnit.SECONDS);
        assertEquals(ta.x(), tc.x(), 1e-9);
        assertEquals(ta.z(), tc.z(), 1e-9);
    }

    @Test
    void exitGatewayLeadsBack() throws Exception {
        start();
        Pos gw = new Pos(800, 85, 0);
        Pos home = new Pos(96, 75, 0);
        ChunkPos cp = ChunkPos.ofBlock(gw.x(), gw.z());
        end.submitAtChunk(cp, r -> r.loadChunk(cp)).get(10, TimeUnit.SECONDS).get(10, TimeUnit.SECONDS);
        var a = new CompletableFuture<DimensionTransition>();
        end.submitAtChunk(cp, r -> {
            var lvl = r.level();
            EndGatewayBlockEntity.placeGatewayPortal(lvl, gw, home, false);
            ((EndGatewayBlock) B.behavior(lvl.getBlockState(gw))).travelWithinLevel(lvl, gw, traveler(false), (l, t) -> a.complete(t));
            return null;
        }).get(10, TimeUnit.SECONDS);
        DimensionTransition t = a.get(30, TimeUnit.SECONDS);
        assertNotNull(t);
        // 슈퍼플랫 꼭대기(풀, -61) 위 — 기반암이 아닌 가장 높은 블록
        assertEquals(-60.0, t.y(), 1e-9);
        assertTrue(Math.abs(t.x() - 96.5) <= 5.0 && Math.abs(t.z() - 0.5) <= 5.0);
    }
}
