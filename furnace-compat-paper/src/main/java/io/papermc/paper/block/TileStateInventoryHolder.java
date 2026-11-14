package io.papermc.paper.block;

import org.bukkit.inventory.Inventory;

/**
 * Paper 1.21.1 TileStateInventoryHolder: 인벤토리가 있는 블록 상태. getInventory 는 놓인 상태면 그 칸 블록 엔티티의 살아 있는 인벤토리
 * (그 region 스레드에서 곧바로 읽고 쓴다), getSnapshotInventory 는 이 상태의 사본(아직 없음 — UnsupportedFeature).
 */
public interface TileStateInventoryHolder extends org.bukkit.block.TileState, org.bukkit.inventory.BlockInventoryHolder {

    @Override
    Inventory getInventory();

    Inventory getSnapshotInventory();
}
