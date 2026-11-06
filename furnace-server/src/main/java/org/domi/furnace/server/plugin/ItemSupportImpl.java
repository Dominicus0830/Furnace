// 아이템 컴포넌트·영속 데이터의 서버 구현. API 스택은 컴포넌트를 SNBT 로 들고 있고, 서버는 컴포넌트마다 코덱(정규 값)이 있다 —
// 읽기는 SNBT → 코덱 정규 값 → API 타입, 쓰기는 그 반대로 코덱을 거쳐 바닐라 규칙(범위·필수 필드)을 지킨다.
// 영속 데이터는 Paper 와 같은 자리(custom_data 의 PublicBukkitValues)에 둔다.
package org.domi.furnace.server.plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import org.domi.furnace.api.item.AttributeModifiers;
import org.domi.furnace.api.item.DyedColor;
import org.domi.furnace.api.item.ItemComponent;
import org.domi.furnace.api.item.ItemComponents;
import org.domi.furnace.api.item.ItemEnchantments;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.item.ItemSupport;
import org.domi.furnace.api.item.Marker;
import org.domi.furnace.api.item.Rarity;
import org.domi.furnace.api.item.Unbreakable;
import org.domi.furnace.api.persistence.PersistentDataContainer;
import org.domi.furnace.api.persistence.PersistentDataView;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.item.CodecException;
import org.domi.furnace.server.item.ComponentType;
import org.domi.furnace.server.item.DataComponents;
import org.domi.furnace.server.item.FlatText;
import org.domi.furnace.server.item.ItemPrototypes;
import org.domi.furnace.server.item.Snbt;
import org.domi.furnace.server.item.Val;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public final class ItemSupportImpl implements ItemSupport {

    public static final ItemSupportImpl INSTANCE = new ItemSupportImpl();

    static final String CUSTOM_DATA = "minecraft:custom_data";
    /** Paper 가 아이템 영속 데이터를 두는 custom_data 안의 키. */
    static final String PUBLIC_BUKKIT_VALUES = "PublicBukkitValues";

    private ItemSupportImpl() {}

    /** 정규 값 ↔ API 값. write 는 코덱이 읽는 JSON 꼴 값을 돌려준다. */
    private record Adapter<T>(Function<Object, T> read, Function<T, Object> write) {}

    private static final Map<String, Adapter<?>> ADAPTERS = new HashMap<>();

    private static <T> void add(ItemComponent<T> c, Function<Object, T> read, Function<T, Object> write) {
        ADAPTERS.put(c.id(), new Adapter<>(read, write));
    }

    private static boolean flag(Map<String, Object> m, String key) { return !m.containsKey(key) || Val.bool(m.get(key)); }

    private static void addInt(ItemComponent<Integer> c) { add(c, v -> Val.number(v).intValue(), i -> i); }

    private static void addMarker(ItemComponent<Marker> c) { add(c, v -> Marker.PRESENT, m -> new LinkedHashMap<String, Object>()); }

    private static ItemEnchantments enchantments(Object v) {
        Map<String, Object> m = Val.map(v);
        Map<String, Integer> levels = new LinkedHashMap<>();
        for (var e : Val.map(m.get("levels")).entrySet()) levels.put(e.getKey(), Val.number(e.getValue()).intValue());
        return new ItemEnchantments(levels, flag(m, "show_in_tooltip"));
    }

    private static Object enchantments(ItemEnchantments e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("levels", new LinkedHashMap<String, Object>(e.levels()));
        m.put("show_in_tooltip", e.showInTooltip());
        return m;
    }

    static {
        add(ItemComponents.CUSTOM_NAME, v -> FlatText.parse(Val.string(v)), Text::toJson);
        add(ItemComponents.ITEM_NAME, v -> FlatText.parse(Val.string(v)), Text::toJson);
        add(ItemComponents.LORE, v -> {
            List<Text> out = new ArrayList<>();
            for (Object o : Val.list(v)) out.add(FlatText.parse(Val.string(o)));
            return out;
        }, l -> {
            List<Object> out = new ArrayList<>();
            for (Text t : l) out.add(t.toJson());
            return out;
        });
        add(ItemComponents.RARITY, v -> Rarity.byId(Val.string(v)), Rarity::id);
        addInt(ItemComponents.MAX_STACK_SIZE);
        addInt(ItemComponents.MAX_DAMAGE);
        addInt(ItemComponents.DAMAGE);
        addInt(ItemComponents.REPAIR_COST);
        addInt(ItemComponents.CUSTOM_MODEL_DATA);
        addInt(ItemComponents.MAP_COLOR);
        addInt(ItemComponents.MAP_ID);
        addInt(ItemComponents.OMINOUS_BOTTLE_AMPLIFIER);
        add(ItemComponents.UNBREAKABLE, v -> new Unbreakable(flag(Val.map(v), "show_in_tooltip")), u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("show_in_tooltip", u.showInTooltip());
            return m;
        });
        add(ItemComponents.ENCHANTMENTS, ItemSupportImpl::enchantments, ItemSupportImpl::enchantments);
        add(ItemComponents.STORED_ENCHANTMENTS, ItemSupportImpl::enchantments, ItemSupportImpl::enchantments);
        add(ItemComponents.ENCHANTMENT_GLINT_OVERRIDE, Val::bool, b -> b);
        add(ItemComponents.DYED_COLOR, v -> {
            Map<String, Object> m = Val.map(v);
            return new DyedColor(Val.number(m.get("rgb")).intValue(), flag(m, "show_in_tooltip"));
        }, d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("rgb", d.rgb());
            m.put("show_in_tooltip", d.showInTooltip());
            return m;
        });
        add(ItemComponents.ATTRIBUTE_MODIFIERS, v -> {
            Map<String, Object> m = Val.map(v);
            List<AttributeModifiers.Entry> out = new ArrayList<>();
            for (Object o : Val.list(m.get("modifiers"))) {
                Map<String, Object> e = Val.map(o);
                Object slot = e.get("slot");
                out.add(new AttributeModifiers.Entry(Val.string(e.get("type")), Val.string(e.get("id")), Val.number(e.get("amount")).doubleValue(),
                        AttributeModifiers.Operation.byId(Val.string(e.get("operation"))), slot == null ? "any" : Val.string(slot)));
            }
            return new AttributeModifiers(out, flag(m, "show_in_tooltip"));
        }, a -> {
            List<Object> list = new ArrayList<>();
            for (var e : a.modifiers()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("type", e.attribute());
                m.put("id", e.id());
                m.put("amount", e.amount());
                m.put("operation", e.operation().id());
                m.put("slot", e.slot());
                list.add(m);
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("modifiers", list);
            m.put("show_in_tooltip", a.showInTooltip());
            return m;
        });
        addMarker(ItemComponents.HIDE_TOOLTIP);
        addMarker(ItemComponents.HIDE_ADDITIONAL_TOOLTIP);
        addMarker(ItemComponents.FIRE_RESISTANT);
        addMarker(ItemComponents.INTANGIBLE_PROJECTILE);
        add(ItemComponents.LOCK, Val::string, s -> s);
        add(ItemComponents.NOTE_BLOCK_SOUND, Val::string, s -> s);
        add(ItemComponents.BASE_COLOR, Val::string, s -> s);
        add(ItemComponents.RECIPES, v -> {
            List<String> out = new ArrayList<>();
            for (Object o : Val.list(v)) out.add(Val.string(o));
            return out;
        }, l -> new ArrayList<Object>(l));
        add(ItemComponents.BLOCK_STATE, v -> {
            Map<String, String> out = new LinkedHashMap<>();
            for (var e : Val.map(v).entrySet()) out.put(e.getKey(), Val.string(e.getValue()));
            return out;
        }, m -> new LinkedHashMap<String, Object>(m));
    }

    // ------------------------------------------------------------------ 컴포넌트

    private static ComponentType type(ItemComponent<?> c) {
        ComponentType t = DataComponents.byName(c.id());
        if (t == null) throw new IllegalArgumentException("알 수 없는 컴포넌트: " + c.id());
        return t;
    }

    @SuppressWarnings("unchecked")
    private static <T> Adapter<T> adapter(ItemComponent<T> c) {
        Adapter<?> a = ADAPTERS.get(c.id());
        if (a == null) throw new IllegalArgumentException("타입 있는 값을 지원하지 않는 컴포넌트: " + c.id() + " — ItemComponent.raw 를 쓴다");
        return (Adapter<T>) a;
    }

    private static IllegalArgumentException bad(ItemComponent<?> c, RuntimeException e) {
        return new IllegalArgumentException(c.id() + " 값이 규칙에 맞지 않는다: " + e.getMessage(), e);
    }

    /** 스택에 붙은 값(정규), 없으면 아이템 기본값, 지워졌으면 null. */
    private static Object effective(ItemStack s, ComponentType t) {
        if (s.removedComponents().contains(t.name())) return null;
        String snbt = s.components().get(t.name());
        if (snbt == null) return ItemPrototypes.defaultValue(s.type(), t.name());
        if (t.isTransient()) return Snbt.parse(snbt);
        return t.normalize(Snbt.parse(snbt));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(ItemStack stack, ItemComponent<T> component) {
        ComponentType t = type(component);
        try {
            Object v = effective(stack, t);
            if (v == null) return null;
            if (component.isRaw()) {
                if (t.isTransient()) throw new IllegalArgumentException(t.name() + " 는 저장하지 않는 컴포넌트라 문자열로 읽을 수 없다");
                return (T) Snbt.write(t.persistent().toNbt(v));
            }
            return adapter(component).read().apply(v);
        } catch (CodecException | ClassCastException e) {
            throw bad(component, e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> ItemStack set(ItemStack stack, ItemComponent<T> component, T value) {
        ComponentType t = type(component);
        if (t.isTransient()) throw new IllegalArgumentException(t.name() + " 는 저장하지 않는 컴포넌트다");
        Object norm;
        try {
            Object in = component.isRaw() ? Snbt.parse((String) value) : adapter(component).write().apply(value);
            norm = t.normalize(in);
        } catch (CodecException | ClassCastException e) {
            throw bad(component, e);
        }
        Object def = ItemPrototypes.defaultValue(stack.type(), t.name());
        if (def != null && t.same(norm, def)) return stripped(stack, t.name());
        return stack.withComponent(t.name(), Snbt.write(t.persistent().toNbt(norm)));
    }

    @Override
    public ItemStack remove(ItemStack stack, ItemComponent<?> component) {
        ComponentType t = type(component);
        if (ItemPrototypes.defaultValue(stack.type(), t.name()) != null) return stack.withoutComponent(t.name());
        return stripped(stack, t.name());
    }

    /** 패치에서 그 컴포넌트의 흔적(값·지움 표시)을 모두 뗀다. */
    private static ItemStack stripped(ItemStack s, String id) {
        if (!s.components().containsKey(id) && !s.removedComponents().contains(id)) return s;
        TreeMap<String, String> m = new TreeMap<>(s.components());
        m.remove(id);
        TreeSet<String> r = new TreeSet<>(s.removedComponents());
        r.remove(id);
        return new ItemStack(s.type(), s.amount(), m, r);
    }

    // ------------------------------------------------------------------ 영속 데이터

    /** 스택의 custom_data(없으면 새 빈 compound). 값이 compound 가 아니면 비어 있는 것으로 본다. */
    private static NbtCompound customData(ItemStack s) {
        String snbt = s.components().get(CUSTOM_DATA);
        if (snbt == null) return new NbtCompound();
        return Snbt.parse(snbt) instanceof NbtCompound c ? c : new NbtCompound();
    }

    @Override
    public PersistentDataView persistentData(ItemStack stack) {
        NbtCompound root = customData(stack);
        return NbtPersistentData.snapshot(root.tags().get(PUBLIC_BUKKIT_VALUES) instanceof NbtCompound c ? c : null);
    }

    @Override
    public ItemStack editPersistentData(ItemStack stack, Consumer<PersistentDataContainer> edit) {
        NbtCompound root = customData(stack);
        NbtCompound values = root.tags().get(PUBLIC_BUKKIT_VALUES) instanceof NbtCompound c ? c.copy() : new NbtCompound();
        String before = Snbt.write(values);
        edit.accept(new NbtPersistentData(values, false));
        if (Snbt.write(values).equals(before)) return stack;
        if (values.tags().isEmpty()) root.tags().remove(PUBLIC_BUKKIT_VALUES);
        else root.tags().put(PUBLIC_BUKKIT_VALUES, values);
        if (root.tags().isEmpty()) return stripped(stack, CUSTOM_DATA);
        return stack.withComponent(CUSTOM_DATA, Snbt.write(root));
    }
}
