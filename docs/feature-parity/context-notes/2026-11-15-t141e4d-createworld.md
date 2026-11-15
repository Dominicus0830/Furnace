# T-141 E4d — Bukkit createWorld·WorldCreator·WorldType (2026-11-15)

기준: Paper 1.21.1(33ff3dff 패치) `CraftServer#createWorld`, `WorldCreator`, `WorldType`, "Throw exception on world create while being ticked"(검사는 Paper 에서도 주석 처리됨).

## 동기 호출을 Furnace 비동기 월드 만들기 위에

- Bukkit `createWorld` 는 동기다. Furnace `createWorld` 는 월드 스레드에서 준비하고 **전역 스레드**에서 `WorldLoadEvent` 를 보낸 뒤 끝나는 future 다.
  호환 계층이 전역 스레드(명령·동기 작업)에서 그 future 를 기다리면 서로 기다린다 → 새 API `Furnace#createWorldNow`.
  - 전역 스레드에서 부르면 월드 스레드 결과만 기다리고 이벤트는 그 자리에서 보낸다(Paper 주 스레드처럼 그동안 전역 틱이 멈춘다).
  - 다른 스레드(서버 시작의 onEnable 스레드, region 스레드)면 `GlobalWait` 표시를 달고 전역 완료까지 기다린다.
  - 함정: 월드 스레드에 앞서 줄 선 **내리기**가 전역 스레드의 플레이어 확인을 기다리는 중이면, 막힌 전역 스레드와 서로 기다린다(예전 길이면 내리기가 30초 뒤 시간 초과).
    `WorldManager.onGlobal` 이 그 일을 전역 대기열과 "도움 대기열" 둘에 넣고(FutureTask 라 한 번만 돈다), `createAndWait` 의 기다림 고리가 도움 대기열을 비운다.
    `MultiWorldTest.createWorldNowOnGlobalHelpsPendingUnload` 가 잡는다(도움을 끄면 TimeoutException 으로 실패하는 것을 확인).
- 함정(검토 R1): `WorldUnloadEvent` 처리기는 월드 스레드가 `onGlobal` 로 기다리는 일 안에서 돈다. 그 안에서 `createWorldNow` 를 부르면 처리기는 월드 스레드를,
  월드 스레드는 처리기를 기다려 30초 멈췄고, 시간 초과 뒤 늦게 돈 몸체가 목록에서만 떼어 반쯤 내린 월드가 남았다. 이제 그 안의 동기 만들기는 곧바로
  `IllegalStateException`(future·다음 틱 안내)이고, 시간 초과 때 아직 안 돈 일은 취소, 이미 도는 몸체는 상태 CAS 로 떼기를 포기한다.
  Paper 는 모두 주 스레드라 처리기 안에서도 바로 된다 — 다른 점. `CreateWorldNowConcurrencyTest.r1UnloadHandlerCreatingWorldNow`.
- 도움 대기열 덕에 `createWorldNow` 를 기다리는 동안 다른 월드의 `WorldUnloadEvent` 처리기가 끼어 돌 수 있다(Paper 에선 없는 순서, 교착보다 낫다).
- 이미 올라온 이름으로 다시 만들면 이벤트 없이 그 월드다. 예전에는 같은 설명서로 다시 부르면 `WorldLoadEvent` 가 또 갔다(Paper 는 그 월드를 돌려줄 뿐).

## Paper 와 같게 한 것

- 같은 이름(대소문자 무시 — Paper `getWorld` 는 소문자로 찾는다)이면 설정이 달라도 그 월드. 이름이나 키 중 하나만 겹치거나 둘이 다른 월드면
  `IllegalArgumentException("Cannot create a world with key … and name … one (or both) already match a world that exists")`(Paper Expand-world-key-API).
- 폴더에 level.dat 이 있으면 저장된 생성기·구조물·시드·평지 층을 쓰고 키·환경만 설명서 값이다(Paper 는 level.dat 의 worldgen 설정). Furnace 가 만든 폴더는
  저장된 설명서, Paper·Spigot·Multiverse 폴더는 `BukkitWorldImport` 추론(`Furnace#storedWorldCreator`). 환경이 저장된 것과 다르면 그 환경의 바닐라 생성기. `load: STARTUP` 켜기 중이면 `IllegalStateException("Cannot create additional worlds on STARTUP")`.
- `CUSTOM` 환경은 `IllegalArgumentException("Illegal dimension (CUSTOM)")`.
- `type` 은 오버월드 생성기에만 쓰인다(Paper `WorldDimensionData.create` 는 오버월드 줄기만 바꾸고 네더·엔드는 바닐라 줄기). 네더 환경 + FLAT = 바닐라 네더.
- FLAT `generatorSettings`: 바닐라 평지 JSON. `layers` 가 없으면(빈 `{}` 포함) 코덱이 실패해 프리셋 기본 평지 — Paper 는 오류 로그, Furnace 는 경고 한 줄.
  JSON 이 아니면 Paper 는 `GsonHelper.parse` 예외, Furnace 는 `IllegalArgumentException`.
- 키: `new WorldCreator(name)` 은 주 월드 이름·`_nether`·`_the_end` 면 바닐라 키, 아니면 `minecraft:<소문자, 빈칸은 _>`.
- `WorldInitEvent` → `WorldLoadEvent` 순서(Paper `initWorld` → `loadWorld`). 서버 API 에 `WorldInitEvent` 를 새로 두고 추가 월드마다 보낸다.
  기동 때 감춘 사이 올라온 `worlds.autoload` 월드도 드러낼 때 Init·Load 를 한 번씩.
- `World#getWorldType`(평지면 FLAT, 아니면 NORMAL — 큰 바이옴·증폭도 NORMAL), `canGenerateStructures`, `isHardcore`, `getGenerator`(늘 null) — `WorldCreator#copy(World)` 가 쓴다.

## 다른 점(가정·남은 일)

- Bukkit `ChunkGenerator`·`BiomeProvider` 로 만드는 길이 없다 → `UnsupportedFeature`(이유와 Furnace 생성 훅 안내). `ChunkGenerator` 는 넓힌 클래스가 올라오도록 자리만 둔다. 후속 후보.
- `bukkit.yml` 의 `worlds.<이름>.generator` 를 보지 않는다(Paper `getGenerator(name)`).
- `hardcore(true)` 는 서버 값과 다르면 무시하고 경고 한 번(추가 월드는 주 월드 level.dat 값).
- `keepSpawnLoaded(TriState)` 는 Adventure 가 호환 계층에 없어 메서드가 없다. 옛 `keepSpawnInMemory(boolean)` 만 있다. 추가 월드는 원래 스폰 청크를 붙잡지 않는다(Paper 는 정하지 않으면 게임 규칙 반경만큼 붙잡음) — 기존 차이.
- 평지 `features`·`lakes` 는 아직 만들지 않는다(경고 한 번). `structure_overrides` 는 읽지 않는다(층을 준 설정은 Furnace 평지가 구조물을 놓지 않는다).
- 월드 이름은 서버 규칙 `[A-Za-z0-9_.-]{1,64}` 라 `new WorldCreator("My World")` 는 `IllegalArgumentException` 이다. Paper 는 폴더 `My World`, 키 `minecraft:my_world`. 빈칸 허용은 후속 후보.
- 네더·엔드 환경 + `FLAT`: 지형은 바닐라 네더·엔드로 같지만, Paper 는 그 월드 levelData 가 평지라 `getWorldType()`=FLAT·접속 is_flat=true(기억 기반, 확인 필요), Furnace 는 NORMAL·false.
- `WorldInitEvent` 시점: Paper 는 `initWorld` 첫머리(스폰 정하기·prepareLevels 전), Furnace 는 월드가 이미 틱을 도는 뒤(비동기 `createWorld` 면 몇 틱 뒤).
- `createWorld(null)` 을 STARTUP 중에 부르면 Paper 는 STARTUP 예외, Furnace 는 null 예외(검사 순서). Paper 는 type 과 무관하게 `generatorSettings` 를 JSON 으로 파싱한다(NORMAL + 잘못된 JSON 은 Paper 만 예외).
- 폴더가 파일이면 Paper 는 `IllegalArgumentException("File (%s) exists and isn't a folder")`, Furnace 는 폴더 잠금 단계의 예외다(문구 다름).
