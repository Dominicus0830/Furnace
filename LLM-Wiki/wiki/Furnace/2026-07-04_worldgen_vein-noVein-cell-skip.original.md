# 2026-07-04 worldgen — vein 셀 NO_VEIN 조기 스킵 (M-opt P0-3)

## 작업 요약
OreVeinifier per-block 호출을 veinToggle 8-코너 bound 판정으로 셀 단위 스킵 — 실지형 스킵률 76~92%, 커밋 9d1bcb7.

## 문제 / 목적
M-opt 서베이에서 generateBlockIds(aquifer 경로)가 청크당 19ms로 최대 병목. sonnet-aquifer 분석 결과 vein y밴드 [-60,50]에 걸친 셀은 무조건 per-block OreVeinifier 3중 DF 평가를 강제 — 셀 전체가 NO_VEIN이어도 스킵 못 함.

## 해결 방법 / 구현 내용
- `OreVeinifier.canSkipCell(minD, maxD, cellMinY, cellMaxY)`: veinToggle 셀 bound로 셀 전체 NO_VEIN 보장 증명. 3단계 — (1) bound 부호 판정(0 걸치면 폴백 false), (2) 셀 y구간이 [minY,maxY]와 비중첩이면 즉시 true, (3) d1 텐트함수 최댓값을 꺾임점 i*=(minY+maxY)/2 clamp된 floor/floor+1 두 후보점에서 유도 → `maxAbs + d1Max < 0.4`.
- `NoiseChunk`: `wrappedVeinToggle` 필드(기존 wrap 캐시 재사용, 신규 DF 트리 아님) + `veinBoundsScratch` 2-slot 스크래치 + 스킵 조건에 `canSkipVeinForCell(cy)` 결합. cellBoundsOracle 기존 인프라 재사용.
- parity 안전 근거: veinStateId의 두 조기반환(i2<0||i1<0, abs+d1<0.4)은 `random.at()` 이전 — RNG 0회 소비라 일괄 스킵해도 출력·RNG 스트림 불변.
- 검증: fuzz 20,000 trial(스킵 판정 true ⇒ 128블록 전부 NO_VEIN + RNG 미소비 감시 예외), parity 3종 0 mismatch, full suite green.
- 성능: 스킵률 76~92%(4시드×4좌표)로 높으나 A/B 5회 median 46→44ms(그룹내 노이즈 3ms) — 경계선. 무해 판정으로 커밋.

## 핵심 코드
```java
public static boolean canSkipCell(double minD, double maxD, int cellMinY, int cellMaxY) {
    boolean copper;
    if (minD > 0.0) copper = true;
    else if (maxD <= 0.0) copper = false;
    else return false; // bound가 0을 걸침 → copper/iron 갈릴 수 있음, 폴백

    int minY = copper ? COPPER_MIN_Y : IRON_MIN_Y;
    int maxY = copper ? COPPER_MAX_Y : IRON_MAX_Y;
    int yLo = Math.max(cellMinY, minY);
    int yHi = Math.min(cellMaxY, maxY);
    if (yLo > yHi) return true; // 셀 y구간 전체가 [minY,maxY] 밖 → 항상 NO_VEIN

    double maxAbs = Math.max(Math.abs(minD), Math.abs(maxD));
    long iStarFloor = Math.floorDiv((long) minY + maxY, 2L);
    int c1 = MathHelper.clamp((int) iStarFloor, yLo, yHi);
    int c2 = MathHelper.clamp((int) iStarFloor + 1, yLo, yHi);
    double mMax = Math.max(veinM(c1, minY, maxY), veinM(c2, minY, maxY));
    double d1Max = MathHelper.clampedMap(mMax, 0.0, 20.0, -0.2, 0.0);
    return maxAbs + d1Max < 0.4;
}
```

## 함정 (중요)
ChunkGenBenchmark의 `benchmarkChunkGenPhases`·`benchmarkActualGeneratePhases`는 4-인자 NoiseChunk(reg=null → oreVeinifier=null)라 vein 경로 미실행 — 죽은 벤치. vein 관련 A/B는 `benchmarkActualWorldSourceGenerate`만 유효.

## 관련 파일
[[2026-07-04_worldgen_m-opt-p0-quickwins]]

## 태그
#Furnace #worldgen #Feature #Performance
