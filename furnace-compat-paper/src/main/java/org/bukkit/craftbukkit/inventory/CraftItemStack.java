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

    /** 거울 칸: 읽을 때마다 지금 handle 필드를 본다(리플렉션으로 바꿔 끼워도 따라간다). */
    private static final class Mirror implements ItemSlot {
        CraftItemStack owner;

        @Override
        public org.domi.furnace.api.item.ItemStack get() {
            var h = owner.handle;
            return h == null ? org.domi.furnace.api.item.ItemStack.EMPTY : h.furnaceValue();
        }

        @Override
        public void set(org.domi.furnace.api.item.ItemStack value) {
            if (value.type().equals("minecraft:air")) {
                owner.handle = null;
                return;
            }
            var h = owner.handle;
            if (h == null) owner.handle = net.minecraft.world.item.ItemStack.fromFurnace(value, value.amount());
            else h.furnaceSet(value);
        }

        @Override
        public int amount() {
            var h = owner.handle;
            return h == null ? 0 : h.getCount();
        }

        /** CraftBukkit: 빈 거울은 그대로, 아니면 NMS 개수를 바꾸고 0 이면 떨어진다. */
        @Override
        public void setAmount(int n) {
            var h = owner.handle;
            if (h == null) return;
            h.setCount(n);
            if (n == 0) owner.handle = null;
        }
    }

    // ------------------------------------------------------------------ 정적 다리

    /** Bukkit → 새 NMS 사본. null·공기는 EMPTY. CraftItemStack 이면 그 NMS 스택의 사본. */
    public static net.minecraft.world.item.ItemStack asNMSCopy(ItemStack original) {
        if (original instanceof CraftItemStack c) return c.handle == null ? net.minecraft.world.item.ItemStack.EMPTY : c.handle.copy();
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
        if (original instanceof CraftItemStack c) return new CraftItemStack(c.handle == null ? null : nonEmpty(c.handle.copy()));
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

    /** CraftBukkit 처럼: 같은 종류면 그대로, 공기면 떨어지고, 빈 거울에 종류를 주면 개수 1 인 새 NMS 스택. */
    @Override
    public void setType(Material type) {
        Objects.requireNonNull(type, "Material cannot be null");
        if (handle != null && handle.getItem().furnaceKey().equals(type.getKey().toString())) return;
        if (type == Material.AIR) {
            handle = null;
            return;
        }
        if (handle == null) {
            handle = new net.minecraft.world.item.ItemStack(Item.byKey(type.getKey().toString()), 1);
            return;
        }
        super.setType(type);
    }

    /** CraftBukkit 처럼 CraftItemStack 사본(NMS 스택도 따로). */
    @Override
    public CraftItemStack clone() { return new CraftItemStack(handle == null ? null : nonEmpty(handle.copy())); }
}
