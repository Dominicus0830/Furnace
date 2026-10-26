// 바닐라 AbstractMinecartContainer(+ ContainerEntity): 아이템 칸을 가진 수레. 부서지거나 죽어 지워지면 내용물을 쏟는다.
// 감속은 찬 정도(비교기 신호)에 따라 0.98~0.995. TODO(메뉴): 엔티티 용기 창(상자 9×3·깔때기), 전리품 표 풀기·피글린 화내기.
package org.domi.furnace.server.entity;

import java.util.Arrays;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;

public abstract class AbstractMinecartContainer extends AbstractMinecart {

    private final ItemStack[] items;
    /** 열지 않은 전리품 표(LootTable)와 씨앗 — 저장 왕복만. */
    private String lootTable;
    private long lootTableSeed;

    protected AbstractMinecartContainer(EntityType type, ChunkLevel level, int id, int size) {
        super(type, level, id);
        items = new ItemStack[size];
        Arrays.fill(items, ItemStack.EMPTY);
    }

    public int size() { return items.length; }

    public ItemStack getItem(int i) { return items[i]; }

    public void setItem(int i, ItemStack s) { items[i] = s == null ? ItemStack.EMPTY : s; }

    public String lootTable() { return lootTable; }

    /** destroy(source): 수레 아이템(그 안의 kill → remove 가 내용물을 먼저 쏟는다) 뒤 chestVehicleDestroyed. TODO(피글린): 플레이어가 부수면 화내기. */
    @Override
    protected void destroy(Damage source) {
        super.destroy(source);
        if (level.gameRule("doEntityDrops")) dropContents();
    }

    /** remove: 없애는 이유(죽음·버림)면 내용물을 쏟는다. */
    @Override
    public void remove(RemovalReason reason) {
        if (reason.shouldDestroy()) dropContents();
        super.remove(reason);
    }

    private void dropContents() {
        for (int i = 0; i < items.length; i++) {
            ItemStack s = items[i];
            items[i] = ItemStack.EMPTY;
            if (!s.isEmpty()) level.dropItemStack(x(), y(), z(), s);
        }
    }

    /** applyNaturalSlowdown: 0.98 + (전리품 표가 없으면) (15 − 찬 정도 신호) × 0.001, 물속이면 0.95 배. 세로는 0. */
    @Override
    protected void applyNaturalSlowdown() {
        float f = 0.98F;
        if (lootTable == null) {
            int i = 15 - redstoneSignal();
            f += i * 0.001F;
        }
        if (isInWater()) f *= 0.95F;
        setDeltaMovement(vx() * f, vy() * 0.0, vz() * f);
    }

    /** AbstractContainerMenu.getRedstoneSignalFromContainer(this). */
    public int redstoneSignal() {
        return org.domi.furnace.server.block.types.ChestBlock.redstoneSignalFromContainer(java.util.List.<ItemStack[]>of(items));
    }

    /** interact: TODO(메뉴) — 바닐라는 interactWithContainerVehicle 로 창을 연다. */
    public boolean interact(Player p, int hand) { return false; }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        if (lootTable != null) {   // addChestVehicleSaveData: 표가 있으면 아이템 대신
            tag.putString("LootTable", lootTable);
            if (lootTableSeed != 0L) tag.putLong("LootTableSeed", lootTableSeed);
            return;
        }
        NbtList list = new NbtList(org.domi.furnace.server.net.io.nbt.NbtIo.TAG_COMPOUND);
        for (int i = 0; i < items.length; i++) {
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
        Arrays.fill(items, ItemStack.EMPTY);
        if (tag.tags().get("LootTable") instanceof String t) {
            lootTable = t;
            lootTableSeed = tag.tags().get("LootTableSeed") instanceof Long s ? s : 0L;
            return;
        }
        lootTable = null;
        NbtList list = tag.getList("Items");
        if (list == null) return;
        for (Object o : list.items()) {
            if (!(o instanceof NbtCompound t)) continue;
            int slot = t.getByte("Slot") & 255;
            if (slot < items.length) items[slot] = ItemStackNbt.read(t);
        }
    }
}
