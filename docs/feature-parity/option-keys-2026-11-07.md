# 설정 키 점검과 규칙 (Q1, 2026-11-07)

도미니코 결정 R4(2026-11-07): "새 옵션은 모두 지원하되 사용자가 ON/OFF 또는 수치로 바꿀 수 있게. 기본값은 바닐라와 같게. 위험한 설정은 경고."
이 문서는 그 규칙과, 지금 있는 `furnace.properties` 키 전부의 표다. 표의 원본은 코드의 `OptionKeys`(`furnace-server/.../config/OptionKeys.java`)이고,
`OptionKeysTest` 가 표 ↔ 코드 기본값 ↔ 이 문서의 어긋남을 잡는다. 키를 더하면 `OptionKeys` 와 이 표에 한 줄씩 넣는다.

## 1. 규칙

1. **기본값 = 바닐라.** 새 옵션을 안 건드린 서버는 바닐라와 같은 결과를 낸다.
2. **키가 있다.** 모든 새 옵션은 `furnace.properties`(바닐라와 겹치는 것은 `server.properties`)의 키로 ON/OFF 또는 수치를 바꿀 수 있다. 코드 상수나 `-D` 시스템 속성만으로 두지 않는다(`-D` 는 디버그·시험용만).
3. **엄격하게 읽는다.** `true`/`false` 가 아닌 글자, 숫자가 아닌 값, 범위 밖 값은 조용히 기본값으로 돌리지 않고 어느 키가 문제인지 말하며 기동을 멈춘다(`OptionKeys.strictBool` 등). 조정(clamp)해서 쓰는 경우에는 조정했다고 경고한다.
4. **위험하면 경고한다.** 시작 로그에 한 줄씩 `WARN` — 대상: 보안을 푸는 값, 바닐라와 다른 게임 속도, 운영에 맞지 않는 개발용 값(`OptionKeys.audit`).
5. **오타는 알려 준다.** 표에 없는 키는 "무시합니다 — '○○' 를 말한 건가요?" 로 경고한다. 바닐라 키(`server-name` `port` `compression-threshold` `view-distance` `level-name` `level-seed`)는 `server.properties` 가 정하므로 `furnace.properties` 에 쓰면 따로 무시 경고가 나간다.
6. **한 곳에서 보인다.** `/furnace config` 덤프(O2)가 `OptionKeys.all()` 을 읽어 시작 때 적용된 값·기본값·출처를 함께 보여 준다. 키를 만들고 표에 안 넣으면 덤프에서도 빠지고 오타 경고가 잘못 뜬다 — `ConfigToolsTest.everyKeyAnyLoaderReadsIsInTheTable` 가 로더들의 키를 표와 맞춘다.
7. 새 묶음(B5 라이브러리 내려받기, O4 백업·저장 레인, Q2 RCON 상한, Q3·Q5 …)의 키도 같은 규칙: 기본값=바닐라(또는 꺼짐), 표에 한 줄, 위험 값 경고.

## 2. 키 표 (`furnace.properties`)

| 키 | 종류 | 기본값 | 허용 값 | 설명 | 경고 |
|---|---|---|---|---|---|
| `tick-nanos` | 수 | 50000000 | 1 이상 | 한 틱 길이(나노초). 바꾸면 게임 속도가 바닐라와 달라진다 | 50000000 이 아니면 경고 |
| `entity-view-distance` | 수 | 8 | 1 이상 | 엔티티가 보이는 거리(청크). view-distance 보다 크게 효과 없음 |  |
| `region-section-shift` | 수 | 4 | 1~8 | 동적 region 구역 크기(2^값 청크) |  |
| `region-merge-radius` | 수 | 1 | 1 이상 | region 합치기 반경(구역 단위) |  |
| `region-threads` | 수 | CPU 수 | 1 이상 | region 작업 스레드 수(공유 풀이면 서버 전체, 끄면 차원마다) | 워커가 코어의 2배를 넘으면 경고 |
| `shared-region-pool` | ON/OFF | true | true|false | 모든 차원·추가 월드가 region 워커 풀 하나를 쓴다(Q5). false 면 차원마다 region-threads 개 | -D 와 충돌하면 경고 |
| `region-pool-dim-cap` | 수 | 0 | 0 이상 | 다른 차원 큐에 일이 있을 때 한 차원이 쓸 수 있는 워커 상한(0 = 워커 수−1) | 공유 풀이 꺼져 있으면 쓰이지 않는다고 경고 |
| `region-pool-starve-ticks` | 수 | 2 | 1 이상 | 차원의 가장 이른 일이 이 틱 수만큼 밀리면 그 차원을 먼저 돌린다 | 공유 풀이 꺼져 있으면 쓰이지 않는다고 경고 |
| `io-threads` | 수 | 4 | 1 이상 | 청크 입출력 스레드 수 |  |
| `autosave-ticks` | 수 | 6000 | 1 이상 | 자동 저장 간격(틱) |  |
| `chunk-eviction-grace-ticks` | 수 | 100 | 1 이상 | 쓰지 않는 청크를 내리기 전 유예(틱) |  |
| `spawn-chunk-radius` | 수 | 2 | 0 이상 | 스폰 청크 반경 |  |
| `chunks-per-tick` | auto\|수 | auto | auto 또는 1 이상 | 플레이어당 틱마다 보내는 청크 수. auto = 클라이언트가 알린 만큼(최대 64, 바닐라), 준비된 청크만 배치로. 수 = 그 수로 고정(예전 동작, 예전 기본 16) |  |
| `velocity-secret` | 글자 | (없음) | - | 비어 있지 않으면 Velocity 모던 포워딩을 켠다(프록시와 같은 비밀값) |  |
| `bungeecord` | ON/OFF | false | true|false | BungeeCord 레거시 IP 포워딩. velocity-secret 과 함께 쓸 수 없다 | online-mode 조합 경고 |
| `chat.strict-order` | ON/OFF | true | true|false | true(바닐라): 순서가 어긋난 채팅이면 서명 체인을 끊는다. false: 그 메시지만 버린다 |  |
| `chat.unsigned-as-system` | ON/OFF | false | true|false | 채팅을 서명 없는 시스템 메시지로 보낸다(채팅 신고 불가) | true 면 경고 |
| `server-brand` | 글자 | Furnace | - | F3 서버 브랜드와 level.dat ServerBrands |  |
| `announce-sleep-status` | ON/OFF | true | true|false | 자는 사람 수·밤 건너뛰기 액션바 |  |
| `commands.send-namespaced` | ON/OFF | true | true|false | 바닐라 명령의 minecraft:이름 별칭을 접속자 명령 트리·자동완성에 보낸다(Paper 와 같다). false 면 안 보여도 입력하면 실행된다 |  |
| `gravity-block-portal-dupe` | ON/OFF | true | true|false | 엔드 포털로 넘어간 중력 블록(모래·자갈 등)이 제자리에도 놓이는 바닐라 복제 현상. false 면 복제되지 않는다(바닐라와 달라짐) |  |
| `motd-format` | plain|legacy|markup | plain | plain|legacy|markup | server.properties motd 해석 방식(plain=바닐라) |  |
| `confirm-interrupt` | ON/OFF | false | true|false | 콘솔 Ctrl+C 를 두 번 눌러야 종료 |  |
| `confirm-interrupt-seconds` | 수 | 5 | 1~60 | 두 번째 Ctrl+C 를 기다리는 초 |  |
| `connection.read-timeout-seconds` | 수 | 30 | 1~3600 | 읽기 무응답 제한(초) |  |
| `limits.chat-length` | 수 | 256 | 1~256 | 채팅 최대 글자 수(바닐라 256) |  |
| `limits.command-length` | 수 | 256 | 1~256 | 명령 최대 글자 수(바닐라 256) |  |
| `limits.item.display-name` | 수 | 0 | 0~1000000 | 아이템 표시 이름 글자 수 상한(0 = 바닐라, 상한 없음) |  |
| `limits.item.item-name` | 수 | 0 | 0~1000000 | 아이템 기본 이름 글자 수 상한(0 = 상한 없음) |  |
| `limits.item.lore-line` | 수 | 0 | 0~1000000 | 아이템 설명 한 줄 글자 수 상한(0 = 상한 없음) |  |
| `limits.item.book-page` | 수 | 0 | 0~1000000 | 책 한 쪽 글자 수 상한(0 = 상한 없음) |  |
| `limits.item.book-author` | 수 | 0 | 0~1000000 | 책 저자 글자 수 상한(0 = 상한 없음) |  |
| `proxy-protocol` | ON/OFF | false | true|false | PROXY 프로토콜(HAProxy v1/v2) 헤더로 실제 클라이언트 주소를 받는다 | trusted 가 비면 경고 |
| `proxy-protocol.trusted` | 목록 | (없음) | IP/CIDR,... | PROXY 헤더를 받아 줄 앞단 주소(비면 모두 — 위험) |  |
| `plugins.fail-fast` | ON/OFF | false | true|false | 플러그인이 하나라도 실패하면 서버 시작을 멈춘다 |  |
| `plugins.data-root` | 경로 | (없음) | - | 플러그인 데이터 폴더 뿌리(비면 plugins/<이름>/) |  |
| `plugins.dev-mode` | ON/OFF | false | true|false | 개발 모드 — 플러그인 핫 리로드를 허용한다 | true 면 경고 |
| `plugins.dev-mode.auto-reload` | ON/OFF | false | true|false | 개발 모드에서 jar 가 바뀌면 자동으로 다시 읽는다 |  |
| `worlds.autoload` | 목록 | (없음) | 이름,... | 시작 때 불러올 추가 월드 |  |
| `worldgen-hooks.overworld` | 키 | (없음) | namespace:path | 오버월드에 걸 플러그인 월드젠 훅(비면 바닐라, `docs/api/worldgen-hooks.md`) |  |
| `worldgen-hooks.the_nether` | 키 | (없음) | namespace:path | 네더에 걸 플러그인 월드젠 훅(비면 바닐라) |  |
| `worldgen-hooks.the_end` | 키 | (없음) | namespace:path | 엔드에 걸 플러그인 월드젠 훅(비면 바닐라) |  |
| `enchantments.disabled` | 목록 | (없음) | id,... | 끌 마법부여 id(예: minecraft:mending) |  |
| `io-uring` | ON/OFF | false | true\|false | 리눅스 io_uring 으로 접속을 받는다(못 쓰면 경고 후 epoll/NIO) |  |
| `rcon.max-clients` | 수 | 16 | 1 이상 | RCON 동시 연결 수 상한 | 낮추거나 올리면 경고(StartupWarnings) |
| `rcon.max-clients-per-ip` | 수 | 4 | 1 이상 | RCON 주소 하나당 동시 연결 수 상한 | 위와 같음 |
| `rcon.max-fails-per-connection` | 수 | 5 | 1 이상 | RCON 연결 하나당 인증 실패 허용 수 | 위와 같음 |
| `rcon.max-fails-per-ip` | 수 | 10 | 1 이상 | RCON 주소 하나당 60초 인증 실패 허용 수 | 위와 같음 |
| `metrics.prometheus.enabled` | ON/OFF | false | true\|false | Prometheus 형식 메트릭을 HTTP GET /metrics 로 내보낸다 |  |
| `metrics.prometheus.bind` | 글자 | 127.0.0.1 | 주소 | 메트릭 HTTP 바인딩 주소 | 루프백이 아닌데 토큰이 없으면 경고 |
| `metrics.prometheus.port` | 수 | 9225 | 0~65535 | 메트릭 HTTP 포트(0 = 빈 포트) |  |
| `metrics.prometheus.token` | 글자 | (없음) | - | 비어 있지 않으면 Bearer 토큰이 맞아야 응답 |  |
| `metrics.jmx.enabled` | ON/OFF | false | true\|false | 같은 값을 JMX MBean 으로 등록 |  |
| `idle-pause.empty-seconds` | 수 | 0 | 0~86400 | 접속자가 없으면 이 초 뒤 게임 틱을 멈춘다(0 = 끔, 바닐라) | 켜면 경고 |
| `idle-pause.flush-caches` | ON/OFF | true | true\|false | 일시정지할 때 캐시를 비운다 |  |
| `backup.interval-minutes` | 수 | 0 | 0~525600 | 주기 백업 간격(분, 0 = 끔) | 5분 미만이면 경고 |
| `backup.directory` | 경로 | (없음) | - | 백업 폴더(비면 backups) |  |
| `backup.keep` | 수 | 10 | 0~100000 | 남길 백업 수(0 = 지우지 않음) | 0 이면 경고 |
| `backup.format` | 글자 | zip | zip\|folder | 백업 형식 |  |
| `backup.skip-if-no-players` | ON/OFF | true | true\|false | 접속자가 없었으면 백업을 건너뛴다 |  |
| `backup.hold-timeout-seconds` | 수 | 10 | 1~600 | 백업 기준점에서 저장을 기다리는 최대 초 |  |
| `plugins.compat.bukkit` | ON/OFF | true | true\|false | Bukkit·Paper 플러그인(plugin.yml) 호환 어댑터 — 끄면 plugin.yml jar 를 읽지 않는다(호환 모듈이 있을 때) |  |
| `plugins.compat.nms.enabled` | ON/OFF | true | true\|false | Bukkit 호환 플러그인을 올릴 때 NMS·CraftBukkit 이름 변환(CraftBukkit 버전 접미사 v1_21_R1 풀기)을 한다 — 끄면 그런 플러그인은 그 이름에서 실패한다 |  |
| `plugins.compat.nms.strict` | ON/OFF | false | true\|false | 미지원 NMS·CraftBukkit 심볼을 쓰는 Bukkit 호환 플러그인은 불러오지 않는다(끄면 그 호출만 실행 때 실패하고 나머지는 돈다) |  |
| `plugins.compat.nms.version-package` | 글자 | plain | plain\|versioned | 플러그인이 읽는 CraftBukkit 패키지 이름 — plain 은 Paper 처럼 접미사 없음, versioned 는 Spigot 처럼 v1_21_R1 을 붙인다(두 이름 모두 클래스는 풀린다) |  |
| `plugins.compat.nms.spigot-mappings` | 글자 | off | off\|download\|files | Spigot 이름으로 컴파일된 NMS 플러그인(매니페스트에 `paperweight-mappings-namespace: mojang` 이 없는 jar)의 NMS 이름을 Mojang 이름으로 바꿔 올린다(H7d). download: 그런 플러그인이 있을 때만 Mojang·Spigot 매핑을 처음 한 번 받아 캐시에 둔다. files: 받지 않고 아래 키의 파일을 쓴다. off: 바꾸지 않는다(그 NMS 호출은 실패) |  |
| `plugins.compat.nms.spigot-mappings.files` | 글자 | (빈칸) | 경로 구분자로 이은 세 파일 | files 모드의 Mojang server.txt, Spigot 클래스 csrg, Spigot 멤버 csrg 경로 |  |
| `plugins.chunk-tickets.max-per-plugin` | 수 | 16384 | 1~100000000 | 플러그인 하나가 청크 티켓으로 붙잡을 수 있는 청크 수 상한(겹침 포함) |  |
| `plugins.compat.scheduler.async-threads` | 글자 | platform | platform\|virtual | Bukkit 비동기 작업을 도는 스레드. platform: 이름 붙은 플랫폼 스레드 풀(Paper 모양), virtual: 가상 스레드 | 잘못된 값은 기동 거부 |
| `plugins.compat.libraries` | 글자 | needed | needed\|all\|off | Paper 가 싣는 라이브러리(Guava·Adventure·SnakeYAML·Brigadier 등)를 호환 플러그인 클래스가 쓸 때만(needed)·호환 플러그인이 있으면 전부(all) 받아 공유 로더에 붙인다(H4 묶음 B, `docs/api/plugin-libraries.md`). Guava·SnakeYAML 은 서버 jar 에 실려 받지 않고 꺼낸다 | 잘못된 값은 경고 후 needed |
| `plugins.compat.libraries.mojang` | ON/OFF | true | true\|false | Brigadier·authlib·DataFixerUpper 를 Mojang 저장소에서 받는다 | off 인데 적혀 있으면 경고 |
| `plugins.compat.libraries.declared` | ON/OFF | true | true\|false | 호환 플러그인 plugin.yml 의 libraries 를 Paper·Spigot 처럼 받는다 |  |
| `plugins.libraries.enabled` | ON/OFF | false | true\|false | 플러그인 설명 파일의 libraries 를 Maven Central 에서 받아 붙인다 | 켜면 안내 |
| `plugins.libraries.offline` | ON/OFF | false | true\|false | 캐시에 있는 라이브러리만 쓴다(Furnace·호환 플러그인 내려받기 모두) | 내려받기가 모두 꺼졌는데 적혀 있으면 경고 |
| `plugins.libraries.transitive` | ON/OFF | true | true\|false | 라이브러리의 compile/runtime 의존까지 받는다 | 위와 같음 |
| `plugins.libraries.require-checksum` | ON/OFF | false | true\|false | 체크섬 기록이 없는 jar 는 받지 않는다 | 위와 같음 |
| `plugins.libraries.cache` | 경로 | (없음) | - | 받은 라이브러리 폴더(비면 plugins/.libraries) |  |
| `plugins.libraries.timeout-seconds` | 수 | 30 | 1~600 | 라이브러리 요청 하나의 제한 시간(초) |  |
| `plugins.libraries.max-size-mb` | 수 | 64 | 1~1024 | 라이브러리 파일 하나의 크기 상한(MB) |  |
| `config-version` | 수 | 1 | 1 이상 | 설정 파일 형식 버전. 키 이름이 바뀌면 서버가 파일을 옮기고 이 값을 올린다(원본은 `.bak`) | 서버보다 새 버전이면 경고 |
| `config.preset` | 글자 | none | none\|vanilla\|performance | 파일에 없는 키를 프리셋 값으로 채운다(파일에 적은 키가 이긴다) |  |
| `jvm.check` | ON/OFF | true | true\|false | 기동 때 Java 버전·힙·GC 옵션을 점검해 경고한다 |  |
| `console.jline` | ON/OFF | true | true\|false | 콘솔 줄 편집·자동완성·기록(터미널이 아니면 자동으로 꺼진다) |  |
| `console.history-size` | 수 | 500 | 0~100000 | 콘솔 명령 기록 줄 수(0 = 파일에 저장 안 함) |  |
| `connection.<단계>.packets-per-second` | 수 | 200 | 0~1000000 | 접속 단계(`handshake` `status` `login` `configuration` `play`)별 초당 패킷 평균 상한, 0 = 제한 없음 | 0 이면 경고 |
| `connection.<단계>.packets-burst` | 수 | 400 | 1~10000000 | 단계별 순간 패킷 상한 | |
| `connection.<단계>.max-frame-bytes` | 수 | 단계별(play 2097152) | 16~2097152 | 단계별 한 패킷 최대 크기, 넘으면 조정 | 조정 시 경고 |
| `connection.<단계>.timeout-seconds` | 수 | login 30, 나머지 0 | 0~86400 | play 를 뺀 단계별 제한 시간(초) | |

접두 가족(이름만 다르다): `spawn.limit.<분류>` · `spawn.player-limit.<분류>`(몹 스폰 상한), `plugins.log-level.<플러그인>`(JUL 수준 이름, 틀린 값은 경고하고 무시), `dimension-seed.the_nether` / `dimension-seed.the_end`(비우면 주 월드 시드 — 바닐라).

## 3. R4 대상 항목별 점검 결과

| 번호 | 항목 | 켜는 곳 | 상태 |
|---|---|---|---|
| 47 | zstd 지역 파일 | `server.properties` `region-file-compression=zstd` (바닐라 키, 값 `deflate`/`gzip`/`lz4`/`none`/`zstd`) | 키 있음. 기본 `deflate`. zstd 면 시작 로그에 "바닐라·Paper 는 읽지 못한다" 경고 |
| 63 | 채팅 신고 끄기 | `chat.unsigned-as-system` | 키 있음, 기본 false, true 면 경고 |
| 64 | 순서 어긋난 채팅 킥 끄기 | `chat.strict-order` | 키 있음, 기본 true(바닐라) |
| 95 | 서버 브랜드 · 잠자기 메시지 · Ctrl+C 확인 | `server-brand` · `announce-sleep-status` · `confirm-interrupt`(+`confirm-interrupt-seconds`) | 키 있음, 기본은 바닐라 |
| 97 | 주기 백업 | (아직 없음) | O4 에서 구현 — 이 규칙대로 키와 표 한 줄을 함께 넣는다 |
| 103 | 길이 한도 | `limits.chat-length` `limits.command-length` `limits.item.*` | 키 있음, 기본은 바닐라(채팅·명령 256, 아이템 상한 없음) |
| 104 | PROXY 프로토콜 | `proxy-protocol` · `proxy-protocol.trusted` | 키 있음. **이번에 추가:** trusted 가 비면 경고 |
| N2 | 접속 단계별 한도 | `connection.<단계>.*` · `connection.read-timeout-seconds` | 키 있음. **이번에 추가:** 속도 제한/로그인 시간 제한을 0 으로 끄면 경고 |
| B5 | 라이브러리 내려받기 | (아직 병합 전) | 규칙에 따라 키(기본 꺼짐)와 표 한 줄을 넣는다 |

### 이번에 고친 것
- **오타 키 경고 신설.** `ConnectionLimits.knownKeys()` 는 "오타 경고용"이라고 적혀 있었지만 쓰는 곳이 없었다. 이제 모든 키가 한 표에 모여 기동 때 확인한다.
- **플러그인 키 엄격화.** `plugins.fail-fast` `plugins.dev-mode` `plugins.dev-mode.auto-reload` 는 `Boolean.parseBoolean` 이라 `ture` 같은 오타가 조용히 `false` 가 됐다. 다른 키처럼 잘못된 값이면 키 이름을 밝히며 기동이 멈춘다.
- **경고 추가:** `proxy-protocol` trusted 없음, `plugins.dev-mode`, `tick-nanos` ≠ 50000000, 패킷 속도 0, 로그인 제한 시간 0.

### 키가 아닌 것(남긴 것)
- `-Dfurnace.sharedRegionPool`(S4 공유 풀) — Q5 에서 키 `shared-region-pool`(기본 true)로 올렸다. `-D` 는 되돌리기 스위치로 남아 파일 값보다 우선한다(`shared-scheduler-default-2026-11-07.md`).
- `-Dfurnace.console.colors` `-Dfurnace.timer.spin` `-Dfurnace.network.compressionLevel` `-Dfurnace.region.compressionLevel` `-Dfurnace.io.backlogLimit`, 그리고 `furnace.watch` 등 월드젠 디버그용 `-D` — 성능 조정·디버그용으로 키를 만들지 않는다. 운영자가 바꿔야 할 일이 생기면 규칙 2 에 따라 키로 올린다.

## 4. `spawn-animals=false` / `spawn-npcs=false` 바닐라 대조

바닐라 1.21.1 서버 바이트코드(`tools/vanilla-data/server-1.21.1`, `javap -c`)와 다시 대조했다.
- `DedicatedServerProperties` 가 `spawn-animals`·`spawn-npcs` 를 읽고, `DedicatedServer.isSpawningAnimals()`·`areNpcsEnabled()` 가 그 값을 돌려준다.
- 소비처는 `ServerLevel.shouldDiscardEntity(Entity)` 하나다: `!isSpawningAnimals() && (e instanceof Animal || e instanceof WaterAnimal)` 이면 지우고, 아니면 `!areNpcsEnabled() && e instanceof Npc` 이면 지운다. 나머지는 `false`.
- Furnace `ChunkLevel.shouldDiscardEntity` 는 같은 논리다(Animal·WaterAnimal, 그리고 `Npc` 인 `Villager`·`WanderingTrader`). 틱 루프에서 이미 있는 개체를 지우는 위치도 같다.
- 결론: **바닐라와 결과가 같다**(결정: 같으면 OK). 바꾼 것 없음.
