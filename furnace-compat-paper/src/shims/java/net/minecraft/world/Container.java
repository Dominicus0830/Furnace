package net.minecraft.world;

import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 칸으로 이루어진 아이템 저장소(Mojang 이름 shim). 플레이어 인벤토리({@link net.minecraft.world.entity.player.Inventory})와 열린 창의 컨테이너 칸이
 * 구현한다. 추상 메서드(칸 수·읽기·쓰기, Paper 가 더한 getContents·getOwner 등)는 명세가 바닐라·Paper 처럼 추상으로 선언하고, 이 조각은 바닐라가 default 로
 * 가진 규칙(칸당 최대 99, 놓기·꺼내기 허용, 세기)만 둔다.
 */
interface Container {

    default int getMaxStackSize(ItemStack stack) { return Math.min(getMaxStackSize(), stack.getMaxStackSize()); }

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
