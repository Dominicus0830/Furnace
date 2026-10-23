## 작업 요약
M4g-b 성능 핫픽스: NoiseChunk 기후 DF 셀코너 캐시(Fix A) + fitness lower-bound pruning(Fix B). 스폰 청크 공허·로드 느림 증상 해결.

## 문제 / 목적
BiomeSampler.sampleChunk() = 1536 quart × (6 DF full-eval + 7593 entry linear search) per chunk.
- 6 DF eval: 각 SinglePointContext가 새 객체 → DF 트리 내 FlatCache/Cache2D 공유 없음. 1536×6 = 9216 full traversal.
- 7593 entry linear: 매 quart마다 전체 순회. 가지치기 없음.
- cold JVM에서 2s 초과 → snapshotChunk().get(2s) timeout → 스폰 5청크 공허.

## 해결 방법 / 구현 내용

### Fix A — NoiseChunk 기후 DF 셀코너 캐시
NoiseChunk 생성 시 6개 기후 DF(temp/hum/cont/ero/depth/ridges)를 셀코너에서 pre-compute.
- 캐시 크기: `double[6][noiseSizeXZ+1][cellCountY+1][noiseSizeXZ+1]` = `[6][5][49][5]` = 7350 double
- 평가 수: 5×49×5×6 = 7350 (기존 9216보다 적지만 핵심은 DF 트리 내 캐시 공유)
- cellWidth=4 = 1 quart → XZ 보간 불필요. Y: cellHeight=8 = 2 quart → 선형 보간(ty=0 or 0.5)
- depth DF는 Y에 선형 → 선형 보간 = bit-exact. 나머지 5개 DF는 Y-independent.
- BiomeSampler.sampleChunk(cx, cz, NoiseChunk) 오버로드 추가.

### Fix B — fitness lower-bound pruning
fitnessWithCutoff(): offset² 또는 각 dimension의 누적합이 bestFitness 이상이면 즉시 return.
평균 탐색 entry 수 대폭 감소 (7593 → 수십~수백 수준 예상).

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
- `climateAt(localQX, absQY=96, localQZ)` → cy=48 → cy+1=49 → AIOOBE. absQY 범위는 0..95 (max cy=47).
- TDD: `NoiseChunkClimateTest` 작성 후 컴파일 실패(RED) → climateAt 구현(GREEN) 확인.
- depth DF가 Y에 비선형이면 선형 보간 mismatch 발생할 수 있으나, 현재 vanilla 1.21.1에서 선형 → 0 mismatch.

## 관련 파일
- `furnace-server/src/main/java/org/domi/furnace/server/world/gen/NoiseChunk.java`
- `furnace-server/src/main/java/org/domi/furnace/server/world/gen/BiomeSampler.java`
- `furnace-server/src/main/java/org/domi/furnace/server/world/gen/NoiseWorldSource.java`
- `furnace-server/src/test/java/org/domi/furnace/server/world/gen/BiomeSamplerParityTest.java`
- `furnace-server/src/test/java/org/domi/furnace/server/world/gen/NoiseChunkClimateTest.java` (신규)

## 태그
#Furnace #worldgen #Performance #BugFix
