// 바닐라 AbstractPiglin: 피글린 계열 공통 — 문을 열고, 오버월드에서 15초 넘게 있으면 좀비 피글린이 된다. 공격 대상은 브레인의 ATTACK_TARGET.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.entity.ai.navigation.GroundPathNavigation;
import org.domi.furnace.server.entity.ai.navigation.PathType;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public abstract class AbstractPiglin extends Monster {
    public static final int DATA_IMMUNE_TO_ZOMBIFICATION = 16;
    public static final int CONVERSION_TIME = 300;
    protected int timeInOverworld;

    protected AbstractPiglin(EntityType type, ChunkLevel level, int id) {
        super(type, level, id);
        setCanPickUpLoot(true);
        if (getNavigation() instanceof GroundPathNavigation g) g.setCanOpenDoors(true);
        setPathfindingMalus(PathType.DANGER_FIRE, 16.0F);
        setPathfindingMalus(PathType.DAMAGE_FIRE, -1.0F);
    }

    protected abstract boolean canHunt();

    public void setImmuneToZombification(boolean v) { entityData.set(DATA_IMMUNE_TO_ZOMBIFICATION, v); }

    protected boolean isImmuneToZombification() { return (Boolean) entityData.get(DATA_IMMUNE_TO_ZOMBIFICATION); }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_IMMUNE_TO_ZOMBIFICATION, SetEntityData.SERIALIZER_BOOLEAN, false);
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        if (isImmuneToZombification()) tag.putBoolean("IsImmuneToZombification", true);
        tag.putInt("TimeInOverworld", timeInOverworld);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        setImmuneToZombification(tag.getBoolean("IsImmuneToZombification"));
        timeInOverworld = tag.getInt("TimeInOverworld");
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (isConverting()) timeInOverworld++;
        else timeInOverworld = 0;
        if (timeInOverworld > CONVERSION_TIME) {
            playConvertedSound();
            finishConversion();
        }
    }

    public boolean isConverting() { return !level.piglinSafe() && !isImmuneToZombification() && !isNoAi(); }

    protected void finishConversion() {
        ZombifiedPiglin z = convertTo(new ZombifiedPiglin(level, level.nextEntityId()), true, "PIGLIN_ZOMBIFIED");
        if (z != null) z.addEffect(new MobEffectInstance(MobEffect.NAUSEA, 200, 0));
    }

    public boolean isAdult() { return !isBaby(); }

    /** 주 손에 든 것이 도구·검 같은 TieredItem 인가. */
    public boolean isHoldingMeleeWeapon() {
        String n = org.domi.furnace.server.item.ItemTools.itemName(getItemBySlot(EquipmentSlot.MAINHAND));
        return n.endsWith("_sword") || n.endsWith("_axe") || n.endsWith("_pickaxe") || n.endsWith("_shovel") || n.endsWith("_hoe");
    }

    @Override public Targetable getTarget() { return getTargetFromBrain(); }

    @Override
    protected void updateNoActionTime() {
        if (PiglinAi.isIdle(this)) super.updateNoActionTime();
    }

    protected abstract void playConvertedSound();
}
