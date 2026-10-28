// 바닐라 DefaultDispenseItemBehavior: 하나를 떼어 앞으로 던진다(속도 6). 실행 → 소리(1000) → 연기(2000, 방향) 순서.
package org.domi.furnace.server.block.dispenser;

import org.domi.furnace.server.block.types.CrafterBlock;
import org.domi.furnace.server.entity.ItemStack;

public class DefaultDispenseItemBehavior implements DispenseItemBehavior {

    @Override
    public final ItemStack dispense(BlockSource source, ItemStack stack) {
        ItemStack out = execute(source, stack);
        playSound(source);
        playAnimation(source, source.facing());
        return out;
    }

    /** execute: 하나를 떼어 getDispensePosition(0.7)에서 spawnItem. */
    protected ItemStack execute(BlockSource source, ItemStack stack) {
        double[] p = source.dispensePosition(0.7, 0.0, 0.0, 0.0);
        spawnItem(source, stack.withCount(1), 6, source.facing(), p);
        return stack.withCount(stack.count() - 1);
    }

    /** spawnItem(level, stack, speed, dir, pos): 제작기와 같은 공식(레벨 난수: ItemEntity 생성 2 + 속도 1 + triangle 3). */
    public static void spawnItem(BlockSource source, ItemStack stack, int speed, int dir, double[] p) {
        CrafterBlock.spawnItem(source.level(), stack, speed, dir, p[0], p[1], p[2]);
    }

    protected void playSound(BlockSource source) { defaultSound(source); }

    protected void playAnimation(BlockSource source, int dir) { defaultAnimation(source, dir); }

    private static void defaultSound(BlockSource source) { source.level().levelEvent(null, 1000, source.pos(), 0); }

    private static void defaultAnimation(BlockSource source, int dir) { source.level().levelEvent(null, 2000, source.pos(), dir); }

    /**
     * consumeWithRemainder(source, stack, remainder): 하나 줄이고, 다 썼으면 남은 것을 그 칸에. 아니면 남은 것을 발사기에 넣고(insertItem)
     * 안 들어간 것은 앞으로 던진다(+ 기본 소리·연기).
     */
    protected ItemStack consumeWithRemainder(BlockSource source, ItemStack stack, ItemStack remainder) {
        ItemStack rest = stack.withCount(stack.count() - 1);
        if (rest.isEmpty()) return remainder;
        source.blockEntity().items[source.slot()] = rest;   // 바닐라는 같은 스택 객체를 줄인 채 넣는다
        ItemStack left = source.blockEntity().insertItem(remainder, source.level());
        if (!left.isEmpty()) {
            int dir = source.facing();
            spawnItem(source, left, 6, dir, source.dispensePosition(0.7, 0.0, 0.0, 0.0));
            defaultSound(source);
            defaultAnimation(source, dir);
        }
        return source.blockEntity().items[source.slot()];
    }
}
