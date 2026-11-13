// 패킷 API: 1.21.1 id 표가 서버 패킷 클래스와 맞는지, 타입 래퍼 왕복, 보내기·가로채기(바꾸기·취소·관찰)·받은 것처럼 넣기·등록 해제.
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
import java.util.concurrent.atomic.AtomicInteger;
import org.domi.furnace.api.entity.Hand;
import org.domi.furnace.api.event.EventPriority;
import org.domi.furnace.api.event.EventSubscription;
import org.domi.furnace.api.packet.ClientboundPacket;
import org.domi.furnace.api.packet.GenericPacket;
import org.domi.furnace.api.packet.Packet;
import org.domi.furnace.api.packet.PacketDirection;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.packet.ServerboundPacket;
import org.domi.furnace.api.packet.clientbound.*;
import org.domi.furnace.api.packet.serverbound.*;
import org.domi.furnace.api.text.NamedTextColor;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockFace;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.api.world.SoundCategory;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.PacketTap;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.play.BlockUpdate;
import org.domi.furnace.server.net.protocol.play.GameEvent;
import org.domi.furnace.server.net.protocol.play.SystemChat;
import org.domi.furnace.server.plugin.packet.ProtocolCodec;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PacketApiTest {

    private static final org.domi.furnace.server.world.BlockRegistry BLOCKS = org.domi.furnace.server.world.BlockRegistry.load("blocks/1.21.1.txt");

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private PluginHost host;
    private ProtocolCodec codec;
    private final TestPlugins.Gamma a = new TestPlugins.Gamma();
    private final TestPlugins.Gamma b = new TestPlugins.Gamma();
    private final java.util.Map<Player, EmbeddedChannel> channels = new java.util.HashMap<>();

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

    private Player online(String name) {
        EmbeddedChannel ch = new EmbeddedChannel();
        Player p = new Player(core.world().entityIds().next(), UUID.randomUUID(), name, ch);
        channels.put(p, ch);
        core.world().playerOnline(p);
        host.packetsImpl().attach(p);
        return p;
    }

    /** 보낸 프레임들([VarInt id][본문]) 바이트. */
    private List<byte[]> sent(Player p) {
        EmbeddedChannel ch = channels.get(p);
        ch.runPendingTasks();
        List<byte[]> out = new ArrayList<>();
        Object o;
        while ((o = ch.readOutbound()) != null) {
            ByteBuf bb = (ByteBuf) o;
            out.add(ByteBufUtil.getBytes(bb));
            bb.release();
        }
        return out;
    }

    private static byte[] frame(org.domi.furnace.server.net.protocol.Packet p) {
        ByteBuf b = Unpooled.buffer();
        Types.writeVarInt(b, p.id());
        p.write(b);
        return ByteBufUtil.getBytes(b);
    }

    // ------------------------------------------------------------------ 표·왕복

    @Test
    void idTableMatchesServerPacketClasses() {
        assertEquals(124, PacketTypes.all(PacketDirection.CLIENTBOUND).size(), "1.21.1 서버→클라이언트 play 패킷 124종");
        assertEquals(55, PacketTypes.all(PacketDirection.SERVERBOUND).size(), "클라이언트→서버 58종(이동 넷을 하나로)");
        assertEquals(SystemChat.ID, codec.ids(PacketTypes.Clientbound.SYSTEM_CHAT)[0]);
        assertEquals(BlockUpdate.ID, codec.ids(PacketTypes.Clientbound.BLOCK_UPDATE)[0]);
        assertEquals(org.domi.furnace.server.net.protocol.play.AddEntity.ID, codec.ids(PacketTypes.Clientbound.ADD_ENTITY)[0]);
        assertEquals(org.domi.furnace.server.net.protocol.play.TeleportEntity.ID, codec.ids(PacketTypes.Clientbound.TELEPORT_ENTITY)[0]);
        assertEquals(org.domi.furnace.server.net.protocol.play.SoundPacket.ID, codec.ids(PacketTypes.Clientbound.SOUND)[0]);
        assertEquals(org.domi.furnace.server.net.protocol.play.CustomPayloadPacket.ID_PLAY, codec.ids(PacketTypes.Clientbound.CUSTOM_PAYLOAD)[0]);
        assertEquals(org.domi.furnace.server.net.protocol.play.UpdateTagsPlay.ID, codec.ids(PacketTypes.Clientbound.UPDATE_TAGS)[0]);
        assertEquals(0x7B, codec.ids(PacketTypes.Clientbound.SERVER_LINKS)[0]);
        assertArrayEquals(new int[]{0x1A, 0x1B, 0x1C, 0x1D}, codec.ids(PacketTypes.Serverbound.MOVE_PLAYER));
        assertEquals(0x39, codec.ids(PacketTypes.Serverbound.USE_ITEM)[0]);
        assertEquals(0x16, codec.ids(PacketTypes.Serverbound.INTERACT)[0]);
        for (PacketDirection d : PacketDirection.values()) {
            for (PacketType<?> t : PacketTypes.all(d)) assertTrue(host.packets().isSupported(t), "1.21.1 에 모두 있다: " + t);
        }
        assertSame(PacketTypes.Clientbound.SYSTEM_CHAT, PacketTypes.byName(PacketDirection.CLIENTBOUND, "system_chat"));
        assertNull(PacketTypes.byName(PacketDirection.SERVERBOUND, "system_chat"));
        assertEquals(767, host.packets().protocolVersion());
    }

    private void roundTrip(Packet p) {
        ByteBuf b1 = Unpooled.buffer();
        int id = codec.encode(p, b1);
        PacketType<?> t = p.type().direction() == PacketDirection.CLIENTBOUND ? codec.clientboundType(id) : codec.serverboundType(id);
        assertSame(p.type(), t, "id → 종류");
        byte[] bytes = ByteBufUtil.getBytes(b1);
        ByteBuf in = Unpooled.wrappedBuffer(bytes);
        Packet back = codec.decode(t, id, in);
        assertEquals(0, in.readableBytes(), "본문을 남김없이 읽는다: " + p);
        ByteBuf b2 = Unpooled.buffer();
        assertEquals(id, codec.encode(back, b2));
        assertArrayEquals(bytes, ByteBufUtil.getBytes(b2), "왕복 뒤 같은 바이트: " + p);
    }

    @Test
    void everyTypedPacketRoundTrips() {
        UUID u = UUID.randomUUID();
        List<Packet> all = List.of(
                new ClientboundSystemChatPacket(Text.text("hi", NamedTextColor.RED).append(Text.translatable("chat.type.text", "a", Text.text("b"))), true),
                new ClientboundSetActionBarTextPacket(Text.text("bar")),
                new ClientboundSetTitleTextPacket(Text.text("T")),
                new ClientboundSetSubtitleTextPacket(Text.text("S")),
                new ClientboundDisconnectPacket(Text.text("bye")),
                new ClientboundSetTitlesAnimationPacket(10, 70, 20),
                new ClientboundClearTitlesPacket(true),
                new ClientboundSetTimePacket(1234L, -6000L),
                new ClientboundBlockUpdatePacket(new BlockPos(-5, -60, 300), BlockData.parse("minecraft:oak_stairs[facing=east,half=top]")),
                new ClientboundAddEntityPacket(1000, u, "minecraft:zombie", 1.5, 64, -2.25, 45f, 90f, 90f, 0, 0.5, 0.0, -0.25),
                ClientboundRemoveEntitiesPacket.of(1, 2, 300000),
                new ClientboundTeleportEntityPacket(7, 1, 2, 3, -90f, 22.5f, true),
                new ClientboundSetEntityMotionPacket(7, 0.125, -1, 3.0),
                new ClientboundRotateHeadPacket(7, 180f),
                new ClientboundAnimatePacket(7, ClientboundAnimatePacket.Animation.CRITICAL_HIT),
                new ClientboundSoundPacket("minecraft:entity.experience_orb.pickup", null, SoundCategory.PLAYERS, 1, 2, 3, 0.5f, 1.2f, 99L),
                new ClientboundSoundPacket("mypack:custom.boom", 32f, SoundCategory.AMBIENT, 1, 2, 3, 1f, 1f, 5L),
                new ClientboundCustomPayloadPacket("x:y", new byte[]{1, 2, 3}),
                ClientboundSetEntityDataPacket.of(7,
                        ClientboundSetEntityDataPacket.entry(0, ClientboundSetEntityDataPacket.Value.ofByte(0x22)),
                        ClientboundSetEntityDataPacket.entry(2, ClientboundSetEntityDataPacket.Value.ofOptionalText(Text.text("npc", NamedTextColor.GOLD))),
                        ClientboundSetEntityDataPacket.entry(3, ClientboundSetEntityDataPacket.Value.ofBoolean(true)),
                        ClientboundSetEntityDataPacket.entry(6, ClientboundSetEntityDataPacket.Value.ofPose(5)),
                        ClientboundSetEntityDataPacket.entry(8, ClientboundSetEntityDataPacket.Value.ofFloat(1.5f)),
                        ClientboundSetEntityDataPacket.entry(9, ClientboundSetEntityDataPacket.Value.ofInt(300)),
                        ClientboundSetEntityDataPacket.entry(10, ClientboundSetEntityDataPacket.Value.ofLong(1L << 40)),
                        ClientboundSetEntityDataPacket.entry(11, ClientboundSetEntityDataPacket.Value.ofString("s")),
                        ClientboundSetEntityDataPacket.entry(12, ClientboundSetEntityDataPacket.Value.ofText(Text.text("t"))),
                        ClientboundSetEntityDataPacket.entry(13, ClientboundSetEntityDataPacket.Value.ofOptionalText(null))),
                ClientboundPlayerInfoUpdatePacket.add(u, "Npc", List.of(org.domi.furnace.api.entity.ProfileProperty.textures("dGV4", "sig"), new org.domi.furnace.api.entity.ProfileProperty("x", "y", null)),
                        org.domi.furnace.api.entity.GameMode.CREATIVE, true, 12, Text.text("표시")),
                ClientboundPlayerInfoUpdatePacket.latency(u, 80),
                ClientboundPlayerInfoUpdatePacket.listed(u, false),
                ClientboundPlayerInfoRemovePacket.of(u, UUID.randomUUID()),
                new ServerboundChatPacket("hello", java.time.Instant.ofEpochMilli(5000), 42L, new byte[256], 3, 0b101),
                new ServerboundChatCommandPacket("gamemode creative"),
                new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN),
                new ServerboundClientInformationPacket("ko_kr", 12, ServerboundClientInformationPacket.ChatVisibility.SYSTEM, true, 0x7F,
                        ServerboundClientInformationPacket.MainArm.LEFT, false, true),
                new ServerboundCustomPayloadPacket("x:y", new byte[]{9}),
                ServerboundInteractPacket.attack(55, true),
                ServerboundInteractPacket.interact(55, Hand.OFF_HAND, false),
                new ServerboundInteractPacket(55, ServerboundInteractPacket.Action.INTERACT_AT, Hand.MAIN_HAND, 0.1f, 1.5f, -0.2f, false),
                ServerboundMovePlayerPacket.position(1, 2, 3, true),
                ServerboundMovePlayerPacket.positionRotation(1, 2, 3, 10f, 20f, false),
                ServerboundMovePlayerPacket.rotation(10f, 20f, true),
                ServerboundMovePlayerPacket.statusOnly(false),
                new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, new BlockPos(1, -2, 3), BlockFace.WEST, 17),
                new ServerboundPlayerCommandPacket(9, ServerboundPlayerCommandPacket.Action.START_RIDING_JUMP, 80),
                new ServerboundSetCarriedItemPacket(4),
                new ServerboundSignUpdatePacket(new BlockPos(0, 70, 0), false, List.of("a", "b", "", "d")),
                new ServerboundSwingPacket(Hand.OFF_HAND),
                new ServerboundUseItemPacket(Hand.MAIN_HAND, 3, 90f, -45f),
                new ServerboundUseItemOnPacket(Hand.MAIN_HAND, new BlockPos(1, 2, 3), BlockFace.UP, 0.5f, 1f, 0.25f, false, 8));
        java.util.Set<PacketType<?>> covered = new java.util.HashSet<>();
        for (Packet p : all) {
            roundTrip(p);
            covered.add(p.type());
        }
        for (PacketDirection d : PacketDirection.values()) {
            for (PacketType<?> t : PacketTypes.all(d)) if (t.typed()) assertTrue(covered.contains(t), "왕복 시험이 빠진 타입 래퍼: " + t);
        }
    }

    @Test
    void decodesServerWrittenEntityDataAndPlayerInfo() {
        UUID u = UUID.randomUUID();
        ByteBuf body = Unpooled.buffer();
        new org.domi.furnace.server.net.protocol.play.SetEntityData(9, List.of(
                new org.domi.furnace.server.net.protocol.play.SetEntityData.Item(0, 0, (byte) 0x20),
                new org.domi.furnace.server.net.protocol.play.SetEntityData.Item(9, 3, 4.5f),
                new org.domi.furnace.server.net.protocol.play.SetEntityData.Item(7, 7, org.domi.furnace.server.entity.ItemStack.EMPTY))).write(body);
        var d = (ClientboundSetEntityDataPacket) codec.decode(PacketTypes.Clientbound.SET_ENTITY_DATA, org.domi.furnace.server.net.protocol.play.SetEntityData.ID, body);
        assertEquals(9, d.entityId());
        assertEquals(new ClientboundSetEntityDataPacket.ByteValue((byte) 0x20), d.entries().get(0).value());
        assertEquals(new ClientboundSetEntityDataPacket.FloatValue(4.5f), d.entries().get(1).value());
        assertEquals(org.domi.furnace.api.item.ItemStack.EMPTY, d.entries().get(2).value().asObject(), "아이템 값도 읽는다(E1)");
        assertFalse(d.hasUnsupported());

        // 이 버전이 모르는 직렬화 번호는 길이를 알 수 없어 거기서 멈춘다 — 다시 보낼 수 없다
        ByteBuf unknown = Unpooled.buffer();
        Types.writeVarInt(unknown, 9);
        unknown.writeByte(0);
        Types.writeVarInt(unknown, 0);
        unknown.writeByte(0x20);
        unknown.writeByte(5);
        Types.writeVarInt(unknown, 99);
        unknown.writeBytes(new byte[] {1, 2, 3});
        var un = (ClientboundSetEntityDataPacket) codec.decode(PacketTypes.Clientbound.SET_ENTITY_DATA, org.domi.furnace.server.net.protocol.play.SetEntityData.ID, unknown);
        assertEquals(new ClientboundSetEntityDataPacket.Unsupported(99), un.entries().get(1).value(), "모르는 종류는 거기서 멈춘다");
        assertTrue(un.hasUnsupported());
        assertThrows(IllegalArgumentException.class, () -> codec.encode(un, Unpooled.buffer()), "읽지 못한 값이 있으면 다시 보낼 수 없다");

        ByteBuf info = Unpooled.buffer();
        new org.domi.furnace.server.net.protocol.play.PlayerInfoUpdate(List.of(new org.domi.furnace.server.net.protocol.play.PlayerInfoUpdate.Entry(u, "Bob",
                List.of(new org.domi.furnace.server.players.GameProfile.Property("textures", "v", "sg")), null, 1, 33))).write(info);
        // 서버 쪽 add 는 채팅 세션 초기화 비트를 늘 켠다(없으면 빈 옵션) — 이 래퍼는 그 비트를 읽지 않으므로 거절한다
        assertThrows(IllegalArgumentException.class, () -> codec.decode(PacketTypes.Clientbound.PLAYER_INFO_UPDATE, org.domi.furnace.server.net.protocol.play.PlayerInfoUpdate.ID, info));
    }

    @Test
    void decodesServerPacketsIntoNamedValues() {
        int stone = org.domi.furnace.server.plugin.ApiConvert.stateId(BlockData.of("minecraft:stone"), BLOCKS);
        ByteBuf body = Unpooled.buffer();
        new BlockUpdate(1, 2, 3, stone).write(body);
        var v = (ClientboundBlockUpdatePacket) codec.decode(PacketTypes.Clientbound.BLOCK_UPDATE, BlockUpdate.ID, body);
        assertEquals(new BlockPos(1, 2, 3), v.pos());
        assertEquals(BlockData.of("minecraft:stone"), v.block());
        assertThrows(IllegalArgumentException.class, () -> codec.encode(new ClientboundBlockUpdatePacket(new BlockPos(0, 0, 0), BlockData.of("nope:block")), Unpooled.buffer()));
        assertThrows(IllegalArgumentException.class, () -> codec.encode(GenericPacket.unreadable(PacketTypes.Clientbound.GAME_EVENT), Unpooled.buffer()));
    }

    // ------------------------------------------------------------------ 보내기

    @Test
    void sendPacketWritesSameBytesAsServer() {
        Player p = online("P");
        int stone = org.domi.furnace.server.plugin.ApiConvert.stateId(BlockData.of("minecraft:stone"), BLOCKS);
        host.player(p).sendPacket(new ClientboundBlockUpdatePacket(new BlockPos(4, 5, 6), BlockData.of("stone")));
        host.packets().broadcast(new ClientboundSystemChatPacket(Text.text("all"), false));
        List<byte[]> out = sent(p);
        assertEquals(2, out.size());
        assertArrayEquals(frame(new BlockUpdate(4, 5, 6, stone)), out.get(0));
        assertArrayEquals(frame(new SystemChat(Text.text("all"), false)), out.get(1));
        assertThrows(IllegalArgumentException.class, () -> host.player(p).sendPacket(GenericPacket.of(PacketTypes.Clientbound.GAME_EVENT)));
    }

    // ------------------------------------------------------------------ 가로채기

    @Test
    void outboundListenerCanModifyCancelAndObserve() {
        Player p = online("P");
        List<String> seen = new ArrayList<>();
        host.packets().listen(a, PacketTypes.Clientbound.SYSTEM_CHAT, e -> {
            seen.add("normal:" + e.packet().message().getString());
            if (e.packet().message().getString().equals("secret")) e.setCancelled(true);
            else e.setPacket(e.packet().withMessage(Text.text("[!] " + e.packet().message().getString())));
        });
        host.packets().listen(b, PacketTypes.Clientbound.SYSTEM_CHAT, EventPriority.MONITOR, e -> {
            seen.add("monitor:" + e.packet().message().getString() + (e.isCancelled() ? "(x)" : ""));
            assertThrows(IllegalStateException.class, () -> e.setCancelled(false));
        });
        host.packets().listen(b, PacketTypes.Clientbound.SYSTEM_CHAT, EventPriority.LOWEST, e -> seen.add("lowest"));
        p.send(new SystemChat(Text.text("hello"), false));
        p.send(new SystemChat(Text.text("secret"), false));
        p.send(new org.domi.furnace.server.net.protocol.play.SetTime(1, 2));   // 구독 없음 — 그대로
        List<byte[]> out = sent(p);
        assertEquals(List.of("lowest", "normal:hello", "monitor:[!] hello", "lowest", "normal:secret", "monitor:secret(x)"), seen);
        assertEquals(2, out.size());
        assertArrayEquals(frame(new SystemChat(Text.text("[!] hello"), false)), out.get(0));
        assertArrayEquals(frame(new org.domi.furnace.server.net.protocol.play.SetTime(1, 2)), out.get(1));
    }

    @Test
    void unreadablePacketsCanBeObservedAndCancelledButNotChanged() {
        Player p = online("P");
        AtomicInteger n = new AtomicInteger();
        host.packets().listen(a, PacketTypes.Clientbound.LEVEL_PARTICLES, e -> {   // 본문이 깨져 범용 코덱이 못 읽는 패킷
            n.incrementAndGet();
            assertFalse(e.packet().readable());
            assertThrows(UnsupportedOperationException.class, () -> e.setPacket(e.packet()));
            e.setCancelled(true);
        });
        p.send(new org.domi.furnace.server.net.protocol.RawPacket(codec.ids(PacketTypes.Clientbound.LEVEL_PARTICLES)[0], new byte[]{0}, null, false));
        assertEquals(List.of(), sent(p));
        assertEquals(1, n.get());
    }

    @Test
    void pluginSentPacketsAreSeenUnlessSilent() {
        Player p = online("P");
        List<Boolean> plugin = new ArrayList<>();
        host.packets().listen(a, PacketTypes.Clientbound.SET_TITLE_TEXT, e -> plugin.add(e.isPluginPacket()));
        host.packets().send(host.player(p), new ClientboundSetTitleTextPacket(Text.text("t")));
        host.packets().sendSilently(host.player(p), new ClientboundSetTitleTextPacket(Text.text("t")));
        assertEquals(2, sent(p).size());
        assertEquals(List.of(true), plugin);
    }

    @Test
    void listenerErrorsDoNotBreakTheConnection() {
        Player p = online("P");
        host.packets().listen(a, PacketTypes.Clientbound.SYSTEM_CHAT, e -> { throw new RuntimeException("시험 예외"); });
        host.packets().listen(b, PacketTypes.Clientbound.SYSTEM_CHAT, e -> e.setPacket(e.packet().withMessage(Text.text("b"))));
        assertThrows(IllegalArgumentException.class, () -> host.packets().listen(a, EventPriority.NORMAL, e -> { }));
        p.send(new SystemChat(Text.text("a"), false));
        assertArrayEquals(frame(new SystemChat(Text.text("b"), false)), sent(p).get(0));
    }

    @Test
    void unsubscribeAndPluginDisableRemoveListeners() {
        Player p = online("P");
        AtomicInteger n = new AtomicInteger();
        EventSubscription s = host.packets().listen(a, PacketTypes.Clientbound.SYSTEM_CHAT, e -> n.incrementAndGet());
        host.packets().listen(b, EventPriority.NORMAL, e -> n.addAndGet(100), PacketTypes.Clientbound.SYSTEM_CHAT, PacketTypes.Clientbound.SET_TIME);
        p.send(new SystemChat(Text.text("1"), false));
        p.send(new org.domi.furnace.server.net.protocol.play.SetTime(1, 2));
        sent(p);
        assertEquals(201, n.get());
        s.unsubscribe();
        host.packetsImpl().unregisterAll(b);
        p.send(new SystemChat(Text.text("2"), false));
        assertEquals(1, sent(p).size());
        assertEquals(201, n.get());
    }

    @Test
    void inboundTapModifiesAndCancels() {
        Player p = online("P");
        PacketTap tap = p.channel().attr(PacketTap.KEY).get();
        host.packets().listen(a, PacketTypes.Serverbound.MOVE_PLAYER, e -> {
            if (!e.packet().hasPosition()) e.setCancelled(true);
            else e.setPacket(ServerboundMovePlayerPacket.position(e.packet().x(), e.packet().y() + 1, e.packet().z(), e.packet().onGround()));
        });
        ByteBuf rot = Unpooled.buffer();
        int rotId = codec.encode(ServerboundMovePlayerPacket.rotation(1, 2, true), rot);
        assertNull(tap.inbound(rotId, rot, false), "취소");
        ByteBuf pos = Unpooled.buffer();
        int posId = codec.encode(ServerboundMovePlayerPacket.position(1, 2, 3, false), pos);
        ByteBuf repl = tap.inbound(posId, pos, false);
        assertNotSame(pos, repl);
        int id = Types.readVarInt(repl);
        assertEquals(posId, id);
        var v = (ServerboundMovePlayerPacket) codec.decode(PacketTypes.Serverbound.MOVE_PLAYER, id, repl);
        assertEquals(3.0, v.y());
        repl.release();
        ByteBuf swing = Unpooled.buffer();
        int swingId = codec.encode(new ServerboundSwingPacket(Hand.MAIN_HAND), swing);
        assertSame(swing, tap.inbound(swingId, swing, false), "구독 없는 종류는 그대로");
        ByteBuf bad = Unpooled.wrappedBuffer(new byte[]{1});
        assertSame(bad, tap.inbound(posId, bad, false), "읽을 수 없는 패킷은 서버 본래 처리에 맡긴다");
    }

    @Test
    void sendRejectsWrongDirectionAndOpaque() {
        Player p = online("P");
        var api = host.player(p);
        assertThrows(IllegalArgumentException.class, () -> host.packets().send(api, GenericPacket.of(PacketTypes.Serverbound.PONG)), "방향이 다르다");
        assertThrows(IllegalArgumentException.class, () -> host.packets().receive(api, GenericPacket.unreadable(PacketTypes.Serverbound.PONG)), "내용 없는 패킷");
        assertThrows(IllegalArgumentException.class, () -> host.packets().receive(api, GenericPacket.of(PacketTypes.Clientbound.PING)), "방향이 다르다");
    }

    // ------------------------------------------------------------------ 번들 (E2)

    private static final byte[] DELIMITER = {0x00};

    @Test
    void sendBundleWrapsPacketsInDelimitersAndListenersSeeEachOne() {
        Player p = online("P");
        List<String> seen = new ArrayList<>();
        host.packets().listen(a, PacketTypes.Clientbound.SET_TITLE_TEXT, e -> {
            seen.add(e.packet().text().getString());
            if (e.packet().text().getString().equals("x")) e.setCancelled(true);   // 안의 하나만 빠진다
        });
        var api = host.player(p);
        host.packets().sendBundle(api, List.of(new ClientboundSetTitleTextPacket(Text.text("a")), new ClientboundSetTitleTextPacket(Text.text("x")),
                new ClientboundSetTitleTextPacket(Text.text("b"))));
        List<byte[]> out = sent(p);
        assertEquals(List.of("a", "x", "b"), seen);
        assertEquals(4, out.size());
        assertArrayEquals(DELIMITER, out.get(0));
        assertArrayEquals(DELIMITER, out.get(3));
        assertArrayEquals(frame(host.packetsImpl().encode(new ClientboundSetTitleTextPacket(Text.text("a")))), out.get(1));
        assertArrayEquals(frame(host.packetsImpl().encode(new ClientboundSetTitleTextPacket(Text.text("b")))), out.get(2));
        host.packets().sendBundle(api, List.of(new ClientboundSetTitleTextPacket(Text.text("c"))));
        assertEquals(3, sent(p).size());
        host.packets().sendBundleSilently(api, List.of(new ClientboundSetTitleTextPacket(Text.text("x"))));
        assertEquals(3, sent(p).size(), "리스너를 거치지 않으면 취소도 없다");
        assertEquals(List.of("a", "x", "b", "c"), seen);
    }

    @Test
    void sendBundleIsAllOrNothingAndChecked() {
        Player p = online("P");
        var api = host.player(p);
        // 둘째가 인코딩되지 않으면(없는 고양이 종류) 첫째도 보내지 않는다
        assertThrows(IllegalArgumentException.class, () -> host.packets().sendBundle(api, List.of(new ClientboundSetTitleTextPacket(Text.text("a")),
                ClientboundSetEntityDataPacket.of(1, ClientboundSetEntityDataPacket.entry(19,
                        ClientboundSetEntityDataPacket.Value.of(org.domi.furnace.api.entity.data.EntityDataType.CAT_VARIANT, "minecraft:nope"))))));
        var delim = assertThrows(IllegalArgumentException.class, () -> host.packets().sendBundle(api, List.of(GenericPacket.of(PacketTypes.Clientbound.BUNDLE_DELIMITER))));
        assertTrue(delim.getMessage().contains("구분자"), delim.getMessage());
        var many = java.util.Collections.nCopies(org.domi.furnace.api.packet.PacketService.BUNDLE_MAX + 1, (org.domi.furnace.api.packet.ClientboundPacket) new ClientboundSetTitleTextPacket(Text.text("a")));
        assertThrows(IllegalArgumentException.class, () -> host.packets().sendBundle(api, many), "바닐라 클라이언트 상한");
        host.packets().sendBundle(api, List.of());   // 빈 번들은 아무것도 보내지 않는다
        assertEquals(List.of(), sent(p));
    }

    /** 서버 Player.sendBundle: 변장 등으로 상한 가까이 늘면 묶음을 나눈다 — 한 번들이 바닐라 상한을 넘지 않는다. */
    @Test
    void serverBundleSplitsBeforeVanillaLimit() {
        Player p = online("P");
        int n = org.domi.furnace.server.entity.Player.BUNDLE_MAX + 10;
        List<org.domi.furnace.server.net.protocol.Packet> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(new org.domi.furnace.server.net.protocol.play.SetTime(i, i));
        p.sendBundle(list);
        List<byte[]> out = sent(p);
        int inBundle = 0, bundles = 0, max = 0;
        boolean open = false;
        for (byte[] f : out) {
            if (java.util.Arrays.equals(f, DELIMITER)) {
                if (open) { bundles++; max = Math.max(max, inBundle); }
                open = !open;
                inBundle = 0;
            } else {
                assertTrue(open, "번들 밖 패킷이 없다");
                inBundle++;
            }
        }
        assertFalse(open);
        assertEquals(2, bundles);
        assertTrue(max <= org.domi.furnace.server.entity.Player.BUNDLE_MAX, "번들 하나 " + max);
        assertEquals(n + 4, out.size());
    }

    private static String shape(List<byte[]> out) {
        StringBuilder sb = new StringBuilder();
        for (byte[] f : out) sb.append(java.util.Arrays.equals(f, DELIMITER) ? "D" : "p");
        return sb.toString();
    }

    /** 구분자는 리스너를 거치지 않는다 — 구분자 리스너가 취소해도 짝이 맞고, silent 번들은 어떤 리스너도 부르지 않는다(검토 R1). */
    @Test
    void bundleDelimitersBypassListeners() {
        Player p = online("P");
        AtomicInteger seen = new AtomicInteger();
        host.packets().listen(a, PacketTypes.Clientbound.BUNDLE_DELIMITER, e -> {
            seen.incrementAndGet();
            e.setCancelled(true);
        });
        var api = host.player(p);
        // EmbeddedChannel 은 쓰기 도중 대기 작업을 돌려 버리므로(실제 이벤트 루프는 아니다) 번들마다 읽는다
        host.packets().sendBundleSilently(api, List.of(new ClientboundSetTitleTextPacket(Text.text("a")), new ClientboundSetTitleTextPacket(Text.text("b"))));
        assertEquals("DppD", shape(sent(p)));
        host.packets().sendBundle(api, List.of(new ClientboundSetTitleTextPacket(Text.text("c"))));
        assertEquals("DpD", shape(sent(p)));
        assertEquals(0, seen.get());
    }

    /** 번들 도중 쓰기가 예외를 내면 아무것도 쓰지 않는다 — 구분자가 열린 채 남지 않는다(검토 R2). */
    @Test
    void bundleWriteFailureWritesNothing() {
        Player p = online("P");
        org.domi.furnace.server.net.protocol.Packet bad = new org.domi.furnace.server.net.protocol.Packet() {
            @Override public int id() { return 0x68; }
            @Override public void write(ByteBuf buf) { throw new IllegalStateException("쓰기 실패"); }
        };
        p.sendBundle(List.of(new org.domi.furnace.server.net.protocol.play.SetTime(1, 1), bad, new org.domi.furnace.server.net.protocol.play.SetTime(2, 2)));
        String s;
        try {
            s = shape(sent(p));
        } catch (IllegalStateException e) {   // EmbeddedChannel 은 작업 예외를 runPendingTasks 에서 다시 던진다
            s = shape(sent(p));
        }
        assertEquals("", s);
        p.send(new org.domi.furnace.server.net.protocol.play.SetTime(3, 3));
        assertEquals("p", shape(sent(p)), "연결은 계속 쓸 수 있다");
    }

    /** API 상한(4096)인 번들은 쪼개지 않고, 모두 취소된 번들은 보내지 않는다(검토 R3·R4). */
    @Test
    void fullBundleIsNotSplitAndEmptyBundleIsNotSent() {
        Player p = online("P");
        var api = host.player(p);
        var many = java.util.Collections.nCopies(org.domi.furnace.api.packet.PacketService.BUNDLE_MAX, (org.domi.furnace.api.packet.ClientboundPacket) new ClientboundSetTitleTextPacket(Text.text("a")));
        host.packets().sendBundle(api, many);
        String s = shape(sent(p));
        assertEquals(2, s.chars().filter(c -> c == 'D').count(), "상한 이하 번들은 한 번들");
        assertEquals(org.domi.furnace.api.packet.PacketService.BUNDLE_MAX + 2, s.length());
        host.packets().listen(a, PacketTypes.Clientbound.SET_TITLE_TEXT, e -> e.setCancelled(true));
        host.packets().sendBundle(api, List.of(new ClientboundSetTitleTextPacket(Text.text("x"))));
        assertEquals("", shape(sent(p)), "모두 취소되면 빈 번들도 보내지 않는다");
    }
}
