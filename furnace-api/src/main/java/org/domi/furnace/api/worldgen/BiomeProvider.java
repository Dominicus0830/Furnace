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

    /**
     * 이 공급자를 쓰는 월드의 정보 — 서버가 생성기를 만들기 직전({@link #biomes()} 앞) 월드 스레드에서 한 번 부른다(월드마다).
     * 기본은 아무것도 하지 않는다.
     */
    default void prepare(Context world) {}

    /**
     * 공급자 이름 — 이 공급자로 만든 월드의 level.dat 설명서에 남는다(T-210). 공급자 객체는 저장할 수 없어서, 다시 열 때 이 이름으로
     * "어떤 공급자가 필요한지"만 알린다. 기본은 클래스 이름.
     */
    default String name() { return getClass().getName(); }

    /**
     * 생성기를 만들 월드의 정보.
     *
     * @param worldName   월드 이름(폴더)
     * @param levelKey    레벨(차원) 키
     * @param environment 환경
     * @param seed        생성 시드(이미 있는 월드는 level.dat 의 시드)
     * @param minY        가장 낮은 블록 y
     * @param maxY        가장 높은 블록 y + 1
     */
    record Context(String worldName, String levelKey, org.domi.furnace.api.world.WorldEnvironment environment, long seed, int minY, int maxY) {}
}
