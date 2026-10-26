// 바닐라 WorkAtPoi: 300틱이 지났고 레벨 난수 nextInt(2)==0 이며 직업 블록 1.73칸 안이면, 블록을 보며 일하고(소리) 필요하면 거래 재고를 채운다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.Map;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Villager;
import org.domi.furnace.server.entity.ai.brain.BlockPosTracker;
import org.domi.furnace.server.entity.ai.brain.GlobalPos;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.MemoryStatus;

public class WorkAtPoi extends Behavior<Villager> {
    private static final int CHECK_COOLDOWN = 300;
    private static final double DISTANCE = 1.73;
    private long lastCheck;

    public WorkAtPoi() { super(Map.of(MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT, MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED)); }

    @Override
    protected boolean checkExtraStartConditions(ChunkLevel level, Villager v) {
        if (level.gameTime() - lastCheck < CHECK_COOLDOWN) return false;
        if (level.random().nextInt(2) != 0) return false;
        lastCheck = level.gameTime();
        GlobalPos g = v.getBrain().getMemory(MemoryModuleType.JOB_SITE).get();
        return g.dimension().equals(level.dimension()) && ValidateNearbyPoi.closerToCenterThan(g.pos(), v, DISTANCE);
    }

    @Override
    protected void start(ChunkLevel level, Villager v, long time) {
        var brain = v.getBrain();
        brain.setMemory(MemoryModuleType.LAST_WORKED_AT_POI, time);
        brain.getMemory(MemoryModuleType.JOB_SITE).ifPresent(g -> brain.setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(g.pos())));
        v.playWorkSound();
        useWorkstation(level, v);
        if (v.shouldRestock()) v.restock();
    }

    protected void useWorkstation(ChunkLevel level, Villager v) {}

    @Override
    protected boolean canStillUse(ChunkLevel level, Villager v, long time) {
        var g = v.getBrain().getMemory(MemoryModuleType.JOB_SITE);
        return g.isPresent() && g.get().dimension().equals(level.dimension()) && ValidateNearbyPoi.closerToCenterThan(g.get().pos(), v, DISTANCE);
    }
}
