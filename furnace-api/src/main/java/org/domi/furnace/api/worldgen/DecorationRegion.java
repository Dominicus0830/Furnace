// 장식 단계 훅이 받는 영역 — 바닐라 WorldGenRegion 과 같은 범위다: 중심(origin) 청크 둘레 5×5 청크를 읽고, 둘레 3×3 청크에 쓴다.
// 좌표는 월드 블록 좌표. 생성 스레드에서 불리고, 훅이 끝나면 더 쓸 수 없다.
package org.domi.furnace.api.worldgen;

import org.domi.furnace.api.world.BlockData;

public interface DecorationRegion {

    /** 차원 키. */
    String world();

    /** 장식하는 청크(바닐라 장식 원점). */
    int originChunkX();

    int originChunkZ();

    /** 그 월드의 생성 시드. */
    long seed();

    /**
     * 바닐라 장식 씨앗(WorldgenRandom.setDecorationSeed(seed, originX*16, originZ*16) 의 값). 이 값으로 난수를 만들면
     * 같은 시드·같은 청크에서 늘 같은 결과가 나온다.
     */
    long decorationSeed();

    int minY();

    int maxY();

    /** 읽을 수 있는 칸인가(중심 둘레 5×5 청크, 높이 안). */
    boolean canRead(int x, int y, int z);

    /** 쓸 수 있는 칸인가(중심 둘레 3×3 청크, 높이 안). */
    boolean canWrite(int x, int y, int z);

    /** 읽을 수 없는 칸이면 공기. */
    BlockData block(int x, int y, int z);

    String blockType(int x, int y, int z);

    /**
     * 블록을 놓는다(이웃 갱신·물리 없음). 쓸 수 없는 칸이면 아무것도 하지 않고 false.
     *
     * @throws IllegalArgumentException 모르는 블록·속성
     */
    boolean setBlock(int x, int y, int z, BlockData data);

    /** 그 칸의 바이옴 키. 읽을 수 없으면 null. */
    String biome(int x, int y, int z);

    /** 그 열에서 공기가 아닌 가장 높은 블록 y(읽을 수 없거나 다 공기면 minY() - 1). */
    int highestBlockY(int x, int z);
}
