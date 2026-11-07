# 설정 도구 묶음 (O2, 2026-11-07)

대상 행: 91(실제 적용값 덤프·검증), 92(프리셋·버전 마이그레이션), 94(JVM 점검), 96(콘솔 줄 편집·자동완성), 98(설정값 환경 변수 치환).
키 표와 규칙은 [option-keys-2026-11-07.md](option-keys-2026-11-07.md). 새 키는 전부 `furnace.properties` 에서 바꿀 수 있다.

## 1. 설정 입구 `FurnaceProps`

`furnace.properties` 를 읽는 모든 로더(`ConfigLoader` `OperatorOptions` `ConnectionLimits` `RconLimits` `PluginSettings` `WorldSettings` `TransportOptions` `MetricsOptions` `MaintenanceOptions` `EnchantmentToggles` `OptionKeys.audit`)가 `FurnaceProps.read(path)` 한 곳을 거친다.
순서: 파일 읽기 → 환경 변수 치환 → `config.preset` 으로 빈 키 채우기. 로더마다 각자 `Properties.load` 하던 것을 걷어 냈으므로 새 로더도 이 입구를 써야 한다.

## 2. 환경 변수 치환 (98)

- 값 안의 `${NAME}` 은 환경 변수, `${NAME:-기본값}` 은 변수가 없거나 비었을 때 기본값. 글자 그대로 `${` 를 쓰려면 달러 두 개 + `{`(`$${`).
- 환경 변수가 없고 기본값도 없으면 **어느 키가 어느 변수를 가리키는지 말하며 기동을 멈춘다**(조용히 빈 비밀번호로 켜지 않는다). `${` 를 열고 안 닫아도 멈춘다.
- `furnace.properties` 에만 적용한다. `server.properties` 는 서버가 값을 바꿔 다시 저장할 수 있어(`store`) 치환하면 비밀값이 파일에 평문으로 되쓰인다 — 그래서 일부러 뺐다. `rcon.password` 는 `server.properties` 에 있으므로 환경 변수로 못 넣는다(필요하면 알려 주세요).
- 덤프는 환경 변수에서 온 값을 "환경 변수에서" 로 표시하고 비밀값은 가린다.

## 3. `/furnace config [all|키 일부]` (91)

권한 3. 서버가 **시작할 때 실제로 적용한** 값을 보여 준다.
- 기본: 기본값과 다른 키만(`*` 표시, 괄호에 기본값, 대괄호에 출처 — 파일 몇 번째 줄 / 프리셋 / 환경 변수).
- `all`: 전체 키 표. `/furnace config backup`: 키에 `backup` 이 든 것만(전체 중에서). Tab 자동완성은 `all` 과 모든 키.
- 모르는 키(오타)를 줄 번호와 "○○ 를 말한 건가요?" 로, 위험한 값 경고도 같이 보여 준다.
- `velocity-secret` `metrics.prometheus.token` 은 `****(N자)` 로 가린다.
- 시작 뒤에 파일이 바뀌었으면 "고친 내용은 재시작해야 적용된다" 고 알린다(바뀐 값을 읽어 오지는 않는다 — 돌고 있는 값이 아니기 때문).
- 시작 로그의 오타 키 경고에도 줄 번호가 붙는다.
- 이번에 키 표(`OptionKeys`)에 빠져 있던 키(`backup.*` `metrics.*` `plugins.libraries.*` `rcon.*` `idle-pause.*` `io-uring` `motd-format`)를 채웠다. 이 키들은 전부 "모르는 키" 경고를 잘못 받고 있었다.

## 4. 형식 버전 마이그레이션과 프리셋 (92)

- `config-version`(없으면 1). 서버의 `ConfigMigration.CURRENT`(지금 1)보다 낮으면 `STEPS` 의 단계(키 이름 바꾸기·없어진 키)를 차례로 적용해 파일을 **줄 단위로** 고친다(주석·순서 보존, 줄바꿈은 LF 로 통일). 고치기 전에 `furnace.properties.v<옛버전>.bak` 로 복사한다. 새 키가 이미 있으면 덮어쓰지 않고 옛 줄을 주석 처리한다. 서버보다 새 버전이면 아무것도 안 하고 경고만.
- 지금까지 키 이름을 바꾼 적이 없어 `STEPS` 는 비어 있다. 키 이름을 바꿀 때 `CURRENT` 를 올리고 `Step(from, renames, removes)` 한 줄을 더하면 된다.
- `config.preset=none|vanilla|performance`: **파일에 없는 키만** 프리셋 값으로 채운다(적은 키가 이긴다).
  - `vanilla`: 바닐라 동작에 영향을 주는 키(`tick-nanos` 채팅 순서·서명, `limits.*`, `plugins.dev-mode`, `idle-pause.empty-seconds`)를 바닐라 값으로.
  - `performance`: `entity-view-distance=6`, `chunk-eviction-grace-ticks=40`. 멀리 있는 몹이 클라이언트에 안 보이는 등 바닐라와 조금 달라지므로 켠 사람만 받는다. 값은 보수적으로 잡았다 — 실측해서 더 넣으려면 `ConfigPresets` 한 곳.

## 5. JVM 점검 (94, `jvm.check` 기본 켜짐)

기동 때 한 번 경고만 한다(동작은 안 바꾼다): Java 21 미만 / LTS(21·25) 아님, 최대 힙 1GB 미만, `-XX:+UseSerialGC`·`UseParallelGC`·`UseEpsilonGC`, CPU 1개, `-Xms` < `-Xmx`. 끄려면 `jvm.check=false`.

## 6. 콘솔 줄 편집·자동완성·기록 (96, `console.jline` 기본 켜짐)

- JLine 3.26.3(`org.jline:jline`, fat jar 에 들어간다). 대화형 터미널이면 화살표 이동·지우기·위/아래 기록·**Tab 자동완성**(플레이어와 같은 명령 트리를 콘솔 권한으로 — 서버가 준비된 뒤부터)이 된다. 기록은 `.console_history`(`console.history-size`, 0 이면 파일에 안 남김).
- 입력이 파이프·파일이거나(헤드리스, CI, 봇 시험) 터미널을 못 만들면 자동으로 예전처럼 표준 입력을 줄 단위로 읽는다. 라이브러리/네이티브 문제도 경고만 하고 되돌아간다. `console.jline=false` 로 끌 수 있다.
- 로그는 입력 줄 위에 찍힌다(logback `RoutedConsoleAppender` → `ConsoleBridge` → `printAbove`). 치던 글자가 로그에 섞이지 않는다.
- Ctrl+C: 터미널이 신호를 만들지 않으므로 편집기가 받아 `InterruptConfirm` 규칙(`confirm-interrupt`)을 똑같이 적용한다. Ctrl+D 는 콘솔 입력만 닫는다.
- 자동완성은 전역 스레드에서 1.5초 안에 응답이 없으면 후보 없이 돌아온다(입력이 멈추지 않게).

## 시험

`ConfigToolsTest`(치환·프리셋·마이그레이션·덤프·JVM·키 표 완전성), `FurnaceAdminCommandsTest.configCommandDumpsAppliedValuesAndCompletesKeys`(실제 서버에서 명령 실행·비밀값 가림·자동완성). 컨테이너에서 pty 로 실제 jar 를 띄워 확인: 프롬프트 `> ` 유지, 로그가 줄 위에 찍힘, Ctrl+U, `furnace config` 덤프, `furnace config ba`+Tab → `backup.` 후보 목록, `fur`+Tab → `furnace`, 환경 변수 없을 때 기동 중단 메시지, JVM 경고 3종.
