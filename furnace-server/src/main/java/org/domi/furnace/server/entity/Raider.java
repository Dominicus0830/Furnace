// 바닐라 Raider(+PatrollingMonster) 의 뼈대: 습격 파도(wave)·참여 여부·순찰 지도자/목표를 저장한다. TODO: 습격(Raid) 시스템과 순찰 목표들(배너·마을 찾기·문 열기·축하).
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public abstract class Raider extends Monster {
    public static final int DATA_IS_CELEBRATING = 16;
    private int wave;
    private boolean canJoinRaid;
    private boolean patrolLeader;
    private int[] patrolTarget;
    private boolean patrolling;

    protected Raider(EntityType type, ChunkLevel level, int id) { super(type, level, id); }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_IS_CELEBRATING, SetEntityData.SERIALIZER_BOOLEAN, false);
    }

    /** Raider.applyRaidBuffs(level, wave, isCaptain): 각 종류가 파도에 따라 장비를 준다(습격 구현 때 사용). */
    public abstract void applyRaidBuffs(int wave, boolean captain);

    public boolean hasActiveRaid() { return false; }

    public boolean isCelebrating() { return (Boolean) entityData.get(DATA_IS_CELEBRATING); }

    public void setCelebrating(boolean v) { entityData.set(DATA_IS_CELEBRATING, v); }

    public int getWave() { return wave; }

    public void setWave(int w) { wave = w; }

    public boolean canJoinRaid() { return canJoinRaid; }

    public void setCanJoinRaid(boolean v) { canJoinRaid = v; }

    public boolean isPatrolLeader() { return patrolLeader; }

    public void setPatrolLeader(boolean v) { patrolLeader = v; }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Wave", wave);
        tag.putBoolean("CanJoinRaid", canJoinRaid);
        if (patrolTarget != null) {
            NbtCompound t = new NbtCompound();
            t.putInt("X", patrolTarget[0]);
            t.putInt("Y", patrolTarget[1]);
            t.putInt("Z", patrolTarget[2]);
            tag.putCompound("PatrolTarget", t);
        }
        tag.putBoolean("PatrolLeader", patrolLeader);
        tag.putBoolean("Patrolling", patrolling);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        wave = tag.getIntOr0("Wave");
        canJoinRaid = tag.getBoolean("CanJoinRaid");
        if (tag.tags().get("PatrolTarget") instanceof NbtCompound t) patrolTarget = new int[] {t.getIntOr0("X"), t.getIntOr0("Y"), t.getIntOr0("Z")};
        patrolLeader = tag.getBoolean("PatrolLeader");
        patrolling = tag.getBoolean("Patrolling");
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) { return !hasActiveRaid() && (!patrolling || distSqr > 16384.0); }
}
