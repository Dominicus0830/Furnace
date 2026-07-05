package org.domi.furnace.server.world.gen.feature.decorator;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.world.gen.feature.BlockPos;
import org.domi.furnace.server.world.gen.feature.TreeFeature;

public record BeehiveDecorator(float probability) implements TreeDecorator {
    // vanilla WORLDGEN_FACING = SOUTH, SPAWN_DIRECTIONS = HORIZONTAL(NORTH,EAST,SOUTH,WEST)에서 SOUTH의
    // opposite(NORTH)를 뺀 나머지 — 순서 EAST, SOUTH, WEST 그대로 유지(shuffle 순서가 RNG 소비 순서에 영향).
    private static final TreeFeature.Direction[] SPAWN_DIRECTIONS = {
        TreeFeature.Direction.EAST, TreeFeature.Direction.SOUTH, TreeFeature.Direction.WEST
    };

    @Override
    public void place(Context context) {
        List<BlockPos> logs = context.logs();
        List<BlockPos> leaves = context.leaves();
        if (logs.isEmpty()) return;
        if (context.random().nextFloat() >= probability) return;

        int y = !leaves.isEmpty()
            ? Math.max(leaves.get(0).y() - 1, logs.get(0).y() + 1)
            : Math.min(logs.get(0).y() + 1 + context.random().nextInt(3), logs.get(logs.size() - 1).y());

        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos pos : logs) {
            if (pos.y() != y) continue;
            for (TreeFeature.Direction dir : SPAWN_DIRECTIONS) {
                candidates.add(new BlockPos(pos.x() + dir.dx(), pos.y(), pos.z() + dir.dz()));
            }
        }
        if (candidates.isEmpty()) return;

        shuffle(candidates, context);

        BlockPos chosen = null;
        for (BlockPos pos : candidates) {
            boolean thisAir = context.view().getBlock(pos.x(), pos.y(), pos.z()) == 0;
            boolean southAir = context.view().getBlock(pos.x(), pos.y(), pos.z() + 1) == 0;
            if (thisAir && southAir) {
                chosen = pos;
                break;
            }
        }
        if (chosen == null) return;

        context.view().setBlock(chosen.x(), chosen.y(), chosen.z(), context.config().beeNestStateId());
        context.decorations().add(chosen);

        // 벌집 tile-entity 데이터(벌 개체 저장)는 fixture가 blockstate만 비교하므로 실제로 만들지 않지만,
        // vanilla BeehiveBlockEntity.storeBee가 소비하는 RNG draw 수는 스트림 동기화를 위해 그대로 소비한다.
        int beeCount = 2 + context.random().nextInt(2);
        for (int i = 0; i < beeCount; i++) context.random().nextInt(599);
    }

    // vanilla Util.shuffle과 동일한 Fisher-Yates(뒤에서부터, nextInt(i)로 스왑).
    private static void shuffle(List<BlockPos> list, Context context) {
        for (int i = list.size(); i > 1; i--) {
            int j = context.random().nextInt(i);
            BlockPos tmp = list.get(i - 1);
            list.set(i - 1, list.get(j));
            list.set(j, tmp);
        }
    }
}
