// 바닐라 Clearable.tryClear: 블록을 바꾸기 전에 담긴 내용물을 비운다(쏟지 않게) — /fill·/setblock·/clone·빠른 블록 쓰기(E7)·호환 계층 Clearable 이 함께 쓴다.
package org.domi.furnace.server.block.entity;

import org.domi.furnace.server.VanillaPort;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.entity.ItemStack;

@VanillaPort("net.minecraft.world.Clearable")
public final class Clearables {

    private Clearables() {}

    /** 바닐라 {@code o instanceof Clearable}: 담는 블록 엔티티 계열·모닥불·조각된 책장·장식 단지·주크박스·독서대. */
    public static boolean isClearable(Object be) {
        return be instanceof ContainerBlockEntity || be instanceof JukeboxBlockEntity || be instanceof LecternBlockEntity;
    }

    /**
     * 바닐라 clearContent(종류별):
     * 담는 블록 엔티티·모닥불·조각된 책장은 칸을 모두 비운다(전리품 표는 그대로 — getItems().clear()).
     * 장식 단지는 removeTheItem → splitTheItem: 전리품을 먼저 풀고 비운다.
     * 주크박스는 removeTheItem → setTheItem(EMPTY): has_record 끄기·재생 멈춤.
     * 독서대는 setBook(EMPTY): 쪽 0·저장 표시(has_book 상태는 그대로).
     * @return 비울 수 있는 종류였는가
     */
    public static boolean tryClear(Object be, BlockLevel level) {
        if (be instanceof DecoratedPotBlockEntity pot) {
            pot.setTheItem(ItemStack.EMPTY, level);   // unpackLootTable(null) 뒤 item.split(최대) — 칸이 빈다
        } else if (be instanceof ChiseledBookShelfBlockEntity c) {
            c.clearContent();
        } else if (be instanceof ContainerBlockEntity c) {
            java.util.Arrays.fill(c.items, ItemStack.EMPTY);
        } else if (be instanceof JukeboxBlockEntity j) {
            j.removeItem(0, j.maxStackSize(), level);
        } else if (be instanceof LecternBlockEntity l) {
            l.setBook(ItemStack.EMPTY, level);
        } else {
            return false;
        }
        return true;
    }
}
