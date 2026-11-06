// 바닐라 YieldJobSite: 무직 어른이 찾아 둔 직업 블록을, 그 블록에 맞는 직업이면서 갈 곳이 없는(또는 이미 그 블록을 기억한) 주변 주민에게 넘긴다.
package org.domi.furnace.server.entity.ai.behavior;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.Villager;
import org.domi.furnace.server.entity.ai.brain.GlobalPos;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.navigation.Path;
import org.domi.furnace.server.world.poi.PoiType;

public final class YieldJobSite {
    private YieldJobSite() {}

    public static OneShot<Villager> create(float speed) {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, Villager villager, long time) {
                if (!present(villager, MemoryModuleType.POTENTIAL_JOB_SITE) || !absent(villager, MemoryModuleType.JOB_SITE)
                        || !present(villager, MemoryModuleType.NEAREST_LIVING_ENTITIES) || !registered(villager, MemoryModuleType.WALK_TARGET)
                        || !registered(villager, MemoryModuleType.LOOK_TARGET)) return false;
                if (villager.isBaby()) return false;
                if (!villager.profession().equals("none")) return false;
                Pos pos = villager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).get().pos();
                PoiType type = level.poiManager().getType(pos);
                if (type == null) return true;
                for (Targetable t : villager.getBrain().getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES).get()) {
                    if (t instanceof Villager v && v != villager && v.isAlive() && nearbyWantsJobsite(type, v, pos)) {
                        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
                        villager.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
                        if (v.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty()) {
                            BehaviorUtils.setWalkAndLookTargetMemories(v, pos, speed, 1);
                            v.getBrain().setMemory(MemoryModuleType.POTENTIAL_JOB_SITE, new GlobalPos(level.dimension(), pos));
                        }
                        break;
                    }
                }
                return true;
            }
        };
    }

    private static boolean nearbyWantsJobsite(PoiType type, Villager v, Pos pos) {
        if (v.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).isPresent()) return false;
        var job = v.getBrain().getMemory(MemoryModuleType.JOB_SITE);
        if (!Villager.heldJobSite(v.profession()).test(type)) return false;
        return job.isEmpty() ? canReachPos(v, pos, type) : job.get().pos().equals(pos);
    }

    private static boolean canReachPos(Villager v, Pos pos, PoiType type) {
        Path p = v.getNavigation().createPath(pos, type.validRange());
        return p != null && p.canReach();
    }
}
