# A16 CraftBukkit 어댑터·라이브러리 로더 — 3차 전체 점검 조사 보고서 (2026-11-11)

- 기준: 브랜치 `claude/quirky-wright-lra834`, 커밋 `218a8922`(PR #228 병합 시점). 이 보고서와 함께 높음·중간 항목 A16-01 을 고친다.
- **범위(코디네이터 지시)**: 계획의 A16 중 NMS 어댑터 스레드(N0~N-lib, PR #218·#223·#226·#229)가 건드리지 않는 곳만 본다 —
  `furnace-compat-paper/.../org/bukkit/craftbukkit/*`(CraftServer·CraftWorld·CraftEntity·CraftLivingEntity·CraftPlayer·CraftItemStack·CraftOfflinePlayer)와
  `furnace-server/.../plugin/library/*`(LibraryResolver·MavenPom·ServerLibraries·SharedLibraryLoader·SpigotMappingFiles). `compat/nms`(ASM 변환·링커·대응표·덫)·
  `net/minecraft/*` shim·`CompatJars`·`PluginBootstrapper` 의 변환·로더 배선은 어댑터 스레드 몫이라 아래 "겹치는 곳"에 관찰만 적었다.
- 재현·수정 시험: `furnace-compat-paper/src/test/java/org/domi/furnace/compat/paper/HeldEntityLiveStateTest.java`(기준 커밋에서 4개 중 **3개 실패**, 수정 뒤 통과),
  `furnace-server/.../plugin/EntityStateApiTest#locationHealthAndRemoveAreLiveOnTheOwnerThread`.

## 요약

| 심각도 | 수 | 항목 |
|---|---|---|
| 치명 | 0 | |
| 높음 | 0 | |
| 중간 | 1 | A16-01(수정함) |
| 낮음 | 4 | A16-02 ~ A16-05(고치지 않음, 아래 제안) |
| 참고 | 3 | A16-06 ~ A16-08 |

## 조사한 범위

- **고정 sha256 전수 대조**: `ServerLibraries.DEFAULT` 의 27개 jar 중 Maven Central 의 24개를 직접 내려받아 sha256 을 다시 쟀다 — **24개 모두 코드의 값과 일치**
  (Guava 32.1.2-jre·failureaccess·Adventure 13개·SnakeYAML 2.2·bungeecord-chat·json-simple·JOML·fastutil·commons-lang3·commons-io·log4j-api·log4j-to-slf4j).
  Mojang 저장소(`libraries.minecraft.net`) 3개(brigadier·authlib·DataFixerUpper)는 이 컨테이너 프록시가 403 이라 **못 쟀다**(바닐라 서버 jar 의 `META-INF/libraries.list` 에서
  옮긴 값이라는 코드 주석을 믿는다 — 사용자 확인 항목 아님, 다음 환경에서 같은 방법으로 쟤 보면 된다).
- **`LibraryResolver`**: 체크섬 우선순위(설명 파일 > 잠금 > `.sha1`)·`require-checksum`·오프라인·캐시 해시 재확인·원자적 쓰기·상한(`maxSizeMb`·pom 4MiB)·
  HTTPS→HTTP 리다이렉트 불가(JDK `Redirect.NORMAL`)·호스트 한 번 실패하면 이번 시작에서 다시 안 감(`unreachable`)·pom 합치기(부모·BOM·속성 10회 치환·깊이 10)·버전 범위 하한.
  좌표 글자 검사(`Library` 생성자의 `[A-Za-z0-9_.+-]`·`..` 거부)가 캐시 경로 탈출을 막는다. pom 부모·BOM 좌표는 캐시를 읽은 **뒤** 같은 검사를 하지만 `/` 가 없어 캐시 밖 파일은 읽히지 않는다.
- **`MavenPom`**: 외부 엔티티·DTD 끔 + `FEATURE_SECURE_PROCESSING`(엔티티 확장 한도) — Central 이 내린 pom 만 읽으므로 충분.
- **`SharedLibraryLoader`·`ServerLibraries.scan`**: 부모 우선 순서(서버 클래스패스 → 공유 jar → 플러그인), 상수 풀·점 표기 둘 다 표지로 셈, 안쪽 jar 2단계, 이미 시도한 묶음 건너뛰기, 실패한 묶음은 경고만 하고 로더에 안 붙임.
- **`PluginBootstrapper.sharedLibrariesFor`**: 묶음 하나가 실패해도 나머지는 붙고, 종료 때 `closeSharedLibraries` 가 로더를 닫고 `sharedTried` 를 비움(같은 부트스트래퍼 재사용 시 새 로더에 다시 붙임).
- **`CraftItemStack`·`CraftOfflinePlayer`·`CraftMagicNumbers`·`CraftServer`**: CraftBukkit 원문과 한 줄씩 대조(`asNMSCopy`·`asCraftMirror`·`setAmount` 0 이면 떨어짐·`equals`/`hashCode` 97*5·
  `getPlayer(String)` 접두어 delta 규칙·`getOfflinePlayer(String)` 의 온라인 모드 캐시 조회·오프라인 UUID `OfflinePlayer:`+이름·`getBukkitVersion` `1.21.1-R0.1-SNAPSHOT`) — 차이 없음.
- **`CraftEntity`·`CraftLivingEntity`·`CraftWorld`·`CraftPlayer`**: 아래 A16-01~05.

## 항목

| ID | 심각도 | 종류 | 한 줄 | 상태 |
|---|---|---|---|---|
| A16-01 | 중간 | shim 값이 서버 상태와 어긋남 | 몹·탈것 등 플레이어가 아닌 엔티티의 `getLocation`·`getWorld`·`getHealth`·`getMaxHealth`·`isValid`·`isDead` 가 핸들을 만든 시점 값이고, `setHealth`·`remove`·`teleport` 가 비동기라 바로 뒤에 읽으면 옛 값 | **수정함**(아래) |
| A16-02 | 낮음 | Paper 차이 | `World#getNearbyEntities(Location,x,y,z)`·`Entity#getNearbyEntities` 가 엔티티 **중심점**으로 상자 안을 가른다. Paper 는 엔티티 경계 상자가 겹치면 포함 | 제안 |
| A16-03 | 낮음 | Paper 차이 | `Server#broadcastMessage` 반환값이 접속자 수. Paper 는 `bukkit.broadcast.user` 를 구독한 발신자 수라 콘솔이 있으면 +1(원문 대조는 못 함 — 기억에 기댄 가정) | 제안 |
| A16-04 | 낮음 | 호환(플러그인 차원) | `CraftWorld#getMinHeight/getMaxHeight` 가 플러그인이 만든 차원 타입이면 `UnsupportedFeature` 를 던진다(WorldEdit·FAWE 류가 시작 때 부르는 흔한 호출) | 제안 |
| A16-05 | 낮음 | 견고성 | `LibraryResolver.fetch` 가 호스트 한 곳에서 `IOException` 한 번(읽기 시간 초과·중단 포함)만 나도 그 호스트를 이번 시작 내내 "닿지 않음" 으로 표시해, 뒤따르는 모든 플러그인의 라이브러리가 조용히 실패 | 제안 |
| A16-06 | 참고 | 설계 | `.sha1` 검증은 같은 서버가 준 값이라 변조 방어가 아니라 전송 오류 검사다. 잠금 파일(처음 받을 때 sha256 기록)이 실질적인 방어 — 문서에 이미 있음 | 없음 |
| A16-07 | 참고 | 겹침 | NMS shim(`net.minecraft.world.entity.Entity#getX/getY/getZ·isRemoved·isAlive`)은 `bukkit.furnaceHandle()` 의 스냅샷을 읽는다. A16-01 과 같은 어긋남이 NMS 쪽에도 있다 | 어댑터 스레드에 넘김 |
| A16-08 | 참고 | 환경 | 이 컨테이너에서 Mojang 저장소·Spigot 허브 접근 불가라 Mojang 3개 jar 해시와 Spigot 원문(`CraftServer#broadcastMessage` 등) 대조를 못 했다 | — |

### A16-01 상세 (중간, 수정함)

- **문제**: Furnace 엔티티 핸들(`EntityHandle`)은 만든 순간의 위치·체력·생사를 `final` 필드로 든다("핸들 시점 값"). `CraftEntity#getLocation`·`isValid`·`CraftLivingEntity#getHealth` 는 그 필드를 그대로 돌려 줬다.
  플러그인이 `Entity`/`LivingEntity` 를 변수·맵에 쥐고 있다가 나중에 읽는 것은 아주 흔하다(`BukkitRunnable` 안의 몹, 보스·펫 플러그인, 쿨다운 맵). CraftBukkit 은 NMS 엔티티가 살아 있는 객체라 항상 지금 값이다.
  - `mob.setHealth(3); mob.getHealth()` → **10**(기대 3). `setHealth` 도 소유 스레드로 예약만 해서 바로 뒤에 읽을 수 없다.
  - `mob.teleport(to); mob.getLocation()` → 옛 위치. (`teleport` 자체는 `teleportNow` 로 곧바로 옮기지만 읽는 쪽이 스냅샷.)
  - `mob.remove(); mob.isValid()` → **true**(기대 false). `remove` 가 큐에 들어가 다음 틱에야 지운다.
  - 몹이 움직이거나 맞거나 죽어도 쥔 객체는 늘 옛 값 — 새 스냅샷이 오는 경로(`Compat.entity(e)` 로 다시 감쌀 때)에서만 갱신.
- **재현**: `HeldEntityLiveStateTest` — 수정 전 실패 메시지 `setHealth 직후 getHealth 가 낡은 값을 줬다 expected 3.0 but was 10.0`, `teleport 직후 getLocation 이 낡은 위치를 줬다 expected 6.5 but was 3.5`, `remove 직후 isValid 가 계속 true 였다`.
- **수정**: 소유 region 스레드에서는 엔티티의 지금 상태를 읽고 쓴다(이미 이름·플래그·효과가 쓰는 `EntityStates` 경로).
  - API: `EntityState.location()`·`remove()`, `LivingEntityState.health()·maxHealth()·setHealth()` 추가(구현은 `EntityStateImpl` — 서버 엔티티의 `x()/y()/z()`·`health()`·`discard()`·`kill()` 를 그대로 부른다, 새 규칙 없음).
  - 어댑터: `CraftEntity` 의 `getLocation`·`getWorld`·`getNearbyEntities`·`isValid`·`isDead`·`remove`, `CraftLivingEntity` 의 `getHealth`·`getMaxHealth`·`setHealth` 가 소유 스레드면 지금 값을 쓰고,
    소유 스레드가 아니거나 상태 손잡이가 없는 플레이어(플레이어 핸들은 그 자체가 지금 값)면 예전 경로(스냅샷 읽기·예약 쓰기)를 그대로 쓴다. 사라진 엔티티의 위치는 핸들 마지막 값, `isValid` 는 false.
  - 바닐라(NMS) 규칙: `setHealth(0)` 은 죽음(`kill`), 최대 체력 초과는 최대 체력으로 자름 — 기존 `updateHealth` 와 같다.
- **남는 한계**: 다른 스레드(비동기 작업·이벤트 루프)에서 읽으면 여전히 핸들 시점 값이다 — 스레드 소유 규칙상 막을 수 없다(`docs/api` 의 "Entity#location 은 만든 시점"). Paper 가 비동기 스레드의 엔티티 읽기를 허용하지 않는 것과 같은 선이다.

### A16-02 ~ A16-05 제안 (고치지 않음)

- **A16-02** 경계 상자 겹침: API 에 엔티티 크기가 없다(`Entity` 에 `boundingBox` 없음) — 크기 노출(Paper `getBoundingBox`)과 함께 고치는 것이 맞다. Sonnet medium, 후속.
- **A16-03** 반환값: Paper 원문을 못 읽어 확정을 미룬다. 확인되면 한 줄(콘솔 구독자 포함) — Haiku low.
- **A16-04** 차원 타입 높이: W2 가 플러그인 차원 타입을 등록하므로 `dimensionType` 의 높이를 API 로 노출하고(이미 서버는 안다) 어댑터가 읽게. Sonnet medium. 사용자 결정 불필요.
- **A16-05** `Unreachable` 판정을 "연결·이름 풀이 실패(`ConnectException`·`UnknownHostException`·`HttpConnectTimeoutException`)"로 좁히고 읽기 시간 초과·중단은 그 파일만 실패로. Sonnet medium.

## 겹치는 곳(어댑터 스레드 몫 — 관찰만)

- `PluginBootstrapper.sharedLibrariesFor`·`CompatJars`·`CompatFormat` 의 라이브러리 배선은 PR #229(N-lib-1)가 고치는 중이라 건드리지 않았다. 읽기만: 묶음 한 개 준비에 실패하면 그 묶음의 클래스를 쓰는 호환 플러그인은 경고 뒤 `NoClassDefFoundError` 로 늦게 실패한다 —
  의도한 설계(문서·경고 문장에 명시)이며 결정 5(필요 시 내려받기)와 맞는다.
- NMS shim 의 위치·생사(A16-07)는 `net.minecraft.world.entity.Entity` 가 `bukkit.furnaceHandle()` 대신 `bukkit.getLocation()`·`bukkit.isValid()` 를 부르게 하면 A16-01 의 이득을 그대로 받는다(한 줄씩, N 스레드가 다음 묶음에서).

## 같음 확인 (다시 볼 필요 없는 것)

- 서버 제공 라이브러리 24개 고정 해시(Central) — 위 대조.
- `CraftItemStack` 거울 규칙·`CraftOfflinePlayer` 동등성·`CraftServer#getPlayer` 접두어 규칙·오프라인 UUID.
- `LibraryResolver` 의 경로 탈출 방어·크기 상한·원자적 저장·잠금 파일 불일치 거부.
