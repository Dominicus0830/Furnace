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
 * {@code get(DataComponentType)}·{@code set}·{@code remove} 는 {@link net.minecraft.core.component.DataComponents} 에 있는 종류만 된다(H7b).
 */
public final class ItemStack {

    /** 빈 스택(바닐라처럼 공유 객체 — 바꾸지 말 것). */
    public static final ItemStack EMPTY = new ItemStack();

    /**
     * Paper 1.21.1 {@code OPTIONAL_STREAM_CODEC}: VarInt 개수(0 이하면 빈 스택, 뒤는 없다) → 아이템 번호 → 컴포넌트 패치. 바이트는 서버의 같은 코덱이 만든다
     * (서버가 클라이언트에 보내는 아이템과 같은 바이트). 아무 스레드.
     */
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, ItemStack> OPTIONAL_STREAM_CODEC =
            new net.minecraft.network.codec.StreamCodec<>() {
                @Override
                public ItemStack decode(net.minecraft.network.RegistryFriendlyByteBuf buf) {
                    var w = org.domi.furnace.compat.nms.NmsChunks.bridge().readItemStackWire(buf.nioBuffer(buf.readerIndex(), buf.readableBytes()));
                    buf.skipBytes(w.length());
                    return w.stack().isEmpty() ? EMPTY : fromFurnace(w.stack(), w.stack().amount());
                }

                @Override
                public void encode(net.minecraft.network.RegistryFriendlyByteBuf buf, ItemStack stack) {
                    buf.writeBytes(org.domi.furnace.compat.nms.NmsChunks.bridge().itemStackWireBytes(stack.furnaceValue()));
                }
            };

    /** Paper 1.21.1 {@code STREAM_CODEC}: {@link #OPTIONAL_STREAM_CODEC} 와 같되 빈 스택은 읽기·쓰기 모두 "Empty ItemStack not allowed". */
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, ItemStack> STREAM_CODEC =
            new net.minecraft.network.codec.StreamCodec<>() {
                @Override
                public ItemStack decode(net.minecraft.network.RegistryFriendlyByteBuf buf) {
                    ItemStack s = OPTIONAL_STREAM_CODEC.decode(buf);
                    if (s.isEmpty()) throw new io.netty.handler.codec.DecoderException("Empty ItemStack not allowed");
                    return s;
                }

                @Override
                public void encode(net.minecraft.network.RegistryFriendlyByteBuf buf, ItemStack stack) {
                    if (stack.isEmpty()) throw new io.netty.handler.codec.EncoderException("Empty ItemStack not allowed");
                    OPTIONAL_STREAM_CODEC.encode(buf, stack);
                }
            };

    private Item item;
    private int count;
    private Map<String, String> components;
    private Set<String> removed;
    /** 접속한 플레이어의 손 칸에 묶인 보기면 값이 있다({@link #liveHand}) — 바꾸면 그 칸에 되쓴다. */
    private LiveSlot live;

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
        if (this == EMPTY) throw new IllegalStateException("ItemStack.EMPTY cannot be modified");
        this.item = Item.byKey(value.type());
        this.count = value.amount();
        this.components = value.components();
        this.removed = value.removedComponents();
        changed();
    }

    /** 호환 모듈 전용 — 종류만 바꾼다(개수·컴포넌트 패치는 그대로, CraftBukkit 의 setItem 자리). EMPTY 는 바꾸지 않는다. */
    public void furnaceSetItem(Item item) {
        if (this == EMPTY) throw new IllegalStateException("ItemStack.EMPTY cannot be modified");
        this.item = java.util.Objects.requireNonNull(item, "item");
        changed();
    }

    // ------------------------------------------------------------------ 바닐라 표면

    /** 바닐라: EMPTY 이거나 종류가 공기이거나 개수가 0 이하. */
    public boolean isEmpty() { return this == EMPTY || item == Item.AIR || count <= 0; }

    /** 비었으면 공기. */
    public Item getItem() { return isEmpty() ? Item.AIR : item; }

    public boolean is(Item other) { return getItem() == other; }

    /** 바닐라: 아이템의 레지스트리 손잡이가 태그에 들었는가(지금 걸린 태그). */
    public boolean is(net.minecraft.tags.TagKey<Item> tag) { return getItem().builtInRegistryHolder().is(tag); }

    public boolean is(net.minecraft.core.Holder<Item> holder) { return is(holder.value()); }

    public boolean is(net.minecraft.core.HolderSet<Item> set) { return set.contains(getItemHolder()); }

    public net.minecraft.core.Holder<Item> getItemHolder() { return getItem().builtInRegistryHolder(); }

    public java.util.stream.Stream<net.minecraft.tags.TagKey<Item>> getTags() { return getItem().builtInRegistryHolder().tags(); }

    /** 비었으면 0. */
    public int getCount() { return isEmpty() ? 0 : count; }

    public void setCount(int count) {
        this.count = count;
        changed();
    }

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

    /**
     * 바닐라 getComponentsPatch: 비었으면 EMPTY, 아니면 기본값에서 바뀐 것(값·지움). 지금 값의 사본이라 이 스택을 고쳐도 따라가지 않는다. 값은 Furnace 가
     * 들고 있는 SNBT 꼴 그대로이고(바닐라는 타입 값), {@code DataComponentPatch.CODEC} 이 컴포넌트 규칙으로 정규화해 쓴다.
     */
    public net.minecraft.core.component.DataComponentPatch getComponentsPatch() {
        return isEmpty() ? net.minecraft.core.component.DataComponentPatch.EMPTY : net.minecraft.core.component.DataComponentPatch.furnaceOf(components, removed);
    }

    /**
     * 바닐라 applyComponents(patch): 값은 set, 지움은 remove 규칙으로 하나씩 얹는다(기본값과 같은 값은 패치에서 뗀다). 바닐라는 끝에
     * {@code verifyComponentsAfterLoad}(머리 프로필 확인)도 부르지만, Furnace 는 그 확인을 스택이 놓이는 칸의 쓰기 지점에서 한다(서버 {@code ProfileFill}).
     * 모르는 컴포넌트·규칙에 안 맞는 값(패치를 직접 만든 경우)은 IllegalArgumentException. EMPTY 는 바닐라처럼 조용히 아무것도 하지 않는다.
     */
    public void applyComponents(net.minecraft.core.component.DataComponentPatch patch) {
        if (patch.isEmpty() || this == EMPTY) return;   // 바닐라: EMPTY 는 components 가 없어서 아무것도 바뀌지 않는다(던지지 않는다)
        var next = org.domi.furnace.api.item.ItemSupport.get().applyComponentPatch(componentView(),
                new org.domi.furnace.api.item.ItemComponentPatch(patch.furnaceSet(), patch.furnaceRemoved()));
        this.components = next.components();
        this.removed = next.removedComponents();
        changed();
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

    /**
     * 바닐라 setDamageValue(damage): 0..최대 내구도로 잘라 damage 컴포넌트에 넣는다(내구도 없는 아이템은 0 으로 잘려 바뀌지 않는다).
     * 손에 묶인 보기면 그 칸에 되쓴다.
     */
    public void setDamageValue(int damage) { set(net.minecraft.core.component.DataComponents.DAMAGE, Math.clamp(damage, 0, getMaxDamage())); }

    /** 바닐라 getEnchantments: enchantments 컴포넌트(없으면 EMPTY)의 사본. 읽기 전용 — 고쳐도 이 아이템은 안 바뀐다. */
    public net.minecraft.world.item.enchantment.ItemEnchantments getEnchantments() {
        var v = componentView();
        return v == null ? net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY
                : net.minecraft.world.item.enchantment.ItemEnchantments.fromFurnace(v.get(ItemComponents.ENCHANTMENTS));
    }

    /**
     * 바닐라 isCorrectToolForDrops(state): tool 컴포넌트의 규칙이 이 상태를 맞는 도구로 치는가(없으면 거짓). 규칙은 서버의 도구 판정
     * ({@code ItemStack.isCorrectToolForDrops})과 같은 것이다.
     */
    public boolean isCorrectToolForDrops(net.minecraft.world.level.block.state.BlockState state) {
        return !isEmpty() && org.domi.furnace.compat.nms.NmsChunks.bridge().correctToolForDrops(furnaceValue(), state.furnaceStateId());
    }

    // ------------------------------------------------------------------ 손에 묶인 보기(NMS shim 5번 묶음)

    private static final class LiveSlot {
        /** 칸이 아직 expect 이면 now 로 바꾼다(그새 바뀌었으면 false — 칸에서 떨어진다). 쓸 수 없는 칸은 던진다. */
        final java.util.function.BiPredicate<org.domi.furnace.api.item.ItemStack, org.domi.furnace.api.item.ItemStack> writer;
        org.domi.furnace.api.item.ItemStack last;

        LiveSlot(java.util.function.BiPredicate<org.domi.furnace.api.item.ItemStack, org.domi.furnace.api.item.ItemStack> writer,
                 org.domi.furnace.api.item.ItemStack last) {
            this.writer = writer;
            this.last = last;
        }
    }

    /**
     * 호환 모듈 전용 — 접속한 플레이어의 손 칸을 바닐라처럼 "그 칸의 스택 객체"로 준다. 이 객체를 고치면(개수·컴포넌트·내구도) 그 칸의 값이 같은 순간 바뀐다.
     * 그새 칸이 다른 스택으로 바뀌었으면 이 객체는 칸에서 떨어진다(바닐라에서 칸에서 빠진 객체와 같다 — 이후 고쳐도 칸은 안 바뀐다). 빈 칸은 EMPTY.
     * 읽기와 되쓰기는 서버 인벤토리의 잠금 안에서 일어난다(아무 스레드).
     */
    public static ItemStack liveHand(org.domi.furnace.api.entity.Player player, boolean mainHand) {
        org.domi.furnace.api.internal.ChunkInternals.HandSlot hs;
        try {
            hs = org.domi.furnace.compat.nms.NmsChunks.bridge().handSlotNow(player, mainHand);
        } catch (IllegalArgumentException e) {
            throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("LivingEntity#getItemInHand", e.getMessage());
        }
        return live(player, hs.windowSlot(), hs.stack());
    }

    /**
     * 호환 모듈 전용 — 접속한 플레이어의 창 칸(갑옷 칸 등, {@code getItemBySlot})을 {@link #liveHand} 와 같은 규칙의 살아 있는 보기로 준다. 빈 칸은 EMPTY.
     */
    public static ItemStack liveSlot(org.domi.furnace.api.entity.Player player, int windowSlot) {
        org.domi.furnace.api.item.ItemStack now;
        try {
            now = org.domi.furnace.compat.nms.NmsChunks.bridge().windowSlotNow(player, windowSlot);
        } catch (IllegalArgumentException e) {
            throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("LivingEntity#getItemBySlot", e.getMessage());
        }
        return live(player, windowSlot, now);
    }

    private static ItemStack live(org.domi.furnace.api.entity.Player player, int windowSlot, org.domi.furnace.api.item.ItemStack now) {
        if (now.isEmpty()) return EMPTY;
        ItemStack s = fromFurnace(now, now.amount());
        s.live = new LiveSlot((expect, next) -> org.domi.furnace.compat.nms.NmsChunks.bridge().replaceSlotNow(player, windowSlot, expect, next), now);
        return s;
    }

    /**
     * 호환 모듈 전용 — 열린 창(번호 containerId)의 slot 번 칸을 살아 있는 보기로 준다(바닐라 {@code Slot#getItem}). 플레이어 인벤토리 칸은 {@link #liveSlot} 과 같은
     * 규칙으로 고치면 그 칸에 되쓰고, 창의 컨테이너 칸(상자·제작 결과 …)은 클라이언트에 마지막으로 알린 값의 사본이며 고치면 {@code UnsupportedNmsCall} 이다
     * (그 컨테이너를 쥔 스레드에서만 바꿀 수 있다). 빈 칸은 EMPTY.
     */
    public static ItemStack liveMenuSlot(org.domi.furnace.api.entity.Player player, int containerId, int slot, boolean playerSlot,
                                         org.domi.furnace.api.item.ItemStack now) {
        if (now.isEmpty()) return EMPTY;
        ItemStack s = fromFurnace(now, now.amount());
        s.live = new LiveSlot(playerSlot
                ? (expect, next) -> org.domi.furnace.compat.nms.NmsChunks.bridge().replaceMenuSlotNow(player, containerId, slot, expect, next)
                : (expect, next) -> {
                    throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("Slot#getItem", "slot " + slot + " of the open window belongs to the container of the window;"
                            + " only the thread that owns that container may change it (use the Bukkit InventoryView of the window)");
                }, now);
        return s;
    }

    /** 바뀐 값을 묶인 칸에 되쓴다. 칸이 그새 바뀌었으면 칸에서 떨어진다. */
    private void changed() {
        LiveSlot l = live;
        if (l == null) return;
        var now = furnaceValue();
        if (l.writer.test(l.last, now)) l.last = now;
        else live = null;
    }

    // ------------------------------------------------------------------ 데이터 컴포넌트(H7b, DataComponents 에 있는 종류만)

    private static net.minecraft.core.component.DataComponents.Type<?, ?> type(net.minecraft.core.component.DataComponentType<?> t) {
        if (t instanceof net.minecraft.core.component.DataComponents.Type<?, ?> k) return k;
        throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("DataComponentType " + t, "the shim has no value conversion for this kind (only the constants of DataComponents work)");
    }

    /** 종류 기본값 위에 패치를 얹은 값(바닐라 get — EMPTY 는 늘 null). */
    @SuppressWarnings("unchecked")
    public <T> T get(net.minecraft.core.component.DataComponentType<? extends T> t) {
        var k = (net.minecraft.core.component.DataComponents.Type<Object, T>) type(t);
        var v = componentView();
        return v == null ? null : k.toNms(v.get(k.furnace()));
    }

    public <T> T getOrDefault(net.minecraft.core.component.DataComponentType<? extends T> t, T fallback) {
        T v = get(t);
        return v != null ? v : fallback;
    }

    public boolean has(net.minecraft.core.component.DataComponentType<?> t) { return get(t) != null; }

    /** 값을 넣고 이전 값을 돌려준다(null 을 넣으면 지운다 — 바닐라 PatchedDataComponentMap.set 과 같다). */
    @SuppressWarnings("unchecked")
    public <T> T set(net.minecraft.core.component.DataComponentType<? super T> t, T value) {
        if (this == EMPTY) throw new IllegalStateException("ItemStack.EMPTY cannot be modified");
        var k = (net.minecraft.core.component.DataComponents.Type<Object, Object>) type(t);
        T old = (T) get((net.minecraft.core.component.DataComponentType<Object>) t);
        var v = componentView();
        var next = value == null ? v.without(k.furnace()) : v.with(k.furnace(), k.toFurnace(value));
        this.components = next.components();
        this.removed = next.removedComponents();
        changed();
        return old;
    }

    /** 지우고 이전 값을 돌려준다. */
    public <T> T remove(net.minecraft.core.component.DataComponentType<? extends T> t) {
        if (this == EMPTY) throw new IllegalStateException("ItemStack.EMPTY cannot be modified");
        @SuppressWarnings("unchecked") var k = (net.minecraft.core.component.DataComponents.Type<Object, T>) type(t);
        T old = get(t);
        var next = componentView().without(k.furnace());
        this.components = next.components();
        this.removed = next.removedComponents();
        changed();
        return old;
    }

    /** 바닐라 getHoverName: 사용자 이름이 있으면 그것, 아니면 아이템 이름(item_name). */
    public net.minecraft.network.chat.Component getHoverName() {
        var custom = get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        if (custom != null) return custom;
        var name = get(net.minecraft.core.component.DataComponents.ITEM_NAME);
        if (name != null) return name;
        return net.minecraft.network.chat.Component.translatable(getDescriptionId());
    }

    /** 바닐라 getDescriptionId: getItem().getDescriptionId(this) — 물약·방패·자석 나침반은 스택에 따라 다르다(비었으면 공기). */
    public String getDescriptionId() { return getItem().getDescriptionId(this); }

    /** 호환 모듈 전용 — 종류 기본값 위에 패치를 얹은 컴포넌트 값(EMPTY 는 null). */
    <T> T furnaceComponent(org.domi.furnace.api.item.ItemComponent<T> component) {
        var v = componentView();
        return v == null ? null : v.get(component);
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

    /**
     * 바닐라 {@code ItemStack#useOn(UseOnContext)}(T-212): 서버가 그 칸의 주인 스레드에서 바닐라 그대로 쓴다(Paper 처럼 블록을 놓으면 블록 놓기
     * 이벤트, 취소하면 놓지 않는다). 지금은 바닐라 생성자로 만든 월드에 없는 플레이어의 문맥만 된다({@link org.domi.furnace.compat.nms.NmsItemUse}).
     */
    public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
        return org.domi.furnace.compat.nms.NmsItemUse.useOn(this, context);
    }

    /** 바닐라와 같다: "개수 종류"(예: 3 minecraft:stone). */
    @Override
    public String toString() { return getCount() + " " + getItem(); }
}
