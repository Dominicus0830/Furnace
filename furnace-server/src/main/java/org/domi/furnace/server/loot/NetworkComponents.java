// 컴포넌트 패치 → 네트워크 바이트(바닐라 DataComponentPatch.STREAM_CODEC: 추가 수, 제거 수, (종류 id, 값)…, 제거 종류 id…).
// 값 모양은 전리품이 만드는 JSON 꼴(LootStack.patch). 네트워크 코덱이 없는 컴포넌트(map_decorations 등)는 보내지 않는다.
package org.domi.furnace.server.loot;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.net.io.Types;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NetworkComponents {

    private static final Logger log = LoggerFactory.getLogger(NetworkComponents.class);

    /** 바닐라에서 네트워크로 보내지 않는(persistent 전용) 컴포넌트. */
    private static final Set<String> NOT_SYNCED = Set.of("minecraft:map_decorations", "minecraft:custom_data", "minecraft:bucket_entity_data",
            "minecraft:block_entity_data", "minecraft:entity_data", "minecraft:recipes", "minecraft:lock", "minecraft:container_loot",
            "minecraft:debug_stick_state", "minecraft:intangible_projectile", "minecraft:creative_slot_lock", "minecraft:map_post_processing");

    private final TagRegistries regs;

    public NetworkComponents(TagRegistries regs) { this.regs = regs; }

    /** 전리품 스택 → 서버 아이템 스택(아이템 id·개수·컴포넌트 바이트). */
    public ItemStack toItemStack(LootStack s) {
        if (s.isEmpty()) return ItemStack.EMPTY;
        Integer id = regs.id("minecraft:item", ResourceId.parse(s.item()));
        if (id == null) return ItemStack.EMPTY;
        byte[] comp = s.patch().isEmpty() ? null : encode(s.patch());
        return new ItemStack(id, s.count(), comp);
    }

    public byte[] encode(Map<String, Object> patch) {
        ByteBuf added = Unpooled.buffer();
        List<Integer> removed = new ArrayList<>();
        int addedCount = 0;
        for (var e : patch.entrySet()) {
            if (NOT_SYNCED.contains(e.getKey())) continue;
            Integer type = id("minecraft:data_component_type", e.getKey());
            if (type == null) continue;
            if (e.getValue() == null) {
                removed.add(type);
                continue;
            }
            int mark = added.writerIndex();
            try {
                Types.writeVarInt(added, type);
                writeValue(added, e.getKey(), e.getValue());
                addedCount++;
            } catch (RuntimeException ex) {
                added.writerIndex(mark);
                log.warn("아이템 컴포넌트 {} 를 네트워크로 바꾸지 못함: {}", e.getKey(), ex.getMessage());
            }
        }
        ByteBuf out = Unpooled.buffer();
        Types.writeVarInt(out, addedCount);
        Types.writeVarInt(out, removed.size());
        out.writeBytes(added);
        for (int t : removed) Types.writeVarInt(out, t);
        byte[] b = new byte[out.readableBytes()];
        out.readBytes(b);
        added.release();
        out.release();
        return b;
    }

    private Integer id(String registry, String element) { return regs.id(registry, ResourceId.parse(element)); }

    private int requireId(String registry, Object element) {
        Integer i = id(registry, (String) element);
        if (i == null) throw new IllegalArgumentException(registry + " 에 없음: " + element);
        return i;
    }

    @SuppressWarnings("unchecked")
    private void writeValue(ByteBuf buf, String type, Object v) {
        switch (type) {
            case "minecraft:damage", "minecraft:max_damage", "minecraft:max_stack_size", "minecraft:repair_cost", "minecraft:map_id",
                 "minecraft:ominous_bottle_amplifier", "minecraft:custom_model_data" -> Types.writeVarInt(buf, ((Number) v).intValue());
            case "minecraft:enchantments", "minecraft:stored_enchantments" -> {
                Map<String, Object> m = (Map<String, Object>) v;
                Map<String, Object> levels = (Map<String, Object>) m.getOrDefault("levels", Map.of());
                Types.writeVarInt(buf, levels.size());
                for (var e : levels.entrySet()) {
                    Types.writeVarInt(buf, requireId("minecraft:enchantment", e.getKey()));
                    Types.writeVarInt(buf, ((Number) e.getValue()).intValue());
                }
                buf.writeBoolean(!(m.get("show_in_tooltip") instanceof Boolean b) || b);
            }
            case "minecraft:potion_contents" -> {
                Map<String, Object> m = (Map<String, Object>) v;
                buf.writeBoolean(m.containsKey("potion"));
                if (m.containsKey("potion")) Types.writeVarInt(buf, requireId("minecraft:potion", m.get("potion")));
                buf.writeBoolean(m.containsKey("custom_color"));
                if (m.containsKey("custom_color")) buf.writeInt(((Number) m.get("custom_color")).intValue());
                List<Object> effects = (List<Object>) m.getOrDefault("custom_effects", List.of());
                if (!effects.isEmpty()) throw new IllegalArgumentException("custom_effects 인코딩은 아직 없다");
                Types.writeVarInt(buf, 0);
            }
            case "minecraft:suspicious_stew_effects" -> {
                List<Object> list = (List<Object>) v;
                Types.writeVarInt(buf, list.size());
                for (Object o : list) {
                    Map<String, Object> e = (Map<String, Object>) o;
                    Types.writeVarInt(buf, requireId("minecraft:mob_effect", e.get("id")));
                    Types.writeVarInt(buf, e.get("duration") instanceof Number n ? n.intValue() : 160);
                }
            }
            case "minecraft:instrument" -> Types.writeVarInt(buf, requireId("minecraft:instrument", v) + 1);   // 레지스트리 참조 = id + 1
            case "minecraft:item_name", "minecraft:custom_name" -> buf.writeBytes(textNbt((String) v));
            case "minecraft:lore" -> {
                List<Object> lines = (List<Object>) v;
                Types.writeVarInt(buf, lines.size());
                for (Object l : lines) buf.writeBytes(textNbt((String) l));
            }
            case "minecraft:block_state" -> {
                Map<String, Object> m = (Map<String, Object>) v;
                Types.writeVarInt(buf, m.size());
                for (var e : m.entrySet()) {
                    Types.writeString(buf, e.getKey());
                    Types.writeString(buf, String.valueOf(e.getValue()));
                }
            }
            case "minecraft:trim" -> {
                Map<String, Object> m = (Map<String, Object>) v;
                Types.writeVarInt(buf, requireId("minecraft:trim_material", m.get("material")) + 1);
                Types.writeVarInt(buf, requireId("minecraft:trim_pattern", m.get("pattern")) + 1);
                buf.writeBoolean(!(m.get("show_in_tooltip") instanceof Boolean b) || b);
            }
            case "minecraft:enchantment_glint_override" -> buf.writeBoolean((Boolean) v);
            case "minecraft:unbreakable" -> buf.writeBoolean(!(((Map<String, Object>) v).get("show_in_tooltip") instanceof Boolean b) || b);
            case "minecraft:rarity" -> Types.writeVarInt(buf, List.of("common", "uncommon", "rare", "epic").indexOf((String) v));
            default -> throw new IllegalArgumentException("네트워크 인코딩을 모르는 컴포넌트");
        }
    }

    /** 텍스트 컴포넌트 JSON 문자열 → 네트워크 NBT(이름 없는 루트, 바닐라 ComponentSerialization). */
    static byte[] textNbt(String json) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bos);
            writeTag(out, JsonParser.parseString(json), true);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeTag(DataOutputStream out, JsonElement e, boolean withType) throws IOException {
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
            if (withType) out.writeByte(8);
            out.writeUTF(e.getAsString());
        } else if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) {
            if (withType) out.writeByte(1);
            out.writeByte(e.getAsBoolean() ? 1 : 0);
        } else if (e.isJsonPrimitive()) {
            if (withType) out.writeByte(3);
            out.writeInt(e.getAsInt());
        } else if (e.isJsonArray()) {
            // 텍스트 목록(extra·with): 바닐라처럼 요소를 모두 컴파운드로 맞춘다(문자열은 {"text": …}).
            if (withType) out.writeByte(9);
            out.writeByte(10);
            out.writeInt(e.getAsJsonArray().size());
            for (JsonElement x : e.getAsJsonArray()) {
                if (x.isJsonObject()) writeTag(out, x, false);
                else {
                    com.google.gson.JsonObject w = new com.google.gson.JsonObject();
                    w.add("text", x);
                    writeTag(out, w, false);
                }
            }
        } else {
            if (withType) out.writeByte(10);
            for (var f : e.getAsJsonObject().entrySet()) {
                JsonElement v = f.getValue();
                int t = v.isJsonObject() ? 10 : v.isJsonArray() ? 9 : v.getAsJsonPrimitive().isString() ? 8
                        : v.getAsJsonPrimitive().isBoolean() ? 1 : 3;
                out.writeByte(t);
                out.writeUTF(f.getKey());
                writeTag(out, v, false);
            }
            out.writeByte(0);
        }
    }
}
