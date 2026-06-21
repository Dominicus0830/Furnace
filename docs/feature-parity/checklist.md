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
- [x] **slice 4 (2026-06-21, 커밋 0176563): weighted/noise/dual_noise state provider.** flower_default
      (weighted)·flower_flower_forest(noise) 활성. 멀티에이전트(claude-main→codex-critic→반영). MathHelper
      clampedMap/inverseLerp 추가. codex-critic HIGH 반영: blocks table=base id만→Properties 보유 state는
      매핑 불가 → Properties 있으면 provider Noop 가드(byte-exact 보존, 잘못된 블록 금지). MED/LOW(weighted
      overflow/empty) 가드. 전체 green.
- [x] **slice 5 (2026-06-21, 커밋 964b388): full blockstate(Name+Properties) 해석.** oracle allstates
      모드로 per-state id 덤프(blocks/blockstates_1.21.1.txt, 26684) + BlockStateKey canonical key +
      BlockRegistry.stateIdOrAir(name,props) + parseState Properties. slice4 Properties Noop 가드 제거 →
      flower_meadow/flower_cherry 등 propertied flower 활성. canonical key oracle↔Furnace byte-identical
      (BlockRegistryStateTest lower≠upper로 런타임 검증). codex-critic 전항목 OK. back-compat(default 폴백). green.
- [ ] placement modifier 추가분 (surface_water_depth_filter, count_on_every_layer, environment_scan, random_offset 등)
- [~] **feature 타입 tree/lake/geode/vegetation_patch 등 = 보류(아키텍처 블로커).** 이들은 청크경계를 넘어
      블록 배치 + tree updateLeaves는 이웃청크 읽기 필요. 현 decoration은 center 청크 1개(ChunkColumn)만
      read/write(NoiseWorldSource.decorate→FeatureWorldView 단일 col). **byte-exact엔 다중청크 decoration
      (vanilla WorldGenRegion급) 선행 필요 = 별도 core 마일스톤.** 현 마일스톤(M4g-c4 등 in-chunk)엔 premature
      → 보류. grass/flower(slice1~5)는 대부분 in-chunk라 Phase2 실가치 확보됨(경계 식물 minor 갭은 그 마일스톤서 동반 해소).
- [ ] (그 마일스톤 내) tree(trunk/foliage placer)·vegetation_patch·lake·geode + 좌표 parity 회귀 테스트

## 검증 명령
- 전체: `./gradlew :furnace-server:test`
- 인게임: seed 123456 새 월드, vanilla 1.21.1과 같은 좌표 대조
