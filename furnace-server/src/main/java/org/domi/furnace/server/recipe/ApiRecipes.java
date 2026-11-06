// 플러그인 API 레시피 ↔ 서버 레시피 변환. 등록 때는 데이터 팩 파서와 같은 검사(모양·재료·결과 수량)를 거쳐 틀린 레시피가 표에 들어가지 않게 한다.
package org.domi.furnace.server.recipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.domi.furnace.api.recipe.RecipeCategory;
import org.domi.furnace.api.recipe.RecipeChoice;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.entity.ItemStack;

public final class ApiRecipes {

    private ApiRecipes() {}

    /** 아이템 변환기: API 스택 → 서버 스택(알 수 없는 아이템이면 IllegalArgumentException). */
    public interface ToServer extends Function<org.domi.furnace.api.item.ItemStack, ItemStack> {}

    /** 서버 스택 → API 스택. */
    public interface ToApi extends Function<ItemStack, org.domi.furnace.api.item.ItemStack> {}

    // ------------------------------------------------------------------ API → 서버

    public static Recipe toServer(org.domi.furnace.api.recipe.Recipe r, RecipeContext ctx, ToServer items) {
        return switch (r) {
            case org.domi.furnace.api.recipe.Recipe.Shaped s -> shaped(s, ctx, items);
            case org.domi.furnace.api.recipe.Recipe.Shapeless s -> {
                if (s.ingredients().isEmpty()) throw new IllegalArgumentException("No ingredients for shapeless recipe");
                if (s.ingredients().size() > 9) throw new IllegalArgumentException("Too many ingredients for shapeless recipe");
                List<Ingredient> ings = new ArrayList<>();
                for (RecipeChoice c : s.ingredients()) ings.add(ingredient(c, ctx, false));
                yield new Recipe.Shapeless(s.group(), craftingCategory(s.category()), result(s.result(), ctx, items), List.copyOf(ings));
            }
            case org.domi.furnace.api.recipe.Recipe.Cooking c -> new Recipe.Cooking(c.kind().type(), c.group(), cookingCategory(c.category()),
                    ingredient(c.ingredient(), ctx, false), result(c.result(), ctx, items), c.experience(), c.cookingTime());
            case org.domi.furnace.api.recipe.Recipe.Stonecutting s -> new Recipe.Stonecutting(s.group(), ingredient(s.ingredient(), ctx, false),
                    result(s.result(), ctx, items));
            case org.domi.furnace.api.recipe.Recipe.SmithingTransform s -> new Recipe.SmithingTransform(ingredient(s.template(), ctx, true),
                    ingredient(s.base(), ctx, true), ingredient(s.addition(), ctx, true), result(s.result(), ctx, items));
            case org.domi.furnace.api.recipe.Recipe.Special sp -> throw new IllegalArgumentException("특수 레시피는 등록할 수 없다: " + sp.type());
        };
    }

    private static Recipe.Shaped shaped(org.domi.furnace.api.recipe.Recipe.Shaped s, RecipeContext ctx, ToServer items) {
        List<String> pattern = s.pattern();
        if (pattern.isEmpty() || pattern.size() > 3) throw new IllegalArgumentException("Invalid pattern: 1..3 rows required");
        int w0 = pattern.get(0).length();
        for (String p : pattern) {
            if (p.length() > 3 || p.isEmpty()) throw new IllegalArgumentException("Invalid pattern: 1..3 columns required");
            if (p.length() != w0) throw new IllegalArgumentException("Invalid pattern: each row must be the same width");
        }
        Map<Character, Ingredient> key = new HashMap<>();
        for (var e : s.ingredients().entrySet()) {
            if (e.getKey() == ' ') throw new IllegalArgumentException("Invalid key entry: ' ' is a reserved symbol.");
            key.put(e.getKey(), ingredient(e.getValue(), ctx, false));
        }
        String[] rows = Recipe.Shaped.shrink(pattern);
        if (rows.length == 0) throw new IllegalArgumentException("Invalid pattern: pattern is empty");
        int width = rows[0].length(), height = rows.length;
        List<Ingredient> ings = new ArrayList<>(width * height);
        for (int i = 0; i < width * height; i++) ings.add(Ingredient.EMPTY);
        Set<Character> unused = new HashSet<>(key.keySet());
        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < rows[r].length(); c++) {
                char ch = rows[r].charAt(c);
                Ingredient ing = ch == ' ' ? Ingredient.EMPTY : key.get(ch);
                if (ing == null) throw new IllegalArgumentException("Pattern references symbol '" + ch + "' but it's not defined in the key");
                unused.remove(ch);
                ings.set(c + width * r, ing);
            }
        }
        if (!unused.isEmpty()) throw new IllegalArgumentException("Key defines symbols that aren't used in pattern: " + unused);
        return new Recipe.Shaped(s.group(), craftingCategory(s.category()), width, height, List.copyOf(ings), result(s.result(), ctx, items),
                s.showNotification());
    }

    private static Recipe.CraftingCategory craftingCategory(RecipeCategory c) {
        return switch (c) {
            case BUILDING -> Recipe.CraftingCategory.BUILDING;
            case REDSTONE -> Recipe.CraftingCategory.REDSTONE;
            case EQUIPMENT -> Recipe.CraftingCategory.EQUIPMENT;
            case MISC -> Recipe.CraftingCategory.MISC;
            default -> throw new IllegalArgumentException("제작대 레시피의 갈래가 아니다: " + c);
        };
    }

    private static Recipe.CookingCategory cookingCategory(RecipeCategory c) {
        return switch (c) {
            case FOOD -> Recipe.CookingCategory.FOOD;
            case BLOCKS -> Recipe.CookingCategory.BLOCKS;
            case MISC -> Recipe.CookingCategory.MISC;
            default -> throw new IllegalArgumentException("굽기 레시피의 갈래가 아니다: " + c);
        };
    }

    /** 아이템 존재·태그 존재를 확인한다(알 수 없으면 IllegalArgumentException). allowEmpty 가 아니면 빈 재료는 오류. */
    static Ingredient ingredient(RecipeChoice c, RecipeContext ctx, boolean allowEmpty) {
        List<Ingredient.Value> vs = new ArrayList<>();
        collect(c, ctx, vs);
        if (vs.isEmpty() && !allowEmpty) throw new IllegalArgumentException("Item array cannot be empty, at least one item must be defined");
        return vs.isEmpty() ? Ingredient.EMPTY : new Ingredient(List.copyOf(vs));
    }

    private static void collect(RecipeChoice c, RecipeContext ctx, List<Ingredient.Value> out) {
        switch (c) {
            case RecipeChoice.Items it -> {
                for (String name : it.items()) {
                    ResourceId id = ResourceId.parse(name);
                    if (ctx.itemId(id) == null || id.equals(ResourceId.parse("minecraft:air"))) throw new IllegalArgumentException("Unknown item: " + id);
                    out.add(new Ingredient.Value(false, id));
                }
            }
            case RecipeChoice.Tag t -> {
                ResourceId id = ResourceId.parse(t.tag());
                if (ctx.itemTag(id) == null) throw new IllegalArgumentException("Unknown item tag: " + id);
                out.add(new Ingredient.Value(true, id));
            }
            case RecipeChoice.Any a -> {
                for (RecipeChoice x : a.choices()) collect(x, ctx, out);
            }
        }
    }

    private static Recipe.Result result(org.domi.furnace.api.item.ItemStack stack, RecipeContext ctx, ToServer items) {
        ItemStack s = items.apply(stack);   // 알 수 없는 아이템·컴포넌트면 IllegalArgumentException
        ResourceId id = ResourceId.parse(stack.type());
        if (ctx.itemId(id) == null || id.equals(ResourceId.parse("minecraft:air"))) throw new IllegalArgumentException("Unknown item: " + id);
        if (stack.amount() < 1 || stack.amount() > 99) throw new IllegalArgumentException("Value must be within range [1;99]: " + stack.amount());
        return new Recipe.Result(id, stack.amount(), s.patch().normalizedFor(id.toString()));
    }

    // ------------------------------------------------------------------ 서버 → API

    public static org.domi.furnace.api.recipe.Recipe toApi(Recipe r, RecipeContext ctx, ToApi items) {
        return switch (r) {
            case Recipe.Shaped s -> {
                // 서로 다른 재료마다 글자를 하나씩(A, B, …) — 빈 칸은 공백
                Map<Ingredient, Character> letters = new LinkedHashMap<>();
                Map<Character, RecipeChoice> key = new LinkedHashMap<>();
                StringBuilder[] rows = new StringBuilder[s.height()];
                for (int y = 0; y < s.height(); y++) {
                    rows[y] = new StringBuilder();
                    for (int x = 0; x < s.width(); x++) {
                        Ingredient ing = s.ingredients().get(x + y * s.width());
                        if (ing.isEmpty()) {
                            rows[y].append(' ');
                            continue;
                        }
                        Character ch = letters.get(ing);
                        if (ch == null) {
                            ch = (char) ('A' + letters.size());
                            letters.put(ing, ch);
                            key.put(ch, choice(ing));
                        }
                        rows[y].append(ch);
                    }
                }
                List<String> pattern = new ArrayList<>();
                for (StringBuilder b : rows) pattern.add(b.toString());
                yield new org.domi.furnace.api.recipe.Recipe.Shaped(s.group(), category(s.category()), pattern, key, result(s.result(), ctx, items),
                        s.showNotification());
            }
            case Recipe.Shapeless s -> {
                List<RecipeChoice> ings = new ArrayList<>();
                for (Ingredient i : s.ingredients()) ings.add(choice(i));
                yield new org.domi.furnace.api.recipe.Recipe.Shapeless(s.group(), category(s.category()), ings, result(s.result(), ctx, items));
            }
            case Recipe.Cooking c -> {
                var kind = switch (c.type()) {
                    case "minecraft:smelting" -> org.domi.furnace.api.recipe.Recipe.CookingKind.SMELTING;
                    case "minecraft:blasting" -> org.domi.furnace.api.recipe.Recipe.CookingKind.BLASTING;
                    case "minecraft:smoking" -> org.domi.furnace.api.recipe.Recipe.CookingKind.SMOKING;
                    default -> org.domi.furnace.api.recipe.Recipe.CookingKind.CAMPFIRE;
                };
                yield new org.domi.furnace.api.recipe.Recipe.Cooking(kind, c.group(), category(c.category()), choice(c.ingredient()),
                        result(c.result(), ctx, items), c.experience(), c.cookingTime());
            }
            case Recipe.Stonecutting s -> new org.domi.furnace.api.recipe.Recipe.Stonecutting(s.group(), choice(s.ingredient()), result(s.result(), ctx, items));
            case Recipe.SmithingTransform s -> new org.domi.furnace.api.recipe.Recipe.SmithingTransform(choice(s.template()), choice(s.base()),
                    choice(s.addition()), result(s.result(), ctx, items));
            case Recipe.Smithing t -> new org.domi.furnace.api.recipe.Recipe.Special(t.type());
            case Recipe.Special sp -> new org.domi.furnace.api.recipe.Recipe.Special(sp.type());
        };
    }

    private static RecipeCategory category(Recipe.CraftingCategory c) { return RecipeCategory.valueOf(c.name()); }

    private static RecipeCategory category(Recipe.CookingCategory c) { return RecipeCategory.valueOf(c.name()); }

    private static org.domi.furnace.api.item.ItemStack result(Recipe.Result r, RecipeContext ctx, ToApi items) {
        return items.apply(r.stack(ctx));
    }

    static RecipeChoice choice(Ingredient ing) {
        if (ing.isEmpty()) return RecipeChoice.NONE;
        List<String> itemNames = new ArrayList<>();
        List<RecipeChoice> parts = new ArrayList<>();
        // 아이템 값은 한 묶음으로, 태그는 하나씩 — 순서는 잃지만 일치 판정은 같다
        for (Ingredient.Value v : ing.values()) {
            if (v.tag()) parts.add(new RecipeChoice.Tag(v.id().toString()));
            else itemNames.add(v.id().toString());
        }
        if (!itemNames.isEmpty()) parts.add(0, new RecipeChoice.Items(itemNames));
        return parts.size() == 1 ? parts.get(0) : new RecipeChoice.Any(parts);
    }
}
