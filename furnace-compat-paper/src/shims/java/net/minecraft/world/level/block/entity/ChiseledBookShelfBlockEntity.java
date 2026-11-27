package net.minecraft.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.domi.furnace.compat.nms.NmsBlockContainer;

/**
 * 바닐라 ChiseledBookShelfBlockEntity(Mojang 이름 shim, 손 조각). 책 6칸·칸당 1권. 칸 쓰기는 서버의 칸 쓰기(마지막 바뀐 칸·블록 상태 갱신)로 이어진다.
 */
class ChiseledBookShelfBlockEntity {

    public ChiseledBookShelfBlockEntity(BlockPos pos, BlockState state) { this(BlockEntityType.furnaceOf("minecraft:chiseled_bookshelf"), pos, state); }

    protected ChiseledBookShelfBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }

    /** Paper: 이 용기를 연 사람들. */
    public java.util.List<org.bukkit.entity.HumanEntity> transaction = new java.util.ArrayList<>();
    private int maxStack = 1;

    @Override
    public void clearContent() { furnaceClearContent(); }

    @Override
    public int getContainerSize() { return NmsBlockContainer.size(this); }

    @Override
    public boolean isEmpty() { return NmsBlockContainer.isEmpty(this); }

    @Override
    public ItemStack getItem(int slot) { return NmsBlockContainer.getItem(this, slot); }

    @Override
    public void setItem(int slot, ItemStack stack) { NmsBlockContainer.setItem(this, slot, stack, getMaxStackSize()); }

    @Override
    public ItemStack removeItemNoUpdate(int slot) { return NmsBlockContainer.removeItemNoUpdate(this, slot); }

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

    @Override
    public org.bukkit.Location getLocation() {
        if (level == null) return null;
        return new org.bukkit.Location(level.getWorld(), worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
    }

    @Override
    public ItemStack removeItem(int slot, int amount) { return NmsBlockContainer.removeItem(this, slot, amount); }

    /** 바닐라 count: 책이 든 칸 수. */
    public int count() {
        int n = 0;
        for (int i = 0; i < getContainerSize(); i++) if (!getItem(i).isEmpty()) n++;
        return n;
    }

    /** 바닐라 canPlaceItem: bookshelf_books 태그, 빈 칸, 수는 최대 쌓기(1). */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return stack.is(net.minecraft.tags.ItemTags.BOOKSHELF_BOOKS) && getItem(slot).isEmpty() && stack.getCount() == getMaxStackSize();
    }
}
