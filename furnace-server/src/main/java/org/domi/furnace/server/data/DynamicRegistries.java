// 데이터 주도 동기화 레지스트리(바닐라 RegistryDataLoader 의 동기화 부분): 켜진 데이터 팩의 <레지스트리>/*.json 과
// 플러그인이 부트스트랩 훅으로 더한 항목을 서버 시작 때 한 번 읽어 순서·내용을 정한다. /reload 로는 바뀌지 않는다(바닐라와 같다).
//  - 순서: 파일 id 순(경로 → namespace, 바닐라 ResourceLocation 순서) = 클라가 받는 번호.
//  - 바닐라 팩 그대로인 항목은 데이터 없이(클라가 아는 minecraft:core), 데이터 팩·플러그인 항목은 네트워크 NBT 를 싣는다.
//  - 바이옴·차원 타입·피해 유형·채팅 유형은 아직 내장 목록(바이옴·차원 타입은 W2 몫).
// 차이: 바닐라는 항목 하나라도 읽지 못하면 월드를 열지 않는다. Furnace 는 그 항목만 빼고 오류를 남긴다.
package org.domi.furnace.server.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DynamicRegistries {

    private static final Logger log = LoggerFactory.getLogger("Furnace");

    /** 데이터 팩에서 읽는 동기화 레지스트리. */
    public static final List<String> DATA_DRIVEN = List.of(
            "minecraft:banner_pattern", "minecraft:enchantment", "minecraft:jukebox_song", "minecraft:painting_variant",
            "minecraft:trim_material", "minecraft:trim_pattern", "minecraft:wolf_variant");

    /** 플러그인이 부트스트랩 훅으로 더한 항목(데이터 팩 JSON 과 같은 꼴). 같은 id 의 데이터 팩 파일이 있으면 그쪽이 이긴다. */
    public record Contribution(String owner, String registry, String id, JsonObject json) {}

    private record Def(JsonObject json, boolean known, String source) {}

    private static volatile DynamicRegistries current;

    private final RegistrySet registries;
    private final Map<String, Map<String, JsonObject>> definitions;
    private final Map<String, Map<String, String>> sources;

    private DynamicRegistries(RegistrySet registries, Map<String, Map<String, JsonObject>> definitions, Map<String, Map<String, String>> sources) {
        this.registries = registries;
        this.definitions = definitions;
        this.sources = sources;
    }

    /** 서버가 지금 쓰는 것(데이터 팩 없이 돌면 null — 그때는 내장 목록·내장 정의를 쓴다). */
    public static DynamicRegistries current() { return current; }

    /** 지금 것으로 건다(RegistrySet.current 도 함께). null 이면 내장 목록으로 되돌린다. */
    public static void install(DynamicRegistries r) {
        current = r;
        RegistrySet.install(r == null ? null : r.registries);
    }

    /** 이것이 지금 것이면 내린다(서버 종료·시험 정리). */
    public static void uninstall(DynamicRegistries r) {
        if (current == r) install(null);
    }

    /** 레지스트리 키 → 데이터 팩 폴더("minecraft:wolf_variant" → "wolf_variant", 다른 namespace 는 "ns/path"). */
    static String directory(String registry) {
        ResourceId id = ResourceId.parse(registry);
        return id.namespace().equals("minecraft") ? id.path() : id.namespace() + "/" + id.path();
    }

    /** base(내장 목록)에서 데이터 주도 레지스트리를 rm·플러그인 항목으로 다시 만든다. */
    public static DynamicRegistries load(RegistrySet base, ResourceManager rm, List<Contribution> contributions) {
        RegistrySet set = base;
        Map<String, Map<String, JsonObject>> defs = new HashMap<>();
        Map<String, Map<String, String>> sources = new HashMap<>();
        for (String registry : DATA_DRIVEN) {
            Map<ResourceId, Def> entries = new TreeMap<>();
            if (contributions != null) {
                for (Contribution c : contributions) {
                    if (!c.registry().equals(registry)) continue;
                    String err = validate(registry, c.json());
                    if (err != null) {
                        log.error("Failed to parse {} from plugin {} ({}): {}", c.id(), c.owner(), registry, err);
                        continue;
                    }
                    entries.put(ResourceId.parse(c.id()), new Def(c.json(), false, "plugin:" + c.owner()));
                }
            }
            String dir = directory(registry);
            String prefix = dir + "/";
            if (rm != null) {
                for (var e : rm.listResources(dir, id -> id.path().endsWith(".json")).entrySet()) {
                    ResourceId file = e.getKey();
                    ResourceId id = file.withPath(file.path().substring(prefix.length(), file.path().length() - ".json".length()));
                    JsonElement json;
                    try (InputStream in = e.getValue().open().get()) {
                        json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                    } catch (Exception ex) {
                        log.error("Failed to parse {} from pack {}", file, e.getValue().packId(), ex);
                        continue;
                    }
                    String err = json.isJsonObject() ? validate(registry, json.getAsJsonObject()) : "not a JSON object";
                    if (err != null) {
                        log.error("Failed to parse {} from pack {}: {}", file, e.getValue().packId(), err);
                        continue;
                    }
                    String pack = e.getValue().packId();
                    entries.put(id, new Def(json.getAsJsonObject(), PackRepository.VANILLA.equals(pack), pack));
                }
            }
            List<String> baseEntries = base.entries(registry);
            List<String> ids = new ArrayList<>(entries.size());
            for (ResourceId id : entries.keySet()) ids.add(id.toString());
            if (rm == null || !ids.containsAll(baseEntries)) {
                // 바닐라 팩을 읽지 못했다 — 내장 목록 그대로(번호가 클라와 어긋나지 않게)
                if (rm != null) log.warn("{}: 바닐라 항목이 빠져 내장 목록을 쓴다", registry);
                continue;
            }
            Map<String, NbtCompound> data = new LinkedHashMap<>();
            Map<String, JsonObject> d = new LinkedHashMap<>();
            Map<String, String> src = new LinkedHashMap<>();
            for (var e : entries.entrySet()) {
                String id = e.getKey().toString();
                d.put(id, e.getValue().json());
                src.put(id, e.getValue().source());
                if (!e.getValue().known()) data.put(id, JsonNbt.compound(e.getValue().json()));
            }
            set = set.with(registry, ids, data);
            defs.put(registry, Collections.unmodifiableMap(d));
            sources.put(registry, Collections.unmodifiableMap(src));
        }
        return new DynamicRegistries(set, defs, sources);
    }

    public RegistrySet registries() { return registries; }

    /** 그 레지스트리 항목의 정의(레지스트리 순서). 데이터 팩에서 읽지 못한 레지스트리면 빈 맵. */
    public Map<String, JsonObject> definitions(String registry) { return definitions.getOrDefault(registry, Map.of()); }

    /** 항목 정의(없으면 null). */
    public JsonObject definition(String registry, String id) {
        Map<String, JsonObject> m = definitions.get(registry);
        return m == null ? null : m.get(id);
    }

    /** 항목을 낸 곳: 팩 id 또는 "plugin:<이름>"(없으면 null). */
    public String source(String registry, String id) {
        Map<String, String> m = sources.get(registry);
        return m == null ? null : m.get(id);
    }

    // ------------------------------------------------------------------ 모양 검사(필수 필드·범위만 — 바닐라 코덱의 일부)

    static String validate(String registry, JsonObject o) {
        return switch (registry) {
            case "minecraft:wolf_variant" -> req(o, "wild_texture", "tame_texture", "angry_texture", "biomes");
            case "minecraft:painting_variant" -> {
                String e = req(o, "asset_id", "width", "height");
                if (e != null) yield e;
                yield range(o, "width", 1, 16) != null ? range(o, "width", 1, 16) : range(o, "height", 1, 16);
            }
            case "minecraft:enchantment" -> {
                String e = req(o, "description", "supported_items", "weight", "max_level", "min_cost", "max_cost", "anvil_cost", "slots");
                if (e != null) yield e;
                yield range(o, "weight", 1, 1024) != null ? range(o, "weight", 1, 1024) : range(o, "max_level", 1, 255);
            }
            case "minecraft:banner_pattern" -> req(o, "asset_id", "translation_key");
            case "minecraft:trim_material" -> req(o, "asset_name", "ingredient", "item_model_index", "description");
            case "minecraft:trim_pattern" -> req(o, "asset_id", "template_item", "description");
            case "minecraft:jukebox_song" -> req(o, "sound_event", "description", "length_in_seconds", "comparator_output");
            default -> null;
        };
    }

    private static String req(JsonObject o, String... keys) {
        for (String k : keys) if (!o.has(k) || o.get(k).isJsonNull()) return "No key " + k + " in MapLike";
        return null;
    }

    private static String range(JsonObject o, String key, int min, int max) {
        try {
            int v = o.get(key).getAsInt();
            return v < min || v > max ? "Value " + v + " outside of range [" + min + ":" + max + "]" : null;
        } catch (RuntimeException e) {
            return "Not a number: " + o.get(key);
        }
    }
}
