// 바닐라 TransientCraftingContainer — 제작 격자(width×height). 칸이 바뀌면(set·remove) 메뉴에 slotsChanged 를 알려 결과를 다시 구한다.
// 저장은 다른 컨테이너에 맡긴다(인벤토리 창은 PlayerInventory 의 창 칸 1..4, 제작대는 SimpleContainer).
package org.domi.furnace.server.menu;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.recipe.CraftingInput;

public final class CraftingGrid implements Container {

    private final Container storage;
    private final int width, height;
    private final AbstractContainerMenu menu;

    public CraftingGrid(Container storage, int width, int height, AbstractContainerMenu menu) {
        this.storage = storage;
        this.width = width;
        this.height = height;
        this.menu = menu;
    }

    public int width() { return width; }

    public int height() { return height; }

    @Override public int size() { return width * height; }

    @Override public ItemStack get(int index) { return index >= size() ? ItemStack.EMPTY : storage.get(index); }

    @Override public void set(int index, ItemStack stack) {
        storage.set(index, stack);
        menu.slotsChanged(this);
    }

    @Override public ItemStack remove(int index, int amount) {
        ItemStack removed = storage.remove(index, amount);
        if (!removed.isEmpty()) menu.slotsChanged(this);
        return removed;
    }

    @Override public ItemStack removeNoUpdate(int index) { return storage.removeNoUpdate(index); }

    @Override public void replaceInPlace(int index, ItemStack stack) { storage.replaceInPlace(index, stack); }

    @Override public void setChanged() { }

    @Override public boolean stillValid(MenuPlayer player) { return true; }

    public List<ItemStack> items() {
        List<ItemStack> l = new ArrayList<>(size());
        for (int i = 0; i < size(); i++) l.add(storage.get(i));
        return l;
    }

    /** 바닐라 asPositionedCraftInput — 앞뒤 빈 행·열을 자른 입력과 오프셋. */
    public CraftingInput.Positioned positioned() { return CraftingInput.positioned(width, height, items()); }

    public CraftingInput asInput() { return positioned().input(); }
}
