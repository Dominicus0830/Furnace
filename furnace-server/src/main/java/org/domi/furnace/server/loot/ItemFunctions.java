// 바닐라 1.21.1 전리품 함수 중 컴포넌트를 직접 만지는 것들 — set_custom_data·copy_custom_data·set_attributes·set_contents·
// modify_contents·set_loot_table·set_lore·fill_player_head·set_banner_pattern·set_fireworks·set_firework_explosion·
// set_book_cover·set_written_book_pages·set_writable_book_pages. 난수 호출 순서는 바닐라 바이트코드 그대로.
package org.domi.furnace.server.loot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.command.arguments.NbtPathArgument;
import org.domi.furnace.server.command.arguments.TagParser;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.item.PCodec;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.net.io.nbt.NbtTags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ItemFunctions {
    private ItemFunctions() {}

    private static final Logger LOG = LoggerFactory.getLogger(ItemFunctions.class);

    private static final String CUSTOM_DATA = "minecraft:custom_data";

    /** 처리하는 함수면 만들어 돌려주고, 아니면 null. */
    static LootFunction parse(String type, JsonObject o, LootRegistries regs) {
        return switch (type) {
            case "minecraft:set_custom_data" -> setCustomData(o);
            case "minecraft:copy_custom_data" -> copyCustomData(o);
            case "minecraft:set_attributes" -> setAttributes(o, regs);
            case "minecraft:set_contents" -> setContents(o, regs);
            case "minecraft:modify_contents" -> modifyContents(o, regs);
            case "minecraft:set_loot_table" -> setLootTable(o, regs);
            case "minecraft:set_lore" -> setLore(o);
            case "minecraft:fill_player_head" -> fillPlayerHead(o);
            case "minecraft:set_banner_pattern" -> setBannerPattern(o);
            case "minecraft:set_fireworks" -> setFireworks(o);
            case "minecraft:set_firework_explosion" -> setFireworkExplosion(o);
            case "minecraft:set_book_cover" -> setBookCover(o);
            case "minecraft:set_written_book_pages" -> setWrittenBookPages(o);
            case "minecraft:set_writable_book_pages" -> setWritableBookPages(o);
            default -> null;
        };
    }

    // ------------------------------------------------------------------ custom_data

    /** 스택의 custom_data 복사본(없으면 빈 compound). 값은 NbtCompound·SNBT 문자열·JSON 꼴 맵 모두 받는다. */
    static NbtCompound customData(LootStack s) {
        Object v = s.get(CUSTOM_DATA);
        if (v == null) return new NbtCompound();
        return (NbtCompound) PCodec.COMPOUND.normalize(v);
    }

    /** 바닐라 CustomData.set — 비었으면 컴포넌트를 지운다. */
    private static void putCustomData(LootStack s, NbtCompound tag) {
        if (tag.tags().isEmpty()) s.remove(CUSTOM_DATA);
        else s.set(CUSTOM_DATA, tag);
    }

    private static NbtCompound parseCompound(JsonElement e) {
        try {
            if (e.isJsonObject()) return (NbtCompound) PCodec.COMPOUND.normalize(Components.fromJson(e));
            return TagParser.parseTag(e.getAsString());
        } catch (CommandSyntaxException ex) {
            throw new LootParseException("NBT 를 읽을 수 없다: " + ex.getMessage());
        }
    }

    private static LootFunction setCustomData(JsonObject o) {
        NbtCompound tag = parseCompound(LootJson.req(o, "tag"));
        return (s, c) -> {
            NbtCompound data = customData(s);
            NbtTags.merge(data, tag.copy());
            putCustomData(s, data);
            return s;
        };
    }

    /** 바닐라 NbtProvider — context(this·attacker·direct_attacker·attacking_player·block_entity) 또는 storage. */
    private interface NbtSource { Object get(LootContext c); }

    private static NbtSource nbtSource(JsonElement e) {
        if (e.isJsonPrimitive()) return contextSource(e.getAsString());
        JsonObject o = e.getAsJsonObject();
        String type = LootJson.id(o, "type");
        return switch (type) {
            case "minecraft:context" -> contextSource(LootJson.req(o, "target").getAsString());
            case "minecraft:storage" -> {
                String id = LootJson.id(o, "source");
                yield c -> c.world().storageNbt(id);
            }
            default -> throw new LootParseException("모르는 NBT 제공자: " + type);
        };
    }

    private static NbtSource contextSource(String target) {
        if (target.equals("block_entity")) {
            // 전리품 문맥의 블록 엔티티는 컴포넌트 값으로만 들고 있어 NBT 를 만들 수 없다 — 아무것도 복사하지 않는다(가정)
            return c -> null;
        }
        LootParams.Key<LootEntity> key = LootCondition.entityTarget(target);
        return c -> {
            LootEntity en = c.param(key);
            return en == null ? null : en.nbt();
        };
    }

    private record CopyOp(NbtPathArgument.NbtPath source, NbtPathArgument.NbtPath target, String op) {
        /** 바닐라 CopyOperation.apply: 원본 경로에서 찾은 게 있을 때만 병합 방식대로 대상에 쓴다. */
        void apply(java.util.function.Supplier<NbtCompound> intoSupplier, Object src) {
            try {
                List<Object> found = source.get(src);
                if (found.isEmpty()) return;
                NbtCompound into = intoSupplier.get();   // 원본 경로에 값이 있을 때만 대상 복사본을 만든다(바닐라 공급자)
                switch (op) {
                    case "replace" -> target.set(into, found.get(found.size() - 1));
                    case "append" -> {
                        for (Object t : target.getOrCreate(into, () -> new NbtList(org.domi.furnace.server.net.io.nbt.NbtIo.TAG_END))) {
                            if (t instanceof NbtList list) {
                                for (Object n : found) list.addTag(list.size(), NbtTags.copy(n), NbtTags.typeOf(n));
                            }
                        }
                    }
                    default -> {
                        for (Object t : target.getOrCreate(into, NbtCompound::new)) {
                            if (t instanceof NbtCompound tc) {
                                for (Object n : found) if (n instanceof NbtCompound nc) NbtTags.merge(tc, nc);
                            }
                        }
                    }
                }
            } catch (CommandSyntaxException ignored) {
                // 바닐라도 경로 오류는 조용히 넘긴다
            }
        }
    }

    private static NbtPathArgument.NbtPath path(String s) {
        try {
            return NbtPathArgument.of(s);
        } catch (CommandSyntaxException ex) {
            throw new LootParseException("NBT 경로를 읽을 수 없다: " + s + " (" + ex.getMessage() + ")");
        }
    }

    private static LootFunction copyCustomData(JsonObject o) {
        NbtSource source = nbtSource(LootJson.req(o, "source"));
        List<CopyOp> ops = new ArrayList<>();
        for (JsonElement x : LootJson.req(o, "ops").getAsJsonArray()) {
            JsonObject op = x.getAsJsonObject();
            String mode = LootJson.req(op, "op").getAsString();
            if (!mode.equals("replace") && !mode.equals("append") && !mode.equals("merge")) throw new LootParseException("모르는 병합 방식: " + mode);
            ops.add(new CopyOp(path(LootJson.req(op, "source").getAsString()), path(LootJson.req(op, "target").getAsString()), mode));
        }
        return (s, c) -> {
            Object src = source.get(c);
            if (src == null) return s;
            NbtCompound[] data = {null};   // 값이 있는 첫 연산이 기존 값을 복사한다(아무 연산도 안 맞으면 컴포넌트를 건드리지 않는다)
            java.util.function.Supplier<NbtCompound> supplier = () -> data[0] != null ? data[0] : (data[0] = customData(s));
            for (CopyOp op : ops) op.apply(supplier, src);
            if (data[0] != null) putCustomData(s, data[0]);
            return s;
        };
    }

    // ------------------------------------------------------------------ 속성 변경치

    private static final List<String> ATTRIBUTE_OPS = List.of("add_value", "add_multiplied_base", "add_multiplied_total");
    private static final List<String> SLOT_GROUPS = List.of("any", "mainhand", "offhand", "hand", "feet", "legs", "chest", "head", "armor", "body");
    private static final String ATTRIBUTES = "minecraft:attribute_modifiers";

    private record AttributeModifier(String id, String attribute, String operation, NumberProvider amount, List<String> slots) {}

    @SuppressWarnings("unchecked")
    private static LootFunction setAttributes(JsonObject o, LootRegistries regs) {
        List<AttributeModifier> mods = new ArrayList<>();
        for (JsonElement x : LootJson.req(o, "modifiers").getAsJsonArray()) {
            JsonObject m = x.getAsJsonObject();
            String attribute = LootJson.id(m, "attribute");
            if (!regs.exists("minecraft:attribute", attribute)) throw new LootParseException("Unknown registry key in minecraft:attribute: " + attribute);
            String op = LootJson.req(m, "operation").getAsString();
            if (!ATTRIBUTE_OPS.contains(op)) throw new LootParseException("모르는 속성 연산: " + op);
            List<String> slots = LootJson.list(m, "slot", e -> {
                String slot = e.getAsString();
                if (!SLOT_GROUPS.contains(slot)) throw new LootParseException("모르는 장비 칸 묶음: " + slot);
                return slot;
            });
            if (slots.isEmpty()) throw new LootParseException("Value must be a non-empty list: slot");
            mods.add(new AttributeModifier(LootJson.id(m, "id"), attribute, op, NumberProvider.parse(LootJson.req(m, "amount")), slots));
        }
        boolean replace = LootJson.bool(o, "replace", true);
        return (s, c) -> {
            List<Object> entries = new ArrayList<>();
            boolean show = true;
            if (!replace) {
                Object cur = s.get(ATTRIBUTES);
                if (!(cur instanceof Map<?, ?> cm) || !(cm.get("modifiers") instanceof List<?> l) || l.isEmpty()) {
                    cur = org.domi.furnace.server.loot.ItemData.vanilla().defaultComponent(s.item(), ATTRIBUTES);   // 비었으면 아이템 기본값 위에 얹는다
                }
                if (cur instanceof Map<?, ?> cm && cm.get("modifiers") instanceof List<?> l) {
                    entries.addAll((List<Object>) LootStack.deepCopy(l));
                    if (cm.get("show_in_tooltip") instanceof Boolean b) show = b;
                }
            }
            for (AttributeModifier m : mods) {
                String slot = m.slots().get(c.random().nextInt(m.slots().size()));   // Util.getRandom 이 먼저
                float amount = m.amount().getFloat(c);
                // 바닐라 ItemAttributeModifiers.withModifierAdded: 같은 (속성, id) 항목을 먼저 지우고 끝에 넣는다
                entries.removeIf(x -> x instanceof Map<?, ?> xm && m.attribute().equals(xm.get("type")) && m.id().equals(xm.get("id")));
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("type", m.attribute());
                e.put("id", m.id());
                e.put("amount", (double) amount);
                e.put("operation", m.operation());
                e.put("slot", slot);
                entries.add(e);
            }
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("modifiers", entries);
            if (!show) v.put("show_in_tooltip", false);
            s.set(ATTRIBUTES, v);
            return s;
        };
    }

    // ------------------------------------------------------------------ 컨테이너 내용

    private static final List<String> CONTAINER_COMPONENTS = List.of("minecraft:container", "minecraft:bundle_contents", "minecraft:charged_projectiles");

    private static String containerComponent(JsonObject o) {
        String comp = LootJson.id(o, "component");
        if (!CONTAINER_COMPONENTS.contains(comp)) throw new LootParseException("Unknown element name:" + comp);
        return comp;
    }

    /** 컴포넌트 안에 든 아이템 정규 값 {id, count, components?} → 전리품 스택. */
    @SuppressWarnings("unchecked")
    static LootStack unembed(Object item) {
        Map<String, Object> m = (Map<String, Object>) item;
        Map<String, Object> patch = new LinkedHashMap<>();
        if (m.get("components") instanceof Map<?, ?> comps) {
            for (var e : comps.entrySet()) {
                String k = (String) e.getKey();
                if (k.startsWith("!")) patch.put(k.substring(1), null);
                else patch.put(k, e.getValue());
            }
        }
        return LootStack.of((String) m.get("id"), m.get("count") instanceof Number n ? n.intValue() : 1, patch);
    }

    static Map<String, Object> embed(LootStack s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.item());
        m.put("count", s.count());
        Map<String, Object> patch = s.encodedPatch();
        if (!patch.isEmpty()) m.put("components", patch);
        return m;
    }

    /** 컨테이너 칸 순서의 아이템(빈 칸은 빈 스택). */
    @SuppressWarnings("unchecked")
    private static List<LootStack> contents(String comp, Object value) {
        List<LootStack> out = new ArrayList<>();
        if (!(value instanceof List<?> list)) return out;
        if (comp.equals("minecraft:container")) {
            int size = 0;
            for (Object slot : list) size = Math.max(size, ((Number) ((Map<String, Object>) slot).get("slot")).intValue() + 1);
            for (int i = 0; i < size; i++) out.add(LootStack.empty());
            for (Object slot : list) {
                Map<String, Object> sm = (Map<String, Object>) slot;
                out.set(((Number) sm.get("slot")).intValue(), unembed(sm.get("item")));
            }
        } else {
            for (Object item : list) out.add(unembed(item));
        }
        return out;
    }

    private static void putContents(LootStack s, String comp, List<LootStack> items) {
        List<Object> out = new ArrayList<>();
        if (comp.equals("minecraft:container")) {
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).isEmpty()) continue;
                Map<String, Object> slot = new LinkedHashMap<>();
                slot.put("slot", i);
                slot.put("item", embed(items.get(i)));
                out.add(slot);
            }
        } else {
            for (LootStack it : items) if (!it.isEmpty()) out.add(embed(it));
        }
        s.set(comp, out);
    }

    private static LootFunction setContents(JsonObject o, LootRegistries regs) {
        String comp = containerComponent(o);
        List<LootEntry> entries = LootJson.list(o, "entries", e -> LootEntry.parse(e, regs));
        return (s, c) -> {
            if (s.isEmpty()) return s;
            List<LootStack> items = new ArrayList<>();
            for (LootEntry en : entries) en.expand(c, cand -> cand.create(LootTable.splitter(items::add), c));
            putContents(s, comp, items);
            return s;
        };
    }

    private static LootFunction modifyContents(JsonObject o, LootRegistries regs) {
        String comp = containerComponent(o);
        LootFunction modifier = LootFunction.parse(LootJson.req(o, "modifier"), regs);
        return (s, c) -> {
            if (s.isEmpty() || s.get(comp) == null) return s;
            List<LootStack> items = new ArrayList<>();
            for (LootStack it : contents(comp, s.get(comp))) {
                if (it.isEmpty()) { items.add(it); continue; }
                LootStack r = modifier.apply(it, c);
                r.limitSize(r.maxStackSize());
                items.add(r);
            }
            putContents(s, comp, items);
            return s;
        };
    }

    private static LootFunction setLootTable(JsonObject o, LootRegistries regs) {
        String name = LootJson.id(o, "name");
        long seed = o.has("seed") ? o.get("seed").getAsLong() : 0L;
        String type = LootJson.id(o, "type");
        if (!regs.exists("minecraft:block_entity_type", type)) throw new LootParseException("Unknown registry key in minecraft:block_entity_type: " + type);
        return (s, c) -> {
            if (s.isEmpty()) return s;
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("loot_table", name);
            v.put("seed", seed);
            s.set("minecraft:container_loot", v);
            return s;
        };
    }

    // ------------------------------------------------------------------ 목록 연산(ListOperation)

    /** 바닐라 ListOperation — mode: replace_all | replace_section(offset, size?) | insert(offset) | append. */
    record ListOp(String mode, int offset, Integer size) {
        static ListOp parse(JsonObject o, int maxSize) {
            String mode = LootJson.req(o, "mode").getAsString();
            int offset = 0;
            Integer size = null;
            switch (mode) {
                case "replace_all", "append" -> { }
                case "replace_section" -> {
                    offset = nonNegative(o, "offset", 0);
                    if (o.has("size")) size = nonNegative(o, "size", 0);
                }
                case "insert" -> offset = nonNegative(o, "offset", 0);
                default -> throw new LootParseException("Unknown element name:" + mode);
            }
            // 바닐라 ListOperation.codec 검증: replace_section 의 size 가 최대보다 크면 거부(offset 초과는 실행 때 로그만)
            if (mode.equals("replace_section") && size != null && size > maxSize) {
                throw new LootParseException("Size value too large: " + size + ", max size is " + maxSize);
            }
            return new ListOp(mode, offset, size);
        }

        private static int nonNegative(JsonObject o, String key, int def) {
            int v = o.has(key) ? o.get(key).getAsInt() : def;
            if (v < 0) throw new LootParseException("Value must be non-negative: " + v);
            return v;
        }

        <T> List<T> apply(List<T> existing, List<T> replacement, int maxSize) {
            switch (mode) {
                case "replace_all":
                    return replacement;
                case "append": {
                    if (existing.size() + replacement.size() > maxSize) {
                        LOG.error("Contents overflow in section append");
                        return existing;
                    }
                    List<T> out = new ArrayList<>(existing);
                    out.addAll(replacement);
                    return out;
                }
                case "insert": {
                    if (offset > existing.size()) {
                        LOG.error("Cannot insert when offset is out of bounds");
                        return existing;
                    }
                    List<T> out = new ArrayList<>(existing.subList(0, offset));
                    out.addAll(replacement);
                    out.addAll(existing.subList(offset, existing.size()));
                    if (out.size() > maxSize) {
                        LOG.error("Contents overflow in section insertion");
                        return existing;
                    }
                    return out;
                }
                default: {   // replace_section
                    if (offset > existing.size()) {
                        LOG.error("Cannot replace when offset is out of bounds");
                        return existing;
                    }
                    List<T> out = new ArrayList<>(existing.subList(0, offset));
                    out.addAll(replacement);
                    int end = offset + (size == null ? replacement.size() : size);
                    if (end < existing.size()) out.addAll(existing.subList(end, existing.size()));
                    if (out.size() > maxSize) {
                        LOG.error("Contents overflow in section replacement");
                        return existing;
                    }
                    return out;
                }
            }
        }
    }

    // ------------------------------------------------------------------ 이름·머리·깃발·폭죽·책

    /** 텍스트 컴포넌트 JSON → 컴포넌트 값(문자열). set_name 과 같은 방식. */
    private static String text(JsonElement e) { return e.toString(); }

    private static LootFunction setLore(JsonObject o) {
        List<Object> lore = new ArrayList<>();
        JsonArray arr = LootJson.req(o, "lore").getAsJsonArray();
        if (arr.size() > 256) throw new LootParseException("List is too long: " + arr.size() + ", expected range [0-256]");
        for (JsonElement x : arr) lore.add(text(x));
        ListOp mode = ListOp.parse(o, 256);
        return (s, c) -> {
            Object cur = s.get("minecraft:lore");
            List<Object> existing = cur instanceof List<?> l ? new ArrayList<>((List<Object>) (List<?>) l) : new ArrayList<>();
            if (cur == null && lore.isEmpty()) {
                s.set("minecraft:lore", new ArrayList<>());
                return s;
            }
            s.set("minecraft:lore", new ArrayList<>(mode.apply(existing, lore, 256)));
            return s;
        };
    }

    private static LootFunction fillPlayerHead(JsonObject o) {
        LootParams.Key<LootEntity> key = LootCondition.entityTarget(LootJson.req(o, "entity").getAsString());
        return (s, c) -> {
            if (s.is("minecraft:player_head") && c.param(key) instanceof LootEntity en && en.gameProfile() != null) {
                s.set("minecraft:profile", en.gameProfile());
            }
            return s;
        };
    }

    private static LootFunction setBannerPattern(JsonObject o) {
        List<Object> patterns = new ArrayList<>();
        for (JsonElement x : LootJson.req(o, "patterns").getAsJsonArray()) patterns.add(Components.fromJson(x));
        boolean append = LootJson.req(o, "append").getAsBoolean();
        return (s, c) -> {
            List<Object> out = new ArrayList<>();
            if (append && s.get("minecraft:banner_patterns") instanceof List<?> old) out.addAll((List<Object>) LootStack.deepCopy(old));
            out.addAll((List<Object>) LootStack.deepCopy(patterns));
            s.set("minecraft:banner_patterns", out);
            return s;
        };
    }

    @SuppressWarnings("unchecked")
    private static LootFunction setFireworks(JsonObject o) {
        List<Object> values = null;
        ListOp mode = null;
        if (o.has("explosions")) {
            JsonObject ex = o.getAsJsonObject("explosions");
            values = new ArrayList<>();
            for (JsonElement x : LootJson.req(ex, "values").getAsJsonArray()) values.add(Components.fromJson(x));
            mode = ListOp.parse(ex, Integer.MAX_VALUE);
        }
        Integer flight = null;
        if (o.has("flight_duration")) {
            flight = o.get("flight_duration").getAsInt();
            if (flight < 0 || flight > 255) throw new LootParseException("Value must be within range [0;255]: " + flight);
        }
        final List<Object> vals = values;
        final ListOp op = mode;
        final Integer fd = flight;
        return (s, c) -> {
            Map<String, Object> old = s.get("minecraft:fireworks") instanceof Map<?, ?> m ? (Map<String, Object>) LootStack.deepCopy((Map<String, Object>) m) : new LinkedHashMap<>();
            List<Object> explosions = old.get("explosions") instanceof List<?> l ? new ArrayList<>((List<Object>) l) : new ArrayList<>();
            if (op != null) explosions = new ArrayList<>(op.apply(explosions, (List<Object>) LootStack.deepCopy(vals), Integer.MAX_VALUE));
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("flight_duration", fd != null ? fd : old.get("flight_duration") instanceof Number n ? n.intValue() : 0);
            v.put("explosions", explosions);
            s.set("minecraft:fireworks", v);
            return s;
        };
    }

    private static LootFunction setFireworkExplosion(JsonObject o) {
        Object shape = o.has("shape") ? o.get("shape").getAsString() : null;
        Object colors = o.has("colors") ? Components.fromJson(o.get("colors")) : null;
        Object fade = o.has("fade_colors") ? Components.fromJson(o.get("fade_colors")) : null;
        Boolean trail = o.has("trail") ? o.get("trail").getAsBoolean() : null;
        Boolean twinkle = o.has("twinkle") ? o.get("twinkle").getAsBoolean() : null;
        return (s, c) -> {
            Map<?, ?> old = s.get("minecraft:firework_explosion") instanceof Map<?, ?> m ? m : Map.of();
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("shape", shape != null ? shape : old.containsKey("shape") ? old.get("shape") : "small_ball");
            v.put("colors", colors != null ? LootStack.deepCopy(colors) : old.containsKey("colors") ? LootStack.deepCopy(old.get("colors")) : new ArrayList<>());
            v.put("fade_colors", fade != null ? LootStack.deepCopy(fade) : old.containsKey("fade_colors") ? LootStack.deepCopy(old.get("fade_colors")) : new ArrayList<>());
            v.put("has_trail", trail != null ? trail : old.get("has_trail") instanceof Boolean b ? b : false);
            v.put("has_twinkle", twinkle != null ? twinkle : old.get("has_twinkle") instanceof Boolean b ? b : false);
            s.set("minecraft:firework_explosion", v);
            return s;
        };
    }

    /** 바닐라 Filterable&lt;T&gt; 의 JSON — 값 하나 또는 {raw, filtered?}. 컴포넌트 쪽이면 raw·filtered 를 텍스트로. */
    private static Object filterable(JsonElement e, boolean component) {
        if (e.isJsonObject() && e.getAsJsonObject().has("raw")) {
            JsonObject f = e.getAsJsonObject();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("raw", component ? text(f.get("raw")) : f.get("raw").getAsString());
            if (f.has("filtered")) m.put("filtered", component ? text(f.get("filtered")) : f.get("filtered").getAsString());
            return m;
        }
        return component ? text(e) : e.getAsString();
    }

    private static LootFunction setBookCover(JsonObject o) {
        Object title = o.has("title") ? filterable(o.get("title"), false) : null;
        String author = o.has("author") ? o.get("author").getAsString() : null;
        Integer generation = null;
        if (o.has("generation")) {
            generation = o.get("generation").getAsInt();
            if (generation < 0 || generation > 3) throw new LootParseException("Value must be within range [0;3]: " + generation);
        }
        final Integer gen = generation;
        return (s, c) -> {
            Map<?, ?> old = s.get("minecraft:written_book_content") instanceof Map<?, ?> m ? m : Map.of();
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("title", title != null ? title : old.containsKey("title") ? old.get("title") : "");
            v.put("author", author != null ? author : old.containsKey("author") ? old.get("author") : "");
            v.put("generation", gen != null ? gen : old.get("generation") instanceof Number n ? n.intValue() : 0);
            v.put("pages", old.containsKey("pages") ? LootStack.deepCopy(old.get("pages")) : new ArrayList<>());
            // WrittenBookContent.EMPTY 의 resolved 는 true
            v.put("resolved", old.get("resolved") instanceof Boolean b ? b : old.isEmpty());
            s.set("minecraft:written_book_content", v);
            return s;
        };
    }

    @SuppressWarnings("unchecked")
    private static LootFunction setWrittenBookPages(JsonObject o) {
        List<Object> pages = new ArrayList<>();
        for (JsonElement x : LootJson.req(o, "pages").getAsJsonArray()) pages.add(filterable(x, true));
        ListOp mode = ListOp.parse(o, Integer.MAX_VALUE);
        return (s, c) -> {
            Map<String, Object> v = s.get("minecraft:written_book_content") instanceof Map<?, ?> m
                    ? (Map<String, Object>) LootStack.deepCopy((Map<String, Object>) m) : new LinkedHashMap<>();
            v.putIfAbsent("title", "");
            v.putIfAbsent("author", "");
            v.put("resolved", false);   // withReplacedPages 는 늘 resolved=false(새 쪽의 선택자·점수 텍스트를 다시 해석한다)
            List<Object> old = v.get("pages") instanceof List<?> l ? new ArrayList<>((List<Object>) l) : new ArrayList<>();
            v.put("pages", new ArrayList<>(mode.apply(old, (List<Object>) LootStack.deepCopy(pages), Integer.MAX_VALUE)));
            s.set("minecraft:written_book_content", v);
            return s;
        };
    }

    @SuppressWarnings("unchecked")
    private static LootFunction setWritableBookPages(JsonObject o) {
        List<Object> pages = new ArrayList<>();
        for (JsonElement x : LootJson.req(o, "pages").getAsJsonArray()) pages.add(filterable(x, false));
        ListOp mode = ListOp.parse(o, 100);
        return (s, c) -> {
            Map<String, Object> v = s.get("minecraft:writable_book_content") instanceof Map<?, ?> m
                    ? (Map<String, Object>) LootStack.deepCopy((Map<String, Object>) m) : new LinkedHashMap<>();
            List<Object> old = v.get("pages") instanceof List<?> l ? new ArrayList<>((List<Object>) l) : new ArrayList<>();
            v.put("pages", new ArrayList<>(mode.apply(old, (List<Object>) LootStack.deepCopy(pages), 100)));
            s.set("minecraft:writable_book_content", v);
            return s;
        };
    }
}
