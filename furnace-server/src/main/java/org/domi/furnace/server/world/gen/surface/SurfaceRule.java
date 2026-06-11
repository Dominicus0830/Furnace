// surface_rule JSON 노드를 표현하는 봉인 인터페이스. overworld 표층 생성에 사용.
package org.domi.furnace.server.world.gen.surface;

import java.util.List;
import java.util.Set;

public sealed interface SurfaceRule {

    // ── Rule 타입 ─────────────────────────────────────────────────────────

    record Sequence(List<SurfaceRule> rules) implements SurfaceRule {}

    record IfTrue(SurfaceCondition condition, SurfaceRule then) implements SurfaceRule {}

    record Block(int blockStateId) implements SurfaceRule {}

    record Bandlands() implements SurfaceRule {}

    // ── Condition 타입 ────────────────────────────────────────────────────

    sealed interface SurfaceCondition {

        record VerticalGradient(
            String randomName,
            YOffset trueAtAndBelow,
            YOffset falseAtAndAbove
        ) implements SurfaceCondition {}

        record AbovePreliminarySurface() implements SurfaceCondition {}

        record StoneDepth(
            int offset,
            boolean addSurfaceDepth,
            int secondaryDepthRange,
            SurfaceType surfaceType
        ) implements SurfaceCondition {}

        record BiomeIs(Set<String> biomes) implements SurfaceCondition {}

        record YAbove(
            YOffset anchor,
            int surfaceDepthMultiplier,
            boolean addStoneDepth
        ) implements SurfaceCondition {}

        record Water(
            int offset,
            int surfaceDepthMultiplier,
            boolean addStoneDepth
        ) implements SurfaceCondition {}

        record NoiseThreshold(
            String noiseId,
            double minInclusive,
            double maxExclusive
        ) implements SurfaceCondition {}

        record Temperature() implements SurfaceCondition {}

        record Steep() implements SurfaceCondition {}

        record Not(SurfaceCondition inner) implements SurfaceCondition {}

        record Hole() implements SurfaceCondition {}
    }

    // ── YOffset 앵커 타입 ─────────────────────────────────────────────────

    sealed interface YOffset {
        record Absolute(int y) implements YOffset {}
        record AboveBottom(int offset) implements YOffset {}
        record BelowTop(int offset) implements YOffset {}
    }

    // ── SurfaceType ────────────────────────────────────────────────────────

    enum SurfaceType { FLOOR, CEILING }
}
