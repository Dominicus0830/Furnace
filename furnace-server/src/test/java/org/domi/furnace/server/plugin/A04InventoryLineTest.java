// A04 회귀 시험(2차 점검 A04 재현 시험에서 옮김, docs/audit/findings/A04-dupe-loss.md).
// 플레이어 인벤토리 줄: 인벤토리를 바꾸는 패킷(버리기·창 클릭·고른 칸 …)과 월드 결과(설치·먹이)가 같은 region 큐에서 받은 순서대로 돌고,
// 퇴장 저장은 그 줄의 끝에서 뜬다. region 스레드를 잡아 둔 채 패킷을 넣어 틈을 결정적으로 만든다.
// 이 시험의 연결 이벤트 루프는 시험 스레드다(ConcurrentEmbeddedChannel). region 은 스스로 틱하지 않고 작업만 돈다.
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

class A04InventoryLineTest {

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

    // ------------------------------------------------------------------ A04-01 설치 + 버리기

    /**
     * 바닐라: use_item_on 다음 Ctrl+Q 는 같은 스레드에서 차례로 — 돌 1 개 설치, 63 개 던짐(합 64).
     * Furnace: handleUseItemOn 이 이벤트 루프에서 손 스택(main)을 떠 두고 region 에서 설치한 뒤 inv.replace(main→63) 로 줄인다.
     * 그 사이 이벤트 루프의 Ctrl+Q 가 칸을 비우면 replace 가 조용히 실패 — 블록은 놓이고 64 개가 다 던져진다(합 65).
     */
    @Test
    void placeThenDropAllBeforeRegionRuns() throws Exception {
        p.inventory().set(HOT0, B.stack("minecraft:stone", 64));
        CountDownLatch release = holdRegion();
        useItemOn(0, gx, GY, gz, 1, 7);   // region 큐에서 기다린다(손 스택 64 를 떠 둔 채)
        playerAction(3);                  // 이벤트 루프에서 바로: 칸 비우기 + 64 개 던지기 예약
        release.countDown();
        tick(3);
        drain();
        boolean placed = blockAt(gx, GY + 1, gz) == B.defaultState("minecraft:stone");
        int total = (placed ? 1 : 0) + invCount("minecraft:stone") + floorCount("minecraft:stone");
        System.out.println("[A04-01] placed=" + placed + " inv=" + invCount("minecraft:stone") + " floor=" + floorCount("minecraft:stone"));
        assertEquals(64, total, "돌이 늘었다(설치 1 + 인벤토리 + 바닥)");
    }

    /** 같은 원인, Q 하나: 바닐라 1 설치 + 1 던짐 + 62 남음. Furnace 는 replace 실패로 63 남음(합 65). */
    @Test
    void placeThenDropOneBeforeRegionRuns() throws Exception {
        p.inventory().set(HOT0, B.stack("minecraft:stone", 64));
        CountDownLatch release = holdRegion();
        useItemOn(0, gx, GY, gz, 1, 7);
        playerAction(4);
        release.countDown();
        tick(3);
        drain();
        boolean placed = blockAt(gx, GY + 1, gz) == B.defaultState("minecraft:stone");
        int total = (placed ? 1 : 0) + invCount("minecraft:stone") + floorCount("minecraft:stone");
        System.out.println("[A04-01b] placed=" + placed + " inv=" + invCount("minecraft:stone") + " floor=" + floorCount("minecraft:stone"));
        assertEquals(64, total);
    }

    /**
     * 같은 원인, 내용물 든 셜커 상자: 바닐라는 놓인 상자 하나(다이아 64)뿐. Furnace 는 놓인 상자(다이아 64) + 던진 상자 아이템(다이아 64).
     */
    @Test
    void placeFullShulkerThenDropAllDuplicatesContents() throws Exception {
        ItemStack[] items = new ItemStack[27];
        java.util.Arrays.fill(items, ItemStack.EMPTY);
        items[0] = B.stack("minecraft:diamond", 64);
        var patch = org.domi.furnace.server.item.ComponentPatch.of(java.util.Map.of("minecraft:container",
                org.domi.furnace.server.block.entity.ContainerBlockEntity.containerOf(items)));
        p.inventory().set(HOT0, new ItemStack(B.stack("minecraft:shulker_box", 1).itemId(), 1, patch));
        CountDownLatch release = holdRegion();
        useItemOn(0, gx, GY, gz, 1, 7);
        playerAction(3);
        release.countDown();
        tick(3);
        drain();
        int placedDiamonds = at(r -> {
            Object be = r.level().getBlockEntity(new Pos(gx, GY + 1, gz));
            if (!(be instanceof org.domi.furnace.server.block.entity.ShulkerBoxBlockEntity box)) return 0;
            int n = 0;
            for (ItemStack s : box.items) if (s != null && !s.isEmpty()) n += s.count();
            return n;
        });
        int thrownBoxes = floorCount("minecraft:shulker_box") + invCount("minecraft:shulker_box");
        System.out.println("[A04-01d] placedBoxDiamonds=" + placedDiamonds + " thrownOrHeldBoxes=" + thrownBoxes);
        assertFalse(placedDiamonds > 0 && thrownBoxes > 0, "놓인 셜커 상자와 던진 셜커 상자가 둘 다 있다(내용물 복제)");
    }

    /** 같은 원인, 손 바꾸기(F): 바닐라는 설치 뒤 왼손으로 63. Furnace 는 왼손 64 + 블록 1. */
    @Test
    void placeThenSwapHandsBeforeRegionRuns() throws Exception {
        p.inventory().set(HOT0, B.stack("minecraft:stone", 64));
        CountDownLatch release = holdRegion();
        useItemOn(0, gx, GY, gz, 1, 7);
        playerAction(6);
        release.countDown();
        tick(3);
        drain();
        boolean placed = blockAt(gx, GY + 1, gz) == B.defaultState("minecraft:stone");
        int total = (placed ? 1 : 0) + invCount("minecraft:stone") + floorCount("minecraft:stone");
        System.out.println("[A04-01c] placed=" + placed + " main=" + p.inventory().get(HOT0) + " off=" + p.inventory().get(45));
        assertEquals(64, total);
    }

    // ------------------------------------------------------------------ A04-02 퇴장 저장이 큐의 region 작업보다 먼저

    /**
     * 바닐라: 끊김 처리 전에 앞서 받은 패킷(use_item_on)을 다 처리 — 블록 1 + 저장 63.
     * Furnace: channelInactive 가 이벤트 루프에서 인벤토리를 바로 떠 저장(64)하고, 큐에 있던 설치는 그 뒤 region 에서 돈다(블록 1).
     * 다시 접속하면 64 + 블록 1.
     */
    @Test
    void queuedPlaceAfterDisconnectSnapshotDuplicates() throws Exception {
        p.inventory().set(HOT0, B.stack("minecraft:stone", 64));
        CountDownLatch release = holdRegion();
        useItemOn(0, gx, GY, gz, 1, 7);
        ch.close();   // channelInactive → savePlayerSnapshot(64)
        release.countDown();
        tick(3);
        boolean placed = blockAt(gx, GY + 1, gz) == B.defaultState("minecraft:stone");
        var snap = savedSnapshot();
        assertNotNull(snap);
        int saved = 0;
        for (ItemStack s : snap.slots()) if (s != null && !s.isEmpty()) saved += s.count();
        System.out.println("[A04-02] placed=" + placed + " saved=" + saved);
        assertEquals(64, (placed ? 1 : 0) + saved, "퇴장 저장 + 놓인 블록이 64 를 넘었다");
    }

    // ------------------------------------------------------------------ A04-03 플러그인이 취소한 버리기 + 퇴장

    /**
     * Paper: 버리기 취소면 같은 틱에 칸으로 돌려놓고, 끊김은 그 뒤 처리되어 저장에 남는다(4 개).
     * Furnace: Q 가 이벤트 루프에서 칸을 줄이고(3) 던지기는 region 큐로. 그 사이 끊기면 저장은 3, 그 뒤 region 이 취소를 보고
     * 끊긴 플레이어 인벤토리에 1 개를 돌려놓는다 — 저장되지 않아 사라진다.
     */
    @Test
    void cancelledDropReturnedAfterDisconnectSnapshotIsLost() throws Exception {
        host.eventsImpl().subscribe(plugin, PlayerDropItemEvent.class, e -> e.setCancelled(true));
        p.inventory().set(HOT0, B.stack("minecraft:diamond", 4));
        CountDownLatch release = holdRegion();
        playerAction(4);   // 이벤트 루프: 칸 4→3, region 큐에 던지기(이벤트)
        ch.close();
        release.countDown();
        tick(3);
        var snap = savedSnapshot();
        int saved = 0;
        for (ItemStack s : snap.slots()) if (s != null && !s.isEmpty()) saved += s.count();
        int floor = floorCount("minecraft:diamond");
        System.out.println("[A04-03] saved=" + saved + " floor=" + floor + " liveInv=" + invCount("minecraft:diamond"));
        assertEquals(4, saved + floor, "다이아몬드가 사라졌다");
    }

    // ------------------------------------------------------------------ A04-04 몹 먹이(칸 덮어쓰기) + Ctrl+Q 경합

    /**
     * Mob.usePlayerItem 은 region 에서 읽어 둔 held 로 inv.set(held-1) 을 한다(비교 없이 덮어쓰기). 같은 때 이벤트 루프의 Ctrl+Q 가
     * 칸을 비우고 64 개를 던지면, 덮어쓰기가 칸을 63 으로 되살린다. 바닐라에선 둘이 차례라 끝에 칸은 늘 빈다.
     * 시도마다: 아기 소 + 밀 64, 상호작용과 Ctrl+Q 를 거의 동시에. 끝난 뒤 칸이 비지 않았으면 복제.
     */
    @Test
    void feedingRacesDropAll() throws Exception {
        Cow[] cow = new Cow[1];
        int eid = at(r -> {
            var l = r.level();
            cow[0] = new Cow(l, l.nextEntityId());
            cow[0].moveTo(p.x() + 1.5, p.y(), p.z(), 0, 0);
            cow[0].setAge(-2_000_000_000);
            l.addFreshEntity(cow[0]);
            return cow[0].id();
        });
        int dupes = 0, tries = 200;
        for (int i = 0; i < tries; i++) {
            p.inventory().set(HOT0, B.stack("minecraft:wheat", 64));
            CountDownLatch go = new CountDownLatch(1);
            core.world().submitAtChunk(p.chunkPos(), r -> {
                try { go.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return null;
            });
            send(frame(0x16, b -> { Types.writeVarInt(b, eid); Types.writeVarInt(b, 0); Types.writeVarInt(b, 0); b.writeBoolean(false); }));
            go.countDown();
            for (int s = 0; s < (i % 50) * 20; s++) Thread.onSpinWait();   // 겹치는 순간을 조금씩 옮긴다
            playerAction(3);
            at(r -> null);   // region 큐 비우기
            drain();
            if (!p.inventory().get(HOT0).isEmpty()) dupes++;
        }
        System.out.println("[A04-04] dupes=" + dupes + "/" + tries);
        assertEquals(0, dupes, "Ctrl+Q 뒤에도 칸에 밀이 남았다(복제)");
    }

    // ------------------------------------------------------------------ A04-05 인벤토리 창 클릭 + 줍기(region) 경합

    /**
     * 인벤토리 창(0) PICKUP 으로 커서의 흙을 칸에 놓는 클릭은 get → set(합친 값) 두 번 잠근다. 같은 때 region 의 줍기
     * (ItemEntity.playerTouch → inventory.add) 가 그 칸에 더하면 set 이 덮어써 주운 개수가 사라질 수 있었다. 이제 창 클릭도 인벤토리 줄
     * (플레이어 region 큐)에서 돌아 줍기와 차례로 돈다. 줍기는 region 스레드의 inventory().add 로 대신한다(playerTouch 가 부르는 바로 그 호출).
     */
    @Test
    void inventoryClickPlaceRacesPickupAdd() throws Exception {
        String dirt = "minecraft:dirt";
        int lost = 0, tries = 150;
        for (int i = 0; i < tries; i++) {
            p.inventory().set(HOT0, B.stack(dirt, 10));
            for (int ws = 9; ws < 45; ws++) if (ws != HOT0) p.inventory().set(ws, B.stack("minecraft:stone", 64));   // 줍기가 HOT0 에만 가게
            p.inventory().set(45, B.stack("minecraft:stone", 64));
            // 커서에 흙 5 를 들게: 창 칸 9 에 흙 5 를 두고 PICKUP
            p.inventory().set(9, B.stack(dirt, 5));
            click(9, 0, 0);
            at(r -> null);
            CountDownLatch go = new CountDownLatch(1);
            int spin = (i % 30) * 10;
            core.world().submitAtChunk(p.chunkPos(), r -> {   // region 스레드의 줍기
                try { go.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                for (int s = 0; s < spin; s++) Thread.onSpinWait();
                p.inventory().add(B.stack(dirt, 3), false, 64, false);
                return null;
            });
            go.countDown();
            click(HOT0, 0, 0);   // 커서 흙 5 를 HOT0(흙 10)에 놓기
            at(r -> null);
            drain();
            Thread.sleep(8);   // 받기 속도 제한(Too many packets) 피하기
            int inv = invCount(dirt);
            ItemStack cursor = p.inventoryMenu().getCarried();
            int all = inv + (cursor.isEmpty() ? 0 : cursor.count());
            if (all != 18) lost++;
        }
        System.out.println("[A04-05] mismatched=" + lost + "/" + tries);
        assertEquals(0, lost, "흙 10 + 5 + 3 = 18 이 아니었다(주운 것이 덮어써짐)");
    }

    // ------------------------------------------------------------------ 줄의 순서: 고른 칸·버리기·설치가 받은 순서대로

    /** 설치 뒤 바로 다른 칸을 골라도 설치는 받을 때 고른 칸(돌)으로 — 바닐라는 받은 순서대로 처리한다. */
    @Test
    void placeThenSelectOtherSlotUsesSlotAtReceipt() throws Exception {
        p.inventory().set(HOT0, B.stack("minecraft:stone", 64));
        p.inventory().set(HOT0 + 1, B.stack("minecraft:dirt", 64));
        CountDownLatch release = holdRegion();
        useItemOn(0, gx, GY, gz, 1, 7);
        send(frame(0x2F, b -> b.writeShort(1)));   // set_held_item: 핫바 1
        release.countDown();
        at(r -> null);
        drain();
        assertEquals(B.defaultState("minecraft:stone"), blockAt(gx, GY + 1, gz), "받을 때 고른 칸의 돌을 놓아야 한다");
        assertEquals(63, p.inventory().get(HOT0).count());
        assertEquals(64, p.inventory().get(HOT0 + 1).count());
        assertEquals(1, p.inventory().selected());
    }

    /** Q 뒤 바로 다른 칸을 골라도 버리기는 받을 때 고른 칸에서. */
    @Test
    void dropThenSelectOtherSlotDropsFromSlotAtReceipt() throws Exception {
        p.inventory().set(HOT0, B.stack("minecraft:stone", 64));
        p.inventory().set(HOT0 + 1, B.stack("minecraft:dirt", 64));
        CountDownLatch release = holdRegion();
        playerAction(4);
        send(frame(0x2F, b -> b.writeShort(1)));
        release.countDown();
        at(r -> null);
        tick(1);
        drain();
        assertEquals(63, p.inventory().get(HOT0).count(), "받을 때 고른 칸(돌)에서 하나 버린다");
        assertEquals(64, p.inventory().get(HOT0 + 1).count());
        assertEquals(1, floorCount("minecraft:stone"));
        assertEquals(0, floorCount("minecraft:dirt"));
    }

    /** 플러그인이 취소한 버리기는 같은 차례에 칸으로 돌아온다(Paper: ServerPlayer.drop 안에서 이벤트 → 되돌리기). */
    @Test
    void cancelledDropReturnsInSameTurn() throws Exception {
        host.eventsImpl().subscribe(plugin, PlayerDropItemEvent.class, e -> e.setCancelled(true));
        p.inventory().set(HOT0, B.stack("minecraft:diamond", 4));
        playerAction(4);
        at(r -> null);
        drain();
        assertEquals(4, invCount("minecraft:diamond"));
        assertEquals(0, floorCount("minecraft:diamond"));
    }

    // ------------------------------------------------------------------ 퇴장 저장 자리: 다시 접속 읽기가 저장을 앞지르지 않는다

    /**
     * 퇴장 저장은 플레이어 region 줄에서 뜬다 — region 이 바쁜 동안 같은 UUID 로 다시 들어오면 읽기가 그 저장을 기다려야 한다
     * (아니면 옛 데이터를 읽어, 그 사이 던진 아이템이 인벤토리에도 되살아난다).
     */
    @Test
    void reconnectReadWaitsForQueuedQuitSave() throws Exception {
        p.inventory().set(HOT0, B.stack("minecraft:stone", 64));
        playerAction(3);   // Ctrl+Q — 64 개 던짐(저장에는 0 이어야 한다)
        at(r -> null);
        CountDownLatch release = holdRegion();
        ch.close();   // 퇴장 저장은 줄에 들어가 region 을 기다린다
        java.util.concurrent.atomic.AtomicReference<PlayerData.Snapshot> read = new java.util.concurrent.atomic.AtomicReference<>();
        CountDownLatch readDone = new CountDownLatch(1);
        var io = core.world().playerIo();
        boolean deferred = PlayerData.afterPendingSaves(core.world().worldDir(), p.uuid(), () -> io.execute(() -> {
            read.set(PlayerData.load(core.world().worldDir(), p.uuid()));
            readDone.countDown();
        }));
        assertTrue(deferred, "줄에서 기다리는 퇴장 저장이 있으면 읽기는 미뤄져야 한다");
        assertFalse(readDone.await(200, TimeUnit.MILLISECONDS), "저장 전에 읽었다");
        release.countDown();
        assertTrue(readDone.await(10, TimeUnit.SECONDS));
        assertNotNull(read.get(), "저장 뒤 읽기 — 파일이 있어야 한다");
        int saved = 0;
        for (ItemStack s : read.get().slots()) if (s != null && !s.isEmpty() && s.itemId() == B.stack("minecraft:stone", 1).itemId()) saved += s.count();
        assertEquals(0, saved, "던진 돌이 저장 인벤토리에 남았다");
        assertFalse(PlayerData.hasPendingSave(core.world().worldDir(), p.uuid()), "저장이 끝나면 자리가 풀려야 한다");
    }

    /** 서버 종료(FLUSH_WORLDS → awaitIo)·시험 정리가 줄에서 기다리는 퇴장 저장까지 기다린다 — 저장이 붙잡힌 region 뒤에 남아 사라지지 않게. */
    @Test
    void awaitIoWaitsForQueuedQuitSave() throws Exception {
        p.inventory().set(HOT0, B.stack("minecraft:stone", 5));
        CountDownLatch release = holdRegion();
        ch.close();
        var waiter = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try { return PlayerData.awaitIo(10, TimeUnit.SECONDS); } catch (InterruptedException e) { return false; }
        });
        Thread.sleep(200);
        assertFalse(waiter.isDone(), "줄에서 기다리는 퇴장 저장보다 먼저 끝났다");
        release.countDown();
        assertTrue(waiter.get(15, TimeUnit.SECONDS));
        var snap = savedSnapshot();
        int saved = 0;
        for (ItemStack s : snap.slots()) if (s != null && !s.isEmpty()) saved += s.count();
        assertEquals(5, saved);
    }

    private void click(int slot, int button, int type) {
        send(frame(0x0E, b -> {
            Types.writeVarInt(b, 0);
            Types.writeVarInt(b, p.inventoryMenu().getStateId());
            b.writeShort(slot);
            b.writeByte(button);
            Types.writeVarInt(b, type);
            Types.writeVarInt(b, 0);
            Types.writeVarInt(b, 0);
        }));
    }

    // ------------------------------------------------------------------ PR #199 독립 검토(R1·R3)

    private void submitRaw(java.lang.reflect.InvocationHandler h) throws Exception {
        Object line = field("inventoryLine");
        Class<?> taskType = Class.forName("org.domi.furnace.server.net.ConnectionHandler$LineTask");
        Object task = java.lang.reflect.Proxy.newProxyInstance(taskType.getClassLoader(), new Class<?>[]{taskType}, h);
        var m = line.getClass().getDeclaredMethod("submit", Player.class, taskType);
        m.setAccessible(true);
        m.invoke(line, p, task);
    }

    /**
     * PR #199 검토 R1: 줄 작업 하나가 Error(AssertionError·NoSuchMethodError 등)를 던져도 줄은 이어진다 —
     * 놓치면 scheduled 가 참으로 남아 뒤 버리기·창 클릭·설치가 모두 무시되고 퇴장 저장은 5초 대체 길로만 뜬다.
     */
    @Test
    void errorInOneLineTaskMustNotKillTheLine() throws Exception {
        submitRaw((proxy, m, a) -> {
            if (m.getName().equals("run")) throw new NoSuchMethodError("검토: 줄 작업 안의 Error");
            return m.invoke(this, a);
        });
        at(r -> null);
        p.inventory().set(HOT0, B.stack("minecraft:stone", 64));
        playerAction(3);   // Ctrl+Q
        at(r -> null);
        tick(1);
        drain();
        assertEquals(64, floorCount("minecraft:stone"), "Error 하나 뒤에도 다음 버리기는 돌아야 한다");
    }

    /** 검토 R1: Error 뒤에도 퇴장 저장은 줄에서 바로 뜬다(5초 대체 저장·같은 UUID 다시 접속 대기 없이). */
    @Test
    void quitSaveAfterLineErrorShouldNotWaitForFallback() throws Exception {
        submitRaw((proxy, m, a) -> {
            if (m.getName().equals("run")) throw new NoSuchMethodError("검토: 줄 작업 안의 Error");
            return m.invoke(this, a);
        });
        at(r -> null);
        ch.close();
        at(r -> null);
        Thread.sleep(500);
        assertFalse(PlayerData.hasPendingSave(core.world().worldDir(), p.uuid()), "퇴장 저장이 줄에서 뜨지 못하고 5초 대체 저장을 기다린다");
    }

    private static final ChunkPos FAR = new ChunkPos(400, 400);

    /** 먼 청크에 다른 region 을 만든다(이 시험 서버는 region 작업 스레드가 하나라 두 region 이 번갈아 돈다). */
    private void makeFarRegion() throws Exception {
        core.world().submitAtChunk(FAR, r -> r.chunkAtBlocking(FAR)).get(30, TimeUnit.SECONDS);
        var rz = core.world().regionizer();
        assertNotNull(rz.ownerOf(FAR.x(), FAR.z()));
        assertNotSame(rz.ownerOf(FAR.x(), FAR.z()), rz.ownerOf(p.chunkPos().x(), p.chunkPos().z()), "먼 청크는 다른 region 이어야 한다");
    }

    /**
     * 검토 R3: 다른 region 칸에 대한 use_item_on 은 줄을 멈추고(작업 스레드가 하나라 넘긴 작업은 이 차례 안에 끝날 수 없다),
     * 그 region 이 끝내면 줄이 다시 예약돼 뒤에 받은 버리기가 돈다.
     */
    @Test
    void crossRegionUseItemOnPausesThenResumesLine() throws Exception {
        makeFarRegion();
        p.inventory().set(HOT0, B.stack("minecraft:stone", 64));
        CountDownLatch release = holdRegion();
        useItemOn(0, FAR.x() * 16 + 8, GY, FAR.z() * 16 + 8, 1, 9);
        playerAction(3);
        release.countDown();
        long dl = System.nanoTime() + 4_000_000_000L;
        while (System.nanoTime() < dl && floorCount("minecraft:stone") != 64) Thread.sleep(20);
        assertEquals(64, floorCount("minecraft:stone"), "먼 region 이 끝낸 뒤 줄이 이어져야 한다");
        assertEquals(0, invCount("minecraft:stone"));
    }

    /**
     * 검토 R3: 다른 region 으로 넘긴 설치가 걸려 있을 때 끊겨도 퇴장 저장은 5초 대체 길 없이 뜨고, 저장과 바닥의 합이 맞는다
     * (버리기가 끊김보다 먼저 돌았으면 바닥 64·저장 0, 아니면 바닥 0·저장 64).
     */
    @Test
    void disconnectAroundCrossRegionUseKeepsSaveConsistent() throws Exception {
        makeFarRegion();
        p.inventory().set(HOT0, B.stack("minecraft:stone", 64));
        useItemOn(0, FAR.x() * 16 + 8, GY, FAR.z() * 16 + 8, 1, 9);
        playerAction(3);
        ch.close();
        long dl = System.nanoTime() + 3_000_000_000L;
        while (System.nanoTime() < dl && PlayerData.hasPendingSave(core.world().worldDir(), p.uuid())) Thread.sleep(20);
        assertFalse(PlayerData.hasPendingSave(core.world().worldDir(), p.uuid()), "5초 대체 저장을 기다리지 않고 줄에서 저장해야 한다");
        assertTrue(PlayerData.awaitIo(10, TimeUnit.SECONDS));
        var snap = savedSnapshot();
        int saved = 0;
        for (ItemStack s : snap.slots()) if (s != null && !s.isEmpty() && s.itemId() == B.stack("minecraft:stone", 1).itemId()) saved += s.count();
        assertEquals(64, saved + floorCount("minecraft:stone"), "저장 " + saved + " + 바닥");
    }
}
