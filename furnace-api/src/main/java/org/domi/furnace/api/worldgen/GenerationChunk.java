// 생성 중인 청크 하나(노이즈·표면·카버 훅이 받는다). 좌표 x·z 는 청크 안 0..15, y 는 월드 높이.
// 이 객체는 훅이 돌아가는 동안만 쓸 수 있고, 생성 스레드(여러 개가 동시에 돈다)에서 불린다. 다른 청크·월드 상태는 건드리지 않는다.
package org.domi.furnace.api.worldgen;

import org.domi.furnace.api.world.BlockData;

public interface GenerationChunk {

    /** 차원 키(minecraft:overworld, 추가 월드면 그 키). */
    String world();

    int chunkX();

    int chunkZ();

    /** 그 월드의 생성 시드. */
    long seed();

    /** 가장 낮은 블록 y(포함). */
    int minY();

    /** 가장 높은 블록 y(포함). */
    int maxY();

    /** 해수면(노이즈 설정의 sea_level, 평지·빈 월드는 바닐라 FlatLevelSource 처럼 -63). */
    int seaLevel();

    BlockData block(int x, int y, int z);

    /** 블록 id 만(속성 없이) — block() 보다 싸다. */
    String blockType(int x, int y, int z);

    /**
     * 블록을 놓는다. 이웃 갱신·물리·빛 계산은 없다(바닐라 생성 단계와 같다). 상자·표지판 같은 블록 엔티티 블록은 청크가 올라갈 때
     * 빈 블록 엔티티가 생긴다. 높이 밖이면 무시한다.
     *
     * @throws IllegalArgumentException 모르는 블록·속성
     */
    void setBlock(int x, int y, int z, BlockData data);

    /** 상자 꼴로 채운다(양 끝 포함, 순서는 상관없다). 높이 밖은 잘린다. */
    void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockData data);

    /** 그 칸의 바이옴 키(4칸 단위). */
    String biome(int x, int y, int z);

    /** 그 열에서 공기가 아닌 가장 높은 블록 y. 다 공기면 minY() - 1. */
    int highestBlockY(int x, int z);
}
