// vanilla minecraft:disk feature — radius IntProvider 샘플 + rule 기반 state provider (byte-exact)
package org.domi.furnace.server.world.gen.feature;

import java.util.List;
import java.util.Set;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class DiskFeature implements ConfiguredFeature {

    /** rule: below(offset 0,-1,0)가 matchStateIds에 속하면 thenStateId, 아니면 다음 rule/fallback. */
    public record Rule(Set<Integer> matchStateIds, int thenStateId) {}

    private final int fallbackStateId;
    private final List<Rule> rules;
    private final TreeIntProvider radius;
    private final int halfHeight;
    private final Set<Integer> targetStateIds;

    public DiskFeature(int fallbackStateId, List<Rule> rules, TreeIntProvider radius, int halfHeight, Set<Integer> targetStateIds) {
        this.fallbackStateId = fallbackStateId;
        this.rules = List.copyOf(rules);
        this.radius = radius;
        this.halfHeight = halfHeight;
        this.targetStateIds = Set.copyOf(targetStateIds);
    }

    @Override
    public boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        boolean placed = false;
        int maxY = y + halfHeight;
        int minY = y - halfHeight - 1;
        int r = radius.sample(random);
        int radiusSquared = r * r;
        // vanilla BlockPos.betweenClosed 순서: x 바깥, z 안쪽 (y span=0)
        for (int bx = x - r; bx <= x + r; bx++) {
            for (int bz = z - r; bz <= z + r; bz++) {
                int dx = bx - x;
                int dz = bz - z;
                if (dx * dx + dz * dz > radiusSquared) continue;
                for (int by = maxY; by > minY; by--) {
                    if (!targetStateIds.contains(view.getBlock(bx, by, bz))) continue;
                    view.setBlock(bx, by, bz, stateFor(view, bx, by, bz));
                    placed = true;
                }
            }
        }
        return placed;
    }

    private int stateFor(FeatureWorldView view, int bx, int by, int bz) {
        for (Rule rule : rules) {
            if (rule.matchStateIds().contains(view.getBlock(bx, by - 1, bz))) {
                return rule.thenStateId();
            }
        }
        return fallbackStateId;
    }
}
