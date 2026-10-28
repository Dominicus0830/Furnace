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
