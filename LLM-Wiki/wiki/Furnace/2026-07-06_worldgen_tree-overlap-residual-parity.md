# 2026-07-06 worldgen — tree-overlap 잔여 mismatch 21건 완전 해소 (byte-exact)

## 작업 요약
seed 51532 (2,0) 20건·(3,3) 1건 feature parity mismatch. 4라운드 RNG 트레이스로 root cause 2건 확정·수정. 둘 다 0 mismatch exact 승격.

## 문제 / 목적
청크 (2,0)·(3,3) lx=15 나무 foliage mismatch 잔존. 이전 가설(spill 창 한계 → cross-origin 두 나무 경합 → 단일 나무 코너 판정) 순차 반증됨. 진짜 원인 필요.

## 해결 방법 / 구현 내용

**조사 기법**: per-draw RNG 트레이스 Furnace·oracle 양쪽 덤프 → diff로 첫 발산 call 특정 → 해당 후보 좌표 판정 입력 실측 → vanilla 소스 대조 확증 후에만 수정.

**Round 1**: (2,0) 자기 나무 RNG 150회 byte-exact 결백. 진짜 발산 = 이웃 origin(3,0) tree 배치 세션 call=27 (Furnace `nextFloat` vs oracle `nextInt(16)` — 메서드 종류부터 갈림 → 캐스케이드).

**Round 2 — root cause ① (커밋 51f6745)**: `ChunkColumn.isOceanFloorBlock`이 short_grass/fern/dead_bush 같은 non-motion-blocking 식물을 "바닥"으로 오판. vanilla `SurfaceWaterDepthFilter`는 OCEAN_FLOOR(식물 제외)−WORLD_SURFACE(식물 포함) 차이로 물깊이 판정. Furnace 둘 다 식물 포함 → diff=0 오통과 → 바닐라가 스킵하는 나무 배치 시도. 식물 3종 OCEAN_FLOOR 판정에서 제외 → (2,0) 20→0.

**Round 3**: (3,3) 1건 별개. 발산 세션 = origin(4,4) birch_tall call=4. water-depth·biome(raw 조회)·giant-trunk abs 전부 배제. 단서: Furnace 쪽 해당 좌표 쓰기 전무 → oracle에만 있는 스킵 사유.

**Round 4 — root cause ② (커밋 d615dae)**: Furnace `PlacedFeature` biome 게이트 raw quart 조회. vanilla `BiomeFilter`는 `BiomeManager.getBiome(pos)` — 8개 인접 quart를 seed 기반 결정론적 "biome zoom" 지터로 블렌딩. 후보 블렌딩 결과 `forest`(birch_tall 미보유) → oracle 스킵, Furnace는 raw `old_growth_birch_forest`로 오통과. `SurfaceSystem` 동일 블렌딩 알고리즘 `FeatureWorldView.getBlendedBiomeId`로 이식, 게이트 교체(seed-obfuscation 바이트 순서까지 기존 검증 코드와 대조) → (3,3) 1→0.

검증: full suite 실패 0 (2회), 기존 8개 fixture 회귀 없음, oracle 임시 계측 전부 원복.

## 핵심 코드
```java
// root cause ① 개념: OCEAN_FLOOR는 non-motion-blocking 지상 식물 제외
// ChunkColumn.isOceanFloorBlock — short_grass/fern/dead_bush를 바닥에서 제외

// root cause ② 개념: biome 게이트는 raw quart가 아니라 블렌딩 결과 사용
// FeatureWorldView.getBlendedBiomeId(x,y,z) — BiomeManager biome-zoom 지터 이식
// (SurfaceSystem의 검증된 블렌딩 알고리즘 재사용)
```

## 관련 파일
- [[2026-07-05_worldgen_gridview-feature-parity]] — RNG 드리프트 디버깅 왕도(BLOCK_DEBUG delta → per-draw 트레이스 → write SET diff)
- [[2026-07-05_worldgen_milestone-gap-survey]]
- 정본: `C:\Users\gagam\.claude\multiagent\tasks\tree-overlap-residual\workers\sonnet-impl\result.md` (Round 1~4 절)
- 수정 파일: `furnace-server/.../world/ChunkColumn.java`, `.../gen/feature/FeatureWorldView.java`, `PlacedFeature` biome 게이트, `FeatureSpillParityTest.java`(exact 승격)

## 교훈
- biome 확인은 반드시 **블렌딩 기준**. raw quart 조회는 quart 경계에서 오판.
- 발산 지점 heightmap류 판정은 vanilla가 쓰는 heightmap 타입(OCEAN_FLOOR vs WORLD_SURFACE vs MOTION_BLOCKING) 술어까지 대조.
- 후속 미확증: `FreezeTopLayerFeature`도 blended-biome 누락 가능성.

## 태그
#Furnace #worldgen #BugFix