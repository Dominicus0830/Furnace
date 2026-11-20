package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.Utf8String;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.domi.furnace.api.packet.PacketTypes;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * 1번 묶음: 플러그인 메시지 코덱(Axiom·VeinMiner·headpat·squaremap)이 쓰는 FriendlyByteBuf 도우미·플러그인 메시지 본문.
 * 기대 바이트·예외 문구는 Paper 1.21.1 바이트코드(javap)에서 옮겼다.
 */
class FriendlyByteBufHelpersTest {

    private static FriendlyByteBuf buf() { return new FriendlyByteBuf(Unpooled.buffer()); }

    private static byte[] bytes(ByteBuf b) {
        byte[] out = new byte[b.readableBytes()];
        b.getBytes(b.readerIndex(), out);
        return out;
    }

    @Test
    void collectionsMapsAndNullablesUseAVarIntCountLikeVanilla() {
        FriendlyByteBuf b = buf();
        b.writeCollection(List.of("a", "bc"), FriendlyByteBuf::writeUtf);
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("x", 1);
        m.put("y", 300);
        b.writeMap(m, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeVarInt);
        b.writeNullable((String) null, FriendlyByteBuf::writeUtf);
        b.writeNullable("n", FriendlyByteBuf::writeUtf);
        // 개수 2, "a", "bc", 개수 2, "x" 1, "y" 300(0xac 0x02), 없음 0, 있음 1 "n"
        assertArrayEquals(new byte[] {2, 1, 'a', 2, 'b', 'c', 2, 1, 'x', 1, 1, 'y', (byte) 0xac, 0x02, 0, 1, 1, 'n'}, bytes(b));

        List<String> list = b.readList(x -> x.readUtf());
        assertEquals(List.of("a", "bc"), list);
        assertTrue(list instanceof ArrayList, "바닐라 Lists.newArrayListWithCapacity");
        assertEquals(m, b.readMap(x -> x.readUtf(), x -> x.readVarInt()));
        assertNull(b.readNullable(x -> x.readUtf()));
        assertEquals("n", b.readNullable(x -> x.readUtf()));
        assertEquals(0, b.readableBytes());

        FriendlyByteBuf c = buf();
        c.writeCollection(Set.of("q"), FriendlyByteBuf::writeUtf);
        Set<String> got = c.readCollection(LinkedHashSet::new, x -> x.readUtf());
        assertEquals(Set.of("q"), got);
        assertTrue(got instanceof LinkedHashSet);
    }

    @Test
    void readByIdAndLimitValue() {
        FriendlyByteBuf b = buf();
        assertSame(b, b.writeById((String s) -> s.length(), "abc"));
        b.writeVarInt(9);
        assertEquals("id3", b.readById(i -> "id" + i));
        java.util.function.IntFunction<ArrayList<Integer>> limited = FriendlyByteBuf.limitValue(ArrayList::new, 8);
        DecoderException e = assertThrows(DecoderException.class, () -> b.readCollection(limited, x -> x.readVarInt()));
        assertEquals("Value 9 is larger than limit 8", e.getMessage());
        assertEquals(3, FriendlyByteBuf.limitValue(i -> i, 8).apply(3));
    }

    @Test
    void nbtIsTheNamelessNetworkFormat() {
        CompoundTag root = new CompoundTag();
        root.putInt("a", 5);
        FriendlyByteBuf b = buf();
        assertSame(b, b.writeNbt(root));
        // compound(10), int(3) "a" 5, END
        assertArrayEquals(new byte[] {10, 3, 0, 1, 'a', 0, 0, 0, 5, 0}, bytes(b));
        CompoundTag back = b.readNbt();
        assertEquals(5, back.getInt("a"));

        // null 은 END 한 바이트, 읽으면 null
        FriendlyByteBuf e = buf();
        e.writeNbt((Tag) null);
        assertArrayEquals(new byte[] {0}, bytes(e));
        assertNull(e.readNbt());

        // 뿌리가 compound 가 아니면: 아무 태그 읽기는 되고, readNbt() 는 바닐라 문구로 거절
        FriendlyByteBuf s = buf();
        s.writeNbt(StringTag.valueOf("hi"));
        assertArrayEquals(new byte[] {8, 0, 2, 'h', 'i'}, bytes(s));
        Tag any = s.readNbt(NbtAccounter.unlimitedHeap());
        assertEquals("hi", any.getAsString());
        FriendlyByteBuf s2 = buf();
        s2.writeNbt(IntTag.valueOf(7));
        DecoderException refused = assertThrows(DecoderException.class, s2::readNbt);
        assertTrue(refused.getMessage().startsWith("Not a compound tag: "), refused.getMessage());

        // 빈 리스트는 원소 타입 END(바닐라 ListTag#write)
        FriendlyByteBuf l = buf();
        l.writeNbt(new ListTag());
        assertArrayEquals(new byte[] {9, 0, 0, 0, 0, 0}, bytes(l));
    }

    @Test
    void resourceKeysHitResultsAndVectors() {
        FriendlyByteBuf b = buf();
        ResourceKey<net.minecraft.world.item.Item> key = ResourceKey.create(Registries.ITEM, ResourceLocation.parse("minecraft:stone"));
        b.writeResourceKey(key);
        BlockPos pos = new BlockPos(10, 64, -3);
        b.writeBlockHitResult(new BlockHitResult(new Vec3(10.25, 64.5, -2.75), Direction.UP, pos, true));
        b.writeVector3f(new Vector3f(1.5f, -2f, 0.25f));
        b.writeQuaternion(new Quaternionf(0.1f, 0.2f, 0.3f, 0.9f));

        ResourceKey<net.minecraft.world.item.Item> k = b.readResourceKey(Registries.ITEM);
        assertEquals(key.location(), k.location());
        assertEquals(key.registry(), k.registry());
        BlockHitResult hit = b.readBlockHitResult();
        assertEquals(pos.getX(), hit.getBlockPos().getX());
        assertEquals(pos.getZ(), hit.getBlockPos().getZ());
        assertEquals(Direction.UP, hit.getDirection());
        assertTrue(hit.isInside());
        assertEquals(10.25, hit.getLocation().x, 1e-6);
        assertEquals(64.5, hit.getLocation().y, 1e-6);
        assertEquals(-2.75, hit.getLocation().z, 1e-6);
        assertEquals(new Vector3f(1.5f, -2f, 0.25f), b.readVector3f());
        assertEquals(new Quaternionf(0.1f, 0.2f, 0.3f, 0.9f), b.readQuaternion());
        assertEquals(0, b.readableBytes());
    }

    @Test
    void utf8StringIsTheSameAsReadUtf() {
        ByteBuf raw = Unpooled.buffer();
        Utf8String.write(raw, "월드", 10);
        FriendlyByteBuf b = new FriendlyByteBuf(raw);
        assertEquals("월드", b.readUtf(10));
        Utf8String.write(raw, "abcd", 4);
        DecoderException e = assertThrows(DecoderException.class, () -> Utf8String.read(raw, 1));
        assertEquals("The received encoded string buffer length is longer than maximum allowed (4 > 3)", e.getMessage());
    }

    /** Paper 와 같이 쓰기·위치 옮기기는 FriendlyByteBuf(공변)를 돌려주고, readBytes(int)·copy 등은 새 ByteBuf 다. */
    @Test
    void delegatedMethodsHaveThePaperReturnTypes() throws Exception {
        for (String[] m : new String[][] {{"writeInt", "int"}, {"skipBytes", "int"}, {"readerIndex", "int"}, {"clear"}, {"writeBoolean", "boolean"}}) {
            Class<?>[] params = new Class<?>[m.length - 1];
            for (int i = 1; i < m.length; i++) params[i - 1] = m[i].equals("int") ? int.class : boolean.class;
            Method x = FriendlyByteBuf.class.getDeclaredMethod(m[0], params);
            assertEquals(FriendlyByteBuf.class, x.getReturnType(), m[0]);
        }
        for (String name : new String[] {"copy", "slice", "duplicate", "unwrap", "asReadOnly"}) {
            assertEquals(ByteBuf.class, FriendlyByteBuf.class.getDeclaredMethod(name).getReturnType(), name);
        }
        FriendlyByteBuf b = buf();
        b.writeBytes(new byte[] {1, 2, 3, 4});
        ByteBuf two = b.readBytes(2);
        assertFalse(two instanceof FriendlyByteBuf);
        assertArrayEquals(new byte[] {1, 2}, bytes(two), "readBytes(int) 는 읽은 바이트를 담은 새 버퍼");
        assertEquals(2, b.readerIndex());
        assertEquals(ByteBuf.class, FriendlyByteBuf.class.getDeclaredMethod("readBytes", int.class).getReturnType());
    }

    @Test
    void discardedPayloadCodecAndType() {
        ResourceLocation id = ResourceLocation.parse("axiom:hello");
        StreamCodec<FriendlyByteBuf, DiscardedPayload> codec = DiscardedPayload.codec(id, 4);
        FriendlyByteBuf in = buf();
        in.writeBytes(new byte[] {7, 8, 9});
        DiscardedPayload p = codec.decode(in);
        assertEquals(id, p.id());
        assertEquals(id, p.type().id());
        assertArrayEquals(new byte[] {7, 8, 9}, bytes(p.data()));
        FriendlyByteBuf out = buf();
        codec.encode(out, p);
        assertArrayEquals(new byte[] {7, 8, 9}, bytes(out));
        FriendlyByteBuf big = buf();
        big.writeBytes(new byte[5]);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> codec.decode(big));
        assertEquals("Payload may not be larger than 4 bytes", e.getMessage());
        assertEquals(ResourceLocation.withDefaultNamespace("brand"), CustomPacketPayload.createType("brand").id());
    }

    /** 플러그인이 connection.send 로 보내는 플러그인 메시지 → Furnace 패킷(채널·바이트), 그리고 반대. */
    @Test
    void customPayloadPacketConvertsBothWays() {
        ByteBuf data = Unpooled.wrappedBuffer(new byte[] {1, 2, 3});
        var shim = new ClientboundCustomPayloadPacket(new DiscardedPayload(ResourceLocation.parse("veinminer:sync"), data));
        var api = (org.domi.furnace.api.packet.clientbound.ClientboundCustomPayloadPacket) PacketDelivery.convert(shim);
        assertEquals("veinminer:sync", api.channel());
        assertArrayEquals(new byte[] {1, 2, 3}, api.data());
        assertEquals(0, data.readableBytes(), "바닐라 인코더처럼 본문 버퍼를 끝까지 읽는다");

        assertTrue(PacketDelivery.reversible(PacketTypes.Clientbound.CUSTOM_PAYLOAD));
        assertTrue(PacketDelivery.losslessRoundTrip(PacketTypes.Clientbound.CUSTOM_PAYLOAD));
        var back = (ClientboundCustomPayloadPacket) PacketDelivery.fromFurnace(api);
        DiscardedPayload d = (DiscardedPayload) back.payload();
        assertEquals(ResourceLocation.parse("veinminer:sync"), d.id());
        assertArrayEquals(new byte[] {1, 2, 3}, bytes(d.data()));
        assertEquals(api, PacketDelivery.convert(back));

        record Mine(ResourceLocation id) implements CustomPacketPayload {
            @Override public Type<Mine> type() { return new Type<>(id); }
        }
        var own = new ClientboundCustomPayloadPacket(new Mine(ResourceLocation.parse("p:own")));
        UnsupportedNmsCall refused = assertThrows(UnsupportedNmsCall.class, () -> PacketDelivery.convert(own));
        assertTrue(refused.getMessage().contains("DiscardedPayload"), refused.getMessage());
    }
}
