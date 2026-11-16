// N3 1단계 패킷 등록부 shim: 패킷 라이브러리(ProtocolLib)가 리플렉션으로 읽는 모양을 그대로 따라 읽어 본다 —
// *Protocols 의 static final 틀 → bind → codec(IdDispatchCodec) → byId 목록의 Entry(type·serializer) → *PacketTypes 필드(제네릭 인자 = 패킷 클래스).
// 번호·이름은 번호 표(furnace-api ProtocolIds)·스키마와 대조한다. 진짜 ProtocolLib 5.4.0 jar 로는 docs/feature-parity/compat-protocollib-registry-2026-11-12.md 의 방법으로 확인.
package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.IdDispatchCodec;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerConnectionListener;
import org.domi.furnace.api.packet.PacketDirection;
import org.domi.furnace.api.packet.schema.PacketDef;
import org.domi.furnace.api.packet.schema.PacketSchema;
import org.domi.furnace.api.packet.schema.ProtocolIds;
import org.junit.jupiter.api.Test;

class PacketRegistryShimTest {

    private static final ProtocolIds IDS = ProtocolIds.protocol767();
    private static final PacketSchema SCHEMA = PacketSchema.protocol767();
    private static final String PKG = "net.minecraft.network.protocol.";

    private static String cap(String s) { return Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    private static ConnectionProtocol connectionProtocol(String protocol) {
        return switch (protocol) {
            case "game" -> ConnectionProtocol.PLAY;
            case "handshake" -> ConnectionProtocol.HANDSHAKING;
            case "status" -> ConnectionProtocol.STATUS;
            case "login" -> ConnectionProtocol.LOGIN;
            case "configuration" -> ConnectionProtocol.CONFIGURATION;
            default -> throw new IllegalArgumentException(protocol);
        };
    }

    /** ProtocolLib 이 하는 대로: Class 이름으로 찾고, static final 필드 중 Unbound 인 값을 모은다. */
    private static List<ProtocolInfo.Unbound<?, ?>> unboundTemplates(String protocol) throws Exception {
        Class<?> c = Class.forName(PKG + protocol + "." + cap(protocol) + "Protocols");
        List<ProtocolInfo.Unbound<?, ?>> out = new ArrayList<>();
        for (Field f : c.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) || !Modifier.isFinal(f.getModifiers())) continue;
            f.setAccessible(true);
            if (f.get(null) instanceof ProtocolInfo.Unbound<?, ?> u) out.add(u);
        }
        return out;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<Object> byId(ProtocolInfo<?> bound) throws Exception {
        Object codec = bound.codec();
        assertInstanceOf(IdDispatchCodec.class, codec);
        for (Field f : IdDispatchCodec.class.getDeclaredFields()) {
            if (List.class.isAssignableFrom(f.getType())) {   // ProtocolLib: List 에서 파생된 유일한 필드
                f.setAccessible(true);
                return (List<Object>) f.get(codec);
            }
        }
        return fail("byId 목록 필드가 없다");
    }

    private static Object entryField(Object entry, String accessor) throws Exception {
        Method m = entry.getClass().getDeclaredMethod(accessor);
        m.setAccessible(true);
        return m.invoke(entry);
    }

    /** *PacketTypes 의 static final PacketType 필드: 값 → (필드 이름, 제네릭 인자 = 패킷 클래스). */
    private static java.util.Map<Object, Class<?>> packetTypeClasses(String holder) throws Exception {
        Class<?> c = Class.forName(PKG + holder + "." + cap(holder) + "PacketTypes");
        java.util.Map<Object, Class<?>> out = new java.util.IdentityHashMap<>();
        for (Field f : c.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) || !Modifier.isFinal(f.getModifiers()) || f.getType() != PacketType.class) continue;
            Type arg = ((ParameterizedType) f.getGenericType()).getActualTypeArguments()[0];
            out.put(f.get(null), arg instanceof Class<?> k ? k : (Class<?>) ((ParameterizedType) arg).getRawType());
        }
        return out;
    }

    @Test
    void connectionProtocolAndFlowMatchVanillaOrder() {
        assertArrayEquals(new String[] {"HANDSHAKING", "PLAY", "STATUS", "LOGIN", "CONFIGURATION"},
                java.util.Arrays.stream(ConnectionProtocol.values()).map(Enum::name).toArray(String[]::new));
        assertEquals("play", ConnectionProtocol.PLAY.id());
        assertEquals(PacketFlow.SERVERBOUND, PacketFlow.values()[0]);
        assertEquals(PacketFlow.CLIENTBOUND, PacketFlow.SERVERBOUND.getOpposite());
        assertEquals("clientbound", PacketFlow.CLIENTBOUND.id());
    }

    @Test
    void everyProtocolRegistryMatchesTheIdTable() throws Exception {
        int total = 0;
        for (String protocol : IDS.protocols()) {
            List<ProtocolInfo.Unbound<?, ?>> templates = unboundTemplates(protocol);
            assertEquals(protocol.equals("handshake") ? 1 : 2, templates.size(), protocol + " 의 방향별 틀 수");
            for (ProtocolInfo.Unbound<?, ?> u : templates) {
                assertEquals(connectionProtocol(protocol), u.id());
                PacketDirection d = u.flow() == PacketFlow.CLIENTBOUND ? PacketDirection.CLIENTBOUND : PacketDirection.SERVERBOUND;
                List<ProtocolIds.Entry> expected = IDS.entries(protocol, d);

                @SuppressWarnings({"unchecked", "rawtypes"})
                ProtocolInfo<?> bound = ((ProtocolInfo.Unbound) u).bind(Function.identity());
                assertEquals(u.id(), bound.id());
                assertEquals(u.flow(), bound.flow());
                List<Object> entries = byId(bound);
                assertEquals(expected.size(), entries.size(), protocol + " " + d);

                List<PacketType<?>> listed = new ArrayList<>();
                u.listPackets((type, id) -> { assertEquals(listed.size(), id); listed.add(type); });
                assertEquals(expected.size(), listed.size());

                for (int i = 0; i < expected.size(); i++) {
                    ProtocolIds.Entry e = expected.get(i);
                    PacketType<?> type = (PacketType<?>) entryField(entries.get(i), "type");
                    assertSame(listed.get(i), type, "번호 " + i);
                    assertEquals(d == PacketDirection.CLIENTBOUND ? PacketFlow.CLIENTBOUND : PacketFlow.SERVERBOUND, type.flow());
                    assertEquals(ResourceLocation.withDefaultNamespace(e.wireName()), type.id(), protocol + " " + d + " #" + i);
                    assertInstanceOf(StreamCodec.class, entryField(entries.get(i), "serializer"));

                    // 종류 상수는 그 holder 의 *PacketTypes 필드와 같은 객체이고, 제네릭 인자가 스키마의 패킷 클래스다
                    Class<?> packetClass = packetTypeClasses(e.holder()).get(type);
                    assertNotNull(packetClass, e.holder() + ":" + e.wireName() + " 가 " + cap(e.holder()) + "PacketTypes 필드가 아니다");
                    PacketDef def = SCHEMA.packet(e.holder(), d, e.wireName()).orElseThrow();
                    assertEquals(def.binaryName(), packetClass.getName(), e.holder() + ":" + e.wireName());
                }
                total += expected.size();
            }
        }
        assertEquals(223, total);
    }

    @Test
    void packetTypesDeclareEverySchemaPacketOnce() throws Exception {
        int n = 0;
        for (String holder : IDS.types().stream().map(ProtocolIds.Type::holder).distinct().toList()) {
            n += packetTypeClasses(holder).size();
        }
        assertEquals(IDS.types().size(), n);
        assertEquals(202, n);
        // 번들 종류(번호 없음)도 필드로 있다
        assertEquals("clientbound/minecraft:bundle", net.minecraft.network.protocol.game.GamePacketTypes.CLIENTBOUND_BUNDLE.toString());
        assertEquals("serverbound/minecraft:intention", net.minecraft.network.protocol.handshake.HandshakePacketTypes.SERVERBOUND_INTENTION.toString());
    }

    @Test
    void idDispatchCodecKeepsPositionsAndRejectsDuplicates() {
        PacketType<?> a = new PacketType<>(PacketFlow.CLIENTBOUND, ResourceLocation.withDefaultNamespace("a"));
        PacketType<?> b = new PacketType<>(PacketFlow.CLIENTBOUND, ResourceLocation.withDefaultNamespace("b"));
        IdDispatchCodec.Builder<ByteBuf, Object, Object> builder = IdDispatchCodec.builder(x -> null);
        StreamCodec<ByteBuf, Object> sc = StreamCodec.unit("x");
        builder.add(a, sc).add(b, sc);
        IdDispatchCodec<ByteBuf, Object, Object> codec = builder.build();
        assertEquals(0, codec.idOf(a));
        assertEquals(1, codec.idOf(b));
        assertEquals(-1, codec.idOf("nope"));
        assertSame(b, codec.typeOf(1));
        assertNull(codec.typeOf(2));
        assertThrows(IllegalStateException.class, () -> IdDispatchCodec.<ByteBuf, Object, Object>builder(x -> null).add(a, sc).add(a, sc).build());
        // 패킷 바이트 변환은 아직 없다 — 지어내지 않고 던진다
        assertThrows(UnsupportedNmsCall.class, () -> codec.encode(Unpooled.buffer(), "x"));
        assertThrows(UnsupportedNmsCall.class, () -> codec.decode(Unpooled.buffer()));
    }

    @Test
    void shimProtocolsSerializersThrowInsteadOfInventingBytes() throws Exception {
        ProtocolInfo.Unbound<?, ?> game = unboundTemplates("game").get(0);
        @SuppressWarnings({"unchecked", "rawtypes"})
        ProtocolInfo<?> bound = ((ProtocolInfo.Unbound) game).bind(Function.identity());
        Object serializer = entryField(byId(bound).get(1), "serializer");
        @SuppressWarnings({"unchecked", "rawtypes"})
        StreamCodec<ByteBuf, Object> sc = (StreamCodec) serializer;
        assertThrows(UnsupportedNmsCall.class, () -> sc.decode(Unpooled.buffer()));
        assertThrows(UnsupportedNmsCall.class, () -> sc.encode(Unpooled.buffer(), "x"));
    }

    @Test
    void serverConnectionListenerHasAReplaceableChannelFutureList() throws Exception {
        ServerConnectionListener conn = MinecraftServer.getServer().getConnection();
        assertSame(conn, MinecraftServer.getServer().getConnection());
        // ProtocolLib: List 파생 필드 중 제네릭 이름에 ChannelFuture 가 든 것을 찾아 갈아 끼운다
        Field channels = null;
        for (Field f : ServerConnectionListener.class.getDeclaredFields()) {
            if (List.class.isAssignableFrom(f.getType()) && !Modifier.isStatic(f.getModifiers()) && f.getGenericType().getTypeName().contains(ChannelFuture.class.getName())) channels = f;
        }
        assertNotNull(channels);
        channels.setAccessible(true);
        List<?> original = (List<?>) channels.get(conn);
        assertTrue(original.isEmpty(), "지금은 실제 소켓 채널을 내보내지 않는다");
        List<ChannelFuture> replacement = new ArrayList<>();
        channels.set(conn, replacement);
        assertSame(replacement, channels.get(conn));
        channels.set(conn, original);
    }

    @Test
    void friendlyByteBufWritesTheProtocolValueFormats() {
        ByteBuf raw = Unpooled.buffer();
        FriendlyByteBuf buf = new FriendlyByteBuf(raw);
        UUID id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        buf.writeVarInt(0).writeVarInt(127).writeVarInt(128).writeVarInt(-1).writeVarInt(Integer.MAX_VALUE)
                .writeVarLong(-1L).writeVarLong(300L).writeUtf("héllo 월드").writeUUID(id).writeByteArray(new byte[] {1, 2, 3})
                .writeBlockPos(new BlockPos(-5, 70, 12345)).writeResourceLocation(ResourceLocation.withDefaultNamespace("stone"))
                .writeVarIntArray(new int[] {1, 300}).writeLongArray(new long[] {7L, -7L}).writeEnum(PacketFlow.CLIENTBOUND);
        // 바닐라 와이어와 같은 바이트: 128 → 0x80 0x01, -1 → ff ff ff ff 0f
        ByteBuf probe = Unpooled.buffer();
        new FriendlyByteBuf(probe).writeVarInt(128).writeVarInt(-1);
        assertArrayEquals(new byte[] {(byte) 0x80, 0x01, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, 0x0f}, bytes(probe));
        assertEquals(1, FriendlyByteBuf.getVarIntSize(127));
        assertEquals(2, FriendlyByteBuf.getVarIntSize(128));
        assertEquals(5, FriendlyByteBuf.getVarIntSize(-1));
        assertEquals(10, FriendlyByteBuf.getVarLongSize(-1L));

        assertEquals(0, buf.readVarInt());
        assertEquals(127, buf.readVarInt());
        assertEquals(128, buf.readVarInt());
        assertEquals(-1, buf.readVarInt());
        assertEquals(Integer.MAX_VALUE, buf.readVarInt());
        assertEquals(-1L, buf.readVarLong());
        assertEquals(300L, buf.readVarLong());
        assertEquals("héllo 월드", buf.readUtf());
        assertEquals(id, buf.readUUID());
        assertArrayEquals(new byte[] {1, 2, 3}, buf.readByteArray());
        BlockPos pos = buf.readBlockPos();
        assertEquals(-5, pos.getX());
        assertEquals(70, pos.getY());
        assertEquals(12345, pos.getZ());
        assertEquals(ResourceLocation.withDefaultNamespace("stone"), buf.readResourceLocation());
        assertArrayEquals(new int[] {1, 300}, buf.readVarIntArray());
        assertArrayEquals(new long[] {7L, -7L}, buf.readLongArray());
        assertEquals(PacketFlow.CLIENTBOUND, buf.readEnum(PacketFlow.class));
        assertEquals(0, buf.readableBytes());
        // 위임: 읽기·쓰기 위치는 안쪽 버퍼와 같다
        assertEquals(raw.readerIndex(), buf.readerIndex());
        assertEquals(raw.writerIndex(), buf.writerIndex());
    }

    @Test
    void friendlyByteBufDelegatesAndDoesNotWrapDerivedBuffers() {
        ByteBuf raw = Unpooled.buffer();
        FriendlyByteBuf buf = new FriendlyByteBuf(raw);
        buf.writeInt(0x01020304).writeByte(9);
        assertEquals(5, raw.writableBytes() >= 0 ? raw.readableBytes() : -1);
        assertSame(buf, buf.writeShort(1), "체이닝은 감싼 객체를 돌려준다");
        // 바닐라(vw.copy)처럼 안쪽 ByteBuf 를 감싸지 않고 그대로 돌려준다
        assertFalse(buf.copy() instanceof FriendlyByteBuf);
        assertFalse(buf.duplicate() instanceof FriendlyByteBuf);
        assertFalse(buf.slice() instanceof FriendlyByteBuf);
        assertEquals(0x01020304, buf.getInt(0));
        assertEquals(1, buf.refCnt());
        assertTrue(buf.release());
        assertEquals(0, raw.refCnt());
        // 문자열 길이 상한
        FriendlyByteBuf small = new FriendlyByteBuf(Unpooled.buffer());
        assertThrows(io.netty.handler.codec.EncoderException.class, () -> small.writeUtf("abcd", 3));
        small.writeUtf("abc", 3);
        assertThrows(io.netty.handler.codec.DecoderException.class, () -> small.readUtf(2));
        // 바닐라 wp.a: 길이가 남은 바이트보다 크면 DecoderException
        FriendlyByteBuf truncated = new FriendlyByteBuf(Unpooled.buffer());
        truncated.writeVarInt(5).writeBytes(new byte[] {1, 2});
        var ex = assertThrows(io.netty.handler.codec.DecoderException.class, truncated::readUtf);
        assertEquals("Not enough bytes in buffer, expected 5, but got 2", ex.getMessage());
    }

    @Test
    void registryFriendlyByteBufCarriesTheRegistryAccess() {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), net.minecraft.core.RegistryAccess.SERVER);
        assertSame(net.minecraft.core.RegistryAccess.SERVER, buf.registryAccess());
        assertSame(net.minecraft.core.RegistryAccess.SERVER, RegistryFriendlyByteBuf.decorator(net.minecraft.core.RegistryAccess.SERVER).apply(Unpooled.buffer()).registryAccess());
    }

    private static byte[] bytes(ByteBuf b) {
        byte[] out = new byte[b.readableBytes()];
        b.getBytes(b.readerIndex(), out);
        return out;
    }
}
