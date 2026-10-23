# 2026-07-04 worldgen — stairs facing full-property 조회 폴백 버그

## 작업 요약
DesertPyramidPiece stairs 4 mismatch 해소. "shape auto-connection 미구현" 진단 = 오진. 실제 = blockstate 조회 폴백 버그. 커밋 942e1b3.

## 문제 / 목적
StructureFeatureParityTest (b) stairs 4셀 mismatch. 기존 가설: worldgen 중 stairs shape auto-connection 미구현. 실측: vanilla `WorldGenRegion.setBlock`은 `updateShape` 호출 안 함(`SHAPE_CHECK_BLOCKS`에 stairs 없음). worldgen 산출물 stairs 항상 straight — auto-connection 불필요.

## 해결 방법 / 구현 내용
실제 원인: DesertPyramidPiece가 stairs blockstate를 facing만으로 조회. full-property 매칭 실패 시 폴백이 잘못된 state 반환. 수정: 조회 지점 5개 → `stairsState` 헬퍼 통합 (1파일, DesertPyramidPiece).

## 관련 파일
[[2026-07-04_worldgen_wgOceanFloor-라이브갱신-심층발산-해소]] [[2026-07-03_worldgen-structure_S3-references-regionRandom-oracle훅우회]]

## 태그
#Furnace #worldgen #BugFix