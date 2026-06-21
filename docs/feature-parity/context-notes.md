# Context Notes — Feature Parity

## 2026-06-21 초기 진단
- 증상: 동일 seed·좌표에서 광석 위치 다름, 잡블럭 없음/위치 다름.
- vanilla `net.minecraft.world.level.biome.FeatureSorter.buildFeaturesPerStep` 확인:
  - biome별 feature 목록으로 ordering 제약 그래프 구성(같은 biome 내 k → k+1).
  - `Graph.depthFirstSearch` 위상정렬 → `Collections.reverse` → step별 목록.
  - 최종 index = step별 정렬 목록 내 위치 (`Util.createIndexIdentityLookup`).
  - feature 첫 등장 순서로 임시 featureIndex 부여(computeIfAbsent) → comparator(step, featureIndex)가 DFS·TreeSet 순서 결정 → biome 순서가 최종 index에 영향.
- 호출부: `ChunkGenerator` lazy `FeatureSorter.buildFeaturesPerStep(List.copyOf(biomeSource.possibleBiomes()), b->b.getGenerationSettings().features(), true)`.
  - **possibleBiomes() 순서가 index에 영향** → Furnace도 동일 순서 재현 필요(Phase 1b에서 oracle로 확정).

## 설계 결정
- 미구현 feature를 drop하면 index가 vanilla와 영영 안 맞음 → **placeholder로 보존** 결정.
- placeholder identity는 feature id 문자열 캐시로 유지(vanilla는 PlacedFeature value identity로 intern).
- placement modifier는 placeholder엔 무의미(place no-op) → Phase 2에서 실제 feature 붙일 때 같이 구현.

## 이미 고친 것(이 세션, 별개 버그)
- 재접속 스폰청크 타임아웃: `ConnectionHandler` future.get(2s) 제거 → 비동기 batch.
- FreezeTopLayer 전세계 동결: biome 온도(<0.15) 게이트 추가.
- furnace.properties: level-seed=123456.

## 2026-06-21 Phase 1 완료
- `FeatureSorter` vanilla 위상정렬 이식, `NoopFeature` placeholder, `BiomeFeatureData` 전체 feature 보존 + LinkedHashMap 순서.
- 발견: 테스트 픽스처가 step6에 같은 feature 연속 2회(인터닝 검증) → self-edge → 거짓 사이클. `cmp.compare!=0` 가드 추가. 실데이터엔 연속 중복 없어 vanilla와 동치.
- 전체 테스트 green (parity 회귀 0).
- **주의: 좌표 byte-exact parity는 아직 미검증.** 결정성·위상정렬 구조만 확보. 실제 vanilla 일치는 (1) biome 순서 = vanilla possibleBiomes() 순서 일치, (2) 전체 feature 목록 일치 전제 → Phase 1b oracle 덤프로 확정 필요.

## 2026-06-21 Phase 1b 완료 + production 버그
- vanilla `MultiNoiseBiomeSource.collectPossibleBiomes` = `parameters().values().map(getSecond)`, base `distinct()` → 첫등장 순서. = biome_parameters.json 첫등장 distinct(확인됨).
- **production 버그:** `NoiseWorldSource.overworld`가 `registries.entries("minecraft:worldgen/biome")`(전체 registry, nether/end 포함)로 `loadFeaturePlacer` → FeatureSorter가 vanilla possibleBiomes(53)와 다른 집합/순서 → 광석 index 어긋남. **Fix:** `possibleBiomeOrder(data, registries)`(biome_parameters.json 첫등장 distinct, registry 존재만) 도출해 전달. `registryBiomeNames`(biome id 매핑용)는 그대로.
- oracle `featureorder` 모드 + `feature_order.txt` fixture + `FeatureOrderParityTest`.
- **결과: step0~8·10 vanilla 정확 일치(광석 step6 34개 포함) → 광석 좌표 parity 입증.** step9 해양식생만 데이터 갭(제외).
- 실좌표(블록단위) 덤프는 vanilla `WorldGenRegion`+`ServerLevel` 셋업 필요(무거움) → 미구현. index/순서 parity로 대체 입증.

## 2026-06-21 Phase 2 slice 1 — random_patch/simple_block (커밋 4518266)
- 핵심 통찰: 바닐라는 feature마다 `setFeatureSeed(decorationSeed, globalIndex, step)`로 **독립 재시드**
  (`seed+index+10000*step`). → Noop이 random 0소비해도 타 feature 스트림 안 깨짐. 구현 feature는
  자기 index 슬롯서 격리 검증 가능(Phase1b index parity가 이를 보장).
- random_patch 소비: try당 정확히 6 nextInt(x: i1,i1 / y: i2,i2 / z: i1,i1). 내부 simple_block의
  block_predicate_filter·simple_state_provider는 0소비 → patch_grass = 6×tries.
- inner feature는 **인라인 PlacedFeature**(Holder 아님): {feature:configured, placement:[block_predicate_filter]}.
  → `PlacedFeature.placeAt(origin)` 추가(기존 place는 y=0 시작이라 부적합).
- **정직성 가드(중요):** parseSimpleBlock은 to_place가 simple_state_provider일 때만 SimpleBlockFeature
  반환, 아니면 Noop. parseRandomPatch는 inner가 SimpleBlockFeature 아니면 Noop. → weighted provider
  꽃 등은 random 소비가 달라 desync되므로 구현 전까지 placeholder 유지(거짓 parity 금지).
- 근사한 부분(추후 정밀화): (1) canSurvive=dirt 태그 근사(블록별 정확 survival 미구현, random
  미소비라 스트림엔 무영향·배치만 근사). (2) heightmap=getSurfaceY+1(heightmap 종류 미구분).
  (3) weighted/noise state provider 미지원. (4) noise_threshold_count 미지원→plains grass 아직 X.
- block↔state 입도: registry.stateIdOrAir는 블록당 default state 1개. 월드도 default 저장 → ground
  set을 default state로 구성하면 매치(grass_block snowy=false 등).

## 2026-06-21 Phase 2 slice 2 — SimplexNoise + noise_threshold_count (커밋 72b5db3)
- vanilla `Biome.BIOME_INFO_NOISE = new PerlinSimplexNoise(new WorldgenRandom(new LegacyRandomSource(2345L)), [0])`.
  octaves=[0] → noiseLevels 1개, highestFreqInput=2^0=1, highestFreqValue=1/(2^1-1)=1 → getValue(x,z,false)
  = **단일 SimplexNoise.getValue(x,z) 2D**. PerlinSimplexNoise 일반 래퍼 불필요(SimplexNoise만 포팅).
- noise_threshold_count/noise_based_count는 RepeatingPlacement = count(pos)만큼 pos 반복, **random 0소비**.
  count = `BIOME_INFO_NOISE.getValue(x/200, z/200, false) < noise_level ? below : above`.
- 검증: oracle `biomeinfonoise` 모드 → biome_info_noise.bin(40샘플) → SimplexNoiseTest 0.0 delta(byte-exact).
- 잔여: noise_based_count(`ceil((value+offset)*ratio)`, value at x/noiseFactor) 미구현. flower류서 같이.

## 2026-06-21 Phase 2 slice 3 — flower noise_threshold_provider (커밋 0485dca, 멀티에이전트)
- **핵심 함정**: state provider noise = vanilla `NormalNoise.create(new WorldgenRandom(new
  LegacyRandomSource(seed)), params)`. PerlinNoise.create(useNewFactory=true)는 `forkPositional()`
  (=LegacyRandomSource.nextLong 1회)로 factorySeed, 옥타브마다 `fromHashOf("octave_"+N)` =
  `new LegacyRandomSource(name.hashCode() ^ factorySeed)`. **Xoroshiro 경로(MD5)와 시드 완전 다름** →
  기존 NormalNoise(Xoroshiro 전용) 재사용 불가. LegacyNormalNoise 신규(noise 산술은 ImprovedNoise에
  LegacyRandom 생성자 추가해 재사용).
- NoiseThresholdProvider.getState 소비순서: noise(0소비) → noise<threshold ? lowStates[nextInt] :
  (nextFloat 먼저, <highChance면 highStates[nextInt], 아니면 defaultState). SimpleBlockFeature.place는
  getState를 생존판정보다 **먼저** 호출(vanilla 일치, 상수 provider는 0소비라 slice1 무영향).
- 검증: oracle `legacynormal:<seed>` 모드 → legacy_normal_2345.bin → LegacyNormalNoiseTest 0.0 delta.
  codex-critic 비평 전항목 OK. (gemini는 쿼터 고갈로 미수행 — log [ERROR].)
- 잔여: weighted_state_provider(flower_default 다수)·dual_noise·noise_provider 미구현(Noop 유지).

## 미해결/주의
- step9 ocean veg 데이터 갭: Furnace biome JSON에 seagrass_simple 없음 + kelp 순서 차이. oracle에 biome-JSON 덤프 모드 추가해 1.21.1로 재생성하면 해소(Phase 2).
- 정확한 좌표 parity는 oracle decoration fixture 없이는 단위테스트로 "결정성/위상정렬"까지만 검증 가능.
  진짜 좌표 일치는 Phase 1b 필수.
- `decorate()`는 chunk에 존재하는 biome의 feature만 place하되 index는 전역값 사용 — vanilla와 등가(재시드 독립). 유지.
