package net.minecraft.world.item.crafting;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/**
 * 바닐라 CraftingInput(Mojang 이름 shim, 손 조각): 제작 격자 입력. 격자에서 빈 가장자리를 잘라 낸 넓이·높이와 칸 목록, 빈 칸이 아닌 재료 수를 든다.
 * 바닐라 {@code ofPositioned} 의 자르기를 그대로 옮겼다. {@code stackedContents()} 는 StackedContents 몸통이 아직 없어 껍데기(던짐)로 둔다.
 */
class CraftingInput {

    public static final CraftingInput EMPTY = new CraftingInput(0, 0, List.of());

    private final int width;
    private final int height;
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

    public static CraftingInput of(int width, int height, List<ItemStack> items) { return ofPositioned(width, height, items).input(); }

    /** 바닐라 ofPositioned: 빈 줄·빈 열을 잘라 내고 잘린 왼쪽·위 자리를 함께 돌려준다. */
    public static Positioned ofPositioned(int width, int height, List<ItemStack> items) {
        if (width == 0 || height == 0) return Positioned.EMPTY;
        int minX = width - 1;
        int maxX = 0;
        int minY = height - 1;
        int maxY = 0;
        for (int y = 0; y < height; y++) {
            boolean rowEmpty = true;
            for (int x = 0; x < width; x++) {
                if (!items.get(x + y * width).isEmpty()) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    rowEmpty = false;
                }
            }
            if (!rowEmpty) {
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        }
        int w = maxX - minX + 1;
        int h = maxY - minY + 1;
        if (w <= 0 || h <= 0) return Positioned.EMPTY;
        if (w == width && h == height) return new Positioned(new CraftingInput(width, height, items), minX, minY);
        List<ItemStack> cut = new ArrayList<>(w * h);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) cut.add(items.get(x + minX + (y + minY) * width));
        return new Positioned(new CraftingInput(w, h, cut), minX, minY);
    }

    public ItemStack getItem(int index) { return items.get(index); }

    public ItemStack getItem(int x, int y) { return items.get(x + y * width); }

    public int size() { return items.size(); }

    public boolean isEmpty() { return ingredientCount == 0; }

    public List<ItemStack> items() { return items; }

    public int ingredientCount() { return ingredientCount; }

    public int width() { return width; }

    public int height() { return height; }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof CraftingInput c) || width != c.width || height != c.height || ingredientCount != c.ingredientCount) return false;
        if (items.size() != c.items.size()) return false;
        for (int i = 0; i < items.size(); i++) if (!ItemStack.matches(items.get(i), c.items.get(i))) return false;
        return true;
    }

    @Override
    public int hashCode() {
        int h = 0;
        for (ItemStack s : items) h = h * 31 + (s.isEmpty() ? 0 : s.getItem().hashCode() * 31 + s.getCount());
        return 31 * (31 * (31 * h + width) + height) + ingredientCount;
    }

    /** 바닐라 CraftingInput.Positioned: 잘린 입력과 잘려 나간 왼쪽·위 칸 수. */
    public record Positioned(CraftingInput input, int left, int top) {
        public static final Positioned EMPTY = new Positioned(CraftingInput.EMPTY, 0, 0);
    }
}
