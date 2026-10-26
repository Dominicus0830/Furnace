// 바닐라 ResetProfession: 직업 블록을 잃었고 거래 경험치가 0·레벨 1 이하인 주민(무직·바보 제외)은 무직으로 돌아간다.
package org.domi.furnace.server.entity.ai.behavior;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Villager;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;

public final class ResetProfession {
    private ResetProfession() {}

    public static OneShot<Villager> create() {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, Villager v, long time) {
                if (!absent(v, MemoryModuleType.JOB_SITE)) return false;
                String p = v.profession();
                if (!p.equals("none") && !p.equals("nitwit") && v.villagerXp() == 0 && v.villagerLevel() <= 1) {
                    v.setProfession("none");
                    v.refreshBrain(level);
                    return true;
                }
                return false;
            }
        };
    }
}
