package org.domi.furnace.server.world.gen.feature.decorator;

import java.util.List;
import org.domi.furnace.server.world.gen.feature.BlockPos;
import org.domi.furnace.server.world.gen.feature.TreeFeature;

public record CocoaDecorator(float probability) implements TreeDecorator {
    // vanilla는 Plane.HORIZONTAL(N,E,S,W)을 돌며 opposite 쪽에 배치 — 배치 위치 기준으로는 S,W,N,E 순서와
    // 동치라서 HORIZONTAL_DIRECTIONS(S,W,N,E) 순회 + dir 쪽 배치 + facing=dir.opposite()가 vanilla와 같은
    // draw-위치 매핑을 만든다(k번째 draw가 같은 칸·같은 facing에 대응).
    @Override
    public void place(Context context) {
        // vanilla는 probability nextFloat를 logs 검사보다 먼저 소비한다.
        if (context.random().nextFloat() >= probability) return;
        if (context.logs().isEmpty()) return;
        int baseY = context.logs().get(0).y();
        for (BlockPos pos : context.logs()) {
            if (pos.y() - baseY > 2) continue;
            for (TreeFeature.Direction dir : TreeFeature.HORIZONTAL_DIRECTIONS) {
                if (context.random().nextFloat() <= 0.25f) {
                    int wx = pos.x() + dir.dx();
                    int wz = pos.z() + dir.dz();
                    if (TreeFeature.isAir(context.view().getBlock(wx, pos.y(), wz))) {
                        int age = context.random().nextInt(3);
                        context.view().setBlock(wx, pos.y(), wz, context.config().cocoaStateId(dir.opposite(), age));
                        context.decorations().add(new BlockPos(wx, pos.y(), wz));
                    }
                }
            }
        }
    }
}
