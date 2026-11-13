// 바닐라 HorseInventoryMenu — 0 안장, 1 몸통 갑옷, 2.. 상자 칸(열 수 × 3, 당나귀·노새는 5열 15칸), 그 뒤 플레이어 주 27칸·핫바 9칸.
// 화면은 open_screen 이 아니라 horse_screen_open 으로 연다(메뉴 종류 없음). 클릭은 말이 있는 region 에서 처리한다(containerBlockPos).
package org.domi.furnace.server.menu;

import org.domi.furnace.server.entity.AbstractHorse;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.world.MenuBlockContext;

public class HorseInventoryMenu extends AbstractContainerMenu {

    private final Container horseContainer;
    private final Container armorContainer;
    private final AbstractHorse horse;
    private final int columns;

    public HorseInventoryMenu(int containerId, Container playerInventory, Container horseContainer, AbstractHorse horse, int columns, MenuPlayer opener) {
        super(null, containerId);
        this.horseContainer = horseContainer;
        this.armorContainer = horse.bodyArmorAccess();
        this.horse = horse;
        this.columns = columns;
        horseContainer.startOpen(opener);
        addSlot(new Slot(horseContainer, 0) {
            @Override public boolean mayPlace(ItemStack stack) { return horse.isSaddleItem(stack) && !hasItem() && horse.isSaddleable(); }

            @Override public boolean isActive() { return horse.isSaddleable(); }
        });
        addSlot(new BodyArmorSlot(armorContainer, horse));
        if (columns > 0) {
            for (int r = 0; r < 3; r++) for (int c = 0; c < columns; c++) addSlot(new Slot(horseContainer, 1 + c + r * columns));
        }
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(playerInventory, c + r * 9 + 9));
        for (int c = 0; c < 9; c++) addSlot(new Slot(playerInventory, c));
    }

    /** ArmorSlot(몸통): 하나만, 그 말이 입을 수 있는 갑옷만, 결속 저주면 서바이벌에서 못 뺀다. 놓을 때 말의 onEquipItem. */
    private static final class BodyArmorSlot extends Slot {
        private final AbstractHorse horse;

        BodyArmorSlot(Container c, AbstractHorse horse) {
            super(c, 0);
            this.horse = horse;
        }

        @Override public int getMaxStackSize() { return 1; }

        @Override public boolean mayPlace(ItemStack stack) { return horse.isBodyArmorItem(stack); }

        @Override public boolean isActive() { return horse.canUseBodySlot(); }

        @Override public void setByPlayer(ItemStack newStack, ItemStack oldStack) {
            horse.onBodyArmorEquip(oldStack, newStack);
            super.setByPlayer(newStack, oldStack);
        }

        @Override public boolean mayPickup(MenuPlayer player) {
            ItemStack cur = getItem();
            return cur.isEmpty() || player.isCreative() || !player.hasBindingCurse(cur);
        }
    }

    public AbstractHorse horse() { return horse; }

    public int columns() { return columns; }

    /** 말이 있는 곳(움직인다) — 클릭·유효성 검사를 그 region 에서. */
    @Override public int[] containerBlockPos() { return MenuBlockContext.blockPos(horse); }

    /** stillValid: 인벤토리가 바뀌지 않았고(상자를 떼거나 달면 새 인벤토리) 말이 살아 있으며 4칸 여유 안. */
    @Override
    public boolean stillValid(MenuPlayer player) {
        return MenuBlockContext.ownsEntity(horse) && !horse.hasInventoryChanged(horseContainer) && horseContainer.stillValid(player)
                && armorContainer.stillValid(player) && horse.isAlive() && player.canInteractWithEntity(horse.boundingBox(), 4.0);
    }

    @Override public org.domi.furnace.api.event.inventory.InventoryCloseEvent.Reason invalidCloseReason() { return reasonForEntity(horse); }

    /**
     * quickMoveStack: 말 칸 → 플레이어(끝에서부터). 플레이어 → 갑옷(비었으면) → 안장 → 상자 칸, 다 안 되면 주 ↔ 핫바.
     * 바닐라는 주 ↔ 핫바로 옮긴 뒤에도 EMPTY 를 돌려준다(그 자리 스택만 줄어든다).
     */
    @Override
    public ItemStack quickMoveStack(MenuPlayer player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack;
            int n = horseContainer.size() + 1;
            Moved moved;
            if (index < n) {
                moved = moveItemStackTo(stack, n, slots.size(), true);
                if (!moved.changed()) return ItemStack.EMPTY;
            } else if (getSlot(1).mayPlace(stack) && !getSlot(1).hasItem()) {
                moved = moveItemStackTo(stack, 1, 2, false);
                if (!moved.changed()) return ItemStack.EMPTY;
            } else if (getSlot(0).mayPlace(stack)) {
                moved = moveItemStackTo(stack, 0, 1, false);
                if (!moved.changed()) return ItemStack.EMPTY;
            } else if (n <= 1 || !(moved = moveItemStackTo(stack, 2, n, false)).changed()) {
                int inv = n + 27, hot = inv + 9;
                Moved m;
                if (index >= inv && index < hot) m = moveItemStackTo(stack, n, inv, false);
                else if (index >= n && index < inv) m = moveItemStackTo(stack, inv, hot, false);
                else m = moveItemStackTo(stack, inv, inv, false);
                if (m.changed()) slot.setCountInPlace(m.remaining());
                return ItemStack.EMPTY;
            }
            if (moved.remaining().isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
            else slot.setCountInPlace(moved.remaining());
        }
        return result;
    }

    @Override
    public void removed(MenuPlayer player) {
        super.removed(player);
        horseContainer.stopOpen(player);
    }
}
