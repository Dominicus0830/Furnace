// 레시피 매니저(바닐라 RecipeManager) — 데이터 팩 recipe/ 를 읽는다. 순서는 바닐라와 같다:
// 파일 id 순(TreeMap)으로 HashMap 에 넣은 뒤 그 HashMap 순서 그대로(ResourceId.hashCode = 바닐라 ResourceLocation.hashCode).
// update_recipes 본문은 읽을 때 한 번 만들어 둔다(1,290개 ≈ 108KB 를 접속마다 다시 만들지 않는다).
package org.domi.furnace.server.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.data.ResourceManager;
import org.domi.furnace.server.net.io.Types;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RecipeManager {

    private static final Logger log = LoggerFactory.getLogger("Furnace");

    /** 바닐라 RECIPE_SERIALIZER 레지스트리 순서. */
    static final List<String> SERIALIZERS = List.of(
        "minecraft:crafting_shaped", "minecraft:crafting_shapeless", "minecraft:crafting_special_armordye",
        "minecraft:crafting_special_bookcloning", "minecraft:crafting_special_mapcloning", "minecraft:crafting_special_mapextending",
        "minecraft:crafting_special_firework_rocket", "minecraft:crafting_special_firework_star",
        "minecraft:crafting_special_firework_star_fade", "minecraft:crafting_special_tippedarrow",
        "minecraft:crafting_special_bannerduplicate", "minecraft:crafting_special_shielddecoration",
        "minecraft:crafting_special_shulkerboxcoloring", "minecraft:crafting_special_suspiciousstew",
        "minecraft:crafting_special_repairitem", "minecraft:smelting", "minecraft:blasting", "minecraft:smoking",
        "minecraft:campfire_cooking", "minecraft:stonecutting", "minecraft:smithing_transform", "minecraft:smithing_trim",
        "minecraft:crafting_decorated_pot");

    public static int serializerIndex(String type) {
        int i = SERIALIZERS.indexOf(type);
        if (i < 0) throw new IllegalArgumentException("Unknown recipe serializer " + type);
        return i;
    }

    /** 직렬화기 → 레시피 타입(바닐라 RecipeType). */
    static String recipeType(String serializer) {
        if (serializer.startsWith("minecraft:crafting_")) return "minecraft:crafting";
        if (serializer.startsWith("minecraft:smithing_")) return "minecraft:smithing";
        return serializer; // smelting·blasting·smoking·campfire_cooking·stonecutting
    }

    /** 제작대 레시피 하나(id + 레시피). */
    public record CraftingEntry(ResourceId id, Recipe recipe) {
        public boolean isSpecial() { return recipe.isSpecial(); }
    }

    private final Map<ResourceId, Recipe> byName;
    private final byte[] networkBody;
    private final RecipeContext ctx;
    private final List<CraftingEntry> crafting;

    private RecipeManager(Map<ResourceId, Recipe> byName, byte[] networkBody, RecipeContext ctx) {
        this.byName = byName;
        this.networkBody = networkBody;
        this.ctx = ctx;
        // 바닐라 byType(CRAFTING) — 레시피 맵 순서 그대로(getRecipeFor 가 첫 일치를 고르므로 순서가 결과를 정한다)
        List<CraftingEntry> c = new ArrayList<>();
        for (var e : byName.entrySet()) if (e.getValue().isCrafting()) c.add(new CraftingEntry(e.getKey(), e.getValue()));
        this.crafting = List.copyOf(c);
    }

    public static RecipeManager load(ResourceManager rm, RecipeContext ctx) {
        // 바닐라 SimpleJsonResourceReloadListener.scanDirectory → HashMap(파일 id 순으로 put)
        Map<ResourceId, JsonObject> prepared = new HashMap<>();
        String prefix = "recipe/";
        for (var e : rm.listResources("recipe", id -> id.path().endsWith(".json")).entrySet()) {
            ResourceId file = e.getKey();
            ResourceId id = file.withPath(file.path().substring(prefix.length(), file.path().length() - ".json".length()));
            try (InputStream in = e.getValue().open().get()) {
                prepared.put(id, JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (Exception ex) {
                log.error("Couldn't parse data file {} from {}", id, file, ex);
            }
        }
        Map<ResourceId, Recipe> byName = new LinkedHashMap<>();
        for (var e : prepared.entrySet()) {
            try {
                byName.put(e.getKey(), Recipe.parse(e.getValue(), ctx));
            } catch (RuntimeException ex) {
                log.error("Parsing error loading recipe {}", e.getKey(), ex);
            }
        }
        log.info("Loaded {} recipes", byName.size());
        // 패킷은 바닐라 getOrderedRecipes = byType.values() 순서 — 레시피 타입이 처음 나온 순으로 묶는다
        Map<String, List<Map.Entry<ResourceId, Recipe>>> byType = new LinkedHashMap<>();
        for (var e : byName.entrySet()) byType.computeIfAbsent(recipeType(e.getValue().type()), k -> new ArrayList<>()).add(e);
        ByteBuf buf = Unpooled.buffer(128 * 1024);
        Types.writeVarInt(buf, byName.size());
        for (var group : byType.values()) {
            for (var e : group) {
                Types.writeString(buf, e.getKey().toString());
                Types.writeVarInt(buf, ctx.serializerId(e.getValue().type()));
                e.getValue().encode(buf, ctx);
            }
        }
        byte[] body = new byte[buf.readableBytes()];
        buf.readBytes(body);
        buf.release();
        return new RecipeManager(byName, body, ctx);
    }

    public Recipe recipe(ResourceId id) { return byName.get(id); }

    public Collection<ResourceId> ids() { return byName.keySet(); }

    public int size() { return byName.size(); }

    public RecipeContext context() { return ctx; }

    /** 제작대 레시피 id → 항목(없거나 제작대 레시피가 아니면 null). */
    public CraftingEntry craftingEntry(ResourceId id) {
        Recipe r = byName.get(id);
        return r != null && r.isCrafting() ? new CraftingEntry(id, r) : null;
    }

    /**
     * 바닐라 getRecipeFor(CRAFTING, input, level, lastRecipe): 빈 입력이면 없음, 지난 레시피가 아직 맞으면 그것, 아니면 순서대로 첫 일치.
     */
    public CraftingEntry findCrafting(CraftingInput input, CraftingEntry last) {
        if (input.isEmpty()) return null;
        if (last != null && last.recipe().matches(input, ctx)) return last;
        for (CraftingEntry e : crafting) if (e.recipe().matches(input, ctx)) return e;
        return null;
    }

    /** 바닐라 getRemainingItemsFor(CRAFTING, input): 일치하는 레시피의 남는 아이템, 없으면 입력 그대로. */
    public List<org.domi.furnace.server.entity.ItemStack> remainingItems(CraftingInput input) {
        CraftingEntry e = findCrafting(input, null);
        if (e != null) return e.recipe().remainingItems(input, ctx);
        return new ArrayList<>(input.items());
    }

    /** update_recipes 본문(레시피 수 + 각 id·직렬화기·내용). */
    public byte[] networkBody() { return networkBody; }
}
