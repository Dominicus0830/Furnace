// lake — 바닐라 LakeFeature(minecraft:lake, 1.21.1에서는 용암 호수만 사용) 포팅. RNG 소비 순서까지 1:1.
// 상태 제공자는 둘 다 simple_state_provider라 RNG를 쓰지 않는다. 바닐라가 쓰는 스케줄 틱·후처리 표시(markAboveForPostProcessing)는
// 월드젠 산출 블록에 영향이 없어 생략한다.
package org.domi.furnace.server.world.gen.feature;

import java.util.HashSet;
import java.util.Set;
import org.domi.furnace.server.world.BlockAttrs;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class LakeFeature implements ConfiguredFeature {

    private final BlockAttrs attrs = BlockAttrs.get();
    private final int fluid;
    private final int barrier;
    private final int caveAir;
    private final Set<Integer> featuresCannotReplace;
    private final Set<Integer> lavaPoolStoneCannotReplace;

    public LakeFeature(int fluid, int barrier, BlockRegistry registry) {
        this.fluid = fluid;
        this.barrier = barrier;
        this.caveAir = registry.defaultStateId("minecraft:cave_air");
        this.featuresCannotReplace = tagStates(registry, "minecraft:features_cannot_replace");
        this.lavaPoolStoneCannotReplace = tagStates(registry, "minecraft:lava_pool_stone_cannot_replace");
    }

    private static Set<Integer> tagStates(BlockRegistry registry, String tag) {
        HashSet<Integer> ids = new HashSet<>();
        for (String b : BlockTags.get().blocksOf(tag)) ids.addAll(registry.stateIdsForBlock(b));
        return Set.copyOf(ids);
    }

    @Override
    public boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        if (y <= view.minY() + 4) return false;
        y -= 4;
        boolean[] mask = new boolean[2048];
        int blobs = random.nextInt(4) + 4;

        for (int i = 0; i < blobs; i++) {
            double sx = random.nextDouble() * 6.0 + 3.0;
            double sy = random.nextDouble() * 4.0 + 2.0;
            double sz = random.nextDouble() * 6.0 + 3.0;
            double cx = random.nextDouble() * (16.0 - sx - 2.0) + 1.0 + sx / 2.0;
            double cy = random.nextDouble() * (8.0 - sy - 4.0) + 2.0 + sy / 2.0;
            double cz = random.nextDouble() * (16.0 - sz - 2.0) + 1.0 + sz / 2.0;
            for (int dx = 1; dx < 15; dx++) {
                for (int dz = 1; dz < 15; dz++) {
                    for (int dy = 1; dy < 7; dy++) {
                        double nx = ((double) dx - cx) / (sx / 2.0);
                        double ny = ((double) dy - cy) / (sy / 2.0);
                        double nz = ((double) dz - cz) / (sz / 2.0);
                        if (nx * nx + ny * ny + nz * nz < 1.0) {
                            mask[(dx * 16 + dz) * 8 + dy] = true;
                        }
                    }
                }
            }
        }

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                for (int dy = 0; dy < 8; dy++) {
                    if (isBoundary(mask, dx, dz, dy)) {
                        int state = view.getBlock(x + dx, y + dy, z + dz);
                        if (dy >= 4 && attrs.isLiquid(state)) return false;
                        if (dy < 4 && !attrs.isSolid(state) && state != fluid) return false;
                    }
                }
            }
        }

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                for (int dy = 0; dy < 8; dy++) {
                    if (mask[(dx * 16 + dz) * 8 + dy]) {
                        int state = view.getBlock(x + dx, y + dy, z + dz);
                        if (!featuresCannotReplace.contains(state)) {
                            view.setBlock(x + dx, y + dy, z + dz, dy >= 4 ? caveAir : fluid);
                        }
                    }
                }
            }
        }

        if (!attrs.isAir(barrier)) {
            for (int dx = 0; dx < 16; dx++) {
                for (int dz = 0; dz < 16; dz++) {
                    for (int dy = 0; dy < 8; dy++) {
                        if (isBoundary(mask, dx, dz, dy) && (dy < 4 || random.nextInt(2) != 0)) {
                            int state = view.getBlock(x + dx, y + dy, z + dz);
                            if (attrs.isSolid(state) && !lavaPoolStoneCannotReplace.contains(state)) {
                                view.setBlock(x + dx, y + dy, z + dz, barrier);
                            }
                        }
                    }
                }
            }
        }
        return true;
    }

    /** 마스크 밖이면서 6방향 이웃 중 하나가 마스크 안인 칸. */
    private static boolean isBoundary(boolean[] m, int dx, int dz, int dy) {
        return !m[(dx * 16 + dz) * 8 + dy]
            && (dx < 15 && m[((dx + 1) * 16 + dz) * 8 + dy]
                || dx > 0 && m[((dx - 1) * 16 + dz) * 8 + dy]
                || dz < 15 && m[(dx * 16 + dz + 1) * 8 + dy]
                || dz > 0 && m[(dx * 16 + (dz - 1)) * 8 + dy]
                || dy < 7 && m[(dx * 16 + dz) * 8 + dy + 1]
                || dy > 0 && m[(dx * 16 + dz) * 8 + (dy - 1)]);
    }
}
