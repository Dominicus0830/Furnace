# A14 Furnace 플러그인 API 계약 — 2차 전체 점검 조사 보고서 (2026-11-08)

- 기준: 브랜치 `claude/quirky-wright-lra834`, 커밋 `eab009d9`(PR #180 1차 점검 병합 시점). 코드는 고치지 않았다.
- 재현 시험: `A14-repro/A14PluginLifecycleReproTest.java.txt` 하나(시험 12개). 기준 커밋에서 클래스 전체를 돌리면 **9개가 실패**하고(계약 차이 재현), 3개는 대조군·같음 확인용이라 통과한다(`control_plainPluginIsCollected`, `measure_hundredReloadsOfWellBehavedPlugin`, `a14_04b_globalFixedRateDriftsBelow20PerSecond`). `measure_*` 를 **혼자** 돌리면 실패한다(A14-09 재현). 쓰는 법: 확장자 `.txt` 를 떼고 `furnace-server/src/test/java/org/domi/furnace/server/plugin/` 에 넣고 `./gradlew :furnace-server:test --tests '*A14PluginLifecycleReproTest*'`. 플러그인은 `HotReloadTest` 처럼 시험 중에 `javax.tools` 로 컴파일한 jar 다(서버 클래스패스 밖이라 옛 클래스로더 회수를 볼 수 있다).
- 실패 시험은 실패 메시지에 **무엇이 옛 로더를 붙잡았는지**(이벤트 버스 표의 키, 경고 집합, 스폰 필터 수, 문맥 로더 스레드)를 반사로 찍는다.

## 요약

| 심각도 | 수 | 항목 |
|---|---|---|
| 치명 | 0 | |
| 높음 | 1 | A14-01 |
| 중간 | 4 | A14-02, A14-03, A14-04, A14-05 |
| 낮음 | 4 | A14-06, A14-07, A14-08, A14-09 |
| 참고 | 3 | A14-10, A14-11, A14-12 |

가장 큰 것은 A14-01 이다. `plugin.tasks()` 로 건 한 번짜리 작업은 끝나도 플러그인이 꺼질 때까지 추적 집합에 남는다. 이벤트마다 `runLater` 를 거는 흔한 플러그인은 운영 서버에서 메모리가 끝없이 는다(개발 모드와 무관).

다음은 "자동 정리" 계약이 비는 곳이다: 스케줄러를 직접 쓴 작업(A14-02)과 스폰 필터·몹 목표(A14-06)는 꺼진 뒤에도 남는다. 이벤트 버스는 플러그인이 정의한 이벤트 클래스를 키로 쥔다(A14-07). 둘 다 핫 리로드 때 옛 코드가 계속 돌거나 옛 클래스로더가 샌다.

## 조사한 범위

- **이벤트 버스**(`plugin/EventBusImpl`): 등록·해제·구운 배열(`baked`)·MONITOR 경고, 원인 기록 플래그, 꺼진 플러그인 건너뛰기. 발화 지점 전수: `furnace-api` 의 구체 이벤트 76종이 모두 서버에서 하나 이상 발화되는지 찾았다(빠진 것 없음). 발화 스레드는 이벤트 클래스 주석·`docs/api/lifecycle.md` 와 실제 발화 지점(`ConnectionHandler`·`ServerCore`·`PluginGameEvents`)을 맞춰 봤다.
- **수명·자동 정리**(`PluginManagerImpl.disable`·`reloadNow`, `FurnacePlugin.setEnabled`·`own`, `PluginTasks`): 정리 목록 17가지와 플러그인이 서버에 무언가를 맡기는 API(소유자 인자를 받는 메서드 전부)를 대조했다. 리로드 100회 측정(아래 "같음 확인").
- **스케줄러 계약**(`api/sched/TaskScheduler`·`GlobalScheduler`, `sched/GlobalThread`, `plugin/ProfiledScheduling`): 지연 단위, 지연 0, 취소, 플러그인 소유 판별. A03-08 을 여기서 이어 받았다.
- **패킷 API**(`plugin/packet/PacketServiceImpl`, `docs/api/packets.md`): 리스너 스레드(나가는 쪽 `SharedChunkFrames.encode` 는 채널 이벤트 루프에서만 불림 — `Player.writeOne`·`ConnectionHandler` 확인), MONITOR 변경 금지(`IllegalStateException`), 꺼질 때 해제.
- **취소 계약**(`docs/api/event-cancel-contract.md` 표 62행): `SERVER_DRIVEN` 으로 분류된 이벤트 중 클라이언트가 결과를 먼저 보여 줄 수 있는 것을 찾았다.
- **번역·청크 티켓·가짜 광원·가상 엔티티·뷰**: 문서의 "꺼지면 서버가 지운다" 약속이 `disable()` 에 실제로 있는지(있음).
- **정적 공유 상태**(1차 점검 C1-03 의 A14 몫): `furnace-server/.../plugin` 과 `furnace-api` 의 정적 가변 필드. 런타임에 쓰는 것은 `Translators.TRANSLATORS`·`GlobalRenderer.GLOBAL`(COW 목록), `GlobalWait.WAITING`(동시 집합), `Furnace.server`·`ItemSupport.support`(volatile)뿐이고 모두 스레드 안전하다. 나머지(`BrainValues.SHAPES`, `ItemSupportImpl.ADAPTERS`, `EntityTally.API`, `MobAiImpl.BY_KEY`, `NamedTextColor`, `PacketTypes`)는 정적 초기화에서만 채운다(쓰는 곳을 하나씩 확인).
- **API 안정성**: `furnace-api` 는 다른 모듈에 기대지 않아(`furnace-api/build.gradle.kts` 의존성은 시험용뿐) 공개 타입에 서버 내부 타입이 드러날 수 없다. `internal` 패키지는 `Internals` 로 문서화된 의도적 다리다.

## 조사하지 못한 범위 (이유)

- **뷰(K2·K3)·가상 엔티티(K1)·구조물(W4)·월드젠 훅(W3)·레지스트리(D2·W2)의 기능별 계약**: 꺼질 때 정리되는지만 봤다. 각 기능의 세부 동작(가상 엔티티가 시야를 따라 나타나고 사라지는지 등)은 해당 기능 시험(`VirtualEntitiesTest`·`PlayerViewsApiTest`·`StructuresApiTest`·`GenerationHookStoreTest`·`GameRegistryApiTest`)이 덮고 있어 다시 하지 않았다.
- **헤드리스 시험 서버(`furnace-test`) 계약**: 수동 시계·`TestPlayer` 장벽 구조만 읽었다. 시험 공백은 A19 몫.
- **실제 클라이언트 화면**: A14-05(책 고스트)는 클라이언트 동작이라 서버 시험으로 볼 수 없다. 사용자 확인으로 넘긴다(`docs/user-todo/entries/2026-11-08-audit-A14.md`).
- **Bukkit 이름으로 보이는 표면**: 계획대로 A15. 단 A14-04 는 Bukkit 어댑터 박동에도 그대로 이어지므로 A15 에 함께 넘긴다.
- H4 묶음 C·D(진행·대기 중)는 보지 않았다.

## 같음 확인 (다시 볼 필요 없는 것)

- **리로드 100회 측정**(`measure_hundredReloadsOfWellBehavedPlugin`): 자동 정리 대상 API 만 쓰는 플러그인(이벤트 구독·명령·`tasks()` 반복 작업·패킷 리스너)을 100번 다시 읽으면 옛 클래스로더 **0개**, 스레드 수 그대로(7 → 7). 시험 하나만 따로 돌리면 첫 번째 로더 하나가 남는데, 원인은 A14-09(JDK 지연 스레드)다.
- 명령·구독·패킷 리스너·번역 저장소(`server.translations()`)·청크 티켓·가짜 광원·가상 엔티티·뷰 규칙·서비스·권한 첨부·플러그인 채널·레시피·게임 규칙·난이도 프로필·마법 부여 끄기는 `disable()` 에서 모두 풀린다.
- 패킷 리스너는 문서대로 그 연결의 이벤트 루프에서만 불리고, MONITOR 가 바꾸거나 취소하면 `IllegalStateException` 이다.
- `PluginEnableEvent` 는 `onEnable` 뒤, `PluginDisableEvent` 는 `onDisable` 앞(Bukkit `JavaPluginLoader` 순서와 같다). `onEnable` 이 던지면 `onDisable` 까지 부르는 것도 Paper `enablePlugin` 과 같다.
- 전역 `runAtFixedRate(…, 1, 1)` 은 빈 작업이면 5초에 99번(`a14_04b`) — 실행 시간만큼 밀리는 "끝난 뒤 다시 걸기"지만 가벼운 작업에서는 차이가 작다. 무거운 박동(Bukkit 어댑터 `LegacyScheduler.heartbeat` 가 많은 작업을 돌릴 때)에서는 그만큼 초당 횟수가 준다 — A14-04 와 같이 고친다.

## 항목

| ID | 심각도 | 종류 | 근거 | 재현 | 제안 |
|---|---|---|---|---|---|
| A14-01 | 높음 | 성능(메모리 누수) | 아래 상세 | `a14_01_pluginTasksKeepsFinishedOneShotTasks`: 한 번짜리 `tasks().runGlobal` 2000개가 다 돈 뒤 추적 집합 크기 기대 0, 실제 **2000** | 아래 상세. 작음. Sonnet medium |
| A14-02 | 중간 | Paper/Spigot 호환·계약 | 아래 상세 | `a14_02_directSchedulerTaskKeepsRunningAfterReload`: 리로드 뒤에도 옛 플러그인의 `server().scheduler().global().runAtFixedRate` 가 `tick:v1` 을 계속 찍는다(새 것과 번갈아) | 아래 상세. 작음~중간. Sonnet high |
| A14-03 | 중간 | 문서·계약 | 아래 상세 | `a14_03_offThreadEventsReportSync`: `AsyncPlayerPreLoginEvent.isAsync()` 기대 true, 실제 **false** | 아래 상세. 작음. Sonnet medium |
| A14-04 | 중간 | 계약·Paper/Spigot 호환 | 아래 상세. A03-08 을 이어 받음 | `a14_04_globalDelayIgnoresTickRate`: `/tick rate 5` 에서 전역 `executeDelayed(…, 20)` 기대 4초 이상, 실제 **1000ms** | 아래 상세. 중간. Opus high |
| A14-05 | 중간 | 취소 계약 | 아래 상세 | 가정(클라이언트 동작). 서버 쪽은 코드로 확인: 취소하면 칸을 다시 보내지 않고 `return` | 아래 상세. 작음. Sonnet medium |
| A14-06 | 낮음 | 계약(정리 누락) | 아래 상세 | `a14_06_spawnFilterOfDisabledPluginPinsOldLoader`: 리로드 뒤 옛 로더가 회수되지 않음 — 붙잡은 것 **스폰 필터 2개**(옛 것+새 것). 몹 목표·길찾기 훅·두뇌 행동·센서는 가정(코드 읽기) | 아래 상세. 작음. Sonnet medium |
| A14-07 | 낮음 | 계약(핫 리로드 누수) | 아래 상세 | `a14_07a`(붙잡은 것: `EventBusImpl.byType` 키 `hot.a.Ping`), `a14_07b`(`baked` 키 `hot.a.Ping`), `a14_07c`(`monitorWarned` 1개) — 모두 옛 로더가 회수되지 않음. 대조군 `control_plainPluginIsCollected` 는 통과 | 아래 상세. 작음. Sonnet medium |
| A14-08 | 낮음 | Paper/Spigot 호환·API 표면 | 아래 상세 | `a14_08_commandOfSelfDisabledPluginStillRuns`: `setEnabled(false)` 로 꺼진 플러그인의 명령이 다시 돈다 | 아래 상세. 작음. Sonnet medium |
| A14-09 | 낮음 | 계약(핫 리로드 누수) | 아래 상세 | `measure_hundredReloadsOfWellBehavedPlugin` 을 **단독으로**: 첫 로더 #0 하나가 남음(문맥 로더·버스 표 아님). `-Dfurnace.a14.prewarm=true`(시험 전에 JDK 지연 실행기를 한 번 씀)면 0개 | 아래 상세. 작음. Sonnet medium |
| A14-10 | 참고 | 계약 | `PluginGoal.stop()`(`PluginGoal.java:94-100`)·`start`·`tick` 이 `!owner.isEnabled()` 면 플러그인 코드를 부르지 않는다. 플러그인이 꺼질 때 돌고 있던 목표는 바닐라 `GoalSelector` 가 `stop()` 을 불러도 `MobGoal.stop` 이 불리지 않는다 | 가정(코드 읽기) | 꺼질 때 마지막 `stop` 은 부르게 하거나(예외는 삼킴), `MobGoal` 문서에 "꺼질 때 stop 은 불리지 않는다"를 적는다. A14-06 과 함께. 작음 |
| A14-11 | 참고 | 문서 | `Translators.register(Translator)`(`Translators.java:25`)는 소유자 인자가 없는 공개 정적 등록이라 꺼질 때 풀리지 않는다. `text.md` 는 `TranslationStore.registerGlobally()` 의 같은 한계만 적었다 | 가정(코드 읽기) | `Translators.register` 에도 "직접 부르면 꺼져도 남는다 — `server.translations()` 를 쓰라"를 적는다. 작음. Haiku low |
| A14-12 | 참고 | 계약 | 전역 `executeDelayed(task, 0)`(`GlobalThread.java:133`)은 0ms 지연이라 **곧바로** 돈다. `TaskScheduler` 문서는 "0이면 다음 틱". region 스케줄러는 다음 틱(B7 기록) | 가정(코드 읽기) | A14-04 를 고칠 때 0·1 을 "다음 전역 틱"으로 같이 맞춘다 |

### A14-01 `PluginTasks` 가 끝난 한 번짜리 작업을 놓지 않는다 (높음)

- 근거: `PluginTasks.track`(`furnace-api/.../plugin/PluginTasks.java:57-60`)이 `runGlobal`·`runRegion`·`runGlobalRepeating`·`runRegionRepeating` 의 손잡이를 `ConcurrentHashMap.newKeySet()` 에 넣고, 빼는 곳은 `cancelAll()`(꺼질 때) 하나뿐이다. 손잡이 `TaskScheduler.Once`(`TaskScheduler.java:72-84`)는 작업 람다를 끝까지 쥔다 — 람다가 잡은 플레이어·아이템·이벤트 객체까지 남는다.
- 결과: 이벤트마다 `tasks().runGlobal(…, 1)`·`runRegion(…)` 을 거는 플러그인(지연 메시지, 다음 틱 되돌리기, 쿨다운 해제 등 흔한 모양)은 꺼질 때까지 손잡이가 쌓인다. 초당 100개면 하루 860만 개다. 개발 모드가 아니어도 생기고, 서버를 다시 켜기 전에는 줄지 않는다. Paper `CraftScheduler` 는 끝난 작업을 대기열에서 뺀다.
- 제안: 한 번짜리는 실행 뒤(또는 취소 때) 집합에서 뺀다. `guard` 가 감싼 실행 끝에 `tasks.remove(handle)` 하면 되는데, 손잡이가 실행보다 늦게 만들어지므로 손잡이를 먼저 만들고 넘기는 순서로 바꾼다. 반복 작업은 취소 때 뺀다. 시험: 위 재현 + "끝난 뒤 `cancelAll` 이 남은 반복 작업만 취소". 파일 `PluginTasks.java`(+ `TaskScheduler.Once` 에 끝남 콜백). 위험 낮음(API 시그니처 그대로, PATCH).

### A14-02 스케줄러를 바로 쓴 작업은 플러그인이 꺼진 뒤에도 돈다 (중간)

- 근거: `PluginTasks.global()` 주석(`PluginTasks.java:48`)은 "그렇게 만든 작업은 자동 취소되지 않는다"고 하지만, `docs/api/plugin-foundation.md` "자동 정리"는 "플러그인이 꺼지면 코어가 구독·**예약 작업**…을 해제한다", 핫 리로드 절은 "옛 클래스를 쥔 채 남는 플러그인이 없다"고 쓴다. `server().scheduler()`·`entity.scheduler()`·`player.scheduler()` 로 건 작업은 `ProfiledScheduling` 이 작업 클래스의 로더로 **소유 플러그인을 이미 안다**(`ProfiledScheduling.java:41-46`, 시간 재기에만 씀). 그런데 `MeasuredRunnable.run`(`:118-126`)은 소유자가 꺼졌는지 보지 않는다.
- 결과: 핫 리로드 뒤 옛 코드와 새 코드가 함께 돈다(재현: `tick:v1`·`tick:v2` 가 번갈아 찍힘). 옛 클래스로더도 그 작업이 쥐어 회수되지 않는다. 운영 서버에서도 다른 플러그인이 `PluginManager.disable(p)` 로 끈 플러그인의 반복 작업이 계속 돈다. Paper(Bukkit `cancelTasks(plugin)`·Folia 스케줄러 전부 플러그인 인자 필수)는 꺼질 때 모두 취소한다.
- 제안: `MeasuredRunnable`(과 `Supplier`·`Callable` 래퍼)이 실행 직전 `p.isEnabled()` 가 거짓이면 건너뛰고, 반복 작업(`TaskScheduler.Repeating`)이면 다음 예약을 하지 않게 한다. 이미 감싸는 자리라 할당이 늘지 않는다. 문서 두 곳을 "어느 스케줄러로 건 작업이든 꺼지면 더 돌지 않는다"로 맞춘다. 파일 `ProfiledScheduling.java`, `TaskScheduler.java`(Repeating 이 감싼 작업의 중지 신호를 보게), 문서. 위험: 플러그인이 `onDisable` 안에서 건 작업을 끝까지 돌리길 기대하는 경우 — `lifecycle.md` 는 이미 "끝까지 돌아야 하는 작업은 `onDisable` 안에서 기다린다"고 적었다. 작음~중간.

### A14-03 연결·가상 스레드에서 부르는 이벤트가 `isAsync()` 를 false 로 준다 (중간)

- 근거: `Event` 바탕 클래스 계약(`furnace-api/.../event/Event.java:3-6`, `docs/api/plugin-foundation.md` "이벤트 발화 스레드"): "동기 이벤트(isAsync()==false)는 그 칸·엔티티를 가진 region 스레드(또는 전역 스레드)에서 불린다." 그런데 `super(true)` 를 쓰는 이벤트는 `ServiceRegisterEvent`·`ServiceUnregisterEvent` 둘뿐이다. 아래 이벤트는 자기 주석과 `lifecycle.md` 가 region 밖에서 불린다고 적었는데 `isAsync()` 가 false 다:
  - 가상 스레드: `AsyncPlayerPreLoginEvent`, `AsyncPlayerConfigureEvent`
  - 연결(이벤트 루프) 스레드: `AsyncChatEvent`, `PlayerHandshakeEvent`, `PlayerLoginEvent`, `PlayerCreateEvent`, `PlayerSpawnLocationEvent`, `PlayerJoinEvent`(`ConnectionHandler.java:3229`), `PlayerQuitEvent`(`:4171`), `PlayerRespawnEvent`, `PlayerCommandPreprocessEvent`(채팅 명령), `PlayerChannelEvent`, `PluginMessageEvent`, `ServerListPingEvent`
- 결과: `isAsync()` 로 "월드를 바로 만져도 되나"를 가르는 플러그인(Bukkit 에서 옮겨 온 흔한 버릇)이 틀린 답을 받는다. Paper 는 `AsyncChatEvent`·`AsyncPlayerPreLoginEvent` 의 `isAsynchronous()` 가 true 다. 연결 스레드는 region 스레드도 전역 스레드도 아니어서, 바탕 클래스 계약대로라면 이 이벤트들은 모두 "비동기"다.
- 제안: 가상 스레드 두 개와 `AsyncChatEvent` 는 `super(true)`(이름이 Async 인 것, Paper 와 같게). 나머지 연결 스레드 이벤트는 (가) `super(true)` 로 바꾸거나 (나) `Event` 계약에 "세 번째 종류: 그 플레이어의 연결 스레드"를 적고 각 이벤트 문서가 그것을 가리키게 한다. (가)는 의미 변경이라 정책상 MINOR 릴리스 노트가 필요하다. 추천은 Async 이름 셋은 (가), 나머지는 (나)(Paper 에서 `PlayerJoinEvent` 는 동기라 true 로 바꾸면 옮겨 온 코드가 놀란다). Bukkit 다리 쪽 `isAsynchronous` 값은 A15 가 함께 본다. 작음.

### A14-04 전역 지연 작업은 서버 틱이 아니라 벽시계로 센다 (중간, A03-08)

- 근거: `TaskScheduler.executeDelayed` 문서(`furnace-api/.../sched/TaskScheduler.java:15-19`): "delayTicks 는 서버 틱 단위 지연이다. 0이면 다음 틱. owner 스레드가 밀리면 그만큼 늦게." 전역 구현 `GlobalThread.executeDelayed`(`sched/GlobalThread.java:123-139`)는 수동 시계가 아니면 `CompletableFuture.delayedExecutor(delayTicks * 50, MILLISECONDS)` 다. 전역 박동은 `/tick rate` 와 무관하게 50ms 이고(`docs/api/tps.md`), 서버 틱(시간·날씨)은 박동마다 `rate/20` 만큼 쌓아 낸다(`ServerCore.java:848`). 전역 지연은 이 틱 수를 보지 않는다.
- 결과: `/tick rate 5` 에서 `runLater(…, 20)` 은 바닐라 서버 기준 4초(20틱)인데 1초에 돈다(재현). `/tick freeze`·`/tick sprint`·서버 지연도 같다. Paper `runTaskLater` 는 `MinecraftServer.tickServer` 마다 한 번 도는 `mainThreadHeartbeat` 로 세므로 틱 속도를 따른다. Bukkit 어댑터(`compat/sched/LegacyScheduler.java:102`)의 박동도 전역 `runAtFixedRate(…, 1, 1)` 이라 같은 차이가 Bukkit 플러그인에 그대로 간다(A15 로 넘김). region 스케줄러는 region 틱으로 세서 속도를 따른다 — 같은 API 의 두 구현이 다르게 센다.
- 제안: 전역 지연 작업을 `ServerCore` 의 전역 틱 번호(박동마다 낸 틱 수, `globalTickCredit` 반영)로 센다. 수동 시계(`manualQueue`)가 이미 그 꼴이라 같은 우선순위 큐를 운영 경로에도 쓰면 된다. 0·1 은 다음 전역 틱(A14-12). freeze 때 전역 지연을 멈출지는 바닐라에 해당 개념이 없으니 Paper(동결 중에도 `tickServer` 는 돌고 스케줄러도 돈다)를 따른다 — 즉 "낸 틱"이 아니라 "박동 수 × rate" 로 센다. 같이 A14-09 의 JDK 지연 스레드도 없어진다. 파일 `GlobalThread.java`, `ServerCore.java`(박동에서 틱 번호 넘기기). 위험: 전역 지연에 기대는 서버 내부 코드(자동 저장 등, A03-07)의 주기가 바뀐다 — 내부용은 벽시계 경로를 따로 남길지 함께 판단. 중간. **Opus high**.

### A14-05 책 편집 취소 뒤 클라이언트 책이 고친 내용으로 남는다 (중간, 가정)

- 근거: 취소 계약 표가 `PlayerEditBookEvent` 를 `SERVER_DRIVEN`("상태가 그대로면 끝, 보낼 것 없음")으로 둔다. 서버 쪽 `ConnectionHandler.handleEditBook`(`net/ConnectionHandler.java:2528-2540`)은 취소(`edit == null`)면 곧바로 `return` 하고 칸을 다시 보내지 않는다. 그런데 바닐라 클라이언트 `BookEditScreen.saveChanges` 는 `edit_book` 패킷을 보내기 **전에** `updateLocalCopy()` 로 손에 든 책의 `writable_book_content` 를 새 쪽으로 바꾼다(클라이언트 1.20.5 이후 코드 기억 — 이 컨테이너에는 클라이언트 jar 가 없어 바이트코드로 확인하지 못했다).
- 결과(가정): 취소해도 클라이언트 책은 고친 글을 보인다. 다시 열어 저장하면 그 글이 다시 올라온다(취소가 사실상 한 번만 막는다). 계약 2번("클라이언트가 먼저 보여 줬을 수 있는 것은 코어가 다시 보낸다")에 어긋난다.
- 제안: 사용자 확인 뒤(`docs/user-todo/entries/2026-11-08-audit-A14.md` 확인 1) `CLIENT_PREDICTED` 로 옮기고 취소 때 `ClientResync.slot(player, 그 칸)` 을 보낸다. `CancelContractTest` 표·`CancelResyncE2ETest` 시험을 더한다(계약 4번 규칙). 작음. Sonnet medium.

### A14-06 스폰 필터·몹 목표·길찾기 훅은 꺼질 때 떼지 않는다 (낮음)

- 근거: `disable()`(`plugin/PluginManagerImpl.java:272-316`)의 정리 목록에 `SpawnPolicy.addFilter`(`SpawnPolicyImpl.java:71-77`), `MobAi.addGoal`·`setPathTypeHook`(`MobAiImpl.java:254-262`), `MobBrain.addBehavior`·`addSensor` 가 없다. 이들은 "쓸 때 소유자가 꺼졌으면 스스로 빠진다"(지연 정리)로 만들었다: 필터는 그 월드에서 스폰 루프가 돌 때(`NaturalSpawner.java:104`), 목표는 그 몹이 `canUse` 를 물을 때(`PluginGoal.java:44-47`), 훅은 길을 찾을 때(`MobAiImpl.java:268`) 빠진다.
- 결과: 스폰이 꺼진 월드(`doMobSpawning=false`, 평화로움, 접속자 없음)의 필터와, 틱을 받지 않는 청크(시뮬레이션 거리 밖·티켓만 걸린 청크)의 몹에 붙은 목표·훅은 계속 남아 옛 플러그인 인스턴스와 클래스로더를 쥔다(재현: 필터 2개가 남고 로더 회수 안 됨). 동작은 건너뛰므로 바닐라 결과는 같다 — 누수만이다.
- 제안: 필터는 `SpawnPolicyImpl` 이 소유자별 목록을 들고 `disable()` 에서 뗀다(티켓·광원과 같은 꼴). 몹 목표·훅은 몹 전체를 훑을 수 없으니 "소유자 → 약참조 몹 목록" 장부를 두고 꺼질 때 그 몹들의 region 에 제거 작업을 맡긴다(또는 지금 지연 정리를 그대로 두고 문서에 "틱을 받을 때 빠진다"를 적는다 — 둘 중 하나). `docs/api/mob-ai.md`·`spawn-and-villages.md` 에 정리 시점을 적는다. 작음. Sonnet medium.

### A14-07 이벤트 버스가 플러그인 이벤트 클래스를 키로 쥔다 (낮음)

- 근거(`plugin/EventBusImpl.java`):
  - `remove`(`:161-168`)는 목록에서 처리기만 빼고 **빈 목록의 키(이벤트 클래스)를 남긴다**. 플러그인이 자기 이벤트 클래스(`Ping extends Event`)를 구독했다면 그 `Class` 가 `byType` 에 남는다(재현 `a14_07a`).
  - `handlers`(`:201`)는 구독자가 없어도 발화·`hasSubscribers` 마다 `baked` 에 그 클래스 → 빈 배열을 넣는다. `baked` 는 등록이 바뀔 때만 비우므로, 처리기가 하나도 없는 플러그인이 자기 이벤트를 발화만 했다면 리로드 때 `unregisterAll` 이 아무것도 못 빼 `baked` 가 그대로 남는다(재현 `a14_07b`).
  - `monitorWarned`(`:235`, `:245`)는 경고한 `Handler`(플러그인·람다를 쥠)를 영원히 둔다(재현 `a14_07c`). `Stats.listeners`(`:336`, `-Dfurnace.tickstats` 일 때)도 같다.
- 결과: 핫 리로드 뒤 옛 클래스로더가 회수되지 않는다(개발 모드 전용, 리로드마다 하나씩 쌓임). 운영 서버의 결과는 같다.
- 제안: `remove` 에서 빈 목록 키를 지우고 `baked` 를 늘 비운다. `unregisterAll(plugin)` 때 그 플러그인 로더가 정의한 클래스 키를 `baked`·`deprecationWarned` 에서 빼고, `monitorWarned`·`Stats.listeners` 에서 그 플러그인 처리기를 뺀다. `HotReloadTest` 에 "자기 이벤트 클래스를 쓰는 플러그인" 회수 시험을 더한다. 작음.

### A14-08 꺼진 플러그인의 명령이 계속 돌고, 수명 함수가 공개다 (낮음)

- 근거: `FurnacePlugin.init`·`setEnabled`(`furnace-api/.../plugin/FurnacePlugin.java:26, 33`)가 "로더 전용"인데 `public final` 이다. 플러그인이 `setEnabled(false)` 를 부르면(자기 자신이든 다른 플러그인이든) `onDisable`·`tasks().cancelAll`·`own` 닫기만 돌고 `PluginManagerImpl.disable` 의 정리(명령·구독·티켓…)는 돌지 않는다. 명령 실행(`CommandRegistryImpl.dispatch`, `:260`)은 소유 플러그인이 켜졌는지 보지 않는다.
- 결과: 재현에서 스스로 끈 플러그인의 명령이 다시 실행된다. Bukkit `PluginCommand.execute` 는 `owningPlugin.isEnabled()` 가 거짓이면 `CommandException("Cannot execute command … plugin … is disabled")` 로 거부한다. Paper `JavaPlugin.setEnabled` 는 `protected final` 이라 다른 플러그인이 부를 수 없다.
- 제안: (1) 명령 실행·탭 완성에서 소유 플러그인이 꺼졌으면 Bukkit 문구로 거부. (2) `setEnabled` 를 부른 쪽이 로더가 아니면 `PluginManager.enable/disable` 로 넘기게(API 시그니처는 그대로 — 가시성 축소는 정책상 MAJOR 라 지금은 위임으로). 작음.

### A14-09 첫 플러그인의 클래스로더를 JDK 지연 스레드가 붙잡는다 (낮음)

- 근거: 전역 지연은 `CompletableFuture.delayedExecutor`(A14-04)를 쓴다. 이 실행기의 스레드(`CompletableFutureDelayScheduler`)는 JDK 안의 정적 싱글턴이라 **처음 쓰일 때** 만들어진다. 서버가 기동 중에 이것을 쓰지 않으면 처음 쓰는 곳은 첫 플러그인의 `onEnable` 이고(예: `tasks().runGlobal`), JDK 21 의 `Thread` 생성자는 만든 쪽 호출 스택의 `AccessControlContext`(그 플러그인 클래스의 `ProtectionDomain` → 클래스로더)를 스레드에 저장한다. 이 스레드는 JVM 끝까지 산다.
- 재현: `measure_hundredReloadsOfWellBehavedPlugin` 을 혼자 돌리면 100개 중 **첫 로더 #0 하나**만 남는다. 문맥 로더·버스 표·스폰 필터 어디에도 없다(`pinnedBy` 결과 빈 목록). 시험 앞에서 지연 실행기를 한 번 써 두면(`-Dfurnace.a14.prewarm=true`) 0개다. 전역 지연을 쓰지 않는 몸통(region 반복 작업·명령·패킷만)도 0개다. 같은 JVM 에서 다른 시험이 먼저 지연 실행기를 썼으면 남지 않는다(그래서 클래스 전체로 돌리면 이 측정은 통과한다).
- 결과: 개발 모드에서 기동 때 켠 첫 플러그인(전역 지연을 처음 쓴 플러그인)의 첫 로더가 회수되지 않고 15초 뒤 누수 경고가 뜬다. 다른 JDK 지연 생성 스레드(가상 스레드 스케줄러는 기동 때 이미 생김)도 같은 위험이 있다.
- 제안: A14-04 를 고치면(전역 지연을 서버 자체 큐로) 없어진다. 그 전이라면 `GlobalThread` 생성자에서 `delayedExecutor` 를 한 번 써 서버 스택에서 스레드를 만들게 한다(한 줄). 작음.

## 다른 파트로 넘기는 것

- **A15**: A14-04 가 Bukkit 어댑터 박동(`LegacyScheduler`)에도 그대로 간다 — `runTaskLater` 가 `/tick rate`·지연을 따르지 않는다. A14-03 의 Bukkit 다리 `isAsynchronous` 값.
- **A17**: A14-01 고친 뒤 많은 작업을 거는 플러그인의 메모리 측정. A14-04 를 큐로 바꾸면 전역 지연 작업 비용.
- **A19**: 재현 시험 중 `a14_07*`·`a14_06`·`measure_*` 는 `HotReloadTest` 로, `a14_01`·`a14_02`·`a14_08` 은 `PluginFoundationTest` 쪽으로 옮기기 좋다. `measure_*` 는 JVM 안 다른 시험 순서에 따라 결과가 달라지니(A14-09) 회귀 시험으로 옮길 때는 A14-09 를 먼저 고친다.
