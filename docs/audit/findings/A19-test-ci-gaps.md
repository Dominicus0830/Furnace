# A19 시험·CI 공백 조사 보고서 (2차 점검, 1단계)

- **기준 커밋**: `claude/quirky-wright-lra834` `eab009d9`(PR #180 병합 뒤). 조사일 2026-11-08~11.
- **조사한 범위**: 모든 모듈의 시험 배치(소스 줄·파일 수), 이름 참조 기반 패키지별 시험 공백(`A19-repro/refs.py.txt`), 단언 없는 시험 검색(`noassert.py.txt`), `.github/workflows/ci.yml`·`docs/ci.md`·`tools/ci/shard-balance.py`, 최근 CI 실행 기록(run 917~946, GitHub Actions API), 흔들리는 시험 로컬 반복, 제한시간 시험, `tools/bot-test` 127개 스크립트 분류, `furnace-test` 헤드리스 서버 활용 가능성.
- **조사하지 못한 범위(이유)**: ① **JaCoCo 줄·가지 커버리지 표는 못 냈다.** 4코어 컨테이너에서 서버 전체 시험이 2 워커로 36분 넘게 걸리다 세션 중단으로 끝나지 못했다. 그래서 줄 커버리지 대신 "시험 소스에 클래스 이름이 한 번이라도 나오는가"(근사)를 썼다. 다음 조사자는 `jacoco.init.gradle`(아래 4장)로 CI 러너나 비어 있는 컨테이너에서 한 번 돌리길 권한다. ② 흔들리는 시험 50회 반복은 WorldBorderJoinTest 한 개만 80회 했다(0실패). 다른 시험은 CI 기록에서 이름만 모았다. ③ 과거 CI 로그 전체 파싱은 하지 않았다(로그 API 출력이 너무 커서 최근 실행 1건의 로그만 읽었다).
- 입력 문서: `docs/ci.md`, `docs/api/testing.md`, 독립 검토 `pr127-review.md`·`pr131-review.md`(5장 색인, 이미 지적된 것은 다시 올리지 않았다).

## 1. 요약

| ID | 심각도 | 한 줄 |
|---|---|---|
| A19-01 | 중간 | 시험 기본 제한시간이 없다. 멈춘 시험은 35분 작업 제한까지 가고, 그때는 보고서도 안 올라온다 |
| A19-02 | 중간 | 재시도 1회가 확률로 실패하는 시험을 가린다(실패율 1/6이면 빨강 확률 약 2.8%). "흔들린 시험" 목록은 아무 데서도 안 보인다 |
| A19-03 | 중간 | 시험 이름이 안 나오는 큰 패키지: `block/types`, `entity/ai`, `datafix`, 구조물 생성, `command/builtin` |
| A19-04 | 낮음 | 단언 없는 벤치마크·진단 시험이 CI 매 실행에 돈다(약 13개) |
| A19-05 | 낮음 | 시간 의존 구조: `Thread.sleep` 192곳, 벽시계 대기 600여 곳. 몹·레벨 난수를 한꺼번에 고정하는 도우미는 `LandMobTest.seed` 뿐(나머지는 시험마다 따로 `setSeed`) |
| A19-06 | 낮음 | 문서만 바꾼 PR 도 서버 시험 3조각(5~7분)을 전부 돌린다. 조각 시간이 7:08/6:15/5:07 로 기운다 |
| A19-07 | 참고 | 봇 시험 127개는 CI 에 없다. 분류표(5장)로 회귀 시험 후보를 정했다 |
| A19-08 | 참고 | `furnace-test` 헤드리스 서버를 서버 시험에 더 쓸 수 있는가: 이벤트·명령·틱은 가능, 패킷·저장·인벤토리 클릭은 불가 |

치명 0 / 높음 0 / 중간 3 / 낮음 3 / 참고 2.

## 2. 항목

### A19-01 시험 기본 제한시간 없음 — 중간
- **현상**: `furnace-server/src/test/resources/junit-platform.properties` 에는 `extensions.autodetection` 한 줄뿐이고 `@Timeout` 은 2개 파일(4곳)에만 있다. 멈춘(교착·무한 반복) 시험은 `ci.yml` 의 `timeout-minutes: 35` 에서야 끝난다. 이 작업 제한에서 취소되면 `if: failure() || steps.server-test.outcome == 'failure'` 보고서 올리기 단계는 돌지 않는다(`failure()` 는 취소를 실패로 치지 않는다 — GitHub 문서 기준, **실제 취소 실행으로는 확인 못 함**).
- **왜 중요한가**: 1차 점검에서 region 스레드 무한 반복(A07-01)·종료 교착(A03)류가 나왔다. 그런 회귀가 CI 에 들어오면 35분을 태우고 보고서 산출물이 안 올라오고, 단계 로그에서 멈춘 시험을 찾기도 어려울 수 있다.
- **재현**: `A19-repro/HangTimeoutRepro.java.txt`. 10초 자는 시험이 기본 설정에서 통과하고, `junit.jupiter.execution.timeout.default=3s` 를 넣으면 `FAILED`(timed out after 3 seconds)가 된다(실측).
- **제안(작음, Haiku low)**: `junit.jupiter.execution.timeout.default=5m`(월드 생성 시험이 가장 느리다 — 정상 최장 시험 시간을 XML 에서 재서 정한다)와 `junit.jupiter.execution.timeout.thread.mode.default=SEPARATE_THREAD`. 무한 반복은 인터럽트로 안 멈추므로 제한 초과 시 JVM 이 죽는지 한 번 확인할 것. 보고서 올리기 단계에 `always()` 추가.

### A19-02 재시도가 확률 실패를 가리고 기록은 안 보인다 — 중간
- **현상**: 재시도는 실패한 시험 클래스를 한 번 더 돌린다(pr127 검토 2번에서 "설계상 감수"로 받아들였다). 그런데 1차 점검이 낸 확률 시험은 이 설계에서 거의 잡히지 않는다: 계산(실패율 p 이면 둘 다 실패할 확률 p², 두 실행이 독립이라는 가정 — 부하와 상관된 흔들림이면 빨강 확률은 더 높다) — `ReviewA03StepMergeTest` 3/18(p≈0.17) → 빨강 3%, `A04RealSocketDupeTest` 24/60(p=0.4) → 빨강 16%. **이 시험들을 CI 회귀 시험으로 그대로 옮기면 회귀가 들어와도 대부분 초록이다.** `first-wave-check.md` 4장이 결정적 시험으로 줄이라고 한 이유가 이것이다.
- **기록이 안 보인다**: pr127 검토가 제안한 `::warning title=흔들린 시험::` 주석과 시험 수 합계 출력은 `ci.yml` 에 반영되지 않았다(현재 머리말 `::warning::` 1줄과 Step Summary 만). 실제 사례: run 943(문서만 바꾼 PR #180)의 조각 3/3 에서 `WorldBorderJoinTest#joinSendsLiveBorderAndChangesBroadcast` 가 처음 실패→재시도 통과했는데 PR 화면에는 초록뿐이다. 이 시험은 `docs/ci.md` 의 흔들린 시험 표에 없는 **새 이름**이다.
- **재현 시도**: `A19-repro/WorldBorderJoin-flake-notes.txt` — 로컬 4코어에서 `@RepeatedTest(40)` 부하 없이·바쁜 루프 8개와 함께 총 80회 0실패. 원인 미확인(가설은 같은 파일).
- **제안(작음)**: ① 재시도 통과 시험마다 `::warning title=흔들린 시험::` 한 줄, ② 기준 브랜치 실행에서 흔들린 시험 이름을 쌓는 산출물(또는 `docs/ci.md` 표 갱신 규칙) — 두 번 이상 나온 시험은 고칠 대상으로, ③ 회귀 시험으로 넣는 재현 시험은 **결정적**이어야 한다는 규칙을 `docs/api/testing.md` 나 점검 계획서에 한 줄. ④ `WorldBorderJoinTest` 는 접속 직후 보더 방송 대상이 되는 시점을 A11 쪽에서 확인(서버 경합일 수 있음).

### A19-03 시험이 안 보이는 패키지 — 중간
이름 참조 근사(`refs.py.txt`, 운영 클래스 단순 이름이 시험 소스에 한 번도 안 나오는 비율). **줄 커버리지가 아니다** — 아래 숫자는 "최소한 이 클래스를 직접 쓰는 시험이 없다"는 뜻이고, 상위 클래스를 통해 간접으로 도는 코드는 못 센다.

| 패키지 | 클래스 | 이름 나옴 | 이름 안 나오는 줄수 | 대표 |
|---|---|---|---|---|
| `block/types` | 288 | 14% | 12,046 | PointedDripstone, Fire, Vine, GrowingPlant |
| `entity/ai` | 230 | 26% | 9,228 | WalkNodeEvaluator, PathNavigation, LongJumpToRandomPos, FlyNodeEvaluator |
| `world/gen` 전체(구조물 51개 포함) | 221 | 47% | 17,072 | 구조물: OceanMonument, NetherFortress, Stronghold, WoodlandMansion |
| `command/builtin` | 54 | 44% | 5,334 | ExecuteConditions, ScoreboardCommand, FunctionCommands |
| `plugin` | 78 | 43% | 6,925 | Protocol767, VirtualEntitiesImpl, PlayerViewsImpl |
| `datafix` | 10 | 30% | 1,985 | ItemStackFixes, BlockEntityFixes, EntityFixes |

- 표에서 뺀 작은 패키지: `world/block` 0%(3클래스), `log` 20%, `block/dispenser` 28%, `command/brigadier` 37%, `command/exec` 40%, `advancement` 41%.
- **근사의 한계**: 이름이 주석·문자열·같은 이름의 다른 클래스에 나와도 "참조됨"으로 세므로 과대 계상도 있고, 간접 실행은 못 센다(양방향 오차). `datafix` 의 "직접 시험 없음"도 이름 기준이다.
- 계획서가 예상한 `players`(86%)·`scores`(91%)·`net/auth`(66%)·`item`(62%)·`world/poi`(57%)는 이름 기준으로는 비어 있지 않다. 작은 패키지라 의미가 적다.
- **`datafix` 가 가장 급하다**: A05-04(옛 판 월드 데이터 픽서 없음)를 고치는 픽서 포팅이 이 패키지에 들어가는데 현재 픽서 세 종류에 직접 시험이 없다. 구조물 생성은 바닐라 대조(oracle)로 따로 지킨다고 알려져 있어(`worldgen-parity.md`) 우선순위는 낮다.
- `entity/ai` 는 `PathNavigation` 이름이 안 나오지만 몹 시험(`LandMobTest` 등)이 간접으로 돈다. 정확한 줄 비율은 JaCoCo 로 확인(못 함).
- **제안(큼 — 나눠서)**: ① JaCoCo 를 CI 기준 브랜치 실행에서 한 번 돌려 표를 확정(Sonnet low), ② `datafix` 픽서별 입력·출력 시험(바닐라 값은 바이트코드 대조, Opus high — A05-04 수정과 같은 스레드), ③ `block/types` 는 모양·틱 동작이 큰 것부터(불·덩굴·점적석) 작게 나눠.

### A19-04 단언 없는 벤치마크·진단 시험 — 낮음
`noassert.py.txt` 로 4,460개 시험 메서드 중 단언·검증 호출이 전혀 없는 42개를 뽑았다. 큰 도우미(`roundTripDistinct` 등)가 대신 단언하는 것은 진짜 단언 없음이 아니므로 빼면 실제로 의심스러운 것은 약 13개다.
- 벤치마크: `ChunkGenBenchmark`(5개), `RegionSectorAllocatorTest.measure_saveThroughput`, `NoiseWorldSourceSurfaceHeightCacheTest.measureBeforeAfterOnRepeatedQuery`, `ChunkMemoryMeasurement.measureRetainedPerColumn`
- 진단·덤프: `TreeFeatureParityDiagnosticTest`(3), `TreeFeatureScanTest`(2), `FeaturePipelineParityTest.dumpStep9Features`·`seagrassDeepScatterTrace`
- 도우미 단언이라 문제 없음: `PaletteStorageTest`, `PlayerDeathDropsRaceTest`(furnace-test), `OracleGroundtruthAuditTest`(assumeTrue 로 건너뜀), `AquiferAirShortcutFuzzTest`, `PluginFoundationTest.earlyConsole…`
- 이들은 CI 에서 매번 돌아 시간을 쓰고(**각 시험 시간은 못 쟀다**) 아무것도 지키지 않는다. 벤치마크는 `@Tag("bench")` 로 빼고 기본 실행에서 제외하는 것이 일반적이다(`furnace.slowTests` 속성 방식이 이미 `AquiferAirShortcutFuzzTest` 에 있다 — 같은 방식 권장). **AGENTS.md 규칙 5("스킵·비활성화로 초록 만들지 않는다")와는 다르다**: 이 시험들은 검증이 아니라 측정이라 기본 실행에서 빼는 것이 규칙 위반이 아니다. 도미니코 확인 권장.
- `GlobalThreadTest.execute_completesFuture` 처럼 "예외 없으면 통과"를 주석으로 적은 시험이 있다. 의도된 것이라 올리지 않는다.

### A19-05 시간·난수 의존 구조 — 낮음
- 시험 소스에서 `Thread.sleep` 192회(74파일; 많은 곳: `SharedSchedulerTest` 12, `ConnectionInputTest` 11, `DimensionChangeTest` 9, `ConnectionStateApiTest` 9, `RconAuthDeadlineTest` 7), `System.nanoTime`·`currentTimeMillis` 348회(78파일), `TimeUnit.…SECONDS)` 인자 줄 614개(121파일, MILLISECONDS 등 포함).
- `setSeed` 는 54곳(29파일; `WardenTest`·`VillagerBrainTest`·`HorseRidingTest` 등이 시험마다 고정). 몹 난수는 시드 없이 `nanoTime` 으로 만들어지므로(`docs/ci.md` 표의 LandMobTest 원인) 고정하지 않은 몹·블록 시험은 같은 위험이 있다. 어느 시험이 고정 안 했는지는 **조사하지 못했다**(몹 클래스별 단독 반복 필요).
- 시험 코드의 플레이어 번호에 `Math.random()` 3곳(`ItemEntityTest`, `PlayerLayersTest`, `EntityViewTrackerTest` 등) — 같은 월드에서 번호가 겹칠 확률 아주 작음, 낮음.
- **제안**: 시간을 기다리는 시험은 `furnace-test` 의 결정적 틱(`tick(n)`)으로 옮길 수 있는지 시험별로 판단(A19-08). 우선순위는 CI 흔들림 기록이 있는 것부터(`docs/ci.md` 표의 시험, 이번에 찾은 `WorldBorderJoinTest`).

### A19-06 CI 구조 — 낮음
- `ci.yml` 은 경로 필터가 없어 문서·`.md` 만 바꾼 PR 도 서버 시험 3조각을 전부 돈다(최근 문서 PR 8건 모두 약 7분). A19 점검 PR 같은 문서 PR 이 많은 이 프로젝트에서는 러너 시간 낭비이자 흔들림 노출이다(run 943 이 문서 PR 에서 흔들렸다). `paths-ignore: ['docs/**', '**.md']` 를 `pull_request` 에 넣되 **`test` 확인이 만들어지지 않으면 병합 판단이 흔들린다**(pr127 검토 C 항목) — 그래서 `test` 작업을 따로 두고 "변경 없음 판정"을 낼지 도미니코와 정해야 한다. 안 바꾸는 쪽이 안전하다.
- 조각 시간(run 943 기준): 조각 1 7:08, 조각 2 6:15, 조각 3 5:07. `shard-balance.py` 로 salt 를 다시 구하면 최대를 6분 안팎으로 줄일 수 있다(약 1분). 시험 수가 늘 때마다 다시 구해야 해 이득이 작다. **시험별 시간 XML 이 필요해 직접 돌려 보지 못했다.**
- 그 밖: 액션이 Node 20 을 쓴다는 경고(`checkout@v4` 등), 작업 끝의 `Terminate orphan process: java`(그레이들 데몬, 무해).
- `light` 의 `:furnace-plugin-test:classes` 는 시험 코드 없이 컴파일만 본다(문서에 이미 적힘).

### A19-07 봇 시험 회귀화 — 참고
`tools/bot-test` 는 JS 127개, CI 어디서도 안 돈다(서버 jar·바닐라 데이터·mineflayer 필요). 분류(스크립트 이름·`README.md` 기준, 내용 전수 확인 아님):

| 묶음 | 예 | 판단 |
|---|---|---|
| 플러그인 API 이벤트·UI(시험 플러그인 필요) | round4·5·8·9·10·11·13·14·15·16 | **`furnace-test` 로 옮길 수 있다**(플러그인 클래스를 `builder().plugin()` 으로, 이벤트·명령·메시지·틱 확인). 가장 이득이 크다 |
| 몹·블록 동작 | entity, round6(브루·돌고래·습격…), round7(전달체), m1·m2·m6·m15 | 서버 시험(`TestServers`·`ArenaLevel`)으로 대부분 가능. 소리·입자 패킷은 `ConcurrentEmbeddedChannel` 로 |
| 프로토콜·접속 | pr2(lz4), pr4(RCON·채널), query, rcon, netjoin | 실제 소켓 시험이 이미 있다(`ConnectionDefenseSocketTest` 등). 봇 쪽은 호환(mineflayer 결과)이 목적이라 수동 유지 |
| 성능·청크 | chunkio, chunkrate, entity perf | CI 에 부적합(시간). A17 이 쓴다 |
| 클라이언트 물리·화면·소리 재생 | ride, round3(mv·horse), snd, vd | 자동화 불가(README 한계) — 사용자 확인 칸 |

### A19-08 헤드리스 시험 서버(`furnace-test`)를 서버 시험에 쓸 수 있나 — 참고
- 이미 쓰인다: `furnace-compat-paper` 시험 36파일, 서버 쪽 사망 드롭·킬 크레딧 시험 8개가 `furnace-test` 모듈에 있다(`PlayerDeathDropsRaceTest` 등). `light` 작업이 `:furnace-test:test`·`:furnace-compat-paper:test` 를 돈다.
- 가능: 결정적 틱(`tick(n)`), 접속 흐름(`join`), 명령, 플러그인 이벤트, 메시지·액션바·플러그인 메시지 수신, `onRegion`·`onGlobal`, 시험 중 시간 의존 제거.
- 불가·불편: 임의 클라이언트 패킷 수신(`TestPlayer` 가 메시지·액션바·플러그인 메시지만 모은다), 인벤토리 클릭(`sendPacket` 로 직접 구성), 디스크 저장·청크 I/O(메모리 월드), 서버 종료 순서(`ShutdownSequence`), 월드 생성기(평지 고정, `generator()` 로 바꿀 수는 있다).
- **결론**: 1차 보고서의 재현 시험을 둘 곳은 다음이 낫다 — A02(소켓·NBT)·A05(저장)·A03(종료)는 `furnace-server` 시험(`TestServers.core` + `@TempDir`), A04 의 이벤트 루프·region 틈은 이미 사망 드롭 시험이 있는 `furnace-test`, A07·A10(틱 범위·스폰)은 `furnace-server` 시험이되 `core.world().viewDistance(10)` 을 먼저 줘야 한다(A10 수정 스레드 기록).
- 의존 방향: `furnace-test` 는 `furnace-server` 를 `api` 로 가지므로 `furnace-server` 시험이 `furnace-test` 를 쓰면 `compileTestJava → furnace-test → furnace-server main` 이라 순환은 아니지만 **직접 시험해 보지 않았다**. 그래서 서버 시험을 `furnace-test` 로 옮기기보다 새 시험을 `furnace-test` 쪽에 두는 지금 방식이 안전하다.

## 3. 이미 검토된 것과의 관계
- pr127(CI 조각·재시도)의 제안 중 반영 안 된 것: 흔들린 시험 `::warning` 주석, 조각별 시험 수 합계, 가드 문구 `Could not complete execution` — A19-02 에 합쳤다.
- pr131(시험 채널 재진입): `ConcurrentEmbeddedChannelTest` 에 순서 보장 시험을 넣자는 제안이 있었다. 이번에 확인 안 했다.

## 4. 재현·측정 도구
- `A19-repro/refs.py.txt`(이름 참조 공백), `noassert.py.txt`(단언 없는 시험), `HangTimeoutRepro.java.txt`, `WorldBorderJoin-flake-notes.txt`.
- JaCoCo 용 gradle init 스크립트(저장소에 커밋하지 않음): `allprojects { if (path in [':furnace-server', ':furnace-api', ':furnace-compat-paper', ':furnace-test']) { afterEvaluate { p -> p.apply plugin: 'jacoco'; p.tasks.withType(JacocoReport).configureEach { reports { xml.required = true; html.required = false } } } } }` 를 `-I jacoco.init.gradle` 로 넘기고 `:furnace-server:test :furnace-server:jacocoTestReport`. 이 컨테이너(4코어)에서는 36분 넘게 끝나지 않았다.

## 5. 수정 제안 묶음(3단계용)
| 묶음 | 내용 | 크기 | 모델 |
|---|---|---|---|
| T1 | 기본 제한시간·보고서 `always()` (A19-01) + 흔들린 시험 주석·기록(A19-02 ①②③) | 작음 | Haiku low / Sonnet low |
| T2 | 단언 없는 벤치마크를 `furnace.slowTests` 방식으로 (A19-04, 도미니코 확인) | 작음 | Haiku low |
| T3 | JaCoCo 한 번 돌려 표 확정 + datafix 픽서 시험 (A19-03) | 중간~큼 | Sonnet medium / Opus high(픽서) |
| T4 | `WorldBorderJoinTest` 원인 확인(A19-02 ④) | 작음 | Sonnet medium |
| T5 | 봇 시험 중 플러그인 이벤트 묶음의 `furnace-test` 이전(A19-07) | 큼, 묶음별로 | Sonnet medium |
