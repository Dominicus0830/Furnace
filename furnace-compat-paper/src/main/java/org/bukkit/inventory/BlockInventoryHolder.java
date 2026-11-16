package org.bukkit.inventory;

import org.bukkit.block.Block;

/** Bukkit BlockInventoryHolder: 블록에 붙은 인벤토리의 주인. */
public interface BlockInventoryHolder extends InventoryHolder {

    Block getBlock();
}
