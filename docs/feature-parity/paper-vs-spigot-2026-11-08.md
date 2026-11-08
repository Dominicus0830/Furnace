# Paper 와 Spigot 이 다르게 움직이는 곳 — Furnace 호환 계층 대조 (2026-11-08)

도미니코 요청: "페이퍼는 이런데 스피갓은 다름(아까 채널 이름 상한처럼) 부분을 알려주고, 더 나은 방식이 뭔지 생각해서 추천". 코드는 바꾸지 않았다. 고칠 일은 끝의 "후속 작업" 표에 모았다.

## 어떻게 확인했나

- **Paper 1.21.1 원문**: Paper 저장소 main 커밋 `33ff3dff`(2024-10-21, 1.21.1 마지막 무렵)의 `patches/api`(495개)·`patches/server`(1068개)를 받아 읽었다(`git fetch --depth=1 origin 33ff3dff217bfa11a41d157c88d903b3965ac975`). 동작 비교용으로만 읽고 코드는 옮기지 않았다(GPLv3, 결정 7 미정).
- **Spigot 쪽은 Paper 패치의 "지운 줄"로 확인했다.** Paper 패치는 Spigot 이 고친 CraftBukkit·Bukkit 위에 얹는 차이(diff)라서, `-` 로 시작하는 줄이 곧 Spigot 1.21.1 의 원래 코드다. 그래서 **Paper 가 바꾼 곳은 Spigot 동작도 원문으로 확인된다.** 거꾸로 Paper 패치가 건드리지 않은 곳은 두 서버가 같다(둘 다 CraftBukkit 그대로). hub.spigotmc.org 는 여전히 403 이다.
- 원문에서 못 본 것은 "미확인(기억)" 으로 적었다. 실제 서버를 켜서 대조한 것은 없다(읽기만).

## 한눈에

| 분류 | 개수 | 뜻 |
|---|---|---|
| ❌ Spigot 규칙을 따르고 있음 (채널 상한과 같은 경우) | 3 | 명령 인자 빈 칸(`CommandMap.dispatch` 길), 표지판 클릭 명령의 이벤트, 비동기 작업 종료 대기 |
| ✅ 이미 Paper 와 같음 | 9 | 채널 이름 상한·예약 채널, 비동기 반복 작업 버그 수정, 권한 없는 명령 숨김, 거절 문장, 알 수 없는 명령 문장, 접속·퇴장 문구 번역, 접속 이벤트 시점, 이벤트 잠금 없음 |
| ⚠️ 두 서버가 같은데 Furnace 만 다름 | 7 | 채팅 받는 사람 바꾸기, `teleport()` 반환·승객, `sendMessage` 주소 링크, `getDisplayName()`, `PlayerMoveEvent` 간격, 없는 명령의 `dispatchCommand` 반환값, 콘솔 권한 |
| 🟢 Furnace 가 둘보다 너그러움(일부러) | 3 | 주 스레드 밖 `dispatchCommand`, 이벤트 스레드 검사 없음, 채널 128개 넘침 |
| 🔧 바꾸기 추천(내부 구조) | 1 | 옛 Bukkit 비동기 작업을 가상 스레드 대신 이름 붙은 플랫폼 스레드 풀로 |

## 1. 명령

| # | 항목 | Paper 1.21.1 | Spigot 1.21.1 | Furnace 지금 | 추천 | 이유 |
|---|---|---|---|---|---|---|
| A1 ❌ | 명령 줄의 연속 빈 칸 (`"give  a b "`) | 빈 칸을 버린다: `StringUtils.split(line, ' ')` → `[give, a, b]` (`fix-empty-array-elements-in-command-arguments`, CraftServer·SimpleCommandMap·BukkitCommandNode 모두) | `line.split(" ")` → `[give, "", a, b]` (가운데 빈 인자, 끝 빈 칸만 버림) — Paper 패치의 지운 줄로 확인 | **길에 따라 다르다.** 플레이어·트리 길(`LegacyCommands.execArgs`, `LegacyCommands.java:160`)은 Paper, `Bukkit.dispatchCommand` 가 먼저 타는 `SimpleCommandMap.dispatch`(`SimpleCommandMap.java:131`)는 **Spigot** | Paper 로 통일 — `SimpleCommandMap.dispatch` 도 `execArgs` 와 같은 규칙. 앞 빈 칸(`" say hi"`)도 Paper 는 버린다 | 같은 서버에서 플레이어가 친 명령과 플러그인이 보낸 명령의 인자 개수가 달라진다. 채널 상한과 같은 "Spigot 기억으로 만든" 자리 |
| A2 ✅ | 권한 없는 사람이 Bukkit 명령을 칠 때 | 명령 노드 요구 조건이 `testPermissionSilent` 라 트리에서 걸러져 바닐라 "Unknown or incomplete command" 가 나온다 (`Brigadier-based-command-API`, 원문 읽기 — 실행 대조는 안 함) | 플레이어 명령이 `cserver.dispatchCommand` → `PluginCommand.execute` → 거절 문장 (Paper 가 지운 `handleCommand` 줄로 확인) | 트리에서 숨겨 "알 수 없는 명령" (`LegacyCommands.java:6` 주석은 이걸 "알려진 차이"라 적었지만 **Paper 와 같다**) | 유지, 주석만 "Paper 와 같음·Spigot 과 다름"으로 | Paper 기준. 단 `Bukkit.dispatchCommand` 로 부르면 두 서버 모두 거절 문장이 나온다 — Furnace 도 같다 |
| A3 ✅ | 기본 거절 문장 | "…if you believe that this is **in error**." 빨강, `paper-global.yml` `messages.no-permission` 으로 바꿈 (`Make-the-default-permission-message-configurable`) | "…if you believe that this is **a mistake**." (Bukkit `Command.java` 지운 줄) | Paper 문장(`LegacyCommand.java:20`, `Command.java:84`), 바꾸는 설정은 없음 | 유지 + 설정 `messages.no-permission` 을 Furnace 옵션으로 두고 설정 가져오기가 옮김 | Paper 가 이미 설정으로 열어 둔 값이라 옵션 하나로 둘 다 된다 |
| A4 ✅ | 없는 명령을 칠 때 문장 | 바닐라 "Unknown or incomplete command, see below for error" (+ `UnknownCommandEvent`) | `spigot.yml` `messages.unknown-command` (기본 "Unknown command. Type "/help" for help." — 문구는 미확인(기억), 쓰는 줄은 Paper 가 지운 줄로 확인) | 바닐라 문장 | 유지 | 바닐라 = Paper |
| A5 ⚠️ | 없는 명령의 `Bukkit.dispatchCommand` 반환값 | `false` | `false` (+ 알 수 없음 문장) | 바닐라·없는 명령은 명령 큐에 넣고 늘 `true` (`CommandRegistryImpl.java:256`) | 트리 루트에 그 이름이 없으면 `false` + 바닐라 오류 문장 | 두 서버가 같다. `false` 를 보고 다른 길로 가는 플러그인이 있다. 있는 명령은 실행기 결과와 상관없이 `true` 인 것은 이미 같다 |
| A6 ❌ | 표지판 `run_command` 클릭 | `PlayerSignCommandPreprocessEvent`(PlayerCommandPreprocessEvent 하위)를 부르고, 취소되면 안 돌리고, 콘솔에 "issued server command" 로그, 앞에 `/` 를 붙여 `//wand` 도 됨. 권한 2 표지판 소스는 그대로. 실패 문장은 `misc.show-sign-click-command-failure-msgs-to-player`(기본 false) 일 때만 (`Fix-commands-from-signs-not-firing-command-events`) | 바닐라 그대로 `performPrefixedCommand` (이벤트·로그 없음 — 지운 줄로 확인) | 바닐라 그대로(`ServerCore.java:1203`) = **Spigot** | Paper 처럼 이벤트·로그를 더하고 실행 자체(권한 2 소스)는 바닐라 유지 | 결과(누가 어떤 권한으로 실행하나)는 바닐라와 같고, 플러그인이 표지판 명령을 막거나 기록할 수 있게 된다. 명령 기록·보안 플러그인이 기대한다 |
| A7 🟢 | 주 스레드 밖에서 `Bukkit.dispatchCommand` | `AsyncCatcher` 로 `IllegalStateException("Command Dispatched Async: …")` | 같음(Spigot 의 AsyncCatcher) — CraftServer 쪽 줄은 미확인(기억), 플레이어 길은 지운 줄로 확인 | 전역 스레드로 넘기고 `true` (`SimpleCommandMap.java:143`) | 유지 | 두 서버에서는 예외로 끝나던 호출이 Furnace 에서는 의도대로 실행된다. 정상 플러그인에는 차이가 없다 |
| A8 🟢 | `/` 로 시작하는 `dispatchCommand("/say hi")` | 이름이 `/say` 라 없는 명령 | 같음 | 앞 `/` 를 떼고 실행 (`CraftServer.java:154`) | 유지 | 너그러운 쪽, 해가 없다 |

## 2. 스케줄러

| # | 항목 | Paper 1.21.1 | Spigot 1.21.1 | Furnace 지금 | 추천 | 이유 |
|---|---|---|---|---|---|---|
| B1 ✅ | `runTaskTimerAsynchronously(Plugin, Consumer, delay, period)` | 주기대로 반복 | **반복 안 함**(주기 자리에 `NO_REPEATING` 을 넘기는 버그 — `Fix-CraftScheduler-runTaskTimerAsynchronously-Plugin` 의 지운 줄) | 반복(`CompatScheduler.java:180`) | 유지 | Paper 기준, Spigot 쪽이 버그 |
| B2 🔧 | 비동기 작업이 도는 스레드 | `ThreadPoolExecutor(코어 4, 최대 무제한, 30초)` + 관리 스레드 하나, 이름 `Craft Scheduler Thread - N - 플러그인` (`Improved-Async-Task-Scheduler`, `Name-craft-scheduler-threads-according-to-the-plugin`) | `Executors.newCachedThreadPool`, 이름 `Craft Scheduler Thread - N` (지운 줄로 확인) | 가상 스레드(이름 없음, `PluginHost.java:35`) | **이름 붙은 플랫폼 스레드 풀로**(Paper 모양). 가상 스레드는 옵션 | 아래 "더 나은 방식" 1번 |
| B3 ❌ | 서버를 끌 때 돌고 있는 비동기 작업 | 최대 5초 기다리고, 남은 작업은 "Nag author(s)… not properly shutting down its async tasks" 로그 (`Wait-for-Async-Tasks-during-shutdown`) | 기다리지 않는다(reload 만 기다림) | 취소만 하고 기다리지 않는다(`LegacyScheduler.java:59`) = **Spigot** | Paper 처럼 5초 기다림 + 로그 | 끄기 직전 DB 저장을 비동기로 하는 플러그인이 많다. 기다리지 않으면 저장이 잘린다 |
| B4 | `getMainThreadExecutor(Plugin)` (Paper API) | 있음 | 없음 | 없음 | 더함(작음) | Paper 전용 API 라 Spigot 플러그인에는 영향 없음 |

## 3. 이벤트·채팅·접속

| # | 항목 | Paper 1.21.1 | Spigot 1.21.1 | Furnace 지금 | 추천 | 이유 |
|---|---|---|---|---|---|---|
| C1 ✅🟢 | `callEvent` 스레드 검사·잠금 | `PaperEventManager`: 비동기 이벤트를 주 스레드에서 → "may only be triggered asynchronously", 동기 이벤트를 다른 스레드에서 → "…synchronously"(서버 종료 중엔 허용). 잠금 없음 | `SimplePluginManager`: 비동기 이벤트를 synchronized 안에서 부르면 예외(지운 줄로 확인), 동기 이벤트는 `synchronized(this)` 로 직렬화(미확인(기억)) | 검사도 잠금도 없음(`CompatPluginManager.java:163`) | 유지 | Furnace 는 동기 이벤트가 region 스레드에서 돈다 — Paper 처럼 던지면 정상 경로가 깨진다. 잠금 없는 것은 Paper 와 같다 |
| C2 ⚠️ | `AsyncPlayerChatEvent#getRecipients()` 바꾸기 | 된다(레거시 받는 사람 집합을 보는 사람으로 씀, Adventure 패치 `viewersFromLegacy`) | 된다 | **`UnsupportedFeature` 예외**(`Mappings.java:218`) | 구현 — 받는 사람 집합을 Furnace 채팅의 보는 사람 거르기로 잇기 | 채팅 채널·무시·근거리 채팅 플러그인 대부분이 쓴다. 두 서버 모두 되는데 Furnace 만 안 된다 — 이 표에서 영향이 제일 크다 |
| C3 ⚠️ | `Player#getDisplayName()` 기본값 | 플레이어 이름 그대로(`adventure$displayName = Component.text(이름)`) | 이름 그대로(`displayName = getScoreboardName()`, 문맥 줄로 확인) | 바닐라 팀 서식(접두사·색)·클릭·호버가 붙은 이름의 레거시(`CraftPlayer.java:58`), `setDisplayName` 없음 | 기본은 이름 그대로, `setDisplayName` 지원 | 둘이 같다. 레거시 채팅 서식(`%1$s`)도 Paper 는 `getDisplayName()` 을 쓴다(`legacyDisplayName`) |
| C4 ✅ | 접속·퇴장 문구 | 번역 컴포넌트 `multiplayer.player.joined`/`left`(노랑) — 클라이언트 언어로 보임, `messages.use-display-name-in-quit-message` 옵션 | 접속은 영어로 굳힌 레거시 문자열, 퇴장 `"§e이름 left the game"` 고정(지운 줄로 확인) | 번역 컴포넌트(`ConnectionHandler.java:3169`) | 유지 | Paper·바닐라와 같다. 한국어 클라이언트에 한국어로 보인다 |
| C5 ⚠️ | `PlayerKickEvent` 의 퇴장 문구 | 번역 컴포넌트, 바꾸면 퇴장 알림에 쓰임 | `"§e이름 left the game."`(**마침표 있음**, 지운 줄로 확인), 바꾸면 `PlayerQuitEvent` 로 넘어감(SPIGOT-3034) | 기본 `"§e이름 left the game"`, 바꿔도 반영 안 됨(`Mappings.java:64`) | 바꾼 값을 퇴장 문구로 잇기 | 두 서버 모두 반영한다 |
| C6 ✅ | `PlayerJoinEvent` 시점 | 플레이어가 월드·추적기에 다 들어가고 탈것까지 실린 뒤 (`Fire-PlayerJoinEvent-when-Player-is-actually-ready`) | 그 전 — 플러그인들이 1틱 미루는 관행이 여기서 나왔다 | 소환 청크·스코어보드·채널 알림 뒤(`ConnectionHandler.java:1469`) — Paper 쪽. 탈것째 접속은 미확인 | 유지 + "탈것 타고 나갔다 들어오기" 시험 하나 | Paper 기준 |
| C7 ⚠️ | `PlayerMoveEvent` 를 부르는 간격 | CraftBukkit 규칙 그대로(Paper 서버 패치가 이 부분을 바꾸지 않음): 마지막으로 알린 자리에서 거리² > 1/256 이거나 회전 합 > 10° 일 때 — 수치는 미확인(기억) | 같음 | 위치가 조금이라도 바뀌면 매번, **회전만 바뀌면 안 부름**(`Region.java:1386`) | CraftBukkit 규칙으로(회전 포함, 문턱 적용) | 시선·AFK 감지 플러그인이 회전 이벤트를 기다린다. 문턱이 있으면 작은 움직임 이벤트가 줄어 성능에도 이득 |

## 4. 플레이어·엔티티·보내기

| # | 항목 | Paper 1.21.1 | Spigot 1.21.1 | Furnace 지금 | 추천 | 이유 |
|---|---|---|---|---|---|---|
| D1 ⚠️ | `Entity#teleport(Location)` | 동기. 승객이 있거나 지워진 엔티티면 `false`, 탈것에서 내림. `TeleportFlag` 으로 승객·탈것 유지 가능 (`More-Teleport-API`) | 동기. 승객이 있거나 지워졌으면 `false`, 내림(지운 줄로 확인) | 비동기로 맡기고 **늘 `true`**(`CraftEntity.java:86`) | 승객·제거 검사로 `false`, 내림. 같은 region 이면 바로 옮기기 | 두 서버가 같다. 반환값으로 성공을 판단하는 플러그인이 있다 |
| D2 ⚠️ | `sendMessage(String)` 안의 주소 | `CraftChatMessage.fromString` 이 `https://…`·`example.com` 을 클릭 링크(OPEN_URL)로 만든다(패턴 원문 확인) | 같음 | § 서식만, 링크 없음(`Compat.java:240`, 서식기 `section()` 은 주소 추출 없음) | Bukkit `sendMessage`·`broadcastMessage` 에서만 주소 추출을 켠다(서식기 `extractUrls` 가 이미 있다) | 둘이 같다. 디스코드 주소 안내 같은 흔한 쓰임이 클릭이 안 된다 |
| D3 ⚠️ | 콘솔의 `hasPermission` | op 규칙(`default: false` 권한은 없음). `console.has-all-permissions: true` 면 전부(기본 false) | op 규칙 — 미확인(기억) | 늘 `true`(`CompatConsole.java:37`) = Paper 의 옵션을 켠 상태 | 기본 op 규칙 + Paper 와 같은 옵션 | Paper 가 이미 옵션으로 연 값 |

## 5. 플러그인 메시징 (PR #146 에서 다룸 — 다시 확인)

| # | 항목 | Paper 1.21.1 | Spigot 1.21.1 | Furnace 지금 | 추천 |
|---|---|---|---|---|---|
| E1 ✅ | 채널 이름 길이 상한 | `Short.MAX_VALUE`(속성 `paper.maxCustomChannelName`) | 64 | Paper | 유지 |
| E2 ✅ | 예약 채널 | `minecraft:register`·`unregister` 만 | `minecraft` 가 들어간 이름 전부 | Paper | 유지 |
| E3 🟢 | 클라이언트 채널 128개 넘김 | 예외로 끊음(`-Dpaper.disableChannelLimit` 면 제한 없음) | 예외로 끊음 — **128 은 두 서버 모두 원문 확인**(`Flag-to-disable-the-channel-limit` 지운 줄) | 넘는 것만 버림 | 유지. `PluginChannels.java:19` 주석의 "가정: 확인 안 함" 을 "확인됨"으로 고친다 |

## 6. 게임 설정 기본값 (참고 — 호환 계층 밖)

Paper 는 `unsupported-settings` 로 바닐라 버그를 기본으로 막는다: `allow-unsafe-end-portal-teleportation`(엔드 포털 모래 복제), `allow-piston-duplication`(TNT·카펫·레일 복제), `allow-headless-pistons`, `allow-permanent-block-break-exploits`(기반암 부수기), `allow-tripwire-disarming-exploits` 가 모두 기본 `false`(`Paper-config-files` 원문 확인). Spigot 은 바닐라대로 된다. Furnace 는 바닐라(= Spigot)를 따르고 설정 가져오기는 이 키를 "비목표"로 적는다(`PaperConfigImport.java:527`). 프로젝트 원칙(바닐라와 같은 결과)대로라 맞다. 엔드 포털 중력 블록 복제는 이미 결정 1(`docs/user-todo/entries/2026-11-08-gravity-block-portal-dupe.md`)로 올라가 있어 여기서 따로 묻지 않는다.

## 더 나은 방식 (추천)

1. **옛 Bukkit 비동기 작업은 플랫폼 스레드 풀로 (B2).** 지금은 가상 스레드인데, Furnace 는 JDK 21 로 돈다(CI `java-version: 21`). JDK 21 의 가상 스레드는 `synchronized` 안에서 소켓·파일을 기다리면 운반 스레드(코어 수만큼)를 붙잡는다(pinning — JDK 24 의 JEP 491 에서야 풀림). 오래된 JDBC 드라이버·커넥션 풀·HTTP 라이브러리가 바로 이 모양이라, DB 를 쓰는 플러그인 몇 개가 동시에 막히면 **모든 비동기 작업이 멈출 수 있다.** 또 스레드 이름이 없어 스레드 덤프·로그에서 어느 플러그인인지 안 보이고, 스레드마다 캐시를 두는 `ThreadLocal`(SimpleDateFormat 등)이 작업마다 새로 만들어진다. 추천: Paper 와 같은 모양(코어 4, 최대 무제한, 30초 유지, 이름 `Craft Scheduler Thread - N - 플러그인`), 옵션 `compat.scheduler.async-threads=platform|virtual`. Paper 도 쓰는 크기라 결과·자원 사용이 익숙하고, Furnace 고유 플러그인 API 는 지금처럼 가상 스레드를 써도 된다.
2. **기준은 "Paper 1.21.1, 단 Paper 가 설정으로 연 것은 Furnace 도 설정으로".** Spigot 용으로 만든 플러그인은 이미 대부분 Paper 위에서 돌고 있어서, Paper 를 따르면 Spigot 플러그인도 깨지지 않는다. 그래서 "Paper 식/Spigot 식" 전환 옵션을 항목마다 두는 것은 추천하지 않는다(시험할 조합만 늘어난다). 예외는 Paper 가 스스로 설정으로 둔 값 — 거절 문장(A3), 콘솔 전체 권한(D3), 채널 이름 상한·채널 수 제한(E1·E3), 표지판 실패 문장(A6) — 이것만 같은 이름의 Furnace 옵션으로 두고 설정 가져오기가 `paper-global.yml` 에서 옮기게 한다.
3. **둘보다 나은 곳은 그대로 둔다.** 주 스레드 밖 `dispatchCommand`(A7), 이벤트 스레드 예외 없음(C1), 채널 넘침을 끊지 않고 버림(E3)은 정상 플러그인의 결과는 같고, 두 서버에서 예외로 죽던 호출만 살아난다.
4. **다음부터 같은 실수를 막는 법.** ① Bukkit 동작을 새로 넣을 때 Paper 1.21.1 패치를 파일 이름으로 찾아 본다(`git grep` 으로 클래스 이름 — Paper 가 바꾼 곳이면 지운 줄이 곧 Spigot). ② 고른 동작 옆에 `// Paper 1.21.1: <패치 이름>` 주석을 단다 — 나중 점검이 `grep` 한 번으로 끝난다. ③ 표의 행마다 호환 모듈 시험 하나(`furnace-compat-paper` 단위 시험)로 동작을 고정한다.

## 후속 작업 (작업 리스트 후보)

모두 코드 수정이 필요하다. 이 문서 PR 은 문서만 바꾼다.

| 묶음 | 내용 | 모델·노력치 | 크기 |
|---|---|---|---|
| P1 명령 정합 | A1 빈 칸 통일, A5 없는 명령 `false`, A6 표지판 명령 이벤트·로그, A3 거절 문장 옵션 + 설정 가져오기, `LegacyCommands.java:6` 주석 고침 | Sonnet high | 작음~중간 |
| P2 비동기 스레드 | B2 플랫폼 스레드 풀(옵션으로 가상), B3 종료 5초 대기, B4 `getMainThreadExecutor` | Opus high (스레드·종료 순서) | 중간 |
| P3 채팅·표시 이름 | C2 받는 사람 바꾸기, C3 `getDisplayName`·`setDisplayName`, C5 킥 퇴장 문구, D2 주소 링크 | Sonnet high | 중간 |
| P4 이동·텔레포트 | C7 `PlayerMoveEvent` 간격(CraftBukkit 규칙), D1 `teleport()` 반환·승객·내리기, C6 탈것 접속 시험 | Opus high (region·이동 검증과 얽힘) | 중간 |
| P5 작은 정리 | D3 콘솔 권한 옵션, E3 `PluginChannels` 주석 | Haiku low | 작음 |

## 확인 못 한 것

- Spigot 원문(hub.spigotmc.org)은 못 읽었다. Paper 가 안 바꾼 곳의 Spigot 동작(C1 의 synchronized 직렬화, C7 의 수치, D3, A4 문구, A7 의 CraftServer 쪽)은 기억이다.
- A2(권한 없으면 Paper 도 "알 수 없는 명령")는 원문의 요구 조건 코드로 판단했고, Paper 서버를 켜서 쳐 보지는 않았다.
- 실제 서버 대조(Paper·Spigot jar 를 켜고 같은 플러그인으로 비교)는 하지 않았다.
