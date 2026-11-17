# T-219 기본 세 월드를 생성기·바이옴 공급자 플러그인으로 (2026-11-17)

T-217(`compat-t217-chunk-generator-worlds-2026-11-17.md`)·T-210 의 뒤. 결정 G2(`docs/user-todo/entries/2026-11-17-t217-chunk-generator.md`)의
추천안 — `furnace.properties` 에 같은 뜻의 설정을 두고, 그 플러그인이 켜질 때까지 기본 월드의 생성기 결정을 미룬다 — 을 적용한다.

## 1. Paper 1.21.1 이 하는 일

- 설정: `bukkit.yml` 의 `worlds.<월드 이름>.generator: 플러그인[:id]`, `worlds.<월드 이름>.biome-provider: 플러그인[:id]`.
  월드 이름은 `level-name`(오버월드), `<level-name>_nether`, `<level-name>_the_end`(CraftBukkit `MinecraftServer#loadWorld0` 의
  `name = levelName + "_" + Environment 소문자` — 기억 기준, 가정).
- 순서(`MinecraftServer` 시작): 모든 플러그인 `onLoad` → `load: STARTUP` 플러그인 `onEnable` → 월드마다 `getGenerator(이름)`·`getBiomeProvider(이름)`
  → 공급자가 없고 생성기가 있으면 `generator.getDefaultBiomeProvider(worldInfo)` → 월드 만들기(`initWorld`: 기본 BlockPopulator → WorldInitEvent →
  새 월드면 스폰: `getFixedSpawnLocation`, 없으면 바닐라 찾기) → 스폰 청크(prepareLevels) → WorldLoadEvent → `POSTWORLD` 플러그인 `onEnable`.
- `CraftServer#getGenerator(String)`·`getBiomeProvider(String)`(CraftBukkit 원문 확인): 이름을 `:` 로 한 번 나눠 플러그인 이름과 id. 플러그인이 없으면
  `Could not set generator for default world '<이름>': Plugin '<이름>' does not exist`, 켜지지 않았으면
  `... Plugin '<전체 이름>' is not enabled yet (is it load:STARTUP?)`, null 이면 `... lacks a default world generator`, 던지면 그 플러그인 로거에
  SEVERE `Could not set generator for default world '<이름>': Plugin '<전체 이름>'` + 예외. 공급자는 `biome provider` / `lacks a default world biome provider`.
  어느 경우든 그 월드는 바닐라 생성기로 열린다.
- 생성기는 저장되지 않는다 — 매 시작마다 설정에서 다시 고른다. 플러그인이 빠지면 새 청크는 바닐라다.

## 2. Furnace 설계

### 2.1 설정
- `furnace.properties`: `world-generator.overworld|the_nether|the_end=플러그인[:id]`, `world-biome-provider.<같은 셋>=플러그인[:id]`(기본 없음 = 바닐라).
  플러그인 이름은 형식과 무관하다 — 그 플러그인을 가진 형식 어댑터(Bukkit 호환 계층)가 답한다.
- Paper 설정 가져오기(`PaperConfigImport`): `bukkit.yml` `worlds.<level-name>[ _nether | _the_end ].generator|biome-provider` 를 위 키로 옮긴다.
  다른 이름의 `worlds.*` 는 "대응 없음"으로 보고서에 남긴다 — Paper 는 `CraftServer#createWorld(WorldCreator)` 에서 생성기·공급자가 비면
  `getGenerator(이름)`·`getBiomeProvider(이름)` 를 불러 **추가 월드에도** 이 설정을 쓰지만(검토에서 바이트코드 확인), Furnace 에는 이름별 설정이 없다(3장, 후속 T-222).
- 같은 차원에 `worldgen-hooks.<차원>`(Furnace 훅 키)과 함께 적으면 Furnace 훅이 이기고 생성기 설정은 경고 한 줄과 함께 무시한다(둘을 겹쳐 걸 수 없다).

### 2.2 시작 순서
Furnace 는 기본 월드를 플러그인보다 먼저 만든다(접속자·명령·플러그인 서버 객체가 월드를 쥔다). 그래서 월드 객체는 지금처럼 만들되, 설정이 있는
차원은 **생성기에 닿는 일**을 플러그인 결정 뒤로 미룬다:

1. 월드 조립(바닐라 생성기로). 설정이 있는 차원은 틱 시작(`startTicking` — 강제 로드 청크)을 미루고, 새 오버월드면 스폰 계산도 미룬다(임시 스폰).
2. 플러그인 `onLoad`(월드는 Bukkit 에 감춰져 있다 — 지금과 같다), `worlds.autoload`.
3. **`load: STARTUP` 플러그인 `onEnable`**(`PluginManagerImpl#enableBeforeWorlds`). 이때 건 Bukkit 예약 작업은 월드를 드러낼 때까지 돌지도 틱을 세지도
   않는다(`LegacyScheduler` hold — Paper 의 `CraftScheduler.mainThreadHeartbeat` 는 시작이 끝난 뒤 `tickServer` 에서만 불린다. 검토 뒤 실제 서버에서
   스폰 청크 준비가 길면 STARTUP 플러그인의 `runTaskLater` 가 감춘 월드를 만나는 것을 찾아 고침).
4. **기본 월드 생성기 고르기**(전역 스레드): 설정이 있는 차원마다 형식 어댑터에 묻는다(`PluginFormat#defaultWorldGeneration`). 답은 훅 객체(T-217
   `ChunkGenerationHooks`)와 바이옴 공급자(T-210 `BiomeProvider`) — 둘 다 없을 수 있다.
5. 주 스레드에서 붙이기: 공급자가 있으면 같은 설정·같은 시드의 생성기를 공급자로 다시 만들어(`NoiseWorldSource#freshCopy(시드, 공급자)` — 생성 풀 공유)
   디스크 생성기의 바탕을 바꾼다(`AnvilWorldSource#replaceBase` — 그 바탕으로 아직 한 청크도 만들지 않았을 때만, 아니면 오류 기록 후 바닐라).
   훅 객체는 `prepare` → `GenHooks.attachObject` → `attached`(Bukkit `getDefaultPopulators`). 새 오버월드면 스폰: `fixedSpawn`, 없으면 바닐라
   찾기(이제 그 생성기로) — 네더·엔드도 같은 기본 스폰을 받는다. 미뤘던 `startTicking`.
6. 스폰 청크(`prepareSpawnArea`) — Paper prepareLevels 자리(지금까지는 3 앞이었다. 모든 서버에서 STARTUP 플러그인 뒤로 옮긴다: Paper 순서).
7. 월드 드러내기(WorldInitEvent·WorldLoadEvent) → 나머지 플러그인 `onEnable`(`enableAfterWorlds`).

### 2.3 Bukkit 호환
- `CompatFormat#defaultWorldGeneration`: `CraftServer#getGenerator(이름)`·`getBiomeProvider(이름)` 와 같은 규칙·같은 문구(1장)로 플러그인을 찾고,
  `getDefaultWorldGenerator(이름, id)`·`getDefaultBiomeProvider(이름, id)` → 공급자가 없으면 `generator.getDefaultBiomeProvider(worldInfo)` →
  T-217 `BukkitChunkGenerators.adapt`·T-210 `BukkitBiomeProviders.adapt`. 그 플러그인이 이 형식의 것이 아니면 답하지 않는다(다른 어댑터 몫).
- 월드가 아직 감춰져 있어도 훅 객체의 `attached`·`fixedSpawn` 은 그 CraftWorld 를 쓴다(Paper 도 initWorld 때 월드는 목록에 있다).
- `CraftWorld#getGenerator`·`getBiomeProvider` 는 기본 월드에서도 그 생성기·공급자를 돌려준다.
- 어느 형식도 답하지 않으면(그 이름의 플러그인이 없다) 서버가 `Lang` 경고 한 줄 — Bukkit 플러그인이 하나라도 있으면 호환 계층이 Paper 문구로 답한다.

### 2.4 저장·다시 열기
- 기본 월드의 생성기는 level.dat 에 남기지 않는다(Paper 와 같다 — 매 시작마다 설정으로 고른다). T-210 E1·T-217 G1 의 "열지 않음" 규칙은 추가 월드
  몫이고, 기본 월드는 플러그인이 없으면 바닐라 생성기로 연다(Paper 와 같다).

## 3. 같음의 기준
- 설정한 기본 월드의 청크 = Paper 에서 같은 bukkit.yml·같은 플러그인으로 만든 청크(T-217 의 난수·순서 규칙 그대로).
- 받아들인 차이:
  - WorldInitEvent 가 스폰 결정 뒤에 온다(T-217 3장과 같은 까닭 — Furnace 는 월드를 먼저 만들어 둔다).
  - 플러그인 `onLoad` 에서 Furnace API 로 그 차원의 청크를 미리 만들게 하면(Bukkit 은 그때 월드가 없다) 생성기를 붙이지 못하고 바닐라로 남는다(오류 기록).
  - `allow-nether=false` 여도 네더 생성기를 묻는다(Furnace 는 네더 월드를 늘 만든다 — 묻는 횟수만 다르다).
  - bukkit.yml `worlds.<추가 월드 이름>.generator·biome-provider`(Paper 는 플러그인이 그 이름으로 생성기 없이 `createWorld` 할 때 쓴다)는
    아직 없다 — Furnace 설정 키는 기본 세 차원뿐이다. 후속 T-222(이름별 키와 가져오기).

## 4. 시험
- 서버: 설정 읽기, `replaceBase`(만든 뒤 거절), 시작 순서(가짜 형식 어댑터 — STARTUP 뒤·스폰 청크 앞에 묻고 스폰이 그 생성기를 따름).
- 호환: 실제 서버(`FurnaceTestServer`)에서 `world-generator.overworld=플러그인:id` 로 켜 STARTUP 플러그인의 생성기가 기본 월드 청크·고정 스폰·
  BlockPopulator 에 쓰이는지, 없는 플러그인·POSTWORLD 플러그인·null 생성기의 Paper 문구, 공급자만 준 경우.
- 설정 가져오기: bukkit.yml `worlds.world.generator` → `world-generator.overworld`.
