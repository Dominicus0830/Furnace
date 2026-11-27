// 손 조각 — Container 몸통은 손으로 쓴다(tools/compat/gen-nms-block-entities.py 는 이 파일을 덮어쓰지 않는다: HAND_FRAGMENT).
package net.minecraft.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.domi.furnace.compat.nms.NmsBlockContainer;

/**
 * 바닐라 DecoratedPotBlockEntity(Mojang 이름 shim, N2b-6) — 블록 엔티티 종류 {@code minecraft:decorated_pot}. 종류별 필드·메서드는 두지 않았고
 * 플러그인이 종류별 클래스로 가르는 {@code instanceof} 와 형변환이 돌게 하는 계층 자리다(저장은 {@link BlockEntity} 가 서버의 NBT 로 한다).
 */
class DecoratedPotBlockEntity {

    public DecoratedPotBlockEntity(BlockPos pos, BlockState state) { this(BlockEntityType.furnaceOf("minecraft:decorated_pot"), pos, state); }

    /** 하위 종류가 쓴다(바닐라는 DecoratedPotBlockEntity 이 종류를 받는 생성자를 둔다). */
    protected DecoratedPotBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }

    /** 바닐라 clearContent(Clearable) — 월드의 블록 엔티티면 서버 블록 엔티티를 바닐라처럼 비운다(T-211 3단계). */
    @Override
    public void clearContent() { furnaceClearContent(); }

    // ---- Container(바닐라 ContainerSingleItem 의 기본 몸통 + Paper 추가분) — 칸은 하나다

    /** Paper: 이 용기를 연 사람들. */
    public java.util.List<org.bukkit.entity.HumanEntity> transaction = new java.util.ArrayList<>();
    private int maxStack = 99;

    @Override
    public int getContainerSize() { return 1; }

    @Override
    public boolean isEmpty() { return NmsBlockContainer.isEmpty(this); }

    /** 바닐라: 0 번 칸만 있고 다른 번호는 빈 스택(던지지 않는다). */
    @Override
    public ItemStack getItem(int slot) { return slot == 0 ? NmsBlockContainer.getItem(this, 0) : ItemStack.EMPTY; }

    @Override
    public ItemStack removeItem(int slot, int amount) { return slot == 0 ? NmsBlockContainer.removeItem(this, 0, amount) : ItemStack.EMPTY; }

    /** 바닐라: removeItem(slot, getMaxStackSize()). */
    @Override
    public ItemStack removeItemNoUpdate(int slot) { return removeItem(slot, getMaxStackSize()); }

    @Override
    public void setItem(int slot, ItemStack stack) { if (slot == 0) NmsBlockContainer.setItem(this, 0, stack, getMaxStackSize()); }

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
