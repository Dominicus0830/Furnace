// block_pile — 바닐라 BlockPileFeature 포팅(마을 건초·호박·수박·눈·얼음 더미).
package org.domi.furnace.server.world.gen.feature;

import org.domi.furnace.server.world.BlockAttrs;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class BlockPileFeature implements ConfiguredFeature {

    private final StateProvider provider;
    private final int dirtPath;
    private final BlockAttrs attrs = BlockAttrs.get();

    public BlockPileFeature(StateProvider provider, BlockRegistry registry) {
        this.provider = provider;
        this.dirtPath = attrs.blockIndex(registry.defaultStateId("minecraft:dirt_path"));
    }

    @Override
    public boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        if (y < view.minY() + 5) return false;
        int i = 2 + random.nextInt(2);
        int j = 2 + random.nextInt(2);
        // BlockPos.betweenClosed(pos-(i,0,j), pos+(i,1,j)): x 가장 빠르게, 그다음 y, z
        for (int pz = z - j; pz <= z + j; pz++) {
            for (int py = y; py <= y + 1; py++) {
                for (int px = x - i; px <= x + i; px++) {
                    int k = x - px, l = z - pz;
                    if ((float) (k * k + l * l) <= random.nextFloat() * 10.0F - random.nextFloat() * 6.0F) {
                        tryPlace(view, px, py, pz, random);
                    } else if ((double) random.nextFloat() < 0.031) {
                        tryPlace(view, px, py, pz, random);
                    }
                }
            }
        }
        return true;
    }

    private void tryPlace(FeatureWorldView view, int x, int y, int z, WorldgenRandom random) {
        if (!attrs.isAir(view.getBlock(x, y, z))) return;
        int below = view.getBlock(x, y - 1, z);
        boolean ok = attrs.blockIndex(below) == dirtPath ? random.nextBoolean() : attrs.isFaceSturdy(below, BlockAttrs.UP);
        if (ok) view.setBlock(x, y, z, provider.getState(random, x, y, z));
    }

    /** rotated_block_provider: Direction.Axis.getRandom(random) 으로 axis 설정. */
    public record RotatedBlockProvider(int x, int y, int z) implements StateProvider {
        @Override
        public int getState(WorldgenRandom random, int px, int py, int pz) {
            return switch (random.nextInt(3)) {
                case 0 -> x;
                case 1 -> y;
                default -> z;
            };
        }
    }
}
