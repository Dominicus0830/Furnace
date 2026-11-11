package org.bukkit.block;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

/**
 * 블록 상태 스냅숏(Bukkit 의 일부). Furnace 호환 계층은 아직 구현을 만들지 않는다 — 이벤트 서명(BlockPlaceEvent 등)이 쓰는 타입으로만 있다.
 * 구현이 생기면 Paper 처럼 스냅숏을 바꾼 뒤 {@link #update()} 로 월드에 쓴다.
 */
public interface BlockState {

    Block getBlock();

    BlockData getBlockData();

    Material getType();

    World getWorld();

    int getX();

    int getY();

    int getZ();

    Location getLocation();

    Location getLocation(Location loc);

    Chunk getChunk();

    void setBlockData(BlockData data);

    void setType(Material type);

    boolean update();

    boolean update(boolean force);

    boolean update(boolean force, boolean applyPhysics);

    boolean isPlaced();
}
