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

    private LootData(LootRegistries registries, Map<String, LootTable> tables, Map<String, LootCondition> predicates,
                     Map<String, LootFunction> modifiers) {
        this.registries = registries;
        this.tables = tables;
        this.predicates = predicates;
        this.modifiers = modifiers;
    }

    public static LootData load(ResourceManager rm, LootRegistries regs) {
        // 인챈트 정의 먼저(전리품 함수가 참조). 레지스트리 순서 = 파일 id 순(path → namespace).
        for (var e : read(rm, "enchantment").entrySet()) {
            try {
                regs.putEnchantment(e.getKey(), Enchantment.parse(e.getKey(), e.getValue().getAsJsonObject(), regs));
            } catch (RuntimeException ex) {
                log.error("Couldn't parse element {}/{} - {}", "minecraft:enchantment", e.getKey(), ex.getMessage());
            }
        }
        Map<String, LootCondition> predicates = parseAll(rm, "predicate", e -> LootCondition.parse(e, regs));
        Map<String, LootFunction> modifiers = parseAll(rm, "item_modifier", e -> LootFunction.parse(e, regs));
        Map<String, LootTable> tables = parseAll(rm, "loot_table", e -> LootTable.parse(e.getAsJsonObject(), regs));
        return new LootData(regs, tables, predicates, modifiers);
    }

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

    /** 표(없으면 빈 표 — 바닐라와 같다). */
    public LootTable table(String id) { return tables.getOrDefault(LootJson.id(id), LootTable.EMPTY); }

    public Map<String, LootTable> tables() { return tables; }

    public int size() { return tables.size(); }

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
