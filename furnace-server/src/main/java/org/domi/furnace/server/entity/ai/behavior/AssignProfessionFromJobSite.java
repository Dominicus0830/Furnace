// 바닐라 AssignProfessionFromJobSite: 찾아 둔 직업 블록에 2칸 안으로 닿으면(또는 구조물 생성 직후) JOB_SITE 로 옮기고, 무직이면 그 블록의 직업을 갖는다.
package org.domi.furnace.server.entity.ai.behavior;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Villager;
import org.domi.furnace.server.entity.ai.brain.GlobalPos;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.world.poi.PoiType;

public final class AssignProfessionFromJobSite {
    private AssignProfessionFromJobSite() {}

    public static OneShot<Villager> create() {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, Villager villager, long time) {
                if (!present(villager, MemoryModuleType.POTENTIAL_JOB_SITE) || !registered(villager, MemoryModuleType.JOB_SITE)) return false;
                GlobalPos g = villager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).get();
                if (!ValidateNearbyPoi.closerToCenterThan(g.pos(), villager, 2.0) && !villager.assignProfessionWhenSpawned()) return false;
                villager.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
                villager.getBrain().setMemory(MemoryModuleType.JOB_SITE, g);
                villager.broadcastEntityEvent((byte) 14);
                if (!villager.profession().equals("none")) return true;
                if (level.dimension().equals(g.dimension())) {
                    PoiType type = level.poiManager().getType(g.pos());
                    String prof = type == null ? null : Villager.professionForJobSite(type);
                    if (prof != null) {
                        villager.setProfession(prof);
                        villager.refreshBrain(level);
                    }
                }
                return true;
            }
        };
    }
}
