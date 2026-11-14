// 빛 엔진이 보는 세계: 청크별 빛 데이터와 블록 상태.
package org.domi.furnace.server.light;

import org.domi.furnace.server.world.ChunkSection;

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

    /**
     * 올라와 있는 청크의 한 구역이 공기(air·cave_air·void_air)뿐인가(바닐라 LevelChunkSection.hasOnlyAir). 힌트가 아니라 결과를 정한다 —
     * 하늘빛 원천 계산이 바닐라처럼 공기뿐인 구역을 건너뛴다({@link SkyLightEngine#computeSources}). 기본은 4096칸을 훑는다(구현은 비공기 수로 빨리).
     */
    default boolean sectionHasOnlyAir(int chunkX, int sectionY, int chunkZ) {
        int x0 = chunkX << 4, y0 = sectionY << 4, z0 = chunkZ << 4;
        for (int y = y0; y < y0 + 16; y++) for (int z = z0; z < z0 + 16; z++) for (int x = x0; x < x0 + 16; x++) {
            if (!ChunkSection.isAir(state(x, y, z))) return false;
        }
        return true;
    }

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

    /**
     * 감소 전파가 올라오지 않은 청크의 칸 (x, y, z) 에서 멈췄다. 그 청크(와 그 청크를 거쳐 빛이 닿았을 수 있는 이웃)의 디스크 빛은 지운 빛을
     * 아직 담고 있을 수 있다 — 구현은 그 청크들을 "저장 빛을 믿지 말 것"으로 표시한다. 기본은 아무것도 안 한다(고립 계산·시험 세계).
     */
    default void decreaseBlocked(int x, int y, int z) {}
}
