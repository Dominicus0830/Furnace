// 두뇌 행동 하나의 모습(읽은 시점 값). name 은 바닐라 행동이면 행동 종류 이름(이름 없는 행동은 "anonymous" — 판마다 바뀔 수 있어 표시용),
// 플러그인 행동이면 BrainBehavior.name(). priority 가 작을수록 먼저 시작을 시도한다.
package org.domi.furnace.api.entity.ai.brain;

public record BehaviorInfo(ActivityKey activity, int priority, String name, boolean running, boolean plugin) {}
