# 2026-07-05 worldgen — fancy oak placer parity 수정

## 작업 요약
fancy oak(큰 나무) 오생성 root cause = BiomeFeatureData 파서에 fancy placer case 부재 → silent fallback. FancyTrunkPlacer/FancyFoliagePlacer 신규 포팅, fixture 0 mismatch, 커밋 2cee9dd.

## 문제 / 목적
사용자 실서버(seed 51532) 육안 제보: 일반 나무 정상, fancy oak 계열만 일반 나무 모양으로 생성. gridview-feature-parity 완결 후 잔여 발산.

## 해결 방법 / 구현 내용
- `BiomeFeatureData.java` trunk/foliage placer switch에 `minecraft:fancy_trunk_placer`/`minecraft:fancy_foliage_placer` case 없음 → `default -> StraightTrunkPlacer/BlobFoliagePlacer` 로 대체 생성이 주범. case 2개 추가.
- `FancyTrunkPlacer`/`FancyFoliagePlacer` 바닐라 mojang-mapped 소스 라인 대조 100% 일치 (makeLimb/getLogAxis/treeShape/trimBranches, BRANCH_SLOPE 등 상수).
- `TrunkPlacer.placeLog` axis 오버로드 추가 (대각선 가지 로그용).
- 재현: 5x5 청크 스캔(ScratchFancyOakScanTest) → (1,0) 대표 fixture `featurespill_51532_1_0.bin`, FeatureSpillParityTest exact assert 0 mismatch. 기존 fixture 전부 0 유지, full suite green.

## 핵심 코드
```java
// BiomeFeatureData.java parseTrunkPlacer/parseFoliagePlacer
case "minecraft:fancy_trunk_placer" -> new FancyTrunkPlacer(baseHeight, randA, randB);
case "minecraft:fancy_foliage_placer" -> new FancyFoliagePlacer(radius, offset, height);
```

## 미해결 (후속 task 필요)
- (2,0) 20건·(3,3) 1건 mismatch, 전부 lx=15 경계 — `ChunkPipeline` spill 창이 ±1청크 고정인데 fancy 가지가 그 이상 뻗을 수 있음. spill 반경 확장 후속 필요.
- 교훈: **파서 default fallback = silent 오생성 근원.** 미구현 placer 5종(Giant/MegaJungle/Cherry/UpwardsBranching/Bending)도 동일 패턴 → mega-tree-placers 후속 task 예정.

## 관련 파일
[[2026-07-05_worldgen_milestone-gap-survey]]

## 태그
#Furnace #worldgen #BugFix
