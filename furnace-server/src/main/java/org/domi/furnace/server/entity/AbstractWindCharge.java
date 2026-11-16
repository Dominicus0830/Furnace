// 바닐라 AbstractWindCharge: 가속·중력 없이 날다 맞으면 1 피해를 주고 윈드 폭발(블록을 부수지 않고 문·레버 등을 건드린다)을 일으킨다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Explosion;

public abstract class AbstractWindCharge extends AbstractHurtingProjectile {
    public static final int GUST_EMITTER_LARGE = 25, GUST_EMITTER_SMALL = 26;
    public static final String BURST_SOUND = "minecraft:entity.wind_charge.wind_burst";

    protected AbstractWindCharge(EntityType type, ChunkLevel level, int id) {
        super(type, level, id);
        accelerationPower = 0.0;
    }

    /** 상자는 위치에서 0.15 아래로 내려 잡는다. */
    @Override
    protected AABB makeBb(double px, double py, double pz) {
        double hw = dimW / 2.0F;
        return new AABB(px - hw, py - 0.15F, pz - hw, px + hw, py - 0.15F + dimH, pz + hw);
    }

    protected abstract void explode(double x, double y, double z);

    /** 윈드 차지끼리는 부딪히지 않는다. */
    @Override
    public boolean canCollideWith(Entity other) { return !(other instanceof AbstractWindCharge) && super.canCollideWith(other); }

    /** 테스트용: 폭발을 직접 일으킨다. */
    public void explodeAt(double x, double y, double z) { explode(x, y, z); }

    @Override
    protected boolean canHitTarget(Targetable t) {
        return !(t instanceof AbstractWindCharge) && !(t instanceof EndCrystal) && super.canHitTarget(t);
    }

    @Override
    protected void onHitEntity(Hit hit) {
        Targetable owner = getOwner();
        Damage dmg = new Damage("wind_charge", false, false, false);
        if (hit.target() instanceof LivingEntity le) le.hurtByVia(dmg, 1.0F, owner, this);
        else hit.target().hurtByMob(dmg, 1.0F, owner instanceof Mob m ? m : null);
        explode(x(), y(), z());
    }

    @Override
    protected void onHitBlock(Hit hit) {
        super.onHitBlock(hit);   // 바닐라는 먼저 맞은 블록의 onProjectileHit
        explode(x(), y(), z());
    }

    @Override
    protected void onHit(Hit hit) {
        super.onHit(hit);
        discard();
    }

    @Override
    public void tick() {
        super.tick();
        if (y() > level.maxBuildHeight() + 30) discard();
    }

    @Override protected boolean shouldBurn() { return false; }

    @Override protected float getInertia() { return 1.0F; }

    @Override protected float getLiquidInertia() { return getInertia(); }

    @Override public boolean hurt(Damage source, float amount) { return false; }
}
