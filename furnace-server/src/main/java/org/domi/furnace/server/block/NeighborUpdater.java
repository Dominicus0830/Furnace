// 바닐라 CollectingNeighborUpdater: 처음 요청은 바로 실행하고, 실행 중에 생긴 요청은 층마다 모아 요청 순서대로 스택에 올려
// 깊이 우선으로 처리한다. 한 연쇄의 전체 요청 수는 max-chained-neighbor-updates(기본 1000000, 음수면 무제한)로 자른다.
package org.domi.furnace.server.block;

import java.util.ArrayDeque;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NeighborUpdater {

    private static final Logger log = LoggerFactory.getLogger(NeighborUpdater.class);

    private interface Entry {
        /** 한 걸음 실행. 더 할 것이 있으면 true. */
        boolean runNext(BlockLevel level);
    }

    private record ShapeUpdate(int dir, int neighborState, Pos pos, Pos neighborPos, int flags, int recursionLeft) implements Entry {
        public boolean runNext(BlockLevel level) {
            executeShapeUpdate(level, dir, neighborState, pos, neighborPos, flags, recursionLeft);
            return false;
        }
    }

    private record SimpleNeighborUpdate(Pos pos, int block, Pos neighborPos) implements Entry {
        public boolean runNext(BlockLevel level) {
            executeUpdate(level, level.getBlockState(pos), pos, block, neighborPos, false);   // 실행 때 읽는다
            return false;
        }
    }

    private record FullNeighborUpdate(int state, Pos pos, int block, Pos neighborPos, boolean moved) implements Entry {
        public boolean runNext(BlockLevel level) {
            executeUpdate(level, state, pos, block, neighborPos, moved);   // 요청 때 잡은 상태
            return false;
        }
    }

    private static final class MultiNeighborUpdate implements Entry {
        private final Pos sourcePos;
        private final int sourceBlock;
        private final int skip;
        private int idx;

        MultiNeighborUpdate(Pos sourcePos, int sourceBlock, int skip) {
            this.sourcePos = sourcePos;
            this.sourceBlock = sourceBlock;
            this.skip = skip;
            if (Dir.UPDATE_ORDER[idx] == skip) idx++;
        }

        public boolean runNext(BlockLevel level) {
            Pos p = sourcePos.relative(Dir.UPDATE_ORDER[idx++]);
            executeUpdate(level, level.getBlockState(p), p, sourceBlock, sourcePos, false);
            if (idx < 6 && Dir.UPDATE_ORDER[idx] == skip) idx++;
            return idx < 6;
        }
    }

    private final BlockLevel level;
    private final int maxChained;
    private final ArrayDeque<Entry> stack = new ArrayDeque<>();
    private final ArrayList<Entry> addedThisLayer = new ArrayList<>();
    private int count;

    public NeighborUpdater(BlockLevel level, int maxChainedNeighborUpdates) {
        this.level = level;
        this.maxChained = maxChainedNeighborUpdates;
    }

    public void shapeUpdate(int dir, int neighborState, Pos pos, Pos neighborPos, int flags, int recursionLeft) {
        addAndRun(pos, new ShapeUpdate(dir, neighborState, pos, neighborPos, flags, recursionLeft));
    }

    public void neighborChanged(Pos pos, int block, Pos fromPos) {
        addAndRun(pos, new SimpleNeighborUpdate(pos, block, fromPos));
    }

    public void neighborChanged(int state, Pos pos, int block, Pos fromPos, boolean moved) {
        addAndRun(pos, new FullNeighborUpdate(state, pos, block, fromPos, moved));
    }

    /** skipDir < 0 이면 건너뛰는 방향 없음. */
    public void updateNeighborsAtExceptFromFacing(Pos pos, int block, int skipDir) {
        addAndRun(pos, new MultiNeighborUpdate(pos, block, skipDir));
    }

    private void addAndRun(Pos pos, Entry e) {
        boolean nested = count > 0;
        boolean limitReached = maxChained >= 0 && count >= maxChained;
        count++;
        if (!limitReached) {
            if (nested) addedThisLayer.add(e);
            else stack.push(e);
        } else if (count - 1 == maxChained) {
            log.error("Too many chained neighbor updates. Skipping the rest. First skipped position: {}", pos.toShortString());
        }
        if (!nested) runUpdates();
    }

    private void runUpdates() {
        try {
            while (!stack.isEmpty() || !addedThisLayer.isEmpty()) {
                for (int i = addedThisLayer.size() - 1; i >= 0; i--) stack.push(addedThisLayer.get(i));
                addedThisLayer.clear();
                Entry top = stack.peek();
                while (addedThisLayer.isEmpty()) {
                    if (!top.runNext(level)) {
                        stack.pop();
                        break;
                    }
                }
            }
        } finally {
            stack.clear();
            addedThisLayer.clear();
            count = 0;
        }
    }

    // ------------------------------------------------------------------ 바닐라 NeighborUpdater 정적

    /** executeShapeUpdate: 그 칸의 updateShape 결과로 updateOrDestroy. */
    public static void executeShapeUpdate(BlockLevel level, int dir, int neighborState, Pos pos, Pos neighborPos, int flags, int recursionLeft) {
        int state = level.getBlockState(pos);
        var events = level.gameEvents();
        if (events.wantsBlockPhysics() && !events.blockPhysics(level.dimension(), pos, state, neighborPos, B.block(neighborState))) return;   // 플러그인 BlockPhysicsEvent 취소
        int newState = BlockBehaviors.of(state).updateShape(state, dir, neighborState, level, pos, neighborPos);
        B.updateOrDestroy(state, newState, level, pos, flags, recursionLeft);
    }

    /** executeUpdate: neighborChanged 호출. */
    public static void executeUpdate(BlockLevel level, int state, Pos pos, int sourceBlock, Pos sourcePos, boolean moved) {
        var events = level.gameEvents();
        if (events.wantsBlockPhysics() && !events.blockPhysics(level.dimension(), pos, state, sourcePos, sourceBlock)) return;   // 플러그인 BlockPhysicsEvent 취소
        BlockBehaviors.of(state).neighborChanged(state, level, pos, sourceBlock, sourcePos, moved);
    }
}
