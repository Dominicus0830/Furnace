# Furnace M2 — 월드 데이터 모델 + 청크 전송 설계서

> 부모 설계서: `docs/superpowers/specs/2026-06-06-furnace-multithreaded-mc-server-design.md` (M0~M6 로드맵). 본 문서는 M2 상세.
> 선행: M0(스레딩 골격), M1(네트워킹 handshake→play, main 머지). 프로토콜 1.21.1(767).

## 1. 목표

바닐라 1.21.1 클라가 play 진입 후 **지형을 본다.** superflat 월드를 생성해 청크를 바닐라 와이어 포맷으로 인코딩·전송하고, 플레이어가 바닥에 서서 안 빠진다. 청크 직렬화를 워커 스레드로 오프메인해 **프로젝트 첫 진짜 멀티스레드 이득**을 실현한다.

**프로젝트 정체성 = 최적화.** Paper 한계 돌파, Pumpkin/Leaf급. 설계 트레이드오프는 정석·완전최적 쪽을 택한다.

## 2. 비목표 (M2 제외)

- Anvil(기존 월드 .mca 로드) — M4 영속성에서 read+write 한 단위로. (부모 설계서 원안의 "M2 Anvil 읽기"를 M4로 이동 — 본 문서가 그 변경의 출처.)
- 블록 파괴/설치, 블록 업데이트 — M4.
- 조명 엔진(실제 라이트 계산) — M5. M2는 청크 패킷 라이트 필드를 superflat 고정값(풀 skylight)으로 채워 어둠만 방지.
- region 분할 병렬 틱 — M3. M2는 region=월드 전체 1개 유지.
- NBT reader — M2는 heightmap용 **write만**. (M1의 "configuration registry는 무NBT" 전제가 M2 청크 패킷에서 깨짐 — heightmap이 NBT 필수.)

## 3. 확정된 핵심 결정

| 항목 | 결정 | 근거 |
|---|---|---|
| 범위 | superflat 생성만 + `WorldSource` 인터페이스 | Anvil은 write와 짝이라 M4로. 인터페이스로 retrofit 회피 |
| 블록 ID | blocks.json 전체 데이터주도 | 팰릿이 전역 state id 요구. M1 레지스트리 패턴(데이터주도)과 일관 |
| 월드 생성 | 클래식 superflat (bedrock/dirt×2/grass) | 바닐라 기본 flat과 동일, "바닥 안 빠짐" verify 쉬움 |
| 팰릿 | 단일+간접+직접 전체 구현 (바닐라 정석) | 최적화 목표. 대역폭 최소. direct-only 단순화 거부 |
| 오프스레드 | 전용 `ChunkWorkerPool`(CPU 코어 수) 병렬 직렬화 → channel.eventLoop로 전송 | 설계서 "첫 MT 이득". M0 스케줄러 global()은 단일 스레드라 병렬 불가 → 전용 풀. 청크 직렬화는 불변 스냅샷 순수함수라 소유권 모델 무관 |
| NBT | 최소 write만 (compound/long_array/end) | heightmap만 필요. reader는 M4 |

## 4. 아키텍처

파이프라인은 M1 그대로. 추가는 **월드 모델 + 청크 직렬화 + onEnterPlay 청크 스트림**.

```
world/                          월드 데이터 모델 (region 스레드 소유)
  BlockRegistry                 blocks.json → 블록명→default state id, 전체 state 수
  BitStorage                    값 N개를 bitsPerEntry로 long[] 팩킹 (compacted)
  Palette (sealed)              SingleValue | Indirect | Direct
  ChunkSection                  블록 16³ + 바이옴 4³ + 비공기 수
  ChunkColumn                   24섹션(y −64..319) + heightmap 2종
world/gen/
  WorldSource (interface)       ChunkColumn generate(cx, cz)
  SuperflatWorldSource          레이어 채움
net/io/nbt/
  NbtWriter                     네트워크 NBT write (루트 이름 없음)
net/protocol/play/
  ChunkSerializer               ChunkColumn → ByteBuf (바닐라 청크 포맷)
  LevelChunkWithLight           clientbound 청크 패킷
net/protocol/play/
  SetCenterChunk                clientbound set_chunk_cache_center
```

### 4.1 데이터 흐름 (청크 전송)

```
onEnterPlay (channel eventLoop)
  ├─ send LoginPlay, SyncPlayerPosition, SetDefaultSpawnPosition  (M1 기존)
  ├─ send SetCenterChunk(0, 0)
  └─ for each (cx,cz) in radius:
        chunkWorkerPool.submit:                          ← 워커풀 스레드 (CPU, 병렬)
           ChunkColumn col = worldSource.generate(cx,cz)  (불변)
           ByteBuf buf = ChunkSerializer.serialize(col)
        .thenAccept(buf ->
           ctx.channel().eventLoop().execute(() ->        ← IO 스레드 (전송)
              send(LevelChunkWithLight(buf))))
```

`ChunkWorkerPool` = 고정 크기 ExecutorService(코어 수), `CompletableFuture<ByteBuf> submit(Supplier)`. NetworkServer 생애주기로 생성/셧다운.

`ChunkColumn`은 생성 후 불변 → 워커 직렬화 중 레이스 없음. 청크는 SetCenterChunk 뒤면 도착 순서 무관(클라가 좌표로 배치).

### 4.2 스레드 소유권

`ChunkColumn`/`World`는 region 스레드 소유(M0 소유권 모델). M2는 region 1개. 직렬화는 **불변 스냅샷의 순수 함수**라 워커서 안전. 생성도 superflat은 순수 계산이라 워커서 가능. M3서 region 분할 시 이 경계가 그대로 확장된다.

## 5. 컴포넌트 상세

### 5.1 BlockRegistry — 데이터주도 블록 ID

blocks.json(5.4MB, datagen 산출)은 jar에 넣기 무겁다. **distill**해 컴팩트 리소스 생성.

- datagen: `java -DbundlerMainClass=net.minecraft.data.Main -jar server.jar --reports` → `generated/reports/blocks.json`.
- 각 블록의 `states[]` 중 `"default": true`인 항목의 `id`가 default state id.
- 산출 리소스 `resources/blocks/1.21.1.txt`: 한 줄 `<block_id> <default_state_id>`. 헤더 줄에 전체 state 개수(`# states <N>`).
- 런타임 `BlockRegistry.load()`: 블록명→default state id 맵, 전체 state 수 보관. 전체 수로 direct 팰릿 비트수 `ceil(log2(N))` 계산.
- M2가 실제 쓰는 블록: `minecraft:air`(id 0), `bedrock`, `dirt`, `grass_block`. 데이터주도라 확장 시 코드 무변경.

### 5.2 BitStorage — 팩킹 프리미티브

바닐라 "compacted" long array. `valuesPerLong = 64 / bitsPerEntry`(나머지 비트 버림, 값이 long 경계 안 넘음).

- `BitStorage(int bitsPerEntry, int size)` → `long[] ceil(size / valuesPerLong)`.
- `set(index, value)`, `get(index)`, `long[] data()`.
- bitsPerEntry=0 특수 처리(단일값 팰릿, 데이터 배열 없음).

### 5.3 Palette — sealed interface

분포로 인코딩 자동 선택. 블록/바이옴 공용 로직, 임계값만 다름.

| 종류 | 블록(섹션 4096칸) | 바이옴(섹션 64칸) | 와이어 |
|---|---|---|---|
| SingleValue | 고유값 1개 | 고유값 1개 | bits=0, [VarInt 값], 데이터 배열 없음 |
| Indirect | 고유 2~? (≤8비트) | 고유 2~3 (≤3비트) | bits, [팰릿 길이][팰릿 VarInt들], BitStorage |
| Direct | 초과 | 초과 | bits=전역비트, 팰릿 없음, BitStorage(전역 id) |

블록 간접 비트: 고유 수에 맞춰 4~8(바닐라는 4 미만도 4로 올림). 직접 비트 = `ceil(log2(전체 block state 수))`(≈15). 바이옴 직접 비트 = `ceil(log2(전체 biome 수))`.

> 정확도 핵심: 바닐라 클라는 블록 간접 팰릿 최소 4비트를 기대. 2~3 고유값이어도 4비트로. 바이옴은 1~3비트 허용.

### 5.4 ChunkSection / ChunkColumn

- `ChunkSection`: `blocks`(4096 state id) + `biomes`(64 biome id) + `nonAirCount`(short). 직렬화 시 블록/바이옴 각각 Palette+BitStorage로.
  - 와이어: `[short nonAirCount][block palette+data][biome palette+data]`.
- `ChunkColumn`: `ChunkSection[24]`(y −64..319, 섹션0 = y −64..−49) + heightmap.
  - heightmap: MOTION_BLOCKING, WORLD_SURFACE. 각 256칸(16×16), 칸당 `ceil(log2(worldHeight+1))`=9비트, BitStorage long[]. 값 = 해당 (x,z) 최고 블록의 (worldY − minY + 1).
  - superflat: 표면 grass_block at y=−61 → heightmap 값 = (−61 − (−64) + 1) = 4.

### 5.5 NbtWriter — 네트워크 NBT write

1.20.2+ 네트워크 NBT는 **루트 compound에 이름 없음**. heightmap 전송에 필요한 최소 태그만.

- `writeCompoundStart()` (TAG_Compound 0x0a, 루트는 이름 생략 / 중첩은 이름 포함)
- `writeLongArray(name, long[])` (TAG_Long_Array 0x0c)
- `writeEnd()` (TAG_End 0x00)
- heightmap NBT 구조: `{ MOTION_BLOCKING: long[], WORLD_SURFACE: long[] }`.

### 5.6 ChunkSerializer + LevelChunkWithLight

바닐라 "Chunk Data and Update Light"(`level_chunk_with_light`) 포맷:

```
int chunkX, int chunkZ
NBT heightmaps                          (NbtWriter)
VarInt dataSize + data:                 24섹션 직렬화 연속
  per section: [short nonAir][blockPalette+data][biomePalette+data]
VarInt blockEntityCount = 0
--- Light (Update Light 부분) ---
BitSet skyLightMask, blockLightMask, emptySkyLightMask, emptyBlockLightMask
VarInt skyLightArrayCount + arrays (2048바이트씩)
VarInt blockLightArrayCount = 0 + arrays
```

- M2 라이트: superflat 지상 섹션은 **풀 skylight**(2048바이트 전부 0xFF), 지하는 0. 마스크는 BitSet(long[]). blockLight 없음. 어둠 방지 목적, 실제 조명은 M5.
- 패킷 id = packets.json `play.clientbound.minecraft:level_chunk_with_light`(plan서 확정).

### 5.7 SetCenterChunk

`set_chunk_cache_center`(packets.json서 id 확정, M1서 0x54로 관측). body: `VarInt chunkX, VarInt chunkZ`. 청크 스트림 전에 1회.

## 6. 설정 추가

- `view-distance`(기본 5 — M2 검증용 보수값. LoginPlay 선언값과 별개로 실제 전송 반경). config 키 추가.

## 7. 위험 / 검증

- **팰릿/BitStorage 비트 정렬** — 한 비트 어긋나면 클라가 청크 디코드 실패하고 끊김. BitStorage·Palette 단위 테스트(라운드트립 + 알려진 벡터)로 방어.
- **heightmap NBT 포맷** — 루트 이름 유무, 비트수 9 정확. NbtWriter 테스트.
- **패킷 ID** — M1 교훈: packets.json 권위 출처로 대조(set_default_spawn 0x54→0x56 사건). plan서 chunk/center id 확정.
- **스레드 안전** — ChunkColumn 불변 단언. 직렬화가 워커서 도는지 스레드 id 단언.

✅ **verify:** 바닐라 1.21.1 접속 → 스폰서 grass 바닥 렌더, 안 빠짐. 청크 직렬화가 워커 스레드(IO 아님)서 실행됨을 스레드 id로 단언.

## 8. 파일 구조 요약

| 파일 | 책임 |
|---|---|
| `world/BlockRegistry.java` | blocks.json distill 로드, 블록명→state id |
| `world/BitStorage.java` | compacted long array 팩킹 |
| `world/Palette.java` | sealed: SingleValue/Indirect/Direct |
| `world/ChunkSection.java` | 블록+바이옴 팰릿 섹션 |
| `world/ChunkColumn.java` | 24섹션 + heightmap |
| `world/gen/WorldSource.java` | 생성 인터페이스(Anvil 자리) |
| `world/gen/SuperflatWorldSource.java` | superflat 레이어 |
| `net/io/nbt/NbtWriter.java` | 네트워크 NBT write |
| `net/protocol/play/ChunkSerializer.java` | ChunkColumn → ByteBuf |
| `net/protocol/play/LevelChunkWithLight.java` | clientbound 청크 패킷 |
| `net/protocol/play/SetCenterChunk.java` | clientbound 센터청크 |
| `net/ChunkWorkerPool.java` | 전용 고정 스레드풀, 청크 병렬 생성+직렬화 |
| `resources/blocks/1.21.1.txt` | distill 생성물 |
| `net/ConnectionHandler.java` | (modify) onEnterPlay 청크 스트림 |
| `config/Config.java`, `ConfigLoader.java` | (modify) view-distance |
| `net/NetworkServer.java` | (modify) WorldSource + ChunkWorkerPool 주입 |

각 신규 클래스는 단위 테스트 동반(TDD). BitStorage·Palette·NbtWriter·ChunkSerializer는 라운드트립/벡터 테스트, 통합은 실 클라 접속(verify).
