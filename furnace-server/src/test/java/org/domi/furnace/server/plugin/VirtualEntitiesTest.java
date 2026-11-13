// 가상 엔티티·가짜 플레이어·가짜 탭 줄: 범위 추적, 플레이어별 보이기, 월드 이동, 클릭, 정리.
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.domi.furnace.api.entity.EquipmentSlot;
import org.domi.furnace.api.entity.GameMode;
import org.domi.furnace.api.entity.Hand;
import org.domi.furnace.api.entity.ProfileProperty;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.packet.Packet;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.packet.clientbound.*;
import org.domi.furnace.api.packet.serverbound.ServerboundInteractPacket;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.virtual.VirtualEntity;
import org.domi.furnace.api.virtual.VirtualInteraction;
import org.domi.furnace.api.virtual.VirtualPlayer;
import org.domi.furnace.api.virtual.VirtualTabList;
import org.domi.furnace.api.world.Location;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.PacketTap;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.plugin.packet.ProtocolCodec;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VirtualEntitiesTest {

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private PluginHost host;
    private ProtocolCodec codec;
    private final TestPlugins.Gamma a = new TestPlugins.Gamma();
    private final TestPlugins.Gamma b = new TestPlugins.Gamma();
    private final java.util.Map<Player, EmbeddedChannel> channels = new java.util.HashMap<>();
    private static final String WORLD = "minecraft:overworld";

    @BeforeEach
    void setUp() {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        host = core.plugins();
        codec = host.packetsImpl().codec();
    }

    @AfterEach
    void tearDown() throws Exception {
        host.close();
        sched.close();
        TestServers.drainIoPools();
    }

    private Player online(String name, double x, double z) {
        EmbeddedChannel ch = new EmbeddedChannel();
        Player p = new Player(core.world().entityIds().next(), UUID.randomUUID(), name, ch);
        p.setPosRaw(x, 64, z);
        channels.put(p, ch);
        core.world().playerOnline(p);
        host.packetsImpl().attach(p);
        return p;
    }

    /** 보낸 패킷을 (id, 값) 으로. 타입 레코드가 없는 종류는 GenericPacket 으로 나온다. */
    private List<Packet> sent(Player p) {
        EmbeddedChannel ch = channels.get(p);
        ch.runPendingTasks();
        List<Packet> out = new ArrayList<>();
        Object o;
        while ((o = ch.readOutbound()) != null) {
            ByteBuf bb = (ByteBuf) o;
            int id = Types.readVarInt(bb);
            PacketType<?> t = codec.clientboundType(id);
            out.add(codec.decode(t, id, bb));
            bb.release();
        }
        return out;
    }



    private static List<PacketType<?>> kinds(List<Packet> l) {
        List<PacketType<?>> k = new ArrayList<>();
        for (Packet p : l) k.add(p.type());
        return k;
    }

    private void track() { host.virtualImpl().tickForTest(); }

    @Test
    void entityAppearsOnlyForPlayersInRangeAndFollowsThem() {
        Player near = online("Near", 10, 0), far = online("Far", 500, 0);
        VirtualEntity e = host.virtualEntities().spawn(a, "villager", new Location(WORLD, 0, 64, 0));
        List<Packet> n = sent(near);
        assertEquals(List.of(PacketTypes.Clientbound.BUNDLE_DELIMITER, PacketTypes.Clientbound.ADD_ENTITY, PacketTypes.Clientbound.ROTATE_HEAD,
                PacketTypes.Clientbound.BUNDLE_DELIMITER), kinds(n), "생성 묶음은 번들 하나");
        assertEquals(e.entityId(), ((ClientboundAddEntityPacket) n.get(1)).entityId());
        assertEquals("minecraft:villager", ((ClientboundAddEntityPacket) n.get(1)).entityType());
        assertEquals(List.of(), sent(far), "멀리 있는 플레이어에게는 안 보낸다");
        assertEquals(1, e.viewers().size());

        // 이미 보는 중이면 바뀐 메타데이터가 바로 간다
        e.setCustomName(Text.text("상점"));
        e.setCustomNameVisible(true);
        assertEquals(2, sent(near).size());
        e.setGlowing(true);
        var meta = (ClientboundSetEntityDataPacket) sent(near).get(0);
        assertEquals(new ClientboundSetEntityDataPacket.ByteValue((byte) 0x40), meta.entries().get(0).value());

        // 멀리 있던 사람이 다가오면 다음 추적 때 나타난다
        far.setPosRaw(5, 64, 5);
        track();
        assertEquals(PacketTypes.Clientbound.ADD_ENTITY, sent(far).get(1).type());
        // 가까운 사람이 멀어지면(범위 + 여유 밖) 사라진다
        near.setPosRaw(200, 64, 0);
        track();
        var gone = (ClientboundRemoveEntitiesPacket) sent(near).get(0);
        assertEquals(List.of(e.entityId()), gone.entityIds());
        assertEquals(1, e.viewers().size());

        // 같은 월드 안에서 옮기면 보는 사람에게 teleport
        e.teleport(new Location(WORLD, 3, 64, 3));
        assertEquals(PacketTypes.Clientbound.TELEPORT_ENTITY, sent(far).get(0).type());
        // 다른 월드로 옮기면 보던 사람에게서 사라진다
        e.teleport(new Location("minecraft:the_nether", 0, 64, 0));
        assertEquals(PacketTypes.Clientbound.REMOVE_ENTITIES, sent(far).get(0).type());
        assertEquals(0, e.viewers().size());
    }

    @Test
    void perPlayerVisibilityAndRemoval() {
        Player p1 = online("A", 0, 0), p2 = online("B", 0, 0);
        VirtualEntity e = host.virtualEntities().spawn(a, "minecraft:zombie", new Location(WORLD, 1, 64, 1));
        sent(p1);
        sent(p2);
        e.hide(host.player(p1));
        assertEquals(PacketTypes.Clientbound.REMOVE_ENTITIES, sent(p1).get(0).type());
        assertEquals(List.of(), sent(p2));
        e.setVisibleByDefault(false);
        assertEquals(PacketTypes.Clientbound.REMOVE_ENTITIES, sent(p2).get(0).type(), "기본을 끄면 지정 없는 사람은 사라진다");
        e.show(host.player(p2));
        assertEquals(PacketTypes.Clientbound.ADD_ENTITY, sent(p2).get(1).type());
        assertEquals(List.of(), sent(p1));
        e.resetVisibility(host.player(p2));
        assertEquals(PacketTypes.Clientbound.REMOVE_ENTITIES, sent(p2).get(0).type());
        e.setVisibleByDefault(true);
        e.show(host.player(p2));
        sent(p2);
        e.remove();
        assertTrue(e.isRemoved());
        assertEquals(PacketTypes.Clientbound.REMOVE_ENTITIES, sent(p2).get(0).type());
        e.remove();   // 두 번째는 아무 일 없다
        assertTrue(host.virtualEntities().byEntityId(e.entityId()).isEmpty());
    }

    @Test
    void fakePlayerSendsProfileBeforeEntityAndHidesNameTagAndCleansUp() {
        Player p = online("Viewer", 0, 0);
        VirtualPlayer npc = host.virtualEntities().spawnPlayer(a, "Guard", List.of(ProfileProperty.textures("dGV4", "sig")), new Location(WORLD, 2, 64, 2));
        List<Packet> out = sent(p);
        // 정보 → (팀: 서버 내부 패킷이라 타입 래퍼 없음) → 엔티티 → 데이터 → 머리
        assertEquals(PacketTypes.Clientbound.PLAYER_INFO_UPDATE, out.get(0).type());
        var info = (ClientboundPlayerInfoUpdatePacket) out.get(0);
        assertEquals("Guard", info.entries().get(0).name());
        assertEquals("dGV4", info.entries().get(0).properties().get(0).value());
        assertFalse(info.entries().get(0).listed(), "탭 목록에는 기본으로 안 올린다");
        int add = kinds(out).indexOf(PacketTypes.Clientbound.ADD_ENTITY);
        assertTrue(add > 0, "엔티티는 프로필 정보 뒤에");
        assertEquals("minecraft:player", ((ClientboundAddEntityPacket) out.get(add)).entityType());
        var data = (ClientboundSetEntityDataPacket) out.get(kinds(out).indexOf(PacketTypes.Clientbound.SET_ENTITY_DATA));
        assertTrue(data.entries().contains(new ClientboundSetEntityDataPacket.Entry(17, new ClientboundSetEntityDataPacket.ByteValue((byte) 0x7F))), "스킨 겉 레이어 전부");
        npc.setSneaking(true);
        List<Packet> sn = sent(p);
        assertEquals(2, sn.size(), "상태 비트와 자세 둘 다");
        assertEquals(new ClientboundSetEntityDataPacket.PoseValue(5), ((ClientboundSetEntityDataPacket) sn.get(1)).entries().get(0).value());

        core.world().setRegistries(core.world().blockRegistry(), org.domi.furnace.server.world.ItemRegistry.load("items/1.21.1.txt"));
        npc.setEquipment(EquipmentSlot.MAIN_HAND, ItemStack.of("minecraft:iron_sword"));
        assertEquals(1, sent(p).size());
        npc.setListed(true);
        assertEquals(PacketTypes.Clientbound.PLAYER_INFO_UPDATE, sent(p).get(0).type());

        // 플러그인이 꺼지면(정리) 엔티티·프로필이 지워진다
        host.virtualImpl().removeAll(a);
        List<Packet> end = sent(p);
        assertTrue(kinds(end).contains(PacketTypes.Clientbound.REMOVE_ENTITIES));
        assertTrue(kinds(end).contains(PacketTypes.Clientbound.PLAYER_INFO_REMOVE));
        assertTrue(npc.isRemoved());
        assertEquals(0, host.virtualEntities().entities(a).size());
    }

    @Test
    void namedMetadataKeysOnDisplayEntities() {
        Player p = online("Viewer", 0, 0);
        core.world().setRegistries(core.world().blockRegistry(), org.domi.furnace.server.world.ItemRegistry.load("items/1.21.1.txt"));
        VirtualEntity text = host.virtualEntities().spawn(a, "text_display", new Location(WORLD, 1, 65, 1));
        VirtualEntity item = host.virtualEntities().spawn(a, "item_display", new Location(WORLD, 2, 65, 1));
        sent(p);
        text.set(org.domi.furnace.api.entity.data.EntityDataKeys.TextDisplay.TEXT, Text.text("상점"));
        text.set(org.domi.furnace.api.entity.data.EntityDataKeys.Display.SCALE, new org.domi.furnace.api.math.Vector3f(2, 2, 2));
        item.set(org.domi.furnace.api.entity.data.EntityDataKeys.ItemDisplay.ITEM_STACK, ItemStack.of("minecraft:diamond"));
        item.set(org.domi.furnace.api.entity.data.EntityDataKeys.Display.LEFT_ROTATION, org.domi.furnace.api.math.Quaternionf.rotationY(45));
        List<Packet> out = sent(p);
        assertEquals(4, out.size());
        var t0 = (ClientboundSetEntityDataPacket) out.get(0);
        assertEquals(java.util.Optional.of(Text.text("상점")), t0.get(org.domi.furnace.api.entity.data.EntityDataKeys.TextDisplay.TEXT));
        assertEquals(23, t0.entries().get(0).index(), "1.21.1 TextDisplay.text 칸");
        var i0 = (ClientboundSetEntityDataPacket) out.get(2);
        assertEquals(java.util.Optional.of(ItemStack.of("minecraft:diamond")), i0.get(org.domi.furnace.api.entity.data.EntityDataKeys.ItemDisplay.ITEM_STACK));
        // 그 종류에 없는 칸은 클라이언트가 끊기기 전에 막는다
        assertThrows(IllegalArgumentException.class, () -> text.set(org.domi.furnace.api.entity.data.EntityDataKeys.ItemDisplay.ITEM_STACK, ItemStack.of("minecraft:diamond")));
        VirtualEntity zombie = host.virtualEntities().spawn(a, "zombie", new Location(WORLD, 3, 64, 1));
        assertThrows(IllegalArgumentException.class, () -> zombie.set(org.domi.furnace.api.entity.data.EntityDataKeys.Display.SCALE, org.domi.furnace.api.math.Vector3f.ONE));
        zombie.set(org.domi.furnace.api.entity.data.EntityDataKeys.Zombie.BABY, true);
        sent(p);
        // 새로 보게 된 사람에게는 쌓인 칸이 생성 묶음으로 간다
        Player late = online("Late", 500, 0);
        late.setPosRaw(1, 64, 1);
        track();
        var lateData = sent(late).stream().filter(x -> x instanceof ClientboundSetEntityDataPacket d && d.entityId() == text.entityId())
                .map(x -> (ClientboundSetEntityDataPacket) x).findFirst().orElseThrow();
        assertEquals(java.util.Optional.of(new org.domi.furnace.api.math.Vector3f(2, 2, 2)), lateData.get(org.domi.furnace.api.entity.data.EntityDataKeys.Display.SCALE));
    }

    /** 생성 묶음은 바닐라처럼 번들 하나 — 메타데이터·장비 없는 모습이 한 프레임 보이지 않는다(E2). */
    @Test
    void spawnIsSentAsOneBundle() {
        VirtualEntity text = host.virtualEntities().spawn(a, "text_display", new Location(WORLD, 1, 65, 1));
        text.set(org.domi.furnace.api.entity.data.EntityDataKeys.TextDisplay.TEXT, Text.text("상점"));
        Player viewer = online("Viewer", 0, 0);
        track();
        List<Packet> out = sent(viewer).stream().filter(x -> !(x instanceof ClientboundSetEntityDataPacket d) || d.entityId() == text.entityId()).toList();
        int start = kinds(out).indexOf(PacketTypes.Clientbound.ADD_ENTITY);
        assertTrue(start > 0);
        assertEquals(List.of(PacketTypes.Clientbound.BUNDLE_DELIMITER, PacketTypes.Clientbound.ADD_ENTITY, PacketTypes.Clientbound.SET_ENTITY_DATA,
                PacketTypes.Clientbound.ROTATE_HEAD, PacketTypes.Clientbound.BUNDLE_DELIMITER), kinds(out).subList(start - 1, start + 4));
    }

    /** 보낼 수 없는 값은 set 에서 바로 거부하고 저장하지 않는다 — 쌓아 두면 나중에 보는 사람이 이름표까지 잃는다. 다른 부류의 칸도 거부. */
    @Test
    void unencodableValueAndForeignOwnerKeyAreRejectedAtSet() {
        VirtualEntity cat = host.virtualEntities().spawn(a, "cat", new Location(WORLD, 1, 64, 1));
        cat.setCustomName(Text.text("나비"));
        assertThrows(IllegalArgumentException.class, () -> cat.set(org.domi.furnace.api.entity.data.EntityDataKeys.Cat.VARIANT, "minecraft:nope"));
        assertThrows(IllegalArgumentException.class, () -> cat.setMetadata(19,
                ClientboundSetEntityDataPacket.Value.of(org.domi.furnace.api.entity.data.EntityDataType.CAT_VARIANT, "minecraft:nope")));
        VirtualEntity box = host.virtualEntities().spawn(a, "interaction", new Location(WORLD, 2, 64, 1));
        assertThrows(IllegalArgumentException.class, () -> box.set(org.domi.furnace.api.entity.data.EntityDataKeys.LivingEntity.HEALTH, 5f),
                "interaction 9번은 height — 번호·값 종류가 맞아도 살아 있는 엔티티가 아니다");
        Player late = online("Late", 0, 0);
        track();
        var data = sent(late).stream().filter(x -> x instanceof ClientboundSetEntityDataPacket d && d.entityId() == cat.entityId())
                .map(x -> (ClientboundSetEntityDataPacket) x).findFirst();
        assertTrue(data.isPresent(), "새로 보는 사람에게 메타데이터 묶음이 간다");
        assertEquals(java.util.Optional.of(java.util.Optional.of(Text.text("나비"))), data.flatMap(d -> d.get(org.domi.furnace.api.entity.data.EntityDataKeys.Entity.CUSTOM_NAME)));
    }

    @Test
    void unknownEntityTypeAndBadNameAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> host.virtualEntities().spawn(a, "nope:thing", new Location(WORLD, 0, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> host.virtualEntities().spawn(a, "minecraft:player", new Location(WORLD, 0, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> host.virtualEntities().spawnPlayer(a, "", List.of(), new Location(WORLD, 0, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> host.virtualEntities().spawnPlayer(a, "x".repeat(17), List.of(), new Location(WORLD, 0, 0, 0)));
        assertEquals(0, host.virtualEntities().entities(a).size(), "실패한 것은 남지 않는다");
    }

    @Test
    void clickIsCaughtAndNotHandledByTheServer() throws Exception {
        Player p = online("P", 0, 0);
        VirtualEntity e = host.virtualEntities().spawn(a, "villager", new Location(WORLD, 1, 64, 1));
        sent(p);
        AtomicReference<VirtualInteraction> got = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        e.onInteract(i -> { got.set(i); latch.countDown(); });
        PacketTap tap = p.channel().attr(PacketTap.KEY).get();

        // 보조 손 우클릭·INTERACT_AT 은 서버가 막기만 하고 처리기는 안 부른다
        assertNull(tap.inbound(codecId(ServerboundInteractPacket.interact(e.entityId(), Hand.OFF_HAND, false)), body(ServerboundInteractPacket.interact(e.entityId(), Hand.OFF_HAND, false)), false));
        var at = new ServerboundInteractPacket(e.entityId(), ServerboundInteractPacket.Action.INTERACT_AT, Hand.MAIN_HAND, 0, 1, 0, false);
        assertNull(tap.inbound(codecId(at), body(at), false));
        assertFalse(latch.await(200, TimeUnit.MILLISECONDS));
        // 주 손 우클릭은 처리기로
        var ia = ServerboundInteractPacket.interact(e.entityId(), Hand.MAIN_HAND, true);
        assertNull(tap.inbound(codecId(ia), body(ia), false));
        assertTrue(latch.await(5, TimeUnit.SECONDS), "처리기가 불리지 않았다");
        assertEquals(e, got.get().entity());
        assertTrue(got.get().isInteract());
        assertTrue(got.get().sneaking());
        // 다른 번호는 건드리지 않는다
        var other = ServerboundInteractPacket.attack(e.entityId() + 100000, false);
        ByteBuf ob = body(other);
        assertSame(ob, tap.inbound(codecId(other), ob, false));
    }

    private int codecId(ServerboundInteractPacket p) { return codec.ids(PacketTypes.Serverbound.INTERACT)[0]; }

    private ByteBuf body(ServerboundInteractPacket p) {
        ByteBuf b = Unpooled.buffer();
        codec.encode(p, b);
        return b;
    }

    @Test
    void fakeTabEntriesAreSentUpdatedAndRemovedWithThePlugin() {
        Player p = online("P", 0, 0);
        VirtualTabList tl = host.virtualEntities().tabList(host.player(p));
        VirtualTabList.Entry e = tl.add(a, "Server", List.of(), GameMode.CREATIVE, 5, Text.text("서버 정보"));
        VirtualTabList.Entry keep = tl.add(b, "Other", List.of(), GameMode.SURVIVAL, 0, null);
        var add = (ClientboundPlayerInfoUpdatePacket) sent(p).get(0);
        assertTrue(add.entries().get(0).listed());
        e.setDisplayName(Text.text("새 이름"));
        e.setLatency(300);
        e.setGameMode(GameMode.SPECTATOR);
        assertEquals(3, sent(p).size());
        host.virtualImpl().removeAll(a);
        var rem = (ClientboundPlayerInfoRemovePacket) sent(p).get(0);
        assertEquals(List.of(e.uuid()), rem.uuids());
        assertTrue(e.isRemoved());
        assertFalse(keep.isRemoved());
        assertEquals(1, tl.entries().size());
        assertThrows(IllegalArgumentException.class, () -> tl.add(a, "x".repeat(17), List.of(), null, 0, null));
    }
}
