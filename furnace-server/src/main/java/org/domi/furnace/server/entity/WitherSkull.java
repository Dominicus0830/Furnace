// 바닐라 WitherSkull: 위더가 쏘는 해골 머리. 맞으면 피해(소유자가 생물이면 8 — 죽이면 소유자 회복 5, 아니면 마법 5)와 위더 효과, 반경 1 폭발.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class WitherSkull extends AbstractHurtingProjectile {
    public static final int DATA_DANGEROUS = 8;

    public WitherSkull(ChunkLevel level, int id) { super(EntityType.WITHER_SKULL, level, id); }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_DANGEROUS, SetEntityData.SERIALIZER_BOOLEAN, false);
    }

    public boolean isDangerous() { return (Boolean) entityData.get(DATA_DANGEROUS); }

    public void setDangerous(boolean b) { entityData.set(DATA_DANGEROUS, b); }

    @Override protected float getInertia() { return isDangerous() ? 0.73F : super.getInertia(); }

    @Override protected boolean shouldBurn() { return false; }

    @Override public boolean hurt(Damage source, float amount) { return false; }

    @Override
    protected void onHitEntity(Hit hit) {
        Targetable target = hit.target();
        Targetable owner = getOwner();
        boolean hurt;
        if (owner instanceof LivingEntity ownerLiving) {
            Damage dmg = new Damage("wither_skull", false, false, false);
            hurt = target instanceof LivingEntity le ? le.hurtByVia(dmg, 8.0F, owner, this) : target.hurtByMob(dmg, 8.0F, owner instanceof Mob m ? m : null);
            if (hurt && !target.isAlive()) ownerLiving.heal(5.0F);
        } else {
            Damage magic = new Damage("magic", false, false, false);
            hurt = target instanceof LivingEntity le ? le.hurtByVia(magic, 5.0F, null, this) : target.hurtByMob(magic, 5.0F, null);
        }
        if (hurt) {
            int seconds = switch (level.difficulty()) {
                case 2 -> 10;
                case 3 -> 40;
                default -> 0;
            };
            if (seconds > 0) {
                MobEffectInstance eff = new MobEffectInstance(MobEffect.WITHER, 20 * seconds, 1);
                if (target instanceof LivingEntity le) le.addEffect(eff);
                else if (target instanceof Player p) p.vitals().addEffect(eff);
            }
        }
    }

    @Override
    protected void onHit(Hit hit) {
        super.onHit(hit);
        level.explode(this, x(), y(), z(), 1.0F, false, level.destroyType("MOB"));
        discard();
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("dangerous", isDangerous());
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("dangerous")) setDangerous(tag.getBoolean("dangerous"));
    }
}
