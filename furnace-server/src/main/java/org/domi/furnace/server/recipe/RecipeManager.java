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

    /** 한 시점의 레시피 표와 거기서 만든 색인·네트워크 본문. 바꿀 때마다 새로 만들어 통째로 바꿔 끼운다(읽는 쪽은 락이 없다). */
    private static final class State {
        final Map<ResourceId, Recipe> byName;
        final byte[] networkBody;
        final List<CraftingEntry> crafting;
        final List<StonecuttingEntry> stonecutting;
        final List<SmithingEntry> smithing;
        final Map<String, List<CookingEntry>> cooking;

        State(Map<ResourceId, Recipe> byName, RecipeContext ctx) {
            this.byName = byName;
            // 바닐라 byType(CRAFTING) — 레시피 맵 순서 그대로(getRecipeFor 가 첫 일치를 고르므로 순서가 결과를 정한다)
            List<CraftingEntry> c = new ArrayList<>();
            for (var e : byName.entrySet()) if (e.getValue().isCrafting()) c.add(new CraftingEntry(e.getKey(), e.getValue()));
            this.crafting = List.copyOf(c);
            List<StonecuttingEntry> sc = new ArrayList<>();
            for (var e : byName.entrySet()) if (e.getValue() instanceof Recipe.Stonecutting r) sc.add(new StonecuttingEntry(e.getKey(), r));
            this.stonecutting = List.copyOf(sc);
            List<SmithingEntry> sm = new ArrayList<>();
            for (var e : byName.entrySet()) if (e.getValue() instanceof Recipe.Smithing r) sm.add(new SmithingEntry(e.getKey(), r));
            this.smithing = List.copyOf(sm);
            Map<String, List<CookingEntry>> ck = new HashMap<>();
            for (var e : byName.entrySet()) if (e.getValue() instanceof Recipe.Cooking r) ck.computeIfAbsent(r.type(), k -> new ArrayList<>()).add(new CookingEntry(e.getKey(), r));
            this.cooking = ck;
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
            this.networkBody = body;
        }
    }

    private volatile State state;
    private final RecipeContext ctx;

    public record CookingEntry(ResourceId id, Recipe.Cooking recipe) { }
    private final Recipe.TrimData trims;

    public record SmithingEntry(ResourceId id, Recipe.Smithing recipe) { }

    public record StonecuttingEntry(ResourceId id, Recipe.Stonecutting recipe) { }

    private RecipeManager(Map<ResourceId, Recipe> byName, RecipeContext ctx, Recipe.TrimData trims) {
        this.trims = trims;
        this.ctx = ctx;
        this.state = new State(byName, ctx);
    }

    /**
     * 레시피를 한 번에 바꾼다(플러그인 등록·제거). remove 를 먼저 적용하고 add 를 맵 끝에 붙인다(제작은 맵 순서의 첫 일치라 데이터 팩 레시피가 먼저 맞는다).
     * 새 표를 다 만든 뒤 한 번에 바꿔 끼우므로 읽는 쪽은 옛 표나 새 표 중 하나만 본다. 모든 스레드에서 불러도 된다.
     * @return 실제로 지운 id
     */
    public synchronized List<ResourceId> change(Map<ResourceId, Recipe> add, java.util.Collection<ResourceId> remove) {
        Map<ResourceId, Recipe> next = new LinkedHashMap<>(state.byName);
        List<ResourceId> removed = new ArrayList<>();
        for (ResourceId id : remove) if (next.remove(id) != null) removed.add(id);
        next.putAll(add);
        state = new State(next, ctx);
        return removed;
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
        return new RecipeManager(byName, ctx, loadTrims(rm));
    }

    /** 데이터 팩의 trim_material(ingredient)·trim_pattern(template_item)을 읽는다. */
    private static Recipe.TrimData loadTrims(ResourceManager rm) {
        Map<ResourceId, ResourceId> materials = new HashMap<>(), patterns = new HashMap<>();
        for (var e : rm.listResources("trim_material", id -> id.path().endsWith(".json")).entrySet()) {
            try (InputStream in = e.getValue().open().get()) {
                JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                materials.put(ResourceId.parse(o.get("ingredient").getAsString()), trimId(e.getKey(), "trim_material"));
            } catch (Exception ex) {
                log.error("Couldn't parse trim material {}", e.getKey(), ex);
            }
        }
        for (var e : rm.listResources("trim_pattern", id -> id.path().endsWith(".json")).entrySet()) {
            try (InputStream in = e.getValue().open().get()) {
                JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                patterns.put(ResourceId.parse(o.get("template_item").getAsString()), trimId(e.getKey(), "trim_pattern"));
            } catch (Exception ex) {
                log.error("Couldn't parse trim pattern {}", e.getKey(), ex);
            }
        }
        return new Recipe.TrimData(materials, patterns);
    }

    private static ResourceId trimId(ResourceId file, String dir) {
        return file.withPath(file.path().substring(dir.length() + 1, file.path().length() - ".json".length()));
    }

    /** 바닐라 getRecipesFor(SMITHING, input): 맞는 레시피를 결과 번역 키 순(같으면 맵 순서)으로. */
    public List<SmithingEntry> findSmithing(org.domi.furnace.server.entity.ItemStack t, org.domi.furnace.server.entity.ItemStack b,
            org.domi.furnace.server.entity.ItemStack a) {
        List<SmithingEntry> out = new ArrayList<>();
        for (SmithingEntry e : state.smithing) if (e.recipe().matches(t, b, a, ctx)) out.add(e);
        out.sort(java.util.Comparator.comparing(e -> descriptionId(e.recipe().sortItem())));
        return out;
    }

    /** 칸 확인용(Menu 의 mayPlace): 그 칸에 놓을 수 있는 레시피가 하나라도 있는가. slot 0 거푸집, 1 밑감, 2 재료. */
    public boolean smithingAccepts(int slot, org.domi.furnace.server.entity.ItemStack s) {
        for (SmithingEntry e : state.smithing) {
            if (slot == 0 ? e.recipe().isTemplate(s, ctx) : slot == 1 ? e.recipe().isBase(s, ctx) : e.recipe().isAddition(s, ctx)) return true;
        }
        return false;
    }

    /** 바닐라 getRecipeFor(SMELTING/BLASTING/SMOKING, input): 레시피 맵 순서의 첫 일치. type = minecraft:smelting 등. */
    public CookingEntry findCooking(String type, org.domi.furnace.server.entity.ItemStack input) {
        if (input.isEmpty()) return null;
        for (CookingEntry e : state.cooking.getOrDefault(type, List.of())) if (e.recipe().ingredient().test(input, ctx)) return e;
        return null;
    }

    public List<SmithingEntry> smithingRecipes() { return state.smithing; }

    public Recipe.TrimData trims() { return trims; }

    public Recipe recipe(ResourceId id) { return state.byName.get(id); }

    public Collection<ResourceId> ids() { return state.byName.keySet(); }

    public int size() { return state.byName.size(); }

    public RecipeContext context() { return ctx; }

    /** 제작대 레시피 id → 항목(없거나 제작대 레시피가 아니면 null). */
    public CraftingEntry craftingEntry(ResourceId id) {
        Recipe r = state.byName.get(id);
        return r != null && r.isCrafting() ? new CraftingEntry(id, r) : null;
    }

    /**
     * 바닐라 getRecipeFor(CRAFTING, input, level, lastRecipe): 빈 입력이면 없음, 지난 레시피가 아직 맞으면 그것, 아니면 순서대로 첫 일치.
     */
    public CraftingEntry findCrafting(CraftingInput input, CraftingEntry last) {
        if (input.isEmpty()) return null;
        State st = state;
        // 지난 레시피는 아직 표에 있을 때만(플러그인이 내렸거나 바꿨으면 다시 찾는다)
        if (last != null && st.byName.get(last.id()) == last.recipe() && last.recipe().matches(input, ctx)) return last;
        for (CraftingEntry e : st.crafting) if (e.recipe().matches(input, ctx)) return e;
        return null;
    }

    /**
     * 바닐라 getRecipesFor(STONECUTTING, input): 재료가 맞는 레시피를 결과 아이템의 번역 키(getDescriptionId) 순으로(같으면 레시피 맵 순서).
     */
    public List<StonecuttingEntry> findStonecutting(org.domi.furnace.server.entity.ItemStack input) {
        List<StonecuttingEntry> out = new ArrayList<>();
        for (StonecuttingEntry e : state.stonecutting) if (e.recipe().ingredient().test(input, ctx)) out.add(e);
        out.sort(java.util.Comparator.comparing(e -> descriptionId(e.recipe().result().item())));
        return out;
    }

    /** Item.getDescriptionId: 블록 아이템은 block.<ns>.<path>, 그 밖은 item.<ns>.<path>. */
    private static String descriptionId(ResourceId item) {
        boolean block = org.domi.furnace.server.item.ItemContext.installed()
                && org.domi.furnace.server.item.ItemContext.id("minecraft:block", item.toString()) != null;
        return (block ? "block." : "item.") + item.namespace() + "." + item.path().replace('/', '.');
    }

    /** 바닐라 getRemainingItemsFor(CRAFTING, input): 일치하는 레시피의 남는 아이템, 없으면 입력 그대로. */
    public List<org.domi.furnace.server.entity.ItemStack> remainingItems(CraftingInput input) {
        CraftingEntry e = findCrafting(input, null);
        if (e != null) return e.recipe().remainingItems(input, ctx);
        return new ArrayList<>(input.items());
    }

    /** update_recipes 본문(레시피 수 + 각 id·직렬화기·내용). */
    public byte[] networkBody() { return state.networkBody; }
}
