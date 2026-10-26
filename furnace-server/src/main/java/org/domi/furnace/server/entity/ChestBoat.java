// 바닐라 ChestBoat: 한 자리(앞으로 0.15) 배 + 27 칸 상자. 부서지거나 죽어 지워지면 내용물을 쏟는다.
// TODO(메뉴): 웅크려 우클릭(또는 자리가 없을 때)으로 여는 상자 창(ChestMenu 9×3)·전리품 표(LootTable) 풀기·피글린 화내기.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;

public class ChestBoat extends Boat {

    public static final int SIZE = 27;
    private final ItemStack[] items = new ItemStack[SIZE];

    public ChestBoat(ChunkLevel level, int id) {
        super(EntityType.CHEST_BOAT, level, id);
        java.util.Arrays.fill(items, ItemStack.EMPTY);
    }

    public ItemStack getItem(int i) { return items[i]; }

    public void setItem(int i, ItemStack s) { items[i] = s; }

    @Override protected float getSinglePassengerXOffset() { return 0.15F; }

    @Override protected int getMaxPassengers() { return 1; }

    @Override
    public String dropItem() { return "minecraft:" + getVariant().name + (getVariant() == Type.BAMBOO ? "_chest_raft" : "_chest_boat"); }

    /** destroy: 배 아이템 뒤 chestVehicleDestroyed(엔티티 드롭 규칙이면 내용물). */
    @Override
    protected void destroy() {
        super.destroy();
        if (level.gameRule("doEntityDrops")) dropContents();
    }

    /** remove: 없애는 이유(죽음·버림)면 내용물을 쏟는다(Containers.dropContents — 이미 비었으면 아무것도). */
    @Override
    public void remove(RemovalReason reason) {
        if (reason.shouldDestroy()) dropContents();
        super.remove(reason);
    }

    private void dropContents() {
        for (int i = 0; i < SIZE; i++) {
            ItemStack s = items[i];
            items[i] = ItemStack.EMPTY;
            if (!s.isEmpty()) level.dropItemStack(x(), y(), z(), s);
        }
    }

    /** interact: 웅크리지 않았으면 배처럼 타 보고, 탈 수 없으면 상자를 연다(TODO(메뉴)). */
    @Override
    public boolean interact(Player p, int hand) {
        if (!p.sneaking() && super.interact(p, hand)) return true;
        return false;   // TODO(메뉴): interactWithContainerVehicle(player) → 상자 창
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        NbtList list = new NbtList(org.domi.furnace.server.net.io.nbt.NbtIo.TAG_COMPOUND);
        for (int i = 0; i < SIZE; i++) {
            if (items[i].isEmpty()) continue;
            NbtCompound t = ItemStackNbt.write(items[i]);
            t.putByte("Slot", (byte) i);
            list.add(t);
        }
        tag.put("Items", list);   // TODO(전리품): LootTable·LootTableSeed
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        java.util.Arrays.fill(items, ItemStack.EMPTY);
        NbtList list = tag.getList("Items");
        if (list == null) return;
        for (Object o : list.items()) {
            if (!(o instanceof NbtCompound t)) continue;
            int slot = t.getByte("Slot") & 255;
            if (slot < SIZE) items[slot] = ItemStackNbt.read(t);
        }
    }
}
