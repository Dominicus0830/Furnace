package org.domi.furnace.compat.vdiff;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.TreeMap;

/**
 * 게임 값 형식 꾸러미(T-235 2단계): 레지스트리 키·채팅 글(Component·Style·TextColor·ClickEvent)·블록·블록 상태·아이템·아이템 더미.
 * 씨앗은 이름(자원 위치·필드 이름·속성 이름)으로만 적고, 올리기는 양쪽 로더의 공개 API(BuiltInRegistries·정적 공장·with… 사슬)로 한다.
 * 내리기는 관측 가능한 값만: 레지스트리 열쇠, 속성 이름=값, 글은 getString 과 코덱 JSON(열쇠 순서 무시한 나무).
 */
final class GameKit {
    private GameKit() {}

    private static final String BIR = "net.minecraft.core.registries.BuiltInRegistries";
    static final String HOLDER = "net.minecraft.core.Holder", HOVER_EVENT = "net.minecraft.network.chat.HoverEvent",
            DATA_COMPONENT_TYPE = "net.minecraft.core.component.DataComponentType", COMPARABLE = "java.lang.Comparable";
    static final String RESOURCE_KEY = "net.minecraft.resources.ResourceKey", TEXT_COLOR = "net.minecraft.network.chat.TextColor",
            STYLE = "net.minecraft.network.chat.Style", COMPONENT = "net.minecraft.network.chat.Component",
            MUTABLE_COMPONENT = "net.minecraft.network.chat.MutableComponent", CLICK_EVENT = "net.minecraft.network.chat.ClickEvent",
            BLOCK = "net.minecraft.world.level.block.Block", BLOCK_STATE = "net.minecraft.world.level.block.state.BlockState",
            ITEM = "net.minecraft.world.item.Item", ITEM_LIKE = "net.minecraft.world.level.ItemLike",
            ITEM_STACK = "net.minecraft.world.item.ItemStack", PROPERTY = "net.minecraft.world.level.block.state.properties.Property";

    // ---- 씨앗 ----

    /** 레지스트리(BuiltInRegistries 필드 이름)의 한 칸. */
    record RegSeed(String registry, String id) {
        @Override
        public String toString() {
            return registry + "/" + id;
        }
    }

    /** 레지스트리 키: 어느 레지스트리의 어느 위치. */
    record KeySeed(String registry, String location) {}

    /** 글 색: rgb 또는 범례 서식 이름(ChatFormatting). */
    record ColorSeed(Integer rgb, String legacy) {}

    record ClickSeed(String action, String value) {}

    /** 스타일 — null 칸은 건드리지 않는다(with… 를 부르지 않음). */
    record StyleSeed(ColorSeed color, Boolean bold, Boolean italic, Boolean underlined, Boolean strikethrough, Boolean obfuscated,
                     String insertion, String font, ClickSeed click) {}

    /** 글: kind = literal·translatable·fallback·keybind·empty. args 는 글·숫자·다른 글 씨앗. */
    record TextSeed(String kind, String a, String b, List<Object> args, StyleSeed style, List<TextSeed> siblings) {}

    /** 블록 상태: 블록 + 속성마다(이름 순) 값 고르기(값 이름 순 번호). */
    record StateSeed(String block, int[] picks) {
        @Override
        public String toString() {
            return "State[" + block + java.util.Arrays.toString(picks) + "]";
        }
    }

    record StackSeed(String item, int count) {}

    /** 공개 정적 필드(예: BlockStateProperties.FACING). */
    record StaticSeed(String owner, String field) {}

    /** 레지스트리 칸의 Holder(registry.wrapAsHolder). */
    record HolderSeed(String registry, String id) {}

    /** 마우스 올림: 글 보이기(SHOW_TEXT)만. */
    record HoverSeed(TextSeed text) {}

    private static final String[] BLOCKS = {"minecraft:air", "minecraft:stone", "minecraft:oak_stairs", "minecraft:command_block", "minecraft:oak_log",
        "minecraft:redstone_wire", "minecraft:water", "minecraft:chest", "minecraft:oak_door", "minecraft:note_block", "minecraft:repeater",
        "minecraft:grass_block", "minecraft:oak_leaves", "minecraft:bell", "minecraft:piston", "minecraft:rail", "minecraft:oak_slab",
        "minecraft:campfire", "minecraft:sculk_sensor", "minecraft:oak_fence", "minecraft:cobblestone_wall", "minecraft:wheat",
        "minecraft:barrier", "minecraft:light", "minecraft:no_such_block"};
    private static final String[] ITEMS = {"minecraft:air", "minecraft:stone", "minecraft:diamond_sword", "minecraft:oak_log",
        "minecraft:ender_pearl", "minecraft:potion", "minecraft:written_book", "minecraft:elytra", "minecraft:bucket", "minecraft:egg",
        "minecraft:shield", "minecraft:no_such_item"};
    private static final int[] COUNTS = {1, 0, -1, 64, 16, 99, 100, 2, Integer.MAX_VALUE, Integer.MIN_VALUE};
    private static final String[] REGISTRIES = {"block", "item", "entity_type", "dimension", "worldgen/biome", "enchantment", "furnace:x"};
    private static final String[] LOCATIONS = {"minecraft:stone", "minecraft:overworld", "minecraft:the_nether", "furnace:a/b", "x:y", "minecraft:pig"};
    private static final String[] LEGACY = {"BLACK", "DARK_RED", "GOLD", "WHITE", "RED", "AQUA", "BOLD", "RESET", "OBFUSCATED"};
    private static final int[] RGBS = {0, 0xFFFFFF, 0xFF5555, 0x123456, 0xFFAA00, -1, 0x1000000, 0x55FF55};
    private static final String[] TEXTS = {"", "a", "Hello", "é§", "%s", "%1$s %2$s", "chat.type.text", "block.minecraft.stone", "key.jump", " "};
    private static final String[] KEYS = {"chat.type.text", "block.minecraft.stone", "item.minecraft.diamond_sword", "no.such.key",
        "commands.give.success.single", "%s", "multiplayer.player.joined", "translation.test.args"};
    private static final String[] CLICKS = {"OPEN_URL", "RUN_COMMAND", "SUGGEST_COMMAND", "CHANGE_PAGE", "COPY_TO_CLIPBOARD"};
    private static final String[] PROPERTIES = {"FACING", "HORIZONTAL_FACING", "AXIS", "WATERLOGGED", "POWERED", "POWER", "HALF",
        "STAIRS_SHAPE", "SLAB_TYPE", "AGE_7", "LEVEL", "NOTE", "NOTEBLOCK_INSTRUMENT", "CHEST_TYPE", "DOOR_HINGE", "OPEN", "LIT"};

    private static final String[] DATA_COMPONENTS = {"MAX_STACK_SIZE", "MAX_DAMAGE", "DAMAGE", "REPAIR_COST", "CUSTOM_NAME", "ITEM_NAME"};
    /** 블록 속성 값 자리(Comparable): 불리언·정수·여러 열거 값 — 속성과 안 맞는 값은 양쪽 다 거절해야 한다. */
    private static final String[] COMPARABLE_ENUMS = {"net.minecraft.core.Direction", "net.minecraft.core.Direction$Axis",
        "net.minecraft.world.level.block.state.properties.Half", "net.minecraft.world.level.block.state.properties.SlabType",
        "net.minecraft.world.level.block.state.properties.StairsShape"};

    /** self = 받는 쪽 클래스(Holder 자리의 레지스트리를 고른다: 아이템 더미·아이템이면 ITEM, 그 밖은 BLOCK). */
    static Object seed(String type, SplittableRandom r, int b, String self) {
        switch (type) {
            case HOLDER -> {
                boolean item = self != null && (self.equals(ITEM_STACK) || self.equals(ITEM));
                return new HolderSeed(item ? "ITEM" : "BLOCK", item ? itemId(r, b) : blockId(r, b));
            }
            case HOVER_EVENT -> {
                return new HoverSeed(text(r, b, 1));
            }
            case DATA_COMPONENT_TYPE -> {
                return new StaticSeed("net.minecraft.core.component.DataComponents", pick(DATA_COMPONENTS, r, b));
            }
            case COMPARABLE -> {
                int k = b >= 0 ? b % 4 : r.nextInt(4);
                if (k == 0) return b >= 0 ? b % 2 == 0 : r.nextBoolean();
                if (k == 1) return b >= 0 ? b % 26 : r.nextInt(-1, 26);
                try {
                    Class<?> e = Class.forName(pick(COMPARABLE_ENUMS, r, b), false, VanillaRuntime.get().loader());
                    Object[] cs = e.getEnumConstants();
                    return new TypeKit.EnumSeed(e.getName(), ((Enum<?>) cs[b >= 0 ? b % cs.length : r.nextInt(cs.length)]).name());
                } catch (ClassNotFoundException ex) {
                    throw new IllegalStateException(ex);
                }
            }
            default -> { }
        }
        return seed(type, r, b);
    }

    static Object seed(String type, SplittableRandom r, int b) {
        return switch (type) {
            case RESOURCE_KEY -> new KeySeed(pick(REGISTRIES, r, b), pick(LOCATIONS, r, b < 0 ? -1 : b / 3));
            case TEXT_COLOR -> color(r, b);
            case STYLE -> style(r, b);
            case COMPONENT, MUTABLE_COMPONENT -> text(r, b, 0);
            case CLICK_EVENT -> new ClickSeed(pick(CLICKS, r, b), pick(TEXTS, r, b));
            case BLOCK -> new RegSeed("BLOCK", blockId(r, b));
            case ITEM, ITEM_LIKE -> new RegSeed("ITEM", itemId(r, b));
            case BLOCK_STATE -> {
                int[] picks = new int[8];
                for (int k = 0; k < picks.length; k++) picks[k] = b >= 0 ? b + k : r.nextInt(64);
                yield new StateSeed(blockId(r, b), picks);
            }
            case ITEM_STACK -> new StackSeed(itemId(r, b), b >= 0 ? COUNTS[b % COUNTS.length] : r.nextInt(8) == 0 ? COUNTS[r.nextInt(COUNTS.length)] : r.nextInt(1, 65));
            case PROPERTY -> new StaticSeed("net.minecraft.world.level.block.state.properties.BlockStateProperties", pick(PROPERTIES, r, b));
            default -> null;
        };
    }

    private static String pick(String[] a, SplittableRandom r, int b) {
        return a[b >= 0 ? b % a.length : r.nextInt(a.length)];
    }

    /** 경계값은 고정 목록, 난수는 바닐라 레지스트리 전체에서(shim 에 없는 블록도 나오게 — 그러면 다름이다). */
    private static String blockId(SplittableRandom r, int b) {
        if (b >= 0 || r.nextInt(4) == 0) return pick(BLOCKS, r, b);
        List<String> all = vanillaIds("BLOCK");
        return all.get(r.nextInt(all.size()));
    }

    private static String itemId(SplittableRandom r, int b) {
        if (b >= 0 || r.nextInt(4) == 0) return pick(ITEMS, r, b);
        List<String> all = vanillaIds("ITEM");
        return all.get(r.nextInt(all.size()));
    }

    private static final Map<String, List<String>> IDS = new java.util.concurrent.ConcurrentHashMap<>();

    private static List<String> vanillaIds(String registry) {
        return IDS.computeIfAbsent(registry, k -> {
            try {
                Object reg = Class.forName(BIR, true, VanillaRuntime.get().loader()).getField(k).get(null);
                List<String> out = new ArrayList<>();
                for (Object id : (Collection<?>) reg.getClass().getMethod("keySet").invoke(reg)) out.add(id.toString());
                out.sort(null);
                return List.copyOf(out);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private static ColorSeed color(SplittableRandom r, int b) {
        boolean legacy = b >= 0 ? b % 3 == 2 : r.nextInt(3) == 0;
        if (legacy) return new ColorSeed(null, pick(LEGACY, r, b));
        return new ColorSeed(b >= 0 ? RGBS[b % RGBS.length] : r.nextInt(0x1000000), null);
    }

    private static Boolean tri(SplittableRandom r, int b, int k) {
        int v = b >= 0 ? (b + k) % 3 : r.nextInt(3);
        return v == 0 ? null : v == 1;
    }

    private static StyleSeed style(SplittableRandom r, int b) {
        if (b == 0) return new StyleSeed(null, null, null, null, null, null, null, null, null); // 빈 스타일
        boolean some = b >= 0 || r.nextBoolean();
        return new StyleSeed(
                some && (b >= 0 ? b % 2 == 1 : r.nextBoolean()) ? color(r, b) : null,
                tri(r, b, 0), tri(r, b, 1), tri(r, b, 2), tri(r, b, 3), tri(r, b, 4),
                (b >= 0 ? b % 5 == 3 : r.nextInt(5) == 0) ? pick(TEXTS, r, b) : null,
                (b >= 0 ? b % 7 == 4 : r.nextInt(7) == 0) ? (b >= 0 && b % 2 == 0 ? "minecraft:uniform" : "minecraft:alt") : null,
                (b >= 0 ? b % 4 == 1 : r.nextInt(4) == 0) ? new ClickSeed(pick(CLICKS, r, b), pick(TEXTS, r, b)) : null);
    }

    private static TextSeed text(SplittableRandom r, int b, int depth) {
        String[] kinds = {"literal", "translatable", "fallback", "keybind", "empty"};
        String kind = pick(kinds, r, b);
        List<Object> args = new ArrayList<>();
        if (kind.equals("translatable") || kind.equals("fallback")) {
            int n = b >= 0 ? b % 3 : r.nextInt(3);
            for (int k = 0; k < n; k++) {
                int t = b >= 0 ? (b + k) % 3 : r.nextInt(3);
                args.add(t == 0 ? pick(TEXTS, r, b < 0 ? -1 : b + k) : t == 1 ? (Object) (b >= 0 ? b + k : r.nextInt(100))
                        : depth < 2 ? text(r, b < 0 ? -1 : b + k + 1, depth + 1) : "x");
            }
        }
        List<TextSeed> sib = new ArrayList<>();
        int ns = depth >= 2 ? 0 : b >= 0 ? b % 3 : r.nextInt(3);
        for (int k = 0; k < ns; k++) sib.add(text(r, b < 0 ? -1 : b + k + 2, depth + 1));
        return new TextSeed(kind, kind.equals("translatable") || kind.equals("fallback") ? pick(KEYS, r, b) : pick(TEXTS, r, b),
                pick(TEXTS, r, b < 0 ? -1 : b + 1), args, style(r, b < 0 ? -1 : b + depth), sib);
    }

    // ---- 입력 조건(paper 줄 when=) ----

    /** when=paper-indestructible-block: 받는 쪽이 Paper 의 부술 수 없는 블록(Block.isDestroyable 거짓)의 상태·블록인가. */
    static final String PAPER_INDESTRUCTIBLE = "paper-indestructible-block";
    /**
     * Paper 1.21.1 Block.isDestroyable(): allowPermanentBlockBreakExploits(기본 거짓)가 아니면 이 열 블록이 거짓. Paper Mojang 이름 jar 의
     * Block.isDestroyable 바이트코드로 확인(11-23).
     */
    private static final java.util.Set<String> PAPER_INDESTRUCTIBLE_BLOCKS = java.util.Set.of("minecraft:barrier", "minecraft:bedrock",
            "minecraft:end_portal_frame", "minecraft:end_portal", "minecraft:end_gateway", "minecraft:command_block",
            "minecraft:repeating_command_block", "minecraft:chain_command_block", "minecraft:structure_block", "minecraft:jigsaw");

    static boolean paperIndestructible(Object recvSeed) {
        String id = recvSeed instanceof StateSeed s ? s.block : recvSeed instanceof RegSeed g && g.registry.equals("BLOCK") ? g.id : null;
        return id != null && PAPER_INDESTRUCTIBLE_BLOCKS.contains(id);
    }

    /** 번역 인자 하나: 글·정수·글 씨앗. */
    static Object translationArg(SplittableRandom r, int b) {
        int t = b >= 0 ? b % 3 : r.nextInt(3);
        return t == 0 ? pick(TEXTS, r, b) : t == 1 ? (Object) (b >= 0 ? b : r.nextInt(100)) : text(r, b, 2);
    }

    static boolean isSeed(Object s) {
        return s instanceof RegSeed || s instanceof KeySeed || s instanceof ColorSeed || s instanceof ClickSeed || s instanceof StyleSeed
                || s instanceof TextSeed || s instanceof StateSeed || s instanceof StackSeed || s instanceof StaticSeed
                || s instanceof HolderSeed || s instanceof HoverSeed;
    }

    // ---- 올리기 ----

    static Object raise(Object s, ClassLoader cl) throws ReflectiveOperationException {
        return switch (s) {
            case RegSeed g -> registryGet(g.registry, g.id, cl);
            case StaticSeed st -> c(cl, st.owner).getField(st.field).get(null);
            case KeySeed k -> {
                Class<?> key = c(cl, RESOURCE_KEY), loc = c(cl, "net.minecraft.resources.ResourceLocation");
                Object reg = key.getMethod("createRegistryKey", loc).invoke(null, parse(k.registry, cl));
                yield key.getMethod("create", key, loc).invoke(null, reg, parse(k.location, cl));
            }
            case ColorSeed col -> col.legacy != null
                    ? c(cl, TEXT_COLOR).getMethod("fromLegacyFormat", c(cl, "net.minecraft.ChatFormatting")).invoke(null, chatFormatting(col.legacy, cl))
                    : c(cl, TEXT_COLOR).getMethod("fromRgb", int.class).invoke(null, col.rgb);
            case ClickSeed ck -> {
                Class<?> act = c(cl, CLICK_EVENT + "$Action");
                yield c(cl, CLICK_EVENT).getConstructor(act, String.class).newInstance(enumValue(act, ck.action), ck.value);
            }
            case StyleSeed st -> style(st, cl);
            case TextSeed t -> text(t, cl);
            case StateSeed ss -> state(ss, cl);
            case HolderSeed h -> c(cl, "net.minecraft.core.Registry").getMethod("wrapAsHolder", Object.class)
                    .invoke(c(cl, BIR).getField(h.registry).get(null), registryGet(h.registry, h.id, cl));
            case HoverSeed hv -> {
                Class<?> act = c(cl, HOVER_EVENT + "$Action");
                yield c(cl, HOVER_EVENT).getConstructor(act, Object.class).newInstance(act.getField("SHOW_TEXT").get(null), text(hv.text, cl));
            }
            case StackSeed sk -> c(cl, ITEM_STACK).getConstructor(c(cl, ITEM_LIKE), int.class).newInstance(registryGet("ITEM", sk.item, cl), sk.count);
            default -> throw new IllegalArgumentException(String.valueOf(s));
        };
    }

    private static Class<?> c(ClassLoader cl, String n) throws ClassNotFoundException {
        Class<?> k = find(cl, n);
        if (k == null) throw new ClassNotFoundException(n);
        return k;
    }

    private static Object parse(String id, ClassLoader cl) throws ReflectiveOperationException {
        return c(cl, "net.minecraft.resources.ResourceLocation").getMethod("parse", String.class).invoke(null, id);
    }

    private static Object registryGet(String registry, String id, ClassLoader cl) throws ReflectiveOperationException {
        Object reg = c(cl, BIR).getField(registry).get(null);
        Class<?> loc = c(cl, "net.minecraft.resources.ResourceLocation");
        return c(cl, "net.minecraft.core.Registry").getMethod("get", loc).invoke(reg, parse(id, cl));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(Class<?> e, String name) {
        return Enum.valueOf((Class) e, name);
    }

    private static Object chatFormatting(String name, ClassLoader cl) throws ClassNotFoundException {
        return enumValue(c(cl, "net.minecraft.ChatFormatting"), name);
    }

    private static Object style(StyleSeed st, ClassLoader cl) throws ReflectiveOperationException {
        Class<?> sc = c(cl, STYLE);
        Object s = sc.getField("EMPTY").get(null);
        if (st.color != null) s = sc.getMethod("withColor", c(cl, TEXT_COLOR)).invoke(s, raise(st.color, cl));
        String[] flags = {"withBold", "withItalic", "withUnderlined", "withStrikethrough", "withObfuscated"};
        Boolean[] vals = {st.bold, st.italic, st.underlined, st.strikethrough, st.obfuscated};
        for (int k = 0; k < flags.length; k++) if (vals[k] != null) s = sc.getMethod(flags[k], Boolean.class).invoke(s, vals[k]);
        if (st.insertion != null) s = sc.getMethod("withInsertion", String.class).invoke(s, st.insertion);
        if (st.font != null) s = sc.getMethod("withFont", c(cl, "net.minecraft.resources.ResourceLocation")).invoke(s, parse(st.font, cl));
        if (st.click != null) s = sc.getMethod("withClickEvent", c(cl, CLICK_EVENT)).invoke(s, raise(st.click, cl));
        return s;
    }

    private static Object text(TextSeed t, ClassLoader cl) throws ReflectiveOperationException {
        Class<?> cc = c(cl, COMPONENT), mc = c(cl, MUTABLE_COMPONENT);
        Object[] args = new Object[t.args.size()];
        for (int k = 0; k < args.length; k++) args[k] = t.args.get(k) instanceof TextSeed ts ? text(ts, cl) : t.args.get(k);
        Object m = switch (t.kind) {
            case "literal" -> cc.getMethod("literal", String.class).invoke(null, t.a);
            case "translatable" -> cc.getMethod("translatable", String.class, Object[].class).invoke(null, t.a, args);
            case "fallback" -> cc.getMethod("translatableWithFallback", String.class, String.class, Object[].class).invoke(null, t.a, t.b, args);
            case "keybind" -> cc.getMethod("keybind", String.class).invoke(null, t.a);
            default -> cc.getMethod("empty").invoke(null);
        };
        m = mc.getMethod("setStyle", c(cl, STYLE)).invoke(m, style(t.style, cl));
        for (TextSeed s : t.siblings) m = mc.getMethod("append", cc).invoke(m, text(s, cl));
        return m;
    }

    private static Object state(StateSeed ss, ClassLoader cl) throws ReflectiveOperationException {
        Object block = registryGet("BLOCK", ss.block, cl);
        Object st = c(cl, BLOCK).getMethod("defaultBlockState").invoke(block);
        Class<?> sh = c(cl, "net.minecraft.world.level.block.state.StateHolder"), pc = c(cl, PROPERTY);
        Method getName = pc.getMethod("getName"), valueName = pc.getMethod("getName", Comparable.class);
        List<Object> props = new ArrayList<>((Collection<?>) sh.getMethod("getProperties").invoke(st));
        props.sort(Comparator.comparing(p -> (String) call(getName, p)));
        Method setValue = sh.getMethod("setValue", pc, Comparable.class);
        for (int k = 0; k < props.size(); k++) {
            Object p = props.get(k);
            List<Object> vals = new ArrayList<>((Collection<?>) pc.getMethod("getPossibleValues").invoke(p));
            vals.sort(Comparator.comparing(v -> (String) call(valueName, p, v)));
            st = setValue.invoke(st, p, vals.get(Math.floorMod(ss.picks[k % ss.picks.length], vals.size())));
        }
        return st;
    }

    private static Object call(Method m, Object recv, Object... args) {
        try {
            return m.invoke(recv, args);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException(e.getCause());
        }
    }

    // ---- 내리기 ----

    /** 이 꾸러미가 내리는 형식이면 중립 값, 아니면 null. 레지스트리 값은 열쇠로, 상태는 속성 이름=값, 글은 평문 + 코덱 JSON. */
    static Object lower(Object v) throws TypeKit.Unsupported {
        try {
            ClassLoader cl = v.getClass().getClassLoader();
            if (cl == null) return null;
            if (is(v, COMPONENT, cl)) return List.of("Component", c(cl, COMPONENT).getMethod("getString").invoke(v), json(v, cl));
            if (is(v, BLOCK_STATE, cl)) return List.of("BlockState", key("BLOCK", v.getClass().getMethod("getBlock").invoke(v), cl), stateValues(v, cl));
            if (is(v, BLOCK, cl)) return List.of("Block", key("BLOCK", v, cl));
            if (is(v, "net.minecraft.world.level.material.Fluid", cl)) return List.of("Fluid", key("FLUID", v, cl));
            if (is(v, DATA_COMPONENT_TYPE, cl)) return List.of("DataComponentType", key("DATA_COMPONENT_TYPE", v, cl));
            if (is(v, HOVER_EVENT, cl)) {
                Class<?> h = c(cl, HOVER_EVENT), act = c(cl, HOVER_EVENT + "$Action");
                Object a = h.getMethod("getAction").invoke(v);
                Object val = h.getMethod("getValue", act).invoke(v, a);
                return List.of("HoverEvent", String.valueOf(act.getMethod("getSerializedName").invoke(a)), TypeKit.lower(val));
            }
            if (is(v, ITEM, cl)) return List.of("Item", key("ITEM", v, cl));
            if (is(v, ITEM_STACK, cl)) {
                Class<?> s = c(cl, ITEM_STACK);
                return List.of("ItemStack", key("ITEM", s.getMethod("getItem").invoke(v), cl), s.getMethod("getCount").invoke(v), s.getMethod("isEmpty").invoke(v));
            }
            return null;
        } catch (InvocationTargetException e) {
            throw new TypeKit.Unsupported("lower " + v.getClass().getName() + ": " + e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new TypeKit.Unsupported("lower " + v.getClass().getName() + ": " + e);
        }
    }

    /** 로더별 형식 찾기 기억(내리기는 값마다 불려 Class.forName 이 시험 시간을 잡아먹는다). 없는 형식은 null. */
    private static final Map<ClassLoader, Map<String, java.util.Optional<Class<?>>>> TYPES = new java.util.concurrent.ConcurrentHashMap<>();

    /** 그 로더에 형식이 없으면 거짓(그 형식의 값일 수 없다). */
    private static boolean is(Object v, String type, ClassLoader cl) {
        Class<?> k = find(cl, type);
        return k != null && k.isInstance(v);
    }

    /** 초기화까지 한 클래스(올리기에서 정적 필드를 읽으므로) 또는 null. */
    private static Class<?> find(ClassLoader cl, String type) {
        return TYPES.computeIfAbsent(cl, x -> new java.util.concurrent.ConcurrentHashMap<>()).computeIfAbsent(type, n -> {
            try {
                return java.util.Optional.of(Class.forName(n, true, cl));
            } catch (ClassNotFoundException e) {
                return java.util.Optional.empty();
            }
        }).orElse(null);
    }

    private static String key(String registry, Object v, ClassLoader cl) throws ReflectiveOperationException {
        Object reg = c(cl, BIR).getField(registry).get(null);
        return String.valueOf(c(cl, "net.minecraft.core.Registry").getMethod("getKey", Object.class).invoke(reg, v));
    }

    private static Map<String, String> stateValues(Object st, ClassLoader cl) throws ReflectiveOperationException {
        Class<?> pc = c(cl, PROPERTY);
        Method getName = pc.getMethod("getName"), valueName = pc.getMethod("getName", Comparable.class);
        Map<String, String> out = new TreeMap<>();
        for (Map.Entry<?, ?> e : ((Map<?, ?>) c(cl, "net.minecraft.world.level.block.state.StateHolder").getMethod("getValues").invoke(st)).entrySet()) {
            out.put((String) getName.invoke(e.getKey()), (String) valueName.invoke(e.getKey(), e.getValue()));
        }
        return out;
    }

    /**
     * Component.Serializer.toJson(글, 레지스트리) 로 적은 JSON 을 열쇠 순서 없는 나무로. 레지스트리 인자는 바닐라 RegistryAccess.EMPTY
     * (shim 은 그 상수가 없고 인자를 받기만 한다 — null). 오류면 "json error <예외 종류>".
     */
    private static Object json(Object comp, ClassLoader cl) throws ReflectiveOperationException {
        Class<?> provider = c(cl, "net.minecraft.core.HolderLookup$Provider");
        Object empty;
        try {
            empty = c(cl, "net.minecraft.core.RegistryAccess").getField("EMPTY").get(null);
        } catch (NoSuchFieldException e) {
            empty = null;
        }
        try {
            Object s = c(cl, COMPONENT + "$Serializer").getMethod("toJson", c(cl, COMPONENT), provider).invoke(null, comp, empty);
            return tree(com.google.gson.JsonParser.parseString((String) s));
        } catch (InvocationTargetException e) {
            return "json error " + e.getCause().getClass().getName();
        }
    }

    private static Object tree(com.google.gson.JsonElement e) {
        if (e.isJsonObject()) {
            Map<String, Object> m = new TreeMap<>();
            for (Map.Entry<String, com.google.gson.JsonElement> x : e.getAsJsonObject().entrySet()) m.put(x.getKey(), tree(x.getValue()));
            return m;
        }
        if (e.isJsonArray()) {
            List<Object> l = new ArrayList<>();
            for (com.google.gson.JsonElement x : e.getAsJsonArray()) l.add(tree(x));
            return l;
        }
        return e.toString();
    }
}
