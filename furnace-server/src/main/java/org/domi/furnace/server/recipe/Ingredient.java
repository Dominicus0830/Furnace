// 레시피 재료(바닐라 Ingredient) — 아이템/태그 값들의 목록. 네트워크로는 펼친 아이템 목록(중복 유지, 값 순서·태그 순서).
package org.domi.furnace.server.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.data.ResourceId;

public record Ingredient(List<Value> values) {

    public static final Ingredient EMPTY = new Ingredient(List.of());

    /** item 이면 아이템 id, 아니면 태그 id. */
    public record Value(boolean tag, ResourceId id) {}

    public boolean isEmpty() { return values.isEmpty(); }

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
