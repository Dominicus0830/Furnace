# M4b — Anvil 영속성 Design

**상태:** 설계 승인 → plan → 구현. M4 분해의 둘째(M4a 위). 브랜치 `m4b-persistence`.

## 1. 배경 / 범위

M4 = M4a(블록 파괴) → **M4b(영속성)** → M4c(인벤토리/아이템/블록설치). M4a는 블록 편집이 **세션 내만** 유지(재시작 시 유실). M4b는 청크 편집과 플레이어 위치를 디스크(.mca / playerdata)에 영속화한다.

**포함:**
- 풀 NBT read+write 코덱(파일용).
- Anvil 영역 파일(.mca) read+write.
- 표준 Anvil 청크 포맷 ↔ ChunkColumn.
- 비동기 청크 로드(디스크 IO를 region 틱서 분리).
- dirty 추적 + 청크 퇴출 + 저장(종료·퇴출·주기 autosave).
- 플레이어 **위치** 영속(playerdata NBT, 확장 가능).
- 기존 바닐라 .mca 월드 로드(팰릿 속성 무시 근사).

**제외:**
- **팰릿 속성 정확 해석**(통나무 축·계단 방향 등) → 별도 전용 마일스톤(M4d 등). M4b는 Name→default state id 근사.
- **인벤토리/체력** 플레이어 데이터 → M4c(playerdata 파일 확장).
- **블록 설치(place)** → M4c.
- **물리 전파·교차region 원자연산** → M5.

## 2. 핵심 결정

- **표준 Anvil 포맷 read+write.** 우리 write == 바닐라 read(같은 파서) → A(우리 라운드트립)·B(바닐라 로드) 코드 수렴. 외부 도구 호환.
- **비동기 청크 로드.** Anvil read = 디스크 IO. region 틱 막으면 TPS 저하(Paper 한계). IO 풀서 read+decode, owner 스레드서 저장. M4a `snapshotChunk`이 이미 async라 자연 통합.
- **저장 = dirty + 로드된 청크만.** 미로드/clean 청크는 안 건드림(바닐라/Paper 방식).
- **.mca per-파일 직렬화.** 한 .mca(32×32 청크)를 여러 Furnace region이 공유 → `RegionFileStore`가 파일별 락으로 read/write 직렬화.
- **플레이어 위치만**(M4b). 인벤은 M4c가 같은 playerdata 파일 확장.

## 3. 컴포넌트

### 3.1 NBT 코덱 (파일용)
- 신규 `io/nbt/Nbt`(태그 트리: byte/short/int/long/float/double/byte_array/string/list/compound/int_array/long_array). Map/record 기반.
- `NbtReader`(DataInput) / `NbtFileWriter`(DataOutput) — 명명 루트 compound, gzip/zlib 옵션.
- 기존 네트워크 `NbtWriter`(무명 루트 최소)는 유지(별도 용도).

### 3.2 Anvil 영역 파일
- 신규 `RegionFile`: `region/r.<rx>.<rz>.mca`. 헤더 8KB(4KB 위치테이블: 청크당 3B 섹터오프셋+1B 섹터수, 4KB 타임스탬프). 청크 = 4KB 섹터 정렬 `[len4][compType1][zlib NBT]`. 섹터 할당/재배치.
- 신규 `RegionFileStore`: RegionFilePos→RegionFile 핸들 캐시, 파일별 락으로 직렬화. `readChunk(cp)`/`writeChunk(cp, bytes)`.

### 3.3 Anvil 청크 코덱
- 신규 `AnvilChunkCodec`: `toNbt(ChunkColumn, cx, cz)` / `fromNbt(Nbt) → ChunkColumn`.
- 섹션: `block_states={palette:[{Name,Properties?}], data:long[]}`, `biomes`, `Y`. heightmaps(MOTION_BLOCKING 등), `Status="minecraft:full"`, `DataVersion`.
- read 팰릿: `Name`→`BlockRegistry.defaultStateId`(속성 무시). air/미지원 블록 → air(0) 폴백.
- write 팰릿: ChunkColumn state id → Name. **역매핑 필요** — 현 BlockRegistry는 name→default만. M4b write 대상은 우리가 둔 블록(superflat: bedrock/dirt/grass/air = 전부 default state) → state id→name 역맵을 default state만으로 구성(우리 블록셋 한정). 비-default state write는 근사(해당 블록 default name). 속성정확 write는 분리 마일스톤.

### 3.4 비동기 청크 로드
- `Region.loadChunk(cp)` → `CompletableFuture<ChunkColumn>`: 메모리 있으면 즉시 / 진행중이면 in-flight / 없으면 IO 풀서 `RegionFileStore.readChunk`→`AnvilChunkCodec.fromNbt`(없으면 base `worldSource.generate`) → owner 스레드서 `chunks` 저장 + 완료. in-flight dedup 맵.
- `World.snapshotChunk`(M4a)의 chunkAt → loadChunk로 교체(future 체인). `breakBlock`은 로드된 청크 대상이라 sync `chunkAt` 유지(없으면 generate 폴백).
- `WorldSource`에 Anvil 구현 `AnvilWorldSource`(RegionFileStore + base generator 폴백). FurnaceMain 배선.

### 3.5 dirty + 퇴출 + 저장
- `ChunkColumn.dirty`(boolean). `setBlock`/`breakBlock` → dirty=true. 로드/생성 직후 clean. 저장 후 clear.
- **퇴출:** owner region 틱서 자기 청크 스캔 — `world.subscribers(cp)` 0이면 grace 카운터 증가, ≥N틱이면 퇴출(dirty면 저장 후, clean이면 즉시 drop). 구독 생기면 카운터 리셋.
- **저장 트리거:** 퇴출(dirty) · 주기 autosave(`autosave-ticks`마다 로드된 dirty flush, 유지) · 종료(전체).
- 저장 = owner 스레드서 `ChunkSnapshot` 떠 IO 풀서 `AnvilChunkCodec.toNbt`+`RegionFileStore.writeChunk`(스냅샷이라 region 변형과 무경쟁).

### 3.6 플레이어 위치 영속
- `world/playerdata/<uuid>.dat`(gzip NBT). compound: `Pos`(double list ×3), `Rotation`(float list ×2), `OnGround`(byte). M4c 확장.
- **접속:** play 진입 시 IO 풀서 playerdata read → 있으면 저장 위치, 없으면 기본(0,-60,0) → SyncPlayerPosition. 읽기 완료 후 스폰.
- **저장:** disconnect(Region.tick disconnected 분기서 권위 위치 IO write) + 종료(접속 전원).

### 3.7 종료 시퀀스
- FurnaceMain graceful stop: 수신 중단 → 각 region 스레드서 dirty 청크 flush(await) → 플레이어 저장 → RegionFile 닫기.

## 4. 데이터 흐름 / 동시성

- **로드:** region → loadChunk → IO 풀(disk read+decode) → owner 스레드 저장. 단일 owner 불변(M4a).
- **저장:** owner 스레드 스냅샷 → IO 풀 write. region 변형과 스냅샷 격리.
- **.mca 공유:** `RegionFileStore` 파일별 락 — 여러 region 동시 write 안전.
- **플레이어:** uuid별 파일, 공유 없음.

## 5. 설정

- `level-name`(기본 "world") — 월드 디렉토리.
- `io-threads`(기본 4) — 디스크 IO 풀.
- `autosave-ticks`(기본 6000 = 5분) — 주기 flush 간격.
- `chunk-eviction-grace-ticks`(기본 100 = 5초) — 구독 0 후 퇴출 유예.

## 6. 컴포넌트 변경 요약

| 파일 | 변경 |
|---|---|
| `io/nbt/Nbt.java` 등 | (create) NBT 태그 트리 |
| `io/nbt/NbtReader`/`NbtFileWriter` | (create) 파일 NBT read/write |
| `world/anvil/RegionFile.java` | (create) .mca 섹터 read/write |
| `world/anvil/RegionFileStore.java` | (create) 파일별 직렬화 핸들 캐시 |
| `world/anvil/AnvilChunkCodec.java` | (create) ChunkColumn ↔ NBT |
| `world/gen/AnvilWorldSource.java` | (create) WorldSource(디스크+generate 폴백) |
| `world/ChunkColumn.java` | (modify) dirty 플래그 |
| `world/Region.java` | (modify) loadChunk async, 퇴출/저장, 플레이어 저장 |
| `world/World.java` | (modify) snapshotChunk→loadChunk, IO 풀, RegionFileStore 보유 |
| `entity/Player.java` | (modify) playerdata 직렬화 헬퍼(위치) |
| `net/ConnectionHandler.java` | (modify) 접속 시 playerdata read→스폰 |
| `FurnaceMain.java` | (modify) 배선 + 종료 시퀀스 |
| `config/Config`·`ConfigLoader` | (modify) level-name/io-threads/autosave-ticks/eviction-grace |

## 7. 테스트

**JUnit:**
- `NbtCodecTest`: 전 태그종 write→read 라운드트립.
- `RegionFileTest`: (cx,cz) 바이트 write→read 일치, 섹터 할당, 헤더 오프셋.
- `AnvilChunkCodecTest`: ChunkColumn→NBT→ChunkColumn 블록 보존; 합성 바닐라 NBT(Name 팰릿) read→default id.
- `ChunkLifecycleTest`: loadChunk 부재 시 generate; break→dirty; 퇴출 시 dirty 저장; 재로드서 air 유지.
- `PlayerDataTest`: 위치 write→read 일치.
- 회귀: 기존 M4a 테스트(snapshotChunk/breakBlock) — async 로드 경유로도 통과.

**수동 verify:**
- (A) superflat 기동 → 블록 파괴 → **서버 재시작** → 재접속 시 블록 여전히 깸 + **마지막 위치 스폰**.
- (B) 바닐라 1.21 월드 디렉토리 배치 → 기동 → 진짜 지형 렌더(속성 근사: 방향성 블록 기본값). 샘플 월드 = build server.jar 생성 or 사용자 제공.
- 주기 autosave: 편집 후 재시작 안 하고도 .mca 갱신 확인(파일 mtime/내용).

## 8. 위험 / 향후

- **위험:** .mca 바이너리 포맷 오류 → 손상/킥(RegionFileTest로 격리). zlib 압축 호환. 음수 좌표 섹터 인덱싱. 종료 중 IO 미완 → 유실(flush await 필수).
- **향후:** 팰릿 속성 정확(별도 마일스톤) · 청크 generate 진짜 지형(노이즈, 로드맵 밖) · 영역 비동기 저장 최적화 · M4c playerdata 확장(인벤/체력).
