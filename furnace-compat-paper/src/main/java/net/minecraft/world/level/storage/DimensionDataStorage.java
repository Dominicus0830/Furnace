package net.minecraft.world.level.storage;

/**
 * 바닐라 차원 저장 데이터(data/*.dat) 묶음 자리(Mojang 이름, T-203 — {@code ServerChunkCache#getDataStorage}). Furnace 는 저장 데이터(습격·지도·명령 저장소)를
 * 서버 안에 두고 이 표로 비추지 않는다 — {@link #cache} 는 늘 비어 있고, 비우는 것(FAWE 임시 레벨 정리)은 아무 일도 하지 않는다.
 */
public final class DimensionDataStorage {

    /** 바닐라 이름 → 저장 데이터 캐시. */
    public final java.util.Map<String, Object> cache = new java.util.HashMap<>();

    /** 호환 모듈 전용. */
    public DimensionDataStorage() {}
}
