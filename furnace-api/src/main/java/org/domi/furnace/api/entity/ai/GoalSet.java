// 몹의 목표 묶음 둘. 바닐라처럼 행동 목표와 대상 목표가 따로 우선순위를 다툰다.
package org.domi.furnace.api.entity.ai;

public enum GoalSet {
    /** 행동 목표(걷기·공격·둘러보기 등). */
    GOALS,
    /** 대상 정하기 목표(가까운 플레이어 노리기, 때린 쪽 노리기 등). */
    TARGETS
}
