package org.domi.furnace.server.world.gen.feature;

import org.domi.furnace.server.world.ChunkSection;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class FreezeTopLayerFeature implements ConfiguredFeature {

    // biome.coldEnoughToSnow / warmEnoughToRain 경계 — 온도가 이 미만일 때만 동결/적설.
    private static final float FREEZE_TEMPERATURE = 0.15f;

    private final int waterId;
    private final int iceId;
    private final int snowId;
    private final float[] biomeTemps; // biome id로 인덱싱한 base 온도. 동결 게이트용.

    public FreezeTopLayerFeature(int waterId, int iceId, int snowId, float[] biomeTemps) {
        this.waterId = waterId;
        this.iceId = iceId;
        this.snowId = snowId;
        this.biomeTemps = biomeTemps;
    }

    @Override
    public boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        boolean changed = false;
        for (int dz = 0; dz < 16; dz++) {
            for (int dx = 0; dx < 16; dx++) {
                int worldX = x + dx;
                int worldZ = z + dz;
                int surfaceY = view.getSurfaceY(worldX, worldZ);
                if (surfaceY < org.domi.furnace.server.world.ChunkColumn.MIN_Y) continue;
                // 추운 바이옴(온도<0.15)에서만 동결 — vanilla SnowAndFreezeFeature 게이트.
                int biomeId = view.getBiomeId(worldX, surfaceY, worldZ);
                float temp = (biomeId >= 0 && biomeId < biomeTemps.length) ? biomeTemps[biomeId] : 0.5f;
                if (temp >= FREEZE_TEMPERATURE) continue;
                int state = view.getBlock(worldX, surfaceY, worldZ);
                if (state == waterId) {
                    view.setBlock(worldX, surfaceY, worldZ, iceId);
                    changed = true;
                } else if (state != ChunkSection.AIR && view.getBlock(worldX, surfaceY + 1, worldZ) == ChunkSection.AIR) {
                    view.setBlock(worldX, surfaceY + 1, worldZ, snowId);
                    changed = true;
                }
            }
        }
        return changed;
    }
}
