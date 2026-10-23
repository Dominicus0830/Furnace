// vanilla BlockBlobFeature(forest_rock) 포팅 — 타이가 이끼 낀 조약돌 바위
package org.domi.furnace.server.world.gen.feature;

import java.util.Set;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class ForestRockFeature implements ConfiguredFeature {
    private final int stateId;             // mossy_cobblestone
    private final Set<Integer> dirtStates; // #minecraft:dirt
    private final Set<Integer> stoneStates; // #minecraft:base_stone_overworld

    public ForestRockFeature(int stateId, Set<Integer> dirtStates, Set<Integer> stoneStates) {
        this.stateId = stateId;
        this.dirtStates = dirtStates;
        this.stoneStates = stoneStates;
    }

    @Override
    public boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        // vanilla: 아래 칸이 dirt/base-stone일 때까지 하강(공기·기타 블록은 통과), minY+3 이하로 내려가면 실패.
        int minY = view.minY();
        while (y > minY + 3) {
            int below = view.getBlock(x, y - 1, z);
            if (!TreeFeature.isAir(below) && (dirtStates.contains(below) || stoneStates.contains(below))) {
                break;
            }
            y--;
        }
        if (y <= minY + 3) return false;

        for (int i = 0; i < 3; i++) {
            int rx = random.nextInt(2);
            int ry = random.nextInt(2);
            int rz = random.nextInt(2);
            float f = (rx + ry + rz) * 0.333F + 0.5F;
            // vanilla BlockPos.betweenClosed 순회 순서(x→y→z 오름차순)와 무관 — 전부 같은 state를 쓰므로 결과 동일.
            for (int dx = -rx; dx <= rx; dx++) {
                for (int dy = -ry; dy <= ry; dy++) {
                    for (int dz = -rz; dz <= rz; dz++) {
                        double distSqr = (double) dx * dx + (double) dy * dy + (double) dz * dz;
                        if (distSqr <= f * f) {
                            view.setBlock(x + dx, y + dy, z + dz, stateId);
                        }
                    }
                }
            }
            x += -1 + random.nextInt(2);
            y -= random.nextInt(2);
            z += -1 + random.nextInt(2);
        }
        return true;
    }
}
