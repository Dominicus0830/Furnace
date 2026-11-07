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

## 2026-10-27 플레이어 잠·리스폰(침대·리스폰 정박기)
- 잠 상태(`Player.sleepingPos`·`sleepCounter`)는 소유 region 이 쓰고, 밤 건너뛰기(전역 `ServerCore.tickSleep`)와 연결 스레드(자는 동안 이동 패킷 무시)가 volatile 로 읽는다. 밤을 건너뛰면 깨우기는 각 플레이어 region 으로 넘겨 **다음 region 틱**에 일어난다(바닐라는 같은 틱).
- 바닐라 `LivingEntity.stopSleeping` 은 침대가 **남아 있을 때만** 일어설 자리로 옮긴다. 침대가 부서져 깨면 자리 그대로(침대 위 0.6875).
- `PlayerList.respawn` 의 정박기 deplete 소리는 `BlockPos.containing(리스폰 자리)` 칸을 본다 — 설 자리는 정박기 칸이 아니므로 사실상 안 난다(바닐라 그대로 이식).
- 리스폰 블록이 없으면(`missingRespawnBlock`) 새 플레이어는 리스폰 지점을 **이어받지 않는다**(copyRespawnPosition 생략). 엔드 귀환(`findRespawnPositionAndUseSpawnBlock(false)`)도 정박기 충전을 쓴다.

## 2026-10-27 무거운 회귀 테스트 게이트 정책
- 수십 초 이상 걸리는 fuzz·다중 seed 회귀 테스트는 `@EnabledIfSystemProperty(named = "furnace.slowTests", matches = "true")` 로 게이트하고, 기본 경로에는 같은 증상을 잡는 경량판을 둔다(음성 대조로 경량판이 버그를 잡는지 확인한 뒤에만). 전체판 실행: `./gradlew :furnace-server:test -Dfurnace.slowTests=true`(build.gradle.kts 가 `furnace.*` 를 테스트 JVM 으로 넘긴다).
- 적용: `AquiferAirShortcutFuzzTest`(기본 seed 1 × 청크 20, 전체 1000청크 약 37 s), `StructurePieceStateIsolationTest`(기본 seed 1234 신전 -154,402 + 동쪽 이웃 먼저 — 4→0 을 재현한 순서, 전체 seed 2 × 순서 5종 약 216 s).
- 교훈: 서버 경로 캐시를 상한에서 통째로 `clear()` 하면 가변 구조물 시작점이 장식 도중 새 객체가 된다(`ChunkPipeline.trimDecoStarts` 참고). 캐시 정리는 "다시 만들어도 같은 것"만 버린다.

## 2026-10-27 블록 엔티티(표지판·조각된 책장·생성기·제작기·깔때기 연결)
- 블록 엔티티 데이터 방송: 바닐라 `ChunkHolder.broadcastChanges` 는 블록 갱신을 보낸 칸마다 `getUpdatePacket` 이 있는 블록 엔티티의 `block_entity_data` 를 함께 보낸다. Furnace 는 `BlockEntity.hasUpdatePacket()` 을 켠 종류(표지판·생성기)만 보낸다 — 바닐라에서 갱신 패킷을 내는 나머지 종류(현수막·모닥불·종 등)는 아직 꺼져 있다(가정: 켜면 클라이언트 표시만 달라진다).
- 상태가 같은 `setBlock` 은 방송하지 않는다. 표지판처럼 상태 없이 내용만 바뀌면 `BlockLevel.sendBlockUpdated(pos)` 로 칸을 방송 대상에 넣는다.
- 표지판 클릭 명령은 전역 스레드에서 돈다(바닐라는 상호작용 안에서 바로 실행) — 같은 틱 안의 순서가 다를 수 있다.
- `ChiseledBookShelfBlockEntity` 는 `last_interacted_slot` 이 없으면 0(getInt 기본값)으로 읽는다(새로 만든 것은 -1).
- 퇴비통의 깔때기 용기(WorldlyContainerHolder)는 부를 때마다 새로 만든다. 뼛가루를 빼면 `removeItem` 과 깔때기의 `setChanged` 에서 두 번 비운다(바닐라 그대로 — 두 번째는 상태가 같아 게임 이벤트만).

## 2026-10-27 이동·엔티티 명령(/tp /ride /random /tag /spreadplayers /forceload /tick)
- **선택자가 플레이어 말고 일반 엔티티도 고른다.** 엔티티는 region 이 나눠 갖는 상태라 `EntitySelector.findRiders` 가 모든 region 에서 후보를 모아 합친 future 를 돌려준다. 명령은 `EntityArgument.withEntities/withEntity` 로 받고, 이미 끝났으면 그 자리에서(오류도 그대로 던진다), 아니면 1 을 돌려준 뒤 끝나는 시점에 실행한다. 그 안에서 엔티티를 바꾸는 일은 반드시 그 엔티티의 주인 region 으로 보낸다(`EntityOps.onOwner`, `Teleports.teleport`). `/kill`·`/effect` 도 이제 일반 엔티티를 고른다(`/effect` 는 살아 있는 엔티티만 세고, 효과는 그 엔티티의 주인 region 에서 건다 — 2026-11-04).
- 결과 순서: 플레이어(접속 순) 다음 엔티티 id 순. 바닐라는 레벨의 엔티티 조회 순서라 `@e` 의 임의 순서가 다를 수 있다. 선택자 `name=` 은 일반 엔티티에 커스텀 이름 저장이 없어 맞는 것이 없다.
- 엔티티 `Tags`(HashSet 순서 그대로 저장, 최대 1024). 플레이어는 플레이어 파일의 `Tags`(VITALS_KEYS 에 넣어 없어지면 지운다). 선택자 `tag=` 는 빈 값이면 "태그 없음", `!` 는 반대.
- `/tp`: 다른 차원으로 옮기는 길이 아직 없어 차원이 다르면 실패한다(바닐라는 차원 이동). 플레이어는 포스트 텔레포트 티켓(5틱)을 걸고 탈것에서 내리고 깨운 뒤 `Player.teleportTo(…yaw, pitch)`, 그 밖은 `Entity.teleportSameLevel`. `facing` 은 옮긴 뒤 위치 기준(바닐라 `Mth.atan2` 근사식).
- `/ride`: 대상과 탈것이 같은 region 에 있어야 한다(다르면 generic 실패). `/random`: 시퀀스 상태는 저장하지 않는다(random_sequences.dat 없음, 바닐라는 저장).
- `/spreadplayers`: 알고리즘·난수 순서는 바닐라 이식. 땅 높이 질의는 청크를 올려 그 region 에서 청크 한 장의 256 열을 한꺼번에 구해 캐시한다(별도 가상 스레드에서 기다림). 팀이 없어 `respectTeams` 면 모두 한 팀(위치 하나, 평균 거리는 바닐라도 Infinity).
- `/forceload`: `World.forcedChunks`(`data/chunks.dat`, 바닐라 `Forced` long 배열). 붙잡은 청크는 `ticketed` 로 내려가지 않고 붙잡는 즉시 올라오며, 플레이어 없이도 엔티티·블록 엔티티 틱을 받는다(`Region.blockEntityTicking`). 무작위 틱·스폰·inhabitedTime 은 바닐라 `ServerChunkCache.tickChunks` 가 `anyPlayerCloseEnoughForSpawning` 청크에만 주므로 강제 청크도 플레이어가 128 블록 안에 없으면 없다(처음 구현에서 잘못 줬다가 고침). 바닐라 티켓 레벨 31 이 둘레 8 청크를 블록 틱 레벨로 함께 올리는 것은 없다(그 청크 한 장만).
- `/tick`: **sprint 는 없다** — region 마다 따로 틱해 한 줄기 서버 틱이 없다. rate 는 모든 region 틱 주기(`Regionizer.setTickNanos`)와 전역 틱의 한 번당 틱 수에 걸고, freeze 는 region 이 월드 진행(예약 틱·무작위 틱·스폰·엔티티·블록 엔티티)만 건너뛰고 플레이어 처리는 계속하며, step 은 각 region 이 N 번씩 진행한다. query 의 틱 시간은 region 틱 하나하나의 소요 시간 100개 표본이다.
- **반환값 차이(알려진 한계)**: 일반 엔티티를 고를 수 있는 선택자(`@e` 등)를 쓰는 명령(/tp /tag /ride /spreadplayers)은 region 에서 후보를 모으는 동안 끝나지 않으면 즉시 1 을 돌려주고 결과(성공 개수·실패)는 나중에 메시지로만 온다. 그래서 `/execute store`, 명령 블록 조건부 사슬, 함수가 반환값에 의존하면 바닐라와 다르다(0 개 일치·실패여도 1). 플레이어만 고르는 선택자와 `@s` 는 즉시 끝나 바닐라 값을 낸다. 계획: `execute`·함수 시스템이 생길 때 명령 실행을 `CompletableFuture<Integer>` 로 바꾸거나, 대상이 일반 엔티티를 포함할 때 전역 스레드에서 한 틱 동안 region 스냅샷을 동기로 모으는 경로를 두어 값을 낸다.
- 의도한 차이: `/tp facing` 의 바라보는 쪽 앵커는 늘 FEET(바닐라는 `source.getAnchor()`, `execute anchored` 가 생기면 연결). `/tp` 도착 엔티티·`/spreadplayers` 대상의 위치·회전은 명령 스레드에서 읽는다(소유 스레드가 아님; 읽기뿐). `/tick freeze` 는 플레이어가 탄 탈것도 멈춘다(바닐라 `isEntityFrozen` 은 플레이어와 플레이어가 탄 것을 정지에서 뺀다). `/tick step` 도중 새로 생긴 region 은 그 step 을 받지 않고 정지 상태로 남고, 나뉜 조각은 부모의 남은 걸음을 물려받는다.
- 일반 엔티티를 바꾸는 명령은 도착한 region 이 그 엔티티를 실제로 갖는지 확인하고 아니면 현재 위치로 다시 보낸다(`EntityOps.runOnOwner`, 최대 5번).

## 2026-10-27 피해 유형·/damage 차이
- damage_type 47종은 `registries/damage_types-1.21.1.txt`(tools/vanilla-data/extract_damage_types.py)에서, 성질은 damage_type 태그에서 읽는다. 상수 18개는 이름만 남은 호환용이다.
- 플레이어 lastHurtByMob 은 LivingEntity.baseTick 처럼 100틱 또는 원인 죽음 때 지우고, getKillCredit 은 lastHurtByPlayer(100틱) → lastHurtByMob 순서다. 이걸 안 지우면 오래전 피격이 굶어 죽을 때 `.player` 메시지로 새어 나온다.
- 차이: 투사체가 직접 엔티티일 때 넉백 방향(calculateHorizontalHurtKnockbackDirection)은 위치로 근사한다. 원인 없는 환경 피해의 `knockback(0.4, 0, 0)` 은 /damage 경로(플레이어)에서만 한다.
- 차이: LivingEntity 가 아닌 /damage 대상은 Entity.hurtBy 로 가서 직접 엔티티·위치를 보지 않는다. `.item` 사망 메시지 변형은 아직 없다.
## 2026-10-27 점수판·팀(/scoreboard, /team)
- `scores/` 패키지(Scoreboard·ServerScoreboard·Objective·PlayerTeam·NumberFormat). 서버 하나를 `synchronized` 로 잠그고 쓴다(명령·region 선택자·플레이어 틱이 모두 만짐). 잠금 순서: PlayerVitals → 점수판.
- 저장은 `<월드>/data/scoreboard.dat`(writeLevelData 때). 바닐라 맵은 fastutil 해시 순서라 목록·저장 순서가 다를 수 있다(여기는 삽입 순서).
- 같은 팀에 다시 join 해도 바닐라는 빼고 다시 넣어 참(성공)이다.
- 아직 없음: 처치 기준(totalKillCount·playerKillCount·teamkill.*·killedByTeam.*)은 처치 기록 경로가 없어 갱신 안 함, 이름표/충돌/투명 아군 규칙 적용, 텍스트 score 내용 풀기, 전리품 score 숫자 공급자, 엔티티 제거 시 점수 삭제(entityRemoved 미연결), /trigger.
- (2026-10-27) `/attribute`·`/clone`·`/fillbiome`: 플레이어는 LivingEntity 가 아니라 `Player.attributes()` 가 바닐라 기본 속성 맵을 따로 들고 있다(값은 전투·이동에 아직 안 쓰이고 클라이언트로도 안 감). `/clone`·`/fillbiome` 은 영역 전체가 명령을 받은 region 한 곳에 로드돼 있어야 하고 아니면 `argument.pos.unloaded`(바닐라는 청크를 불러 와 계속함); `/clone from|to <차원>` 은 같은 차원일 때만 동작. `/fillbiome` 재전송(`chunks_biomes` 0x0E)은 청크마다 패킷 하나. `/place`(월드젠 feature 가 살아 있는 레벨이 아니라 `FeatureWorldView` 위에서 돌아 어댑터가 필요)와 `/schedule`(함수 시스템 없음)은 아직 없다.
## 2026-10-27 NBT 경로·/data·/loot·/item
- **명령 SNBT 는 `TagParser`(바닐라 이식)로 읽는다.** `item/Snbt` 는 코덱용으로 더 너그럽다(`[B;1,2]` 허용). 명령 인자(nbt_compound_tag·nbt_tag·nbt_path 필터·블록 상태 NBT·아이템 컴포넌트·loot_table 인라인)는 바닐라처럼 `[B;1b,2b]` 만 받고 `argument.nbt.*` 키로 실패한다.
- NBT 경로 연산은 태그 정체성(같은 compound·list 를 고쳐 씀)에 기댄다. 값 모형에서 배열이 `byte[]/int[]/long[]` 라 그 자리에서 끼울 수 없어, 연산 동안만 `NbtTags.thaw` 로 `NbtArray` 로 바꾸고 저장할 때 `freeze` 한다. `NbtList` 는 바닐라 ListTag 처럼 비면 종류가 풀린다(`addTag/setTag/remove/clear`).
- `/data get` 출력의 compound 키 순서: 바닐라 CompoundTag 는 HashMap 순서. 키로 새 HashMap 을 만들어 그 순서를 따른다(지우기 이력으로 표 크기가 다르면 드물게 다를 수 있음 — 가정).
- 플레이어 `/data get entity` 는 저장 경로의 조각(vitals·인벤토리·엔더 상자·레시피 북·태그·위치)으로 만든 근사 NBT 다. Motion·Fire 같은 엔티티 공통 필드 일부가 없다.
- `/loot`·`/item` 의 엔티티 칸은 플레이어(인벤토리·엔더 상자·커서·제작 칸·장비)와 생물 장비뿐. 말·마을 주민·알레이·상자 보트·광산 수레·아이템 액자·아이템 디스플레이 전용 칸은 없다(칸 없음 취급). 블록 용기는 `ContainerBlockEntity`(캠프파이어 제외); 주크박스는 바닐라에선 용기지만 여기선 아니다.
- loot_table·loot_modifier 인자는 데이터 팩을 실행 때 보므로 없는 id 오류(`argument.resource_or_id.failed_to_parse`)가 바닐라(파싱 때)보다 늦게 난다.
- **차이**: `/loot mine` 은 block_entity 는 넘기지만 this_entity(실행 주체)는 넘기지 않는다. `/loot kill` 의 magic 피해 태그 조회(`is(tag)`)는 늘 false 다(TODO: 피해 종류 태그).
- 정정: 명령 인자 쪽만 `TagParser` 로 옮겼다. `item/Snbt.parse`(데이터 팩 로더·PCodec·아이템 NBT·loot 테스트)는 그대로 옛 파서이고 Snbt 는 쓰기 쪽만 NbtArray 를 받게 바뀌었다. **후속 계획**: 데이터 팩·아이템 NBT 의 SNBT 읽기도 바닐라 TagParser(코덱 경로는 `TagParser.parseTag` + DataResult 메시지)로 옮긴다 — `[B;1,2]` 같은 너그러운 입력이 바닐라에선 거부된다.
- `TagParser` 깊이 제한(의도한 차이): 바닐라 1.21.1 은 제한이 없어 명령 최대 길이 안의 `[`×32000 으로도 StackOverflowError 가 난다(테스트로 확인). 512 를 넘으면 `arguments.nbtpath.too_deep` 으로 실패시킨다.
- storage 의 읽기-수정-쓰기는 `CommandStorage` 잠금 하나로 묶는다(여러 region 스레드의 명령이 같은 id 를 동시에 바꿀 때 갱신 유실 방지).
- **알려진 차이(/execute·/function 선행 과제)**: entity·block 대상 `/data` 는 대상 region 으로 보내므로(`Regionizer.enqueue` 는 늘 inbox) 결과값이 거의 늘 1 이고 실제 값·실패는 메시지로만 온다. `execute store result … run data get entity|block …` 가 1 을 저장하게 된다. storage 는 즉시 끝나고 entity·block 은 나중이라, 한 함수 안에서 `data modify storage … from entity …` 다음 줄의 `data get storage` 가 아직 반영 안 된 값을 읽을 수 있다. 명령이 이미 그 region 스레드에서 돌면 그 자리에서 실행하는 지름길은 이번에 넣지 않았다(지금은 명령이 region 스레드에서 도는 경로가 표지판·명령 블록뿐이고, 소유 확인 API 를 정리해야 함). /execute·/function 작업에서 명령 실행을 future 결과로 바꾸거나 소유 스레드 즉시 실행을 넣어 해결한다.

## 2026-10-27 명령 실행 코어(S0) — 의도한 차이·가정
- **의도한 차이**: 지연 결과가 CommandSyntaxException 이 아닌 예외로 끝나면(`EntityOps.reportFailure`) forked 여부와 상관없이 `command.failed` 메시지를 보내고 실패 콜백 뒤 컨텍스트를 **계속** 돈다. 바닐라는 큐 실행 중 비-CSE 예외면 `performCommand` catch 로 컨텍스트 전체가 중단된다. 동기 경로(명령 본문이 바로 던진 예외)는 바닐라처럼 중단한다. (가정: region 실패는 대상 하나의 문제라 뒤 줄을 살리는 쪽을 택함 — S5 함수 테스트에서 재검토)
- **의도한 차이**: 지연 결과 타임아웃(벽시계 5초) 뒤 늦게 끝난 region 작업의 부작용(블록·엔티티 변경)은 되돌리지 않고 콜백만 버린다(세대 토큰). 뒤 명령이 그 결과를 볼 수 있다.
- **의도한 차이**: 한 컨텍스트가 region 왕복으로 여러 틱에 걸치면 뒤 최상위 명령이 모두 기다린다(head-of-line). `CommandQueue.pendingCount/currentContextTicks` 지표와 100틱 경고로 관측한다.

## 2026-10-27 플레이어 외형 동기(set_equipment·엔티티 데이터 17/18)
- **알려진 차이**: 손 바꾸기(F 키)에서 바닐라는 두 손 변경을 set_equipment 목록에서 빼고 EntityEvent 55(손 바꿈)를 보낸다(LivingEntity.handleHandSwap). Furnace 는 `Player.syncEquipment` 가 두 칸을 set_equipment 로 보낸다. 클라 화면 결과는 같고 손 바꿈 애니메이션 차이만 있다(가정: 1.21.1 클라 기준 미확인).
- 함정: `Player` 는 `LivingEntity` 가 아니라 몹 트래커 경로(`EntityTracker.addPairing`)를 타지 않는다. 플레이어 외형은 `Region.updateVisibility` + `Player.pairingData/pairingEquipment`(소유 스레드가 공개하는 volatile 불변 스냅샷)로 따로 보낸다. 리스폰(새 ServerPlayer)은 `markEntityDataResend` 로 기본값 기준 재전송.

## 2026-10-27 묶음 B 후속 (방패·토끼·포털)
- 방패 막기 넉백(바닐라 btn.hurt): 막으면 blockedByShield(defender.knockback(0.5, defender−attacker), 공격자 쪽) 후 일반 0.4(공격자 반대)가 이어져 몹은 순 0.15 만 밀린다. 플레이어는 완전히 막으면 markHurt 가 안 서서 set_entity_motion 이 0건(안 밀림). 지난 방향 반전 수정은 오진이었다. Player.knockback 은 속도만 쌓고 hurtMarked 가 선 틱에 한 번(Region.sendPendingKnockback) 보낸다.
- 차이(미이식): blockedByShield 공격자별 재정의 — Ravager(50% 기절/포효, 아니면 강한 밀침), Hoglin(새끼 아니면 throwTarget), Zoglin. 지금은 모두 기본 0.5 로 처리한다.
- 토끼 변종: 바이옴을 못 읽으면(ServerCore.biomeAt 이 비소유 region/미로드 청크에서 null) 태그에 안 걸린 것으로 보아 흰/금 토끼가 갈색 계열이 될 수 있다(의도한 차이). 확대 지터가 이웃 region 청크로 넘어가는 경계에서만 생긴다. 다른 region 청크를 안전하게 읽는 경로는 아직 없다.
- 지옥문 Y 한 칸 아래: 서버 계산(PortalShape/NetherPortalBlock/SyncPlayerPosition)은 바닐라와 일치하는 것으로 확인했으나 실접속 현상은 원인 미확정. 후보: acceptPosition 이 충돌 검사 없이 클라 y 수용, findCollisionFreePosition 이 미로드/타 region 청크·같은 틱 setBlock 칸을 보는 방식, PortalForcer.getMotionBlockingHeight 의미(맨 위 블록 y vs 그 위 칸), 순간이동 직후 moves 샘플 경합. 확인법: 도착 y·출구 rect.minCorner.y·첫 클라 이동 패킷 y 를 로그로 대조.

## 2026-10-27 사용자 버그 묶음 C — 확정 못 한 후보와 확인 방법
- region 경계 가설(C1 상호작용 엔티티 조회, C4 `hasChunksAt`, C3 게임 이벤트 리스너): `Regionizer.sectionFor` 는 반경(radius ≥ 1) 안의 구역을 가진 region 끼리 `pendingMerges` 로 묶고, 쉬는 틈에 합친다. 그래서 플레이어 주변의 연속된 로드 청크는 합치기 전 잠깐을 빼면 한 region 이다. 상시 원인으로는 확정하지 못했다. 확인 방법: 실서버에서 말·순찰 위치 청크의 `regionizer.ownerOf` 가 플레이어 쪽 region 과 다른지 로그로 본다.
- C1 시프트 뒤 재탑승: 바닐라도 `removePassenger` 가 승객의 `boardingCooldown` 을 60 으로 둔다(3초 동안 못 탄다). `Player.tickRiding` 은 플레이어 틱마다 돈다. RELEASE_SHIFT_KEY 가 오면 sneaking 이 풀린다.
- C1 상호작용 거리: Furnace 는 중심 거리 < 6 이라 바닐라(눈→AABB, 3+1)보다 넓다. 그래서 탑승이 막히는 원인은 아니다. 바닐라 판정과 맞추는 일은 남아 있다.
- C2 경고 단계: 직접 설치한 비명체는 can_summon=false 라서 바닐라도 경고 없이 비명만 지른다. 평화 난이도, doWardenSpawning=false 일 때도 같다. 확인 방법: 비명체의 can_summon 상태와 플레이어 `warden_spawn_tracker` NBT 를 본다.
- C3: 촉매 경험치(소 1~3, 좀비 5)만큼만 퍼지는 것은 바닐라 그대로다. 돌·심층암 위 퍼짐은 시험으로 확인했다.
- C4: 마을 섹션 거리 2 이내에서는 바닐라도 순찰이 나오지 않는다. 판정은 10~11분마다 한 번, 그중 1/5 만 성공, 낮에만 된다.

## 2026-10-27 /execute E1 (S1) — 구현 메모·차이
- 비동기 수정자: `AsyncRedirectModifier`(`forkAsync`) / `AsyncSingleRedirectModifier`(`redirectAsync`). `BuildContexts.AsyncStage` 가 소스를 순서대로 하나씩 적용하다 미완료 future 에서 큐를 멈추고(suspend) 전역 스레드에서 같은 자리부터 이어 간다. 변환(`withEntity`·위치·회전 읽기)은 `ExecuteCommands.onGlobal` 로 전역에서 돈다.
- 함정: `positioned over` 는 `positioned` 바로 아래 하위 노드다(`positioned <pos>` 뒤에는 못 붙는다). `execute positioned 1 2 3 positioned over …` 로 쓴다. vec3 정수 x·z 는 +0.5.
- 조회성: `positioned over` 는 `regionizer().ownerOf(cx,cz) == null` 이면 region 을 만들지 않고 `argument.pos.unloaded`. 주인이 있어도 청크가 로드돼 있지 않으면(`Region.heightAt` == MIN_VALUE) 같은 오류.
- **알려진 차이**: `execute on leasher` 는 끈 체계가 없어(isLeashed 항상 거짓) 항상 비어 있다. `on target` 은 Mob 만(Interaction 엔티티 없음). `on origin` 은 발사체·효과 구름·송곳니·점화 TNT·벡스·떨어진 아이템.
- `VanillaCommandTreeTest`: 바닐라 덤프 JSON 은 루트로 가는 리다이렉트(`execute run`)에 `redirect` 키가 없다(경로가 비어서). 패킷에는 플래그가 있다 → 비교에서 루트 리다이렉트는 "없음"으로 본다. NOT_YET 에 `/execute` if·unless·store.

## 2026-10-27 /place — 바닐라와 다른 점(Furnace 전용)
- 방식: region 스레드에서 실제 청크 `copy()` 격자에 월드젠 코드를 돌리고, 바뀐 칸만 실제 월드에 `setBlock` 으로 옮긴다(feature 는 flag 3 = Feature.setBlock 기본, template·structure·jigsaw 는 flag 2). 칸 순서는 y→z→x 라 바닐라의 쓰기 순서와 다르다.
- 관측 차이(feature): 바닐라는 feature 마다 자기 flag(나무 19 등)로 ServerLevel 에 직접 쓴다. 여기선 일괄 flag 3 이라 나무 잎처럼 모양 갱신을 일부러 끄는 feature 도 이웃 갱신을 받는다. 중간 상태에서 일어나는 갱신(모래 낙하 순서 등)도 다를 수 있다. FeatureWorldView 에 틱 예약 API 가 없어 `level.scheduleTick`(spring_feature 의 유체 틱 등)은 재현하지 않는다. 다만 옮길 때 `setBlock` 의 onPlace·이웃 갱신으로 유체 틱이 걸릴 수는 있다. 사본의 postProcessMarks 는 버린다(바닐라 LevelChunk 도 승격 때만 처리한다).
- template·structure: 유체 침수 처리와 updateShapeAtEdge 는 사본(실제 월드와 같은 상태) 안에서 한다. 결과 상태는 바닐라와 같고, 옮기기는 바닐라와 같은 flag 2 다.
- 로드 요건이 바닐라보다 엄격하다: 바닐라 template 은 checkLoaded(pos, pos+size), jigsaw 는 원점 청크만 검사하고 나머지는 그 자리에서 불러온다. Furnace 는 회전된 범위, 모든 조각이 닿는 청크까지 한 region 에 로드돼 있어야 한다. 격자 밖·미로드 청크를 읽거나 쓰면(`FeatureWorldView.missingAccess`) 아무것도 옮기지 않고 argument.pos.unloaded 로 끝난다.
- 크기 상한: 격자는 최대 19x19 청크(반경 9)다. 넘으면 미지원 오류로 끝난다. jigsaw 는 최대 거리 128 이라 약 17x17 이다. 사본과 칸 비교 비용은 청크 수×384×256 이고, 361청크면 약 3500만 칸을 region 스레드에서 비교한다(측정 전 추정).
- feature 하위 트리(필드로 이어진 selector 자식 등) 어디에라도 NoopFeature 가 있으면 미지원 오류다(규칙 3).

## 2026-10-27 /execute if|unless E2 (ExecuteConditions)

- `if predicate`: this_entity 는 엔티티 소유 region 에서 스냅샷(`typeSpecific` 만 살아 있는 보기), 평가는 origin 청크 소유 region 에서 한다(biome·block 위치 조건이 그 region 청크만 읽는다). 엔티티와 origin 이 다른 region 이면 엔티티 값은 스냅샷 시점 것. origin 청크에 주인이 없으면 region 을 만들지 않고 전역에서 평가 — 위치 조건은 읽을 곳이 없어 거짓(바닐라는 청크를 불러와 읽는다: **parity 차이**).
- `if loaded`: 바닐라 ENTITY_TICKING + areEntitiesLoaded 를 `Region.isEntityTickingLoaded`(엔티티 로드 완료 + 플레이어 시뮬레이션 거리 안 또는 /forceload)로 옮겼다. 플레이어도 forceload 도 없는 청크는 로드돼 있어도 거짓.
- `if blocks`: 시작 영역 최소 모서리 청크의 region 하나에 세 모서리·두 영역 청크가 모두 있어야 한다. 아니면 `argument.pos.unloaded` — 바닐라는 사이 청크를 불러오므로 **parity 차이**(설계서가 의도한 /clone 과 같은 제약). 블록 엔티티는 종류 + `saveWithoutMetadata`(id·x·y·z 없음) 동등성이고, 같은 내용의 상자 둘은 일치한다.
- `if biome`: 지터로 고른 quart 의 청크가 로드돼 있지 않으면 바닐라 `LevelReader.getNoiseBiome`(dcz.java:37-40)처럼 생성기 바이옴(`NoiseWorldSource.biomeAt`)으로 본다. 생성기가 없는 월드(슈퍼플랫)는 일치 없음. 모르는 바이옴·태그는 데이터 팩이 있으면 파싱 때(`ResourceOrTagKeyArgument.parse(reader, source)`) 위치가 붙은 오류.
- `if items block` 의 컨테이너 집합은 `HopperBlockEntity.containerOf`(ContainerBlockEntity 하위). 바닐라 Container 중 **주크박스**는 Furnace 에서 ContainerBlockEntity 가 아니라 빠져 있다(가정: 바닐라 lectern·campfire 는 Container 가 아니라 제외가 맞다).

## 2026-10-27 /execute E3 (if score, store score·bossbar)

- `store ... score` 는 대상 목록을 redirect 단계에서 한 번 풀고(비동기 선택자는 `redirectAsync`), 콜백은 명령 결과마다 그 목록에 쓴다. 포크마다 콜백 한 번 — 합계 아님. 같은 점수에 store 를 여럿 걸면 앞 store 가 먼저 쓰고 뒤가 최종값(`chain(source.callback, new)`).
- 읽기 전용 기준(health 등. **trigger 는 읽기 전용이 아니다**)에 store 하면 `getOrCreatePlayerScore` 가 점수 항목(0)을 만든 뒤 `set` 이 IllegalStateException — 바닐라와 같다. 콜백 예외는 동기·지연 경로 모두 CommandQueue.drive 의 catch 가 command.failed 로 알리고 컨텍스트 전체를 끝낸다(남은 포크는 안 돈다).
- `if score` 오류 순서: 바닐라는 target → targetObjective → source → sourceObjective. 기준 조회는 선택자 future 를 기다리지 않고 동기로 하되, 이미 실패한 선택자 future 는 다음 기준 조회보다 먼저 돌려준다. **알려진 차이**: 아직 안 끝난 선택자(region 왕복)의 실패는 뒤따르는 기준 오류보다 늦게 나온다(기준 오류가 먼저).
- store bossbar 는 설계서 S3 행(287)의 범위라 이 단계에 넣었다(S4 는 block·entity·storage). 보스바 조회는 redirect 때, 쓰기는 `CustomBossEvent` 의 synchronized setValue/setMax.


## 2026-10-27 함수 시스템(S5) 의 의도한 한계
- 매크로(`$` 줄) 함수는 S7 전까지 **로드 실패로 빠진다**(로그 "Macro line … is not supported yet (매크로는 S7)"). 연쇄 효과: 그 함수를 필수로 참조하는 함수 태그(`#minecraft:tick`·`#minecraft:load` 포함)는 태그 전체가 빠지고(TagLoader 필수 참조 규칙), `function x:y {…}`·`function x:y with …` 를 쓰는 일반 함수도 그 인자 노드가 트리에 없어 파싱 실패로 빠진다.
- tick 함수가 region 왕복으로 길어져 같은 함수가 아직 시작도 못 하고 명령 큐에 기다리고 있으면, 그 틱의 같은 함수 호출은 건너뛴다(함수마다 경고 1회, 리로드 뒤 다시 1회). 바닐라에는 없는 보호라 그만큼 #tick 실행 횟수가 줄 수 있다.
- 함수 파싱은 플러그인 onEnable 뒤(`ServerCore.loadFunctions`, FurnaceMain)에 한다 — 플러그인 명령을 쓰는 함수가 시작 때 빠지지 않게. 컴파일 소스 레벨은 바닐라(null)와 달리 오버월드다(파싱은 레벨을 읽지 않는다).

## 2026-10-27 /execute E4 (store block·entity·storage, if data)

- `store … (block|entity|storage) <path> <type> <scale>`: 대상은 redirect 때 해석(엔티티 없음·블록 엔티티 아님·로드 안 됨은 명령 오류라 run 이 돌지 않는다). 콜백은 결과마다 주인 스레드에서 `getData → path.set → setData` 하고 완료 future 를 돌려주며 `ExecuteCommand.awaitCallback` 이 그 완료까지 컨텍스트를 멈춘다. 캐스팅은 바닐라 순서(`(int)(v*scale)` 절삭, byte 넘침 그대로).
- 콜백 안 `CommandSyntaxException`(플레이어 쓰기 거부 `commands.data.entity.invalid`, 블록 엔티티 사라짐, `l[0]` nothing_found, too_deep)은 바닐라(`amt` store 콜백의 빈 catch)처럼 **조용히** 삼킨다 — 메시지 없음, 저장소는 setData 전이라 그대로.
- **차이**: 블록 Handle 은 블록 엔티티 객체가 아니라 좌표를 붙들고 쓸 때 다시 찾는다(바닐라는 옛 객체에 쓴다). 엔티티 Handle 은 해석 시점의 엔티티를 붙들되, 콜백 때 주인 region 을 못 찾으면(그새 kill 됨) 바닐라가 죽은 엔티티에 조용히 load 하는 것처럼 **쓰기를 건너뛰고 조용히** 끝낸다.
- 콜백의 비-문법 실패(지연 future 가 예외로 끝남)는 동기 콜백 예외와 같은 길이다: `awaitCallback` 이 다시 던져 `CommandQueue.drive` 의 catch 가 `command.failed` 를 보내고 컨텍스트를 끝낸다(남은 포크는 안 돈다, forked 여부 무관 — 바닐라 performCommand catch 와 같다).
- **알려진 위험**: (1) 콜백 future 가 5 초 타임아웃(`ExecutionContext.suspend`)에 걸리면 컨텍스트는 이어 가지만 region 쓰기는 늦게 반영될 수 있다(되돌리지 않음, 설계서 §7 늦은 부작용). (2) `ExecuteCommand` 는 "실행 하나당 콜백 future 하나" 를 전제한다 — `chain` 이 여러 store 를 future 하나로 합성하므로 성립하고, 두 번째 미완료 콜백을 같은 단계에서 suspend 하면 `ExecutionContext.suspend` 가 IllegalStateException 을 낸다. S5 가 포크 결과를 몰아 부르게 바꾸면 다시 확인. (3) `CustomCommandExecutor.WithErrorHandling`(문법 오류 시 `onFailure()`)과 `FallthroughTask`(`returnFailure()`)는 콜백이 돌려준 future 를 버린다 — `store block … run function` 이 실패로 끝날 때 블록 쓰기 완료를 기다리지 않아 다음 줄이 옛 값을 볼 수 있다(S5 의 ExecutionControl 에서 합류시킬 것).

## 2026-10-27 /schedule (S6) 의 의도한 차이
- 예약 함수는 `gameTime+1` 직후에 `CommandQueue` 로 들어간다(clock 락 밖). 큐가 idle 이면 그 자리에서 동기로 끝나 바닐라 순서(gameTime+1 → 예약 함수 → dayTime+1)와 같지만, 같은 틱 앞의 `#tick` 함수나 다른 명령이 region 왕복으로 기다리는 중이면 FIFO 뒤로 밀려 dayTime 증가 뒤나 다음 틱 이후에 돈다. 종료 중(큐 닫힘)에는 이벤트를 꺼내지 않아 level.dat 에 남는다.

## 2026-10-27 병렬 에이전트 작업 교훈 (/execute·함수 라운드)
- 공유 스크래치 디렉터리의 고정 이름 파일(로그·임시 소스)은 병렬 에이전트가 서로 덮어쓴다. 에이전트마다 하위 디렉터리나 고유 접미사(작업 이름)를 쓰고, 남이 만든 로그를 결과 근거로 삼지 않는다.
- gradle 은 동시에 둘 돌리지 않는다는 규칙이 병렬 에이전트에서는 지켜지기 어렵다. 같은 잠금 파일에 `flock` 을 걸어 순서대로 돌린다(예: `flock <공유 잠금 파일> ./gradlew --offline :furnace-server:test …`). 잠금 파일 경로는 모든 에이전트가 같은 것을 쓴다. 정확한 잠금 파일 경로는 가정이다(이 문서에 기록하지 않았다) — 라운드 시작 때 상위 에이전트가 정해 준다.
- 병합된 HEAD 에서 전체 `:furnace-server:test`(skipOracle) 는 `full12.log` 기준 10분 53초로 BUILD SUCCESSFUL 이었다. 단계별 부분 시험만 믿지 말고 병합 뒤 전체를 한 번 돌린다.
- graphify: `pip install graphifyy`(0.9.97) 후 `graphify update .` 가 이 환경에서 통했다(AST 추출 3334 파일, 32885 노드·132457 엣지·492 커뮤니티). 노드가 5000 을 넘어 `graph.html` 은 커뮤니티 집계 보기다. 큰 단계 하나(/execute·함수 S0~S7)를 끝낸 이번에 한 번 갱신했다.

## 2026-10-28 F-05 섹터 할당기·sync-chunk-writes
- `RegionFile` 은 바닐라 RegionBitmap 첫 적합 + "새 자리 데이터 → 헤더 → 옛 해제" 순서다. in-place 덮어쓰기를 되살리지 않는다(크래시 시 청크 손상, `RegionSectorAllocatorTest` 가 잡는다).
- O_DSYNC 에서는 write 호출 수가 곧 동기화 횟수다. 헤더 두 항목(4B×2)을 따로 쓰면 8KB 한 번보다 느리다(측정 963 vs 1,210 청크/초). sync 일 때 헤더는 한 번에 쓴다.
- `sync-chunk-writes=true`(기본) 저장 처리량은 꺼짐의 약 1/17. 끄는 것은 사용자 결정 항목(`docs/user-todo.md`).
## 2026-10-28 /kill 전 엔티티·/tp 차원 간·/tick sprint
- 바닐라 `/tp` 의 다른 레벨 경로(Entity.teleportTo)는 새 엔티티를 만들어 restoreFrom 하므로, TeleportCommand 가 그 뒤에 하는 손질(facing lookAt, y 속도 0, 땅 위, 길 찾기 중단)은 **옛 엔티티**에 걸려 새 엔티티에는 안 보인다. Furnace 도 그대로 따른다(일반 엔티티 차원 간 이동은 속도 유지·facing 무시). 승객은 unRide 로 원래 차원에 남는다(오류 없음).
- `LivingEntity.kill` 은 generic_kill 피해 Float.MAX_VALUE, `Entity.kill` 은 KILLED 제거 + entity_die 게임 이벤트(이전엔 게임 이벤트가 빠져 있었다).
- `/tick sprint` 보고 시간은 바닐라의 틱 작업 시간 합 대신 벽시계 시간이다(설계 `tick-sprint-design-2026-10-28.md`).
## 2026-10-28 엔더 드래곤 재대조
- 바닐라 EndDragonFight.isArenaLoaded 는 안쪽 반복 오타(`z = 8 + cz; z <= 8 + cz`)로 x -8..8, z +8 한 줄만 본다. "고쳐서" 전체를 보면 parity 가 깨진다.
- `TargetingConditions.test` 는 출처가 null 이면 거리·투명 검사를 건너뛴다. 수정 파괴 시 플레이어 찾기는 출처 없이 부른다(거리 무제한).
- 앉은 단계는 화살뿐 아니라 WindCharge 도 불붙이고 무시한다. 상세: `dragon-parity-recheck-2026-10-28.md`.
## 2026-10-28 /locate biome·poi
- biome: `BiomeSource.findClosestBiome3d`(반경 6400, 가로 32, 세로 64)를 `BiomeLocator` 로 옮겼다. 3d 판은 난수를 안 쓴다. 나선은 `BlockPos.spiralAround(동, 남)` 반복자 그대로(마지막 변 순서까지), 세로는 `Mth.outFromOrigin(y, minY+1, minY+height, 64)`. 바이옴은 청크 저장값이 아니라 climate sampler 값(바닐라와 같다 — `/fillbiome` 결과는 안 보인다). 거리는 3차원 `floor(sqrt((float) distSqr))`, 좌표 y 는 절대값. 고정 바이옴(superflat)은 맞으면 출발점.
- poi: `PoiManager.findClosestWithType`(반경 256, ANY). 청크 사각마다 올라온 청크는 주인 region 스레드에서 복사, 주인이 없거나 안 올라온 청크는 poi/ 파일에서 읽는다(`PoiLocator`, locate 작업 스레드).
- 디스크 읽기는 그 청크의 IO 레인(FIFO)에서 해서, 내릴 때 넣은 `savePoiAsync` 가 끝난 뒤 내용을 본다. 묶음 실행 사이 region 합병·분할로 주인이 바뀐 청크는 새 주인에게 다시 보낸다(최대 8회, 넘으면 디스크). 조회는 `submitAtOwnedChunk` 라 region 을 새로 만들지 않는다.
- 바닐라에 없는 상한: biome 120초, poi 30초 벽시계. 넘거나 실행한 플레이어가 나가면 탐색을 멈추고 일반 명령 실패(`command.failed`)로 끝낸다 — not_found 를 내지 않는다.
- 차이(가정 포함): 바닐라는 서버 스레드에서 막고 돈다. 여기서는 작업 스레드라 그 사이 틱이 지나간다. 같은 섹션 안 같은 거리 POI 의 우열은 바닐라도 HashMap 순서라 맞추지 않았다. 실서버 대조(같은 시드 좌표)는 아직 안 했다.
## 2026-10-28 미등록 루트 명령 정리 (/spectate·/debug·/jfr, /publish·/perf 보류)
- 바닐라 `Commands` 생성자: `/publish` 는 `includeIntegrated`(통합 서버) 에서만 등록한다. 전용 서버(DEDICATED) 트리에는 없으므로 이 서버도 등록하지 않는다(commands.json 은 ALL 로 뽑아 있다). `/jfr` 은 `JvmProfiler.isAvailable`(JFR 모듈이 있는 JVM) 일 때만, 공통 명령 뒤 전용 서버 명령 앞에 등록된다.
- `/perf` 는 10 초 성능 표본(틱·CPU·GC 등 MetricsRecorder)을 모아 debug/ 에 zip 보고서를 만든다. 표본 수집 체계가 없어 보류(VanillaCommandTreeTest.ABSENT_ROOTS). 메시지만 내면 거짓 보고가 된다.
- `/spectate`: 카메라는 관전자의 소유 region 이 정한다(`Region.setCamera`). 보정 텔레포트의 회전은 대상이 아니라 관전자 자신의 것이다(틱 동기 때만 대상 회전). 패킷 순서는 보정 텔레포트(player_position) → set_camera(0x52) → 명령 메시지. 대상이 다른 차원이면 바닐라(teleportTo 6인자 첫 줄 setCamera(this))대로 자기 자리 보정 → set_camera(자기 id) → 차원 이동 → set_camera(자기 id) 이고 카메라는 자기 자신으로 끝난다(메시지는 started, 대상을 계속 따라가지 않는다). 같은 카메라로 다시 지정하면 패킷 없이 메시지만. 틱마다 대상 위치로 서버 쪽 위치를 맞추고(패킷 없음), 웅크리기·대상 죽음/사라짐·관전 모드 해제 때 자기 시점으로 돌아간다. 차이: 관전 모드 해제는 바닐라가 그 자리에서, 여기서는 다음 region 틱에 되돌린다. 대상이 다른 region 이면 대상 주인 region 이 뜬 표본(Player.CameraView, 비동기 갱신)으로 따라가고, 40틱 넘게 대상을 못 찾으면(차원을 옮긴 비플레이어 엔티티의 옛 객체 등) 복귀한다. 표본 요청의 라우팅용 좌표 읽기만 비소유(EntityOps.runOnOwner 와 같은 가정).
- 관전자의 왼쪽 클릭(공격 패킷)도 바닐라처럼 그 엔티티로 시점을 옮긴다(`ServerPlayer.attack` 의 관전 분기).
- `/debug start|stop`: 바닐라 1.21.1 은 stop 이 파일을 쓰지 않고 시간·틱 수·초당 틱 메시지만 낸다. 시작 요청은 다음 틱 머리에서 이뤄지고, 시작 틱 전에 stop 하면 0 초·0 틱·NaN 이며 요청이 남는다(바닐라 `bk()` 가 아직 시작 안 한 요청을 지우지 않음).
- `/debug function`: 추적 소스(`CommandSource.withTraceOutput`)는 성공·실패 메시지를 늘 받고(sendCommandFeedback 무시, withSilent 무효) 채팅 대신 파일 `[M]` 줄로 간다. `[E]` 줄은 줄바꿈 없이 끝나 바로 뒤 `[M]` 이 같은 줄에 붙는다 — 바닐라 Tracer 그대로이니 "고치지" 않는다. 추적 파일은 컨텍스트가 끝날 때 닫힌다.
- `/jfr`: 바닐라는 Mojang 설정(.jfc)과 전용 JFR 이벤트를 켜고 멈춘 뒤 jfr-report-*.json 요약을 만든다. 여기는 JDK 기본 `profile` 설정만 쓰고 전용 이벤트·요약이 없다.
## 2026-10-28 묘목·뼛가루 지형 생성 연결(TreeGrower, 감사 H3/3-1/3-2)
- 블록 쪽 훅: `BlockLevel.configuredFeature/placedFeature/biomeFlowerFeature/placeFeature`. 구현은 `world/LiveFeatures` — 그 칸 region 스레드에서 실제 청크 복사본(±1 청크, 처음 닿는 청크만 복사)에 월드젠 feature 를 돌리고 **쓴 칸만 쓴 순서대로** `setBlock(flag 3)` 으로 옮긴다.
- 난수: 레벨 난수(LegacyRandomSource) 48비트 상태·가우스 보관값을 WorldgenRandom 으로 옮겨 돌리고 되돌린다. 같은 LCG 라 소비 순서가 바닐라와 같다. LegacyRandomSource 가 아닌 난수(시험용)만 nextLong 시드로 대신한다.
- TreeGrower 는 바닐라 그대로: 큰 나무 고르기(보조 큰 나무 있을 때만 nextFloat) → 2×2 를 (0,0),(0,-1),(-1,0),(-1,-1) 순으로 찾아 4칸 공기(flag 4) → 실패면 4칸 원래 묘목 → 아니면 hasFlowers(±2,±1) 계산 뒤 nextFloat(항상) 로 보조/꽃 변형. 일반 경로는 자리에 유체 레거시 블록(flag 4) 후 놓고 실패면 묘목으로.
- 미구현(Noop 이 하위에 있는) feature 를 고르면 묘목·블록을 건드리지 않는다(다른 나무로 대신 키우지 않음). 2026-10-28 기준 대상 27개 configured + grass_bonemeal + 8개 바이옴 꽃 feature 는 모두 구현돼 있다(`SaplingBonemealGrowthTest.neededFeaturesAreImplemented`).
- 반영 flag: 쓴 칸마다 flag 를 남긴다. TreeFeature 계열은 바닐라처럼 19(UPDATE_KNOWN_SHAPE), 나머지는 Feature.setBlock 기본 3. 나무 가장자리 모양 갱신은 사본 안 근사(VineSupport) 대신 반영 뒤 실제 updateShape 를 바닐라 면 순서(flag 3&~1)로 부른다 → 가장자리 잎만 예약 틱, 안쪽 잎은 없음(시험 확인).
- 복사: 청크 기둥은 쓰기 시 복사(`ChunkColumn.cowCopy`, 섹션은 처음 쓸 때만 복사). 잔디 뼛가루 1회 할당 측정 9,696,488 B → 741,392 B(`copyOnWriteReducesAllocation`).
- 한계(바닐라와 다름): feature 가 ±1 청크 밖, 다른 region 소유, 미로드 청크에 닿으면 아무것도 옮기지 않는다. 이때 묘목은 그대로지만 feature 가 쓴 레벨 난수는 이미 소비된 상태다(바닐라는 그 청크를 불러 놓고 성공). region 경계·미로드 청크 옆 묘목은 그 조건이 풀릴 때까지 자라지 않는다. 다른 region 쓰기 위임(읽기 스냅샷 + 소유 region 에 쓰기)은 두 region 상태가 한 틱 안에서 원자적이지 않아(그 사이 상대 칸이 바뀔 수 있음) 이번엔 하지 않았다.
- 잔디 뼛가루: grass_bonemeal 이 미구현이면 통째로 안 한다. 꽃 feature 는 바이옴마다 걷기 뒤에야 알 수 있어, 미구현이면 이미 쓴 걷기 난수는 두고 그 시도만 건너뛴다(현재 데이터엔 미구현 꽃 없음). 바이옴 조회(biomeAt)가 다른 region 칸이면 null → 그 시도의 꽃을 건너뛴다.
- 꽃 feature 찾기는 바닐라 PlacedFeature.getFeatures() 펼침(자기 먼저, 선택기·패치·식생 패치·뿌리 하위 순)에서 첫 flower 타입이다.
- 블록 엔티티를 생성 NBT 로 바꿔 넣을 때(addBlockEntity) 옛 것은 setRemoved 한다(바닐라 LevelChunk.setBlockEntity). 전에는 옛 벌집이 틱 목록에 남았다.
- 바닐라 실서버와의 블록 단위 대조는 아직 안 했다(oracle 불가 환경) — 나무 모양은 월드젠 fixture 로 검증된 TreeFeature 를 그대로 쓴다는 가정.
## 2026-10-28 선택자 nbt·level·advancements·predicate
- `predicate=` 는 기존 `ServerCore.commandCondition`·`LootEntity` 를 상속한다. 차이: `EntityPredicate` 에 nbt 조건이 없고, 비생명체는 종류·불·땅·위치만, 생명체도 장비·플래그 일부만 본다. 난수는 ThreadLocalRandom(바닐라 레벨 난수). 참조 재귀는 `LootContext.visited` 가 막는다.
- 플레이어 대상 판정은 명령 스레드에서 한다(기존 선택자와 같다). 발전과제 진행은 `PlayerAdvancements.testProgress` 잠금 안에서, NBT 는 `menuLock` 안에서 읽는다. 위치 조건은 플레이어 region 밖에서 청크를 읽을 수 있다(위험).

## 2026-10-28 발사기·공급기 (감사 H1)
- 구현: `block/dispenser/`(BlockSource·기본/선택 동작·등록표), `DispenserBlockEntity.getRandomSlot`(전리품 먼저 채우고 저수지 추출, 레벨 난수)·`insertItem`, 공급기는 앞 용기에 `HopperBlockEntity.addItem`(소리 없음), 없으면 기본 던지기. 엔티티 쪽은 `entity/DispenserEquip`.
- 동작 구현: 기본 던지기(1000/2000), 빈 발사기 1001, 화살·물부리·분광 화살·알·눈덩이·경험치 병·투척/잔류 물약·폭죽(1004)·화염구(1018)·바람 구슬(1051), 생성 알, 갑옷 거치대, 안장, 말 갑옷, 상자(상자 말 무리), 배 9종×2, 수레 6종, 물·용암·가루눈 양동이, 빈 양동이, 부싯돌, 뼛가루, TNT, 머리·해골, 위더 해골(위더 바탕 BlockPattern 24방향), 깎은 호박(골렘 바탕), 셜커 상자 놓기, 유리병(벌집 꿀·물), 발광석(리스폰 정박기), 가위(벌집·양·버섯소·눈 골렘·보그드), 솔(아르마딜로), 밀랍, 물병(진흙), 갑옷 전반(ArmorItem.dispenseArmor: 몹·거치대·플레이어).
- 보류(기본 던지기): 양탄자 16색(라마 장식 없음). (몹 양동이 6종은 2026-10-30 구현.)
- 차이: OptionalDispenseItemBehavior 의 성공 표시(솔처럼 안 바꾸는 경로가 지난 값을 씀)는 바닐라는 전역, 여기는 스레드별. 갑옷 대상 목록에서 플레이어는 엔티티 뒤에 온다. 아이템 이름(custom_name)·entity_data 를 엔티티에 옮기지 않는다(ItemUse 와 같은 TODO). 보그드 깎기는 버섯 전리품이 없다(기존 TODO).
## 2026-10-28 swing·pick_item / 구조물 스위치·light_update / 게임룰
- swing(0x36) → `Player.swing(hand, false)`: animate(0x03, 주 손 0·보조 손 3)를 보는 사람에게만(본인 제외), 휘두르는 중 절반(getCurrentSwingDuration/2) 전에는 다시 안 보낸다. 서버가 직접 휘두르는 곳은 use_item_on·use_item 결과가 SUCCESS 류일 때(본인 포함). 공격·블록 파괴는 클라이언트가 swing 을 따로 보낸다(서버 호출 없음). 엔티티 상호작용의 shouldSwing 은 interact 가 결과를 돌려주지 않아 아직 없다.
- pick_item(0x20)은 1.21.1 에서 인벤토리 번호만 온다(서바이벌 휠클릭): pickSlot(getSuitableHotbarSlot → 맞바꾸기) 후 set_slot(-2) 두 개 + set_carried_item. 크리에이티브 복제는 클라이언트가 set_creative_mode_slot 으로 한다. 0..35 밖 번호는 끊는다(바닐라는 검사 없이 예외 — 가정).
- generate_features(level.dat)를 모든 차원 StructureEngine 에 연결. 탐험 지도(findNearestMapStructure)도 server.properties 가 아니라 level.dat 값을 본다. `/locate` 는 바닐라처럼 스위치를 보지 않고 예측으로 찾는다(StructureEngine.startOf). 바닐라는 이미 생성된 청크는 저장된 시작점을 보지만 Furnace 는 항상 예측한다(기존 차이).
- light_update(0x2A): 바닐라 ChunkHolder.broadcastChanges 는 바뀐 빛 섹션(setStoredLevel 칸의 ±1 상자가 걸친 섹션)을 블록 변경보다 먼저, `getPlayers(pos, true)` — 그 청크를 받았고(isChunkTracked = 시야 안 && 전송 끝) 둘레 8칸 중 하나라도 받지 않은 가장자리 플레이어에게만 보낸다. 안쪽은 클라이언트가 스스로 계산한다. 아직 청크를 못 받은 구독자는 건너뛴다(보류하지 않는다 — 빛은 청크 패킷에 들어간다). 새 청크의 첫 빛(lightNewChunk)은 그 청크에 변경 표시를 남기지 않는다(이웃 쪽 변경은 남긴다). 이웃 판정을 위해 `Subscription.ready` 는 volatile. **위험**: 청크 본문을 직렬화한 뒤 ready 전에 바뀐 빛은 그 클라이언트에 안 간다(블록 갱신은 보류돼 가므로 클라이언트가 다시 계산한다).
- 게임룰: fireDamage·freezeDamage(Player.isInvulnerableTo), enderPearlsVanishOnDeath(ThrownEnderpearl.tick — UUID 로만 읽은 진주는 같은 레벨에서 주인을 찾는다), spawnRadius(adjustSpawnLocation: 첫 접속·리스폰 블록 없는 리스폰·크레딧 뒤) 구현. 새 플레이어·월드 스폰 리스폰의 회전은 바닐라처럼 0. **차이**: 미리 올리는 반경은 32칸까지(그 밖은 올라온 청크만 — 바닐라는 필요 때 불러온다), 첫 접속은 계산을 5초(FIRST_SPAWN_TIMEOUT_MS)까지만 기다리고 넘으면 흩뿌리지 않은 월드 스폰(바닐라는 메인 스레드에서 기다린다). noCollision 은 블록만 본다(보트·셜커 충돌, 보더 충돌 없음).
- 제외: projectilesCanBreakBlocks(2026-11-03 연결 — 아래 "투사체-블록 반응"), spectatorsGenerateChunks(관전자가 청크를 올리지 않고 남이 올린 청크만 받는 구독 모델이 필요), maxEntityCramming(성능 설계 필요), commandBlockOutput(명령 블록 미구현).
- 2026-10-28 겉날개 비행(ElytraFlight): player_command 8 → region 스레드에서 tryToStartFallFlying(공중·비행 아님·물 밖·부유 없음·내구도 2 이상 남은 겉날개), 실패면 stopFallFlying(플래그 강제 재전송). 매 틱 updateFallFlying(땅·탈것·부유·겉날개 없음이면 해제, 10 틱마다 elytra_glide 게임 이벤트, 20 틱마다 내구도 1) → travel 겉날개 식 → fallFlyTicks. 공유 플래그 0x80·자세 1·크기 0.6×0.6(눈 0.4)를 본인과 추적자에게. 비행 중 FireworkRocketItem.use 는 플레이어에 붙은 로켓(가속 look·0.1+(look·1.5−v)·0.5, 손 위치, 폭발 피해는 붙은 플레이어가 받음). 바닐라 1.21.1 updateFallFlying 은 물·사다리를 보지 않는다(물은 travel 물 분기가 겉날개 분기를 가릴 뿐). **차이**: 플레이어 위치는 클라 권위라 서버 deltaMovement 는 비행 시작 때 마지막 이동 패킷 변위로 시작한다(바닐라는 서버가 내내 굴린 값) — 충돌 피해량이 바닐라와 다를 수 있다. 서버 move 는 블록 충돌만(엔티티 충돌·블록 속도 계수·위치 이동 없음), 용암 분기·aviate_one_cm 통계·주변 플레이어에게 가는 로켓 폭발 피해는 없다. 이동 검증(MAX_STEP 100 블록/패킷, 토큰 400)은 바닐라 300(겉날개) 제곱 기준보다 느슨해 비행을 막지 않는다. 붙은 로켓이 플레이어와 다른 region 에 있으면 위치(volatile)만 따라가고 가속은 빠지며, 다음 틱 handOffEntities 가 플레이어 region 으로 넘긴다. 붙은 대상 폭발 피해는 언제나 플레이어 소유 스레드 작업(runOnOwner)으로 준다. 창조 비행(abilities.flying, 탈것 아님)이면 travel 뒤 해제(Player.travel). 물속 틱은 서버 속도를 이동 패킷 변위로 이어 간다(바닐라는 물 이동 식). NBT FallFlying 로드는 Motion 으로 서버 속도를 시작한다(Furnace 는 플레이어 Motion 을 저장하지 않아 바닐라가 쓴 파일에서만 의미). 리스폰 초기화는 소유 스레드 작업.
- 2026-10-28 (T5 말): `Mob.finalizeSpawn(r, d)` 2인자는 무리 데이터 경로를 건너뛴다. AgeableMob·말(능력치 굴림·색)은 `finalizeSpawnGroup(r, d, null)` 로 불러야 바닐라 `finalizeSpawn(..., null)` 과 같다. `/summon`·알이 2인자를 불러 말 체력이 기본 53 으로 남았었다. 내린 뒤 재탑승 60틱 거부는 바닐라(`removePassenger` → `boardingCooldown = 60`, `canRide`)와 같다.

## 2026-10-28 T8 스컬크 촉매 — 서버 단계별 실증
- 실제 `RegionLevel` 과 `World.tickOnce`(Region.tick 전체, /forceload 로 블록 엔티티 틱 판정 통과)에서 (1) entity_die 발행 (2) 청크 구역 리스너 수신 (3) 커서 추가 (4) 블록 엔티티 틱의 updateCursors (5) 블록 변환이 모두 동작한다(`SculkCatalystRegionTest`, 플레이어 처치 경로 포함). 좀비 전하 5 → 스컬크 5칸, 소 3 → 3칸, 스켈레톤(활) 8 → 8칸, 풀 덮인 땅도 퍼짐.
- 고친 차이: `LivingEntity.shouldDropExperience` 는 바닐라처럼 `!isBaby()`(아기 동물은 전하 없음). 올챙이 false, 호글린 true.
- 서버 쪽에서는 끊긴 단계를 재현하지 못했다. 남은 후보(가정): 몹과 촉매 청크가 서로 다른 region 에 있을 때(디스패처는 다른 region 청크를 보지 않는다), 클라이언트 표시. 확인 방법: 실서버에서 처치 직후 bloom(촉매 윗면 빛) 여부와 경험치 구슬이 떨어지는지 본다 — 구슬이 떨어지면 리스너가 못 받은 것이다.

## 2026-10-30 T6 워든 음파 연속 발동 — Behavior 지속시간 시간 기준
- 원인: `Behavior` 시간 초과는 전역 gameTime(전역 스레드가 올림), 기억 TTL 은 region 브레인 틱으로 줄어 둘이 어긋났다. SonicBoom 은 소리 대기 26틱이 지속 60틱 종료보다 1틱 늦게 풀리게 맞물려 있어, region 틱이 2틱 앞서면 한 충전에 음파가 두 번 나갔다.
- 수정: 시간 초과를 시작 뒤 지난 브레인 틱 수로 잰다. 엔티티가 매 틱 브레인을 돌 때만 바닐라와 같은 값이다.
- 의도한 차이: 엔티티가 틱을 건너뛰는 동안(NoAI 토글, lazy 청크, region 이동 공백) gameTime 만 흐르면 바닐라는 재개 즉시 시간 초과로 멈추지만 Furnace 는 남은 틱만큼 계속한다.
## 2026-10-30 T3 마을 주민 AI — 단계별 실증
- (A) POI: 실제 생성 경로(Anvil+Noise, seed 0 plains 272,944)에서 직업 블록·침대·종이 청크 로드 때 블록에서 복원된다. 구조물 주민은 AssignProfessionWhenSpawned 를 달고 들어온다. 문제없음.
- (B) 브레인: CORE/WORK/MEET/REST/IDLE/PANIC/PRE_RAID/RAID/HIDE 패키지가 이미 바닐라 순서대로 있다. "일을 안 함·직업 없음"의 원인은 이동이었다. 흙길(15/16) 같은 낮은 블록을 못 올라서 경로가 있는데도 멈췄고, 직업 블록 2칸 안으로 못 들어갔다 → T4(Entity.collide maxUpStep) 병합 뒤 600틱 안에 직업을 얻는다.
- (C) 습격자 발광: GLOWING 효과는 붙지만 공유 플래그 6 이 켜지지 않아 클라이언트에 외곽선이 안 보였다. 바닐라 LivingEntity.isCurrentlyGlowing(효과 || 태그)과 updateGlowingStatus 를 넣었다. 차이: 바닐라는 effectsDirty 로 다음 tickEffects 에서 맞추고, 여기서는 효과가 바뀌는 그 자리에서 맞춘다(투명과 같은 방식). 패킷 도착이 한 틱 빠를 수 있다.
- 종 → HIDE: 단위 시험으로 숨기 진입과 15초 뒤 해제를 확인했다. 숨을 집(HOME POI)이 없으면 SetHiddenState 가 끝나지 않아 계속 숨는다 — 바닐라도 같다.
- 시험 서버에서는 플레이어나 /forceload 가 없으면 엔티티가 틱하지 않는다(바닐라 엔티티 틱 레벨과 같음).
## 2026-10-28 피스톤·셜커 상자 엔티티 밀기 (감사 H4)
- `PistonMovingBlockEntity.tick` 이 진행도를 올리기 전에 moveCollidedEntities·moveStuckEntities(꿀)를 바닐라 순서로 부른다. 밀 때 `NOCLIP`(ThreadLocal, region 스레드마다)로 그 방향으로 움직이는 칸의 충돌 모양을 받침만 남긴다. 슬라임은 미는 축 속도를 방향 단위값(±1)으로 바꾼 뒤 민다.
- `Entity.move(MOVER_PISTON)` 은 limitPistonMovement(한 게임 틱 한 축 누적 ±0.51, 1e-5 이하는 안 움직임). getPistonPushReaction=IGNORE 는 `ignoresPistonPush()`(구름·표시·상호작용·마커·불길한 아이템 생성기·마커 갑옷 거치대).
- 셜커 상자: 열리는 동안 뚜껑 띠 상자에 닿은 엔티티를 두께+0.01 만큼(MOVER_SHULKER_BOX). **고친 버그**: `Shulker.progressAabb` 의 contract 가 바닐라와 반대 방향이었다(셜커 엔티티 peek 밀기 상자도 틀렸었다). 셜커 엔티티는 SHULKER_BOX 이동에 순간이동(전에는 PISTON 에 잘못 걸려 있었다 — 피스톤은 셜커를 그냥 민다). 화살은 스스로가 아닌 이동 뒤 shouldFall 이면 떨어지고, 고정 액자는 밀리지 않는다.
- **차이·한계**: (1) 플레이어는 레벨 엔티티가 아니고 위치를 클라이언트가 정해 서버는 밀지 않는다. 클라이언트가 같은 블록 엔티티로 스스로 밀린다. 바닐라도 피스톤 밀기에 따로 위치 보정 패킷을 보내지 않는다(슬라임 발사는 바닐라 서버도 ServerPlayer 를 건너뛴다). 바닐라는 일반 밀기에서 ServerPlayer 의 서버 위치를 옮기므로 다음 이동 패킷 전까지의 서버 쪽 위치만 다르다. (2) 엔티티는 블록 엔티티가 도는 region 레벨에 든 것만 본다. 붙은 region 은 합쳐지므로 다른 region 엔티티가 1칸 안에 있는 경우는 합치기 대기(pendingMerges) 중뿐이고, 그 동안은 밀리지 않는다(다른 스레드의 엔티티를 건드리지 않으며, 늦게 runAt 으로 미는 가짜도 만들지 않는다). (3) 바닐라 VoxelShape.toAabbs 는 상자를 합친 목록, Furnace 는 모양 데이터의 상자 목록 — 복잡한 모양에서 상자 분할이 다르면 깊이가 다를 수 있다(가정: 일반 블록은 같다). (4) 엔더 드래곤 파트는 목록에 없다.
## 2026-10-30 몹 양동이 · 엔더의 눈 던지기 (감사 H7 · 블록 항목)
- **몹 양동이**: `Bucketable`(물고기 4종·아홀로틀·올챙이). 물 양동이로 우클릭 → 소리(fill_fish/axolotl/tadpole) → `saveToBucketTag`(공통: custom_name 컴포넌트 + bucket_entity_data 의 NoAI·Silent·NoGravity·Glowing·Invulnerable(켜진 것만)·Health(늘); 열대어 BucketVariantTag; 아홀로틀 Variant·Age·HuntingCooldown(남은 틱, 있을 때만); 올챙이 Age) → 손 교체(createFilledResult, 중복 방지 없음: 크리에이티브면 물 양동이 유지 + 채운 것은 인벤토리) → filled_bucket → 몹 discard. 쏟기: `ItemUse.bucketUse` 가 물 붓기 성공 뒤 `checkExtraContent`(몹 되살리기 + entity_place). 몹 양동이는 소리만 neutral `empty_fish|axolotl|tadpole` 로 **대체**되고 fluid_place 이벤트가 없다(MobBucketItem.playEmptySound). 네더에선 물이 증발해도 몹은 나온다(바닐라 그대로).
- **난수 순서**: 되살리기는 `EntityType.spawn(BUCKET)` 순서 — 위치 난수 → 방향 `nextFloat`(레벨) → finalizeSpawn(대구·연어·복어·열대어·올챙이는 Mob.finalizeSpawn 레벨 난수 3번(방향 nextFloat 포함 4번) + 열대어 변종 추첨, **아홀로틀은 BUCKET 이면 아무것도 안 굴림**) → 주변 소리(몹 난수) → custom_name → 추가 → loadFromBucketTag → FromBucket=true. `Mob.finalizeSpawnGroup(…, SpawnReason)` 를 새로 두고 Axolotl 이 BUCKET 을 거른다.
- 발사기: 몹 양동이 6종이 물 양동이처럼 붓고(`emptyMobBucketContents`) 몹을 되살린다(전엔 보류=기본 던지기). 열대어 양동이를 먹이로 주면 물 양동이가 남는다(Axolotl.usePlayerItem). 양동이 출신 물고기·아홀로틀은 `requiresCustomPersistence`.
- **엔더의 눈 던지기**: `EnderEyeItem.use` — 시선이 끝없는 틀이면 PASS(useOn 이 채움), 아니면 `#eye_of_ender_located` 를 `ExplorerMaps.Backend.findNearestStructurePos`(= StructureLocator.findNearest, skipKnown=false, 반경 100, 플레이어 칸 기준; 새 메서드로 locate 의 y 까지 돌려준다 — 스트롱홀드는 y=0)로 찾아 있으면 눈을 **몸 가운데 높이**에 띄우고 `signalTo` → projectile_shoot → `used_ender_eye`(수평 거리² 매처 새로 구현) → 소리 `ender_eye.launch` 피치 = lerp(레벨 nextFloat, 0.33, 0.5) → 하나 소모(크리에이티브 제외) → 통계 → 팔 휘두르기 → **SUCCESS**(서버 결과가 SUCCESS 라 handleUseItem 이 한 번 더 휘두른다 — 바닐라와 같이 animate 두 번). 없으면 CONSUME(소모·소리 없음).
- 눈 비행 수정: 바닐라는 서버에서도 입자 호출의 인자(`random.nextDouble()` 두 번)를 평가하므로 **물 밖 틱마다 엔티티 난수 2번** 소모한다(물속은 없음). 틀렸던 `fireImmune` 재정의 제거(바닐라 EyeOfEnder 는 불에 안 면역이 아님). 드롭/깨짐(80틱 초과, `nextInt(5) > 0` 이면 아이템·아니면 levelEvent 2003)은 기존 구현이 바닐라와 같음을 디컴파일로 확인.
- **차이·한계(확인 못 한 것은 가정)**: (1) 플레이어 `startUsingItem`(사용 중 생물 플래그) 없음 — 엔더의 눈 use 에서 바닐라가 거는 플래그 메타데이터 차이. (2) 오버월드가 아닌 차원에서는 조회하지 않는다(가정: 네더·엔드 생성기에는 스트롱홀드 구조물 집합이 걸러져 바닐라도 못 찾음). (3) 지도 지원(`ExplorerMaps.Backend`)은 월드 생성기가 있을 때만 붙는다 — 평지·시험에선 눈이 나가지 않는다. (4) `ENTITY_DATA` 컴포넌트가 붙은 양동이(명령어로 만든 것)의 `updateCustomEntityTag` 덮어쓰기는 없다(spawn egg 와 같은 TODO). (5) 플레이어 소유 스레드 규칙: 발전 과제는 `Player.runOnOwner` 로, 몹 잡기·눈 던지기는 소유 region 스레드(handleInteract·handleUseItem)에서만 한다. (6) `Region.breakerActor` 는 `trigger()`(filled_bucket 위치 트리거)를 아직 덮어쓰지 않아 use_item 경로의 빈 양동이로 뜬 `filled_bucket` 은 여전히 전달되지 않는다(이번 범위 밖, 몹 잡기 경로는 직접 보낸다).
- 시험: `MobBucketPickupTest`(잡기 10), `MobBucketUseTest`(쏟기 9: 데이터·소리·이벤트·난수 소비·네더·크리에이티브·왕복), `EnderEyeUseTest`(7), `EyeOfEnderTest`(8: 첫 틱 점화식·난수 2회/0회·80틱 드롭/깨짐·저장).

## 2026-10-30 엔더의 눈 드롭 pickupDelay
- 바닐라 80틱 드롭은 `new ItemEntity(...)` 만 쓰고 pickupDelay 를 건드리지 않는다(0). `setDefaultPickUpDelay()`(10) 호출을 제거했다. 눈의 `fireImmune` 재정의 제거(바닐라에 없음)와 함께 `EyeOfEnderTest` 로 고정.
## 2026-10-30 생물끼리 밀기·maxEntityCramming (감사 "생물끼리 밀기")
- `LivingEntity.pushEntities`(바닐라 btn.r)를 aiStep 끝에서 부른다. 난수 순서: 후보가 비지 않고 규칙 > 0 이고 크기 > 규칙-1 일 때만 `random.nextInt(4)`, 0 이면 탈것에 안 탄 수를 세어 넘치면 cramming 6 피해, 그다음 후보마다 `후보.push(나)`. 플레이어도 같은 흐름(Region.pushEntities, 난수는 `Player.random()`).
- 후보 수집은 `PushScratch`(레벨당 하나 재사용)가 `EntityStore.forEachInNoCopy`(구역 인덱스, 구역별 임시 목록·람다 할당 없음)로 한다. 팀이 하나도 없으면(`Scoreboard.hasTeams`, volatile) 팀 조회를 건너뛴다. 엔티티는 같은 region(레벨) 것만 읽고 쓰고, 플레이어는 차원 전체를 읽기만 한다(쓰기 없음, 기존 forEachPlayerIn 선례). 관전 중인 플레이어도 바닐라처럼 후보가 있으면 굴림·cramming 판정을 돌고 밀기만 막힌다.
- 후속 과제(성능): 플레이어 후보 수집이 `forEachPlayer` 전체 순회라 O(생물×플레이어)다. region 별 플레이어 공간 인덱스가 생기면 바꾼다.
- 팀 충돌 규칙은 디컴파일 bsw.a 그대로: 내 규칙 never → 후보 없음, 상대 never → 제외, PUSH_OWN_TEAM 이고 같은 팀이면 제외, 그 밖에 PUSH_OTHER_TEAMS 가 끼면 같은 팀일 때만 허용. 이름과 뜻이 거꾸로 보이지만 디컴파일이 그렇다(가정: 확인은 디컴파일만).
- 예외 엔티티를 바닐라에 맞춤: Bat(isPushable=false, pushEntities 비움), ArmorStand(isPushable=false, 거리² 0.2 이하 탈 수 있는 수레만 민다), AbstractHorse(`!isVehicle`).
- **차이·한계**: (1) 플레이어는 레벨 엔티티가 아니라 후보 목록에서 엔티티 뒤에 붙는다(바닐라는 구역 순서). 속도 합산 순서가 달라 부동소수 마지막 자리가 다를 수 있다. (2) 플레이어끼리 미는 쪽 속도는 클라이언트 권위라 버린다(몹 쪽 몫만 적용). 크램밍 수에는 다른 플레이어도 센다. (3) 플레이어 isPushable 의 사다리 판정은 `Player.onClimbable` 필드를 쓴다. (4) `LivingEntity.push` 의 자동 회전 공격(riptide) 중 무시는 넣지 않았다(몹에는 해당 없음, 플레이어 쪽 속도는 어차피 버림). (5) Axolotl 죽은 척 중 isPushable 은 바닐라도 override 가 없어 그대로. (6) 다른 region 에 걸친 엔티티는 합치기 전까지 후보에서 빠진다.
## 2026-10-30 몹 목표 누락 M8~M11 (Cat·Dolphin·Fox·Parrot·Vindicator)
- 추가(바닐라 1.21.1 본문 이식, 난수 호출 순서 그대로): Cat `CatRelaxOnOwnerGoal`(3)·`CatLieOnBedGoal`(5)·`CatSitOnBlockGoal`(7), Dolphin `PlayWithItemsGoal`(8)·`FollowBoatGoal`(8)과 아이템 줍기(`canHoldItem`·`pickUpItem`, `setCanPickUpLoot(true)`), Fox `StrollThroughVillageGoal`(9, 새 기반 `ai/goal/StrollThroughVillageGoal`)·`ClimbOnTopOfPowderSnowGoal`(0, 새 `ai/goal/ClimbOnTopOfPowderSnowGoal`; Rabbit 의 빈 Stub 도 이걸로 교체), Vindicator `VindicatorBreakDoorGoal`(1, 습격 중·보통 이상 난이도).
- **미구현(근사 활성화 안 함)**: Dolphin `DolphinSwimWithPlayerGoal`(2) — Player 에 수영 상태(`Entity.updateSwimming` 규칙)가 없다. Parrot `LandOnOwnersShoulderGoal`(3) — 플레이어 어깨 엔티티 저장·복귀(ShoulderEntityLeft/Right)가 없어, 목표만 넣으면 앵무새가 사라진다. Dolphin `DolphinSwimToTreasureGoal` 은 전부터 구조물을 못 찾는 것으로 처리(`findNearestMapStructure` 연결 필요). Vindicator 'Johnny' 타깃·습격 무기 마법 부여는 그대로 TODO.
- 차이·한계: (1) `CatSitOnBlockGoal` 의 열린 상자 판정은 `OpenersBlockEntity.openCount`(바닐라 `ChestBlockEntity.getOpenCount` 와 같은 값, 상자 블록 엔티티가 openers 계열일 때만). (2) `CatRelaxOnOwnerGoal` 의 끈(leash) 분기는 끈이 없어 생략. 아침 선물은 `giftLoot`(`gameplay/cat_morning_gift`)을 쓰며 주인 수면 카운터 `Player.sleepCounter()` 로 판단한다. (3) `FollowBoatGoal` 의 플레이어 입력은 `Player.inputXxa/inputZza`(탄 동안만 갱신) 이고 진행 방향은 `Dir.fromYRot(yaw)`(getDirection=getMotionDirection). (4) Dolphin `PlayWithItemsGoal` 은 `ItemEntity.isInWater()` 를 쓰므로 엔티티가 한 번 틱해야 물속 판정이 선다.
- 시험: `CatDolphinFoxGoalsTest`(목표 표·각 목표 동작), `DoorGoalsTest`(Vindicator 3건), `MobGoalTableTest`(Rabbit 표 갱신).
## 2026-10-31 U4 말 재탑승·능력치 대조 / U5 방어 속성 전송
- **U4(a) 재탑승**: 디컴파일 `Entity.removePassenger`(boardingCooldown=60), `baseTick`(틱마다 -1), `canRide`(웅크림 아님 && 쿨다운 ≤ 0), `startRiding`, `AbstractHorse.mobInteract` 와 Furnace(`Entity.mount`/`Player.stopRiding`/`tickRiding`/`AbstractHorse.interact`)를 대조했으나 **차이를 찾지 못했다**. 내린 뒤 60틱(3초)은 바닐라도 못 탄다. 이 동작을 `HorseRemountTest` 로 고정(59틱 거부·60틱 허용·웅크림 거부·웅크려 내리면 웅크림 해제). 실제 접속에서 3초보다 오래 막히면 클라이언트 웅크림 입력(player_input/player_command)이 서버에 남는 경우가 의심되나 재현 못 함(가정).
- **U4(b) 능력치**: `generateMaxHealth`(15+nextInt(8)+nextInt(9)), `generateJumpStrength`, `generateSpeed`, `Horse/Donkey·Mule/Zombie·SkeletonHorse.randomizeAttributes` 의 호출 순서(체력→속도→점프, 좀비·스켈레톤은 점프만, 노새·당나귀는 체력만), `setOffspringAttributes`(체력·점프·속도 순, `createOffspringAttribute` 난수 3회 평균) 모두 디컴파일과 같다. 코드 수정 없음, `HorseRemountTest` 에 순서·범위 시험 추가.
- **U5 방어 속성**: 플레이어의 `attributes()` 는 클라이언트로 전혀 안 보냈고(장비 수정자도 안 달림) 방어력은 인벤토리에서 즉석 계산만 했다. 클라이언트 방어구 아이콘은 `generic.armor` 속성을 읽으므로 안 떴다. `Player.syncArmorAttributes`(syncEquipment 에서 매 틱): 칸이 바뀌면 `LivingEntity.detectEquipmentUpdates` 와 같이 이전 아이템 수정자를 빼고 새 아이템 수정자를 장비 슬롯 그룹 기준으로 달며, 방어/방어 인성 값이 달라졌거나 처음(입장)·리스폰(`markEntityDataResend`)이면 `update_attributes`(armor, armor_toughness)를 본인과 보는 사람에게 보낸다. 내구도만 바뀌면 값이 같아 안 보낸다. 시험 `PlayerArmorAttributeTest`.
- **차이·한계**: (1) 플레이어 맵의 다른 속성(최대 체력·이동 속도 등)은 아직 계산에 안 쓰이므로 보내지 않는다(armor·armor_toughness 만). (2) 새로 보게 된 관찰자에게 주는 페어링 패킷에는 플레이어 속성이 없다(방어 수치는 본인 화면 용도라 영향 없음). (3) 차원 이동(Respawn KEEP_ALL)은 클라이언트가 속성을 이어받는다고 가정하고 다시 보내지 않는다. (4) 송신 순서는 바닐라 sendChanges(엔티티 데이터 뒤 속성)를 따르려 syncLivingFlags 뒤에 둔 syncEquipment 안에서 보낸다. 입장 시점 첫 전송이 Login 직후 틱이라는 점은 바닐라의 첫 sendChanges 와 같다고 가정.

## 2026-10-31 파괴수·좀비 연속 타격 (U2, U6)
- 파괴수(cki): 나뭇잎 파괴(수평 충돌+mobGriefing, 0.2 확장 상자, 부술 게 없으면 점프)·돌진 속도·기절/포효 틱·포효 6 피해는 이미 바닐라와 같았다(시험 `LandMobTest.ravagerSmashesLeavesWhenBlocked`·`ravagerKeepsLeavesWhenMobGriefingOff` 로 고정, 후속 커밋에서 추가). 빠져 있던 것은 방패에 막혔을 때 `blockedByShield`(포효 중 아니면 난수 50% 기절 40틱·소리·이벤트 39·push, 아니면 강한 밀치기)였고 `Shields.blockedByShield` 훅으로 구현했다. 플레이어 방패 막힘의 push 는 관전자 제외, 플레이어 탑승 여부(isVehicle)는 확인하지 않음(가정: 탑승 중 플레이어 없음). 호글린·조글린의 같은 훅(HoglinBase.throwTarget)은 아직 기본 밀치기(미구현).
- 가정: 파괴수가 나뭇잎을 못 부순다는 현상은 코드상 재현되지 않았다. 실제 경로에서 수평 충돌까지 도달하지 못하는 경우(경로 탐색이 나뭇잎 앞에서 멈춤 등)는 접속 시험으로 재확인이 필요하다.
- 좀비 연속 두 번 때리기(U6): 바닐라에도 있는 동작으로 판단, 코드 변경 없음. 근거 `caw`(MeleeAttackGoal): `d()`(start)가 `this.i = 0` 으로 공격 대기를 비우고, `c()`(canContinueToUse)는 followingTargetEvenIfNotSeen=false 인 좀비에서 `!navigation.l()`(길 끝나면 중단)이라 붙어서 길이 끝나면 목표가 끊겼다가 `b()` 쿨다운(20틱 간격 검사) 뒤 재시작해 20틱 안이어도 즉시 한 번 더 때린다. 무적 시간(10틱 초과분만 통과)이 지난 뒤면 두 번 다 들어간다. `RavagerZombieAttackTest.zombieAttackGoalRestartSwingsImmediately` 로 고정.
## 2026-10-31 주민 축하 폭죽 소유자(U1)·주민 문 행동 점검(U3)
- U1 원인: `CelebrateVillagersSurvivedRaid` 가 폭죽을 소유자 없이 만들었다. 바닐라는 `new FireworkRocketEntity(level, owner, x, eyeY, z, stack)`(= 같은 난수 소비 + `setOwner`)라 소유자는 `leftOwner` 가 될 때까지 맞지 않는다. 소유자가 없으면 첫 틱에 주민 몸통에 맞아 터져 주민이 5+2 피해를 받았다. 소유자 받는 생성자를 추가해 넘긴다.
- 같이 고침: `dealExplosionDamage` 가 붙은 대상(attachedToEntity)을 반경 루프에서 빼지 않아 겉날개 로켓이 붙은 플레이어를 두 번 때렸다. 바닐라는 `living != attachedToEntity`.
- U3 점검 결과: `InteractWithDoor`(바닐라 bwf)는 이미 있고 `VillagerGoalPackages` CORE 에 들어 있으며, `Villager` 가 `setCanOpenDoors(true)`, 길찾기는 `WALKABLE_DOOR`(DOOR_WOOD_CLOSED 통과)·철문 차단(DOOR_IRON_CLOSED)이 맞다. 시험으로 열고 지나가기·철문 불통과를 확인했다.
- 바닐라도 그런 점(이식 그대로): 브레인이 매 틱 도는데 다음 노드가 마지막 검사 노드와 다르면 20틱 쿨다운(`decrementAndGet`)이라 문 열기·닫기 검사가 약 20틱마다 일어난다. 그래서 문 앞에서 잠깐 멈췄다 열리고, 닫을 문은 주민이 문 중심에서 3칸 안에 있는 틱에만 닫힌다(그 밖이면 닫지 않고 기억에서만 뺀다). 사용자가 "문을 안 연다/안 닫는다"고 느낀 것이 이 지연·미닫음이면 바닐라와 같은 동작이다(가정: 바닐라 실서버 대조는 못 했다).
- 시험: `FireworkOwnerTest`, `VillagerDoorTest`.
## 2026-10-31 플레이어 이동 서버 검증·블록 파괴 속도·근접 전투 효과 (디컴파일 aru.a(ahg)/cmx.c/cmx.e 대조)
- **이동 검증(A)**: 바닐라 `handleMovePlayer` 를 `PlayerMoveValidator` 로 이식했다. 연결(IO)은 거친 검사(`acceptPosition`: 비유한·패킷당 100 블록·토큰 버킷)만 하고, region 이 `validatesMoves` 면 위치를 발행하지 않고 `Player.pushCheckedMove` 로 검증 대기열에만 넣는다. 소유 region 스레드(`Region.processMoves`)가 패킷마다 `handle` — firstGood(틱 머리 위치)/lastGood(마지막 수락 위치), `receivedMovePacketCount - knownMovePacketCount`(5 초과면 1), `거리² − |deltaMovement|² > 100(겉날개 300) × 패킷 수` → moved too quickly, 서버 쪽 `move(PLAYER)` 뒤 어긋남² > 0.0625(크리에이티브·관전 제외) → moved wrongly, 그리고 `isPlayerCollidingWithBlocks`. 되돌릴 땐 `p.teleportTo(...)`(연결이 clearMoves 로 대기를 풀고 SyncPlayerPosition 을 보낸다)와 `moveRejectHold`(연결이 처리하기 전의 대기 패킷은 버림). 수락하면 region 이 `applyMove` 로 위치·회전·onGround 를 정하고 기존 `vitals.onMove` 소모/낙하 경로로 이어진다. 틱 머리(`PlayerMoveValidator.tick`, 드레인 뒤)는 `resetPosition`·known 카운트 갱신·떠 있는 틱을 센다: `clientIsFloating`(바닐라: 세로 차이 항 `m >= -0.03125` 는 `m = 0` 으로 만든 뒤라 언제나 참 — 그대로)이고 80 틱(중력 속성 기준 `getMaximumFlyingTicks`) 초과면 `multiplayer.disconnect.flying` 킥. allow-flight(`LevelServices.flightAllowed`)·mayfly·부양·겉날개·급류 회전·관전자·발밑 0.55 안 블록이면 면제.
- 좌표 NaN·각도 비유한은 보정이 아니라 `multiplayer.disconnect.invalid_player_movement` 로 끊는다(바닐라 `containsInvalidValues`). 무한대 좌표는 바닐라처럼 클램프하지 않고 거친 검사에서 보정 텔레포트로 거절한다(차이). `move_player_status_only`(0x1D)는 위치·회전 없이 onGround 만 받는다.
- **차이·한계(이동)**: (1) `|deltaMovement|²` 는 서버 쪽 deltaMovement 가 없어 겉날개 비행 중엔 `flyV*`, 아니면 마지막 수락 변위로 근사(가정; 보통 더 큰 허용). (2) 서버 `move` 는 블록 충돌만(엔티티 충돌 보트·셜커, 월드 보더 모양 없음) — `noCollision` 도 블록만. (3) 플레이어에 `stuckSpeedMultiplier`(거미줄·달콤한 열매·가루눈)가 없어 그 안에서는 바닐라보다 관대. (4) 되돌림 뒤 20 틱마다 텔레포트 재전송(`updateAwaitingTeleport`)은 없다(기존 `correctionTpId` 흐름이 확인 전 패킷을 버린다). (5) 서버 텔레포트 직후(`teleported`)는 firstGood 도 새 자리로 맞춘다(바닐라는 lastGood 만 — 확인과 첫 이동 패킷이 같은 틱에 들어오면 우연히 킥 가능). (6) `/tick freeze` 만 `runsNormally=false`(step 중에도 거짓으로 본다). (7) 점프 소모(`jumpFromGround`)는 수락된 뒤 `vitals.onMove` 가 하므로 되돌려진 패킷의 점프는 소모 없음(바닐라는 판정 전에 소모). (8) 거친 검사의 패킷당 100 블록은 남겨 두었다 — 바닐라 임계(≈10 블록)는 region 검증이 담당. (9) region 이 아직 이 플레이어를 돌리기 전(`validatesMoves` 거짓)에는 예전처럼 즉시 발행한다.
- **블록 파괴 속도(B)**: `Player.getDestroySpeed` 전체를 이식(`BlockActor.destroySpeed`): 도구 속도 → (1 초과면) + mining_efficiency 속성(효율 = 레벨²+1, 아이템 수정자 포함) → 성급함/전달체 힘 × (1 + (증폭+1)×0.2f, 큰 증폭) → 채굴 피로 × (0.3, 0.09, 0.0027, 8.1E-4) → × block_break_speed 속성 → 눈이 물에 잠기면 × submerged_mining_speed(0.2, 친수성은 add_multiplied_total 4×레벨) → 공중이면 ÷ 5. 플레이어 속성은 맵이 없어 `PlayerAttributes.value` 가 기본값 + 효과 수정자(양 × (증폭+1)) + 주 손/갑옷 `attribute_modifiers` + 인챈트 속성 효과(효율·친수성·휘두르기 날)로 즉석 계산. 서버의 파괴 진행도 검증 경로(`BlockBreaking`: START/STOP/ABORT, 경과 틱+1 곱, 0.7 임계, 지연 파괴)는 이미 바닐라(aqw)와 같아 손대지 않았다.
- **근접 전투(C)**: 공격력을 `attack_damage` 속성(기본 1 + 무기 + 힘 +3×(증폭+1)/나약함 −4×(증폭+1))으로, 쿨다운 지연을 `attack_speed` 속성(성급함 +10%·피로 −10% × (증폭+1), add_multiplied_total)으로, 쓸기 조건의 `getSpeed()` 를 `movement_speed`(0.1f, 속도/느림 효과 반영)로, 쓸기 비율을 `sweeping_damage_ratio` 속성으로 바꿨다. 인챈트 추가 피해는 바닐라처럼 `(f + 추가) − f`. 살충: `post_attack` 으로 절지류 대상에게 느림 IV(지속 `lerp(nextFloat, 1.5, 1.5+0.5×(레벨−1))`초 × 20 반올림, 난수: 대상의 `nextInt(1)` → `nextFloat` → `nextFloat`).
- **차이·한계(전투)**: (1) `walkDist − walkDistO` 는 여전히 마지막 수락 변위 × 0.6 근사(바닐라는 틱 안 누적). (2) 철퇴·가시 등은 다른 작업 몫. (3) 살충 느림은 대상이 플레이어면 걸리지 않는다(`sensitive_to_bane_of_arthropods` 는 엔티티 종류 태그라 플레이어는 해당 없음). (4) 쓸기 대상별 `doPostAttackEffects` 는 기존대로 피해 여부와 상관없이 부른다(바닐라 디컴파일도 조건 없음).
- 시험: `PlayerMoveValidatorTest`(임계 손계산·벽·가장자리·비행 킥), `RegionMoveValidationTest`(region 배선), `ConnectionInputTest`(NaN 끊기·0x1D), `BlockBreakingTest`(성급함·피로·속성·검증 경로), `PlayerMeleeTest`(힘/나약함·공격 속도·살충·쓸기 속도).

## 2026-10-31 명령 블록·명령 블록 수레 (작업 5)
- 이식: `BaseCommandBlock`(바닐라 dbx), `CommandBlockEntity`(dqr), `CommandBlock`(dhh: neighborChanged → 예약 틱 → tick → executeChain), `MinecartCommandBlock`, 서버 수신 `set_command_block`(0x30)·`set_command_minecart`(0x31), 편집 화면용 `block_entity_data` 전송. NBT 키는 바닐라 그대로(Command, SuccessCount, CustomName, TrackOutput, LastOutput, UpdateLastExecution, LastExecution, powered, conditionMet, auto).
- **바닐라와 다른 점(의도)**: 바닐라는 명령이 그 자리에서 동기로 돈다. 여기서는 전역 스레드 명령 큐에서 비동기로 돌고, 끝나면 소유 region 으로 돌아와 성공 횟수를 반영한 뒤 체인의 다음 칸을 잇는다. 그래서 (1) 체인은 한 게임 틱 안에 끝나지 않고 몇 틱에 걸칠 수 있다, (2) 실행 중에는 성공 횟수가 0(비교기도 0), (3) `lastExecution` 을 실행 시작 때 기록한다(같은 틱 재실행 방지는 같음), (4) 반복 블록의 재예약은 명령 완료를 기다리지 않고 바로 1틱 뒤로 건다(파이프라인), (5) 실행 도중 블록 엔티티가 지워지면 결과를 버린다.
- 체인이 region 경계를 넘으면 다음 칸이 이 region 소유가 아니라 끊긴다(가정: 바닐라는 로드된 청크면 계속). `set_command_minecart` 는 플레이어가 있는 region 의 수레만 찾는다(바닐라는 레벨 전체).
- 수레 소스 표시 이름은 `Entity.displayName()`(바닐라 getDisplayName 은 호버·삽입 포함, 차이는 채팅 장식뿐 — 가정). 수레 `readAdditionalSaveData` 는 바닐라의 onSyncedDataUpdated 때문에 명령이 비어 있지 않으면 로드 후 SuccessCount 가 0 이 되는 것까지 따랐다(디컴파일 확인; 실서버 대조는 못 함 — 가정).
- 미구현: 명령 블록 편집 화면의 서버 쪽 외 검사 없음, `removeComponentsFromTag`(아이템 복사 때 CustomName 제거) 경로 없음, 명령 블록 수레 우클릭 화면은 서버에서 할 일 없음(클라이언트가 동기화 데이터로 연다).
- 시험: `CommandBlockTest`(손계산: 체인 순서·조건부·상한·재실행 방지·꺼짐·Searge·반복 예약·set_command_block 적용·저장 키).
## 2026-10-31 엔티티 NBT 공백 재확인(A)·블록 엔티티 전리품/설치(B)
- (A) 재확인: 감사 문서가 적은 CustomName·CustomNameVisible·Glowing·HasVisualFire·DeathLootTable·active_effects·AbsorptionAmount 는 HEAD 에서 이미 저장·복원된다(`EntityNbtRoundTripTest` 가 기존 보장). Entity.saveWithoutId/load, LivingEntity, Mob 을 디컴파일(bsr/btn/btp)과 키 단위로 대조해 실제로 남은 차이만 고쳤다.
- 고침 1: `Team` — 바닐라는 LivingEntity 읽을 때만 쓰고(저장 안 함, 점수판이 저장) 이름의 팀에 `uuid.toString()` 으로 넣는다. 없는 팀은 경고만. 구현 완료.
- 고침 2: `Invisible` — 바닐라 Entity 는 읽지도 쓰지도 않고 갑옷 거치대·아이템 액자(+발광)만 항상 기록·읽는다. 공통 Entity 에서 빼고 두 클래스로 옮겼다(예전엔 모든 엔티티가 켜진 경우 기록해, 투명 소 등이 바닐라와 다른 NBT 였다).
- 이미 같음(점검만): Pos/Motion/Rotation/Air/Fire/UUID/Tags/Passengers(탄 것은 탈것 좌표), Health/HurtTime/HurtByTimestamp/DeathTime/attributes/FallFlying/Sleeping*/Brain, Mob 의 ArmorItems/HandItems/드롭 확률/body_armor/LeftHanded/NoAI/DeathLootTable(+Seed). `leash` 는 키 보존만(다른 워커 담당).
- 바닐라와 다른 점(남김): Mob 로드 시 `DATA_MOB_FLAGS` 를 통째로 덮어 aggressive 비트(4)가 사라진다(로드 직후엔 원래 0 이라 영향 없음, 가정). 잘못된 `DeathLootTable` 문자열 검증 없음.
- (B) 재확인: 블록 엔티티 → 전리품 문맥(`LootParams.BLOCK_ENTITY`), `copy_components`/`copy_name`, 셜커 상자 container 드롭, 폭발·피스톤·유체·플레이어 파괴 경로는 이미 collectComponents 를 넘기고 있었다(`BlockEntityLootTest`). 빠진 두 곳을 구현했다.
  - 크리에이티브 셜커 파괴(`ShulkerBoxBlock.playerWillDestroy`): 내용물이 있으면 collectComponents 를 담은 상자를 칸 중심에 기본 줍기 지연으로 떨군다. 아니면 `unpackLootTable(player)` 자리에 `unpackContainerLoot`(플레이어 문맥은 가정 — null).
  - `block_entity_data` 설치 시 적용(`BlockPlacement.updateCustomBlockEntityTag`): saveCustomOnly + 병합 → 달라졌으면 load + setChanged. `onlyOpCanSetNbt`(스포너·표지판)는 관리자 블록 권한이 있어야 한다. 기본 `BlockEntity.onlyOpCanSetNbt()` 추가.
  - 바닐라 동작 주의: 컨테이너의 `CustomName`/`Items` 를 block_entity_data 로 넣어도 이어지는 `applyComponents`(custom_name·container 컴포넌트)가 덮어쓴다 — 그대로 이식했으므로 이름 붙인 상자는 `custom_name` 컴포넌트로 가야 한다(가정: 디컴파일 BaseContainerBlockEntity 의 applyImplicitComponents 기억 기준, 이 항목은 직접 재확인하지 못함).
  - 한계: 명령 블록·직소·구조 블록 블록 엔티티는 아직 없어 `onlyOpCanSetNbt` 를 달지 않았다(만들 때 true 로).
- 시험: `EntityNbtRoundTripTest`(Team·Invisible·장비/승객 왕복·SNBT 소환→저장), `BlockEntityComponentsTest`(block_entity_data 병합·권한, 크리에이티브 셜커 드롭).
- 정정(후속): 바닐라 `ShulkerBoxBlock.playerWillDestroy` 의 `isEmpty()` 는 내부에서 `unpackLootTable(null)` 을 먼저 한다. 처음 구현은 items 만 보고 비었다고 판단해, 전리품표가 있는 안 열린 셜커 상자를 크리에이티브로 부술 때 드롭이 없었다. 비었는지 보기 전에 `unpackContainerLoot` 를 부르도록 고쳤다(시험 `creativeBreakUnpacksLootTableBeforeEmptyCheck`). 플레이어 문맥 null 은 여전히 가정.
- 정정: `blockEntityDataMergesIntoPlacedEntity` 의 마지막 assertFalse 는 설치로 손 아이템이 소모돼(빈 스택) 컴포넌트가 null 이라 헛통과였다. 따로 든 아이템으로 다시 적용하면 데이터의 `id` 가 saveCustomOnly 에 없어 병합 결과가 늘 달라지므로 true 다 — 바닐라 loadInto 도 같은 비교라는 것은 가정(id 제거 여부 미확인). 기대값을 true 로 고쳤다.
- (명령 블록 후속) 알려진 차이: 바닐라는 명령이 한 게임 틱 안에 완결되지만 여기서는 비동기라 완결 시점이 틱을 넘을 수 있다. 앞 실행이 끝나기 전에 같은 블록이 다시 실행되려 하면 `BUSY` 로 건너뛴다(예약 틱은 유지) — 명령이 1틱보다 오래 걸리는 반복 블록은 바닐라보다 느리게 돈다. 체인 중간 칸이 BUSY 면 체인이 거기서 끊긴다.
- (명령 블록 후속) 스레드 가정: `BlockCommandSink` 의 `entity()`·`displayName()`·`x/y/z/xRot/yRot` 는 전역 스레드(명령 소스 생성·메시지 출력)에서 읽는다. 블록 엔티티는 불변 좌표라 안전하지만, 수레는 소유 region 이 쓰는 위치/이름을 비소유 스레드에서 읽는다(읽기뿐, 한 틱 어긋날 수 있음 — 가정). 실행 플래그(`returned`/`early`/`running`)는 AtomicBoolean 이다.
- (명령 블록 후속) `CommandBlockEntity.onlyOpCanSetNbt()` = true(바닐라 dqr.q). 시험: 지연 레벨(`Slow`)로 PENDING 경로·겹침 방지·체인 순서, 거부 사유/플래그/모드 매핑. ConnectionHandler 자체(채널 필요)는 단위 시험하지 못하고 그 안의 판정(`denyReason`, `decodeFlags`, `applyEdit`)을 시험한다.
## 2026-10-31 철퇴(Mace)·밀도·관통·돌풍·가시
- 철퇴 스매시(`MaceSmash`, 디컴파일 `cvb`): 조건 낙하 > 1.5 && 겉날개 아님. 보너스 = 기본(≤3: 4f, ≤8: 12+2(f-3), 그 위 22+f-8) + 밀도 `0.5F+0.5F(L-1)` × f. 치명타 배율 앞에서 더한다(`Player.attack` 순서). 맞춘 뒤(setLastHurtMob 다음, 후처리 앞) hurtEnemy: 충격 맥락 설정·속도 Y=0.01 패킷·소리(지상 대상 낙하>5 heavy)·레벨 이벤트 2013(750)·충격파(대상 기준 3.5 이내, 힘 `(3.5-거리)*0.7F*(낙하>5?2:1)*(1-넉백저항)`, y 0.7F). 후처리 뒤 postHurtEnemy 가 낙하 거리를 지운다.
- 낙하 피해 면제: `PlayerVitals` 에 현재 충격 맥락(위치·무시 플래그·유예 40틱, NBT `ignore_fall_damage_from_current_explosion` 등 바닐라 키) 추가. 폭발이 플레이어를 맞히면 `onExplosionHit(source)`(원인이 윈드 차지일 때만 면제). 이 때문에 윈드 차지 폭발도 이제 바닐라처럼 낙하 면제를 켠다.
- 관통: `CombatRules.getDamageAfterAbsorb(.., weapon)` 이 armor_effectiveness(-0.15F + -0.15F(L-1))를 더해 0~1 로 자른다. `Entity.Damage` 에 `weapon`(DamageSource.getWeaponItem) 추가, 플레이어 근접·`Mob.doHurtTarget` 이 채운다.
- 돌풍: 낙하 ≥ 1.5 && 비행 아님이면 공격자 위치에서 반경 3.5, 밀림 배수 [1.2,1.75,2.2] / 너머 1.5+0.35(L-1), 원인 없는 윈드 폭발.
- 가시(`EnchantEffects.thorns`): 맞은 쪽 장비 칸(MAINHAND, OFFHAND, FEET, LEGS, CHEST, HEAD, BODY 순, 슬롯 그룹 ANY)마다 레벨 난수 nextFloat < 0.15F+0.15F(L-1) → 때린 쪽 난수 nextFloat*4+1 피해(thorns, 원인=가시 주인) → 그 장비 내구 2(내구성 판정은 레벨 난수). 크리에이티브 주인은 내구 그대로. 플레이어 근접·쓸기·몹 근접에서 호출.
- 미구현(근사로 켜지 않음): 착지 추가 입자(spawnExtraParticlesOnFall)·낙하-폭발 발전 과제, 투사체(화살 등)로 맞았을 때 가시, 몹 무기의 후처리 인챈트(불 속성·돌풍), 몹 무기 modifyDamage, 가시 장비 파괴 통계. 가정: 플레이어 충격파 넉백 저항은 방어구 knockback_resistance 합, 레벨 이벤트 위치는 `floor(y-1e-5)`(getOnPos 근사).
- 물갈퀴·서리 걸음·영혼 가속·호흡·민첩한 웅크리기·물 친화: 미구현. 서버 속성(물 이동 효율 등)·플레이어 속성 시스템이 없어 근사 없이 남긴다.
- 시험: `MaceEnchantTest`.
- 후속: 이동마다 맥락 초기화는 뺐다(착지 판정 안의 reset 만). 레벨 이벤트 2013 은 대상 위치. 충격파 isAlliedTo 없음·방어구 knockback 근사·players() 는 호출 region 한정(가정). 관전자는 onExplosionHit 건너뜀.
## 2026-10-31 가죽끈(leash) 체계 (몹 능력 감사 작업 4-1)

- 근거: 바닐라 `btk`(Leashable), `btp`(Mob), `cjb`(LeashFenceKnotEntity), `cuz`(LeadItem), `diw`(FenceBlock), `afa`(set_entity_link). 바닐라 1.21.1 `Leashable` 은 Mob 과 Boat 둘이 구현한다.
- 구현(`Mob`): 붙잡은 쪽 `leashHolder`(플레이어 또는 엔티티) + 저장에서 읽어 아직 못 이은 `leashPending`(UUID 또는 울타리 Pos). 매 틱 `Entity.baseTick` 끝의 `tickLeash`:
  끈 정보가 남았으면 붙잡은 쪽을 찾아 잇고(UUID 는 같은 region 엔티티 저장소·플레이어, 울타리 좌표는 그 칸 매듭을 찾거나 만듦), 100틱이 지나도 못 찾으면 끈 아이템을 떨구고 지운다.
  몹이나 붙잡은 쪽이 죽었으면 끈을 풀고 끈 아이템을 떨군다. 거리(float) > 10 이면 끊김(`leashTooFarBehaviour`: 풀고 MOVE 플래그 끔), > 6 이면 속도에 `copySign(d²·0.4)` 를 더하고 낙하 거리를 1 로 줄임, 그 이하는 `PathfinderMob` 이 붙잡은 쪽으로 걷는다(패닉 제외).
  `PathfinderMob.checkLeashDistance`: 붙잡은 쪽 칸 둘레 5칸으로 제한. `TamableAnimal`: 앉은 자세면 당기지 않고 10칸 넘으면 끊는다. 끈 정보가 완전히 사라지면 `clearRestriction`.
- 상호작용(`Mob.interactLeash`, `ConnectionHandler` 에서 mobInteract 보다 먼저): 내가 맨 끈이면 풀고(크리에이티브는 아이템 안 떨굼) `ENTITY_INTERACT`; 끈 아이템을 들고 `canHaveALeashAttachedToIt`(Enemy 아님, 매이지 않음)이면 맨다. **끈 아이템 1개는 크리에이티브에서도 줄어든다**(바닐라 `shrink(1)` 그대로).
- 울타리: `FenceBlock.useWithoutItem` 과 `ItemUse` 의 lead 가 `LeashFenceKnotEntity.bindPlayerMobs`(±7칸 박스, 첫 하나 묶을 때 매듭 설치 소리) → `block_attach`. 매듭 우클릭(`interact`): 내 끈을 옮기고, 없으면 매듭을 없앤다(크리에이티브는 매인 것도 풀기). 매듭을 때리면 죽고 깨지는 소리.
- 패킷: `SetEntityLink`(0x59, **int 두 개**, VarInt 아님). 추적 시작(`addPairing`)과 맺기/풀기에 보낸다. id 는 로컬에 `reports/packets.json` 이 없어 디컴파일 `agg` 의 등록 순서(set_entity_data 바로 다음)로 확인했다(가정: 실제 json 대조는 못 함).
- 그 밖: `execute on leasher`(`Mob.getLeashHolder`), `WanderingTraderSpawner` 가 짐 라마를 상인에게 맴, Bee/Llama/TraderLlama/Armadillo 의 `isLeashed` 연결(Mob 의 것을 쓴다). 저장 키 `leash`: 엔티티는 `{UUID:int[4]}`, 매듭은 `int[3]`.
- 스레드: 붙잡은 쪽이 **다른 region 의 비플레이어 엔티티**면 `level.entities().contains` 가 거짓이라 거리 처리를 건너뛰고(위치를 읽지 않는다), 저장에서 읽은 경우 100틱 뒤 끈이 사라진다. 플레이어는 기존 방식대로 다른 region 이어도 위치를 읽는다(읽기만).
- **미구현/다른 점**: (1) Boat 의 Leashable(우클릭 맺기·`leash` 저장)은 없다. 끈 아이템으로 보트를 클릭하면 탑승 쪽으로 간다. (2) 이름표·스폰알이 끈보다 먼저 처리되는 바닐라 순서는 Furnace 에 이름표 상호작용이 없어 해당 없음. (3) Camel/AbstractHorse 의 `checkLeashDistance` 재정의(앉은 낙타 일으키기, 먹는 말 멈추기), Sniffer/Camel 의 isLeashed 조건, Parrot FollowMobGoal 의 isLeashed 검사, Cat 선물 위치(`cfh:474`)는 연결하지 않았다. (4) 다른 차원 이동 시 `dropLeash(true,false)` 연결 안 함(끈 정보만 NBT 로 남는다). (5) 끈을 맨 몹이 죽으면 다음 틱 `tickLeash` 가 풀고 끈을 떨군다(바닐라 구조 그대로, 죽는 몹이 baseTick 을 도는지에 의존).
- 시험: `LeashTest`(거리 8 → vx 0.4 손계산, 11 → 끊김+끈 아이템 1, 5 → 제한 구역 5, 저장/복원, 100틱 만료, 패킷 바이트).

## 2026-10-31 늑대 변종·늑대 갑옷 (몹 능력 감사 작업 4-2)

- 변종: 엔티티 데이터 22(직렬화기 23 = wolf_variant, VarInt = 레지스트리 번호). 번호는 `registries/1.21.1.txt` 의 알파벳 순(ashen 0 … woods 8), 기본 pale(3).
  스폰: 바이옴으로 `Wolf.variantForBiome`(taiga=pale, grove=snowy, old_growth_pine_taiga=black, snowy_taiga=ashen, forest=woods, old_growth_spruce_taiga=chestnut, #is_savanna=spotted, #is_jungle=rusty, #is_badlands=striped, 없으면 pale). 난수 소비 없음.
  같은 무리(`WolfGroupData`)는 첫 늑대의 변종을 나눠 쓴다. 번식: 이전의 빈 `nextBoolean` 이 이제 부모 둘 중 변종 고르기(난수 순서 동일, 호출 1회). NBT `variant` = `minecraft:<이름>`.
  바닐라가 변종 데이터(`wolf_variant/*.json`)를 레지스트리 순서로 훑어 첫 일치를 고르는데, 바이옴 집합이 서로 겹치지 않아 순서는 결과에 영향이 없다.
- 갑옷: 몸통 칸. 소유자가 새끼가 아닌 늑대에게 늑대 갑옷을 들고 우클릭하면 장착(`setBodyArmorItem`: 떨굼 확률 2·장착 소리), 가위(내구도 -1, 귀속 저주는 크리에이티브만)로 벗기면 `unequip_wolf` 소리와 갑옷 떨굼,
  앉은 늑대에게 아르마딜로 비늘로 수리(`(int)(maxDamage*0.125F)`, 아이템 1개는 크리에이티브도 줄어듦). 염료는 소유자만(같은 색이면 곧바로 부모 처리, 앉기 전환 없음 — 바닐라 순서).
  피해: 갑옷이 있고 `#bypasses_wolf_armor` 가 아니면 늑대는 **피해를 전혀 받지 않고** 갑옷이 `ceil(피해)` 만큼 닳는다. 금 단계(0.95/0.69/0.32 이상)가 바뀌면 `item.wolf_armor.crack` 과 비늘 입자 20개. 아픈 소리도 `item.wolf_armor.damage`.
  장비 패킷은 기존 `detectEquipmentUpdates`(BODY 칸 포함)가 보낸다.
- **미구현/다른 점**: 갑옷 방어 속성(11)이 `bypasses_wolf_armor` 피해 경로의 방어 계산에 쓰이는 것과 그때의 `hurtArmor`(몸통 칸 내구도)는 `LivingEntity` 에 `hurtArmor` 가 몹용으로 없어 연결하지 않았다. 갑옷이 부서질 때의 깨짐 사운드·엔티티 이벤트(47+슬롯)는 `hurtEquipped` 가 하는 만큼만. 새끼 늑대 변종(번식)은 바이옴이 아니라 부모 중 하나.
- 시험: `WolfVariantArmorTest`(바이옴 표, NBT, 금 단계 경계 3/4·19/20·43/44, 흡수 5→내구 5, 2.1→3, magic 은 늑대가 맞음).

## 2026-10-31 알레이 음표 블록·주크박스 수신 (몹 능력 감사 작업 4-3)

- 근거: `cgk`(Allay), `cgk.a`(주크박스 리스너), `cgk.b`(진동 사용자), `AllayAi.hearNoteblock`. 알레이는 리스너 둘을 가진다(`Entity.dynamicGameEventListeners()` 새로 추가, `ChunkLevel` 의 등록·이동·해제 4곳이 모두 돈다).
  (1) 진동 수신기: 반경 16, 기억한 음표 블록(`LIKED_NOTEBLOCK_POSITION`)이 없으면 모든 진동을 받고, 있으면 그 칸·그 차원만. `note_block_play` 만 반응해 `hearNoteblock`(없으면 기억+600틱, 같은 칸이면 600틱 갱신). 틱마다 `VibrationSystem.Ticker`. NBT `listener` 저장/복원.
  (2) 주크박스 수신기: 반경 10, `jukebox_play` → 아직 안 추면 그 칸을 기억하고 춤, `jukebox_stop_play` → 기억한 칸(또는 기억 없음)일 때만 멈춤. 기억한 칸은 저장하지 않는다(바닐라도 그렇다).
  틱: 춤추는데 주크박스를 모르거나 10칸 밖(칸 가운데 기준 제곱거리 < 100)이거나 주크박스 블록이 아니면 20틱마다 춤을 멈추고 기억을 지운다. 겁먹으면(`isPanicking`) 춤 멈춤.
- **미구현/다른 점**: 진동 입자 패킷은 공용 `VibrationSystem` 이 아직 보내지 않는다(기존 한계). 알레이 클라이언트 춤 애니메이션 값은 서버에 불필요해 생략.
- 시험: `AllayListenerTest`(리스너 둘과 반경, 재생/정지 이벤트, 이미 춤추는 중 두 번째 주크박스 무시, 음표 블록 기억·필터, 600틱, NBT).

## 2026-10-31 불덩이 되치기 (몹 능력 감사 작업 4-4)

- 근거: `cmx.e`(Player.attack), `cnp.a`(Projectile.deflect), `cnq.c`(AIM_DEFLECT), `cne.b`(onDeflection), `cjx.a/b/g`(Ghast.hurt·isInvulnerableTo).
- **감사 문서와 다른 점(디컴파일이 정답)**: 1.21.1 에서 플레이어가 쳐서 튕길 수 있는 투사체는 태그 `#redirectable_projectile` = `fireball`(가스트 큰 불덩이), `wind_charge`, `breeze_wind_charge` 뿐이다.
  **블레이즈의 작은 불덩이(`small_fireball`)는 이 태그에 없어 튕기지 않는다**(맞을 수도 없다). 그래서 작은 불덩이는 손대지 않았다.
- 구현: 큰 불덩이(`LargeFireball`)가 `isPickable` 이 되어 플레이어 공격 대상이 된다. `Region.attackEntity` 가 피해 전에 `deflectAim`: 시선 단위 벡터가 새 속도, 소유자는 플레이어, `hasImpulse`, 쳐서 튕기면 `accelerationPower = 0.1`(아니면 절반). 피해·허기 소모 없이 `entity.player.attack.nodamage` 소리.
  `AbstractHurtingProjectile.hurt` 는 이제 무적이 아니면 참(바닐라). 가스트: 직접 원인이 큰 불덩이이고 때린 쪽이 플레이어면 무적 검사를 건너뛰고 피해 1000.
- **미구현/다른 점**: 바람 구슬(`wind_charge`, `breeze_wind_charge`)의 튕김(처음 5틱은 튕기지 않음)은 연결하지 않았다. 플레이어의 공격 쿨다운·`isAttackable` 판정, 화살 등 다른 투사체가 불덩이를 맞힐 때의 세부 동작은 `isPickable` 이 참이 된 만큼만 바뀐다(가정: 바닐라와 같다고 보았으나 따로 대조하지 못함). Breeze 의 투사체 튕김(`Breeze.java` TODO)도 아직이다.
- 시험: `FireballDeflectTest`(yaw 0 → (0,0,1), yaw 90 → x=-1, pitch -90 → y=1, 가속 0.1/절반, 플레이어 소유 불덩이에 가스트 사망, 다른 가스트 소유는 생존).

## 2026-10-31 검토 후속: 끈 대상 제한·알레이 청취 태그·region 읽기 (작업 4 후속)

- `canBeLeashed`(디컴파일 `y()`) 재정의를 전수 반영: 박쥐·판다·거북·`WaterAnimal`(false; 돌고래·오징어는 true)·주민·방랑 상인(false), 늑대는 분노 중이면 false, Hoglin/Zoglin 은 Enemy 여도 true. 끈 아이템을 든 주민 우클릭은 `interactLeash` 가 거절해 거래로 간다.
- 알레이 진동 사용자가 `#allay_can_listen` 만 듣는다(걸음·블록 설치가 note_block_play 를 밀어내지 않음). 진동 틱은 바닐라처럼 `aiStep` 서버 쪽으로 옮겼다.
- `tickLeash` 는 다른 region 엔티티를 읽지 않는다: 붙잡은 쪽이 이 region 저장소에 있으면 그때만 생존·거리를 보고, 있다가 없어지면(제거·이탈) 끈을 푼다. 처음부터 다른 region 소속이면 건너뛴다(플레이어 위치 읽기만 예외).
- 늑대 갑옷 장착은 크리에이티브에서도 1개 줄어든다(`split(1)`).
## 2026-10-31 B1 수영·기어가기 자세와 이동 검증 오탐 (검토 지적)
- 문제: 서버가 수영·기어가기 자세를 몰라 `p.boundingBox()`(1.8)로 충돌을 계산해, 클라이언트가 수영/기어가기(0.6)로 높이 1칸 틈을 지나면 `moved wrongly` 로 되돌렸다.
- 구현(디컴파일 cmx.fR=updatePlayerPose, EntityDimensions 대조): `PlayerPose.update` 를 region 이 매 틱 `tickVitals` 뒤에 부른다. `Entity.updateSwimming`(이미 수영 중이면 질주∧물에 닿음∧비탑승 유지, 아니면 질주∧눈이 물속∧물에 닿음∧비탑승으로 시작), 수영 크기(0.6) 상자가 들어갈 때만 갱신, 원하는 자세(겉날개 → 수영 → 급류 회전 → 웅크림(비행 중 제외) → 서기)가 안 들어가면 웅크림(1.5, `forcedCrouch`), 그것도 안 되면 수영 자세(기어가기, `crawling`). `Player.pose()`(엔티티 데이터 6)가 3 을 내고 수영 플래그는 공유 플래그 0x10 으로 방송, `bbHeight`(수영·기어가기·급류 회전 0.6)·`eyeY`(0.4) 반영.
- 검증 쪽 안전장치: 서버 자세는 틱당 한 번 갱신되어 한 틱 늦을 수 있으므로 `PlayerMoveValidator.height` 는 수영·기어가기 자세이거나 질주 중 물에 닿았으면(곧 수영) 0.6 상자로 충돌을 본다(가정: 오탐보다 관대함을 택함).
- 차이·한계: 수영 시작 조건의 "발 위치 블록이 물 FluidState" 는 "몸이 물에 닿음"으로 근사. 서 있을 틈 판정은 블록 충돌만(엔티티 충돌 없음). 관전자/탑승 시 틈 판정 생략은 바닐라와 같다.
- 시험: `PlayerMoveValidatorTest` — 높이 1칸 통로에서 서 있으면 거절, 수영·기어가기·질주+물 접촉은 수락; 천장 아래 기어가기 자세·0.6 키·눈 0.4·수영 비트 없음; 물속 질주 시 수영 플래그·pose 3·비트 0x10 및 해제.

## 2026-10-31 서버 운영 키 연결: RCON·Query·워치독·spawn-npcs·max-world-size·압축 (작업 G-1)
- 근거: 디컴파일(난독화 이름) `avk`(RconThread), `avj`(RconClient), `avi`(QueryThreadGs4), `avd`(응답 버퍼), `ave`(바이트 도우미), `apq`(ServerWatchdog), `apn`(DedicatedServer), `aqu`(ServerLevel), `dwr`(RegionFileVersion).
- **RCON**(`remote/RconServer`·`RconProtocol`·`RconConsole`): 패킷 형식(리틀엔디언 길이·id·종류, NUL 2개)·종류 3 로그인/2 명령/0 응답·인증 실패 id -1(종류 2)·응답 4096 글자 분할(빈 응답도 한 패킷)·알 수 없는 종류 "Unknown request <16진>"·실행 예외 "Error executing: …"는 바닐라와 같다. 접속 수락·클라이언트별 읽기는 데몬 IO 스레드, 명령은 `Commands.performPrefixedCommandAsync` 로 전역 스레드에서 돌고 IO 스레드는 결과만 기다린다(최대 5분). 출력은 평문을 줄바꿈 없이 이어 붙인다(바닐라 `RconConsoleSource` 동일). `broadcast-rcon-to-ops` 는 `CommandSource.shouldInformAdmins` 가 읽는다. 권한 4, 이름 "Rcon".
  - 차이: 바닐라는 소켓 read 한 번 = 패킷 하나라 TCP 가 조각나면 연결을 끊는데, 여기는 길이 필드로 정확히 읽는다. 길이가 10 미만이거나 1456 초과면 바닐라처럼 끊는다. 인증 실패 횟수 제한은 바닐라에도 여기도 없다(무차별 대입 방어 없음 — 가정이 아니라 둘 다 코드에 없음).
  - 비밀번호가 비었거나 포트가 0..65535 밖이면 바닐라처럼 경고하고 켜지 않는다. 바인딩 주소는 `server-ip`(비면 0.0.0.0).
- **Query**(`remote/QueryServer`·`QueryHandler`): 핸드셰이크(종류 9) 응답 `\t<세션 id 4바이트를 문자열로 변환한 것><토큰 10진수>\0`, 토큰은 0..2^24 난수, 상태(종류 0)는 15바이트면 full, 아니면 basic. 바닐라 특이점 그대로: 문자열은 글자의 하위 8비트만 쓴다(`writeBytes` — 한글 MOTD 는 깨진다), 챌린지는 30초마다 통째로 비운다, full 응답은 5초 캐시(세션 id 만 갈아 끼움), 표시 포트는 게임 포트(질의 포트가 아님). 플러그인 목록은 바닐라처럼 빈 문자열(Paper 형식 아님). 수신은 데몬 IO 스레드 하나. 서버 정보는 읽기 전용 스냅숏(`core.players()`).
  - 가정: 실제 질의 클라이언트(mcstatus 등)와의 상호운용은 확인하지 못했다. 바이트 형식은 디컴파일과 단위 시험으로만 대조했다.
- **워치독**(`ServerWatchdog`, max-tick-time): 바닐라는 서버 스레드 하나의 "다음 틱 시각"만 보지만 Furnace 는 틱하는 곳이 여럿이라 Probe 로 나눈다 — 전역 틱(`ServerCore.scheduledTick` 시작 박동이 임계 넘게 멈춤)과 차원마다 Regionizer(지금 틱 중인 region 중 가장 오래 도는 것, 또는 큐 머리가 마감을 넘긴 시간). 임계 초과 시 치명 로그 → 모든 스레드 덤프(`ThreadMXBean`)가 든 크래시 리포트(`crash-reports/`, 멈춘 스레드의 스택이 원인 스택) → `System.exit(1)`, 10초 안에 안 끝나면 `halt(1)`(바닐라 동일). 워치독 스레드는 휘발성 값만 읽고 틱 상태·잠금을 만지지 않는다. 임계 1/4(최대 1초)마다 검사. `max-tick-time <= 0`(-1)이면 켜지 않는다. 기본 60000ms.
  - 차이: 긴 명령(예: 아주 큰 `/fill`)이 전역 스레드를 임계 넘게 잡으면 전역 박동이 끊겨 크래시로 판정된다 — 바닐라도 서버 스레드에서 명령이 길면 같다. `/tick sprint` 중에는 박동이 40ms 예산마다 돌아 영향 없다(가정: 실측 안 함). 실제 크래시·종료 경로는 시험에서 `exit` 훅을 바꿔 끼워 확인했고, 실서버에서 일부러 멈춰 본 적은 없다.
- **spawn-npcs**: `ChunkLevel.tickEntities` 가 `shouldDiscardEntity`(ServerLevel 동일)로 Npc(주민·방랑 상인)를 지운다. 같은 함수가 동물 스폰이 꺼졌을 때 `Animal`·`WaterAnimal` 도 지운다(바닐라 동작 — 기존엔 spawn-animals=false 가 자연 스폰만 막고 이미 있는 동물은 그대로였다). 스폰(NaturalSpawner 등)에서 Npc 를 막는 경로는 없다 — 바닐라도 스폰 단계는 막지 않고 틱에서 지운다(근거: `aqu.i` 호출은 엔티티 틱 한 곳뿐이다).
- **max-world-size**: 월드 보더의 절대 한계(`absoluteMaxSize`)로 연결(`ServerCore` 생성자). 바닐라 `ServerLevel` 도 같은 값을 `setAbsoluteMaxSize` 로 넣는다. `/worldborder` 인자 범위 상수(MAX_SIZE)는 바닐라처럼 고정.
- **region-file-compression**: `Compression.configure` — `deflate`(기본)·`gzip`·`none`·`lz4` 지원, 청크 헤더의 압축 종류 바이트(1/2/3/4)를 쓰고 읽는다. lz4 는 2026-11-03 에 추가(아래 "lz4 지역 파일"). 잘못된 값이면 바닐라처럼 오류를 로그하고 deflate 를 유지한다. 프로세스 전역 하나(바닐라 `RegionFileVersion.configure` 동일).
- **text-filtering-config**: 구현하지 않는다. 바닐라는 원격 채팅 필터 서비스(HTTP)에 연결하는 `ServerTextFilter` 를 만드는 설정인데 Furnace 에는 필터 서비스 연동이 없다. 비어 있지 않으면 기동 때 경고만 한다.
- 시험: `RconProtocolTest`·`RconServerTest`(루프백 소켓 왕복: 로그인·명령·인증 실패 -1·분할·예외·알 수 없는 종류·길이 위반 끊기·연결별 인증)·`RconCoreTest`(IO 스레드에서 전역 스레드 명령, 출력 수집)·`QueryHandlerTest`(바이트 단위: 핸드셰이크·basic·full·캐시·30초 청소·잘못된 챌린지)·`QueryServerTest`(UDP 루프백)·`ServerWatchdogTest`(판정·스레드 경로·크래시 리포트)·`OpsConfigWiringTest`(max-world-size, 워치독 프로브)·`SpawnNpcsRegionTest`·`RegionCompressionConfigTest`.

## 2026-10-31 관측 명령 /furnace threads|regions|profile (작업 G-2)
- `FurnaceAdminCommands`(권한 3, 기동 때 따로 등록 — 바닐라 트리 대조 대상 아님). 전역: 대기 작업 수(`GlobalThread.queueLength`, ThreadPoolExecutor 직접 사용)·전역 틱 시간. 차원별: region 수·대기열·워커 상태·틱 시간 p50/p95/최근 최대/가동 후 최대(`TickTimeRing` 512 표본)·큐 지연 분포(기존 `Regionizer.schedStats`)·region 목록·느린 region 상위 3.
- 소유권: 명령은 전역 스레드에서 돌고 `Regionizer` 자신의 잠금 안에서 값만 복사한다. region 소유 상태(청크·엔티티·플레이어)는 읽지 않는다.
- 한계: 틱 시간은 region 틱 전체 소요이며 구간별 분해는 없다(`-Dfurnace.tickstats=true`). 워커 상태는 Thread.getState 스냅숏. 실서버 부하 중 출력은 확인하지 못했다(가정: 정상).
- 시험: `FurnaceAdminCommandsTest`, `RegionizerObservabilityTest`, `GlobalThreadQueueTest`.
## 2026-10-31 발전 과제 트리거 연결·엔티티 조건·보상 (작업 E)

- 근거: 디컴파일 `an`(CriteriaTriggers)의 각 필드 호출(`an.X.a(`)과 각 `*Trigger` 클래스, `bv`(EntityPredicate)·`di`(effects)·`dj`(movement)·`dz`(slots)·`dk`(nbt), `dhr`(Crafter), `dro`(SculkCatalyst), `cnl`(FishingHook), `btm`(LightningBolt), `cnd`(AbstractArrow), `aqv`(ServerPlayer tick/reset/effect 훅), `coi`(Raid), `btn`(LivingEntity.hurt).
- 호출 지점 목록과 한계는 `advancement-triggers-2026-10-28.md` 의 "2026-10-31 추가 연결"에 있다. 56종 모두 호출 지점이 생겼다(근사·미완 남음: effects_changed source 등).
- **함정 1 — trigger 이름 매핑**: 코드 TODO 의 `RAID_OMEN`·`RAID_WIN` 은 실제 트리거 이름이 아니다. 습격 징조 흡수 때는 `voluntary_exile`, 습격 승리 때는 `hero_of_the_village`(둘 다 플레이어 조건만 본다).
- **함정 2 — 값 순서**: `item_durability_changed` 의 delta 는 `지금 피해 - 새 피해`(깎이면 음수), durability 는 `최대 - 새 피해`. `player_hurt_entity` 의 dealt 는 방패로 막기 전 피해(`LivingEntity.hurt` 맨 앞 `f = amount`), taken 은 막은 뒤(막으면 0).
- **함정 3 — 발동 시점**: `using_item` 은 쓰는 동안 매 틱(`ServerPlayer.updateUsingItem`), `levitation` 도 매 틱, `slide_down_block` 은 20틱마다, `bee_nest_destroyed` 의 벌 수는 벌을 내보낸 **뒤** 남은 수, `player_generates_container_loot` 는 전리품을 만들기 **전**, `summoned_entity`(위더)는 엔티티를 넣기 **전**, (골렘)은 넣은 **뒤**.
- `Projectile.onHitBlock` 이 이제 맞은 블록의 `BlockBehavior.onProjectileHit` 을 부른다(이전엔 빈 함수). 지금 오버라이드는 `TargetBlock` 뿐이지만, 같은 TODO 를 가진 블록들(초·모닥불·TNT·장식 항아리 …)을 이어 붙일 자리다. `AbstractArrow.onHitBlock` 은 바닐라처럼 `super` 를 먼저 부른다.
- EntityPredicate 의 effects·nbt·vehicle·passenger·targeted_entity·stepping_on·movement_affected_by·movement·team·slots·periodic_tick 지원(미지원이라 조건 전체가 거짓이던 문제). `LootEntity` 에 기본 메서드가 늘었고, `entity/LootEntities` 가 살아 있는 엔티티 값을 돌려준다. `LivingEntity.lootEntity()` 와 `ServerCore.lootEntity(Player)` 는 이를 쓴다(몹의 장비 칸이 전리품 조건에 보이게 된 부수 효과 — 전엔 늘 빈 칸).
- 스레드: 모든 호출은 `Player.triggerAdvancement`(= `runOnOwner`) 안에서 플레이어 주인 스레드가 평가한다. 다른 region 엔티티 값은 호출 쪽에서 `KillCredit.lootOf` 로 떠서 넘긴다. NBT 복사는 `EntityPredicate.nbtUsed`(nbt 조건이 읽힌 적 있음)일 때만 — 실제 바닐라 발전 과제에는 nbt 조건이 없어 평소엔 복사 비용이 없다.
- 보상: 전리품이 인벤토리로 들어간다(이전엔 경고만). 함수는 전역 스레드에서 부여하면 즉시, region 스레드에서 부여하면 다음 전역 틱(함수 관리자 전역 전용이라 같은 틱 보장 불가 — 가정: 한 틱 차이는 관측 영향이 작다).
- **가정(확인 못 함)**: 플레이어 `getKnownMovement` 와 서버가 아는 변위의 차이, 효과를 건 순간 대신 틱 끝에 시작 위치를 잡는 levitation 의 위치 오차, `player_interacted_with_entity` 의 SUCCESS/CONSUME_PARTIAL 구분, 갑옷 거치대·끈 매듭 상호작용 경로.

## 2026-10-31 시련 생성기 블록 엔티티 (작업 C-1)

- 근거: `dsc`(TrialSpawnerBlockEntity), `dsf`(TrialSpawner), `dsg`(Config), `dsh`(Data), `dsi`(State·전이), `dse`(PlayerDetector), `doo`(블록), `btb.a`(장비 표), `btu`(OminousItemSpawner).
  구현 `block/entity/TrialSpawner{,Config,Data,State}`·`TrialSpawnerBlockEntity`. 월드젠이 놓는 NBT(`normal_config`·`ominous_config`: spawn_potentials·total_mobs 등, 불길한 쪽은 일반 위에 병합)를 그대로 읽는다.
- **구조물 템플릿 NBT 에는 `id` 가 없다**(trial_spawner 14곳, 금고는 있음). 바닐라는 블록이 만든 블록 엔티티에 `loadWithComponents` 하므로, `ChunkColumn.promote` 가 id 없는 생성 NBT 를 그 칸 블록의 `newBlockEntity` 에 읽힌다.
- 시선 검사는 `ClipContext.Block.VISUAL`(디컴 `dcf.a.c`)이다 — 충돌 모양이 아니라 `getVisualShape`. `BlockBehavior.getVisualShape`(기본 = 충돌 모양)와 `Clip.clipVisual` 신설. 오버라이드: 유리류(`TransparentBlock`)·`IronBarsBlock`·가루눈 = 빈 모양,
  울타리·눈 층 = 외곽 모양, 진흙·영혼 모래 = 꽉 찬 블록(디컴 `djz/dol/dlt/diw/dni/dkx/dnl`).
- 난수 순서: INACTIVE 판정마다 [다음 데이터가 없고 후보가 있으면 nextInt(합)] → 몹 생성 x(nextDouble×2)·y(nextInt(3))·z(nextDouble×2) → 회전 nextFloat → finalizeSpawn → 생성 후 다음 후보 nextInt(합).
  불길한 ACTIVE: 아이템 추첨 nextInt(합) → nextBoolean → (2명 이상이면 nextInt) → 위치 nextInt(4) → OminousItemSpawner nextInt(61) → 소리 pitch nextFloat×2.
- 상태: 일반 감지는 (pos.asLong + 게임시각) % 20 == 0 일 때만. **COOLDOWN 중에는 일반 플레이어를 등록하지 않는다**(불길해질 때만) — 바닐라 그대로.
- **가정**: DFU `lenientOptionalFieldOf(이름, 기본값)` 인코더는 기본값과 같은 필드를 쓰지 않는다. 그래서 저장 NBT 가 짧고, 불길한 구성의 값이 기본값과 같으면(예: simultaneous_mobs 2.0) 저장에서 빠져 **다시 읽을 때 일반 구성 값을 물려받는다**
  (바닐라도 같은 왕복 불일치를 낳는다고 본다 — 시험 `loadMergesOminousOverNormal…` 에 기록).
- **미구현/다른 점**: ① 클라이언트 몫(입자·주변 소리·표시용 회전 몹)은 생략, 레벨 이벤트 3011~3014·3019~3021 과 서버 소리만 보낸다. ② 표시용 몹은 "만들 수 있는가"만 본다(`Entities.create`; 안 되는 id 는 매 틱 다시 시도하지 않음).
  ③ `current_mobs` 는 이 region 저장소에서만 찾는다 — 다른 region 으로 넘어간 몹은 추적에서 빠진다(스레드 소유권 규칙; 47칸 안이면 보통 같은 region). ④ 탈것 승객(`Passengers`)은 BaseSpawner 와 같이 미이식.
  ⑤ 장비 표는 `Mob.equipmentSlotFor`(이름 기준)로 Equipable 을 대신한다. ⑥ 감지기 SHEEP 변종(`dse.c`)은 바닐라 데이터가 쓰지 않아 생략. ⑦ 평화로움·`doMobSpawning` 검사는 레벨 값 그대로.
  ⑧ 불길한 아이템 표 평가에 쓰는 origin 은 없음(바닐라 EMPTY 문맥).
- **소유권 처리**: 감지(읽기)는 기존 관행대로 접속자 전체를 훑지만, 나쁜 징조→시련 징조 변환(효과 쓰기)은 `Player.runOnOwner` 로 그 플레이어의 소유 스레드에 넘긴다(다른 region 이어도 안전; 같은 틱에 바로 보이지 않을 수 있음).
  `current_mobs` 추적은 이 region 저장소만 보므로 region 을 넘어간 몹은 추적에서 빠져 다음 몹이 더 빨리 나올 수 있다. 표시용 몹 캐시는 `displayEntityCreated`/`failedDisplayId`(만들 수 없는 id 는 같은 id 로 재시도 안 함 — 바닐라는 매번 시도).
  코덱 엄격도 차이: 바닐라 루트의 `target_cooldown_length`·`required_player_range` 는 lenient 가 아닌 `optionalFieldOf` 라 틀린 값이면 TrialSpawner 전체 파싱이 실패(기존 값 유지)하지만, 여기서는 필드별로 기본값에 떨어뜨린다.
- 추가: `Mob.dropPreservedEquipment`·`setPersistenceRequired`, `ChunkLevel.lootItems`(origin·this_entity·luck, 시드/레벨 난수/표 난수), 스폰 알을 시련 생성기에 쓰면 다음 데이터 종류만 바뀜(`ItemUse`).
- 시험: `TrialSpawnerBlockEntityTest`(구성 병합·기본값 생략·lenient, 상태 전이 전부, 시드 고정 스폰 위치 손계산, 시선(돌/유리), 불길한 전환·효과 변환, 보상 배출 주기, 불길한 아이템 생성기 난수 순서·위치, 템플릿 NBT 승격).
## 2026-10-31 금고 블록 엔티티 (작업 C-2)

- 근거: `dsk`(VaultBlockEntity + Server/Client), `dsm`(VaultConfig), `dsn`(VaultServerData), `dso`(VaultSharedData), `dsp`(VaultState·전이), `dou`(VaultBlock), `dse`(PlayerDetector).
  구현 `block/entity/Vault{Config,ServerData,SharedData,State,BlockEntity}`·`VaultBlock`. 구조물 템플릿 NBT(`config: {key_item: {id, count}, loot_table}`, id 있음)를 그대로 읽는다.
- 틱(서버): 20틱마다 활성 금고 표시 아이템을 전리품 표에서 새로 뽑는다(표를 **레벨 난수**로 평가한 뒤 `nextInt(개수)` 하나 — 표 난수가 아니다). 갱신 시각이 되면 상태 한 칸:
  inactive/active = 범위(활성 4.0 / 비활성 4.5, 블록 거리² &lt; 범위², 창작 모드 포함·관전자 제외, **이미 보상 받은 플레이어 제외**) 안에 사람이 있으면 active, 20틱 쉼. unlocking → 20틱 뒤 ejecting. ejecting = 20틱마다 목록 **끝**에서 하나를
  (위 1.2칸, 속도 2) 내놓고, 비면 총 배출 수를 0 으로 하고 비활성 범위로 확인. 소리 높이 = 0.8 + 0.4 × 진행도, 진행도 = 총 1 이면 1, 아니면 1 − (남은 − 1)/(총 − 1).
- 열쇠: 활성일 때만. 같은 아이템·컴포넌트이고 개수가 열쇠 이상이어야 한다(아니면 실패 소리 — 15틱에 한 번). 이미 보상을 받은 플레이어는 거절 소리. 통과하면 전리품 표를(this_entity = 플레이어, 행운 = 속성 luck, origin = 칸 가운데, 표의 난수) 평가해
  비어 있지 않을 때만 통계 used·열쇠 소비(창작 제외)·unlocking(14틱 쉼)·보상 받은 플레이어 기록(128명 넘으면 가장 오래된 사람부터 밀림)·연결 플레이어 갱신. 이벤트 3015/3016(활성/비활성, 불길하면 데이터 1)·3017(배출).
- 저장: DFU 규칙(가정, 시련 생성기 노트 참고)대로 `config`·`shared_data`·`server_data` 는 늘 쓰고, 안의 기본값 필드는 생략. `key_item` 은 비어 있지 않으면 늘 쓴다.
  **바닐라 quirk**: `server_data` 를 읽을 때 `total_ejections_needed` 를 옮기지 않는다(copyFrom 이 빠뜨림) — 배출 도중 재시작하면 진행도가 `남은 수`가 되어 소리 높이가 올라간다. 그대로 포팅.
- **미구현/다른 점**: 클라이언트 입자·주변 소리·표시 아이템 회전 생략. 금고 회전·거울(`rotate/mirror`) 훅은 아직 이 서버 블록 체계에 없다(템플릿 배치는 표 변환으로 처리). 감지기는 시련 생성기와 같은 `PlayerDetector`(시선 없음).
  연결 플레이어는 UUID 만 들고 다른 region 플레이어도 위치만 읽는다(스케줄러 규칙의 읽기 전용 예외 — 기존 스컬크·시련 생성기와 같다).
- 시험: `VaultBlockEntityTest`(템플릿 NBT·기본값 생략·범위 검증 거부, server/shared 왕복, 128명 제한, 진행도 손계산, 범위 경계 16/20.25, 표시 아이템 순환 난수, 열쇠 성공/실패/거절/창작/빈 보상, 배출 주기·높이, 배출 후 상태).

## 2026-10-31 불길한 병·시련 징조 (작업 C-3)

- 근거: `cvh`(OminousBottleItem), `brs`(BadOmen), `dsh.a/transformBadOmenIntoTrialOmen`, `bsb`(MobEffects).
- 시련/불길한 시련 열쇠(`trial_key`, `ominous_trial_key`)는 바닐라에서 평범한 아이템(`new Item`)이다 — 쓰임은 금고 블록의 `useItemOn`(C-2)뿐. 일반 `BlockPlacement.useItemOn` 경로가 `item_used_on_block` 발동까지 해 준다.
- `trial_omen` 효과는 자체 틱 동작이 없다(`MobEffect` 기본). 효과를 보는 쪽은 시련 생성기(C-1): 일반 생성기 감지에서 시련 징조가 있으면 불길해지고(소모 안 함), 나쁜 징조만 있으면 시련 징조로 바꾼다 —
  지속 18000 × (증폭+1), 증폭 0, 나쁜 징조 제거. 이벤트 3020(플레이어 눈 칸, 데이터 0)과 생성기(데이터 1).
- 불길한 병(`ominous_bottle`)을 마실 수 있게 했다(`Consuming`): 32틱 마시기, 끝나면 consume_item 발동·통계 used·`item.ominous_bottle.dispose`·나쁜 징조 120000틱(증폭 = `ominous_bottle_amplifier` 컴포넌트, 기본 0; 입자 없음·아이콘 있음).
  병은 하나 줄고 **빈 병이 남지 않는다**(`ItemStack.consume` 만). 창작 모드는 소비하지 않는다. 나쁜 징조는 마을 안에서 기존 `Raids.tickOmens` 가 습격 징조로 바꾼다.
- **미구현/다른 점**: 불길한 병 레시피/거래/약탈자 대장 드롭은 전리품 표·기존 코드가 맡는다(이 작업 범위 밖). 마시는 소리는 블록 중심(blockPosition+0.5), 음원 분류는 플레이어("players").
- 시험: `ConsumingTest` 불길한 병 2건(증폭 3 + 120000틱 + 효과 플래그 + 병 감소, 컴포넌트 없음 = 증폭 0 + 창작 비소비), 시련 징조 전환은 `TrialSpawnerBlockEntityTest`.
## 2026-10-31 B 플레이어 속성 맵 정식화와 속성 위 인챈트
- 구조: `PlayerAttributes.modifiers(속성, 장비칸[], 효과, 달리기, 위치효과)` 가 수정자 목록의 유일한 원천이다. `PlayerAttributes.value` 는 이것으로 직접 합성하고, `Player.syncAttributes`(region 스레드, 매 틱 `syncEquipment` 안)는 같은 목록으로 살아 있는 `AttributeMap` 을 맞춘다(이 동기가 단 id 만 기억해 `/attribute` 로 단 수정자는 건드리지 않음). 입력(장비·효과 종류/증폭·달리기·위치 효과 버전)이 안 바뀌면 재계산하지 않는다.
- 전송(`update_attributes` 0x75): 입장·리스폰 첫 동기에 syncable 전부, 이후 `AttributeMap.takeDirty()` 만. 패킷은 값 복사본(`UpdateAttributes.snapshot`)이라 전송 스레드와 경합이 없다. 새로 보게 된 시청자에게는 엔티티 데이터 다음·장비 앞에 syncable 스냅샷(`Player.pairingAttributes`). 가정: 바닐라 `getSyncableAttributes` 의 속성 순서는 HashMap 순서(불특정)라 등록 순서를 쓴다(클라이언트는 순서에 무관).
- 달리기: `LivingEntity.setSprinting` 의 `minecraft:sprinting`(0.3F, add_multiplied_total)을 달리기 플래그에서 맞춘다 → 이동 속도 속성이 클라이언트로 간다. 인챈트 수정자 id 는 바닐라처럼 `minecraft:enchantment.<이름>/<칸>`(예전 코드는 `/칸` 접미사가 없었다).
- 최대 체력: `PlayerVitals.maxHealth` = 속성 값(기본값 + 체력 강화 효과 + 장비·`/attribute` 수정자). 효과 수정자는 효과에서 바로 합성(걸자마자 반영, 바닐라와 동일), 기본값·그 밖의 수정자는 `syncAttributes` 가 넘긴다(최대 1틱 늦음). 최대 체력이 줄면 체력을 자른다(`onAttributeUpdated`). 저장은 안 한다: 플레이어 속성 맵(`/attribute base set` 등)은 `playerdata` 에 저장·복원되지 않는다(미구현).
- 인챈트(`EnchantAttributes` 표, 데이터 `data/minecraft/enchantment/*.json`): 물갈퀴(feet, water_movement_efficiency, linear(0.33333334,+0.33333334) — float 연산, 3레벨은 범위 1.0 으로 잘림), 호흡(head, oxygen_bonus), 민첩한 웅크리기(legs, sneaking_speed 0.15/레벨), 친수성(head, submerged_mining_speed ×(1+4)), 효율·쓸어 담기(기존). 칸이 맞지 않으면 켜지지 않는다.
- 호흡: `LivingEntity.decreaseAirSupply` — 산소 보너스 b>0 이면 `nextDouble() >= 1/(b+1)` 일 때 공기를 안 깎고, b≤0 이면 난수 없이 1 깎는다(단락 평가). 난수는 플레이어 난수(`Entity.random` 은 바닐라도 비결정이라 관측 불가).
- 영혼 가속·서리 걸음(`PlayerLocationEffects`, `Enchantment.runLocationChangedEffects`): 블록 칸이 바뀔 때(baseTick)와 발 장비가 바뀔 때(비활성화 후 재실행)만 돈다. 영혼 블록 판정은 `getOnPos(0.500001F)`(`PlayerProbe.onPos`, 지지 블록 기준)이 `#soul_speed_blocks`. 영혼 가속: ① movement_speed +0.0405F+0.0105F×(L-1) ② movement_efficiency +1.0 ③ 난수 0.04F(레벨 난수, 조건 순서상 항상 먼저 굴림) 통과 && 지면 && 영혼 블록이면 장비 내구 1(`hurtAndBreak`: 내구성·크리에이티브 처리 포함). 서리 걸음: 지면이면 발 아래 반경 `(int)clamp(3+(L-1),0,16)` 원판(x 안쪽·z 바깥 순서)의 칸 중 위가 `#air`, 블록이 물이고 유체가 원천, 엔티티가 막지 않으면 `frosted_ice[age=0]`(setBlockAndUpdate + block_place 게임 이벤트).
- 서리 얼음 녹기: `FrostedIceBlock.tick` 의 TODO(빛)를 풀었다 — `(nextInt(3)==0 || 이웃<4) && getMaxLocalRawBrightness > 11-age-1 && slightlyMelt` 후 이웃 얼음 연쇄(Direction 순서). 빛 엔진이 없는 청크(`col.light()==null`)에서는 밝기가 15 로 보여 항상 녹을 수 있다(가정: 빛 데이터가 있는 청크는 바닐라와 같음).
- 미구현/차이: 영혼 가속의 tick 효과(영혼 입자·`particle.soul_escape` 소리), 서리 걸음의 `burn_from_stepping`(마그마 블록) 피해 면역(플레이어의 마그마 블록 피해 자체가 아직 없음), 얼어붙음(가루눈) 이동 속도 수정자 `minecraft:powder_snow`, 플레이어 속성 저장·복원, 물갈퀴/영혼 가속이 실제 움직임에 미치는 효과(클라이언트 예측이 속성을 읽는다 — 서버는 속성만 보낸다).  `FrostedIceBlock.tick` 의 녹는 경로는 전용 단위 시험이 없다(전체 시험에서 깨지지 않음만 확인).
- 시험: `PlayerAttributeSyncTest`(입장 전체 전송·변경분만·선로 바이트·스냅샷·체력 강화·최대 체력 자르기·수동 수정자 보존·리스폰 재전송), `PlayerEnchantAttributeTest`(인챈트별 값·칸 제한·호흡 난수 재현·영혼 가속 활성/유지/해제·비행·내구 소모 확률·서리 걸음 25칸 원판).
- 후속(B 검토): 서리 걸음은 `level.getBlockState`/`setBlock` 이 region 경계 밖 청크(chunkAt null)에서 실패하면 그 칸을 건너뛴다 — 반경 3~4 원판이 region 경계를 넘으면 경계 쪽 칸이 얼지 않아 바닐라와 얼음 모양이 다를 수 있다(차이). `isUnobstructed`(unobstructed 조건)는 플레이어만 보고 몹·보트 등 blocksBuilding 엔티티는 보지 않는다(`ChunkLevel.isUnobstructed` 한계) — 몹이 서 있는 칸에도 얼음이 생긴다. 시험: 영혼 가속 내구 소모는 레벨 난수를 시드 고정해 같은 난수열 재현값과 정확히 비교, `FrostedIceBlock` 녹는 경로는 시드 고정 단위 시험(age 0→3→물)을 추가했다.
## 2026-10-31 수영·기어가기 자세 후속 정리
- 급류 회전(autoSpinAttack) 중 눈 높이 0.4(SPIN_ATTACK 0.6/0.4). 줍기·캠프파이어·포털·플레이어 드롭·Traveler.of(Player)·물속 눈 판정·ChunkLevel 겹침 검사·경험구슬 추적·PlayerActor/LookAtPlayerGoal 눈 높이를 bbHeight()/eyeY() 로 통일.
- 키가 float(1.8F) 기준으로 바뀐 곳은 이전 double 1.8 과 1e-7 수준으로 다르다(바닐라는 float 차원).
- 남은 것(가정: 영향 작음): ConnectionHandler 의 상호작용 거리 검사(p.y()+1.62 하드코딩 4곳)는 자세를 반영하지 않는다.
## 2026-11-01 작업 D: 고고학(솔질)과 아이템 사용 효과
- 솔질 블록 엔티티(`BrushableBlockEntity`)·솔(`Brushing`)을 디컴파일 `dql`/`csu` 대로 이식: 10틱 쿨다운, 마지막 솔질 40틱 뒤 4틱마다 2씩 되돌림(`BrushableBlock.tick`→`checkReset`), 10번째에 아이템(`nextInt(21)+10` 개, 면 옆 칸 가운데 아래, 속도 0)과 3008 이벤트 뒤 turns_into. 저장: LootTable(+Seed) 또는 item, hit_direction 은 갱신 태그에만. 솔은 useOn 으로 200틱 사용 시작, 5번째 틱마다 솔질, 시선이 블록을 벗어나면 놓는다.
- 서버도 파티클 인자 평가로 레벨 난수를 쓴다(`nextInt(6)+7` 번 × `nextDouble` 2개). 보이지 않는 블록 목록(공기·유체·표지판·깃발·머리·장벽·빛·구조 빈 칸·움직이는 피스톤·엔드 차원문/관문·장식 항아리·거품 기둥)은 디컴파일 클래스 계층에서 뽑은 것이다(**가정**: 엔티티 블록 중 목록에 없는 것은 MODEL/ENTITYBLOCK).
- 시선은 현재 회전을 쓴다(바닐라는 `getViewVector(0)` = xRotO/yRotO — 틱 시작 값과 같다고 **가정**). 크리에이티브 범위는 속성값 + 0.5 로 근사(Player 속성 맵이 아직 모디파이어를 안 쓴다). 행운은 Player 속성 맵 값(행운 효과 미반영).
- `player_generates_container_loot` 발전 과제 트리거는 상자와 마찬가지로 아직 안 건다.
- 의심스러운 스튜(효과를 음식 전에 적용), 불길한 병(음식 컴포넌트를 쓰지 않고 소리 뒤 bad_omen 120000틱·증폭 컴포넌트, 허기 무관), 합창 열매(먹은 뒤 16번 시도, 플레이어 난수 순서 x→y→z, 크리에이티브도, 재사용 20틱), 염소 뿔(악기 컴포넌트 또는 첫 번째, 140틱 사용·재사용, 소리 range/16 음량), 이름표(Mob 은 mobInteract·끈 맺기보다 먼저, 내가 맨 끈 풀기보다는 나중, 갑옷 거치대는 action 0), 책(edit_book 0x14 저장·서명, open_book 0x32, 패킷 id 는 GameProtocols 등록 순서로 확인), 가마솥 씻기(가죽 계열·늑대 갑옷 / 깃발 한 겹 / 색 셜커 상자).
- 차이·한계: 합창 열매는 플레이어를 옮기는 일이 연결 스레드로 넘어가므로 몸이 들어맞는지를 후보 위치로 옮긴 상자로 본다(바닐라는 옮겼다 되돌림). 여우가 합창 열매를 먹는 경로는 없다. 글자 걸러내기 없음(raw 만). 책 쪽 1024자/제목 32자 초과 시 조용히 무시(바닐라는 담았다가 저장·전송에서 실패), edit_book 처리 중 런타임 예외는 연결을 끊지 않고 로그만 남기고 무시. 서명된 책을 펼칠 때 선택자·점수 컴포넌트 해석(`resolveBookComponents`)과 resolved 갱신은 미구현. 먹기 공통의 burp 소리·used 통계는 기존대로 없음(불길한 병·염소 뿔만 used 통계를 올린다).

## 2026-11-01 검토 후속: 워치독 오탐(B1)·RCON/Query 보안 보강 (작업 G-3)
- **B1 워치독 오탐**: `globalBeat` 초기값이 ServerCore 생성 시각이라 플러그인 로드·스폰 청크 생성·함수 로드가 `max-tick-time` 보다 오래 걸리면 첫 전역 틱 전에 "정지"로 판정돼 크래시했다. 수정: (1) `globalBeat` 초기값 0 = 첫 전역 틱 전, 이때 프로브는 0 을 돌려준다. (2) `ServerWatchdog.check` 가 멈춘 시간을 "감시 시작(생성) 이후 경과"로 상한한다 — region 프로브(큐 머리 지연 등)에도 같은 보호. 시험: 가짜 시계(`ServerWatchdogTest.longStartupBeforeWatchdogStartsDoesNotTrip`)와 첫 틱 전 프로브 0(`OpsConfigWiringTest`).
- **RCON 보강(바닐라에 없음 — 바닐라와 달라지는 점)**: 인증 실패마다 1초 지연, 연결당 5회 실패 시 끊기, IP 당 60초 창 10회 실패 시 그 IP 의 새 연결을 즉시 끊기, 동시 연결 상한 16(초과분은 즉시 끊김), 인증 전 소켓 읽기 30초 제한(인증 뒤 무제한), 비밀번호 비교는 `MessageDigest.isEqual`(UTF-8 바이트), 스레드 이름은 `RCON Client #n`(IP 제거), 모든 주소(0.0.0.0/::)로 열리면 "평문 프로토콜, 방화벽/loopback 권장" 경고. 바닐라는 이 중 아무것도 없다. 정상 사용(올바른 비밀번호 한 번)에는 영향 없음. 한계: IP 제한은 프록시 뒤 공용 IP 를 한꺼번에 막을 수 있고, 메모리 내 맵이라 재시작하면 초기화된다.
- **Query 보강**: 챌린지 맵 상한 1024(가득 차면 새 주소의 핸드셰이크를 무시, 30초 청소로 비워짐). 바닐라는 무제한. `hostIp` 는 기동 때 한 번만 계산(바닐라는 생성 시 한 번 — 동일).
- **lz4 경고**: (2026-11-03 해소) lz4 읽기·쓰기를 구현했다.
- 시험: `RconServerTest`(연결당 실패 끊기·IP 차단·스레드 이름·연결 상한), `QueryHandlerTest.challengeMapIsCapped`.
- **알려진 한계(RCON 보강)**: IP 차단은 NAT·loopback 뒤의 정당한 관리자까지 60초 막을 수 있다. idle 연결 16개가 상한을 채우면 관리자 접속을 방해할 수 있다(후속: IP당 동시 연결 상한). 실패 IP 맵 청소는 크기가 4096 을 넘을 때만 한다. 실패 카운트는 응답을 쓰기 전에 올린다(재접속 경합 방지).

## 2026-11-02 월드젠 잔여: nether_fossil · void_start_platform · bonus_chest (작업 4)
- **nether_fossil**: `NetherFossilStructure`(디컴파일 `ema` + `elz`) 이식. 난수 순서: `nextInt(16)`(x) → `nextInt(16)`(z) → 높이 uniform(`absolute 32` ~ `below_top 2` = 32..125, `nextInt(94)`) → 위에서 아래로 base column 탐색(위 칸 공기 && 아래 칸 영혼 모래 또는 윗면 단단) → y ≤ 해수면(32)이면 시작점 없음. 조각 생성은 바이옴 검사 뒤: `Rotation.getRandom`(`nextInt(4)`)이 템플릿 선택(`nextInt(14)`)보다 먼저. 위치는 단단한 아래 칸 좌표. 조각은 postProcess 때 쓰기 상자를 템플릿 전체 상자로 넓힌다(청크 경계로 잘리지 않는 바닐라 quirk, 재현).
- **FeaturePlacer 수정**: 바닐라는 `placeInChunk` 호출마다 새 `getWritableArea` 상자를 만든다. Furnace 는 청크당 하나를 공유해서 상자를 넓히는 조각(화석)이 뒤 구조물·피처에 새는 문제가 있어 호출마다 새로 만들게 했다. (정정) 상자를 넓히는 조각은 화석만이 아니다 — `RuinedPortalStructure.postProcess` 도 `wb.encapsulate` 로 넓힌다. 같은 청크에서 ruined_portal 이 다른 start 보다 먼저 올 때 이전에는 넓어진 상자가 뒤 start 로 새던 것이 이제 사라진다. 이 변경은 바닐라 방향이지만 기존 결과가 바뀔 수 있다(기존 fixture 시험 통과로만 확인).
- **void_start_platform**: `VoidStartPlatformFeature` 이식(청크 (0,0) 체커보드 거리 1 이내에서만, 중심 (8, origin.y+3, 8) 조약돌 + 반경 16 돌). 바닐라 origin 은 청크 최소 y(`SectionPos.origin`)인데 Furnace `PlacedFeature.placeWithBiomeCheck` 가 y=0 을 넘기고 있어 `view.minBuildHeight()` 로 고쳤다(biome-only 배치 외에는 y 를 새로 정하므로 대부분 불변이나, 오버월드(minY=-64)에서 `freeze_top_layer` 의 바이옴 검사(`getBlendedBiomeId` 에 쓰는 y)가 달라질 수 있다 — 바닐라 origin 쪽이 맞다). the_void 바이옴은 `possibleBiomes`(53개)에 없어 일반 월드에서는 호출되지 않는다.
- **bonus_chest**: `BonusChestFeature` 이식(열 셔플 `Util.toShuffledList` → MOTION_BLOCKING_NO_LEAVES 위 첫 빈 칸에 상자 + `spawn_bonus_chest` 표 시드 `nextLong` + 사방 횃불 `canSurvive`). `/place feature minecraft:bonus_chest` 로 쓸 수 있다.
  - **미연결(바닐라와 다른 점)**: 바닐라는 월드 생성 시 `MinecraftServer.setInitialSpawn` 이 `level.random`(비결정 시드)으로 이 피처를 스폰 지점에 놓는다. 전용 서버에는 설정이 없고(`level.dat` 의 `bonus_chest` 만 읽는다) Furnace 의 스폰 결정 코드에는 연결하지 않았다. 난수원이 비결정이라 재현 대상도 아니다.
  - 중심 청크 밖 읽기/쓰기는 월드젠 뷰 제약을 따른다(`/place` 격자 뷰에서는 반경 안에서 정상).
- **generate-structures=false** 재확인(최신 HEAD): `server.properties` → `LevelData.generate_features` → `FurnaceMain.applyWorldGenOptions` 가 오버월드·네더·엔드 모든 `NoiseWorldSource` 의 `StructureEngine.setStructuresEnabled` 를 끈다. 이미 동작하며(`WorldGenOptionsTest`) 수정할 것 없음. 평지(`SuperflatWorldSource`)는 구조물을 아직 만들지 않는다.
- **검증 한계(가정, oracle 미검증)**: 샌드박스에서 worldgen oracle 을 쓸 수 없어 위 세 항목은 디컴파일을 한 줄씩 대조한 손계산 시험(`NetherFossilStructureTest`, `VoidAndBonusFeaturesTest`)만 있다. 실제 네더 지형(영혼 모래 골짜기)에서의 화석 위치·바닐라 `.mca` 대조는 하지 않았다. base column 은 기존 `TerrainAccess.baseBlock`(노이즈 채움, Beardifier 없음)을 그대로 쓴다.
- 시험 한계: `/place` 의 "아직 구현 안 됨" 경로는 루트 Noop 이 없어져 PlaceCommandTest 에서 더는 직접 시험하지 않는다. Noop 판정(`containsNoop`)은 단위로만 간접 확인된다.
## 2026-11-02 몹 능력 감사 후속: 게임 이벤트·머리 드롭·거미줄·돼지 타기
- **몹 게임 이벤트 방출**(바닐라 호출 지점, 원인 엔티티·위치 그대로): `ENTITY_DAMAGE`(LivingEntity.actuallyHurt 에서 흡수 뒤 실제 피해가 있을 때, 플레이어는 PlayerVitals.actuallyHurt), `EXPLODE`(Explosion.explode 맨 앞, 원인 엔티티로. 폭죽은 owner), `PRIME_FUSE`(크리퍼 부풀기 시작), `TELEPORT`(엔더맨은 옛 위치 Vec3, 셜커는 옛 칸 가운데), `SHEAR`(양·무시룸·눈 골렘·보그드, 플레이어가 원인), `EAT`(판다), `DRINK`(마녀), `ENTITY_PLACE`(닭 알), `ENTITY_ACTION`(늑대 털기), `PROJECTILE_SHOOT`(Projectile 첫 틱, 주인이 원인)·`PROJECTILE_LAND`(맞은 위치/칸 가운데+블록 상태). 엔더맨 블록 집기/놓기는 `BLOCK_DESTROY`/`BLOCK_PLACE` 도 직접 낸다.
- 늑대 털기 소리 시점을 바닐라(털기 시작 틱, `cs==0`)로 옮겼다(전엔 0.4 지점 통과 때). 소리 높이 난수 2회가 8틱 앞당겨진다.
- **감사 오류 정정**: Hoglin 을 "평화로움 despawn"이라 한 것은 틀렸다. `clh.java:189` 의 `ee()` 는 `shouldDropExperience`(항상 참)이며 Furnace 는 이미 같다. Hoglin 은 `shouldDespawnInPeaceful` 을 덮지 않아 평화로움에서도 남는다(`EntityAuditFixesTest` 가 확인).
- **충전 크리퍼 머리 드롭**: Creeper/Zombie 계열(Husk·Drowned·ZombieVillager·ZombifiedPiglin 포함)/Skeleton/WitherSkeleton/Piglin 의 `dropCustomDeathLoot` 에서 `super` 뒤(Piglin 은 인벤토리 드롭 앞)에 처리. 크리퍼당 1회(`droppedSkulls`, 저장 안 함), 크리퍼는 자기 자신의 머리 제외. Stray·Bogged 는 바닐라에서도 머리 없음(가정: 바닐라 해당 클래스가 이 코드를 안 가진다).
- **거미줄**: WebBlock 이 Spider(CaveSpider 포함)는 건너뛴다. 가루눈·달콤한 열매 덤불은 바닐라도 거미를 막지 않는다.
- **돼지 타기**: 안장 + 승객 없음 + 웅크리지 않음 → 탄다. 당근 낚싯대(주/보조 손)를 들면 조종(`getControllingPassenger`) — 이동은 클라이언트가 정하고 서버는 move_vehicle 로 받는다. 우클릭 가속(`ItemBasedSteering.boost`: 총 시간 `nextInt(841)+140` 동기화, 내구도 7, 닳으면 낚싯대)과 `tickRidden`(방향 따라가기·가속 진행).
- **한계/미구현**: 돼지 내릴 자리는 기본(몸통 꼭대기, 바닐라는 DismountHelper). 낚싯대로 바뀔 때 다른 컴포넌트는 옮기지 않는다. **스트라이더 안장 상호작용·조종·뒤틀린 균류 막대·좀비 피글린 기수**, **Breeze 투사체 튕김** 은 미구현(근사 활성화 안 함). `PROJECTILE_LAND` 의 "엔티티 맞음" 위치는 `Hit` 좌표 그대로. Enderman `BLOCK_*` 외 몹의 블록 이벤트(양의 풀 먹기 등)는 이번에 안 건드렸다.
- **후속(같은 날)**: 펫 사망 메시지는 구현됨 — `LivingEntity.getCombatTracker`(원인·직접 엔티티 이름 기록)와 `TamableAnimal.die`(주인에게 `runOnOwner` 로 전송, `getKillCredit` 이름을 넘겨 `.player` 변형 포함). 차이: 몹은 낙하 위치(fallLocation)를 기록하지 않아 낙하 사망 메시지가 `death.fell.accident.*` 변형 없이 일반형으로 나온다. 전투 기록의 alive 인자는 바닐라처럼 피해 전 체력>0.
- 몹 `actuallyHurt` 가 이제 흡수 체력을 먼저 깎는다(바닐라 btn.f). 전엔 몹 흡수 체력이 피해를 막지 않았다. 체력·전투 기록·`ENTITY_DAMAGE` 는 흡수를 뺀 값이 0 이 아닐 때만.
- 돼지 `boost()` 소유권 전제: 탄 플레이어의 스레드에서 부르고, 탄 돼지와 플레이어는 같은 region 이다(가정: 탑승 중 region 이 갈리지 않는다).
## 2026-11-02 작업 6: 몹 미구현 잔여 (돌고래·앵무새 어깨·바람 구슬·가죽끈·일러저/셜커/토끼/크리퍼/여우)
- **돌고래**: `DolphinSwimWithPlayerGoal`(헤엄치는 플레이어 10칸 안 → 돌고래의 가호 100틱, 6분의 1로 갱신)·`DolphinSwimToTreasureGoal`(구조물 탐색 `dolphin_located` 반경 50, skipKnownStructures=false). 구조물 탐색은 레벨의 `ExplorerMaps.Backend`가 있을 때만 된다(월드 생성기 없는 평지·시험은 "못 찾음" 처리). `generate-features=false` 면 못 찾는다. 도착 판정은 바닐라 `jd.a(x, 돌고래 y, z)` 칸 중심 4칸.
- **앵무새 어깨**: `PlayerShoulders`(Player 의 `ShoulderEntityLeft/Right` NBT 저장·복원, 엔티티 데이터 19·20 compound 동기, 20틱 뒤 내려놓기, 1/200 울음). 내려놓는 시점은 바닐라대로 피해 입음·사망·관전자 전환·급류 회전·낙하(>0.5)/물속/비행/잠/가루눈. **접속 종료 때는 내려놓지 않는다** — 바닐라 `PlayerList.remove`는 `removeEntitiesOnShoulder`를 부르지 않고(해당 메서드는 protected 라 ServerPlayer 안에서만 불린다, 디컴파일 확인) NBT 로 저장해 재접속 때 복원한다. 의뢰서의 "접속 종료 시 내려놓기"와 다르다. `isInPowderSnow` 는 발 칸이 가루눈인지로 **가정**. 어깨 상태는 다른 region 의 앵무새도 앉히므로 `Player.shoulderLock` 으로 지킨다(앉히기는 조건 확인 후 잠금 안에서 쓰기). 어깨에서 내려올 때 앵무새는 `Entities.load`(저장 NBT)로 다시 만들어 UUID 를 보존한다.
- **바람 구슬·브리즈**: `Projectile.isPickable` = redirectable_projectile(fireball·wind_charge·breeze_wind_charge), `Projectile.deflect(Deflection, …)`(AIM·REVERSE·BREEZE), `WindCharge.canDeflect` = 던진 직후 5틱 거짓, 플레이어 공격은 이 세 종류를 시선 방향으로 튕긴다. 브리즈는 바람 구슬이 아닌 투사체를 `entity.breeze.deflect` 소리와 함께 반대로(속도 -0.5배, 170~190도 회전) 튕긴다(`hitTargetOrDeflectSelf`, 같은 엔티티에게 연속 튕김 방지). **미구현**: 다른 투사체가 redirectable 투사체(불덩이·바람 구슬)를 맞혀 되치는 경로 — `Projectile.Hit` 의 대상이 `Targetable` 이라 투사체를 맞힐 수 없다(화살이 바람 구슬을 맞히지 못함). 시험은 REVERSE 회전각을 범위(170~190)와 `yRotO` 동일 증가로만 검증한다(엔티티 난수 시드 고정 불가).
- **가죽끈 잔여**: `Leashable` 인터페이스(Mob·Boat), 배 가죽끈(탄성 6칸 초과 `(거리-6)`·0.15/0.2, 10칸 초과 끊김, 파괴 시 끈 아이템, 저장 `leash`), 말 `checkLeashDistance`(6칸 넘게 당기면 먹기 해제, 5칸 제한 없음)·낙타(앉은 채 6칸 넘게 당기면 일어남, 이동 제어·무작위 앉기는 끈이면 안 함), 스니퍼 `canSniff` 에 `!탑승 && !끈`, `FollowMobGoal` 은 끈이면 멈춤, 차원 이동으로 빠질 때 `dropLeash(true, false)`. 낙타 `canChangePose`(서 있을 틈 검사)는 여전히 항상 true 근사. 죽은 끈 몹은 다음 틱에 끈 아이템을 떨군다(바닐라와 같은 경로, 시험 추가). `ExecuteCommands` `on leasher` 는 Mob 만 본다(배 미반영).
- **일러저 Johnny**: 이름이 정확히 `Johnny` 가 되면 영구 `isJohnny`, 간격 0 으로 보이고 닿는 모든 생명체(갑옷 거치대 제외)를 노린다. 습격 무기 마법: 불길한 징조 레벨 2/3/4/5 → 확률 0.1/0.25/0.5/0.75 로 `raid/vindicator`(파도 ≤ 5) 또는 `raid/vindicator_post_wave_5` 마법 제공자를 건다 — 제공자 데이터가 서버에 로드된 경우에만 마법이 붙는다(없으면 쇠 도끼 그대로). 약탈자(Pillager) 쇠뇌 마법은 습격 AI 작업 소관이라 건드리지 않았다.
- **셜커**: `ShulkerDefenseAttackGoal`(팀이 있고 보이는 적대 몹 `Enemy`). 플레이어 밀기(MOVER_SHULKER)는 아직 미구현.
- **킬러 토끼**: 변종 99 가 되고 이름이 없으면 `entity.minecraft.killer_bunny`(번역) 이름을 단다. 점프 입자는 클라이언트 몫.
- **크리퍼**: 폭발 뒤 걸린 효과로 효과 구름(반경 2.5, 사용마다 -0.5, 대기 10, 지속 300, 틱마다 -반경/지속, 색은 `PotionContents.getColor` 평균), 염소는 `setTarget` 에서 제외, `doHurtTarget` 은 항상 참(때리지 않음). **미구현**: 충전된 다른 크리퍼의 폭발로 죽을 때 머리 떨구기(`Damage` 에 원인 엔티티가 없다), 폭발 직후 `triggerOnDeathMobEffects`.
- **여우**: 입에 문 음식을 다 먹으면 효과를 실제로 건다(확률은 엔티티 난수, 먹는 소리는 `entity.fox.eat` + 레벨 난수 피치, EAT 게임 이벤트). 이전 구현은 소리가 `entity.generic.eat`·피치 난수가 엔티티 난수였다.
- **작업 6 검토 후속(2026-11-02)**: 돌고래 보물 태그는 `minecraft:dolphin_located`(#ocean_ruin, #shipwreck; 반경 50, skipKnown=false)로 바로잡았다(`on_treasure_maps`는 묻힌 보물 전용). 돌고래의 가호는 `Player.runOnOwner`로 넘긴다.
- **스레드 소유권 위험(앵무새 어깨, 미해결·가정)**: `LandOnOwnersShoulderGoal`은 앵무새 region 스레드에서 주인의 `onGround`·`vitals().isInWater()`·발 칸 블록(가루눈)을 읽고 `shoulderLock` 안에서 어깨 NBT를 쓴다(읽기 전용 + 잠금이라 값이 낡을 수는 있어도 깨지지 않는다고 **가정**). `PlayerVitals.hurt/die`의 `removeEntitiesOnShoulder`는 피해를 받는 쪽 스레드에서 `currentLevel.addFreshEntity`를 부르므로, 피해 출처가 다른 region 이면 플레이어 소유 region 밖에서 엔티티를 추가할 수 있다. 후속 과제: 내려놓기를 `runOnOwner`로 넘기고 앉히기는 앵무새→주인 메시지로 바꾼다.
- **후속 과제(성능)**: 돌고래 보물 탐색(`StructureLocator.findNearest`)은 region 스레드에서 동기로 돈다. 반경 50 청크 격자 탐색이라 틱이 수십~수백 ms 멈출 수 있다. 비동기 풀로 옮기고 결과만 region 으로 돌려받아야 한다.
## 2026-11-02 작업 3: 네트워크·서버 기능 공백 (패킷 id 는 packets.json, 와이어는 디컴파일 대조)
- **clientbound 추가**: hurt_animation(플레이어 피격, 방패로 막으면 생략·방향=atan2(dz,dx)-yaw), player_combat_enter/end(CombatTracker: 원인이 생물일 때 진입, 마지막 피해 300틱 뒤·사망 시 종료 — 이전엔 종료 기준이 100틱 하나였다), server_links(구성 단계, `bug-report-link` 의 report_bug 하나), player_look_at(`/tp … facing` 뒤, 앵커는 발·위치형으로만 보낸다 — 바닐라는 엔티티 대상이면 엔티티형, 결과 회전은 같다).
- **직렬화만 있고 호출 지점 없음(근사 활성화 안 함)**: sound_entity(바닐라 엔티티 대상 playSound 호출 지점을 확인하지 못했다), delete_chat(서명 캐시 번호 체계는 있으나 삭제 요청 경로가 없다), set_simulation_distance·set_chunk_cache_radius(런타임에 시야/시뮬레이션 거리가 바뀌는 경로가 없다).
- **serverbound**: change_difficulty/lock_difficulty·태그 질의는 권한 2 이상만. 블록 태그 질의는 로드된 칸의 saveWithoutMetadata, 엔티티 질의는 플레이어가 추적 중인 엔티티(`tracked`)나 접속 플레이어만 찾는다(추적 위치 기준 region 에서 조회 — region 경계에서 못 찾으면 바닐라와 달리 응답 없음).
- **teleport_to_entity**: 관전자만, 접속 중인 플레이어 UUID 만 찾는다. 다른 엔티티 UUID 는 무시(바닐라는 모든 엔티티; 바닐라 관전 메뉴는 플레이어 목록뿐이라 일반 클라이언트에선 관측 차이 없음 — 가정).
- **chunk_batch_received**: `ChunkSendRate` 가 PlayerChunkSender 를 따른다. **차이**: 첫 응답이 오기 전에는 제한하지 않는다(바닐라는 첫 배치 하나 뒤 응답을 기다린다). 응답 없이 도는 시험·접속 초기 흐름을 바꾸지 않으려는 것. 미확인 배치는 0 아래로 내리지 않는다. 기존 `chunks-per-tick`·동시 진행 상한은 위에 그대로 걸린다.
- **custom_payload**: play 단계의 minecraft:brand 이외 채널은 `PluginMessageEvent`(IO 스레드에서 불림), 본문 32767바이트 초과는 버린다. 송신은 `Player.sendPluginMessage`(상한 1048576). 채널 등록/해제(minecraft:register)는 구현하지 않았다.
- **리소스 팩**: 레지스트리·태그 뒤 push → 끝 상태 응답 → 구성 끝(바닐라 순서). 필수 팩은 DECLINED 만 쫓아낸다(FAILED_DOWNLOAD 등은 바닐라도 통과). 구성 단계가 아닌데 오는 끝 상태 응답은 바닐라가 예외로 끊지만 여기선 무시한다. prompt JSON 이 해석되지 않으면 안내 문구 없이 보낸다. 해시가 40자를 넘으면 잘라 보낸다(바닐라는 예외).
- **player-idle-timeout**: `Player.lastActionTime`(ms)을 바닐라 `resetLastActionTime` 호출 지점(player_action·use_item(_on)·swing·player_command·interact·client_command·container_click/button·place_recipe·sign_update·set_carried_item(유효 슬롯)·채팅/명령)에서 갱신, 이동 패킷은 갱신하지 않는다. region 틱에서 검사해 `multiplayer.disconnect.idling` 로 킥. **차이**: use_item_on 은 바닐라가 거리 검사 통과 뒤에 갱신하지만 여기선 패킷이 형식에 맞으면 갱신한다. 틱 단위 지연은 region 틱 주기만큼.
- **후속(검토 반영, 2026-11-02)**:
  - 구성 단계 keep-alive: 바닐라는 구성 단계에서도 15초마다 보낸다. 큰 팩을 받는 동안 30초 읽기 타임아웃에 끊기던 문제는 **팩 응답을 기다리는 동안에만** 구성 keep-alive(0x04)를 15초마다 보내 해결했다(`ConnectionHandler.scheduleConfigKeepAlive`). 팩이 없는 서버의 접속 순서는 그대로다. **남은 차이**: 바닐라와 달리 응답 지연을 직접 재지 않고 읽기 타임아웃에만 맡긴다. 팩 설정은 바닐라처럼 기동 때 한 번 읽는다(`ServerLoginContext.resourcePack`).
  - `entity_tag_query` 스레드 소유권: 추적 맵(`tracked`)은 질의 플레이어의 소유 region 에서 읽고, 대상 엔티티·플레이어 NBT 는 추적 위치의 소유 region 에서 읽어 응답한다(IO 스레드에서 직접 읽지 않는다). 질의 플레이어의 region 이 아닌 틱(차원 이동·넘기기 중)에 온 질의는 버린다. 조회가 `tracked` 기준이라 같은 레벨만 찾는다(바닐라는 같은 레벨 `getEntity`). 다른 차원 접속자를 id 로 질의하던 경로는 없앴다.
## 2026-11-02 마을·습격 미구현 완성 (작업 2)
- **현황 재확인**: 조사표(unimplemented-survey 12번)의 "습격자 목표 AI 없음"은 낡은 내용이다. `Raider` 의 ObtainRaidLeaderBannerGoal·PathfindToRaidGoal·RaiderMoveThroughVillageGoal·RaiderCelebration·HoldGroundAttackGoal, `AbstractIllager.RaiderOpenDoorGoal`, `Raid`(승리/패배/영웅 효과), 주민 `HarvestFarmland`·`UseBonemeal`·`ShowTradesToPlayer`·`LookAndFollowTradingPlayerSink`·`GiveGiftToHero`·`Villager.finalizeSpawn`·`IronGolem.DefendVillageTargetGoal`(평판 -100)은 이미 있었고 바닐라 디컴파일과 목표 조건·난수 순서를 다시 대조해 차이를 못 찾았다(대조: coj/cbg/cmk). 이번에 시험(`RaiderGoalsTest`)만 더했다.
- **마을 포위(VillageSiege)**: `VillageSiege`(오버월드 전용, 바닐라 ceq). 순서 = [`!isDay && spawnEnemies`] 시각이 정확히 0.5(`timeOfDay(dayTime)`, 자정 18000 딱 한 틱)이면 `nextInt(10)==0` 로 TONIGHT/DONE → (포위 정해지지 않았으면) 플레이어를 돌며 비관전·`isCloseToVillage(pos,1)`·`without_zombie_sieges` 아닌 첫 플레이어 둘레 32칸 원(`nextFloat*2π`, cos→x, sin→z, `Mth.cos/sin` 표)의 점을 10번까지 시도(칸 찾기: x `nextInt(16)-8`, z `nextInt(16)-8`, 지표 높이, 마을·좀비 EVENT 스폰 규칙) → 3틱마다 한 마리, 최대 20마리. 좀비는 `finalizeSpawn` 을 `moveTo` **앞**에 불러 난이도가 원점(0,0,0) 기준(바닐라 순서 그대로), 그다음 `nextFloat*360` 방향. 틱 위치: customSpawners 순서 팬텀→순찰→고양이→포위→상인(`RegionLevel.tickBlocks`, 오버월드만).
  - **다른 점**: 바닐라 `level.players()` 는 차원 전체 플레이어인데 여기서는 그 region 의 플레이어다. 포위 상태(오늘 밤 여부·남은 마릿수)도 region 마다 따로라, 플레이어가 다른 region 으로 넘어가면 그 region 의 상태로 이어진다(자정 굴림은 모든 region 이 각자 굴린다). 원점 청크가 다른 region 에 있으면 그 청크의 머문 시간은 0 으로 본다(`getCurrentDifficultyAt` 이 이 region 의 청크만 본다) — 가정.
  - 기존에 팬텀·순찰·고양이·상인 스포너가 모든 차원에서 돌던 것 중 **상인만** 오버월드로 제한했다(바닐라는 네 개 모두 오버월드 전용이지만 나머지 셋은 손대지 않음 — 후속). → 2026-11-03 에 나머지 셋도 오버월드 전용으로 고쳤다(아래 절).
- **방랑 상인 타이머 저장**: `WanderingTraderState`(월드 공유)를 오버월드 `World` 에 붙이고 `LevelData.wanderingTrader` 로 level.dat 의 `WanderingTraderSpawnDelay/Chance/Id` 를 읽고 쓴다(0/0 이면 24000/25 — 바닐라 생성자 규칙). 스포너가 상인을 내면 `WanderingTraderId` 에 UUID(int[4])를 기록한다. region 마다 스포너가 있으므로 같은 게임 틱에는 한 region 만 타이머를 돌린다(`claimTick`).
  - **다른 점**: 바닐라는 지연을 24000 으로 되돌린 값을 바로 data 에 쓰지 않고 다음 감소 때 쓴다(저장 직후 재시작하면 ≤0 으로 읽혀 곧 다시 굴린다). 여기서는 살아 있는 값을 그대로 둬 저장값이 항상 현재 값이다. 상인 스폰 자체는 타이머를 이긴 region 의 플레이어 중에서 고른다(바닐라는 차원 전체) — 그 region 에 플레이어가 없으면 "플레이어 없음 → 확률만 초기화" 경로를 탈 수 있다.
- **스폰 이유**: 몹 생성기(`BaseSpawner`)·시련 생성기가 주민/습격자에 SPAWNER/TRIAL_SPAWNER 이유를 넘기게 했다(바닐라 `finalizeSpawn(…, SPAWNER, …)`) — 생성기 마녀가 습격에 참여하고, 생성기 주민은 그 자리 바이옴의 종류를 받는다. 발사기 스폰 알은 SPAWN_EGG 로 남긴다(주민 쪽 결과는 DISPENSER 와 같다).
- 시험: `VillageSiegeTest`, `WanderingTraderTest`(level.dat 왕복·공유 타이머·상인 번호), `RaiderGoalsTest`(축하·마을로 향하기·깃발 줍기·마녀 참여 이유). 생성기 → 주민/습격자 이유 연결(`BaseSpawner`·`TrialSpawner`)은 시험 레벨에서 몹이 서지 않아 직접 시험하지 못했다(`finalizeSpawn` 쪽 분기만 시험) — 가정.

## 2026-11-02 마을 포위 후속 — 좀비 finalizeSpawn 난수 정정 외
- **좀비 finalizeSpawn 난수(parity 수정)**: `Zombie.finalizeSpawn` 이 바닐라(1.21.1 ckw)에 없는 `nextBoolean()` 을 하나 더 소비해, 이후 난수가 모든 좀비 스폰에서 한 칸씩 밀려 있었다. 바닐라는 `new ZombieGroupData(getSpawnAsBabyOdds(r), true)` 로 두 번째 값이 상수 `true` 라 난수를 쓰지 않는다. 상수로 고쳤고 `ZombieFinalizeRandomTest` 가 시드 50개에서 `nextBoolean` 호출 0 을 고정한다.
- **방랑 상인 타이머 원자화**: `claimTick`·읽기·감소·쓰기를 `state.lock()` 으로 묶었고, level.dat 저장도 같은 잠금으로 (delay, chance) 쌍을 한 번에 읽는다. 승자 region 에 플레이어가 없으면 타이머를 소비하지 않는다. 상인 대상 플레이어는 여전히 타이머를 돌리는 region 의 플레이어 중에서 고른다(바닐라는 차원 전체) — 차이.
- **알·발사기 마녀**: 스폰 알과 발사기로 낸 마녀도 스폰 이유를 받아 습격에 참여한다(`spawnMobFromEgg(..., SpawnReason)`).
- **포위 좀비 난이도**: `finalizeSpawn` 을 `moveTo` 앞에 불러 원점 기준 난이도를 쓰는 바닐라 순서를 `VillageSiegeTest` 가 고정한다.
## 2026-11-02 플러그인 API 1단계: 엔티티·월드·아이템·인벤토리 (작업 5)
- 설계: `docs/api/entity-world-design.md`. 원칙: 다른 region 상태는 동기로 읽거나 쓰지 않는다 — 이벤트 스냅샷 / `CompletableFuture`(소유 region 에서 실행) / `*Now`(소유 스레드 아니면 `ThreadOwnershipException`).
- 바닐라·Paper 와 다른 점·한계:
  - `EntityDamageEvent.damage` 는 방어력·인챈트 적용 **전** 원본 피해(Paper 는 최종 피해를 따로 준다). 취소하면 `hurtBy` 가 false 로 끝나 무적 시간·밀려남도 없다(Paper 와 같다고 **가정**, 미확인). 플레이어는 `PlayerVitals.hurt` 에서, 몹은 `LivingEntity.hurtBy` 에서 발화.
  - `EntityDeathEvent` 는 몹만(플레이어는 기존 `PlayerDeathEvent`). 전리품 수정은 없다.
  - `EntitySpawnEvent` 는 `addFreshEntity` 에서 발화 — 같은 UUID 중복으로 거부될 엔티티도 이벤트를 먼저 받는다. 청크 로드로 복원되는 엔티티·`loadChunkEntities` 경로는 발화하지 않는다(Paper 는 `CreatureSpawnEvent` 로 사유를 준다 — 사유 없음).
  - `PlayerMoveEvent` 는 위치가 바뀐 이동만(회전만 바뀌면 안 부름), `from/to` 의 yaw/pitch 는 현재 값. 취소는 `teleportTo(from)` 보정이라 한 틱 어긋날 수 있다.
  - `PlayerTeleportEvent` 는 같은 차원 `Player.teleportTo` 만. 차원 이동·원인 구분 없음. 호출한 스레드에서 불리며 region 스레드가 아닐 수 있다.
  - `PlayerInteractEvent` 는 RIGHT_CLICK_BLOCK / 빈 손이 아닌 RIGHT_CLICK_AIR 만. LEFT_CLICK·빈 손 허공 클릭 없음.
  - `PlayerDropItemEvent` 취소 시 아이템은 원래 칸이 아니라 첫 빈 칸으로 돌아간다(가득 차면 그대로 떨어진다).
  - `InventoryOpenEvent` 는 제작대·블록 메뉴(`openBlockMenu`)만. 상자·상인·말·엔티티 컨테이너는 발화하지 않는다. 메뉴 종류 키는 클래스 이름에서 만든 값이라 바닐라 menu type 이름과 다를 수 있다(가정).
  - `World.setBlock` 은 `BlockPlaceEvent/BlockBreakEvent` 를 거치지 않는다. 플래그 11 로 바로 설치 후 방송.
  - `Entity.teleport` 는 같은 차원·같은 region 안만 true(다른 region 으로 넘기는 이동은 핸드오프가 필요해 false). `Entity.scheduler()` 는 핸들 시점 위치의 region 스케줄러다.
  - 기존 `BlockBreakEvent/BlockPlaceEvent` 는 차원을 `"minecraft:overworld"` 로 하드코딩해 둔다(이번 작업에서 발견, 미수정). `PlayerHandle.location()` 은 실제 차원으로 고침.
- 후속(검토 반영): `PlayerMoveEvent` 취소는 `applyMove(prev)` 로 서버 위치를 되돌린 뒤 `Player.correctTo`(이벤트 우회 보정)로 텔레포트하며 `moveRejectHold` 를 건다 — 검증기(`PlayerMoveValidator.reject`)도 같은 `correctTo` 를 쓰도록 바꿔, 텔레포트 리스너가 보정을 삼키지 못한다(Paper 의 정확한 동작은 미확인, **가정**). 검증 대기 패킷은 hold 동안 버려진다. `EntitySpawnEvent` 는 목록 추가 뒤 발화·취소 시 되돌림, 승객 묶음은 전부 아니면 전무(Paper 는 개별 엔티티 단위일 수 있다 — 미확인). `Interact` 취소는 손 슬롯 `ContainerSetSlot(-2)` 재전송(블록은 기존 재전송). 플레이어 인벤토리 쓰기는 `runOnOwner`(소유 틱에서 적용되므로 Future 가 최대 한 틱 늦게 완료). 서버 스레드에서 미완료 `RegionFuture.get()/join()` 은 예외.

## 2026-11-03 불안정 시험 근본 원인 — TabListTest, GeneratedVillageProfessionTest
- **TabListTest(단독 약 4000회에 1회)**: 넷티 `EmbeddedEventLoop` 의 작업 큐는 동기화 없는 `ArrayDeque` 다. `Player.send` 는 `channel.eventLoop().execute` 로 넣는데, 청크 전송 완료 콜백(`serializedChunkBody().thenAccept`, `ChunkBatchFinished`)은 청크 워커 스레드에서 돈다. 그 사이 시험 스레드가 `runPendingTasks` 로 꺼내면 작업이 사라지거나 큐가 깨져(`ArrayIndexOutOfBoundsException` 까지 재현) 이후 패킷이 통째로 빠졌다. 실패 모양은 `farAwayOnlinePlayer…` 의 "퇴장하면 탭에서 제거: []"(빈 목록). 6개 시험 7200회 중 2회 재현. 실서버 NIO 이벤트 루프는 스레드 안전하므로 **시험 하네스 문제**다.
  - 수정: 시험용 `ConcurrentEmbeddedChannel`(execute·schedule 과 runPendingTasks·readOutbound·readInbound 를 한 잠금으로 직렬화, 큐 자체는 원래 EmbeddedEventLoop 것). `new EmbeddedChannel(` 을 쓰던 시험 전부(약 100개 파일)를 이것으로 바꿨다. 수정 뒤 TabListTest 20400회 실패 0. `ConcurrentEmbeddedChannelTest` 는 일반 EmbeddedChannel 로 바꾸면 바로 깨진다(확인함).
  - 같은 경합으로 설명되는 과거 간헐 현상: `ChunkSendPacketOrderGoldenTest` 끝에 블록 변경이 하나 더 붙던 것(perf-c4/c5 "원인 미확인"), 다른 플레이어 채널 시험들의 드문 실패. 이 시험은 region 스레드에서 꺼내도록 이미 고쳐 두었지만 청크 워커 쪽 넣기와는 여전히 경합했다(추정, 이번에 따로 반복 재현은 안 했다).
- **GeneratedVillageProfessionTest(전체 스위트에서 가끔)**: 주민 4명 중 직업을 얻을 수 있는 건 둘뿐이다 — 하나는 바보, 하나는 대장간 카운터(매끄러운 돌, y=72 발판) 위에서 벽에 박힌 용광로(257,71,903)까지 2칸 안으로 못 간다(용광로가 고체라 길 목표가 위로 올라가 고체 기둥 끝 y=78 이 되고, 그 목표에 가장 가까운 곳이 지금 자리라 길이 1칸짜리로 끝난다. `GroundPathNavigation.createPath` 는 바닐라와 같은 꼴이라 바닐라도 같을 것으로 **가정**, 실서버 대조 안 함). 나머지 둘이 **고정 600틱 안에** 모두 직업을 얻고 그중 하나가 WORK 로 직업 블록 근처에 있어야 했는데, 무작위 시드 84회 측정에서 둘째 직업 획득 180~450틱(농부 둘이 퇴비통 하나를 다투면 늦다), WORK 근처 도달 500~670틱이었다. 부하가 있을 때 6회 중 1회 실패를 재현(직업 1명).
  - 수정: 조건은 그대로 두고 20틱마다 확인하며 상한 2400틱(낮 3900, WORK 시간대 안)까지 돈다. 부하 중(JVM 3개 동시) 45회 실패 0.
- 반복 실행 방법(커밋 안 함): JUnit `LauncherFactory` 로 한 클래스를 N번 돌리는 임시 시험을 두고 `--tests` 로 돌렸다. **같은 JVM 안에서 병렬로 돌리면 `IoPoolDrainExtension` 이 정적 `TestServers.IO_POOLS` 를 다른 실행 것까지 닫아 `RejectedExecutionException` 이 난다**(하네스 착시). 병렬은 JVM 을 따로 띄워야 한다.
- **ConnectionInputTest.furnace_opensWithFourDataSlots(이번 전체 실행에서 1회, `expected 4 but was 0`)**: 시험 쪽 대기 조건이 이르다. `openResolved` 는 `setContainerMenu` 로 창을 먼저 걸고 그다음 `setSynchronizer` 에서 첫 `container_set_content`·데이터 칸(0x14)을 보내는데, 시험은 `hasOpenContainer()` 가 참이 되자마자 꺼냈다. 창을 여는 시험 7곳을 `awaitOpen`(창 열림을 기다린 뒤 그 칸의 region 에 빈 작업을 보내 연 작업이 끝까지 돌았음을 확인)으로 바꿨다. 서버 코드는 그대로다. 다른 PR CI 의 상자 열기 시험 간헐 실패도 같은 대기 방식이라 같은 원인일 가능성이 크다(**추정**, 그 CI 로그의 실패 시험 이름은 로그가 잘려 확인하지 못했다).

## 2026-11-03 lz4 지역 파일 · 플레이어 속성 저장

- **lz4 지역 파일(type 4)**: `world/anvil/Lz4Block` 이 lz4-java `LZ4BlockInputStream/LZ4BlockOutputStream` 형식을 외부 라이브러리 없이 구현한다(블록 = `LZ4Block` 매직 + 토큰 + 압축 길이 + 원래 길이 + XXHash32(씨앗 0x9747b28c, 하위 28비트) 체크섬, 64KB 블록, 길이 0 끝 블록). 해제는 체크섬·경계를 검사하고 손상 시 IOException.
  - **가정**: 바닐라 1.21.1 `RegionFileVersion` 의 lz4 가 lz4-java 블록 스트림 기본 생성자라는 것은 기억에 기댄 것이다. 이 컨테이너엔 디컴파일이 없어 재확인 못 했다. 형식 자체는 lz4-java 1.8.0 jar(스크래치패드에만, 커밋 안 함)와 양방향 400건 대조로 확인했다(그쪽 출력 → 우리 해제, 우리 출력 → 그쪽 해제, 크기 0~30만 바이트). 실제 바닐라 lz4 `.mca` 로는 아직 안 열어 봤다.
  - 압축 바이트는 lz4-java 와 같지 않다(탐욕 해시 압축). 저장 결과 바이트는 관측 결과가 아니므로 parity 대상이 아니다. 크기는 lz4-java 와 거의 같았다(28만 바이트 반복 데이터에서 1654 대 1655).
  - 시험: `Lz4BlockTest`(lz4-java 가 만든 고정 벡터 해제, 체크섬, XXH32 공개 시험값, 크기별 왕복, 손상 거부), `RegionCompressionConfigTest`(lz4 설정·region 파일 왕복).
- **플레이어 속성 저장·복원**: 플레이어 NBT `attributes`(바닐라 LivingEntity 와 같은 키·형식: id, base, modifiers{id, amount, operation}). 장비·효과·달리기·위치 효과 수정자(`Player.managedModifiers`)는 바닐라 transient 와 같게 빼고, `/attribute` 로 단 수정자와 기본값만 저장한다. 저장은 연결 스레드에서 하므로 소유 스레드가 `syncAttributes` 에서 바뀐 때만(`AttributeMap.version`) 불변 스냅샷을 떠 둔다. 접속 때는 바닐라 readAdditionalSaveData 순서대로 속성 → 체력 순으로 읽어 늘린 최대 체력 위의 체력이 20 으로 잘리지 않는다. 한 번도 동기 전이면 저장에 붙이지 않아 파일의 기존 값이 남는다.
  - 남은 차이(가정): 바닐라 리스폰은 새 ServerPlayer 를 만들며 `restoreFrom` 이 속성을 어떻게 옮기는지(1.21.1 에서 기본값만 옮기는지) 디컴파일로 확인하지 못했다. Furnace 는 리스폰해도 같은 객체라 속성이 그대로 남는다.
  - 시험: `PlayerAttributeSaveTest`.

## 2026-11-03 스트라이더: 안장·조종·뒤틀린 균류 막대·기수
- 바닐라 1.21.1 `ckq`(Strider)·`bti`(ItemBasedSteering)·`ctz`(FoodOnAStickItem) 디컴파일 대조로 이식.
- **ItemBasedSteering 분리**: 돼지와 스트라이더가 같은 클래스(`entity/ItemBasedSteering`)로 안장(동기화 데이터)·가속(`nextInt(841)+140`, 비교 뒤 증가)·저장(`Saddle`)·`boostFactor` 를 쓴다. 돼지 동작은 그대로(PigRidingTest 통과).
- **조종**: 안장 + 첫 승객이 뒤틀린 균류 막대(주/보조 손)를 든 플레이어면 그가 조종, 아니면 Mob 기본(좀비 피글린 기수가 조종). `tickRidden` = 방향 따라가기 → `tickBoost` → 기본. `getRiddenSpeed`(속도 × 떨면 0.35/아니면 0.55 × 가속 배율)는 바닐라 값으로 두었지만 서버 이동에는 쓰지 않는다(조종은 클라이언트, move_vehicle).
- **뒤틀린 균류 막대 사용**: `ItemUse.foodOnAStickUse` 가 당근 낚싯대와 공용. 탈것이 막대 종류의 엔티티(당근 → 돼지, 균류 → 스트라이더)이고 `boost` 가 되면 내구도(당근 7, 균류 1), 아니면 사용 통계 + PASS. 돼지에 탄 채 균류 막대는 아무 일 없음.
- **상호작용**: 먹이 아님 + 안장 + 승객 없음 + 웅크리지 않음 → 탄다. 아니면 Animal(먹이·번식) → 처리 안 됐으면 안장 아이템(SaddleItem: 살아 있고 새끼 아님, 소리 출처 neutral, `EQUIP` 게임 이벤트). 먹이로 처리됐으면 조용하지 않을 때 `strider.eat`(피치 `1 + (r-r)·0.2`, 엔티티 난수).
- **틱**: 유혹 중 `nextInt(140)==0` → happy, 아니면 패닉 중 `nextInt(60)==0` → retreat(엔티티 난수, AI 여부와 무관하게 매 틱). 떨기 = 따뜻하지 않음 **또는 탄 탈것이 떠는 스트라이더**. 기본 틱 → 용암 위 뜨기 → `checkInsideBlocks`. `checkFallDamage` 는 `checkInsideBlocks` 후 용암 안이면 낙하 거리 초기화(이전의 `causeFallDamage` 재정의는 바닐라에 없어 제거). ambient 소리 조건을 "떨지 않음"에서 바닐라의 "유혹 중 아님"으로 정정. `nextStep = moveDist + 0.6`.
- **내릴 자리**: 승객 시선 방향과 ±22.5°·±45° 다섯 탈출 벡터 × (몸통 꼭대기 → 바닥 −0.5) 칸 후보를 순서대로, 용암 칸은 건너뛰고 바닥·몸 충돌 검사. 없으면 (x, 몸통 꼭대기, z). `canAddPassenger` = 승객 없음 && 눈이 용암에 잠기지 않음(`Entity.isEyeInLava` 추가).
- **기수 스폰(finalizeSpawn)**: 새끼면 그대로. 레벨 난수로 `nextInt(30)==0` → 좀비 피글린(아기 여부 `Zombie.getSpawnAsBabyOdds` 를 먼저 뽑고, `ZombieGroupData(baby, false)` = 닭 기수 금지로 `finalizeSpawnWithGroup`) 탑승 → 주 손 뒤틀린 균류 막대 → 내 안장(소리 없음). 아니면 `nextInt(10)==0` → 새끼 스트라이더(나이 −24000) 탑승. 기수면 그룹 = 아기 확률 0, 아니면 0.5. 들어온 그룹은 버린다(바닐라와 같음). 기수는 레벨에 넣지 않고 태우기만 — 부른 쪽 `addFreshEntityWithPassengers`.
- **다른 점·한계**: 승객 부착점의 걷기 흔들림(`walkAnimation` 기반 y 오프셋)은 서버에 걷기 애니메이션 상태가 없어 0(서 있을 때 값). 걸음 소리(`step`/`step_lava`)는 엔티티 걸음 소리 전반과 함께 미구현. `shouldPassengersInheritMalus` 는 값만 있고 길찾기가 아직 안 본다. 바닐라 StriderPathNavigation 의 `hasValidPathType`(용암·불 경로 허용)은 Furnace 길찾기에 그 훅이 없어 없음(경로 비용은 생성자의 malus 로 같음). 내릴 때 고른 자세로 승객 자세를 바꾸지 않는다(다른 탈것과 같은 TODO). 돼지와 같은 소유권 전제(탄 플레이어와 스트라이더는 같은 region).
- 시험: `StriderTest` 12개(안장·타기·조종·가속·막대 사용·내릴 자리·기수 세 경로와 난수 호출 순서·저장·안장 드롭·떨기 상속). 일부러 기수 확률·용암 건너뛰기·떨기 상속을 틀리게 바꾸면 5개가 실패하는 것을 확인했다.

## 2026-11-03 운영 구멍 메우기: RCON IP당 연결·minecraft:register·안 보내던 패킷 4종
- **RCON IP당 동시 연결 상한**(바닐라에는 없음): `RconServer.MAX_CLIENTS_PER_IP = 4`. 전체 상한 16 과 별개로, 같은 IP 가 살아 있는 연결 4개를 들고 있으면 다섯 번째는 수락 즉시 닫는다. 연결이 끝나면 다음 수락 때 자리가 난다. 루프백도 예외가 아니다(같은 기계의 관리 도구 여럿이 동시에 붙으면 걸릴 수 있음 — `user-todo.md`). `clientCount()` 는 이제 스레드가 살아 있는 연결만 센다.
- **플러그인 채널 등록(minecraft:register / minecraft:unregister)**: 바닐라에는 없는 Bukkit 관례. 본문은 채널 이름을 NUL 로 이은 UTF-8(`net/PluginChannels`).
  - 클라이언트 → 서버: 구성·play 두 단계 모두 받는다(Fabric 은 구성 단계에서 보낸다). 올바른 자원 이름만 정규화해 담고, 연결당 128개까지(CraftBukkit `CraftPlayer.addChannel` 의 128 — **가정**, Paper 소스 미확인; Paper 는 넘으면 연결을 끊는 것으로 기억하지만 여기서는 넘는 것만 버린다). 이 두 채널은 `PluginMessageEvent` 로 넘기지 않는다(CraftBukkit 와 같다). API `Player.listeningPluginChannels()`.
  - 서버 → 클라이언트: 플러그인이 `Furnace.registerPluginChannel(plugin, channel)` 로 등록한 채널을 접속 직후(접속 알림 직전) 한 번 `minecraft:register` 로 보낸다(CraftBukkit `sendSupportedChannels`). 실행 중 새로 등록·해제하면 접속자 모두에게 보낸다. 플러그인이 꺼지면 그 플러그인의 등록이 풀린다(다른 플러그인이 같은 채널을 쓰면 유지). 등록 여부와 관계없이 받은 메시지는 그대로 `PluginMessageEvent` 로 온다(Bukkit 은 등록한 채널만 플러그인에 넘긴다 — 차이).
  - 같이 고친 것: 바닐라 클라이언트는 `minecraft:brand` 를 구성 단계에서 보내는데 Player 가 생기기 전이라 `Player.clientBrand()` 가 늘 null 이었다. play 진입 때 넘기게 했다.
- **sound_entity**: 바닐라 1.21.1 에서 `Level.playSound(except, entity, …)`(→ `ServerLevel.playSeededSound` 엔티티 변형, aqu:761)를 부르는 곳을 디컴파일 바이트코드에서 모두 찾아(`dcw.a(Lcmx;Lbsr;Lavo;Lavq;FF)V` 호출 29곳) Furnace 에 있는 것을 `ChunkLevel.playSound(except, entity|player, …)` 로 바꿨다: 양·무시룸·눈 골렘 털 깎기, 돼지 안장(손·발사기), 스트라이더 안장(발사기), 염소·스니퍼 먹이, 브리즈 충전·튕김, 알레이 아이템 던지기·주고받기·자수정 소리, 개구리 알 낳기·혀·먹기·착지, 염소 착지·점프·박치기 준비·박치기·뿔 부러짐, 삼지창 던지기·급류, 빈 지도 사용, 염소 뿔. 범위·씨앗은 위치 변형과 같다(엔티티의 지금 위치에서 16, 크기 > 1 이면 16×크기).
  - Furnace 에 없어 못 바꾼 곳: 보그드 털 깎기, 염료로 양 물들이기(`DyeItem.interactLivingEntity`), 스트라이더 손 안장(스트라이더 미구현).
  - 같이 고친 것: 염소 뿔 소리 분류를 `"records"` 로 넘겨 VOICE(9)로 나가던 것을 바닐라 RECORDS(`"record"`, 2)로.
- **delete_chat**: 바닐라 서버는 이 패킷을 **보내지 않는다**(디컴파일에서 `acw` 생성 호출 0곳 — 코덱만 있음). Paper 의 `Player.deleteMessage` 처럼 플러그인 API `Player.deleteChatMessage(byte[] 서명)` 으로만 보낸다. 서명은 `AsyncChatEvent.signature()`(서명 없는 채팅이면 null). 수신자 서명 캐시에 있으면 캐시 번호, 없으면 서명 256바이트 전체(바닐라 `MessageSignature.pack`).
- **set_chunk_cache_radius / set_simulation_distance**: 바닐라는 `PlayerList.setViewDistance / setSimulationDistance`(aur:705·716)에서만 보낸다 — 전용 서버는 기동 때 한 번 부르고(접속자 없음) 실행 중에는 바꾸는 길이 없어 사실상 안 나간다. Furnace 는 플러그인 API `Furnace.setViewDistance / setSimulationDistance` 로 실행 중 바꿀 수 있게 하고 그때 보낸다. 값은 2~32 로 자른다(`ChunkMap.setServerViewDistance` 와 같게, 시뮬레이션 거리도 같은 범위로 — 가정). 시야 거리는 모든 차원 `World` 값과 플레이어별 전송 거리 `min(새 값, 클라이언트 렌더 거리)` 를 바꾸고(클라이언트가 설정을 바꿨을 때와 같은 길), 시뮬레이션 거리는 `LevelServices.simulationDistance()` 를 통해 region 이 다음 틱부터 읽는다. 새 접속자의 login 패킷도 바뀐 값을 쓴다. 명령(`/furnace` 등)으로 바꾸는 길은 만들지 않았다.
- 시험: `RconServerTest.perIpCapRefusesExtraClientsAndFreesSlotOnClose`, `PluginChannelsTest`, `ClientChannelRegisterFlowTest`(구성 단계 brand·register → Player, play 단계 unregister), `ChannelsAndDistanceTest`(서버 채널 알림·철회, delete_chat 바이트, 거리 변경 패킷·자르기), `SoundEntityBroadcastTest`(엔티티 번호·범위·except, 털 깎기가 위치 소리를 안 보냄).

## 2026-11-03 플러그인 API 2단계: EntityScheduler·영속 데이터·Chunk·스폰/조회·칸·상인·왼쪽 클릭
- 설계: `docs/api/entity-world-design.md` 8장. 새 동기 접근은 없다(사본 또는 소유 스레드 Future).
- 바닐라·Paper 와 다른 점·한계·가정:
  - `EntityScheduler` 의 retired 는 엔티티를 내린 스레드에서 바로 불린다(Folia 와 같다고 **가정**). 차원 이동도 retire 한다 — Furnace 의 차원 이동은 엔티티를 새로 만드므로(`setRemoved(CHANGED_DIMENSION)`). 플레이어는 접속 종료에서만 retire(죽음·리스폰·차원 이동으로는 retire 하지 않는다 — 같은 `Player` 객체가 이어진다).
  - `EntityScheduler` 작업은 `RegionLevel.tickBlocks` 의 블록 이벤트 뒤·엔티티 틱 앞에서 돈다(Folia 는 region 틱 앞부분 — 순서 차이는 **가정**, 플러그인 관측에 영향 적음). 엔티티 틱 청크 밖의 엔티티도 돈다.
  - 영속 데이터 키 검사는 `NamespacedKey` 규칙(소문자). Paper 의 `PersistentDataType.LIST`·사용자 정의 종류는 없다(값은 보존).
  - `World.nearbyEntities` 는 center 칸 region 이 가진 것만 — region 경계 근처에서는 Bukkit `getNearbyEntities` 보다 적게 나올 수 있다.
  - `World.inventoryAt` 은 큰 상자를 반쪽(27칸)으로 돌려준다(Bukkit 은 `DoubleChestInventory` 54칸). 열지 않은 전리품 상자는 채우지 않는다(Bukkit `getInventory` 도 채우지 않는다고 **가정**).
  - `Merchant.setTrades` 는 수요·특가를 0 으로 둔다(Bukkit `MerchantRecipe` 는 demand/specialPrice 를 가진다 — 다음 단계).
  - LEFT_CLICK_AIR: Spigot 은 휘두르기 패킷의 손과 상관없이 주 손으로 발화하지만, Furnace 는 왼손(hand 1) 휘두르기에서 발화하지 않는다(**가정**: 왼손 휘두르기는 왼쪽 클릭이 아니다). Spigot 의 "아이템을 막 버린 틱이면 발화 안 함" 같은 예외 처리는 없다. 시선 추적은 엔티티 상자를 부풀리지 않는다(Spigot rayTrace raySize 0).
  - `PlayerInteractEntityEvent` 는 INTERACT 만(INTERACT_AT 의 `PlayerInteractAtEntityEvent` 없음). 다른 플레이어 우클릭은 발화하지 않는다(서버가 플레이어 상호작용을 처리하지 않는 경로).
- 검증: `PluginApiStage2Test`(11개), `EntityTasksTest`(4개) — region 넘김 뒤 작업이 새 region 스레드에서 도는 것, retired, 저장 왕복(BukkitValues·ChunkBukkitValues·플레이어 파일), 청크를 만들지 않는 것, 상자 부서진 뒤 실패, LEFT_CLICK 취소가 크리에이티브 즉시 파괴를 막는 것. 실제 클라이언트 확인은 없다(`docs/user-todo.md`).

## 2026-11-03 특수 스포너 오버월드 전용
- 바닐라 1.21.1 `MinecraftServer.createLevels` 는 특수 스포너 다섯(팬텀 dzk → 순찰 dzj → 고양이 cmg → 포위 ceq → 상인 cmr)을 `ImmutableList.of(...)` 로 묶어 오버월드 `ServerLevel` 에만 주고, 다른 차원은 `ImmutableList.of()`(빈 목록)로 만든다(`javap -c -p` 로 확인).
- 전에는 팬텀·순찰·고양이가 모든 차원에서 돌아 네더·엔드에서 순찰대가 나올 수 있었고(바닐라 순찰은 하늘·마을 검사만 해서 네더에서도 굴림이 통과할 수 있다), 레벨 난수 소비도 바닐라와 달랐다. `RegionLevel.tickCustomSpawners` 로 묶어 다섯 모두 `minecraft:overworld` 일 때만 돈다. 순서는 그대로.
- 시험: `CustomSpawnerDimensionTest` — 네더 region 에서 돌려도 팬텀·순찰·고양이 타이머가 0 그대로(= 레벨 난수를 쓰지 않음), 오버월드에서는 바닐라 범위로 다시 걸린다. 플레이어 없이 시험하므로 채널이 필요 없다.

## 2026-11-03 시야 거리 변경 즉시 반영, Block.pushEntitiesUp
- **시야 거리**: `Region.updateChunks` 가 중심 청크가 그대로면 바로 돌아가서, 클라이언트 렌더 거리(client_information)가 바뀌어도 청크 경계를 넘어야 청크를 더 보내거나 내렸다. 바닐라 `ChunkMap.updateChunkTracking` 처럼 지금 추적 범위의 (중심, 거리)를 기억해(`Player.trackedViewDistance`) 둘 중 하나라도 바뀌면 그 틱에 차이를 반영한다. `SetCenterChunk` 는 중심이 바뀔 때만 보낸다(`applyChunkTrackingView`). 서버 상한보다 크게 요청하면 실제 거리가 그대로라 아무것도 안 보낸다. 실행 중 서버 시야 거리를 바꾸는 `ServerCore.setViewDistance`(PR #4)도 `Player.viewDistance` 만 바꾸면 같은 길로 반영된다.
- **시뮬레이션 거리**: region 이 매 틱 `simulationDistance()` 를 다시 읽으므로(무작위 틱·블록 엔티티 틱 대상) 값이 바뀌면 다음 틱부터 쓴다 — 고칠 것 없음. 기준 브랜치에는 실행 중 바꾸는 길 자체가 없다(PR #4 의 `setSimulationDistance`).
- **pushEntitiesUp**(바닐라 `Block.pushEntitiesUp`, `javap -c -p` 로 dfy 대조): `Shapes.joinUnoptimized(from 충돌, to 충돌, ONLY_SECOND)` 을 칸으로 옮겨 비면 끝, 아니면 그 경계 상자와 겹치는 관전자 아닌 엔티티마다 상자를 1 올려 모양 쪽으로 -1 의 Y 충돌 거리 d 를 구해 `teleportRelative(0, 1 + d, 0)`. 엔티티는 `moveTo`(회전 유지) + 승객 재배치, 플레이어는 `ServerPlayer.teleportRelative` 처럼 다섯 축 모두 상대값 깃발(0x1F)로 `player_position`(dy, 회전 0)을 보낸다. Paper 처럼 `PlayerTeleportEvent` 를 거친다.
  - 호출 지점 6곳(바닐라와 같은 수, 순서 대조): 경작지·흙길 → 흙(`FarmBlock.turnToDirt`, 설치 전), 흙길 설치가 안 될 때 흙(`DirtPathBlock.getStateForPlacement`), 엔더의 눈 끼우기(설치 전), 눈 쌓임 한 겹 더(`tickPrecipitation`, 설치 전), 점적석 진흙 → 점토(설치 후), 스컬크 맥 → 스컬크(설치 후).
  - `Shape.joinOnlySecond` 의 격자는 IndexMerger 를 따른다: 둘 다 큐브 격자면 더 고운 쪽, 아니면 좌표 합집합. 엔티티가 새 부분에 걸쳐 있을 때 collide 결과가 칸 경계에 달려서다. `NonOverlappingMerger`·IndirectMerger 의 범위 밖 좌표 버림은 빈 칸 쪽에만 영향이라 따라 하지 않았다(결과 같음, 손 계산).
  - 엔티티를 훑는 순서: 비플레이어 엔티티 다음 플레이어(바닐라는 엔티티 구역 순서). 각자 독립으로 움직이고 난수를 쓰지 않아 결과는 같다.
- **봇 확인(오프라인 서버 + mineflayer, 2026-11-03)**: 서버 시야 6, 움직이지 않고 렌더 거리 6→2 에서 unload 144·center 0, 2→6 에서 청크 144, 6→12 는 아무것도 안 옴. 경작지 위 갑옷 거치대가 말라 흙이 되자 y −60.0625 → −60. 경작지 위 플레이어는 `player_position`(x 0, y 0.0625, z 0, 회전 0, 깃발 0x1F) 한 번을 받고 −60 에 섬. 실제 클라이언트 화면(구멍이 메워지는지)은 미확인.

## 2026-11-03 올챙이·아기 스니퍼 생성과 피글린 분노 연결
바닐라 1.21.1 바이트코드(`javap -c -p`, 난독화 이름 djc=FrogspawnBlock, dnh=SnifferEggBlock, clo=PiglinAi, dfy=Block, cow=ChestBoat, cox=ContainerEntity, cpa=MinecartChest)로 대조했다.

- **개구리알 부화**(`FrogspawnBlock.hatch`): 블록을 지우고 `block.frogspawn.hatch` 소리 뒤 `nextIntBetweenInclusive(2, 6)` 마리. 마리마다 **x 오프셋 → z 오프셋 → 방향** 순서로 난수를 쓴다. 오프셋은 `clamp(nextDouble, 0.2, 0.8)` 인데 바닐라 상수는 float 를 넓힌 값(0.20000000298023224 / 0.7999999970197678)이라 그대로 썼다. 방향은 `nextIntBetweenInclusive(1, 361)`(1~361, 바닐라 그대로 — wrapDegrees 하지 않는다), y 는 **칸 y − 0.5**, 올챙이는 `setPersistenceRequired`. 블록 틱 난수(`random` 인자)를 쓴다.
- **스니퍼 알 부화**(`SnifferEggBlock.tick`): hatch 2 면 소리 → `destroyBlock` → 아기 스니퍼(`setBaby(true)`) 를 **`pos.getCenter()`(x+0.5, y+0.5, z+0.5)** 에, 방향은 `wrapDegrees(level.random.nextFloat() × 360)`(레벨 난수, 블록 틱 난수가 아니다). 이전 주석의 "칸 아래 가운데"는 틀렸다. persistence 는 걸지 않는다. 스니퍼의 아기 나이는 -48000(`Sniffer.setBaby`).
- **피글린 분노 호출부 7곳**(바닐라와 같은 지점·같은 `requireLineOfSight`):
  - `Block.playerWillDestroy`: 파괴 효과(2001) 뒤, 게임 이벤트 앞. 상태가 `#minecraft:guarded_by_piglins`(금 블록·금 광석·상자류 등)일 때 **시야 불필요(false)**.
  - 통·상자·엔더 상자·셜커 상자 `useWithoutItem`(통계 뒤), 상자 수레 `interact`, 상자 배 `interact`·`openCustomInventoryScreen`: 모두 **시야 필요(true)**.
  - `ContainerEntity.chestVehicleDestroyed`(상자 수레·상자 배 파괴): `doEntityDrops` 가 꺼져 있으면 바닐라가 먼저 return 하므로 **분노도 없다**. 부순 쪽이 플레이어일 때만.
  - 깔때기 수레는 분노시키지 않는다(바닐라도 상자 수레만).
- 엔티티 없는 레벨을 위해 `BlockLevel` 에 `spawnTadpole`·`spawnBabySniffer`·`angerNearbyPiglins` 기본 빈 메서드를 두고 `ChunkLevel` 에서 구현했다(`spawnBabyTurtle`·`spawnSilverfishAt` 와 같은 방식).
- **고친 버그**: `PiglinAi.setAngerTarget` 이 대상을 `(Entity)` 로 캐스팅해 UUID 를 읽어, 대상이 **플레이어면 ClassCastException** 이었다(Furnace 플레이어는 `Entity` 가 아니다). 지금까지 호출부가 없어 드러나지 않았다. `uuidOf(Targetable)` 로 나눠 고쳤다.
- 탈것 파괴에서 부순 쪽을 알아야 해서 `AbstractMinecart.destroy(Damage)` → `destroy(Damage, Targetable)`, `Boat.destroy()` → `destroy(Targetable)` 로 넓혔다(`hurtBy` 가 이미 attacker 를 받는다).
- **금 방어구와 분노**: `isWearingGold` 는 피글린이 플레이어를 보자마자 공격하는 대상(NEAREST_VISIBLE_ATTACKABLE_PLAYER)에서만 빼고, `angerNearbyPiglins` 는 한가함·시야만 본다(clo 바이트코드의 필터 람다 2개 + setAngerTarget). 그래서 금을 입어도 상자를 열거나 금을 캐면 화낸다(봇 시험에서 금 투구 착용 상태로 angry_at 확인).
- **시험 교훈**: 금을 걸치지 않은 플레이어가 가까이 있으면 피글린이 바로 FIGHT 로 가 `isIdle` 이 거짓이라 분노가 걸리지 않는다(바닐라도 같다). 시험에서는 플레이어에게 금 투구를 입히고, "IDLE ∧ 플레이어를 봄"이 될 때까지 틱을 돌린다. 오버월드에서는 피글린이 약 300틱 뒤 좀비 피글린으로 변하므로 폴링 상한을 그 안(200틱)에 둔다. 시험: `entity/HatchSpawnAndPiglinAngerTest`(10개).

## 2026-11-03 1.21 효과 발동 4종(wind_charged·weaving·oozing·infested)
- 바닐라 1.21.1 바이트코드(`javap -c -p`) 대조: 효과 클래스 bsi(돌풍)·bsh(거미줄)·bsc(슬라임)·brv(좀벌레), 난수 함수는 bsb 의 람다(거미줄 randomBetweenInclusive(2,3), 슬라임 2 고정, 좀벌레 확률 0.1·randomBetweenInclusive(1,2)). 구현은 `effect/MobEffectTriggers`.
- **호출 지점**: `LivingEntity.remove(KILLED|DISCARDED)` → triggerOnDeathMobEffects(효과마다 onMobRemoved 후 효과 맵 비우기, 발동은 KILLED 만). 몹은 죽고 20틱 뒤(tickDeath) 시체 자리에서. `LivingEntity.hurt` 끝(죽음·피격 소리 뒤, 발전 과제 앞)에서 막지 않았으면 onMobHurt.
- **플레이어**: 바닐라는 리스폰(`PlayerList.respawn` → `removePlayerImmediately(KILLED)`) 때 발동한다 — 죽은 순간이 아니다. Furnace 는 리스폰 요청 때 죽은 자리 region 에서 발동한 뒤 리스폰 위치를 찾는다. 끝 관문 귀환(CHANGED_DIMENSION)은 발동 안 함. 거미줄은 플레이어면 mobGriefing 과 무관.
- **난수 순서**: 돌풍 반경 3 + 대상 nextFloat×2. 거미줄은 randomInCube(15 번, 뽑을 때마다 x·y·z nextInt(3) — 다 차면 더 안 뽑음) 후 바닐라 `Vec3i.hashCode` 순 HashSet 순서로 설치·levelEvent 3018. 슬라임은 레벨 난수로 yRot. 좀벌레는 대상 난수 nextFloat(확률) → 마리 수 → (마리마다) 방향 randomBetween(−π/2, π/2), 레벨 난수 yRot, 넣은 뒤 entity.silverfish.hurt(레벨 난수 씨앗). 속도 회전은 JOML 1.10.5 `Vector3f.rotateY`(FASTMATH 꺼짐, cosFromSin)를 그대로 옮겼다(바이트코드 대조).
- **같이 고친 것**: 브리즈 윈드 차지 소리가 `entity.wind_charge.wind_burst` 였는데 바닐라 cog 는 `entity.breeze.wind_burst`(avp.cQ) — 돌풍 효과와 같은 소리로 정정. 거미줄 속 weaving 몹 감속 (0.5, 0.25, 0.5). `Explosion.withSourcePlayer`: 원인이 플레이어인 폭발에서 그 플레이어를 밀림 대상에서 뺀다(Level.getEntities(source, …)).
- **가정·한계**: 플레이어 좀벌레 속도는 `Player.serverMovement()`(서버가 아는 근사 deltaMovement)로 계산 — 바닐라 ServerPlayer 의 서버 deltaMovement 와 같다는 보장 없음. 효과 입자 목록(LivingEntity DATA_EFFECT_PARTICLES) 동기화는 원래 없다(이 효과들의 전용 입자 small_gust·item_cobweb·item_slime·infested 도 안 보임). `/effect` 명령은 지금 플레이어만 대상으로 받는다(몹은 소환 NBT `active_effects` 로 걸어야 함).
- 시험: `MobEffectTriggersTest`(11개). 봇 시험(오프라인 서버, `tools/bot-test` 방식, 스크립트 pr16.js): 몹 oozing → 슬라임 2, 몹 weaving → 거미줄 2(바닥 층만), mobGriefing=false 몹 → 0, wind_charged → 폭발 패킷 1(반경 4.11, breeze 돌풍 소리 id 198)·옆 좀비 +x 로 밀림, 몹 infested → 10번 안에 좀벌레, 플레이어 oozing·weaving(mobGriefing=false)·infested → 리스폰 뒤 슬라임 2·거미줄 3·좀벌레. 봇은 입자·소리 재생은 못 본다.

## 2026-11-03 봇 접속 시험에서 찾은 것
- 엔티티 디스크 덧붙이기(`World.appendEntitiesAsync` → `AnvilWorldSource.appendEntities`): 빈 `Entities` 리스트는 디스크에서 TAG_END 리스트로 읽힌다. 거기에 compound 를 `add` 하면 쓰기에서 `미지원 태그: 0` 으로 실패해 덧붙이려던 엔티티가 사라졌다. 빈·다른 종류 리스트면 compound 리스트를 새로 만든다(바닐라 ListTag 는 빈 리스트에 첫 원소가 들어올 때 종류가 정해진다).
- F3+I 엔티티·블록 엔티티 질의 응답(`tag_query`)은 `NbtTags.freeze` 꼴(배열 = byte[]/int[]/long[])로 보낸다. `thaw`(NbtArray)는 `/data` 경로 연산 전용이라 `NbtWriter` 가 쓰지 못한다.
- 확인함(봇): 레지스트리 전송은 클라이언트의 알려진 팩 응답과 상관없이 내용을 생략한다(바닐라는 팩을 모르는 클라이언트에 내용을 보낸다) — 바닐라 클라이언트는 영향 없음, 고치지 않음.

## 효과 입자(2026-11-04)
- 몸에서 나는 효과 입자는 서버가 뿌리지 않는다. 바닐라 1.20.5+ 는 `LivingEntity.DATA_EFFECT_PARTICLES`(엔티티 데이터 10, 직렬화기 18 = `List<ParticleOptions>`)와 `DATA_EFFECT_AMBIENCE_ID`(11, boolean)를 보내고 클라이언트가 그 목록에서 골라 뿌린다. 예전 Furnace 는 10 을 1.20.4 식 int 색으로 정의만 해 두고 보내지 않았다.
- 목록 = 입자를 보이는 효과마다 `MobEffect.particle(ambient)`. 기본은 효과 색 entity_effect(알파 255, 환경 효과면 38 = floor(0.15·255)), 고유 입자 6종: trial_omen·raid_omen·wind_charged→small_gust·weaving→item_cobweb·oozing→item_slime·infested(1.21.1 `bsb`·`brx` 바이트코드 확인). 효과가 없으면 목록만 비우고 11 은 그대로 둔다(바닐라 removeEffectParticles).
- 몹은 `LivingEntity.updateInvisibilityStatus`(효과가 바뀔 때)와 NBT 불러오기에서, 플레이어는 `Player.syncLivingFlags`(매 틱, 바뀌었을 때만 보냄)에서 맞추고 페어링 데이터에도 넣는다.
- 즉시 효과는 바닐라 HealOrHarmMobEffect 처럼 "치유 쪽이면 4<<단계, 피해 쪽이면 6<<단계" — 언데드는 즉시 회복이 6 피해, 즉시 피해가 4 치유(전엔 숫자가 뒤바뀌어 있었다).

## 2026-11-03 몹 낙하 위치(FallLocation) 기록
- 바닐라(btn·brp): `CombatTracker.recordDamage` 가 맞은 순간 `FallLocation.getCurrentFallLocation(mob)` 을 기록한다. 몹도 플레이어와 같다. 지금까지 몹은 null 을 넣어 사다리·덩굴 낙하사가 `death.fell.accident.generic` 으로만 나왔다.
- `LivingEntity.currentFallLocation()`: `lastClimbablePos`(onClimbable 이 이미 기록하던 칸)가 있으면 **그 칸의 지금 블록**으로 이름을 정하고(부서져 공기면 other_climbable — 바닐라와 같음), 없고 물속이면 water. 표는 brp.a(dtc): 사다리·`#trapdoors` → ladder, vine → vines, 우는/뒤틀린 덩굴(+plant), 비계, 그 밖 other_climbable.
- `LivingEntity.checkFallDamage` 를 덮어 땅에 닿으면 `lastClimbablePos` 를 지운다(바닐라 btn.a(DZLdtc;Ljd;)V 끝). 벌은 바닐라처럼 checkFallDamage 가 비어 있어 지워지지 않는다.
- **아직 다른 점**: 바닐라 LivingEntity.checkFallDamage 앞부분(물 상태 갱신, onChangedBlock, 안전 낙하 거리 초과 시 착지 블록 입자)은 넣지 않았다. ~~다락문을 사다리로 쓰는 오르기(`trapdoorUsableAsLadder`)는 몹·플레이어 모두 없다. 플레이어(`PlayerVitals`)는 매달린 순간의 블록 이름을 저장해, 사다리가 부서진 뒤 떨어져도 ladder 로 나온다(바닐라는 other_climbable).~~ → 아래 2026-11-03 다락문 절에서 고침.
- 시험: `MobFallLocationTest`(사다리·덩굴·부서진 사다리·일반 낙하·블록 표).

## 2026-11-03 다락문 사다리 오르기·플레이어 낙하 위치 칸 저장
- 바닐라(btn.p_ = LivingEntity.onClimbable): 관전자가 아니고, 발 칸이 `#climbable` 이면 매달린다. 아니면 그 칸 블록이 `TrapDoorBlock`(instanceof — 철·구리 다락문 포함)이고 `trapdoorUsableAsLadder`(btn.c(jd,dtc)): 열림(open)이고 바로 아래 칸이 사다리(`Blocks.LADDER`)이며 사다리 facing == 다락문 facing 이면 매달린다. 두 경우 모두 `lastClimbablePos` 를 그 칸으로.
- Furnace: 판정을 `TrapDoorBlock.isTrapDoor` / `TrapDoorBlock.usableAsLadder` 로 두고 몹(`LivingEntity.onClimbable`)과 플레이어(`PlayerProbe.climbable`) 모두 쓴다. 몹 `onClimbable` 에 관전자 검사도 넣었다(몹은 언제나 false 라 동작 차이 없음).
- 플레이어 낙하 위치: 이전에는 매달린 순간의 블록 이름을 저장해, 사다리가 부서진 뒤 떨어져도 ladder 였다. 이제 `PlayerVitals.lastClimbablePos`(칸)를 저장하고, 맞은 순간 그 칸의 **지금** 블록으로 이름을 정한다(`LivingEntity.fallLocationOf`, 바닐라 FallLocation.getCurrentFallLocation). 땅에 닿거나 리스폰하면 지운다.
- **아직 다른 점**: 블록은 플레이어 주인 region 스레드에서만 읽는다(`Region.getBlock`, 미로드 청크는 공기 → other_climbable). 주인 스레드가 아닌 곳에서 피해가 기록되면 매달린 순간의 이름으로 물러선다(바닐라에는 없는 경로).
- 시험: `PlayerFallLocationTest`(다락문 판정 표·부서진/바뀐 칸·물러섬), `MobFallLocationTest.openTrapdoorAboveLadderIsClimbable`, `TrapdoorLadderFallE2ETest`(실제 소켓 클라이언트로 오프라인 접속 → /setblock 으로 사다리·다락문 → /tp 로 다락문 칸에 매달림 → 옆으로 비켜 25칸 낙하 → 사망 화면 `death.fell.accident.ladder`, 미리 다락문을 부수면 `other_climbable`). 이 접속 시험은 블록 레지스트리를 `World.setRegistries` 로 걸어야 `PlayerProbe` 가 돈다(`TestServers.core` 는 걸지 않는다).

## 2026-11-03 투사체-블록 반응(Block.onProjectileHit)·projectilesCanBreakBlocks
- 근거: `javap -c -p` 바이트코드. `cnp`(Projectile) `a(dcw, jd)` = mayInteract, `b(dcw)` = mayBreak. 훅 `a(dcw, dtc, ewy, cnp)` 를 가진 블록은 10개 — `deq` 양초 바탕, `dew` 자수정, `dfu` 종, `dfv` 큰 흘림잎, `dgm` 모닥불, `dhd` 후렴화, `dhy` 장식 단지, `dlq` 뾰족한 점적석, `doi` TNT, `dog` 과녁(이미 있음).
- `Projectile.mayInteract(level, pos)`: 쏜 쪽이 플레이어면 `ServerPlayer.mayInteract` = 스폰 보호 밖(`InventoryHost.mayInteract`) && 월드 보더 안, 아니면 쏜 쪽이 없거나 `mobGriefing`. `Entity.mayInteract` 의 재정의라 가루눈·가마솥(불붙은 엔티티) 판정에도 투사체면 이 규칙이 걸린다(바닐라와 같음).
- `Projectile.mayBreak(level)`: 종류가 `#impact_projectiles`(화살류·폭죽·눈덩이·화염구·소형 화염구·달걀·삼지창·드래곤 화염구·위더 해골·바람 구슬 둘) && `projectilesCanBreakBlocks`. 호출처는 후렴화·장식 단지·뾰족한 점적석 셋뿐.
- 블록별: 양초는 `isOnFire && canBeLit` 만(mayInteract 안 봄 — 몹 화살도 mobGriefing 과 무관하게 켠다). 모닥불은 `isOnFire && mayInteract && !lit && !waterlogged` → 플래그 11. TNT 는 `isOnFire && mayInteract` → 쏜 쪽이 생물이면 주인으로 점화 후 `removeBlock`. 자수정은 hit 다음 chime, 높이마다 레벨 난수 `0.5 + nextFloat×1.2`. 종은 쏜 쪽이 플레이어면 그 플레이어로 `onHit(…, checkHit=true)`(울렸고 플레이어면 bell_ring 통계). 큰 흘림잎은 바로 `full` + tilt_down. 후렴화는 `mayInteract && mayBreak` → `destroyBlock(pos, true, 투사체)`. 장식 단지는 같은 조건에서 `cracked=true`(플래그 4) 뒤 `destroyBlock(pos, true, 투사체)` → 조각 4개. 점적석은 + `ThrownTrident` && 속도 길이 > 0.6 → `destroyBlock(pos, true)`(엔티티 없음).
- **피뢰침**: 1.21.1 `LightningRodBlock` 에는 onProjectileHit 가 없다. 집전 삼지창 벼락은 마법 효과 `hit_block`(channeling.json)이고 `ThrownTrident.onHitBlock` 에 이미 있다. 블록의 TODO 를 설명 주석으로 바꿨다.
- **함께 고친 것**: 바닐라 `onHitBlock` 재정의는 모두 `super.onHitBlock` 을 먼저 부르는데(바이트코드로 확인 — cnl·cno·cns·cnt·cob·cof·cnk), Furnace 의 `SmallFireball`·`FishingHook`·`ShulkerBullet`·`AbstractWindCharge`·`LlamaSpit`·`ThrownPotion` 은 안 불러 블록 훅(과녁 포함)이 빠져 있었다.
- 종 `onHit` 은 바닐라처럼 울렸고 플레이어가 있을 때만 통계를 준다(이전엔 null 플레이어면 NPE 가능). `attemptToRing` 원인은 Object(플레이어·엔티티).
- **차이(가정)**: 투사체가 부순 블록의 전리품 문맥 `this` 엔티티는 비어 있다(`BlockLevel.destroyBlockByEntity` — 게임 이벤트 원인만 투사체). 바닐라 후렴화·장식 단지 전리품 표는 엔티티 조건이 없어 관측 차이는 없을 것.
- 시험: `ProjectileBlockHitTest`(15개).
- 봇 시험(오프라인 서버 + mineflayer, `/summon arrow … {Motion:[…],Fire:200s}`, 2026-11-03): TNT 점화(블록 사라지고 점화된 TNT 1개), 꺼진 모닥불 켜짐, 양초 켜짐·물에 잠긴 양초 그대로, 큰 흘림잎 full → 몇 초 뒤 none, 후렴화·장식 단지 파괴(아이템 3개)·`projectilesCanBreakBlocks false` 면 그대로, 눈덩이로 후렴화 파괴 확인. 자수정은 소리 2개(높이 무작위), 종 자리에서도 소리를 받았으나 mineflayer 소리 이름 표가 어긋나 이름 대조는 못 했다. 양초·흘림잎은 모양이 작아 옆에서 쏜 화살이 지나간다(위에서 쏘아 확인).

## 2026-11-03 엔티티 블록 훅(entityInside·stepOn·fallOn) — 남은 TODO(엔티티) 중 블록 쪽
- 근거: `javap -c -p` 바이트코드. 훅 서술자: entityInside `a(dtc, dcw, jd, bsr)`, stepOn `a(dcw, jd, dtc, bsr)`, fallOn `a(dcw, dtc, jd, bsr, F)`. 클래스: `dfv` 큰 흘림잎, `dlq` 점적석, `dht` 작물(`cki` = 파괴수), `dln` 주전자 풀, `doc` 달콤한 열매 덤불, `dpc` 수련잎(`cov` = 보트), `djc` 개구리알, `dma` 레드스톤 광석, `dor` 거북 알, `dgi` 버튼(`cnd` = AbstractArrow), `dkr` 마그마.
- 큰 흘림잎: tilt none && 땅을 디뎠고 발 y > 칸 y + 0.6875 && 신호 없음 → unstable(소리 없음, 10틱 뒤 partial).
- 점적석 fallOn: 위를 향한 `tip`(tip_merge 아님)이면 `causeFallDamage(거리 + 2, 2, stalagmite)`, 아니면 기본. 이를 위해 `Entity.causeFallDamage` 에 피해 종류 인자를 더했다(바닐라 3인자와 같게, 재정의 11곳 모두).
- 작물·주전자 풀: 파괴수 && mobGriefing → `destroyBlock(pos, true, 파괴수)`. 달콤한 열매: 여우·벌이 아닌 생물은 `makeStuckInBlock(0.8, 0.75, 0.8)`, 나이 > 0 이고 xOld/zOld 와 다르고 |dx| 또는 |dz| ≥ 0.003F 이면 피해 1. 수련잎: 보트 → `destroyBlock(pos, true, 보트)`. 개구리알: 떨어지는 블록 → `destroyBlock(pos, false)`. 레드스톤 광석: 웅크리지 않았으면 켬. 거북 알: stepOn 웅크리지 않음 1/100, fallOn 좀비(종류 포함)가 아니면 1/3, 깰 수 있는 것 = 거북·박쥐 아님 && 생물 && (플레이어 || mobGriefing), 레벨 난수 `nextInt(chance)`.
- 버튼: `canButtonBeActivatedByArrows`(나무) && 꺼짐 → checkPressed: 모양의 감싸는 상자 안 첫 AbstractArrow 가 있으면 켬(게임 이벤트 출처 = 화살), 예약 틱마다 다시 보고 없으면 끈다. 화살이 날아가며 지난 칸의 entityInside 를 부르도록 `AbstractArrow.tick` 끝에 `checkInsideBlocks()` 를 더했다(바닐라 `aT()` — 그동안 빠져 있어 화살이 불·나무 압력판·인계철선에도 안 걸렸다).
- **플레이어 쪽**(플레이어는 Entity 가 아니다): `Region.checkInsideBlocksPlayer`(옛 checkInsideCampfires)에서 큰 흘림잎(`entityInsideAt`)·달콤한 열매(틱 머리 위치 = xOld 와의 가로 변위, `hurtsPlayer`), `PlayerMovementEmission.stepOn` 에서 레드스톤 광석·거북 알·**마그마**(플레이어 hot_floor 피해가 그동안 없었다, 서리 보행자 장화면 면역), 착지 때(`PlayerVitals.landedOnLastMove`) 거북 알 fallOnPlayer(1/3), 석순 끝 착지는 Region 이 착지 칸 상태를 보고 `landingStalagmite` 로 알려 PlayerVitals 가 거리 + 2·배수 2·stalagmite 피해를 준다. 착지 칸은 `PlayerProbe.onPosLegacyPos`.
- **차이(가정)**: 플레이어 끼임(덤불·거미줄 속도)은 클라이언트가 계산하므로 서버는 하지 않는다. 플레이어 entityInside 는 틱마다 마지막 위치로 한 번(바닐라는 이동 패킷마다 — 무적 시간 때문에 피해 횟수는 같을 것). 모닥불의 서리 보행자 면역(burn_from_stepping)은 아직 플레이어에게 없다. 몹의 `isSteppingCarefully` 는 늘 거짓(바닐라는 isShiftKeyDown — 몹은 웅크리지 않으므로 같음).
- 남은 TODO(엔티티)(이번에 안 한 것): 개구리알 올챙이·스니퍼 알 아기 스니퍼 생성, 독서대 아이템 엔티티 위치, 가루눈 엔티티별 충돌 모양, `Block.pushEntitiesUp`(흙길·경작지·점적석·눈 등), 피글린 분노(상자·통·셜커 상자·엔더 상자), 비교기 아이템 액자, 지옥문 좀비 피글린 스폰, 엔드 관문 순간 이동.
- 시험: `EntityBlockHookTest`(10개).
- 봇 시험(오프라인 서버 + mineflayer, 평지, `gamerule naturalRegeneration false` 로 회복을 끄고 봐야 피해가 보인다 — 평화 난이도 회복이 피해를 가린다): 마그마 걷기 피해·웅크리면 없음, 나이 2 덤불을 지나며 20 → 16, 덤불 안 좌우 15 → 4, 나이 0 은 없음, 레드스톤 광석 밟자 lit, 흘림잎 위에 서자 unstable → partial(0.5초) → full(1초)·플레이어가 떨어짐, 거북 알 20초 걷기로 9칸 중 2칸 줄고·12번 떨어지자 4개 모두 깨짐·웅크려 밟으면 그대로, 석순 끝 6칸 낙하 20 → 12(풀 위 20 → 17), 나무 버튼은 화살에 켜지고 꽂혀 있는 동안 유지·화살을 지우면 꺼짐·돌 버튼은 안 켜짐, 화살로 나무 압력판 켜짐, 보트 자리 수련잎 사라짐, 떨어진 모래에 개구리알 사라짐, 파괴수 10초에 밀 81 → 60(mobGriefing false 면 81 그대로). `/setblock … thickness=frustum` 은 바닐라처럼 updateFromNeighbourShapes 로 tip 이 된다(끝이 아닌 점적석 착지는 단위 시험으로만 확인).

## 2026-11-04 몹 장착 이벤트 — 불변 ItemStack 과 "제자리 수정"
- 바닐라가 장비 스택을 그 자리에서 고치는 곳(hurtAndBreak·setDamageValue·shrink·쇠뇌 CHARGED_PROJECTILES·enchantItemFromProvider·copyAndClear)을 Furnace 는 새 스택을 넣어 흉내 낸다. 이런 곳은 `setItemSlot` 이 아니라 `updateItemSlot` 을 쓴다 — `setItemSlot` 은 바닐라처럼 `onEquipItem`(장착 소리·equip/unequip, 엔티티 난수 nextLong)을 부른다.
- 새 호출 지점을 쓸 때는 바닐라 원본이 `setItemSlot`/`setItemInHand` 를 부르는지 바이트코드(`a:(Lbsy;Lcuq;)V`, `a:(Lbqq;Lcuq;)V`)로 확인한다. 함정: `Mob.convertTo` 는 새 몹에만 setItemSlot, 옛 몹은 copyAndClear(이벤트 없음). 여우가 다 먹어 비면 바닐라는 setItemSlot 을 안 부른다.
- onEquipItem 은 `firstTick` 이면 아무것도 안 하므로 스폰(finalizeSpawn) 중 장비 변경은 이벤트·난수가 없다.

## 2026-11-04 플러그인 API 3단계 이벤트(22종)
- 추가: 플레이어 `PlayerToggleSneak/Sprint/FlightEvent`·`PlayerItemHeldEvent`·`PlayerSwapHandItemsEvent`·`PlayerItemConsumeEvent`·`PlayerChangedWorldEvent`·`PlayerKickEvent`·`PlayerExpChangeEvent`·`PlayerLevelChangeEvent`·`PlayerAdvancementDoneEvent`·`PlayerBedEnter/LeaveEvent`, 엔티티 `EntityExplodeEvent`·`ProjectileLaunchEvent`·`ProjectileHitEvent`·`ItemSpawnEvent`·`EntityTameEvent`·`EntityBreedEvent`·`EntityTargetEvent`, 블록 `BlockExplodeEvent`, 서버 `ServerListPingEvent`. 서버 입구는 `GameEvents`(구독자 없으면 이벤트 객체도 안 만든다, `has(type)`).
- **바닐라 구멍 메움**: F 키 손 바꾸기(`player_action` 6)가 아예 처리되지 않았다. 바닐라 handlePlayerAction 대로 관전자 제외, 보조 ← 주, 주 ← 보조, stopUsingItem(플레이어 소유 스레드). 클라이언트는 예측하지 않는다.
- **스레드**: 웅크림·달리기·비행·칸 변경은 netty 에서 바로 상태를 바꾸던 것을, 해당 이벤트 구독자가 있을 때만 `Player.runOnOwner` 로 넘겨 소유 스레드에서 묻고 적용한다(구독자가 없으면 예전과 같다). 발전 과제 달성은 준 스레드(인벤토리 변화로 오는 레시피 과제는 netty 스레드), 킥은 요청한 스레드, 서버 목록은 netty 스레드.
- **Bukkit 과 같게 한 것**: 투사체·아이템 스폰은 `EntitySpawnEvent` 하위(그래서 `EntitySpawnEvent` 는 final 이 아니다). 폭발 이벤트는 엔티티 피해·밀림 뒤, 섞은 뒤 블록 부수기 전(취소면 블록·불·폭발 패킷 없음). 길들이기는 확률 난수 뒤(취소면 실패 연기). 번식은 새끼를 만든 뒤(취소면 사랑 상태 유지). 대상 지정은 대상이 바뀔 때만, 취소면 하위 클래스 부수 효과도 건너뛴다(`Mob.setTarget` 이 boolean). 레벨 변화는 CraftBukkit 처럼 틱마다 비교. 경험치 이벤트는 구슬 줍기만(`/xp` 제외). 비행 취소면 능력 패킷을 다시 보낸다. 칸 변경 취소면 이전 칸을 다시 보낸다. 같은 계정 중복 접속 킥은 취소 불가라 이벤트 없음(`Player.kickForced`).
- **바닐라 쪽 정렬**: 폭발 패킷의 칸 목록을 섞은 뒤 보낸다(바닐라 ServerLevel.explode 순서, 패킷은 난수를 쓰지 않아 난수 순서는 그대로).
- **아직 없는 것(의도)**: 폭발 `yield`, 소비 아이템 바꾸기(`setItem`), 침대 진입 실패 결과(`BedEnterResult` — 검사를 다 통과한 경우만 발화), 브레인 몹(피글린·호글린·워든)의 공격 기억 대상 지정, 개구리·거북·스니퍼·아르마딜로 등 알·특수 번식, `CreatureSpawnEvent`(스폰 이유), 블록 물리·레드스톤·피스톤·유체·성장 같은 블록 이벤트와 인벤토리 제작·화로·호퍼 이벤트(다음 묶음).
- **발견(손대지 않음, 가정)**: 바람 폭발(TRIGGER_BLOCK)은 바닐라가 toBlow 를 섞는데(`Util.shuffle`, 레벨 난수) Furnace 는 섞지 않는다 — 레벨 난수 순서가 한 번 다를 수 있다. 확인 후 따로 고칠 것.
- 시험: `PluginApiStage3Test`(서버 단위 10개), 봇 `tools/bot-test/round4/ev.js` + `probe/EventProbe`(33개 확인).

## 탄 채 접속 종료(RootVehicle, 2026-11-04)
- 바닐라 ServerPlayer.addAdditionalSaveData·PlayerList.remove/placeNewPlayer 와 같은 결과: 플레이어 하나만 태운 뿌리 탈것은 그 플레이어 데이터의 `RootVehicle {Attach, Entity}` 로 저장하고, 퇴장 때 승객째 월드에서 뺀다(UNLOADED_WITH_PLAYER). 접속 때 승객째 불러 Attach 의 것에 강제로 태우고, 못 태우면 지운다("Couldn't reattach entity to player"). 그런 탈것은 청크 엔티티 저장에서 빠진다(Entity.shouldBeSaved — `Entity.savedWithChunk`).
- 구조(Furnace 자체): 플레이어 본체 저장은 연결 스레드, 탈것은 주인 region 스레드가 떠서 같은 플레이어 데이터 실행기에 `RootVehicle` 만 덧쓴다(`PlayerData.saveRootVehicle`). 본체 저장은 이 키를 건드리지 않으므로 두 저장의 순서가 바뀌어도 같다. 자동 저장은 `runOnOwner` 로 탈것을 다시 쓰거나(탄 동안) 지운다(내렸으면). 종료(`World.flushAll`)는 청크를 쓰기 전에 끊긴 플레이어의 탈것부터 옮긴다.
- 차이: 끊긴 직후 아주 빨리 다시 들어와 region 이 퇴장을 처리하기 전에 데이터를 읽으면 그 접속에서는 타지 않고, 탈것은 다음 접속 때 돌아온다(복제는 없다 — 같은 UUID 가 월드에 있으면 넣지 않는다).

## 2026-11-04 탄 동안 받은 걸음 패킷(봇 3차 접속 시험)
- 바닐라 `handleMovePlayer` 는 `player.isPassenger()` 면 패킷 위치를 버리고 회전만 받는다(검증 없음). Furnace 는 연결 스레드가 탄 여부를 보고 `acceptRiding` 으로 보내지만, **타기 직전에 검증 대기열에 들어간 패킷**은 region 이 탄 뒤에 꺼내 검증해 `moved wrongly` 로 되돌렸다(탈것 위 자리와 땅 위 위치 비교).
- `Region.processMoves` 에서 `p.vehicle() != null` 이면 위치를 버리고 회전만 받는다. 시험 `RegionMoveValidationTest.movePacketQueuedBeforeMountingIsNotRejectedAfterMount`.
- 같은 종류의 함정: 연결 스레드가 본 상태(탔음·잠·텔레포트 대기)와 region 이 검증할 때의 상태가 다를 수 있다. 검증 대기열을 꺼내는 쪽에서 상태를 다시 본다.
- 파괴수 나뭇잎(U2): 바닐라 `cki.m_`(Ravager.aiStep) 디컴파일 대조로 Furnace 와 같음 확인. 1.21.1 에는 파괴수 전용 길찾기 클래스가 없고 `setPathfindingMalus(LEAVES, 0)` 뿐이다.
- 디컴파일: Vineflower 1.10.1 은 구글 Maven 미러(`maven-central.storage-download.googleapis.com`)에서 받을 수 있다. 난독화 클래스는 `main` 의 `tools/vanilla-data/server-1.21.1/*.class`, 이름은 `EntityType`(bsx) 의 `ldc "ravager"` 다음 invokedynamic 람다로 찾는다.

## 2026-11-06 플러그인 API 3단계 둘째 묶음(블록·인벤토리 이벤트 15종)
- 추가: 블록 `BlockPhysicsEvent`·`BlockRedstoneEvent`·`BlockPistonExtend/RetractEvent`(바탕 `BlockPistonEvent`)·`BlockFromToEvent`·`BlockGrowEvent`·`BlockBurnEvent`·`BlockIgniteEvent`·`LeavesDecayEvent`, 인벤토리 `PrepareItemCraftEvent`·`CraftItemEvent`(`InventoryClickEvent` 하위 — 그래서 `InventoryClickEvent` 는 final 이 아니다)·`FurnaceBurnEvent`·`FurnaceSmeltEvent`·`InventoryMoveItemEvent`. 입구는 같은 `GameEvents`(구독자 없으면 이벤트 객체를 만들지 않는다). `BlockLevel.gameEvents()` 기본은 NONE.
- **발화 자리(CraftBukkit 과 같게)**: 물리는 `NeighborUpdater.executeUpdate`(neighborChanged)와 `executeShapeUpdate`(모양 갱신) 둘 다 — 취소면 그 갱신을 건너뛴다. 레드스톤은 가루 `updatePowerStrength`, 레버 `pull`, 버튼 `press`·`checkPressed`, 압력판 `checkPressed`, 램프(켜기·끄기). 레버·버튼·램프는 켜짐/꺼짐만 본다. 피스톤: 늘이기·끈끈이 줄이기는 `moveBlocks` 에서 resolve 뒤(밀 칸 + 부술 칸), 일반 피스톤 줄이기는 `checkIfExtend` 에서 블록 이벤트 걸기 전(빈 목록). 끈끈이 줄이기 취소면 머리는 들어가고 블록만 안 끌려온다. 유체: 아래 흐름 취소면 그 틱 옆 흐름도 없다. 성장: `B.grow`(handleBlockGrowEvent) — 작물(무작위·뼛가루), 줄기(나이·열매 칸), 사탕수수(취소돼도 나이 0 — CraftBukkit), 선인장, 네더 사마귀, 코코아, 달콤한 열매. 불: `checkBurnOut` 은 확률 난수 뒤, 번짐은 이미 불인 칸 제외, 용암 무작위 틱, 부싯돌(취소돼도 닳음)·화염구(취소돼도 하나 씀, 둘 다 PASS 를 돌려준다).
- 화로: 연료 이벤트는 "불이 꺼져 있고 구울 수 있을 때" — 취소면 불을 켜지 않는다(CraftBukkit 은 틱 전체를 return 해서 막 꺼진 화로의 lit 상태 갱신까지 건너뛰는데, Furnace 는 불 켜기만 건너뛴다). 굽기 이벤트는 결과를 바꿀 수 있고, 구독자가 없을 때는 바닐라처럼 결과 칸을 grow(1) 한다.
- 깔때기: 밀어 넣기(initiatorIsSource=true) 취소면 꺼낸 것을 되돌리고 8 틱 쉬며 다른 칸은 보지 않는다. 빨아들이기 취소면 되돌리고(블록 깔때기는 쉼) 다음 칸을 계속 본다(CraftBukkit 과 같다). 용기 종류는 `Inv.typeKey()`.
- 제작: `PrepareItemCraftEvent` 는 `CraftingLogic.slotChangedCraftingGrid`(작업대·인벤토리 2×2, 레시피 없어도 발화) — `MenuPlayer.prepareCraft`. `CraftItemEvent` 는 결과 칸(0번) 클릭이고 결과가 있을 때(QUICK_CRAFT·PICKUP_ALL 제외) `InventoryClickEvent` 대신.
- **아직 없는 것(의도)**: `BlockSpreadEvent`·`BlockFormEvent`·`BlockFadeEvent`(풀·균사·눈·얼음·산호·불 사라짐), 대나무·켈프·덩굴·후렴 꽃 성장, 피스톤 외 레드스톤 부품(중계기·비교기·관측기·감지 레일·덫 철사), 번개·화염구 엔티티·불화살 점화, 모닥불 `BlockCookEvent`, `FurnaceExtractEvent`·`FurnaceStartSmeltEvent`, 깔때기 아이템 엔티티 줍기 `InventoryPickupItemEvent`, 마법부여·모루·양조 이벤트, `InventoryDragEvent`, 제작기(crafter).
- 시험: `PluginApiBlockEventsTest`(서버 단위 10개), 봇 `tools/bot-test/round5/ev.js` + `probe/BlockEventProbe`(31개).
- 끈끈이 줄이기 취소: 머리를 플래그 20(클라이언트 알림 없음)으로 지운 뒤라 취소면 `sendBlockUpdated(front)` 로 알린다 — 봇에서 머리가 남아 보였다.
- **발견(손대지 않음)**: 콘솔 `execute if block <칸> <블록>{NBT}` 의 NBT 조건이 내용과 맞아도 늘 "Test failed" 다(`execute if data block … Items[…]` 는 맞게 동작). 따로 확인할 것.

## 2026-11-06 플러그인 API 3단계 셋째 묶음(번짐·형성·소멸, 마법 부여·모루·양조, 탈것, 낚시·차원문·책, CreatureSpawnEvent)
- 추가: 블록 `BlockFormEvent`·`BlockSpreadEvent`·`BlockFadeEvent`, 인벤토리 `PrepareItemEnchantEvent`(+`EnchantmentOffer`)·`EnchantItemEvent`·`PrepareAnvilEvent`·`BrewEvent`·`BrewingStandFuelEvent`, 탈것 `VehicleEnterEvent`·`VehicleExitEvent`(바탕 `VehicleEvent`)·`EntityMountEvent`·`EntityDismountEvent`, `PlayerFishEvent`·`PlayerPortalEvent`·`EntityPortalEvent`·`PortalCreateEvent`·`PlayerEditBookEvent`, `CreatureSpawnEvent`(안에 `SpawnReason`). 구독자가 없으면 이벤트 객체를 만들지 않고 동작도 그대로다(`B.form/spread/fade` 도우미).
- **번짐·형성·소멸**: 번짐 = handleBlockSpreadEvent(풀·균사체·덩굴·버섯), 형성 = handleBlockFormEvent(흑요석·조약돌·현무암·눈·얼음·콘크리트 가루·비·눈 내림), 소멸 = callBlockFadeEvent(눈 녹음·얼음 녹음·산호 죽음·경작지→흙·불 꺼짐·덮인 풀). 용암이 흑요석으로 굳는 것을 취소하면 옆의 물이 용암 칸을 대신 채운다(`canBeReplacedWith`, 바닐라 규칙).
- **마법 부여**: 제안은 단서(clue) 마법이 있는 칸만 CraftBukkit 처럼 내보낸다. `EnchantItemEvent` 에서 마법 맵을 바꾸면 바꾼 대로 붙고, 레벨·청금석 값도 이벤트 값. 취소면 레벨·청금석이 그대로.
- **모루**: `PrepareAnvilEvent` 가 결과 칸·비용을 바꿀 수 있고(`firePrepare`), 결과가 비었을 때도 발화한다.
- **탈것**: `VehicleEnter/Exit` 가 `EntityMount/Dismount` 보다 먼저. **스스로 내리는 경우만 취소할 수 있다**(`cancellable`). 탈것이 사라짐·내쫓김·차원 이동·순간이동으로 강제로 내릴 땐 취소해도 무시한다(`stopRidingForced`). 이벤트는 보트·광산 수레만 `Vehicle` 로 친다.
- **스폰**: `Entity.spawnReason("…")` 로 CraftBukkit SpawnReason 이름을 달아 `CreatureSpawnEvent` 로 보낸다(자연·소환·번식·슬라임 분열·눈 골렘 짓기·스포너·알·변환 등).
- **아직 없는 것(의도)**: 눈 골렘 눈 흔적 `EntityBlockFormEvent`, 스컬크 번짐, `EntityPortalEnterEvent`, 차원문 목적지 좌표(비동기 계산이라 대상 차원만 준다), `PortalCreateEvent` 의 NETHER_PAIR 이유, `BrewingStartEvent`, 켈프·대나무 성장, 이끼·뼛가루 퍼짐 일부.
- 시험: `PluginApiFormEventsTest`(서버 단위 7개), 봇 `tools/bot-test/round8/ev.js` + `probe/Stage3cProbe`(구역 form enchant anvil brew vehicle fish portal book spawn, 모두 PASS).
- **봇 시험 함정**: mineflayer `putTargetItem`/`rename`/`enchant` 는 이 서버에서 timeout 이 나므로(서버는 정상) `clickWindow`·원시 패킷(`name_item {name}`, `enchant_item`)으로 보낸다. 취소 모드는 블록을 놓기 *전에* 켜야 하고(무작위 틱이 빠르면 놓는 도중 이벤트가 난다), `mark()` 는 명령 앞에 둔다. 슬라임은 peaceful 에서 사라지니 분열 시험만 easy.


## 2026-11-06 피해 계산 단계 API (`EntityDamageCalculateEvent`)
- 위시리스트의 `EntityDamageCalculateEvent`/`DamageModifierEvent` 를 하나로 냈다. 방어력→저항→보호 인챈트→엔티티 고유(마녀)→흡수 체력 단계별로 읽고 바꾼다(`docs/api/entity-world-design.md` 4.1). 기본 결과는 바닐라와 비트 단위로 같다.
- 서버 쪽: `LivingEntity.actuallyHurt`·`PlayerVitals.actuallyHurt` 가 `GameEvents.wantsDamageCalc()` 가 참일 때만 `StageRules`(그 엔티티의 현재 속성·효과·장비로 바닐라 식을 계산)를 만들어 이벤트를 부르고, 구독자가 없으면 예전 경로 그대로. 흡수 체력 소비는 `input(ABSORPTION) - output(ABSORPTION)` 으로 같은 식.
- 마녀의 `getDamageAfterMagicAbsorb` 오버라이드는 `LivingEntity.entityStageApplies/entityStage` 훅으로 바꿨다(결과 같음: 보호 인챈트 뒤, bypasses_effects 여도 적용).
- 방어구 내구도(`Player.hurtArmor`)는 이벤트 전에 닳는다 — 바닐라가 내구도를 먼저 깎고 방어력을 읽는 순서를 지키려는 것. 그래서 `base` 를 바꿔도 내구도 소모는 안 바뀐다.
- 알아 둘 것: bypass 태그 피해(예: `generic`)는 방어력 단계가 `applies=false` 다. 봇 시험에서 `generic` 으로 방어력 시험을 하다 헷갈렸다 — 방어력 시험은 `cactus`·`mob_attack` 으로.
- 봇 함정: 피해 직후 체력을 읽을 때 `naturalRegeneration` 을 꺼야 한다(체력 비교가 틀어진다).
- 시험: api 8 + server `DamageStagesTest` 8 + `EntityWorldApiTest` 1, 봇 `tools/bot-test/round9/damage.js` 8항목 전부 통과.

## 2026-11-06 전달체(conduit) 블록 엔티티
- 그동안 `ConduitBlock` 은 물 잠김만 있고 블록 엔티티가 없어 전달체를 지어도 아무 일도 없었다. `ConduitBlockEntity` 를 바닐라 1.21.1 `dqt.b(serverTick)` 바이트코드와 대조해 넣었다.
- 40틱마다: 둘레 3×3×3 이 모두 물인지 → 5×5×5 세 축 평면 바깥 고리(42칸)의 프리즈머린·벽돌·어두운 프리즈머린·바다 랜턴 수. 16 이상이면 활성(바뀌면 activate/deactivate 소리), 42 면 사냥.
  활성이면 범위 `수/7*16`(칸 거리, 위로 월드 높이만큼) 안 물·비에 닿은 플레이어에게 전달체의 힘 260틱(앰비언트), 사냥이면 ±8 상자 안 물·비에 닿은 `Enemy` 하나를 레벨 난수로 골라 마법 피해 4 + attack.target 소리. 대상이 바뀌면 블록 갱신(클라이언트 눈). 80틱마다 ambient, 60+0..39틱마다 ambient.short.
- `Target`(int[4] UUID) 저장·로드, 로드 뒤 첫 검사에서 UUID 로 다시 찾는다. 갱신 태그는 saveCustomOnly.
- 차이(region): 대상이 다른 region 으로 넘어가면 이 스레드가 건드리지 않고 놓는다(8칸 안이라 경계 근처에서만).
- `Entity.isInWaterOrRain()` 추가(물 또는 비, 거품 기둥 따로 안 봄 — 바닐라와 같음), 플레이어 쪽은 `Tridents.isInWaterOrRain` 을 공개로.
- 시험: `ConduitBlockEntityTest`(4), 봇 `tools/bot-test/round7/conduit.js` — 15개 꺼짐/16개 효과(duration 260 앰비언트)/42개 익사체 2초마다 4 피해·Target 저장/틀 하나 빼면 멈춤/소리 4종.

## 2026-11-06 커스텀 바이옴·차원 타입 등록 (W2, 불만 표 39번)
- 추가(furnace-api): `plugin.lifecycle.GameRegistrar` + `LifecycleEvents.GAME_REGISTRIES`(부트스트랩에서 등록, 서버 시작 때 레지스트리 고정 전 한 번), `worldgen.BiomeDefinition`·`DimensionTypeDefinition`(값 객체·빌더·equals, 필드 이름 버전 중립)·`SpawnCategory`, `World.biomeAt/setBiome`(기본 구현은 UnsupportedOperationException).
- 서버: `GameRegistryStore`(플러그인 항목·소유자·활성 여부) + `GameRegistryCodec`(정의 → 네트워크 NBT, 소리는 항상 `{sound_id}` 직접 형식 — 참조 형식은 클라에 없는 id 면 레지스트리 전체가 거부된다). `RegistrySet.withExtras` 가 바닐라 뒤에 등록 순서로 붙이고 `data(registry, entry)` 로 전송 본문을 쥔다. `RegistryData` 가 본문 있는 항목만 `hasData=true` + 네트워크 NBT 로 보낸다(바닐라는 그대로 false).
- 인덱스: 바닐라 바이옴 64개(차원 타입 4개) 인덱스는 그대로, 플러그인 것은 뒤. **고정 뒤에는 지우지 않는다** — 플러그인이 꺼지면 항목은 비활성으로만 표시하고(클라이언트가 받은 id 공간 유지) 서버 쪽 상태(`BiomeWeather.CUSTOM` 날씨 성질·`BiomeSpawns.CUSTOM` 스폰 목록)는 걷는다. 핫 리로드(`reprepare`)는 그 플러그인의 훅만 다시 돌려 이미 있는 자기 키를 갱신·재활성(정의가 바뀌면 "다시 접속해야 받는다" 경고), 새 키는 거부(재시작 필요).
- 연결: `FurnaceMain` 이 `pluginBoot.collectGameRegistries(바닐라 세트)` 로 만든 세트를 월드젠(`NoiseWorldSource`·`AnvilChunkCodec`)·`biomeDirectBits`·`DataPackManager`(태그·/fillbiome·/locate·execute if biome)·`ServerCore.registries()`(접속 처리의 `RegistryData`·차원 인덱스)에 같이 쓴다. 청크 바이옴 팔레트 비트 수는 클라이언트가 레지스트리 크기로 계산하므로 확장 세트 크기를 써야 한다. `WorldgenData.biomeFrozenModifier` 는 바닐라 JSON 이 없는 이름(플러그인 바이옴)이면 false.
- 청크 저장은 이름으로: 플러그인이 빠지면 그 칸은 평원으로 읽히고(월드는 열린다) 다시 넣으면 돌아온다(`VanillaChunkFormatTest`).
- 차원 타입은 등록·전송까지다. 그 타입을 쓰는 차원(월드)을 만드는 것은 다중 월드(W1): 만들 때 `GameRegistryStore.dimensionType(key).definition()` 으로 정의를 읽어 서버 `DimensionType` 을 구성하고 `Respawn`/`Login` 의 차원 인덱스는 `ServerCore.registries().indexOf("minecraft:dimension_type", key)`(ConnectionHandler 가 이미 그렇게 읽는다).
- 시험: `GameRegistryTest`(7), `VanillaChunkFormatTest`(+1), 봇 `tools/bot-test/round-w2/glow.js` 11/11(레지스트리 본문 수신·접속·setBiome→chunk_biomes·/fillbiome·execute if biome). 문서 `docs/api/bootstrap-registries.md` 6장.

## 2026-11-06 이벤트 취소 계약(취소하면 상태 그대로, 화면 되돌리기는 코어가)
- 규칙·이벤트별 표: `docs/api/event-cancel-contract.md`. 도우미 `plugin/ClientResync`(칸·창 전체·단축 칸·능력·블록+블록 엔티티·드롭 되돌림).
- 고친 것: ① 창 -2 패킷에 **창 칸 번호**(36+선택, 45)를 보내던 5곳(우클릭·엔티티 클릭·먹기 취소 — 갑옷 칸을 덮을 수 있었다)을 Inventory 번호로. ② `BlockPlaceEvent` 취소 시 손 칸 재전송 없음. ③ `PlayerDropItemEvent` 취소가 첫 빈 칸에 돌려주고 재전송 없음 → 든 칸으로(CraftBukkit). ④ 던지기·배·수레·거치대·액자·불꽃·수정·엔더의 눈·낚싯대는 취소돼도 아이템을 소모했다 → 소모 없이 재전송, 활·쇠뇌·삼지창은 탄약·내구도·장전 되돌림. ⑤ 부수기·왼쪽 클릭·우클릭 취소 때 블록 엔티티(표지판 글 등)가 안 따라갔다.
- 시험: `CancelResyncE2ETest`(진짜 연결 핸들러, 28개), `CancelContractTest`(새 취소 가능 이벤트가 표·문서에 없으면 실패), 화로·침대 취소는 각 단위 시험. 합성 데이터 팩 `SyntheticRecipes` 로 바닐라 데이터 없이 화로 시험.
- 시험 함정: 시험 스케줄러는 스스로 틱하지 않는다 — `submitAtChunk(p.chunkPos(), r -> r.tick())` 로 돌려야 ack·이동·먹기가 진행된다. 접속 직후 `p.drainPending()` 전엔 플레이어 위치가 0,0,0.
- 한계: 웅크림·달리기 취소는 되돌릴 방법이 없음(클라이언트 입력, Paper 동일). 부싯돌은 취소해도 닳음(CraftBukkit 동일).


## 2026-11-06 플러그인 UI API: 메뉴·사이드바·보스바
- 추가(furnace-api): `menu.Menu`(+`MenuType`·`MenuClick`·`ClickKind`), `bossbar.BossBar`, `scoreboard.Sidebar`. `Audience` 에 `showBossBar/hideBossBar/showSidebar/hideSidebar`, `Player` 에 `openMenu(Menu)/closeMenu()/currentMenu()`. 모두 레지스트리 키·`Text`·`ItemStack`(API) 만 쓰는 버전 중립 타입이고 상태는 API 객체가 갖는다(서버는 리스너로 따라간다).
- 메뉴: 상자 1~6줄·발사기 3×3·깔때기 5칸. **기본은 모든 클릭 취소**(플레이어 인벤토리 칸 쉬프트 클릭·끌기 포함), 처리기가 `click.allow()` 를 부르면 바닐라대로. 처리기는 연결 스레드에서 불리고 던진 예외는 기록 후 취소. `Menu.set/button/clear/fillEmpty/border` 로 내용을 바꾸면 보고 있는 사람 화면이 `broadcastChanges` 로 따라온다. 열기/닫기는 `InventoryHost.openPluginMenu/execute` 로 연결 이벤트 루프에서(처리기 안에서 `close()` 해도 클릭이 끝난 뒤에 닫힌다). InventoryOpen/Close/ClickEvent 는 그대로 발화(처리기가 거부한 클릭은 InventoryClickEvent 를 거치지 않는다). 서버 `AbstractContainerMenu.pluginOwner` 가 `MenuSession` 표식.
- 사이드바: 서버 점수판을 건드리지 않고 그 플레이어에게만 목표(`fs…`)·점수 패킷. 줄 i 의 점수 = 줄 수 − i(큰 점수가 위), 숫자는 blank 형식으로 숨김, 최대 15줄, 40자 제한 없음. 바뀐 줄만 다시 보내고 줄 수가 바뀌면 전부. 숨기면 서버 점수판의 사이드바 목표를 되돌려 건다. 한 플레이어에 하나(새로 보이면 교체).
- 보스바: 서버 `/bossbar` 와 무관한 일회용(UUID 별도), 이름·진행도(0..1 자름)·색·모양·옵션 변경 시 해당 패킷만. `Text` 는 플레이어 로케일로 `localize`.
- 접속 종료 시 `PlayerHandle.dispose()` 가 구독을 푼다(메뉴는 닫힘 경로의 `inventoryClose`).
- 시험: `PluginUiApiTest`(서버 단위 9개), 봇 `tools/bot-test/round10/ui.js` + `probe/UiProbe` — 10/10 PASS(실제 접속에서 메뉴 열림·장식 칸 취소·allow·버튼+닫기, 보스바·사이드바 패킷 순서).
- 아직 없는 것: 메뉴 제목 실시간 변경(open_screen 재전송), 페이지 나누기 도우미, 사이드바 점수 숫자 표시, 보스바 서버 `/bossbar` 와의 연동.
## 2026-11-06 접속 단계 이벤트·종료 순서 계약 (불만 표 4·8번)
- 계약 문서: `docs/api/lifecycle.md`. 새 API: `api.net.PlayerConnection`(연결 하나에 하나, `id()` 로 단계 사이를 잇는다)·`ConnectionPhase`, 이벤트 `PlayerHandshakeEvent`(취소 가능)·`AsyncPlayerConfigureEvent`(가상 스레드, 블로킹 허용, 구성 끝 직전)·`PlayerCreateEvent`(플레이 패킷 전)·`PlayerSpawnLocationEvent`(LoginPlay 전, 차원도 바꿈)·`ServerShutdownEvent`, `Furnace.isStopping()`, `Player.connection()`. 기존 `AsyncPlayerPreLoginEvent`·`PlayerLoginEvent` 에 `connection()`(옛 생성자 유지).
- 바닐라 흐름 변화: 첫 명령 트리를 `CommandSource.forProfile`(플레이어 없음 → 플러그인 명령 권한을 콘솔로 판정하던 구멍) 대신 바닐라 `sendCommands` 처럼 `CommandSource.player(core, p)` 로 거른다. 바닐라 명령은 권한 레벨만 보므로 트리는 같다. 첫 위치 계산(sx..spitch)을 LoginPlay 앞으로 옮겼다(값·패킷 순서는 같다).
- 구성 대기 중 keep-alive: `scheduleConfigKeepAlive` 조건을 `awaitingPack || awaitingConfigure` 로. 플러그인이 `PlayerConnection.disconnect` 로 끊었으면(`kickReason`) 구성 끝을 보내지 않는다.
- 종료: `FurnaceMain` 의 종료 훅을 `ShutdownSequence`(단계 enum 순서 = 계약, 단계별 예외 격리, 한 번만)로 바꿨다. 순서는 이전과 같다(이벤트 단계만 추가). region 정지는 모든 onDisable 뒤라 Folia #353 같은 문제는 없다 — 시험 `ShutdownSequenceTest.pluginsDisableWhileRegionsStillRun`.
- 시험: `LoginPhaseEventsTest`(실제 소켓 7개: 순서·같은 연결·스레드, 핸드셰이크 취소, 구성 블로킹 후 거부, 첫 명령 트리 권한, 첫 위치 바꾸기·없는 차원 무시, 구성 중 disconnect), `ShutdownSequenceTest`(3개). 봇 probe(`tools/bot-test/round4/probe/EventProbe`)에 접속 단계 describe 추가(연결 번호 `#n`).
- 남은 것(불만 표): 7번 종료 이유(`PlayerQuitEvent` 이유 enum, P2), 102번 구성 단계 패킷 제한, 구성 단계 플러그인 메시지 보내기·받기(`PlayerConnection.sendPluginMessage`), 상태 조회 연결의 `ServerListPingEvent.connection()`.

## 2026-11-06 region 경계 차이 1: 마을 포위·방랑 상인 대상·앵무새 어깨
- **마을 포위·방랑 상인을 차원에 하나로**(`world/DimensionSpawners`): 그동안 region 마다 `VillageSiege` 가 따로 있어 자정 굴림이 region 수만큼 일어났고(두 region 이면 포위 확률 약 19%), 포위 대상도 그 region 플레이어 중 첫 사람이었다. 상인은 타이머를 이긴 region 의 플레이어 중에서 골랐고, 플레이어가 없는 동안 타이머가 멈췄다. 이제 오버월드 `World` 에 스포너 하나씩을 두고 **전역 틱**(`ServerCore.tick`, 시간이 한 틱 간 뒤 — 바닐라 `ServerLevel.tick` 의 tickTime → 청크 틱 순서)이 게임 틱마다 한 번 돌린다. 조건은 바닐라 `ServerChunkCache.tickChunks`(aqs `t()` 바이트코드: `runsNormally` && `doMobSpawning` 일 때 `tickCustomSpawners(spawnEnemies, spawnFriendlies)`, 플레이어 수 조건 없음)와 같다. 낮 판정은 `BlockLevel.skyDarken(비, 천둥, timeOfDay) < 4`.
  - 포위 플레이어 고르기: region 이 자기 틱(`RegionLevel.tickCustomSpawners`)에서 "마을 안·비관전·바이옴 조건"을 채우는 자기 플레이어를 `VillageSiege.report` 로 알리고, 전역 틱이 차원 플레이어 순서(엔티티 번호 순 — 바닐라 `level.players()` 의 들어온 순 대신, 가정)에서 최근 2틱 안에 보고된 첫 사람을 고른다. 32칸 원 점 10번 굴림은 그 플레이어 region 스레드에서(`Player.runOnOwner`), 좀비 스폰은 중심점 청크 region 에서(`submitAtOwnedChunk`). 답이 40틱 안에 안 오면(접속 종료) 다시 고르고, 날이 밝은 뒤 온 답은 버린다(맡김 번호).
  - 상인: 타이머·굴림은 전역(플레이어가 없어도 돈다 — 그때 이기면 바닐라처럼 "플레이어 없음 = 성공"으로 확률 25), 대상은 차원 전체 살아 있는 플레이어 중 하나(`getRandomPlayer`), 그 뒤 마을 종 찾기·스폰은 그 플레이어 region 스레드에서 스포너 난수에서 갈라 낸 새 난수로. 스폰 성공은 늦게 와도 `WanderingTraderState` 잠금 안에서 확률을 25 로 되돌리고 상인 번호를 적는다.
  - **다른 점(의도, 관측 차이 없음 수준)**: 넘긴 일은 그 region 의 다음 틱 머리에 돌아 바닐라보다 한 틱 안쪽으로 늦다(포위의 첫 좀비는 2틱쯤, 상인은 1틱쯤). 난수는 region/전역 난수라 바닐라와 수열이 다르다(원래도 같지 않았다).
  - 단독 레벨(시험)은 `VillageSiege.Host.of`·`WanderingTraderSpawner.Host.of` 로 그 자리에서 해 바닐라와 같은 한 틱·같은 난수 순서다(기존 손계산 시험 그대로 통과).
- **앵무새 어깨**: 앉히기는 앵무새 region 이 주인을 가졌을 때만(몸이 겹쳐야 앉으므로 같은 region — 다른 region 의 주인 상태는 읽지 않는다, 이 목표는 플래그가 없어 다른 목표를 막지 않으므로 결과가 같다). 내려놓기는 어깨를 부른 스레드에서 바로 비우고, 몹은 플레이어 region 스레드에서 세운다 — 명령(`/gamemode spectator`·`/damage`, 전역 스레드)에서 불리면 다음 플레이어 틱에 그 자리에. 그 사이 끊기면 어깨로 되돌려 저장한다.
- 남은 region 차이(이번에 손대지 않음): 팬텀·순찰·고양이 스포너는 여전히 region 마다 타이머가 따로다(바닐라는 차원 하나 — 순찰 nextTick·팬텀 nextTick 이 region 수만큼). 돌고래의 플레이어 효과 부여 일부. 명령 블록 체인은 둘째 묶음.
- 시험: `VillageSiegeTest`(서버 길 3개: 보고 순서로 첫 사람, 늦은 답 버림, 떠남·무응답 재시도), `WanderingTraderTest.serverTimerRunsWithoutPlayersAndResetsChanceOnLateSpawn`. 봇 `tools/bot-test/round8/`: 두 봇을 3000칸 떨어뜨려(서로 다른 region) 둘 다 종 격자 마을·deep_dark(자연 스폰 없음)에 두고 60밤 — 고친 뒤 포위 9밤 모두 먼저 들어온 봇 쪽, 고치기 전 jar 는 10밤 중 2밤이 뒤에 들어온 봇 쪽. 어깨: 콘솔 `/gamemode spectator`·`/damage` 뒤 어깨가 비고 앵무새가 봇 자리(거리 0)에 섰다.

## 2026-11-06 데이터 주도 동기화 레지스트리·게임 레지스트리 훅 (묶음 D2, 불만 표 27·32·37번)
- 마법부여·늑대/그림 변종·현수막 무늬·갑옷 장식·음반은 이제 시작 때 데이터 팩에서 읽는다(`DynamicRegistries`). 바닐라 데이터만이면 순서가 `registries/1.21.1.txt` 와 같고(시험이 대조), 데이터 팩·플러그인 항목은 RegistryData 에 NBT 를 싣는다. `RegistrySet.current()` 가 지금 표 — 정적 `RegistrySet.load` 로 번호를 캐시하면 데이터 팩 항목이 끼는 순간 번호가 어긋난다(늑대 pale=3 같은 상수 금지, `Wolf.paleIndex()`).
- 함정: 시험에서 `DataPackManager.start(..., initialEnabled=List.of())` 로 월드 팩을 두면 vanilla 팩이 자동 추가 순서상 월드 팩 "위"로 가서 덮어쓰기가 안 보인다. 실제 새 월드처럼 `List.of("vanilla")` 로 시작한다.
- JSON→NBT 는 NbtOps 규칙: 정수는 가장 작은 타입, 바이트·정수·롱만의 리스트는 배열 태그(short 리스트는 리스트), 섞인 리스트는 `{"": 값}` 으로 싼다.
- 마법부여 `effects` 는 아직 계산하지 않는다 — 데이터 팩 마법부여는 정의(선택·비용·모루)까지만 바닐라와 같다. 효과 엔진은 후속 과제.
- 마법부여 끄기는 레지스트리에서 빼지 않고 선택 지점(`Enchantments.resolve`·`selectForTable`·`enchantItemFromTag`·`Single` 제공자·책 거래)에서만 거른다. 아무것도 끄지 않으면 경로가 그대로라 난수 소비가 같다.
- 난이도 프로필 배율은 1 이면 곱하지 않는다(float 결과 보존). 프로필은 저장하지 않는다.
- 봇 확인(2026-11-06, `tools/bot-test/round12/registries.js`): 월드 데이터 팩의 늑대 변종·마법부여·그림이 RegistryData 에 데이터째 실리고 mineflayer 가 읽음, 사용자 마법부여 칼 지급·변종 늑대 소환·저장 이름까지 8항목 PASS.

## 2026-11-06 블록·레시피 API (묶음 D1)
- 설계·사용법은 `docs/api/block-recipe-api.md`. `Block`(살아 있는 칸)/`BlockSnapshot`(복사) 분리, 타입 있는 속성(`BlockProperty`·`BlockType`), 레시피 레지스트리(`Furnace.recipes()`).
- **RecipeManager 는 이제 바뀐다**: 레시피 표·색인·네트워크 본문을 한 `State` 에 묶고 `volatile` 로 바꿔 끼운다(`change(add, remove)`, 쓰는 쪽만 `synchronized`). 읽는 쪽(`findCrafting` 등)은 락이 없다. `findCrafting(input, last)` 의 지난 레시피는 아직 표에 같은 객체로 있을 때만 쓴다(플러그인이 내렸거나 바꿨으면 다시 찾는다).
- 플러그인 레시피는 `PluginRecipes`(소유자·원본 서버 레시피)에 따로 적어 두고 `/reload` 뒤 `ServerCore` 리로드 리스너에서 `reapply` 로 새 매니저에 입힌다(패킷 보내기 전). 변환은 데이터 팩 파서의 검사를 같게 하려고 `recipe/ApiRecipes` 가 서버 `Recipe` 레코드를 직접 만든다(JSON 을 거치지 않는다 — 결과 컴포넌트가 SNBT 라서).
- 시험 함정: 서버 아이템 레지스트리 `idOf` 는 `minecraft:` 접두가 있어야 한다(없으면 -1 이라 특수 레시피 매칭에서 NPE 처럼 보인다). `Furnace.server()` 는 전역이라 서버 단위 시험에서는 `snapshot.live()`·`BlockEvent.liveBlock()` 을 못 쓴다.

## 2026-11-06 아이템 컴포넌트 타입 API · 아이템 영속 데이터
- 플러그인 API `ItemStack`(불변 record, 컴포넌트는 SNBT) 위에 타입 있는 층을 얹었다: `ItemComponent<T>`(id + 값 종류)·`ItemComponents`(26종: 이름·로어·희귀도·수치 8·불괴·마법 부여 2·광택 덮어쓰기·염색·속성 변경치·표시 4·문자열/목록/맵 5)·`ItemComponent.raw(id)`(57종 전부를 SNBT 문자열로).
  `ItemStack.get/has/getOrDefault/with/without`, `persistentData()`, `withPersistentData(콜백)`. 값 타입은 버전 중립(레지스트리 키 문자열·`Text`·record) — 저장 꼴과의 변환은 서버 `ItemSupportImpl`(API 쪽 입구는 `ItemSupport`, `PluginHost` 가 설치).
- 바닐라 규칙은 서버 코덱을 그대로 거친다: 범위(damage ≥ 0, max_stack_size 1~99, 로어 256줄…)·마법 부여 레지스트리 검사를 어기면 IllegalArgumentException.
  `get` 은 스택에 붙은 값 → 아이템 기본값(`ItemPrototypes`) 순(바닐라 ItemStack.get). `with` 로 기본값과 같은 값을 넣으면 붙이지 않고 기본으로 되돌린다(PatchedDataComponentMap.set). `without` 은 아이템에 기본값이 있을 때만 "지움" 표시, 없으면 그냥 뗀다.
- 아이템 영속 데이터는 Paper 와 같은 자리 `custom_data` 의 `PublicBukkitValues`(키는 `namespace:path` 문자열). 스택이 불변이라 어느 스레드에서도 읽고(사본), `withPersistentData` 는 새 스택을 돌려준다(바뀐 것이 없으면 같은 스택). 다 비우면 PublicBukkitValues, 그래서 custom_data 가 비면 컴포넌트도 뗀다 — 마지막 줄은 Paper 원문 대조 못 한 **가정**.
  custom_data 의 다른 키·PublicBukkitValues 의 모르는 값은 건드리지 않고 보존.
- `PersistentDataType.list(원소종류)` 추가(Paper 의 LIST 와 같은 NBT 리스트 꼴): 엔티티·청크·아이템 영속 데이터 모두. 중첩 목록·섞인 종류는 거부.
- 이후 버전 지원 대비: 컴포넌트 필드 이름·형식이 바뀌는 버전(1.21.2+, 1.21.5+)은 `ItemSupport` 구현만 바꾸면 되고 API 타입은 그대로다. 아직 타입이 없는 컴포넌트(food·tool·potion_contents·book·trim·fireworks…)는 `raw` 로 쓴다.
- 시험: `ItemComponentsApiTest`(14) — 코덱 범위, 기본값 처리, raw, 서버 변환·저장 왕복, 영속 데이터 보존·목록.

## 2026-11-06 구조물 블록·직소 블록
- 바닐라 `StructureBlockEntity`/`JigsawBlockEntity`·`StructureBlock`·`JigsawBlock`·패킷 3종(0x34 `update_structure_block`, 0x33 `update_jigsaw_block`, 0x17 `generate_structure`)을 바이트코드와 대조해 이식. 편집 화면은 클라이언트가 블록 엔티티 데이터(`saveCustomOnly` 갱신 태그)만 보고 연다. 권한은 창조 모드 + 권한 2 이상, 아니면 패킷을 조용히 버린다.
- 저장소는 `TemplateManager`(월드 `generated/<ns>/structures/<경로>.nbt` → 내장). 신호 저장(`saveStructure(level, false)`)은 메모리 템플릿만 갈아 끼우고 파일을 쓰지 않는다. 파일 쓰기는 `.tmp` 에 쓴 뒤 옮긴다.
- 놓기는 월드젠 `TemplatePlacer.placeInWorld` 를 `LiveGrid`(로드된 청크 복사본 → flag 2 로 되돌림)에서 돌린다. `LiveGrid` 는 `/place` 에서 꺼내 공용으로 만들었다. 범위 밖 청크가 있으면 `GridException`.
- **함정 1** `LiveGrid.commit` 이 아이템이 든 템플릿 상자를 `generatedBlockEntity`(생성 NBT)로만 남겨 청크를 다시 읽기 전까지 `execute if items`/열기가 "컨테이너 아님" 이었다 → 아이템이 든 상자는 바로 `ChestBlockEntity` 로 만든다(`/place template` 도 같이 고쳐짐).
- **함정 2** 새 직소 블록 엔티티의 `joint` 는 `rollable`(필드 기본값)이다. `aligned` 기본은 저장 NBT 에 `joint` 가 없거나 이름이 틀렸을 때만(앞면이 수평이면 aligned, 수직이면 rollable).
- **함정 3(봇)** `/data get block` 출력은 mineflayer 가 잘라 읽기 어렵다 — `execute if data block <칸> {필드:값}` / `execute if items block <칸> container.0 <아이템>` 의 통과 여부로 확인한다(숫자 태그는 형이 같아야 한다: `seed:5L`, `sizeX:3`). 비어 있는 서버에는 청크가 안 올라 있어 `/data get` 이 "not loaded" 라 한다 — 봇이 접속해 있을 때만 읽는다.
- 한계: 데이터 팩 템플릿·.snbt 없음, 옛 DataVersion 데이터 픽서 없음, 구조물 블록은 그 region 에서 로드된 청크만 본다.


## 2026-11-06 접속 상태 머신 N1 — 구성 단계 주고받기·끊긴 이유 (불만 표 5·6·7번)
- 5(연결 ID)는 접속 단계 이벤트 PR 에서 이미 `PlayerConnection.id()` 로 들어갔다. 이 묶음은 6·7번과 그 둘레.
- 6: `PlayerConnection` 에 리소스 팩(`sendResourcePack`/`removeResourcePack`/`removeAllResourcePacks`)·쿠키(`requestCookie`/`storeCookie`)·`transfer`. 코드는 `ConnectionHandler.api*`. 구성 패킷 `CookieRequest`(0x00)·`ResourcePackPop`(0x08)·`StoreCookie`(0x0A)·`ConfigTransfer`(0x0B)를 새로 넣었다(serverbound `cookie_response` 0x01).
  - 대기 Future 는 `pluginPacks`/`pendingCookies` 맵에 있고 `channelInactive` → `releaseWaiters` 가 `ConnectionClosedException` 으로 끝낸다. 완료는 `completeAsync`(플러그인 실행기)라 후속 처리가 netty 스레드를 막지 않는다. 구성 이벤트 처리기 스레드는 `configureThread` 로 기억했다가, 끊긴 뒤 1초(`CONFIGURE_INTERRUPT_GRACE_MS`)에도 안 돌아오면 인터럽트한다.
  - 함정: 서버 리소스 팩 대기(`awaitingPack`)는 응답이 어느 팩 것인지 몰랐다 → `readPackResponse` 가 플러그인 팩 응답이면 `lastPackResponseWasPlugin` 을 켜서 구성 진행에 쓰지 않는다. 구성 끝 패킷을 보낸 뒤에는 구성 패킷을 더 보낼 수 없다(`canSendDuringConfigurationOrPlay`).
  - play 단계의 쿠키(요청·저장)는 없다(구성만). 구성 중 플러그인 채널 수신도 없다.
- 7: `QuitReason`(DISCONNECTED·KICKED·TIMED_OUT·ERRONEOUS_STATE·SERVER_SHUTDOWN) 를 `PlayerQuitEvent.reason()` 와 `PlayerConnection.quitReason()` 에. `ConnectionHandler.markQuit` 가 처음 남긴 이유만 남기고, `kick`(기본 KICKED)·`closeFlushed`(ERRONEOUS_STATE)·타임아웃 세 곳·종료가 이유를 정한다. 아무 표시가 없으면 `Player.kickReason()` 이 있으면 KICKED, 없으면 DISCONNECTED.
- 시험: `ConnectionStateApiTest` 17개(실제 소켓: 팩·쿠키·전송·끊김 정리·QuitReason 여섯 가지).

## 2026-11-06 플러그인별 시간 프로파일러(/timings)
- API: `Furnace.profiler()` → `PluginProfiler`(start/stop/reset/report, `timing(plugin, kind, name)` 로 플러그인이 자기 구간도 잰다), `Timing`(start 가 음수면 꺼짐 — 그대로 stop 에 넘기면 아무것도 안 함).
- 서버: `plugin/PluginProfilerImpl`. 구간은 LongAdder 묶음이라 region 스레드 여럿이 잠금 없이 동시에 기록한다. 겹친 구간은 스레드별 틀 스택으로 **자기 시간**을 나눈다 — 리스너 안에서 다른 플러그인 이벤트가 돌아도 두 번 세지 않는다. 스레드 종류(region·전역·그 밖)는 스레드가 처음 잴 때 한 번 정한다.
- 잰 곳: 이벤트 리스너(`EventBusImpl.callTimed` — 꺼져 있으면 `call` 은 `isRunning()` 하나만 더 읽는다), 플러그인 명령 실행·자동완성(`CommandRegistryImpl`, 이름공간 붙은 이름과 같은 줄), `PluginTasks`(플러그인을 안다), onLoad/onEnable/onDisable.
- 플러그인이 `server().scheduler()`·엔티티/청크/월드 `scheduler()`·`entityScheduler()`·`asyncExecutor()` 를 바로 쓰는 작업은 `ProfiledScheduling` 이 감싼다: **작업 객체 클래스의 클래스로더**(람다는 정의한 클래스의 로더)가 `PluginClassLoader` 면 그 플러그인 몫. 서버·API 클래스 작업은 감싸지 않는다(PluginTasks 의 guard 람다도 API 클래스라 두 번 감싸지 않는다). `TaskScheduler` 기본 `runAtFixedRate/runLater` 는 작업을 API 클래스(Repeating/Once)로 싸므로 감싸기를 먼저 하고 원래 스케줄러에 넘긴다. `submit` 도 FutureTask 로 싸기 전에 `newTaskFor` 에서 감싼다.
- 시험에서 TestPlugins 클래스는 앱 클래스패스에 있어 부모 우선 위임으로 PluginClassLoader 가 아니라 앱 로더가 정의한다 → 클래스로더 귀속 시험은 시험 안에서 javac 로 플러그인을 컴파일해 jar 로 만든다(`PluginProfilerTest.compileProbePlugin`).
- 명령: `/timings on|off|reset|report [플러그인]|export`(권한 3, `timings/plugins-*.txt`). 기동 때부터 재려면 `-Dfurnace.profiler=true`. "틱당" = 자기 시간 ÷ (측정 시간/50ms) — region 이 여럿이면 여러 스레드 합이라 50ms 를 넘을 수 있다.

## 2026-11-06 가상 엔티티·NPC·가짜 탭 줄 (묶음 K1, 불만 표 49번)

- API `org.domi.furnace.api.virtual`(`VirtualEntities`·`VirtualEntity`·`VirtualPlayer`·`VirtualTabList`), 입구 `Furnace.virtualEntities()`. 서버 `plugin/VirtualEntitiesImpl`(PluginHost.virtualImpl). 설명 `docs/api/virtual-entities.md`.
- 패킷 래퍼 셋 추가: `SET_ENTITY_DATA`(값 종류 9개, 나머지는 `Unsupported` 로 멈춤), `PLAYER_INFO_UPDATE`(채팅 세션 비트는 거절), `PLAYER_INFO_REMOVE`.
- 추적은 전역 스레드 2틱 주기(`runAtFixedRate`). 클릭은 `PacketServiceImpl.listenInternal`(소유자 없는 내부 구독, 첫 가상 엔티티를 만들 때 한 번 등록)로 INTERACT 를 잡아 취소하고 주 손 우클릭·공격만 처리기로 넘긴다.
- 정리: `PluginManagerImpl.disable()` 에서 `virtualImpl().removeAll(plugin)`. 새 "플러그인에 맡기는 것" API 는 여기에 해제를 넣는다는 규칙(B8) 그대로.
- 함정: 플레이어 엔티티는 `player_info_update` 가 `add_entity` 보다 먼저여야 스킨이 그려진다. 차원 이동 때 클라이언트는 엔티티를 잃지만 탭 정보는 남는다.
- 시험: `VirtualEntitiesTest` 6개(추적·보이기·NPC 순서·클릭·정리·탭), `PacketApiTest` 왕복 확장, 봇 `tools/bot-test/round11/virtual.js` 11/11(minecraft-protocol 이 실제로 패킷을 해석).

## 2026-11-06 region 경계 차이 2: 명령 블록 체인 한 틱
- 그동안 명령 블록 명령은 전역 스레드 명령 큐에서 비동기로 돌고 끝나면 소유 region 으로 돌아와 체인을 이었다 — 체인 한 칸마다 region 왕복이라 첫 칸에서 끝 칸까지 5~8틱(봇 실측). 바닐라는 그 예약 틱 안에 끝난다.
- 이제 `BaseCommandBlock.performCommand` 가 명령을 보낸 뒤 그 자리에서 결과를 기다린다(`BlockLevel.awaitCommand` → `Region.awaitUrgent`). 기다리는 동안 region 은 **전역 스레드가 보낸 작업만**(`Region.Task.urgent`, `GlobalThread.isGlobalThread()` 로 표시) 작업 큐에서 골라 돌린다 — 명령이 이 region 의 칸·엔티티를 만지는 왕복과 결과 콜백이 그 안에서 처리되어, 콜백이 performCommand 가 돌아오기 전에 불리므로 RAN(바닐라 true)으로 끝나고 체인이 그대로 이어진다. 성공 횟수(비교기)도 바로 보인다.
- **다른 region 이 필요한 명령**(`@e` 는 모든 region 을 돈다): 명령 블록이 기다리는 동안(`Region.URGENT_WAITERS > 0`) 급한 작업을 받은 쉬는 region 은 다음 틱을 기다리지 않고 급한 작업만 도는 차례(drain)를 곧바로 받는다(`Regionizer.urgentArrived` — 틱 수·계측·나누기는 건드리지 않고 원래 다음 틱 시각으로 돌아간다). 틱 중이던 region 은 틱이 끝나자마자. 워커가 모자라면(기다리는 스레드가 워커를 쥐고 있다) 기다리는 스레드가 그 drain 을 대신 돈다(`helpDrainAny` — 그 동안 두 region 을 함께 쥔다).
- **틱 예산**: 한 region 틱에서 기다린 시간 합이 `-Dfurnace.commandBlockWaitMicros`(기본 20000µs) 를 넘으면 그 칸부터는 예전처럼 비동기로 이어 간다(체인 순서는 그대로, 시간만 늦어짐). 서버 시작 직후 첫 명령은 명령 트리·클래스 로딩으로 예산을 넘길 수 있다.
- 기다리는 동안 돌리는 작업은 전역 스레드가 보낸 것 전부다(다른 사람의 채팅 명령 왕복 포함) — 바닐라에서 명령이 그 자리에서 도는 것과 같은 자리라 결과는 같다고 본다(가정). 다른 출처(연결·IO·다른 region)의 작업은 그대로 다음 틱 머리에 돈다.
- 남은 차이: 체인이 로드되지 않은 청크로 이어지면 끊긴다(바닐라 getBlockState 는 청크를 그 자리에서 불러온다). 이웃 청크는 region 불변식(구역 반경)상 늘 같은 region 이라 로드된 청크 사이에서는 끊기지 않는다. `set_command_minecart` 는 여전히 플레이어 region 의 수레만 찾는다.
- 시험: `CommandBlockOneTickTest`(체인 세 칸이 예약 틱 호출 하나 안에 끝나고 성공 횟수 111, 3200칸 떨어진 다른 region 의 돼지를 찾는 `@e` 체인도 같은 호출 안에). 봇 `tools/bot-test/round8/cmdchain.js`: 임펄스+체인 5칸(첫 칸·끝 칸이 `time query gametime` 을 점수로, 중간은 3000칸 떨어진 다른 봇 옆 돼지를 `@e` 로) 10번 — 이 PR 은 차이 10번 모두 0, 기준 브랜치 jar 는 5~8틱.

## 2026-11-07 다중 월드 (묶음 W1, 불만 표 38·42행)
- API `Furnace.createWorld(WorldCreator)`·`unloadWorld(World, save)`·`worldNamed`, `WorldEnvironment`·`WorldGenerator`, 이벤트 `WorldLoadEvent`·`WorldUnloadEvent`. 서버 `world/WorldManager`(조립 `build` 는 기본 네더·엔드도 같이 쓴다), 설정 `world/WorldSettings`, 명령 `/worlds`. 설명 `docs/api/worlds.md`.
- 폴더는 Paper 배치(`<이름>/`, 네더 환경은 `<이름>/DIM-1`). level.dat 에 `furnace:world` 설명서를 남겨 `/worlds load`·`worlds.autoload` 가 다시 연다.
- 추가 월드는 `Dimensions.register` 로 시계·접속자·엔티티 id 를 오버월드와 공유(바닐라 데이터 팩 차원과 같은 방식). region 작업 스레드는 월드마다 뜨고, 내릴 때 `World.closeForUnload` 가 스케줄러 종료 훅에서 떼고 닫는다(`FurnaceSchedulerImpl.removeCloseHook`).
- 내리기 함정: ① 저장 역압(`ioBackedUp`)이 남은 dirty 청크를 다음 틱으로 미루는데 내린 뒤에는 다음 틱이 없다 → `World.forceSave`. ② 저장은 IO 레인에서 비동기라 flushAll 만 기다리고 저장소를 닫으면 쓰기가 닫힌 파일에 간다 → `IoLanes.barrier()`(레인마다 FIFO 끝에 빈 작업). ③ 내린 월드에 작업을 넣으면 영영 안 도는 future 가 생긴다 → `runAtChunk` 는 RejectedExecution, `submitAt*` 은 실패한 future. ⑤ 플레이어 차원(`p.dimension()`)은 목적 청크 준비 뒤 연결 스레드에서야 바뀌어 `playersHere()` 로는 오는 중인 플레이어가 안 보인다(봇 시험에서 실제로 내린 월드에 갇힘) → `World.incomingTravels`(travel 시작~도착 콜백) + `applyDimensionChange` 가 목적 월드가 내려졌으면 오버월드 스폰으로. ④ 플러그인 월드 손잡이 캐시는 키로 묶여 있어 같은 키를 다시 올리면 옛 World 를 가리켰다 → `PluginHost.handleFor` 가 World 가 다르면 새로.
- 바닐라가 차원 **타입** 값으로 정하는 동작을 차원 **키**로 하던 곳을 고쳤다(기본 세 차원은 결과 같음): 침대·리스폰 앵커·피글린 안전·습격·infiniburn·지옥문 좀비 피글린(natural)·몬스터 스폰 밝기·`B.timeOfDay`(fixedTime)·습격 파일 이름. `DimensionType` 에 infiniburn·hasRaids·monsterSpawnLight 범위·블록 빛 상한을 더했다(옛 15인자 생성자는 typeKey 로 채움 — 시험 그대로). 키로 정하는 바닐라 동작(포털 목적지, 오버월드 전용 스포너, 엔드→오버월드 크레딧)은 그대로 키.
- 차원별 시드(`dimension-seed.*`)·추가 월드 시드는 생성기와 LoginPlay/Respawn hashed seed, 서버 바이옴 확대 지터(`ServerCore.biomeZoomSeed(World)`)에 같이 쓴다. `/seed`·랜덤 시퀀스·`ChunkLevel.worldSeed` 는 바닐라처럼 주 월드 시드.
- ⑥ `World.worldDir()` 는 차원 폴더가 아니라 서버 공용 데이터(playerdata·stats·advancements·generated 구조물·탈것) 자리다. 추가 월드에 `<이름>/` 을 넣었더니 그 월드에서 나간 플레이어가 `mining/playerdata` 에 저장돼 재접속 때 오버월드로 왔다(봇 2단계에서 발견). → 차원 저장 뿌리는 `World.levelRoot()`(습격·forceload·command storage 의 data 폴더 기준), `worldDir` 는 추가 월드도 주 월드 폴더.
- 봇 시험 `tools/bot-test/round-w1`: 1단계 16/16, 2단계(autoload 재시작 뒤 mining 으로 재접속·금 블록) 3/3. 새 노이즈 월드로 처음 tp 하면 청크 생성(~6초)이 명령 결과 대기(5초)를 넘어 "An unexpected error occurred" 뒤에 성공 문구가 온다 — 기존 명령 대기 구조의 일(새 네더 지역으로 tp 해도 같다), W1 에서 고치지 않았다.
- 시험 `MultiWorldTest` 10개(만들기·저장·내리기·다시 불러오기, 네더 환경 폴더·타입 규칙, 플러그인 타입 높이, 검사, 취소(고리·WorldUnloadEvent), 플러그인 API·이벤트, 설정·설명서 왕복, 바닐라 타입 값).

## 2026-11-07 두뇌(Brain) 플러그인 API (E2)
- API `MobAi.brain()` → `MobBrain`(기억·센서·활동·플러그인 행동). 설계·규칙 `docs/api/mob-brain.md`.
- 활동 붙잡기는 `Brain.setActiveActivity` 한 곳에서 돌린다: 붙잡힌 동안 몹이 고른 활동 대신 붙잡은 활동으로 바꾼다(비었으면 다시 켠다). 그래서 주민 `refreshBrain` 직후(새 두뇌의 켜진 활동이 비어 있음)에도 `useDefaultActivity` 한 번이면 붙잡은 활동 + 핵심 활동으로 돌아온다. 바닐라 경로는 null 검사 하나.
- 기억 값 모양은 `MemoryModuleType` 상수의 제네릭 형식에서 반사로 읽는다(`BrainValues`). 새 기억을 넣을 때 형식을 정확히 적으면 API 가 따라오고, 시험이 내부 값 목록(dummy·path·hurt_by·ram_target)을 지킨다.
- 두뇌 틱 도중(행동·센서 콜백 안) 센서·행동 넣기·빼기는 틱 끝으로 미룬다(E1 GoalSelector 와 같은 방식).
- 함정: 플러그인 설명 파일 `commands:` 를 `{description: …}` 흐름 형식으로 쓸 때 설명에 `(`·`[` 가 들어가면 ConfigParseException 으로 플러그인이 안 뜬다 — 블록 형식 + 따옴표로.
- 시험: `MobBrainApiTest` 18개, `MobAiHandleTest` 주민 두뇌 1건 추가, 봇 `tools/bot-test/round13/brain.js` 12/12.

## 2026-11-07 명령 점검 M11
- 텍스트 속 선택자·점수·NBT 는 `ComponentUtils.resolve`(future)로 푼다. 엔티티가 region 소유라 읽기가 비동기이므로 쓰는 쪽은 `EntityOps.result`/`thenOnGlobal` 로 이어야 한다. 전부 플레이어·저장소만 건드리면 future 가 이미 끝나 있어 동기 길과 같은 속도.
- brigadier 는 입력과 똑같은 후보를 제안하지 않는다(`/tag x remove a` 에서 `a` 를 다시 안 띄움) — 자동완성 시험은 접두 일부로.
- 새 시험 `TextResolveCommandsTest`(15개), 봇 `tools/bot-test/round15`. 자세한 내용 `command-audit-m11-2026-11-07.md`.

## 2026-11-07 플레이어별 가상 블록·바이옴·보더·장비·채팅 서식 (묶음 K3, 불만 표 52~54행)
- API `Furnace.playerViews()` → `PlayerViews`(+`PlayerBorder`), `AsyncChatEvent.renderer(ChatRenderer)`. 설계·규칙 `docs/api/player-views.md`.
- 핵심(52행): 서버 `Player` 마다 `ViewLayers`(블록·바이옴 층, 장비 덮어쓰기, 개인 `WorldBorder`)를 두고, 나가는 청크 본문·`block_update`·`section_blocks_update`·`chunks_biomes`·`set_equipment`·보더 패킷에 연결의 `ViewRewriter` 가 덧입힌다. 청크를 다시 받아도(멀리 갔다 옴) 층이 남는다 — 봇으로 확인.
- 함정 ①: `ConnectionHandler.send(ctx, …)` 는 `Player.send` 를 거치지 않아 K2 의 `ViewRewriter` 를 못 지났다(스폰 청크·차원 이동·`LoginPlay`/`Respawn` 이 그 길이라 차원 이름도 몰랐다). 이제 이 길도 같은 `ViewRewriter` 를 거친다(`joining` 필드로 접속 도중의 Player).
- 함정 ②: 청크 본문 다시 쓰기는 덮을 칸이 있는 섹션만 풀어 다시 인코딩하고 나머지는 바이트 복사한다. 직접 팰릿 비트 수(`World.blockDirectBits/biomeDirectBits`)를 층이 처음 걸릴 때 월드에서 읽어 둔다. 비공기 수(`nonAirCount`)를 다시 세지 않으면 클라이언트가 섹션을 빈 것으로 건너뛴다.
- 함정 ③: 공유 청크 프레임(`SharedChunkFrames`)은 본문 `byte[]` 동일성이 키다 — 덧입힌 본문은 `shared=null` 로 만들어 그 사람에게만 압축한다(다른 사람의 캐시를 더럽히지 않는다).
- 개인 보더는 `WorldBorder` 인스턴스 하나를 접속에 붙인다. 서버의 `InitializeBorder` 는 개인 보더 상태로 바꿔 보내고 다른 `SetBorder*` 는 버린다. `enforce` 면 `ServerCore.mayInteract`·`Env.borderDamage(Player, …)` 가 이 보더를 쓴다.
- 채팅 렌더러는 서명 없는 시스템 메시지(`unsigned-as-system` 과 같은 길)로 받는 사람마다 한 줄씩 보낸다.
- 시험: `PlayerLayersTest` 12(청크 본문 다시 쓰기·소유권·재전송·차원·보더·장비), `PlayerViewsApiTest` 6, `CancelResyncE2ETest.chatRendererBuildsOneUnsignedLinePerViewer`, 봇 `tools/bot-test/round16/k3.js` 21/21(`round16/probe/K3Probe`).


## 2026-11-07 스폰 정책·엔티티 수·자세·주민/마을/습격 API (E3)
- 설계·바닐라 대조 `docs/api/spawn-and-villages.md`. 기본값은 바닐라 상수(70·10·15·5·5·20·5·…)이고 정책이 비어 있으면 스폰 경로가 바닐라와 같다(`WorldSpawnPolicy.state` 가 `VANILLA` 로 접힘). 활성 범위·엔티티 상한 같은 게임플레이 변경은 넣지 않았다(D3).
- 세계 상한은 `(int)((long)상한 × 스폰 가능 청크 수 / 289)`. 플레이어 상한 0 이하면 그 플레이어 주변은 건너뛴다(바닐라 상수로는 닿지 않고 플러그인이 0 을 걸 때만 닿는 경로).
- `spawn.limit.<카테고리>`·`spawn.player-limit.<카테고리>`(`furnace.properties`)는 월드 정책의 시작값. 0·바닐라 3배 초과·모르는 `spawn.*` 키는 경고, 숫자가 아니거나 범위 밖이면 시작 거부.
- spawn-animals/npcs=false 는 바닐라 1.21.1 `ServerLevel.shouldDiscardEntity`(Animal·WaterAnimal·Npc)와 바이트코드·전 엔티티 종류로 대조해 같음(`SpawnDiscardTest`). 소·주민은 지워지고 좀비는 남는다(봇 `round15/e3.js MODE=npcs`).
- 함정: `EntityHandle.onEntity` 는 region 틱이 돌아야 작업이 실행된다(엔티티 따라다니는 큐) — 시험이 틱을 안 돌리면 영영 안 끝난다. `World.raidAt` 같은 위치 조회는 로드 안 된 청크면 그 region 에 넘기지 않고 빈 결과를 돌려준다.
- 평지 월드는 슬라임 청크 때문에 몬스터가 바로 쌓여 "몬스터 수 == 0" 단정이 깨진다. 상한을 건 뒤 증가 여부로 본다.
- 시험: `SpawnPolicyStateTest` 8, `SpawnPolicySpawnTest` 6, `EntityPolicyApiTest` 8, `NoPhysicsRegionTest` 1, `SpawnDiscardTest`, 봇 `round15/e3.js` 15/15 + `MODE=npcs` 1/1.

## 2026-11-07 M4 약탈자·습격 바닐라 대조 (바이트코드)
- 대조 방법: 바닐라 1.21.1 jar(`tools/vanilla-data`)를 `javap -c -p` 로 읽어 Furnace 와 줄 단위로 맞춰 봤다(Vineflower 실행은 자동 모드에서 거부됨). 대상: `Raid`(coi: 틱·파도 구성·보너스 수·스폰 위치·joinRaid·updateRaiders·소리·save), `Raids`(cok), `Raider`(coj), `PatrollingMonster`(cke)+`LongDistancePatrolGoal`(cke$a), `PatrolSpawner`(dzj), 일러저 `Pillager`(ckg)·`Vindicator`(cks)·`Vindicator.BreakDoorGoal`(cks$a)·`Ravager`·`Witch`.
- **같음 확인**: 습격 틱 순서와 모든 상수(대기 300, 최대 48000, 112² 이탈, 30틱 바깥, 승리 40틱 대기, 축하 600), 나쁜 징조→습격 징조→습격 시작 흐름(`BadOmen`/`RaidOmen` 효과, `createOrExtendRaid` 의 마을 POI 평균 위치), 파도별 수·추가 수 난수 순서(`getPotentialBonusSpawns`), 파괴수 기수, 스폰 위치 찾기(`findRandomSpawnPos`), 습격 뿔피리, 저장 키 순서, 순찰 스포너(12000+nextInt(1200), 5일째 낮, 1/5, 24~47칸, 크기=ceil(실효 난이도)+1)와 장거리 순찰 목표(회전 90 라디안·0.4·10칸 걸음), 문 부수기·문 열기 조건.
- **가정이었던 것 3개 확정**: ① 순찰 저장 키는 `patrol_target`(정수 배열) 맞음. ② 마녀·약탈수는 `canBeLeader()==false` 맞음. ③ 불길한 깃발은 **희귀도 구성 요소가 없고** 이름만 금색 스타일(`item_name`={translate, color:gold}) — 기존의 `rarity: uncommon` 은 틀렸다.
- **고친 차이**:
  - 불길한 깃발(`Raider.leaderBanner`): 희귀도 uncommon 제거, 이름에 금색. 바닐라 월드에서 온 깃발과 `ItemStack.matches` 가 어긋나(줍기·지도자 판정 실패) 있었다.
  - `Raid.addWaveMob`: 바닐라는 같은 UUID 의 옛 개체를 새 개체로 **바꾸고 항상 참**을 돌려준다. 이전에는 거짓을 돌려주고 옛 개체를 남겨, 청크가 내려갔다 올라온 습격자가 집합에 없어 생존 수가 모자랐다(웨이브가 일찍 끝날 수 있음).
  - `Pillager.applyRaidBuffs` 가 비어 있었다: 습격 징조 확률(`Raid.getEnchantOdds`: 단계 2→0.1, 3→0.25, 4→0.5, 5→0.75)을 통과하면 새 석궁에 파도 > 5 면 `raid/pillager_post_wave_5`, 파도 > 3 이면 `raid/pillager_post_wave_3` 제공자로 마법을 걸어 주 무기로 바꾼다(그 밖은 그대로). `Vindicator` 도 같은 `getEnchantOdds` 를 쓰게 정리.
  - `Raider.die` 영웅 후보: 바닐라는 `damageSource.getEntity()` 가 플레이어일 때만 후보. 이전에는 "100틱 안에 마지막으로 때린 플레이어"라서 길들인 늑대·낙사·불 등 다른 원인으로 죽어도 영웅이 됐다.
- **독립 검토(읽기 전용) 결과**: 블로커 없음. 사소한 차이 기록: `updateRaiders` 의 `level.getEntity(uuid)==null` 검사는 없지만 청크가 내려가면 개체가 removed 로 표시돼 `isRemoved()` 가 덮는다. 습격 대기 위치·스폰 위치 검사는 바닐라 `isPositionEntityTicking` 대신 `hasChunkAt`(적재 여부)을 쓴다. `Vindicator.populateDefaultEquipmentSlots` 는 바닐라처럼 `getCurrentRaid()==null` 로 맞췄다.
- **남은 차이**(구조상): 내려간 청크·다른 region 의 POI 는 조회하지 않아 중심 둘레 ±2 섹션 중 일부가 다른 region 이면 마을 판정이 모자랄 수 있다. 같은 거리 POI 선택 순서(`getInRange` 순서)는 region 청크 순서에 따른다. 보스바 진행도 `0/0`(NaN) 은 바닐라가 NaN 을 보내지만 여기서는 0 으로 보낸다.
- 시험: `RaidParityTest`(깃발 구성·addWaveMob 교체·습격 석궁 마법 확률/파도·영웅 처치자) 신규.

## 2026-11-07 수면·리스폰 바닐라 대조 (묶음 M9)
- 바닐라 1.21.1 바이트코드 대조(`aqv`=ServerPlayer): `stopSleepInBed(true,true)` 를 부르는 곳은 **disconnect 와 teleportTo(level, …7인자) 둘뿐**이다. 차원 이동(changeDimension)·포털은 깨우지 않는다(자면서 포털에 들어갈 수 없다).
- 자는 중 텔레포트: `/tp`·setCamera 는 이미 깨웠으나 플러그인 `PlayerHandle.teleport`·`Player.teleportTo`(3·5인자)·`teleportRelative`(엔더 진주 등)는 안 깨웠다. 이제 `Player.wakeThen` 이 이벤트 취소 확인 뒤 소유 스레드(`runOnOwner`)에서 깨우고 **그다음** 목적지로 보정 텔레포트한다(깨우기가 일어설 자리로 옮기는 보정이 목적지를 덮어쓰지 않게 순서 고정). 상대 텔레포트는 깬 뒤 자리 기준.
- 자는 중 접속 종료: 연결 스레드가 저장 위치(ix/iy/iz)를 침대 위에서 읽어 침대 위가 저장됐다. 소유 region 이 자는 동안 일어설 자리를 `Player.sleepStandUp`(x,y,z,yaw)에 떠 두고(잠들 때 + 1초마다, `PlayerSleeping.refreshStandUp`), `channelInactive` 가 자는 중이면 그 값을 저장 위치로 쓴다(바닐라: 일어선 자리·침대를 보는 방향·pitch 0). 저장을 region 쪽으로 미룰 수 있지만 종료 시 region 이 먼저 멈추면 저장이 사라질 위험이 있어 미리 떠 두는 쪽을 택했다.
- "네더·엔드 기본 스폰 (0,64,0)" 은 이미 오래전에 공유 스폰으로 바뀌었다(FurnaceMain). 남은 틈 둘: `/setworldspawn` 이 네더·엔드 `World.defaultSpawn` 에 반영되지 않았고(도착할 때 보내는 `SetDefaultSpawnPosition` 이 옛 값), 바닐라는 오버월드가 아니면 `commands.setworldspawn.failure.not_overworld` 로 거부한다(`aok`). 둘 다 고쳤다. 따로 level.dat 을 가진 추가 월드(W1)는 자기 스폰을 유지.
- `/spawnpoint` 는 리스폰 차원을 오버월드로 고정했으나 바닐라는 `source.getLevel().dimension()` (`aoj`) — 네더에서 쓰면 네더가 리스폰 차원(강제 지점이라 침대 검사 없음). 고쳤다.
- 스폰 보호(`spawn-protection`)는 바닐라 `DedicatedServer.isUnderSpawnProtection` 이 오버월드가 아니면 거짓(`apn`) — 네더·엔드·추가 월드에서도 걸리던 것을 고쳤다.
- 안 고친 것(의도): 밤 건너뛰기 후 깨우기는 전역 스레드가 region 의 플레이어를 못 만져 그 플레이어의 region 으로 넘긴다(결과 같음, 그 사이 region 틱이 돌면 `Player.tick` 의 "낮이면 깨기"가 먼저 깨운다). 추가 월드(W1)의 밤 건너뛰기 판정은 주 월드 접속자만 센다(시계가 공유라 설계 판단이 필요 — 도미니코가 원하면 별 묶음).
- 시험: `PlayerSleepingTest` +4(일어설 자리 캐시·갱신·텔레포트가 먼저 깨움·상대 텔레포트), `SetWorldSpawnDimensionTest` 2. 봇 `tools/bot-test/round-m9/sleep.js` 9/9 — 수정 전 jar 로 같은 스크립트를 돌리면 접속 종료 위치(침대 위 dist=0)·setworldspawn 거부·네더 스폰 3개가 FAIL(대조군).

## 2026-11-07 컨테이너·수레·깔때기 바닐라 대조 (묶음 M3)
- **이미 있던 것 확인(user-todo 3장이 낡았음)**: 상자/깔때기 수레·상자 배 창(`openEntityContainer`), 명령 블록 수레, 디스펜서 수레 6종 설치, 몹이 수레 밀기(`pushEntities`→`AbstractMinecart.push`), 깔때기 수레 빨아들임, `VindicatorBreakDoorGoal`, 양조기 hopper 삽입 규칙. 양조기 연료 칸은 바닐라 `BrewingStandBlockEntity.canPlaceItem(4)` 가 serverTick 의 연료 판정과 같은 단일 아이템(블레이즈 가루)만 받는 것을 `javap -c` 로 확인(가정 해소).
- **새로 한 것**
  - 스포너 수레(`MinecartSpawner`): 매 틱 `BaseSpawner.serverTick(level, blockPosition())`, 이벤트 1 은 엔티티 이벤트로, 저장은 `BaseSpawner.save/load`(이전엔 값만 보존하고 움직이지 않았다).
  - 라마·상인 라마 카펫: 몸통 칸(BODY)을 늘 쓸 수 있고 `wool_carpets` 태그만 입는다(바닐라 `Llama.canUseSlot=true`, `isBodyArmorItem`). 말 인벤토리 창·우클릭·디스펜서·`body_armor_item` 저장은 기존 말 갑옷 경로를 그대로 쓴다. 당나귀·노새는 불가.
  - 열지 않은 전리품 표(`LootTable`): 부수거나 죽어 내용물을 쏟을 때, 창 칸에 닿을 때(`EntityContainer`), 깔때기가 빨아들일 때(`EntityInv`) 먼저 표를 푼다(바닐라 `getItem`/`Containers.dropContents` 의 `unpackChestVehicleLootTable(null)`). 광산 상자 수레가 대표 사례. 푸는 길은 `ServerCore.unpackContainerLoot` 가 엔티티도 받도록 넓혔다(연 플레이어 없음 = this_entity·luck 없음).
  - 엔티티 용기 창 제목: 이름표 이름이 있으면 그 이름, 없으면 종류 이름, 팀이 있으면 접두사·색·접미사(`ContainerEntity.containerTitle`).
- **남은 차이(가정)**: 제목의 hover(show_entity)·insertion 은 화면에 안 보여 붙이지 않았다. 스포너 수레의 `BaseSpawner` 는 스포너 블록과 같은 코드라 `TODO(엔티티)`(승객·equipment)가 같이 남는다. 광산 수레 아이템의 이름·`entity_data` 컴포넌트는 그대로 미구현(M8 이후).
- 시험: `ContainerEntityParityTest`(스포너 수레 틱·저장, 라마 카펫, 전리품 표 풀기 시점, 창 제목).

## 2026-11-07 블록 엔티티·지도 바닐라 대조 (묶음 M8, 바이트코드)

`javap -c -p` 로 BaseSpawner(`dby`)·TrialSpawner(`dsf`)·EquipmentUser(`btb`)·SignBlockEntity(`drs`)·SignText(`drt`)·ComponentUtils(`xc`)·ScoreContents(`yh`)·SelectorContents(`yi`)·GsonHelper.toStableString(`aye`)·MapItemSavedData(`eqt`)·BeehiveBlockEntity(`dqe`)·BeehiveBlock(`dfs`)를 대조했다.
- **이미 같았던 것(닫음)**: 깃발 무늬 컴포넌트 → 블록 엔티티(applyImplicitComponents), 깃발 부술 때 이름·무늬 드롭(copy_components + BLOCK_ENTITY 매개변수), 네더 지도 색(hasCeiling: 반경 절반·흙/돌 해시 무늬), 벌집 들고나기·꿀 레벨·저장, 표지판 저장 JSON(키 재귀 정렬 = toStableString, HTML 이스케이프 없음, 바닐라 코덱 필드 구성).
- **고친 것**: (1) 지도 표식 회전 흔들림은 바닐라가 **네더**(`dcw.i` = the_nether) 지도에서 하는데 Furnace 는 엔드에서 하고 있었다. 깃발 표식도 toggleBanner 가 레벨을 넘기므로 네더에서 돈다(불러온 깃발은 레벨 null → 안 돈다). (2) 스포너: `loadEntityRecursive` — Passengers 를 깊이 우선으로 불러 강제로 태우고(승객 id 도 정규화), 함수는 엔티티마다(BaseSpawner 는 같은 자리·자기 회전, 시련 생성기는 엔티티마다 회전 nextFloat). (3) 스포너 `SpawnData.equipment` → `Mob.equip(EquipmentTable)`; 칸 고르기는 Equipable(늑대·말 갑옷·양털 카펫 → 몸, 방패 → 왼손) 기준으로 바꿨다(전엔 이름 판정이라 늑대 갑옷이 주 손으로 갔다). 시련 생성기도 같은 메서드를 쓴다. (4) 표지판: 레벨에 놓인 뒤 읽을 때(/data merge block, /setblock·/fill NBT, /clone, 아이템 block_entity_data, /place·구조물 블록 배치) 그때 읽은 면만 줄마다(원문 → 걸러진 것) `ComponentUtils.updateForEntity(표지판 소스, 줄, null, 0)` — 소스는 이름 "Sign"·권한 2·칸 가운데·엔티티 없음·그 월드. 명령 오류면 원래 줄. 청크 읽기는 레벨이 없어 안 푼다(바닐라와 같음). 면 색은 `fieldOf.orElse(BLACK)` 이라 틀린 색이면 검정(전엔 면 전체를 버렸다). (5) `ComponentUtils`: 점수(ScoreContents) 풀기 추가 — 이름을 선택자로 읽어 대상이 하나면 그 주인(둘 이상이면 toomany 오류), 없으면 이름 그대로, `*` 는 entity 로; 목표·점수가 없으면 빈 텍스트, 있으면 목표 숫자 형식 또는 NO_STYLE. 선택자 결과는 formatList — 하나면 표시 이름 자체(빈 텍스트로 감싸지 않음, 스타일은 바닐라 withStyle 처럼 원래 줄이 정한 값만 덮고 표시 이름의 클릭·호버·삽입은 남음 — 처음엔 통째로 바꿨다가 독립 검토에서 고침), 여럿이면 빈 텍스트에 구분자로. (6) `Player.displayName` 에 바닐라처럼 엔티티 호버(show_entity)를 붙였고, 플레이어·표지판 명령 소스의 표시 이름도 이것을 쓴다(전엔 팀 서식·호버가 빠졌다). (7) 벌집 크리에이티브 파괴(doTileDrops, 벌이 있거나 꿀 > 0): bees + block_state{honey_level} 를 실은 아이템을 칸 모서리에 떨군다(M17 줄에서 지움).
- **M11 과 합침**: 기준 브랜치의 M11 이 `ComponentUtils.resolve`(future, 일반 엔티티 선택자·NBT 까지)를 넣어 M8 의 동기 판을 버리고 그것을 쓴다. 표지판은 `BlockLevel.resolveSignLines(pos, 줄들, apply)` — 플레이어·점수판만 쓰면 그 자리에서, 다른 region 의 엔티티를 읽으면 끝난 뒤 그 칸의 주인 region 에서 넣고(그새 표지판이 바뀌었으면 버림) 갱신을 보낸다.
- **남은 차이**: 위의 늦게 끝나는 경우 바닐라(동기)보다 한 틱 안팎 늦게 바뀐다(최종 글은 같다). 아직 포팅 안 된 엔티티 종류의 스포너 승객은 빠진다. 지도의 미로드 청크 칠하기(기존 TODO)는 그대로.

## 2026-11-07 청크 전송 auto · furnace.properties 자동 생성
- 청크 로딩 속도는 봇(mineflayer)으로 못 잰다 — `tools/bot-test/chunkrate/chunkrate.js` 처럼 `chunk_batch_received` 값을 정해 답하는 접속기를 쓴다. 진단·전후 수치: `perf-chunk-send-2026-11-07.md`.
- `chunks-per-tick=auto`(기본)는 준비된 청크만 배치에 넣는다. 배치에 생성 중인 청크를 넣으면 Finished 가 가장 느린 생성을 기다려 클라이언트 응답이 늦어지고 이미 있는 청크까지 막힌다.
- `World.serializedChunkBody(cp, true)` 는 주인 region 스레드면 작업 큐를 건너뛴다. 다른 곳에서 inline 을 켜도 그 스레드가 주인이 아니면 예전처럼 큐로 간다.
- furnace.properties 를 읽는 새 키를 넣으면 `FurnacePropertiesFile.sections()` 에도 넣는다(`FurnacePropertiesFileTest` 가 접속 방어 키와 고정 목록을 확인한다).

## 2026-11-07 팀·PvP 규칙 연결 (묶음 M16)
- 바닐라 1.21.1 바이트코드 대조(`bsr.s`=Entity.isAlliedTo(Entity), `bsr.a(eyi)`=isAlliedTo(Team), `cmx.a(cmx)`=Player.canHarmPlayer, `cef.a`=TargetingConditions.test, `cce.h`=HurtByTargetGoal.alertOthers, `cgh.a`=Wolf.wantsToAttack, `cnd.l`=AbstractArrow.tick): `isAlliedTo` 를 부르는 곳은 타게팅 조건·플레이어 쓸기(`cmx`)·철퇴 충격파(`cvb`)·워든 canTargetEntity(`cmb.c`)·HurtByTarget 경보·좀비 피글린 경보·송곳니(`cnh`)·MobEffectUtil.addEffectToPlayersAround(`bsa`, 장로 수호자 저주·워든 어둠) 이다. 모두 이어 붙였다.
- **구조**: `Targetable.team()`/`isAlliedTo(other)` 기본 구현(같은 팀 = `PlayerTeam.isAlliedTo`). `LivingEntity.team()` = `getTeam()`, `Player.team()` 은 원래 있던 것. 덮어쓰기: `TamableAnimal`(길들였으면 주인·주인의 아군), `AbstractIllager`(팀 규칙 뒤, `illager_friends` 태그는 **둘 다 팀이 없을 때만** — 이전엔 팀과 무관하게 동맹이었다), `Evoker` 는 그대로.
- **PvP**: `Player.canHarmPlayer(other, pvp)` — this 쪽 서버 설정 pvp 가 꺼지면 거짓, 팀이 없거나 다른 팀이면 참, 같은 팀이면 그 팀의 아군 피해. 쓰는 곳: 맞는 쪽(`PlayerVitals.hurt`, 이미 있던 인라인 검사를 이 함수로), **화살 주인→맞은 사람**(`AbstractArrow.tick`: 못 때리면 hit·entityHit 둘 다 null — 바닐라처럼 블록 맞음도 그 틱엔 사라지고 사람을 통과한다), **늑대**(`wantsToAttack`: 주인이 맞는 사람을 못 때리면 공격 안 함, 길들인 말도 안 함 — 이쪽은 이전에 빠져 있었다). pvp 설정은 `LevelServices.pvp()`(기본 참, `ServerCore` 가 `settings.pvp`) → `ChunkLevel.pvp()`.
- **TargetGoal.canContinueToUse**: 바닐라는 isAlliedTo 가 아니라 `getTeam()` 동일 객체 비교(`team != null && target.team == team`). 그대로 옮겼다. HurtByTargetGoal 경보는 `isAlliedTo(lastHurtByMob)` + 길들인 동물은 **주인이 같을 때만** 돕는다(이전엔 둘 다 빠짐).
- **전리품 피해 태그**: `/loot kill`(`minecraft:magic`)과 몹 죽음 전리품(`ServerCore.entityDrops`) 의 `LootDamageSource.is(tag)` 가 늘 거짓이었다 → `DamageType.keyHasTag`. `damage_source_properties` 의 `is_fire`·`is_explosion` 등 태그 조건이 이제 맞다.
- **/teammsg 채팅 필터**: 바닐라의 `shouldFilterMessageTo` 분기는 글자 걸러내기 서비스(`text-filtering-config`)가 없으면 마스크가 PASS_THROUGH 라 관측 결과가 같다(받는 쪽 클라이언트 옵션과 무관하게 원문, "모두 걸러짐" 안내도 없음). Furnace 는 그 서비스를 아직 지원하지 않으므로(FurnaceMain 경고) 코드는 안 늘리고 주석으로 닫았다.
- **독립 검토(읽기 전용)가 찾은 둘, 고침**: ① `TamableAnimal.getTeam` 은 길들였고 주인이 보이면 **주인의 팀**(바닐라 `bul.ck`)인데 Entity 기본(자기 UUID 의 팀)을 그대로 써서 쓸기·충격파·워든·선택자 `team=`·팀 충돌이 펫에서 틀렸다. ② `PlayerVitals.hurt` 의 `ap != player` 예외 제거 — 바닐라 `ServerPlayer.hurt` 는 자기 자신이 원인이어도 `canHarmPlayer(자기)` 를 검사해, pvp 꺼짐·아군 피해 꺼진 팀에서는 내 화살·내 TNT·내 폭죽에 안 다친다. `Player.team()` 에는 `hasTeams()` 단락 추가.
- 시험: `TeamPvpRulesTest` 10개(같은 팀/없음, 전투 조건, 목표 지속, 길들인 동물 주인, 일러저 친구, canHarmPlayer 표, 맞는 쪽 무시, 늑대, 길들인 말, 화살 통과).

## 2026-11-07 월드젠 단계 훅·청크 재생성 (W3)
- API `LifecycleEvents.WORLD_GENERATION` → `WorldGenerationRegistrar.register(키, ChunkGenerationHooks)`. 월드는 키만 쥔다(`WorldCreator.generationHooks`, 기본 차원은 `worldgen-hooks.*`). 서버 `plugin/GenerationHookStore`(키 → 플러그인·훅, 꺼지면 빠짐), `world/gen/GenHooks`(월드 바인딩·예외 격리·BlockData 변환). 문서 `docs/api/worldgen-hooks.md`.
- 훅 지점: `NoiseWorldSource.generateWithBiomes`(NOISE·SURFACE — 높이 조회 `generate()` 에도 들어가 구조물이 플러그인 지형 위에 선다), `applyCarvers`(CARVERS, WG 높이 지도 얼리기 전), `applyStructureStarts`(STRUCTURES, 고른 뒤 거르기), `FeaturePlacer.decorate`(FEATURES 끄면 지형지물만 건너뛰고 구조물 조각은 놓음, 지형지물별 거르기), `decorateOrigin` 뒤 `decorate` 훅. 평지·빈 월드는 `SuperflatWorldSource.generate` 한 곳.
- 훅이 바뀌면(핫 리로드·다시 걸기) `NoiseWorldSource.refreshHookCaches` 가 지형·높이·시작점·pristine 캐시를 비운다(`generateIsolated`·`generateWithFeatures` 가 부른다). 서버 경로의 공유 장식 저장소는 이미 넘친 청크가 있어 그대로 둔다.
- 재생성: `World.regenerateChunk` → 생성 풀에서 `generateIsolated`(독립 경로) → 그 청크 region 에서 `Region.replaceRegenerated`. 빛은 옛 `ChunkLight` 를 새 컬럼에 옮기고 바뀐 칸만 `LevelLight.blockChanged` — 새 빛을 처음부터 만들면 이웃으로 번진 옛 빛이 남는다. 재전송은 구독을 새로 만들어(ready=false) 옛 직렬화 전송을 버리게 한다.
- 함정: `RegionLevel` 의 청크 조회 캐시(`chunkUnloaded()`)를 교체 직후 비워야 옛 객체를 읽지 않는다. 시험 월드에서 구독자 없는 청크는 몇 틱 뒤 내려가므로 시험은 구독을 걸어 둔다.
- 독립 검토 뒤 고친 함정:
  - 엔티티 로드 경합: `finishLoad` 는 디스크 엔티티 읽기를 비동기로 걸고 청크 future 를 먼저 끝낸다. 그 사이 교체하면 읽은 엔티티가 동일성 검사에 걸려 버려지고 `entitiesLoaded` 가 영영 false(FRESH 는 디스크 엔티티를 못 지운다). `ChunkColumn.pendingEntityLoad` 를 두고 재생성은 그걸 기다린다.
  - 남긴 엔티티의 `DynamicGameEventListener` 는 구역이 같으면 다시 걸지 않아 옛 청크에 남는다 — 교체 뒤 그 청크 엔티티마다 remove/add.
  - 캐시 무효화 표식이 훅 객체를 강하게 쥐면 꺼진 플러그인 클래스로더가 샌다 — `GenHooks.stamp()` 는 전역 번호 + 약한 참조. 표식 비교는 서버 경로 `generateWithFeatures` 앞에서도 한다(훅 없는 월드는 volatile 읽기 하나).
  - 훅 격리는 `LinkageError` 도(핫 언로드 중 NoClassDefFoundError — 공유 장식 도중 터지면 원점이 반쯤 장식된 채 남는다).
  - 재생성 생성은 월드마다 한 줄로(`regenTail`) — 289개가 생성 대기 풀을 다 붙잡지 않게.
- 시험: `GenHooksTest` 12, `GenerationHookStoreTest` 1, `RegenerateChunkTest` 2(알레이 진동 리스너·FRESH 엔티티 지우기 포함).
