// 추가 월드의 지형 생성기. 버전 중립 — 바닐라 월드 프리셋·평지·빈 월드에 대응한다.
package org.domi.furnace.api.world;

public enum WorldGenerator {
    /** 환경의 바닐라 노이즈 생성기(오버월드·네더·엔드). 같은 시드면 바닐라와 같은 지형이다. */
    DEFAULT,
    /** 큰 바이옴(오버월드 노이즈 설정 minecraft:large_biomes). NORMAL 환경에서만. */
    LARGE_BIOMES,
    /** 증폭(minecraft:amplified). NORMAL 환경에서만. */
    AMPLIFIED,
    /** 평지(바닐라 minecraft:flat). 층·바이옴은 {@link WorldCreator.Builder#flatLayers} 로, 정하지 않으면 바닐라 기본(기반암·흙 2·잔디). */
    FLAT,
    /** 빈 월드 — 블록 없이 바이옴 minecraft:the_void 만(바닐라 평지 "The Void" 에서 시작 발판을 뺀 것). */
    VOID,
    /** 단일 바이옴 지표(바닐라 minecraft:single_biome_surface) — 오버월드 노이즈 지형에 바이옴이 plains 하나. NORMAL 환경에서만. */
    SINGLE_BIOME_SURFACE,
    /** 디버그 월드(바닐라 minecraft:debug_all_block_states) — 모든 블록 상태를 격자로 늘어놓는다(y=60 방벽, y=70 상태). NORMAL 환경, y=60..70 을 품은 차원 타입에서만. */
    DEBUG_ALL_BLOCK_STATES
}
