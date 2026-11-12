# 월드젠 baseHeight 훅 (Paper CustomChunkGenerator.getBaseHeight) — 2026-11-12

PR #232 검토의 N3 후속. 도미니코 지시 "PAPER랑 같게".

- Paper(최신 main `CustomChunkGenerator`): `getBaseHeight` 는 `implementBaseHeight` 가 참이면 플러그인(Bukkit `ChunkGenerator.getBaseHeight(WorldInfo, Random, x, z, HeightMap)`)이 돌려준 값을 그대로 NMS `getBaseHeight`(= **첫 빈 칸 y**)로 쓰고, `UnsupportedOperationException`(Bukkit 기본 구현)이면 `implementBaseHeight=false` 로 끄고 `delegate.getBaseHeight`(바닐라). `getBaseColumn` 은 늘 `delegate`.
- Furnace: `ChunkGenerationHooks.baseHeight(GenerationHeightmap, x, z) → OptionalInt`(기본 empty = 바닐라). `NoiseWorldSource.firstOccupiedHeight` 가 훅 값 − 1 을 돌려준다(구조물 코드는 occupied 기준). `baseBlock`(getBaseColumn)은 안 건드린다. 평지(flatLayers)도 같은 진입점이라 훅이 먹는다.
- 차이: Paper 는 훅 예외를 그대로 올리지만 Furnace 는 기록하고 그 질의만 바닐라(기존 훅 예외 규칙). Paper 는 시드된 `Random` 을 넘기지만 Furnace 훅은 (x, z)만 받는다 — Furnace API 사용자는 난수가 필요하면 월드 시드·좌표로 직접 만든다.
- **호환 어댑터(Bukkit `ChunkGenerator.getBaseHeight`)가 Paper 와 같은 결과를 내려면**(PR #233 검토 R1):
  (a) Paper 는 호출마다 새 `WorldgenRandom(LegacyRandomSource)` 를 `(x>>4)*341873128712L + (z>>4)*132897987541L` 로 시드한다(월드 시드는 안 들어간다). 그것을 `RandomSourceWrapper.RandomWrapper` 로 감싼 `java.util.Random` 을 플러그인에 넘긴다.
  (b) 플러그인 반환값은 +1/-1 없이 그대로 훅 값이다(Bukkit javadoc 은 "highest block" 이라 쓰지만 Paper 는 첫 빈 칸 y 로 쓴다).
  (c) `UnsupportedOperationException` 은 생성기 객체 단위로 끈다(Furnace 는 훅 객체 단위로 같은 효과).
- 기록(수정 불필요): 훅이 `Integer.MAX_VALUE`/`MIN_VALUE` 를 돌려주면 폐허 포털 지하 `findBetween` 범위가 넘쳐 `IllegalArgumentException`(바닐라 `Mth.randomBetweenInclusive` 도 같아 Paper 와 같다). 기존: `JigsawStructure` 가 `project_start_to_heightmap` 의 `OCEAN_FLOOR_WG` 외 값을 모두 `WORLD_SURFACE_WG` 로 읽는다(1.21.1 바닐라 데이터 7곳은 전부 `WORLD_SURFACE_WG` 라 영향 없음). Bukkit `HeightMap` 6종 중 구조물이 쓰는 `WORLD_SURFACE_WG`·`OCEAN_FLOOR_WG` 두 가지만 넘긴다.
- 함정: 높이 캐시(`surfaceHeightCache`)는 바닐라 값만 담는다 — 훅 결과를 캐시에 넣으면 훅 교체가 낡는다. 구조물 시작점 캐시는 훅 바뀜 표식으로 이미 비워진다.
