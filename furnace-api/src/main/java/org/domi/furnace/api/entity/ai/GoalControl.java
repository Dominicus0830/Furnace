// 목표가 쥐는 제어 칸. 같은 칸을 쓰는 목표는 우선순위가 높은(숫자가 작은) 것 하나만 돈다.
// 몹을 다른 몹이 타고 조종하면 MOVE·LOOK·JUMP 목표는 쉰다(바닐라 그대로).
package org.domi.furnace.api.entity.ai;

public enum GoalControl {
    /** 걷기·길 따라가기. */
    MOVE,
    /** 고개 돌리기. */
    LOOK,
    /** 점프. */
    JUMP,
    /** 공격 대상 정하기(보통 TARGETS 쪽 목표). */
    TARGET
}
