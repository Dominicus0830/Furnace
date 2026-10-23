## 작업 요약
M4g-b 성능 핫픽스: NoiseChunk 기후 DF 셀코너 캐시(Fix A) + fitness lower-bound pruning(Fix B). 스폰 청크 공허·로드 느림 해결.

## 문제 / 목적
BiomeSampler.sampleChunk() = 1536 quart × (6 DF full-eval + 7593 entry linear search) per chunk.
- 6 DF eval: 각 SinglePointContext 새 객체 → DF 트리 내 FlatCache/Cache2D 공유 없음. 1536×6 = 9216 full traversal.
- 7593 entry linear: 매 quart마다 전체 순회. 가지치기 없음.
- cold JVM에서 2s 초과 → snapshotChunk().get(2s) timeout → 스폰 5청크 공허.

## 해결 방법 / 구현 내용

### Fix A — NoiseChunk 기후 DF 셀코너 캐시
NoiseChunk 생성 시 6개 기후 DF(temp/hum/cont/ero/depth/ridges) 셀코너 pre-compute.
- 캐시 크기: `double[6][noiseSizeXZ+1][cellCountY+1][noiseSizeXZ+1]` = `[6][5][49][5]` = 7350 double
- 평가 수: 5×49×5×6 = 7350 (기존 9216보다 적지만 핵심은 DF 트리 내 캐시 공유)
- cellWidth=4 = 1 quart → XZ 보간 불필요. Y: cellHeight=8 = 2 quart → 선형 보간(ty=0 or 0.5)
- depth DF Y-linear → 선형 보간 = bit-exact. 나머지 5개 DF Y-independent.
- BiomeSampler.sampleChunk(cx, cz, NoiseChunk) 오버로드 추가.

### Fix B — fitness lower-bound pruning
fitnessWithCutoff(): offset² 또는 각 dimension 누적합이 bestFitness 이상이면 즉시 return.
탐색 entry 수 급감 (7593 → 수십~수백 예상).

### 검증
BiomeSamplerParityTest 3 fixture 모두 0 mismatch 유지.
- seed=0 cx=0 cz=0: 0 mismatches
- seed=0 cx=5 cz=5: 0 mismatches
- seed=123456789 cx=0 cz=0: 0 mismatches

## 핵심 코드

### NoiseChunk — 기후 캐시 pre-compute + climateAt()
```java
// 필드
private final double[][][][] climateCache; // [6][5][49][5]

// 생성자에서
this.climateCache = buildClimateCache(router);

private double[][][][] buildClimateCache(NoiseRouter router) {
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
                cache[0][i][j][k] = router.temperature().compute(ctx);
                cache[1][i][j][k] = router.humidity().compute(ctx);
                cache[2][i][j][k] = router.continents().compute(ctx);
                cache[3][i][j][k] = router.erosion().compute(ctx);
                cache[4][i][j][k] = router.depth().compute(ctx);
                cache[5][i][j][k] = router.ridges().compute(ctx);
            }
        }
    }
    return cache;
}

// absQY=0..95, localQX/Z=0..3
public double[] climateAt(int localQX, int absQY, int localQZ) {
    int cy = absQY >>> 1;           // 0..47 (cell Y index)
    double ty = (absQY & 1) * 0.5; // 0.0 or 0.5
    double[] result = new double[6];
    for (int d = 0; d < 6; d++) {
        double v0 = climateCache[d][localQX][cy][localQZ];
        double v1 = climateCache[d][localQX][cy + 1][localQZ];
        result[d] = v0 + ty * (v1 - v0);
    }
    return result;
}
```

### BiomeSampler — pruning + noiseChunk 오버로드
```java
static long fitnessWithCutoff(long[] pMin, long[] pMax, long offset, long[] target, long cutoff) {
    long f = offset * offset;
    if (f >= cutoff) return f;
    for (int i = 0; i < 6; i++) {
        long d = paramDistance(pMin[i], pMax[i], target[i]);
        f += d * d;
        if (f >= cutoff) return f;
    }
    return f;
}

public int[] sampleChunk(int cx, int cz, NoiseChunk noiseChunk) {
    int[] result = new int[1536];
    for (int qy = 0; qy < 96; qy++) {
        for (int qz = 0; qz < 4; qz++) {
            for (int qx = 0; qx < 4; qx++) {
                double[] climate = noiseChunk.climateAt(qx, qy, qz);
                long[] target = {
                    quantize((float) climate[0]),
                    quantize((float) climate[1]),
                    quantize((float) climate[2]),
                    quantize((float) climate[3]),
                    quantize((float) climate[4]),
                    quantize((float) climate[5])
                };
                result[qy * 16 + qz * 4 + qx] = findBestEntry(target);
            }
        }
    }
    return result;
}
```

### NoiseWorldSource — nc 재사용
```java
// generate() 내에서 nc 이미 생성됨 → 비용 0으로 재사용
NoiseChunk nc = new NoiseChunk(chunkX, chunkZ, router, settings);
boolean[] stone = nc.stoneMask();
// ...
int[] biomeIds = biomeSampler.sampleChunk(chunkX, chunkZ, nc);
```

## 함정 / 주의
- `climateAt(localQX, absQY=96, localQZ)` → cy=48 → cy+1=49 → AIOOBE. absQY 범위 0..95 (max cy=47).
- TDD: `NoiseChunkClimateTest` 작성 후 컴파일 실패(RED) → climateAt 구현(GREEN) 확인.
- depth DF Y-비선형이면 선형 보간 mismatch 가능. 현재 vanilla 1.21.1 선형 → 0 mismatch.

## 관련 파일
- `furnace-server/src/main/java/org/domi/furnace/server/world/gen/NoiseChunk.java`
- `furnace-server/src/main/java/org/domi/furnace/server/world/gen/BiomeSampler.java`
- `furnace-server/src/main/java/org/domi/furnace/server/world/gen/NoiseWorldSource.java`
- `furnace-server/src/test/java/org/domi/furnace/server/world/gen/BiomeSamplerParityTest.java`
- `furnace-server/src/test/java/org/domi/furnace/server/world/gen/NoiseChunkClimateTest.java` (신규)

## 후속 (같은 날, 커밋 7548c0d) — depth-group search 로 최종 해결

위 Fix A+B 후에도 sampleChunk 134→21ms 수준. 측정(ChunkGenBenchmark) 결과 linear/BVH 모두 한계.

**실패한 시도:**
- BVH/RTree: MC biome entry 범위 넓음([-4500,4500] 등) → AABB lower bound ≈ 0 → pruning 무용. 삭제.
- constPart 정렬 + break: 지하 quart에서 depthDist² 전 entry 공통으로 큼 → bestFit 큼 → break 미발동. 42ms 역효과.

**최종 알고리즘 (depth-group search, 4ms):**
1. 기후 6차원 중 depth(d=4) 제외 5개 = FlatCache Y-불변 (NoiseChunk.isClimateColumnYInvariant 로 검증, false면 linear 폴백)
2. 7593 entry를 (depthMin,depthMax) 쌍으로 그룹핑 (BiomeSampler 생성자, 그룹 수 ~소수)
3. 컬럼(qx,qz)당 1 pass: 그룹별 min constPart(5차원 거리²+offset²) + min idx
4. quart당: `f_g = minConst_g + depthDist_g²` 그룹 수만큼 비교 — 7593 → ~10

**정확성:** `min over entries(c_i + d_g²) = min over groups(minConst_g + d_g²)` 수학적 동치. 타이브레이크(min fitness → min entry idx) = 그룹 내/간 min-idx 추적으로 보존. 3 fixture 0 mismatch.

```java
// BiomeSampler 생성자: depth 범위로 그룹핑
long key = (e.paramsMin()[4] << 32) | (e.paramsMax()[4] & 0xFFFFFFFFL);
// 컬럼당: 그룹별 min constPart (d=4 제외 5차원 + offset²)
// quart당: f = gMinConst[gi] + depthDist²  → ~10개 비교
```

**FlatCache wrap 수정도 포함:** climate DF를 `mapAll(this::wrap)` 통과 → FlatCache pre-fill 활성화 (이전엔 raw compute → 캐시 미작동 → 7350 full traversal).

**측정 (ChunkGenBenchmark, n=8 post-warmup):**
| 단계 | 이전 | 이후 |
|---|---|---|
| NoiseChunk construct | 3ms | 2ms |
| stoneMask | 11ms | 13ms |
| sampleChunk | 134ms | **4ms** |
| TOTAL | 149ms | **20ms** |

**남은 병목:** stoneMask 13ms(terrain DF 자체, vanilla 동급), view-distance=64 = 16641 청크(→10 권장).

## 후속 2 (커밋 6949bda) — stoneMask 셀 skip (interval bounds)

측정: stoneMask 13ms = sliceFill(코너 DF 평가) 4ms + 블록 루프(lerp+트리워크) 4ms 반반.

**원리:** 삼선형 보간 = 코너 8개의 볼록결합 → 보간값 ∈ [min코너, max코너] 항상.
final_density 트리(min(squeeze(mul(0.64, interp)), noodle) 형태, 단일 interpolator 아님)에 **interval bound 전파**.
- `DensityFunctions.cellBounds(df, oracle, yMin, yMax)` — Constant/TwoArgFn(구간연산)/Clamp/Mapped(ABS·SQUARE 비단조 처리)/YClampedGradient(셀 y끝점)/RangeChoice(분기 union)/BlendDensity(재귀 필수, min/maxValue가 ±∞)/그 외 글로벌 min·max 폴백
- oracle 패턴: NoiseChunk 래퍼(NoiseInterpolator→코너 8개 min/max, FlatCache→상수, Cache*→wrapped 재귀)는 NoiseChunk가 처리. 비싼 노드(BlendedNoise, spline)는 전부 interpolator 뒤 → 트리 상단 ~20노드만 순회
- 구간 lo > 1e-9 → 셀 64블록 전부 돌(mask=true 직접 채움), hi < -1e-9 → 전부 비돌(기본값). 아니면 일반 루프
- SKIP_EPS=1e-9 = lerp 부동소수점 ulp 오차 보험

**주의:** selectCellYZ를 코너선택/CacheAllInCell채우기로 분리(`fillCellCaches()`), skip 셀은 후자 생략. interpolationCounter는 단조증가라 gap 안전(충돌 불가).

**결과:** 694/768 셀 skip, 블록 루프 4→1ms, stoneMask 13→7ms, 청크 총 **14ms**. parity 전부 0 mismatch(NoiseWorldSourceParityTest·NoiseChunkTest·BiomeSamplerParityTest).

**잔여:** sliceFill 6ms(코너 BlendedNoise 옥타브, vanilla 동급 — SIMD/노이즈 미시최적화 영역), view-distance=64.

## 태그
#Furnace #worldgen #Performance #BugFix