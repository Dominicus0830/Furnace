// 아이템 스택 코덱(바닐라 ItemStack.CODEC·SINGLE_ITEM_CODEC·STREAM_CODEC·OPTIONAL_STREAM_CODEC).
// 컴포넌트 안에 든 아이템(묶음·용기·쇠뇌 탄 등)의 정규 값: {"id": 이름, "count": 수, "components"?: {"minecraft:x": 값, "!minecraft:y": {}}}.
package org.domi.furnace.server.item;

import io.netty.buffer.ByteBuf;
import java.util.LinkedHashMap;
import java.util.Map;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public final class ItemStackCodecs {
    private ItemStackCodecs() {}

    public static final String AIR = "minecraft:air";
    public static final int MAX_COUNT = 99;

    /** 아이템 이름(공기 아님, 아이템 레지스트리에 있음). */
    static final PCodec ITEM_NON_AIR = PCodec.registryId("minecraft:item").validate(v -> !AIR.equals(v), v -> "Item must not be minecraft:air");

    /** ItemStack.CODEC — id, count(1..99, 없으면 1, 항상 씀), components(비면 안 씀). */
    public static final PCodec ITEM_STACK = new PCodec() {
        public Object normalize(Object in) {
            Map<String, Object> m = Val.map(in);
            Object rawId = m.get("id");
            if (rawId == null) throw new CodecException("No key id in MapLike[" + Val.describe(m) + "]");
            String id = (String) ITEM_NON_AIR.normalize(rawId);
            int count = 1;
            if (m.get("count") instanceof Number n) {
                int c = n.intValue();
                if (c >= 1 && c <= MAX_COUNT) count = c;   // fieldOf("count").orElse(1): 없거나 범위 밖·잘못된 꼴이면 1
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", id);
            out.put("count", count);
            Object comps = m.get("components");
            if (comps != null) {
                ComponentPatch p = ComponentPatch.fromCodec(comps).normalizedFor(id);
                if (!p.isEmpty()) out.put("components", p.toCodecMap());
            }
            return out;
        }
        public Object toNbt(Object v) {
            Map<String, Object> m = Val.map(v);
            NbtCompound c = new NbtCompound();
            c.putString("id", (String) m.get("id"));
            c.putInt("count", (Integer) m.get("count"));
            if (m.get("components") != null) c.put("components", patchOf(m).toNbt());
            return c;
        }
    };

    /** ItemStack.SINGLE_ITEM_CODEC — 개수 없이 id, components(개수 1). 정규 값에는 count 를 넣지 않는다. */
    public static final PCodec SINGLE_ITEM = new PCodec() {
        public Object normalize(Object in) {
            Map<String, Object> m = Val.map(in);
            Object rawId = m.get("id");
            if (rawId == null) throw new CodecException("No key id in MapLike[" + Val.describe(m) + "]");
            String id = (String) ITEM_NON_AIR.normalize(rawId);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", id);
            Object comps = m.get("components");
            if (comps != null) {
                ComponentPatch p = ComponentPatch.fromCodec(comps).normalizedFor(id);
                if (!p.isEmpty()) out.put("components", p.toCodecMap());
            }
            return out;
        }
        public Object toNbt(Object v) {
            Map<String, Object> m = Val.map(v);
            NbtCompound c = new NbtCompound();
            c.putString("id", (String) m.get("id"));
            if (m.get("components") != null) c.put("components", patchOf(m).toNbt());
            return c;
        }
    };

    /** 정규 아이템 값의 컴포넌트 패치. */
    public static ComponentPatch patchOf(Map<String, Object> stack) {
        Object comps = stack.get("components");
        if (comps == null) return ComponentPatch.EMPTY;
        Map<String, Object> raw = new LinkedHashMap<>();
        for (var e : Val.map(comps).entrySet()) {
            String k = e.getKey();
            if (k.startsWith("!")) raw.put(k.substring(1), null);
            else raw.put(k, e.getValue());
        }
        return ComponentPatch.ofNormalized(raw);
    }

    // ------------------------------------------------------------------ 네트워크

    /** OPTIONAL_STREAM_CODEC: VarInt 개수(0 = 빈 스택) → 아이템 id → 패치. 빈 스택의 정규 값은 null. */
    public static final NCodec OPTIONAL_STREAM = new NCodec() {
        public Object read(ByteBuf buf) {
            ItemStack s = readStack(buf);
            return s.isEmpty() ? null : toCanonical(s);
        }
        public void write(ByteBuf buf, Object v) { writeStack(buf, v == null ? ItemStack.EMPTY : fromCanonical(Val.map(v))); }
    };

    /** STREAM_CODEC: 빈 스택을 받지 않는다. */
    public static final NCodec STREAM = new NCodec() {
        public Object read(ByteBuf buf) {
            ItemStack s = readStack(buf);
            if (s.isEmpty()) throw new CodecException("Empty ItemStack not allowed");
            return toCanonical(s);
        }
        public void write(ByteBuf buf, Object v) {
            ItemStack s = fromCanonical(Val.map(v));
            if (s.isEmpty()) throw new CodecException("Empty ItemStack not allowed");
            writeStack(buf, s);
        }
    };

    public static ItemStack readStack(ByteBuf buf) {
        int count = Types.readVarInt(buf);
        if (count <= 0) return ItemStack.EMPTY;
        int itemId = Types.readVarInt(buf);
        String name = ItemContext.name("minecraft:item", itemId);
        if (name == null) throw new CodecException("Unknown item id: " + itemId);
        ComponentPatch patch = ComponentPatch.read(buf).normalizedFor(name);
        return new ItemStack(itemId, count, patch);
    }

    public static void writeStack(ByteBuf buf, ItemStack s) {
        if (s == null || s.isEmpty()) {
            Types.writeVarInt(buf, 0);
            return;
        }
        Types.writeVarInt(buf, s.count());
        Types.writeVarInt(buf, s.itemId());
        s.patch().write(buf);
    }

    /** 바닐라 getMaxStackSize: 패치의 max_stack_size, 없으면 아이템 기본값, 그것도 없으면 1. */
    public static int maxStackSize(ItemStack s) {
        Object v = s.patch().entries().get("minecraft:max_stack_size");
        if (v instanceof Integer i) return i;
        if (s.patch().entries().containsKey("minecraft:max_stack_size")) return 1;   // 지움 표시
        String name = ItemContext.installed() ? ItemContext.name("minecraft:item", s.itemId()) : null;
        return name == null ? 64 : ItemPrototypes.maxStackSize(name);
    }

    public static Map<String, Object> toCanonical(ItemStack s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", ItemContext.name("minecraft:item", s.itemId()));
        m.put("count", s.count());
        if (!s.patch().isEmpty()) m.put("components", s.patch().toCodecMap());
        return m;
    }

    public static ItemStack fromCanonical(Map<String, Object> m) {
        Integer id = ItemContext.id("minecraft:item", (String) m.get("id"));
        if (id == null) throw new CodecException("Unknown item: " + m.get("id"));
        int count = m.get("count") instanceof Number n ? n.intValue() : 1;
        return new ItemStack(id, count, patchOf(m));
    }
}
