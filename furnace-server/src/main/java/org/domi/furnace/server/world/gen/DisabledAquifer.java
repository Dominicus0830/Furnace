// 바닐라 DisabledAquifer 포팅 — aquifer 비활성 시 단순 Y 기반 수체 채우기
package org.domi.furnace.server.world.gen;

import org.domi.furnace.server.world.gen.df.DensityFunction;

public record DisabledAquifer(int seaLevel, int lavaFloorY, int waterId, int lavaId)
        implements Aquifer {

    @Override
    public int computeSubstance(DensityFunction.FunctionContext ctx, double density) {
        int y = ctx.blockY();
        if (y < lavaFloorY) return lavaId;
        if (y < seaLevel)   return waterId;
        return 0;
    }
}
