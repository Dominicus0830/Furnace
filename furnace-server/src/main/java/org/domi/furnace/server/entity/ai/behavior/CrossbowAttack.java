// 바닐라 CrossbowAttack(브레인): 석궁을 든 몹이 대상이 보이면 장전(UNCHARGED→CHARGING→CHARGED)하고 20~39틱 뒤 쏜다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.Map;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Crossbow;
import org.domi.furnace.server.entity.CrossbowAttackMob;
import org.domi.furnace.server.entity.EquipmentSlot;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.ai.brain.EntityTracker;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.MemoryStatus;

public class CrossbowAttack<E extends Mob & CrossbowAttackMob> extends Behavior<E> {
    private static final int TIMEOUT = 1200;
    private int attackDelay;
    private State crossbowState = State.UNCHARGED;

    private enum State { UNCHARGED, CHARGING, CHARGED, READY_TO_ATTACK }

    public CrossbowAttack() { super(Map.of(MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED, MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT), TIMEOUT); }

    @Override
    protected boolean checkExtraStartConditions(ChunkLevel level, E mob) {
        Targetable target = getAttackTarget(mob);
        return mob.isHolding("minecraft:crossbow") && BehaviorUtils.canSee(mob, target) && BehaviorUtils.isWithinAttackRange(mob, target, 0);
    }

    @Override protected boolean canStillUse(ChunkLevel level, E mob, long gameTime) { return mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET) && checkExtraStartConditions(level, mob); }

    @Override
    protected void tick(ChunkLevel level, E mob, long gameTime) {
        Targetable target = getAttackTarget(mob);
        mob.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
        crossbowAttack(mob, target);
    }

    @Override
    protected void stop(ChunkLevel level, E mob, long gameTime) {
        if (mob.isUsingItem()) mob.stopUsingItem();
        if (mob.isHolding("minecraft:crossbow")) {
            mob.setChargingCrossbow(false);
            EquipmentSlot slot = mob.getWeaponHoldingHand("minecraft:crossbow") == 0 ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
            mob.updateItemSlot(slot, org.domi.furnace.server.item.ItemTools.with(mob.getItemBySlot(slot), "minecraft:charged_projectiles", java.util.List.of()));
        }
    }

    private void crossbowAttack(E mob, Targetable target) {
        if (crossbowState == State.UNCHARGED) {
            mob.startUsingItem(mob.getWeaponHoldingHand("minecraft:crossbow"));
            crossbowState = State.CHARGING;
            mob.setChargingCrossbow(true);
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
            attackDelay--;
            if (attackDelay == 0) crossbowState = State.READY_TO_ATTACK;
        } else if (crossbowState == State.READY_TO_ATTACK) {
            mob.performRangedAttack(target, 1.0F);
            crossbowState = State.UNCHARGED;
        }
    }

    private static Targetable getAttackTarget(Mob mob) { return mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).get(); }
}
