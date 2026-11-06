// 바닐라 RangedCrossbowAttackGoal: 석궁 든 몹이 시야가 닿으면 장전(UNCHARGED→CHARGING→CHARGED)한 뒤 20~39틱 기다렸다 쏜다.
package org.domi.furnace.server.entity.ai.goal;

import java.util.EnumSet;
import org.domi.furnace.server.entity.Crossbow;
import org.domi.furnace.server.entity.CrossbowAttackMob;
import org.domi.furnace.server.entity.EquipmentSlot;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.ai.Goal;

public class RangedCrossbowAttackGoal<T extends Mob & CrossbowAttackMob> extends Goal {
    private enum State { UNCHARGED, CHARGING, CHARGED, READY_TO_ATTACK }

    private final T mob;
    private State crossbowState = State.UNCHARGED;
    private final double speedModifier;
    private final float attackRadiusSqr;
    private int seeTime, attackDelay, updatePathDelay;

    public RangedCrossbowAttackGoal(T mob, double speed, float radius) {
        this.mob = mob;
        this.speedModifier = speed;
        this.attackRadiusSqr = radius * radius;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() { return isValidTarget() && isHoldingCrossbow(); }

    private boolean isHoldingCrossbow() { return mob.isHolding("minecraft:crossbow"); }

    @Override
    public boolean canContinueToUse() { return isValidTarget() && (canUse() || !mob.getNavigation().isDone()) && isHoldingCrossbow(); }

    private boolean isValidTarget() { return mob.getTarget() != null && mob.getTarget().isAlive(); }

    @Override
    public void stop() {
        mob.setAggressive(false);
        mob.setTarget(null);
        seeTime = 0;
        if (mob.isUsingItem()) {
            mob.stopUsingItem();
            mob.setChargingCrossbow(false);
            EquipmentSlot slot = mob.getUsedItemHand() == 0 ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
            mob.updateItemSlot(slot, org.domi.furnace.server.item.ItemTools.with(mob.getItemBySlot(slot), "minecraft:charged_projectiles", java.util.List.of()));
        }
    }

    @Override public boolean requiresUpdateEveryTick() { return true; }

    @Override
    public void tick() {
        Targetable target = mob.getTarget();
        if (target == null) return;
        boolean canSee = mob.hasLineOfSight(target);
        boolean seen = seeTime > 0;
        if (canSee != seen) seeTime = 0;
        if (canSee) seeTime++;
        else seeTime--;
        double dx = target.x() - mob.x(), dy = target.y() - mob.y(), dz = target.z() - mob.z();
        double dist = dx * dx + dy * dy + dz * dz;
        boolean approach = (dist > attackRadiusSqr || seeTime < 5) && attackDelay == 0;
        if (approach) {
            if (--updatePathDelay <= 0) {
                mob.getNavigation().moveTo(target, crossbowState == State.UNCHARGED ? speedModifier : speedModifier * 0.5);
                updatePathDelay = 1 + mob.random().nextInt(2);
            }
        } else {
            updatePathDelay = 0;
            mob.getNavigation().stop();
        }
        mob.getLookControl().setLookAt(target.x(), target.eyeY(), target.z(), 30.0F, 30.0F);
        if (crossbowState == State.UNCHARGED) {
            if (!approach) {
                mob.startUsingItem(mob.getWeaponHoldingHand("minecraft:crossbow"));
                crossbowState = State.CHARGING;
                mob.setChargingCrossbow(true);
            }
        } else if (crossbowState == State.CHARGING) {
            if (!mob.isUsingItem()) crossbowState = State.UNCHARGED;
            int ticks = mob.getTicksUsingItem();
            EquipmentSlot slot = mob.getUsedItemHand() == 0 ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
            if (ticks >= Crossbow.chargeDuration(mob.getItemBySlot(slot))) {
                Crossbow.releaseUsing(mob, slot, ticks);
                mob.stopUsingItem();
                crossbowState = State.CHARGED;
                attackDelay = 20 + mob.random().nextInt(20);
                mob.setChargingCrossbow(false);
            }
        } else if (crossbowState == State.CHARGED) {
            if (--attackDelay == 0) crossbowState = State.READY_TO_ATTACK;
        } else if (crossbowState == State.READY_TO_ATTACK && canSee) {
            mob.performRangedAttack(target, 1.0F);
            crossbowState = State.UNCHARGED;
        }
    }
}
