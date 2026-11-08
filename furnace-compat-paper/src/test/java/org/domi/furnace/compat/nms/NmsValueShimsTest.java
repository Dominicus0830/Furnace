package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.ResourceLocationException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtBridge;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * H7b·H7c 값 shim 이 바닐라 1.21.1 과 같은 값을 내는지. 기대값은 바닐라 서버 jar 를 javap 로 읽은 식(kh·jd·ji·exc·dcd·akr·uc·ue)에서 손으로 셈했다.
 */
class NmsValueShimsTest {

    @Test
    void blockPosPacksAndPrintsLikeVanilla() {
        // x 26비트 <<38, z 26비트 <<12, y 12비트
        assertEquals(274877919234L, new BlockPos(1, 2, 3).asLong());
        assertEquals(274877919234L, BlockPos.asLong(1, 2, 3));
        for (int[] c : new int[][] {{-1, -64, -1}, {29_999_999, 2047, -30_000_000}, {-30_000_000, -2048, 29_999_999}}) {
            BlockPos p = BlockPos.of(BlockPos.asLong(c[0], c[1], c[2]));
            assertEquals(new BlockPos(c[0], c[1], c[2]), p);
        }
        BlockPos p = new BlockPos(1, 2, 3);
        assertEquals("BlockPos{x=1, y=2, z=3}", p.toString());
        assertEquals((2 + 3 * 31) * 31 + 1, p.hashCode(), "Vec3i 해시 (y + z*31)*31 + x");
        assertEquals(new BlockPos(1, 3, 3), p.above());
        assertEquals(new BlockPos(1, 2, 2), p.north());
        assertEquals(new BlockPos(1, 2, 2), p.relative(Direction.NORTH));
        assertEquals(new BlockPos(-2, 4, 3), BlockPos.containing(-1.5, 4.9, 3.0));
        assertTrue(new BlockPos(0, 0, 1).compareTo(new BlockPos(5, 0, 0)) > 0, "비교는 y → z → x 순");
        assertEquals(6.0F, (float) p.distManhattan(new BlockPos(0, 0, 0)));
        BlockPos.MutableBlockPos m = p.mutable();
        m.move(Direction.UP, 3);
        assertEquals(new BlockPos(1, 5, 3), m.immutable());
        assertEquals(p, BlockPos.fromFurnace(p.furnace()));
    }

    @Test
    void directionDataValuesMatchVanilla() {
        assertEquals(List.of("DOWN", "UP", "NORTH", "SOUTH", "WEST", "EAST"),
                java.util.Arrays.stream(Direction.values()).map(Enum::name).toList());
        assertEquals(2, Direction.NORTH.get2DDataValue());
        assertEquals(0, Direction.SOUTH.get2DDataValue());
        assertEquals(1, Direction.WEST.get2DDataValue());
        assertEquals(3, Direction.EAST.get2DDataValue());
        assertEquals(-1, Direction.UP.get2DDataValue());
        assertSame(Direction.SOUTH, Direction.NORTH.getOpposite());
        assertSame(Direction.EAST, Direction.NORTH.getClockWise());
        assertSame(Direction.NORTH, Direction.from3DDataValue(2));
        assertSame(Direction.SOUTH, Direction.fromYRot(0.0));
        assertSame(Direction.WEST, Direction.fromYRot(90.0));
        assertEquals(-1, Direction.NORTH.getStepZ());
        assertSame(Direction.Axis.Z, Direction.NORTH.getAxis());
        assertEquals("north", Direction.NORTH.getSerializedName());
        assertSame(Direction.EAST, Direction.byName("east"));
        assertNull(Direction.byName("nope"));
    }

    @Test
    void vec3AndChunkPosMatchVanilla() {
        assertEquals("(1.0, 2.5, -3.0)", new Vec3(1, 2.5, -3).toString());
        assertNotEquals(new Vec3(0.0, 0, 0), new Vec3(-0.0, 0, 0), "바닐라는 Double.compare 로 비교한다(0.0 ≠ -0.0)");
        assertEquals(new Vec3(2, 4, 6), new Vec3(1, 2, 3).scale(2));
        assertEquals(5.0, new Vec3(3, 4, 0).length(), 1e-12);
        ChunkPos c = new ChunkPos(1, 2);
        assertEquals(8589934593L, c.toLong());
        assertEquals("[1, 2]", c.toString());
        assertEquals(1458653700, c.hashCode(), "바닐라 ChunkPos.hash(1664525·1013904223·-559038737)");
        assertEquals(1440273439, new ChunkPos(-3, 7).hashCode());
        assertEquals(new ChunkPos(-1, 0), new ChunkPos(new BlockPos(-1, 64, 15)));
        assertEquals(-16, new ChunkPos(-1, 0).getMinBlockX());
    }

    @Test
    void resourceLocationValidatesLikeVanilla() {
        ResourceLocation r = ResourceLocation.parse("stone");
        assertEquals("minecraft", r.getNamespace());
        assertEquals("minecraft:stone", r.toString());
        assertEquals(ResourceLocation.fromNamespaceAndPath("my_plugin", "a/b"), ResourceLocation.parse("my_plugin:a/b"));
        var e = assertThrows(ResourceLocationException.class, () -> ResourceLocation.parse("Bad:x"));
        assertEquals("Non [a-z0-9_.-] character in namespace of location: Bad:x", e.getMessage());
        e = assertThrows(ResourceLocationException.class, () -> ResourceLocation.parse("ok:Bad"));
        assertEquals("Non [a-z0-9/._-] character in path of location: ok:Bad", e.getMessage());
        assertNull(ResourceLocation.tryParse("a b"));
        assertTrue(ResourceLocation.parse("b:a").compareTo(ResourceLocation.parse("a:b")) < 0, "경로 먼저, 다음 이름공간");
        assertEquals("block.minecraft.stone", r.toLanguageKey("block"));
    }

    @Test
    void nbtTreeConvertsAndReadsLikeVanilla() {
        CompoundTag t = new CompoundTag();
        t.putInt("n", 7);
        t.putString("s", "hi");
        t.putBoolean("b", true);
        UUID id = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
        t.putUUID("id", id);
        ListTag l = new ListTag();
        l.add(StringTag.valueOf("x"));
        l.add(StringTag.valueOf("y"));
        t.put("l", l);
        CompoundTag inner = new CompoundTag();
        inner.putDouble("d", 1.5);
        t.put("c", inner);

        assertEquals(7, t.getInt("n"));
        assertEquals(0, t.getInt("missing"), "없는 키는 0");
        assertEquals(0, t.getInt("s"), "종류가 다르면 0");
        assertEquals("", t.getString("n"), "문자열 아닌 값의 getString 은 빈 문자열");
        assertTrue(t.getBoolean("b"));
        assertEquals(id, t.getUUID("id"));
        var bad = assertThrows(IllegalArgumentException.class, () -> t.getUUID("n"));
        assertEquals("Expected UUID-Tag to be of type INT[], but found INT.", bad.getMessage(), "바닐라 NbtUtils.loadUUID 문구(TagType 이름)");
        assertThrows(NullPointerException.class, () -> t.getUUID("missing"), "바닐라처럼 없는 키는 NPE");
        assertEquals(2, t.getList("l", Tag.TAG_STRING).size());
        assertEquals(0, t.getList("l", Tag.TAG_INT).size(), "다른 원소 종류로 읽으면 빈 목록");
        assertEquals(1.5, t.getCompound("c").getDouble("d"));
        assertTrue(t.contains("n", Tag.TAG_ANY_NUMERIC));

        // Furnace NBT 로 갔다 와도 같다
        assertEquals(t, NbtBridge.fromFurnace(NbtBridge.toFurnace(t)));
        assertEquals(t, CompoundTag.fromFurnace(t.furnace()));
        CompoundTag copy = t.copy();
        copy.getCompound("c").putInt("z", 1);
        assertFalse(t.getCompound("c").contains("z"), "copy 는 깊은 사본");

        // 수 변환은 바닐라 규칙(DoubleTag 는 floor, FloatTag 는 (long) 자르기)
        assertEquals(-2L, DoubleTag.valueOf(-1.5).getAsLong());
        assertEquals(-1L, FloatTag.valueOf(-1.5F).getAsLong());
        assertEquals(-2, FloatTag.valueOf(-1.5F).getAsInt());
        assertSame(IntTag.valueOf(5), IntTag.valueOf(5), "-128..1023 캐시");

        CompoundTag parsed = TagParser.parseTag("{n:7,s:\"hi\",c:{d:1.5d}}");
        assertEquals(7, parsed.getInt("n"));
        assertEquals("hi", parsed.getString("s"));
        assertEquals(1.5, parsed.getCompound("c").getDouble("d"));
        assertThrows(UnsupportedNmsCall.class, () -> TagParser.parseTag("{n:"));
    }

    @Test
    void componentsBridgeToFurnaceText() {
        Component c = Component.literal("a").withStyle(ChatFormatting.RED).append(Component.literal("b"));
        assertEquals("ab", c.getString());
        var text = Component.furnace(c);
        assertEquals("ab", text.plainText());
        String json = Component.Serializer.toJson(c, RegistryAccess.SERVER);
        assertEquals(json, Component.Serializer.toJson(Component.Serializer.fromJson(json, RegistryAccess.SERVER), RegistryAccess.SERVER));
        assertSame(ChatFormatting.RED, ChatFormatting.getByCode('c'));
        assertEquals(0xFF5555, ChatFormatting.RED.getColor());
        assertEquals("§c", ChatFormatting.RED.toString());
        assertSame(ChatFormatting.BOLD, ChatFormatting.getByName("bold"));
        assertEquals("ab", ChatFormatting.stripFormatting("§ca§lb"));
    }

    @Test
    void packetShimsConvertToFurnaceRecords() {
        // 1.21.1 바닐라: 하루 순환이 꺼져 있으면 하루 시간을 음수로(0 은 -1)
        assertEquals(6000L, new ClientboundSetTimePacket(10, 6000, true).getDayTime());
        assertEquals(-6000L, new ClientboundSetTimePacket(10, 6000, false).getDayTime());
        assertEquals(-1L, new ClientboundSetTimePacket(10, 0, false).getDayTime());
        assertEquals(new org.domi.furnace.api.packet.clientbound.ClientboundSetTimePacket(10, -6000),
                new ClientboundSetTimePacket(10, 6000, false).toFurnace());
        // 속도는 ±3.9 로 자르고 1/8000 단위
        var motion = new ClientboundSetEntityMotionPacket(5, new Vec3(10, -0.00006, 0.5));
        assertEquals(3.9, motion.getXa());
        assertEquals(0.0, motion.getYa(), "(int) 자르기라 0 쪽으로");
        assertEquals(0.5, motion.getZa());
        var chat = new ClientboundSystemChatPacket(Component.literal("hi"), true).toFurnace();
        assertTrue(chat instanceof org.domi.furnace.api.packet.clientbound.ClientboundSystemChatPacket s && s.actionBar()
                && s.message().plainText().equals("hi"));
    }
}
