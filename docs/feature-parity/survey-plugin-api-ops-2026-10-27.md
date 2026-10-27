# 플러그인 API·운영(4단계) 현황 조사 (2026-10-27)

기준: 브랜치 `claude/quirky-wright-lra834` (f25f8042). 코드 수정 없이 읽기만 했고, 테스트는 돌리지 않았다(표의 "테스트"는 파일에 있는 것을 가리킬 뿐 통과 여부 미검증).
로드맵 출처: `docs/feature-parity/roadmap-2026-10-09.md` 180~189행(원칙 9개), 28~42행(API v0).
경로 약칭: API=`furnace-api/src/main/java/org/domi/furnace/api`, SRV=`furnace-server/src/main/java/org/domi/furnace/server`.

## A. furnace-api / furnace-plugin-test

### A-1. 어댑터 대비 설계 원칙 9개

| # | 원칙 | 상태 | 근거 | 테스트 / 비고 |
|---|---|---|---|---|
| 1 | 이벤트 모델 Bukkit 의미 | 부분 | 우선순위 LOWEST~MONITOR `API/event/EventPriority.java:5`, `ignoreCancelled` `API/event/Subscribe.java:16`, 취소 `Cancellable`, 어노테이션 + 람다 구독 `SRV/plugin/EventBusImpl.java:60,94`, ignoreCancelled 처리 `:155,177`, 구독자 없으면 이벤트 생성 생략 `:120` | `PluginSystemTest.eventBusOrdersByPriorityAndHonorsCancellation`(:157). 빈칸: 동기/비동기 구분 API 없음(`Event` 에 isAsync 없음, `AsyncChatEvent`·`AsyncPlayerPreLoginEvent` 는 이름뿐). 이벤트 종류가 17개뿐(player 10·block 3·server 4) — 이동·상호작용·피해·엔티티·인벤토리·월드 이벤트 없음 |
| 2 | 스레드 모델 명시 | 부분 | `Owned`/`ThreadOwner` `API/concurrent/`, `GlobalScheduler`(전역 직렬 실행기) `API/sched/GlobalScheduler.java`, `FurnaceScheduler.global()` `:7` | `OwnedTest`. 빈칸: 각 API 메서드의 "호출 가능 스레드" 문서가 `Player` 클래스 주석(:1) 한 곳뿐. 이벤트가 어느 스레드에서 불리는지 규약 없음 |
| 3 | 레지스트리 이름 기반 | 없음 | API 에 Material/BlockData/EntityType/Enchantment 류 타입 자체가 없다. 있는 건 `world/BlockPos`, `world/Location` 뿐. `BlockBreakEvent` 도 좌표만 노출 | 자체 정수 id 를 노출하지 않는 점은 지켜짐(노출할 게 없음). 서버 쪽 `BlockRegistry`/`BlockStateKey` 를 API 용 식별자+조회로 감싸는 일이 필요 |
| 4 | 자체 Text 타입 | 구현됨 | `API/text/Text.java` 외 마크업·직렬화기·번역 | 테스트 21개 중 text 계열 13개(`TextEncodingTest`, `JsonSerializerTest`, `MarkupParseTest` 등). 골든 파일 바이트 일치(로드맵 29행) |
| 5 | 명령·권한·완성 | 부분 | 명령 `API/command/CommandRegistry.java:16`(라벨·별칭·설명), `BasicCommand.suggest` `:14`, 권한 `API/permission/`(TRUE/FALSE/OP/NOT_OP, 자식 상속, 첨부), Brigadier 노드 연결 `SRV/plugin/CommandRegistryImpl.java` | `PluginSystemTest.commandsRegisterNamespacedOnConflict…`(:115), `permissionsFollowDefaultsChildrenAndAttachments`(:202). 빈칸: 플러그인이 Brigadier 노드를 직접 등록하는 API 없음(문자열 인자 greedy 뿐), 권한 영속 저장 없음 |
| 6 | 플러그인 로더 | 구현됨 | 디스크립터 `API/plugin/PluginDescription.java`(name·version·main·api-version·depend·softdepend·loadbefore·commands·permissions), jar 별 `PluginClassLoader` `SRV/plugin/PluginManagerImpl.java:389`(부모→자기→타 플러그인 `:358`), 위상 정렬 `:137`, 실패 시 비활성화 | `PluginSystemTest.loadsInDependencyOrderAndSkipsBadJars`(:81), `failingEnableDisablesPlugin`(:237). 빈칸: 런타임 로드/핫 리로드 API 없음(`PluginManager` 에 load 메서드 없음), Bukkit `plugin.yml` 로딩은 5단계 어댑터 몫 |
| 7 | PersistentDataContainer | 없음 | `PersistentData` 검색 0건(API·서버 모두). NBT 타입은 서버 내부에만 있음 | 없음. 엔티티·아이템·청크·월드 어느 쪽도 API 로 못 만짐. API 에 Item/Entity/World 타입이 아예 없는 게 선행 병목 |
| 8 | 스케줄러 | 부분 | `FurnaceScheduler`(global/region) `API/sched/FurnaceScheduler.java:7,14`, 지연·반복 `TaskScheduler.java:19-35`, 플러그인별 자동 취소 `API/plugin/PluginTasks.java:26-49`, 구현 `SRV/sched/` | 스케줄러 검토는 `shared-scheduler-review-2026-10-27.md` 참고. 빈칸: 엔티티별 스케줄러 없음(`FurnaceScheduler.java:1` 주석에 "추후 entity"), 비동기는 `PluginTasks.runAsync`(:42) 뿐 |
| 9 | 서버 내부 클래스 비노출 | 구현됨 | `furnace-api` 에서 `org.domi.furnace.server` import 0건(grep). api 모듈은 server 를 모름 | 자동 검사 테스트(ArchUnit 류)는 없음 — 빈칸, 소 |

### A-2. 플러그인 API v0

| 항목 | 상태 | 근거 | 테스트 / 비고 |
|---|---|---|---|
| 명령 등록 | 구현됨 | `API/command/CommandRegistry.java`, `SRV/plugin/CommandRegistryImpl.java`(150줄), 네임스페이스 충돌 처리·끌 때 해제 | `PluginSystemTest:115` |
| 이벤트 버스 | 구현됨(범위 좁음) | `API/event/EventBus.java`, `SRV/plugin/EventBusImpl.java`(261줄) | `PluginSystemTest:157`. 이벤트 17종 |
| 권한 | 구현됨 | `API/permission/*`, `SRV/plugin/PermissionManagerImpl.java`, `PermissibleBase.java` | `PluginSystemTest:202` |
| 스케줄러 | 구현됨 | A-1 #8 참고 | |
| 설정 | 구현됨 | `API/config/`(YAML/TOML/JSON, 주석 보존), `FurnacePlugin.saveDefaultConfig` | `ConfigFileTest`, `YamlParserTest`, `YamlWriterTest`, `TomlTest`, `JsonTest`, `CrossFormatTest`, `ConfigSectionTest` |
| Player API | 부분 | `API/entity/Player.java`, `SRV/plugin/PlayerHandle.java`(151줄) — 메시지·타이틀·액션바·게임 모드 등 | 직접 테스트는 확인 못 함. 인벤토리·위치 이동·효과·속성 등은 없음 |
| 샘플 플러그인 | 구현됨 | `furnace-plugin-test/src/main/java/org/domi/furnace/plugintest/SamplePlugin.java`(명령·이벤트·설정·반복 작업), `furnace-plugin-test/src/main/resources/furnace-plugin.yml` | 샘플 자체 테스트 없음. 서버 쪽 로더 테스트는 `furnace-server/src/test/java/org/domi/furnace/server/plugin/PluginSystemTest.java`(244줄, 테스트 6개)와 `TestPlugins.java` |
| 플러그인 틱 통계 | 부분 | `PluginSystemTest.tickStatsCountsCallsOnlyWhenEnabled`(:178)가 있으나 구현 위치는 확인 못 함(`/timings` 와 연결 안 됨) | |

## B. 4단계 운영 항목 현황

| 항목 | 상태 | 근거 | 비고 |
|---|---|---|---|
| `/tps` `/mspt` | 없음 | 명령 리터럴 목록(`SRV/command/**`)에 없음 | 틱 계측은 `-Dfurnace.tickstats=true` 로그뿐(`SRV/world/Regionizer.java:305-345`, region 별 p50/p95/max). region 별 틱이라 "서버 TPS" 정의부터 필요 |
| `/version` `/plugins` | 없음 | 명령 목록에 없음. 정보 소스는 있음: `Furnace.version()`(`API/Furnace.java`), `PluginManager.plugins()` | 소규모 |
| `/timings` | 없음 | 명령·프로파일러 없음 | `Regionizer` 단계별 계측(:339)을 재사용 가능 |
| 구현된 명령(참고) | — | help kick list say seed ban ban-ip banlist pardon pardon-ip op deop whitelist save-all/off/on setidletimeout stop, time weather gamerule difficulty worldborder, gamemode defaultgamemode kill experience/xp spawnpoint setworldspawn, datapack reload recipe, locate summon setblock fill effect damage advancement msg tell w me 등 | `/reload` 는 데이터 팩 리로드 |
| server.properties | 부분 | 키 정의 약 70개 `SRV/config/ServerProperties.java:34-112`(바닐라 키 파싱, 기본 파일 바이트 일치 — 로드맵 17행) | 서버 코드가 실제 읽는 키(ServerProperties 밖 참조 수 기준): view-distance, simulation-distance, spawn-protection, op/function-permission-level, network-compression-threshold, player-idle-timeout, enforce-secure-profile, hide-online-players, enable-status, log-ips, accepts-transfers, prevent-proxy-connections, max-chained-neighbor-updates, entity-broadcast-range-percentage 등. **파싱만 하고 안 읽음(참조 0)**: max-tick-time, rate-limit, enable-query, enable-rcon, use-native-transport, sync-chunk-writes, region-file-compression, max-world-size, enable-command-block, text-filtering-config, broadcast-rcon-to-ops, enable-jmx-monitoring, resource-pack*. spawn-monsters 등은 `RegionLevel.java:198` 로 연결(필드명 다른 접근은 놓쳤을 수 있음) |
| paper-*.yml / spigot.yml / bukkit.yml 유사 설정 | 없음 | 서버 코드에 해당 설정 로더 없음(이름은 `PluginManagerImpl` 주석에만) | `API/config` 의 YAML 로더는 있으므로 도구는 준비됨. "Paper 기본 동작 차이 목록 작성"(로드맵 4단계 선두)이 먼저 필요 |
| 안티 엑스레이 | 없음 | grep 0건 | 청크 전송 경로(`SRV/net/protocol`)에서 블록 치환. 월드젠 비의존 |
| 스폰 한도 | 부분 | `SRV/entity/spawn/NaturalSpawner.java`(바닐라 몹 캡 포팅), spawn-monsters/animals/npcs 연결 | Paper `spawn-limits`·`per-player-mob-spawns` 등 설정 키 없음 |
| 엔티티 활성 범위 | 부분 | `entity-broadcast-range-percentage` 만 연결(`SRV/block/ChunkLevel.java:1548`). simulation-distance 는 Region 틱 범위에 반영(`SRV/world/Region.java:871,892`) | Paper `entity-activation-range`, `tracking-range` 계열 없음 |
| RCON / Query | 없음 | `enable-rcon`·`enable-query` 파싱만(`ServerProperties.java:52,54`), 리스너 코드 0 | Netty 이미 있음 |
| 메트릭 | 없음 | bStats·Prometheus·JMX 모두 없음(`enable-jmx-monitoring` 파싱만) | |
| 크래시 리포트 | 없음 | `crash-reports` 없음, UncaughtExceptionHandler 없음 | 종료 훅은 있음(`SRV/FurnaceMain.java:314`) |
| 워치독 | 없음 | `max-tick-time` 미사용, Watchdog 클래스 없음 | region 별 틱이라 스레드별 감시 필요 |
| 시작 스크립트 | 없음 | `.sh`/`.bat` 은 `tools/block-oracle/run.sh`(개발용)와 `gradlew.bat` 뿐 | `application` 플러그인 + `-Xmx6G`(`furnace-server/build.gradle.kts:27`)라 `installDist` 가 스크립트를 만들 수 있음(실행해 보지 않음) |
| Docker / fat jar | 없음 | Dockerfile 없음. `furnace-server/build.gradle.kts` 에 shadow/Manifest 설정 없음(`application` mainClass 만) | shadow 플러그인(오프라인 의존 캐시 문제) 또는 `installDist` |
| CI | 없음 | `.github/` 디렉터리 없음 | 테스트는 `-PskipOracle=true` 로 Paper 저장소 없이 가능(`settings.gradle.kts`). 바닐라 jar 추출(3-1)은 첫 실행 때 Mojang 다운로드 — CI 네트워크 정책 확인 필요 |

## C. 빈칸별 규모와 의존, 병렬 가능성

규모: 소 = 반나절 이하, 중 = 며칠, 대 = 1주 이상 또는 설계 결정 필요(현재 구조 기반 추정).

| 빈칸 | 규모 | 선행 의존 | 병렬 |
|---|---|---|---|
| `/version` `/plugins` | 소 | 없음 | 가능 |
| `/tps` `/mspt` | 중 | region 별 틱 시간 상시 계측(지금은 옵션 로그) + "서버 TPS" 정의 결정 | `/timings`·메트릭과 계측 레이어 공유 → 같은 사람이 묶는 편이 낫다 |
| `/timings` | 중~대 | 위 계측 + 플러그인 핸들러 시간 기록(`EventBusImpl`) | `/tps` 뒤 |
| server.properties 미사용 키 연결 | 중 | max-tick-time→워치독, enable-rcon/query→RCON/Query, region-file-compression/sync-chunk-writes→`world/storage`, resource-pack→설정 단계 패킷 | 키마다 독립 → 병렬 가능 |
| paper/spigot/bukkit 설정 파일 | 대 | Paper 기본 동작 차이 목록 작성 → 키별 구현. 파일 로더 자체는 소(`API/config` 사용) | 로더 틀만 먼저, 키는 기능과 함께 점진 |
| 안티 엑스레이 | 중 | 청크 전송 경로, 블록 팔레트(`BlockRegistry`) | 가능 |
| 스폰 한도·활성 범위 설정 | 중 | `NaturalSpawner` 몹 캡 상수, 엔티티 틱 경로(`Region`)에 설정 주입. paper 설정 파일 틀이 있으면 편함 | 설정 틀 이후 |
| RCON | 소~중 | 콘솔 명령 경로 존재 | 가능 |
| Query | 소~중 | UDP 리스너 | 가능 |
| 메트릭 | 중 | 계측 레이어(`/tps` 와 공유) | `/tps` 뒤 |
| 크래시 리포트 | 소 | 없음(전역 예외 핸들러 + 파일) | 가능 |
| 워치독 | 중 | region 별 틱 시작 시각 기록(`Regionizer`), 스레드 덤프, `max-tick-time` | 크래시 리포트와 묶음 |
| 시작 스크립트 | 소 | 없음 | 가능 |
| fat jar / Docker | 중 | shadow 도입(의존 캐시), 바닐라 jar 추출 위치 정책(EULA: jar 미포함) | 가능, CI 와 같은 사람이 |
| CI(.github) | 소~중 | `-PskipOracle=true` 테스트 한 단계로 시작 가능. 바닐라 데이터 접근 정책 확인 | 가능 |
| API: 비동기 이벤트 구분 | 소 | `Event.isAsync` + 버스 규약 | 가능 |
| API: 이벤트 확충(이동·피해·상호작용·인벤토리·엔티티) | 대 | 해당 게임플레이 코드에 이벤트 지점, API 에 Entity/Item/World 타입 필요 | 게임플레이와 병행 |
| API: 레지스트리 이름 타입(식별자, Material 등) | 중 | `BlockRegistry`·아이템 레지스트리 노출 설계 | 가능, PDC·Item/Entity API 의 선행 |
| API: PersistentDataContainer | 대 | Entity/Item/Chunk/World API 타입, NBT 공개 모델 | 위 레지스트리·타입 뒤 |
| API: 엔티티 스케줄러 | 중 | 엔티티→region 소유 매핑(`Regionizer`), `.claude/rules/scheduler.md` 규칙 | 가능 |
| API: 스레드 규약 문서화 | 소 | 없음 | 가능 |
| API: 내부 클래스 비노출 검사 테스트 | 소 | 없음 | 가능 |

### 한눈에 보는 결론
- 원칙 9개 중 구현 3(Text, 로더, 비노출), 부분 4(이벤트, 스레드, 명령·권한, 스케줄러), 없음 2(레지스트리 이름, PDC). 주된 병목은 API 에 Entity/Item/World/Block 타입이 없다는 점.
- 4단계 운영 항목은 사실상 전부 비어 있음(운영 명령 0, Paper 설정 0, RCON·Query·메트릭·크래시·워치독·스크립트·Docker·CI 0). 골격(Netty, YAML 설정 로더, 종료 훅, region 계측 코드)은 있다.
- 먼저 할 소규모 묶음(병렬 가능): `/version` `/plugins`, 크래시 리포트, 시작 스크립트, CI, 비동기 이벤트 구분, 스레드 규약 문서.
