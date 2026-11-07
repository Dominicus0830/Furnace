package org.bukkit.entity;

import org.bukkit.GameMode;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public interface HumanEntity extends LivingEntity, InventoryHolder {

    @Override
    PlayerInventory getInventory();

    /** 옛 이름: 주 손 아이템(비어 있어도 null 이 아니라 공기 스택). */
    ItemStack getItemInHand();

    void setItemInHand(ItemStack item);

    GameMode getGameMode();

    void setGameMode(GameMode mode);
}
