// surface_rule JSON을 SurfaceRule 봉인 인터페이스 트리로 파싱한다.
package org.domi.furnace.server.world.gen.surface;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.domi.furnace.server.world.BlockRegistry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SurfaceRuleParser {

    private final BlockRegistry reg;

    public SurfaceRuleParser(BlockRegistry reg) {
        this.reg = reg;
    }

    public SurfaceRule parse(JsonObject json) {
        String type = json.get("type").getAsString();
        return switch (type) {
            case "minecraft:sequence"  -> parseSequence(json);
            case "minecraft:condition" -> parseCondition(json);
            case "minecraft:block"     -> parseBlock(json);
            case "minecraft:bandlands" -> new SurfaceRule.Bandlands();
            default -> throw new IllegalArgumentException("Unknown surface rule type: " + type);
        };
    }

    private SurfaceRule.Sequence parseSequence(JsonObject json) {
        JsonArray arr = json.getAsJsonArray("sequence");
        List<SurfaceRule> rules = new ArrayList<>(arr.size());
        for (JsonElement el : arr) rules.add(parse(el.getAsJsonObject()));
        return new SurfaceRule.Sequence(rules);
    }

    private SurfaceRule.IfTrue parseCondition(JsonObject json) {
        SurfaceRule.SurfaceCondition cond = parseConditionNode(json.getAsJsonObject("if_true"));
        SurfaceRule then = parse(json.getAsJsonObject("then_run"));
        return new SurfaceRule.IfTrue(cond, then);
    }

    private SurfaceRule.Block parseBlock(JsonObject json) {
        String blockName = json.getAsJsonObject("result_state").get("Name").getAsString();
        int id = reg.defaultStateId(blockName);
        return new SurfaceRule.Block(id);
    }

    private SurfaceRule.SurfaceCondition parseConditionNode(JsonObject json) {
        String type = json.get("type").getAsString();
        return switch (type) {
            case "minecraft:vertical_gradient"         -> parseVerticalGradient(json);
            case "minecraft:above_preliminary_surface" -> new SurfaceRule.SurfaceCondition.AbovePreliminarySurface();
            case "minecraft:stone_depth"               -> parseStoneDepth(json);
            case "minecraft:biome"                     -> parseBiome(json);
            case "minecraft:y_above"                   -> parseYAbove(json);
            case "minecraft:water"                     -> parseWater(json);
            case "minecraft:noise_threshold"           -> parseNoiseThreshold(json);
            case "minecraft:temperature"               -> new SurfaceRule.SurfaceCondition.Temperature();
            case "minecraft:steep"                     -> new SurfaceRule.SurfaceCondition.Steep();
            case "minecraft:not"                       -> new SurfaceRule.SurfaceCondition.Not(
                                                              parseConditionNode(json.getAsJsonObject("invert")));
            case "minecraft:hole"                      -> new SurfaceRule.SurfaceCondition.Hole();
            default -> throw new IllegalArgumentException("Unknown condition type: " + type);
        };
    }

    private SurfaceRule.SurfaceCondition.VerticalGradient parseVerticalGradient(JsonObject json) {
        String name = json.get("random_name").getAsString();
        SurfaceRule.YOffset trueBelow  = parseYOffset(json.getAsJsonObject("true_at_and_below"));
        SurfaceRule.YOffset falseAbove = parseYOffset(json.getAsJsonObject("false_at_and_above"));
        return new SurfaceRule.SurfaceCondition.VerticalGradient(name, trueBelow, falseAbove);
    }

    private SurfaceRule.SurfaceCondition.StoneDepth parseStoneDepth(JsonObject json) {
        int offset           = json.get("offset").getAsInt();
        boolean addSurfDepth = json.get("add_surface_depth").getAsBoolean();
        int secondaryRange   = json.get("secondary_depth_range").getAsInt();
        String typeStr       = json.get("surface_type").getAsString();
        SurfaceRule.SurfaceType t = "ceiling".equals(typeStr)
            ? SurfaceRule.SurfaceType.CEILING
            : SurfaceRule.SurfaceType.FLOOR;
        return new SurfaceRule.SurfaceCondition.StoneDepth(offset, addSurfDepth, secondaryRange, t);
    }

    private SurfaceRule.SurfaceCondition.BiomeIs parseBiome(JsonObject json) {
        Set<String> biomes = new HashSet<>();
        for (JsonElement el : json.getAsJsonArray("biome_is")) {
            biomes.add(el.getAsString());
        }
        return new SurfaceRule.SurfaceCondition.BiomeIs(biomes);
    }

    private SurfaceRule.SurfaceCondition.YAbove parseYAbove(JsonObject json) {
        SurfaceRule.YOffset anchor = parseYOffset(json.getAsJsonObject("anchor"));
        int mult         = json.get("surface_depth_multiplier").getAsInt();
        boolean addStone = json.get("add_stone_depth").getAsBoolean();
        return new SurfaceRule.SurfaceCondition.YAbove(anchor, mult, addStone);
    }

    private SurfaceRule.SurfaceCondition.Water parseWater(JsonObject json) {
        int offset  = json.get("offset").getAsInt();
        int mult    = json.get("surface_depth_multiplier").getAsInt();
        boolean add = json.get("add_stone_depth").getAsBoolean();
        return new SurfaceRule.SurfaceCondition.Water(offset, mult, add);
    }

    private SurfaceRule.SurfaceCondition.NoiseThreshold parseNoiseThreshold(JsonObject json) {
        String noiseId = json.get("noise").getAsString();
        double min = json.get("min_threshold").getAsDouble();
        double max = json.get("max_threshold").getAsDouble();
        return new SurfaceRule.SurfaceCondition.NoiseThreshold(noiseId, min, max);
    }

    private SurfaceRule.YOffset parseYOffset(JsonObject json) {
        if (json.has("absolute"))     return new SurfaceRule.YOffset.Absolute(json.get("absolute").getAsInt());
        if (json.has("above_bottom")) return new SurfaceRule.YOffset.AboveBottom(json.get("above_bottom").getAsInt());
        if (json.has("below_top"))    return new SurfaceRule.YOffset.BelowTop(json.get("below_top").getAsInt());
        throw new IllegalArgumentException("Unknown YOffset format: " + json);
    }
}
