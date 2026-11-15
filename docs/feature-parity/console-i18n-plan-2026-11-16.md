# 콘솔 출력 Paper화 + 실행 환경 언어 따라가기 — 계획 (2026-11-16)

도미니코 요청: (1) 콘솔 메시지가 Paper 처럼 깔끔해야 한다. (2) 서버를 돌리는 환경의 언어에 따라 메시지가 달라져야 한다.
기준 브랜치 `claude/quirky-wright-lra834`(5a9c7c552)에서 fatJar 를 빌드해 실제 시작·명령·종료 로그를 받아 대조했다(플러그인 jar 는 `furnace-plugin-test` 샘플 하나).

## 1. 지금 콘솔이 Paper 와 다른 점 (실측)

### 1-1. 줄 모양
| | Paper 콘솔 | Furnace 지금 |
|---|---|---|
| 접두 | `[15:30:16 INFO]: 메시지` | `[15:47:19] [main/INFO]: 메시지` (바닐라 파일 형식을 콘솔에도 씀) |
| 라이브러리 로그 | `[com.zaxxer.hikari.HikariDataSource] 메시지`(마인크래프트·Bukkit 밖 로거는 이름이 앞에 붙음) | 접두 없음 → 플러그인이 가져온 라이브러리 줄과 서버 줄이 구별 안 됨 |
| 색 | WARN 노랑·ERROR 빨강(터미널일 때) | 텍스트 컴포넌트만 색, 레벨 색 없음 |
| `latest.log` | `[15:30:16] [Server thread/INFO]: …` | 같은 형식(스레드 이름만 `main`·`furnace-global` 등) |

→ 콘솔만 Paper 형식으로 바꾼다. `latest.log` 는 지금 형식을 유지한다(파일을 읽는 도구가 있다).

### 1-2. 줄 내용 (기본 시작 한 번, 플러그인 1개 기준 — 전체 73줄 중)
**Paper·바닐라와 문구가 같아 그대로 둘 것 (번역 금지 — 호스팅 패널·플러그인이 읽는다)**
`Starting minecraft server version 1.21.1`, `Loading properties`, `Default game type: …`, `Generating keypair`, `Starting Minecraft server on *:25565`, 오프라인 모드 경고 4줄, `Preparing level "world"`, `Preparing start region for dimension …`, `Preparing spawn area: n%`, `Time elapsed: n ms`, `Using epoll channel type`, `Done (9.5s)! For help, type "help"`, `[플러그인] Loading/Enabling/Disabling X vN`, `Could not load '…' in folder 'plugins'`, `UUID of player …`, `… logged in with entity id …`, `… lost connection: …`, `Can't keep up! …`, `Saving the game…`, `Stopping the server`, `There are 0 of a max of 20 players online:`, 콘솔 명령 오류 문구.

**Furnace 가 따로 찍는 줄 — Paper 와 다름. 처리 안**
| 지금 줄 | 문제 | 처리 |
|---|---|---|
| `Furnace 부팅: motd='…', tickNanos=…` | 내부 값 | **바꿈**: `This server is running Furnace <버전> (Minecraft 1.21.1)` 한 줄(Paper 의 "This server is running Paper version …" 에 해당). motd·tick 은 DEBUG |
| `네트워크 서버 listen: 포트 25565 (압축 임계값 256)` | `Starting Minecraft server on` 과 중복 | DEBUG 로 내림 |
| `월드 시드=…, 기본 스폰=(…)` | Paper 는 안 찍음 | DEBUG 로 내림(`/seed` 가 있다) |
| `[GEN] 생성 대기열: …` + `[SCHED] region 워커 N개…` | 내부 용어 두 줄 | **한 줄로 합침**(Paper 의 `[ChunkTaskScheduler] Chunk system is using …` 에 해당): `Chunk system is using 4 generation threads and 4 region worker threads` |
| 엔티티 활성 범위·손잡이 경고 두 줄(각 300~600자) | 한 줄에 몇 백 자, 매 기동 WARN | **짧은 한 줄 INFO**: `Paper-style entity defaults are on (differs from vanilla): …  Set config.preset=vanilla to turn them off. Details: /furnace config` — 자세한 키 목록은 `/furnace config`·furnace.properties 설명으로 |
| `Preparing spawn area: 0%` 가 11번 반복 | 같은 줄 반복 | 퍼센트가 바뀔 때만 찍음(바닐라·Paper 는 반복하지만 출력만 깔끔해지고 문구는 같다) |
| `Failed to request yggdrasil public key` + 스택 25줄 | 인터넷 없는 서버에서 25줄 | **한 줄 WARN** + 원인·"채팅 서명·신고가 안 된다, 인터넷/방화벽 확인" 안내, 스택은 DEBUG (**Paper 는 스택을 그대로 찍음 — 다른 점**) |
| `바닐라 데이터를 준비하지 못해 …` 400자 한 줄 | 한 줄이 너무 김 | 앞 문장(무슨 일) + 다음 줄(어떻게: 파일 경로·`-Dfurnace.vanillaJar`) 로 나눔 |
| `Plugin X 에 api-version 이 없다…` | Paper 는 `Legacy plugin X vN does not specify an api-version.` | Bukkit 형식 플러그인은 Paper 문구, Furnace 형식은 자체 문구(번역됨) |
| 종료: `종료 중 — dirty 청크/플레이어 flush`, `네트워크 서버 종료`, `[STOP] 저장 실패 0건` | 내부 용어 | Paper 순서로: `Stopping server` → 플러그인 `Disabling` → `Saving players` → `Saving worlds` → `All dimensions are saved`. 저장 실패 줄은 **실패가 있을 때만** (Paper 의 `ThreadedAnvilChunkStorage` 이름은 Furnace 에 없어 쓰지 않음 — **다른 점**) |
| `[BACKUP]` `[PAUSE]` `[TICK]` `[STOP]` `[핫 리로드]` 같은 대문자·한글 말머리 | 말머리가 제각각 | `[Backup]` `[IdlePause]` `[Tick]` `[HotReload]` 처럼 **영어 고정 말머리**(검색용) + 번역된 문장 |
| 콘솔 명령 결과: `플러그인 (1): Sample`, `전역 TPS (최근 5초…)` 등 | 한국어 고정 | 번역 대상. **Paper 의 `plugins`·`tps` 와 모양이 다름**(Paper: `Server Plugins (1):`/`Bukkit Plugins:`/`- Sample`). 형식은 지금 유지, 문장만 번역 |
| `help` 콘솔 출력 | 바닐라 `/help` 사용법 목록(Paper 는 Bukkit 도움말 형식) | 이번엔 손대지 않음 |
| `Preparing start region for dimension` 이 오버월드만 | Paper 는 네더·엔드도 찍음 | 이번엔 손대지 않음(스폰 청크를 오버월드만 미리 준비하기 때문) |

### 1-3. 한국어 하드코딩 규모
- Furnace 의 `log.info/warn/error` 호출(debug 제외) **581개 중 한글이 든 것 348개**(84개 파일). 가장 많은 곳: `FurnaceMain` 42, `PluginBootstrapper` 30, `ConnectionHandler` 28, `LibraryResolver` 13, `Regionizer`·`WorldManager` 11.
- 그 밖에 콘솔 명령 결과 문장, 시작 점검(설정 경고) 문장이 한글로 박혀 있다.
- 예외(`IllegalArgumentException` 등) 메시지·주석 속 한글은 약 3000곳 — 개발자가 스택에서 읽는 글이라 **번역하지 않고 영어로 고정**하는 별도 정리 TASK 로 뺀다(이번 범위 밖, 아래 4장).

## 2. 언어 기능 설계

- **고르는 순서**: JVM 시스템 속성 `-Dfurnace.language=ko` → 환경변수 `FURNACE_LANGUAGE` → `furnace.properties` 의 `language=auto|en|ko` → `auto` 면 JVM 기본 로케일(`user.language`, 리눅스는 `LANG`, 윈도는 지역 설정) → 해당 언어 파일이 없으면 영어. 기본값 `auto`.
  - 설정을 읽기 전의 몇 줄(EULA·설정 이전 단계)은 `auto` 로 찍는다.
- **번역 파일**: `furnace-server/src/main/resources/furnace-lang/en.properties`, `ko.properties`(UTF-8). 바닐라 `lang/en_us.json`(클라이언트가 쓰는 텍스트 컴포넌트용)과 섞지 않는다. 문장 안 자리는 `{}`(순서대로, 기존 slf4j 식) 또는 `{0}`(번호 — 한국어는 어순이 달라 필요).
- **쓰는 법**: `log.warn(Lang.t("pause.close-failed", dir), e)` — 예외는 번역 대상이 아니라 마지막 인자로 그대로 넘긴다(스택은 영어 그대로).
- **번역 안 하는 것**: 바닐라·Paper 문구 그대로인 줄(1-2장 첫 목록), 스택 트레이스, DEBUG 로그, 설정 키·명령어 이름, 영어 고정 말머리(`[Backup]` 등). 단 운영자가 로그에서 읽는 실패 이유(예외 메시지)는 던지는 자리에서 `Lang.t` 로 번역한다(`lib.*`·`spigot.*`·`plugin.load.*` 등). 그래서 한국어 서버에서도 `Done (…)!`·`Loading X v1` 같은 줄은 영어로 나온다(패널 호환).
- **빠진 키 처리**: 선택 언어에 없으면 영어, 영어에도 없으면 키 이름을 그대로 찍고 DEBUG 경고. 시험이 두 언어 키 목록·`{}` 개수가 같은지 매번 확인한다.
- **문장 스타일(개발자 친화)**: 모든 WARN/ERROR 는 "무슨 일 → 서버가 한 일 → 어떻게 하면 되는지(키·파일 경로)" 순서.
- **로그 파일 언어**: 콘솔과 같은 언어. 영어로 따로 남기는 옵션은 두지 않음(결정 필요 시 아래 5장).

## 3. 형식 설계 (콘솔)
`logback.xml` 의 콘솔 패턴을 `[%d{HH:mm:ss} %level]: %fprefix%fmsg%n` 로 바꾼다.
- `%fprefix` : 로거 이름에 `.` 이 있고 `org.domi.furnace.`·`net.minecraft.`·`com.mojang.`·`Minecraft` 가 아니면 `[로거이름] ` 을 붙임(Paper 와 같은 규칙). 플러그인 로거는 이미 `[이름]` 이 메시지에 들어 있어 영향 없음.
- WARN·ERROR 줄 색(노랑·빨강)은 ANSI 가 이미 켜질 때만(터미널일 때만; `NO_COLOR`·`-Dfurnace.console.colors=false` 로 끔) — 지금 ANSI 판단 로직을 그대로 쓴다.
- 파일 패턴·JLine·`RoutedConsoleAppender` 는 안 바꾼다(플러그인이 콘솔을 읽는 방식 유지).

## 4. 단계 (작게 나눠 PR)
1. **PR1 — 뼈대 + 형식**: `Lang`, `en`/`ko` 파일, `language` 옵션(`OptionKeys`·`FurnacePropertiesFile` 포함), 콘솔 패턴·`%fprefix`·레벨 색, 키 대조 시험, "새 한글 하드코딩 금지" 시험(아직 안 옮긴 파일 목록을 허용 목록으로 두고 줄여 나감). 시작 줄 정리(1-2장 표의 DEBUG 내림·합치기·긴 경고 줄임)와 `FurnaceMain`·`ShutdownSequence` 문구 번역.
2. **PR2 — 플러그인 로딩 쪽**: `PluginBootstrapper`·`PluginManagerImpl`·`LibraryResolver`·`LibrarySettings`·`SpigotMappingFiles`·호환 계층(`furnace-compat-paper`) 로그, `api-version`·의존성 오류 문구.
3. **PR3 — 월드·접속·유지보수**: `ConnectionHandler`·`WorldManager`·`World`·`Regionizer`·`Pregen`·`GenQueue`·백업·가져오기·RCON 등 나머지 로그.
4. **PR4 — 콘솔 명령 결과·설정 점검 문구**: `/furnace …`, `/tps`, `/timings`, 플러그인 목록, `OptionKeys.audit` 경고 등.
5. **(별도 TASK, 이번 범위 밖)** 예외 메시지 한글 ≈3000곳을 영어로 정리, `furnace.properties` 가 만들어질 때 쓰는 설명 주석(127줄)·`/furnace config` 설명도 번역할지.
각 PR 은 새 시험 + 영향받는 기존 시험(로그 문구를 검사하는 17개 시험 파일) 수정, 로컬은 바꾼 영역만, 전체는 CI. 독립 검토 후 CI `test` 초록이면 병합.

## 5. 도미니코 결정이 필요할 수 있는 것 (기본은 추천대로 진행)
| # | 내용 | 안 하면 | 추천 |
|---|---|---|---|
| L1 | 영어가 기본(해당 언어 파일 없을 때·`LANG` 없는 도커) | 한국어 서버가 영어 로그를 볼 수 있음(`language=ko` 로 고정 가능) | `auto`, 못 찾으면 영어 |
| L2 | 한국어에서도 말머리(`[Backup]`)·패널이 읽는 줄은 영어 | 한국어 번역 때문에 호스팅 패널의 "서버 켜짐" 감지가 깨질 수 있음 | 영어 고정 |
| L3 | 엔티티 활성 범위·손잡이 경고 WARN→짧은 INFO 한 줄 | 매 기동 WARN 이 뜸 | 한 줄 INFO(바닐라와 다르다는 사실은 그대로 알림) |
| L4 | yggdrasil 키 실패 시 스택 25줄 대신 한 줄 WARN(Paper 와 다름) | 인터넷 없는 서버에서 콘솔이 지저분 | 한 줄 + DEBUG 스택 |
| L5 | 종료 줄을 Paper 순서(`Saving players`, `Saving worlds` …)로 | 지금처럼 내부 용어 | Paper 순서 |

## 6. 지켜야 할 호환
- 바닐라·Paper 문구 줄은 한 글자도 안 바꾼다(위 목록).
- `latest.log` 형식·위치·롤오버 그대로. `RoutedConsoleAppender`→`ConsoleBridge`→JLine 경로 그대로(플러그인이 System.out 을 잡거나 로거를 가로채는 방식 유지).
- 윈도 `cmd` 한글 깨짐: 지금도 UTF-8 바이트를 직접 쓴다. 이번엔 바꾸지 않고, `start.bat` 에 `chcp 65001` 이 필요한지는 PR1 에서 확인해 알린다.
