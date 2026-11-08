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
}
