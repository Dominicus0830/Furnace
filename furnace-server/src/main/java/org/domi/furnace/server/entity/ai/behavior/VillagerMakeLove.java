// 바닐라 VillagerMakeLove(350틱): 번식 상대 주민과 마주 보고 275~324틱 뒤 음식을 소화하고, 닿을 수 있는 빈 침대(48칸)를 잡아 아기를 낳는다.
// 침대가 없으면 화난 표시(13), 아기를 못 만들면 잡은 침대 티켓을 돌려준다. 난수는 몹 자신의 난수.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.Map;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.AgeableMob;
import org.domi.furnace.server.entity.EntityType;
import org.domi.furnace.server.entity.Villager;
import org.domi.furnace.server.entity.ai.brain.GlobalPos;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.MemoryStatus;
import org.domi.furnace.server.entity.ai.navigation.Path;
import org.domi.furnace.server.world.poi.PoiTypes;

public class VillagerMakeLove extends Behavior<Villager> {
    private long birthTimestamp;

    public VillagerMakeLove() {
        super(Map.of(MemoryModuleType.BREED_TARGET, MemoryStatus.VALUE_PRESENT, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.VALUE_PRESENT), 350, 350);
    }

    @Override
    protected boolean checkExtraStartConditions(ChunkLevel level, Villager v) { return isBreedingPossible(v); }

    @Override
    protected boolean canStillUse(ChunkLevel level, Villager v, long time) { return time <= birthTimestamp && isBreedingPossible(v); }

    @Override
    protected void start(ChunkLevel level, Villager v, long time) {
        AgeableMob t = v.getBrain().getMemory(MemoryModuleType.BREED_TARGET).get();
        BehaviorUtils.lockGazeAndWalkToEachOther(v, t, 0.5F, 2);
        t.broadcastEntityEvent((byte) 18);
        v.broadcastEntityEvent((byte) 18);
        int d = 275 + v.random().nextInt(50);
        birthTimestamp = time + d;
    }

    @Override
    protected void tick(ChunkLevel level, Villager v, long time) {
        Villager t = (Villager) v.getBrain().getMemory(MemoryModuleType.BREED_TARGET).get();
        if (BehaviorUtils.distSqr(v, t) > 5.0) return;
        BehaviorUtils.lockGazeAndWalkToEachOther(v, t, 0.5F, 2);
        if (time >= birthTimestamp) {
            v.eatAndDigestFood();
            t.eatAndDigestFood();
            tryToGiveBirth(level, v, t);
        } else if (v.random().nextInt(35) == 0) {
            t.broadcastEntityEvent((byte) 12);
            v.broadcastEntityEvent((byte) 12);
        }
    }

    private void tryToGiveBirth(ChunkLevel level, Villager v, Villager t) {
        Pos bed = level.poiManager().take(type -> type == PoiTypes.HOME, (type, pos) -> canReach(v, pos, type.validRange()), v.blockPosition(), 48);
        if (bed == null) {
            t.broadcastEntityEvent((byte) 13);
            v.broadcastEntityEvent((byte) 13);
            return;
        }
        Villager child = breed(level, v, t);
        if (child != null) child.getBrain().setMemory(MemoryModuleType.HOME, new GlobalPos(level.dimension(), bed));
        else level.poiManager().release(bed);
    }

    @Override
    protected void stop(ChunkLevel level, Villager v, long time) { v.getBrain().eraseMemory(MemoryModuleType.BREED_TARGET); }

    private static boolean isBreedingPossible(Villager v) {
        var t = v.getBrain().getMemory(MemoryModuleType.BREED_TARGET).filter(x -> x.type() == EntityType.VILLAGER);
        return t.isPresent() && BehaviorUtils.targetIsValid(v.getBrain(), MemoryModuleType.BREED_TARGET, EntityType.VILLAGER) && v.canBreed() && t.get().canBreed();
    }

    private static boolean canReach(Villager v, Pos pos, int validRange) {
        Path p = v.getNavigation().createPath(pos, validRange);
        return p != null && p.canReach();
    }

    private static Villager breed(ChunkLevel level, Villager v, Villager t) {
        Villager child = (Villager) v.getBreedOffspring(level, t);
        if (child == null) return null;
        v.setAge(6000);
        t.setAge(6000);
        child.setAge(-24000);
        child.moveTo(v.x(), v.y(), v.z(), 0.0F, 0.0F);
        level.addFreshEntityWithPassengers(child);
        child.broadcastEntityEvent((byte) 12);
        return child;
    }
}
