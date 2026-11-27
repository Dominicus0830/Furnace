package net.minecraft.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.domi.furnace.compat.nms.NmsBlockContainer;

/**
 * 바닐라 JukeboxBlockEntity(Mojang 이름 shim, 손 조각). 칸 1개짜리 Container(ContainerSingleItem): 칸 쓰기는 서버의 setTheItem(has_record 상태·재생 시작·정지)으로 이어지고,
 * 빼기는 수와 관계없이 음반 통째로 나온다(splitTheItem).
 */
class JukeboxBlockEntity {

    public JukeboxBlockEntity(BlockPos pos, BlockState state) { this(BlockEntityType.furnaceOf("minecraft:jukebox"), pos, state); }

    protected JukeboxBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }

    /** Paper: 이 용기를 연 사람들. */
    public java.util.List<org.bukkit.entity.HumanEntity> transaction = new java.util.ArrayList<>();
    private int maxStack = 1;

    @Override
    public void clearContent() { furnaceClearContent(); }

    @Override
    public int getContainerSize() { return NmsBlockContainer.size(this); }

    @Override
    public boolean isEmpty() { return NmsBlockContainer.isEmpty(this); }

    /** 바닐라 ContainerSingleItem: 칸 0 만 있고 다른 칸은 던지지 않고 빈 칸으로 본다. */
    @Override
    public ItemStack getItem(int slot) { return slot == 0 ? NmsBlockContainer.getItem(this, 0) : ItemStack.EMPTY; }

    @Override
    public void setItem(int slot, ItemStack stack) { if (slot == 0) NmsBlockContainer.setItem(this, 0, stack, getMaxStackSize()); }

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
    public ItemStack removeItem(int slot, int amount) { return slot != 0 ? ItemStack.EMPTY : NmsBlockContainer.removeItem(this, slot, Integer.MAX_VALUE); }

    public ItemStack getTheItem() { return getItem(0); }

    public void setTheItem(ItemStack stack) { setItem(0, stack); }

    public ItemStack splitTheItem(int amount) { return removeItem(0, amount); }

    /** 바닐라 canPlaceItem: jukebox_playable 컴포넌트가 있고 칸이 비었다. */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) { 
        if (stack.isEmpty() || !getItem(slot).isEmpty()) return false;
        var v = stack.furnaceValue();
        if (v.removedComponents().contains("minecraft:jukebox_playable")) return false;
        // 1.21.1 바닐라에서 기본으로 그 구성 요소를 가진 아이템은 음반(music_disc_*)뿐이고, 그 밖의 아이템은 스택에 직접 달았을 때만
        return v.components().containsKey("minecraft:jukebox_playable") || v.type().startsWith("minecraft:music_disc_");
    }
}
