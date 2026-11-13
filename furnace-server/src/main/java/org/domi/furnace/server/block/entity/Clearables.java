// 바닐라 Clearable.tryClear: 블록을 바꾸기 전에 담긴 내용물을 비운다(쏟지 않게) — /fill·/setblock·/clone·빠른 블록 쓰기(E7)가 함께 쓴다.
package org.domi.furnace.server.block.entity;

public final class Clearables {

    private Clearables() {}

    /** 주크박스·독서대는 아직 빠졌다. */
    public static void tryClear(Object be) {
        if (be instanceof ContainerBlockEntity c) java.util.Arrays.fill(c.items, org.domi.furnace.server.entity.ItemStack.EMPTY);
        else if (be instanceof ChiseledBookShelfBlockEntity c) c.clearContent();
    }
}
