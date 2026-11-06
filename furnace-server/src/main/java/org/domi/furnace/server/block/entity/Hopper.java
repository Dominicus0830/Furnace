// 바닐라 Hopper: 깔때기·깔때기 수레 공통 — 기준 좌표(getLevelX/Y/Z), 격자에 맞는가, 빨아들이는 상자, 칸.
package org.domi.furnace.server.block.entity;

import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.entity.AABB;

public interface Hopper {
    double levelX();

    double levelY();

    double levelZ();

    boolean isGridAligned();

    HopperBlockEntity.Inv hopperInv(BlockLevel level);

    default AABB suckAabb() { return HopperBlockEntity.SUCK_AABB; }
}
