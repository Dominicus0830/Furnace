// 레시피(바닐라 Recipe 의 23개 직렬화기). 네트워크 인코딩은 바닐라 각 Serializer.STREAM_CODEC 순서 그대로.
package org.domi.furnace.server.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.net.io.Types;

public sealed interface Recipe {

    /** 직렬화기 이름(minecraft:crafting_shaped …). */
    String type();

    /** 직렬화기별 본문(바닐라 STREAM_CODEC). */
    void encode(ByteBuf buf, RecipeContext ctx);

    /** 결과 아이템(바닐라 ItemStack.STRICT_CODEC — 수량 1..99, 컴포넌트 패치). */
    record Result(ResourceId item, int count, org.domi.furnace.server.item.ComponentPatch patch) {
        static Result parse(JsonObject o, RecipeContext ctx) {
            ResourceId id = ResourceId.parse(o.get("id").getAsString());
            if (ctx.itemId(id) == null || id.equals(ResourceId.parse("minecraft:air"))) throw new IllegalArgumentException("Unknown item: " + id);
            int count = o.has("count") ? o.get("count").getAsInt() : 1;
            if (count < 1 || count > 99) throw new IllegalArgumentException("Value must be within range [1;99]: " + count);
            var patch = org.domi.furnace.server.item.ComponentPatch.EMPTY;
            if (o.has("components")) {
                try {
                    patch = org.domi.furnace.server.item.ComponentPatch.fromCodec(
                            org.domi.furnace.server.loot.Components.fromJson(o.get("components"))).normalizedFor(id.toString());
                } catch (org.domi.furnace.server.item.CodecException e) {
                    throw new IllegalArgumentException(e.getMessage());
                }
            }
            return new Result(id, count, patch);
        }

        void encode(ByteBuf buf, RecipeContext ctx) {
            Types.writeVarInt(buf, count);
            Types.writeVarInt(buf, ctx.itemId(item));
            patch.write(buf);
        }
    }

    /** ItemStack.STREAM_CODEC(컴포넌트 없음): 수량, 아이템 id, 추가/제거 컴포넌트 수(0, 0). */
    static void writeStack(ByteBuf buf, int itemId, int count) {
        Types.writeVarInt(buf, count);
        Types.writeVarInt(buf, itemId);
        Types.writeVarInt(buf, 0);
        Types.writeVarInt(buf, 0);
    }

    /** Ingredient.CONTENTS_STREAM_CODEC — 펼친 아이템 스택 목록(각 수량 1). */
    static void writeIngredient(ByteBuf buf, Ingredient ing, RecipeContext ctx) {
        int[] ids = ing.itemIds(ctx);
        Types.writeVarInt(buf, ids.length);
        for (int id : ids) writeStack(buf, id, 1);
    }

    enum CraftingCategory { BUILDING, REDSTONE, EQUIPMENT, MISC }

    enum CookingCategory { FOOD, BLOCKS, MISC }

    static <E extends Enum<E>> E readCategory(JsonObject o, Class<E> type, E def) {
        if (!o.has("category")) return def;
        return Enum.valueOf(type, o.get("category").getAsString().toUpperCase(java.util.Locale.ROOT));
    }

    static String readGroup(JsonObject o) { return o.has("group") ? o.get("group").getAsString() : ""; }

    // ------------------------------------------------------------------ 제작대

    record Shaped(String group, CraftingCategory category, int width, int height, List<Ingredient> ingredients, Result result,
                  boolean showNotification) implements Recipe {
        public String type() { return "minecraft:crafting_shaped"; }

        public void encode(ByteBuf buf, RecipeContext ctx) {
            Types.writeString(buf, group);
            Types.writeVarInt(buf, category.ordinal());
            Types.writeVarInt(buf, width);
            Types.writeVarInt(buf, height);
            for (Ingredient i : ingredients) writeIngredient(buf, i, ctx);
            result.encode(buf, ctx);
            buf.writeBoolean(showNotification);
        }

        static Shaped parse(JsonObject o, RecipeContext ctx) {
            Map<Character, Ingredient> key = new HashMap<>();
            for (var e : o.getAsJsonObject("key").entrySet()) {
                if (e.getKey().length() != 1) throw new IllegalArgumentException("Invalid key entry: '" + e.getKey() + "' is an invalid symbol (must be 1 character only).");
                if (e.getKey().equals(" ")) throw new IllegalArgumentException("Invalid key entry: ' ' is a reserved symbol.");
                key.put(e.getKey().charAt(0), Ingredient.parse(e.getValue(), ctx, false));
            }
            List<String> pattern = new ArrayList<>();
            for (JsonElement p : o.getAsJsonArray("pattern")) pattern.add(p.getAsString());
            if (pattern.isEmpty() || pattern.size() > 3) throw new IllegalArgumentException("Invalid pattern: too many rows, 3 is maximum");
            int w0 = pattern.get(0).length();
            for (String p : pattern) {
                if (p.length() > 3) throw new IllegalArgumentException("Invalid pattern: too many columns, 3 is maximum");
                if (p.length() != w0) throw new IllegalArgumentException("Invalid pattern: each row must be the same width");
            }
            String[] rows = shrink(pattern);
            int width = rows.length == 0 ? 0 : rows[0].length(), height = rows.length;
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
            boolean notify = !o.has("show_notification") || o.get("show_notification").getAsBoolean();
            return new Shaped(Recipe.readGroup(o), Recipe.readCategory(o, CraftingCategory.class, CraftingCategory.MISC), width, height, List.copyOf(ings),
                Result.parse(o.getAsJsonObject("result"), ctx), notify);
        }

        /** 바닐라 ShapedRecipePattern.shrink — 앞뒤 빈 행, 양옆 공백 열을 잘라낸다. */
        static String[] shrink(List<String> p) {
            int first = Integer.MAX_VALUE, last = 0, top = 0, bottom = 0;
            for (int i = 0; i < p.size(); i++) {
                String s = p.get(i);
                first = Math.min(first, firstNonSpace(s));
                int l = lastNonSpace(s);
                last = Math.max(last, l);
                if (l < 0) {
                    if (top == i) top++;
                    bottom++;
                } else {
                    bottom = 0;
                }
            }
            if (p.size() == bottom) return new String[0];
            String[] out = new String[p.size() - bottom - top];
            for (int i = 0; i < out.length; i++) out[i] = p.get(i + top).substring(first, last + 1);
            return out;
        }

        private static int firstNonSpace(String s) {
            int i = 0;
            while (i < s.length() && s.charAt(i) == ' ') i++;
            return i;
        }

        private static int lastNonSpace(String s) {
            int i = s.length() - 1;
            while (i >= 0 && s.charAt(i) == ' ') i--;
            return i;
        }
    }

    record Shapeless(String group, CraftingCategory category, Result result, List<Ingredient> ingredients) implements Recipe {
        public String type() { return "minecraft:crafting_shapeless"; }

        public void encode(ByteBuf buf, RecipeContext ctx) {
            Types.writeString(buf, group);
            Types.writeVarInt(buf, category.ordinal());
            Types.writeVarInt(buf, ingredients.size());
            for (Ingredient i : ingredients) writeIngredient(buf, i, ctx);
            result.encode(buf, ctx);
        }

        static Shapeless parse(JsonObject o, RecipeContext ctx) {
            List<Ingredient> ings = new ArrayList<>();
            for (JsonElement e : o.getAsJsonArray("ingredients")) {
                Ingredient i = Ingredient.parse(e, ctx, true);
                if (!i.isEmpty()) ings.add(i);   // 바닐라: 빈 재료는 거른다
            }
            if (ings.isEmpty()) throw new IllegalArgumentException("No ingredients for shapeless recipe");
            if (ings.size() > 9) throw new IllegalArgumentException("Too many ingredients for shapeless recipe");
            return new Shapeless(Recipe.readGroup(o), Recipe.readCategory(o, CraftingCategory.class, CraftingCategory.MISC),
                Result.parse(o.getAsJsonObject("result"), ctx), List.copyOf(ings));
        }
    }

    /** 특수 제작(갑옷 염색·지도 복제 …)과 장식 단지 — 카테고리만 실린다. */
    record Special(String type, CraftingCategory category) implements Recipe {
        public void encode(ByteBuf buf, RecipeContext ctx) { Types.writeVarInt(buf, category.ordinal()); }
    }

    // ------------------------------------------------------------------ 화로류

    record Cooking(String type, String group, CookingCategory category, Ingredient ingredient, Result result, float experience,
                   int cookingTime) implements Recipe {
        public void encode(ByteBuf buf, RecipeContext ctx) {
            Types.writeString(buf, group);
            Types.writeVarInt(buf, category.ordinal());
            writeIngredient(buf, ingredient, ctx);
            result.encode(buf, ctx);
            buf.writeFloat(experience);
            Types.writeVarInt(buf, cookingTime);
        }

        static Cooking parse(String type, JsonObject o, RecipeContext ctx, int defaultTime) {
            return new Cooking(type, Recipe.readGroup(o), Recipe.readCategory(o, CookingCategory.class, CookingCategory.MISC),
                Ingredient.parse(o.get("ingredient"), ctx, false), Result.parse(o.getAsJsonObject("result"), ctx),
                o.has("experience") ? o.get("experience").getAsFloat() : 0f,
                o.has("cookingtime") ? o.get("cookingtime").getAsInt() : defaultTime);
        }
    }

    record Stonecutting(String group, Ingredient ingredient, Result result) implements Recipe {
        public String type() { return "minecraft:stonecutting"; }

        public void encode(ByteBuf buf, RecipeContext ctx) {
            Types.writeString(buf, group);
            writeIngredient(buf, ingredient, ctx);
            result.encode(buf, ctx);
        }
    }

    // ------------------------------------------------------------------ 대장장이 작업대

    record SmithingTransform(Ingredient template, Ingredient base, Ingredient addition, Result result) implements Recipe {
        public String type() { return "minecraft:smithing_transform"; }

        public void encode(ByteBuf buf, RecipeContext ctx) {
            writeIngredient(buf, template, ctx);
            writeIngredient(buf, base, ctx);
            writeIngredient(buf, addition, ctx);
            result.encode(buf, ctx);
        }
    }

    record SmithingTrim(Ingredient template, Ingredient base, Ingredient addition) implements Recipe {
        public String type() { return "minecraft:smithing_trim"; }

        public void encode(ByteBuf buf, RecipeContext ctx) {
            writeIngredient(buf, template, ctx);
            writeIngredient(buf, base, ctx);
            writeIngredient(buf, addition, ctx);
        }
    }

    /** JSON 하나를 레시피로(바닐라 Recipe.CODEC 디스패치). 모르는 타입·잘못된 내용은 예외. */
    static Recipe parse(JsonObject o, RecipeContext ctx) {
        String type = ResourceId.parse(o.get("type").getAsString()).toString();
        return switch (type) {
            case "minecraft:crafting_shaped" -> Shaped.parse(o, ctx);
            case "minecraft:crafting_shapeless" -> Shapeless.parse(o, ctx);
            case "minecraft:smelting" -> Cooking.parse(type, o, ctx, 200);
            case "minecraft:blasting", "minecraft:smoking", "minecraft:campfire_cooking" -> Cooking.parse(type, o, ctx, 100);
            case "minecraft:stonecutting" -> new Stonecutting(Recipe.readGroup(o), Ingredient.parse(o.get("ingredient"), ctx, false),
                Result.parse(o.getAsJsonObject("result"), ctx));
            case "minecraft:smithing_transform" -> new SmithingTransform(Ingredient.parse(o.get("template"), ctx, true),
                Ingredient.parse(o.get("base"), ctx, true), Ingredient.parse(o.get("addition"), ctx, true),
                Result.parse(o.getAsJsonObject("result"), ctx));
            case "minecraft:smithing_trim" -> new SmithingTrim(Ingredient.parse(o.get("template"), ctx, true),
                Ingredient.parse(o.get("base"), ctx, true), Ingredient.parse(o.get("addition"), ctx, true));
            case "minecraft:crafting_special_armordye", "minecraft:crafting_special_bookcloning", "minecraft:crafting_special_mapcloning",
                 "minecraft:crafting_special_mapextending", "minecraft:crafting_special_firework_rocket",
                 "minecraft:crafting_special_firework_star", "minecraft:crafting_special_firework_star_fade",
                 "minecraft:crafting_special_tippedarrow", "minecraft:crafting_special_bannerduplicate",
                 "minecraft:crafting_special_shielddecoration", "minecraft:crafting_special_shulkerboxcoloring",
                 "minecraft:crafting_special_suspiciousstew", "minecraft:crafting_special_repairitem",
                 "minecraft:crafting_decorated_pot" -> new Special(type, Recipe.readCategory(o, CraftingCategory.class, CraftingCategory.MISC));
            default -> throw new IllegalArgumentException("Unknown recipe serializer " + type);
        };
    }
}
