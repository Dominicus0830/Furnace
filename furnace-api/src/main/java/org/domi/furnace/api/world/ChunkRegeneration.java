// 청크 재생성 선택(불만 표 41번, World.regenerateChunk). 값 객체.
package org.domi.furnace.api.world;

/**
 * @param removeEntities      그 청크 안의 엔티티(플레이어 빼고)를 지운다. 기본 false — 엔티티는 그 자리에 남는다
 * @param generationEntities  새 청크가 생성 때 만드는 것을 넣는다: 구조물 템플릿의 엔티티(액자·셜커 …)와 처음 동물(바닐라
 *                            spawnOriginalMobs). 기본 false — 남아 있는 엔티티와 겹치지 않게
 */
public record ChunkRegeneration(boolean removeEntities, boolean generationEntities) {

    /** 블록·바이옴·블록 엔티티만 새로 만들고 엔티티는 그대로. */
    public static final ChunkRegeneration DEFAULT = new ChunkRegeneration(false, false);

    /** 처음 생성과 같게: 엔티티를 지우고 생성 때의 엔티티·동물을 넣는다. */
    public static final ChunkRegeneration FRESH = new ChunkRegeneration(true, true);
}
