# 2026-07-06 worldgen·net — regrass·WORLD_SURFACE·버섯 canSurvive

## 작업 요약
실서버 스크린샷 발산 후속 4커밋: carver regrass(2d4f510)·연못 fixture(892b5b7)·WORLD_SURFACE heightmap 전송(817b160)·mushroom canSurvive(2bc33b9). full suite green. 정본 tasks/{carver-regrass,heightmap-worldsurface,pond-tree-residual}/.

## 핵심 내용
1. **carver regrass** — vanilla WorldCarver.carveBlock reachedSurface/topMaterial 이식. carve가 지표 관통 시 노출 dirt를 biome top material 복원. (-3,-6) 32→0. 스크린샷 흙 패치 원인.
2. **WORLD_SURFACE 전송** — ChunkSerializer가 MOTION_BLOCKING만 보내 클라 F3 `SH ??`. surface[]가 실제 !isAir 술어(=WORLD_SURFACE semantics)라 재사용. **잔여**: MOTION_BLOCKING 태그가 blocksMotion 아닌 !isAir 값 — tasks/motionblocking-predicate(pending).
3. **버섯 canSurvive** — SimpleBlockFeature에 vanilla canSurvive 분기 부재. mushroom 전용 근사(mushroom_grow_block 태그 or WORLD_SURFACE_WG 기반 skylight<13). oracle 0 mismatch로 **바닐라도 gen 중 버섯 게이트 입증** — "gen 중 광량 미계산이라 전부 통과" 이전 결론 정정. 함정: 라이브 heightmap 기준으로 하면 같은 patch 내 자기간섭으로 16→7 한계, 동결본(WG) 기준이어야 16→0.
4. **tree-overlap 미해결** — cross-origin 경합 반증. 단일 나무 foliage 코너 판정 발산으로 좁힘. validTreePos isLeaves 제거 실험은 대악화(0→276), 재시도 금지. tasks/tree-overlap-residual(pending).
5. **giant-trunk abs 이중 적용 버그 발견** (FoliagePlacer.shouldSkipLocationSigned) — mega-tree-placers 작업 시 필수 수정.

## 다음 (내일)
tree-overlap-residual + motionblocking-predicate (pending 등록됨) → mega-tree-placers(giant-abs 포함) → coral → monster_room.

## 관련 파일
[[2026-07-05_worldgen_realserver-divergence-fixes]], [[2026-07-05_worldgen_milestone-gap-survey]]

## 태그
#Furnace #worldgen #BugFix
