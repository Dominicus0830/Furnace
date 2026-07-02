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

## 2026-06-21 Phase 2 slice 4 — weighted/noise/dual_noise provider (커밋 0176563, 멀티에이전트)
- weighted: getState=nextInt(totalWeight) 1회+누적차감(vanilla WeightedRandom). noise: LegacyNormalNoise→
  clamp((1+v)/2,0,0.9999); states[(int)(d*size)], 0소비. dual: slowNoise variety list+fastNoise index, 0소비.
- MathHelper에 inverseLerp/clampedMap 추가(vanilla Mth 직접 대조).
- **codex-critic HIGH 발견(중요)**: `blocks/1.21.1.txt`는 블록당 **base(default) state id 1개만** 보유
  (per-property 미열거). parseState는 Name만 읽음 → Properties 보유 state(tall_grass[half]·pink_petals[…])는
  base로 축약돼 byte-exact 깨짐. **보수적 가드 채택**: state에 Properties 있으면 weighted/noise/dual provider를
  Noop 유지(잘못된 블록 배치 금지). flower_default·flower_flower_forest(무Properties)만 활성.
  → **slice 5 = full blockstate 해석**(blocks table per-state id 데이터 필요). 해결 시 flower_meadow/cherry 활성.
- 교훈: claude-main이 Properties를 LOW로 과소평가 → codex-critic이 HIGH로 정당 격상. reviewer가 byte-exact
  break를 잡음(Producer-Reviewer 실효).

## 2026-06-21 Phase 2 slice 5 — full blockstate 해석 (커밋 964b388, 멀티에이전트)
- slice4 codex-critic HIGH(Properties 축약) 해소. blocks/1.21.1.txt는 block→default id만 → 신규
  blocks/blockstates_1.21.1.txt(oracle allstates, 26684 state별 id).
- **canonical key 계약**(oracle canonicalKey ↔ Furnace BlockStateKey.of byte-identical): props 없으면
  `name`, 있으면 `name;k1=v1,k2=v2`(property 이름 String 오름차순, value=Property.getName(value)).
  oracle은 vanilla state.getValues(), Furnace는 JSON Properties — value 직렬화 동일.
- BlockRegistry: sibling blockstates 테이블 best-effort 로드(부재 시 빈 맵·default 폴백 = back-compat,
  기존 1.21.1.txt·60여 호출처 무변경). stateIdOrAir(name,props) exact 해석.
- parseState가 Properties 해석 → 모든 state(weighted/noise/dual/threshold low·high)가 정확 id.
- 검증 핵심: canonical key 불일치 시 lower==upper(둘 다 default 폴백)로 BlockRegistryStateTest 실패 →
  통과 = 양측 일치 입증. tall_grass;half=lower=10756(=base, lower가 default), pink_petals non-default≠base.

## 미해결/주의
- step9 ocean veg 데이터 갭: Furnace biome JSON에 seagrass_simple 없음 + kelp 순서 차이. oracle에 biome-JSON 덤프 모드 추가해 1.21.1로 재생성하면 해소(Phase 2).
- 정확한 좌표 parity는 oracle decoration fixture 없이는 단위테스트로 "결정성/위상정렬"까지만 검증 가능.
  진짜 좌표 일치는 Phase 1b 필수.
- `decorate()`는 chunk에 존재하는 biome의 feature만 place하되 index는 전역값 사용 — vanilla와 등가(재시드 독립). 유지.

## 2026-07-02 oracle 태그 미바인딩 버그 — fixture 자체가 오염돼 있었음
- **증상**: seagrass_deep에서 oracle이 tall_seagrass를 단 1개도 안 놓음(nextDouble<0.8로 tall roll인데도).
  Furnace RNG 시퀀스는 oracle과 완전 일치(scatter/isTall 전부 동일) — 배치 결과만 다름.
- **원인**: OracleMain은 VanillaRegistries.createLookup()(datagen 모드)이라 태그가 자동 바인딩 안 됨.
  BLOCK 태그 일부만 hardcode 바인딩했고 **FLUID 태그는 미바인딩** → `FluidTags.WATER`가 빈 태그 →
  `TallSeagrassBlock.canSurvive`(LOWER: `fluidState.is(FluidTags.WATER) && amount==8`)가 항상 false →
  oracle fixture에서 tall_seagrass 전멸. short(SeagrassBlock)는 fluid 태그 검사 없어 정상 → "short만 5개" 증상.
- **추가 발견**: `minecraft:leaves`·`minecraft:replaceable_by_trees` 블록 태그도 미바인딩이었음.
  vanilla `TreeFeature.validTreePos = isAir || is(REPLACEABLE_BY_TREES)` → oracle 트리가 물/풀/잎 위치에
  배치 실패 → **tree fixture(-53,-26 등)도 바닐라와 다름. 라운드 3~10의 트리 디버깅은 오염된 oracle을
  추적한 것일 수 있음.** 재검증 필요.
- **수정**: OracleMain에 fluid 태그(water/lava) 바인딩 + leaves/replaceable_by_trees 블록 태그 추가
  (내용은 server-1.21.1.jar의 data/minecraft/tags/*.json에서 그대로 추출 — 추측 금지).
  featurefull fixture 6개 전부 재생성.
- **교훈**: oracle 환경에서 태그 의존 로직(canSurvive 류)은 태그 바인딩 여부부터 의심. fixture와 실제
  바닐라 월드 인게임 대조가 최종 권위.

## 2026-07-02 featurefull 3좌표 0 mismatch 달성 (40/71/314 → 0/0/0)
버그 4개 연쇄 수정.
1. **oracle FLUID/블록 태그 미바인딩** (위 항목) — fixture 재생성으로 tall_seagrass 등 복원.
2. **oracle setBlock center-only 드롭**: OracleWorldGenLevel.setBlock/ensureCanWrite가 center 청크만 허용
   → 바닐라 WorldGenRegion writeRadius=1(3×3)과 다름 → 이웃 걸친 tall_seagrass 배치 드롭 → 이후
   scatter 재방문 시 water 판정이 바뀌어 RNG 소비 분기. 3×3 허용으로 수정, fixture 재재생성.
3. **DiskFeature radius 미샘플**: radius가 IntProvider(uniform 2..6)인데 상수 근사 + RNG 미소비 →
   desync. TreeIntProvider 샘플링 + rule_based state provider(아래 air→sandstone) + vanilla y 루프
   (top→down, minY 경계 -half-1 exclusive)로 재작성. coord3의 sand 13개 해소.
4. **GlowLichen 2건**:
   a. vanilla MultifaceGrowthFeature의 quirk 재현 — 탐색 루프가 `setWithOffset(blockPos, direction)`을
      매 반복 호출해 **origin+1에서 전진하지 않음**. Furnace는 전진 구현이라 divergence. 그대로 재현.
   b. spread 경로에서 Furnace가 feature의 validDirections(can_place_on_floor=false)를 잘못 적용 →
      DOWN면 lichen 배치 불가. vanilla는 spread 시 block 레벨 isFaceSupported(전방향)만 검사. 제거.
- 디버깅 방법론: oracle.rngTrace ↔ furnace.rngTrace 콜 시퀀스 diff로 분기 iteration 특정 →
  oracle.dumpColumns로 해당 좌표 world state 대조. (glow_lichen은 RNG 소비가 양측 동일한데 배치만
  달라서 world-state/알고리즘 차이로 즉시 좁혀짐.)
- 부수 수정: SeagrassFeature tall에 vanilla canSurvive의 source-water(amount==8) 가드 추가.
  OreFeatureTest(wgOceanFloor 미초기화)·BiomeFeatureDataTest(GlowLichen/Tree 타입 허용) stale 갱신.
