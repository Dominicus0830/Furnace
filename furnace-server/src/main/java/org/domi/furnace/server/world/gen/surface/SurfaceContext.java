// SurfaceRule 트리 평가 시 단일 블록에 대한 상태를 담는 컨텍스트
package org.domi.furnace.server.world.gen.surface;

import org.domi.furnace.server.world.gen.noise.PositionalRandom;
import org.domi.furnace.server.world.gen.WorldgenData;
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
    public PositionalRandom randomFactory;
    public WorldgenData worldgenData;
    public Map<Long, Integer> biomeCache;
    // 이 청크의 quart biome 배열(sampleChunk 결과, 인덱스 (qy*4+qz)*4+qx) — 청크 안쪽 quart는 다시 검색하지 않고 여기서 읽는다. null이면 미사용.
    public int[] chunkBiomeIds;
    public int chunkQuartX0, chunkQuartZ0;   // 청크 첫 quart의 월드 quart 좌표
    /**
     * 청크 바로 바깥 한 줄(이웃 청크) quart 의 바이옴 메모 — biomeCache(HashMap) 대신 배열로(성능, 값은 같은 sampleQuart 결과).
     * x·z 는 chunkQuart0-1 .. chunkQuart0+4(6칸), y 는 haloQuartY0 부터 haloQuartYCount 칸. 빈 칸은 Integer.MIN_VALUE. null 이면 미사용.
     */
    public int[] haloBiomes;
    public int haloQuartY0, haloQuartYCount;
    /**
     * haloBiomes 와 같은 칸 배치의 꼭짓점 지터(fiddle x·y·z, 칸마다 3개) 메모. 안 구한 칸은 NaN. null 이면 블록마다 계산(예전 경로).
     * 이 표가 있으면 haloBiomes 의 청크 안쪽 높이 범위 칸도 chunkBiomeIds 값으로 채워져 있다.
     */
    public double[] zoomFiddles;
    // 16×16 surfaceY grid for steep condition
    public int[] surfaceYGrid;  // [lz*16+lx]

    // ---- 컴파일된 규칙용 캐시(SurfaceSystem.Compiled) ----
    /** 현재 블록의 바이옴 id 를 이미 구했는가(블록마다 false 로 되돌린다). */
    public boolean biomeIdValid;
    public int biomeId;
    /** 기둥 번호 — 바뀌면 기둥 단위 캐시(xz)가 무효가 된다. */
    public int column;
    /** 기둥 단위 캐시: 슬롯별 값과 그 값을 구한 기둥 번호. */
    public double[] xzValue;
    public int[] xzColumn;
}
