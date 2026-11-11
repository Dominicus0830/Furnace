# A06 월드젠 바닐라 대조 — 2차 전체 점검 조사 보고서 (2026-11-08)

- 기준: 브랜치 `claude/quirky-wright-lra834`, 커밋 `eab009d9`(PR #180 병합 시점). 코드는 고치지 않았다.
- 바닐라 근거: 1.21.1 서버 jar(`main` 브랜치 `tools/vanilla-data/server-1.21.1`)를 Vineflower 로 스크래치패드에 디컴파일해 읽었다(커밋하지 않음). 난독화 이름은 아래 "바닐라 대응표".
- 재현 시험: `A06-repro/` 4개 파일, 시험 7개. 기준 커밋에서 **6개가 실패**하고(차이 재현), 1개(`A06NoopFeatureScanTest`)는 같음 확인용이라 통과한다. 쓰는 법은 맨 끝.
- 독립 검증: 항목마다 Furnace 코드와 바닐라 디컴파일을 다시 읽는 별도 검증(읽기 전용)을 한 번 거쳤다. 결과와 반영은 "검증 결과" 절.

## 요약

| 심각도 | 수 | 항목 |
|---|---|---|
| 치명 | 0 | |
| 높음 | 5 | A06-01 저장된 구조물 조각 id · A06-02 보물 지도가 빈 지도 · A06-03 생성 때 유체 틱 없음(샘·용암 폭포·대수층 가장자리) · A06-04 평지 월드 구조물·지형지물 없음 · A06-05 데이터 팩 월드젠 무시 |
| 중간 | 2 | A06-06 탐험 지도 skipKnownStructures 없음 · A06-07 구조물 생성 꺼짐에도 `/locate` 가 찾음 |
| 낮음 | 0 | |
| 참고 | 3 | A06-08 noise_settings 2개 없음 · A06-09 지그소 패딩 기준 · A06-10 조사 방법 한계 |

도미니코 확인이 필요한 항목은 `docs/user-todo/entries/2026-11-08-audit-A06.md`(결정 1건, 확인 1건).

한 줄 요약: **지형·동굴·지형지물·구조물 조각 생성 자체는 바닐라 코드와 같다**(대조 범위는 아래 "같음 확인"). 차이는 모두 생성 결과를 **세계에 이어 붙이는 가장자리**에 있다 — 저장 형식(A06-01), 전리품·지도(A06-02·06), 생성 직후 유체 틱(A06-03), 기본 경로가 아닌 월드 타입(A06-04)과 데이터 팩(A06-05).

## 조사한 범위

- 구조물: 배치(`RandomSpreadStructurePlacement`·`ConcentricRingsStructurePlacement`·제외 구역·빈도 감소 방식), 구조물 선택(가중치·바이옴 검사·`possibleSet`), `StructureCheck` 예측, 고정 조각 구조물 13종의 조각 코드, 지그소(`JigsawPlacement.addPieces`·`Placer.tryPlacingChildren`·우선순위 대기열·풀 별칭·확장 핵), 저장(`ChunkStructures.toNbt`).
- M13 이 남긴 대조: 정글 사원·마녀 오두막·해저 신전·전초기지·고대 도시·시련의 회당·보루 잔해·옛 길 잔해 — 고정 조각 3종(정글 사원·마녀 오두막·해저 신전)은 정수 상수 대조, 지그소 5종은 공통 코드 대조로 확인했다.
- `/locate structure`, 탐험 지도(지도 제작자 거래), 전리품 `exploration_map`, 엔더의 눈이 쓰는 `findNearestMapStructure`.
- 월드 타입: `level-type`(normal·flat·large_biomes·amplified), `generator-settings` JSON, 평지 프리셋, 시드 해석(빈 시드·숫자·문자열 해시).
- 지형지물 196개(`configured_feature`)가 Furnace 파서에서 Noop 으로 떨어지는지 전수 검사.
- 월드젠 데이터(바이옴·지형지물·처리기·구조물·구조물 세트·템플릿 풀·카버·밀도 함수·노이즈·구조물 `.nbt` 1180개)를 바닐라 jar 와 바이트 비교.
- 생성 후처리(`postProcessGeneration`)·샘·성채 용암의 예약 틱.
- W3 훅이 꺼졌을 때 출력이 같은지(훅 기본값·분기).

## 조사하지 못한 범위 (이유)

- **바닐라 실서버로 같은 시드 블록 대조**: 바닐라 서버를 돌리려면 Mojang 라이브러리(DataFixerUpper·brigadier·authlib)가 필요한데 호스트가 막혀 있다. 공개 소스로 빌드해 돌리는 방법은 이 컨테이너의 자동 권한 판정에서 거부돼 하지 않았다(우회하지 않음). 그래서 이번 대조는 **바이트코드·디컴파일 코드 대 Furnace 코드**와 **데이터 바이트 비교**까지다. 기존 oracle fixture 시험(`worldgen-oracle`)이 지형 블록 대조를 맡고 있다. 실서버 대조를 열지는 user-todo 확인 1.
- **월드 끝(±30,000,000)·아주 큰 시드·음수 좌표의 블록 출력**: 코드에서 `floorDiv`·`floorMod`·`long` 시드 계산과 `PerlinNoise.wrap` 이 바닐라와 같은 것만 확인했다. 실제 출력 대조는 위와 같은 이유로 못 했다.
- **네더·엔드 출력**: 구조물(성채·보루·엔드 도시) 코드 대조까지. 지형 블록은 oracle 범위.
- 마녀·고양이 `finalizeSpawn`(마녀 오두막 구조물 몹)은 몹 쪽(A10)이라 보지 않았다.
- 성능(A17)·저장 손상(A05)은 범위 밖.

## 같음 확인 (다시 볼 필요 없는 것)

- **고정 조각 구조물**: 정글 사원(`elt`)은 정수 상수 튜플이 정렬 비교로 완전히 같다. 마녀 오두막(`eml`)은 손으로 대조해 같다. 사막 피라미드(`elm`)·해저 신전(`emb`)·요새(`emj`)·네더 요새(`elx`)·난파선(`emh`)·폐광(`elv`)·엔드 도시(`elo`)·삼림 대저택(`emn`)·이글루(`elq`)·묻힌 보물(`elk`)·바다 폐허(`emd`)는 반복문·상수 이름 같은 표기 차이만 있다(차이 나는 튜플을 하나씩 확인). 예외는 네더 요새 용암 예약 틱(A06-03 ③).
- **지그소**(전초기지·고대 도시·시련의 회당·보루 잔해·옛 길 잔해·마을·폐허 포털 아님): `ekv.addPieces` 의 시작 조각 선택·높이 맞춤·`max_distance_from_center`, `Placer.tryPlacingChildren` 의 연결 순서·`SequencedPriorityIterator`(`ayz`) 우선순위·`selection_priority`·`placement_priority`, 풀 별칭(`pool_aliases`), 빈 풀 대체(fallback), 확장 핵(`use_expansion_hack`)이 같다.
- **배치**: 무작위 퍼짐(`ekm`) 격자·`spread_type`(triangular)·`salt`, 동심원(`ekp`) 요새 링 생성(바이옴 탐색 `findBiomeHorizontal` 포함), 제외 구역, 빈도 감소 방식 4종(`default`·`legacy_type_1~3`), `WorldgenRandom` 시드 식이 같다. `/locate` 의 나선 순서(`duz` 160·184줄)도 같다.
- **데이터**: 위 범위의 월드젠 JSON 과 구조물 `.nbt` 1180개가 바닐라 jar 와 바이트 단위로 같다. 예외는 A06-08.
- **지형지물**: 바닐라 `configured_feature` 196개 중 Noop 으로 떨어지는 것 0개(`A06NoopFeatureScanTest`, 하위 설정까지 따라감).
- **시드·월드 타입**: 빈 `level-seed` 는 무작위 시드, 숫자 문자열은 `long`, 그 밖은 `String.hashCode`. `WorldPreset` 해석(amplified·large_biomes 의 노이즈 설정·바이옴 소스)이 같다.
- **W3 훅이 꺼졌을 때**: `NoiseWorldSource`·`SuperflatWorldSource` 의 `genHooks` 기본값이 `null` 이고 모든 분기가 `hooks == null` 을 먼저 본다. 훅이 없으면 바닐라 경로 그대로다.

---

## 항목

### A06-01 [높음] 저장된 구조물 시작점의 조각 id 가 모두 `furnace:piece`

- 종류: 바닐라 차이 · Paper/Spigot 호환
- 바닐라: `PiecesContainer.load`(`ekg.a(uh, ekh)`, 디컴파일 47~67줄)는 조각마다 `id` 를 소문자로 바꿔 **`StructurePieceType` 레지스트리**(`lt.Q`)에서 찾고, 없으면 `"Unknown structure piece id"` 를 찍고 그 조각을 버린다. 조각이 하나도 남지 않은 시작점은 빈 시작점으로 읽히고 `StructureStart.isValid()`(`ejz` 25~46줄)가 거짓이라 `StructureManager` 가 무시하며, 바닐라가 그 청크를 다시 저장하면 `id:"INVALID"` 로 굳는다(`ejz` 92줄). 바닐라 조각 id 는 `mscorridor`·`nebcr`·`shstart`·`tejp`·`tesh`·`omb…`·`ecp`·`wmp`·`btp`·`shipwreck`·`nefos`·`jigsaw` 등이다.
- Furnace: `ChunkStructures.java:164` 가 모든 조각을 `id = "furnace:piece"`(상자만)로 쓴다. Furnace 자신은 다시 읽을 때(`fromNbt`, 179~226줄) 조각 상자만 쓰므로 Furnace 안에서는 문제가 없다. (`references` 0 은 새 시작점으로는 바닐라와 같은 값이다. 갱신이 없는 것은 A06-06.)
- 관측: **Furnace 가 생성한 월드를 바닐라·Paper 로 옮기면** 이미 생성된 구조물이 청크가 로드되는 순간 빈 시작점이 된다. 마녀 오두막 마녀·네더 요새 블레이즈/위더 해골·해저 신전 가디언·전초기지 약탈자 같은 **구조물 안 스폰**, `skip=true` 인 탐험 지도가 끊기고 조각마다 오류 로그가 찍힌다. 바닐라가 한 번 다시 저장하면 되돌릴 수 없다(단방향). `/locate` 는 디스크의 `id` 만 읽는 `StructureCheck`(`ejs` 105~136줄) 덕에 청크가 로드되기 전까지는 여전히 찾는다. 블록 데이터는 멀쩡하고 Furnace 안에서는 정상이라 치명(손상)이 아니라 높음으로 둔다. 반대로 바닐라에서 가져온 월드의 원래 조각은 `raw` 로 보존돼(151줄) 문제없다.
- 재현: `A06StructureReproTest.savedStructurePiecesUseVanillaPieceTypeIds` — 시드 1234 마을 주변 청크를 생성·저장, 조각 105개 모두 `furnace:piece`(기대: 바닐라 조각 id).
- 제안: 조각마다 바닐라 `StructurePiece.save` 형식(`id`·`GD`·`O`·`BB`·조각별 필드, 지그소는 `pool_element`·`ground_level_delta`·`junctions`)으로 쓴다. 조각 클래스 13종과 지그소에 `save` 를 하나씩 붙이는 일이라 범위가 넓다. A06-06(references 영속)의 선행 작업이다. A05(저장) 수정과 같은 파일을 만지므로 A05 수정이 끝난 뒤. **위험**: 형식을 틀리게 쓰면 바닐라가 조각을 버리는 것은 지금과 같아 나빠지지 않는다. **모델: Opus high**, 크기: 큼. 옮길 계획이 없으면 우선순위를 낮출 수 있어 user-todo 결정 1.

### A06-02 [높음] 전리품 `exploration_map` 이 늘 빈 지도를 준다

- 종류: 바닐라 차이
- 바닐라: `ExplorationMapFunction.run`(`esx`)은 `ServerLevel.findNearestMapStructure(destination, origin, search_radius, skip_existing_chunks)` 로 찾은 위치에 `MapItem.create` + `renderBiomePreviewMap` + 장식을 붙인 지도를 돌려준다. 난파선 지도 상자(`shipwreck_map`: 묻힌 보물), 바다 폐허 큰·작은 상자(묻힌 보물 지도)가 이것을 쓴다.
- Furnace: `LootFunction.java:278~290` 은 `c.world().findNearestMapStructure(...)` 와 `createExplorationMap(...)` 를 부르는데, `ServerCore.lootWorldFor`(`ServerCore.java:163`)가 만드는 `LootWorld` 가 이 둘을 구현하지 않아 기본값 `null`(`LootWorld.java:30·33`)이다. 그래서 늘 원래의 빈 지도(`minecraft:map`)를 그대로 돌려준다. 지도 제작자 거래 쪽(`explorerMapBackend`, `ServerCore.java:330`)은 구현돼 있어 둘이 따로 논다.
- 관측: 난파선·바다 폐허에서 얻은 보물 지도가 빈 지도다. 묻힌 보물(바다의 심장)을 지도로 찾을 수 없다.
- 재현: `A06MapReproTest.lootExplorationMapFindsBuriedTreasure` — 시드 1234, 원점 근처에서 묻힌 보물(-359,-151)이 있는데 전리품 함수 결과가 `minecraft:filled_map` 이 아니다.
- 제안: `lootWorldFor` 의 `LootWorld` 에 두 메서드를 `explorerMapBackend()` 로 이어 준다(구조물 생성 꺼짐 검사·`generate_features` 검사 포함, 바닐라 `ServerLevel.findNearestMapStructure` 와 같이). **모델: Sonnet medium**, 크기: 작음.

### A06-03 [높음] 생성 단계에 유체 틱·후처리 통로가 없다(샘·용암 폭포·대수층 경계·구조물 속 물)

- 종류: 바닐라 차이
- 바닐라 다섯 곳(원인은 하나 — 생성 단계에서 유체를 깨우는 통로):
  1. `SpringFeature.place`(`edp`, 67줄)는 샘 블록을 놓은 뒤 `level.scheduleTick(pos, fluid, 0)` 을 건다. 이 틱은 생성 단계의 `ProtoChunk` 예약 틱으로 저장돼 청크가 틱 범위에 들어오면 바로 흐른다.
  2. `LevelChunk.postProcessGeneration`(`dvi.H()`, 457~482줄)은 후처리 표시가 된 모든 칸에 대해 **유체 상태가 비어 있지 않으면 `fluidState.tick(level, pos)`** 를 부른다(대수층·카버 가장자리의 물·용암). 그 뒤 액체 블록이 아닌 것만 `updateFromNeighbourShapes`(플래그 20).
  3. 네더 요새 `CastleStalkRoom`(`elx` 안쪽 클래스, 466줄)은 가운데 용암을 놓고 `scheduleTick(pos, Fluids.LAVA, 0)`.
  4. 레거시 조각의 `StructurePiece.placeBlock`(`ejv` 154~157줄)은 놓은 블록에 유체가 있으면(물·용암·물에 잠긴 블록) **모두** `scheduleTick(pos, fluid, 0)` 을 건다. 요새·해저 신전·난파선·폐광 등 모든 고정 조각 구조물이 해당한다.
  5. 노이즈 채우기(`NoiseBasedChunkGenerator.doFill`, `dzb` 303~305줄)와 카버는 `aquifer.shouldScheduleFluidUpdate()` 이고 유체가 있으면 `markPosForPostprocessing` 을 한다. 이 표시가 2번의 대상이다.
- Furnace:
  1. `SpringFeature.java:46` 은 `trySetBlock` 만 한다.
  2. `ChunkPipeline.java:263~280` `applyPostProcessing` 은 물·용암 칸을 건너뛰며 주석이 "유체 틱은 서버 틱에서"라고 하지만, 그 틱을 거는 곳이 없다(`Region` 설치 경로·`ChunkColumn` 의 `fluid_ticks` 모두 비어 있음).
  3. `NetherFortressStructure.java:639` 은 용암 예약 틱을 일부러 뺐다(주석이 "생성 블록에는 영향이 없다"고 하지만, 영향은 틱 범위에 들어온 뒤에 생긴다).
  4. `StructurePiece.java:216~227` `placeBlock` 에 유체 예약 틱이 없다.
  5. `NoiseBasedAquifer.java:120` `shouldScheduleFluidUpdate()` 를 부르는 곳이 없고, 노이즈 채우기·카버(`world/gen/carver`)가 후처리 표시를 하지 않는다. 그래서 2번을 고쳐도 대수층 경계 칸은 표시조차 없어 반쪽이다.
  - `Region.java:478` 은 디스크에 저장돼 있던 `fluid_ticks` 만 되살린다. 새 청크에는 그 키가 없다.
- 관측: 동굴 벽·산비탈의 **샘이 흐르지 않고 한 칸 원천 블록으로 멈춰 있다**(물·용암 폭포가 없다). 대수층·협곡 가장자리에서 흘러야 할 물이 멈춘 채 남고, 네더 요새 계단실 용암이 흐르지 않는다. 구조물 안(해저 신전 주변·난파선·요새 수로)의 물도 마찬가지다. 블록이 업데이트되면(옆을 캐면) 그때 흐르기 시작해 "건드리면 흐르는 샘"이 된다. 지형 모양이 눈에 띄게 다르다.
- 재현: `A06SpringTickReproTest.freshlyGeneratedSpringsCarryFluidTicks` — 시드 1234 마을 주변 3×3 청크를 `generateWithFeatures`(실제 `AnvilWorldSource` 경로)로 생성. 광석 1476개가 놓였고(장식이 돈 것 확인) 샘 모양 원천 2개(물 (-379,97,765)·(-366,55,739))가 있는데 `fluid_ticks` 0개.
- 제안: ① `FeatureWorldView` 에 `scheduleFluidTick(x,y,z,fluid,delay)` 를 두고 청크 `fluid_ticks` 로 쌓아 설치 때 `Region` 예약 틱으로 옮긴다(저장·로드는 바닐라 `fluid_ticks` 형식 — 이미 읽는 코드가 있는지 A05 와 같이 본다). ② `applyPostProcessing` 이 유체 칸에서 바닐라처럼 유체 `tick` 을 한 번 돌린다(또는 0틱 예약 — 바닐라는 즉시 실행이라 같은 틱 순서가 되게). 이 단계는 청크가 레벨에 붙는 순간(FULL)이라 소유 region 스레드에서 해야 한다. ③ 성채 용암·`StructurePiece.placeBlock` 은 ①의 경로로. ④ 노이즈 채우기·카버가 `shouldScheduleFluidUpdate()` 일 때 후처리 표시를 한다(노이즈 출력은 같고 표시만 늘어 oracle 블록 대조에는 영향 없음). **위험**: 유체 틱이 생기면 생성 직후 청크 갱신량이 늘어 A17(성능)과 겹친다. **모델: Opus high**(파이프라인·region 경계), 크기: 중간.

### A06-04 [높음] 평지 월드에 구조물·지형지물·호수가 없다

- 종류: 바닐라 차이
- 바닐라: `FlatLevelSource` 는 `structure_overrides`(기본 프리셋: 요새·마을, `classic_flat`: 마을)의 구조물을 만들고, `features: true` 인 프리셋은 바이옴 지형지물을, `lakes: true` 는 호수를 둔다. **빈 공간(the_void) 프리셋**은 `features: true` 라 `void_start_platform`(청크 (0,0) 돌 원판)이 생긴다.
- Furnace: `SuperflatWorldSource.java:1~2` 주석대로 층 쌓기만 한다. `settingsNbt` 는 `structure_overrides`·`features`·`lakes` 를 level.dat 에 쓰지만(73~99줄) 생성에서 읽지 않는다. `VoidStartPlatformFeature` 는 있지만 평지 경로가 부르지 않는다. `/locate`(`LocateCommand.java:17~18`)·엔더의 눈은 `NoiseWorldSource` 가 아니면 "찾을 수 없음"이고, 탐험 지도 백엔드(`ServerCore.java:333·355`)도 `NoiseWorldSource` 일 때만 붙어 평지에서는 지도 제작자 탐험 지도 거래가 없다. 스폰 높이는 `FurnaceMain.java:304~307` 이 `surfaceY()` 로 잡는다.
- 관측: 기본 평지(`level-type=flat`)에 마을이 없고 엔더의 눈이 날지 않는다. 빈 공간 프리셋에서는 발판이 없어 스폰하자마자 공허로 떨어진다(코드 근거, 실제 접속 확인은 안 함). 평지에 `/locate structure #village` 가 실패한다.
- 재현: `A06StructureReproTest.defaultSuperflatGeneratesVillages` — 바닐라 기본 평지 배치로 마을 후보 청크 `ChunkPos[3,5]`(시드 1234)를 골라 생성, 구조물 시작점이 없다.
- 제안: 평지 생성기에 `structure_overrides` 의 구조물 세트로 `StructureEngine` 을 붙이고(바이옴 소스 = 고정 바이옴, 높이 = 층 높이), `features`·`lakes` 가 켜졌을 때 바이옴 지형지물 단계를 돌린다. 지형 접근(`TerrainAccess`)을 평지 층 높이로 주는 어댑터가 필요하다. 구조물(`structure_overrides`)과 지형지물·호수(빈 공간 발판 포함, `VoidStartPlatformFeature` 는 이미 이식됨)는 크기·위험이 달라 **PR 두 개로 나눈다**(발판·지형지물 먼저가 작다). **모델: Opus high**, 크기: 중간.

### A06-05 [높음] 데이터 팩의 월드젠(바이옴·구조물·노이즈 설정·차원)을 조용히 무시한다

- 종류: 바닐라 차이
- 바닐라: 월드 데이터 팩의 `data/<ns>/worldgen/**`(바이옴·configured/placed feature·구조물·구조물 세트·템플릿 풀·노이즈 설정·밀도 함수)와 `dimension/*.json`·`dimension_type` 이 레지스트리에 합쳐져 생성에 쓰인다(Terralith·Tectonic·구조물 추가 팩 등).
- Furnace: `WorldgenData.java:27·187` 이 클래스패스 `worldgen/1.21.1` 만 읽는다. `StructureRegistry.java:113·149·175~178`(구조물·구조물 세트·태그)도 같다. `DynamicRegistries.java:33~34` 의 데이터 주도 레지스트리 목록에 월드젠·차원 계열이 없다. 데이터 팩이 월드젠에 닿는 곳은 구조물 템플릿 `.nbt`(`TemplateManager.java:133~141`)뿐이다. 데이터 팩에 월드젠이 있어도 경고가 없다. 구조물·바이옴 **태그**도 클래스패스만 보므로 팩이 `#on_treasure_maps` 같은 태그를 바꿔도 반영되지 않는다.
- 관측: 월드젠 데이터 팩을 넣어도 지형·구조물이 그대로다. 사용자는 실패를 알 수 없다. 반대로 구성 단계에서 클라이언트에 보내는 바이옴 레지스트리(W2)와 생성에 쓰는 바이옴이 어긋날 수 있다(바이옴 추가 팩).
- 재현: 가정(시험 없음 — 코드 경로로 확인: `WorldgenData` 생성자는 데이터 팩 인자를 받지 않는다).
- 제안: 1단계(작음) — 월드젠 경로가 든 데이터 팩을 찾으면 "아직 지원하지 않음" 경고를 기동 로그에 찍는다. 2단계(큼) — `WorldgenData` 를 레지스트리 겹침(바닐라 → 데이터 팩 순서) 구조로 바꾸고, 바이옴 id 표·구조물 세트·노이즈 설정을 그 레지스트리에서 만든다. 결정 R4(데이터 팩 지원)와 관련. **모델: Opus high**, 크기: 큼. user-todo 결정 1 과 묶어 우선순위를 정한다.

### A06-06 [중간] 탐험 지도 `skipKnownStructures` 가 없어 같은 구조물을 반복해 가리킨다

- 종류: 바닐라 차이
- 바닐라: `ChunkGenerator.findNearestMapStructure(..., skipKnownStructures=true)`(`duz`)는 후보 시작점이 이미 참조된 것(`StructureStart.canBeReferenced()` 가 거짓 — references 가 상한에 닿음)이면 건너뛰고, 고른 시작점에 `addReference` 를 해 저장한다. 그래서 지도 제작자에게 탐험 지도를 다시 사거나(거래 갱신 후) 같은 종류 보물 지도를 또 얻으면 **다음으로 가까운** 구조물을 가리킨다.
- Furnace: `StructureLocator.java:30` `findNearest` 는 `skip` 인자가 없고 **예측 전용**이라 저장된 시작점을 전혀 보지 않는다. `addReference`·`canBeReferenced` 대응이 코드 전체에 없고, 저장은 `references` 를 늘 0 으로 쓴다(`ChunkStructures.java:159`). `ExplorerMaps.java:15` TODO 의 "시작점 저장이 없어 못 한다"는 낡았다(`ChunkStructures` 는 이미 있다). 바닐라에서 가져온 월드의 저장된 references 도 무시된다. 전리품 `skip_existing_chunks`(A06-02)도 이 구현에 기댄다.
- 관측: 해저 신전·삼림 대저택 탐험 지도를 여러 장 사도 모두 같은 곳을 가리킨다. 거래 진행(새 지도)이 의미가 없다.
- 재현: `A06MapReproTest.secondExplorerMapSkipsKnownStructure` — 첫 번째·두 번째 해저 신전 지도가 둘 다 (208,208).
- 제안: A06-01(저장 형식) 뒤에, 시작점 references 를 저장하고(`ChunkStructures`), `findNearest` 에 `skip` 인자를 넣어 바닐라 순서대로 검사·증가한다. 참조 증가는 그 시작점 청크를 가진 region 이 소유자이므로 다른 region 의 청크면 그쪽 스레드로 넘겨야 한다(또는 전역 표). **모델: Opus high**(스레드 소유권), 크기: 중간.

### A06-07 [중간] `generate-structures=false` 인데 `/locate structure` 가 위치를 알려 준다

- 종류: 바닐라 차이
- 바닐라: 구조물 시작 단계(`ChunkStatusTasks.generateStructureStarts`, `dwb` 16줄)는 `worldGenOptions.generateStructures()` 가 거짓이면 `createStructures` 를 건너뛴다. `/locate` 의 후보 검사(`duz` 184~199줄)는 `StructureCheck.checkStart` 가 `START_PRESENT` 를 줄 때(저장된 시작점이 있을 때)만 바로 답하고, 예측만 된 청크(`CHUNK_LOAD_NEEDED`)는 **그 청크를 `STRUCTURE_STARTS` 단계까지 실제로 만들어 시작점을 읽는다**. 구조물이 꺼졌으면 그 단계가 시작점을 만들지 않으므로 어떤 후보도 통과하지 못하고 `commands.locate.structure.not_found` 가 된다.
- Furnace: `StructureEngine.startOf`(`StructureEngine.java:299~312`)가 스위치(`structuresEnabled`)를 일부러 보지 않고, 주석(305줄)이 "바닐라 /locate 는 generateStructures 와 무관하다"고 적는다. `LocateCommand` 도 검사하지 않는다.
- 관측: 구조물 생성을 끈 서버에서 `/locate structure #village` 가 있지도 않은 마을 좌표를 준다. (단서: 예전에 구조물이 켜진 채 생성된 청크의 시작점은 바닐라도 찾는다.)
- 재현: `A06StructureReproTest.locateFindsNothingWhenStructuresDisabled` — 구조물 꺼짐에서 `village_plains`(272,944)를 돌려준다.
- 제안: `startOf`(또는 `StructureLocator.findNearest` 입구)가 `structuresEnabled` 가 꺼졌으면 null 을 돌려주게 하고 305줄 주석을 고친다. 엔더의 눈·탐험 지도는 이미 부르는 쪽이 검사하지만 한곳으로 모으면 A06-02 와도 맞는다. 플러그인 API `StructuresImpl.java:385` 도 같은 경로를 쓰므로 같이 바뀐다. **모델: Sonnet low**, 크기: 작음.

### A06-08 [참고] noise_settings `caves`·`floating_islands` 가 없다

- 바닐라 jar 에는 `worldgen/noise_settings/caves.json`·`floating_islands.json` 이 있지만 Furnace 리소스에는 없다. 바닐라 월드 프리셋은 이 둘을 쓰지 않으므로 기본 경로에는 영향이 없다. 데이터 팩·`generator-settings` 로 이 이름을 쓰면 실패한다(A06-05 와 함께).

### A06-09 [참고] 지그소 패딩 높이 기준

- Furnace 지그소의 경계(패딩) 계산 한 곳이 차원 높이 대신 `genDepth` 를 기준으로 쓴다. 1.21.1 바닐라 구조물(보루 잔해 포함)의 설정값에서는 결과가 같아 관측 차이가 없음을 확인했다. 데이터 팩 구조물(A06-05)을 열 때 다시 본다.

### A06-10 [참고] 조사 방법 한계

- 블록 출력은 oracle fixture(기존 시험)에 맡기고 이번에는 코드 대조만 했다. 실서버 대조를 열면 월드 끝·큰 시드·네더/엔드 구조물 블록까지 한 번에 확인할 수 있다(user-todo 확인 1).

---

## 검증 결과

독립 검증(읽기 전용, Furnace 코드와 바닐라 디컴파일을 처음부터 다시 읽음)의 판정과 반영 내용:

| 항목 | 판정 | 반영 |
|---|---|---|
| A06-01 | 부분적으로 맞음 | 바닐라는 시작점을 "버리는" 것이 아니라 빈 시작점으로 읽고 재저장 때 `INVALID` 로 굳힌다. `/locate` 는 청크 로드 전까지 찾는다. `references` 0 은 결함이 아님(A06-06 으로). 심각도 높음 유지(치명 아님) |
| A06-02 | 맞음 | — |
| A06-03 | 맞음, 범위가 더 넓다 | `StructurePiece.placeBlock` 유체 틱(④)과 노이즈 채우기·카버의 대수층 후처리 표시(⑤)를 추가. 둘 다 디컴파일(`ejv` 154~157줄, `dzb` 303~305줄)과 Furnace grep 으로 다시 확인했다 |
| A06-04 | 맞음 | 탐험 지도 백엔드도 평지에 없음, PR 두 개로 나누기 |
| A06-05 | 맞음 | 태그·`DynamicRegistries` 근거 추가 |
| A06-06 | 맞음 | `StructureLocator` 가 저장된 시작점을 안 봄, TODO 낡음, A06-01 선행 |
| A06-07 | 맞음 | 예전에 구조물이 켜진 채 생성된 청크는 바닐라도 찾는다(단서) |

## 충돌·순서 주의 (수정 스레드용)

- `ChunkStructures`(A06-01·06)는 A05(청크 저장) 수정이 만지는 저장 경로와 겹친다. A05 수정 병합 뒤에 시작한다.
- A06-03 ①②는 `ChunkPipeline`·`Region` 설치 경로를 만진다. A03(스케줄러) 수정·A17(성능) 점검과 순서를 맞춘다.
- A06-02 와 A06-06 은 `ServerCore` 의 지도 백엔드 하나로 모으는 것이 자연스럽다(A06-02 먼저, 작음). A06-06 은 A06-01 의 저장 형식(references 영속)이 먼저다.
- A06-04 는 `ServerCore` 지도 백엔드가 평지에도 붙도록 A06-02 와 같은 자리를 만진다.

## 수정 묶음 제안 (코디네이터용)

| 묶음 | 항목 | 모델·노력 | 크기 |
|---|---|---|---|
| G1 지도 | A06-02 (A06-06 은 G5 뒤) | Sonnet medium | 작음 |
| G2 생성 유체 틱 | A06-03 | Opus high | 중간 |
| G3 평지 | A06-04 | Opus high | 중간 |
| G4 `/locate` 스위치 | A06-07 | Sonnet low | 작음 |
| G5 구조물 저장 | A06-01 → A06-06 references 영속 | Opus high | 큼, 결정 1 뒤 |
| G6 데이터 팩 월드젠 | A06-05 1단계(경고) / 2단계 | Sonnet low / Opus high | 작음 / 큼, 결정 1 뒤 |

## 바닐라 대응표 (1.21.1 난독화)

| 난독화 | 바닐라 |
|---|---|
| `duz` | ChunkGenerator(`findNearestMapStructure` 보조 160·184줄) |
| `dwb` | ChunkStatusTasks(`generateStructureStarts`, 16줄 `generateStructures` 검사) |
| `dvi` | LevelChunk(`H()` = postProcessGeneration) |
| `ejs` / `ejt` | StructureCheck / StructureCheckResult |
| `eko` / `ekm` / `ekp` | StructurePlacement / RandomSpread / ConcentricRings |
| `ekg` / `eki` | PiecesContainer / StructurePieceType |
| `ejz` / `ejv` | StructureStart / StructurePiece |
| `dzb` | NoiseBasedChunkGenerator(`doFill`) |
| `ani` | LocateCommand |
| `ekv` / `ayz` | JigsawPlacement / SequencedPriorityIterator |
| `esx` | ExplorationMapFunction |
| `edp` / `ece` | SpringFeature / Feature 레지스트리(`spring_feature`) |
| `epf` | Fluids(`e` = LAVA) |
| `elx` `emj` `elt` `eml` `elm` `emb` `elq` `elv` `elo` `emn` `elk` `emh` `elz` `emd` `emf` | 네더 요새·요새·정글 사원·마녀 오두막·사막 피라미드·해저 신전·이글루·폐광·엔드 도시·대저택·묻힌 보물·난파선·네더 화석·바다 폐허·폐허 포털 |

## 재현 시험 쓰는 법

1. `A06-repro/*.java.txt` 를 `furnace-server/src/test/java/org/domi/furnace/server/audit/` 에 `.java` 로 복사한다.
2. `ORG_GRADLE_PROJECT_skipOracle=true ./gradlew --offline :furnace-server:test --tests 'org.domi.furnace.server.audit.A06*'`
3. 기준 커밋 결과: `A06NoopFeatureScanTest` 통과(0/196), 나머지 6개 실패. 고친 뒤에는 모두 통과해야 한다. `A06MapReproTest` 는 `ServerCore` 비공개 메서드(`lootWorldFor`·`explorerMapBackend`)를 리플렉션으로 부르므로, 이름이 바뀌면 시험을 맞춘다.
