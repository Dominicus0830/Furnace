// SurfaceRuleParser 단위 테스트 — overworld surface_rule JSON 파싱 검증
package org.domi.furnace.server.world.gen.surface;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.domi.furnace.server.world.gen.WorldgenData;
import org.domi.furnace.server.world.BlockRegistry;

class SurfaceRuleParserTest {

    @Test
    void parsesOverworldSurfaceRule() {
        WorldgenData data = WorldgenData.load("worldgen/1.21.1");
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        SurfaceRuleParser parser = new SurfaceRuleParser(reg);
        var noiseSettingsJson = data.noiseSettings("minecraft:overworld");
        SurfaceRule rule = parser.parse(noiseSettingsJson.get("surface_rule").getAsJsonObject());
        assertInstanceOf(SurfaceRule.Sequence.class, rule);
        var seq = (SurfaceRule.Sequence) rule;
        assertFalse(seq.rules().isEmpty());
    }

    @Test
    void parsesBedrockCondition() {
        WorldgenData data = WorldgenData.load("worldgen/1.21.1");
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        var parser = new SurfaceRuleParser(reg);
        var noiseSettingsJson = data.noiseSettings("minecraft:overworld");
        SurfaceRule rule = parser.parse(noiseSettingsJson.get("surface_rule").getAsJsonObject());
        var seq = (SurfaceRule.Sequence) rule;
        var first = (SurfaceRule.IfTrue) seq.rules().get(0);
        assertInstanceOf(SurfaceRule.SurfaceCondition.VerticalGradient.class, first.condition());
    }
}
