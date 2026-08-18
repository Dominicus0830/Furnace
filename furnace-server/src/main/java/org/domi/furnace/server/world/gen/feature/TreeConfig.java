package org.domi.furnace.server.world.gen.feature;

import java.util.List;
import java.util.Set;
import org.domi.furnace.server.world.gen.feature.decorator.TreeDecorator;
import org.domi.furnace.server.world.gen.feature.foliage.FoliagePlacer;
import org.domi.furnace.server.world.gen.feature.root.MangroveRootPlacer;
import org.domi.furnace.server.world.gen.feature.size.FeatureSize;
import org.domi.furnace.server.world.gen.feature.trunk.TrunkPlacer;

public record TreeConfig(
    int trunkStateId,
    int dirtStateId,
    String leafBlockName,
    int[][] leafStateIds,
    TrunkPlacer trunkPlacer,
    FoliagePlacer foliagePlacer,
    MangroveRootPlacer rootPlacer, // mangrove 계열 전용 — 없으면 null
    FeatureSize minimumSize,
    List<TreeDecorator> decorators,
    boolean ignoreVines,
    boolean forceDirt,
    Set<Integer> dirtStates,
    Set<Integer> dirtReplaceExemptStates,
    Set<Integer> replaceableStates,
    Set<Integer> logStates,
    int[] cocoaStates,
    int[] vineStates,
    int beeNestStateId
) {
    public int leafStateId(int replacedStateId, int distance) {
        int water = TreeFeature.isWater(replacedStateId) ? 1 : 0;
        return leafStateIds[water][distance - 1];
    }

    public int cocoaStateId(TreeFeature.Direction facing, int age) {
        return cocoaStates[age * 4 + facing.horizontalIndex()];
    }

    public int vineStateId(TreeFeature.Direction facing) {
        return vineStates[facing.horizontalIndex()];
    }
}
