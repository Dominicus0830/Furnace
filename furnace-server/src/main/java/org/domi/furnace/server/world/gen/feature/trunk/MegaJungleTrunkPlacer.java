package org.domi.furnace.server.world.gen.feature.trunk;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.world.gen.carver.Mth;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.feature.BlockPos;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;
import org.domi.furnace.server.world.gen.feature.TreeConfig;

// vanilla MegaJungleTrunkPlacer 포팅 (정글 거목). GiantTrunkPlacer의 2x2 몸통 위에 나선형으로
// 퍼지는 가지를 추가로 심는다. Mth.sin/cos(vanilla 룩업 테이블)로 각도를 비트정확하게 계산.
public final class MegaJungleTrunkPlacer extends GiantTrunkPlacer {
    public MegaJungleTrunkPlacer(int baseHeight, int heightRandA, int heightRandB) {
        super(baseHeight, heightRandA, heightRandB);
    }

    @Override
    public List<FoliageAttachment> placeTrunk(
            FeatureWorldView view, WorldgenRandom random, int height, BlockPos origin, TreeConfig config, List<BlockPos> logs) {
        List<FoliageAttachment> list = new ArrayList<>(super.placeTrunk(view, random, height, origin, config, logs));

        for (int i = height - 2 - random.nextInt(4); i > height / 2; i -= 2 + random.nextInt(4)) {
            float f = random.nextFloat() * (float) (Math.PI * 2);
            int dx = 0;
            int dz = 0;
            for (int step = 0; step < 5; step++) {
                dx = (int) (1.5F + Mth.cos(f) * step);
                dz = (int) (1.5F + Mth.sin(f) * step);
                BlockPos pos = new BlockPos(origin.x() + dx, origin.y() + i - 3 + step / 2, origin.z() + dz);
                placeLog(view, pos, config, logs);
            }
            list.add(new FoliageAttachment(new BlockPos(origin.x() + dx, origin.y() + i, origin.z() + dz), -2, false));
        }

        return list;
    }
}
