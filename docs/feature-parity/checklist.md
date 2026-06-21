# Feature Parity 체크리스트

## Phase 1 — FeatureSorter vanilla parity  ✅ (2026-06-21, 전체 테스트 green)
- [x] `BiomeFeatureData`: 미구현 feature를 drop하지 말고 no-op placeholder PlacedFeature로 유지
- [x] no-op `ConfiguredFeature` 구현 (`NoopFeature`, place → false)
- [x] Loader.configuredFeature: 미구현 타입 → `NoopFeature.INSTANCE` (placedFeature id별 캐시로 identity 유지)
- [x] `FeatureSorter.build`: vanilla 위상정렬(DFS post-order + reverse) 이식 — step별 전역 정렬 목록 + index
- [x] biome 순서 결정적화 (LinkedHashMap + `orderedBiomes()` = load=registry 순서)
- [x] self-edge(같은 feature 연속) 가드로 거짓 사이클 방지
- [~] 단위 테스트: 기존 FeatureSorterTest/BiomeFeatureDataTest 통과. 결정성 전용 테스트는 미추가(필요시)

## Phase 1b — oracle index/순서 검증  ✅ (2026-06-21)
- [x] OracleMain `featureorder` 모드: vanilla possibleBiomes 순서 + step별 정렬 feature id 덤프 (`feature_order.txt`)
- [x] vanilla possibleBiomes() 순서 = biome_parameters.json 첫등장 distinct 확인
- [x] **production 버그 발견·수정**: NoiseWorldSource가 registry 전체 biome으로 FeatureSorter 빌드 → possibleBiomes(53)로 교체(`possibleBiomeOrder`). 안 고쳤으면 실게임 광석 index 어긋남
- [x] `FeatureOrderParityTest`: step6(광석 34개) 포함 step0~8·10 vanilla와 정확 일치 + production biome 순서 == vanilla
- [~] 알려진 갭: step9 해양식생(seagrass_simple 누락, kelp 순서) — Furnace biome JSON 데이터 차이. 테스트서 제외. Phase 2 데이터 보정.
- 주의: WorldGenRegion 기반 실좌표 덤프는 미구현(무거움). index/순서 일치로 좌표 parity 입증(구현 feature의 random 소비는 기존 OreFeatureTest 등이 담보).

## Phase 2 — 미구현 feature/modifier (점진)
- [x] **slice 1 (2026-06-21, 커밋 4518266): random_patch + simple_block.** short_grass 등
      simple_state_provider 지면식물 생성. 추가 modifier: heightmap, block_predicate_filter
      (matching_blocks). PlacedFeature.placeAt(origin) 내포 지원. 정직성 가드 = 상수 provider만
      활성화, weighted(꽃)는 Noop 유지. 테스트 RandomPatchFeatureTest 3종, 전체 240 green.
      - 근사/한계(추후): canSurvive = dirt 태그로 근사(블록별 정확 survival 미구현). heightmap =
        getSurfaceY+1(WORLD_SURFACE_WG/MOTION_BLOCKING 미구분). plains는 noise_threshold_count
        미구현이라 아직 grass 안 나옴(다음 슬라이스). forest/savanna/taiga 등 count-path만 활성.
- [x] **slice 2 (2026-06-21, 커밋 72b5db3): SimplexNoise + noise_threshold_count.** plains 계열 grass
      활성. SimplexNoise byte-exact 포팅(oracle biome_info_noise.bin fixture 40샘플 0.0 delta 검증).
      BIOME_INFO_NOISE=SimplexNoise(2345). oracle에 biomeinfonoise 모드 추가. 전체 green.
      - 잔여: noise_based_count(flower_plains 등 일부)는 미구현. weighted provider 슬라이스서 같이.
- [x] **slice 3 (2026-06-21, 커밋 0485dca): noise_threshold_provider + noise_based_count → flower 활성.**
      멀티에이전트(claude-main 구현 → codex-critic 비평 green → Orchestrator 반영). 핵심: state provider
      noise는 vanilla LegacyRandomSource 경로 → LegacyNormalNoise 신규(oracle legacynormal:2345 fixture
      0.0 delta byte-exact). StateProvider 추상화(Simple/NoiseThreshold), minecraft:flower 라우팅. 전체 green.
      - 잔여: weighted_state_provider(flower_default 등)·dual_noise_provider·noise_provider는 Noop 유지.
- [ ] weighted/noise/dual_noise state provider (random 소비) → flower(random_patch) 활성
- [ ] placement modifier 추가분 (surface_water_depth_filter, count_on_every_layer, environment_scan, random_offset 등)
- [ ] feature 타입: tree, vegetation_patch, fossil, geode, lake, glow_lichen, ... (빈도순 우선)
- [ ] 각 타입 추가 시 placeholder 교체 → 좌표 parity 회귀 테스트

## 검증 명령
- 전체: `./gradlew :furnace-server:test`
- 인게임: seed 123456 새 월드, vanilla 1.21.1과 같은 좌표 대조
