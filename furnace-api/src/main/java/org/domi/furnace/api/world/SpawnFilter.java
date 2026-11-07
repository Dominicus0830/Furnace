// 자연 스폰 조건 술어 — SpawnPolicy.addFilter. 바닐라 스폰 조건(바이옴 목록·자리·빛·거리)을 다 통과하고 몹을 만들기 직전에 묻는다.
package org.domi.furnace.api.world;

@FunctionalInterface
public interface SpawnFilter {

    /**
     * false 면 그 칸의 이번 시도는 바닐라 조건에 걸린 것처럼 건너뛴다(몹을 만들지 않고 난수도 더 쓰지 않는다).
     * region 틱 스레드에서 자주(스폰 시도마다) 불리고 여러 region 에서 동시에 불린다 — 가볍고 스레드에 안전하게 짠다.
     * 예외를 던지면 로그 한 번 남기고 그 필터는 빠진다. 플러그인이 꺼져도 빠진다.
     */
    boolean allow(SpawnCandidate candidate);
}
