package net.minecraft.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 칸으로 이루어진 아이템 저장소(Mojang 이름 shim). 플레이어 인벤토리({@link net.minecraft.world.entity.player.Inventory})와 열린 창의 컨테이너 칸이
 * 구현한다. 기본 메서드는 바닐라와 같은 규칙이다(칸당 최대 99, 놓기·꺼내기 허용, 세기).
 */
public interface Container extends Clearable {

    float DEFAULT_DISTANCE_BUFFER = 4.0F;

    int getContainerSize();

    boolean isEmpty();

    ItemStack getItem(int slot);

    ItemStack removeItem(int slot, int amount);

    ItemStack removeItemNoUpdate(int slot);

    void setItem(int slot, ItemStack stack);

    /** CraftBukkit·Paper 가 컨테이너에 더한 메서드: 모든 칸의 지금 내용(칸 번호 순서). */
    default List<ItemStack> getContents() {
        List<ItemStack> out = new ArrayList<>(getContainerSize());
        for (int i = 0; i < getContainerSize(); i++) out.add(getItem(i));
        return out;
    }

    default int getMaxStackSize() { return 99; }

    default int getMaxStackSize(ItemStack stack) { return Math.min(getMaxStackSize(), stack.getMaxStackSize()); }

    void setChanged();

    boolean stillValid(Player player);

    default void startOpen(Player player) {}

    default void stopOpen(Player player) {}

    default boolean canPlaceItem(int slot, ItemStack stack) { return true; }

    default boolean canTakeItem(Container target, int slot, ItemStack stack) { return true; }

    default int countItem(Item item) {
        int n = 0;
        for (int i = 0; i < getContainerSize(); i++) {
            ItemStack s = getItem(i);
            if (s.getItem().equals(item)) n += s.getCount();
        }
        return n;
    }

    default boolean hasAnyOf(Set<Item> items) { return hasAnyMatching(s -> !s.isEmpty() && items.contains(s.getItem())); }

    default boolean hasAnyMatching(Predicate<ItemStack> test) {
        for (int i = 0; i < getContainerSize(); i++) if (test.test(getItem(i))) return true;
        return false;
    }
}
