// 바닐라 BreezeWindCharge: 브리즈가 쏘는 윈드 차지. 폭발 반경 3, 밀림 배수 기본.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Explosion;

public class BreezeWindCharge extends AbstractWindCharge {
    private static final Explosion.Calculator CALC = new Explosion.WindCalculator(null);

    public BreezeWindCharge(ChunkLevel level, int id) { super(EntityType.BREEZE_WIND_CHARGE, level, id); }

    @Override
    protected void explode(double x, double y, double z) {
        level.explode(this, x, y, z, 3.0F, CALC, GUST_EMITTER_SMALL, GUST_EMITTER_LARGE, BURST_SOUND);
    }
}
