// void_start_platform — 바닐라 VoidStartPlatformFeature 이식 (the_void 바이옴의 스폰 발판: 청크(0,0) 주변에 돌 원판 + 중심 조약돌)
package org.domi.furnace.server.world.gen.feature;

import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class VoidStartPlatformFeature implements ConfiguredFeature {

    // PLATFORM_ORIGIN = (8, 3, 8), PLATFORM_ORIGIN_CHUNK = 청크 (0, 0)
    private static final int OX = 8, OY = 3, OZ = 8;
    private static final int PLATFORM_RADIUS = 16;
    private static final int PLATFORM_RADIUS_CHUNKS = 1;

    private final int stone;
    private final int cobblestone;

    public VoidStartPlatformFeature(BlockRegistry registry) {
        this.stone = registry.defaultStateId("minecraft:stone");
        this.cobblestone = registry.defaultStateId("minecraft:cobblestone");
    }

    private static int checkerboardDistance(int x1, int z1, int x2, int z2) {
        return Math.max(Math.abs(x1 - x2), Math.abs(z1 - z2));
    }

    @Override
    public boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        int chunkX = x >> 4, chunkZ = z >> 4;
        if (checkerboardDistance(chunkX, chunkZ, OX >> 4, OZ >> 4) > PLATFORM_RADIUS_CHUNKS) return true;
        int cy = y + OY;   // PLATFORM_ORIGIN.atY(origin.getY() + PLATFORM_ORIGIN.getY())
        for (int bz = chunkZ << 4; bz <= (chunkZ << 4) + 15; bz++) {
            for (int bx = chunkX << 4; bx <= (chunkX << 4) + 15; bx++) {
                if (checkerboardDistance(OX, OZ, bx, bz) <= PLATFORM_RADIUS) {
                    view.setBlock(bx, cy, bz, bx == OX && bz == OZ ? cobblestone : stone);
                }
            }
        }
        return true;
    }
}
