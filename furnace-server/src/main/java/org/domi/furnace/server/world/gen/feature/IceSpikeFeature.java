// ice_spike — 바닐라 IceSpikeFeature 포팅 (눈 블록 위에 packed_ice 뾰족탑 + 아래로 뿌리)
package org.domi.furnace.server.world.gen.feature;

import java.util.Set;
import org.domi.furnace.server.world.BlockAttrs;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class IceSpikeFeature implements ConfiguredFeature {

    private final BlockAttrs attrs = BlockAttrs.get();
    private final Set<Integer> dirt;
    private final int snowBlock;
    private final int ice;
    private final int packedIce;

    public IceSpikeFeature(BlockRegistry registry) {
        java.util.HashSet<Integer> ids = new java.util.HashSet<>();
        for (String b : BlockTags.get().blocksOf("minecraft:dirt")) ids.addAll(registry.stateIdsForBlock(b));
        this.dirt = Set.copyOf(ids);
        this.snowBlock = registry.defaultStateId("minecraft:snow_block");
        this.ice = registry.defaultStateId("minecraft:ice");
        this.packedIce = registry.defaultStateId("minecraft:packed_ice");
    }

    private boolean replaceable(int state) {
        return attrs.isAir(state) || dirt.contains(state) || state == snowBlock || state == ice;
    }

    @Override
    public boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        while (attrs.isAir(view.getBlock(x, y, z)) && y > ChunkColumn.MIN_Y + 2) {
            y--;
        }
        if (view.getBlock(x, y, z) != snowBlock) return false;
        y += random.nextInt(4);
        int height = random.nextInt(4) + 7;
        int width = height / 4 + random.nextInt(2);
        if (width > 1 && random.nextInt(60) == 0) {
            y += 10 + random.nextInt(30);
        }

        for (int dy = 0; dy < height; dy++) {
            float radius = (1.0F - (float) dy / (float) height) * (float) width;
            int r = ceil(radius);
            for (int dx = -r; dx <= r; dx++) {
                float fx = (float) Math.abs(dx) - 0.25F;
                for (int dz = -r; dz <= r; dz++) {
                    float fz = (float) Math.abs(dz) - 0.25F;
                    if ((dx == 0 && dz == 0 || !(fx * fx + fz * fz > radius * radius))
                        && (dx != -r && dx != r && dz != -r && dz != r || !(random.nextFloat() > 0.75F))) {
                        int s = view.getBlock(x + dx, y + dy, z + dz);
                        if (replaceable(s)) view.setBlock(x + dx, y + dy, z + dz, packedIce);
                        if (dy != 0 && r > 1) {
                            s = view.getBlock(x + dx, y - dy, z + dz);
                            if (replaceable(s)) view.setBlock(x + dx, y - dy, z + dz, packedIce);
                        }
                    }
                }
            }
        }

        int base = width - 1;
        if (base < 0) base = 0;
        else if (base > 1) base = 1;

        for (int dx = -base; dx <= base; dx++) {
            for (int dz = -base; dz <= base; dz++) {
                int py = y - 1;
                int budget = 50;
                if (Math.abs(dx) == 1 && Math.abs(dz) == 1) budget = random.nextInt(5);
                while (py > 50) {
                    int s = view.getBlock(x + dx, py, z + dz);
                    if (!replaceable(s) && s != packedIce) break;
                    view.setBlock(x + dx, py, z + dz, packedIce);
                    py--;
                    if (--budget <= 0) {
                        py -= random.nextInt(5) + 1;
                        budget = random.nextInt(5);
                    }
                }
            }
        }
        return true;
    }

    private static int ceil(float f) {
        int i = (int) f;
        return f > (float) i ? i + 1 : i;
    }
}
