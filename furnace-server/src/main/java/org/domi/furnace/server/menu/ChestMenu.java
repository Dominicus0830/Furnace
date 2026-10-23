// 바닐라 ChestMenu(generic_9xN) — 위에 용기 rows*9 칸, 아래에 플레이어 주 인벤토리 27칸과 핫바 9칸. 상자·통·더블 상자가 쓴다.
package org.domi.furnace.server.menu;

public class ChestMenu extends ContainerBackedMenu {

    private final int rows;

    /** @param menuType minecraft:menu 이름(예: minecraft:generic_9x3). @param blockPos 블록에 매인 용기면 좌표, 아니면 null. */
    public ChestMenu(String menuType, int containerId, Container playerInventory, Container container, int rows, int[] blockPos, MenuPlayer opener) {
        super(menuType, containerId, container, blockPos);
        checkContainerSize(container, rows * 9);
        this.rows = rows;
        container.startOpen(opener);
        for (int r = 0; r < rows; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(container, c + r * 9));
        addPlayerSlots(playerInventory);
    }

    public static ChestMenu threeRows(int containerId, Container playerInventory, Container container, int[] blockPos, MenuPlayer opener) {
        return new ChestMenu("minecraft:generic_9x3", containerId, playerInventory, container, 3, blockPos, opener);
    }

    public static ChestMenu sixRows(int containerId, Container playerInventory, Container container, int[] blockPos, MenuPlayer opener) {
        return new ChestMenu("minecraft:generic_9x6", containerId, playerInventory, container, 6, blockPos, opener);
    }

    public int rows() { return rows; }
}
