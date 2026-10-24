// 바닐라 BrewingStandMenu(양조대, brewing_stand) — 칸: 0..2 병, 3 재료, 4 연료, 5..31 주 인벤토리, 32..40 핫바. 데이터 0 = 양조 시간, 1 = 연료.
package org.domi.furnace.server.menu;

import org.domi.furnace.server.block.entity.BrewingStandBlockEntity;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.item.PotionBrewing;
import org.domi.furnace.server.world.BlockEntityContainer;

public class BrewingStandMenu extends ContainerBackedMenu {

    public static final int BOTTLE_SLOT_START = 0, BOTTLE_SLOT_END = 2, INGREDIENT_SLOT = 3, FUEL_SLOT = 4, SLOT_COUNT = 5,
            INV_SLOT_START = 5, INV_SLOT_END = 32, USE_ROW_SLOT_START = 32, USE_ROW_SLOT_END = 41;

    private final BrewingStandBlockEntity be;

    public BrewingStandMenu(int containerId, Container playerInventory, BlockEntityContainer container, int[] blockPos) {
        super("minecraft:brewing_stand", containerId, container, blockPos);
        checkContainerSize(container, 5);
        this.be = (BrewingStandBlockEntity) container.blockEntity();
        for (int i = 0; i < 3; i++) addSlot(new PotionSlot(container, i));
        addSlot(new Slot(container, 3) {
            @Override public boolean mayPlace(ItemStack stack) { return PotionBrewing.isIngredient(stack); }
        });
        addSlot(new FuelSlot(container, 4));
        addPlayerSlots(playerInventory);
        addDataSlot(DataSlot.of(() -> be.brewTime, v -> be.brewTime = v));
        addDataSlot(DataSlot.of(() -> be.fuel, v -> be.fuel = v));
    }

    private static final class FuelSlot extends Slot {
        FuelSlot(Container c, int i) { super(c, i); }

        @Override public boolean mayPlace(ItemStack stack) { return mayPlaceItem(stack); }

        static boolean mayPlaceItem(ItemStack s) { return "minecraft:blaze_powder".equals(ItemTools.itemName(s)); }
    }

    private static final class PotionSlot extends Slot {
        PotionSlot(Container c, int i) { super(c, i); }

        @Override public boolean mayPlace(ItemStack stack) { return mayPlaceItem(stack); }

        @Override public int getMaxStackSize(ItemStack stack) { return 1; }

        @Override public int getMaxStackSize() { return 1; }

        // TODO(발전 과제): 꺼낼 때 potion 이 있으면 brewed_potion 조건 발동.

        static boolean mayPlaceItem(ItemStack s) { return PotionBrewing.isBottleSlotItem(s); }
    }

    @Override
    public ItemStack quickMoveStack(MenuPlayer player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return result;
        ItemStack stack = slot.getItem();
        result = stack;
        Moved moved;
        if ((index < 0 || index > 2) && index != INGREDIENT_SLOT && index != FUEL_SLOT) {
            if (FuelSlot.mayPlaceItem(result)) {
                // 바닐라: 연료 칸으로 옮겼거나(옮겨졌으면) 재료 칸으로 옮기지 못하면 EMPTY — 옮긴 결과는 이미 칸에 반영된다
                Moved toFuel = moveItemStackTo(stack, FUEL_SLOT, FUEL_SLOT + 1, false);
                if (toFuel.changed()) { writeBack(slot, toFuel); return ItemStack.EMPTY; }
                if (slots.get(INGREDIENT_SLOT).mayPlace(stack)) {
                    Moved toIng = moveItemStackTo(stack, INGREDIENT_SLOT, FUEL_SLOT, false);
                    if (!toIng.changed()) return ItemStack.EMPTY;
                    writeBack(slot, toIng);
                    return ItemStack.EMPTY;
                }
                moved = null;
                return finishWithoutMove();
            } else if (slots.get(INGREDIENT_SLOT).mayPlace(stack)) {
                moved = moveItemStackTo(stack, INGREDIENT_SLOT, FUEL_SLOT, false);
                if (!moved.changed()) return ItemStack.EMPTY;
            } else if (PotionSlot.mayPlaceItem(result)) {
                moved = moveItemStackTo(stack, 0, INGREDIENT_SLOT, false);
                if (!moved.changed()) return ItemStack.EMPTY;
            } else if (index >= INV_SLOT_START && index < INV_SLOT_END) {
                moved = moveItemStackTo(stack, USE_ROW_SLOT_START, USE_ROW_SLOT_END, false);
                if (!moved.changed()) return ItemStack.EMPTY;
            } else if (index >= USE_ROW_SLOT_START && index < USE_ROW_SLOT_END) {
                moved = moveItemStackTo(stack, INV_SLOT_START, INV_SLOT_END, false);
                if (!moved.changed()) return ItemStack.EMPTY;
            } else {
                moved = moveItemStackTo(stack, INV_SLOT_START, USE_ROW_SLOT_END, false);
                if (!moved.changed()) return ItemStack.EMPTY;
            }
        } else {
            moved = moveItemStackTo(stack, INV_SLOT_START, USE_ROW_SLOT_END, true);
            if (!moved.changed()) return ItemStack.EMPTY;
            slot.onQuickCraft(moved.remaining(), result);
        }
        stack = moved.remaining();
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setCountInPlace(stack);
        slot.setChanged();
        if (stack.count() == result.count()) return ItemStack.EMPTY;
        slot.onTake(player, result);   // 바닐라는 여기에 옮기기 전 복사본을 넘긴다
        return result;
    }

    /** 바닐라는 같은 스택 객체를 제자리에서 바꾸므로 일찍 돌아가도 칸에는 반영된다. */
    private void writeBack(Slot slot, Moved m) {
        if (m.remaining().isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setCountInPlace(m.remaining());
    }

    private ItemStack finishWithoutMove() { return ItemStack.EMPTY; }
}
