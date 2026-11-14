package org.bukkit;

/** 청크 좌표 핸들(블록 접근은 H3). */
public interface Chunk {

    int getX();

    int getZ();

    World getWorld();

    boolean isLoaded();

    /**
     * 청크를 불러온다. Furnace 는 동기로 불러오지 않는다 — 이미 로드돼 있으면 true, 아니면 아무것도 하지 않고 false
     * (Paper 는 그 자리에서 불러오거나 만들고 true). 붙잡아 두려면 Furnace ChunkTickets 를 쓴다.
     */
    boolean load(boolean generate);

    /** load(true). */
    boolean load();

    /**
     * 이 청크 안(엔티티 위치 기준)의 엔티티(플레이어 포함). 이 청크를 가진 region 스레드에서만(아니면 ThreadOwnershipException — 전역 스레드의
     * 동기 작업은 region 을 빌려 읽는다). 로드되지 않았으면 빈 배열(Paper 는 불러온다).
     */
    org.bukkit.entity.Entity[] getEntities();
}
