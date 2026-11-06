// 바닐라 ThrownEgg: 던진 달걀. 맞으면 1/8 확률로 병아리가 나온다(그중 1/32 은 네 마리). 피해는 0.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;

public class ThrownEgg extends ThrowableProjectile {
    public ThrownEgg(ChunkLevel level, int id) { super(EntityType.EGG, level, id); }

    @Override protected String defaultItem() { return "minecraft:egg"; }

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
        if (random.nextInt(8) == 0) {
            int count = random.nextInt(32) == 0 ? 4 : 1;
            for (int i = 0; i < count; i++) {
                Chicken chicken = new Chicken(level, level.nextEntityId());
                chicken.setAge(-24000);
                chicken.moveTo(x(), y(), z(), yRot(), 0.0F);
                level.addFreshEntity(chicken.spawnReason("EGG"));
            }
        }
        broadcastEntityEvent((byte) 3);
        discard();
    }
}
