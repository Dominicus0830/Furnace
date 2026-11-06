package org.domi.furnace.server.world.gen.feature.decorator;

import org.domi.furnace.server.world.gen.feature.BlockPos;
import org.domi.furnace.server.world.gen.feature.TreeFeature;

// vanilla LeaveVineDecorator 포팅 — 방향 순서는 vanilla 하드코딩 그대로 WEST, EAST, NORTH, SOUTH
// (RNG draw가 방향별로 소비되므로 순서가 결과에 직결). 매달림 길이도 vanilla와 동일하게 시작 + 최대 4
// (총 5칸). 이전 구현은 HORIZONTAL_DIRECTIONS(S,W,N,E) 순회 + 최대 4칸이라 어긋났다(mega-tree-placers task 수정).
public record LeaveVineDecorator(float probability) implements TreeDecorator {
    private static final TreeFeature.Direction[] VANILLA_ORDER = {
        TreeFeature.Direction.WEST, TreeFeature.Direction.EAST, TreeFeature.Direction.NORTH, TreeFeature.Direction.SOUTH
    };

    @Override
    public void place(Context context) {
        for (BlockPos pos : context.leaves()) {
            for (TreeFeature.Direction dir : VANILLA_ORDER) {
                if (context.random().nextFloat() >= probability) continue;
                int wx = pos.x() + dir.dx();
                int wz = pos.z() + dir.dz();
                if (TreeFeature.isAir(context.view().getBlock(wx, pos.y(), wz))) {
                    addHangingVine(context, new BlockPos(wx, pos.y(), wz), dir.opposite());
                }
            }
        }
    }

    // vanilla addHangingVine — 시작 칸은 무조건 배치(호출부에서 공기 확인 완료), 그 아래로 공기인 동안 최대 4칸 추가.
    private void addHangingVine(Context context, BlockPos start, TreeFeature.Direction facing) {
        if (org.domi.furnace.server.world.gen.carver.RandomTrace.isActive())
            org.domi.furnace.server.world.gen.carver.RandomTrace.log("leave_vine start=" + start.x() + "," + start.y() + "," + start.z() + " facing=" + facing);
        context.view().setBlock(start.x(), start.y(), start.z(), context.config().vineStateId(facing));
        context.decorations().add(start);
        BlockPos current = new BlockPos(start.x(), start.y() - 1, start.z());
        for (int i = 4; TreeFeature.isAir(context.view().getBlock(current.x(), current.y(), current.z())) && i > 0; i--) {
            context.view().setBlock(current.x(), current.y(), current.z(), context.config().vineStateId(facing));
            context.decorations().add(current);
            current = new BlockPos(current.x(), current.y() - 1, current.z());
        }
    }
}
