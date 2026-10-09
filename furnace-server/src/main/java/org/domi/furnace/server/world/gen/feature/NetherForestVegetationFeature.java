// nether_forest_vegetation — 바닐라 NetherForestVegetationFeature 포팅(니륨 위 뿌리·균류·새싹 흩뿌리기)
package org.domi.furnace.server.world.gen.feature;

import java.util.HashSet;
import java.util.Set;
import org.domi.furnace.server.world.BlockAttrs;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class NetherForestVegetationFeature implements ConfiguredFeature {

    private final StateProvider provider;
    private final int spreadWidth, spreadHeight;
    private final BlockSurvival survival;
    private final Set<Integer> nylium;
    private final BlockAttrs attrs = BlockAttrs.get();

    public NetherForestVegetationFeature(StateProvider provider, int spreadWidth, int spreadHeight, BlockRegistry registry, BlockSurvival survival) {
        this.provider = provider;
        this.spreadWidth = spreadWidth;
        this.spreadHeight = spreadHeight;
        this.survival = survival;
        HashSet<Integer> ids = new HashSet<>();
        for (String b : BlockTags.get().blocksOf("minecraft:nylium")) ids.addAll(registry.stateIdsForBlock(b));
        this.nylium = Set.copyOf(ids);
    }

    @Override
    public boolean place(FeatureWorldView v, WorldgenRandom random, int x, int y, int z) {
        if (!nylium.contains(v.getBlock(x, y - 1, z))) return false;
        if (y >= v.minBuildHeight() + 1 && y + 1 < v.maxBuildHeight()) {
            int placed = 0;
            for (int i = 0; i < spreadWidth * spreadWidth; i++) {
                // origin.offset(x: w-w, y: h-h, z: w-w) — 인자 평가 순서대로 난수 소비
                int px = x + random.nextInt(spreadWidth) - random.nextInt(spreadWidth);
                int py = y + random.nextInt(spreadHeight) - random.nextInt(spreadHeight);
                int pz = z + random.nextInt(spreadWidth) - random.nextInt(spreadWidth);
                int state = provider.getState(random, px, py, pz);
                if (attrs.isAir(v.getBlock(px, py, pz)) && py > v.minBuildHeight() && survival.canSurvive(v, state, px, py, pz)) {
                    v.setBlock(px, py, pz, state);
                    placed++;
                }
            }
            return placed > 0;
        }
        return false;
    }
}
