// 바닐라 WindCharge: 플레이어·몹이 던지는 윈드 차지. 폭발 반경 1.2, 밀림 1.22배. 던진 직후 5틱은 소유자에게 맞지 않는다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Explosion;

public class WindCharge extends AbstractWindCharge {
    private static final Explosion.Calculator CALC = new Explosion.WindCalculator(1.22F);
    private int noDeflectTicks = 5;

    public WindCharge(ChunkLevel level, int id) { super(EntityType.WIND_CHARGE, level, id); }

    @Override
    public void tick() {
        if (noDeflectTicks > 0) noDeflectTicks--;
        super.tick();
    }

    @Override
    protected boolean canHitTarget(Targetable t) {
        return (noDeflectTicks <= 0 || t != getOwner()) && super.canHitTarget(t);
    }

    @Override
    protected void explode(double x, double y, double z) {
        level.explode(this, x, y, z, 1.2F, CALC, GUST_EMITTER_SMALL, GUST_EMITTER_LARGE, BURST_SOUND);
    }
}
