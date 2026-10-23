// 바닐라 ContainerSynchronizer — 메뉴와 클라이언트 사이의 패킷. 상태 번호(stateId)는 호출마다 menu.incrementStateId() 로 올린다.
package org.domi.furnace.server.menu;

import java.util.List;
import org.domi.furnace.server.entity.ItemStack;

public interface MenuSynchronizer {

    void sendInitialData(AbstractContainerMenu menu, List<ItemStack> items, ItemStack carried, int[] dataSlots);

    void sendSlotChange(AbstractContainerMenu menu, int slot, ItemStack stack);

    void sendCarriedChange(AbstractContainerMenu menu, ItemStack stack);

    void sendDataChange(AbstractContainerMenu menu, int id, int value);
}
