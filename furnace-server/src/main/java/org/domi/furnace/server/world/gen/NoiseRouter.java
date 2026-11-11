// 바닐라 NoiseRouter의 경량 포트. final_density + initialDensityWithoutJaggedness + 6개 기후 DF 보유.
package org.domi.furnace.server.world.gen;

import org.domi.furnace.server.world.gen.noise.PositionalRandom;
import com.google.gson.JsonObject;
import org.domi.furnace.server.world.gen.df.DensityFunction;
import org.domi.furnace.server.world.gen.df.DensityFunctionParser;

public final class NoiseRouter {

    private final DensityFunction finalDensity;
    private final DensityFunction initialDensityWithoutJaggedness;
    private final DensityFunction temperature;
    private final DensityFunction humidity;   // JSON 필드명: "vegetation"
    private final DensityFunction continents;
    private final DensityFunction erosion;
    private final DensityFunction depth;
    private final DensityFunction ridges;     // JSON 필드명: "ridges" (= weirdness)
    private final DensityFunction barrierNoise;
    private final DensityFunction fluidLevelFloodednessNoise;
    private final DensityFunction fluidLevelSpreadNoise;
    private final DensityFunction lavaNoise;
    private final DensityFunction veinToggle;
    private final DensityFunction veinRidged;
    private final DensityFunction veinGap;
    private final PositionalRandom aquiferRandom;
    private final PositionalRandom oreRandom;
    /** RandomState.random — 차원 난수 팩토리(legacy 차원은 LegacyPositionalRandom). 지표·영역 난수의 뿌리. */
    private final PositionalRandom random;

    /**
     * 예비 지표 높이(NoiseChunk.getPreliminarySurfaceLevel) 공유 캐시 — 같은 라우터(시드)에서 위치만의 함수라
     * 청크·대수층·카버마다 다시 계산하던 것을 나눠 쓴다. 잠금 없는 직접 사상 캐시: 칸마다 (quartX 24비트, quartZ 24비트,
     * 값 16비트)를 long 하나에 담아 원자적으로 읽고 쓴다(충돌하면 덮어쓸 뿐 틀린 값을 돌려주지 않는다).
     */
    /** 셀 꼭짓점 기둥 공유 캐시(이웃 청크가 경계 기둥을 다시 계산하지 않게) — {@link SliceColumnCache}. 4096칸. */
    private final SliceColumnCache sliceColumns = new SliceColumnCache(12);

    SliceColumnCache sliceColumns() { return sliceColumns; }

    private final java.util.concurrent.atomic.AtomicLongArray pslCache = new java.util.concurrent.atomic.AtomicLongArray(1 << 16);
    private static final int PSL_NONE = Integer.MAX_VALUE;   // 지표 없음(바닐라 MAX_VALUE)

    /** 캐시된 예비 지표 높이, 없으면 Integer.MIN_VALUE. */
    public int cachedPreliminarySurface(int quartX, int quartZ) {
        if (!pslFits(quartX, quartZ)) return Integer.MIN_VALUE;
        long e = pslCache.get(pslSlot(quartX, quartZ));
        if (e == 0 || (e >>> 16) != pslKey(quartX, quartZ)) return Integer.MIN_VALUE;
        int v = (int) (e & 0xFFFF);
        return v == 0xFFFF ? PSL_NONE : v - 0x4000;
    }

    public void cachePreliminarySurface(int quartX, int quartZ, int level) {
        if (!pslFits(quartX, quartZ)) return;
        int v;
        if (level == PSL_NONE) v = 0xFFFF;
        else if (level >= -0x3FFF && level < 0xBFFF) v = level + 0x4000;
        else return;
        pslCache.set(pslSlot(quartX, quartZ), (pslKey(quartX, quartZ) << 16) | v);
    }

    private static boolean pslFits(int qx, int qz) {
        return qx >= -(1 << 23) && qx < (1 << 23) && qz >= -(1 << 23) && qz < (1 << 23);
    }

    private static long pslKey(int qx, int qz) {
        return ((long) (qx & 0xFFFFFF) << 24) | (qz & 0xFFFFFF);
    }

    private static int pslSlot(int qx, int qz) {
        int h = qx * 0x9E3779B1 + qz * 0x85EBCA77;
        return (h ^ (h >>> 16)) & ((1 << 16) - 1);
    }

    private NoiseRouter(DensityFunction finalDensity,
                        DensityFunction initialDensityWithoutJaggedness,
                        DensityFunction temperature, DensityFunction humidity,
                        DensityFunction continents, DensityFunction erosion,
                        DensityFunction depth, DensityFunction ridges,
                        DensityFunction barrierNoise,
                        DensityFunction fluidLevelFloodednessNoise,
                        DensityFunction fluidLevelSpreadNoise,
                        DensityFunction lavaNoise,
                        DensityFunction veinToggle, DensityFunction veinRidged, DensityFunction veinGap,
                        PositionalRandom aquiferRandom,
                        PositionalRandom oreRandom,
                        PositionalRandom random) {
        this.random = random;
        this.finalDensity = finalDensity;
        this.initialDensityWithoutJaggedness = initialDensityWithoutJaggedness;
        this.temperature  = temperature;
        this.humidity     = humidity;
        this.continents   = continents;
        this.erosion      = erosion;
        this.depth        = depth;
        this.ridges       = ridges;
        this.barrierNoise = barrierNoise;
        this.fluidLevelFloodednessNoise = fluidLevelFloodednessNoise;
        this.fluidLevelSpreadNoise = fluidLevelSpreadNoise;
        this.lavaNoise = lavaNoise;
        this.veinToggle = veinToggle;
        this.veinRidged = veinRidged;
        this.veinGap = veinGap;
        this.aquiferRandom = aquiferRandom;
        this.oreRandom = oreRandom;
    }

    public static NoiseRouter build(WorldgenData data, NoiseSettings settings, long seed) {
        DensityFunctionParser parser = new DensityFunctionParser(data, seed, settings.legacyRandomSource());
        JsonObject routerJson = data.noiseSettings(settings.name())
                                    .get("noise_router").getAsJsonObject();
        return new NoiseRouter(
            parser.parseElement(routerJson.get("final_density")),
            parser.parseElement(routerJson.get("initial_density_without_jaggedness")),
            parser.parseElement(routerJson.get("temperature")),
            parser.parseElement(routerJson.get("vegetation")),   // humidity
            parser.parseElement(routerJson.get("continents")),
            parser.parseElement(routerJson.get("erosion")),
            parser.parseElement(routerJson.get("depth")),
            parser.parseElement(routerJson.get("ridges")),
            parser.parseElement(routerJson.get("barrier")),
            parser.parseElement(routerJson.get("fluid_level_floodedness")),
            parser.parseElement(routerJson.get("fluid_level_spread")),
            parser.parseElement(routerJson.get("lava")),
            parser.parseElement(routerJson.get("vein_toggle")),
            parser.parseElement(routerJson.get("vein_ridged")),
            parser.parseElement(routerJson.get("vein_gap")),
            parser.aquiferRandom(),
            parser.oreRandom(),
            parser.positional()
        );
    }

    public DensityFunction finalDensity() { return finalDensity; }
    public DensityFunction initialDensityWithoutJaggedness() { return initialDensityWithoutJaggedness; }
    public DensityFunction temperature() { return temperature; }
    public DensityFunction humidity() { return humidity; }
    public DensityFunction continents() { return continents; }
    public DensityFunction erosion() { return erosion; }
    public DensityFunction depth() { return depth; }
    public DensityFunction ridges() { return ridges; }
    public DensityFunction barrierNoise() { return barrierNoise; }
    public DensityFunction fluidLevelFloodednessNoise() { return fluidLevelFloodednessNoise; }
    public DensityFunction fluidLevelSpreadNoise() { return fluidLevelSpreadNoise; }
    public DensityFunction lavaNoise() { return lavaNoise; }
    public DensityFunction veinToggle() { return veinToggle; }
    public DensityFunction veinRidged() { return veinRidged; }
    public DensityFunction veinGap() { return veinGap; }
    public PositionalRandom aquiferRandom() { return aquiferRandom; }
    public PositionalRandom oreRandom() { return oreRandom; }
    public PositionalRandom random() { return random; }
}
