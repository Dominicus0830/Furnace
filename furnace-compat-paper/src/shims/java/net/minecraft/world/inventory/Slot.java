package net.minecraft.world.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 창의 칸 하나(Mojang 이름 shim): 컨테이너의 한 칸을 창 번호({@link #index})로 보는 창구. 서버가 연 창의 칸은 호환 모듈이 만들고, 플레이어 인벤토리 칸은 그
 * 플레이어의 {@link net.minecraft.world.entity.player.Inventory}, 창이 가진 컨테이너 칸은 그 창의 컨테이너 보기를 컨테이너로 가진다. 화면 좌표({@link #x}·{@link #y})는
 * 서버에 없어 서버 창에서는 0 이다.
 */
class Slot {

    private final int slot;
    public int index;
    public final int x;
    public final int y;
    public final Container container;

    public Slot(Container container, int slot, int x, int y) {
        this.container = container;
        this.slot = slot;
        this.x = x;
        this.y = y;
    }

    public ItemStack getItem() { return container.getItem(slot); }

    public boolean hasItem() { return !getItem().isEmpty(); }

    public void set(ItemStack stack) {
        container.setItem(slot, stack);
        setChanged();
    }

    /** 바닐라: 플레이어가 놓은 값이 칸에 들어간다(서버 창에서는 {@link #set} 과 같다). */
    public void setByPlayer(ItemStack stack) { set(stack); }

    public void setChanged() { container.setChanged(); }

    public int getContainerSlot() { return slot; }

    public int getMaxStackSize() { return container.getMaxStackSize(); }

    public int getMaxStackSize(ItemStack stack) { return Math.min(getMaxStackSize(), stack.getMaxStackSize()); }

    public ItemStack remove(int amount) { return container.removeItem(slot, amount); }

    public boolean mayPlace(ItemStack stack) { return true; }

    public boolean mayPickup(Player player) { return true; }

    public boolean isActive() { return true; }

    public boolean isFake() { return false; }

    public boolean isHighlightable() { return true; }

    public boolean allowModification(Player player) { return mayPickup(player) && mayPlace(getItem()); }
}
