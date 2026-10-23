# 2026-07-02 worldgen-feature — featurefull 3좌표 0 mismatch 달성

## 작업 요약
M4g-d feature 파이프라인 parity. FeaturePipelineParityTest 3좌표 40/71/314 → 0/0/0. 전체 스위트 green, 커밋 fb4bd82.

## 문제 / 목적
full-chunk feature 비교(featurefull fixture)에서 seagrass/kelp/glow_lichen/disk mismatch 잔존.

## 해결 방법 / 구현 내용
oracle 오염 수정([[2026-07-02_worldgen-oracle_태그미바인딩-3x3쓰기-fixture오염]]) 후 Furnace측 버그 3개.
1. **DiskFeature radius 미샘플**: radius가 IntProvider(disk_sand uniform 2..6)인데 상수 근사 + RNG 미소비 → desync. `TreeIntProvider.sample` + rule_based state provider(아래 air→sandstone) + vanilla y 루프(top→down, `by > y-half-1`)로 재작성. parseDisk가 rule 파싱(offset [0,-1,0] matching_blocks만 허용, 아니면 Noop — 정직성 가드).
2. **GlowLichen 탐색 비전진 quirk**: vanilla `MultifaceGrowthFeature`가 `mutableBlockPos.setWithOffset(blockPos, direction)`을 루프 안에서 호출 → 탐색 origin+1에서 전진 안 하고 searchRange번 재시도(유명 바닐라 버그). Furnace는 전진 구현 → divergence. quirk 그대로 재현.
3. **GlowLichen spread에 validDirections 오적용**: spread 경로에서 feature 설정(can_place_on_floor=false) 적용 → DOWN면 lichen 배치 불가. vanilla는 spread 시 block 레벨 `isFaceSupported`(전방향)만 검사 — `isValidStateForPlacement`에서 방향 필터 제거.
4. 부수: SeagrassFeature tall에 vanilla canSurvive의 source-water(amount==8) 가드. stale 테스트 2개 갱신(OreFeatureTest `loadGenerated`로 wgOceanFloor 초기화, BiomeFeatureDataTest 화이트리스트에 GlowLichen/Tree).

## 디버깅 방법론 (재사용 가치 높음)
- `-Doracle.rngTrace` ↔ `-Dfurnace.rngTrace` (+Cx/Cz/Step/Idx) 콜 시퀀스 diff → 분기 iteration 특정.
- RNG 소비 동일한데 배치만 다름 → world-state/알고리즘 차이. `-Doracle.dumpColumns`로 좌표 블록 대조.
- shuffle 콜 그룹 해석: 초기 shuffle=5요소(bound 5,4,3,2), except-shuffle=4요소(4,3,2). UP 방향은 opposite DOWN이 validDirections에 없어 except도 5요소(5,4,3,2) — 그룹 폭으로 방향 walk 역산 가능.

## 핵심 코드
```java
// GlowLichen — vanilla 비전진 quirk 재현
int currentX = x + OFFSETS[direction][0];  // origin+1 고정
int currentY = y + OFFSETS[direction][1];
int currentZ = z + OFFSETS[direction][2];
for (int i = 0; i < searchRange; i++) {
    int currentState = view.getBlock(currentX, currentY, currentZ); // 전진 없음
    ...
}
```
```java
// DiskFeature — radius 샘플 + rule 기반 state
int r = radius.sample(random);           // RNG 소비 (vanilla IntProvider.sample)
for (int by = maxY; by > minY; by--) {   // top→down, minY = y-half-1 exclusive
    if (!targetStateIds.contains(view.getBlock(bx, by, bz))) continue;
    view.setBlock(bx, by, bz, stateFor(view, bx, by, bz)); // 아래 air면 sandstone
}
```

## 관련 파일
[[2026-07-02_worldgen-oracle_태그미바인딩-3x3쓰기-fixture오염]], [[레이어-월드젠]]

## 태그
#Furnace #worldgen-feature #BugFix #Feature