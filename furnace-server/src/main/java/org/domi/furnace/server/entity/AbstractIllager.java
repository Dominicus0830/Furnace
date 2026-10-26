// 바닐라 AbstractIllager: 약탈자·변명자 등 일러저의 공통 — 마을 주민(아기 제외)은 건드리지 않고, 일러저 친구 태그끼리는 팀이 없으면 동맹.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;

public abstract class AbstractIllager extends Raider {
    public enum IllagerArmPose { CROSSED, ATTACKING, SPELLCASTING, BOW_AND_ARROW, CROSSBOW_HOLD, CROSSBOW_CHARGE, CELEBRATING, NEUTRAL }

    protected AbstractIllager(EntityType type, ChunkLevel level, int id) { super(type, level, id); }

    public IllagerArmPose getArmPose() { return IllagerArmPose.CROSSED; }

    /** AbstractIllager.RaiderOpenDoorGoal: 습격 중일 때만 문을 연다(닫지 않는다). */
    public static class RaiderOpenDoorGoal extends org.domi.furnace.server.entity.ai.goal.OpenDoorGoal {
        private final Raider raider;

        public RaiderOpenDoorGoal(Raider raider) {
            super(raider, false);
            this.raider = raider;
        }

        @Override
        public boolean canUse() { return super.canUse() && raider.hasActiveRaid(); }
    }

    /** AbstractIllager.canAttack: 아기 주민은 공격하지 않는다. */
    @Override
    public boolean canAttackType(Targetable target) {
        return target instanceof Villager v && v.isBaby() ? false : super.canAttackType(target);
    }

    /** AbstractIllager.isAlliedTo: 일러저 친구 태그끼리는 팀이 없으면 동맹. TODO(팀): 팀 시스템이 생기면 super.isAlliedTo(팀) 포함. */
    public boolean isAlliedTo(Targetable other) {
        return other instanceof Entity e && e.type().is("minecraft:illager_friends");
    }
}
