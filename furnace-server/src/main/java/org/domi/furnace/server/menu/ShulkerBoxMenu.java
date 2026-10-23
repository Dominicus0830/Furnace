// 바닐라 ShulkerBoxMenu(shulker_box) — 27칸. 셜커 상자 안에는 셜커 상자를 넣을 수 없다(ShulkerBoxSlot).
package org.domi.furnace.server.menu;

import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemContext;

public class ShulkerBoxMenu extends ContainerBackedMenu {

    public ShulkerBoxMenu(int containerId, Container playerInventory, Container container, int[] blockPos, MenuPlayer opener) {
        super("minecraft:shulker_box", containerId, container, blockPos);
        checkContainerSize(container, 27);
        container.startOpen(opener);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new Slot(container, c + r * 9) {
                    @Override public boolean mayPlace(ItemStack stack) { return canFitInsideContainerItems(stack); }
                });
            }
        }
        addPlayerSlots(playerInventory);
    }

    /** Item.canFitInsideContainerItems: 셜커 상자 아이템만 안 된다(BlockItem 이 ShulkerBoxBlock 이면 거짓). */
    static boolean canFitInsideContainerItems(ItemStack stack) {
        String n = stack.isEmpty() || !ItemContext.installed() ? null : ItemContext.name("minecraft:item", stack.itemId());
        return n == null || !(n.equals("minecraft:shulker_box") || n.endsWith("_shulker_box"));
    }
}
