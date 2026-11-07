package org.bukkit.craftbukkit.inventory;

import java.util.Objects;
import net.minecraft.world.item.Item;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.domi.furnace.compat.paper.CompatItems;
import org.domi.furnace.compat.paper.ItemSlot;

/**
 * NMS 아이템 스택을 비추는 Bukkit 스택(CraftBukkit 이름, H6). 거울은 칸 번호가 아니라 <b>NMS 스택 객체</b>에 묶인다 —
 * {@link #asCraftMirror} 로 만든 것을 바꾸면 그 NMS 스택이 바뀌고, NMS 스택을 바꾸면 거울에서 보인다. 개수를 0 으로 하거나 종류를 공기로 하면
 * CraftBukkit 처럼 NMS 스택에서 떨어진다(그 뒤는 빈 스택).
 * 정적 다리 {@link #asNMSCopy}·{@link #asBukkitCopy}·{@link #asCraftMirror}·{@link #asCraftCopy}·{@link #asNewCraftStack} 는 CraftBukkit 과 같은 규칙이다.
 * 라이브러리가 리플렉션으로 읽고 쓰는 필드 {@code handle} 도 같은 이름·타입이다.
 */
public final class CraftItemStack extends ItemStack {

    /** CraftBukkit 과 같은 이름의 필드(리플렉션으로 읽고 쓰는 라이브러리가 있다). 비었으면 null — ItemStack.EMPTY 를 들지 않는다. */
    net.minecraft.world.item.ItemStack handle;

    private CraftItemStack(Mirror slot, net.minecraft.world.item.ItemStack handle) {
        super(slot);
        slot.owner = this;
        this.handle = handle == null || handle == net.minecraft.world.item.ItemStack.EMPTY ? null : handle;
    }

    private CraftItemStack(net.minecraft.world.item.ItemStack handle) { this(new Mirror(), handle); }

    /** 지금 handle — 리플렉션으로 ItemStack.EMPTY 를 넣었어도 빈 거울(null)로 본다(EMPTY 는 바꿀 수 없다). */
    private net.minecraft.world.item.ItemStack live() {
        var h = handle;
        return h == net.minecraft.world.item.ItemStack.EMPTY ? null : h;
    }

    /** 거울 칸: 읽을 때마다 지금 handle 필드를 본다(리플렉션으로 바꿔 끼워도 따라간다). */
    private static final class Mirror implements ItemSlot {
        CraftItemStack owner;

        @Override
        public org.domi.furnace.api.item.ItemStack get() {
            var h = owner.live();
            return h == null ? org.domi.furnace.api.item.ItemStack.EMPTY : h.furnaceValue();
        }

        @Override
        public void set(org.domi.furnace.api.item.ItemStack value) {
            if (value.type().equals("minecraft:air")) {
                owner.handle = null;
                return;
            }
            var h = owner.live();
            if (h == null) owner.handle = net.minecraft.world.item.ItemStack.fromFurnace(value, value.amount());
            else h.furnaceSet(value);
        }

        @Override
        public int amount() {
            var h = owner.live();
            return h == null ? 0 : h.getCount();
        }

        /** CraftBukkit: 빈 거울은 그대로, 아니면 NMS 개수를 바꾸고 0 이면 떨어진다. */
        @Override
        public void setAmount(int n) {
            var h = owner.live();
            if (h == null) return;
            h.setCount(n);
            if (n == 0) owner.handle = null;
        }
    }

    // ------------------------------------------------------------------ 정적 다리

    /** Bukkit → 새 NMS 사본. null·공기는 EMPTY. CraftItemStack 이면 그 NMS 스택의 사본. */
    public static net.minecraft.world.item.ItemStack asNMSCopy(ItemStack original) {
        if (original instanceof CraftItemStack c) {
            var h = c.live();
            return h == null ? net.minecraft.world.item.ItemStack.EMPTY : h.copy();
        }
        if (original == null) return net.minecraft.world.item.ItemStack.EMPTY;
        var value = original.asFurnace();
        if (value.type().equals("minecraft:air")) return net.minecraft.world.item.ItemStack.EMPTY;
        return net.minecraft.world.item.ItemStack.fromFurnace(value, original.getAmount());
    }

    /** NMS → 새 평범한 Bukkit 사본. 빈 스택이면 공기 스택(null 이 아니다). */
    public static ItemStack asBukkitCopy(net.minecraft.world.item.ItemStack original) {
        if (original == null || original.isEmpty()) return new ItemStack(Material.AIR);
        return CompatItems.copyOf(original.furnaceValue(), original.getCount());
    }

    /** NMS 스택을 비추는 거울. null·빈 스택이면 빈 거울. */
    public static CraftItemStack asCraftMirror(net.minecraft.world.item.ItemStack original) {
        return new CraftItemStack(original == null || original.isEmpty() ? null : original);
    }

    /** Bukkit → 자기 NMS 사본을 든 새 CraftItemStack. */
    public static CraftItemStack asCraftCopy(ItemStack original) {
        if (original instanceof CraftItemStack c) return c.clone();
        Objects.requireNonNull(original, "original");
        return new CraftItemStack(nonEmpty(asNMSCopy(original)));
    }

    public static CraftItemStack asNewCraftStack(Item item) { return asNewCraftStack(item, 1); }

    /** 종류와 개수로 새 CraftItemStack(공기이거나 개수 0 이면 빈 스택). */
    public static CraftItemStack asNewCraftStack(Item item, int amount) {
        Objects.requireNonNull(item, "item");
        if (item.furnaceKey().equals("minecraft:air") || amount == 0) return new CraftItemStack(null);
        return new CraftItemStack(new net.minecraft.world.item.ItemStack(item, amount));
    }

    private static net.minecraft.world.item.ItemStack nonEmpty(net.minecraft.world.item.ItemStack s) { return s.isEmpty() ? null : s; }

    // ------------------------------------------------------------------ 인스턴스

    /**
     * CraftBukkit 처럼: 지금 종류(빈 스택은 공기)와 같으면 그대로, 공기·아이템 아닌 종류면 떨어지고, 빈 거울에 종류를 주면 개수 1 인 새 NMS 스택,
     * 붙어 있으면 같은 NMS 스택에서 종류만 바꾼다(개수·컴포넌트 패치 유지 — 개수 0 으로 붙어 있어도 떨어지지 않는다).
     */
    @Override
    public void setType(Material type) {
        Objects.requireNonNull(type, "Material cannot be null");
        var h = live();
        String key = type.getKey().toString();
        if (h != null ? h.getItem().furnaceKey().equals(key) : type == Material.AIR) return;
        if (type == Material.AIR || !type.isItem()) {
            handle = null;
            return;
        }
        if (h == null) {
            handle = new net.minecraft.world.item.ItemStack(Item.byKey(key), 1);
            return;
        }
        h.furnaceSetItem(Item.byKey(key));
    }

    /** CraftBukkit 처럼 CraftItemStack 사본(NMS 스택도 따로). */
    @Override
    public CraftItemStack clone() {
        var h = live();
        return new CraftItemStack(h == null ? null : nonEmpty(h.copy()));
    }
}
