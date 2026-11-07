// 청크 생성 단계(바닐라 ChunkStatus 중 플러그인이 고르거나 바꿀 수 있는 것). 바닐라 순서대로 적었다.
package org.domi.furnace.api.worldgen;

public enum GenerationStage {
    /** 지형 밀도·대수층(돌·물·용암). 바이옴은 이 단계 전에 이미 정해져 있다. */
    NOISE,
    /** 표면 규칙(잔디·모래·테라코타 …)과 기반암. */
    SURFACE,
    /** 동굴·협곡 깎기(카버). */
    CARVERS,
    /** 구조물(마을·요새·폐광 …) 시작점과 조각 배치. */
    STRUCTURES,
    /** 장식(지형지물 — 나무·광석·꽃·호수 …). */
    FEATURES
}
