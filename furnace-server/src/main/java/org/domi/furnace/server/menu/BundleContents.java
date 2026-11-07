// 바닐라 BundleContents(+Mutable): 가방(bundle) 안 아이템 목록과 무게(1 = 64칸). 가장 최근 것이 앞(0번)이다.
// 무게는 정확한 분수로 센다 — 아이템마다 1/최대 개수, 가방은 1/16 + 안의 무게, 벌이 든 아이템은 1.
package org.domi.furnace.server.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemStackCodecs;
import org.domi.furnace.server.item.ItemTools;

public final class BundleContents {

    public static final String KEY = "minecraft:bundle_contents";

    private final List<ItemStack> items = new ArrayList<>();
    private long num, den = 1;   // 무게 num/den (0..1)

    private BundleContents() { }

    /** 그 가방 스택의 내용(컴포넌트가 없으면 null — 가방이 아니다). */
    @SuppressWarnings("unchecked")
    public static BundleContents of(ItemStack bundle) {
        if (!(ItemTools.component(bundle, KEY) instanceof List<?> raw)) return null;
        BundleContents c = new BundleContents();
        for (Object o : raw) {
            if (!(o instanceof Map<?, ?> m)) continue;
            ItemStack s = ItemStackCodecs.fromCanonical((Map<String, Object>) m);
            c.items.add(s);
            c.addWeight(weight(s), s.count());
        }
        return c;
    }

    /** 가방 스택에 이 내용을 담은 새 스택(비어도 빈 목록을 남긴다 — 컴포넌트 기본값이 빈 내용이다). */
    public ItemStack applyTo(ItemStack bundle) {
        List<Object> raw = new ArrayList<>(items.size());
        for (ItemStack s : items) raw.add(ItemStackCodecs.toCanonical(s));
        return ItemTools.with(bundle, KEY, raw);
    }

    public List<ItemStack> items() { return List.copyOf(items); }

    public boolean isEmpty() { return items.isEmpty(); }

    /** 무게 분자/분모. */
    public long[] weight() { return new long[] {num, den}; }

    // ------------------------------------------------------------------ 분수

    private static long gcd(long a, long b) { return b == 0 ? Math.abs(a) : gcd(b, a % b); }

    private void set(long n, long d) {
        long g = gcd(n, d);
        if (g > 1) {
            n /= g;
            d /= g;
        }
        num = n;
        den = d;
    }

    /** 무게 += w × count (w = {분자, 분모}). */
    private void addWeight(long[] w, int count) {
        set(num * w[1] + w[0] * count * den, den * w[1]);
    }

    /** getWeight(stack): 가방이면 1/16 + 안의 무게, 벌이 들었으면 1, 아니면 1/최대 개수. */
    static long[] weight(ItemStack s) {
        BundleContents inner = of(s);
        if (inner != null) {
            long[] w = inner.weight();
            return new long[] {w[0] * 16 + w[1], w[1] * 16};   // 1/16 + w
        }
        if (ItemTools.component(s, "minecraft:bees") instanceof List<?> bees && !bees.isEmpty()) return new long[] {1, 1};
        return new long[] {1, Math.max(1, Stacks.maxStack(s))};
    }

    /** Item.canFitInsideContainerItems — 셜커 상자만 못 들어간다. */
    public static boolean canFitInside(ItemStack s) { return !ItemTools.itemName(s).endsWith("shulker_box"); }

    /** getMaxAmountToAdd: 남은 무게 ÷ 한 개 무게의 몫(0 아래로는 안 간다). */
    public int maxAmountToAdd(ItemStack s) {
        long[] w = weight(s);
        long remainN = den - num;   // (1 - num/den) = (den - num)/den
        long n = remainN * w[1], d = den * w[0];   // (remainN/den) ÷ (w0/w1)
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, n / d));
    }

    private int findStackable(ItemStack s) {
        if (!Stacks.isStackable(s)) return -1;
        for (int i = 0; i < items.size(); i++) if (Stacks.isSameItemSameComponents(items.get(i), s)) return i;
        return -1;
    }

    /** tryInsert: 담을 수 있는 만큼(무게·최대) 앞에 넣고 담은 개수를 돌려준다. 담을 수 없으면 0. */
    public int tryInsert(ItemStack s) {
        if (s.isEmpty() || !canFitInside(s)) return 0;
        int i = Math.min(s.count(), maxAmountToAdd(s));
        if (i == 0) return 0;
        addWeight(weight(s), i);
        int idx = findStackable(s);
        if (idx != -1) {
            ItemStack old = items.remove(idx);
            items.add(0, old.withCount(old.count() + i));
        } else {
            items.add(0, s.withCount(i));
        }
        return i;
    }

    /** tryTransfer(slot, player): 칸의 스택에서 담을 수 있는 만큼 안전하게 꺼내(onTake 포함) 담는다. 담은 개수. */
    public int tryTransfer(Slot slot, MenuPlayer player) {
        ItemStack s = slot.getItem();
        int max = maxAmountToAdd(s);
        return tryInsert(slot.safeTake(s.count(), max, player));
    }

    /** removeOne: 맨 앞 스택을 통째로 꺼낸다(없으면 null). */
    public ItemStack removeOne() {
        if (items.isEmpty()) return null;
        ItemStack s = items.remove(0);
        long[] w = weight(s);
        set(num * w[1] - w[0] * s.count() * den, den * w[1]);
        return s;
    }
}
