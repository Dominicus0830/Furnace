// 바닐라 1.21.1 데이터 컴포넌트 57종(DataComponents) — 레지스트리 순서 = 네트워크 id.
// 각 종류의 저장 코덱·네트워크 코덱은 바닐라 레코드 코덱과 같은 필드 이름·기본값·생략 규칙을 따른다.
package org.domi.furnace.server.item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.domi.furnace.server.item.NCodec.f;
import static org.domi.furnace.server.item.NCodec.fDef;
import static org.domi.furnace.server.item.NCodec.fOpt;
import static org.domi.furnace.server.item.PCodec.opt;
import static org.domi.furnace.server.item.PCodec.optEmpty;
import static org.domi.furnace.server.item.PCodec.record;
import static org.domi.furnace.server.item.PCodec.req;

public final class DataComponents {
    private DataComponents() {}

    private static final List<ComponentType> ALL = new ArrayList<>();
    private static final Map<String, ComponentType> BY_NAME = new HashMap<>();
    private static final Map<ComponentType, Integer> IDS = new HashMap<>();

    private static ComponentType reg(String name, PCodec persistent, NCodec network) {
        ComponentType t = new ComponentType("minecraft:" + name, persistent, network);
        IDS.put(t, ALL.size());
        ALL.add(t);
        BY_NAME.put(t.name(), t);
        return t;
    }

    public static ComponentType byName(String name) {
        ComponentType t = BY_NAME.get(name);
        if (t == null && name.indexOf(':') < 0) t = BY_NAME.get("minecraft:" + name);
        return t;
    }

    public static ComponentType require(String name) {
        ComponentType t = byName(name);
        if (t == null) throw new CodecException("No element " + name + " in registry minecraft:data_component_type");
        return t;
    }

    public static int networkId(ComponentType t) { return IDS.get(t); }

    public static ComponentType byNetworkId(int id) {
        if (id < 0 || id >= ALL.size()) throw new CodecException("Unknown data component type id: " + id);
        return ALL.get(id);
    }

    public static int count() { return ALL.size(); }

    public static List<ComponentType> all() { return List.copyOf(ALL); }

    // ------------------------------------------------------------------ 공통 조각

    private static final PCodec TEXT = PCodec.TEXT_FLAT;

    /** ItemEnchantments: {levels: {인챈트: 1..255}, show_in_tooltip?=true}, 또는 levels 맵만. */
    private static final PCodec ENCHANT_LEVELS = PCodec.map(PCodec.registryId("minecraft:enchantment"), PCodec.intRange(0, 255));
    private static final PCodec ENCHANTMENTS = PCodec.alternative(
            record(req("levels", ENCHANT_LEVELS), opt("show_in_tooltip", PCodec.BOOL, true)),
            ENCHANT_LEVELS, levels -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("levels", levels);
                return m;
            });   // 레벨 0 도 코덱으로는 그대로 남는다(Mutable.set 만 0 이하를 뺀다)

    private static final NCodec ENCHANTMENTS_STREAM = NCodec.record(
            f("levels", NCodec.map(NCodec.registryId("minecraft:enchantment"), NCodec.VAR_INT, Integer.MAX_VALUE)),
            fDef("show_in_tooltip", NCodec.BOOL, true));

    /** show_in_tooltip 하나만 있는 레코드(Unbreakable 등). */
    private static final PCodec TOOLTIP_ONLY = record(opt("show_in_tooltip", PCodec.BOOL, true));
    private static final NCodec TOOLTIP_ONLY_STREAM = NCodec.record(fDef("show_in_tooltip", NCodec.BOOL, true));

    // 블록 술어(AdventureModePredicate)
    private static final PCodec VALUE_MATCHER = new PCodec() {
        private final PCodec ranged = record(optEmpty("min", PCodec.STRING), optEmpty("max", PCodec.STRING));
        public Object normalize(Object in) { return in instanceof String s ? s : ranged.normalize(in); }
        public Object toNbt(Object v) { return v instanceof String s ? s : ranged.toNbt(v); }
    };
    private static final PCodec STATE_PROPERTIES = PCodec.map(PCodec.STRING, VALUE_MATCHER);
    /** NbtPredicate: TagParser.LENIENT_CODEC — SNBT 문자열(쓸 때 이 꼴)이나 compound. 정규 값은 NbtCompound. */
    private static final PCodec NBT_PREDICATE = new PCodec() {
        public Object normalize(Object in) { return PCodec.COMPOUND.normalize(in); }
        public Object toNbt(Object v) { return Snbt.write(v); }
    };
    private static final PCodec BLOCK_PREDICATE = record(optEmpty("blocks", Codecs.holderSet("minecraft:block")),
            optEmpty("state", STATE_PROPERTIES), optEmpty("nbt", NBT_PREDICATE));
    /** ExtraCodecs.nonEmptyList(BlockPredicate 리스트). */
    private static final PCodec BLOCK_PREDICATES = BLOCK_PREDICATE.listOf().validate(v -> !((List<?>) v).isEmpty(), v -> "List must have contents");
    private static final PCodec ADVENTURE_PREDICATE = PCodec.alternative(
            record(req("predicates", BLOCK_PREDICATES), opt("show_in_tooltip", PCodec.BOOL, true)),
            // 전체 꼴이 어떤 이유로든 실패하면 같은 객체를 블록 술어 하나로 다시 읽는다(바닐라 SINGLE 대체 — 보통 {} 가 된다)
            BLOCK_PREDICATE, p -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("predicates", new ArrayList<>(List.of(p)));
                return m;
            });
    private static final NCodec VALUE_MATCHER_STREAM = new NCodec() {
        private final NCodec ranged = NCodec.record(fOpt("min", NCodec.STRING), fOpt("max", NCodec.STRING));
        public Object read(io.netty.buffer.ByteBuf buf) { return buf.readBoolean() ? NCodec.STRING.read(buf) : ranged.read(buf); }
        public void write(io.netty.buffer.ByteBuf buf, Object v) {
            buf.writeBoolean(v instanceof String);
            if (v instanceof String) NCodec.STRING.write(buf, v);
            else ranged.write(buf, v);
        }
    };
    private static final NCodec STATE_PROPERTIES_STREAM = new NCodec() {
        public Object read(io.netty.buffer.ByteBuf buf) {
            int n = org.domi.furnace.server.net.io.Types.readVarInt(buf);
            Map<String, Object> m = new LinkedHashMap<>();
            for (int k = 0; k < n; k++) {
                String name = (String) NCodec.STRING.read(buf);
                m.put(name, VALUE_MATCHER_STREAM.read(buf));
            }
            return m;
        }
        public void write(io.netty.buffer.ByteBuf buf, Object v) {
            Map<String, Object> m = Val.map(v);
            org.domi.furnace.server.net.io.Types.writeVarInt(buf, m.size());
            for (var e : m.entrySet()) {
                NCodec.STRING.write(buf, e.getKey());
                VALUE_MATCHER_STREAM.write(buf, e.getValue());
            }
        }
    };
    private static final NCodec BLOCK_PREDICATE_STREAM = NCodec.record(fOpt("blocks", NCodec.holderSet("minecraft:block")),
            fOpt("state", STATE_PROPERTIES_STREAM), fOpt("nbt", NCodec.COMPOUND));
    private static final NCodec ADVENTURE_PREDICATE_STREAM = NCodec.record(
            f("predicates", BLOCK_PREDICATE_STREAM.listOf()),
            fDef("show_in_tooltip", NCodec.BOOL, true));

    // 속성 변경치
    private static final List<String> OPERATIONS = List.of("add_value", "add_multiplied_base", "add_multiplied_total");
    private static final List<String> SLOT_GROUPS = List.of("any", "mainhand", "offhand", "hand", "feet", "legs", "chest", "head", "armor", "body");
    private static final PCodec ATTRIBUTE_ENTRY = record(req("type", PCodec.registryId("minecraft:attribute")),
            req("id", PCodec.RESOURCE_LOCATION), req("amount", PCodec.DOUBLE), req("operation", PCodec.enumByName(OPERATIONS)),
            opt("slot", PCodec.enumByName(SLOT_GROUPS), "any"));
    private static final PCodec ATTRIBUTE_MODIFIERS = PCodec.alternative(
            record(req("modifiers", ATTRIBUTE_ENTRY.listOf()), opt("show_in_tooltip", PCodec.BOOL, true)),
            ATTRIBUTE_ENTRY.listOf(), l -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("modifiers", l);
                return m;
            });
    private static final NCodec ATTRIBUTE_ENTRY_STREAM = NCodec.record(f("type", NCodec.registryId("minecraft:attribute")),
            f("id", NCodec.RESOURCE_LOCATION), f("amount", NCodec.DOUBLE), f("operation", NCodec.enumById(OPERATIONS)),
            fDef("slot", NCodec.enumById(SLOT_GROUPS), "any"));
    private static final NCodec ATTRIBUTE_MODIFIERS_STREAM = NCodec.record(f("modifiers", ATTRIBUTE_ENTRY_STREAM.listOf()),
            fDef("show_in_tooltip", NCodec.BOOL, true));

    // 상태 효과
    /** MobEffectInstance.Details — 숨은 효과는 같은 모양으로 재귀. show_icon 은 없으면 show_particles 와 같고, 쓸 때는 항상 쓴다. */
    private static final PCodec EFFECT_DETAILS = PCodec.lazy(() -> new PCodec() {
        private final PCodec base = record(opt("amplifier", Codecs.UNSIGNED_BYTE, 0), opt("duration", PCodec.INT, 0),
                opt("ambient", PCodec.BOOL, false), opt("show_particles", PCodec.BOOL, true), optEmpty("show_icon", PCodec.BOOL),
                optEmpty("hidden_effect", DataComponents.EFFECT_DETAILS));
        @SuppressWarnings("unchecked")
        public Object normalize(Object in) {
            Map<String, Object> m = (Map<String, Object>) base.normalize(in);
            if (!m.containsKey("show_icon")) {
                Map<String, Object> out = new LinkedHashMap<>();
                for (var e : m.entrySet()) {
                    if (e.getKey().equals("hidden_effect")) out.put("show_icon", PCodec.get(m, "show_particles", true));
                    out.put(e.getKey(), e.getValue());
                }
                if (!out.containsKey("show_icon")) out.put("show_icon", PCodec.get(m, "show_particles", true));
                return out;
            }
            return m;
        }
        public Object toNbt(Object v) { return base.toNbt(v); }
    });
    private static final PCodec EFFECT_INSTANCE = new PCodec() {
        public Object normalize(Object in) {
            Map<String, Object> m = Val.map(in);
            if (m.get("id") == null) throw new CodecException("No key id in MapLike[" + Val.describe(m) + "]");
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", PCodec.registryId("minecraft:mob_effect").normalize(m.get("id")));
            out.putAll(Val.map(EFFECT_DETAILS.normalize(m)));
            return out;
        }
        public Object toNbt(Object v) {
            Map<String, Object> m = Val.map(v);
            org.domi.furnace.server.net.io.nbt.NbtCompound c = new org.domi.furnace.server.net.io.nbt.NbtCompound();
            c.putString("id", (String) m.get("id"));
            Map<String, Object> details = new LinkedHashMap<>(m);
            details.remove("id");
            c.tags().putAll(((org.domi.furnace.server.net.io.nbt.NbtCompound) EFFECT_DETAILS.toNbt(details)).tags());
            return c;
        }
    };
    private static final NCodec EFFECT_DETAILS_STREAM = NCodec.lazy(() -> NCodec.record(fDef("amplifier", NCodec.VAR_INT, 0),
            fDef("duration", NCodec.VAR_INT, 0), fDef("ambient", NCodec.BOOL, false), fDef("show_particles", NCodec.BOOL, true),
            f("show_icon", NCodec.BOOL), fOpt("hidden_effect", DataComponents.EFFECT_DETAILS_STREAM)));
    private static final NCodec EFFECT_INSTANCE_STREAM = new NCodec() {
        public Object read(io.netty.buffer.ByteBuf buf) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", NCodec.registryId("minecraft:mob_effect").read(buf));
            out.putAll(Val.map(EFFECT_DETAILS_STREAM.read(buf)));
            return out;
        }
        public void write(io.netty.buffer.ByteBuf buf, Object v) {
            Map<String, Object> m = Val.map(v);
            NCodec.registryId("minecraft:mob_effect").write(buf, m.get("id"));
            EFFECT_DETAILS_STREAM.write(buf, m);
        }
    };

    /** FoodProperties.PossibleEffect — effect() 가 숨은 효과를 빼고 복사하므로 숨은 효과는 읽혀도 다시 쓰이지 않는다. */
    private static final PCodec POSSIBLE_EFFECT = record(req("effect", EFFECT_INSTANCE.post(DataComponents::stripHidden)),
            opt("probability", PCodec.floatRange(0f, 1f, null), 1.0f));
    private static final NCodec POSSIBLE_EFFECT_STREAM = NCodec.record(f("effect", EFFECT_INSTANCE_STREAM.map(DataComponents::stripHidden, v -> v)),
            fDef("probability", NCodec.FLOAT, 1.0f));

    private static Object stripHidden(Object effect) {
        Map<String, Object> m = Val.map(effect);
        if (!m.containsKey("hidden_effect")) return effect;
        Map<String, Object> out = new LinkedHashMap<>(m);
        out.remove("hidden_effect");
        return out;
    }

    private static final PCodec FOOD = record(req("nutrition", PCodec.NON_NEGATIVE_INT), req("saturation", PCodec.FLOAT),
            opt("can_always_eat", PCodec.BOOL, false), opt("eat_seconds", PCodec.POSITIVE_FLOAT, 1.6f),
            optEmpty("using_converts_to", ItemStackCodecs.SINGLE_ITEM), opt("effects", POSSIBLE_EFFECT.listOf(), List.of()));
    private static final NCodec FOOD_STREAM = NCodec.record(f("nutrition", NCodec.VAR_INT), f("saturation", NCodec.FLOAT),
            fDef("can_always_eat", NCodec.BOOL, false), fDef("eat_seconds", NCodec.FLOAT, 1.6f),
            fOpt("using_converts_to", ItemStackCodecs.STREAM.map(DataComponents::dropCount, DataComponents::withCount1)),
            fDef("effects", POSSIBLE_EFFECT_STREAM.listOf(), List.of()));

    private static Object dropCount(Object stack) {
        Map<String, Object> m = new LinkedHashMap<>(Val.map(stack));
        m.remove("count");
        return m;
    }

    private static Object withCount1(Object stack) {
        Map<String, Object> m = new LinkedHashMap<>(Val.map(stack));
        m.putIfAbsent("count", 1);
        return m;
    }

    // 도구
    private static final PCodec TOOL_RULE = record(req("blocks", Codecs.holderSet("minecraft:block")),
            optEmpty("speed", PCodec.POSITIVE_FLOAT), optEmpty("correct_for_drops", PCodec.BOOL));
    private static final PCodec TOOL = record(req("rules", TOOL_RULE.listOf()), opt("default_mining_speed", PCodec.FLOAT, 1.0f),
            opt("damage_per_block", PCodec.NON_NEGATIVE_INT, 1));
    private static final NCodec TOOL_RULE_STREAM = NCodec.record(f("blocks", NCodec.holderSet("minecraft:block")),
            fOpt("speed", NCodec.FLOAT), fOpt("correct_for_drops", NCodec.BOOL));
    private static final NCodec TOOL_STREAM = NCodec.record(f("rules", TOOL_RULE_STREAM.listOf()),
            fDef("default_mining_speed", NCodec.FLOAT, 1.0f), fDef("damage_per_block", NCodec.VAR_INT, 1));

    // 염색·지도
    private static final PCodec DYED_COLOR = PCodec.alternative(record(req("rgb", PCodec.INT), opt("show_in_tooltip", PCodec.BOOL, true)),
            PCodec.INT, rgb -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("rgb", rgb);
                return m;
            });
    private static final NCodec DYED_COLOR_STREAM = NCodec.record(f("rgb", NCodec.INT), fDef("show_in_tooltip", NCodec.BOOL, true));
    private static final PCodec MAP_DECORATION = record(req("type", PCodec.registryId("minecraft:map_decoration_type")),
            req("x", PCodec.DOUBLE), req("z", PCodec.DOUBLE), req("rotation", PCodec.FLOAT));

    // 물약
    private static final PCodec POTION_CONTENTS = PCodec.alternative(
            record(optEmpty("potion", PCodec.registryId("minecraft:potion")), optEmpty("custom_color", PCodec.INT),
                    opt("custom_effects", EFFECT_INSTANCE.listOf(), List.of())),
            PCodec.registryId("minecraft:potion"), p -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("potion", p);
                return m;
            });
    private static final NCodec POTION_CONTENTS_STREAM = NCodec.record(fOpt("potion", NCodec.registryId("minecraft:potion")),
            fOpt("custom_color", NCodec.INT), fDef("custom_effects", EFFECT_INSTANCE_STREAM.listOf(), List.of()));
    private static final PCodec STEW_EFFECT = record(req("id", PCodec.registryId("minecraft:mob_effect")), PCodec.optLenient("duration", PCodec.INT, 160));
    private static final NCodec STEW_EFFECT_STREAM = NCodec.record(f("id", NCodec.registryId("minecraft:mob_effect")),
            fDef("duration", NCodec.VAR_INT, 160));

    // 책
    /** Filterable<T>: {raw, filtered?} 또는 T 그대로. 정규 값은 언제나 {raw, filtered?}. */
    private static PCodec filterable(PCodec inner) {
        return PCodec.alternative(record(req("raw", inner), optEmpty("filtered", inner)), inner, v -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("raw", v);
            return m;
        });
    }

    private static NCodec filterableStream(NCodec inner) {
        return NCodec.record(f("raw", inner), fOpt("filtered", inner));
    }

    private static final PCodec WRITABLE_BOOK = record(opt("pages", PCodec.list(filterable(PCodec.string(1024)), 0, 100), List.of()));
    private static final NCodec WRITABLE_BOOK_STREAM = NCodec.record(fDef("pages",
            NCodec.list(filterableStream(NCodec.string(1024)), 100), List.of()));
    private static final PCodec WRITTEN_BOOK = record(req("title", filterable(PCodec.string(32))), req("author", PCodec.STRING),
            opt("generation", PCodec.intRange(0, 3), 0), opt("pages", filterable(PCodec.flatText(32767)).listOf(), List.of()),
            opt("resolved", PCodec.BOOL, false));
    private static final NCodec WRITTEN_BOOK_STREAM = NCodec.record(f("title", filterableStream(NCodec.string(32))),
            f("author", NCodec.STRING), fDef("generation", NCodec.VAR_INT, 0),
            fDef("pages", filterableStream(NCodec.TEXT).listOf(), List.of()), fDef("resolved", NCodec.BOOL, false));

    // 갑옷 장식
    private static final PCodec TRIM_MATERIAL_DIRECT = record(req("asset_name", PCodec.STRING), req("ingredient", PCodec.registryId("minecraft:item")),
            req("item_model_index", PCodec.FLOAT), opt("override_armor_materials", PCodec.map(PCodec.registryId("minecraft:armor_material"), PCodec.STRING), Map.of()),
            req("description", Codecs.TEXT_TREE));
    private static final PCodec TRIM_PATTERN_DIRECT = record(req("asset_id", PCodec.RESOURCE_LOCATION),
            req("template_item", PCodec.registryId("minecraft:item")), req("description", Codecs.TEXT_TREE), PCodec.orElse("decal", PCodec.BOOL, false));
    private static final PCodec TRIM = record(req("material", Codecs.holder("minecraft:trim_material", TRIM_MATERIAL_DIRECT)),
            req("pattern", Codecs.holder("minecraft:trim_pattern", TRIM_PATTERN_DIRECT)), opt("show_in_tooltip", PCodec.BOOL, true));
    private static final NCodec TRIM_MATERIAL_DIRECT_STREAM = NCodec.record(f("asset_name", NCodec.STRING),
            f("ingredient", NCodec.registryId("minecraft:item")), f("item_model_index", NCodec.FLOAT),
            fDef("override_armor_materials", NCodec.map(NCodec.registryId("minecraft:armor_material"), NCodec.STRING, Integer.MAX_VALUE), Map.of()),
            f("description", Codecs.TEXT_TREE_STREAM));
    private static final NCodec TRIM_PATTERN_DIRECT_STREAM = NCodec.record(f("asset_id", NCodec.RESOURCE_LOCATION),
            f("template_item", NCodec.registryId("minecraft:item")), f("description", Codecs.TEXT_TREE_STREAM), f("decal", NCodec.BOOL));
    private static final NCodec TRIM_STREAM = NCodec.record(f("material", NCodec.holder("minecraft:trim_material", TRIM_MATERIAL_DIRECT_STREAM)),
            f("pattern", NCodec.holder("minecraft:trim_pattern", TRIM_PATTERN_DIRECT_STREAM)), fDef("show_in_tooltip", NCodec.BOOL, true));

    // 엔티티·블록 엔티티 데이터
    private static final PCodec CUSTOM_DATA_WITH_ID = PCodec.COMPOUND.validate(v -> ((org.domi.furnace.server.net.io.nbt.NbtCompound) v).tags().get("id") instanceof String,
            v -> "Missing id for entity in: " + Snbt.write(v));

    // 악기·음반
    private static final PCodec INSTRUMENT_DIRECT = record(req("sound_event", Codecs.SOUND_EVENT), req("use_duration", PCodec.POSITIVE_INT),
            req("range", PCodec.POSITIVE_FLOAT));
    private static final NCodec INSTRUMENT_DIRECT_STREAM = NCodec.record(f("sound_event", Codecs.SOUND_EVENT_STREAM),
            f("use_duration", NCodec.VAR_INT), f("range", NCodec.FLOAT));
    private static final PCodec JUKEBOX_SONG_DIRECT = record(req("sound_event", Codecs.SOUND_EVENT), req("description", Codecs.TEXT_TREE),
            req("length_in_seconds", PCodec.POSITIVE_FLOAT), req("comparator_output", PCodec.intRange(0, 15)));
    private static final NCodec JUKEBOX_SONG_DIRECT_STREAM = NCodec.record(f("sound_event", Codecs.SOUND_EVENT_STREAM),
            f("description", Codecs.TEXT_TREE_STREAM), f("length_in_seconds", NCodec.FLOAT), f("comparator_output", NCodec.VAR_INT));
    /**
     * 음반의 EitherHolder: 저장 코덱으로 읽으면 레지스트리 홀더, 아이템 기본값(Item.Properties.jukeboxPlayable)은 키만 가진 꼴이라
     * 바닐라에서 둘은 같지 않다. 키만 가진 꼴은 정규 값에 {@link #KEY_ONLY} 표시를 단다(저장·JSON 으로는 나가지 않음).
     */
    public static final String KEY_ONLY = "furnace:key_only";
    private static final PCodec JUKEBOX_PLAYABLE = record(req("song", PCodec.registryId("minecraft:jukebox_song")),
            opt("show_in_tooltip", PCodec.BOOL, true));
    /** 네트워크: either(홀더, 키) — 01 + 홀더(id+1 / 00 + 직접 값), 00 + 키 이름. 그 뒤 show_in_tooltip. */
    private static final NCodec JUKEBOX_PLAYABLE_STREAM = new NCodec() {
        private final NCodec holder = NCodec.holder("minecraft:jukebox_song", JUKEBOX_SONG_DIRECT_STREAM);
        public Object read(io.netty.buffer.ByteBuf buf) {
            Map<String, Object> m = new LinkedHashMap<>();
            boolean isHolder = buf.readBoolean();
            m.put("song", isHolder ? holder.read(buf) : NCodec.RESOURCE_LOCATION.read(buf));
            if (!buf.readBoolean()) m.put("show_in_tooltip", false);
            if (!isHolder) m.put(KEY_ONLY, true);
            return m;
        }
        public void write(io.netty.buffer.ByteBuf buf, Object v) {
            Map<String, Object> m = Val.map(v);
            boolean keyOnly = Boolean.TRUE.equals(m.get(KEY_ONLY));
            buf.writeBoolean(!keyOnly);
            if (keyOnly) NCodec.RESOURCE_LOCATION.write(buf, m.get("song"));
            else holder.write(buf, m.get("song"));
            buf.writeBoolean(!Boolean.FALSE.equals(m.get("show_in_tooltip")));
        }
    };

    /** 아이템 기본값의 음반 값 → 키만 가진 꼴. */
    static Object jukeboxKeyOnly(Object v) {
        Map<String, Object> m = new LinkedHashMap<>(Val.map(v));
        m.put(KEY_ONLY, true);
        return m;
    }

    // 자석석 추적기
    private static final PCodec GLOBAL_POS = record(req("dimension", PCodec.RESOURCE_LOCATION), req("pos", Codecs.BLOCK_POS));
    private static final NCodec GLOBAL_POS_STREAM = NCodec.record(f("dimension", NCodec.RESOURCE_LOCATION), f("pos", Codecs.BLOCK_POS_STREAM));
    private static final PCodec LODESTONE = record(optEmpty("target", GLOBAL_POS), opt("tracked", PCodec.BOOL, true));
    private static final NCodec LODESTONE_STREAM = NCodec.record(fOpt("target", GLOBAL_POS_STREAM), fDef("tracked", NCodec.BOOL, true));

    // 폭죽
    private static final List<String> FIREWORK_SHAPES = List.of("small_ball", "large_ball", "star", "creeper", "burst");
    private static final PCodec INT_LIST = PCodec.INT.listOf();
    private static final NCodec INT_LIST_STREAM = NCodec.INT.listOf();
    private static final PCodec FIREWORK_EXPLOSION = record(req("shape", PCodec.enumByName(FIREWORK_SHAPES)),
            opt("colors", INT_LIST, List.of()), opt("fade_colors", INT_LIST, List.of()),
            opt("has_trail", PCodec.BOOL, false), opt("has_twinkle", PCodec.BOOL, false));
    private static final NCodec FIREWORK_EXPLOSION_STREAM = NCodec.record(f("shape", NCodec.enumById(FIREWORK_SHAPES)),
            fDef("colors", INT_LIST_STREAM, List.of()), fDef("fade_colors", INT_LIST_STREAM, List.of()),
            fDef("has_trail", NCodec.BOOL, false), fDef("has_twinkle", NCodec.BOOL, false));
    private static final PCodec FIREWORKS = record(opt("flight_duration", Codecs.UNSIGNED_BYTE, 0),
            opt("explosions", PCodec.list(FIREWORK_EXPLOSION, 0, 256), List.of()));
    private static final NCodec FIREWORKS_STREAM = NCodec.record(fDef("flight_duration", NCodec.VAR_INT, 0),
            fDef("explosions", NCodec.list(FIREWORK_EXPLOSION_STREAM, 256), List.of()));

    // 플레이어 머리
    private static final PCodec PROFILE = PCodec.alternative(
            record(optEmpty("name", Codecs.PLAYER_NAME), optEmpty("id", Codecs.UUID_CODEC), opt("properties", Codecs.PROPERTY_MAP, List.of())),
            Codecs.PLAYER_NAME, n -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", n);
                return m;
            });
    private static final NCodec PROFILE_STREAM = NCodec.record(fOpt("name", NCodec.string(16)), fOpt("id", Codecs.UUID_STREAM),
            fDef("properties", Codecs.PROPERTY_MAP_STREAM, List.of()));

    // 깃발
    private static final PCodec BANNER_PATTERN_DIRECT = record(req("asset_id", PCodec.RESOURCE_LOCATION), req("translation_key", PCodec.STRING));
    private static final NCodec BANNER_PATTERN_DIRECT_STREAM = NCodec.record(f("asset_id", NCodec.RESOURCE_LOCATION), f("translation_key", NCodec.STRING));
    private static final PCodec BANNER_LAYER = record(req("pattern", Codecs.holder("minecraft:banner_pattern", BANNER_PATTERN_DIRECT)),
            req("color", Codecs.DYE_COLOR));
    private static final NCodec BANNER_LAYER_STREAM = NCodec.record(f("pattern", NCodec.holder("minecraft:banner_pattern", BANNER_PATTERN_DIRECT_STREAM)),
            f("color", Codecs.DYE_COLOR_STREAM));

    // 용기
    private static final PCodec CONTAINER_SLOT = record(req("slot", PCodec.intRange(0, 255)), req("item", ItemStackCodecs.ITEM_STACK));
    /** ItemContainerContents: 읽을 때 칸 번호로 모으고(같은 칸은 나중 것이 이김) 쓸 때는 칸 번호 순서. */
    private static final PCodec CONTAINER = PCodec.list(CONTAINER_SLOT, 0, 256).post(v -> {
        java.util.TreeMap<Integer, Object> bySlot = new java.util.TreeMap<>();
        for (Object o : Val.list(v)) bySlot.put((Integer) Val.map(o).get("slot"), o);
        return new ArrayList<>(bySlot.values());
    });
    /** 네트워크는 빈 칸을 포함한 아이템 리스트 — 저장 꼴(칸 번호 + 아이템)로 바꿔 둔다. */
    private static final NCodec CONTAINER_STREAM = NCodec.list(ItemStackCodecs.OPTIONAL_STREAM, 256).map(l -> {
        List<Object> slots = new ArrayList<>();
        List<Object> items = Val.list(l);
        for (int k = 0; k < items.size(); k++) {
            if (items.get(k) == null) continue;
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("slot", k);
            s.put("item", items.get(k));
            slots.add(s);
        }
        return slots;
    }, v -> {
        int size = 0;
        for (Object o : Val.list(v)) size = Math.max(size, (Integer) Val.map(o).get("slot") + 1);
        List<Object> items = new ArrayList<>();
        for (int k = 0; k < size; k++) items.add(null);
        for (Object o : Val.list(v)) items.set((Integer) Val.map(o).get("slot"), Val.map(o).get("item"));
        return items;
    });

    /** PotDecorations: 0..4 개 아이템(벽돌 = 빈 칸), 쓸 때는 언제나 4개(빈 칸은 벽돌). 정규 값은 4개 리스트. */
    private static final String BRICK = "minecraft:brick";
    private static Object pot4(Object v) {
        List<Object> l = new ArrayList<>(Val.list(v));
        while (l.size() < 4) l.add(BRICK);
        return l;
    }
    private static final PCodec POT_DECORATIONS = PCodec.list(PCodec.registryId("minecraft:item"), 0, 4).post(DataComponents::pot4);
    private static final NCodec POT_DECORATIONS_STREAM = NCodec.list(NCodec.registryId("minecraft:item"), 4).map(DataComponents::pot4, v -> v);

    private static final PCodec BEE = record(opt("entity_data", PCodec.COMPOUND, new org.domi.furnace.server.net.io.nbt.NbtCompound()),
            req("ticks_in_hive", PCodec.INT), req("min_ticks_in_hive", PCodec.INT));
    private static final NCodec BEE_STREAM = NCodec.record(fDef("entity_data", NCodec.COMPOUND, new org.domi.furnace.server.net.io.nbt.NbtCompound()), f("ticks_in_hive", NCodec.VAR_INT),
            f("min_ticks_in_hive", NCodec.VAR_INT));

    private static final PCodec SEEDED_LOOT = record(req("loot_table", PCodec.RESOURCE_LOCATION), opt("seed", PCodec.LONG, 0L));

    // ------------------------------------------------------------------ 등록(바닐라 순서)

    public static final ComponentType CUSTOM_DATA = reg("custom_data", PCodec.COMPOUND, null);
    public static final ComponentType MAX_STACK_SIZE = reg("max_stack_size", PCodec.intRange(1, 99), NCodec.VAR_INT);
    public static final ComponentType MAX_DAMAGE = reg("max_damage", PCodec.POSITIVE_INT, NCodec.VAR_INT);
    public static final ComponentType DAMAGE = reg("damage", PCodec.NON_NEGATIVE_INT, NCodec.VAR_INT);
    public static final ComponentType UNBREAKABLE = reg("unbreakable", TOOLTIP_ONLY, TOOLTIP_ONLY_STREAM);
    public static final ComponentType CUSTOM_NAME = reg("custom_name", TEXT, NCodec.TEXT);
    public static final ComponentType ITEM_NAME = reg("item_name", TEXT, NCodec.TEXT);
    public static final ComponentType LORE = reg("lore", PCodec.list(TEXT, 0, 256), NCodec.list(NCodec.TEXT, 256));
    public static final ComponentType RARITY = reg("rarity", PCodec.enumByName(List.of("common", "uncommon", "rare", "epic")),
            NCodec.enumById(List.of("common", "uncommon", "rare", "epic")));
    public static final ComponentType ENCHANTMENTS_TYPE = reg("enchantments", ENCHANTMENTS, ENCHANTMENTS_STREAM);
    public static final ComponentType CAN_PLACE_ON = reg("can_place_on", ADVENTURE_PREDICATE, ADVENTURE_PREDICATE_STREAM);
    public static final ComponentType CAN_BREAK = reg("can_break", ADVENTURE_PREDICATE, ADVENTURE_PREDICATE_STREAM);
    public static final ComponentType ATTRIBUTE_MODIFIERS_TYPE = reg("attribute_modifiers", ATTRIBUTE_MODIFIERS, ATTRIBUTE_MODIFIERS_STREAM);
    public static final ComponentType CUSTOM_MODEL_DATA = reg("custom_model_data", PCodec.INT, NCodec.VAR_INT);
    public static final ComponentType HIDE_ADDITIONAL_TOOLTIP = reg("hide_additional_tooltip", PCodec.UNIT, NCodec.UNIT);
    public static final ComponentType HIDE_TOOLTIP = reg("hide_tooltip", PCodec.UNIT, NCodec.UNIT);
    public static final ComponentType REPAIR_COST = reg("repair_cost", PCodec.NON_NEGATIVE_INT, NCodec.VAR_INT);
    public static final ComponentType CREATIVE_SLOT_LOCK = reg("creative_slot_lock", null, NCodec.UNIT);
    public static final ComponentType ENCHANTMENT_GLINT_OVERRIDE = reg("enchantment_glint_override", PCodec.BOOL, NCodec.BOOL);
    public static final ComponentType INTANGIBLE_PROJECTILE = reg("intangible_projectile", PCodec.UNIT, null);
    /** FoodProperties 의 using_converts_to 는 ItemStack(참조 비교) — 그 값이 있으면 같은 객체가 아닌 한 같지 않다. */
    public static final ComponentType FOOD_TYPE = reg("food", FOOD, FOOD_STREAM).equality(
            (a, b) -> !Val.map(a).containsKey("using_converts_to") && !Val.map(b).containsKey("using_converts_to") && Val.same(a, b), Val::hash);
    public static final ComponentType FIRE_RESISTANT = reg("fire_resistant", PCodec.UNIT, NCodec.UNIT);
    public static final ComponentType TOOL_TYPE = reg("tool", TOOL, TOOL_STREAM);
    public static final ComponentType STORED_ENCHANTMENTS = reg("stored_enchantments", ENCHANTMENTS, ENCHANTMENTS_STREAM);
    public static final ComponentType DYED_COLOR_TYPE = reg("dyed_color", DYED_COLOR, DYED_COLOR_STREAM);
    public static final ComponentType MAP_COLOR = reg("map_color", PCodec.INT, NCodec.INT);
    public static final ComponentType MAP_ID = reg("map_id", PCodec.INT, NCodec.VAR_INT);
    public static final ComponentType MAP_DECORATIONS = reg("map_decorations", PCodec.map(PCodec.STRING, MAP_DECORATION), null);
    public static final ComponentType MAP_POST_PROCESSING = reg("map_post_processing", null, NCodec.enumById(List.of("lock", "scale")));
    public static final ComponentType CHARGED_PROJECTILES = reg("charged_projectiles", ItemStackCodecs.ITEM_STACK.listOf(), ItemStackCodecs.STREAM.listOf());
    public static final ComponentType BUNDLE_CONTENTS = reg("bundle_contents", ItemStackCodecs.ITEM_STACK.listOf(), ItemStackCodecs.STREAM.listOf());
    /** MobEffectInstance.equals 는 숨은 효과를 보지 않는다. */
    public static final ComponentType POTION_CONTENTS_TYPE = reg("potion_contents", POTION_CONTENTS, POTION_CONTENTS_STREAM).equality(
            (a, b) -> Val.same(withoutHidden(a), withoutHidden(b)), v -> Val.hash(withoutHidden(v)));

    private static Object withoutHidden(Object potion) {
        Map<String, Object> m = Val.map(potion);
        if (!(m.get("custom_effects") instanceof List<?> effects) || effects.isEmpty()) return potion;
        Map<String, Object> out = new LinkedHashMap<>(m);
        List<Object> l = new ArrayList<>();
        for (Object e : effects) l.add(stripHidden(e));
        out.put("custom_effects", l);
        return out;
    }
    public static final ComponentType SUSPICIOUS_STEW_EFFECTS = reg("suspicious_stew_effects", STEW_EFFECT.listOf(), STEW_EFFECT_STREAM.listOf());
    public static final ComponentType WRITABLE_BOOK_CONTENT = reg("writable_book_content", WRITABLE_BOOK, WRITABLE_BOOK_STREAM);
    public static final ComponentType WRITTEN_BOOK_CONTENT = reg("written_book_content", WRITTEN_BOOK, WRITTEN_BOOK_STREAM);
    public static final ComponentType TRIM_TYPE = reg("trim", TRIM, TRIM_STREAM);
    public static final ComponentType DEBUG_STICK_STATE = reg("debug_stick_state", PCodec.map(PCodec.registryId("minecraft:block"), PCodec.STRING), null);
    public static final ComponentType ENTITY_DATA = reg("entity_data", CUSTOM_DATA_WITH_ID, NCodec.COMPOUND);
    public static final ComponentType BUCKET_ENTITY_DATA = reg("bucket_entity_data", PCodec.COMPOUND, NCodec.COMPOUND);
    public static final ComponentType BLOCK_ENTITY_DATA = reg("block_entity_data", CUSTOM_DATA_WITH_ID, NCodec.COMPOUND);
    public static final ComponentType INSTRUMENT = reg("instrument", Codecs.holder("minecraft:instrument", INSTRUMENT_DIRECT),
            NCodec.holder("minecraft:instrument", INSTRUMENT_DIRECT_STREAM));
    public static final ComponentType OMINOUS_BOTTLE_AMPLIFIER = reg("ominous_bottle_amplifier", PCodec.intRange(0, 4), NCodec.VAR_INT);
    public static final ComponentType JUKEBOX_PLAYABLE_TYPE = reg("jukebox_playable", JUKEBOX_PLAYABLE, JUKEBOX_PLAYABLE_STREAM);
    public static final ComponentType RECIPES = reg("recipes", PCodec.RESOURCE_LOCATION.listOf(), null);
    public static final ComponentType LODESTONE_TRACKER = reg("lodestone_tracker", LODESTONE, LODESTONE_STREAM);
    public static final ComponentType FIREWORK_EXPLOSION_TYPE = reg("firework_explosion", FIREWORK_EXPLOSION, FIREWORK_EXPLOSION_STREAM);
    public static final ComponentType FIREWORKS_TYPE = reg("fireworks", FIREWORKS, FIREWORKS_STREAM);
    public static final ComponentType PROFILE_TYPE = reg("profile", PROFILE, PROFILE_STREAM);
    public static final ComponentType NOTE_BLOCK_SOUND = reg("note_block_sound", PCodec.RESOURCE_LOCATION, NCodec.RESOURCE_LOCATION);
    public static final ComponentType BANNER_PATTERNS = reg("banner_patterns", BANNER_LAYER.listOf(), BANNER_LAYER_STREAM.listOf());
    public static final ComponentType BASE_COLOR = reg("base_color", Codecs.DYE_COLOR, Codecs.DYE_COLOR_STREAM);
    public static final ComponentType POT_DECORATIONS_TYPE = reg("pot_decorations", POT_DECORATIONS, POT_DECORATIONS_STREAM);
    public static final ComponentType CONTAINER_TYPE = reg("container", CONTAINER, CONTAINER_STREAM);
    public static final ComponentType BLOCK_STATE = reg("block_state", PCodec.map(PCodec.STRING, PCodec.STRING),
            NCodec.map(NCodec.STRING, NCodec.STRING, Integer.MAX_VALUE));
    public static final ComponentType BEES = reg("bees", BEE.listOf(), BEE_STREAM.listOf());
    public static final ComponentType LOCK = reg("lock", PCodec.STRING, null);
    public static final ComponentType CONTAINER_LOOT = reg("container_loot", SEEDED_LOOT, null);
}
