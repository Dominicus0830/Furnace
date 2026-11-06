// worldgen/processor_list/*.json 로더 — 바닐라 StructureProcessorList 코덱 해석.
package org.domi.furnace.server.world.gen.structure.template;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.gen.feature.BlockTags;

public final class ProcessorLists {

    private final BlockRegistry registry;
    private final Map<String, List<Processor>> cache = new ConcurrentHashMap<>();

    public ProcessorLists(BlockRegistry registry) {
        this.registry = registry;
    }

    /** id: "minecraft:street_plains" 또는 "street_plains". */
    public List<Processor> get(String id) {
        String key = id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
        return cache.computeIfAbsent(key, this::load);
    }

    private List<Processor> load(String name) {
        String res = "worldgen/1.21.1/worldgen/processor_list/" + name + ".json";
        try (InputStream in = ProcessorLists.class.getClassLoader().getResourceAsStream(res)) {
            if (in == null) throw new IllegalArgumentException("processor_list 없음: " + name);
            JsonElement root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            JsonArray arr = root.isJsonArray() ? root.getAsJsonArray() : root.getAsJsonObject().getAsJsonArray("processors");
            List<Processor> out = new ArrayList<>();
            for (JsonElement e : arr) out.add(parseProcessor(e.getAsJsonObject()));
            return List.copyOf(out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ------------------------------------------------------------------ processor

    public Processor parseProcessor(JsonObject o) {
        String type = stripNs(o.get("processor_type").getAsString());
        switch (type) {
            case "rule": {
                List<Processors.Rule> rules = new ArrayList<>();
                for (JsonElement e : o.getAsJsonArray("rules")) rules.add(parseRule(e.getAsJsonObject()));
                return new Processors.RuleProcessor(rules);
            }
            case "block_rot": {
                Set<String> rottable = o.has("rottable_blocks") ? blockSet(o.get("rottable_blocks")) : null;
                return new Processors.BlockRot(rottable, o.get("integrity").getAsFloat());
            }
            case "capped": {
                Processor delegate = parseProcessor(o.getAsJsonObject("delegate"));
                int min, max;
                JsonElement lim = o.get("limit");
                if (lim.isJsonObject()) {
                    JsonObject lo = lim.getAsJsonObject();
                    String lt = stripNs(lo.get("type").getAsString());
                    if (lt.equals("constant")) { min = max = lo.get("value").getAsInt(); }
                    else { min = lo.get("min_inclusive").getAsInt(); max = lo.get("max_inclusive").getAsInt(); }
                } else {
                    min = max = lim.getAsInt();
                }
                return new Processors.Capped(delegate, min, max);
            }
            case "protected_blocks":
                return new Processors.ProtectedBlocks(blockSet(o.get("value")));
            case "block_age":
                return new Processors.BlockAge(o.get("mossiness").getAsFloat());
            case "gravity":
                return new Processors.Gravity(o.has("heightmap") ? o.get("heightmap").getAsString().toLowerCase() : "world_surface_wg",
                    o.has("offset") ? o.get("offset").getAsInt() : 0);
            case "jigsaw_replacement":
                return new Processors.JigsawReplacement();
            case "lava_submerged_block":
                return new Processors.LavaSubmerged();
            case "blackstone_replace":
                return new Processors.BlackstoneReplace();
            case "block_ignore": {
                Set<String> names = new HashSet<>();
                for (JsonElement e : o.getAsJsonArray("blocks")) names.add(e.getAsJsonObject().get("Name").getAsString());
                return new Processors.BlockIgnore(names);
            }
            case "nop":
                return new Processors.Nop();
            default:
                throw new IllegalArgumentException("미지원 processor_type: " + type);
        }
    }

    private Processors.Rule parseRule(JsonObject r) {
        Processors.RuleTest input = parseRuleTest(r.getAsJsonObject("input_predicate"));
        Processors.RuleTest loc = parseRuleTest(r.getAsJsonObject("location_predicate"));
        Processors.PosRuleTest pos = r.has("position_predicate") ? parsePosTest(r.getAsJsonObject("position_predicate")) : Processors.alwaysTruePos();
        int out = parseState(r.getAsJsonObject("output_state"));
        Processors.BlockEntityModifier mod = r.has("block_entity_modifier") ? parseModifier(r.getAsJsonObject("block_entity_modifier")) : Processors.passthrough();
        return new Processors.Rule(input, loc, pos, out, mod);
    }

    private Processors.RuleTest parseRuleTest(JsonObject o) {
        String t = stripNs(o.get("predicate_type").getAsString());
        return switch (t) {
            case "always_true" -> Processors.alwaysTrue();
            case "block_match" -> Processors.blockMatch(ns(o.get("block").getAsString()));
            case "blockstate_match" -> Processors.blockStateMatch(parseState(o.getAsJsonObject("block_state")));
            case "tag_match" -> Processors.tagMatch(new HashSet<>(BlockTags.get().blocksOf(ns(o.get("tag").getAsString().replaceFirst("^#", "")))));   // "tag" 는 # 없는 태그 id
            case "random_block_match" -> Processors.randomBlockMatch(ns(o.get("block").getAsString()), o.get("probability").getAsFloat());
            case "random_blockstate_match" -> Processors.randomBlockStateMatch(parseState(o.getAsJsonObject("block_state")), o.get("probability").getAsFloat());
            default -> throw new IllegalArgumentException("미지원 rule_test: " + t);
        };
    }

    private Processors.PosRuleTest parsePosTest(JsonObject o) {
        String t = stripNs(o.get("predicate_type").getAsString());
        return switch (t) {
            case "always_true" -> Processors.alwaysTruePos();
            case "linear_pos" -> Processors.linearPos(f(o, "min_chance", 0), f(o, "max_chance", 0), i(o, "min_dist", 0), i(o, "max_dist", 0));
            case "axis_aligned_linear_pos" -> Processors.axisAlignedLinearPos(f(o, "min_chance", 0), f(o, "max_chance", 0), i(o, "min_dist", 0), i(o, "max_dist", 0),
                o.has("axis") ? o.get("axis").getAsString() : "y");
            default -> throw new IllegalArgumentException("미지원 pos_rule_test: " + t);
        };
    }

    private Processors.BlockEntityModifier parseModifier(JsonObject o) {
        String t = stripNs(o.get("type").getAsString());
        return switch (t) {
            case "clear" -> Processors.clear();
            case "passthrough" -> Processors.passthrough();
            case "append_loot" -> Processors.appendLoot(o.get("loot_table").getAsString());
            case "append_static" -> Processors.appendStatic(new NbtCompound());   // data는 SNBT — 사용처 없음
            default -> throw new IllegalArgumentException("미지원 block_entity_modifier: " + t);
        };
    }

    // ------------------------------------------------------------------ 보조

    public int parseState(JsonObject o) {
        String name = ns(o.get("Name").getAsString());
        Map<String, String> props = new TreeMap<>();
        if (o.has("Properties")) for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("Properties").entrySet()) props.put(e.getKey(), e.getValue().getAsString());
        int id = registry.exactStateId(org.domi.furnace.server.world.BlockStateKey.of(name, props));
        return id >= 0 ? id : registry.defaultStateId(name);
    }

    private Set<String> blockSet(JsonElement e) {
        String s = e.getAsString();
        if (s.startsWith("#")) return new HashSet<>(BlockTags.get().blocksOf(s.substring(1)));
        return Set.of(ns(s));
    }

    private static String stripNs(String s) { return s.startsWith("minecraft:") ? s.substring("minecraft:".length()) : s; }

    private static String ns(String s) { return s.contains(":") ? s : "minecraft:" + s; }

    private static float f(JsonObject o, String k, float d) { return o.has(k) ? o.get(k).getAsFloat() : d; }

    private static int i(JsonObject o, String k, int d) { return o.has(k) ? o.get(k).getAsInt() : d; }
}
