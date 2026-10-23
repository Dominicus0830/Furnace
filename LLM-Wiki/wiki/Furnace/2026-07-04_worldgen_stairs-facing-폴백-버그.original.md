# 2026-07-04 worldgen — stairs facing full-property 조회 폴백 버그

## 작업 요약
DesertPyramidPiece의 stairs 4 mismatch 해소 — "shape auto-connection 미구현" 진단은 오진, 실제는 blockstate 조회 폴백 버그. 커밋 942e1b3.

## 문제 / 목적
StructureFeatureParityTest (b)에서 stairs 4셀 mismatch. 기존 가설 = worldgen 중 stairs shape auto-connection 미구현. 실측 결과 vanilla `WorldGenRegion.setBlock`은 `updateShape`를 호출하지 않고(`SHAPE_CHECK_BLOCKS`에 stairs 없음) worldgen 산출물 stairs는 항상 straight — auto-connection 자체가 불필요.

## 해결 방법 / 구현 내용
실제 원인: DesertPyramidPiece가 stairs blockstate를 facing만으로 조회 → full-property 매칭 실패 시 폴백이 잘못된 state 반환. 수정: 5개 조회 지점을 `stairsState` 헬퍼로 통합 (1파일, DesertPyramidPiece).

## 관련 파일
[[2026-07-04_worldgen_wgOceanFloor-라이브갱신-심층발산-해소]] [[2026-07-03_worldgen-structure_S3-references-regionRandom-oracle훅우회]]

## 태그
#Furnace #worldgen #BugFix
