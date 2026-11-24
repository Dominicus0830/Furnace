package net.minecraft.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.domi.furnace.compat.nms.NmsBlockContainer;

/**
 * 바닐라 BaseContainerBlockEntity(Mojang 이름 shim, N2b-6). 칸을 가진 블록 엔티티(상자·통·셜커·깔때기·발사기·화로류·양조대·제작기)의 {@code Container} 몸통은 여기 한 곳에서
 * {@link NmsBlockContainer} 로 이어진다 — 월드의 보기는 서버 칸을 주인 스레드에서, 떨어진 것은 자기 NBT 의 Items 를. 종류별 칸 수는 그 표가 든다.
 * 바닐라는 이 필드·메서드를 구체 클래스마다 가지지만(Paper 가 더한 {@code transaction}·{@code maxStack} 포함) 이 shim 은 공통 상위에 한 번 둔다.
 */
class BaseContainerBlockEntity {

    /** Paper: 이 용기를 연 사람들(onOpen/onClose 가 고친다). */
    public java.util.List<org.bukkit.entity.HumanEntity> transaction = new java.util.ArrayList<>();
    private int maxStack = 99;

    protected BaseContainerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }

    /** 바닐라 clearContent(Clearable) — 월드의 보기면 서버 블록 엔티티를 바닐라처럼 비운다(T-211 3단계). */
    @Override
    public void clearContent() { furnaceClearContent(); }

    @Override
    public int getContainerSize() { return NmsBlockContainer.size(this); }

    @Override
    public boolean isEmpty() { return NmsBlockContainer.isEmpty(this); }

    @Override
    public ItemStack getItem(int slot) { return NmsBlockContainer.getItem(this, slot); }

    @Override
    public ItemStack removeItem(int slot, int amount) { return NmsBlockContainer.removeItem(this, slot, amount); }

    @Override
    public ItemStack removeItemNoUpdate(int slot) { return NmsBlockContainer.removeItemNoUpdate(this, slot); }

    @Override
    public void setItem(int slot, ItemStack stack) { NmsBlockContainer.setItem(this, slot, stack, getMaxStackSize()); }

    @Override
    public java.util.List<ItemStack> getContents() { return NmsBlockContainer.getContents(this); }

    @Override
    public int getMaxStackSize() { return maxStack; }

    @Override
    public void setMaxStackSize(int size) { this.maxStack = size; }

    @Override
    public java.util.List<org.bukkit.entity.HumanEntity> getViewers() { return transaction; }

    @Override
    public void onOpen(org.bukkit.craftbukkit.entity.CraftHumanEntity who) { transaction.add(who); }

    @Override
    public void onClose(org.bukkit.craftbukkit.entity.CraftHumanEntity who) { transaction.remove(who); }

    /** Paper: 그 칸의 블록 상태(InventoryHolder 일 때만, 레벨이 없거나 공기면 null). */
    @Override
    public org.bukkit.inventory.InventoryHolder getOwner() {
        if (level == null) return null;
        org.bukkit.block.Block block = level.getWorld().getBlockAt(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
        if (block.getType() == org.bukkit.Material.AIR) return null;
        org.bukkit.block.BlockState state = block.getState();
        return state instanceof org.bukkit.inventory.InventoryHolder holder ? holder : null;
    }

    @Override
    public org.bukkit.Location getLocation() {
        if (level == null) return null;
        return new org.bukkit.Location(level.getWorld(), worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
    }
}
