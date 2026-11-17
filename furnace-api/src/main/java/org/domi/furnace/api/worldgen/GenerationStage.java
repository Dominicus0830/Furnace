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
    /** 장식(지형지물. 나무·광석·꽃·호수 …). 끄면 지형지물만 빠지고 구조물 조각은 놓인다. */
    FEATURES,
    /**
     * 장식 단계 전체(구조물 조각 + 지형지물, 바닐라 ChunkGenerator#applyBiomeDecoration). 끄면 둘 다 없다 — Bukkit
     * {@code ChunkGenerator#shouldGenerateDecorations} 가 false 인 것과 같다. 끈 청크도 decorate 훅과 월드 장식({@code World#addChunkDecorator})은 돈다.
     */
    DECORATION,
    /** 청크를 처음 만들 때 놓는 동물(바닐라 spawnOriginalMobs — 노이즈 생성기만, 평지에는 없다). 끄면 그 청크에 놓지 않는다. */
    MOBS
}
