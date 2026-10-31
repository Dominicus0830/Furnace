// 바닐라 LargeFireball: 가스트가 쏘는 큰 불덩이. 맞으면 폭발(explosionPower, mobGriefing 이 켜졌을 때만 블록을 부수고 불을 낸다)하고 맞은 대상에 6 피해.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public class LargeFireball extends Fireball {
    private int explosionPower = 1;

    public LargeFireball(ChunkLevel level, int id) { super(EntityType.FIREBALL, level, id); }

    /** redirectable_projectile 태그(fireball): 플레이어가 때릴 수 있다. */
    @Override public boolean isPickable() { return true; }

    public void setExplosionPower(int p) { explosionPower = p; }

    @Override
    protected void onHit(Hit hit) {
        super.onHit(hit);
        boolean griefing = level.gameRule("mobGriefing");
        level.explode(this, x(), y(), z(), explosionPower, griefing, level.destroyType("MOB"));
        discard();
    }

    @Override
    protected void onHitEntity(Hit hit) {
        Targetable target = hit.target();
        Targetable owner = getOwner();
        Damage dmg = new Damage("fireball", true, false, false);
        if (target instanceof LivingEntity le) le.hurtByVia(dmg, 6.0F, owner, this);
        else target.hurtByMob(dmg, 6.0F, owner instanceof Mob m ? m : null);
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("ExplosionPower", (byte) explosionPower);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("ExplosionPower")) explosionPower = tag.getByte("ExplosionPower");
    }
}
