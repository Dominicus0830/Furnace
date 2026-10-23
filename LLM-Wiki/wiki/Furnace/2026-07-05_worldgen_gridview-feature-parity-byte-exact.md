# 2026-07-05 worldgen — 그리드 FeatureWorldView feature 발산 byte-exact 수정

## 작업 요약
9-grid FeatureWorldView feature 발산(forest 모래 패치·잎 없는 나무) 13라운드 추적 → byte-exact 0/0. (51532,5,7) 754→0, (1234,-49,-20) 564→0.

## 문제 / 목적
seed 51532 forest에 모래 패치 대량 + 잎 없는 통나무. decorateCenter 단일컬럼 view 0 mismatch, 9-grid FeatureWorldView ~732 mismatch. 발산 원인 규명 → oracle fixture 기준 byte-exact 수정.

## 해결 방법 / 구현 내용
근본원인 6개, 커밋 7개.

1. **disk 물 게이트 누락** (`8930f28`) — disk_sand/clay/gravel matching_fluids predicate 미구현 → 육지 모래 패치. 754→346.
2. **isOceanFloorBlock 잎 제외 오류** (`269da3b`) — 바닐라 OCEAN_FLOOR/OCEAN_FLOOR_WG는 `blocksMotion` 기준, 잎 포함. 잎 제외는 MOTION_BLOCKING_NO_LEAVES 전용. 346→169.
3. **validTreePos 잎 미포함 + 부수 버그 2개** (`1e8eb83`) — 바닐라 `#minecraft:replaceable_by_trees` 잎 포함(나무가 이웃 캐노피 뚫고 성장). 미포함 → tree_fail free_height → RNG 드리프트. 부수 발견: BeehiveDecorator no-op 스텁(draw 1개만 소비), ForkingTrunkPlacer가 placeLog 실패에도 top-Y/attachment 기록(바닐라는 OptionalInt 게이팅).
4. **surface[] 라이브 갱신 누락** (`76e7e78`) — WORLD_SURFACE 계열 장식 중 동결. updateSurface()를 setBlock에 연결.
5. **oracle 하니스 버그** (`3e097d4`) — oracle ProtoChunk `setPersistedStatus` 미호출 → EMPTY 고정 → `heightmapsAfter()` WORLDGEN_HEIGHTMAPS만 반환 → OCEAN_FLOOR decoration 내내 동결. 실서버는 CARVERS 승급으로 라이브. **정답 fixture 자체가 바닐라와 다름.** CARVERS 승급 추가 + fixture 전체 재생성.
6. **WG heightmap 동결 누락 + Direction enum 순서** (`fbead9e`, `9103e7b`) — 반대 방향 버그. OCEAN_FLOOR_WG/WORLD_SURFACE_WG는 carve 완료 시 동결이 바닐라 정답, Furnace는 계속 갱신 → 이웃 캐노피 spill이 WG 하이맵 올려 OreFeature/patch_grass 게이트 발산. `freezeWgHeightmaps()` + `surfaceWg[]` 분리(MOTION_BLOCKING계 라이브 유지). 마지막 1개: TreeFeature.Direction enum 선언 순서 ≠ 바닐라 `Direction.values()`(DOWN,UP,NORTH,SOUTH,WEST,EAST) → 잎 distance BFS tie-break 발산.

디버깅 방법론(입증). ① BLOCK_DEBUG 3x3 grid delta → 첫 발산 origin/step/idx 격리. ② per-draw RNG 트레이스(-D{furnace,oracle}.rngTrace*) → 첫 상이 draw. ③ per-column write 트레이스 → 블록 SET 리터럴 diff. 상위 버그 수정 후 이전 draw 번호 전부 stale — fresh 재추적 필수.

## 핵심 코드
```java
// 바닐라 ChunkStatus.java — heightmap 생명주기의 정본
WORLDGEN_HEIGHTMAPS = {OCEAN_FLOOR_WG, WORLD_SURFACE_WG};   // EMPTY..SURFACE 단계만 갱신, CARVERS부터 동결
FINAL_HEIGHTMAPS = {OCEAN_FLOOR, WORLD_SURFACE, MOTION_BLOCKING, MOTION_BLOCKING_NO_LEAVES}; // CARVERS부터 라이브
// ProtoChunk.setBlockState는 getPersistedStatus().heightmapsAfter()에 든 타입만 갱신
```
```java
// oracle 수정 — OracleMain.buildCarvedProtoChunk 끝
chunk.setPersistedStatus(ChunkStatus.CARVERS); // 없으면 EMPTY 고정 → OCEAN_FLOOR 영구 동결 → fixture 오답
```

## 관련 파일
[[2026-07-04_worldgen_feature-neighbor-spill]] (선행 task — 그리드 발산을 pre-existing 버그로 분리)

## 태그
#Furnace #worldgen #BugFix