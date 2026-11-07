// 게임 레지스트리 훅(LifecycleEvents.GAME_REGISTRIES)의 데이터 주도 몫(GameRegistryStore.Registrar 가 넘긴다): 타입 있는 정의를 데이터 팩 JSON 꼴로 바꿔
// DynamicRegistries 에 넘긴다. 태그 더하기는 플러그인별로 들고 있다가 데이터를 읽을 때마다(시작·/reload) 넘기고, 플러그인이 꺼지면 뺀다.
package org.domi.furnace.server.plugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.domi.furnace.api.plugin.PluginDescription;
import org.domi.furnace.api.registry.EnchantmentDefinition;
import org.domi.furnace.api.registry.PaintingVariant;
import org.domi.furnace.api.registry.WolfVariant;
import org.domi.furnace.server.data.DataPackManager;
import org.domi.furnace.server.data.DynamicRegistries;
import org.domi.furnace.server.data.ResourceId;

final class GameRegistryHooks {

    private final List<DataPackManager.TagAddition> tags = new CopyOnWriteArrayList<>();

    /** 지금 남아 있는 플러그인 태그 더하기(등록 순). */
    List<DataPackManager.TagAddition> tagAdditions() { return List.copyOf(tags); }

    void removeOwner(String owner) { tags.removeIf(t -> t.owner().equals(owner)); }

    /** 그 플러그인의 등록기. 더한 항목은 out 에 모인다. */
    DataRegistrar registrar(PluginDescription d, List<DynamicRegistries.Contribution> out) {
        return new DataRegistrar(d, out);
    }

    final class DataRegistrar {
        private final PluginDescription d;
        private final List<DynamicRegistries.Contribution> out;

        DataRegistrar(PluginDescription d, List<DynamicRegistries.Contribution> out) {
            this.d = d;
            this.out = out;
        }

        private String ownKey(String key) {
            if (key == null) throw new NullPointerException("key");
            if (key.indexOf(':') < 0) key = d.namespace() + ":" + key;   // 바이옴·차원 타입처럼 namespace 를 붙인다
            ResourceId id = ResourceId.tryParse(key);
            if (id == null) throw new IllegalArgumentException("잘못된 키(namespace:path): " + key);
            if (!id.namespace().equals(d.namespace())) {
                throw new IllegalArgumentException("키의 namespace 는 자기 플러그인 namespace(" + d.namespace() + ")여야 한다: " + key);
            }
            return id.toString();
        }

        private void add(String registry, String key, JsonObject json) {
            String k = ownKey(key);
            for (var c : out) {
                if (c.registry().equals(registry) && c.id().equals(k)) throw new IllegalArgumentException("이미 더한 항목: " + registry + " " + k);
            }
            out.add(new DynamicRegistries.Contribution(d.name(), registry, k, json));
        }

        public void wolfVariant(String key, WolfVariant v) {
            JsonObject o = new JsonObject();
            o.addProperty("wild_texture", id(v.wildTexture()));
            o.addProperty("tame_texture", id(v.tameTexture()));
            o.addProperty("angry_texture", id(v.angryTexture()));
            o.add("biomes", holderSet(v.biomes()));
            add("minecraft:wolf_variant", key, o);
        }

        public void paintingVariant(String key, PaintingVariant v) {
            JsonObject o = new JsonObject();
            o.addProperty("asset_id", id(v.assetId()));
            o.addProperty("height", v.height());
            o.addProperty("width", v.width());
            add("minecraft:painting_variant", key, o);
        }

        public void enchantment(String key, EnchantmentDefinition e) {
            add("minecraft:enchantment", key, enchantmentJson(e));
        }

        public void json(String registry, String key, String json) {
            String reg = ResourceId.parse(registry).toString();
            if (!DynamicRegistries.DATA_DRIVEN.contains(reg)) throw new IllegalArgumentException("항목을 더할 수 없는 레지스트리: " + registry);
            JsonElement e;
            try {
                e = JsonParser.parseString(json);
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException("JSON 을 읽지 못했다: " + ex.getMessage(), ex);
            }
            if (!e.isJsonObject()) throw new IllegalArgumentException("JSON 객체가 아니다");
            add(reg, key, e.getAsJsonObject());
        }

        public void addToTag(String registry, String tag, String... entries) {
            String reg = ResourceId.parse(registry).toString();
            if (!org.domi.furnace.server.data.TagRegistries.SYNCED.contains(reg)) {
                throw new IllegalArgumentException("태그를 보내지 않는 레지스트리: " + registry);
            }
            ResourceId t = ResourceId.tryParse(tag.startsWith("#") ? tag.substring(1) : tag);
            if (t == null) throw new IllegalArgumentException("잘못된 태그 id: " + tag);
            List<String> list = new ArrayList<>();
            for (String e : entries) list.add(id(e));
            tags.add(new DataPackManager.TagAddition(d.name(), reg, t.toString(), List.copyOf(list)));
        }
    }

    private static String id(String s) {
        ResourceId id = ResourceId.tryParse(s);
        if (id == null) throw new IllegalArgumentException("잘못된 id: " + s);
        return id.toString();
    }

    /** 바닐라 HolderSet 꼴: 태그 하나면 "#tag" 문자열, 아니면 id 목록. */
    static JsonElement holderSet(List<String> set) {
        if (set.size() == 1 && set.get(0).startsWith("#")) return new com.google.gson.JsonPrimitive("#" + id(set.get(0).substring(1)));
        JsonArray a = new JsonArray();
        for (String s : set) a.add(id(s));
        return a;
    }

    static JsonObject enchantmentJson(EnchantmentDefinition e) {
        JsonObject o = new JsonObject();
        o.addProperty("anvil_cost", e.anvilCost());
        o.add("description", JsonParser.parseString(org.domi.furnace.api.text.serializer.JsonTextSerializer.json().serialize(e.description())));
        if (!e.exclusiveSet().isEmpty()) o.add("exclusive_set", holderSet(e.exclusiveSet()));
        o.add("max_cost", cost(e.maxCost()));
        o.addProperty("max_level", e.maxLevel());
        o.add("min_cost", cost(e.minCost()));
        if (e.primaryItems() != null) o.add("primary_items", holderSet(e.primaryItems()));
        JsonArray slots = new JsonArray();
        e.slots().forEach(slots::add);
        o.add("slots", slots);
        o.add("supported_items", holderSet(e.supportedItems()));
        o.addProperty("weight", e.weight());
        return o;
    }

    private static JsonObject cost(EnchantmentDefinition.Cost c) {
        JsonObject o = new JsonObject();
        o.addProperty("base", c.base());
        o.addProperty("per_level_above_first", c.perLevelAboveFirst());
        return o;
    }
}
