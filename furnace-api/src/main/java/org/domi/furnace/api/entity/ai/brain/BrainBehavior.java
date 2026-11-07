// 플러그인이 만드는 두뇌 행동. 한 활동에 우선순위와 함께 붙고, 그 활동이 켜져 있을 때만 시작을 시도한다(바닐라 행동 규칙 그대로):
//  매 틱 — 기억 만료 → 센서 → 멈춘 행동 중 켜진 활동의 것을 우선순위 순서로 시작 시도 → 돌고 있는 행동마다 이어 갈지 묻고 tick 또는 stop.
// 활동이 꺼져도 돌던 행동은 shouldContinue 가 false 가 될 때까지 이어진다(바닐라와 같다).
// 그 몹을 가진 region 스레드에서 몹 틱 안에서 불린다 — 막히는 일을 하지 말 것. 넘겨받는 MobAi·MobBrain 은 그 호출 안에서만 쓴다.
// 플러그인이 꺼지면 다시 불리지 않고 스스로 빠진다. 콜백이 예외를 던지면 로그 한 번 남기고 빠진다. 저장되지 않는다(청크가 내려갔다 올라오면 다시 넣는다).
package org.domi.furnace.api.entity.ai.brain;

import java.util.Map;
import org.domi.furnace.api.entity.ai.MobAi;

public interface BrainBehavior {

    /** 표시용 이름(behaviors() 에 나온다). */
    default String name() { return getClass().getName(); }

    /** 시작 조건이 되는 기억 상태. 모두 맞아야 shouldStart 를 묻는다. 넣을 때 한 번 읽고, 없던 기억은 두뇌에 등록된다. */
    default Map<MemoryKey<?>, MemoryCondition> requiredMemories() { return Map.of(); }

    /** 시작할 것인가(기억 조건이 맞을 때만 불린다). */
    default boolean shouldStart(MobAi mob, MobBrain brain) { return true; }

    /** 돌고 있을 때 이어 갈 것인가. 기본 false — 시작한 다음 틱에 멈추는 한 번짜리 행동. */
    default boolean shouldContinue(MobAi mob, MobBrain brain) { return false; }

    default void start(MobAi mob, MobBrain brain) {}

    default void tick(MobAi mob, MobBrain brain) {}

    default void stop(MobAi mob, MobBrain brain) {}
}
