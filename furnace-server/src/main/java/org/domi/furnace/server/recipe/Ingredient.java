// 레시피 재료(바닐라 Ingredient) — 아이템/태그 값들의 목록. 네트워크로는 펼친 아이템 목록(중복 유지, 값 순서·태그 순서).
package org.domi.furnace.server.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.entity.ItemStack;

public final class Ingredient {

    public static final Ingredient EMPTY = new Ingredient(List.of());

    /** item 이면 아이템 id, 아니면 태그 id. */
    public record Value(boolean tag, ResourceId id) {}

    private final List<Value> values;
    // 매칭용으로 펼쳐 정렬한 아이템 id(바닐라 Ingredient.getStackingIds). 레시피 매니저가 다시 만들어질 때 재료도 새로 만들어지므로 한 번만 푼다.
    private volatile int[] sortedIds;
    private volatile RecipeContext sortedFor;

    public Ingredient(List<Value> values) { this.values = values; }

    public List<Value> values() { return values; }

    public boolean isEmpty() { return values.isEmpty(); }

    @Override public boolean equals(Object o) { return o instanceof Ingredient i && values.equals(i.values); }

    @Override public int hashCode() { return values.hashCode(); }

    /**
     * 바닐라 Ingredient.test: 빈 재료는 빈 스택만, 아니면 값(아이템·태그)에 든 아이템이면 된다(개수·컴포넌트 무시).
     */
    public boolean test(ItemStack stack, RecipeContext ctx) {
        if (stack == null) return false;
        if (values.isEmpty()) return stack.isEmpty();
        if (stack.isEmpty()) return false;
        return Arrays.binarySearch(sorted(ctx), stack.itemId()) >= 0;
    }

    private int[] sorted(RecipeContext ctx) {
        int[] s = sortedIds;
        if (s != null && sortedFor == ctx) return s;
        s = itemIds(ctx).clone();
        Arrays.sort(s);
        sortedIds = s;
        sortedFor = ctx;
        return s;
    }

    /** JSON: {"item": …} · {"tag": …} · 그 배열. allowEmpty 가 아니면 빈 배열은 오류. */
    static Ingredient parse(JsonElement e, RecipeContext ctx, boolean allowEmpty) {
        List<Value> vs = new ArrayList<>();
        if (e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            if (a.isEmpty() && !allowEmpty) throw new IllegalArgumentException("Item array cannot be empty, at least one item must be defined");
            for (JsonElement x : a) vs.add(value(x.getAsJsonObject(), ctx));
        } else {
            vs.add(value(e.getAsJsonObject(), ctx));
        }
        return new Ingredient(List.copyOf(vs));
    }

    private static Value value(JsonObject o, RecipeContext ctx) {
        boolean hasItem = o.has("item"), hasTag = o.has("tag");
        if (hasItem == hasTag) throw new IllegalArgumentException("Ingredient needs exactly one of item or tag: " + o);
        if (hasItem) {
            ResourceId id = ResourceId.parse(o.get("item").getAsString());
            if (ctx.itemId(id) == null || id.equals(ResourceId.parse("minecraft:air"))) throw new IllegalArgumentException("Unknown item: " + id);
            return new Value(false, id);
        }
        return new Value(true, ResourceId.parse(o.get("tag").getAsString()));
    }

    /** 펼친 아이템 숫자 id(바닐라 Ingredient.getItems — distinct 는 ItemStack 동일성이라 중복은 남는다). */
    public int[] itemIds(RecipeContext ctx) {
        List<Integer> out = new ArrayList<>();
        for (Value v : values) {
            if (v.tag()) {
                int[] t = ctx.itemTag(v.id());
                if (t != null) for (int x : t) out.add(x);
            } else {
                out.add(ctx.itemId(v.id()));
            }
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }
}
