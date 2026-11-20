package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.DataResult;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * PR #509 독립 검토에서 나온 세 가지(바닐라 {@code DispatchedMapCodec} 부분 결과·"missed input", 비 NBT ops 의 불리언, 읽기 쪽 JSON 값)를 고정한다.
 * 기대값은 검토자가 바닐라 1.21.1 서버로 직접 돌린 값이다.
 */
class NmsComponentCodecOpsTest {

    private static FurnaceTestServer server;

    @BeforeAll
    static void start() { server = FurnaceTestServer.builder().start(); }

    @AfterAll
    static void stop() { server.close(); }

    private static CompoundTag compound(Object... kv) {
        CompoundTag c = new CompoundTag();
        for (int i = 0; i < kv.length; i += 2) c.put((String) kv[i], (net.minecraft.nbt.Tag) kv[i + 1]);
        return c;
    }

    @Test
    void decodeKeepsGoodEntriesAsPartialAndReportsMissedInput() {
        var ctx = RegistryAccess.SERVER.createSerializationContext(NbtOps.INSTANCE);
        CompoundTag in = compound("minecraft:repair_cost", IntTag.valueOf(2), "minecraft:nope", new CompoundTag());
        DataResult<DataComponentPatch> r = DataComponentPatch.CODEC.parse(ctx, in);
        assertTrue(r.error().isPresent());
        String message = r.error().get().message();
        assertTrue(message.startsWith("No component with type: 'minecraft:nope'"), message);
        assertTrue(message.endsWith(" missed input: {\"minecraft:nope\":{}}"), message);
        DataComponentPatch partial = r.resultOrPartial().orElseThrow();
        assertEquals(Map.of("minecraft:repair_cost", "2"), partial.furnaceSet());
        assertTrue(partial.furnaceRemoved().isEmpty());
    }

    @Test
    void decodeJoinsErrorsInInputOrderAndKeepsTheRestAsPartial() {
        var ctx = RegistryAccess.SERVER.createSerializationContext(NbtOps.INSTANCE);
        CompoundTag in = compound("minecraft:repair_cost", IntTag.valueOf(2), "minecraft:max_stack_size", StringTag.valueOf("x"),
                "minecraft:nope", new CompoundTag(), "!minecraft:damage", new CompoundTag());
        DataResult<DataComponentPatch> r = DataComponentPatch.CODEC.parse(ctx, in);
        String message = r.error().orElseThrow().message();
        assertTrue(message.indexOf("; ") > 0, message);
        assertTrue(message.contains("minecraft:nope") && message.contains("minecraft:max_stack_size"), message);
        DataComponentPatch partial = r.resultOrPartial().orElseThrow();
        assertEquals(Map.of("minecraft:repair_cost", "2"), partial.furnaceSet());
        assertEquals(Set.of("minecraft:damage"), partial.furnaceRemoved());
    }

    @Test
    void duplicateKeysAreAnErrorButOneEntryStays() {
        var ctx = RegistryAccess.SERVER.createSerializationContext(NbtOps.INSTANCE);
        CompoundTag in = compound("minecraft:repair_cost", IntTag.valueOf(2), "repair_cost", IntTag.valueOf(3));
        DataResult<DataComponentPatch> r = DataComponentPatch.CODEC.parse(ctx, in);
        String m = r.error().orElseThrow().message();
        assertTrue(m.contains("Duplicate entry for key: '"), m);   // 복합 태그의 키 순서는 해시라 어느 쪽이 먼저 읽히는지는 정해져 있지 않다
        assertEquals(1, r.resultOrPartial().orElseThrow().furnaceSet().size());
    }

    @Test
    void nonNbtOpsWriteBooleansAndNumbersByFieldType() {
        var ctx = RegistryAccess.SERVER.createSerializationContext(PlainOps.INSTANCE);
        DataComponentPatch p = DataComponentPatch.furnaceOf(
                Map.of("minecraft:unbreakable", "{show_in_tooltip:0b}", "minecraft:repair_cost", "5"), Set.of("minecraft:damage"));
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) DataComponentPatch.CODEC.encodeStart(ctx, p).getOrThrow(IllegalStateException::new);
        assertEquals(Map.of("show_in_tooltip", false), out.get("minecraft:unbreakable"));   // NBT 꼴이면 0b → 0
        assertEquals(5, out.get("minecraft:repair_cost"));
        assertEquals(Map.of(), out.get("!minecraft:damage"));
        assertEquals(3, out.size());
    }

    @Test
    void nonNbtOpsWriteNbtStoredValuesLikeVanilla() {
        var ctx = RegistryAccess.SERVER.createSerializationContext(PlainOps.INSTANCE);
        DataComponentPatch p = DataComponentPatch.furnaceOf(Map.of("minecraft:custom_data", "{a:1b,b:[1,2],c:\"x\"}"), Set.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) DataComponentPatch.CODEC.encodeStart(ctx, p).getOrThrow(IllegalStateException::new);
        Map<?, ?> data = assertInstanceOf(Map.class, out.get("minecraft:custom_data"));
        assertEquals((byte) 1, data.get("a"));   // NbtOps.convertTo(JsonOps) 가 바이트를 그 수로 옮기는 것과 같다
        assertEquals(List.of(1, 2), data.get("b"));
        assertEquals("x", data.get("c"));
    }

    @Test
    void nonNbtOpsRoundTrip() {
        var ctx = RegistryAccess.SERVER.createSerializationContext(PlainOps.INSTANCE);
        DataComponentPatch p = DataComponentPatch.furnaceOf(
                Map.of("minecraft:unbreakable", "{show_in_tooltip:0b}", "minecraft:repair_cost", "5"), Set.of("minecraft:damage"));
        Object json = DataComponentPatch.CODEC.encodeStart(ctx, p).getOrThrow(IllegalStateException::new);
        DataComponentPatch back = DataComponentPatch.CODEC.parse(ctx, json).getOrThrow(IllegalStateException::new);
        assertEquals(p.furnaceSet().keySet(), back.furnaceSet().keySet());
        assertEquals(p.furnaceRemoved(), back.furnaceRemoved());
        assertEquals("5", back.furnaceSet().get("minecraft:repair_cost"));
    }

    @Test
    void nonNbtOpsKeepOriginalValuesInMissedInput() {
        var ctx = RegistryAccess.SERVER.createSerializationContext(PlainOps.INSTANCE);
        Map<String, Object> in = new java.util.LinkedHashMap<>();
        in.put("minecraft:repair_cost", 2);
        in.put("minecraft:nope", Map.of("flag", true));
        DataResult<DataComponentPatch> r = DataComponentPatch.CODEC.parse(ctx, in);
        String message = r.error().orElseThrow().message();
        assertTrue(message.endsWith(" missed input: {minecraft:nope={flag=true}}"), message);   // 못 읽은 항목은 받은 값 그대로(참·거짓이 0/1 로 바뀌지 않는다)
        assertFalse(r.resultOrPartial().orElseThrow().furnaceSet().isEmpty());
    }
}
