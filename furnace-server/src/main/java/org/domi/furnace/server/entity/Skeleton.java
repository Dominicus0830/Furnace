// 바닐라 Skeleton: 분말눈 속에서 140틱 지나면 300틱 뒤 스트레이로 변한다. TODO: 변환 소리·크리퍼 폭발 시 머리 떨구기.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class Skeleton extends AbstractSkeleton {
    public static final int DATA_STRAY_CONVERSION = 16;
    private int inPowderSnowTime;
    private int conversionTime;

    public Skeleton(ChunkLevel level, int id) { super(EntityType.SKELETON, level, id); }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_STRAY_CONVERSION, SetEntityData.SERIALIZER_BOOLEAN, false);
    }

    public boolean isFreezeConverting() { return (Boolean) entityData.get(DATA_STRAY_CONVERSION); }

    public void setFreezeConverting(boolean v) { entityData.set(DATA_STRAY_CONVERSION, v); }

    @Override
    public void tick() {
        if (isAlive() && !isNoAi()) {
            if (isInPowderSnow()) {
                if (isFreezeConverting()) {
                    conversionTime--;
                    if (conversionTime < 0) doFreezeConversion();
                } else {
                    inPowderSnowTime++;
                    if (inPowderSnowTime >= 140) startFreezeConversion(300);
                }
            } else {
                inPowderSnowTime = -1;
                setFreezeConverting(false);
            }
        }
        super.tick();
    }

    private void startFreezeConversion(int ticks) {
        conversionTime = ticks;
        setFreezeConverting(true);
    }

    private void doFreezeConversion() {
        Stray stray = convertTo(new Stray(level, level.nextEntityId()), true, "FROZEN");
        if (stray != null && !isSilent()) level.levelEvent(null, 1048, blockPosition(), 0);
    }


    @Override public String getAmbientSound() { return "entity.skeleton.ambient"; }

    @Override protected String getHurtSound(Damage source) { return "entity.skeleton.hurt"; }

    @Override protected String getDeathSound() { return "entity.skeleton.death"; }

    @Override String getStepSound() { return "entity.skeleton.step"; }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("StrayConversionTime", isFreezeConverting() ? conversionTime : -1);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("StrayConversionTime") && tag.getInt("StrayConversionTime") > -1) startFreezeConversion(tag.getInt("StrayConversionTime"));
    }

    /** 충전 크리퍼 폭발에 죽으면 머리를 한 번 떨군다(바닐라 dropCustomDeathLoot). */
    @Override
    protected void dropCustomDeathLoot(Damage source, boolean byPlayer) {
        super.dropCustomDeathLoot(source, byPlayer);
        Creeper.dropSkullIfCharged(this, damageSourceEntity(), "skeleton_skull");
    }
}
