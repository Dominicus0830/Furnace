// 바닐라 LookAndFollowTradingPlayerSink: 거래 중인 플레이어(4칸 안, 메뉴 열림)를 바라보며 따라간다. 시간 제한 없음. 끝나면 걷기·시선 목표를 지운다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.Map;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.Villager;
import org.domi.furnace.server.entity.ai.brain.EntityTracker;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.MemoryStatus;
import org.domi.furnace.server.entity.ai.brain.WalkTarget;

public class LookAndFollowTradingPlayerSink extends Behavior<Villager> {
    private final float speedModifier;

    public LookAndFollowTradingPlayerSink(float speed) {
        super(Map.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED, MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED), Integer.MAX_VALUE);
        this.speedModifier = speed;
    }

    @Override
    protected boolean checkExtraStartConditions(ChunkLevel level, Villager v) {
        Targetable p = v.tradingTarget();
        return v.isAlive() && p != null && !v.isInWater() && !v.hurtMarked && BehaviorUtils.distSqr(v, p) <= 16.0 && v.tradingPlayer() != null;
    }

    @Override
    protected boolean canStillUse(ChunkLevel level, Villager v, long time) { return checkExtraStartConditions(level, v); }

    @Override
    protected void start(ChunkLevel level, Villager v, long time) { followPlayer(v); }

    @Override
    protected void stop(ChunkLevel level, Villager v, long time) {
        v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        v.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
    }

    @Override
    protected void tick(ChunkLevel level, Villager v, long time) { followPlayer(v); }

    @Override protected boolean timedOut(long time) { return false; }

    private void followPlayer(Villager v) {
        Targetable p = v.tradingTarget();
        v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(p, false), speedModifier, 2));
        v.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(p, true));
    }
}
