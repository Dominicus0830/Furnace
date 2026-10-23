// 바닐라 CartographyTableMenu(지도 제작대, cartography_table) — 칸: 0 지도, 1 종이/빈 지도/유리판, 2 결과, 3..29 주 인벤토리, 30..38 핫바.
package org.domi.furnace.server.menu;

import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemTools;

public class CartographyMenu extends AbstractContainerMenu {

    public static final int MAP_SLOT = 0, ADDITIONAL_SLOT = 1, RESULT_SLOT = 2, INV_SLOT_START = 3, INV_SLOT_END = 30,
            USE_ROW_SLOT_START = 30, USE_ROW_SLOT_END = 39;

    private final int[] blockPos;
    private final BlockAccess access;
    private long lastSoundTime;
    private final Container container;
    private final ResultContainer resultContainer;

    public CartographyMenu(int containerId, Container playerInventory, MenuPlayer owner, int[] blockPos, BlockAccess access) {
        super("minecraft:cartography_table", containerId);
        this.blockPos = blockPos;
        this.access = access;
        this.container = new SimpleContainer(2) {
            @Override public void setChanged() {
                slotsChanged(this);   // 바닐라는 slotsChanged 를 먼저 부르고 상위 setChanged 를 한다
                super.setChanged();
            }
        };
        this.resultContainer = new ResultContainer(new SimpleContainer(1)) {
            @Override public void setChanged() {
                slotsChanged(this);
                super.setChanged();
            }
        };
        addSlot(new Slot(container, 0) {
            @Override public boolean mayPlace(ItemStack stack) { return is(stack, "filled_map"); }
        });
        addSlot(new Slot(container, 1) {
            @Override public boolean mayPlace(ItemStack stack) { return is(stack, "paper") || is(stack, "map") || is(stack, "glass_pane"); }
        });
        addSlot(new Slot(resultContainer, 0) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }

            @Override public void onTake(MenuPlayer player, ItemStack stack) {
                slots.get(0).remove(1);
                slots.get(1).remove(1);
                // TODO(지도): MapItem.onCraftedBy — map_post_processing(잠금·배율)을 지도 저장 데이터에 적용한다.
                long now = access.gameTime();
                if (lastSoundTime != now) {
                    access.playSound("minecraft:ui.cartography_table.take_result");
                    lastSoundTime = now;
                }
                super.onTake(player, stack);
            }
        });
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(playerInventory, c + r * 9 + 9));
        for (int c = 0; c < 9; c++) addSlot(new Slot(playerInventory, c));
    }

    private static boolean is(ItemStack s, String item) { return ("minecraft:" + item).equals(ItemTools.itemName(s)); }

    @Override public int[] containerBlockPos() { return blockPos; }

    @Override public boolean stillValid(MenuPlayer player) {
        return access.stillBlock() && player.distanceToSqr(blockPos[0] + 0.5, blockPos[1] + 0.5, blockPos[2] + 0.5) <= 64.0;
    }

    @Override
    public void slotsChanged(Container c) {
        ItemStack map = container.get(0), aux = container.get(1), result = resultContainer.get(0);
        if (result.isEmpty() || (!map.isEmpty() || !aux.isEmpty())) {
            if (!map.isEmpty() && !aux.isEmpty()) setupResultSlot(map, aux, result);
        } else {
            resultContainer.removeNoUpdate(0);
        }
    }

    private void setupResultSlot(ItemStack map, ItemStack aux, ItemStack current) {
        BlockAccess.MapInfo data = access.mapInfo(map);
        if (data == null) return;
        ItemStack out;
        if (is(aux, "paper") && !data.locked() && data.scale() < 4) {
            out = ItemTools.with(map.withCount(1), "minecraft:map_post_processing", "scale");
            broadcastChanges();
        } else if (is(aux, "glass_pane") && !data.locked()) {
            out = ItemTools.with(map.withCount(1), "minecraft:map_post_processing", "lock");
            broadcastChanges();
        } else {
            if (!is(aux, "map")) {
                resultContainer.removeNoUpdate(0);
                broadcastChanges();
                return;
            }
            out = map.withCount(2);
            broadcastChanges();
        }
        if (!out.equals(current)) {
            resultContainer.set(0, out);
            broadcastChanges();
        }
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) { return slot.container != resultContainer && super.canTakeItemForPickAll(stack, slot); }

    @Override
    public ItemStack quickMoveStack(MenuPlayer player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return result;
        ItemStack stack = slot.getItem();
        result = stack;
        Moved moved;
        if (index == RESULT_SLOT) {
            // TODO(지도): MapItem.onCraftedBy
            moved = moveItemStackTo(stack, INV_SLOT_START, USE_ROW_SLOT_END, true);
            if (!moved.changed()) return ItemStack.EMPTY;
            slot.onQuickCraft(moved.remaining(), result);
        } else if (index != ADDITIONAL_SLOT && index != MAP_SLOT) {
            if (is(stack, "filled_map")) {
                moved = moveItemStackTo(stack, MAP_SLOT, MAP_SLOT + 1, false);
            } else if (is(stack, "paper") || is(stack, "map") || is(stack, "glass_pane")) {
                moved = moveItemStackTo(stack, ADDITIONAL_SLOT, RESULT_SLOT, false);
            } else if (index >= INV_SLOT_START && index < INV_SLOT_END) {
                moved = moveItemStackTo(stack, USE_ROW_SLOT_START, USE_ROW_SLOT_END, false);
            } else {
                moved = moveItemStackTo(stack, INV_SLOT_START, INV_SLOT_END, false);
            }
            if (!moved.changed()) return ItemStack.EMPTY;
        } else {
            moved = moveItemStackTo(stack, INV_SLOT_START, USE_ROW_SLOT_END, false);
            if (!moved.changed()) return ItemStack.EMPTY;
        }
        stack = moved.remaining();
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setCountInPlace(stack);
        slot.setChanged();
        if (stack.count() == result.count()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        broadcastChanges();
        return result;
    }

    @Override
    public void removed(MenuPlayer player) {
        super.removed(player);
        resultContainer.removeNoUpdate(0);
        clearContainer(player, container);
    }
}
