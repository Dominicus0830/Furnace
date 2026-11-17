// 생성된 Craft 엔티티 클래스가 쓰는 읽기 도우미. 소유 스레드면 지금 값, 아니면 핸들 시점 값(getHealth 와 같은 규칙).
package org.domi.furnace.compat.paper;

import org.domi.furnace.api.concurrent.ThreadOwnershipException;
import org.domi.furnace.api.entity.Entity;
import org.domi.furnace.api.entity.LivingEntityState;

public final class EntityReads {

    private EntityReads() {}

    /** Tameable#isTamed / AbstractHorse#isTamed. */
    public static boolean tamed(Entity h) {
        if (EntityStates.live(h)) {
            try {
                Boolean now = EntityStates.read(h, LivingEntityState.class, LivingEntityState::tamed, null, "Tameable#isTamed");
                if (now != null) return now;
            } catch (ThreadOwnershipException | UnsupportedFeature raced) {
                // 핸들 시점 값으로
            }
        }
        return h instanceof org.domi.furnace.api.entity.Mob m && m.tamed();
    }
}
