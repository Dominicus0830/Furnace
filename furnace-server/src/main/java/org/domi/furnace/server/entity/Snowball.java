// 바닐라 Snowball: 던진 눈덩이. 맞은 대상에게 피해 0(블레이즈는 3)을 주고 사라진다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;

public class Snowball extends ThrowableProjectile {
    public Snowball(ChunkLevel level, int id) { super(EntityType.SNOWBALL, level, id); }

    @Override protected String defaultItem() { return "minecraft:snowball"; }

    @Override
    protected void onHitEntity(Hit hit) {
        Targetable target = hit.target();
        Targetable owner = getOwner();
        float damage = target instanceof Blaze ? 3.0F : 0.0F;
        Damage dmg = new Damage("thrown", false, false, false);
        if (target instanceof LivingEntity le) le.hurtByVia(dmg, damage, owner, this);
        else target.hurtByMob(dmg, damage, owner instanceof Mob m ? m : null);
    }

    @Override
    protected void onHit(Hit hit) {
        super.onHit(hit);
        broadcastEntityEvent((byte) 3);
        discard();
    }
}
