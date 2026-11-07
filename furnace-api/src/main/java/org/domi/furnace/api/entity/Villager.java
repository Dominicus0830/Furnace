// 마을 주민. 직업·단계는 핸들 시점 값이고, 평판(소문)은 소유 region 스레드에서 읽고 쓴다. 거래는 Merchant, AI 는 Mob.
// 집·직업 블록·모임 장소는 주민 두뇌 기억이다 — MobAi.brain() 로 읽는다(minecraft:home, minecraft:job_site, minecraft:meeting_point).
package org.domi.furnace.api.entity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface Villager extends Merchant, Mob {

    /** 직업 키(minecraft:farmer, 없으면 minecraft:none). 핸들 시점. */
    String profession();

    /** 직업 단계 1..5(견습 1 … 달인 5). 핸들 시점. */
    int professionLevel();

    /**
     * 그 플레이어(또는 엔티티)에 대한 평판 = 종류별 값 × 가중치의 합(거래 가격 할인·철 골렘 소환에 쓰는 바닐라 getPlayerReputation).
     * 주민이 사라졌으면 null 로 완료.
     */
    CompletableFuture<Integer> reputation(UUID target);

    /** target 에 대한 종류별 소문 값(0 이 아닌 것만). 주민이 사라졌으면 null 로 완료. */
    CompletableFuture<Map<GossipKind, Integer>> gossip(UUID target);

    /**
     * 소문을 더한다(바닐라 GossipContainer.add — 종류 최댓값으로 잘리고, 합이 2 미만이면 지워진다). value 가 음수면 줄인다.
     * 바뀌었으면 true, 주민이 사라졌으면 false.
     */
    CompletableFuture<Boolean> addGossip(UUID target, GossipKind kind, int value);

    /** target 의 모든 소문을 지운다(평판 0). 주민이 사라졌으면 false. */
    CompletableFuture<Boolean> clearGossip(UUID target);

    /** 바닐라 onReputationEvent 와 같은 값으로 소문을 더한다. 주민이 사라졌으면 false. */
    CompletableFuture<Boolean> applyReputationEvent(ReputationEvent event, UUID target);
}
