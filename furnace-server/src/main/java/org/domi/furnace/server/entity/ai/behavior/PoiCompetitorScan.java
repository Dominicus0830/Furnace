// 바닐라 PoiCompetitorScan: 같은 직업 블록을 기억한 주변 주민끼리 경험치가 많은 쪽만 남기고 나머지는 JOB_SITE 를 잊는다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.List;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.Villager;
import org.domi.furnace.server.entity.ai.brain.GlobalPos;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.world.poi.PoiType;

public final class PoiCompetitorScan {
    private PoiCompetitorScan() {}

    public static OneShot<Villager> create() {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, Villager villager, long time) {
                if (!present(villager, MemoryModuleType.JOB_SITE) || !present(villager, MemoryModuleType.NEAREST_LIVING_ENTITIES)) return false;
                GlobalPos jobSite = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).get();
                PoiType type = level.poiManager().getType(jobSite.pos());
                if (type != null) {
                    List<Targetable> nearby = villager.getBrain().getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES).get();
                    Villager winner = villager;
                    for (Targetable t : nearby) {
                        if (t instanceof Villager v && v != villager && v.isAlive() && competesForSameJobsite(jobSite, type, v)) winner = selectWinner(winner, v);
                    }
                }
                return true;
            }
        };
    }

    private static Villager selectWinner(Villager a, Villager b) {
        Villager winner, loser;
        if (a.villagerXp() > b.villagerXp()) {
            winner = a;
            loser = b;
        } else {
            winner = b;
            loser = a;
        }
        loser.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
        return winner;
    }

    private static boolean competesForSameJobsite(GlobalPos jobSite, PoiType type, Villager v) {
        var other = v.getBrain().getMemory(MemoryModuleType.JOB_SITE);
        return other.isPresent() && jobSite.equals(other.get()) && Villager.heldJobSite(v.profession()).test(type);
    }
}
