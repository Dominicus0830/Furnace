// 바닐라 InventoryMenu(containerId 0) — 플레이어 인벤토리 창. 칸: 0 제작 결과, 1..4 제작 격자, 5..8 머리·가슴·다리·발, 9..35 주, 36..44 핫바, 45 왼손.
// 제작 격자(2×2)는 PlayerInventory 의 창 칸 1..4 에 저장되고, 결과 칸은 저장하지 않는 한 칸짜리 컨테이너다(바닐라 ResultContainer).
package org.domi.furnace.server.menu;

import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.PlayerInventory;

public class InventoryMenu extends AbstractContainerMenu implements RecipeBookMenu {

    public static final int RESULT_SLOT = 0, CRAFT_SLOT_START = 1, CRAFT_SLOT_END = 5, ARMOR_SLOT_START = 5, ARMOR_SLOT_END = 9,
            INV_SLOT_START = 9, INV_SLOT_END = 36, USE_ROW_SLOT_START = 36, USE_ROW_SLOT_END = 45, SHIELD_SLOT = 45;

    private static final EquipSlot[] ARMOR_ORDER = {EquipSlot.HEAD, EquipSlot.CHEST, EquipSlot.LEGS, EquipSlot.FEET};

    private final CraftingGrid craftSlots;
    private final ResultContainer resultSlots;
    private final PlayerInventoryContainer inventory;
    private final MenuPlayer owner;

    public InventoryMenu(PlayerInventory inv, MenuPlayer owner) {
        super(null, 0);
        this.owner = owner;
        this.inventory = new PlayerInventoryContainer(inv);
        this.craftSlots = new CraftingGrid(new WindowSlotsContainer(inv, 1, 4), 2, 2, this);
        this.resultSlots = new ResultContainer(new SimpleContainer(1));
        addSlot(new ResultSlot(owner, craftSlots, resultSlots, 0));
        for (int i = 0; i < 4; i++) addSlot(new Slot(craftSlots, i));
        for (int i = 0; i < 4; i++) addSlot(new ArmorSlot(inventory, 39 - i, ARMOR_ORDER[i], owner));
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + (row + 1) * 9));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col));
        addSlot(new Slot(inventory, 40));
    }

    public CraftingGrid craftSlots() { return craftSlots; }

    public ResultContainer resultSlots() { return resultSlots; }

    /** 격자가 바뀌었다 — 레시피를 다시 찾아 결과 칸을 채운다(InventoryMenu.slotsChanged → slotChangedCraftingGrid). */
    @Override
    public void slotsChanged(Container container) {
        CraftingLogic.slotChangedCraftingGrid(this, owner, craftSlots, resultSlots, null);
    }

    /** 플레이어 인벤토리 컨테이너(Inventory) — 다른 메뉴가 같은 객체로 플레이어 칸을 만든다(findSlot·transferState 가 컨테이너로 짝을 찾는다). */
    public PlayerInventoryContainer inventoryContainer() { return inventory; }

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
        resultSlots.clearContent();
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

    // --- RecipeBookMenu ---
    @Override public int resultSlotIndex() { return RESULT_SLOT; }
    @Override public int gridWidth() { return 2; }
    @Override public int gridHeight() { return 2; }
    @Override public Slot slot(int index) { return slots.get(index); }
    @Override public int containerId() { return containerId; }

    @Override public void fillCraftSlotsStackedContents(org.domi.furnace.server.recipe.StackedContents sc) {
        for (ItemStack s : craftSlots.items()) sc.accountSimpleStack(s);
    }

    @Override public void clearCraftingContent() {
        for (int i = 0; i < craftSlots.size(); i++) craftSlots.removeNoUpdate(i);
        resultSlots.clearContent();
    }

    @Override public boolean recipeMatches(org.domi.furnace.server.recipe.RecipeManager.CraftingEntry recipe) {
        var rm = owner.recipes();
        return rm != null && recipe.recipe().matches(craftSlots.asInput(), rm.context());
    }
}
