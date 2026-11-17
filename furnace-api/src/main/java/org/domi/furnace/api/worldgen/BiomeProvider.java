// 바이옴 공급자(T-203 3단계): 노이즈 생성기의 바이옴 원천을 대신한다 — Bukkit BiomeProvider·CraftBukkit CustomWorldChunkManager 와 같은 자리.
// 지형(밀도)은 바닐라처럼 바이옴과 무관하고, 청크에 저장되는 바이옴·표면 규칙·지형지물·구조물 후보가 이 공급자를 따른다.
package org.domi.furnace.api.worldgen;

import java.util.List;

public interface BiomeProvider {

    /**
     * 블록 좌표의 바이옴 키("minecraft:plains"). 바닐라처럼 4×4×4 칸(quart)마다 그 모서리 좌표(quart × 4)로 부른다 —
     * 청크를 채울 때는 섹션 아래부터, 섹션 안에서 x 바깥·y 가운데·z 안쪽 순서. 생성 스레드 여러 개에서 동시에 불린다(상태를 바꾸지 말 것).
     * 돌려준 바이옴은 {@link #biomes()} 에 있어야 바닐라와 같은 지형지물이 놓인다.
     *
     * @param climate 그 칸의 바닐라 기후 값(처음 읽을 때 계산한다)
     */
    String biome(int x, int y, int z, Climate climate);

    /** 나올 수 있는 바이옴 키 목록. 순서가 지형지물 단계 순서를 정한다(바닐라 BiomeSource#possibleBiomes). 생성기를 만들 때 한 번 부른다. */
    List<String> biomes();
}
