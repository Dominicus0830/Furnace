# T-141 물결 1 — B: World 시간·날씨·스폰·폴더·엔티티 목록·아이템 떨구기 (2026-11-14)

FAWE 2.16.2 가 부르는 World·Chunk·Server 멤버 중 서버 상태 읽기·쓰기 몫. 기준은 Paper 1.21.1(Youer 1.21.1 사본의 CraftWorld + Paper 33ff3dff 패치).
**Paper main 은 1.21.1 기준으로 쓰지 않는다**(예: main 의 Material.isSolid 는 태그 기반으로 바뀌었다 — PR #366 검토).

## Paper 1.21.1 동작과 Furnace 구현
- 시간: `getTime = getFullTime % 24000`(음수 보정), `setTime` 은 차이를 24000 나머지로 더해 **앞으로만**, `setFullTime` 은 dayTime 을 바꾸고 그 월드 접속자에게
  `set_time` 을 곧바로 보낸다. Paper 는 TimeSkipEvent(CUSTOM)를 먼저 부른다 — Furnace 에는 그 이벤트가 아직 없다(T-040b 이벤트 물결).
- 날씨: `hasStorm/isThundering` 은 비·천둥 **깃발**(세기 아님). `setStorm(b)` = 깃발 + rainTime 0 + clearWeatherTime 0, `setThundering(b)` = 깃발 + thunderTime 0 + clear 0.
  남은 시간 0 이면 다음 전역 틱의 날씨 순환(WorldClock.advanceWeatherCycle)이 바닐라 구간에서 새로 뽑는다. Paper 는 WeatherChangeEvent·ThunderChangeEvent(취소 가능)를 부른다 — 아직 없음.
- 스폰: `getSpawnLocation` = 공유 스폰 블록 좌표(가운데 아님)·각도, pitch 0. `setSpawnLocation(x,y,z,angle)` = ServerLevel.setDefaultSpawnPos(스폰 청크 티켓 옮김, 모든 접속자에게 알림).
  다른 월드의 Location 이면 false. Paper 는 SpawnChangeEvent — 아직 없음.
- 시계 공유: Furnace 는 모든 차원이 오버월드 시계를 쓴다(World.shareServerState). 오버월드가 아닌 월드의 시간·날씨 쓰기는 무시하고 경고 한 번(결정 T141-D1).
  네더·엔드 스폰도 오버월드 것(ServerCore.setWorldSpawn 이 따라 옮김) — false. 추가 월드 스폰은 자기 것이고 저장 때 level.dat 으로 옮긴다(WorldManager.copySpawn).
- 폴더: 바닐라 DimensionType.getStorageFolder 배치(오버월드 = 월드 폴더, 네더 DIM-1, 엔드 DIM1, 플러그인 차원 dimensions/ns/path).
  추가 월드도 청크 저장소와 같은 폴더다: 오버월드 환경 = `<이름>/`, 네더 환경 = `<이름>/DIM-1`, 엔드 환경 = `<이름>/DIM1`
  (CraftBukkit getDimensionPath = getStorageFolder(레벨 폴더, 차원 종류) — FAWE 등은 이 아래 `region/` 을 읽는다. PR #369 검토 R2).
  Paper 는 world_nether/DIM-1 처럼 월드마다 폴더가 따로라 경로 문자열은 다르다(배치가 다른 것, 동작 차이 아님).
- 엔티티 목록: `getEntities` 는 서버 엔티티 색인(동시 맵) 스냅숏 + 그 월드 접속자, Paper 처럼 isValid 로 거른다(지워졌거나 체력 0 인 몹·죽은 플레이어 빠짐) — **아무 스레드**(Folia 와 같은 규칙: 목록은 어디서나, 엔티티 조작은 소유 스레드).
  `getLivingEntities`·`getEntitiesByClass`·`getEntitiesByClasses` 는 그 위의 거르기.
- `Chunk#getEntities`: 엔티티 **위치**가 그 청크 안이고 isValid 인 것(Paper ChunkEntitySlices.getChunkEntities 와 같음). 소유 region 스레드(또는 전역 스레드의 빌림 구간)에서만 — 아니면
  ThreadOwnershipException(getNearbyEntities 와 같은 규칙). 로드되지 않은 청크는 빈 배열(Paper 는 불러온다).
- `dropItem`: ItemEntity(바닐라 생성자 — 무작위 속도) + 줍기 지연 10 + consumer(넣기 전) + addFreshEntity(CUSTOM). `dropItemNaturally` 는 월드 난수 nextFloat 셋으로
  축마다 +0.25~0.75 뒤 dropItem. 소유 region 스레드 전용.
- `Server/Bukkit.getCurrentTick` = 서버 틱 번호(바닐라 tickCount, 0 부터 틱 머리에서 오름, Paper 의 currentTick 과 같은 뜻). `shutdown` = /stop.

## 이번에 넣지 않은 것(물결 1-C 이후)
- `getChunkAtAsync`·`refreshChunk`(청크 적재·재전송 경로), `setKeepSpawnInMemory`(게임 규칙 쓰기 API 가 먼저 — W2 GameRule), `isDayTime`.
- 시간·날씨·스폰 이벤트(TimeSkip·WeatherChange·ThunderChange·SpawnChange) — 이벤트 물결 T-040b.
