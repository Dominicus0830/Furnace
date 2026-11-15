// 용기 잠금(바닐라 LockCode·BaseContainerBlockEntity.canUnlock): 실제 접속에서 잠긴 상자·신호기를 열면 화면이 안 열리고
// "container.isLocked" 알림(액션 바)과 block.chest.locked 소리가 그 플레이어에게만 간다. 열쇠 이름을 단 아이템을 들면 열린다.
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.entity.BeaconBlockEntity;
import org.domi.furnace.server.block.entity.ContainerBlockEntity;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.ConnectionHandler;
import org.domi.furnace.server.net.ServerLoginContextAccess;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.protocol.play.OpenScreen;
import org.domi.furnace.server.net.protocol.play.SoundPacket;
import org.domi.furnace.server.net.protocol.play.SystemChat;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.Region;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContainerLockE2ETest {

    private static final ChunkPos C00 = new ChunkPos(0, 0);
    private static final byte[] LOCKED = "container.isLocked".getBytes(StandardCharsets.UTF_8);

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private PluginHost host;
    private EmbeddedChannel ch;
    private ConnectionHandler handler;
    private Player p;
    private final List<Pkt> out = new ArrayList<>();

    record Pkt(int id, byte[] body) {}

    @BeforeAll
    static void install() { ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt"))); }

    @BeforeEach
    void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        host = core.plugins();
        core.world().setRegistries(org.domi.furnace.server.world.BlockRegistry.load("blocks/1.21.1.txt"), org.domi.furnace.server.world.ItemRegistry.load("items/1.21.1.txt"));
        core.world().submitAtChunk(C00, r -> r.chunkAtBlocking(C00)).get(30, TimeUnit.SECONDS);
        join();
        p.vitals().setGameMode(org.domi.furnace.server.world.GameMode.SURVIVAL);
        spot = new Pos((int) Math.floor(p.x()) + 2, -60, (int) Math.floor(p.z()));
    }

    /** 플레이어 옆 바닥 위 칸. */
    private Pos spot;

    @AfterEach
    void tearDown() throws Exception {
        if (ch != null) ch.close();
        host.close();
        sched.close();
        TestServers.drainIoPools();
    }

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

    /** 접속하고 스폰 청크 전달이 끝날 때까지 나간 패킷을 모두 모은다(지우지 않는다). */
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
            drain();
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
        p.drainPending();   // 접속 대기 중인 위치를 region 이 반영
        drain();
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

    private <T> T at(java.util.function.Function<Region, T> f) throws Exception {
        return core.world().submitAtChunk(C00, f).get(30, TimeUnit.SECONDS);
    }

    private static boolean contains(byte[] hay, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= hay.length; i++) {
            for (int j = 0; j < needle.length; j++) if (hay[i + j] != needle[j]) continue outer;
            return true;
        }
        return false;
    }

    private void useItemOn(Pos pos, int seq) {
        ch.writeInbound(frame(0x38, b -> {
            Types.writeVarInt(b, 0);
            Types.writeBlockPos(b, pos.x(), pos.y(), pos.z());
            Types.writeVarInt(b, 1);
            b.writeFloat(0.5f); b.writeFloat(1.0f); b.writeFloat(0.5f);
            b.writeBoolean(false);
            Types.writeVarInt(b, seq);
        }));
    }

    /** 몇 틱 돌리며 나간 패킷을 모은다. */
    private void settle() throws Exception {
        for (int i = 0; i < 10; i++) {
            core.world().submitAtChunk(p.chunkPos(), r -> { r.tick(); return null; }).get(30, TimeUnit.SECONDS);
            drain();
            Thread.sleep(20);
        }
    }

    private boolean sent(int id) { return out.stream().anyMatch(k -> k.id() == id); }

    private boolean lockedNotice() { return out.stream().anyMatch(k -> k.id() == SystemChat.ID && contains(k.body(), LOCKED)); }

    private void hold(String name) {
        var stack = org.domi.furnace.server.block.B.stack("minecraft:stick", 1);
        if (name != null) stack = ItemTools.with(stack, "minecraft:custom_name", name);
        p.inventory().set(36 + p.inventory().selected(), stack);
    }

    private int count(int id) { return (int) out.stream().filter(k -> k.id() == id).count(); }

    private long lockedNotices() { return out.stream().filter(k -> k.id() == SystemChat.ID && contains(k.body(), LOCKED)).count(); }

    private boolean noticeHas(String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        return out.stream().anyMatch(k -> k.id() == SystemChat.ID && contains(k.body(), LOCKED) && contains(k.body(), b));
    }

    private ContainerBlockEntity[] doubleChest() throws Exception {
        var reg = org.domi.furnace.server.world.BlockRegistry.load("blocks/1.21.1.txt");
        Pos left = spot, right = new Pos(spot.x() + 1, spot.y(), spot.z());
        return at(r -> {
            r.placeBlock(left.x(), left.y(), left.z(), reg.stateIdOrAir("minecraft:chest", java.util.Map.of("facing", "north", "type", "left", "waterlogged", "false")));
            r.placeBlock(right.x(), right.y(), right.z(), reg.stateIdOrAir("minecraft:chest", java.util.Map.of("facing", "north", "type", "right", "waterlogged", "false")));
            return new ContainerBlockEntity[] {(ContainerBlockEntity) r.level().getBlockEntity(left), (ContainerBlockEntity) r.level().getBlockEntity(right)};
        });
    }

    /** 알림은 액션 바(SystemChat overlay=true — 바닐라 displayClientMessage(…, true)). */
    private boolean noticeIsOverlay() {
        var k = out.stream().filter(x -> x.id() == SystemChat.ID && contains(x.body(), LOCKED)).findFirst().orElseThrow();
        return k.body()[k.body().length - 1] == 1;
    }

    @Test
    void lockedChestDoesNotOpenWithoutKey() throws Exception {
        at(r -> {
            r.level().setBlock(spot, B.defaultState("minecraft:chest"), 3);
            ((ContainerBlockEntity) r.level().getBlockEntity(spot)).lock = "key";
            return null;
        });
        hold("\"wrong\"");
        out.clear();
        useItemOn(spot, 1);
        settle();
        assertFalse(sent(OpenScreen.ID), "잠긴 상자는 열리지 않는다");
        assertTrue(lockedNotice(), "container.isLocked 알림");
        assertTrue(noticeIsOverlay(), "알림은 액션 바");
        assertTrue(sent(SoundPacket.ID), "block.chest.locked 소리");

        hold("\"key\"");
        out.clear();
        useItemOn(spot, 2);
        settle();
        assertTrue(sent(OpenScreen.ID), "열쇠 이름을 든 아이템이면 열린다");
        assertFalse(lockedNotice());
    }

    /**
     * 관전자는 잠금 검사를 건너뛴다(canUnlock 의 isSpectator) — 잠금 알림·소리가 없다. 바닐라 관전자는 잠긴 통도 실제로 열지만
     * Furnace 관전자는 아직 용기를 아예 열지 못해(잠금과 무관한 기존 동작, 후속 과제) 여기서는 알림·소리가 없는 것만 본다.
     */
    @Test
    void spectatorGetsNoLockNotice() throws Exception {
        at(r -> {
            r.level().setBlock(spot, B.defaultState("minecraft:barrel"), 3);
            ((ContainerBlockEntity) r.level().getBlockEntity(spot)).lock = "key";
            return null;
        });
        p.vitals().setGameMode(org.domi.furnace.server.world.GameMode.SPECTATOR);
        hold(null);
        out.clear();
        useItemOn(spot, 1);
        settle();
        assertFalse(lockedNotice(), "관전자는 잠금을 무시한다");
        assertEquals(0, count(SoundPacket.ID), "잠금 소리도 없다");
    }

    @Test
    void lockedBeaconDoesNotOpenWithoutKey() throws Exception {
        at(r -> {
            r.level().setBlock(spot, B.defaultState("minecraft:beacon"), 3);
            ((BeaconBlockEntity) r.level().getBlockEntity(spot)).lock = "key";
            return null;
        });
        hold(null);
        out.clear();
        useItemOn(spot, 1);
        settle();
        assertFalse(sent(OpenScreen.ID), "잠긴 신호기는 열리지 않는다");
        assertTrue(lockedNotice());
    }

    /** 큰 상자: 둘째(왼쪽)만 잠겨도 막힌다. 알림 한 번, 소리 한 번. 이름 인자는 그 상자(둘째)의 이름. */
    @Test
    void doubleChestSecondLockedBlocks() throws Exception {
        var c = doubleChest();
        at(r -> { c[0].lock = "key"; c[0].customName = "\"LeftBox\""; return null; });
        hold("\"wrong\"");
        out.clear();
        useItemOn(spot, 1);
        settle();
        assertEquals(0, count(OpenScreen.ID), "둘째가 잠기면 안 열린다");
        assertEquals(1, lockedNotices());
        assertEquals(1, count(SoundPacket.ID));
        assertTrue(noticeHas("LeftBox"), "알림 이름 = 잠긴 상자의 이름");
    }

    /** 큰 상자 둘 다 다른 열쇠: 첫째(오른쪽)에서 막히고 알림·소리는 한 번, 이름은 첫째 것(&& 단락). */
    @Test
    void doubleChestBothLockedOneNoticeFirstName() throws Exception {
        var c = doubleChest();
        at(r -> {
            c[0].lock = "a"; c[0].customName = "\"LeftBox\"";
            c[1].lock = "b"; c[1].customName = "\"RightBox\"";
            return null;
        });
        hold(null);
        out.clear();
        useItemOn(spot, 1);
        settle();
        assertEquals(0, count(OpenScreen.ID));
        assertEquals(1, lockedNotices(), "알림 한 번");
        assertEquals(1, count(SoundPacket.ID), "소리 한 번");
        assertTrue(noticeHas("RightBox"), "첫째(오른쪽)의 이름");
        assertFalse(noticeHas("LeftBox"));
    }

    /** 큰 상자 둘 다 같은 열쇠면 열린다. */
    @Test
    void doubleChestBothSameKeyOpens() throws Exception {
        var c = doubleChest();
        at(r -> { c[0].lock = "k"; c[1].lock = "k"; return null; });
        hold("\"k\"");
        out.clear();
        useItemOn(spot, 1);
        settle();
        assertEquals(1, count(OpenScreen.ID));
        assertEquals(0, lockedNotices());
    }

    /** 이름 없는 단일 상자의 알림 인자는 container.chest(큰 상자여도 각 상자 이름이라 chestDouble 아님). */
    @Test
    void noticeDefaultNameIsSingleChestKey() throws Exception {
        var c = doubleChest();
        at(r -> { c[1].lock = "x"; return null; });
        hold(null);
        out.clear();
        useItemOn(spot, 1);
        settle();
        assertTrue(noticeHas("container.chest"));
        assertFalse(noticeHas("container.chestDouble"));
    }

    /** 소리는 block.chest.locked 레지스트리 번호(+1)로, blocks 소스로. */
    @Test
    void soundIsChestLocked() throws Exception {
        at(r -> {
            r.level().setBlock(spot, B.defaultState("minecraft:barrel"), 3);
            ((ContainerBlockEntity) r.level().getBlockEntity(spot)).lock = "key";
            return null;
        });
        hold(null);
        out.clear();
        useItemOn(spot, 1);
        settle();
        var s = out.stream().filter(k -> k.id() == SoundPacket.ID).findFirst().orElseThrow();
        var bb = Unpooled.wrappedBuffer(s.body());
        int sid = Types.readVarInt(bb) - 1;
        assertEquals((int) ItemContext.id("minecraft:sound_event", "minecraft:block.chest.locked"), sid);
        assertEquals(4, Types.readVarInt(bb), "blocks");
    }

    /** 다른 용기 종류(화로·깔때기·셜커 상자·제작기)도 막힌다. */
    @Test
    void otherContainersBlocked() throws Exception {
        for (String block : new String[] {"minecraft:furnace", "minecraft:hopper", "minecraft:shulker_box", "minecraft:dispenser", "minecraft:brewing_stand", "minecraft:crafter"}) {
            at(r -> {
                r.level().setBlock(spot, B.defaultState("minecraft:air"), 3);
                r.level().setBlock(spot, B.defaultState(block), 3);
                ((ContainerBlockEntity) r.level().getBlockEntity(spot)).lock = "key";
                return null;
            });
            hold(null);
            out.clear();
            useItemOn(spot, 1);
            settle();
            assertEquals(0, count(OpenScreen.ID), block);
            assertEquals(1, lockedNotices(), block);
        }
    }

    /** 경합: 열쇠를 들고 누른 직후 다른 칸을 고르면(같은 차례에 받은 패킷), 바닐라는 누를 때의 손으로 검사해 연다. */
    @Test
    void keyCheckedAtClickTimeNotAfterSlotSwitch() throws Exception {
        at(r -> {
            r.level().setBlock(spot, B.defaultState("minecraft:barrel"), 3);
            ((ContainerBlockEntity) r.level().getBlockEntity(spot)).lock = "key";
            return null;
        });
        int fails = 0;
        for (int i = 0; i < 10; i++) {
            p.inventory().setSelected(0);
            p.inventory().set(36, ItemTools.with(B.stack("minecraft:stick", 1), "minecraft:custom_name", "\"key\""));
            p.inventory().set(37, org.domi.furnace.server.entity.ItemStack.EMPTY);
            if (p.hasOpenContainer()) {
                int cid = p.containerMenu().containerId;
                ch.writeInbound(frame(0x0F, b -> Types.writeVarInt(b, cid)));
                settle();
            }
            out.clear();
            int seq = 10 + i;
            useItemOn(spot, seq);
            ch.writeInbound(frame(0x2F, b -> b.writeShort(1)));   // 바로 다음 칸
            settle();
            if (count(OpenScreen.ID) != 1) fails++;
        }
        assertEquals(0, fails, "누른 뒤 칸을 바꿨을 때 잠금으로 막힌 횟수(10번 중)");
    }

    /** 디스크 왕복: Lock 은 저장되고 빈 Lock 은 쓰지 않는다(BE 저장 경로). */
    @Test
    void beRoundTrip() throws Exception {
        at(r -> {
            r.level().setBlock(spot, B.defaultState("minecraft:barrel"), 3);
            var be = (ContainerBlockEntity) r.level().getBlockEntity(spot);
            var t = new org.domi.furnace.server.net.io.nbt.NbtCompound();
            t.putString("Lock", "");
            be.load(t);
            assertNull(be.lock);
            assertFalse(be.saveWithoutMetadata().tags().containsKey("Lock"));
            t.putString("Lock", "k");
            be.load(t);
            assertEquals("k", be.saveWithoutMetadata().tags().get("Lock"));
            return null;
        });
    }

    @Test
    void unlocksWithComparesPlainName() {
        assertTrue(ContainerBlockEntity.unlocksWith(null, null), "열쇠가 없으면 늘 열린다");
        assertTrue(ContainerBlockEntity.unlocksWith("", null));
        var plain = org.domi.furnace.server.block.B.stack("minecraft:stick", 1);
        assertFalse(ContainerBlockEntity.unlocksWith("key", plain), "이름 없는 아이템");
        assertTrue(ContainerBlockEntity.unlocksWith("key", ItemTools.with(plain, "minecraft:custom_name", "{\"text\":\"key\",\"color\":\"red\"}")), "글자만 본다");
        assertFalse(ContainerBlockEntity.unlocksWith("key", ItemTools.with(plain, "minecraft:custom_name", "\"Key\"")), "대소문자는 다르다");
    }
}
