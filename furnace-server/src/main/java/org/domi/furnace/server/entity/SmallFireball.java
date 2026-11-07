// 바닐라 SmallFireball: 블레이즈가 쏘는 작은 불덩이. 맞은 대상을 5초 태우고 5 피해, 블록에 맞으면 옆 빈 칸에 불을 붙인다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.types.BaseFireBlock;

public class SmallFireball extends Fireball {
    public SmallFireball(ChunkLevel level, int id) { super(EntityType.SMALL_FIREBALL, level, id); }

    @Override
    protected void onHitEntity(Hit hit) {
        Targetable target = hit.target();
        Targetable owner = getOwner();
        int oldFire = target instanceof Entity e ? e.remainingFireTicks() : target instanceof Player pl ? pl.remainingFireTicks() : 0;
        if (target instanceof Entity e) e.igniteForSeconds(5.0F);
        else if (target instanceof Player pl) pl.igniteForSecondsLater(5.0F);
        Damage dmg = new Damage("fireball", true, false, false);
        boolean hurt;
        if (target instanceof LivingEntity le) hurt = le.hurtByVia(dmg, 5.0F, owner, this);
        else hurt = target.hurtByMob(dmg, 5.0F, owner instanceof Mob m ? m : null);
        if (!hurt && target instanceof Entity e) e.setRemainingFireTicks(oldFire);
        else if (!hurt && target instanceof Player pl) pl.runOnOwner(() -> pl.setRemainingFireTicks(oldFire));
    }

    @Override
    protected void onHitBlock(Hit hit) {
        super.onHitBlock(hit);   // 바닐라는 먼저 맞은 블록의 onProjectileHit
        Targetable owner = getOwner();
        if (!(owner instanceof Mob) || level.gameRule("mobGriefing")) {
            Pos pos = hit.blockPos().relative(hit.face());
            if (level.isEmptyBlock(pos)) level.setBlock(pos, BaseFireBlock.getState(level, pos), 3);
        }
    }

    @Override
    protected void onHit(Hit hit) {
        super.onHit(hit);
        discard();
    }
}
