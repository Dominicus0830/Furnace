// 바닐라 CraftingInput — 제작 격자에서 앞뒤 빈 행·열을 잘라낸 입력(width×height, 아이템 목록)과 빈칸이 아닌 칸 수.
package org.domi.furnace.server.recipe;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.entity.ItemStack;

public final class CraftingInput {

    public static final CraftingInput EMPTY = new CraftingInput(0, 0, List.of());

    /** 자른 입력과 원래 격자에서의 왼쪽/위 오프셋(결과 칸이 재료를 줄일 때 쓴다). */
    public record Positioned(CraftingInput input, int left, int top) {
        public static final Positioned EMPTY = new Positioned(CraftingInput.EMPTY, 0, 0);
    }

    private final int width, height;
    private final List<ItemStack> items;
    private final int ingredientCount;

    private CraftingInput(int width, int height, List<ItemStack> items) {
        this.width = width;
        this.height = height;
        this.items = items;
        int n = 0;
        for (ItemStack s : items) if (!s.isEmpty()) n++;
        this.ingredientCount = n;
    }

    /** 바닐라 CraftingInput.of — 빈 행·열을 잘라낸다. */
    public static CraftingInput of(int width, int height, List<ItemStack> items) { return positioned(width, height, items).input(); }

    /** 바닐라 CraftingInput.ofPositioned. */
    public static Positioned positioned(int width, int height, List<ItemStack> items) {
        if (width == 0 || height == 0) return Positioned.EMPTY;
        int minX = width - 1, maxX = 0, minY = height - 1, maxY = 0;
        for (int y = 0; y < height; y++) {
            boolean emptyRow = true;
            for (int x = 0; x < width; x++) {
                if (!items.get(x + y * width).isEmpty()) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    emptyRow = false;
                }
            }
            if (!emptyRow) {
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        }
        int w = maxX - minX + 1, h = maxY - minY + 1;
        if (w <= 0 || h <= 0) return Positioned.EMPTY;
        if (w == width && h == height) return new Positioned(new CraftingInput(width, height, items), minX, minY);
        List<ItemStack> cut = new ArrayList<>(w * h);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) cut.add(items.get(x + minX + (y + minY) * width));
        return new Positioned(new CraftingInput(w, h, cut), minX, minY);
    }

    public int width() { return width; }

    public int height() { return height; }

    /** 칸 수(width×height). */
    public int size() { return items.size(); }

    public ItemStack getItem(int index) { return items.get(index); }

    public ItemStack getItem(int x, int y) { return items.get(x + y * width); }

    public List<ItemStack> items() { return items; }

    /** 빈칸이 아닌 칸 수. */
    public int ingredientCount() { return ingredientCount; }

    public boolean isEmpty() { return ingredientCount == 0; }
}
