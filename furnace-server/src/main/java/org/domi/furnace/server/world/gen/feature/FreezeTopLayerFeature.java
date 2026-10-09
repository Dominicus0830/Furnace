// freeze_top_layer — 바닐라 SnowAndFreezeFeature: 청크 16x16 각 열의 MOTION_BLOCKING 최상단에서 물 동결 + 눈 층 설치
package org.domi.furnace.server.world.gen.feature;

import org.domi.furnace.server.world.BlockAttrs;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.BiomeClimate;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class FreezeTopLayerFeature implements ConfiguredFeature {

    private final BlockRegistry registry;
    private final BlockAttrs attrs = BlockAttrs.get();
    private final int waterId;
    private final int iceId;
    private final int snowId;
    private final float[] biomeTemps;     // biome id(registry 순번)로 인덱싱한 base temperature
    private final boolean[] biomeFrozen;  // temperature_modifier == frozen
    private final java.util.Set<Integer> cannotSurviveOn;   // #snow_layer_cannot_survive_on
    private final java.util.Set<Integer> canSurviveOn;      // #snow_layer_can_survive_on
    private final java.util.Set<Integer> snowLayerStates;

    public FreezeTopLayerFeature(BlockRegistry registry, float[] biomeTemps, boolean[] biomeFrozen) {
        this.registry = registry;
        this.waterId = registry.defaultStateId("minecraft:water");
        this.iceId = registry.defaultStateId("minecraft:ice");
        this.snowId = registry.defaultStateId("minecraft:snow");
        this.biomeTemps = biomeTemps;
        this.biomeFrozen = biomeFrozen;
        this.cannotSurviveOn = statesOfTag(registry, "minecraft:snow_layer_cannot_survive_on");
        this.canSurviveOn = statesOfTag(registry, "minecraft:snow_layer_can_survive_on");
        this.snowLayerStates = registry.stateIdsForBlock("minecraft:snow");
    }

    private static java.util.Set<Integer> statesOfTag(BlockRegistry registry, String tag) {
        java.util.HashSet<Integer> ids = new java.util.HashSet<>();
        for (String b : BlockTags.get().blocksOf(tag)) ids.addAll(registry.stateIdsForBlock(b));
        return java.util.Set.copyOf(ids);
    }

    @Override
    public boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        for (int dz = 0; dz < 16; dz++) {
            for (int dx = 0; dx < 16; dx++) {
                int wx = x + dx;
                int wz = z + dz;
                int belowY = view.getMotionBlockingY(wx, wz);
                int topY = belowY + 1;                                   // 바닐라 getHeight(MOTION_BLOCKING) = 최상단 블록 y + 1
                int biomeId = view.getBlendedBiomeId(wx, topY, wz);
                float base = biomeId >= 0 && biomeId < biomeTemps.length ? biomeTemps[biomeId] : 0.5f;
                boolean frozen = biomeId >= 0 && biomeId < biomeFrozen.length && biomeFrozen[biomeId];

                // Biome.shouldFreeze(level, below, false): 따뜻하지 않고, 블록광 < 10(worldgen 중 0), 물 source 블록
                if (inRange(view, belowY) && !warm(base, frozen, wx, belowY, wz)
                        && view.getBlock(wx, belowY, wz) == waterId) {
                    view.setBlock(wx, belowY, wz, iceId);
                }

                // Biome.shouldSnow(level, top)
                if (inRange(view, topY) && !warm(base, frozen, wx, topY, wz)) {
                    int state = view.getBlock(wx, topY, wz);
                    if ((attrs.isAir(state) || snowLayerStates.contains(state)) && snowCanSurvive(view, wx, topY, wz)) {
                        view.setBlock(wx, topY, wz, snowId);
                        int below = view.getBlock(wx, belowY, wz);
                        int snowy = BlockStates.trySetValue(registry, below, "snowy", "true");
                        if (snowy >= 0) view.setBlock(wx, belowY, wz, snowy);
                    }
                }
            }
        }
        return true;
    }

    private static boolean inRange(FeatureWorldView view, int y) {
        return y >= view.minY() && y <= view.maxY();
    }

    private static boolean warm(float base, boolean frozen, int x, int y, int z) {
        return BiomeClimate.temperature(base, frozen, x, y, z) >= 0.15F;
    }

    /** SnowLayerBlock.canSurvive */
    private boolean snowCanSurvive(FeatureWorldView view, int x, int y, int z) {
        int below = view.getBlock(x, y - 1, z);
        if (cannotSurviveOn.contains(below)) return false;
        if (canSurviveOn.contains(below)) return true;
        return attrs.isCollisionFaceFull(below, BlockAttrs.UP)
            || (snowLayerStates.contains(below) && BlockStates.hasValue(registry, below, "layers", "8"));
    }
}
