// 바닐라 InventoryCarrier: 아이템을 주워 담는 작은 가방을 가진 몹(알레이·피글린·마을 주민 등).
package org.domi.furnace.server.entity;

import org.domi.furnace.server.net.io.nbt.NbtCompound;

public interface InventoryCarrier {
    String INVENTORY_TAG = "Inventory";

    MobInventory getInventory();

    /** InventoryCarrier.pickUpItem: 원하는 아이템이고 가방에 들어가면 주워 담는다. */
    static void pickUpItem(Mob mob, InventoryCarrier carrier, ItemEntity itemEntity) {
        ItemStack stack = itemEntity.getItem();
        if (mob.wantsToPickUp(stack)) {
            MobInventory inv = carrier.getInventory();
            if (!inv.canAddItem(stack)) return;
            mob.onItemPickup(itemEntity);
            int n = stack.count();
            ItemStack remaining = inv.addItem(stack);
            itemEntity.broadcastTake(mob, n - remaining.count());
            if (remaining.isEmpty()) itemEntity.discard();
            else itemEntity.setItem(remaining);
        }
    }

    default void readInventoryFromSave(NbtCompound tag) {
        if (tag.tags().get(INVENTORY_TAG) instanceof org.domi.furnace.server.net.io.nbt.NbtList l) getInventory().fromTag(l);
    }

    default void writeInventoryToSave(NbtCompound tag) { tag.putList(INVENTORY_TAG, getInventory().createTag()); }
}
