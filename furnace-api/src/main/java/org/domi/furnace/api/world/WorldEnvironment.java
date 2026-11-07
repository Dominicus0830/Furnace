// 추가 월드의 환경 — 저장 폴더 배치(Paper·CraftBukkit 과 같다)와 기본 차원 타입·생성기를 정한다. 버전 중립.
package org.domi.furnace.api.world;

public enum WorldEnvironment {
    /** 오버월드 같은 월드: 청크는 {@code <이름>/region}, 기본 차원 타입 minecraft:overworld. */
    NORMAL("minecraft:overworld"),
    /** 네더 같은 월드: 청크는 {@code <이름>/DIM-1/region}, 기본 차원 타입 minecraft:the_nether. */
    NETHER("minecraft:the_nether"),
    /** 엔드 같은 월드: 청크는 {@code <이름>/DIM1/region}, 기본 차원 타입 minecraft:the_end. 드래곤 전투는 그 월드의 level.dat 에 따로 남는다. */
    THE_END("minecraft:the_end");

    private final String defaultDimensionType;

    WorldEnvironment(String defaultDimensionType) { this.defaultDimensionType = defaultDimensionType; }

    /** 차원 타입을 정하지 않았을 때 쓰는 바닐라 차원 타입 키. */
    public String defaultDimensionType() { return defaultDimensionType; }
}
