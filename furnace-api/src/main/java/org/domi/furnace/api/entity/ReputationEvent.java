// 주민이 평판을 바꾸는 사건(바닐라 ReputationEventType).
package org.domi.furnace.api.entity;

public enum ReputationEvent {
    /** 좀비 주민을 고침: 큰 긍정 20 + 작은 긍정 25. */
    ZOMBIE_VILLAGER_CURED,
    /** 철 골렘을 죽임(바닐라에는 효과가 없다). */
    GOLEM_KILLED,
    /** 주민을 때림: 작은 부정 25. */
    VILLAGER_HURT,
    /** 주민을 죽임: 큰 부정 25. */
    VILLAGER_KILLED,
    /** 거래함: 거래 2. */
    TRADE
}
