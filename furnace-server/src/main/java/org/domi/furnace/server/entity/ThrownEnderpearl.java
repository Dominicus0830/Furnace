// 바닐라 ThrownEnderpearl: 던진 엔더 진주. 맞은 자리로 소유자가 순간이동하고 낙하 피해 5 를 받으며, 5% 확률로 엔더마이트가 나온다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;

public class ThrownEnderpearl extends ThrowableProjectile {
    public ThrownEnderpearl(ChunkLevel level, int id) { super(EntityType.ENDER_PEARL, level, id); }

    @Override protected String defaultItem() { return "minecraft:ender_pearl"; }

    @Override
    protected void onHitEntity(Hit hit) {
        Targetable owner = getOwner();
        Damage dmg = new Damage("thrown", false, false, false);
        if (hit.target() instanceof LivingEntity le) le.hurtByVia(dmg, 0.0F, owner, this);
        else hit.target().hurtByMob(dmg, 0.0F, owner instanceof Mob m ? m : null);
    }

    @Override
    protected void onHit(Hit hit) {
        super.onHit(hit);
        for (int i = 0; i < 32; i++) {   // 입자는 클라이언트 몫이지만 인자 평가로 엔티티 난수는 서버에서도 쓰인다
            random.nextDouble();
            random.nextGaussian();
            random.nextGaussian();
        }
        Targetable owner = getOwner();
        if (owner instanceof LivingEntity le && le.isAlive()) {
            if (random.nextFloat() < 0.05F && level.gameRule("doMobSpawning")) {
                Endermite mite = new Endermite(level, level.nextEntityId());
                mite.moveTo(le.x(), le.y(), le.z(), le.yRot(), le.xRot());
                level.addFreshEntity(mite);
            }
            le.teleportTo(x(), y(), z());
            le.resetFallDistance();
            le.hurt(new Damage("fall", false, false, false), 5.0F);
        }
        if (owner instanceof Player p && !p.isDisconnected()) {
            if (random.nextFloat() < 0.05F && level.gameRule("doMobSpawning")) {
                Endermite mite = new Endermite(level, level.nextEntityId());
                mite.moveTo(p.x(), p.y(), p.z(), p.yaw(), p.pitch());
                level.addFreshEntity(mite);
            }
            p.teleportTo(x(), y(), z());
            p.vitals().resetFallDistance();
            p.vitals().hurt(org.domi.furnace.server.entity.damage.DamageType.FALL, 5.0F);
        }
        discard();
    }
}
