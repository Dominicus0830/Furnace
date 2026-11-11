# A10 몹 AI·두뇌·스폰 — 2차 전체 점검 조사 보고서 (2026-11-08)

- 기준: 브랜치 `claude/quirky-wright-lra834`, 커밋 `8f8b188f`(PR #170 병합 시점). 코드는 고치지 않았다.
- 바닐라 근거: 1.21.1 서버 jar(`main` 브랜치 `tools/vanilla-data/server-1.21.1`)를 `javap -c -p` 로 읽었다. 난독화 이름을 함께 적는다.
- 재현 시험: `A10-repro/*.java.txt` 3개(시험 11개). 기준 커밋에서 돌리면 **10개가 실패**하고(차이 재현), 1개(`alwaysBlocksStayValid`)는 같음 확인용이라 통과한다. 쓰는 법: 확장자 `.txt` 를 떼고 `A10SpawnChunkCountTest` 는 `furnace-server/src/test/java/org/domi/furnace/server/world/`, 나머지 둘은 `.../server/entity/` 에 넣는다.

## 요약

| 심각도 | 수 | 항목 |
|---|---|---|
| 치명 | 0 | |
| 높음 | 1 | A10-01 |
| 중간 | 4 | A10-02, A10-03, A10-04, A10-05 |
| 낮음 | 4 | A10-06, A10-07, A10-08, A10-09 |
| 참고 | 3 | A10-10, A10-11, A10-12 |

가장 큰 것은 A10-01이다. 혼자 있는 플레이어 둘레의 자연 스폰 전역 상한이 바닐라보다 약 3분의 1 낮다(몬스터 70 → 47). 이 값은 시뮬레이션 거리를 줄이면 더 낮아진다.

## 조사한 범위

- 자연 스폰 `entity/spawn/NaturalSpawner`: 틱 흐름(`Region.collectTickingChunks` → `RegionLevel.tickBlocks`), `createState`, 전역·플레이어별 상한, `spawnCategoryForChunk`·`spawnCategoryForPosition` 난수 순서, 위치·종류 판정, 에너지 예산. 바닐라 `ServerChunkCache.tickChunks`(aqs), `NaturalSpawner`(ddh, `ddh$d`), `DistanceManager`(aqk, `aqk$b`) 와 맞춰 봤다.
- 스폰 바닥 판정 `SpawnPlacements.isValidSpawn`: 바닐라 `Blocks`(dga) 정적 초기화의 `isValidSpawn` 술어 전부를 뽑아 대조했다.
- 몹 목표 표: 바닐라 `bsx`(EntityType) 등록에서 몹 129종의 클래스를 찾고, 상속 사슬 안 모든 메서드의 `GoalSelector.addGoal`(can.a(ILcam;)V) 호출을 (선택기, 우선순위)로 뽑았다. 같은 방식으로 Furnace `goalSelector/targetSelector.addGoal` 을 뽑아 비교했다(Furnace 125종이 겹친다. 디스플레이 3종과 낚시찌는 몹이 아니다).
- 두뇌: 두뇌 몹 10종(알레이·아홀로틀·개구리·염소·호글린·피글린·피글린 야수·올챙이·주민·조글린)의 센서·기억 목록과 `setMemoryWithExpiry` 만료 상수, `Brain.tick` 순서, `ExpirableValue`.
- 길찾기: `WalkNodeEvaluator` 의 칸 종류 분류(getPathTypeFromState·getPathTypeStatic·checkNeighbourBlocks)와 문·레일 평가.
- 특수 스포너: 팬텀·순찰·고양이(region 마다 도는 것).
- 사라짐: `Mob.checkDespawn`.
- 메모 후보 "몹 AI 난수 시험 안정화": `LandMobTest` 는 커밋 `c95e4f7f` 에서 이미 레벨·몹 난수를 고정했다. 처리된 것으로 본다(흔들리는 시험 전반은 A19).

## 조사하지 못한 범위 (이유)

- 두뇌 몹의 **활동별 행동 목록**(행동 클래스 이름 단위 대조): 매핑 파일이 없어 행동 클래스를 이름으로 맞출 수 없다. 센서·기억 목록과 만료 상수만 맞춰 봤다.
- 낙타·스니퍼·아르마딜로·브리즈·워든의 센서 목록: 이 몹들은 목록 상수를 몹 클래스가 아닌 곳에 두어 스크립트가 찾지 못했다. 목표·스폰 쪽만 봤다.
- 습격 파도·POI: M4(PR #86)와 M5 가 바이트코드로 닫았다(입력 문서). 다시 하지 않았다.
- 틱 단위 장면 대조(바닐라 서버 실행): Mojang 라이브러리 호스트가 막혀 바닐라 서버를 돌릴 수 없다. 바이트코드 근거와 Furnace 시험으로 대신했다.
- 전투 피해(A11)·AI 성능(A17)은 범위 밖이다.

## 같음 확인 (다시 볼 필요 없는 것)

- 목표 표: 125종 중 121종은 (선택기, 우선순위) 다중집합이 바닐라와 같다. 다른 4종은 아래 A10-02·04·09 다.
- 두뇌 몹 10종의 센서 목록은 모두 같다. 기억 목록은 아홀로틀 하나만 다르다(A10-11).
- 기억 만료 상수 약 30개(피글린 감탄 119·400, 분노 600, 호글린 공격 200, 워든 굴 1200·만짐 20·진동 40, 브리즈 점프 2/10·사격 60/100, 아홀로틀 사냥 2400, 철 골렘 감지 599, 스니퍼 9600 등)가 같다. `Brain.tick` 순서(기억 잊기 → 센서 → 행동 시작 → 행동 틱)와 `ExpirableValue`(먼저 만료 검사, 그다음 1 감소)도 같다.
- `GoalSelector` 의 선택 규칙(같은 우선순위는 서로 빼앗지 못함, 넣은 순서로 돎)이 같다.
- 길찾기 칸 분류 `getPathTypeFromState`·`getPathTypeStatic`·`checkNeighbourBlocks`, 문(`WALKABLE_DOOR`·`DOOR_OPEN`)·레일(`UNPASSABLE_RAIL`) 평가가 같다.
- `spawnCategoryForPosition` 의 난수 호출 순서(무리 크기 ceil(nextFloat×4), ±6 이동 두 번, 종류 고르기, minCount+nextInt, 방향 nextFloat×360), 24블록·스폰 지점 24블록 판정, `isValidSpawnPositionForType`, `isValidPositionForMob`, 플레이어별 상한(`LocalMobCapCalculator`), 400틱마다 유지 종류도 같다.
- `Mob.checkDespawn`(128 즉시, 32 밖이고 600틱 무행동이면 1/800)가 같다.
- `PhantomSpawner` 본문(난수 순서 포함)이 같다.

## 항목

| ID | 심각도 | 종류 | 근거 | 재현 | 제안 |
|---|---|---|---|---|---|
| A10-01 | 높음 | 바닐라 차이 | 아래 상세 | `A10SpawnChunkCountTest.naturalSpawnChunkCountForOnePlayerIs289`: 기대 289, 실제 **195** → 몬스터 상한 **47**(바닐라 70) | `Region.collectTickingChunks` 의 `spawnChunkCount` 를 "스폰 대상 청크 수"와 떼어, 관전자 포함(`spectatorsGenerateChunks` 가 참일 때) 플레이어 청크 둘레 체비셰프 8 칸의 합집합 수로 센다. 청크 로드·시뮬레이션 거리·128블록 원과 무관해야 한다. 작음. Sonnet high |
| A10-02 | 중간 | 바닐라 차이 | 아래 상세 | `A10ReproTest.catAvoidPlayersGoalPriorityIs4`: Furnace 목표 표 끝이 `3 CatAvoidEntityGoal` | `Cat.reassessTameGoals` 의 `addGoal(3, …)` 를 4로 바꾼다. 작음. Sonnet medium |
| A10-03 | 중간 | 바닐라 차이 | 아래 상세 | `A10ReproTest.lightBlocksAreNotValidSpawnInVanilla`(발광석이 참), `copperGratesAreNeverValidSpawn`(구리 격자가 참) | `SpawnPlacements.initSets` 의 ALWAYS 를 바닐라 always 목록(jack_o_lantern·redstone_lamp·carved_pumpkin·soul_sand·mud)으로 줄이고, NEVER 에 구리 격자 8종을 넣는다. 가능하면 이름 목록 대신 블록 속성에 바닐라 술어를 달아 TODO 를 없앤다. 작음~중간. Sonnet high |
| A10-04 | 중간 | 바닐라 차이 | 아래 상세 | `A10ReproTest.witherSkeletonTargetsPiglins`: 대상 목록에 `AbstractPiglin` 없음 | `WitherSkeleton` 에 `registerGoals` 를 두고 `targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, AbstractPiglin.class, true))` 를 **super 앞에** 넣는다(넣는 순서도 바닐라와 같게). 작음. Sonnet medium |
| A10-05 | 중간 | 바닐라 차이 | 아래 상세 | `A10RegionSpawnerTest.patrolPicksPerPlayerDoubleWithTwoRegions`: 같은 시간에 플레이어 A 가 뽑힌 횟수가 스포너 하나 62번, region 마다 119번 | 순찰·고양이(·팬텀) 스포너를 차원에 하나 두고 전역 틱에서 돌린다. 포위·상인이 이미 쓰는 `DimensionSpawners` 방식을 따른다. 뽑힌 플레이어의 region 에서 스폰을 실행해야 해서 region 넘김이 필요하다. 중간. Opus high |
| A10-06 | 낮음 | 바닐라 차이 | `NaturalSpawner.isRightDistanceToPlayerAndSpawnPoint`(`NaturalSpawner.java:232`)의 마지막 조건이 `level.withinBorder(pos)`. 바닐라 `ddh.a(aqu,duy,jd$a,double)` 는 `aqu.g(jd)`(ServerLevel.isNaturalSpawningAllowed(BlockPos) → `dxr.a(jd)` 엔티티 관리자 canPositionTick, 즉 엔티티 틱 청크)다 | 가정(시험 레벨에는 엔티티 틱 개념이 없어 재현 못 함). 시뮬레이션 거리 10 이상이면 이웃 청크가 늘 엔티티 틱 범위라 차이가 없다. 9 이하이면 가장자리 청크에서 퍼진 무리가 엔티티 틱이 아닌 청크에 나서 멈춘 채 상한을 차지할 수 있다 | 마지막 조건을 "그 청크가 엔티티 틱(`Region.blockEntityTicking` 과 같은 판정)"으로 바꾼다. 작음. Sonnet medium |
| A10-07 | 낮음 | 바닐라 차이 | `NaturalSpawner.spawnCategoryForChunk`(`NaturalSpawner.java:221`)가 `getWorldSurfaceHeight(x,z) + 1` 을 쓴다. `ChunkLevel.getWorldSurfaceHeight` 는 이미 "맨 위 블록 + 1"(Level.getHeight 꼴)이다. 바닐라 `ddh.a(dcw,dvi)` 는 `dvi.a(dyy$a.b,II)`(LevelChunk.getHeight = firstAvailable − 1) + 1 이다 | `A10ReproTest.spawnHeightUpperBoundIsTopBlockPlusOne`: 기대 71, 실제 72 | `+ 1` 을 지운다. 난수 범위가 한 칸 줄어 바닐라와 같아진다. 영향은 스폰 시도가 약 1% 헛도는 정도다. 작음. Haiku low |
| A10-08 | 낮음 | 바닐라 차이 | `SpawnState.canSpawn`·`afterSpawn`(`NaturalSpawner.java:131,162`)이 에너지 예산 바이옴을 `biomeName`(확대 지터가 있는 바이옴)으로 본다. 바닐라 `ddh$d.a(bsx,jd,duy)`·`ddh$d.a(btp,duy)` 는 `ddh.a(jd,duy)` = **스폰 중인 청크**의 `getNoiseBiome(quart)`(rough 바이옴)이다. Furnace `createState` 는 이미 rough 를 쓴다 | 가정(바이옴 경계에서만 다름. spawn_costs 가 있는 영혼 모래 골짜기·뒤틀린 숲 경계). 재현 시험 없음 | 두 곳을 `roughBiomeName` 으로 바꾼다. 바닐라는 이웃 청크 칸도 스폰 중인 청크의 섹션에서 `x&3, z&3` 로 읽는다. 이 버릇까지 맞추려면 청크를 넘겨야 한다. 작음. Sonnet medium |
| A10-09 | 낮음 | 바닐라 차이 | 바닐라 `Silverfish`(ckk)·`Endermite`(cju) `registerGoals` 에 `1 ClimbOnTopOfPowderSnowGoal`(cab)이 있다. Furnace 에는 없다. 1차 감사(`mob-ability-audit-2026-10-31.md`)가 "가정, 낮음"으로 적었고 아직 그대로다 | `A10ReproTest.silverfishAndEndermiteClimbPowderSnow`: 목표 표에 없음 | 목표 하나를 포팅해 두 몹에 넣는다. 작음. Sonnet medium |
| A10-10 | 참고 | 바닐라 차이 | `SpawnState.near`(`NaturalSpawner.java:69`)가 `<= 128²`. 바닐라 `ChunkMap.playerIsCloseEnoughForSpawning` 는 `< 16384.0`(Furnace `Region.closeToPlayer` 는 이미 `<`) | 가정(거리가 정확히 128.0 일 때만 다름) | `<` 로 맞춘다. 작음 |
| A10-11 | 참고 | 바닐라 차이 | 바닐라 `Axolotl`(cgq) 기억 목록에 `nearest_visible_player`·`nearest_visible_targetable_player` 가 있다. Furnace `Axolotl` 목록에는 없다 | 가정. 아홀로틀에는 플레이어 센서가 없어 이 기억은 채워지지 않는다. 이 기억을 `VALUE_ABSENT`·`REGISTERED` 로 요구하는 행동도 찾지 못했다. 관측 차이는 확인 못 함 | 목록에 두 줄을 더한다(플러그인 API `MobBrain` 의 기억 목록 조회 결과가 바닐라와 같아진다). 작음 |
| A10-12 | 참고 | 바닐라 차이 | A10-05 와 같은 원인으로, region 이 새로 생기면 그 region 의 `PhantomSpawner`·`PatrolSpawner`·`CatSpawner` 가 `nextTick = 0` 으로 시작한다. 그래서 다음 틱에 곧바로 한 번 굴린다(바닐라는 차원에 하나라 기동 때 한 번뿐이다) | 가정(코드 근거: `RegionLevel.java:241-243` 필드 초기값). region 나누기·새 region 이 잦은 서버에서 팬텀·순찰 굴림이 늘어난다 | A10-05 를 고치면 함께 없어진다 |

### A10-01 자연 스폰 전역 상한의 청크 수 (높음)

- 바닐라: `ServerChunkCache.tickChunks`(aqs)가 `ddh.a(ILjava/lang/Iterable;Lddh$b;Lddg;)`(createState)에 `aqk.b()`(DistanceManager.getNaturalSpawnChunkCount)를 넘긴다. 이 값은 `aqk$b`(FixedPlayerDistanceChunkTracker, 생성자 인자 `bipush 8`)의 칸 수다. `aqk$b.a(long,int)` 는 수준이 8 **이하**인 칸만 남기고, 수준은 플레이어 청크 0 에서 이웃(대각 포함)마다 1씩 늘어난다. 그래서 플레이어 한 명이면 17×17 = **289** 칸이다. 이 값은 청크가 올라와 있는지, 시뮬레이션 거리, 128블록 원과 **무관**하다. 관전자도 `spectatorsGenerateChunks`(기본 참)면 이 수에 들어간다.
- Furnace: `Region.collectTickingChunks`(`Region.java:1543-1563`)가 "무작위 틱을 받을 청크" 수를 `spawnChunkCount` 로 그대로 쓴다. 이 청크는 올라와 있고, 체비셰프 `min(sim−1, 8)` 안이며, 청크 가운데가 128블록 원 안(`closeToPlayer`)이다. `RegionLevel.tickBlocks` 가 이 값을 `NaturalSpawner.createState(spawnCount, …)` 에 넘긴다.
- 결과(혼자 있는 플레이어, 둘레가 모두 로드된 가장 유리한 경우): 195 칸. 상한(`max × 칸 수 / 289`)은 아래와 같다.

| 종류 | 바닐라 | Furnace |
|---|---|---|
| monster | 70 | 47 |
| creature | 10 | 6 |
| ambient | 15 | 10 |
| water_ambient | 20 | 13 |
| water_creature, axolotls, underground_water_creature | 5 | 3 |

- 시뮬레이션 거리를 낮추면 더 줄어든다. 예를 들어 시뮬레이션 거리 6(범위 5)이면 11×11 = 121 칸이라 몬스터 상한이 29 다. 바닐라는 이때도 70 이다(그래서 바닐라는 시뮬레이션 거리를 줄이면 스폰 밀도가 오른다).
- 관측 영향: 몹 농장 효율, 밤 몬스터 수, 바다 물고기 수가 바닐라보다 눈에 띄게 적다. 플레이어별 상한(`localCanSpawn`, 70)은 바닐라와 같아서 전역 상한이 먼저 걸린다.

### A10-02 고양이 "플레이어 피하기" 목표 우선순위 (중간)

- 바닐라 `Cat.reassessTameGoals`(cfh.gA)는 `can.a(ILcam;)V` 에 `iconst_4` 로 넣는다(유혹 목표 `CatTemptGoal` 과 같은 4). Furnace `Cat.java:228` 은 `addGoal(3, avoidPlayersGoal)` 이다.
- 같은 우선순위끼리는 서로 빼앗지 못한다(`canBeReplacedBy` 는 `<`). 그래서 바닐라에서는 길들지 않은 고양이가 물고기를 든 플레이어에게 유혹되기 시작하면 피하기 목표가 끼어들지 못한다. Furnace 에서는 피하기(3)가 유혹(4)을 언제든 끊는다.
- 관측 영향(가정. 목표 표 차이만 재현했다): 마을 떠돌이 고양이가 물고기를 든 플레이어에게 다가오다 달아나서 길들이기가 바닐라보다 어렵다.

### A10-03 스폰 바닥 블록 판정 (중간)

- 바닐라 `Blocks`(dga) 정적 초기화의 `isValidSpawn` 지정을 모두 뽑은 결과:
  - never(`dga.a`): bedrock, glass, tinted_glass, 염색 유리(도우미 `dga.b(cti)`), 트랩도어 전부, barrier, chorus_flower, scaffolding, **copper_grate**. 나머지 구리 격자 7종은 `dtb$d.a(dtb)`(ofFullCopy(copper_grate))라 never 를 물려받는다.
  - always(`dga.b`): **soul_sand, carved_pumpkin, jack_o_lantern, redstone_lamp, mud** 다섯뿐이다.
  - 나뭇잎(`dga.c`, 도우미 `dga.a(dnm)`)은 오셀롯·앵무새만, 얼음(`dga.f`)·살얼음(`dga.e`)은 북극곰만, 마그마(`dga.d`)는 불 면역만.
  - 그 밖은 기본 술어 `dtb$d.a(dtc,dcc,jd,bsx)`: 윗면이 단단하고 **빛 < 14**.
- Furnace `SpawnPlacements.initSets`(`SpawnPlacements.java:66-80`)는 ALWAYS 에 glowstone·sea_lantern·beacon·shroomlight·froglight 3종을 넣었다(바닐라는 빛 15 라 불가). 그리고 NEVER 에 구리 격자가 없다(바닐라 never).
- 관측 영향: 네더는 블록 빛 제한이 없어서(`monster_spawn_block_light_limit` 15), 발광석·바다 랜턴 바닥 위에도 가스트·피글린·마그마 큐브가 난다. 오버월드에서는 그 칸 블록 빛 때문에 몬스터가 대개 막힌다. 구리 격자는 빛을 내지 않아 어두우면 그 위에 몬스터가 난다(시련의 방 바닥, 건축물). 스폰 막기 설계가 바닐라와 다르게 동작한다.

### A10-04 위더 스켈레톤이 피글린을 노리지 않음 (중간)

- 바닐라 `WitherSkeleton.registerGoals`(cku.B)는 `bX`(targetSelector)에 `iconst_3` 로 `ccf`(NearestAttackableTargetGoal)·`clm`(AbstractPiglin)·`true` 를 넣은 뒤 `cjl.B`(AbstractSkeleton.registerGoals)를 부른다. 그래서 대상 목록 첫 줄이 피글린이다.
- Furnace `WitherSkeleton` 은 `registerGoals` 를 덮어쓰지 않아 이 목표가 없다.
- 관측 영향: 요새·보루 근처에서 위더 스켈레톤과 피글린(야수 포함)이 싸우지 않는다. 피글린 쪽에서 위더 스켈레톤을 피하거나 노리는 동작(PiglinAi)은 있어서 한쪽만 싸운다.

### A10-05 특수 스포너가 region 마다 따로 돎 (중간)

- 바닐라 `MinecraftServer.createLevels` 는 오버월드 `ServerLevel` 에 팬텀(dzk)·순찰(dzj)·고양이(cmg)·포위·상인을 **하나씩** 준다. 순찰은 `nextTick`(12000+nextInt(1200))이 될 때마다 `level.players()` **전체**에서 한 명을 뽑는다. 고양이는 1200틱마다 `getRandomPlayer()` 로 한 명을 뽑는다.
- Furnace `RegionLevel.java:241-243` 은 세 스포너를 region 레벨의 필드로 두고, `tickCustomSpawners` 에 그 region 의 플레이어만 넘긴다. `PatrolSpawner.java:3`·`CatSpawner.java:3` 주석과 `context-notes.md` 936행에 "남은 region 차이"로 적혀 있지만, 받아들인 차이로 정해진 적은 없다.
- 결과: 플레이어들이 서로 멀어 region 이 k 개면, 순찰대와 고양이 스폰 시도가 플레이어마다 최대 k 배가 된다. 재현 시험에서는 플레이어 둘이 각자 region 에 있을 때 한 명이 뽑힌 횟수가 62 → 119 로 약 2배였다. 팬텀은 원래 모든 플레이어를 돌아서 평균 빈도는 같다. 다만 타이머가 region 마다 따로 돈다(A10-12).
- 포위·상인은 이미 `DimensionSpawners` 로 차원에 하나다. 같은 방식으로 옮기면 된다.

## 도미니코 확인·결정

없다. 모든 항목이 바닐라와 같게 고치는 방향이다. 실제 클라이언트로 봐야 하는 것도 없다(A10-02 고양이 동작은 서버 시험으로 확인할 수 있다).
