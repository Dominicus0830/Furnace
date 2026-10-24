// 바닐라 Mob: 몹 플래그(NoAI·왼손잡이·공격 중)와 영속(PersistenceRequired). AI·길찾기는 아직 없다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public abstract class Mob extends LivingEntity {

    /** Mob.DATA_MOB_FLAGS_ID = 15: 1 NoAI, 2 왼손잡이, 4 공격 중. */
    public static final int DATA_MOB_FLAGS = 15;

    protected boolean persistenceRequired;

    protected Mob(EntityType type, ChunkLevel level, int id) { super(type, level, id); }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_MOB_FLAGS, SetEntityData.SERIALIZER_BYTE, (byte) 0);
    }

    public boolean isNoAi() { return ((Byte) entityData.get(DATA_MOB_FLAGS) & 1) != 0; }

    public void setNoAi(boolean v) {
        byte b = (Byte) entityData.get(DATA_MOB_FLAGS);
        entityData.set(DATA_MOB_FLAGS, (byte) (v ? b | 1 : b & ~1));
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("PersistenceRequired", persistenceRequired);
        tag.putBoolean("LeftHanded", ((Byte) entityData.get(DATA_MOB_FLAGS) & 2) != 0);
        if (isNoAi()) tag.putBoolean("NoAI", true);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        persistenceRequired = tag.getBoolean("PersistenceRequired");
        byte b = (byte) ((tag.getBoolean("LeftHanded") ? 2 : 0));
        entityData.set(DATA_MOB_FLAGS, b);
        setNoAi(tag.getBoolean("NoAI"));
    }
}
