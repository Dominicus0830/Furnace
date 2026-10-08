// SurfaceRule 트리 평가 시 단일 블록에 대한 상태를 담는 컨텍스트
package org.domi.furnace.server.world.gen.surface;

import org.domi.furnace.server.world.gen.WorldgenData;
import org.domi.furnace.server.world.gen.noise.XoroshiroRandomSource;
import java.util.Map;

public final class SurfaceContext {
    public int blockX, blockY, blockZ;
    public String biomeName;
    public int stoneDepthAbove;
    public int stoneDepthBelow;
    public int surfaceY;
    public int waterY;
    public int preliminarySurfaceLevel;
    public int surfaceDepth;
    public int minY;
    public int maxY;
    public int seaLevel;
    public XoroshiroRandomSource.XoroshiroPositionalRandomFactory randomFactory;
    public WorldgenData worldgenData;
    public Map<Long, Integer> biomeCache;
    // 이 청크의 quart biome 배열(sampleChunk 결과, 인덱스 (qy*4+qz)*4+qx) — 청크 안쪽 quart는 다시 검색하지 않고 여기서 읽는다. null이면 미사용.
    public int[] chunkBiomeIds;
    public int chunkQuartX0, chunkQuartZ0;   // 청크 첫 quart의 월드 quart 좌표
    // 16×16 surfaceY grid for steep condition
    public int[] surfaceYGrid;  // [lz*16+lx]
}
