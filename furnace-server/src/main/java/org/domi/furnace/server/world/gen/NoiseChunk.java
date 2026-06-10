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
    public NoiseChunk(int chunkX, int chunkZ, NoiseRouter router, NoiseSettings settings) {
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
        this.wrappedClimate = new DensityFunction[]{
            router.temperature().mapAll(this::wrap),
            router.humidity().mapAll(this::wrap),
            router.continents().mapAll(this::wrap),
            router.erosion().mapAll(this::wrap),
            router.depth().mapAll(this::wrap),
            router.ridges().mapAll(this::wrap)
        };
        this.climateCache = buildClimateCache();
    }

    private double[][][][] buildClimateCache() {
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
                        cache[d][i][j][k] = wrappedClimate[d].compute(ctx);
                    }
                }
            }
        }
        return cache;
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

    private DensityFunction wrap(DensityFunction df) {
        return wrapped.computeIfAbsent(df, this::wrapNew);
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

    private double[] cellBoundsOracle(DensityFunction df) {
        if (df instanceof NoiseInterpolator ni) return ni.cornerBounds();
        if (df instanceof FlatCache fc) {
            double v = fc.compute(new DensityFunction.SinglePointContext(
                cellStartBlockX, cellStartBlockY, cellStartBlockZ));
            return new double[]{v, v};
        }
        if (df instanceof Cache2D c)        return cellBoundsOf(c.wrapped());
        if (df instanceof CacheOnce c)      return cellBoundsOf(c.wrapped());
        if (df instanceof CacheAllInCell c) return cellBoundsOf(c.wrapped());
        return null; // DensityFunctions.cellBounds 가 처리
    }

    private double[] cellBoundsOf(DensityFunction df) {
        return DensityFunctions.cellBounds(df, this::cellBoundsOracle,
            cellStartBlockY, cellStartBlockY + cellHeight - 1);
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
                    double[] bounds = cellBoundsOf(this.wrappedFinalDensity);
                    if (bounds[1] < -SKIP_EPS) {
                        skippedCells++;
                        continue; // 전부 비돌 — mask 기본값 false 유지
                    }
                    if (bounds[0] > SKIP_EPS) {
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
        double[] cornerBounds() {
            double lo = Math.min(Math.min(Math.min(noise000, noise001), Math.min(noise100, noise101)),
                                 Math.min(Math.min(noise010, noise011), Math.min(noise110, noise111)));
            double hi = Math.max(Math.max(Math.max(noise000, noise001), Math.max(noise100, noise101)),
                                 Math.max(Math.max(noise010, noise011), Math.max(noise110, noise111)));
            return new double[]{lo, hi};
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
