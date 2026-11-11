## 2026-11-11 표면 규칙 꼭짓점 바이옴 = 이웃 청크 저장 바이옴 (surface-stored-biome)

- 바닐라 `NoiseBasedChunkGenerator.buildSurface` 는 `WorldGenRegion` 의 BiomeManager 를 쓴다. ChunkPyramid SURFACE 단계가 반경 1 이웃을 BIOMES 이상으로 요구하고
  `WorldGenRegion.getChunk` 는 상태가 모자라면 예외라, `getBiome` 의 꼭짓점 quart 바이옴은 **그 quart 가 속한 청크에 저장된 바이옴**이다.
  `ChunkAccess.getNoiseBiome` 은 quart y 를 **차원** 높이 범위로 자른다(네더 0..255, 생성 높이 0..127 이 아님). PR #228 검토가 바이트코드로 확인.
- 예전 Furnace 는 이웃 쪽을 `sampleQuart`(R-트리 힌트 없음)로, y 를 자르지 않고 구했다. 오버월드 시드 3 에서 28블록이 바닐라 모델과 달랐다(블록 결과는 표본에서 그대로).
- 저장 바이옴(= `sampleChunk`)과 quart 단독 검색(`sampleQuart`)의 차이는 **R-트리 거리 동률**뿐이다: 기후 값은 같다(`fillTarget` 보간 = 직접 계산, 4설정 600청크 84만 quart 대조, 다른 값 0).
  동률 예: 오버월드 시드 3 quart (24,−7,7) — humidity 거리 0.7−0.3392 와 depth 거리 1−0.6392 가 같아 river(힌트)와 lush_caves(트리 순서)가 갈린다.
- 그래서 이웃 quart 는 `ClimateRTree.searchUnique`(최근접에 값이 다른 leaf 가 또 있으면 MIN_VALUE)로 구하고, 동률이면 그 이웃 청크의 `sampleChunk` 를 통째로 만든다.
  최근 만든 청크의 바이옴 배열은 `NoiseWorldSource.chunkBiomeCache`(512)에 두고 이웃이 그대로 읽는다 — 캐시 유무로 답이 바뀌지 않는다(시험이 빈·찬 두 경우를 본다).
- carver 의 `topMaterial` 은 바닐라도 `BiomeManager.withDifferentSource(biomeSource)` 라 저장 바이옴이 아니라 바이옴 소스를 바로 읽는다(자르지 않음) — 그대로 둔다.
- 바닐라 R-트리 힌트는 스레드 지역(직전 쿼리)이라 바닐라 자신도 동률에서 비결정적이다. Furnace 모델은 "청크마다 힌트 없이 시작"(sampleChunk)이다.
- 월드젠 훅: 구조물 배치 높이·바닥 열은 훅을 보지 않는다 — `docs/api/worldgen-hooks.md` 를 실제에 맞췄고 결정 항목은 `docs/user-todo/entries/2026-11-11-worldgen-hooks-base-height.md`.
