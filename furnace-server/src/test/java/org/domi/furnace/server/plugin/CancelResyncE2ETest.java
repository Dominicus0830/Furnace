// 이벤트 취소 계약(docs/api/event-cancel-contract.md)의 끝-끝 시험: 진짜 연결 핸들러에 클라이언트 패킷을 넣고,
// 플러그인이 이벤트를 취소하면 ① 서버 상태가 하나도 안 바뀌고 ② 클라이언트가 먼저 예측했을 수 있는 것을 코어가 되돌려 보내는지 본다.
// 클라이언트는 평소 칸 개수·블록·능력·위치를 먼저 바꿔 보므로(예측), "되돌려 보내는 패킷"이 나가야 화면이 어긋나지 않는다.
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
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.domi.furnace.api.event.block.BlockBreakEvent;
import org.domi.furnace.api.event.block.BlockPlaceEvent;
import org.domi.furnace.api.event.entity.EntitySpawnEvent;
import org.domi.furnace.api.event.entity.ProjectileLaunchEvent;
import org.domi.furnace.api.event.inventory.InventoryClickEvent;
import org.domi.furnace.api.event.player.PlayerDropItemEvent;
import org.domi.furnace.api.event.player.PlayerEditBookEvent;
import org.domi.furnace.api.event.player.PlayerFishEvent;
import org.domi.furnace.api.event.player.PlayerInteractEntityEvent;
import org.domi.furnace.api.event.player.PlayerInteractEvent;
import org.domi.furnace.api.event.player.PlayerItemConsumeEvent;
import org.domi.furnace.api.event.player.PlayerItemHeldEvent;
import org.domi.furnace.api.event.player.PlayerMoveEvent;
import org.domi.furnace.api.event.player.PlayerSwapHandItemsEvent;
import org.domi.furnace.api.event.player.PlayerToggleFlightEvent;
import org.domi.furnace.api.event.player.PlayerToggleSneakEvent;
import org.domi.furnace.api.event.player.PlayerToggleSprintEvent;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.Cow;
import org.domi.furnace.server.entity.ItemEntity;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.ConnectionHandler;
import org.domi.furnace.server.net.ServerLoginContextAccess;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.play.BlockChangedAck;
import org.domi.furnace.server.net.protocol.play.BlockUpdate;
import org.domi.furnace.server.net.protocol.play.ContainerSetContent;
import org.domi.furnace.server.net.protocol.play.ContainerSetSlot;
import org.domi.furnace.server.net.protocol.play.PlayerAbilities;
import org.domi.furnace.server.net.protocol.play.SetHeldSlot;
import org.domi.furnace.server.net.protocol.play.SyncPlayerPosition;
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

class CancelResyncE2ETest {

    private static final ChunkPos C00 = new ChunkPos(0, 0);
    private static final int AIR = B.defaultState("minecraft:air");

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private PluginHost host;
    private final TestPlugins.Gamma plugin = new TestPlugins.Gamma();
    private EmbeddedChannel ch;
    private ConnectionHandler handler;
    private Player p;
    /** 나간 패킷(id, 본문) 누적. */
    private final List<Pkt> out = new ArrayList<>();

    record Pkt(int id, byte[] body) {
        ByteBuf buf() { return Unpooled.wrappedBuffer(body); }
    }

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
    }

    @AfterEach
    void tearDown() throws Exception {
        host.eventsImpl().unregisterAll(plugin);
        if (ch != null) ch.close();
        host.close();
        sched.close();
        TestServers.drainIoPools();
    }

    // ------------------------------------------------------------------ 접속·패킷 도우미

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
        ch.writeInbound(frame(0x00, b -> Types.writeVarInt(b, 1)));   // 스폰 텔레포트 확인
        ch.runPendingTasks();
        p = (Player) field("player");
        assertNotNull(p);
        double sx = (double) field("ix"), sz = (double) field("iz");
        var rp = new org.domi.furnace.api.sched.RegionPos(Math.floorDiv((int) Math.floor(sx), 16), Math.floorDiv((int) Math.floor(sz), 16));
        sched.region(rp).execute(() -> { }).get();
        p.drainPending();   // 접속 대기 중인 위치를 region 이 반영
        p.vitals().setGameMode(GameMode.SURVIVAL);
        drain();
        out.clear();
        gx = (int) Math.floor(p.x()) + 2;
        gz = (int) Math.floor(p.z());
    }

    /** 플레이어 옆 바닥 블록(그 위 칸이 설치 자리). */
    private int gx, gz;
    private static final int GY = -61;

    /** 플레이어가 속한 region 을 n 틱 돌린다(이 시험의 스케줄러는 스스로 틱하지 않는다). */
    private void tick(int n) throws Exception {
        for (int i = 0; i < n; i++) {
            core.world().submitAtChunk(p.chunkPos(), r -> { r.tick(); return null; }).get(30, TimeUnit.SECONDS);
        }
    }

    private void drain() {
        ch.runPendingTasks();
        Object o;
        while ((o = ch.readOutbound()) != null) {
            ByteBuf bb = (ByteBuf) o;
            int id = Types.readVarInt(bb);
            byte[] body = new byte[bb.readableBytes()];
            bb.readBytes(body);
            bb.release();
            out.add(new Pkt(id, body));
        }
    }

    /** 조건을 만족하는 패킷이 나올 때까지(최대 10초) 모은다. */
    private Pkt await(int id, Predicate<Pkt> test, String what) throws Exception {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (true) {
            tick(1);
            drain();
            for (Pkt k : out) if (k.id() == id && test.test(k)) return k;
            if (System.nanoTime() > deadline) fail("기다린 패킷이 안 나왔다: " + what + " / 나간 것: " + ids());
            Thread.sleep(20);
        }
    }

    /** 일정 시간(1초) 동안 모아서 그런 패킷이 하나도 없는지 본다. */
    private boolean none(int id, Predicate<Pkt> test) throws Exception {
        for (int i = 0; i < 20; i++) { tick(1); drain(); }
        for (Pkt k : out) if (k.id() == id && test.test(k)) return false;
        return true;
    }

    private List<String> ids() {
        List<String> r = new ArrayList<>();
        for (Pkt k : out) r.add("0x" + Integer.toHexString(k.id()));
        return r;
    }

    private void send(ByteBuf f) { ch.writeInbound(f); }

    private <E extends org.domi.furnace.api.event.Event> void on(Class<E> type, Consumer<? super E> h) {
        host.eventsImpl().subscribe(plugin, type, h);
    }

    private <T> T at(java.util.function.Function<Region, T> f) throws Exception {
        return core.world().submitAtChunk(C00, f).get(30, TimeUnit.SECONDS);
    }

    private int blockAt(int x, int y, int z) throws Exception {
        return at(r -> r.level().getBlockState(new Pos(x, y, z)));
    }

    /** container_set_slot 본문: 창 id(byte), 상태(VarInt), 칸(short), 개수(VarInt), 아이템(VarInt). */
    private record Slot(int container, int slot, int count, int item) {}

    private static Slot slotOf(Pkt k) {
        ByteBuf b = k.buf();
        int c = b.readByte();
        Types.readVarInt(b);
        int s = b.readShort();
        int n = Types.readVarInt(b);
        int item = n > 0 ? Types.readVarInt(b) : 0;
        return new Slot(c, s, n, item);
    }

    private static boolean setSlot(Pkt k, int container, int slot, int count) {
        Slot s = slotOf(k);
        return s.container() == container && s.slot() == slot && s.count() == count;
    }

    private static int[] blockUpdateAt(Pkt k) {
        ByteBuf b = k.buf();
        int[] pos = Types.readBlockPos(b);
        return new int[] {pos[0], pos[1], pos[2], Types.readVarInt(b)};
    }

    private static boolean blockUpdateIs(Pkt k, int x, int y, int z, int state) {
        int[] u = blockUpdateAt(k);
        return u[0] == x && u[1] == y && u[2] == z && u[3] == state;
    }

    private void give(int windowSlot, String item, int n) {
        p.inventory().set(windowSlot, B.stack(item, n));
    }

    private int count(int windowSlot) { return p.inventory().get(windowSlot).count(); }

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

    private void useItem(int hand, int seq) {
        send(frame(0x39, b -> { Types.writeVarInt(b, hand); Types.writeVarInt(b, seq); b.writeFloat(p.yaw()); b.writeFloat(p.pitch()); }));
    }

    private void playerAction(int status, int x, int y, int z, int face, int seq) {
        send(frame(0x24, b -> { Types.writeVarInt(b, status); Types.writeBlockPos(b, x, y, z); b.writeByte(face); Types.writeVarInt(b, seq); }));
    }

    private static final int HOT0 = 36;   // 창 칸 36 = 단축 칸 0 = Inventory 번호 0

    // ------------------------------------------------------------------ 블록 설치

    @Test
    void blockPlaceCancelKeepsWorldAndResendsHandAndBlock() throws Exception {
        List<String> log = new java.util.concurrent.CopyOnWriteArrayList<>();
        on(BlockPlaceEvent.class, e -> { log.add(e.placedType()); e.setCancelled(true); });
        give(HOT0, "minecraft:stone", 5);
        useItemOn(0, gx, GY, gz, 1, 7);
        await(BlockChangedAck.ID, k -> true, "block_changed_ack");
        Pkt hand = await(ContainerSetSlot.ID, k -> setSlot(k, -2, 0, 5), "손 칸(-2, 0번) 5개로 되돌림");
        assertEquals(B.stack("minecraft:stone", 5).itemId(), slotOf(hand).item());
        await(BlockUpdate.ID, k -> blockUpdateIs(k, gx, GY + 1, gz, AIR), "설치하려던 칸을 공기로");
        assertEquals(AIR, blockAt(gx, GY + 1, gz), "취소하면 블록이 놓이지 않는다");
        assertEquals(5, count(HOT0), "취소하면 아이템이 줄지 않는다");
        assertEquals(List.of("minecraft:stone"), log);
    }

    @Test
    void blockPlaceWithoutCancelStillConsumes() throws Exception {
        give(HOT0, "minecraft:stone", 5);
        useItemOn(0, gx, GY, gz, 1, 7);
        await(BlockChangedAck.ID, k -> true, "ack");
        assertEquals(B.defaultState("minecraft:stone"), blockAt(gx, GY + 1, gz));
        assertEquals(4, count(HOT0));
    }

    // ------------------------------------------------------------------ 도우미(조건 기다리기)

    /** 조건이 참이 될 때까지 region 을 돌리며 기다린다(최대 10초). */
    private void until(java.util.function.BooleanSupplier cond, String what) throws Exception {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (!cond.getAsBoolean()) {
            if (System.nanoTime() > deadline) fail("조건이 안 맞았다: " + what + " / 나간 것: " + ids());
            tick(1);
            drain();
        }
    }

    private static final java.util.List<String> NONE = java.util.List.of();

    private ItemStack inv(int windowSlot) { return p.inventory().get(windowSlot); }

    // ------------------------------------------------------------------ 블록 부수기·왼쪽 클릭

    @Test
    void blockBreakCancelKeepsBlockAndResendsIt() throws Exception {
        p.vitals().setGameMode(GameMode.CREATIVE);
        List<String> log = new java.util.concurrent.CopyOnWriteArrayList<>();
        on(BlockBreakEvent.class, e -> { log.add(e.blockType()); e.setCancelled(true); });
        int ground = blockAt(gx, GY, gz);
        drain();
        out.clear();
        playerAction(0, gx, GY, gz, 1, 3);   // START_DESTROY_BLOCK — 창조 모드는 한 번에 부순다
        await(BlockUpdate.ID, k -> blockUpdateIs(k, gx, GY, gz, ground), "부수려던 블록을 원래 상태로");
        assertEquals(ground, blockAt(gx, GY, gz), "취소하면 블록이 그대로");
        assertEquals(1, log.size());
    }

    @Test
    void blockBreakCancelResendsBlockEntityToo() throws Exception {
        p.vitals().setGameMode(GameMode.CREATIVE);
        int sign = B.defaultState("minecraft:oak_sign");
        at(r -> { r.level().setBlock(new Pos(gx, GY + 1, gz), sign, 3); return null; });
        assertTrue(at(r -> (Object) r.level().getBlockEntity(new Pos(gx, GY + 1, gz)) != null).booleanValue(), "표지판 블록 엔티티");
        on(BlockBreakEvent.class, e -> e.setCancelled(true));
        tick(2);
        drain();
        out.clear();
        playerAction(0, gx, GY + 1, gz, 1, 3);
        await(BlockUpdate.ID, k -> blockUpdateIs(k, gx, GY + 1, gz, sign), "표지판 상태 재전송");
        await(org.domi.furnace.server.net.protocol.play.BlockEntityDataPacket.ID, k -> true, "표지판 블록 엔티티 재전송");
        assertEquals(sign, blockAt(gx, GY + 1, gz));
    }

    @Test
    void leftClickBlockCancelResendsBlock() throws Exception {
        p.vitals().setGameMode(GameMode.CREATIVE);
        on(PlayerInteractEvent.class, e -> { if (e.action().isLeftClick()) e.setCancelled(true); });
        int ground = blockAt(gx, GY, gz);
        drain();
        out.clear();
        playerAction(0, gx, GY, gz, 1, 3);
        await(BlockUpdate.ID, k -> blockUpdateIs(k, gx, GY, gz, ground), "왼쪽 클릭 취소 → 블록 재전송");
        assertEquals(ground, blockAt(gx, GY, gz));
    }

    // ------------------------------------------------------------------ 오른쪽 클릭

    @Test
    void interactAirCancelResendsHand() throws Exception {
        give(HOT0, "minecraft:stone", 5);
        List<String> log = new java.util.concurrent.CopyOnWriteArrayList<>();
        on(PlayerInteractEvent.class, e -> { log.add(e.action().name()); e.setCancelled(true); });
        useItem(0, 4);
        await(ContainerSetSlot.ID, k -> setSlot(k, -2, 0, 5), "손 칸 재전송");
        assertEquals(List.of("RIGHT_CLICK_AIR"), log);
        assertEquals(5, count(HOT0));
    }

    @Test
    void interactBlockCancelResendsHandAndBothBlocks() throws Exception {
        give(HOT0, "minecraft:stone", 5);
        on(PlayerInteractEvent.class, e -> { if (e.action().isRightClick()) e.setCancelled(true); });
        int ground = blockAt(gx, GY, gz);
        useItemOn(0, gx, GY, gz, 1, 9);
        await(ContainerSetSlot.ID, k -> setSlot(k, -2, 0, 5), "손 칸 재전송");
        await(BlockUpdate.ID, k -> blockUpdateIs(k, gx, GY, gz, ground), "눌린 블록 재전송");
        await(BlockUpdate.ID, k -> blockUpdateIs(k, gx, GY + 1, gz, AIR), "면 너머 칸 재전송");
        assertEquals(AIR, blockAt(gx, GY + 1, gz));
        assertEquals(5, count(HOT0));
    }

    @Test
    void interactEntityCancelResendsHandAndDoesNothing() throws Exception {
        give(HOT0, "minecraft:wheat", 4);
        Cow[] cow = new Cow[1];
        int eid = at(r -> {
            var l = r.level();
            cow[0] = new Cow(l, l.nextEntityId());
            cow[0].moveTo(p.x() + 1.5, p.y(), p.z(), 0, 0);
            l.addFreshEntity(cow[0]);
            return cow[0].id();
        });
        List<String> log = new java.util.concurrent.CopyOnWriteArrayList<>();
        on(PlayerInteractEntityEvent.class, e -> { log.add(e.target().type()); e.setCancelled(true); });
        send(frame(0x16, b -> { Types.writeVarInt(b, eid); Types.writeVarInt(b, 0); Types.writeVarInt(b, 0); b.writeBoolean(false); }));
        await(ContainerSetSlot.ID, k -> setSlot(k, -2, 0, 4), "손 칸 재전송");
        assertEquals(List.of("minecraft:cow"), log);
        assertEquals(4, count(HOT0), "취소하면 밀을 먹이지 않는다");
        assertEquals(0, (int) at(r -> cow[0].inLoveTime()), "사랑 상태가 되지 않는다");
    }

    // ------------------------------------------------------------------ 던지기·놓기로 엔티티를 만드는 아이템

    @Test
    void projectileLaunchCancelKeepsSnowballAndResendsHand() throws Exception {
        give(HOT0, "minecraft:snowball", 5);
        List<String> log = new java.util.concurrent.CopyOnWriteArrayList<>();
        on(ProjectileLaunchEvent.class, e -> { log.add(e.entity().type()); e.setCancelled(true); });
        useItem(0, 4);
        await(ContainerSetSlot.ID, k -> setSlot(k, -2, 0, 5), "눈덩이 5개로 되돌림");
        assertEquals(List.of("minecraft:snowball"), log);
        assertEquals(5, count(HOT0), "취소하면 눈덩이가 줄지 않는다");
    }

    @Test
    void entitySpawnCancelKeepsArmorStandItemAndResendsHand() throws Exception {
        give(HOT0, "minecraft:armor_stand", 3);
        on(EntitySpawnEvent.class, e -> e.setCancelled(true));
        useItemOn(0, gx, GY, gz, 1, 5);
        await(ContainerSetSlot.ID, k -> setSlot(k, -2, 0, 3), "거치대 3개로 되돌림");
        assertEquals(3, count(HOT0), "취소하면 아이템이 줄지 않는다");
    }

    @Test
    void entitySpawnCancelClearsFishingHook() throws Exception {
        give(HOT0, "minecraft:fishing_rod", 1);
        List<String> log = new java.util.concurrent.CopyOnWriteArrayList<>();
        on(EntitySpawnEvent.class, e -> { log.add(e.entity().type()); e.setCancelled(true); });
        useItem(0, 4);
        until(() -> !log.isEmpty(), "낚시찌 스폰 이벤트");
        assertNull(p.fishing, "취소된 낚시찌가 플레이어에게 남으면 다음 우클릭이 회수로 간다");
    }

    @Test
    void fishCastCancelThrowsNoHookAndLeavesRodAlone() throws Exception {
        give(HOT0, "minecraft:fishing_rod", 1);
        ItemStack before = p.inventory().get(HOT0);
        boolean[] cancel = {true};
        List<String> log = new java.util.concurrent.CopyOnWriteArrayList<>();
        on(PlayerFishEvent.class, e -> { log.add(e.state().name()); e.setCancelled(cancel[0]); });
        useItem(0, 4);
        until(() -> !log.isEmpty(), "낚시 이벤트");
        tick(3);
        assertEquals(List.of("FISHING"), log);
        assertNull(p.fishing, "취소하면 낚시찌가 연결되지 않는다");
        assertEquals(0, (int) at(r -> r.level().getEntities(null, new org.domi.furnace.server.entity.AABB(p.x() - 8, p.y() - 8, p.z() - 8, p.x() + 8, p.y() + 8, p.z() + 8),
                e -> e instanceof org.domi.furnace.server.entity.FishingHook).size()), "낚시찌 엔티티가 생기지 않는다");
        assertTrue(before.sameItemSameComponents(p.inventory().get(HOT0)), "낚싯대는 그대로");
        cancel[0] = false;   // 대조: 허용하면 던져진다
        useItem(0, 5);
        until(() -> p.fishing != null, "허용하면 낚시찌가 연결된다");
    }

    // ------------------------------------------------------------------ 책 편집

    @Test
    void editBookCancelLeavesBookAlone() throws Exception {
        give(HOT0, "minecraft:writable_book", 1);
        ItemStack before = p.inventory().get(HOT0);
        boolean[] cancel = {true};
        List<String> log = new java.util.concurrent.CopyOnWriteArrayList<>();
        on(PlayerEditBookEvent.class, e -> { log.add(e.pages().toString()); e.setCancelled(cancel[0]); });
        Runnable edit = () -> send(frame(0x14, b -> { Types.writeVarInt(b, 0); Types.writeVarInt(b, 1); Types.writeString(b, "hello"); b.writeBoolean(false); }));
        edit.run();
        until(() -> !log.isEmpty(), "책 편집 이벤트");
        tick(3);
        assertTrue(before.sameItemSameComponents(p.inventory().get(HOT0)), "취소하면 책 내용이 그대로");
        cancel[0] = false;   // 대조: 허용하면 저장된다
        edit.run();
        until(() -> !before.sameItemSameComponents(p.inventory().get(HOT0)), "허용하면 책이 바뀐다");
    }

    // ------------------------------------------------------------------ 버리기

    @Test
    void dropOneCancelReturnsToHeldSlotAndResendsInventory() throws Exception {
        give(HOT0, "minecraft:stick", 3);
        List<Integer> log = new java.util.concurrent.CopyOnWriteArrayList<>();
        on(PlayerDropItemEvent.class, e -> { log.add(1); e.setCancelled(true); });
        playerAction(4, 0, 0, 0, 0, 0);   // DROP_ITEM(Q)
        until(() -> !log.isEmpty(), "드롭 이벤트");
        await(ContainerSetContent.ID, k -> true, "인벤토리 전체 재전송");
        assertEquals(3, count(HOT0), "취소하면 든 칸으로 돌아온다");
        assertEquals(0, (int) at(r -> r.level().getEntities(null, new org.domi.furnace.server.entity.AABB(p.x() - 5, p.y() - 5, p.z() - 5, p.x() + 5, p.y() + 5, p.z() + 5),
                e -> e instanceof ItemEntity).size()), "아이템 엔티티가 생기지 않는다");
    }

    @Test
    void dropAllCancelReturnsWholeStackToHeldSlot() throws Exception {
        give(HOT0, "minecraft:stick", 7);
        give(HOT0 + 1, "minecraft:stick", 7);
        List<Integer> log = new java.util.concurrent.CopyOnWriteArrayList<>();
        on(PlayerDropItemEvent.class, e -> { log.add(1); e.setCancelled(true); });
        playerAction(3, 0, 0, 0, 0, 0);   // DROP_ALL_ITEMS(Ctrl+Q)
        until(() -> !log.isEmpty(), "드롭 이벤트");
        await(ContainerSetContent.ID, k -> true, "인벤토리 전체 재전송");
        assertEquals(7, count(HOT0), "한 덩이가 통째로 든 칸에 돌아온다(옆 칸에 합쳐지지 않는다)");
        assertEquals(7, count(HOT0 + 1));
    }

    // ------------------------------------------------------------------ 창 클릭·단축 칸·손 바꾸기

    @Test
    void inventoryClickCancelResendsWholeMenu() throws Exception {
        give(HOT0, "minecraft:stone", 5);
        List<Integer> log = new java.util.concurrent.CopyOnWriteArrayList<>();
        on(InventoryClickEvent.class, e -> { log.add(e.slot()); e.setCancelled(true); });
        int state = p.inventoryMenu().getStateId();
        ItemStack stack = inv(HOT0);
        send(frame(0x0E, b -> {
            Types.writeVarInt(b, 0);
            Types.writeVarInt(b, state);
            b.writeShort(HOT0);
            b.writeByte(0);
            Types.writeVarInt(b, 0);   // PICKUP
            Types.writeVarInt(b, 1);
            b.writeShort(HOT0);
            org.domi.furnace.server.item.ItemStackCodecs.writeStack(b, ItemStack.EMPTY);   // 클라이언트 예측: 칸이 빔
            org.domi.furnace.server.item.ItemStackCodecs.writeStack(b, stack);             // 클라이언트 예측: 커서에 듦
        }));
        await(ContainerSetContent.ID, k -> true, "전체 내용(커서 포함) 재전송");
        assertEquals(List.of(HOT0), log);
        assertSame(stack, inv(HOT0), "취소하면 칸은 그대로");
    }

    @Test
    void itemHeldCancelResendsSelectedSlot() throws Exception {
        List<String> log = new java.util.concurrent.CopyOnWriteArrayList<>();
        on(PlayerItemHeldEvent.class, e -> { log.add(e.previousSlot() + ">" + e.newSlot()); e.setCancelled(true); });
        send(frame(0x2F, b -> b.writeShort(3)));
        await(SetHeldSlot.ID, k -> k.buf().readByte() == 0, "선택 칸 0 으로 되돌림");
        assertEquals(List.of("0>3"), log);
        assertEquals(0, p.inventory().selected());
    }

    @Test
    void swapHandsCancelLeavesBothHandsAlone() throws Exception {
        give(HOT0, "minecraft:stone", 2);
        give(45, "minecraft:stick", 1);
        ItemStack main = inv(HOT0), off = inv(45);
        List<Integer> log = new java.util.concurrent.CopyOnWriteArrayList<>();
        on(PlayerSwapHandItemsEvent.class, e -> { log.add(1); e.setCancelled(true); });
        playerAction(6, 0, 0, 0, 0, 0);
        until(() -> !log.isEmpty(), "손 바꾸기 이벤트");
        assertSame(main, inv(HOT0));
        assertSame(off, inv(45));
        // 클라이언트는 손 바꾸기를 먼저 해 보지 않는다(서버 응답을 기다림) — 되돌릴 것이 없다
    }

    // ------------------------------------------------------------------ 먹기

    @Test
    void consumeCancelKeepsFoodAndResendsHand() throws Exception {
        give(HOT0, "minecraft:golden_apple", 3);
        p.vitals().setFoodLevel(10);
        List<Integer> log = new java.util.concurrent.CopyOnWriteArrayList<>();
        on(PlayerItemConsumeEvent.class, e -> { log.add(1); e.setCancelled(true); });
        useItem(0, 4);
        until(() -> !log.isEmpty(), "먹기 이벤트(약 32틱)");
        await(ContainerSetSlot.ID, k -> setSlot(k, -2, 0, 3), "먹던 칸 재전송");
        assertEquals(3, count(HOT0));
        assertEquals(10, p.vitals().foodLevel(), "허기 상태가 바뀌지 않았다");
    }

    // ------------------------------------------------------------------ 웅크림·달리기·비행·이동

    @Test
    void toggleFlightCancelResendsAbilities() throws Exception {
        p.vitals().setGameMode(GameMode.CREATIVE);
        tick(2);
        drain();
        out.clear();
        List<Boolean> log = new java.util.concurrent.CopyOnWriteArrayList<>();
        on(PlayerToggleFlightEvent.class, e -> { log.add(e.isFlying()); e.setCancelled(true); });
        send(frame(0x23, b -> b.writeByte(0x02)));   // 비행 시작
        await(PlayerAbilities.ID, k -> true, "능력 재전송");
        assertEquals(List.of(true), log);
        assertFalse(p.vitals().isFlying(), "취소하면 날지 않는다");
    }

    @Test
    void toggleSneakAndSprintCancelKeepServerState() throws Exception {
        List<String> log = new java.util.concurrent.CopyOnWriteArrayList<>();
        on(PlayerToggleSneakEvent.class, e -> { log.add("sneak"); e.setCancelled(true); });
        on(PlayerToggleSprintEvent.class, e -> { log.add("sprint"); e.setCancelled(true); });
        send(frame(0x25, b -> { Types.writeVarInt(b, p.entityId()); Types.writeVarInt(b, 0); Types.writeVarInt(b, 0); }));   // PRESS_SHIFT_KEY
        send(frame(0x25, b -> { Types.writeVarInt(b, p.entityId()); Types.writeVarInt(b, 3); Types.writeVarInt(b, 0); }));   // START_SPRINTING
        until(() -> log.size() == 2, "두 이벤트");
        assertFalse(p.sneaking(), "취소하면 서버는 웅크리지 않은 것으로 둔다");
        assertFalse(p.sprinting());
        // 웅크림·달리기는 클라이언트가 직접 정하는 입력이라 되돌려 보낼 방법이 없다 — 계약서 "클라이언트 주도"
    }

    @Test
    void moveCancelTeleportsBackAndKeepsPosition() throws Exception {
        double x0 = p.x(), y0 = p.y(), z0 = p.z();
        List<Integer> log = new java.util.concurrent.CopyOnWriteArrayList<>();
        on(PlayerMoveEvent.class, e -> { log.add(1); e.setCancelled(true); });
        send(frame(0x1A, b -> { b.writeDouble(x0 + 0.4); b.writeDouble(y0); b.writeDouble(z0); b.writeBoolean(true); }));
        await(SyncPlayerPosition.ID, k -> true, "되돌림 텔레포트");
        tick(3);   // 되돌린 위치는 다음 틱에 반영된다
        assertEquals(List.of(1), log);
        assertEquals(x0, p.x(), 1e-9, "취소하면 서버 위치가 그대로");
    }

    // ------------------------------------------------------------------ 텔레포트·게임 모드(서버가 시작 — 클라이언트 예측 없음)

    @Test
    void teleportCancelKeepsPositionAndSendsNothing() throws Exception {
        double x0 = p.x();
        List<Integer> log = new java.util.concurrent.CopyOnWriteArrayList<>();
        on(org.domi.furnace.api.event.player.PlayerTeleportEvent.class, e -> { log.add(1); e.setCancelled(true); });
        at(r -> { p.teleportTo(x0 + 50, p.y(), p.z()); return null; });
        assertEquals(List.of(1), log);
        assertTrue(none(SyncPlayerPosition.ID, k -> true), "취소하면 위치 패킷이 나가지 않는다");
        assertEquals(x0, p.x(), 1e-9);
    }

    @Test
    void gameModeChangeCancelKeepsMode() throws Exception {
        List<Integer> log = new java.util.concurrent.CopyOnWriteArrayList<>();
        on(org.domi.furnace.api.event.player.PlayerGameModeChangeEvent.class, e -> { log.add(1); e.setCancelled(true); });
        assertFalse(p.vitals().setGameMode(GameMode.CREATIVE));
        assertEquals(List.of(1), log);
        assertEquals(GameMode.SURVIVAL, p.gameMode());
    }

    // ------------------------------------------------------------------ 불 붙이기·창 열기·채팅·명령

    @Test
    void blockIgniteCancelKeepsFireOutAndResendsBlock() throws Exception {
        give(HOT0, "minecraft:flint_and_steel", 1);
        List<String> log = new java.util.concurrent.CopyOnWriteArrayList<>();
        on(org.domi.furnace.api.event.block.BlockIgniteEvent.class, e -> { log.add("ignite"); e.setCancelled(true); });
        useItemOn(0, gx, GY, gz, 1, 6);
        await(BlockChangedAck.ID, k -> true, "ack");
        await(BlockUpdate.ID, k -> blockUpdateIs(k, gx, GY + 1, gz, AIR), "불 자리를 공기로 되돌림");
        assertEquals(List.of("ignite"), log);
        assertEquals(AIR, blockAt(gx, GY + 1, gz), "취소하면 불이 붙지 않는다");
    }

    @Test
    void inventoryOpenCancelOpensNothing() throws Exception {
        int table = B.defaultState("minecraft:crafting_table");
        at(r -> { r.level().setBlock(new Pos(gx, GY + 1, gz), table, 3); return null; });
        List<String> log = new java.util.concurrent.CopyOnWriteArrayList<>();
        on(org.domi.furnace.api.event.inventory.InventoryOpenEvent.class, e -> { log.add("open"); e.setCancelled(true); });
        tick(2);
        drain();
        out.clear();
        useItemOn(0, gx, GY + 1, gz, 1, 8);
        until(() -> !log.isEmpty(), "창 열기 이벤트");
        assertTrue(none(org.domi.furnace.server.net.protocol.play.OpenScreen.ID, k -> true), "취소하면 창을 열라는 패킷이 나가지 않는다");
        assertSame(p.inventoryMenu(), p.containerMenu(), "취소하면 열린 창이 없다");
    }

    @Test
    void commandPreprocessCancelRunsNothing() throws Exception {
        List<String> log = new java.util.concurrent.CopyOnWriteArrayList<>();
        on(org.domi.furnace.api.event.player.PlayerCommandPreprocessEvent.class, e -> { log.add(e.message()); e.setCancelled(true); });
        send(frame(0x04, b -> Types.writeString(b, "me waves")));
        until(() -> !log.isEmpty(), "명령 사전 처리 이벤트");
        assertEquals(List.of("/me waves"), log);
        assertTrue(none(org.domi.furnace.server.net.protocol.play.SystemChat.ID, k -> true), "취소하면 명령이 실행되지 않는다(/me 의 알림이 없다)");
    }

    @Test
    void chatCancelBroadcastsNothing() throws Exception {
        List<String> log = new java.util.concurrent.CopyOnWriteArrayList<>();
        on(org.domi.furnace.api.event.player.AsyncChatEvent.class, e -> { log.add(e.message().toString()); e.setCancelled(true); });
        send(frame(0x06, b -> {
            Types.writeString(b, "hello");
            b.writeLong(System.currentTimeMillis());
            b.writeLong(0L);
            b.writeBoolean(false);
            Types.writeVarInt(b, 0);
            b.writeBytes(new byte[3]);
        }));
        until(() -> !log.isEmpty(), "채팅 이벤트");
        assertTrue(none(org.domi.furnace.server.net.protocol.play.PlayerChatPacket.ID, k -> true), "취소하면 아무에게도 가지 않는다");
    }
}
