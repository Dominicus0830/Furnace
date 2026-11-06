// 바닐라 StackedContents: 인벤토리의 아이템을 종류별 개수로 모아 두고, 레시피 재료를 서로 겹치지 않게 배정할 수 있는지(최대 유량)와
// 몇 번 만들 수 있는지를 구한다. 배정 순서가 결과(어느 칸에 어느 아이템이 들어가는지)를 정하므로 바닐라 알고리즘을 그대로 옮겼다.
package org.domi.furnace.server.recipe;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemStackCodecs;
import org.domi.furnace.server.item.ItemTools;

public final class StackedContents {

    /** 아이템 숫자 id → 개수. */
    public final Map<Integer, Integer> contents = new HashMap<>();

    private int count(int id) { return contents.getOrDefault(id, 0); }

    /** accountSimpleStack: 손상·마법·이름이 없는 스택만 센다. */
    public void accountSimpleStack(ItemStack s) {
        if (s == null || s.isEmpty()) return;
        boolean damaged = ItemTools.isDamageable(s) && ItemTools.damage(s) > 0;
        boolean enchanted = !org.domi.furnace.server.item.EnchantTools.levels(s, org.domi.furnace.server.item.EnchantTools.ENCHANTMENTS).isEmpty();
        boolean named = ItemTools.component(s, "minecraft:custom_name") != null;
        if (!damaged && !enchanted && !named) accountStack(s, ItemStackCodecs.maxStackSize(s));
    }

    public void accountStack(ItemStack s, int maxStack) {
        if (s.isEmpty()) return;
        add(s.itemId(), Math.min(maxStack, s.count()));
    }

    void add(int id, int amount) { contents.merge(id, amount, Integer::sum); }

    boolean has(int id) { return count(id) > 0; }

    /** 개수가 있으면 amount 만큼 빼고 id, 모자라면 0. */
    int take(int id, int amount) {
        int have = count(id);
        if (have >= amount) {
            contents.put(id, have - amount);
            return id;
        }
        return 0;
    }

    public void clear() { contents.clear(); }

    public static ItemStack fromStackingIndex(int id) { return id == 0 ? ItemStack.EMPTY : new ItemStack(id, 1); }

    public boolean canCraft(List<Ingredient> ingredients, RecipeContext ctx, List<Integer> out) { return canCraft(ingredients, ctx, out, 1); }

    public boolean canCraft(List<Ingredient> ingredients, RecipeContext ctx, List<Integer> out, int amount) {
        return new Picker(ingredients, ctx).tryPick(amount, out);
    }

    public int getBiggestCraftableStack(List<Ingredient> ingredients, RecipeContext ctx, int max, List<Integer> out) {
        return new Picker(ingredients, ctx).biggest(max, out);
    }

    private final class Picker {
        private final List<Ingredient> all;
        private final List<int[]> ids = new ArrayList<>();   // 비어 있지 않은 재료의 (정렬된) 아이템 id
        private final int d;          // 비어 있지 않은 재료 수
        private final int[] items;    // 쓸 수 있는 아이템 id(정렬)
        private final int f;
        private final BitSet g;
        private final List<Integer> path = new ArrayList<>();

        Picker(List<Ingredient> ingredients, RecipeContext ctx) {
            this.all = ingredients;
            List<Ingredient> nonEmpty = new ArrayList<>();
            for (Ingredient i : ingredients) if (!i.isEmpty()) nonEmpty.add(i);
            for (Ingredient i : nonEmpty) {
                int[] a = i.itemIds(ctx).clone();
                java.util.Arrays.sort(a);
                ids.add(a);
            }
            this.d = nonEmpty.size();
            TreeSet<Integer> set = new TreeSet<>();
            for (int[] a : ids) for (int v : a) set.add(v);
            set.removeIf(v -> !has(v));
            this.items = set.stream().mapToInt(Integer::intValue).toArray();
            this.f = items.length;
            this.g = new BitSet(d + f + d + d * f);
            for (int i = 0; i < d; i++) {
                for (int j = 0; j < f; j++) {
                    if (contains(ids.get(i), items[j])) g.set(edge(true, j, i));
                }
            }
        }

        private boolean contains(int[] a, int v) { for (int x : a) if (x == v) return true; return false; }

        boolean tryPick(int amount, List<Integer> out) {
            if (amount <= 0) return true;
            int matched = 0;
            while (findPath(amount)) {
                take(items[path.get(0)], amount);
                int last = path.size() - 1;
                setMatched(path.get(last));
                for (int i = 0; i < last; i++) flipEdge((i & 1) == 0, path.get(i), path.get(i + 1));
                path.clear();
                g.clear(0, d + f);
                matched++;
            }
            boolean success = matched == d;
            boolean fill = success && out != null;
            if (fill) out.clear();
            g.clear(0, d + f + d);
            int ingredientIdx = 0;
            for (Ingredient ing : all) {
                if (fill && ing.isEmpty()) {
                    out.add(0);
                } else if (!ing.isEmpty()) {
                    for (int j = 0; j < f; j++) {
                        if (flow(false, ingredientIdx, j)) {
                            flipEdge(true, j, ingredientIdx);
                            add(items[j], amount);
                            if (fill) out.add(items[j]);
                        }
                    }
                    ingredientIdx++;
                }
            }
            return success;
        }

        private boolean findPath(int amount) {
            for (int i = 0; i < f; i++) {
                if (count(items[i]) >= amount) {
                    visit(false, i);
                    while (!path.isEmpty()) {
                        int size = path.size();
                        boolean odd = (size & 1) == 1;
                        int last = path.get(size - 1);
                        if (!odd && !isMatched(last)) break;
                        int limit = odd ? d : f;
                        int k = 0;
                        while (true) {
                            if (k < limit) {
                                if (visited(odd, k) || !possible(odd, last, k) || !flowFree(odd, last, k)) {
                                    k++;
                                    continue;
                                }
                                visit(odd, k);
                            }
                            k = path.size();
                            if (k == size) path.remove(k - 1);
                            break;
                        }
                    }
                    if (!path.isEmpty()) return true;
                }
            }
            return false;
        }

        private boolean isMatched(int i) { return g.get(d + f + i); }

        private void setMatched(int i) { g.set(d + f + i); }

        private int edge(boolean forward, int a, int b) {
            int idx = forward ? a * d + b : b * d + a;
            return d + f + d + 2 * idx;
        }

        private boolean possible(boolean forward, int a, int b) { return g.get(edge(forward, a, b)); }

        private boolean flowFree(boolean forward, int a, int b) { return forward != g.get(1 + edge(forward, a, b)); }

        private boolean flow(boolean forward, int a, int b) { return forward != g.get(1 + edge(forward, a, b)); }

        private void flipEdge(boolean forward, int a, int b) { g.flip(1 + edge(forward, a, b)); }

        private void visit(boolean forward, int i) {
            g.set((forward ? 0 : d) + i);
            path.add(i);
        }

        private boolean visited(boolean forward, int i) { return g.get((forward ? 0 : d) + i); }

        private int maxPossible() {
            int result = Integer.MAX_VALUE;
            for (int[] a : ids) {
                int m = 0;
                for (int v : a) m = Math.max(m, count(v));
                if (result > 0) result = Math.min(result, m);
            }
            return result;
        }

        int biggest(int max, List<Integer> out) {
            int lo = 0, hi = Math.min(max, maxPossible()) + 1;
            while (true) {
                int mid = (lo + hi) / 2;
                if (tryPick(mid, null)) {
                    if (hi - lo <= 1) {
                        if (mid > 0) tryPick(mid, out);
                        return mid;
                    }
                    lo = mid;
                } else {
                    hi = mid;
                }
            }
        }
    }
}
