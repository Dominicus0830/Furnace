// 바닐라 SpellcasterIllager: 주문을 외우는 일러저(소환사·환술사)의 공통 — 주문 종류(데이터 17)와 주문 시전 목표 기반.
package org.domi.furnace.server.entity;

import java.util.EnumSet;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.ai.Goal;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public abstract class SpellcasterIllager extends AbstractIllager {
    public static final int DATA_SPELL_CASTING_ID = 17;
    protected int spellCastingTickCount;
    private IllagerSpell currentSpell = IllagerSpell.NONE;

    protected enum IllagerSpell { NONE, SUMMON_VEX, FANGS, WOLOLO, DISAPPEAR, BLINDNESS }

    protected SpellcasterIllager(EntityType type, ChunkLevel level, int id) { super(type, level, id); }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_SPELL_CASTING_ID, SetEntityData.SERIALIZER_BYTE, (byte) 0);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        spellCastingTickCount = tag.getIntOr0("SpellTicks");
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("SpellTicks", spellCastingTickCount);
    }

    @Override
    public IllagerArmPose getArmPose() {
        if (isCastingSpell()) return IllagerArmPose.SPELLCASTING;
        return isCelebrating() ? IllagerArmPose.CELEBRATING : IllagerArmPose.CROSSED;
    }

    public boolean isCastingSpell() { return spellCastingTickCount > 0; }

    public void setIsCastingSpell(IllagerSpell spell) {
        currentSpell = spell;
        entityData.set(DATA_SPELL_CASTING_ID, (byte) spell.ordinal());
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (spellCastingTickCount > 0) spellCastingTickCount--;
    }

    protected int getSpellCastingTime() { return spellCastingTickCount; }

    protected abstract String getCastingSoundEvent();

    protected class SpellcasterCastingSpellGoal extends Goal {
        public SpellcasterCastingSpellGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }

        @Override public boolean canUse() { return getSpellCastingTime() > 0; }

        @Override
        public void start() {
            navigation.stop();
        }

        @Override
        public void stop() {
            setIsCastingSpell(IllagerSpell.NONE);
        }

        @Override
        public void tick() {
            if (getTarget() != null) getLookControl().setLookAt(getTarget().x(), getTarget().eyeY(), getTarget().z(), getMaxHeadYRot(), getMaxHeadXRot());
        }
    }

    protected abstract class SpellcasterUseSpellGoal extends Goal {
        protected int attackWarmupDelay;
        protected int nextAttackTickCount;

        @Override
        public boolean canUse() {
            Targetable t = getTarget();
            if (t == null || !t.isAlive()) return false;
            return isCastingSpell() ? false : tickCount >= nextAttackTickCount;
        }

        @Override
        public boolean canContinueToUse() {
            Targetable t = getTarget();
            return t != null && t.isAlive() && attackWarmupDelay > 0;
        }

        @Override
        public void start() {
            attackWarmupDelay = adjustedTickDelay(getCastWarmupTime());
            spellCastingTickCount = getCastingTime();
            nextAttackTickCount = tickCount + getCastingInterval();
            String sound = getSpellPrepareSound();
            if (sound != null) playSound(sound, 1.0F, 1.0F);
            setIsCastingSpell(getSpell());
        }

        @Override
        public void tick() {
            attackWarmupDelay--;
            if (attackWarmupDelay == 0) {
                performSpellCasting();
                playSound(getCastingSoundEvent(), 1.0F, 1.0F);
            }
        }

        protected abstract void performSpellCasting();

        protected int getCastWarmupTime() { return 20; }

        protected abstract int getCastingTime();

        protected abstract int getCastingInterval();

        protected abstract String getSpellPrepareSound();

        protected abstract IllagerSpell getSpell();
    }
}
