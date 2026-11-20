package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.DataResult;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import org.domi.furnace.api.item.ItemComponents;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * T-191(NbtOps)·T-200(아이템 구성 요소 코덱). 기대값은 바닐라 1.21.1 {@code NbtOps}·{@code DataComponentPatch.CODEC} 을 바이트코드와 대조해 정했다.
 * DataFixerUpper 라이브러리는 시험 클래스패스에서 대역(src/dfu)이거나 진짜(-PdfuJar)다 — 두 쪽에서 같은 결과여야 해서 대역에 없는 멤버는 쓰지 않는다.
 */
class NmsComponentCodecTest {

    private static FurnaceTestServer server;

    @BeforeAll
    static void start() { server = FurnaceTestServer.builder().start(); }

    @AfterAll
    static void stop() { server.close(); }

    private static <R> R ok(DataResult<R> r) { return r.getOrThrow(IllegalStateException::new); }

    private static String error(DataResult<?> r) {
        try {
            r.getOrThrow(IllegalStateException::new);
        } catch (IllegalStateException e) {
            return e.getMessage();
        }
        throw new AssertionError("성공했다: " + r);
    }

    private static CompoundTag compound(Object... kv) {
        CompoundTag c = new CompoundTag();
        for (int i = 0; i < kv.length; i += 2) c.put((String) kv[i], (Tag) kv[i + 1]);
        return c;
    }

    // ------------------------------------------------------------------ NbtOps

    @Test
    void nbtOpsIsADynamicOpsForTags() {
        assertTrue(NbtOps.INSTANCE instanceof com.mojang.serialization.DynamicOps<?>);
        assertSame(EndTag.INSTANCE, NbtOps.INSTANCE.empty());
        assertEquals("NBT", NbtOps.INSTANCE.toString());
    }

    @Test
    void primitivesFollowVanilla() {
        var ops = NbtOps.INSTANCE;
        assertEquals(ByteTag.valueOf((byte) 1), ops.createBoolean(true));
        assertEquals(IntTag.valueOf(7), ops.createInt(7));
        // 수 하나를 일반 수로 만들면 double 이다(바닐라 createNumeric)
        assertEquals(net.minecraft.nbt.DoubleTag.valueOf(2.0), ops.createNumeric(2));
        assertEquals(7, ok(ops.getNumberValue(IntTag.valueOf(7))).intValue());
        assertEquals("Not a number", error(ops.getNumberValue(StringTag.valueOf("x"))));
        assertEquals("hi", ok(ops.getStringValue(StringTag.valueOf("hi"))));
        assertEquals("Not a string", error(ops.getStringValue(IntTag.valueOf(1))));
        assertTrue(ok(ops.getBooleanValue(ByteTag.valueOf((byte) 2))));
    }

    @Test
    void createListPicksArrayTagsForUniformNumbers() {
        var ops = NbtOps.INSTANCE;
        assertEquals(new ListTag(), ops.createList(Stream.empty()));
        assertInstanceOf(ByteArrayTag.class, ops.createList(Stream.of(ByteTag.valueOf((byte) 1), ByteTag.valueOf((byte) 2))));
        assertArrayEquals(new int[] {1, 2, 3}, ((IntArrayTag) ops.createList(Stream.of(IntTag.valueOf(1), IntTag.valueOf(2), IntTag.valueOf(3)))).getAsIntArray());
        assertArrayEquals(new long[] {5L}, ((LongArrayTag) ops.createList(Stream.of(LongTag.valueOf(5)))).getAsLongArray());
        // 다른 종류가 섞이면 감싼 묶음 리스트: [1b, "x"] -> [{"":1b}, {"":"x"}]
        Tag mixed = ops.createList(Stream.of(ByteTag.valueOf((byte) 1), StringTag.valueOf("x")));
        ListTag l = assertInstanceOf(ListTag.class, mixed);
        assertEquals(compound("", ByteTag.valueOf((byte) 1)), l.get(0));
        assertEquals(compound("", StringTag.valueOf("x")), l.get(1));
        // 읽을 때는 벗겨 준다
        assertEquals(List.of(ByteTag.valueOf((byte) 1), StringTag.valueOf("x")), ok(ops.getStream(mixed)).toList());
        // 문자열만 모으면 보통 리스트
        ListTag strings = assertInstanceOf(ListTag.class, ops.createList(Stream.of(StringTag.valueOf("a"), StringTag.valueOf("b"))));
        assertEquals(2, strings.size());
        assertEquals(Tag.TAG_STRING, strings.getElementType());
    }

    @Test
    void mergeToListAndMapFollowVanilla() {
        var ops = NbtOps.INSTANCE;
        Tag list = ok(ops.mergeToList(ops.empty(), StringTag.valueOf("a")));
        list = ok(ops.mergeToList(list, StringTag.valueOf("b")));
        assertEquals(2, ((ListTag) list).size());
        assertTrue(error(ops.mergeToList(IntTag.valueOf(1), StringTag.valueOf("a"))).startsWith("mergeToList called with not a list"));

        Tag map = ok(ops.mergeToMap(ops.empty(), StringTag.valueOf("k"), IntTag.valueOf(1)));
        assertEquals(compound("k", IntTag.valueOf(1)), map);
        // 원본은 바뀌지 않는다(shallowCopy)
        Tag map2 = ok(ops.mergeToMap(map, StringTag.valueOf("j"), IntTag.valueOf(2)));
        assertEquals(1, ((CompoundTag) map).size());
        assertEquals(2, ((CompoundTag) map2).size());
        assertTrue(error(ops.mergeToMap(ops.empty(), IntTag.valueOf(1), IntTag.valueOf(2))).startsWith("key is not a string"));
        assertTrue(error(ops.mergeToMap(IntTag.valueOf(1), StringTag.valueOf("k"), IntTag.valueOf(2))).startsWith("mergeToMap called with not a map"));
    }

    @Test
    void mapReadingAndRemove() {
        var ops = NbtOps.INSTANCE;
        CompoundTag c = compound("a", IntTag.valueOf(1), "b", StringTag.valueOf("x"));
        var map = ok(ops.getMap(c));
        assertEquals(IntTag.valueOf(1), map.get("a"));
        assertNull(map.get("zzz"));
        assertEquals(2, map.entries().count());
        assertEquals(2, ok(ops.getMapValues(c)).count());
        assertEquals("Not a map: 1", error(ops.getMap(IntTag.valueOf(1))));
        CompoundTag rebuilt = (CompoundTag) ops.createMap(ok(ops.getMapValues(c)));
        assertEquals(c, rebuilt);
        CompoundTag removed = (CompoundTag) ops.remove(c, "a");
        assertFalse(removed.contains("a"));
        assertTrue(c.contains("a"), "원본은 그대로");
    }

    @Test
    void arrayHelpers() {
        var ops = NbtOps.INSTANCE;
        ByteArrayTag bytes = (ByteArrayTag) ops.createByteList(java.nio.ByteBuffer.wrap(new byte[] {1, 2, 3}));
        assertEquals(3, bytes.getAsByteArray().length);
        assertEquals(3, ok(ops.getByteBuffer(bytes)).capacity());
        assertEquals(6, ok(ops.getIntStream(new IntArrayTag(new int[] {1, 2, 3}))).sum());
        assertEquals(6L, ok(ops.getLongStream(new LongArrayTag(new long[] {1, 2, 3}))).sum());
        assertEquals(new IntArrayTag(new int[] {4, 5}), ops.createIntList(java.util.stream.IntStream.of(4, 5)));
    }

    @Test
    void convertToTheSameOpsRebuildsTheTag() {
        CompoundTag inner = compound("n", IntTag.valueOf(3), "s", StringTag.valueOf("t"));
        ListTag list = new ListTag();
        list.add(inner.copy());
        list.add(inner.copy());
        CompoundTag tag = compound("list", list, "ints", new IntArrayTag(new int[] {1, 2}), "bytes", new ByteArrayTag(new byte[] {9}), "c", inner,
                "d", net.minecraft.nbt.DoubleTag.valueOf(1.5), "f", net.minecraft.nbt.FloatTag.valueOf(2.5F), "l", LongTag.valueOf(7));
        Tag copy = NbtOps.INSTANCE.convertTo(NbtOps.INSTANCE, tag);
        assertEquals(tag, copy);
        // 감싸는 연산(RegistryOps)도 안쪽 NbtOps 로 같은 결과
        var wrapped = RegistryOps.create(NbtOps.INSTANCE, RegistryAccess.SERVER);
        assertEquals(tag, NbtOps.INSTANCE.convertTo(wrapped, tag));
        assertEquals(tag, wrapped.convertTo(NbtOps.INSTANCE, tag));
    }

    private static void assertArrayEquals(int[] a, int[] b) { org.junit.jupiter.api.Assertions.assertArrayEquals(a, b); }

    private static void assertArrayEquals(long[] a, long[] b) { org.junit.jupiter.api.Assertions.assertArrayEquals(a, b); }

    // ------------------------------------------------------------------ RegistryOps · 직렬화 맥락

    @Test
    void serializationContextWrapsTheOps() {
        var ctx = RegistryAccess.SERVER.freeze().createSerializationContext(NbtOps.INSTANCE);
        assertInstanceOf(RegistryOps.class, ctx);
        assertEquals(IntTag.valueOf(1), ctx.createInt(1));
        assertSame(EndTag.INSTANCE, ctx.empty());
        // 감싼 연산 위에 감싸개를 다시 씌워도 같은 안쪽 연산이면 그대로
        assertSame(ctx, ctx.withParent(NbtOps.INSTANCE));
        var key = net.minecraft.core.registries.Registries.ITEM;
        assertTrue(ctx.getter(key).isPresent(), "아이템 레지스트리 조회기");
        assertTrue(ctx.owner(key).isPresent());
        assertEquals(RegistryOps.create(NbtOps.INSTANCE, RegistryAccess.SERVER), RegistryAccess.SERVER.createSerializationContext(NbtOps.INSTANCE));
    }

    // ------------------------------------------------------------------ DataComponentPatch.CODEC

    private static ItemStack stack(org.domi.furnace.api.item.ItemStack value) { return ItemStack.fromFurnace(value, value.amount()); }

    @Test
    void patchEncodesLikeVanillaNbt() {
        var api = new org.domi.furnace.api.item.ItemStack("minecraft:stick", 1).with(ItemComponents.REPAIR_COST, 5).without(ItemComponents.MAX_STACK_SIZE);
        DataComponentPatch patch = stack(api).getComponentsPatch();
        assertEquals(2, patch.size());
        var ctx = RegistryAccess.SERVER.createSerializationContext(NbtOps.INSTANCE);
        Tag tag = ok(DataComponentPatch.CODEC.encodeStart(ctx, patch));
        assertEquals(compound("minecraft:repair_cost", IntTag.valueOf(5), "!minecraft:max_stack_size", new CompoundTag()), tag);
        // 비어 있는 패치는 빈 묶음
        assertEquals(new CompoundTag(), ok(DataComponentPatch.CODEC.encodeStart(ctx, DataComponentPatch.EMPTY)));
    }

    @Test
    void patchDecodesAndRoundTrips() {
        var ctx = RegistryAccess.SERVER.createSerializationContext(NbtOps.INSTANCE);
        CompoundTag custom = compound("hello", StringTag.valueOf("world"), "n", IntTag.valueOf(4));
        CompoundTag in = compound("minecraft:custom_data", custom, "minecraft:repair_cost", IntTag.valueOf(2), "!minecraft:max_stack_size", new CompoundTag());
        DataComponentPatch patch = ok(DataComponentPatch.CODEC.parse(ctx, in));
        assertEquals(2, patch.furnaceSet().size());
        assertEquals(Set.of("minecraft:max_stack_size"), patch.furnaceRemoved());
        assertEquals(in, ok(DataComponentPatch.CODEC.encodeStart(ctx, patch)));
        // 읽은 패치가 스택에서 바닐라처럼 읽힌다
        ItemStack s = new ItemStack(net.minecraft.world.item.Items.STICK);
        s.applyComponents(patch);
        assertEquals(custom, s.get(DataComponents.CUSTOM_DATA).copyTag());
        assertEquals(1, s.getMaxStackSize(), "지운 max_stack_size 는 1");
        assertEquals(patch, s.getComponentsPatch());
    }

    @Test
    void decodeRejectsBadInputLikeVanilla() {
        var ctx = RegistryAccess.SERVER.createSerializationContext(NbtOps.INSTANCE);
        assertTrue(error(DataComponentPatch.CODEC.parse(ctx, compound("minecraft:nope", new CompoundTag()))).contains("No component with type: 'minecraft:nope'"));
        assertTrue(error(DataComponentPatch.CODEC.parse(ctx, compound("minecraft:repair_cost", StringTag.valueOf("x")))).length() > 0);
        assertTrue(error(DataComponentPatch.CODEC.parse(ctx, IntTag.valueOf(1))).startsWith("Not a map"));
        assertTrue(error(DataComponentPatch.CODEC.parse(ctx, compound("!minecraft:repair_cost", IntTag.valueOf(1)))).contains("Not a map"));
    }

    @Test
    void emptyPatchDecodesToEmpty() {
        var ctx = RegistryAccess.SERVER.createSerializationContext(NbtOps.INSTANCE);
        assertSame(DataComponentPatch.EMPTY, ok(DataComponentPatch.CODEC.parse(ctx, new CompoundTag())));
    }

    // ------------------------------------------------------------------ ItemStack#getComponentsPatch · applyComponents

    @Test
    void getComponentsPatchOfEmptyStacksIsEmpty() {
        assertSame(DataComponentPatch.EMPTY, ItemStack.EMPTY.getComponentsPatch());
        assertSame(DataComponentPatch.EMPTY, new ItemStack(net.minecraft.world.item.Items.STONE, 0).getComponentsPatch(), "개수 0 은 비었다");
        assertTrue(new ItemStack(net.minecraft.world.item.Items.STONE).getComponentsPatch().isEmpty());
    }

    @Test
    void applyComponentsFollowsSetAndRemoveRules() {
        ItemStack s = new ItemStack(net.minecraft.world.item.Items.STICK);
        // 종류 기본값과 같은 값은 패치에 안 남는다(바닐라 PatchedDataComponentMap.set)
        s.applyComponents(DataComponentPatch.furnaceOf(Map.of("minecraft:max_stack_size", "64"), Set.of()));
        assertTrue(s.getComponentsPatch().isEmpty());
        // 기본값이 있는 컴포넌트(max_stack_size)를 지우면 지움 표시, 기본값이 없는 컴포넌트(custom_data)를 지우면 그냥 뗀다
        s.applyComponents(DataComponentPatch.furnaceOf(Map.of("minecraft:custom_data", "{a:1}"), Set.of()));
        assertTrue(s.getComponentsPatch().furnaceSet().containsKey("minecraft:custom_data"));
        s.applyComponents(DataComponentPatch.furnaceOf(Map.of(), Set.of("minecraft:max_stack_size", "minecraft:custom_data")));
        assertEquals(Set.of("minecraft:max_stack_size"), s.getComponentsPatch().furnaceRemoved());
        assertEquals(1, s.getMaxStackSize());
        // 지움 뒤에 값을 다시 얹으면 지움이 풀린다
        s.applyComponents(DataComponentPatch.furnaceOf(Map.of("minecraft:max_stack_size", "16"), Set.of()));
        assertEquals(16, s.getMaxStackSize());
        // 복사본에는 영향이 없다
        ItemStack copy = s.copy();
        s.applyComponents(DataComponentPatch.furnaceOf(Map.of("minecraft:repair_cost", "9"), Set.of()));
        assertFalse(copy.getComponentsPatch().furnaceSet().containsKey("minecraft:repair_cost"));
        assertEquals(2, s.getComponentsPatch().size());
    }

    @Test
    void applyComponentsRejectsUnknownComponentsAndLeavesEmptyAlone() {
        ItemStack s = new ItemStack(net.minecraft.world.item.Items.STICK);
        assertThrows(IllegalArgumentException.class, () -> s.applyComponents(DataComponentPatch.furnaceOf(Map.of("minecraft:nope", "1"), Set.of())));
        ItemStack.EMPTY.applyComponents(DataComponentPatch.furnaceOf(Map.of("minecraft:repair_cost", "1"), Set.of()));   // 바닐라: EMPTY 는 던지지 않고 그대로 비어 있다
        assertTrue(ItemStack.EMPTY.isEmpty());
        assertTrue(ItemStack.EMPTY.getComponentsPatch().isEmpty());
        ItemStack.EMPTY.applyComponents(DataComponentPatch.EMPTY);
    }
}
