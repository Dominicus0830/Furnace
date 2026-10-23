// 바닐라 InventoryMenu(containerId 0) — 플레이어 인벤토리 창. 칸: 0 제작 결과, 1..4 제작 격자, 5..8 머리·가슴·다리·발, 9..35 주, 36..44 핫바, 45 왼손.
// 제작 결과 칸은 아직 레시피 매칭이 없어 항상 비어 있다 — TODO(제작): 4-2 제작 단계에서 ResultSlot·slotChangedCraftingGrid.
package org.domi.furnace.server.menu;

import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.PlayerInventory;

public class InventoryMenu extends AbstractContainerMenu {

    public static final int RESULT_SLOT = 0, CRAFT_SLOT_START = 1, CRAFT_SLOT_END = 5, ARMOR_SLOT_START = 5, ARMOR_SLOT_END = 9,
            INV_SLOT_START = 9, INV_SLOT_END = 36, USE_ROW_SLOT_START = 36, USE_ROW_SLOT_END = 45, SHIELD_SLOT = 45;

    private static final EquipSlot[] ARMOR_ORDER = {EquipSlot.HEAD, EquipSlot.CHEST, EquipSlot.LEGS, EquipSlot.FEET};

    private final Container craftSlots, resultSlots;
    private final PlayerInventoryContainer inventory;

    public InventoryMenu(PlayerInventory inv, MenuPlayer owner) {
        super(null, 0);
        this.inventory = new PlayerInventoryContainer(inv);
        this.craftSlots = new WindowSlotsContainer(inv, 1, 4);
        this.resultSlots = new WindowSlotsContainer(inv, 0, 1);
        addSlot(new Slot(resultSlots, 0) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        for (int i = 0; i < 4; i++) addSlot(new Slot(craftSlots, i));
        for (int i = 0; i < 4; i++) addSlot(new ArmorSlot(inventory, 39 - i, ARMOR_ORDER[i], owner));
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + (row + 1) * 9));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col));
        addSlot(new Slot(inventory, 40));
    }

    public Container craftSlots() { return craftSlots; }

    /** 방어구 칸 — 그 칸 장비만, 하나만, 결속 저주가 있으면 서바이벌에서는 못 뺀다. */
    private static final class ArmorSlot extends Slot {
        private final EquipSlot type;
        private final MenuPlayer owner;

        ArmorSlot(Container c, int index, EquipSlot type, MenuPlayer owner) {
            super(c, index);
            this.type = type;
            this.owner = owner;
        }

        @Override public int getMaxStackSize() { return 1; }

        @Override public boolean mayPlace(ItemStack stack) { return type == owner.equipmentSlotFor(stack); }

        @Override public boolean mayPickup(MenuPlayer player) {
            ItemStack cur = getItem();
            return cur.isEmpty() || player.isCreative() || !player.hasBindingCurse(cur);
        }
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) { return slot.container != resultSlots; }

    @Override
    public boolean stillValid(MenuPlayer player) { return true; }

    @Override
    public void removed(MenuPlayer player) {
        super.removed(player);
        resultSlots.removeNoUpdate(0);
        clearContainer(player, craftSlots);
    }

    @Override
    public ItemStack quickMoveStack(MenuPlayer player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return result;
        ItemStack stack = slot.getItem();
        result = stack;
        EquipSlot eq = player.equipmentSlotFor(result);
        Moved moved;
        if (index == RESULT_SLOT) {
            moved = moveItemStackTo(stack, INV_SLOT_START, USE_ROW_SLOT_END, true);
        } else if (index >= CRAFT_SLOT_START && index < CRAFT_SLOT_END) {
            moved = moveItemStackTo(stack, INV_SLOT_START, USE_ROW_SLOT_END, false);
        } else if (index >= ARMOR_SLOT_START && index < ARMOR_SLOT_END) {
            moved = moveItemStackTo(stack, INV_SLOT_START, USE_ROW_SLOT_END, false);
        } else if (eq.isArmor() && !slots.get(8 - armorIndex(eq)).hasItem()) {
            int target = 8 - armorIndex(eq);
            moved = moveItemStackTo(stack, target, target + 1, false);
        } else if (eq == EquipSlot.OFFHAND && !slots.get(SHIELD_SLOT).hasItem()) {
            moved = moveItemStackTo(stack, SHIELD_SLOT, SHIELD_SLOT + 1, false);
        } else if (index >= INV_SLOT_START && index < INV_SLOT_END) {
            moved = moveItemStackTo(stack, USE_ROW_SLOT_START, USE_ROW_SLOT_END, false);
        } else if (index >= USE_ROW_SLOT_START && index < USE_ROW_SLOT_END) {
            moved = moveItemStackTo(stack, INV_SLOT_START, INV_SLOT_END, false);
        } else {
            moved = moveItemStackTo(stack, INV_SLOT_START, USE_ROW_SLOT_END, false);
        }
        if (!moved.changed()) return ItemStack.EMPTY;
        stack = moved.remaining();
        if (index == RESULT_SLOT) slot.onQuickCraft(stack, result);
        // 바닐라는 stack 이 칸의 그 객체라 count 가 이미 줄어 있다 — 불변 스택이라 남은 것을 칸에 다시 쓴다.
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY, result);
        else slot.setCountInPlace(stack);
        if (stack.count() == result.count()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        if (index == RESULT_SLOT) player.drop(stack, false);
        return result;
    }

    /** EquipmentSlot.getIndex(): 발 0, 다리 1, 가슴 2, 머리 3. */
    private static int armorIndex(EquipSlot s) {
        return switch (s) {
            case FEET -> 0;
            case LEGS -> 1;
            case CHEST -> 2;
            default -> 3;
        };
    }
}
