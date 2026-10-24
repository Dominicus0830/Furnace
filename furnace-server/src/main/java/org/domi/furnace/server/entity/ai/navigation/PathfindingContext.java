// 바닐라 PathfindingContext: 길찾기가 월드를 읽는 창 — 칸 상태와 칸 종류(캐시 없이 바로 계산), 몹이 선 칸.
package org.domi.furnace.server.entity.ai.navigation;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Mob;

public final class PathfindingContext {
    private final ChunkLevel level;
    private final Pos mobPosition;

    public PathfindingContext(ChunkLevel level, Mob mob) {
        this.level = level;
        this.mobPosition = mob.blockPosition();
    }

    public PathType getPathTypeFromState(int x, int y, int z) { return WalkNodeEvaluator.getPathTypeFromState(this, new Pos(x, y, z)); }

    public int getBlockState(Pos p) { return level.getBlockState(p); }

    public ChunkLevel level() { return level; }

    public Pos mobPosition() { return mobPosition; }
}
