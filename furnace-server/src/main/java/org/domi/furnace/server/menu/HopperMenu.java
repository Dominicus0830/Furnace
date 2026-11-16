// 바닐라 HopperMenu(hopper) — 5칸.
package org.domi.furnace.server.menu;

import org.domi.furnace.server.VanillaPort;

@VanillaPort("net.minecraft.world.inventory.HopperMenu")
public class HopperMenu extends ContainerBackedMenu {

    public HopperMenu(int containerId, Container playerInventory, Container container, int[] blockPos, MenuPlayer opener) {
        super("minecraft:hopper", containerId, container, blockPos);
        checkContainerSize(container, 5);
        container.startOpen(opener);
        for (int i = 0; i < 5; i++) addSlot(new Slot(container, i));
        addPlayerSlots(playerInventory);
    }
}
