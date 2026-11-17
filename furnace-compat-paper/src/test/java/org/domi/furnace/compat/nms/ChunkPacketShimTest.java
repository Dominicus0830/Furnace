package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.WildcardType;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.api.packet.GenericPacket;
import org.domi.furnace.api.packet.PacketStruct;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.compat.paper.fixtures.JarCompiler;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * T-182: 청크·빛 패킷 shim 이 패킷 라이브러리가 반사로 찾는 모양이고(ProtocolLib WrappedLevelChunkData), Furnace 범용 패킷과 같은 값으로 오간다.
 */
class ChunkPacketShimTest {

    @TempDir
    Path tmp;

    private FurnaceTestServer start() throws Exception {
        Path jar = JarCompiler.jar(tmp.resolve("cp.jar"), "name: Cp\nversion: '1'\nmain: cp.P\napi-version: '1.21'\n",
                Map.of("cp.P", "package cp; public final class P extends org.bukkit.plugin.java.JavaPlugin {}"), Set.of());
        return FurnaceTestServer.builder().failFast(false).pluginJar(jar).start();
    }

    private static List<Field> fields(Class<?> c, Class<?> type) {
        List<Field> out = new ArrayList<>();
        for (Field f : c.getDeclaredFields()) if (!Modifier.isStatic(f.getModifiers()) && f.getType() == type) out.add(f);
        return out;
    }

    private static byte[] layer(int seed) {
        byte[] a = new byte[2048];
        for (int i = 0; i < a.length; i++) a[i] = (byte) (i * seed);
        return a;
    }

    @Test
    void dataClassesHaveTheShapeTheLibraryLooksFor() throws Exception {
        // ChunkData: CompoundTag·byte[]·List 필드가 하나씩, (FriendlyByteBuf 하위, int, int) 생성자
        Class<?> chunk = ClientboundLevelChunkPacketData.class;
        assertEquals(1, fields(chunk, CompoundTag.class).size());
        assertEquals(1, fields(chunk, byte[].class).size());
        List<Field> lists = fields(chunk, List.class);
        assertEquals(1, lists.size());
        Class<?> info = (Class<?>) ((ParameterizedType) lists.getFirst().getGenericType()).getActualTypeArguments()[0];
        assertEquals("BlockEntityInfo", info.getSimpleName());
        assertTrue(FriendlyByteBuf.class.isAssignableFrom(chunk.getConstructor(RegistryFriendlyByteBuf.class, int.class, int.class).getParameterTypes()[0]));
        // BlockEntityInfo: int 둘(packedXZ, y 순서)·종류·태그, 네 값 생성자
        List<Field> ints = fields(info, int.class);
        assertEquals(List.of("packedXZ", "y"), ints.stream().map(Field::getName).toList());
        assertEquals(1, fields(info, BlockEntityType.class).size());
        assertEquals(1, fields(info, CompoundTag.class).size());
        assertNotNull(info.getDeclaredConstructor(int.class, int.class, BlockEntityType.class, CompoundTag.class));
        // LightData: BitSet 넷(하늘·블록·빈 하늘·빈 블록 순서), List 둘(하늘·블록)
        Class<?> light = ClientboundLightUpdatePacketData.class;
        assertEquals(List.of("skyYMask", "blockYMask", "emptySkyYMask", "emptyBlockYMask"), fields(light, BitSet.class).stream().map(Field::getName).toList());
        assertEquals(List.of("skyUpdates", "blockUpdates"), fields(light, List.class).stream().map(Field::getName).toList());
        assertNotNull(light.getConstructor(FriendlyByteBuf.class, int.class, int.class));
        // 바닐라에 없는 값 생성자는 공개하지 않는다
        assertThrows(NoSuchMethodException.class, () -> chunk.getConstructor(CompoundTag.class, byte[].class, List.class));
        assertEquals(1, light.getConstructors().length);
        // BuiltInRegistries.BLOCK_ENTITY_TYPE: Registry<BlockEntityType<?>> — ProtocolLib WrappedRegistry 가 형식 인자의 원시 형식으로 찾는다
        var g = (ParameterizedType) net.minecraft.core.registries.BuiltInRegistries.class.getField("BLOCK_ENTITY_TYPE").getGenericType();
        var arg = (ParameterizedType) g.getActualTypeArguments()[0];
        assertEquals(BlockEntityType.class, arg.getRawType());
        assertInstanceOf(WildcardType.class, arg.getActualTypeArguments()[0]);
    }

    @Test
    void anEmptyBufferGivesEmptyDataTheLibraryThenFills() {
        // ProtocolLib ChunkData·LightData.fromValues: 0 버퍼로 만든 뒤 setter·addAll 로 채운다
        var buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(new byte[64]), null);
        var chunk = new ClientboundLevelChunkPacketData(buf, 0, 0);
        assertTrue(chunk.getHeightmaps().isEmpty());
        assertEquals(0, chunk.furnaceBuffer().length);
        assertTrue(chunk.furnaceBlockEntities().isEmpty());
        var light = new ClientboundLightUpdatePacketData(new FriendlyByteBuf(Unpooled.wrappedBuffer(new byte[64])), 0, 0);
        assertTrue(light.getSkyYMask().isEmpty());
        light.getSkyUpdates().add(layer(1));   // 바꿀 수 있는 목록
        assertEquals(1, light.getSkyUpdates().size());
    }

    /**
     * ProtocolLib ZeroBuffer 모양: 읽을 바이트 0, 그런데 읽기는 늘 0 을 준다(라이브러리가 빈 자료를 만들 때 쓰는 버퍼).
     * 진짜 ZeroBuffer 처럼 readableBytes 를 믿고 스트림으로 읽으면 EOF 가 난다(PR #399 검토 R1).
     */
    static final class ZeroLikeBuffer extends io.netty.buffer.SwappedByteBuf {
        ZeroLikeBuffer() { super(Unpooled.EMPTY_BUFFER); }
        @Override public int readableBytes() { return 0; }
        @Override public boolean isReadable() { return false; }
        @Override public boolean isReadable(int n) { return false; }
        @Override public byte readByte() { return 0; }
        @Override public short readUnsignedByte() { return 0; }
        @Override public boolean readBoolean() { return false; }
        @Override public short readShort() { return 0; }
        @Override public int readUnsignedShort() { return 0; }
        @Override public int readInt() { return 0; }
        @Override public long readLong() { return 0; }
        @Override public io.netty.buffer.ByteBuf readBytes(byte[] dst) { java.util.Arrays.fill(dst, (byte) 0); return this; }
        @Override public io.netty.buffer.ByteBuf readBytes(byte[] dst, int off, int len) { java.util.Arrays.fill(dst, off, off + len, (byte) 0); return this; }
    }

    @Test
    void theLibrarysZeroBufferGivesEmptyData() throws Exception {
        // ProtocolLib StructureCache 의 빈 자료용 직렬화기는 NbtAccounter 를 이름으로 찾는다 — 없으면 fromValues·createPacket 이 NPE
        Class<?> accounter = Class.forName("net.minecraft.nbt.NbtAccounter");
        assertNotNull(accounter.getMethod("unlimitedHeap").invoke(null));
        var chunk = new ClientboundLevelChunkPacketData(new RegistryFriendlyByteBuf(new ZeroLikeBuffer(), null), 0, 0);
        assertTrue(chunk.getHeightmaps().isEmpty());
        assertEquals(0, chunk.furnaceBuffer().length);
        assertTrue(chunk.furnaceBlockEntities().isEmpty());
        var light = new ClientboundLightUpdatePacketData(new FriendlyByteBuf(new ZeroLikeBuffer()), 0, 0);
        assertTrue(light.getSkyYMask().isEmpty() && light.getBlockYMask().isEmpty());
        assertTrue(light.getSkyUpdates().isEmpty() && light.getBlockUpdates().isEmpty());
    }

    @Test
    void chunkAndLightGoToTheShimAndBackUnchanged() throws Exception {
        try (FurnaceTestServer server = start()) {
            NbtCompound heightmaps = new NbtCompound().putLongArray("MOTION_BLOCKING", new long[] {1, 2, 3}).putLongArray("WORLD_SURFACE", new long[37]);
            byte[] buffer = new byte[300];
            for (int i = 0; i < buffer.length; i++) buffer[i] = (byte) (i * 31);
            var chest = new LinkedHashMap<String, Object>();
            chest.put("packed_xz", 0x3A);
            chest.put("y", -60);
            chest.put("type", "minecraft:chest");
            chest.put("tag", Optional.of(new NbtCompound().putString("CustomName", "\"box\"")));
            var sign = new LinkedHashMap<String, Object>();
            sign.put("packed_xz", 0xFF);
            sign.put("y", 319);
            sign.put("type", "minecraft:sign");
            sign.put("tag", Optional.empty());
            var chunkData = new LinkedHashMap<String, Object>();
            chunkData.put("heightmaps", heightmaps);
            chunkData.put("buffer", buffer);
            chunkData.put("block_entities_data", List.of(PacketStruct.of(chest), PacketStruct.of(sign)));
            BitSet sky = new BitSet();
            sky.set(0);
            sky.set(25);
            var lightData = new LinkedHashMap<String, Object>();
            lightData.put("sky_y_mask", sky.toLongArray());
            lightData.put("block_y_mask", new long[0]);
            lightData.put("empty_sky_y_mask", new long[] {2});
            lightData.put("empty_block_y_mask", new long[] {(1L << 26) - 1});
            lightData.put("sky_updates", List.of(layer(3), layer(5)));
            lightData.put("block_updates", List.of());
            var m = new LinkedHashMap<String, Object>();
            m.put("x", -7);
            m.put("z", 123456);
            m.put("chunk_data", PacketStruct.of(chunkData));
            m.put("light_data", PacketStruct.of(lightData));
            GenericPacket api = GenericPacket.of(PacketTypes.Clientbound.LEVEL_CHUNK_WITH_LIGHT, PacketStruct.of(m));

            var shim = assertInstanceOf(ClientboundLevelChunkWithLightPacket.class, PacketDelivery.fromFurnace(api));
            assertEquals(-7, shim.getX());
            assertEquals(123456, shim.getZ());
            assertArrayEquals(buffer, shim.getChunkData().furnaceBuffer());
            assertEquals(2, shim.getChunkData().furnaceBlockEntities().size());
            Object[] v = ClientboundLevelChunkPacketData.furnaceInfoValues(shim.getChunkData().furnaceBlockEntities().getFirst());
            assertEquals(0x3A, v[0]);
            assertEquals(-60, v[1]);
            assertEquals("minecraft:chest", BlockEntityType.getKey((BlockEntityType<?>) v[2]).toString());
            assertNull(ClientboundLevelChunkPacketData.furnaceInfoValues(shim.getChunkData().furnaceBlockEntities().get(1))[3]);
            assertEquals(sky, shim.getLightData().getSkyYMask());
            assertEquals(api, shim.toFurnace(), "같은 값으로 돌아온다");
            assertTrue(PacketDelivery.reversible(PacketTypes.Clientbound.LEVEL_CHUNK_WITH_LIGHT));

            // 바이트로 쓰고 다시 읽어도 같다(바닐라 write·읽기 생성자 모양)
            var out = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
            shim.getChunkData().write(out);
            var again = new ClientboundLevelChunkPacketData(out, 0, 0);
            assertEquals(0, out.readableBytes());
            assertEquals(api, ClientboundLevelChunkWithLightPacket.of(-7, 123456, again, shim.getLightData()).toFurnace());
            var lout = new FriendlyByteBuf(Unpooled.buffer());
            shim.getLightData().write(lout);
            assertEquals(api, ClientboundLevelChunkWithLightPacket.of(-7, 123456, again, new ClientboundLightUpdatePacketData(lout, 0, 0)).toFurnace());

            var lm = new LinkedHashMap<String, Object>();
            lm.put("x", 3);
            lm.put("z", -4);
            lm.put("light_data", PacketStruct.of(lightData));
            GenericPacket update = GenericPacket.of(PacketTypes.Clientbound.LIGHT_UPDATE, PacketStruct.of(lm));
            var lshim = assertInstanceOf(ClientboundLightUpdatePacket.class, PacketDelivery.fromFurnace(update));
            assertEquals(update, lshim.toFurnace());
            assertTrue(PacketDelivery.reversible(PacketTypes.Clientbound.LIGHT_UPDATE));

            // 서버 값에서 만든 자료의 빛 목록도 리스너가 바꿀 수 있다(비동기 처리기·리스너가 getSkyUpdates().set/add 한다 — 검토 S4 M3)
            lshim.getLightData().getSkyUpdates().set(0, layer(9));
            lshim.getLightData().getBlockUpdates().add(layer(1));
            assertEquals(2, lshim.getLightData().getSkyUpdates().size());

            // 블록 엔티티 종류 번호는 바닐라 등록 순서(서버 패킷 코덱과 같은 표) — write·바이트 생성자를 직접 부르는 플러그인용(검토 S4 M12)
            var reg = net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE;
            assertEquals(0, reg.getId(NmsBlockEntityTypes.byName("minecraft:furnace", "시험")));
            assertEquals(1, reg.getId(NmsBlockEntityTypes.byName("minecraft:chest", "시험")));
            assertEquals(7, reg.getId(NmsBlockEntityTypes.byName("minecraft:sign", "시험")));
        }
    }

    @Test
    void badValuesSayWhere() throws Exception {
        try (FurnaceTestServer server = start()) {
            var shortLayer = ClientboundLightUpdatePacketData.furnace(new BitSet(), new BitSet(), new BitSet(), new BitSet(), List.of(new byte[10]), List.of());
            var ex = assertThrows(IllegalArgumentException.class, () -> ClientboundLightUpdatePacket.of(0, 0, shortLayer).toFurnace());
            assertTrue(ex.getMessage().contains("light_update.light_data.skyUpdates[0]") && ex.getMessage().contains("2048"), ex.getMessage());
            var e2 = assertThrows(IllegalArgumentException.class, () -> NmsBlockEntityTypes.byName("minecraft:nope", "x.type"));
            assertTrue(e2.getMessage().contains("x.type") && e2.getMessage().contains("minecraft:nope"), e2.getMessage());
        }
    }
}
