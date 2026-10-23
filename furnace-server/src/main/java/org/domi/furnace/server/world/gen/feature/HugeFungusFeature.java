// huge_fungus — 바닐라 HugeFungusFeature 포팅(줄기 → 모자 → 장식·늘어진 덩굴). planted=true 경로(묘목 성장)도 같은 코드로 따른다.
package org.domi.furnace.server.world.gen.feature;

import org.domi.furnace.server.world.BlockAttrs;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class HugeFungusFeature implements ConfiguredFeature {

    private static final float HUGE_PROBABILITY = 0.06F;

    private final int validBaseBlock, stemState, hatState, decorState;
    private final BlockPredicate replaceableBlocks;
    private final boolean planted;
    private final int genDepth;   // ChunkGenerator.getGenDepth() — 네더 128
    private final NetherVinesFeatures.Blocks vines;
    private final BlockAttrs attrs = BlockAttrs.get();
    private final int air, hatBlock, wartBlockBlock;
    private final boolean stemPost, hatPost, decorPost;

    public HugeFungusFeature(int validBaseBlockState, int stemState, int hatState, int decorState, BlockPredicate replaceableBlocks,
                             boolean planted, int genDepth, BlockRegistry registry) {
        this.genDepth = genDepth;
        this.validBaseBlock = attrs.blockIndex(validBaseBlockState);
        this.stemState = stemState;
        this.hatState = hatState;
        this.decorState = decorState;
        this.replaceableBlocks = replaceableBlocks;
        this.planted = planted;
        this.vines = new NetherVinesFeatures.Blocks(registry);
        this.air = registry.stateIdOrAir("minecraft:air");
        this.hatBlock = attrs.blockIndex(hatState);
        this.stemPost = BlockStates.hasPostProcess(registry, stemState);
        this.hatPost = BlockStates.hasPostProcess(registry, hatState);
        this.decorPost = BlockStates.hasPostProcess(registry, decorState);
        this.wartBlockBlock = attrs.blockIndex(registry.defaultStateId("minecraft:nether_wart_block"));
    }

    @Override
    public boolean place(FeatureWorldView v, WorldgenRandom random, int x, int y, int z) {
        // 바닥 블록이 valid_base_block 이어야 한다(아니면 pos=null → 실패)
        if (attrs.blockIndex(v.getBlock(x, y - 1, z)) != validBaseBlock) return false;
        int height = NetherVinesFeatures.nextIntBetween(random, 4, 13);
        if (random.nextInt(12) == 0) height *= 2;
        if (!planted) {
            if (y + height + 1 >= genDepth) return false;
        }
        boolean big = !planted && random.nextFloat() < HUGE_PROBABILITY;
        v.setBlock(x, y, z, air);
        placeStem(v, random, x, y, z, height, big);
        placeHat(v, random, x, y, z, height, big);
        return true;
    }

    /** HugeFungusFeature.isReplaceable — canBeReplaced 이거나(replacePlants 일 때) replaceable_blocks 에 속함. */
    private boolean isReplaceable(FeatureWorldView v, int x, int y, int z, boolean replacePlants) {
        if (attrs.isReplaceable(v.getBlock(x, y, z))) return true;
        return replacePlants && replaceableBlocks.test(v, x, y, z);
    }

    /** WorldGenRegion.setBlock — hasPostProcess 상태면 후처리 표시도 남긴다. */
    private static void setBlock(FeatureWorldView v, int x, int y, int z, int state, boolean post) {
        v.setBlock(x, y, z, state);
        if (post) v.markPosForPostprocessing(x, y, z);
    }

    /** level.destroyBlock(pos, true) — 월드젠에서는 드롭 없이 공기(유체 없음)로 바뀐다. planted 경로 전용. */
    private void destroyBlock(FeatureWorldView v, int x, int y, int z) {
        if (!attrs.isAir(v.getBlock(x, y, z))) v.setBlock(x, y, z, air);
    }

    private void placeStem(FeatureWorldView v, WorldgenRandom random, int ox, int oy, int oz, int height, boolean big) {
        int i = big ? 1 : 0;
        for (int j = -i; j <= i; j++) {
            for (int k = -i; k <= i; k++) {
                boolean corner = big && Math.abs(j) == i && Math.abs(k) == i;
                for (int l = 0; l < height; l++) {
                    int px = ox + j, py = oy + l, pz = oz + k;
                    if (isReplaceable(v, px, py, pz, true)) {
                        if (planted) {
                            if (!attrs.isAir(v.getBlock(px, py - 1, pz))) destroyBlock(v, px, py, pz);
                            setBlock(v, px, py, pz, stemState, stemPost);
                        } else if (corner) {
                            if (random.nextFloat() < 0.1F) setBlock(v, px, py, pz, stemState, stemPost);
                        } else {
                            setBlock(v, px, py, pz, stemState, stemPost);
                        }
                    }
                }
            }
        }
    }

    private void placeHat(FeatureWorldView v, WorldgenRandom random, int ox, int oy, int oz, int height, boolean big) {
        boolean isWart = hatBlock == wartBlockBlock;
        int i = Math.min(random.nextInt(1 + height / 3) + 5, height);
        int j = height - i;
        for (int k = j; k <= height; k++) {
            int l = k < height - random.nextInt(3) ? 2 : 1;
            if (i > 8 && k < j + 4) l = 3;
            if (big) l++;
            for (int i1 = -l; i1 <= l; i1++) {
                for (int j1 = -l; j1 <= l; j1++) {
                    boolean xEdge = i1 == -l || i1 == l;
                    boolean zEdge = j1 == -l || j1 == l;
                    boolean inner = !xEdge && !zEdge && k != height;
                    boolean corner = xEdge && zEdge;
                    boolean lower = k < j + 3;
                    int px = ox + i1, py = oy + k, pz = oz + j1;
                    if (isReplaceable(v, px, py, pz, false)) {
                        if (planted && !attrs.isAir(v.getBlock(px, py - 1, pz))) destroyBlock(v, px, py, pz);
                        if (lower) {
                            if (!inner) placeHatDropBlock(v, random, px, py, pz, isWart);
                        } else if (inner) {
                            placeHatBlock(v, random, px, py, pz, 0.1F, 0.2F, isWart ? 0.1F : 0.0F);
                        } else if (corner) {
                            placeHatBlock(v, random, px, py, pz, 0.01F, 0.7F, isWart ? 0.083F : 0.0F);
                        } else {
                            placeHatBlock(v, random, px, py, pz, 5.0E-4F, 0.98F, isWart ? 0.07F : 0.0F);
                        }
                    }
                }
            }
        }
    }

    private void placeHatBlock(FeatureWorldView v, WorldgenRandom random, int x, int y, int z,
                               float decorBlockProbability, float hatBlockProbability, float vinesProbability) {
        if (random.nextFloat() < decorBlockProbability) {
            setBlock(v, x, y, z, decorState, decorPost);
        } else if (random.nextFloat() < hatBlockProbability) {
            setBlock(v, x, y, z, hatState, hatPost);
            if (random.nextFloat() < vinesProbability) tryPlaceWeepingVines(v, random, x, y, z);
        }
    }

    private void placeHatDropBlock(FeatureWorldView v, WorldgenRandom random, int x, int y, int z, boolean isWart) {
        if (attrs.blockIndex(v.getBlock(x, y - 1, z)) == hatBlock) {
            setBlock(v, x, y, z, hatState, hatPost);
        } else if ((double) random.nextFloat() < 0.15) {
            setBlock(v, x, y, z, hatState, hatPost);
            if (isWart && random.nextInt(11) == 0) tryPlaceWeepingVines(v, random, x, y, z);
        }
    }

    private void tryPlaceWeepingVines(FeatureWorldView v, WorldgenRandom random, int x, int y, int z) {
        int by = y - 1;
        if (attrs.isAir(v.getBlock(x, by, z))) {
            int i = NetherVinesFeatures.nextIntBetween(random, 1, 5);
            if (random.nextInt(7) == 0) i *= 2;
            NetherVinesFeatures.placeWeepingColumn(vines, v, random, x, by, z, i, 23, 25);
        }
    }
}
