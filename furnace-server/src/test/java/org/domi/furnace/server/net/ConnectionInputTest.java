// ConnectionHandler 입력 검증: 비정상 이동은 거르고 한 번만 보정, 임의 좌표 블록 요청은 청크를 생성하지 않고, 임의 스폰 좌표에서도 정상 이동은 수락
package org.domi.furnace.server.net;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.entity.EntityIdAllocator;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.play.BlockChangedAck;
import org.domi.furnace.server.net.protocol.play.ContainerClose;
import org.domi.furnace.server.net.protocol.play.ContainerSetContent;
import org.domi.furnace.server.net.protocol.play.ContainerSetSlot;
import org.domi.furnace.server.net.protocol.play.SyncPlayerPosition;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.ChunkSection;
import org.domi.furnace.server.world.SpawnPoint;
import org.domi.furnace.server.world.World;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConnectionInputTest {

    @BeforeAll
    static void items() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private World world;
    private EmbeddedChannel ch;
    private ConnectionHandler handler;
    private Player p;
    private List<int[]> joinOut;
    private int autosaveTicksForJoin = Integer.MAX_VALUE;
    private java.util.concurrent.ExecutorService io;

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

    /** spawn 좌표로 접속 → 스폰 청크 전달 완료 → 스폰 텔레포트 confirm → 게이트 해제까지 진행한다. */
    private void join(SpawnPoint spawn) throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        world = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1), sched, 1, 16, 2, 2,
                reg.directBits(), 7, io = Executors.newSingleThreadExecutor());
        world.configurePersistence(autosaveTicksForJoin, 100);
        world.worldDir(tmp);                 // 저장 위치/인벤토리를 실제 world/ 가 아닌 임시 폴더에서 읽는다
        world.defaultSpawn(spawn);
        handler = new ConnectionHandler("t", -1, sched, world, new EntityIdAllocator());
        ch = new EmbeddedChannel(handler);
        ch.writeInbound(frame(0x00, b -> { Types.writeVarInt(b, 767); Types.writeString(b, "localhost");
            b.writeShort(25565); Types.writeVarInt(b, 2); }));
        ch.writeInbound(frame(0x00, b -> { Types.writeString(b, "Tester"); Types.writeUuid(b, new UUID(0, 0)); }));
        ch.writeInbound(frame(0x03, null));
        ch.writeInbound(frame(0x07, b -> Types.writeVarInt(b, 0)));
        ch.writeInbound(frame(0x03, null));  // → PLAY
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (!(boolean) field("spawnChunksDelivered")) {
            ch.runPendingTasks();
            assertTrue(System.nanoTime() < deadline, "스폰 청크 전달이 끝나지 않음");
            Thread.sleep(20);
        }
        ch.writeInbound(frame(0x00, b -> Types.writeVarInt(b, 1)));   // 스폰 텔레포트 confirm → 게이트 해제
        ch.runPendingTasks();
        assertEquals(-1, (int) field("pendingTeleportId"), "스폰 게이트가 풀려야 한다");
        p = (Player) field("player");
        RegionPos sp = new RegionPos(Math.floorDiv((int) Math.floor(spawn.playerX()), 16), Math.floorDiv((int) Math.floor(spawn.playerZ()), 16));
        sched.region(sp).execute(() -> { }).get();
        p.drainPending();
        joinOut = drainOutbound();   // 접속 중 나간 패킷(검증용으로 보관)
    }

    @AfterEach
    void close() throws InterruptedException {
        if (sched != null) sched.close();
        // 연결 종료 시 비동기 playerdata 저장이 @TempDir 정리와 겹치지 않도록 IO 실행기를 비운다.
        if (io != null) {
            io.shutdown();
            io.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    private List<int[]> drainOutbound() {
        ch.runPendingTasks();
        List<int[]> out = new ArrayList<>();   // {packetId, 첫 정수(있으면)}
        Object o;
        while ((o = ch.readOutbound()) != null) {
            ByteBuf bb = (ByteBuf) o;
            int id = Types.readVarInt(bb);
            out.add(new int[]{id});
            bb.release();
        }
        return out;
    }

    private static long count(List<int[]> pkts, int id) {
        return pkts.stream().filter(a -> a[0] == id).count();
    }

    private static void move(EmbeddedChannel ch, double x, double y, double z) {
        ch.writeInbound(frame(0x1A, b -> { b.writeDouble(x); b.writeDouble(y); b.writeDouble(z); b.writeBoolean(true); }));
    }

    /** 위치 패킷과 회전 전용 패킷이 region이 drain하기 전에 연달아 오면 서로의 값을 덮어쓰면 안 된다. */
    @Test
    void positionThenRotationInSameTick_keepsBoth() throws Exception {
        join(new SpawnPoint(0, -60, 0, 0f));
        move(ch, 10, -60, 3);
        ch.writeInbound(frame(0x1C, b -> { b.writeFloat(45f); b.writeFloat(10f); b.writeBoolean(true); }));
        p.drainPending();
        assertEquals(10.0, p.x(), "회전 전용 패킷이 대기 중인 새 위치를 옛 위치로 덮어썼다");
        assertEquals(45f, p.yaw());
    }

    @Test
    void rotationThenPositionInSameTick_keepsBoth() throws Exception {
        join(new SpawnPoint(0, -60, 0, 0f));
        ch.writeInbound(frame(0x1C, b -> { b.writeFloat(90f); b.writeFloat(0f); b.writeBoolean(true); }));
        move(ch, 20, -60, 0);
        p.drainPending();
        assertEquals(20.0, p.x());
        assertEquals(90f, p.yaw(), "위치 전용 패킷이 대기 중인 새 회전을 옛 회전으로 덮어썼다");
    }

    @Test
    void positionRotationPacket_thenRotationOnly_keepsPosition() throws Exception {
        join(new SpawnPoint(0, -60, 0, 0f));
        ch.writeInbound(frame(0x1B, b -> { b.writeDouble(7); b.writeDouble(-60); b.writeDouble(7);
            b.writeFloat(30f); b.writeFloat(5f); b.writeBoolean(true); }));
        ch.writeInbound(frame(0x1C, b -> { b.writeFloat(60f); b.writeFloat(15f); b.writeBoolean(true); }));
        p.drainPending();
        assertEquals(7.0, p.x());
        assertEquals(7.0, p.z());
        assertEquals(60f, p.yaw());
    }

    @Test
    void teleportAndNonFinite_areRejected_withSingleCorrection_thenResumeAfterConfirm() throws Exception {
        join(new SpawnPoint(0, -60, 0, 0f));
        double x0 = p.x();
        move(ch, 5_000_000, -60, 0);
        move(ch, Double.NaN, -60, 0);
        move(ch, 0.5, Double.POSITIVE_INFINITY, 0.5);
        p.drainPending();
        assertEquals(x0, p.x(), "급이동/NaN/무한대는 반영되지 않는다");
        // 보정 텔레포트는 연쇄로 쏟아지지 않고 한 번만 나간다(확인 전의 옛 위치 패킷은 조용히 버림)
        assertEquals(1, count(drainOutbound(), SyncPlayerPosition.ID), "보정은 한 번만 보내야 한다");

        move(ch, x0 + 1, -60, 0);                         // 확인 전엔 정상 이동도 무시
        p.drainPending();
        assertEquals(x0, p.x(), "보정 확인 전엔 위치 패킷을 받지 않는다");

        int tpId = (int) field("correctionTpId");
        ch.writeInbound(frame(0x00, b -> Types.writeVarInt(b, tpId)));   // 클라가 보정 확인
        move(ch, x0 + 1, -60, 0);
        p.drainPending();
        assertEquals(x0 + 1, p.x(), "확인 후엔 정상 이동 수락 재개");
    }

    /** 보정 텔레포트의 회전은 마지막으로 수락한 회전이어야 한다(region이 아직 drain 안 한 옛 값이 아니라). */
    @Test
    void correctionTeleport_carriesLastAcceptedRotation_notStaleRegionValue() throws Exception {
        join(new SpawnPoint(0, -60, 0, 0f));
        ch.writeInbound(frame(0x1C, b -> { b.writeFloat(45f); b.writeFloat(10f); b.writeBoolean(true); }));   // drain 안 함
        move(ch, 5_000_000, -60, 0);                                                                          // 급이동 → 보정
        ch.runPendingTasks();
        float[] rot = null;
        Object o;
        while ((o = ch.readOutbound()) != null) {
            ByteBuf bb = (ByteBuf) o;
            if (Types.readVarInt(bb) == SyncPlayerPosition.ID) {
                bb.skipBytes(24);                       // x,y,z
                rot = new float[]{bb.readFloat(), bb.readFloat()};
            }
            bb.release();
        }
        assertNotNull(rot, "보정 SyncPlayerPosition이 나가야 한다");
        assertEquals(45f, rot[0], "yaw");
        assertEquals(10f, rot[1], "pitch");
    }

    /** 월드 기본 스폰/저장 위치는 임의 좌표다 — 기준 위치를 (0,0)에서 시작하면 스폰이 100블록 밖일 때 첫 이동부터 거부된다. */
    /** 접속 시 바닐라처럼 난이도·능력·보더·시간·체력·경험치와 서버 view distance가 전달돼야 한다. */
    @Test
    void join_sendsVanillaStatePackets() throws Exception {
        join(new SpawnPoint(0, -60, 0, 0f));
        for (int id : new int[]{0x0B, 0x38, 0x25, 0x64, 0x5D, 0x5C}) {
            assertEquals(1, count(joinOut, id), "접속 시 패킷 0x" + Integer.toHexString(id) + " 한 번 전송돼야 한다");
        }
    }

    /** 월드 시간은 주기적으로 갱신되어 모든 플레이어가 같은 시계를 봐야 한다. */
    @Test
    void worldTime_isResentPeriodically() throws Exception {
        join(new SpawnPoint(0, -60, 0, 0f));
        ch.advanceTimeBy(1100, java.util.concurrent.TimeUnit.MILLISECONDS);
        ch.runScheduledPendingTasks();
        assertTrue(count(drainOutbound(), 0x64) >= 1, "1초 뒤 SetTime이 다시 가야 한다");
    }

    /** 저장된 인벤토리는 서버 메모리에만 두지 말고 접속 때 클라이언트에도 보내야 한다(안 보이는 아이템으로 설치되는 불일치 방지). */
    @Test
    void join_sendsSavedInventoryAndHeldSlot() throws Exception {
        ItemStack[] saved = new ItemStack[46];
        java.util.Arrays.fill(saved, ItemStack.EMPTY);
        saved[36] = new ItemStack(1, 5, null);
        org.domi.furnace.server.entity.PlayerData.save(tmp,
                org.domi.furnace.server.net.protocol.login.LoginStart.offlineUuid("Tester"),
                0, -60, 0, 0f, 0f, true, saved);
        join(new SpawnPoint(0, -60, 0, 0f));
        assertEquals(1, count(joinOut, 0x13), "container 0 내용이 접속 때 전송돼야 한다");
        assertEquals(1, count(joinOut, 0x53), "선택 슬롯(Set Held Item)이 전송돼야 한다");
    }

    private static void writeStrictSlot(ByteBuf b, int count, int itemId, int numAdd) {
        Types.writeVarInt(b, count);
        if (count == 0) return;
        Types.writeVarInt(b, itemId);
        Types.writeVarInt(b, numAdd);
        Types.writeVarInt(b, 0);
    }

    private ItemStack[] slotsOf() throws Exception {
        Object inv = field("inv");
        java.lang.reflect.Field f = inv.getClass().getDeclaredField("slots");
        f.setAccessible(true);
        return (ItemStack[]) f.get(inv);
    }

    /** 비정상 아이템(음수 개수·알 수 없는 id·과대 개수)은 인벤토리/상자에 들어가면 안 된다(다른 플레이어 클라이언트까지 깨뜨림). */
    @Test
    void invalidItems_areRejected_inCreativeSlotAndContainerClick() throws Exception {
        join(new SpawnPoint(0, -60, 0, 0f));
        world.setRegistries(BlockRegistry.load("blocks/1.21.1.txt"),
                org.domi.furnace.server.world.ItemRegistry.load("items/1.21.1.txt"));
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
        // 0x32: 돌 65개 — 최대 스택(64)을 넘으면 바닐라처럼 조용히 무시
        ch.writeInbound(frame(0x32, b -> { b.writeShort(36); Types.writeVarInt(b, 65); Types.writeVarInt(b, 1); Types.writeVarInt(b, 0); Types.writeVarInt(b, 0); }));
        assertTrue(slotsOf()[36].isEmpty(), "최대 스택을 넘는 개수는 거부돼야 한다");
        // 정상 아이템은 그대로 들어간다
        ch.writeInbound(frame(0x32, b -> { b.writeShort(38); Types.writeVarInt(b, 3); Types.writeVarInt(b, 1); Types.writeVarInt(b, 0); Types.writeVarInt(b, 0); }));
        assertEquals(3, slotsOf()[38].count());

        // 0x0E: 음수 개수(= 비정상 스택)가 든 클릭 보고는 인벤토리에 아무것도 만들지 않는다
        ch.writeInbound(frame(0x0E, b -> {
            Types.writeVarInt(b, 0); Types.writeVarInt(b, 0); b.writeShort(-1); b.writeByte(0);
            Types.writeVarInt(b, 0); Types.writeVarInt(b, 1);
            b.writeShort(30); writeStrictSlot(b, -1, 1, 0);
            writeStrictSlot(b, 0, 0, 0);
        }));
        assertTrue(slotsOf()[12].isEmpty(), "음수 개수 클릭이 인벤토리에 반영됐다");
        assertTrue(ch.isActive());

        // 알 수 없는 아이템 id 는 바닐라처럼 디코드 실패 → 연결 종료
        ch.writeInbound(frame(0x32, b -> { b.writeShort(37); Types.writeVarInt(b, 1); Types.writeVarInt(b, 99999); Types.writeVarInt(b, 0); Types.writeVarInt(b, 0); }));
        assertTrue(slotsOf()[37].isEmpty(), "알 수 없는 아이템 id는 거부돼야 한다");
        assertFalse(ch.isActive(), "디코드할 수 없는 아이템은 바닐라처럼 연결을 끊는다");
    }

    /** 인벤토리 창(0) 클릭: 서버가 클릭을 재현한다(바닐라) — 클라이언트 보고는 예측 대조일 뿐이라 서버 결과와 다른 보고는 서버 값으로 바로잡는다. */
    @Test
    void inventoryClick_isReplayedByServer_andReportsOnlyCorrect() throws Exception {
        join(new SpawnPoint(0, -60, 0, 0f));
        world.setRegistries(BlockRegistry.load("blocks/1.21.1.txt"),
                org.domi.furnace.server.world.ItemRegistry.load("items/1.21.1.txt"));
        slotsOf()[36] = new ItemStack(1, 10, null);
        var im = p.inventoryMenu();
        int st = im.getStateId();
        // 핫바 0(창 36)의 10개를 집는다: 서버가 재현 → 36 비움, 커서 10. 클라이언트 예측과 같으므로 추가 패킷이 없다.
        ch.writeInbound(frame(0x0E, b -> {
            Types.writeVarInt(b, 0); Types.writeVarInt(b, st); b.writeShort(36); b.writeByte(0);
            Types.writeVarInt(b, 0); Types.writeVarInt(b, 1);
            b.writeShort(36); writeStrictSlot(b, 0, 0, 0);
            writeStrictSlot(b, 10, 1, 0);
        }));
        assertTrue(slotsOf()[36].isEmpty(), "집은 칸은 비어야 한다");
        assertEquals(10, im.getCarried().count());
        drainOutbound();
        // 창 20 에 내려놓기
        ch.writeInbound(frame(0x0E, b -> {
            Types.writeVarInt(b, 0); Types.writeVarInt(b, im.getStateId()); b.writeShort(20); b.writeByte(0);
            Types.writeVarInt(b, 0); Types.writeVarInt(b, 1);
            b.writeShort(20); writeStrictSlot(b, 10, 1, 0);
            writeStrictSlot(b, 0, 0, 0);
        }));
        assertEquals(10, slotsOf()[20].count(), "재배치는 반영");
        assertEquals(0, count(drainOutbound(), ContainerSetSlot.ID), "예측과 같으면 되돌려 보내지 않는다");
        // 거짓 보고: 21 은 빈 칸이고 커서도 비었는데 "21 에 10개가 생겼다"고 보고 → 서버는 아무것도 만들지 않고 그 칸을 바로잡는다
        ch.writeInbound(frame(0x0E, b -> {
            Types.writeVarInt(b, 0); Types.writeVarInt(b, im.getStateId()); b.writeShort(21); b.writeByte(0);
            Types.writeVarInt(b, 0); Types.writeVarInt(b, 1);
            b.writeShort(21); writeStrictSlot(b, 10, 1, 0);
            writeStrictSlot(b, 0, 0, 0);
        }));
        assertTrue(slotsOf()[21].isEmpty(), "서버가 재현한 결과(빈 칸)가 진실이다");
        assertEquals(1, count(drainOutbound(), ContainerSetSlot.ID), "어긋난 칸 하나만 서버 값으로 되돌려 보낸다");
    }

    /** 상태 번호가 다르면(클라이언트가 오래된 화면을 보고 있다) 서버는 클릭을 재현한 뒤 전체 내용을 다시 보낸다. */
    @Test
    void inventoryClick_withStaleStateId_resendsWholeContent() throws Exception {
        join(new SpawnPoint(0, -60, 0, 0f));
        world.setRegistries(BlockRegistry.load("blocks/1.21.1.txt"),
                org.domi.furnace.server.world.ItemRegistry.load("items/1.21.1.txt"));
        slotsOf()[36] = new ItemStack(1, 10, null);
        var im = p.inventoryMenu();
        ch.writeInbound(frame(0x0E, b -> {
            Types.writeVarInt(b, 0); Types.writeVarInt(b, (im.getStateId() + 5) & 32767); b.writeShort(36); b.writeByte(0);
            Types.writeVarInt(b, 0); Types.writeVarInt(b, 0);
            writeStrictSlot(b, 0, 0, 0);
        }));
        assertEquals(1, count(drainOutbound(), ContainerSetContent.ID), "상태 번호 불일치 → 전체 재전송");
    }

    /** 사거리 밖(미로드) 상자 창에 대한 클릭은 청크를 만들지 않고, 인벤토리도 안 바꾸며, 창을 닫는다. */
    @Test
    void chestClickOutOfReach_isDropped_closesContainer_noChunkLoad() throws Exception {
        join(new SpawnPoint(0, -60, 0, 0f));
        var chest = org.domi.furnace.server.world.ChestBlockEntity.empty(1000, -60, 1000);   // 월드에 없는(미로드 청크) 상자
        var menu = org.domi.furnace.server.menu.ChestMenu.threeRows(7, p.inventoryMenu().inventoryContainer(),
                new org.domi.furnace.server.world.BlockEntityContainer(chest), new int[]{1000, -60, 1000}, null);
        p.setContainerMenu(menu);
        ChunkPos far = ChunkPos.ofBlock(1000, 1000);
        ch.writeInbound(frame(0x0E, b -> {
            Types.writeVarInt(b, 7); Types.writeVarInt(b, 0); b.writeShort(5); b.writeByte(0);
            Types.writeVarInt(b, 0);          // PICKUP
            Types.writeVarInt(b, 1);
            b.writeShort(30);                 // 플레이어 인벤토리 슬롯으로 아이템을 넣는 변경
            Types.writeVarInt(b, 1); Types.writeVarInt(b, 1); Types.writeVarInt(b, 0); Types.writeVarInt(b, 0);
            Types.writeVarInt(b, 0);          // carried 비어있음
        }));
        // 클릭은 그 상자의 region 스레드에서 처리된다 — 닫힘이 나갈 때까지 기다린다
        long deadline = System.nanoTime() + 5_000_000_000L;
        long closes = 0;
        while (closes == 0 && System.nanoTime() < deadline) {
            closes = count(drainOutbound(), 0x12);
            if (closes == 0) Thread.sleep(20);
        }
        RegionPos frp = new RegionPos(1000 >> 4, 1000 >> 4);
        boolean[] loaded = new boolean[1];
        sched.region(frp).execute(() -> loaded[0] = world.regionAt(frp).chunkLoaded(far)).get();
        assertFalse(loaded[0], "미로드 상자 청크를 만들면 안 된다");
        assertEquals(1, closes, "청크가 없으면 ContainerClose가 나가야 한다");
        assertFalse(p.hasOpenContainer());
        assertTrue(slotsOf()[12].isEmpty());
    }

    /** 조건이 참이 될 때까지 채널 작업을 돌리며 기다린다. */
    private interface Cond { boolean test() throws Exception; }

    private void await(String what, Cond cond) throws Exception {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (!cond.test()) {
            ch.runPendingTasks();
            assertTrue(System.nanoTime() < deadline, "기다리다 시간 초과: " + what);
            Thread.sleep(10);
        }
    }

    /** 월드에 상자를 놓고(안에 돌 5개) 서버가 플레이어에게 그 상자를 연다 — 실제 openChest 경로. */
    private org.domi.furnace.server.world.ChestBlockEntity openRealChest(int x, int y, int z) throws Exception {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        world.setRegistries(reg, org.domi.furnace.server.world.ItemRegistry.load("items/1.21.1.txt"));
        int chestState = reg.defaultStateId("minecraft:chest");
        RegionPos rp = new RegionPos(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
        org.domi.furnace.server.world.ChestBlockEntity[] chest = new org.domi.furnace.server.world.ChestBlockEntity[1];
        sched.region(rp).execute(() -> {
            var r = world.regionAt(rp);
            r.placeBlock(x, y, z, chestState);
            r.setChestItem(x, y, z, 0, new ItemStack(1, 5, null));
            chest[0] = r.chestAt(x, y, z);
        }).get();
        drainOutbound();
        java.lang.reflect.Method m = ConnectionHandler.class.getDeclaredMethod("openContainer", int.class, int.class, int.class);
        m.setAccessible(true);
        m.invoke(handler, x, y, z);
        await("상자 창 열림", () -> p.hasOpenContainer());
        return chest[0];
    }

    /** 상자를 열고 → 돌을 꺼내 → 인벤토리에 놓고 → shift-클릭으로 되돌리고 → 닫는다. 서버가 모든 클릭을 재현하고 예측과 같으면 추가 패킷이 없다. */
    @Test
    void chestMenu_fullFlow_openTakeMoveClose() throws Exception {
        join(new SpawnPoint(0, -60, 0, 0f));
        var chest = openRealChest(2, -60, 2);
        List<int[]> opened = drainOutbound();
        assertEquals(1, count(opened, 0x33), "open_screen 한 번");
        assertEquals(1, count(opened, ContainerSetContent.ID), "첫 container_set_content 한 번");
        var menu = p.containerMenu();
        assertEquals(1, menu.containerId, "첫 창 번호는 1");
        assertEquals(63, menu.slots.size());
        assertEquals(5, menu.getSlot(0).getItem().count());

        // 상자 칸 0 의 돌 5개를 집는다 (클라이언트 예측: 칸 비움 + 커서 5)
        ch.writeInbound(frame(0x0E, b -> {
            Types.writeVarInt(b, 1); Types.writeVarInt(b, menu.getStateId()); b.writeShort(0); b.writeByte(0);
            Types.writeVarInt(b, 0); Types.writeVarInt(b, 1);
            b.writeShort(0); writeStrictSlot(b, 0, 0, 0);
            writeStrictSlot(b, 5, 1, 0);
        }));
        await("커서에 돌 5개", () -> menu.getCarried().count() == 5);
        assertTrue(chest.items[0].isEmpty(), "상자 칸이 비어야 한다");
        assertEquals(0, count(drainOutbound(), ContainerSetSlot.ID), "예측과 같으면 추가 패킷이 없다");

        // 플레이어 인벤토리 첫 칸(메뉴 27 = Inventory 9)에 놓는다
        ch.writeInbound(frame(0x0E, b -> {
            Types.writeVarInt(b, 1); Types.writeVarInt(b, menu.getStateId()); b.writeShort(27); b.writeByte(0);
            Types.writeVarInt(b, 0); Types.writeVarInt(b, 1);
            b.writeShort(27); writeStrictSlot(b, 5, 1, 0);
            writeStrictSlot(b, 0, 0, 0);
        }));
        await("인벤토리에 돌 5개", () -> slotsOf()[9].count() == 5);
        assertTrue(menu.getCarried().isEmpty());

        // shift-클릭으로 상자로 되돌린다
        ch.writeInbound(frame(0x0E, b -> {
            Types.writeVarInt(b, 1); Types.writeVarInt(b, menu.getStateId()); b.writeShort(27); b.writeByte(0);
            Types.writeVarInt(b, 1); Types.writeVarInt(b, 2);
            b.writeShort(27); writeStrictSlot(b, 0, 0, 0);
            b.writeShort(0); writeStrictSlot(b, 5, 1, 0);
            writeStrictSlot(b, 0, 0, 0);
        }));
        await("상자로 되돌림", () -> chest.items[0].count() == 5);
        assertTrue(slotsOf()[9].isEmpty());

        // 닫기: 클라이언트가 보낸 container_close — 서버는 인벤토리 창으로 돌아가고 닫힘 패킷은 보내지 않는다
        ch.writeInbound(frame(0x0F, b -> Types.writeVarInt(b, 1)));
        await("창 닫힘", () -> !p.hasOpenContainer());
        assertEquals(0, count(drainOutbound(), ContainerClose.ID));
    }

    /** 상자가 부서지면 열어 둔 창이 닫히고, 그 뒤의 클릭은 아무것도 만들지 못한다(복제 방지). */
    @Test
    void chestMenu_breakWhileOpen_closesAndRejectsLateClicks() throws Exception {
        join(new SpawnPoint(0, -60, 0, 0f));
        openRealChest(2, -60, 2);
        drainOutbound();
        var menu = p.containerMenu();
        RegionPos rp = new RegionPos(0, 0);
        sched.region(rp).execute(() -> world.regionAt(rp).breakBlock(2, -60, 2)).get();
        await("창 닫힘", () -> !p.hasOpenContainer());
        assertEquals(1, count(drainOutbound(), ContainerClose.ID), "열어 둔 사람에게 강제 container_close");
        // 늦게 도착한 클릭(닫기 전 창 번호)은 무시된다
        ch.writeInbound(frame(0x0E, b -> {
            Types.writeVarInt(b, menu.containerId); Types.writeVarInt(b, 0); b.writeShort(0); b.writeByte(0);
            Types.writeVarInt(b, 0); Types.writeVarInt(b, 0);
            writeStrictSlot(b, 5, 1, 0);
        }));
        assertTrue(slotsOf()[12].isEmpty());
        assertTrue(p.inventoryMenu().getCarried().isEmpty());
    }

    /** 제작대를 열고(번호 1·crafting 메뉴·격자 3×3 포함 46칸) → 블록이 사라지면 틱의 유효성 검사가 창을 닫는다. */
    @Test
    void craftingTable_opensAndClosesWhenBlockIsGone() throws Exception {
        join(new SpawnPoint(0, -60, 0, 0f));
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        world.setRegistries(reg, org.domi.furnace.server.world.ItemRegistry.load("items/1.21.1.txt"));
        int tableState = reg.defaultStateId("minecraft:crafting_table");
        RegionPos rp = new RegionPos(0, 0);
        sched.region(rp).execute(() -> world.regionAt(rp).placeBlock(2, -60, 2, tableState)).get();
        drainOutbound();
        java.lang.reflect.Method m = ConnectionHandler.class.getDeclaredMethod("openCraftingTable", int.class, int.class, int.class);
        m.setAccessible(true);
        m.invoke(handler, 2, -60, 2);
        await("제작대 창 열림", () -> p.hasOpenContainer());
        List<int[]> opened = drainOutbound();
        assertEquals(1, count(opened, 0x33), "open_screen 한 번");
        assertEquals(1, count(opened, ContainerSetContent.ID));
        assertTrue(p.containerMenu() instanceof org.domi.furnace.server.menu.CraftingMenu);
        assertEquals(46, p.containerMenu().slots.size());
        assertEquals("minecraft:crafting", p.containerMenu().menuType());
        // 블록을 없애면 다음 틱 검사에서 닫힌다
        sched.region(rp).execute(() -> world.regionAt(rp).breakBlock(2, -60, 2)).get();
        await("제작대가 사라져 창이 닫힘", () -> {
            sched.region(rp).execute(() -> world.regionAt(rp).tick()).get();   // 테스트 월드는 자동으로 틱하지 않는다 — 틱을 직접 돌려 유효성 검사를 일으킨다
            return !p.hasOpenContainer();
        });
        assertEquals(1, count(drainOutbound(), ContainerClose.ID));
    }

    /** 월드에 블록(상태 이름·속성)을 놓고 그 칸 용기를 연다 — 실제 openContainer 경로. 반환: 그 칸의 블록 엔티티. */
    private org.domi.furnace.server.block.entity.ContainerBlockEntity openBlock(int x, int y, int z, String block, Map<String, String> props,
            java.util.function.Consumer<org.domi.furnace.server.block.entity.ContainerBlockEntity> fill) throws Exception {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        world.setRegistries(reg, org.domi.furnace.server.world.ItemRegistry.load("items/1.21.1.txt"));
        RegionPos rp = new RegionPos(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
        org.domi.furnace.server.block.entity.ContainerBlockEntity[] be = new org.domi.furnace.server.block.entity.ContainerBlockEntity[1];
        sched.region(rp).execute(() -> {
            var r = world.regionAt(rp);
            r.placeBlock(x, y, z, reg.stateIdOrAir(block, props));
            be[0] = (org.domi.furnace.server.block.entity.ContainerBlockEntity) r.level().getBlockEntity(new org.domi.furnace.server.block.Pos(x, y, z));
            if (fill != null) fill.accept(be[0]);
        }).get();
        drainOutbound();
        java.lang.reflect.Method m = ConnectionHandler.class.getDeclaredMethod("openContainer", int.class, int.class, int.class);
        m.setAccessible(true);
        m.invoke(handler, x, y, z);
        await("용기 창 열림", () -> p.hasOpenContainer());
        return be[0];
    }

    @Test
    void barrel_opensAsNineByThree_andShowsItems() throws Exception {
        join(new SpawnPoint(0, -60, 0, 0f));
        var barrel = openBlock(2, -60, 2, "minecraft:barrel", Map.of("facing", "up", "open", "false"), b -> b.items[3] = new ItemStack(1, 7, null));
        var menu = p.containerMenu();
        assertEquals("minecraft:generic_9x3", menu.menuType());
        assertEquals(63, menu.slots.size());
        assertEquals(7, menu.getSlot(3).getItem().count());
        assertEquals(1, count(drainOutbound(), 0x33));
        assertNotNull(barrel);
    }

    @Test
    void enderChest_opensPlayersOwnInventory() throws Exception {
        join(new SpawnPoint(0, -60, 0, 0f));
        p.enderChest().set(4, new ItemStack(1, 9, null));
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        world.setRegistries(reg, org.domi.furnace.server.world.ItemRegistry.load("items/1.21.1.txt"));
        RegionPos rp = new RegionPos(0, 0);
        sched.region(rp).execute(() -> world.regionAt(rp).placeBlock(2, -60, 2, reg.stateIdOrAir("minecraft:ender_chest", Map.of("facing", "north", "waterlogged", "false")))).get();
        drainOutbound();
        java.lang.reflect.Method m = ConnectionHandler.class.getDeclaredMethod("openContainer", int.class, int.class, int.class);
        m.setAccessible(true);
        m.invoke(handler, 2, -60, 2);
        await("엔더 상자 창 열림", () -> p.hasOpenContainer());
        var menu = p.containerMenu();
        assertEquals("minecraft:generic_9x3", menu.menuType());
        assertEquals(9, menu.getSlot(4).getItem().count());
    }

    @Test
    void stonecutter_opensAndSendsInitialData() throws Exception {
        join(new SpawnPoint(0, -60, 0, 0f));
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        world.setRegistries(reg, org.domi.furnace.server.world.ItemRegistry.load("items/1.21.1.txt"));
        RegionPos rp = new RegionPos(0, 0);
        sched.region(rp).execute(() -> world.regionAt(rp).placeBlock(2, -60, 2, reg.stateIdOrAir("minecraft:stonecutter", Map.of("facing", "north")))).get();
        drainOutbound();
        java.lang.reflect.Method m = ConnectionHandler.class.getDeclaredMethod("openStonecutter", int.class, int.class, int.class);
        m.setAccessible(true);
        m.invoke(handler, 2, -60, 2);
        await("석재 절단기 창 열림", () -> p.hasOpenContainer());
        var menu = p.containerMenu();
        assertEquals("minecraft:stonecutter", menu.menuType());
        assertEquals(38, menu.slots.size());
        assertEquals(1, count(drainOutbound(), 0x14), "고른 레시피 번호(-1) 데이터가 초기 동기에 실린다");
    }

    @Test
    void shulkerBox_rejectsNestedShulkerBoxes() throws Exception {
        join(new SpawnPoint(0, -60, 0, 0f));
        openBlock(2, -60, 2, "minecraft:shulker_box", Map.of("facing", "up"), null);
        var menu = p.containerMenu();
        assertEquals("minecraft:shulker_box", menu.menuType());
        assertEquals(63, menu.slots.size());
        int box = org.domi.furnace.server.item.ItemContext.id("minecraft:item", "minecraft:shulker_box");
        int dirt = org.domi.furnace.server.item.ItemContext.id("minecraft:item", "minecraft:dirt");
        assertFalse(menu.getSlot(0).mayPlace(new ItemStack(box, 1)));
        assertFalse(menu.getSlot(0).mayPlace(new ItemStack(org.domi.furnace.server.item.ItemContext.id("minecraft:item", "minecraft:lime_shulker_box"), 1)));
        assertTrue(menu.getSlot(0).mayPlace(new ItemStack(dirt, 1)));
        assertTrue(menu.getSlot(30).mayPlace(new ItemStack(box, 1)), "플레이어 인벤토리 칸은 제한이 없다");
    }

    @Test
    void hopperAndDispenser_haveTheirOwnMenus() throws Exception {
        join(new SpawnPoint(0, -60, 0, 0f));
        openBlock(2, -60, 2, "minecraft:hopper", Map.of("facing", "down", "enabled", "true"), null);
        assertEquals("minecraft:hopper", p.containerMenu().menuType());
        assertEquals(41, p.containerMenu().slots.size());
        ch.writeInbound(frame(0x0F, b -> Types.writeVarInt(b, p.containerMenu().containerId)));
        await("깔때기 창 닫힘", () -> !p.hasOpenContainer());
        openBlock(3, -60, 2, "minecraft:dispenser", Map.of("facing", "north", "triggered", "false"), null);
        assertEquals("minecraft:generic_3x3", p.containerMenu().menuType());
        assertEquals(45, p.containerMenu().slots.size());
    }

    /** 더블 상자: 오른쪽 상자가 앞 27칸, 왼쪽이 뒤 27칸인 9×6 창. 어느 쪽을 눌러도 같고, 꺼내면 그 쪽 상자에서 빠진다. */
    @Test
    void doubleChest_isOneNineBySixMenu_rightChestFirst() throws Exception {
        join(new SpawnPoint(0, -60, 0, 0f));
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        world.setRegistries(reg, org.domi.furnace.server.world.ItemRegistry.load("items/1.21.1.txt"));
        RegionPos rp = new RegionPos(0, 0);
        // facing=north: 왼쪽 상자의 짝은 동쪽(+x), 오른쪽 상자의 짝은 서쪽(-x)
        org.domi.furnace.server.block.entity.ContainerBlockEntity[] chests = new org.domi.furnace.server.block.entity.ContainerBlockEntity[2];
        sched.region(rp).execute(() -> {
            var r = world.regionAt(rp);
            r.placeBlock(2, -60, 2, reg.stateIdOrAir("minecraft:chest", Map.of("facing", "north", "type", "left", "waterlogged", "false")));
            r.placeBlock(3, -60, 2, reg.stateIdOrAir("minecraft:chest", Map.of("facing", "north", "type", "right", "waterlogged", "false")));
            chests[0] = (org.domi.furnace.server.block.entity.ContainerBlockEntity) r.level().getBlockEntity(new org.domi.furnace.server.block.Pos(2, -60, 2));
            chests[1] = (org.domi.furnace.server.block.entity.ContainerBlockEntity) r.level().getBlockEntity(new org.domi.furnace.server.block.Pos(3, -60, 2));
            chests[0].items[0] = new ItemStack(3, 4, null);   // 왼쪽
            chests[1].items[0] = new ItemStack(1, 5, null);   // 오른쪽
        }).get();
        drainOutbound();
        java.lang.reflect.Method m = ConnectionHandler.class.getDeclaredMethod("openContainer", int.class, int.class, int.class);
        m.setAccessible(true);
        m.invoke(handler, 2, -60, 2);   // 왼쪽을 눌렀다
        await("더블 상자 창 열림", () -> p.hasOpenContainer());
        var menu = p.containerMenu();
        assertEquals("minecraft:generic_9x6", menu.menuType());
        assertEquals(90, menu.slots.size());
        assertEquals(5, menu.getSlot(0).getItem().count(), "첫째 = 오른쪽 상자");
        assertEquals(4, menu.getSlot(27).getItem().count(), "둘째 = 왼쪽 상자");
        // 왼쪽 상자 칸(메뉴 27)의 것을 집는다
        ch.writeInbound(frame(0x0E, b -> {
            Types.writeVarInt(b, menu.containerId); Types.writeVarInt(b, menu.getStateId()); b.writeShort(27); b.writeByte(0);
            Types.writeVarInt(b, 0); Types.writeVarInt(b, 1);
            b.writeShort(27); writeStrictSlot(b, 0, 0, 0);
            writeStrictSlot(b, 4, 3, 0);
        }));
        await("왼쪽 상자에서 꺼냄", () -> chests[0].items[0].isEmpty());
        assertEquals(5, chests[1].items[0].count(), "오른쪽 상자는 그대로");
        assertEquals(4, menu.getCarried().count());
    }

    /** 상자를 열면 여는 수가 1 이 되어 뚜껑 block_event(1, 1) 이 나가고, 닫으면 0 으로 돌아온다. 통은 open 속성이 따라 켜졌다 꺼진다. */
    @Test
    void openerCounter_chestSendsLidEvent_barrelTogglesOpenProperty() throws Exception {
        join(new SpawnPoint(0, -60, 0, 0f));
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        var chest = openBlock(2, -60, 2, "minecraft:chest", Map.of("facing", "north", "type", "single", "waterlogged", "false"), null);
        await("상자 여는 수 1", () -> chest.openCount == 1);
        RegionPos chestRp = new RegionPos(0, 0);
        sched.region(chestRp).execute(() -> world.regionAt(chestRp).tick()).get();   // 블록 이벤트는 틱에서 방송된다(테스트 월드는 자동 틱이 없다)
        List<int[]> out = drainOutbound();
        assertTrue(count(out, org.domi.furnace.server.net.protocol.play.BlockEventPacket.ID) >= 1, "뚜껑 block_event 가 나가야 한다");
        ch.writeInbound(frame(0x0F, b -> Types.writeVarInt(b, p.containerMenu().containerId)));
        await("상자 창 닫힘", () -> !p.hasOpenContainer());
        await("상자 여는 수 0", () -> chest.openCount == 0);

        var barrel = openBlock(4, -60, 2, "minecraft:barrel", Map.of("facing", "up", "open", "false"), null);
        RegionPos rp = new RegionPos(0, 0);
        int[] openState = new int[1];
        await("통 열림 속성", () -> {
            sched.region(rp).execute(() -> openState[0] = world.regionAt(rp).level().getBlockState(new org.domi.furnace.server.block.Pos(4, -60, 2))).get();
            return reg.canonicalKeyOf(openState[0]).contains("open=true");
        });
        assertEquals(1, barrel.openCount);
        ch.writeInbound(frame(0x0F, b -> Types.writeVarInt(b, p.containerMenu().containerId)));
        await("통 닫힘", () -> {
            sched.region(rp).execute(() -> openState[0] = world.regionAt(rp).level().getBlockState(new org.domi.furnace.server.block.Pos(4, -60, 2))).get();
            return reg.canonicalKeyOf(openState[0]).contains("open=false");
        });
        assertEquals(0, barrel.openCount);
    }

    /** 덫 상자도 상자처럼 열리고, 여는 사람 수가 신호 세기가 된다. */
    @Test
    void trappedChest_opensAndSignalsOpenerCount() throws Exception {
        join(new SpawnPoint(0, -60, 0, 0f));
        var chest = openBlock(2, -60, 2, "minecraft:trapped_chest", Map.of("facing", "north", "type", "single", "waterlogged", "false"), null);
        assertEquals("minecraft:trapped_chest", chest.type());
        assertEquals("minecraft:generic_9x3", p.containerMenu().menuType());
        await("여는 수 1", () -> chest.openCount == 1);
    }

    /** 부서진 용기의 내용물은 땅에 쏟아지고(통·깔때기·발사기), 셜커 상자는 아직 쏟지 않는다. */
    @Test
    void breakingBarrelDropsContentsAsItemEntities() throws Exception {
        join(new SpawnPoint(0, -60, 0, 0f));
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        world.setRegistries(reg, org.domi.furnace.server.world.ItemRegistry.load("items/1.21.1.txt"));
        RegionPos rp = new RegionPos(0, 0);
        int[] dropped = new int[1];
        sched.region(rp).execute(() -> {
            var r = world.regionAt(rp);
            r.placeBlock(2, -60, 2, reg.stateIdOrAir("minecraft:barrel", Map.of("facing", "up", "open", "false")));
            var b = (org.domi.furnace.server.block.entity.ContainerBlockEntity) r.level().getBlockEntity(new org.domi.furnace.server.block.Pos(2, -60, 2));
            b.items[0] = new ItemStack(1, 5, null);
            r.breakBlock(2, -60, 2);
            assertNull(r.level().getBlockEntity(new org.domi.furnace.server.block.Pos(2, -60, 2)));
            assertTrue(b.items[0].isEmpty(), "쏟은 칸은 비운다");
        }).get();
    }

    /** 접속이 끊기면 바닐라 Player.remove 처럼 커서·제작 격자의 아이템이 인벤토리 창에서 빠진다(월드에 떨어진다) — 저장된 playerdata 와 겹치지 않는다. */
    @Test
    void disconnect_clearsCursorAndCraftGrid() throws Exception {
        join(new SpawnPoint(0, -60, 0, 0f));
        slotsOf()[1] = new ItemStack(1, 3, null);     // 제작 격자
        slotsOf()[36] = new ItemStack(2, 4, null);
        var im = p.inventoryMenu();
        ch.writeInbound(frame(0x0E, b -> {
            Types.writeVarInt(b, 0); Types.writeVarInt(b, im.getStateId()); b.writeShort(36); b.writeByte(0);
            Types.writeVarInt(b, 0); Types.writeVarInt(b, 0);
            writeStrictSlot(b, 4, 2, 0);
        }));
        assertEquals(4, im.getCarried().count());
        ch.close();
        assertTrue(im.getCarried().isEmpty());
        assertTrue(slotsOf()[1].isEmpty(), "제작 격자는 월드에 떨어지고 저장되지 않는다");
    }

    /** 컴포넌트 달린 아이템도 정확히 읽어 그대로 담는다(크리에이티브 슬롯·클릭 모두). */
    @Test
    void componentItems_areDecodedAndKept() throws Exception {
        join(new SpawnPoint(0, -60, 0, 0f));
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
        ch.writeInbound(frame(0x32, b -> {
            b.writeShort(36); Types.writeVarInt(b, 1); Types.writeVarInt(b, 1);
            Types.writeVarInt(b, 1); Types.writeVarInt(b, 0); Types.writeVarInt(b, 13); Types.writeVarInt(b, 7);   // custom_model_data = 7
        }));
        assertEquals(org.domi.furnace.server.item.ComponentPatch.of(java.util.Map.of("minecraft:custom_model_data", 7)), slotsOf()[36].patch());
        ch.writeInbound(frame(0x0E, b -> {
            Types.writeVarInt(b, 0); Types.writeVarInt(b, 0); b.writeShort(-1); b.writeByte(0);
            Types.writeVarInt(b, 0); Types.writeVarInt(b, 0);   // 변경 슬롯 0개
            Types.writeVarInt(b, 1); Types.writeVarInt(b, 1);   // carried: 컴포넌트 있음
            Types.writeVarInt(b, 1); Types.writeVarInt(b, 0); Types.writeVarInt(b, 13); Types.writeVarInt(b, 7);
        }));
        assertTrue(ch.isActive(), "컴포넌트 아이템 하나로 접속이 끊겼다");
    }

    /** 클릭 변경 슬롯 개수에 상한(바닐라 128)이 있어야 한다. */
    @Test
    void containerClick_hugeChangedCount_isRejected() throws Exception {
        join(new SpawnPoint(0, -60, 0, 0f));
        ch.writeInbound(frame(0x0E, b -> {
            Types.writeVarInt(b, 0); Types.writeVarInt(b, 0); b.writeShort(-1); b.writeByte(0);
            Types.writeVarInt(b, 0); Types.writeVarInt(b, 1_000_000);
        }));
        assertFalse(ch.isActive(), "비정상적으로 큰 변경 개수는 연결을 끊어야 한다");
    }

    /** 같은 이름으로 다시 접속하면 바닐라처럼 기존 세션이 사유와 함께 끊겨야 한다(같은 UUID·playerdata 공유 방지). */
    @Test
    void duplicateLogin_kicksExistingSession() throws Exception {
        join(new SpawnPoint(0, -60, 0, 0f));
        EmbeddedChannel second = new EmbeddedChannel(new ConnectionHandler("t", -1, sched, world, new EntityIdAllocator()));
        second.writeInbound(frame(0x00, b -> { Types.writeVarInt(b, 767); Types.writeString(b, "localhost");
            b.writeShort(25565); Types.writeVarInt(b, 2); }));
        second.writeInbound(frame(0x00, b -> { Types.writeString(b, "Tester"); Types.writeUuid(b, new UUID(0, 0)); }));
        List<int[]> out = drainOutbound();
        assertEquals(1, count(out, 0x1D), "기존 세션에 Disconnect(0x1D)가 가야 한다");
        assertFalse(ch.isActive(), "기존 세션은 끊겨야 한다");
        second.close();
    }

    /** 플레이어 위치·인벤토리는 접속 해제·종료 때만이 아니라 주기적으로도 저장돼야 한다(크래시 시 접속 이후 전부 손실 방지). */
    @Test
    void autosave_persistsPlayerWhileStillConnected() throws Exception {
        autosaveTicksForJoin = 2;     // 2틱 = 100ms
        join(new SpawnPoint(0, -60, 0, 0f));
        move(ch, 10, -60, 3);
        long deadline = System.nanoTime() + 5_000_000_000L;
        org.domi.furnace.server.entity.PlayerData.Snapshot s = null;
        while (System.nanoTime() < deadline) {
            ch.runPendingTasks();
            s = org.domi.furnace.server.entity.PlayerData.load(tmp, p.uuid());
            if (s != null && s.x() == 10.0) break;
            Thread.sleep(50);
        }
        assertTrue(ch.isActive(), "아직 접속 중이어야 한다");
        assertNotNull(s, "자동저장으로 파일이 있어야 한다");
        assertEquals(10.0, s.x());
    }

    /** 틱당 수천 개의 패킷을 쏟아내는 클라이언트는 킥돼야 한다(region 인박스 폭주·DoS 방지). 정상적인 소규모 버스트는 통과. */
    @Test
    void packetFlood_isKicked_butSmallBurstIsNot() throws Exception {
        join(new SpawnPoint(0, -60, 0, 0f));
        for (int i = 0; i < 60; i++) ch.writeInbound(frame(0x2F, b -> b.writeShort(1)));   // Set Held Item 60개
        assertTrue(ch.isActive(), "작은 버스트까지 킥하면 안 된다");
        for (int i = 0; i < 3000; i++) {
            if (!ch.isActive()) break;
            ch.writeInbound(frame(0x2F, b -> b.writeShort(1)));
        }
        assertFalse(ch.isActive(), "패킷 폭주는 연결을 끊어야 한다");
    }

    /** 한 번에 쏟아낸 이동 패킷으로 수천 블록을 순간이동할 수 없어야 한다(틱당 이동 예산). */
    @Test
    void movementBurst_isLimitedByBudget() throws Exception {
        join(new SpawnPoint(0, -60, 0, 0f));
        for (int i = 1; i <= 60; i++) move(ch, i * 90.0, -60, 0);   // 패킷마다 90블록, 합계 5400블록
        double reached = (double) (Double) fieldOf("ix");
        assertTrue(reached < 1000, "예산 없이 5400블록을 이동했다: ix=" + reached);
    }

    private Object fieldOf(String name) throws Exception { return field(name); }

    /** 연결이 끊기면 region 틱을 기다리지 않고 즉시 접속자 집합(탭 기준)에서 빠져야 한다 — adopt 전에 끊긴 경우의 누수 방지. */
    @Test
    void disconnect_removesPlayerFromOnlineSetImmediately() throws Exception {
        join(new SpawnPoint(0, -60, 0, 0f));
        assertNotNull(world.onlinePlayer(p.entityId()));
        ch.close();
        assertNull(world.onlinePlayer(p.entityId()), "끊긴 플레이어가 접속자 집합에 남아 있다");
    }

    /** 끊길 때 저장 위치는 region이 아직 drain 안 한 최신 IO 위치여야 한다(옛/0,0,0 값으로 덮어쓰면 안 된다). */
    @Test
    void disconnect_savesLatestIoPosition_evenIfRegionHasNotDrained() throws Exception {
        join(new SpawnPoint(0, -60, 0, 0f));
        move(ch, 10, -60, 3);              // region drain 없이 바로 끊는다
        ch.close();
        long deadline = System.nanoTime() + 5_000_000_000L;
        org.domi.furnace.server.entity.PlayerData.Snapshot s = null;
        while (System.nanoTime() < deadline) {
            s = org.domi.furnace.server.entity.PlayerData.load(tmp, p.uuid());
            if (s != null && s.x() == 10.0) break;
            Thread.sleep(20);
        }
        assertNotNull(s, "플레이어 데이터가 저장되어야 한다");
        assertEquals(10.0, s.x());
        assertEquals(3.0, s.z());
    }

    @Test
    void farFromOriginSpawn_normalMoveIsAccepted() throws Exception {
        join(new SpawnPoint(5000, -60, 5000, 0f));
        double x0 = p.x();
        move(ch, x0 + 0.3, p.y(), p.z());
        p.drainPending();
        assertEquals(x0 + 0.3, p.x(), 1e-9, "스폰 근처의 정상 이동은 수락돼야 한다");
        assertEquals(0, count(drainOutbound(), SyncPlayerPosition.ID), "정상 이동에 보정이 나가면 안 된다");
    }

    private static boolean loaded(World world, RegionPos rp, int bx, int bz) throws Exception {
        boolean[] r = new boolean[1];
        world.scheduler().region(rp).execute(() -> r[0] = world.regionAt(rp).chunkLoaded(ChunkPos.ofBlock(bx, bz))).get();
        return r[0];
    }

    @Test
    void blockRequests_farAwayOrOutOfRange_doNotCreateChunks_andAreAcked() throws Exception {
        join(new SpawnPoint(0, -60, 0, 0f));
        // 스폰 청크(0,0 근방, 반경 2)가 아닌 곳의 요청 — 거부되고 청크가 새로 생기면 안 된다.
        int[][] bad = {{1_000_000, -61, 1_000_000}, {5000, -61, 0}, {100, -61, 100}, {100, 1000, 100}, {100, -1000, 100}};
        // 스폰 청크 안이지만 월드 높이 밖 — 예외 없이 거부+ack만 돼야 한다(청크는 원래 로드돼 있음).
        int[][] outOfHeight = {{0, 1000, 0}, {0, -1000, 0}};
        int[][] all = java.util.stream.Stream.concat(java.util.Arrays.stream(bad), java.util.Arrays.stream(outOfHeight)).toArray(int[][]::new);
        for (int[] b : all) {
            ch.writeInbound(frame(0x24, f -> { Types.writeVarInt(f, 0); Types.writeBlockPos(f, b[0], b[1], b[2]);
                f.writeByte(1); Types.writeVarInt(f, 7); }));                       // 파괴
            ch.writeInbound(frame(0x38, f -> { Types.writeVarInt(f, 0); Types.writeBlockPos(f, b[0], b[1], b[2]);
                Types.writeVarInt(f, 1); f.writeFloat(0.5f); f.writeFloat(0.5f); f.writeFloat(0.5f);
                f.writeBoolean(false); Types.writeVarInt(f, 8); }));                // 설치/상자 열기
        }
        for (int i = 0; i < 20; i++) { ch.runPendingTasks(); Thread.sleep(25); }
        for (int[] b : bad) {
            RegionPos rp = new RegionPos(b[0] >> 4, b[2] >> 4);
            assertFalse(loaded(world, rp, b[0], b[2]), "거부된 요청이 청크를 생성했다: " + java.util.Arrays.toString(b));
        }
        // 바닐라: ack 는 틱 끝에 한 번(그 틱에 받은 가장 큰 sequence) — 요청마다가 아니다. region 을 한 틱 돌려 내보낸다.
        world.tickOnce().get();
        for (int i = 0; i < 5; i++) { ch.runPendingTasks(); Thread.sleep(10); }
        assertTrue(count(drainOutbound(), BlockChangedAck.ID) >= 1, "거부해도 예측 정리용 ack는 보내야 한다");
    }

    @Test
    void digWithinReachOnLoadedChunk_breaksBlock() throws Exception {
        join(new SpawnPoint(0, -60, 0, 0f));
        ch.writeInbound(frame(0x24, f -> { Types.writeVarInt(f, 0); Types.writeBlockPos(f, 1, -61, 1);
            f.writeByte(1); Types.writeVarInt(f, 3); }));
        RegionPos r00 = new RegionPos(0, 0);
        int[] state = new int[1];
        for (int i = 0; i < 20; i++) {
            ch.runPendingTasks();
            sched.region(r00).execute(() -> state[0] = world.regionAt(r00).chunkAt(new ChunkPos(0, 0)).getBlock(1, -61, 1)).get();
            if (state[0] == ChunkSection.AIR) break;
            Thread.sleep(25);
        }
        assertEquals(ChunkSection.AIR, state[0], "도달 거리 안의 로드된 청크 블록은 파괴된다");
    }
}
