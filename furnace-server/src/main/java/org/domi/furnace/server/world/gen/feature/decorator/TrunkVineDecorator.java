package org.domi.furnace.server.world.gen.feature.decorator;

import org.domi.furnace.server.world.gen.feature.BlockPos;
import org.domi.furnace.server.world.gen.feature.TreeFeature;

// vanilla TrunkVineDecorator 포팅 — 방향 순서는 vanilla 하드코딩 그대로 WEST, EAST, NORTH, SOUTH
// (RNG draw가 방향별로 소비되므로 순서가 결과에 직결). 이전 구현은 HORIZONTAL_DIRECTIONS(S,W,N,E) 순회에
// 더해 배치 조건이 반전(vanilla는 nextInt(3)>0일 때 배치=2/3 확률, 이전 구현은 ==0일 때 배치=1/3)돼
// 있었다(mega-tree-placers task 수정).
public final class TrunkVineDecorator implements TreeDecorator {
    private static final TreeFeature.Direction[] VANILLA_ORDER = {
        TreeFeature.Direction.WEST, TreeFeature.Direction.EAST, TreeFeature.Direction.NORTH, TreeFeature.Direction.SOUTH
    };

    @Override
    public void place(Context context) {
        for (BlockPos pos : context.logs()) {
            for (TreeFeature.Direction dir : VANILLA_ORDER) {
                if (context.random().nextInt(3) == 0) continue;
                int wx = pos.x() + dir.dx();
                int wz = pos.z() + dir.dz();
                if (TreeFeature.isAir(context.view().getBlock(wx, pos.y(), wz))) {
                    context.view().setBlock(wx, pos.y(), wz, context.config().vineStateId(dir.opposite()));
                    context.decorations().add(new BlockPos(wx, pos.y(), wz));
                }
            }
        }
    }
}
