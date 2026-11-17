# T-203 FAWE `//regen` 지원 설계 (2026-11-17)

로드맵 T-203 의 첫 묶음. 범위는 `//regen`(시드·바이옴 선택 포함)이다. 같은 T-203 의 `//forest`·`//feature`·엔티티 복사·`simulateItemUse` 는 쓰는 NMS 표면이 달라 따로 한다.

## 1. FAWE 2.16.2 가 하는 일 (소스·`javap` 로 확인)

`PaperweightFaweAdapter#regenerate` → `PaperweightRegen#regenerate()`(상위 `Regenerator`).

1. `prepare`: `((CraftWorld) w).getHandle()` 로 원래 레벨, 시드 = 선택 시드 또는 `getSeed()`.
2. `initNewWorld`(주 스레드에서 `QueueHandler#sync`):
   - 임시 폴더 → `LevelStorageSource.createDefault(dir).createAccess("faweregentempworld", LevelStem 키)`.
   - 원래 `serverLevelData`(PrimaryLevelData 필드)에서 `worldGenOptions()`(시드를 바꾸면 `withSeed`)·`settings`(필드)의 게임 모드·하드코어·난이도·명령·게임 규칙·데이터 설정으로 새 `LevelSettings`·`PrimaryLevelData(…, SpecialWorldProperty, Lifecycle.stable())`.
   - **`new ServerLevel(15 인자){…}`** 익명 하위 클래스: 서버·`server.executor`·저장소·새 레벨 데이터·**원래 차원 키**·`new LevelStem(원래 dimensionTypeRegistration(), 원래 getChunkSource().getGenerator())`·빈 진행 듣기(`ChunkProgressListener` 구현)·`isDebug`·시드·빈 스포너 목록·false·`getRandomSequences()`·환경·원래 Bukkit 생성기·바이옴 공급자(바이옴을 고르면 하나만 돌려주는 `BiomeProvider`, 아니면 원래 월드 것). `getUncachedNoiseBiome`·`save(3)`·`save(4)` 재정의(저장 안 함).
   - `noSave = true`, `CraftServer` 의 `worlds` 표에서 `"faweregentempworld"` 를 지움(리플렉션 `Map#remove`), `checkName(원래 이름)`, `Level#paperConfig` 필드가 있으면 원래 설정을 넣는다.
   - 정적 초기화: `CraftServer.class.getDeclaredField("worlds")`, `NoiseBasedChunkGenerator.class.getDeclaredField("settings")` — **없으면 클래스 초기화가 실패**한다.
3. `copyToWorld`: 매 틱 주 스레드에서 `getChunkSource().pollTask()` 를 10 ms 동안 부르고, `new ChunkCache<>(BukkitAdapter.adapt(freshWorld.getWorld()))` 를 원천으로 `target.setBlocks(region, pattern)`. 무늬는 칸마다 원천 청크의 `getFullBlock`(블록 + 블록 엔티티 NBT)을 넣고, `-b`·바이옴 선택이면 바이옴도 넣는다. **엔티티는 옮기지 않는다**(임시 월드 생성 때 생긴 동물·구조물 엔티티는 버려진다).
4. `cleanup`: `session.close()`, 주 스레드에서 `getChunkSource().getDataStorage().cache.clear()`·`getChunkSource().close(false)`, 표에서 다시 지우기, 임시 폴더 지우기.

원천 청크는 평소 FAWE 길(`PaperweightGetBlocks` → `ensureLoaded` → `CraftWorld#getChunkAtAsync(x, z, true, true)` + FAWE 티켓, 섹션·블록 엔티티 읽기)로 읽는다. 이 길은 이미 Furnace 에서 돈다(N2a~T-198).

Paper 의 임시 레벨은 `MinecraftServer#levels` 에 들어가지 않아 **틱을 돌지 않는다** — 무작위 틱·예약 틱·엔티티가 돌지 않은, 생성이 막 끝난(FULL) 청크를 그대로 읽는다.

## 2. 설계: 서버 "임시 월드"(scratch world)

### 2.1 서버 쪽

- `WorldSource#freshCopy(long seed)`: 같은 생성 설정(노이즈 설정·차원·고정 바이옴·평지 층·구조물 켬/끔·플러그인 월드젠 훅)으로 **처음 상태의** 생성기를 새로 만든다. 공유 장식 저장소(`ChunkPipeline`)도 새것이라 원래 월드에서 이미 만든 청크와 섞이지 않는다 — 같은 시드의 새 월드에서 그 청크들을 처음 만드는 것과 같다(바닐라 임시 레벨과 같은 뜻).
- `WorldManager#createScratch(World original, long seed)`: `build()` 와 같은 조립이지만
  - 원천은 Anvil 저장소 없이 생성기 그대로(디스크에 쓰지 않는다 — FAWE 의 임시 폴더는 비어 있는 채로 지워진다),
  - **틱을 켜지 않는다**(`startTicking` 안 부름 — region 은 작업 큐·청크 로드·공개 스냅샷만 처리. Paper 임시 레벨과 같다),
  - `Dimensions` 에 등록하지 않는다(접속자·명령·포털·`getWorlds` 에 안 보인다). 차원 키는 겹치지 않는 내부 키 `furnace:scratch/<n>`,
  - 자동 저장·청크 내보내기 저장 없음, 플러그인 이벤트 없음(결정 D1),
  - 시계·게임 규칙은 공유하지 않는다(틱이 없어 시간이 가지 않고, 생성 결과는 이 값들을 읽지 않는다). 엔티티 id 는 서버 전체 카운터라 따로 할 일이 없다.
- `closeScratch`: 자기 region 스레드(공유 region 풀이면 자기 차원만 뗀다)·생성 대기열 닫기, 청크 버리기(저장 안 함). 닫힌 뒤 들어온 작업은 거절. 생성 풀은 원래 월드 것을 같이 써서 닫을 것이 없다.
- 함께 고치는 것: `NoiseWorldSource` 마다 만드는 장식 레인 스레드를 월드를 내려도 닫지 않았다(추가 월드 내리기도 같은 누수). `close()` 를 부르는 대신 레인을 유휴 30초 뒤 스레드가 끝나는 풀(코어 1, `allowCoreThreadTimeOut`)로 바꿨다 — 내리는 길마다 닫기를 잊을 일이 없다. 임시 월드는 생성 풀을 원래 것과 같이 쓴다.

### 2.2 호환 쪽(NMS shim)

- `ServerLevel` 의 Paper 15 인자 생성자: 넘겨받은 `LevelStem#generator()` 가 Furnace 월드의 생성기 shim 이면 그 월드의 `freshCopy(seed)` 로 임시 월드를 만들고, 그 월드의 `CraftWorld` 를 이 객체(익명 하위 클래스 포함)에 묶는다. `dimension()` 은 넘겨받은 키를 돌려준다(Paper 와 같다). 다른 생성기(플러그인이 직접 만든 `NoiseBasedChunkGenerator` 등)나 Bukkit `ChunkGenerator` 는 `UnsupportedNmsCall`(이유를 적은 메시지).
- 새 shim: `PrimaryLevelData`(+`SpecialWorldProperty`, `settings` 필드, `worldGenOptions/checkName/getLevelName/isFlatWorld/isDebugWorld`), `LevelSettings`, `WorldOptions#withSeed`, `LevelStem`(+키 상수), `LevelStorageSource(+LevelStorageAccess)`, `ChunkProgressListener`, `util.ProgressListener`, `RandomSequences`, `DimensionDataStorage#cache`, `ChunkGenerator`/`NoiseBasedChunkGenerator#settings`(필드), `GameType`·`Difficulty`·`GameRules`·`WorldDataConfiguration`(값 운반용).
- `ServerLevel`: `serverLevelData`·`noSave` 필드, `dimensionTypeRegistration()`, `isDebug()`, `getRandomSequences()`, `getCraftServer()`, `save(…)`(임시 월드는 아무것도 안 함, 보통 월드는 서버 저장 요청). `ServerChunkCache`: `getGenerator()`, `getDataStorage()`, `pollTask()`(Furnace 는 주 스레드가 돌려 줄 일이 없어 false), `close(boolean)`(임시 월드면 닫기, 보통 월드면 `UnsupportedNmsCall`).
- `CraftServer#worlds` 표의 `remove(이름)`: 표에 없는 이름이면 `Map` 약속대로 null(지금은 예외). 있는 월드를 지우는 것은 지금처럼 거절.
- 임시 월드는 `CraftServer#worlds`·`getWorlds()` 에 처음부터 넣지 않는다(Paper 는 생성자에서 넣고 FAWE 가 곧바로 지운다 — 그 짧은 틈은 따라 하지 않는다).

### 2.3 스레드 규칙

- 생성자는 아무 스레드(FAWE 는 주 스레드=전역 스레드). 임시 월드 조립은 디스크·잠금이 없어 그 자리에서 끝난다(월드 관리자 스레드를 기다리지 않는다).
- 임시 월드 청크 읽기는 보통 월드와 같은 규칙(N2b-3 공개 스냅샷, `getChunkAtAsync`). 틱이 없어도 region 은 작업 큐와 틱 끝 공개를 돈다(`Region#runTick` 의 `gameTick=false` 길).
- 닫기는 아무 스레드. FAWE 는 주 스레드에서 부른다.

## 3. 같음의 기준과 한계

- 같은 시드·같은 생성 설정이면 임시 월드 청크 = 같은 시드의 새 월드에서 만든 청크(Furnace 월드젠 parity 범위 안). 블록 엔티티(상자 전리품 표·스포너)는 생성 때 것.
- 겹치는 장식(청크 경계를 넘는 나무 등)은 생성 순서에 따라 달라질 수 있다 — 바닐라·Paper 도 순서(스레드)에 따라 그렇다. FAWE 가 청크를 읽는 순서는 두 서버가 같다.
- 빛: 대상 월드의 빛 다시 계산은 FAWE 평소 길(T-197 몫).
- 엔티티: 옮기지 않는다(Paper 와 같다).

## 4. 단계

| 단계 | 내용 | 시험 |
|---|---|---|
| 1 | 서버 임시 월드(`freshCopy`·`createScratch`·닫기, 장식 레인 누수 고침) + 내부 다리 | 임시 월드 청크 = 같은 시드 새 월드 청크, 다른 시드는 다름, 틱 없음, 안 보임, 닫은 뒤 거절, 공유 region 풀에서 여러 스레드가 동시에 만들고 닫기(#486 검토) |
| 2 | 호환 shim(위 2.2) | FAWE 꼴 `PaperweightRegen` 사본을 **NMS 변환기로 실은 플러그인 클래스**로 돌려(T-204 교훈) 대상 월드 결과 = 새 월드 청크 |
| 3 | `//regen <바이옴>`: Bukkit `BiomeProvider` 로 생성(결정 D2) | 고정 바이옴 공급자 = 바닐라 고정 바이옴 월드, 일반 공급자 호출 순서 |
| 4 | 실제 FAWE 로 Paper 대조(클라우드에서 Paper 1.21.1-133·FAWE 2.16.2 직접 실행) | 같은 기준 월드에서 `//regen`·`//regen <시드>`·`//regen -b` 뒤 지역 파일 블록·블록 엔티티 비교, 걸린 시간 |

## 5. 검토 후속(#486, 3단계 PR 에 넣는다)

- 닫지 않은 임시 레벨 안전망: 플러그인이 닫지 않고 버린 레벨도 거둔다(약한 참조 + Cleaner 로 `closeScratch`).
- 닫을 때 진행 중 청크 불러오기를 정리한다(일반 내리기의 대기와 같은 뜻).
- 닫은 뒤 이미 받아 둔 `CraftChunk#getWorld()` 가 null 이 되지 않게.
- 임시 월드 생성 요청의 대기열 우선순위(`PRIO_NO_PLAYERS`)는 4단계 측정 때 본다.
