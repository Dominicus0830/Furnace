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
- 이미 올라온 이름으로 다시 만들면 이벤트 없이 그 월드다. 예전에는 같은 설명서로 다시 부르면 `WorldLoadEvent` 가 또 갔다(Paper 는 그 월드를 돌려줄 뿐).

## Paper 와 같게 한 것

- 같은 이름이면 설정이 달라도 그 월드(Paper 는 `getWorld(name)` 먼저). `load: STARTUP` 켜기 중이면 `IllegalStateException("Cannot create additional worlds on STARTUP")`.
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
- 폴더가 파일이면 Paper 는 `IllegalArgumentException("File (%s) exists and isn't a folder")`, Furnace 는 폴더 잠금 단계의 예외다(문구 다름).
