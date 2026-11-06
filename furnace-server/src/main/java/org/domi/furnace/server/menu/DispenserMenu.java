// 바닐라 DispenserMenu(generic_3x3) — 발사기·공급기 9칸. 이동 뒤 onTake 를 부르고 개수가 그대로면 EMPTY(다른 용기 메뉴와 다르다).
package org.domi.furnace.server.menu;

public class DispenserMenu extends ContainerBackedMenu {

    public DispenserMenu(int containerId, Container playerInventory, Container container, int[] blockPos, MenuPlayer opener) {
        super("minecraft:generic_3x3", containerId, container, blockPos);
        checkContainerSize(container, 9);
        container.startOpen(opener);
        for (int r = 0; r < 3; r++) for (int c = 0; c < 3; c++) addSlot(new Slot(container, c + r * 3));
        addPlayerSlots(playerInventory);
    }

    @Override protected boolean notifiesTake() { return true; }
}
