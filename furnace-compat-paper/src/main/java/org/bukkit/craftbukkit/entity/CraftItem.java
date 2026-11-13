package org.bukkit.craftbukkit.entity;

import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.domi.furnace.compat.paper.CompatItems;
import org.domi.furnace.compat.paper.UnsupportedFeature;

/**
 * 떨어진 아이템 엔티티(minecraft:item) 위의 Bukkit {@link Item}. 아이템·줍기 지연은 핸들을 만든 순간의 값이다(Furnace 핸들은 스냅숏).
 * 쓰기는 아직 이을 곳이 없어 UnsupportedFeature 로 큰 소리로 실패한다 — 조용히 무시하지 않는다.
 */
public class CraftItem extends CraftEntity implements Item {

    public CraftItem(org.domi.furnace.api.entity.Entity handle) { super(handle); }

    @Override
    public ItemStack getItemStack() {
        var stack = furnaceHandle().droppedItem();
        return stack == null ? new ItemStack(org.bukkit.Material.AIR) : CompatItems.copyOf(stack);   // CraftItem.getItemStack 은 복사본
    }

    @Override
    public void setItemStack(ItemStack stack) { throw new UnsupportedFeature("Item#setItemStack", "Furnace 핸들은 떨어진 아이템을 바꾸는 길이 아직 없다"); }

    @Override
    public int getPickupDelay() { return Math.max(0, furnaceHandle().pickupDelay()); }

    @Override
    public void setPickupDelay(int delay) { throw new UnsupportedFeature("Item#setPickupDelay", "Furnace 핸들은 줍기 지연을 바꾸는 길이 아직 없다"); }
}
