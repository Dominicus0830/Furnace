// 바닐라 ChestBoat: 한 자리(앞으로 0.15) 배 + 27 칸 상자. 부서지거나 죽어 지워지면 내용물을 쏟는다.
// 웅크려 우클릭(또는 자리가 없을 때)·타고 인벤토리 키로 상자 창(ChestMenu 9×3)을 연다 — 열 때 전리품 표를 푼다. TODO(피글린): 화내기.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;

public class ChestBoat extends Boat implements ContainerEntity {

    public static final int SIZE = 27;
    private final ItemStack[] items = new ItemStack[SIZE];
    /** 열지 않은 전리품 표와 씨앗. */
    private String lootTable;
    private long lootTableSeed;

    public ChestBoat(ChunkLevel level, int id) {
        super(EntityType.CHEST_BOAT, level, id);
        java.util.Arrays.fill(items, ItemStack.EMPTY);
    }

    public ItemStack getItem(int i) { return items[i]; }

    public void setItem(int i, ItemStack s) { items[i] = s; }

    @Override public ItemStack[] chestVehicleItems() { return items; }

    @Override public String lootTable() { return lootTable; }

    @Override public void setLootTable(String t) { lootTable = t; }

    @Override public long lootTableSeed() { return lootTableSeed; }

    public void setLootTable(String t, long seed) {
        lootTable = t;
        lootTableSeed = seed;
    }

    @Override public Entity asEntity() { return this; }

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

    /**
     * interact: 웅크리지 않았으면 배처럼 타 본다. 자리가 남았는데 웅크리지 않았으면 PASS, 아니면 상자 창.
     * TODO(게임 이벤트): container_open. TODO(피글린): PiglinAi.angerNearbyPiglins(player, true).
     */
    @Override
    public boolean interact(Player p, int hand) {
        if (!p.sneaking() && super.interact(p, hand)) return true;
        if (canAddPassenger(p) && !p.sneaking()) return false;
        interactWithContainerVehicle(p);
        return true;
    }

    /** openCustomInventoryScreen: 타고 인벤토리 키를 누르면 상자 창. TODO(게임 이벤트)·TODO(피글린). */
    public void openCustomInventoryScreen(Player p) { interactWithContainerVehicle(p); }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        if (lootTable != null) {   // addChestVehicleSaveData: 표가 있으면 아이템 대신
            tag.putString("LootTable", lootTable);
            if (lootTableSeed != 0L) tag.putLong("LootTableSeed", lootTableSeed);
            return;
        }
        NbtList list = new NbtList(org.domi.furnace.server.net.io.nbt.NbtIo.TAG_COMPOUND);
        for (int i = 0; i < SIZE; i++) {
            if (items[i].isEmpty()) continue;
            NbtCompound t = ItemStackNbt.write(items[i]);
            t.putByte("Slot", (byte) i);
            list.add(t);
        }
        tag.put("Items", list);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        java.util.Arrays.fill(items, ItemStack.EMPTY);
        if (tag.tags().get("LootTable") instanceof String t) {   // readChestVehicleSaveData
            lootTable = t;
            lootTableSeed = tag.tags().get("LootTableSeed") instanceof Long seed ? seed : 0L;
            return;
        }
        lootTable = null;
        NbtList list = tag.getList("Items");
        if (list == null) return;
        for (Object o : list.items()) {
            if (!(o instanceof NbtCompound t)) continue;
            int slot = t.getByte("Slot") & 255;
            if (slot < SIZE) items[slot] = ItemStackNbt.read(t);
        }
    }
}
