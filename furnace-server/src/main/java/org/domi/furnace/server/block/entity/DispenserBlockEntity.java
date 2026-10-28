// 바닐라 DispenserBlockEntity: 9칸 용기. 무작위 칸 고르기(getRandomSlot)와 남은 것 넣기(insertItem).
package org.domi.furnace.server.block.entity;

import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemStackCodecs;
import org.domi.furnace.server.menu.Stacks;

public class DispenserBlockEntity extends ContainerBlockEntity {

    public DispenserBlockEntity(Pos pos, int state) { super("minecraft:dispenser", pos, state, 9); }

    protected DispenserBlockEntity(String type, Pos pos, int state) { super(type, pos, state, 9); }

    @Override public boolean isValid(int state) { return org.domi.furnace.server.block.B.is(state, "dispenser"); }

    @Override public String defaultTitleKey() { return "container.dispenser"; }

    /**
     * getRandomSlot(random): 열지 않은 전리품을 먼저 채우고, 비지 않은 칸마다 nextInt(j++) == 0 이면 그 칸(저수지 추출, j 는 1부터).
     * 빈 발사기면 -1.
     */
    public int getRandomSlot(BlockLevel level) {
        if (lootTable != null) level.unpackContainerLoot(this);
        int slot = -1, j = 1;
        for (int i = 0; i < items.length; i++) {
            if (!items[i].isEmpty() && level.random().nextInt(j++) == 0) slot = i;
        }
        return slot;
    }

    /** insertItem(stack): 빈 칸이나 같은 아이템 칸에 앞에서부터 최대 수량까지 넣고 남은 것을 돌려준다(setItem 은 setChanged). */
    public ItemStack insertItem(ItemStack stack, BlockLevel level) {
        int max = Math.min(99, ItemStackCodecs.maxStackSize(stack));
        for (int i = 0; i < items.length; i++) {
            ItemStack cur = items[i];
            if (cur.isEmpty() || Stacks.isSameItemSameComponents(stack, cur)) {
                int n = Math.min(stack.count(), max - cur.count());
                if (n > 0) {
                    items[i] = cur.isEmpty() ? stack.withCount(n) : cur.withCount(cur.count() + n);
                    stack = stack.withCount(stack.count() - n);
                    setChanged(level);
                }
                if (stack.isEmpty()) break;
            }
        }
        return stack;
    }
}
