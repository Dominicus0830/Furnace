// 바닐라 NoiseChunk 셀 기반 샘플링 + 삼선형 보간 엔진 비트정확 포팅
package org.domi.furnace.server.world.gen;

import org.domi.furnace.server.world.gen.df.DensityFunction;
import org.domi.furnace.server.world.gen.df.DensityFunctions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 청크 1개에 대한 셀 기반 밀도 샘플링 + 삼선형 보간.
 *
 * 바닐라 NoiseChunk 를 수직 채우기(fill loop)에 필요한 최소 범위로 포팅.
 * Aquifer / Beardifier / Blender 는 제외하고 final_density 보간만 구현.
 *
 * 핵심 구조:
 *   - wrap(DensityFunction): marker 노드를 셀 인식 구현체로 교체
 *   - NoiseInterpolator: 셀 코너 슬라이스 버퍼 + 삼선형 보간
 *   - FlatCache / Cache2D / CacheOnce / CacheAllInCell: 캐시 구현체
 *   - stoneMask(): 전체 청크에 대해 interpolated final_density > 0 여부 반환
 */
public final class NoiseChunk implements DensityFunction.ContextProvider, DensityFunction.FunctionContext {

    // ── 청크/셀 구조 파라미터 ──────────────────────────────────────────────
    final int cellCountXZ;   // = 16 / cellWidth  = 4
    final int cellCountY;    // = height / cellHeight = 48
    final int cellNoiseMinY; // = floorDiv(minY, cellHeight) = -8
    private final int firstCellX;  // = floorDiv(chunkX*16, cellWidth)
    private final int firstCellZ;  // = floorDiv(chunkZ*16, cellWidth)
    final int firstNoiseX;   // = quartFromBlock(chunkX*16) = chunkX*4
    final int firstNoiseZ;   // = quartFromBlock(chunkZ*16) = chunkZ*4
    final int noiseSizeXZ;   // = quartFromBlock(cellCountXZ * cellWidth)
    final int cellWidth;
    final int cellHeight;

    // ── 보간기/캐시 목록 ──────────────────────────────────────────────────
    final List<NoiseInterpolator> interpolators = new ArrayList<>();
    final List<CacheAllInCell> cellCaches = new ArrayList<>();
    private final Map<DensityFunction, DensityFunction> wrapped = new HashMap<>();

    // ── 현재 보간 상태 (FunctionContext 로 노출) ──────────────────────────
    boolean interpolating = false;
    boolean fillingCell = false;
    int cellStartBlockX;
    int cellStartBlockY;
    int cellStartBlockZ;
    int inCellX;
    int inCellY;
    int inCellZ;
    long interpolationCounter = 0L;
    long arrayInterpolationCounter = 0L;
    int arrayIndex = 0;

    // ── 보간 대상 함수 ────────────────────────────────────────────────────
    private final DensityFunction wrappedFinalDensity;
    private final NoiseRouter router;   // 예비 지표 높이 공유 캐시
    private final DensityFunction pslDensity; // PSL 스캔용 initial density — cache_2d 동적 캐시로 컬럼당 2D 1회

    // ── ore vein: solid 블록 override (overworld 전용, 항상 활성) ──
    private final OreVeinifier oreVeinifier;
    private final DensityFunction wrappedVeinToggle;

    // ── 기후 DF (mapAll 래핑) — FlatCache pre-fill 활성화, dim: 0=temp 1=hum 2=cont 3=ero 4=depth 5=ridges ──
    private final DensityFunction[] wrappedClimate;

    // ── 기후 DF 셀코너 캐시 [dim 0..5][qx 0..noiseSizeXZ][cy 0..cellCountY][qz 0..noiseSizeXZ] ──
    private final double[][][][] climateCache;

    // ── sliceFillingContextProvider: 셀 Y 슬라이스를 채울 때 사용 ─────────
    private final DensityFunction.ContextProvider sliceFillingContextProvider =
        new DensityFunction.ContextProvider() {
            @Override
            public DensityFunction.FunctionContext forIndex(int i) {
                NoiseChunk.this.cellStartBlockY =
                    (i + NoiseChunk.this.cellNoiseMinY) * NoiseChunk.this.cellHeight;
                NoiseChunk.this.interpolationCounter++;
                NoiseChunk.this.inCellY = 0;
                NoiseChunk.this.arrayIndex = i;
                return NoiseChunk.this;
            }

            @Override
            public void fillAllDirectly(double[] values, DensityFunction fn) {
                for (int i = 0; i <= NoiseChunk.this.cellCountY; i++) {
                    NoiseChunk.this.cellStartBlockY =
                        (i + NoiseChunk.this.cellNoiseMinY) * NoiseChunk.this.cellHeight;
                    NoiseChunk.this.interpolationCounter++;
                    NoiseChunk.this.inCellY = 0;
                    NoiseChunk.this.arrayIndex = i;
                    values[i] = fn.compute(NoiseChunk.this);
                }
            }
        };

    // ─────────────────────────────────────────────────────────────────────
    // 생성자
    // ─────────────────────────────────────────────────────────────────────

    /**
     * @param chunkX    청크 X 좌표 (블록 = chunkX*16)
     * @param chunkZ    청크 Z 좌표 (블록 = chunkZ*16)
     * @param router    최종 밀도 함수를 담은 NoiseRouter
     * @param settings  NoiseSettings (minY, height, cellWidth, cellHeight)
     */
    /** ore vein 비활성(reg 없음) — climate/biome 등 vein 무관 테스트 호환용. */
    public NoiseChunk(int chunkX, int chunkZ, NoiseRouter router, NoiseSettings settings) {
        this(chunkX, chunkZ, router, settings, null);
    }

    public NoiseChunk(int chunkX, int chunkZ, NoiseRouter router, NoiseSettings settings,
                      org.domi.furnace.server.world.BlockRegistry reg) {
        this.router = router;
        this.cellWidth  = settings.cellWidth();
        this.cellHeight = settings.cellHeight();
        this.cellCountXZ = 16 / this.cellWidth;   // 4
        this.cellCountY  = settings.height() / this.cellHeight; // 48
        this.cellNoiseMinY = Math.floorDiv(settings.minY(), this.cellHeight); // -8

        int firstNoiseBlockX = chunkX * 16;
        int firstNoiseBlockZ = chunkZ * 16;
        this.firstCellX = Math.floorDiv(firstNoiseBlockX, this.cellWidth);
        this.firstCellZ = Math.floorDiv(firstNoiseBlockZ, this.cellWidth);
        this.firstNoiseX = quartFromBlock(firstNoiseBlockX); // chunkX*4
        this.firstNoiseZ = quartFromBlock(firstNoiseBlockZ); // chunkZ*4
        this.noiseSizeXZ = quartFromBlock(this.cellCountXZ * this.cellWidth); // 4

        // wrap 이 호출되면 Marker 노드를 셀 인식 구현체로 교체한다.
        // finalDensity 먼저 wrap → wrapped HashMap 채움 → climate DF wrap 시 sub-DF 공유
        this.wrappedFinalDensity = router.finalDensity().mapAll(this::wrap);
        // PSL은 청크 밖 ±halo 컬럼도 샘플 → FlatCache(셀바운드) 대신 cache_2d만 동적 Cache2D로 교체.
        // top-down 스캔(x,z 고정) 시 비싼 2D 스플라인(continents/erosion/ridges)이 컬럼당 1회만 계산됨.
        this.pslDensity = router.initialDensityWithoutJaggedness().mapAll(this::wrapPsl);
        this.wrappedClimate = new DensityFunction[]{
            router.temperature().mapAll(this::wrap),
            router.humidity().mapAll(this::wrap),
            router.continents().mapAll(this::wrap),
            router.erosion().mapAll(this::wrap),
            router.depth().mapAll(this::wrap),
            router.ridges().mapAll(this::wrap)
        };
        // vein DF는 NoiseChunk context로 평가 → 반드시 wrap(interpolated 노드 등록·셀 보간).
        // overworld는 ore_veins_enabled=true. reg 없으면(테스트) 비활성.
        this.wrappedVeinToggle = reg == null || !settings.oreVeinsEnabled() ? null : router.veinToggle().mapAll(this::wrap);
        this.oreVeinifier = reg == null || !settings.oreVeinsEnabled() ? null : new OreVeinifier(
            this.wrappedVeinToggle,
            router.veinRidged().mapAll(this::wrap),
            router.veinGap().mapAll(this::wrap),
            router.oreRandom(),
            reg.stateIdOrAir("minecraft:copper_ore"),
            reg.stateIdOrAir("minecraft:raw_copper_block"),
            reg.stateIdOrAir("minecraft:granite"),
            reg.stateIdOrAir("minecraft:deepslate_iron_ore"),
            reg.stateIdOrAir("minecraft:raw_iron_block"),
            reg.stateIdOrAir("minecraft:tuff"));
        this.climateCache = buildClimateCache();
    }

    private double[][][][] buildClimateCache() {
        // W-09: 구조상 Y-불변인 차원(오버월드는 depth 외 5개)은 j=0 에서 한 번만 평가하고 위로 복사한다.
        boolean[] yInv = new boolean[6];
        for (int d = 0; d < 6; d++) yInv[d] = isYInvariant(wrappedClimate[d]);
        return buildClimateCache(yInv);
    }

    /** 테스트용: 게이트 없이 전 칸 평가한 기후 캐시(새 NoiseChunk 상태에 영향 없음 — DF 는 순수, 캐시는 메모이즈뿐). */
    double[][][][] climateCacheUngatedForTest() { return buildClimateCache(new boolean[6]); }

    double[][][][] climateCacheForTest() { return climateCache; }

    double[][][][] rebuildClimateCacheForTest() { return buildClimateCache(); }

    boolean climateYInvariantForTest(int d) { return isYInvariant(wrappedClimate[d]); }

    private double[][][][] buildClimateCache(boolean[] yInv) {
        int xn = noiseSizeXZ + 1;   // 5
        int yn = cellCountY + 1;    // 49
        int zn = noiseSizeXZ + 1;   // 5
        double[][][][] cache = new double[6][xn][yn][zn];
        for (int i = 0; i < xn; i++) {
            int bx = (firstNoiseX + i) * 4;
            for (int j = 0; j < yn; j++) {
                int by = (cellNoiseMinY + j) * cellHeight;
                for (int k = 0; k < zn; k++) {
                    int bz = (firstNoiseZ + k) * 4;
                    DensityFunction.SinglePointContext ctx =
                        new DensityFunction.SinglePointContext(bx, by, bz);
                    for (int d = 0; d < 6; d++) {
                        cache[d][i][j][k] = j > 0 && yInv[d] ? cache[d][i][0][k] : wrappedClimate[d].compute(ctx);
                    }
                }
            }
        }
        return cache;
    }

    /** 래핑된 DF 가 blockY 에 의존하지 않음이 구조상 확실한가. FlatCache 는 청크 쿼트 범위 안에서 XZ 표만 읽는다. */
    private boolean isYInvariant(DensityFunction df) {
        if (df instanceof FlatCache) return true;
        if (df instanceof Cache2D c) return isYInvariant(c.wrapped());
        if (df instanceof DensityFunctions.MarkerOrMarked) return false;   // 보간·셀 캐시는 청크 상태 의존
        return DensityFunctions.yInvariantNode(df, this::isYInvariant);
    }

    /**
     * 청크 로컬 quart 좌표에서 기후 6개를 quantized long 으로 직접 채운다 (allocation 없음).
     * out[d] = BiomeSampler.quantize((float) interpolated_climate[d])
     *
     * @param localQX 0..3
     * @param absQY   0..95
     * @param localQZ 0..3
     * @param out     길이 ≥ 6 의 pre-allocated long 배열
     */
    public void fillTarget(int localQX, int absQY, int localQZ, long[] out) {
        int cy = absQY >>> 1;
        // 노이즈 높이 위 쿼트(네더 y128+): 기후 DF 가 y 불변인 차원이라 최상단 셀 값으로 충분.
        if (cy >= cellCountY) { cy = cellCountY - 1; absQY = cy * 2 + 1; }
        double ty = (absQY & 1) * 0.5;
        for (int d = 0; d < 6; d++) {
            double v0 = climateCache[d][localQX][cy][localQZ];
            double v1 = climateCache[d][localQX][cy + 1][localQZ];
            double v = v0 + ty * (v1 - v0);
            out[d] = BiomeSampler.quantize((float) v);
        }
    }

    /**
     * 컬럼(localQX, localQZ)에서 depth(d=4) 제외 5개 기후 차원이 전부 Y-불변인지.
     * FlatCache 래핑된 DF는 항상 true — false 면 BiomeSampler 가 일반 경로로 폴백한다.
     */
    public boolean isClimateColumnYInvariant(int localQX, int localQZ) {
        for (int d = 0; d < 6; d++) {
            if (d == 4) continue;
            double v0 = climateCache[d][localQX][0][localQZ];
            for (int cy = 1; cy <= cellCountY; cy++) {
                if (climateCache[d][localQX][cy][localQZ] != v0) return false;
            }
        }
        return true;
    }

    /** @deprecated 테스트 전용 — 프로덕션은 fillTarget 사용 */
    @Deprecated
    public double[] climateAt(int localQX, int absQY, int localQZ) {
        int cy = absQY >>> 1;
        double ty = (absQY & 1) * 0.5;
        double[] result = new double[6];
        for (int d = 0; d < 6; d++) {
            double v0 = climateCache[d][localQX][cy][localQZ];
            double v1 = climateCache[d][localQX][cy + 1][localQZ];
            result[d] = v0 + ty * (v1 - v0);
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────
    // DensityFunction.FunctionContext 구현
    // ─────────────────────────────────────────────────────────────────────

    @Override public int blockX() { return cellStartBlockX + inCellX; }
    @Override public int blockY() { return cellStartBlockY + inCellY; }
    @Override public int blockZ() { return cellStartBlockZ + inCellZ; }

    // ─────────────────────────────────────────────────────────────────────
    // DensityFunction.ContextProvider 구현 (CacheAllInCell 채우기용)
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public DensityFunction.FunctionContext forIndex(int idx) {
        int i  = Math.floorMod(idx, cellWidth);
        int i1 = Math.floorDiv(idx, cellWidth);
        int i2 = Math.floorMod(i1, cellWidth);
        int i3 = cellHeight - 1 - Math.floorDiv(i1, cellWidth);
        this.inCellX = i2;
        this.inCellY = i3;
        this.inCellZ = i;
        this.arrayIndex = idx;
        return this;
    }

    @Override
    public void fillAllDirectly(double[] values, DensityFunction fn) {
        this.arrayIndex = 0;
        for (int i = cellHeight - 1; i >= 0; i--) {
            this.inCellY = i;
            for (int i1 = 0; i1 < cellWidth; i1++) {
                this.inCellX = i1;
                for (int i2 = 0; i2 < cellWidth; i2++) {
                    this.inCellZ = i2;
                    values[this.arrayIndex++] = fn.compute(this);
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // wrap: marker → 셀 인식 구현체로 교체
    // ─────────────────────────────────────────────────────────────────────

    /**
     * 이 NoiseChunk의 wrap 규칙(FlatCache = 쿼트 XZ 정렬값, 청크 밖은 직접 계산 등)을 그대로 적용한 DF — 바닐라는 aquifer에
     * NoiseChunk가 mapAll(wrap)한 라우터를 넘기므로 erosion/depth의 flat_cache가 청크 문맥에 따라 쿼트 정렬값을 돌려준다.
     */
    public DensityFunction wrappedForAquifer(DensityFunction df) {
        return df.mapAll(this::wrap);
    }

    private DensityFunction wrap(DensityFunction df) {
        return wrapped.computeIfAbsent(df, this::wrapNew);
    }

    /**
     * PSL 전용 래핑: cache_2d Marker만 동적 Cache2D(컬럼 단위, halo 포함 어디서나 동작)로 교체.
     * FlatCache는 셀바운드라 청크 밖 컬럼을 못 캐시 → bare passthrough 유지(정확). 나머지도 passthrough.
     * 결과는 raw 트리와 비트일치(메모이즈만) — top-down 스캔서 2D 스플라인을 컬럼당 1회로 줄인다.
     */
    private DensityFunction wrapPsl(DensityFunction df) {
        if (df instanceof DensityFunctions.Marker marker
                && marker.type() == DensityFunctions.Marker.Type.Cache2D) {
            return new Cache2D(marker.wrapped());
        }
        return df;
    }

    private DensityFunction wrapNew(DensityFunction df) {
        if (df instanceof DensityFunctions.Marker marker) {
            return switch (marker.type()) {
                case Interpolated    -> new NoiseInterpolator(marker.wrapped());
                case FlatCache       -> new FlatCache(marker.wrapped());
                case Cache2D         -> new Cache2D(marker.wrapped());
                case CacheOnce       -> new CacheOnce(marker.wrapped());
                case CacheAllInCell  -> new CacheAllInCell(marker.wrapped());
            };
        }
        return df;
    }

    // ─────────────────────────────────────────────────────────────────────
    // 슬라이스 채우기 (vanilla fillSlice)
    // ─────────────────────────────────────────────────────────────────────

    long sliceFillNanos = 0; // 벤치마크용 — fillSlice(코너 DF 평가) 누적 시간
    private final double[] cellBoundsScratch = new double[2];
    private final double[] veinBoundsScratch = new double[2];

    private void fillSlice(boolean isSlice0, int cellX) {
        long t0 = System.nanoTime();
        this.cellStartBlockX = cellX * this.cellWidth;
        this.inCellX = 0;

        for (int iz = 0; iz < this.cellCountXZ + 1; iz++) {
            int cz = this.firstCellZ + iz;
            this.cellStartBlockZ = cz * this.cellWidth;
            this.inCellZ = 0;
            this.arrayInterpolationCounter++;

            for (NoiseInterpolator interp : this.interpolators) {
                double[] slice = (isSlice0 ? interp.slice0 : interp.slice1)[iz];
                interp.fillArray(slice, this.sliceFillingContextProvider);
            }
        }

        this.arrayInterpolationCounter++;
        this.sliceFillNanos += System.nanoTime() - t0;
    }

    // ─────────────────────────────────────────────────────────────────────
    // 보간 루프 제어 (vanilla NoiseChunk 공개 메서드)
    // ─────────────────────────────────────────────────────────────────────

    private void initializeForFirstCellX() {
        this.interpolating = true;
        this.interpolationCounter = 0L;
        fillSlice(true, this.firstCellX);
    }

    private void advanceCellX(int increment) {
        fillSlice(false, this.firstCellX + increment + 1);
        this.cellStartBlockX = (this.firstCellX + increment) * this.cellWidth;
    }

    private void selectCellYZ(int iy, int iz) {
        for (NoiseInterpolator interp : this.interpolators) {
            interp.selectCellYZ(iy, iz);
        }
        this.cellStartBlockY = (iy + this.cellNoiseMinY) * this.cellHeight;
        this.cellStartBlockZ = (this.firstCellZ + iz) * this.cellWidth;
    }

    /** 셀 skip 판정 후에만 호출 — CacheAllInCell 채우기 (vanilla selectCellYZ 후반부) */
    private void fillCellCaches() {
        this.fillingCell = true;
        this.arrayInterpolationCounter++;

        for (CacheAllInCell cache : this.cellCaches) {
            cache.noiseFiller.fillArray(cache.values, this);
        }

        this.arrayInterpolationCounter++;
        this.fillingCell = false;
    }

    // ── 셀 skip: final_density 의 셀 내 보수적 구간으로 부호가 자명한 셀의 블록 루프 생략 ──

    /** 부호 판정 마진. 보간 lerp 의 부동소수점 오차(~수 ulp)에 대한 보험. */
    private static final double SKIP_EPS = 1e-9;

    int skippedCells = 0; // 벤치마크용

    private boolean cellBoundsOracle(DensityFunction df, double[] out) {
        if (df instanceof NoiseInterpolator ni) {
            ni.cornerBounds(out);
            return true;
        }
        if (df instanceof FlatCache fc) {
            double v = fc.compute(new DensityFunction.SinglePointContext(
                cellStartBlockX, cellStartBlockY, cellStartBlockZ));
            out[0] = v;
            out[1] = v;
            return true;
        }
        if (df instanceof Cache2D c) {
            cellBoundsOf(c.wrapped(), out);
            return true;
        }
        if (df instanceof CacheOnce c) {
            cellBoundsOf(c.wrapped(), out);
            return true;
        }
        if (df instanceof CacheAllInCell c) {
            cellBoundsOf(c.wrapped(), out);
            return true;
        }
        return false; // DensityFunctions.cellBounds 가 처리
    }

    private void cellBoundsOf(DensityFunction df, double[] out) {
        DensityFunctions.cellBounds(df, this::cellBoundsOracle,
            cellStartBlockY, cellStartBlockY + cellHeight - 1, out);
    }

    private void updateForY(int blockY, double t) {
        this.inCellY = blockY - this.cellStartBlockY;
        for (NoiseInterpolator interp : this.interpolators) {
            interp.updateForY(t);
        }
    }

    private void updateForX(int blockX, double t) {
        this.inCellX = blockX - this.cellStartBlockX;
        for (NoiseInterpolator interp : this.interpolators) {
            interp.updateForX(t);
        }
    }

    private void updateForZ(int blockZ, double t) {
        this.inCellZ = blockZ - this.cellStartBlockZ;
        this.interpolationCounter++;
        for (NoiseInterpolator interp : this.interpolators) {
            interp.updateForZ(t);
        }
    }

    private void stopInterpolation() {
        this.interpolating = false;
    }

    private void swapSlices() {
        for (NoiseInterpolator interp : this.interpolators) {
            interp.swapSlices();
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // stoneMask(): 전체 청크에 대해 interpolated final_density > 0 반환
    // ─────────────────────────────────────────────────────────────────────

    /**
     * 청크 전체 블록(16×384×16)에 대해 보간된 final_density > 0 이면 true(stone).
     * 인덱스 = (y - minY) * 256 + lz * 16 + lx (FixtureFormat.blockIndex 와 동일).
     */
    public boolean[] stoneMask() {
        int minY   = this.cellNoiseMinY * this.cellHeight; // -64
        int height = this.cellCountY   * this.cellHeight; // 384
        boolean[] mask = new boolean[height * 16 * 16];

        initializeForFirstCellX();

        // 바닐라 NoiseBasedChunkGenerator.doFill 과 동일한 순서:
        //   outer: cellX (0..cellCountXZ-1)
        //     advanceCellX(cx)         ← slice1 에 cx+1 열을 채운다
        //     inner: cellZ (0..cellCountXZ-1)
        //       cellY (top-down: cellCountY-1..0)
        //         selectCellYZ(cy, cz)
        //         in-cell Y (top-down: cellHeight-1..0)
        //           updateForY
        //           in-cell X (0..cellWidth-1)
        //             updateForX
        //             in-cell Z (0..cellWidth-1)
        //               updateForZ → compute
        //     swapSlices()             ← outer 루프 끝에서 교환

        int chunkBlockX = this.firstCellX * this.cellWidth;
        int chunkBlockZ = this.firstCellZ * this.cellWidth;

        for (int cx = 0; cx < this.cellCountXZ; cx++) {
            advanceCellX(cx);

            for (int cz = 0; cz < this.cellCountXZ; cz++) {
                for (int cy = this.cellCountY - 1; cy >= 0; cy--) {
                    selectCellYZ(cy, cz);

                    // 셀 skip: 보수적 구간으로 부호 자명하면 블록 루프 생략 (결과 비트일치)
                    cellBoundsOf(this.wrappedFinalDensity, cellBoundsScratch);
                    if (cellBoundsScratch[1] < -SKIP_EPS) {
                        skippedCells++;
                        continue; // 전부 비돌 — mask 기본값 false 유지
                    }
                    if (cellBoundsScratch[0] > SKIP_EPS) {
                        skippedCells++;
                        int yBase = cy * this.cellHeight;
                        int xBase = cx * this.cellWidth;
                        int zBase = cz * this.cellWidth;
                        for (int iy = 0; iy < this.cellHeight; iy++) {
                            int rowBase = (yBase + iy) * 256;
                            for (int iz = 0; iz < this.cellWidth; iz++) {
                                int rb = rowBase + (zBase + iz) * 16 + xBase;
                                for (int ix = 0; ix < this.cellWidth; ix++) {
                                    mask[rb + ix] = true; // 전부 돌
                                }
                            }
                        }
                        continue;
                    }

                    fillCellCaches();

                    // Y 방향 in-cell 루프: top-down (cellHeight-1 .. 0)
                    for (int iy = this.cellHeight - 1; iy >= 0; iy--) {
                        int blockY = (this.cellNoiseMinY + cy) * this.cellHeight + iy;
                        double ty = (double) iy / this.cellHeight;
                        updateForY(blockY, ty);

                        // X 방향 in-cell 루프
                        for (int ix = 0; ix < this.cellWidth; ix++) {
                            int blockX = chunkBlockX + cx * this.cellWidth + ix;
                            double tx = (double) ix / this.cellWidth;
                            updateForX(blockX, tx);

                            // Z 방향 in-cell 루프
                            for (int iz = 0; iz < this.cellWidth; iz++) {
                                int blockZ = chunkBlockZ + cz * this.cellWidth + iz;
                                double tz = (double) iz / this.cellWidth;
                                updateForZ(blockZ, tz);

                                double density = this.wrappedFinalDensity.compute(this);
                                int lx = blockX & 15;  // local x
                                int lz = blockZ & 15;  // local z
                                int yOff = blockY - minY; // y + 64
                                int idx = yOff * 256 + lz * 16 + lx;
                                if (idx >= 0 && idx < mask.length) {
                                    mask[idx] = density > 0.0;
                                }
                            }
                        }
                    }
                }
            }

            swapSlices(); // outer 루프 끝에서 교환
        }

        stopInterpolation();
        return mask;
    }

    // 디버그: -Dfurnace.aqwatch=x,y,z;x,y,z — 해당 칸의 density·aquifer 결과를 stdout에 남긴다.
    private static final int[][] AQ_WATCH = parseAqWatch(System.getProperty("furnace.aqwatch"));

    private static int[][] parseAqWatch(String v) {
        if (v == null) return null;
        String[] parts = v.split(";");
        int[][] out = new int[parts.length][];
        for (int i = 0; i < parts.length; i++) {
            String[] p = parts[i].split(",");
            out[i] = new int[]{Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim())};
        }
        return out;
    }

    /**
     * 전체 청크(16×height×16)에 대해 블록 state ID를 반환한다.
     * density > 0 → stoneId, density ≤ 0 → aquifer.computeSubstance(this, density).
     * 인덱스 = (y - minY) * 256 + lz * 16 + lx (stoneMask 와 동일).
     * all-fluid 셀은 aquifer에 정확한 density를 전달하기 위해 skip 하지 않는다.
     */
    /** 바닐라 NoiseChunk 의 beardifier — final density 에 블록 단위로 더해진다(cacheAllInCell(add(final, beardifier))). null = 없음. */
    private org.domi.furnace.server.world.gen.structure.Beardifier beardifier;

    public void setBeardifier(org.domi.furnace.server.world.gen.structure.Beardifier b) {
        this.beardifier = b == null || b.isEmpty() ? null : b;
    }

    public int[] generateBlockIds(int stoneId, Aquifer aquifer) {
        int minY   = this.cellNoiseMinY * this.cellHeight;
        int height = this.cellCountY   * this.cellHeight;
        int[] blocks = new int[height * 16 * 16];

        initializeForFirstCellX();

        int chunkBlockX = this.firstCellX * this.cellWidth;
        int chunkBlockZ = this.firstCellZ * this.cellWidth;

        for (int cx = 0; cx < this.cellCountXZ; cx++) {
            advanceCellX(cx);

            for (int cz = 0; cz < this.cellCountXZ; cz++) {
                for (int cy = this.cellCountY - 1; cy >= 0; cy--) {
                    selectCellYZ(cy, cz);

                    cellBoundsOf(this.wrappedFinalDensity, cellBoundsScratch);
                    boolean beardHere = beardifier != null && beardifier.mayAffect(
                        chunkBlockX + cx * this.cellWidth, (this.cellNoiseMinY + cy) * this.cellHeight, chunkBlockZ + cz * this.cellWidth,
                        chunkBlockX + cx * this.cellWidth + this.cellWidth - 1, (this.cellNoiseMinY + cy) * this.cellHeight + this.cellHeight - 1,
                        chunkBlockZ + cz * this.cellWidth + this.cellWidth - 1);
                    if (!beardHere && cellBoundsScratch[0] > SKIP_EPS
                        && (!cellOverlapsVeinRange(cy) || canSkipVeinForCell(cy))) {
                        // all-stone skip (aquifer not called). vein y범위 셀이어도 veinToggle bound로
                        // 셀 전체 NO_VEIN이 보장되면(canSkipVeinForCell) per-block oreVeinifier 없이 skip.
                        int yBase = cy * this.cellHeight;
                        int zBase = cz * this.cellWidth;
                        int xBase = cx * this.cellWidth;
                        for (int iy = 0; iy < this.cellHeight; iy++) {
                            int rowBase = (yBase + iy) * 256;
                            for (int iz = 0; iz < this.cellWidth; iz++) {
                                int rb = rowBase + (zBase + iz) * 16 + xBase;
                                for (int ix = 0; ix < this.cellWidth; ix++) {
                                    blocks[rb + ix] = stoneId;
                                }
                            }
                        }
                        continue;
                    }
                    // NOTE: all-fluid skip removed — aquifer needs exact per-block density

                    fillCellCaches();

                    for (int iy = this.cellHeight - 1; iy >= 0; iy--) {
                        int blockY = (this.cellNoiseMinY + cy) * this.cellHeight + iy;
                        double ty = (double) iy / this.cellHeight;
                        updateForY(blockY, ty);

                        for (int ix = 0; ix < this.cellWidth; ix++) {
                            int blockX = chunkBlockX + cx * this.cellWidth + ix;
                            double tx = (double) ix / this.cellWidth;
                            updateForX(blockX, tx);

                            for (int iz = 0; iz < this.cellWidth; iz++) {
                                int blockZ = chunkBlockZ + cz * this.cellWidth + iz;
                                double tz = (double) iz / this.cellWidth;
                                updateForZ(blockZ, tz);

                                double density = this.wrappedFinalDensity.compute(this);
                                if (beardHere) density += beardifier.compute(blockX, blockY, blockZ);
                                int lx = blockX & 15;
                                int lz = blockZ & 15;
                                int yOff = blockY - minY;
                                int idx = yOff * 256 + lz * 16 + lx;
                                if (idx >= 0 && idx < blocks.length) {
                                    if (density > 0.0) {
                                        // 바닐라 material rule: solid → aquifer null → oreVeinifier 실행.
                                        // vein!=NO_VEIN이면 vein id, 아니면 stoneId.
                                        int vein = this.oreVeinifier == null
                                            ? OreVeinifier.NO_VEIN : this.oreVeinifier.veinStateId(this);
                                        blocks[idx] = vein != OreVeinifier.NO_VEIN ? vein : stoneId;
                                    } else {
                                        int substance = aquifer.computeSubstance(this, density);
                                        if (AQ_WATCH != null) {
                                            for (int[] w : AQ_WATCH) {
                                                if (w[0] == blockX && w[1] == blockY && w[2] == blockZ) {
                                                    System.out.println("FAQ " + blockX + " " + blockY + " " + blockZ + " density=" + density + " -> " + substance);
                                                }
                                            }
                                        }
                                        if (substance == Aquifer.NO_CARVE) {
                                            // 바닐라 material rule 체인 [aquifer, oreVeinifier]: aquifer가 null(장벽으로 유체가 막힘)을 돌려주면
                                            // 다음 규칙인 oreVeinifier가 실행되고, 그래도 null이면 기본 블록(stone)이다.
                                            int vein = this.oreVeinifier == null
                                                ? OreVeinifier.NO_VEIN : this.oreVeinifier.veinStateId(this);
                                            blocks[idx] = vein != OreVeinifier.NO_VEIN ? vein : stoneId;
                                        } else {
                                            blocks[idx] = substance;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            swapSlices();
        }

        stopInterpolation();
        return blocks;
    }

    /** 셀 cy의 블록 y범위가 vein y범위 [-60,50]와 겹치면 true(all-stone skip 금지→per-block oreVeinifier). vein 비활성 시 false. */
    private boolean cellOverlapsVeinRange(int cy) {
        if (this.oreVeinifier == null) return false;
        int cellMinY = (this.cellNoiseMinY + cy) * this.cellHeight;
        int cellMaxY = cellMinY + this.cellHeight - 1;
        return cellMaxY >= OreVeinifier.VEIN_MIN_Y && cellMinY <= OreVeinifier.VEIN_MAX_Y;
    }

    /**
     * all-stone(final_density 셀 전체 양수)이면서 vein y범위와 겹치는 셀에서, veinToggle의 셀 bound로
     * 셀 전체 NO_VEIN이 보장되면 true(per-block oreVeinifier 없이 skip 가능). 판정 로직/증명은
     * OreVeinifier.canSkipCell 참조. package-private(테스트용 — fuzz 테스트가 예측치를 직접 검증).
     */
    boolean canSkipVeinForCell(int cy) {
        if (this.oreVeinifier == null) return false;

        cellBoundsOf(this.wrappedVeinToggle, veinBoundsScratch);

        int cellMinY = (this.cellNoiseMinY + cy) * this.cellHeight;
        int cellMaxY = cellMinY + this.cellHeight - 1;

        return OreVeinifier.canSkipCell(veinBoundsScratch[0], veinBoundsScratch[1], cellMinY, cellMaxY);
    }

    /**
     * quart 좌표(quartX, quartZ)에서 initialDensityWithoutJaggedness > 0.390625 인
     * 최상단 셀 Y 블록 좌표를 반환한다. 없으면 minY - 1.
     *
     * 바닐라 SurfaceSystem 이 above_preliminary_surface 조건에 사용.
     * quartX = blockX >> 2, quartZ = blockZ >> 2.
     *
     * 0.390625 threshold = 25/64 (vanilla SurfaceSystem.hasPreliminarySurface)
     */
    public int getPreliminarySurfaceLevel(int quartX, int quartZ) {
        int cached = router.cachedPreliminarySurface(quartX, quartZ);
        if (cached != Integer.MIN_VALUE) return cached;
        int level = computePreliminarySurfaceLevel(quartX, quartZ);
        router.cachePreliminarySurface(quartX, quartZ, level);
        return level;
    }

    private int computePreliminarySurfaceLevel(int quartX, int quartZ) {
        int bx = quartToBlock(quartX);
        int bz = quartToBlock(quartZ);
        int minY = cellNoiseMinY * cellHeight;
        for (int y = minY + cellCountY * cellHeight; y >= minY; y -= cellHeight) {
            double d = pslDensity.compute(new DensityFunction.SinglePointContext(bx, y, bz));
            if (d > 0.390625) {
                return y;
            }
        }
        return Integer.MAX_VALUE;
    }

    // ─────────────────────────────────────────────────────────────────────
    // 내부 유틸
    // ─────────────────────────────────────────────────────────────────────

    /** QuartPos.fromBlock(n) = Math.floorDiv(n, 4) */
    private static int quartFromBlock(int block) {
        return Math.floorDiv(block, 4);
    }

    /** QuartPos.toBlock(quart) = quart * 4 */
    private static int quartToBlock(int quart) {
        return quart * 4;
    }

    // ─────────────────────────────────────────────────────────────────────
    // NoiseInterpolator: 코너 샘플 + 삼선형 보간
    // ─────────────────────────────────────────────────────────────────────

    final class NoiseInterpolator implements DensityFunctions.MarkerOrMarked {
        // slice[z][y] — 크기 (cellCountXZ+1) × (cellCountY+1)
        double[][] slice0;
        double[][] slice1;

        private final DensityFunction noiseFiller;

        // 현재 선택된 셀의 8 코너 값
        private double noise000, noise001, noise100, noise101;
        private double noise010, noise011, noise110, noise111;

        // 중간 보간 값
        private double valueXZ00, valueXZ10, valueXZ01, valueXZ11;
        private double valueZ0, valueZ1;
        private double value;

        NoiseInterpolator(DensityFunction noiseFiller) {
            this.noiseFiller = noiseFiller;
            this.slice0 = allocateSlice(cellCountY, cellCountXZ);
            this.slice1 = allocateSlice(cellCountY, cellCountXZ);
            interpolators.add(this);
        }

        private double[][] allocateSlice(int cY, int cXZ) {
            int rows = cXZ + 1;
            int cols = cY + 1;
            double[][] s = new double[rows][cols];
            for (int i = 0; i < rows; i++) s[i] = new double[cols];
            return s;
        }

        void selectCellYZ(int iy, int iz) {
            this.noise000 = slice0[iz    ][iy    ];
            this.noise001 = slice0[iz + 1][iy    ];
            this.noise100 = slice1[iz    ][iy    ];
            this.noise101 = slice1[iz + 1][iy    ];
            this.noise010 = slice0[iz    ][iy + 1];
            this.noise011 = slice0[iz + 1][iy + 1];
            this.noise110 = slice1[iz    ][iy + 1];
            this.noise111 = slice1[iz + 1][iy + 1];
        }

        /** 현재 셀 8 코너의 [min, max]. 삼선형 보간값은 항상 이 구간 안(볼록결합). */
        void cornerBounds(double[] out) {
            out[0] = Math.min(Math.min(Math.min(noise000, noise001), Math.min(noise100, noise101)),
                              Math.min(Math.min(noise010, noise011), Math.min(noise110, noise111)));
            out[1] = Math.max(Math.max(Math.max(noise000, noise001), Math.max(noise100, noise101)),
                              Math.max(Math.max(noise010, noise011), Math.max(noise110, noise111)));
        }

        void updateForY(double t) {
            this.valueXZ00 = MathHelper.lerp(t, noise000, noise010);
            this.valueXZ10 = MathHelper.lerp(t, noise100, noise110);
            this.valueXZ01 = MathHelper.lerp(t, noise001, noise011);
            this.valueXZ11 = MathHelper.lerp(t, noise101, noise111);
        }

        void updateForX(double t) {
            this.valueZ0 = MathHelper.lerp(t, valueXZ00, valueXZ10);
            this.valueZ1 = MathHelper.lerp(t, valueXZ01, valueXZ11);
        }

        void updateForZ(double t) {
            this.value = MathHelper.lerp(t, valueZ0, valueZ1);
        }

        @Override
        public double compute(DensityFunction.FunctionContext ctx) {
            if (ctx != NoiseChunk.this) {
                return noiseFiller.compute(ctx);
            }
            if (!interpolating) {
                throw new IllegalStateException("Sampling interpolator outside interpolation loop");
            }
            if (fillingCell) {
                // CacheAllInCell 채우기 중: lerp3 로 직접 계산
                return MathHelper.lerp3(
                    (double) inCellX / cellWidth,
                    (double) inCellY / cellHeight,
                    (double) inCellZ / cellWidth,
                    noise000, noise100, noise010, noise110,
                    noise001, noise101, noise011, noise111
                );
            }
            return this.value;
        }

        @Override
        public void fillArray(double[] array, DensityFunction.ContextProvider provider) {
            if (fillingCell) {
                provider.fillAllDirectly(array, this);
            } else {
                noiseFiller.fillArray(array, provider);
            }
        }

        void swapSlices() {
            double[][] tmp = slice0;
            slice0 = slice1;
            slice1 = tmp;
        }

        @Override
        public DensityFunction wrapped() { return noiseFiller; }

        @Override
        public DensityFunctions.Marker.Type type() {
            return DensityFunctions.Marker.Type.Interpolated;
        }

        @Override public double minValue() { return noiseFiller.minValue(); }
        @Override public double maxValue() { return noiseFiller.maxValue(); }
    }

    // ─────────────────────────────────────────────────────────────────────
    // FlatCache: 쿼트 XZ 좌표 기준 캐시 (Y 독립)
    // ─────────────────────────────────────────────────────────────────────

    final class FlatCache implements DensityFunctions.MarkerOrMarked {
        private final DensityFunction noiseFiller;
        final double[][] values; // [noiseSizeXZ+1][noiseSizeXZ+1]

        FlatCache(DensityFunction noiseFiller) {
            this.noiseFiller = noiseFiller;
            this.values = new double[noiseSizeXZ + 1][noiseSizeXZ + 1];
            // 셀 코너 쿼트 좌표에서 미리 채운다
            for (int i = 0; i <= noiseSizeXZ; i++) {
                int qx = firstNoiseX + i;
                int bx = quartToBlock(qx);
                for (int j = 0; j <= noiseSizeXZ; j++) {
                    int qz = firstNoiseZ + j;
                    int bz = quartToBlock(qz);
                    this.values[i][j] = noiseFiller.compute(
                        new DensityFunction.SinglePointContext(bx, 0, bz));
                }
            }
        }

        @Override
        public double compute(DensityFunction.FunctionContext ctx) {
            int qx = quartFromBlock(ctx.blockX()) - firstNoiseX;
            int qz = quartFromBlock(ctx.blockZ()) - firstNoiseZ;
            if (qx >= 0 && qz >= 0 && qx < values.length && qz < values.length) {
                return values[qx][qz];
            }
            return noiseFiller.compute(ctx);
        }

        @Override
        public void fillArray(double[] array, DensityFunction.ContextProvider provider) {
            provider.fillAllDirectly(array, this);
        }

        @Override public DensityFunction wrapped() { return noiseFiller; }
        @Override public DensityFunctions.Marker.Type type() { return DensityFunctions.Marker.Type.FlatCache; }
        @Override public double minValue() { return noiseFiller.minValue(); }
        @Override public double maxValue() { return noiseFiller.maxValue(); }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Cache2D: blockX + blockZ 기준 캐시
    // ─────────────────────────────────────────────────────────────────────

    static final class Cache2D implements DensityFunctions.MarkerOrMarked {
        private final DensityFunction function;
        // Long 키: ((long)blockX << 32) | (blockZ & 0xFFFFFFFFL) — vanilla ChunkPos.asLong 과 동일
        private long lastPos = Long.MIN_VALUE;
        private double lastValue;

        Cache2D(DensityFunction function) {
            this.function = function;
        }

        @Override
        public double compute(DensityFunction.FunctionContext ctx) {
            int bx = ctx.blockX(), bz = ctx.blockZ();
            long key = (long) bx << 32 | (bz & 0xFFFFFFFFL);
            if (key == lastPos) return lastValue;
            lastPos = key;
            lastValue = function.compute(ctx);
            return lastValue;
        }

        @Override
        public void fillArray(double[] array, DensityFunction.ContextProvider provider) {
            function.fillArray(array, provider);
        }

        @Override public DensityFunction wrapped() { return function; }
        @Override public DensityFunctions.Marker.Type type() { return DensityFunctions.Marker.Type.Cache2D; }
        @Override public double minValue() { return function.minValue(); }
        @Override public double maxValue() { return function.maxValue(); }
    }

    // ─────────────────────────────────────────────────────────────────────
    // CacheOnce: interpolationCounter / arrayInterpolationCounter 기준 캐시
    // ─────────────────────────────────────────────────────────────────────

    final class CacheOnce implements DensityFunctions.MarkerOrMarked {
        private final DensityFunction function;
        private long lastCounter = Long.MIN_VALUE;
        private long lastArrayCounter = Long.MIN_VALUE;
        private double lastValue;
        private double[] lastArray;

        CacheOnce(DensityFunction function) {
            this.function = function;
        }

        @Override
        public double compute(DensityFunction.FunctionContext ctx) {
            if (ctx != NoiseChunk.this) {
                return function.compute(ctx);
            }
            if (lastArray != null && lastArrayCounter == arrayInterpolationCounter) {
                return lastArray[arrayIndex];
            }
            if (lastCounter == interpolationCounter) {
                return lastValue;
            }
            lastCounter = interpolationCounter;
            lastValue = function.compute(ctx);
            return lastValue;
        }

        @Override
        public void fillArray(double[] array, DensityFunction.ContextProvider provider) {
            if (lastArray != null && lastArrayCounter == arrayInterpolationCounter) {
                System.arraycopy(lastArray, 0, array, 0, array.length);
            } else {
                wrapped().fillArray(array, provider);
                if (lastArray != null && lastArray.length == array.length) {
                    System.arraycopy(array, 0, lastArray, 0, array.length);
                } else {
                    lastArray = array.clone();
                }
                lastArrayCounter = arrayInterpolationCounter;
            }
        }

        @Override public DensityFunction wrapped() { return function; }
        @Override public DensityFunctions.Marker.Type type() { return DensityFunctions.Marker.Type.CacheOnce; }
        @Override public double minValue() { return function.minValue(); }
        @Override public double maxValue() { return function.maxValue(); }
    }

    // ─────────────────────────────────────────────────────────────────────
    // CacheAllInCell: 셀 내부 전체 블록을 미리 채워두는 배열 캐시
    // ─────────────────────────────────────────────────────────────────────

    final class CacheAllInCell implements DensityFunctions.MarkerOrMarked {
        final DensityFunction noiseFiller;
        final double[] values; // cellWidth * cellWidth * cellHeight

        CacheAllInCell(DensityFunction noiseFiller) {
            this.noiseFiller = noiseFiller;
            this.values = new double[cellWidth * cellWidth * cellHeight];
            cellCaches.add(this);
        }

        @Override
        public double compute(DensityFunction.FunctionContext ctx) {
            if (ctx != NoiseChunk.this) {
                return noiseFiller.compute(ctx);
            }
            if (!interpolating) {
                throw new IllegalStateException("Sampling outside interpolation loop");
            }
            int ix = inCellX, iy = inCellY, iz = inCellZ;
            if (ix >= 0 && iy >= 0 && iz >= 0
                    && ix < cellWidth && iy < cellHeight && iz < cellWidth) {
                return values[((cellHeight - 1 - iy) * cellWidth + ix) * cellWidth + iz];
            }
            return noiseFiller.compute(ctx);
        }

        @Override
        public void fillArray(double[] array, DensityFunction.ContextProvider provider) {
            provider.fillAllDirectly(array, this);
        }

        @Override public DensityFunction wrapped() { return noiseFiller; }
        @Override public DensityFunctions.Marker.Type type() { return DensityFunctions.Marker.Type.CacheAllInCell; }
        @Override public double minValue() { return noiseFiller.minValue(); }
        @Override public double maxValue() { return noiseFiller.maxValue(); }
    }
}
