package net.minecraft.core.component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.Unbreakable;
import org.domi.furnace.api.item.ItemComponent;
import org.domi.furnace.api.item.ItemComponents;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.compat.nms.NmsComponentWire;
import org.domi.furnace.compat.nms.RawComponentValue;

/**
 * 데이터 컴포넌트 종류 상수(Mojang 이름 shim, 일부). 값은 Furnace 아이템 컴포넌트 API 를 거쳐 읽고 쓴다(서버가 1.21.1 저장 꼴로 바꾼다).
 */
public final class DataComponents {

    private DataComponents() {}

    public static final DataComponentType<CustomData> CUSTOM_DATA = new Type<String, CustomData>("minecraft:custom_data", ItemComponent.raw("custom_data"),
            snbt -> CustomData.of(net.minecraft.nbt.TagParser.parseTag(snbt)), d -> d.copyTag().toString());
    public static final DataComponentType<Integer> MAX_STACK_SIZE = same("minecraft:max_stack_size", ItemComponents.MAX_STACK_SIZE);
    public static final DataComponentType<Integer> MAX_DAMAGE = same("minecraft:max_damage", ItemComponents.MAX_DAMAGE);
    public static final DataComponentType<Integer> DAMAGE = same("minecraft:damage", ItemComponents.DAMAGE);
    public static final DataComponentType<Integer> REPAIR_COST = same("minecraft:repair_cost", ItemComponents.REPAIR_COST);
    public static final DataComponentType<Component> CUSTOM_NAME = new Type<>("minecraft:custom_name", ItemComponents.CUSTOM_NAME,
            Component::fromFurnace, Component::furnace);
    public static final DataComponentType<Component> ITEM_NAME = new Type<>("minecraft:item_name", ItemComponents.ITEM_NAME,
            Component::fromFurnace, Component::furnace);
    public static final DataComponentType<CustomModelData> CUSTOM_MODEL_DATA = new Type<>("minecraft:custom_model_data",
            ItemComponents.CUSTOM_MODEL_DATA, CustomModelData::new, CustomModelData::value);
    public static final DataComponentType<Unbreakable> UNBREAKABLE = new Type<org.domi.furnace.api.item.Unbreakable, Unbreakable>("minecraft:unbreakable", ItemComponents.UNBREAKABLE,
            u -> new Unbreakable(u.showInTooltip()), u -> new org.domi.furnace.api.item.Unbreakable(u.showInTooltip()));
    public static final DataComponentType<ItemLore> LORE = new Type<List<Text>, ItemLore>("minecraft:lore", ItemComponents.LORE, lines -> {
        List<Component> out = new ArrayList<>(lines.size());
        for (Text t : lines) out.add(Component.fromFurnace(t));
        return new ItemLore(out);
    }, lore -> {
        List<Text> out = new ArrayList<>(lore.lines().size());
        for (Component c : lore.lines()) out.add(Component.furnace(c));
        return out;
    });

    /** 값 클래스를 두지 않은 종류(값은 {@link RawComponentValue}): 번들 내용·컨테이너·컨테이너 loot. */
    public static final DataComponentType<RawComponentValue> BUNDLE_CONTENTS = raw("minecraft:bundle_contents");
    public static final DataComponentType<RawComponentValue> CONTAINER = raw("minecraft:container");
    public static final DataComponentType<RawComponentValue> CONTAINER_LOOT = raw("minecraft:container_loot");

    private static final java.util.concurrent.ConcurrentHashMap<String, DataComponentType<?>> BY_KEY = new java.util.concurrent.ConcurrentHashMap<>();

    static {
        for (DataComponentType<?> t : new DataComponentType<?>[] {CUSTOM_DATA, MAX_STACK_SIZE, MAX_DAMAGE, DAMAGE, REPAIR_COST, CUSTOM_NAME, ITEM_NAME,
                CUSTOM_MODEL_DATA, UNBREAKABLE, LORE, BUNDLE_CONTENTS, CONTAINER, CONTAINER_LOOT}) {
            BY_KEY.put(t.furnaceKey(), t);
        }
    }

    private static DataComponentType<RawComponentValue> raw(String key) { return new Raw(key); }

    /**
     * 호환 모듈 전용: 그 키의 종류(값 변환이 있으면 {@link Type}, 아니면 늘 같은 {@link Raw} 객체). 서버 레지스트리에 없는 키는
     * IllegalArgumentException(바닐라 레지스트리 조회 실패와 같은 문구) — 아무 키나 영구히 쌓지 않는다.
     */
    public static DataComponentType<?> furnaceType(String key) {
        String k = key.indexOf(':') < 0 ? "minecraft:" + key : key;
        DataComponentType<?> t = BY_KEY.get(k);
        if (t == null) {
            org.domi.furnace.compat.nms.NmsDataComponentTypes.registry();   // 서버 목록의 종류를 채운다
            t = BY_KEY.get(k);
            if (t == null) throw new IllegalArgumentException("No element " + k + " in registry minecraft:data_component_type");
        }
        return t;
    }

    /** 호환 모듈 전용: 레지스트리를 만들 때 서버 목록의 키를 등록한다(상수가 아니면 값 없는 종류). */
    public static DataComponentType<?> furnaceRegister(String key) { return BY_KEY.computeIfAbsent(key, Raw::new); }

    /** 호환 모듈 전용: 값 클래스 없이 저장 꼴 SNBT 로 값을 주고받는 종류. */
    public static final class Raw implements DataComponentType<RawComponentValue> {
        private final String key;
        private final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, RawComponentValue> wire;

        Raw(String key) {
            this.key = key;
            this.wire = NmsComponentWire.codec(key, RawComponentValue::snbt, RawComponentValue::new);
        }

        @Override
        public String furnaceKey() { return key; }

        @Override
        public net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, RawComponentValue> streamCodec() { return wire; }

        @Override
        public String toString() { return key; }
    }

    private static DataComponentType<Integer> same(String key, ItemComponent<Integer> c) { return new Type<>(key, c, Function.identity(), Function.identity()); }

    /**
     * 호환 모듈 전용: Furnace 컴포넌트 키와 값 변환을 가진 종류.
     *
     * @param <F> Furnace 값 종류
     * @param <T> NMS 값 종류
     */
    public static final class Type<F, T> implements DataComponentType<T> {
        private final String key;
        private final ItemComponent<F> furnace;
        private final Function<F, T> toNms;
        private final Function<T, F> toFurnace;

        Type(String key, ItemComponent<F> furnace, Function<F, T> toNms, Function<T, F> toFurnace) {
            this.key = key;
            this.furnace = furnace;
            this.toNms = toNms;
            this.toFurnace = toFurnace;
        }

        @Override
        public String furnaceKey() { return key; }

        private volatile net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, T> wire;

        @Override
        public net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, T> streamCodec() {
            var w = wire;
            if (w == null) {
                wire = w = NmsComponentWire.codec(key, this::toSnbt, this::fromSnbt);
            }
            return w;
        }

        /** NMS 값 → 저장 꼴 SNBT. */
        public String toSnbt(T v) {
            return org.domi.furnace.api.item.ItemSupport.get().componentSnbt(furnace, toFurnace(v));
        }

        /** 저장 꼴 SNBT → NMS 값. */
        public T fromSnbt(String snbt) {
            return toNms(new org.domi.furnace.api.item.ItemStack("minecraft:stone", 1, java.util.Map.of(key, snbt), java.util.Set.of()).get(furnace));
        }

        public ItemComponent<F> furnace() { return furnace; }

        public T toNms(F v) { return v == null ? null : toNms.apply(v); }

        public F toFurnace(T v) { return toFurnace.apply(v); }

        @Override
        public String toString() { return key; }
    }

}
