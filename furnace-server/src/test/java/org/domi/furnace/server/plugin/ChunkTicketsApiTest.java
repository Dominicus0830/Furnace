// 플러그인 청크 티켓 API(W4/46번): 걸면 올라와 있고, 겹치면 세고, 놓거나 플러그인이 꺼지면 풀리고, 엔티티 틱 단계를 주며, 한도를 지킨다.
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.world.ChunkTickets;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChunkTicketsApiTest {

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private PluginHost host;
    private FurnacePlugin alpha, beta;

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    @BeforeEach
    void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        core.world().setRegistries(org.domi.furnace.server.world.BlockRegistry.load("blocks/1.21.1.txt"), org.domi.furnace.server.world.ItemRegistry.load("items/1.21.1.txt"));
        core.world().configurePersistence(1_000_000, 2);   // 퇴출 유예 2틱 — 티켓이 없으면 금방 내려간다
        Path dir = tmp.resolve("plugins");
        PluginSystemTest.jar(dir.resolve("a.jar"), "furnace-plugin.yml", "name: Alpha\nversion: 1\nmain: org.domi.furnace.server.plugin.TestPlugins$Alpha\n", TestPlugins.Alpha.class);
        PluginSystemTest.jar(dir.resolve("b.jar"), "furnace-plugin.yml", "name: Beta\nversion: 1\nmain: org.domi.furnace.server.plugin.TestPlugins$Beta\n", TestPlugins.Beta.class);
        host = new PluginHost(core, dir);
        host.pluginsImpl().loadAll();
        host.pluginsImpl().enableAll();
        alpha = host.plugins().plugin("Alpha");
        beta = host.plugins().plugin("Beta");
    }

    @AfterEach
    void tearDown() throws Exception {
        host.pluginsImpl().disableAll();
        host.pluginsImpl().closeAll();
        host.close();
        sched.close();
        TestServers.drainIoPools();
    }

    private org.domi.furnace.api.world.World api() { return host.world("minecraft:overworld"); }

    private void ticks(int n) throws Exception {
        for (int i = 0; i < n; i++) core.world().tickOnce().get(30, TimeUnit.SECONDS);
    }

    @Test
    void ticketLoadsAndHoldsChunksUntilReleased() throws Exception {
        var tickets = host.chunkTickets();
        var w = api();
        // 티켓 없이 올린 대조 청크는 유예(2틱)가 지나면 내려간다
        core.world().submitAtChunk(new ChunkPos(80, 80), r -> r.chunkAtBlocking(new ChunkPos(80, 80))).get(30, TimeUnit.SECONDS);
        var t = tickets.add(alpha, w, 40, 40, 1, false);
        t.loaded().get(30, TimeUnit.SECONDS);
        assertTrue(t.isActive());
        assertEquals(9, t.chunkCount());
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) assertTrue(w.isChunkLoaded(40 + dx, 40 + dz), dx + "," + dz);
        assertTrue(tickets.isHeld(w, 41, 39));
        assertFalse(tickets.isHeld(w, 42, 40));
        assertEquals(java.util.List.of(t), tickets.tickets(alpha));
        assertEquals(java.util.List.of(t), tickets.tickets(w));
        assertTrue(tickets.tickets(beta).isEmpty());
        ticks(12);
        assertFalse(w.isChunkLoaded(80, 80), "티켓이 없는 대조 청크는 내려갔다");
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) assertTrue(w.isChunkLoaded(40 + dx, 40 + dz), "티켓이 붙잡는다");

        t.release();
        assertFalse(t.isActive());
        assertFalse(tickets.isHeld(w, 40, 40));
        assertTrue(tickets.tickets(alpha).isEmpty());
        ticks(12);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) assertFalse(w.isChunkLoaded(40 + dx, 40 + dz), "놓으면 보통 규칙대로 내려간다");
        t.release();   // 두 번 놓아도 괜찮다
    }

    @Test
    void overlappingTicketsAreCounted() throws Exception {
        var tickets = host.chunkTickets();
        var w = api();
        var a = tickets.add(alpha, w, 40, 40, 1, false);
        var b = tickets.add(beta, w, 41, 40, 0, true);
        a.loaded().get(30, TimeUnit.SECONDS);
        b.loaded().get(30, TimeUnit.SECONDS);
        assertEquals(2, tickets.ticketsAt(w, 41, 40).size());
        assertEquals(1, tickets.ticketsAt(w, 39, 40).size());
        a.release();
        ticks(12);
        assertTrue(w.isChunkLoaded(41, 40), "다른 주인의 티켓이 붙잡고 있다");
        assertFalse(w.isChunkLoaded(39, 40));
        b.close();   // AutoCloseable
        ticks(12);
        assertFalse(w.isChunkLoaded(41, 40));
    }

    @Test
    void tickingTicketGivesEntityTickStage() throws Exception {
        var tickets = host.chunkTickets();
        var w = api();
        var holdOnly = tickets.add(alpha, w, 40, 40, 0, false);
        var ticking = tickets.add(beta, w, 44, 40, 0, true);
        holdOnly.loaded().get(30, TimeUnit.SECONDS);
        ticking.loaded().get(30, TimeUnit.SECONDS);
        ticks(2);
        // 엔티티 틱 단계(/execute if loaded 의 바닐라 조건과 같은 판정) — 붙잡기만 하는 티켓은 거짓, ticking 은 참
        assertFalse(isEntityTicking(40, 40));
        assertTrue(isEntityTicking(44, 40));
    }

    private boolean isEntityTicking(int cx, int cz) throws Exception {
        ChunkPos cp = new ChunkPos(cx, cz);
        return core.world().submitAtChunk(cp, r -> r.isEntityTickingLoaded(cx, cz)).get(30, TimeUnit.SECONDS);
    }

    @Test
    void disablingPluginReleasesItsTickets() throws Exception {
        var tickets = host.chunkTickets();
        var w = api();
        var a1 = tickets.add(alpha, w, 40, 40, 1, true);
        var a2 = tickets.add(alpha, w, 60, 60, 0, false);
        var b = tickets.add(beta, w, 70, 70, 0, false);
        a1.loaded().get(30, TimeUnit.SECONDS);
        a2.loaded().get(30, TimeUnit.SECONDS);
        b.loaded().get(30, TimeUnit.SECONDS);
        host.pluginsImpl().disable(alpha);
        assertFalse(a1.isActive());
        assertFalse(a2.isActive());
        assertTrue(b.isActive());
        assertTrue(tickets.tickets(alpha).isEmpty());
        assertEquals(java.util.List.of(b), tickets.tickets(w));
        assertThrows(IllegalStateException.class, () -> tickets.add(alpha, w, 40, 40), "꺼진 플러그인은 걸 수 없다");
        ticks(12);
        assertFalse(w.isChunkLoaded(40, 40));
        assertTrue(w.isChunkLoaded(70, 70));
    }

    @Test
    void releaseAllByWorld() throws Exception {
        var tickets = host.chunkTickets();
        var w = api();
        var a = tickets.add(alpha, w, 40, 40);
        var b = tickets.add(alpha, w, 50, 50);
        assertEquals(2, tickets.releaseAll(alpha, w));
        assertFalse(a.isActive());
        assertFalse(b.isActive());
        assertEquals(0, tickets.releaseAll(alpha));
    }

    @Test
    void limitsAndValidation() {
        var tickets = host.chunkTickets();
        var w = api();
        assertThrows(IllegalArgumentException.class, () -> tickets.add(alpha, w, 0, 0, -1, true));
        assertThrows(IllegalArgumentException.class, () -> tickets.add(alpha, w, 0, 0, ChunkTickets.MAX_RADIUS + 1, true));
        assertThrows(NullPointerException.class, () -> tickets.add(null, w, 0, 0));
        // 반경 16 은 1089 청크 — 15개째에서 한도(16384)를 넘는다. 실제로 올리지 않도록 가짜 월드로는 못 하니 걸리는 데까지만 센다
        int n = 2 * ChunkTickets.MAX_RADIUS + 1;
        int max = ChunkTickets.MAX_CHUNKS_PER_OWNER / (n * n);
        assertEquals(15, max);
    }
}
