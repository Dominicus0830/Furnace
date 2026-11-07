// 빛 엔진이 보는 세계: 청크별 빛 데이터와 블록 상태.
package org.domi.furnace.server.light;

public interface LightWorld {
    /** 그 청크의 빛 데이터(올라와 있지 않으면 null). */
    ChunkLight light(int chunkX, int chunkZ);

    /** 올라와 있는 청크의 블록 상태(월드 높이 밖은 void_air). */
    int state(int x, int y, int z);

    /**
     * 올라와 있는 청크의 한 구역(sectionY = 블록 y >> 4)이 한 상태로만 차 있으면 그 상태, 모르거나 섞였으면 -1.
     * 빠른 경로용 힌트일 뿐이다 — -1 을 돌려도 결과는 같고 느릴 뿐이다.
     */
    default int uniformSectionState(int chunkX, int sectionY, int chunkZ) { return -1; }

    /** 그 구역에 빛을 내는 블록이 있을 수도 있는가(바닐라 findBlockLightSources 의 maybeHas). false 면 확실히 없다. 기본 true. */
    default boolean sectionMayEmit(int chunkX, int sectionY, int chunkZ) { return true; }

    /** 올라오지 않은 청크 칸으로 보는 상태(바닐라: 기반암). */
    int unloadedState();

    int minY();

    int maxY();

    /** 한 번의 계산(청크 켜기·갱신 실행) 시작 — 구현은 이 구간에서만 청크 조회 캐시를 쓴다(밖에서는 청크가 내려가거나 바뀔 수 있다). */
    default void begin() {}

    /** 계산 끝 — 캐시를 비우고 쓰기를 멈춘다. */
    default void end() {}

    /** 그 칸의 가짜 발광(플러그인 빛 API — 블록 발광과 큰 쪽을 쓴다). 기본 0. */
    default int extraEmission(int x, int y, int z) { return 0; }

    /** 칸의 빛 값을 썼다(LayerLightSectionStorage.setStoredLevel → sectionsAffectedByLightUpdates). sky 면 하늘빛. */
    default void lightChanged(int x, int y, int z, boolean sky) {}
}
