package org.bukkit;

import org.bukkit.block.data.BlockData;

/** 나무 생성 등이 블록을 놓을 대상(Bukkit). World#generateTree(…, BlockChangeDelegate) 가 쓴다 — Furnace 는 아직 그 생성기를 열지 않는다. */
public interface BlockChangeDelegate {

    boolean setBlockData(int x, int y, int z, BlockData blockData);

    BlockData getBlockData(int x, int y, int z);

    int getHeight();

    boolean isEmpty(int x, int y, int z);
}
