package org.bukkit;

import org.bukkit.block.data.BlockData;

/** 나무 생성이 블록을 놓을 대상(Bukkit). World#generateTree(…, BlockChangeDelegate) 가 쓴 칸마다 setBlockData 를 부른다. */
public interface BlockChangeDelegate {

    boolean setBlockData(int x, int y, int z, BlockData blockData);

    BlockData getBlockData(int x, int y, int z);

    int getHeight();

    boolean isEmpty(int x, int y, int z);
}
