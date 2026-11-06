// 플러그인이 만드는 몹 목표. 모든 메서드는 그 몹을 가진 region 스레드에서 불린다(몹 틱 안) — 막히는 일을 하지 말 것.
// 부르는 주기는 바닐라와 같다: 시작 여부(shouldActivate)는 2틱에 한 번, tick 은 tickEveryTick 이 false 면 2틱에 한 번.
// 넘겨받는 MobAi 는 그 호출 안에서만 쓴다(다른 스레드로 넘기면 ThreadOwnershipException).
// 플러그인이 꺼지면 목표는 다시 불리지 않고 스스로 빠진다. 콜백이 예외를 던지면 로그를 한 번 남기고 그 목표는 멈춘다.
// 몹이 청크와 함께 내려갔다 다시 올라오면 바닐라 목표만 남는다(플러그인 목표는 저장되지 않는다) — 다시 넣어야 한다.
package org.domi.furnace.api.entity.ai;

import java.util.Set;

public interface MobGoal {

    /** 이 목표의 이름표(찾기·빼기에 쓴다). 플러그인 이름공간을 쓸 것. */
    GoalKey key();

    /** 쥐는 제어 칸. 넣을 때 한 번 읽는다. */
    default Set<GoalControl> controls() { return Set.of(); }

    /** 시작할 수 있는가. */
    boolean shouldActivate(MobAi mob);

    /** 돌고 있을 때 이어 갈 것인가. 기본은 shouldActivate 와 같다. */
    default boolean shouldStayActive(MobAi mob) { return shouldActivate(mob); }

    /** 우선순위가 더 높은 목표가 끼어들 수 있는가. 넣을 때 한 번 읽는다. */
    default boolean interruptible() { return true; }

    /** 매 틱 tick 을 부를 것인가(기본은 2틱에 한 번). 넣을 때 한 번 읽는다. */
    default boolean tickEveryTick() { return false; }

    default void start(MobAi mob) {}

    default void stop(MobAi mob) {}

    default void tick(MobAi mob) {}
}
