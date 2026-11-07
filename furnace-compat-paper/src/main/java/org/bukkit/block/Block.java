package org.bukkit.block;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * 블록 한 칸. 1차 호환 범위는 위치와 종류 읽기뿐이다 — 쓰기(setType)·BlockState·BlockData 는 H3.
 * getType 은 그 칸을 가진 region 스레드에서만 된다(Furnace 블록 읽기 규칙, 이벤트 처리기 안은 해당).
 */
public interface Block {

    World getWorld();

    int getX();

    int getY();

    int getZ();

    Location getLocation();

    /** 지금 종류. 소유 스레드 전용, 청크가 로드돼 있지 않으면 IllegalStateException. */
    Material getType();

    Block getRelative(int modX, int modY, int modZ);

    Block getRelative(BlockFace face);
}
