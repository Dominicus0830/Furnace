# NMS 어댑터 N2b-6 — FAWE 실제 jar 통합과 Paper 대조 (2026-11-16)

설계: `compat-nms-n2b-design-2026-11-14.md` 6장 N2b-6 줄과 5장 V6. 앞 단계: N2b-1~4(`compat-nms-n2b4-2026-11-15.md`, PR #396). 결정: 도미니코 11-14 D1~D4 모두 A.
측정: 도미니코 노트북(i5-1235U, Windows, Temurin 21), 실제 `FastAsyncWorldEdit-Paper-2.16.2.jar`(도미니코가 올린 파일, GitHub 릴리스와 같은 크기)와
`paper-1.21.1-133.jar`(PaperMC 배포, sha256 39bd8c00…). 함정·재현 메모는 `context-notes/2026-11-16-nms-n2b6.md`.

## 1. 결론

| 항목 | 결과 |
|---|---|
| FAWE 1.21.1 어댑터(`PaperweightFaweAdapter`) | **올라온다** — 전에는 클래스 초기화(`DataComponentPatch`)에서 죽어 어댑터 없이 돌았다(블록 표 AIOOBE, 편집 명령 전부 실패) |
| 대표 편집 7가지(아래 2장) | **예외 없이 끝나고 FAWE 가 낸 바뀐 칸 수가 Paper 와 모두 같다** |
| 저장 파일 대조(16×16 청크) | **블록 0칸, 바이옴 0칸, 하이트맵 4종 0칸 차이** |
| 빛 | 다르다 — Paper 는 FAWE 부탁으로 Starlight 가 다시 계산, Furnace 는 결정 D2 대로 다시 계산하지 않는다(편집한 41청크에서 하늘빛 126,006칸·블록빛 3,151칸). 다시 계산은 N2b-5(T-197) |
| 블록 엔티티 | FAWE 편집과 무관한 차이뿐 — `keepPacked` 키(바닐라 서버 jar 에 있는 키인데 Furnace 가 저장한 타일에는 없음, 2026-11-16 T-198 실측 노트)와 키 순서 |
| 남은 구멍 | 블록 엔티티가 든 섹션을 FAWE 가 바꾸면 그 청크 편집이 `UnsupportedNmsCall` 로 실패한다(블록 엔티티 지우기·넣기 길 없음, T-198). 이번 시나리오에는 그런 섹션이 없다 |

## 2. 어떻게 쟀나

1. **기준 월드**: Paper 로 시드 `20261115` 월드를 만들고 `forceload` 로 (−128..127)² 블록 = 16×16 청크를 끝까지 생성·저장. 두 서버 모두 이 폴더의 사본으로 시작한다.
2. **편집**(콘솔, `gamerule randomTickSpeed 0`·`doFireTick false`, 같은 순서):
   E1 `//set stone` 10³(섹션 경계 y=64 지남) · E2 `//replace grass_block,dirt,sand,short_grass,tall_grass,podzol,coarse_dirt air` 48×81×48 · E3 `//set oak_planks` **64³** ·
   E4 `//copy` + `//stack 2 north` + `//move 12 up` · E5 공중 `//set water` 10×6×10 · E6 `//undo`(E5 되돌림) · E7 다시 `//set water` 6×3×6. 끝에 `save-all flush`·`stop`.
3. **비교**: `tools/compat/anvil-diff.py`(새로 만듦) — region 파일을 청크마다 읽어 블록 상태(팔레트 순서와 무관하게 풀어서)·바이옴·하이트맵 4종·빛 니블·블록 엔티티·최상위 태그를 칸 단위로 견준다.
4. **대조군**: 플러그인 없이 바닐라 `/fill` 만 돌린 Paper·Furnace — FAWE 와 상관없는 엔진 차이(빛 저장 꼴·블록 엔티티 직렬화)를 가려낸다.

FAWE 출력(두 서버 같음): E1 `Operation completed (1000)`, E2 `8353 blocks have been replaced`, E3 `(262144)`, E4 `1000 blocks affected`·`2000 blocks changed`·`1000 blocks moved`, E5 `(600)`, E6 `Undid 1 available edits`, E7 `(108)`.

## 3. 결과 자세히

- **블록·바이옴·하이트맵**: 256청크 모두 0칸. 하이트맵은 FAWE 가 스스로 셈해 `setRawData` 로 넣는 값이고(N2b-4 일지·고정 하이트맵), 그 셈이 블록 술어(아래 4장)에 기대므로 이 결과가 술어가 Paper 와 같다는 실측 증거다.
- **빛**: 편집한 41청크 하늘빛 126,006칸·블록빛 3,151칸 다름(Furnace = 편집 전 빛). 편집하지 않은 122청크의 차이(하늘빛 섹션 유무 2,638·블록빛 2,668칸·하늘빛 110칸)는 대조군에도 있는 Furnace 빛 엔진 대 Starlight 의 저장 꼴·값 차이로, FAWE 와 상관없다(T-202 측정).
- **최상위 태그**: Furnace 가 다시 저장한 청크마다 `isLightOn` 1(Paper 0 — Starlight 는 0 으로 쓰고 자기 키를 둔다)과 `furnace:light_version`. Furnace 는 Paper 월드의 청크를 올릴 때 빛을 다시 계산해 저장 대상으로 삼는다(바닐라도 `isLightOn` 0 이면 다시 계산).
- **블록 엔티티**: 3청크(던전 상자·스포너) — `keepPacked:0` 이 Furnace 에 없음(바닐라·Paper 는 저장하는 타일에 넣는다 — T-198 실측 노트). 내용은 같다.
- **콘솔**: Paper 콘솔은 앞 `/` 를 떼지 않아 `/pos1`, Furnace 콘솔은 바닐라처럼 하나 떼서 `//pos1` 로 쳐야 한다(T-201).

## 4. 고친 것 — FAWE 어댑터가 올라오고 편집하는 데 필요했던 것

정답은 **Paper 1.21.1 jar 를 그대로 부트스트랩해 잰 값**이다(`SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();` 뒤 메서드를 불러 표로 뽑음 — 시험 자원 `n2b/material-oracle-1.21.1.tsv.gz`·`n2b/block-state-properties-1.21.1.tsv`, `NmsFaweSurfaceTest` 의 단계표·난수 상수). 이름·서술자는 Paper jar 를 `javap -p` 로 보고 맞췄다(커밋한 것은 이름뿐).

| 묶음 | 내용 |
|---|---|
| 판·코덱 | `SharedConstants`·`WorldVersion`·`DetectedVersion`·`DataVersion`(3955). `DataComponentPatch`(`CODEC` 은 진짜 DFU Codec 이지만 인코딩·디코딩은 오류 결과 — T-200), `References`·`DataFixers`(판을 올리지 않는 고치기 — T-199), `NbtOps`(상수만 — T-191). DataFixerUpper 컴파일 대역(`src/dfu`, authlib 과 같은 방식) |
| 난수 | `RandomSource`·`LegacyRandomSource`·`SingleThreadedRandomSource`·`BitRandomSource`·`MarsagliaPolarGaussian`·`RandomSupport` — Paper 와 같은 수열(시험) |
| 블록 술어(보조 작업자) | `getLightBlock`·`getShape`/`isShapeFullBlock`·`canOcclude`·`isSolidRender`·`isSignalSource`·`destroySpeed`·`getExplosionResistance`·`getFriction`·`getLightEmission`·`getPistonPushReaction`·`blocksMotion`·`canBeReplaced`·`defaultMapColor`(`MapColor` 62종)·`EmptyBlockGetter`·`VoxelShape`, 다리 `BlockStateInternals`. **상태 26,684개 모두 Paper 와 같다**(`blockMaterialPredicatesMatchPaperForEveryState`). Paper 는 부술 수 없는 블록 10종(기반암 등)의 피스톤 반응을 늘 BLOCK 으로 주므로 그대로 따랐다(`Block#isDestroyable`) |
| 블록 엔티티(보조 작업자) | `EntityBlock`(바닐라와 같은 블록 집합), `BlockEntity`·`BeaconBlockEntity`·`BlockEntityType`, 새 블록 엔티티의 `saveWithId` = 서버가 새로 만든 블록 엔티티 저장값(FAWE 의 블록별 기본 타일), 살아 있는 블록 엔티티 보기(T-198 에서 `Level#getBlockEntity`·청크 `getBlockEntity` 로 바뀜), `LevelChunk#getBlockEntities`·`getBlockEntity`(읽기) |
| 레지스트리(보조 작업자) | `RegistryAccess#registryOrThrow`·`lookupOrThrow`, `WritableRegistry`, 바이옴 레지스트리(청크 shim 과 같은 번호·객체, 서버 태그), configured_feature 196·placed_feature 233·structure 34 키, `TreeFeature`·`CoralTreeFeature` 식별, 지형지물 키 모음 7클래스(146 필드), `CraftNamespacedKey` |
| 블록 속성 | `BlockStateProperties` 114 상수 — 필드 순서·종류·이름·값 순서까지 Paper 와 같고, 블록이 쓰는 속성과 같은 객체 |
| 청크·서버 | `ChunkHolder`·`ChunkMap`(`getVisibleChunkIfPresent`·`getPlayers`)·`ChunkResult`·`TicketType`·`Unit`, `ServerChunkCache` 의 `chunkMap`·`mainThreadProcessor`·`getChunkFutureMainThread`·`getLightEngine`·티켓 4종(→ Furnace 청크 티켓 `NmsTickets`, 만료 틱 포함), `ChunkPyramid`·`ChunkStep`·`ChunkDependencies`(Paper 와 같은 누적 반경), `ThreadedLevelLightEngine#starlight$serverRelightChunks`(이 판에서는 다시 계산 없이 콜백만 — D2. T-197 에서 실제 다시 계산으로 바뀜, `compat-nms-n2b5-relight-2026-11-16.md`), `LevelLightEngine`·`DataLayer`·`LightLayer`, `MinecraftServer#execute`·`executor`·`currentTick`, `CraftServer.worlds`, `LevelChunk` 의 Paper 필드(`level`·`locX`·`locZ`·`loaded`·`mustNotSave`)·`setLightCorrect`(저장 표시 — R5c), `StateHolder.propertiesCodec`, Paper(Moonrise) `Level#moonrise$getEntityLookup`·`EntityLookup`·`ChunkEntitySlices#getAllEntities`·`ChunkHolderManager.UNLOAD_COOLDOWN`, `MCUtil.MAIN_EXECUTOR` |
| Paper 서술자 | **`LevelChunkSection(PalettedContainer, PalettedContainer)`** — Paper 는 바이옴 인자를 바꿨다. 이게 없어 FAWE 의 섹션 교체(N2b-4 의 본체)가 실제 jar 에서는 막혀 있었다(N2b-4 시험은 Furnace shim 으로 컴파일한 FAWE 꼴이라 못 잡았다). `PalettedContainer.registry`·`get(int)` 공개 |
| 패킷 | `ClientboundLevelChunkPacketData.TWO_MEGABYTES`, 청크 보기로 만드는 `ClientboundLevelChunkWithLightPacket(LevelChunk, …)` — 보낼 때 서버가 그 청크를 보고 있는 플레이어에게 통째로 다시 보낸다(`World#refreshChunk`, 한 패킷 객체에 한 번) |
| 상속 껍데기 | **정책 바꿈**: NMS 클래스를 상속한 플러그인 클래스를 정의 거부하던 것을 "껍데기로 정의, 만들 때 같은 문구로 거부"로(인스턴스 메서드는 지움, 정적 멤버는 그대로, 인터페이스는 여전히 거부). FAWE 어댑터 생성자가 가짜 플레이어(`ServerPlayer` 상속) 생성자 참조를 필드에 만들기 때문 — 가리키기만 하는 코드는 이제 돈다 |
| 플러그인 켜는 스레드 | **서버 바꿈**: onLoad·onEnable 을 전역 스레드에서 부른다(Bukkit 동기 작업이 도는 곳). FAWE 는 켤 때 `Thread.currentThread()` 를 주 스레드로 적어 두고 동기 작업마다 "Not main thread" 로 검사한다 — Paper 는 켜는 스레드 = 주 스레드. 개발 모드 다시 읽기는 이미 전역 스레드에서 켰다 |
| 서버 블록 모양 데이터 | Paper 정답표가 찾은 바닐라 불일치: 키 큰 풀·큰 고사리·해바라기·라일락·장미 덤불·모란·벌레잡이 식물(DoublePlantBlock 14상태)의 외곽·가림 모양이 데이터에 x·z 로 0.25 밀린 꽉 찬 블록으로 들어 있었다(추출기가 이 블록이 쓰지도 않는 위치 어긋남을 뺐다). 바닐라는 어긋남 없는 꽉 찬 블록 — `blockshapes_1.21.1.txt.gz` 의 모양 600 을 고쳤다(서버의 이 블록 외곽 모양도 함께 바닐라와 같아진다) |
| 라이브러리 | Paper 가 서버 jar 에 싣는 JDBC 드라이버 `sqlite-jdbc 3.46.0.0`·`mysql-connector-j 8.4.0`(Paper `libraries.list` 의 sha256 그대로) — FAWE 기록 DB |

## 5. 남은 것·가정

1. **빛 다시 계산 없음(D2)** — Paper 는 FAWE 편집 뒤 빛이 맞고 Furnace 는 옛 빛. `starlight$serverRelightChunks` 를 Furnace 빛 엔진으로(N2b-5, T-197).
2. **블록 엔티티 쓰기 없음(T-198 에서 함 — `compat-nms-t198-block-entities-2026-11-16.md`)** — `LevelChunk#removeBlockEntity`·`Level#getBlockEntity`(만들기)·`BlockEntity#loadWithComponents` 가 없어, 블록 엔티티가 든 섹션을 바꾸거나 상자를 붙여 넣으면 그 청크 편집이 실패한다(T-198, 거울 일지에 블록 엔티티 넣기).
3. **옛 판 데이터 고치기 없음** — 켤 때 FAWE `LegacyMapper` 가 `minecraft:grass`·`sign` 등 30줄 "Unknown block" 오류를 낸다(Paper 는 DFU 로 고친다). 옛 스케매틱도 안 올라간다(T-199).
4. **아이템 구성 요소 코덱 없음** — `//wand` 아이템 주기·브러시 등 아이템 변환이 실패한다(T-200, NbtOps 는 T-191).
5. 가정: `ChunkMap#getPlayers` 는 체비셰프 시야 거리 안의 플레이어(바닐라는 실제로 보낸 청크), 티켓 수준 32(블록 틱만)는 Furnace 에 해당 등급이 없어 로드만, `ChunkHolder` 의 틱 미래는 로드된 청크면 끝난 미래.
6. 이번에 안 잰 V6 항목: 편집 직후 같은 틱 Bukkit 읽기(다른 플러그인 표본), 물 섹션의 무작위 틱 진행(시나리오는 randomTickSpeed 0), 빠른 블록 놓기 표본·squaremap. FAWE 의 다른 기능(`//regen`·`//forest`·엔티티 복사·`//biome`)도 안 돌렸다 — 남은 없는 기호 목록은 시작 로그(T-203).
7. 콘솔 앞 `/` 처리(T-201), 편집 안 한 청크의 빛 값 차이(T-202).

## 6. 시험

- 새 `NmsFaweSurfaceTest`: 판 상수, 단계표 두 개(Paper 표), 난수 수열(Paper), `BlockStateProperties` 114개(Paper 표·블록 속성과 같은 객체), **블록 술어 26,684상태 × 17가지(Paper 표)**, `DataLayer`, FAWE 리플렉션 찾기 전부, `ChunkHolder`, 빛 다시 계산 콜백, `DataComponentPatch.CODEC`, `References`.
- 보조 작업자 시험: `NmsBlockMaterialTest`(5), `NmsEntityBlockTest`(3), `NmsLiveBlockEntityTest`(1), `NmsRegistryAccessTest`(9).
- 바꾼 시험: `NmsLinkerTest`·`NmsH7PluginTest`(상속 → 껍데기), `NmsLinkerTest` 의 없는 정적 필드 예를 `MinecraftServer.tickCount` 로(currentTick 은 이제 있다).
- 실측: 노트북 2장 시나리오(재현 스크립트는 보고서 `furnace/reviews/n2b6-fawe-paper-2026-11-16.md`).
