// bonus_chest — 바닐라 BonusChestFeature 이식: origin 청크의 열을 섞은 순서로 훑어 첫 빈 자리에 상자(보너스 전리품)와 횃불을 놓는다.
package org.domi.furnace.server.world.gen.feature;

import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.world.BlockAttrs;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.block.BlockShapes;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class BonusChestFeature implements ConfiguredFeature {

    private static final String LOOT_TABLE = "minecraft:chests/spawn_bonus_chest";
    // Direction.Plane.HORIZONTAL 순서: NORTH, EAST, SOUTH, WEST
    private static final int[] DX = {0, 1, 0, -1};
    private static final int[] DZ = {-1, 0, 1, 0};

    private final int chest;
    private final int torch;
    private final BlockShapes shapes;

    public BonusChestFeature(BlockRegistry registry) {
        this.chest = registry.defaultStateId("minecraft:chest");
        this.torch = registry.defaultStateId("minecraft:torch");
        this.shapes = BlockShapes.of(registry);
    }

    /** Util.toShuffledList(IntStream.rangeClosed(min, max), random) — 뒤에서부터 교환. */
    static int[] shuffledRange(int min, int max, WorldgenRandom random) {
        int[] a = new int[max - min + 1];
        for (int i = 0; i < a.length; i++) a[i] = min + i;
        for (int i = a.length; i > 1; i--) {
            int j = random.nextInt(i);
            int t = a[i - 1];
            a[i - 1] = a[j];
            a[j] = t;
        }
        return a;
    }

    @Override
    public boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        int minX = (x >> 4) << 4, minZ = (z >> 4) << 4;
        int[] xs = shuffledRange(minX, minX + 15, random);
        int[] zs = shuffledRange(minZ, minZ + 15, random);
        BlockAttrs a = BlockAttrs.get();
        for (int bx : xs) {
            for (int bz : zs) {
                // getHeightmapPos(MOTION_BLOCKING_NO_LEAVES, (bx, 0, bz)): 맨 위 점유 블록 바로 위 칸
                int py = view.getMotionBlockingNoLeavesY(bx, bz) + 1;
                int cur = view.getBlock(bx, py, bz);
                if (a.isAir(cur) || a.collisionEmpty(cur)) {
                    view.setBlock(bx, py, bz, chest);
                    // RandomizableContainer.setBlockEntityLootTable: 표 + random.nextLong() 시드
                    NbtCompound nbt = new NbtCompound();
                    nbt.putString("id", "minecraft:chest");
                    nbt.putInt("x", bx); nbt.putInt("y", py); nbt.putInt("z", bz);
                    nbt.putString("LootTable", LOOT_TABLE);
                    nbt.putLong("LootTableSeed", random.nextLong());
                    view.putGeneratedBlockEntity(bx, py, bz, nbt);
                    for (int d = 0; d < 4; d++) {
                        int tx = bx + DX[d], tz = bz + DZ[d];
                        if (shapes.canSurvive(view, torch, tx, py, tz)) view.setBlock(tx, py, tz, torch);
                    }
                    return true;
                }
            }
        }
        return false;
    }
}
