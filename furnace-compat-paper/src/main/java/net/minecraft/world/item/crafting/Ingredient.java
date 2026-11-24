package net.minecraft.world.item.crafting;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/**
 * 재료(바닐라 1.21.1 Ingredient — 값 목록 {@link Value}: 아이템 하나 또는 태그). {@link #test} 는 바닐라와 같다: 비었으면 빈 스택만, 아니면 아이템 종류가 같으면
 * 맞다(Paper 의 {@link #exact} 면 컴포넌트까지). 코덱·쌓기 번호·스트림 코덱은 바닐라 서버 안에 있어 껍데기로 남는다(부르면 {@code UnsupportedNmsCall}).
 */
public class Ingredient implements Predicate<ItemStack> {

    public static final Ingredient EMPTY = new Ingredient(Stream.empty());

    private final Value[] values;
    private ItemStack[] itemStacks;
    /** Paper: 참이면 {@link #test} 가 컴포넌트까지 본다(CraftBukkit 의 정확한 재료). */
    public boolean exact;

    private Ingredient(Stream<? extends Value> values) { this.values = values.toArray(Value[]::new); }

    private Ingredient(Value[] values) { this.values = values; }

    public ItemStack[] getItems() {
        ItemStack[] cached = itemStacks;
        if (cached == null) {
            cached = Arrays.stream(values).flatMap(v -> v.getItems().stream()).distinct().toArray(ItemStack[]::new);
            itemStacks = cached;
        }
        return cached;
    }

    @Override
    public boolean test(ItemStack stack) {
        if (stack == null) return false;
        if (isEmpty()) return stack.isEmpty();
        for (ItemStack s : getItems()) {
            if (exact) {
                if (s.getItem() == stack.getItem() && ItemStack.isSameItemSameComponents(stack, s)) return true;
            } else if (s.is(stack.getItem())) {
                return true;
            }
        }
        return false;
    }

    public boolean isEmpty() { return values.length == 0; }

    @Override
    public boolean equals(Object o) { return o instanceof Ingredient other && Arrays.equals(values, other.values); }

    @Override
    public int hashCode() { return Arrays.hashCode(values); }

    private static Ingredient fromValues(Stream<? extends Value> values) {
        Ingredient ingredient = new Ingredient(values);
        return ingredient.isEmpty() ? EMPTY : ingredient;
    }

    public static Ingredient of() { return EMPTY; }

    public static Ingredient of(ItemLike... items) { return of(Arrays.stream(items).map(ItemStack::new)); }

    public static Ingredient of(ItemStack... stacks) { return of(Arrays.stream(stacks)); }

    public static Ingredient of(Stream<ItemStack> stacks) { return fromValues(stacks.filter(s -> !s.isEmpty()).map(ItemValue::new)); }

    public static Ingredient of(TagKey<Item> tag) { return fromValues(Stream.of(new TagValue(tag))); }

    /** 재료 값 하나가 내는 스택들. */
    public interface Value {
        Collection<ItemStack> getItems();
    }

    /** 아이템 하나(스택). 같음은 종류와 개수만 본다. */
    public record ItemValue(ItemStack item) implements Value {
        @Override
        public boolean equals(Object o) {
            return o instanceof ItemValue other && other.item.getItem().equals(item.getItem()) && other.item.getCount() == item.getCount();
        }

        @Override
        public Collection<ItemStack> getItems() { return Collections.singleton(item); }
    }

    /** 태그 하나. 같음은 태그 이름만 본다. */
    public record TagValue(TagKey<Item> tag) implements Value {
        @Override
        public boolean equals(Object o) { return o instanceof TagValue other && other.tag.location().equals(tag.location()); }

        @Override
        public Collection<ItemStack> getItems() {
            List<ItemStack> out = new java.util.ArrayList<>();
            for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) out.add(new ItemStack(holder.value()));
            return out;
        }
    }
}
