# NMS 어댑터 N2b-0 조사 — 캐시 전수·플러그인 필드 수준 사용·바닐라 정답기 (2026-11-14)

설계: `compat-nms-n2b-design-2026-11-14.md`(PR #351). 이 문서는 6장 단계표의 **N2b-0**(결정 D1~D4 와 무관한 조사) 결과다.
산출물: 필드 전수 시험 `ChunkFieldCensusTest`, 바닐라 jar 정답기 도구 `tools/vanilla-data/ExportSectionOracle.java` + 정답표
`furnace-server/src/test/resources/n2b/section-oracle-1.21.1.txt.gz` + 대조 시험 `VanillaSectionOracleTest`, 조사 스크립트 `tools/compat/nms-field-survey.py`.

## 1. 요약

- **Furnace 블록 파생 캐시는 `ChunkColumn`·`ChunkSection` 안에 갇혀 있다.** 그 밖에서 블록 내용에 기대는 캐시는 `World` 의 비동기 직렬화 본문(`modCount` 비교) 하나뿐이고, 이것도 `touch()` 의 `modCount++` 로 무효화된다. 따라서 들여오기(N2b-4)는 `importSection` 한 곳에서 `touch()` + 섹션 개수·마스크 재계산 + 고정 하이트맵 처리만 하면 된다. 앞으로 생길 필드는 `ChunkFieldCensusTest` 가 잡는다(2장).
- **플러그인 7종 중 필드·배열 수준으로 섹션을 바꾸는 것은 FAWE 뿐이다.** 나머지는 메서드 수준(WorldEdit·BentoBox·BlockChanger 의 `setBlockState`), 바이옴 컨테이너 제자리 쓰기(Denizen), 읽기 전용 복사(squaremap), 월드젠(Iris)이다. 다만 BlockChanger 의 `MethodHandles.arrayElementSetter(LevelChunkSection[])` 와 Denizen 의 바이옴 `getAndSetUnchecked` 는 설계의 쓰기 장벽이 못 보는 모양이라 **훑기(정식 경로)** 가 필요하다는 설계 판단을 뒷받침한다(3장).
- **바닐라 jar 정답기는 클라우드에서 6초 안에 돈다**(부트스트랩 4.4초, 상태 26,684개 깃발 1.5초, 섹션 300개·열 40개 0.6초). 하이트맵 정답기에 쓸 `ProtoChunk` 는 **섹션 배열을 다 채워 넘기면 바이옴 레지스트리 없이(null) 만들어진다** — 설계가 걱정한 부트스트랩 비용(데이터 팩 레지스트리)은 필요 없다(4장).
- **Furnace 표 26,684개 상태 전부가 바닐라 개수 깃발과 같다**(공기·블록 무작위 틱·유체 있음·용암). 섹션 비공기 수·무작위 틱 판정·하이트맵 넷도 같은 입력에서 바닐라와 같다 — **하나만 빼고**: 아래 버그.
- **발견한 버그(N2b 와 무관, 별도 수정 과제로 넘김)**: `ChunkColumn.updateSurface` 는 `surface[열]==0` 을 "프라임 전"으로 보고 갱신을 건너뛴다. 하이트맵을 만든 뒤 열 전체가 공기여도 0 이라, **빈 열(공허 월드·스카이블록·엔드 공허, 바닥까지 판 열)에 놓은 블록이 WORLD_SURFACE 에 영영 반영되지 않는다.** 바닐라 `Heightmap.update` 는 반영한다. 정답기 열 40개에서 44칸이 정확히 이 모양으로 다르다. 영향: 청크 패킷·저장 WORLD_SURFACE, 자연 스폰 높이 범위(`Region.surfaceHeight`), `SpawnLocation`, `/execute positioned over world_surface`. 시험은 이 모양만 세고(`knownEmptyColumn`) 다른 차이는 실패시킨다 — 수정되면 엄격 비교로 바꾼다.

## 2. Furnace 블록 파생 캐시 전수 목록(V3 의 바탕)

분류는 `ChunkFieldCensusTest` 의 표가 정본이다. 새 필드가 생기면 시험이 실패해 분류를 강제한다.

| 분류 | 들여오기 때 | 필드 |
|---|---|---|
| CONTENT | 거울에서 옮겨 씀 | `ChunkColumn.sections`, `ChunkSection.blocks`·`biomes` |
| FURNACE_CACHE | **반드시 버리거나 다시 셈** | `ChunkColumn`: `modCount`·`cachedSerializedBody`·`inflightSerializedBody`·`cachedMotionBlockingHeightmap`·`cachedWorldSurfaceHeightmap`·`cachedSnapshot`(모두 `touch()`), `sharedSections`(COW — `ownSection` 거쳐 갈아 끼움), `randomTickMask`(T-053 — 섹션 `attach`). `ChunkSection`: `nonAirCount`·`tickingCount`·`scratch`, `owner`·`ownerIndex`(attach/detach), `lightFrozen`(얼린 섹션은 갈아 끼움) |
| HEIGHTMAP | 고정 하이트맵 모드(설계 3.5) | `surface`(라이브), `oceanFloor`·`motionBlocking`(칸별 지연 UNSET), MBNL 은 필드 없이 매번 계산 |
| VANILLA_DERIVED | 바닐라처럼 안 고침(D2 추천 A) | `ChunkColumn.blockEntities`·`light`, `ChunkLight.sky`·`block`·`lowestSource` |
| UNRELATED | 무관 | 저장 표시 `dirty`, 월드젠 상태(`surfaceWg`·`wgOceanFloor`·`wgHeightmapsFrozen`·`generated*`·`postProcessMarks`·`freshlyGenerated`·`newlyCreated`), 보내기 대기(`bodyWaits`·`lightWaits`·`lightWaitTick`), 빛 저장 상태(`lightPrecomputed`·`lightStored`·`lightStaleSeq`), 엔티티·POI 원본·구조물·게임 이벤트 리스너·`extraNbt`·`inhabitedTime`, `ChunkLight` 사본 관리(`base`·`ownSky`·`ownBlock`)·`lightEnabled` |
| FIXED | 무관 | 상수·정적 표(`HAS_BE`·`leavesTable`·`motionBlockingTable`·`blocksMotionTable`·`hasFluidTable`·`randomTicking` 등)·모양(`minY`·`sectionCount`·`heightmapBits`) |

**세 클래스 밖**(코드 전수 검색 — 필드 이름에 cache 가 든 것과 `modCount()` 쓰는 곳):
- `World` 비동기 본문 직렬화(1316·1342행): 시작 때 `modCount` 를 쥐고 끝날 때 같을 때만 캐시에 넣는다 → 들여오기가 `touch()` 를 거치면 안전.
- `RegionLevel`·`ChunkLevel` 의 좌표 → `ChunkColumn` 작은 캐시: 컬럼 **객체** 를 가리킬 뿐 블록 내용을 담지 않는다. 들여오기는 컬럼을 바꾸지 않으므로 무관.
- 길찾기(`WalkNodeEvaluator`·`SwimNodeEvaluator`)의 칸 종류·충돌 캐시: 길 하나 찾는 동안만 산다(바닐라도 같음).
- `ChunkPipeline` pristine 사본: 월드젠 전용(로드된 청크와 무관).
- N2a `NmsChunks.Cached`: 다리(bridge) 객체 하나, 블록 내용 없음.
- `ChunkBodyRewriter`(플레이어별 가상 블록): 보낼 때 현재 블록을 읽는다 — 캐시 아님.

## 3. 플러그인 필드·배열 수준 사용 표

조사 대상과 판(소스는 저장소에 넣지 않음): FAWE 2.16.2(`adapter-1_21` + worldedit-core `ReflectionUtils`), WorldEdit 920a810(`adapter-1.21`), squaremap 8e3cfea, Denizen 3f523e9(`v1_21`), BentoBox 12109eb2(`nms/v1_21`), Iris(`NMSBinding`), BlockChanger(단일 파일 라이브러리 — "빠른 블록 놓기" 표본).

### 3.1 사람이 확인한 분류

| 플러그인 | 무엇을 하나 | 스레드 | N2b 에서 필요한 것 |
|---|---|---|---|
| **FAWE** | W2 통째: `new LevelChunkSection`(5인자 `PalettedContainer`), 섹션 배열 CAS(`arrayElementVarHandle(Object[])`), 바이옴 컨테이너 필드 대입, 개수 3개 0 대입(`clearCounts`), `Heightmap.setRawData`, 끝에 `setUnsaved`·`setLightCorrect`·청크 패킷(`MinecraftServer.execute`)·Starlight 빛 | FAWE 작업 스레드(섹션 잠금) | 설계 전부(거울·공개 스냅샷·들여오기·훑기·고정 하이트맵). 설계 1장에 상세 |
| **WorldEdit** | 메서드 수준 `LevelChunk.setBlockState`, `chunk.setBiome` + `setUnsaved`, `getSections()[i] != null` 검사(블록 갱신 알림 전) | 주 스레드 | `getSections()` 가 null 없는 안정 배열이면 충분(N2a 로 이미 동작) |
| **squaremap** | 읽기 전용: `getSections()` → `getStates().copy()`·`getBiomes().copy()`, `hasPrimedHeightmap`·`getOrCreateHeightmapUnprimed(WORLD_SURFACE).getRawData().clone()` 으로 스냅샷을 만들어 렌더 스레드로 넘김 | 스냅샷은 `MinecraftServer` 실행기(주 스레드), 렌더는 자기 풀 | `MinecraftServer.execute` 가 Furnace 에서 어느 스레드로 가는지(전역 → region 빌림)와 **WORLD_SURFACE raw 가 바닐라와 같은지** — 1장의 빈 열 버그가 지도에 그대로 드러난다 |
| **Denizen** | ① 바이옴 컨테이너 제자리 쓰기: `getSection(i).getBiomes()` 를 `PalettedContainer` 로 캐스팅 → `acquire` → `getAndSetUnchecked` → `release`(저장 표시 없음) ② `chunk.setBiome` + `setUnsaved` ③ `heightmaps` **필드** 직접 읽기(`getHandle(FEATURES).heightmaps.get(MOTION_BLOCKING)`) ④ 가짜 블록: 청크 패킷을 디코드해 자기 `PalettedContainer` 로 다시 씀(청크가 아니라 패킷) | 스크립트 실행 스레드(주 스레드) | ① 은 **블록이 아니라 바이옴 컨테이너의 W1** — 설계의 칸 일지·훑기가 바이옴 컨테이너도 덮어야 한다(설계 3.7 에 바이옴 명시 필요, 아래 4) ③ 은 `heightmaps` 필드 GETFIELD — 거울 `ChunkAccess` 에 바닐라 모양 `Map` 필드 필요 ④ 는 N3a 패킷 shim 몫 |
| **BentoBox** | Spigot 이름 `nmsChunk.a(pos, state, false)`(= `LevelChunk.setBlockState`) — 메서드 수준 | 주 스레드 | H7d 이름 변환 + N2a. 필드 수준 없음 |
| **Iris** | 월드젠: 바이옴 저장소·`IdMapper` 필드 리플렉션 | 생성 스레드 | N2b 범위 밖(월드젠 API 과제) |
| **BlockChanger** | `getSections` 를 `findVirtual` 로, `LevelChunkSection.setBlockState` 를 `findVirtual` 로, **`MethodHandles.arrayElementSetter(LevelChunkSection[])`** 로 섹션 배열 원소 교체 | 주 스레드 | 배열 원소 교체는 AASTORE 와 같은 경로라 **쓰기 장벽이 못 본다**(설계가 AASTORE 를 장벽에서 뺐다) → 훑기의 정체성 검사가 잡아야 한다. 설계대로 |

### 3.2 스크립트 원시 개수(`nms-field-survey.py`)

글자 맞추기라 거짓 양성이 있다(Denizen W3 4건 중 3건은 `CraftPersistentDataContainer.getRaw`, 1건은 패킷 빛 바이트의 `DataLayer`; squaremap `new PalettedContainer` 는 자기 스냅샷의 빈 섹션). 3.1 이 사람이 확인한 결과다.

| 분류 | 모양 | BentoBox | BlockChanger | Denizen | FAWE | Iris | WorldEdit | squaremap |
|---|---|---|---|---|---|---|---|---|
| W1 | LevelChunkSection.setBlockState | · | · | · | 1 | · | 1 | · |
| W1 | PalettedContainer.getAndSet(Unchecked)/set | · | · | 1 | · | · | · | · |
| W1 | PalettedContainer acquire/release(잠금) | · | · | 2 | 4 | · | · | · |
| W2 | new LevelChunkSection | · | · | · | 3 | · | · | · |
| W2 | new PalettedContainer | · | · | 3 | 5 | · | · | 1 |
| W2 | 섹션 배열 원소 교체(CAS·arrayElement) | · | 1 | · | · | · | · | · |
| W2 | 바이옴 컨테이너 필드 교체 | · | · | 1 | 19 | · | · | 1 |
| W2 | 개수 필드 직접 대입(nonEmpty·ticking) | · | · | · | 11 | · | · | · |
| W2 | 하이트맵 setRawData | · | · | · | 1 | · | · | · |
| W3 | 원시 long[]·DataLayer byte[] 접근 | · | · | 4 | 5 | · | · | 1 |
| R | getSections() | · | · | · | 6 | · | 2 | 1 |
| R | getStates()/getBiomes() 복사·읽기 | · | · | 1 | 10 | 1 | 1 | 3 |
| R | heightmaps 필드·getOrCreateHeightmapUnprimed | · | · | 1 | 2 | · | · | 4 |
| P | setUnsaved/markUnsaved/setLightCorrect | · | · | 1 | 4 | 1 | 1 | · |
| P | 청크 패킷 직접 만들기·보내기 | · | · | 1 | 2 | · | · | · |
| P | 빛 다시 계산 직접 호출 | · | · | 8 | 4 | 1 | 1 | · |
| X | getDeclaredField/findGetter/findSetter·Unsafe | · | 30 | 5 | 28 | 5 | 5 | 1 |

(BentoBox 는 Spigot 이름이라 Mojang 이름 패턴에 안 걸린다 — 3.1 에서 손으로 확인.)

## 4. 바닐라 jar 정답기(V1 뼈대)

**도구** `tools/vanilla-data/ExportSectionOracle.java`(다른 Export 도구와 같은 방식, 1.21.1 난독화 이름): 번들러 jar 의 내부 서버 jar(서명 파일 `META-INF/*.SF·*.RSA` 를 지운 사본)와 라이브러리를 클래스패스에 두고 실행한다. 출력은 "이 입력에 바닐라가 낸 숫자"뿐이고, 입력은 시드로 시험이 다시 만든다(생성기를 양쪽에 같은 글자로 두고 입력 해시로 확인).
1. **상태별 개수 깃발**(26,684개): 바닐라 섹션에 그 상태 하나를 놓아(하나씩 놓기) 개수 3개가 얼마나 느는지, 그 상태 하나로 새 섹션을 만들어(다시 세기) 얼마나 되는지.
2. **섹션 300개**: 하나씩 놓은 섹션(A) → 같은 블록으로 새 섹션을 만들어 다시 센 섹션(B, 디스크에서 읽은 섹션과 같음) → B 에 하나씩 바꾸기(C)의 개수 3개.
3. **열 40개**(24 섹션): 바닐라 `ProtoChunk` + `Heightmap.primeHeightmaps` 뒤 `LevelChunk.setBlockState` 와 같은 순서(섹션 쓰기 → 같은 상태면 건너뜀 → 네 종류 `Heightmap.update`)로 바꾼 뒤 WORLD_SURFACE·OCEAN_FLOOR·MOTION_BLOCKING·MBNL raw.

**확인한 바닐라 개수 특이점**(정답표에 실제로 들어 있다 — N2b-1 shim 이 그대로 따라야 한다):
- 다시 세기(`recalcBlockCounts`)는 유체가 있는 칸을 nonEmpty 에 **두 번** 센다(블록 + 유체). 하나씩 놓기는 한 번. 섹션 300개 중 289개에서 A 와 B 의 nonEmpty 가 다르다.
- tickingFluid: 다시 세기는 **무작위 틱 유체(용암)만**, 하나씩 놓기는 **유체가 있으면 모두**(물 포함) 센다. 그래서 디스크에서 읽은 섹션에서 물을 지우면 **0 아래로 내려간다**(C 300개 중 90개가 음수). `isRandomlyTicking` 은 `> 0` 이라 음수는 "안 함"이다.
- 그 결과 **같은 블록인데 "하나씩 놓은 섹션"(월드젠·게임 중)과 "읽은 섹션"의 무작위 틱 여부가 300개 중 103개에서 다르다**(R17 — 양동이 물 스레드가 다루는 문제의 정량 근거).
- 위 숫자(289·90·103)는 생성기가 흥미로운 상태(물·용암·무작위 틱)를 70% 고르는 **합성 분포**의 값이다 — 실제 월드 빈도가 아니다. `vanillaCountModelMatchesOracle` 가 정답표에서 세어 못 박는다.
- **nonEmpty 의 0 여부도 갈린다**(검토 R3): 다시 센 섹션의 nonEmpty 여분(= 읽을 때의 유체 칸 수)은 그 칸을 지워도 1씩만 줄어 다시 셀 때까지 남는다. 물만 있던 섹션을 읽은 뒤 모두 공기로 비우면 바닐라 `hasOnlyAir()`=false, Furnace `nonAirCount`=0. 영향: `LevelChunk.setBlockState` 의 "빈 섹션에 공기면 바로 반환", 빛 `updateSectionStatus`, 청크 패킷 nonEmpty. 무작위 틱 판정과 함께 R17(양동이 물) 과제로 넘겼다. 물 섹션의 "무작위 틱"은 물 자체가 틱을 받지 않아도 `ServerLevel.tickChunk` 가 그 섹션에서 `getBlockRandomPos` 를 randomTickSpeed 번 불러 레벨 공용 난수를 진행시키므로 다른 섹션이 고르는 칸이 바뀐다(검토 R4).
- Furnace `ChunkSection.nonAirCount` 는 유체 두 번 세기가 없는 정확한 수, `tickingCount` 는 "블록 무작위 틱 또는 용암" 칸 수다 — 즉 **다시 세기 판정**과 같다(시험으로 확인). 하나씩 놓기 쪽 판정과 위 nonEmpty 여분은 R17 과제.

**시험** `VanillaSectionOracleTest`(정답표 46 KB gz):
- `stateFlagsMatchFurnaceTables` — Furnace 공기 판정·`BlockBehavior.isRandomlyTicking`·유체 상태·용암 판정이 26,684개 모두 바닐라와 같다.
- `vanillaCountModelMatchesOracle` — 바닐라 개수 규칙의 본 모델(하나씩 놓기·다시 세기)이 300개 모두 정답과 같다. N2b-1 의 떨어진 `LevelChunkSection` 이 이 모델을 쓴다.
- `furnaceSectionMatchesVanillaOnSameInput` — Furnace 비공기 수 = 바닐라 하나씩 놓기 nonEmpty, 무작위 틱 판정 = 바닐라 다시 세기(하나씩 놓은 섹션·통째 교체 섹션 모두).
- `furnaceColumnHeightmapsMatchVanilla` — 네 하이트맵이 처음(로드 직후처럼 내용에서 만든 값)과 바꾼 뒤 모두 바닐라와 같다. 단 "바꾼 뒤" 단계의 빈 열 WORLD_SURFACE 버그 모양만 허용하고 그 수를 44칸으로 못 박는다 — 수정(PR #354)이 들어오면 0 이 되어 실패하므로 그때 허용을 지운다.

**비용**: 부트스트랩 4.4초, 깃발 1.5초, 섹션 300개 0.3초, 열 40개 0.2초(이 컨테이너). `ProtoChunk(pos, UpgradeData.EMPTY, sections[24], ticks, ticks, LevelHeightAccessor.create(-64,384), null, null)` — 섹션을 다 넘기면 `replaceMissingSections` 가 레지스트리를 쓰지 않는다. 바이옴 컨테이너는 `null` 로 둬도 개수·하이트맵에는 쓰이지 않는다. **N2b-2 고정 하이트맵의 정답기는 이 도구를 늘리면 된다**(원시 쓰기 → 낡은 값 유지 → 다음 `update` 의 결과까지 같은 방식으로 기록).

**정답표를 다시 만들 때**(생성기를 바꾸면 해시가 달라져 시험이 바로 알려 준다):
```
unzip <번들러 jar> 'META-INF/versions/*' 'META-INF/libraries/*'
cp META-INF/versions/1.21.1/server-1.21.1.jar s.jar && zip -qd s.jar 'META-INF/*.SF' 'META-INF/*.RSA'
CP="s.jar:$(find META-INF/libraries -name '*.jar' | tr '\n' ':')"
javac -d out -cp "$CP" tools/vanilla-data/ExportSectionOracle.java && java -cp "$CP:out" ExportSectionOracle oracle.txt
gzip -9 -c oracle.txt > furnace-server/src/test/resources/n2b/section-oracle-1.21.1.txt.gz
```

## 5. 설계에 되먹일 것(N2b-1 이후 단계가 반영)

1. **바이옴 컨테이너도 칸 일지·훑기 대상**(Denizen ①): 설계 3.7 의 W1 일지가 블록 컨테이너만 말한다. 바이옴 `getAndSet(Unchecked)` 와 `acquire/release` 도 같은 규칙(주인은 바로, 비동기는 일지)으로 다룬다.
2. **거울 `ChunkAccess` 의 `heightmaps` 필드**(Denizen ③): GETFIELD 로 읽히므로 바닐라 모양 `Map<Types, Heightmap>` 필드가 있어야 하고, 그 값은 Furnace 하이트맵(고정 모드면 고정 값)을 비춰야 한다.
3. **WORLD_SURFACE raw 는 플러그인이 직접 읽는다**(squaremap): 빈 열 버그가 고쳐지기 전에는 Furnace 지도 결과가 바닐라와 다르다. N2b-6 대조 전에 수정 필요.
4. **떨어진 섹션의 개수 3개는 4장 모델을 그대로**: 생성자(다시 세기)와 `setBlockState`(하나씩)가 다른 규칙이어야 바닐라와 같다. Furnace 정의(`nonAirCount`·`tickingCount`)와 따로 둔다.
