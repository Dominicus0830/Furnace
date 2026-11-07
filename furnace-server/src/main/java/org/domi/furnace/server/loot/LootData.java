// 데이터 팩의 전리품 표·조건(predicate)·아이템 수정자(item_modifier)와 인챈트 정의(바닐라 ReloadableServerRegistries 의 전리품 부분).
package org.domi.furnace.server.loot;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.data.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LootData {

    private static final Logger log = LoggerFactory.getLogger(LootData.class);

    private final LootRegistries registries;
    private final Map<String, LootTable> tables;
    private final Map<String, LootCondition> predicates;
    private final Map<String, LootFunction> modifiers;
    private final Map<String, EnchantmentProvider> enchantmentProviders;

    private LootData(LootRegistries registries, Map<String, LootTable> tables, Map<String, LootCondition> predicates,
                     Map<String, LootFunction> modifiers, Map<String, EnchantmentProvider> enchantmentProviders) {
        this.registries = registries;
        this.tables = tables;
        this.predicates = predicates;
        this.modifiers = modifiers;
        this.enchantmentProviders = enchantmentProviders;
    }

    public static LootData load(ResourceManager rm, LootRegistries regs) {
        return load(rm, regs, null);
    }

    /**
     * enchantments: 시작 때 고정된 마법부여 레지스트리의 정의(레지스트리 순서). null 이거나 비면 rm 의 enchantment/*.json 을 읽는다.
     * 바닐라는 마법부여를 동적 레지스트리로 시작 때 한 번 읽는다 — /reload 로 새 마법부여가 생기지 않는다.
     */
    public static LootData load(ResourceManager rm, LootRegistries regs, Map<String, com.google.gson.JsonObject> enchantments) {
        EntityPredicate.nbtUsed = false;
        EntityPredicate.variantUsed = false;   // 리로드마다 다시 센다(이후 읽는 조건이 켠다)
        // 인챈트 정의 먼저(전리품 함수가 참조). 레지스트리 순서 = 파일 id 순(path → namespace).
        Map<String, ? extends JsonElement> defs = enchantments != null && !enchantments.isEmpty() ? enchantments : read(rm, "enchantment");
        for (var e : defs.entrySet()) {
            try {
                regs.putEnchantment(e.getKey(), Enchantment.parse(e.getKey(), e.getValue().getAsJsonObject(), regs));
            } catch (RuntimeException ex) {
                log.error("Couldn't parse element {}/{} - {}", "minecraft:enchantment", e.getKey(), ex.getMessage());
            }
        }
        Map<String, LootCondition> predicates = parseAll(rm, "predicate", e -> LootCondition.parse(e, regs));
        Map<String, LootFunction> modifiers = parseAll(rm, "item_modifier", e -> LootFunction.parse(e, regs));
        Map<String, LootTable> tables = parseAll(rm, "loot_table", e -> LootTable.parse(e.getAsJsonObject(), regs));
        Map<String, EnchantmentProvider> providers = parseAll(rm, "enchantment_provider", e -> EnchantmentProvider.parse(e, regs));
        return new LootData(regs, tables, predicates, modifiers, providers);
    }

    /** 마법 부여 제공자(enchantment_provider/*.json). 없으면 null(바닐라: 아무것도 걸지 않음). */
    public EnchantmentProvider enchantmentProvider(String id) { return enchantmentProviders.get(LootJson.id(id)); }

    private static <T> Map<String, T> parseAll(ResourceManager rm, String dir, Function<JsonElement, T> parser) {
        Map<String, T> out = new LinkedHashMap<>();
        for (var e : read(rm, dir).entrySet()) {
            try {
                out.put(e.getKey(), parser.apply(e.getValue()));
            } catch (RuntimeException ex) {
                log.error("Couldn't parse element {}/{} - {}", "minecraft:" + dir, e.getKey(), ex.getMessage());
            }
        }
        return Collections.unmodifiableMap(out);
    }

    /** 디렉터리의 JSON 전부(파일 id 순). */
    private static Map<String, JsonElement> read(ResourceManager rm, String dir) {
        Map<String, JsonElement> out = new LinkedHashMap<>();
        String prefix = dir + "/";
        for (var e : rm.listResources(dir, id -> id.path().endsWith(".json")).entrySet()) {
            ResourceId file = e.getKey();
            String id = file.withPath(file.path().substring(prefix.length(), file.path().length() - ".json".length())).toString();
            try (InputStream in = e.getValue().open().get()) {
                out.put(id, JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
            } catch (Exception ex) {
                log.error("Couldn't parse data file {} from {}", id, file, ex);
            }
        }
        return out;
    }

    public LootRegistries registries() { return registries; }

    /** 아이템 수정자(item_modifier/*.json). 없으면 null. */
    public LootFunction modifier(String id) { return modifiers.get(LootJson.id(id)); }

    /** 아이템 수정자 id 목록(제안용). */
    public java.util.Set<String> modifierIds() { return modifiers.keySet(); }

    /** 표가 있는가(LootTable.EMPTY 기본값과 구분). */
    public boolean hasTable(String id) { return tables.containsKey(LootJson.id(id)); }

    /** 명령 인자의 인라인 표(JSON 꼴). */
    public LootTable parseTable(JsonElement e) { return LootTable.parse(e.getAsJsonObject(), registries); }

    /** 명령 인자의 인라인 수정자(JSON 꼴, 배열이면 순서대로). */
    public LootFunction parseModifier(JsonElement e) { return LootFunction.parse(e, registries); }

    /** 표(없으면 빈 표 — 바닐라와 같다). */
    public LootTable table(String id) { return tables.getOrDefault(LootJson.id(id), LootTable.EMPTY); }

    public Map<String, LootTable> tables() { return tables; }

    public int size() { return tables.size(); }

    /** 조건(predicate/*.json). 없으면 null. */
    public LootCondition predicate(String id) { return predicates.get(LootJson.id(id)); }

    /** 조건 id 목록(제안용). */
    public java.util.Set<String> predicateIds() { return predicates.keySet(); }

    /** 바닐라 /execute if predicate 의 최상위 평가: 이 조건을 방문 목록에 올리고 시험한다(자기 참조는 참조 지점에서 끊긴다). */
    public boolean test(LootCondition c, LootContext ctx) {
        ctx.pushVisited(c);
        try {
            return c.test(ctx);
        } finally {
            ctx.popVisited(c);
        }
    }

    boolean testPredicate(String id, LootContext ctx) {
        LootCondition c = predicates.get(id);
        if (c == null) {
            log.warn("Tried using unknown predicate {}", id);
            return false;
        }
        if (!ctx.pushVisited(c)) {
            log.warn("Detected infinite loop in loot tables");
            return false;
        }
        try {
            return c.test(ctx);
        } finally {
            ctx.popVisited(c);
        }
    }

    LootStack applyModifier(String id, LootStack s, LootContext ctx) {
        LootFunction f = modifiers.get(id);
        if (f == null) {
            log.warn("Unknown function: {}", id);
            return s;
        }
        if (!ctx.pushVisited(f)) {
            log.warn("Detected infinite loop in loot tables");
            return s;
        }
        try {
            return f.apply(s, ctx);
        } finally {
            ctx.popVisited(f);
        }
    }
}
