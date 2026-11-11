// PR #217 독립 검토 재현 시험(R1). 하네스는 A04InventoryLineTest 에서 복사.
// R1: 바닐라 ServerPlayer.openItemGui(aqv) 는 resolveBookComponents 가 참이면 containerMenu.broadcastChanges() 로 칸 갱신을 먼저 보내고
//     open_book 을 보낸다. 클라이언트는 open_book 을 받는 순간 손의 책으로 BookViewScreen 을 만든다.
package org.domi.furnace.server.plugin;


import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.domi.furnace.api.event.player.PlayerDropItemEvent;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.AABB;
import org.domi.furnace.server.entity.Cow;
import org.domi.furnace.server.entity.ItemEntity;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.PlayerData;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.ConnectionHandler;
import org.domi.furnace.server.net.ServerLoginContextAccess;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.GameMode;
import org.domi.furnace.server.world.Region;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class A12OpenBookOrderTest {

    private static final ChunkPos C00 = new ChunkPos(0, 0);
    private static final int AIR = B.defaultState("minecraft:air");
    private static final int GY = -61;
    private static final int HOT0 = 36;

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private PluginHost host;
    private final TestPlugins.Gamma plugin = new TestPlugins.Gamma();
    private EmbeddedChannel ch;
    private ConnectionHandler handler;
    private Player p;
    private int gx, gz;

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    @BeforeEach
    void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        host = core.plugins();
        core.world().setRegistries(org.domi.furnace.server.world.BlockRegistry.load("blocks/1.21.1.txt"), org.domi.furnace.server.world.ItemRegistry.load("items/1.21.1.txt"));
        plugin.setEnabled(true);
        core.world().submitAtChunk(C00, r -> r.chunkAtBlocking(C00)).get(30, TimeUnit.SECONDS);
        join();
        p.vitals().setGameMode(GameMode.SURVIVAL);
        drain();
        gx = (int) Math.floor(p.x()) + 2;
        gz = (int) Math.floor(p.z());
    }

    @AfterEach
    void tearDown() throws Exception {
        host.eventsImpl().unregisterAll(plugin);
        if (ch != null) ch.close();
        host.close();
        sched.close();
        TestServers.drainIoPools();
    }

    // ------------------------------------------------------------------ 도우미

    private static ByteBuf frame(int id, Consumer<ByteBuf> body) {
        ByteBuf b = Unpooled.buffer();
        Types.writeVarInt(b, id);
        if (body != null) body.accept(b);
        return b;
    }

    private Object field(String name) throws Exception {
        Field f = ConnectionHandler.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(handler);
    }

    private void join() throws Exception {
        var login = ServerLoginContextAccess.offline("t").core(core);
        handler = new ConnectionHandler(login, -1, sched, core.world(), core.world().entityIds());
        ch = new ConcurrentEmbeddedChannel(handler);
        ch.writeInbound(frame(0x00, b -> { Types.writeVarInt(b, 767); Types.writeString(b, "localhost"); b.writeShort(25565); Types.writeVarInt(b, 2); }));
        ch.writeInbound(frame(0x00, b -> { Types.writeString(b, "Tester"); Types.writeUuid(b, new UUID(0, 0)); }));
        ch.writeInbound(frame(0x03, null));
        ch.writeInbound(frame(0x07, b -> Types.writeVarInt(b, 0)));
        ch.writeInbound(frame(0x03, null));
        long deadline = System.nanoTime() + 20_000_000_000L;
        while (!(boolean) field("spawnChunksDelivered")) {
            ch.runPendingTasks();
            assertTrue(System.nanoTime() < deadline, "스폰 청크 전달이 끝나지 않음");
            Thread.sleep(20);
        }
        ch.writeInbound(frame(0x00, b -> Types.writeVarInt(b, 1)));
        ch.runPendingTasks();
        p = (Player) field("player");
        assertNotNull(p);
        double sx = (double) field("ix"), sz = (double) field("iz");
        var rp = new org.domi.furnace.api.sched.RegionPos(Math.floorDiv((int) Math.floor(sx), 16), Math.floorDiv((int) Math.floor(sz), 16));
        sched.region(rp).execute(() -> { }).get();
        p.drainPending();
    }

    private void tick(int n) throws Exception {
        for (int i = 0; i < n; i++) core.world().submitAtChunk(p.chunkPos(), r -> { r.tick(); return null; }).get(30, TimeUnit.SECONDS);
    }

    private void drain() {
        ch.runPendingTasks();
        Object o;
        while ((o = ch.readOutbound()) != null) ((ByteBuf) o).release();
    }

    private void send(ByteBuf f) { ch.writeInbound(f); }

    private <T> T at(java.util.function.Function<Region, T> f) throws Exception {
        return core.world().submitAtChunk(C00, f).get(30, TimeUnit.SECONDS);
    }

    private int blockAt(int x, int y, int z) throws Exception { return at(r -> r.level().getBlockState(new Pos(x, y, z))); }

    /** 플레이어 region 스레드를 잡아 둔다 — 그 사이 보낸 패킷의 region 작업은 큐에서 기다리고, 이벤트 루프 일은 바로 돈다. */
    private CountDownLatch holdRegion() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        core.world().submitAtChunk(p.chunkPos(), r -> {
            entered.countDown();
            try { release.await(30, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return null;
        });
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        return release;
    }

    private PlayerData.Snapshot savedSnapshot() throws Exception {
        long dl = System.nanoTime() + 5_000_000_000L;
        PlayerData.Snapshot s = null;
        while (System.nanoTime() < dl && (s = PlayerData.load(core.world().worldDir(), p.uuid())) == null) Thread.sleep(20);
        assertNotNull(s, "퇴장 저장이 없다");
        return s;
    }

    /** 플레이어 근처 바닥의 아이템 엔티티 개수 합. */
    private int floorCount(String item) throws Exception {
        int id = B.stack(item, 1).itemId();
        return at(r -> {
            int n = 0;
            var box = new AABB(p.x() - 16, p.y() - 16, p.z() - 16, p.x() + 16, p.y() + 16, p.z() + 16);
            for (var e : r.level().getEntities(null, box, x -> x instanceof ItemEntity)) {
                var s = ((ItemEntity) e).getItem();
                if (s.itemId() == id && !e.isRemoved()) n += s.count();
            }
            return n;
        });
    }

    private int invCount(String item) {
        int id = B.stack(item, 1).itemId(), n = 0;
        for (ItemStack s : p.inventory().snapshot()) if (!s.isEmpty() && s.itemId() == id) n += s.count();
        return n;
    }

    private void useItemOn(int hand, int x, int y, int z, int face, int seq) {
        send(frame(0x38, b -> {
            Types.writeVarInt(b, hand);
            Types.writeBlockPos(b, x, y, z);
            Types.writeVarInt(b, face);
            b.writeFloat(0.5f); b.writeFloat(1.0f); b.writeFloat(0.5f);
            b.writeBoolean(false);
            Types.writeVarInt(b, seq);
        }));
    }

    /** player_action 3(Ctrl+Q 한 칸 통째)·4(Q 하나)·6(손 바꾸기). */
    private void playerAction(int status) {
        send(frame(0x24, b -> { Types.writeVarInt(b, status); Types.writeBlockPos(b, 0, 0, 0); b.writeByte(0); Types.writeVarInt(b, 0); }));
    }


    private java.util.List<Integer> outIds() {
        ch.runPendingTasks();
        java.util.List<Integer> out = new ArrayList<>();
        Object o;
        while ((o = ch.readOutbound()) != null) {
            ByteBuf bb = (ByteBuf) o;
            out.add(Types.readVarInt(bb));
            bb.release();
        }
        return out;
    }

    private static ItemStack book(boolean resolved) {
        ItemStack s = new ItemStack(ItemContext.id("minecraft:item", "written_book"), 1);
        return org.domi.furnace.server.item.ItemTools.with(s, "minecraft:written_book_content",
                java.util.Map.of("title", "T", "author", "A", "generation", 0, "pages", List.of("{\"text\":\"hi\"}"), "resolved", resolved));
    }

    /** R1: 풀린 책의 칸 갱신(container_set_slot/content)이 open_book 보다 먼저 가야 한다(같은 틱 안, 다음 틱 broadcast 를 기다리지 않고). */
    @Test
    void resolvedBookSlotUpdatePrecedesOpenBook() throws Exception {
        int ws = p.inventory().heldWindowSlot();
        p.inventory().set(ws, book(false));
        tick(1);
        drain();   // 클라이언트는 풀리지 않은 책을 안다
        send(frame(0x39, b -> { Types.writeVarInt(b, 0); Types.writeVarInt(b, 1); b.writeFloat(p.yaw()); b.writeFloat(p.pitch()); }));
        at(r -> null);   // region 이 use_item 을 처리할 때까지
        at(r -> null);
        var ids = outIds();
        int open = ids.indexOf(org.domi.furnace.server.net.protocol.play.OpenBook.ID);
        assertTrue(open >= 0, "open_book 이 가야 한다: " + ids);
        int slot = -1;
        for (int i = 0; i < ids.size(); i++) {
            int id = ids.get(i);
            if (id == org.domi.furnace.server.net.protocol.play.ContainerSetSlot.ID || id == org.domi.furnace.server.net.protocol.play.ContainerSetContent.ID) { slot = i; break; }
        }
        Object c = org.domi.furnace.server.item.ItemTools.component(p.inventory().get(ws), "minecraft:written_book_content");
        assertEquals(Boolean.TRUE, ((java.util.Map<?, ?>) c).get("resolved"), "서버 쪽 책은 풀렸다");
        assertTrue(slot >= 0 && slot < open, "바닐라: 칸 갱신 → open_book. 보낸 순서(id): " + ids);
    }
}
