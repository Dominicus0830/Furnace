// 바닐라 UseItemGoal: 조건이 맞으면 손에 아이템을 쥐고 쓴다(방랑 상인의 투명 물약·우유). 끝나면 손을 비우고 소리를 낸다.
package org.domi.furnace.server.entity.ai.goal;

import java.util.function.Predicate;
import org.domi.furnace.server.entity.EquipmentSlot;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.Goal;

public class UseItemGoal<T extends Mob> extends Goal {
    private final T mob;
    private final ItemStack item;
    private final Predicate<? super T> canUseSelector;
    private final String finishUsingSound;

    public UseItemGoal(T mob, ItemStack item, String finishUsingSound, Predicate<? super T> canUseSelector) {
        this.mob = mob;
        this.item = item;
        this.finishUsingSound = finishUsingSound;
        this.canUseSelector = canUseSelector;
    }

    @Override public boolean canUse() { return canUseSelector.test(mob); }

    @Override public boolean canContinueToUse() { return mob.isUsingItem(); }

    @Override
    public void start() {
        mob.setItemSlot(EquipmentSlot.MAINHAND, item);
        mob.startUsingItem(0);
    }

    @Override
    public void stop() {
        mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        if (finishUsingSound != null) mob.playSound(finishUsingSound, 1.0F, mob.random().nextFloat() * 0.2F + 0.9F);
    }
}
