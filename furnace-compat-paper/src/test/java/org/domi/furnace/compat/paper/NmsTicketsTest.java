package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftWorld;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.compat.nms.NmsTickets;
import org.domi.furnace.compat.paper.fixtures.JarCompiler;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * N2b-6 NmsTickets(NMS 청크 티켓 → Furnace 청크 티켓): 수준 → 반경, 같은 티켓은 하나, 만료 미루기, remove, 주인이 꺼지면 표도 비움,
 * 풀린 뒤 다시 걸기, 동시에 걸고 풀어도 티켓이 새지 않는다. 티켓을 거는 호출은 플러그인 코드에서 해야 주인을 찾으므로 시험 플러그인의 정적 메서드를 부른다.
 */
class NmsTicketsTest {

    private static final String YML = """
            name: TicketUser
            version: '1'
            main: nmstk.TkPlugin
            api-version: '1.21'
            """;

    private static final String PLUGIN = """
            package nmstk;
            import org.bukkit.plugin.java.JavaPlugin;
            public final class TkPlugin extends JavaPlugin {}
            """;

    private static final String TK = """
            package nmstk;
            import net.minecraft.server.level.ServerLevel;
            import net.minecraft.server.level.TicketType;
            import net.minecraft.util.Unit;
            import net.minecraft.world.level.ChunkPos;
            public final class Tk {
                public static void add(ServerLevel l, int x, int z, int level) { l.getChunkSource().addTicketAtLevel(TicketType.PLUGIN, new ChunkPos(x, z), level, Unit.INSTANCE); }
                public static void remove(ServerLevel l, int x, int z, int level) { l.getChunkSource().removeTicketAtLevel(TicketType.PLUGIN, new ChunkPos(x, z), level, Unit.INSTANCE); }
                public static void addTimed(ServerLevel l, int x, int z, int level) { l.getChunkSource().addTicketAtLevel(TicketType.POST_TELEPORT, new ChunkPos(x, z), level, 7); }
            }
            """;

    @TempDir
    Path tmp;

    private FurnaceTestServer server;
    private ServerLevel level;
    private Method add, remove, addTimed;

    @BeforeEach
    void start() throws Exception {
        Path jar = JarCompiler.jar(tmp.resolve("tk.jar"), YML, Map.of("nmstk.TkPlugin", PLUGIN, "nmstk.Tk", TK), Set.of("net.minecraft."));
        server = FurnaceTestServer.builder().failFast(false).pluginJar(jar).start();
        Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE);
        level = ((CraftWorld) Bukkit.getWorlds().get(0)).getHandle();
        Class<?> tk = Bukkit.getPluginManager().getPlugin("TicketUser").getClass().getClassLoader().loadClass("nmstk.Tk");
        add = tk.getMethod("add", ServerLevel.class, int.class, int.class, int.class);
        remove = tk.getMethod("remove", ServerLevel.class, int.class, int.class, int.class);
        addTimed = tk.getMethod("addTimed", ServerLevel.class, int.class, int.class, int.class);
    }

    @AfterEach
    void stop() { server.close(); }

    private FurnacePlugin owner() { return server.plugin("TicketUser"); }

    private org.domi.furnace.api.world.World world() { return level.getWorld().furnaceHandle(); }

    private int held() { return server.api().chunkTickets().tickets(owner()).size(); }

    @Test
    void levelBecomesLoadRadiusAndTickRadiusAndRemoveReleases() throws Exception {
        add.invoke(null, level, 400, 400, 31);   // 로드 반경 2, 엔티티 틱 반경 0
        assertEquals(1, NmsTickets.countAt(level, new ChunkPos(400, 400)));
        assertEquals(2, held(), "로드 티켓 하나 + 틱 티켓 하나");
        var tickets = server.api().chunkTickets();
        assertTrue(tickets.isHeld(world(), 402, 398));
        add.invoke(null, level, 400, 400, 31);   // 같은 티켓은 하나로 센다
        assertEquals(1, NmsTickets.countAt(level, new ChunkPos(400, 400)));
        assertEquals(2, held());
        remove.invoke(null, level, 400, 400, 31);
        assertEquals(0, NmsTickets.countAt(level, new ChunkPos(400, 400)));
        assertEquals(0, held());
        remove.invoke(null, level, 400, 400, 31);   // 없는 것을 풀어도 아무 일도 없다
        add.invoke(null, level, 410, 410, 34);   // 33 보다 크면 아무것도 붙잡지 않는다(세기만 한다)
        assertEquals(1, NmsTickets.countAt(level, new ChunkPos(410, 410)));
        assertEquals(0, held());
    }

    @Test
    void expiringTicketsAreExtendedWhenAddedAgainAndReleasedWhenTheyRunOut() throws Exception {
        addTimed.invoke(null, level, 420, 420, 32);   // POST_TELEPORT: 만료 5틱
        assertEquals(1, NmsTickets.countAt(level, new ChunkPos(420, 420)));
        server.tick(3);
        addTimed.invoke(null, level, 420, 420, 32);   // 만료 미루기
        server.tick(3);
        assertEquals(1, NmsTickets.countAt(level, new ChunkPos(420, 420)), "다시 걸어 아직 만료 전");
        server.tick(10);
        assertEquals(0, NmsTickets.countAt(level, new ChunkPos(420, 420)), "만료됨");
        assertEquals(0, held());
    }

    @Test
    void aTicketReleasedBehindOurBackIsHeldAgainWhenAddedAgain() throws Exception {
        add.invoke(null, level, 430, 430, 33);
        assertEquals(1, held());
        for (var t : List.copyOf(server.api().chunkTickets().tickets(owner()))) t.release();   // 서버가 풀었다(주인이 꺼짐·월드 내림처럼)
        assertEquals(0, held());
        add.invoke(null, level, 430, 430, 33);   // 낡은 항목이 "이미 있음"으로 막으면 안 된다
        assertEquals(1, held(), "다시 붙잡았다");
        assertEquals(1, NmsTickets.countAt(level, new ChunkPos(430, 430)));
    }

    @Test
    void disablingTheOwnerClearsItsEntries() throws Exception {
        add.invoke(null, level, 440, 440, 33);
        add.invoke(null, level, 441, 441, 32);
        assertEquals(2, NmsTickets.countAt(level, new ChunkPos(440, 440)) + NmsTickets.countAt(level, new ChunkPos(441, 441)));
        NmsTickets.pluginDisabled(owner());
        assertEquals(0, NmsTickets.countAt(level, new ChunkPos(440, 440)));
        assertEquals(0, NmsTickets.countAt(level, new ChunkPos(441, 441)));
        assertEquals(0, held(), "표에서 떼며 Furnace 티켓도 풀었다");
        add.invoke(null, level, 440, 440, 33);   // 다시 켠 플러그인이 같은 티켓을 걸면 새로 붙잡는다
        assertEquals(1, held());
    }

    @Test
    void concurrentAddAndRemoveDoNotLeakTickets() throws Exception {
        int threads = 6;
        int rounds = 400;
        CountDownLatch go = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        List<Thread> ts = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            boolean adder = i % 2 == 0;
            Thread t = new Thread(() -> {
                try {
                    go.await();
                    for (int r = 0; r < rounds; r++) {
                        if (adder) add.invoke(null, level, 450, 450, 33);
                        else remove.invoke(null, level, 450, 450, 33);
                    }
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                }
            }, "tickets-" + i);
            ts.add(t);
            t.start();
        }
        go.countDown();
        for (Thread t : ts) t.join(60_000);
        assertEquals(null, failure.get(), String.valueOf(failure.get()));
        remove.invoke(null, level, 450, 450, 33);
        assertEquals(0, NmsTickets.countAt(level, new ChunkPos(450, 450)));
        assertEquals(0, held(), "다 풀었는데 Furnace 쪽에 티켓이 남았다 — 새는 티켓");
        assertNotNull(owner());
        assertFalse(server.api().chunkTickets().isHeld(world(), 450, 450));
    }
}
