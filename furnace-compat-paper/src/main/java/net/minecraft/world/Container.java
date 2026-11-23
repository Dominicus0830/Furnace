package net.minecraft.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.domi.furnace.compat.nms.UnsupportedNmsCall;

/**
 * 칸으로 이루어진 아이템 저장소(Mojang 이름 shim). 플레이어 인벤토리({@link net.minecraft.world.entity.player.Inventory})와 열린 창의 컨테이너 칸이
 * 구현한다. 기본 메서드는 바닐라와 같은 규칙이다(칸당 최대 99, 놓기·꺼내기 허용, 세기).
 *
 * <p>바닐라에서 추상인 메서드(칸 수·읽기·쓰기 등)는 여기서 {@code default} 로 던진다: 생성기가 만든 상자 수레·상자 배 같은 클래스가 이 인터페이스를
 * 물려받는데, 그 추상 메서드의 던지는 몸통을 만들려면 서명표(Paper 서버 표)로 잠금 파일을 다시 만들어야 한다. 그 표가 있는 곳에서 이 인터페이스를
 * 명세(조각)로 옮기면 추상으로 되돌린다 — 바닐라와 다른 점(user-todo 2026-11-23-shim-containers 표 ⑥).
 */
public interface Container extends Clearable {

    float DEFAULT_DISTANCE_BUFFER = 4.0F;

    default int getContainerSize() { throw unimplemented("getContainerSize"); }

    default boolean isEmpty() { throw unimplemented("isEmpty"); }

    default ItemStack getItem(int slot) { throw unimplemented("getItem"); }

    default ItemStack removeItem(int slot, int amount) { throw unimplemented("removeItem"); }

    default ItemStack removeItemNoUpdate(int slot) { throw unimplemented("removeItemNoUpdate"); }

    default void setItem(int slot, ItemStack stack) { throw unimplemented("setItem"); }

    /** CraftBukkit·Paper 가 컨테이너에 더한 메서드: 모든 칸의 지금 내용(칸 번호 순서). */
    default List<ItemStack> getContents() {
        List<ItemStack> out = new ArrayList<>(getContainerSize());
        for (int i = 0; i < getContainerSize(); i++) out.add(getItem(i));
        return out;
    }

    default int getMaxStackSize() { return 99; }

    default int getMaxStackSize(ItemStack stack) { return Math.min(getMaxStackSize(), stack.getMaxStackSize()); }

    default void setChanged() { throw unimplemented("setChanged"); }

    default boolean stillValid(Player player) { throw unimplemented("stillValid"); }

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

    private static UnsupportedNmsCall unimplemented(String what) {
        return new UnsupportedNmsCall("Container#" + what, "this container is a Furnace shim without slot storage here");
    }
}
