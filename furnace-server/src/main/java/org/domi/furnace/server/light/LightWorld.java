// 빛 엔진이 보는 세계: 청크별 빛 데이터와 블록 상태.
package org.domi.furnace.server.light;

public interface LightWorld {
    /** 그 청크의 빛 데이터(올라와 있지 않으면 null). */
    ChunkLight light(int chunkX, int chunkZ);

    /** 올라와 있는 청크의 블록 상태(월드 높이 밖은 void_air). */
    int state(int x, int y, int z);

    /** 올라오지 않은 청크 칸으로 보는 상태(바닐라: 기반암). */
    int unloadedState();

    int minY();

    int maxY();

    /** 한 번의 계산(청크 켜기·갱신 실행) 시작 — 구현은 이 구간에서만 청크 조회 캐시를 쓴다(밖에서는 청크가 내려가거나 바뀔 수 있다). */
    default void begin() {}

    /** 계산 끝 — 캐시를 비우고 쓰기를 멈춘다. */
    default void end() {}
}
