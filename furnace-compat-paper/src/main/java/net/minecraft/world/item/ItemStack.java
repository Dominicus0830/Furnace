package net.minecraft.world.item;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.world.level.ItemLike;
import org.domi.furnace.api.item.ItemComponents;

/**
 * 아이템 스택 shim(Mojang 이름, H6 최소 표면). 바닐라처럼 <b>가변</b>이고 equals 는 객체 정체성이다(바닐라도 equals 를 덮지 않는다).
 * 종류·개수·데이터 컴포넌트(Furnace 아이템의 SNBT 패치)를 들고, {@code CraftItemStack.asNMSCopy/asBukkitCopy/asCraftMirror} 로 Bukkit 스택과 오간다.
 * 비었나·개수·같은 종류·같은 컴포넌트·복사·나누기는 바닐라 1.21.1 바이트코드와 같은 규칙이다. 컴포넌트를 타입으로 읽고 쓰는
 * {@code get(DataComponentType)}·{@code set} 은 H7(값 shim)이 더한다.
 */
public final class ItemStack {

    /** 빈 스택(바닐라처럼 공유 객체 — 바꾸지 말 것). */
    public static final ItemStack EMPTY = new ItemStack();

    private Item item;
    private int count;
    private Map<String, String> components;
    private Set<String> removed;

    private ItemStack() {
        this.item = null;
        this.count = 0;
        this.components = Map.of();
        this.removed = Set.of();
    }

    public ItemStack(ItemLike item) { this(item, 1); }

    public ItemStack(ItemLike item, int count) {
        this.item = Objects.requireNonNull(item, "item").asItem();
        this.count = count;
        this.components = Map.of();
        this.removed = Set.of();
    }

    private ItemStack(Item item, int count, Map<String, String> components, Set<String> removed) {
        this.item = item;
        this.count = count;
        this.components = components;
        this.removed = removed;
    }

    // ------------------------------------------------------------------ Furnace 다리(호환 모듈 전용)

    /** 호환 모듈 전용 — Furnace 값과 개수로(개수 0·음수도 그대로 — 비었나는 바닐라 규칙으로 본다). 빈 값이면 EMPTY 가 아닌 새 빈 스택. */
    public static ItemStack fromFurnace(org.domi.furnace.api.item.ItemStack value, int count) {
        return new ItemStack(Item.byKey(value.type()), count, value.components(), value.removedComponents());
    }

    /** 호환 모듈 전용 — 지금 값(비었으면 Furnace EMPTY). */
    public org.domi.furnace.api.item.ItemStack furnaceValue() {
        if (isEmpty()) return org.domi.furnace.api.item.ItemStack.EMPTY;
        return new org.domi.furnace.api.item.ItemStack(item.furnaceKey(), count, components, removed);
    }

    /** 호환 모듈 전용 — 종류·컴포넌트·개수를 Furnace 값으로 바꾼다(CraftItemStack 거울의 쓰기). EMPTY 는 바꾸지 않는다. */
    public void furnaceSet(org.domi.furnace.api.item.ItemStack value) {
        if (this == EMPTY) throw new IllegalStateException("ItemStack.EMPTY 는 바꿀 수 없다");
        this.item = Item.byKey(value.type());
        this.count = value.amount();
        this.components = value.components();
        this.removed = value.removedComponents();
    }

    /** 호환 모듈 전용 — 종류만 바꾼다(개수·컴포넌트 패치는 그대로, CraftBukkit 의 setItem 자리). EMPTY 는 바꾸지 않는다. */
    public void furnaceSetItem(Item item) {
        if (this == EMPTY) throw new IllegalStateException("ItemStack.EMPTY 는 바꿀 수 없다");
        this.item = java.util.Objects.requireNonNull(item, "item");
    }

    // ------------------------------------------------------------------ 바닐라 표면

    /** 바닐라: EMPTY 이거나 종류가 공기이거나 개수가 0 이하. */
    public boolean isEmpty() { return this == EMPTY || item == Item.AIR || count <= 0; }

    /** 비었으면 공기. */
    public Item getItem() { return isEmpty() ? Item.AIR : item; }

    public boolean is(Item other) { return getItem() == other; }

    /** 비었으면 0. */
    public int getCount() { return isEmpty() ? 0 : count; }

    public void setCount(int count) { this.count = count; }

    public void grow(int n) { setCount(getCount() + n); }

    public void shrink(int n) { grow(-n); }

    public ItemStack copy() {
        if (isEmpty()) return EMPTY;
        return new ItemStack(item, count, components, removed);
    }

    public ItemStack copyWithCount(int n) {
        if (isEmpty()) return EMPTY;
        ItemStack s = copy();
        s.setCount(n);
        return s;
    }

    /** 앞에서 n 개(가진 것보다 많으면 가진 만큼)를 떼어 새 스택으로 주고 이 스택에서는 뺀다. */
    public ItemStack split(int n) {
        int take = Math.min(n, getCount());
        ItemStack out = copyWithCount(take);
        shrink(take);
        return out;
    }

    /**
     * 컴포넌트를 읽을 값 — 바닐라 컴포넌트 조회는 isEmpty 를 보지 않는다(개수 0 인 엔더 진주도 16). EMPTY 는 컴포넌트가 하나도 없어 null.
     */
    private org.domi.furnace.api.item.ItemStack componentView() {
        if (this == EMPTY) return null;
        return new org.domi.furnace.api.item.ItemStack(item.furnaceKey(), Math.max(count, 0), components, removed);
    }

    /** 바닐라 getOrDefault(MAX_STACK_SIZE, 1) — 종류 기본값 위에 패치를 얹은 값, 패치로 지웠거나 EMPTY 면 1. */
    public int getMaxStackSize() {
        var v = componentView();
        return v == null ? 1 : v.getOrDefault(ItemComponents.MAX_STACK_SIZE, 1);
    }

    /** 바닐라 getOrDefault(MAX_DAMAGE, 0) — 내구도가 없는 아이템·EMPTY 는 0. */
    public int getMaxDamage() {
        var v = componentView();
        return v == null ? 0 : v.getOrDefault(ItemComponents.MAX_DAMAGE, 0);
    }

    /** 바닐라 Mth.clamp(getOrDefault(DAMAGE, 0), 0, getMaxDamage()) — 내구도 없는 아이템의 damage 는 0 으로 잘린다. */
    public int getDamageValue() {
        var v = componentView();
        return v == null ? 0 : Math.min(Math.max(v.damage(), 0), getMaxDamage());
    }

    /** 같은 종류인가(바닐라 isSameItem). */
    public static boolean isSameItem(ItemStack a, ItemStack b) { return a.is(b.getItem()); }

    /** 같은 종류이고, 둘 다 비었거나 컴포넌트가 같은가(바닐라 isSameItemSameComponents). */
    public static boolean isSameItemSameComponents(ItemStack a, ItemStack b) {
        if (!a.is(b.getItem())) return false;
        if (a.isEmpty() && b.isEmpty()) return true;
        return a.components.equals(b.components) && a.removed.equals(b.removed);
    }

    /** 같은 객체이거나, 개수가 같고 isSameItemSameComponents(바닐라 matches). */
    public static boolean matches(ItemStack a, ItemStack b) {
        if (a == b) return true;
        return a.getCount() == b.getCount() && isSameItemSameComponents(a, b);
    }

    /** 바닐라와 같다: "개수 종류"(예: 3 minecraft:stone). */
    @Override
    public String toString() { return getCount() + " " + getItem(); }
}
