// 바닐라 ContainerListener — 슬롯 내용이 바뀔 때(서버 플레이어는 inventory_changed 발전 과제용).
package org.domi.furnace.server.menu;

import org.domi.furnace.server.entity.ItemStack;

public interface MenuListener {

    void slotChanged(AbstractContainerMenu menu, int slotIndex, ItemStack stack);

    default void dataChanged(AbstractContainerMenu menu, int dataIndex, int value) { }
}
