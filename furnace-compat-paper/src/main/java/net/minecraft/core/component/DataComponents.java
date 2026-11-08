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

        public ItemComponent<F> furnace() { return furnace; }

        public T toNms(F v) { return v == null ? null : toNms.apply(v); }

        public F toFurnace(T v) { return toFurnace.apply(v); }

        @Override
        public String toString() { return key; }
    }

}
