// 엔더 상자 메뉴의 용기: 플레이어의 엔더 인벤토리(EnderChestInventory 27칸)를 보여 주고, 여는 수·소리는 그 엔더 상자 블록 엔티티가 센다.
package org.domi.furnace.server.world;

import org.domi.furnace.server.block.entity.EnderChestBlockEntity;
import org.domi.furnace.server.block.entity.OpenersBlockEntity;
import org.domi.furnace.server.entity.EnderChestInventory;
import org.domi.furnace.server.entity.ItemStack;

public final class EnderChestContainer extends OpenableContainer {

    private final EnderChestInventory storage;
    private final EnderChestBlockEntity chest;

    public EnderChestContainer(EnderChestInventory storage, EnderChestBlockEntity chest, RegionRunner runner) {
        super(runner);
        this.storage = storage;
        this.chest = chest;
    }

    @Override public OpenersBlockEntity opener() { return chest; }

    @Override public int size() { return EnderChestInventory.SIZE; }

    @Override public ItemStack get(int i) { return storage.get(i); }

    @Override public void set(int i, ItemStack s) { storage.set(i, s); }

    @Override public ItemStack remove(int i, int amount) {
        ItemStack cur = storage.get(i);
        if (cur.isEmpty() || amount <= 0) return ItemStack.EMPTY;
        int n = Math.min(amount, cur.count());
        storage.set(i, cur.withCount(cur.count() - n));
        return cur.withCount(n);
    }

    @Override public ItemStack removeNoUpdate(int i) {
        ItemStack cur = storage.get(i);
        storage.set(i, ItemStack.EMPTY);
        return cur;
    }

    @Override public void setChanged() { }
}
