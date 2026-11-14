# NMS 어댑터 N2b-1 — 떨어진 섹션 객체와 바닐라 개수 규칙 (2026-11-14)

설계: `compat-nms-n2b-design-2026-11-14.md` 6장 N2b-1 줄. 조사: `compat-nms-n2b0-survey-2026-11-14.md`(4장 정답기, 5장 되먹임 4개). 결정: 도미니코 11-14 D1~D4 모두 A.

## 1. 무엇이 생겼나

플러그인이 서버와 상관없는 섹션·팔레트를 **직접 만들어** 쓰는 길(FAWE 가 청크를 통째로 갈아 끼우기 전에 하는 일)이 바닐라와 같은 결과를 낸다. 이 단계는 **서버에 아무것도 쓰지 않는다** — 떨어진 객체를 서버 청크에 넣는 길(배열 대입·필드 쓰기)은 N2b-2·N2b-4 다.

| 클래스 | 표면 |
|---|---|
| `PalettedContainer` | 공개 생성자 `(IdMap, T, Strategy)`, Paper 5인자 `(IdMap, Strategy, Configuration, BitStorage, List)`, 진짜 `volatile Data data` 필드, `get`·`set`·`getAndSet`·`getAndSetUnchecked`, `read`·`write`·`getSerializedSize`, `getAll`·`maybeHas`·`count`, `copy`·`recreate`, `acquire`·`release`(할 일 없음 — 3장) |
| `PalettedContainer.Strategy` | `SECTION_STATES`·`SECTION_BIOMES`, `size`·`getIndex`·`getConfiguration`, 직렬화 비트 수 |
| `Configuration`·`Data` | 바닐라 레코드 모양(공개), `createData`·`copyFrom`·`copy`·`write`·`getSerializedSize` |
| 팔레트 | `Palette`(+`Factory`), `SingleValuePalette`·`LinearPalette`·`HashMapPalette`·`GlobalPalette`, `MissingPaletteEntryException`, `PaletteResize` |
| 비트 저장소 | `BitStorage`, `SimpleBitStorage`(바닐라 배치, 범위 밖이면 commons-lang3 문구의 `IllegalArgumentException`, 길이 틀리면 `InitializationException`), `ZeroBitStorage` |
| ID 표 | `IdMapper`(바닐라 포팅 + 서버 상태 번호를 그대로 쓰는 고정 표), `CrudeIncrementalIntIdentityHashBiMap`, `Block.BLOCK_STATE_REGISTRY`, `Registry.asHolderIdMap()` |
| `LevelChunkSection` | 생성자 `(states, biomes)`(다시 세기)·`(Registry<Biome>)`(공기·평원), 필드 `nonEmptyBlockCount`·`tickingBlockCount`·`tickingFluidCount`(short)·`states`(final)·`biomes`(final 아님), `setBlockState`(하나씩 ±1), `recalcBlockCounts`, `hasOnlyAir`·`isRandomlyTicking(Blocks/Fluids)`, `read`·`readBiomes`·`write`·`getSerializedSize`, `getNoiseBiome` |
| `ChunkAccess` | `protected final LevelChunkSection[] sections` 필드, `public final Map<Heightmap.Types, Heightmap> heightmaps` 필드 |
| `BlockState` | `isRandomlyTicking()`(서버 블록 동작 표, 상태별로 한 번 읽어 둠) |

## 2. 바닐라와 같게 맞춘 것

**정답기** `tools/vanilla-data/ExportPaletteOracle.java` — 바닐라 1.21.1 서버 jar 의 `PalettedContainer`·`LevelChunkSection` 을 떨어진 객체로 만들어 시드로 만든 같은 입력을 넣고, 직렬화 바이트의 해시(비트 수·팔레트 순서·long 배치가 모두 들어 있다)·크기·`maybeHas`·`count` 합·`copy`/`recreate` 해시·개수 3개를 적는다. 240건(4종 × 60):
- `DEFAULT` — `(IdMap, T, Strategy)` 생성 후 칸 쓰기(블록 상태·바이옴). 팔레트 크기가 단일 → 선형 → 해시 → 전역으로 넘어가는 경계를 일부러 넘긴다.
- `CTOR`·`CTOR_MISMATCH` — 5인자 생성자. 일부는 Configuration 의 비트 수와 저장소 비트 수가 다르거나 팔레트에 없는 번호를 담는다(FAWE 가 실제로 만드는 모양). 바닐라는 29건에서 `IllegalArgumentException` — 같은 자리(몇 번째 연산)에서 같은 예외 종류가 나야 통과.
- `SECTION` — 무작위 블록으로 `LevelChunkSection` 을 만들어(다시 세기) 칸을 바꾸고(하나씩) 개수 3개와 섹션 직렬화 해시.

**시험** `NmsDetachedSectionOracleTest`:
- `paletteContainersMatchVanillaOracle` — 240건 모두 직렬화 바이트·크기·예외 위치가 바닐라와 같다.
- `detachedSectionCountsMatchVanillaOracle` — 섹션 300개(N2b-0 정답표 `section-oracle-1.21.1.txt.gz` 의 같은 입력)에서 생성자 = 다시 세기, `setBlockState` = 하나씩. 음수 `tickingFluidCount` 90개, 다시 센 섹션과 하나씩 놓은 섹션의 판정이 갈리는 경우가 실제로 있는지까지 못 박는다.
- `stateFlagsThroughShimMatchVanilla` — 26,684개 상태의 `isAir`·`isRandomlyTicking`·유체·용암 판정이 shim 을 거쳐도 바닐라와 같다.
- `biomeRegistrySizeMatchesVanilla`(64 — 바이옴 전역 비트 수가 여기서 정해진다), `fieldsHaveVanillaNamesAndTypes`(FAWE 처럼 `Field.setShort` 로 개수를 쓰고 `biomes` 필드를 갈아 끼우면 그대로 보인다), `copyIsIndependentAndRecreateUsesFirstPaletteEntry`.
- 변이 5개(다시 세기에서 유체 두 번 세기 뺌, 하나씩 놓기에서 물을 tickingFluid 에서 뺌, 선형 팔레트 비트 +2, 해시 팔레트 번호 순서, 바이옴 Configuration 비트)를 넣으면 모두 실패했다.

**개수 규칙**(조사 문서 4장, 되먹임 4): 생성자·`recalcBlockCounts` 는 유체 칸을 nonEmpty 에 두 번, tickingFluid 는 용암만. `setBlockState` 는 ±1, tickingFluid 는 비어 있지 않은 유체 모두(물 포함) — 음수가 될 수 있고 `isRandomlyTicking` 은 `> 0`. Furnace 서버 정의(`nonAirCount`·`tickingCount`)와 따로 둔다.

**PR #358(양동이 물) 정렬**: 서버 `ChunkSection` 이 바닐라 `tickingFluidCount` 를 갖게 됐다. 서버 청크의 섹션 **보기**는 `isRandomlyTicking`·`isRandomlyTickingBlocks`·`isRandomlyTickingFluids` 를 모두 서버의 그 개수로 답한다(다리 `sectionRandomTickingPartsNow` — 비트 1 블록, 2 유체). 칸 쓰기로 놓은 물은 유체 쪽이 참이다(`NmsChunkShimsTest.viewRandomTickPartsFollowServerVanillaCounts`).

**팔레트 세부**: 블록 1~4 비트는 4비트 선형, 5~8 해시, 그 위는 전역 `ceillog2(26684)` = 15. 바이옴 1~3 비트 선형, 그 위 전역 `ceillog2(64)` = 6. 읽기(`read`)는 바닐라 `unpack` 과 같은 순서(처음 나온 순서의 팔레트, 직렬화 비트 수 계산).

## 3. 안정 정체성 (설계 3.5)

같은 청크를 다시 달라고 하면 **같은** `LevelChunk` 와 같은 `sections` 배열·같은 섹션·같은 컨테이너 객체가 온다. 청크가 로드돼 있는 동안(서버 `ChunkColumn` 객체가 같은 동안) 유지된다:
- `NmsChunks` 의 `WeakHashMap<서버 청크 표시, WeakReference<LevelChunk>>`. 서버 표시는 다리 `loadedChunkToken`(서버 `ChunkColumn` 그 자체 — 호환 계층은 `Object` 로만 본다).
- 섹션은 자기 청크를 **강하게** 쥔다 — 플러그인이 섹션만 들고 있어도 청크 shim 이 사라져 다른 객체가 나오는 일이 없다.
- 청크가 내려갔다 다시 올라오면 새 `ChunkColumn` 이라 새 shim(바닐라도 새 `LevelChunk`).
- 월드젠 하이트맵(`WORLD_SURFACE_WG` 등)은 `getOrCreateHeightmapUnprimed` 로 처음 만들면 그 shim 이 살아 있는 동안 `heightmaps` 맵에 남는다(바닐라와 같은 모양).

## 4. 되먹임 4개 (조사 문서 5장)

| # | 내용 | 이 PR |
|---|---|---|
| 1 | 바이옴 컨테이너도 칸 일지·훑기 대상 | 설계 3.4 에 적고 6장 N2b-2(주인 스레드 바이옴 칸 쓰기)·N2b-4(일지 — 블록·바이옴 칸) 줄에 넣음. 떨어진 섹션의 바이옴 컨테이너는 이미 진짜 객체 |
| 2 | 거울 `ChunkAccess.heightmaps` 필드 | 필드 구현(로드 뒤 보존되는 4종 보기). 고정 하이트맵 값을 비추는 일은 N2b-2 |
| 3 | WORLD_SURFACE 빈 열 버그 | PR #354 로 이미 고침 |
| 4 | 떨어진 섹션 개수 3개는 바닐라 모델 그대로 | 구현·정답 시험(2장) |

## 5. 결정 D1~D4 와 이 단계

- D1(칸 일지)·D3(저장 표시 없는 편집은 저장 안 함 + 내려갈 때 경고 한 줄)·D4(다른 region 스레드 = 비동기 스레드 규칙)는 서버에 쓰는 단계(N2b-2~4)의 일이다. 이 PR 은 떨어진 객체만 만들어 해당 없음. 보기는 여전히 쓰기를 거부한다(`UnsupportedNmsCall`, N2b-2 안내).
- D2(섹션 교체 때 빛 계산 안 함 — Paper 같게)는 N2b-4·N2b-5.

## 6. 가정·차이 (확인 못 한 것)

- **`acquire`/`release` 는 할 일 없음.** Paper 1.21.1 은 `ThreadingDetector` 를 끈다고 알고 있어 같게 뒀다 — 가정, V6 Paper 대조 때 확인.
- **`count` 의 호출 순서**: 바닐라는 fastutil `Int2IntOpenHashMap` 순회 순서, 여기서는 처음 나온 순서. 합과 각 값의 수는 같다(시험이 합을 본다). 순서에 기대는 플러그인은 아직 못 봤다.
- 서버 청크 섹션 **보기**의 `data` 필드는 null(진짜 팔레트를 만들지 않는다). 리플렉션으로 `data` 를 읽는 플러그인은 N2b-4(거울)에서 다룬다. `copy()`·`write()` 등은 보기에서도 바닐라 결과(그 자리에서 만들어 냄).
- 보기의 `write()` nonEmpty 는 지금 칸을 다시 센 값(디스크에서 읽은 섹션과 같음). 칸 쓰기로 바뀐 섹션의 바닐라 nonEmpty(하나씩 놓기 값)를 서버가 따로 갖는 것은 N2b-2(`sectionCountsNow`).
- 코덱(`codecRW`·`codecRO`)과 `pack` 은 넣지 않았다(DFU 가 필요 — 쓰는 플러그인을 아직 못 봄).
- `SimpleBitStorage` 는 바닐라의 나눗셈 마법수 표 대신 `index / valuesPerLong` 을 쓴다 — 결과가 같다(정답 시험이 long 배치를 본다).

## 7. FAWE 시작 실패 분석 (도미니코 `latest.log`, 빌드 e19110ca, FAWE 2.16.2)

**증상**: `Failed to load the built-in legacy id registry` → `BlockTypesCache.<clinit>` 237행 `ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 1`, 이어서 `Failed to load biomes via adapter (not present)`, 접속 뒤 `Material.isLegacy()` 없음(`NoSuchMethodError`).

**원인 사슬**(FAWE 2.16.2 소스로 확인):
1. FAWE 의 1.21.1 어댑터 `PaperweightFaweAdapter` 가 클래스 초기화에서 죽는다 — 148행 `DataComponentPatch.CODEC.optionalFieldOf(...)`(로그 61행 "미지원 NMS 호출"). 그래서 어댑터 없이 돈다(로그의 "does not fully support your version of Bukkit" 경고가 이 뜻).
2. 어댑터가 없으면 `BukkitBlockRegistry.values()` 가 `BlockRegistry` 기본값 **빈 목록**을 준다(FAWE 는 어댑터 없이 블록 목록을 얻을 길이 없다 — Paper 에서도 어댑터가 없으면 똑같이 죽는다).
3. `BlockTypesCache` 가 `values = new BlockType[블록 수 + 1]` = 길이 1 을 만들고, 예약 번호(`__RESERVED__`=0, `AIR`=1, `CAVE_AIR`=2, `VOID_AIR`=3)를 넣다가 1 에서 넘친다 — 이것이 "Index 1, length 1". 즉 **AIOOBE 는 결과일 뿐이고 Furnace 데이터가 틀린 것이 아니다**. 바이옴 실패도 같은 원인(어댑터 없음).

**어댑터가 살아나면 FAWE 가 Furnace 에서 기대하는 데이터**(어댑터 소스의 `getRegisteredDefaultBlockStates`·`init`·`adaptToChar`·`getInternalBiomeId`·`getRegisteredBiomes`·`PaperweightBlockMaterial`):

| FAWE 가 하는 일 | 필요한 Furnace 쪽 | 지금 |
|---|---|---|
| `BuiltInRegistries.BLOCK` 를 돌며 `CraftBlockData.fromData(block.defaultBlockState()).getAsString()` | 블록 레지스트리 순회(`DefaultedRegistry#iterator` — 로그에서 없는 기호), 기본 상태 문자열 | 레지스트리·`fromData` 있음, `iterator` 없음 |
| `init()`: 자기 상태 표의 모든 상태에 `Block.BLOCK_STATE_REGISTRY.getId(state)` → `ibdToOrdinal[id]`(길이 = FAWE 상태 수) | 상태 번호가 바닐라와 같고 0 ≤ id < 26,684 — 넘으면 AIOOBE | **N2b-1 에서 구현**(`BLOCK_STATE_REGISTRY` = 서버 상태 번호, 26,684 상태 판정이 바닐라와 같음을 시험) |
| 상태 판정(`PaperweightBlockMaterial`): `isAir`·`isRandomlyTicking`(FAWE 무작위 틱 표)·유체·`getLightBlock`·`getShape`·`canOcclude`·`isSignalSource`·`isSolidRender`·`destroySpeed`·`getExplosionResistance`·`getFriction`·`getLightEmission`·`getPistonPushReaction`·`blocksMotion`·`canBeReplaced`·`defaultMapColor` | `BlockState` 판정들, `EmptyBlockGetter` | `isRandomlyTicking` 은 **N2b-1 에서 구현**, 나머지 대부분과 `EmptyBlockGetter` 없음 |
| `BlockStateProperties` 의 정적 필드를 리플렉션으로 돌며 속성 이름·값 목록 | `BlockStateProperties`·`Property` 계열 | 없음 |
| `registryAccess().registryOrThrow(BIOME)` → `WritableRegistry` 로 캐스트, `stream`·`getKey`·`getId` | 바이옴 레지스트리 번호(바닐라 순서, 64개) | 번호 표는 **N2b-1 의 `NmsChunks.BIOME_IDS`**(시험으로 64 확인), `RegistryAccess#registryOrThrow`·`WritableRegistry` 없음 |
| 어댑터 생성자: `SharedConstants.getCurrentVersion().getDataVersion().getVersion()` 가 3953/3955, `CraftServer.worlds` 필드, `ServerChunkCache` 의 메인 스레드 청크 future 메서드·executor 필드(리플렉션), 데이터 변환기 | 버전 상수(1.21.1 = 3955), CraftServer·ChunkCache 내부 이름 | 없음 |
| 클래스 초기화: `DataComponentPatch.CODEC`(+ DFU `Codec.optionalFieldOf`) | 아이템 구성 요소 패치 코덱 | 없음(로그 61행) |
| (어댑터와 별개) `Material.isLegacy()` | Bukkit API 메서드 | 없음 — 호환 계층 Bukkit API 구멍(T-141 API 확장 물결) |

**정리**: N2b-1 은 이 중 상태 번호 표·무작위 틱 판정·바이옴 번호 표를 바닐라와 같게 만들었다. FAWE 가 켜지려면 어댑터 초기화 기호(위 표의 "없음" 줄)가 먼저 있어야 하고, 설계 6장 **N2b-6**(어댑터 선택·초기화 기호 묶음)의 첫 목록이 이 표다. `Material.isLegacy` 는 N2b 가 아니라 Bukkit API 쪽(T-141)이다.

## 8. 정답표를 다시 만들 때

`compat-nms-n2b0-survey-2026-11-14.md` 4장과 같다(서명 지운 내부 jar + 라이브러리). 인자만 다르다:
```
javac -d out -cp "$CP" tools/vanilla-data/ExportPaletteOracle.java && java -cp "$CP:out" ExportPaletteOracle oracle.txt s.jar
gzip -9 -c oracle.txt > furnace-compat-paper/src/test/resources/n2b/palette-oracle-1.21.1.txt.gz
```
생성기(`Cases`·`SectionCase`)는 도구와 시험에 같은 글자로 있고 입력 해시로 서로 확인한다 — 한쪽만 바꾸면 시험이 해시 불일치로 알려 준다.
