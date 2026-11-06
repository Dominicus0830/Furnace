// 바닐라 ServerPlaceRecipe(레시피 북 클릭 → 격자 채우기): 격자를 비우고, 인벤토리 재료를 겹치지 않게 배정해 레시피 모양대로 놓는다.
// 못 채우면 비운 채 고스트 레시피(place_ghost_recipe)를 보낸다.
package org.domi.furnace.server.menu;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.PlayerInventory;
import org.domi.furnace.server.item.EnchantTools;
import org.domi.furnace.server.item.ItemStackCodecs;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.recipe.RecipeManager.CraftingEntry;
import org.domi.furnace.server.recipe.StackedContents;

public final class ServerPlaceRecipe {

    /** 못 채웠을 때 고스트 레시피를 보내는 길. */
    public interface GhostSender { void sendGhost(int containerId, CraftingEntry recipe); }

    private final StackedContents stacked = new StackedContents();
    private final RecipeBookMenu menu;
    private final MenuPlayer player;
    private final PlayerInventory inventory;
    private final GhostSender ghost;

    public ServerPlaceRecipe(RecipeBookMenu menu, MenuPlayer player, PlayerInventory inventory, GhostSender ghost) {
        this.menu = menu;
        this.player = player;
        this.inventory = inventory;
        this.ghost = ghost;
    }

    private ItemStack inv(int i) { return inventory.get(PlayerInventory.windowSlot(i)); }

    /** 바닐라 recipeClicked(player, recipe, shift). */
    public void recipeClicked(CraftingEntry recipe, boolean shift) {
        if (recipe == null || !player.hasRecipe(recipe.id())) return;
        var rm = player.recipes();
        if (rm == null) return;
        if (testClearGrid() || player.isCreative()) {
            stacked.clear();
            for (int i = 0; i < 36; i++) stacked.accountSimpleStack(inv(i));
            menu.fillCraftSlotsStackedContents(stacked);
            if (stacked.canCraft(recipe.recipe().placementIngredients(), rm.context(), null)) {
                handleRecipeClicked(recipe, shift);
            } else {
                clearGrid();
                ghost.sendGhost(menu.containerId(), recipe);
            }
        }
    }

    private void clearGrid() {
        for (int i = 0; i < menu.gridSize(); i++) {
            if (menu.shouldMoveToInventory(i)) {
                ItemStack s = menu.slot(i).getItem();
                if (!s.isEmpty()) player.placeItemBackInInventory(s);   // 못 넣은 것은 던진다(바닐라 placeItemBackInInventory)
            }
        }
        menu.clearCraftingContent();
    }

    private void handleRecipeClicked(CraftingEntry recipe, boolean shift) {
        var rm = player.recipes();
        var ings = recipe.recipe().placementIngredients();
        boolean matches = menu.recipeMatches(recipe);
        int biggest = stacked.getBiggestCraftableStack(ings, rm.context(), Integer.MAX_VALUE, null);
        if (matches) {
            for (int i = 0; i < menu.gridSize(); i++) {
                if (i != menu.resultSlotIndex()) {
                    ItemStack s = menu.slot(i).getItem();
                    if (!s.isEmpty() && Math.min(biggest, ItemStackCodecs.maxStackSize(s)) < s.count() + 1) return;
                }
            }
        }
        int amount = stackSize(shift, biggest, matches);
        List<Integer> list = new ArrayList<>();
        if (stacked.canCraft(ings, rm.context(), list, amount)) {
            int min = amount;
            for (int id : list) {
                ItemStack s = StackedContents.fromStackingIndex(id);
                if (!s.isEmpty()) {
                    int m = ItemStackCodecs.maxStackSize(s);
                    if (m < min) min = m;
                }
            }
            if (stacked.canCraft(ings, rm.context(), list, min)) {
                clearGrid();
                placeRecipe(menu.gridWidth(), menu.gridHeight(), menu.resultSlotIndex(), recipe, list.iterator(), min);
            }
        }
    }

    private int stackSize(boolean shift, int biggest, boolean matches) {
        int n = 1;
        if (shift) {
            n = biggest;
        } else if (matches) {
            n = Integer.MAX_VALUE;
            for (int i = 0; i < menu.gridSize(); i++) {
                if (i != menu.resultSlotIndex()) {
                    ItemStack s = menu.slot(i).getItem();
                    if (!s.isEmpty() && n > s.count()) n = s.count();
                }
            }
            if (n != Integer.MAX_VALUE) n++;
        }
        return n;
    }

    /** 바닐라 PlaceRecipe.placeRecipe: 모양 레시피는 격자 가운데에 맞춰 놓는다. */
    private void placeRecipe(int gridWidth, int gridHeight, int resultSlot, CraftingEntry recipe, java.util.Iterator<Integer> ingredients, int amount) {
        int w = gridWidth, h = gridHeight;
        if (recipe.recipe().shapedWidth() >= 0) {
            w = recipe.recipe().shapedWidth();
            h = recipe.recipe().shapedHeight();
        }
        int slot = 0;
        for (int y = 0; y < gridHeight; y++) {
            if (slot == resultSlot) slot++;
            boolean flag = h < gridHeight / 2.0F;
            int k = (int) Math.floor(gridHeight / 2.0F - h / 2.0F);
            if (flag && k > y) {
                slot += gridWidth;
                y++;
            }
            for (int x = 0; x < gridWidth; x++) {
                if (!ingredients.hasNext()) return;
                flag = w < gridWidth / 2.0F;
                k = (int) Math.floor(gridWidth / 2.0F - w / 2.0F);
                int l = w;
                boolean inside = x < w;
                if (flag) {
                    l = k + w;
                    inside = k <= x && x < k + w;
                }
                if (inside) {
                    addItemToSlot(ingredients.next(), slot, amount);
                } else if (l == x) {
                    slot += gridWidth - x;
                    break;
                }
                slot++;
            }
        }
    }

    private void addItemToSlot(int id, int slotIndex, int amount) {
        Slot slot = menu.slot(slotIndex);
        ItemStack stack = StackedContents.fromStackingIndex(id);
        if (!stack.isEmpty()) {
            int remaining = amount;
            while (remaining > 0) {
                remaining = moveItemToGrid(slot, stack, remaining);
                if (remaining == -1) return;
            }
        }
    }

    private int moveItemToGrid(Slot slot, ItemStack stack, int remaining) {
        int idx = findSlotMatchingUnusedItem(stack);
        if (idx == -1) return -1;
        ItemStack invStack = inv(idx);
        int taken;
        int ws = PlayerInventory.windowSlot(idx);
        if (remaining < invStack.count()) {
            inventory.set(ws, invStack.withCount(invStack.count() - remaining));
            taken = remaining;
        } else {
            inventory.set(ws, ItemStack.EMPTY);
            taken = invStack.count();
        }
        if (slot.getItem().isEmpty()) slot.set(invStack.withCount(taken));
        else slot.set(slot.getItem().withCount(slot.getItem().count() + taken));
        return remaining - taken;
    }

    /** Inventory.findSlotMatchingUnusedItem: 같은 아이템·컴포넌트이고 손상·마법·이름이 없는 첫 칸. */
    private int findSlotMatchingUnusedItem(ItemStack stack) {
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv(i);
            if (!s.isEmpty() && Stacks.isSameItemSameComponents(stack, s) && !(ItemTools.isDamageable(s) && ItemTools.damage(s) > 0)
                    && EnchantTools.levels(s, EnchantTools.ENCHANTMENTS).isEmpty() && ItemTools.component(s, "minecraft:custom_name") == null) return i;
        }
        return -1;
    }

    /** 바닐라 testClearGrid: 격자의 내용을 인벤토리가 다 받아 줄 수 있는가. */
    private boolean testClearGrid() {
        List<ItemStack> acc = new ArrayList<>();
        int empty = 0;
        for (int i = 0; i < 36; i++) if (inv(i).isEmpty()) empty++;
        for (int i = 0; i < menu.gridSize(); i++) {
            if (i == menu.resultSlotIndex()) continue;
            ItemStack s = menu.slot(i).getItem();
            if (s.isEmpty()) continue;
            int slot = inventory.slotWithRemainingSpace(s, ItemStackCodecs.maxStackSize(s));
            if (slot == -1 && acc.size() <= empty) {
                for (int k = 0; k < acc.size(); k++) {
                    ItemStack a = acc.get(k);
                    int max = ItemStackCodecs.maxStackSize(a);
                    if (a.itemId() == s.itemId() && a.count() != max && a.count() + s.count() <= max) {
                        acc.set(k, a.withCount(a.count() + s.count()));
                        s = ItemStack.EMPTY;
                        break;
                    }
                }
                if (!s.isEmpty()) {
                    if (acc.size() >= empty) return false;
                    acc.add(s);
                }
            } else if (slot == -1) {
                return false;
            }
        }
        return true;
    }
}
