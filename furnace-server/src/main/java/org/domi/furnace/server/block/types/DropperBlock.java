// 바닐라 DropperBlock: 공급기. 무작위 칸의 아이템 하나를 앞 용기에 넣고(소리 없음), 용기가 없으면 기본 던지기. 빈 공급기는 실패 소리만(게임 이벤트 없음).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.dispenser.BlockSource;
import org.domi.furnace.server.block.dispenser.DispenseBehaviors;
import org.domi.furnace.server.block.entity.DropperBlockEntity;
import org.domi.furnace.server.block.entity.HopperBlockEntity;
import org.domi.furnace.server.entity.ItemStack;

public class DropperBlock extends DispenserBlock {

    public DropperBlock(int block) { super(block); }

    @Override
    protected void dispenseFrom(BlockLevel level, int state, Pos pos) {
        if (!(level.getBlockEntity(pos) instanceof DropperBlockEntity be)) return;   // 바닐라: 경고만
        int slot = be.getRandomSlot(level);
        if (slot < 0) {
            level.levelEvent(null, 1001, pos, 0);
            return;
        }
        ItemStack stack = be.items[slot];
        if (stack.isEmpty()) return;
        int dir = Dir.byName(FACING.valueName(level.getBlockState(pos)));
        HopperBlockEntity.Inv target = HopperBlockEntity.getContainerAt(level, pos.relative(dir));
        ItemStack rest;
        if (target == null) {
            rest = DispenseBehaviors.DEFAULT.dispense(new BlockSource(level, pos, state, be, slot), stack);
        } else {
            // HopperBlockEntity.addItem(공급기, 앞 용기, 하나, 반대 면): 다 들어가면 하나 줄인다
            ItemStack left = HopperBlockEntity.addItem(null, target, stack.withCount(1), Dir.opposite(dir));
            rest = left.isEmpty() ? stack.withCount(stack.count() - 1) : stack;
        }
        setSlot(be, slot, rest, level);
    }
}
