// bamboo — 바닐라 BambooFeature 포팅(줄기 5~16칸, 확률적으로 podzol 원판, 꼭대기 잎 3칸)
package org.domi.furnace.server.world.gen.feature;

import java.util.Map;
import java.util.Set;
import org.domi.furnace.server.world.BlockAttrs;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class BambooFeature implements ConfiguredFeature {

    private final float probability;
    private final BlockAttrs attrs = BlockAttrs.get();
    private final Set<Integer> bambooPlantableOn;
    private final Set<Integer> dirt;
    private final int stalk;        // age=1, leaves=none, stage=0
    private final int topLarge;     // leaves=large, stage=1
    private final int midLarge;     // leaves=large
    private final int midSmall;     // leaves=small
    private final int podzol;

    public BambooFeature(float probability, BlockRegistry registry) {
        this.probability = probability;
        this.bambooPlantableOn = tag(registry, "minecraft:bamboo_plantable_on");
        this.dirt = tag(registry, "minecraft:dirt");
        this.stalk = registry.stateIdOrAir("minecraft:bamboo", Map.of("age", "1", "leaves", "none", "stage", "0"));
        this.topLarge = registry.stateIdOrAir("minecraft:bamboo", Map.of("age", "1", "leaves", "large", "stage", "1"));
        this.midLarge = registry.stateIdOrAir("minecraft:bamboo", Map.of("age", "1", "leaves", "large", "stage", "0"));
        this.midSmall = registry.stateIdOrAir("minecraft:bamboo", Map.of("age", "1", "leaves", "small", "stage", "0"));
        this.podzol = registry.stateIdOrAir("minecraft:podzol", Map.of("snowy", "false"));
    }

    private static Set<Integer> tag(BlockRegistry registry, String name) {
        java.util.HashSet<Integer> ids = new java.util.HashSet<>();
        for (String b : BlockTags.get().blocksOf(name)) ids.addAll(registry.stateIdsForBlock(b));
        return Set.copyOf(ids);
    }

    @Override
    public boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        int placed = 0;
        int py = y;
        if (!attrs.isAir(view.getBlock(x, py, z))) return false;
        if (bambooPlantableOn.contains(view.getBlock(x, py - 1, z))) {       // BambooStalkBlock.canSurvive
            int height = random.nextInt(12) + 5;
            if (random.nextFloat() < probability) {
                int r = random.nextInt(4) + 1;
                for (int bx = x - r; bx <= x + r; bx++) {
                    for (int bz = z - r; bz <= z + r; bz++) {
                        int dx = bx - x, dz = bz - z;
                        if (dx * dx + dz * dz <= r * r) {
                            int sy = view.getSurfaceY(bx, bz);               // getHeight(WORLD_SURFACE) - 1
                            if (dirt.contains(view.getBlock(bx, sy, bz))) view.setBlock(bx, sy, bz, podzol);
                        }
                    }
                }
            }
            for (int i = 0; i < height && attrs.isAir(view.getBlock(x, py, z)); i++) {
                view.setBlock(x, py, z, stalk);
                py++;
            }
            if (py - y >= 3) {
                view.setBlock(x, py, z, topLarge);
                view.setBlock(x, py - 1, z, midLarge);
                view.setBlock(x, py - 2, z, midSmall);
            }
        }
        placed++;
        return placed > 0;
    }
}
