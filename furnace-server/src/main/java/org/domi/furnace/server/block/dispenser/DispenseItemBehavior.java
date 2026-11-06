// 바닐라 DispenseItemBehavior: 발사기가 칸의 스택을 내보내고 그 칸에 남길 스택을 돌려준다. NOOP 은 칸을 건드리지 않는다.
package org.domi.furnace.server.block.dispenser;

import org.domi.furnace.server.entity.ItemStack;

@FunctionalInterface
public interface DispenseItemBehavior {

    DispenseItemBehavior NOOP = (source, stack) -> stack;

    ItemStack dispense(BlockSource source, ItemStack stack);
}
