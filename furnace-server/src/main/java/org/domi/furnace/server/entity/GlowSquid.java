// 바닐라 GlowSquid: 맞으면 100틱 동안 어두워지는(빛이 약해지는) 오징어. 먹물은 발광 먹물 입자.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class GlowSquid extends Squid {
    public static final int DATA_DARK_TICKS_REMAINING = 16;

    public GlowSquid(ChunkLevel level, int id) { super(EntityType.GLOW_SQUID, level, id); }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_DARK_TICKS_REMAINING, SetEntityData.SERIALIZER_INT, 0);
    }

    public int getDarkTicksRemaining() { return (Integer) entityData.get(DATA_DARK_TICKS_REMAINING); }

    private void setDarkTicks(int t) { entityData.set(DATA_DARK_TICKS_REMAINING, t); }

    @Override
    protected void aiStep() {
        super.aiStep();
        int i = getDarkTicksRemaining();
        if (i > 0) setDarkTicks(i - 1);
        // 몸 주변 발광 입자(addParticle)는 클라이언트 쪽
    }

    @Override
    public boolean hurtBy(Damage source, float amount, Targetable attacker) {
        boolean hurt = super.hurtBy(source, amount, attacker);
        if (hurt) setDarkTicks(100);
        return hurt;
    }

    @Override protected String getSquirtSound() { return "entity.glow_squid.squirt"; }

    @Override public String getAmbientSound() { return "entity.glow_squid.ambient"; }

    @Override protected String getHurtSound(Damage source) { return "entity.glow_squid.hurt"; }

    @Override protected String getDeathSound() { return "entity.glow_squid.death"; }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("DarkTicksRemaining", getDarkTicksRemaining());
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        setDarkTicks(tag.getIntOr0("DarkTicksRemaining"));
    }
}
