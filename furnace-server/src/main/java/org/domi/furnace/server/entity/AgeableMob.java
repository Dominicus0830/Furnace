// 바닐라 AgeableMob: 나이(음수면 아기, 틱마다 0 쪽으로), 강제 나이, 아기 표시 데이터(16).
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public abstract class AgeableMob extends PathfinderMob {
    public static final int DATA_BABY_ID = 16;
    public static final int BABY_START_AGE = -24000;

    protected int age;
    protected int forcedAge;
    protected int forcedAgeTimer;

    protected AgeableMob(EntityType type, ChunkLevel level, int id) { super(type, level, id); }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_BABY_ID, SetEntityData.SERIALIZER_BOOLEAN, false);
    }

    /** AgeableMob.getBreedOffspring: 새끼를 만든다(없으면 null). */
    public abstract AgeableMob getBreedOffspring(ChunkLevel level, AgeableMob partner);

    public boolean canBreed() { return false; }

    public int getAge() { return age; }

    /** AgeableMob.setAge: 아기 ↔ 어른이 바뀔 때만 데이터와 크기를 갱신한다. */
    public void setAge(int v) {
        int old = getAge();
        age = v;
        if (old < 0 && v >= 0 || old >= 0 && v < 0) {
            entityData.set(DATA_BABY_ID, v < 0);
            ageBoundaryReached();
            refreshBabyDimensions();
        }
    }

    public void ageUp(int seconds, boolean forced) {
        int old = getAge();
        int a = old + seconds * 20;
        if (a > 0) a = 0;
        int delta = a - old;
        setAge(a);
        if (forced) {
            forcedAge += delta;
            if (forcedAgeTimer == 0) forcedAgeTimer = 40;
        }
        if (getAge() == 0) setAge(forcedAge);
    }

    public void ageUp(int seconds) { ageUp(seconds, false); }

    protected void ageBoundaryReached() {}

    /** 아기 몸 크기 배율(기본 0.5, 거북 0.3). */
    protected float babyScale() { return 0.5F; }

    @Override
    public float getAgeScale() { return isBaby() ? babyScale() : 1.0F; }

    protected void refreshBabyDimensions() {
        float s = isBaby() ? babyScale() : 1.0F;
        EntityType t = type();
        setDimensions(t.width() * s, t.height() * s, t.eyeHeight() * s);
    }

    @Override
    public boolean isBaby() { return getAge() < 0; }

    public void setBaby(boolean baby) { setAge(baby ? BABY_START_AGE : 0); }

    @Override
    protected void aiStep() {
        super.aiStep();
        if (isAlive()) {
            int a = getAge();
            if (a < 0) setAge(a + 1);
            else if (a > 0) setAge(a - 1);
        }
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Age", getAge());
        tag.putInt("ForcedAge", forcedAge);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        setAge(tag.getInt("Age"));
        forcedAge = tag.getInt("ForcedAge");
    }

    @Override
    public Object finalizeSpawnGroup(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty, Object group) {
        return finalizeSpawn(r, difficulty, group instanceof GroupData g ? g : null);
    }

    /** AgeableMob.finalizeSpawn: 그룹 데이터가 없으면 새로 만들어, 확률로 아기를 낸다. */
    public static class GroupData {
        public int groupSize;
        public final boolean shouldSpawnBaby;
        public final float babySpawnChance;

        public GroupData(boolean shouldSpawnBaby, float babySpawnChance) {
            this.shouldSpawnBaby = shouldSpawnBaby;
            this.babySpawnChance = babySpawnChance;
        }

        public GroupData(boolean shouldSpawnBaby) { this(shouldSpawnBaby, 0.05F); }

        public GroupData(float babyChance) { this(true, babyChance); }
    }

    public GroupData finalizeSpawn(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty, GroupData data) {
        if (data == null) data = new GroupData(true);
        if (data.shouldSpawnBaby && data.groupSize > 0 && r.nextFloat() <= data.babySpawnChance) setAge(BABY_START_AGE);
        data.groupSize++;
        finalizeSpawn(r, difficulty);
        return data;
    }
}
