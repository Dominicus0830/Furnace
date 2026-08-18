package org.domi.furnace.server.world.gen.feature.trunk;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.feature.BlockPos;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;
import org.domi.furnace.server.world.gen.feature.TreeConfig;
import org.domi.furnace.server.world.gen.feature.TreeFeature;
import org.domi.furnace.server.world.gen.feature.TreeIntProvider;

// vanilla BendingTrunkPlacer 포팅 (azalea_tree 등). 한 방향으로 조금씩 기울며 올라간 뒤, 같은 방향으로
// 한 번 더 꺾여 뻗는다.
//
// 이 placer의 유일한 vanilla 소비처는 azalea_tree.json → rooted_azalea_tree.json(root_system feature)
// 뿐이며, root_system은 별도 미구현 항목(worldgen-gap-survey #17, 이 작업 범위 밖)이라 자연 생성 경로로
// 도달할 수 없다 — oracle featurespill fixture로 검증 불가. result.md Issues/Caveats 참조.
public final class BendingTrunkPlacer extends TrunkPlacer {
    private final int minHeightForLeaves;
    private final TreeIntProvider bendLength;

    public BendingTrunkPlacer(int baseHeight, int heightRandA, int heightRandB, int minHeightForLeaves, TreeIntProvider bendLength) {
        super(baseHeight, heightRandA, heightRandB);
        this.minHeightForLeaves = minHeightForLeaves;
        this.bendLength = bendLength;
    }

    @Override
    public List<FoliageAttachment> placeTrunk(
            FeatureWorldView view, WorldgenRandom random, int height, BlockPos origin, TreeConfig config, List<BlockPos> logs) {
        TreeFeature.Direction dir = TreeFeature.getRandomDirection(random);
        int i = height - 1;
        setDirtAt(view, origin, config, logs);
        List<FoliageAttachment> list = new ArrayList<>();

        int x = origin.x();
        int y = origin.y();
        int z = origin.z();
        for (int i1 = 0; i1 <= i; i1++) {
            if (i1 + 1 >= i + random.nextInt(2)) {
                x += dir.dx();
                z += dir.dz();
            }
            placeLog(view, new BlockPos(x, y, z), config, logs);
            if (i1 >= minHeightForLeaves) {
                list.add(new FoliageAttachment(new BlockPos(x, y, z), 0, false));
            }
            y += 1;
        }

        int bendLen = bendLength.sample(random);
        for (int i2 = 0; i2 <= bendLen; i2++) {
            placeLog(view, new BlockPos(x, y, z), config, logs);
            list.add(new FoliageAttachment(new BlockPos(x, y, z), 0, false));
            x += dir.dx();
            z += dir.dz();
        }

        return list;
    }
}
