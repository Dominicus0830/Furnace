// 바닐라 WalkTarget: 걸어갈 목표(추적기, 속도, 도착으로 치는 거리).
package org.domi.furnace.server.entity.ai.brain;

import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Targetable;

public final class WalkTarget {
    private final PositionTracker target;
    private final float speedModifier;
    private final int closeEnoughDist;

    public WalkTarget(Pos pos, float speed, int closeEnough) { this(new BlockPosTracker(pos), speed, closeEnough); }

    public WalkTarget(double[] vec, float speed, int closeEnough) {
        this(new BlockPosTracker(new Pos((int) Math.floor(vec[0]), (int) Math.floor(vec[1]), (int) Math.floor(vec[2]))), speed, closeEnough);
    }

    public WalkTarget(Targetable entity, float speed, int closeEnough) { this(new EntityTracker(entity, false), speed, closeEnough); }

    public WalkTarget(PositionTracker target, float speed, int closeEnough) {
        this.target = target;
        this.speedModifier = speed;
        this.closeEnoughDist = closeEnough;
    }

    public PositionTracker getTarget() { return target; }

    public float getSpeedModifier() { return speedModifier; }

    public int getCloseEnoughDist() { return closeEnoughDist; }
}
