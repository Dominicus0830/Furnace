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
- **선택자가 플레이어 말고 일반 엔티티도 고른다.** 엔티티는 region 이 나눠 갖는 상태라 `EntitySelector.findRiders` 가 모든 region 에서 후보를 모아 합친 future 를 돌려준다. 명령은 `EntityArgument.withEntities/withEntity` 로 받고, 이미 끝났으면 그 자리에서(오류도 그대로 던진다), 아니면 1 을 돌려준 뒤 끝나는 시점에 실행한다. 그 안에서 엔티티를 바꾸는 일은 반드시 그 엔티티의 주인 region 으로 보낸다(`EntityOps.onOwner`, `Teleports.teleport`). 기존 `/kill`·`/effect` 는 아직 플레이어만 본다.
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
- 제외: projectilesCanBreakBlocks(소비처 Block.onProjectileHit 훅이 아직 없다), spectatorsGenerateChunks(관전자가 청크를 올리지 않고 남이 올린 청크만 받는 구독 모델이 필요), maxEntityCramming(성능 설계 필요), commandBlockOutput(명령 블록 미구현).
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
- **region-file-compression**: `Compression.configure` — `deflate`(기본)·`gzip`·`none` 지원, 청크 헤더의 압축 종류 바이트(1/2/3)를 쓰고 읽는다. **`lz4` 는 미지원**: 바닐라가 쓰는 LZ4 블록 스트림(`net.jpountz`) 라이브러리가 없다. lz4 로 저장된 월드는 읽지도 못한다(읽기도 type 4 미지원 — 기존). 잘못된 값이면 바닐라처럼 오류를 로그하고 deflate 를 유지한다. 프로세스 전역 하나(바닐라 `RegionFileVersion.configure` 동일).
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
- **lz4 경고**: `region-file-compression=lz4` 미지원이며 읽기도 안 된다. 1.20.5+ 바닐라에서 lz4 로 저장된 월드를 가져오면 청크 읽기가 실패한다(가져오기 전에 바닐라에서 deflate 로 바꿔 저장해야 함).
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
  - 기존에 팬텀·순찰·고양이·상인 스포너가 모든 차원에서 돌던 것 중 **상인만** 오버월드로 제한했다(바닐라는 네 개 모두 오버월드 전용이지만 나머지 셋은 손대지 않음 — 후속).
- **방랑 상인 타이머 저장**: `WanderingTraderState`(월드 공유)를 오버월드 `World` 에 붙이고 `LevelData.wanderingTrader` 로 level.dat 의 `WanderingTraderSpawnDelay/Chance/Id` 를 읽고 쓴다(0/0 이면 24000/25 — 바닐라 생성자 규칙). 스포너가 상인을 내면 `WanderingTraderId` 에 UUID(int[4])를 기록한다. region 마다 스포너가 있으므로 같은 게임 틱에는 한 region 만 타이머를 돌린다(`claimTick`).
  - **다른 점**: 바닐라는 지연을 24000 으로 되돌린 값을 바로 data 에 쓰지 않고 다음 감소 때 쓴다(저장 직후 재시작하면 ≤0 으로 읽혀 곧 다시 굴린다). 여기서는 살아 있는 값을 그대로 둬 저장값이 항상 현재 값이다. 상인 스폰 자체는 타이머를 이긴 region 의 플레이어 중에서 고른다(바닐라는 차원 전체) — 그 region 에 플레이어가 없으면 "플레이어 없음 → 확률만 초기화" 경로를 탈 수 있다.
- **스폰 이유**: 몹 생성기(`BaseSpawner`)·시련 생성기가 주민/습격자에 SPAWNER/TRIAL_SPAWNER 이유를 넘기게 했다(바닐라 `finalizeSpawn(…, SPAWNER, …)`) — 생성기 마녀가 습격에 참여하고, 생성기 주민은 그 자리 바이옴의 종류를 받는다. 발사기 스폰 알은 SPAWN_EGG 로 남긴다(주민 쪽 결과는 DISPENSER 와 같다).
- 시험: `VillageSiegeTest`, `WanderingTraderTest`(level.dat 왕복·공유 타이머·상인 번호), `RaiderGoalsTest`(축하·마을로 향하기·깃발 줍기·마녀 참여 이유). 생성기 → 주민/습격자 이유 연결(`BaseSpawner`·`TrialSpawner`)은 시험 레벨에서 몹이 서지 않아 직접 시험하지 못했다(`finalizeSpawn` 쪽 분기만 시험) — 가정.

## 2026-11-02 마을 포위 후속 — 좀비 finalizeSpawn 난수 정정 외
- **좀비 finalizeSpawn 난수(parity 수정)**: `Zombie.finalizeSpawn` 이 바닐라(1.21.1 ckw)에 없는 `nextBoolean()` 을 하나 더 소비해, 이후 난수가 모든 좀비 스폰에서 한 칸씩 밀려 있었다. 바닐라는 `new ZombieGroupData(getSpawnAsBabyOdds(r), true)` 로 두 번째 값이 상수 `true` 라 난수를 쓰지 않는다. 상수로 고쳤고 `ZombieFinalizeRandomTest` 가 시드 50개에서 `nextBoolean` 호출 0 을 고정한다.
- **방랑 상인 타이머 원자화**: `claimTick`·읽기·감소·쓰기를 `state.lock()` 으로 묶었고, level.dat 저장도 같은 잠금으로 (delay, chance) 쌍을 한 번에 읽는다. 승자 region 에 플레이어가 없으면 타이머를 소비하지 않는다. 상인 대상 플레이어는 여전히 타이머를 돌리는 region 의 플레이어 중에서 고른다(바닐라는 차원 전체) — 차이.
- **알·발사기 마녀**: 스폰 알과 발사기로 낸 마녀도 스폰 이유를 받아 습격에 참여한다(`spawnMobFromEgg(..., SpawnReason)`).
- **포위 좀비 난이도**: `finalizeSpawn` 을 `moveTo` 앞에 불러 원점 기준 난이도를 쓰는 바닐라 순서를 `VillageSiegeTest` 가 고정한다.
