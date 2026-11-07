package org.bukkit;

/** 청크 좌표 핸들(블록 접근은 H3). */
public interface Chunk {

    int getX();

    int getZ();

    World getWorld();

    boolean isLoaded();
}
