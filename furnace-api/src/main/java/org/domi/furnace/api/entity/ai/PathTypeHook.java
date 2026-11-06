// 길찾기 칸 판정 훅(몹 하나에 하나). 바닐라가 그 몹 기준으로 (x, y, z) 칸에 붙인 종류를 받아, 바꿀 종류를 돌려준다(null 이면 그대로).
// 예: 특정 블록 위를 BLOCKED 로 바꿔 못 가게, DANGER_OTHER 로 바꿔 돌아가게. 비용은 MobAi.setPathCost 로 종류별로 정한다.
// 길찾기 도중 그 몹의 region 스레드에서 아주 자주 불린다 — 가볍게, 막히지 않게. 그 스레드이므로 World.blockNow 로 칸을 읽을 수 있다.
// 한 번 길을 찾는 동안 같은 칸의 결과는 기억해 두고 다시 묻지 않는다. 예외를 던지면 로그를 한 번 남기고 훅을 뗀다.
package org.domi.furnace.api.entity.ai;

@FunctionalInterface
public interface PathTypeHook {
    PathNodeType classify(int x, int y, int z, PathNodeType vanilla);
}
