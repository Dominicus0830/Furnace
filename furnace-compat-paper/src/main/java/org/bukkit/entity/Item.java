package org.bukkit.entity;

import org.bukkit.inventory.ItemStack;

/** 떨어진 아이템 엔티티(Bukkit 의 일부). 호환 계층은 아직 아이템 엔티티를 이 타입으로 보이지 않는다(CraftItem 없음). */
public interface Item extends Entity {

    ItemStack getItemStack();

    void setItemStack(ItemStack stack);

    int getPickupDelay();

    void setPickupDelay(int delay);
}
