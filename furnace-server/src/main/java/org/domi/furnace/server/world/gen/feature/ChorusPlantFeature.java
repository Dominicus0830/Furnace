// chorus_plant — 바닐라 ChorusPlantFeature + ChorusFlowerBlock.generatePlant/growTreeRecursive + ChorusPlantBlock.getStateWithConnections 이식
package org.domi.furnace.server.world.gen.feature;

import java.util.HashMap;
import java.util.Map;
import org.domi.furnace.server.world.BlockAttrs;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class ChorusPlantFeature implements ConfiguredFeature {

    // Direction.Plane.HORIZONTAL 순서(NORTH, EAST, SOUTH, WEST) — getRandomDirection 이 nextInt(4) 로 이 배열을 고른다.
    private static final int[] HDX = {0, 1, 0, -1};
    private static final int[] HDZ = {-1, 0, 1, 0};

    private final BlockAttrs attrs = BlockAttrs.get();
    private final boolean[] isPlant, isFlower, isEndStone;
    /** getStateWithConnections 결과 표 — 인덱스 비트: 0 down, 1 up, 2 north, 3 east, 4 south, 5 west. */
    private final int[] plantByConnections = new int[64];
    private final int flowerAge5;

    public ChorusPlantFeature(BlockRegistry registry) {
        int n = registry.totalStateCount();
        this.isPlant = flags(registry, n, "minecraft:chorus_plant");
        this.isFlower = flags(registry, n, "minecraft:chorus_flower");
        this.isEndStone = flags(registry, n, "minecraft:end_stone");
        for (int bits = 0; bits < 64; bits++) {
            Map<String, String> p = new HashMap<>();
            p.put("down", String.valueOf((bits & 1) != 0));
            p.put("up", String.valueOf((bits & 2) != 0));
            p.put("north", String.valueOf((bits & 4) != 0));
            p.put("east", String.valueOf((bits & 8) != 0));
            p.put("south", String.valueOf((bits & 16) != 0));
            p.put("west", String.valueOf((bits & 32) != 0));
            int id = registry.stateIdOrAir("minecraft:chorus_plant", p);
            if (id == registry.stateIdOrAir("minecraft:air") || !isPlant[id])
                throw new IllegalStateException("chorus_plant 상태를 찾지 못함: " + p);
            plantByConnections[bits] = id;
        }
        Map<String, String> flower = new HashMap<>();
        flower.put("age", "5");
        this.flowerAge5 = registry.stateIdOrAir("minecraft:chorus_flower", flower);
        if (!isFlower[flowerAge5]) throw new IllegalStateException("chorus_flower[age=5] 상태를 찾지 못함");
    }

    private static boolean[] flags(BlockRegistry registry, int n, String block) {
        boolean[] out = new boolean[n];
        for (int id : registry.stateIdsForBlock(block)) out[id] = true;
        return out;
    }

    @Override
    public boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        if (isEmpty(view, x, y, z) && isEndStone[view.getBlock(x, y - 1, z)]) {
            generatePlant(view, x, y, z, random, 8);
            return true;
        }
        return false;
    }

    /** ChorusFlowerBlock.generatePlant(level, pos, random, maxHorizontalDistance). */
    public void generatePlant(FeatureWorldView view, int x, int y, int z, WorldgenRandom random, int maxHorizontalDistance) {
        view.setBlock(x, y, z, stateWithConnections(view, x, y, z));
        growTreeRecursive(view, x, y, z, random, x, z, maxHorizontalDistance, 0);
    }

    private void growTreeRecursive(FeatureWorldView view, int bx, int by, int bz, WorldgenRandom random,
                                   int startX, int startZ, int maxHorizontalDistance, int depth) {
        int height = random.nextInt(4) + 1;
        if (depth == 0) height++;

        for (int i = 0; i < height; i++) {
            int py = by + i + 1;
            if (!allNeighborsEmpty(view, bx, py, bz, -1)) return;
            view.setBlock(bx, py, bz, stateWithConnections(view, bx, py, bz));
            view.setBlock(bx, py - 1, bz, stateWithConnections(view, bx, py - 1, bz));
        }

        boolean placedBranch = false;
        if (depth < 4) {
            int branches = random.nextInt(4);
            if (depth == 0) branches++;

            for (int i = 0; i < branches; i++) {
                int dir = random.nextInt(4);
                int nx = bx + HDX[dir], ny = by + height, nz = bz + HDZ[dir];
                if (Math.abs(nx - startX) < maxHorizontalDistance && Math.abs(nz - startZ) < maxHorizontalDistance
                    && isEmpty(view, nx, ny, nz) && isEmpty(view, nx, ny - 1, nz)
                    && allNeighborsEmpty(view, nx, ny, nz, (dir + 2) & 3)) {
                    placedBranch = true;
                    view.setBlock(nx, ny, nz, stateWithConnections(view, nx, ny, nz));
                    // pos.relative(dir.getOpposite())
                    int ox = nx - HDX[dir], oz = nz - HDZ[dir];
                    view.setBlock(ox, ny, oz, stateWithConnections(view, ox, ny, oz));
                    growTreeRecursive(view, nx, ny, nz, random, startX, startZ, maxHorizontalDistance, depth + 1);
                }
            }
        }

        if (!placedBranch) {
            view.setBlock(bx, by + height, bz, flowerAge5);
        }
    }

    /** ChorusFlowerBlock.allNeighborsEmpty(level, pos, excludingDir) — excludingDir 은 수평 인덱스(없으면 -1). */
    private boolean allNeighborsEmpty(FeatureWorldView view, int x, int y, int z, int excludingDir) {
        for (int d = 0; d < 4; d++) {
            if (d != excludingDir && !isEmpty(view, x + HDX[d], y, z + HDZ[d])) return false;
        }
        return true;
    }

    private boolean isEmpty(FeatureWorldView view, int x, int y, int z) {
        return attrs.isAir(view.getBlock(x, y, z));
    }

    /** ChorusPlantBlock.getStateWithConnections(level, pos, chorus_plant 기본 상태). */
    private int stateWithConnections(FeatureWorldView view, int x, int y, int z) {
        int below = view.getBlock(x, y - 1, z);
        int bits = 0;
        if (isPlant[below] || isFlower[below] || isEndStone[below]) bits |= 1;
        if (connects(view.getBlock(x, y + 1, z))) bits |= 2;
        if (connects(view.getBlock(x, y, z - 1))) bits |= 4;
        if (connects(view.getBlock(x + 1, y, z))) bits |= 8;
        if (connects(view.getBlock(x, y, z + 1))) bits |= 16;
        if (connects(view.getBlock(x - 1, y, z))) bits |= 32;
        return plantByConnections[bits];
    }

    private boolean connects(int state) {
        return isPlant[state] || isFlower[state];
    }
}
