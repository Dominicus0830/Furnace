// 바닐라 BlockPosTracker: 블록 한 칸(중심) 또는 정확한 점을 가리킨다.
package org.domi.furnace.server.entity.ai.brain;

import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Mob;

public final class BlockPosTracker implements PositionTracker {
    private final Pos blockPos;
    private final double[] pos;

    public BlockPosTracker(Pos p) {
        this.blockPos = p;
        this.pos = new double[] {p.x() + 0.5, p.y() + 0.5, p.z() + 0.5};
    }

    public BlockPosTracker(double x, double y, double z) {
        this.blockPos = new Pos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        this.pos = new double[] {x, y, z};
    }

    @Override public double[] currentPosition() { return pos; }

    @Override public Pos currentBlockPosition() { return blockPos; }

    @Override public boolean isVisibleBy(Mob looker) { return true; }

    @Override public String toString() { return "BlockPosTracker{blockPos=" + blockPos + "}"; }
}
