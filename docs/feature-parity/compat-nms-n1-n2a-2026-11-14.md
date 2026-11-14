# NMS 어댑터 N1 + N2a — 핸들 멤버와 청크 메서드 수준 표면 (2026-11-14)

계획: `compat-nms-adapter-plan-2026-11-11.md` 4장 순서 2. 결정: 도미니코 11-14 B6(N1+N2a 지금 병행, N2b 는 설계 먼저).

## 1. 무엇이 생겼나

플러그인이 `ServerLevel`·`LevelChunk` 를 Mojang 이름으로 부르면 Furnace 서버 청크를 **보기**(매 호출 서버 데이터를 읽음)로 읽고, 블록 쓰기는 `World.editBlocksNow` 로 간다. 이름·설명자는 바닐라 1.21.1(Mojang 이름) 그대로라 NmsSymbols 가 바이트코드 대조로 통과시킨다.

| 클래스 | 표면 |
|---|---|
| `Level` (LevelAccessor 구현) | `OVERWORLD`·`NETHER`·`END`, `dimension()`, `getMinBuildHeight`·`getMaxBuildHeight`·`getHeight()`, `getChunkSource()`, `getChunk(II)`·`getChunkAt(BlockPos)`·`getChunk(II,ChunkStatus,Z)`, `hasChunk`·`hasChunkAt`·`isLoaded`, Paper `getChunkIfLoaded`·`getBlockStateIfLoaded`, `getBlockState`·`getFluidState`, `getHeight(Types,x,z)`·`getHeightmapPos`, `setBlock(pos,state,flags[,depth])`·`setBlockAndUpdate`·`removeBlock`·`isEmptyBlock` |
| `ServerLevel` | `getChunkSource(): ServerChunkCache`, `getLevel()`, `getMinecraftWorld()`, `getEntity(UUID)` |
| `ServerChunkCache` / `ChunkSource` | `getChunk(II,ChunkStatus,Z)`·`getChunk(IIZ)`·`getChunkNow`·`hasChunk`·`getLoadedChunksCount`, Paper `getChunkAtIfLoadedImmediately`·`getChunkAtIfCachedImmediately` |
| `ChunkAccess` / `LevelChunk` | `getPos`, `getSections`·`getSection`, `getBlockState`·`getFluidState`·`getBlockStateIfLoaded`, `getOrCreateHeightmapUnprimed`·`hasPrimedHeightmap`·`getHeight(Types,x,z)`, `getPersistedStatus`, `setUnsaved`, `getNoiseBiome`, `getLevel`, `setBlockState(pos,state,moved)` |
| `LevelChunkSection` | 상수 4개, `getBlockState`·`getFluidState`, `hasOnlyAir`, `isRandomlyTicking`, `getStates`·`getBiomes`, `getNoiseBiome`, `maybeHas`, `acquire`·`release`·`recalcBlockCounts`(할 일 없음) |
| `PalettedContainer` / `PalettedContainerRO` | `get(III)`, `getAll`, `maybeHas`, `count`, `copy()`(떼어 낸 고칠 수 있는 사본), 사본에만 `set`·`getAndSet`·`getAndSetUnchecked`, `acquire`·`release` |
| `Heightmap` (+`Types`·`Usage`) | `getFirstAvailable`·`getHighestTaken`·`getRawData`, `Types` 6개(직렬화 키·`sendToClient`·`keepAfterWorldgen`) |
| `ChunkStatus` | 12개 상태(바닐라 순서), `getIndex`·`getParent`·`byName`·`isOrAfter` 등 비교, `getName`·`toString` |
| `Biome`·`Registries.BIOME` | 키만 가진 값, 같은 키는 같은 `Holder.Reference` |
| `Entity` (N1) | `position()`·`blockPosition()`·`chunkPosition()`·`getType()` |
| `MinecraftServer` (N1) | `getLevel(ResourceKey<Level>)`·`overworld()`·`levelKeys()` |
| 인터페이스 | `LevelHeightAccessor`·`BlockGetter`·`LevelReader`·`LevelWriter`·`LevelAccessor`(바닐라 default 그대로) |

## 2. 바닐라와 같게 맞춘 것 (javap 로 1.21.1 바이트코드 확인)

- `LevelChunk.getBlockState`: 섹션이 `hasOnlyAir` 면 그 칸이 동굴 공기여도 `minecraft:air` 를 돌려준다.
- `Level.getBlockState` 높이 밖 → `void_air`, `getFluidState` 높이 밖 → 빈 유체. `Level.getHeight` 로드 안 된 청크 → `getMinBuildHeight`.
- `ChunkAccess.getHeight(Types,x,z)` = `getFirstAvailable - 1`. `LevelChunk` 에서 월드젠 전용 하이트맵(`WORLD_SURFACE_WG`·`OCEAN_FLOOR_WG`)은 비어 있다(최저 y).
- `Heightmap.getRawData`: 비트 수 = `ceil(log2(height+1))`, long 하나에 `64/bits` 칸, 값 = `firstAvailable - minY`, 순서 `x + z*16`.
- `ChunkStatus.byName` 모르는 이름 → `EMPTY`, `EMPTY.getParent()` → 자기.
- `setBlock` 플래그: 1 → 이웃 갱신 즉시, 2 → 클라이언트에 보냄, 16 또는 남은 깊이 ≤ 0 → 모양 갱신 없음, 4·8 은 렌더 힌트라 무시. `LevelChunk.setBlockState` 는 이웃·모양·패킷 없이 바꾼다(바닐라와 같음).
- `PalettedContainer.count` 는 처음 나온 순서로 센다. `maybeHas` 는 실제 칸을 본다(팔레트에 남은 옛 값으로 참이 되지 않음 — 바닐라는 팔레트를 보지만 참일 수 있다는 뜻이라 관측상 허용 범위).

## 3. 스레드·로드 규칙

- "보기" 읽기는 그 청크를 가진 region 스레드(또는 전역 빌림 구간)에서만. 아니면 `ThreadOwnershipException`(어느 메서드에서 무엇을 했는지가 메시지에).
- Furnace 는 청크를 동기로 불러오지 않는다. 로드 안 된 청크를 `getChunk(...,create=true)` 로 달라고 하면 이유와 대안(`getChunkIfLoaded`·`getChunkAtIfLoadedImmediately`·Bukkit `getChunkAtAsync`)을 담은 `IllegalStateException`. `create=false` 면 바닐라처럼 null.
- 섹션·팔레트 보기는 쓰기를 받지 않는다(`UnsupportedNmsCall`, `copy()` 로 사본을 쓰라고 안내). 블록 쓰기는 `Level.setBlock`·`LevelChunk.setBlockState`.

## 4. 계획과 다른 점 — 모듈 경계

계획 5장 결정 1 은 서버에 기대는 새 모듈 `furnace-compat-nms` 였다. 실제로 나눠 보니 `ServerLevel`·`CraftWorld`·`CraftPlayer` 가 이미 `furnace-compat-paper` 에 있고 청크 shim 이 이들과 서로 불러 순환이 생긴다. 그래서 **`furnace-api` 의 불안정 내부 다리 `ChunkInternals`**(`Internals.chunks()`, 블록 상태 번호·바이옴 번호 단위 읽기 12개)를 두고 서버가 구현하게 했다. 호환 계층은 여전히 `furnace-api` 만 본다. `Internals` 와 같은 등급(버전 중립 보장 없음)이고 일반 플러그인용이 아니라고 문서에 적었다. N2b(필드·배열 쓰기)·N3a(chunkMap·ChunkHolder)가 서버 내부를 더 넓게 필요로 하면 그때 다리를 넓히거나 모듈을 나눈다.

## 5. 하지 않은 것 (다른 묶음)

- **N2b**: `LevelChunkSection` 생성자·`setBlockState`, `Heightmap` 생성자·`setRawData`, `PalettedContainer` 공개 생성자, 비공개 필드(`data`·`tickingBlockCount`·`heightmaps`·`sections` 배열 대입), ProtoChunk. 이 심볼들은 계속 함정(UnsupportedNmsLink)으로 남는다 — `NmsChunkSymbolsTest` 가 "없음"을 확인한다.
- **N3a**: `ChunkMap`·`ChunkHolder`·빛 엔진·티켓·`getFullStatus`.
- 블록 엔티티 읽기, CraftBukkit 엔티티 종류별 클래스 나무(T-141 Bukkit 엔티티 인터페이스와 겹침), `DamageSource`, `PalettedContainer` 직렬화·`recreate`, 바이옴 속성(기후·효과), `Level.getHeight` 의 ±3000만 밖.
- FAWE 편집은 아직 안 된다(N2b + N4a + `DataComponentPatch` 등 필요). FAWE 의 없는 심볼 수(330)는 사용자 jar 로만 다시 잴 수 있다.

## 6. 시험

- `NmsChunkShimsTest`(10): 높이 접근자, 섹션 보기를 칸마다 `Region.getBlock` 과 대조(섹션 0·1·4·23), 레벨 읽기·하이트맵·raw data·바이옴, 사본 분리·보기 쓰기 거부, 동굴 공기만 있는 섹션, `setBlock` 플래그(2|16 은 횃불이 남고 3 은 떨어짐), `LevelChunk.setBlockState` 는 블록 갱신 패킷 없음·플래그 3 은 보냄, 로드 안 됨·다른 스레드, `ChunkStatus`·`Heightmap.Types`, 엔티티 멤버.
- `NmsChunkSymbolsTest`: 위 표면의 바닐라 설명자가 NmsSymbols 에 있고, N2b 심볼은 없는지.
