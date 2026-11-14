# CI 구조와 병합 병목 대응 (2026-11-07)

`.github/workflows/ci.yml` 의 현재 구조와, 왜 그렇게 바꿨는지(측정값)를 적는다.

## 측정 (2026-11-05~07, 기준 브랜치 실행 + PR 실행 약 340건)
- 한 실행 시간: 성공 15분, 불안정 시험이 걸려 전체를 다시 돌리면 25~30분. 시간 거의 전부가 `서버 테스트` 단계(12~14분, 3500개).
  API 시험 35초, 샘플 컴파일 20초, 시험 서버 모듈 7초, fatJar 3초.
- 작업 브랜치마다 `push` 와 `pull_request` 가 둘 다 돌아 같은 시험이 두 번 돌았다.
- Gradle 캐시는 "기본 브랜치(main)에서만 저장"이라 기준 브랜치(`claude/quirky-wright-lra834`) 캐시가 저장된 적이 없었다(로그: `Cache is read-only`).
- 실패 로그에서 뽑은 흔들린 시험(전체 실패 집계, 같은 실행의 재시도 포함):

| 시험 | 횟수 | 원인 | 조치 |
|---|---|---|---|
| `CommandBlockOneTickTest.entitySelectorAcrossRegionsFinishesInsideTheSameTick` | 24 | 명령 블록 대기 예산 20ms(서버 기본값)를 바쁜 러너에서 넘김 | 시험 JVM 에서만 예산 500ms(`furnace.commandBlockWaitMicros`, `furnace-server/build.gradle.kts`) |
| `SpawnPolicySpawnTest` (`categoryCapZero…`, `spawnAnimalsFalse…`) | 9 | 닭 기수(탈것) 난수 — PR #125 에서 수정됨 | 수정 완료 |
| `LandMobTest` (`goatRams…`, `frogBrain…`, `shulkerShoots…`) | 6 | 몹 AI 난수(엔티티 난수가 시드 없이 `nanoTime` 으로 만들어짐) | 시험이 레벨·몹 난수를 `setSeed` 로 고정(`LandMobTest.seed`, 2026-11-08) — 서버 동작은 그대로 |
| `ConnectionStateApiTest.pluginPackBlocks…`, `GameplayFlowTest.gameModeExperience…`, `SpreadPlayersCommandTest.spreadsCowsInRealWorld` | 각 4 | 접속·틱 대기 10초 초과 | 대기 한도를 20~30초로 늘림 |
| `GeneratedVillageProfessionTest`, `DispenserBlockTest.arrowIsShotWithPickupAllowed` | 각 1 | 난수 | CI 재시도로 흡수 |

## 지금 구조
- `server-tests (1/3~3/3)`: 서버 시험을 시험 클래스 이름 해시로 3조각으로 나눠 동시에 돈다(`-PtestShard=i/3`, `furnace-server/build.gradle.kts`).
  한 조각이 실패하면 **처음에 실패한 시험 클래스만** 한 번 다시 돌린다(전체 재실행 12분 → 1분 안팎). 다시 돌려도 실패하면 진짜 실패다.
  재시도에서 통과한 시험은 "흔들린 시험" 주석(PR 의 Checks 화면)·실행 요약·산출물 `flaky-tests-N`(30일 보관)에 이름이 남는다 — 이 목록이 흔들리는 시험을 고칠 우선순위다(아래 "재시도 정책").
- `light`: API 시험, 샘플 플러그인 컴파일, 시험 서버 모듈, 호환 계층 모듈, fatJar. 기준 브랜치 실행에서는 `user-todo-combined` 산출물도 올린다.
  시험 단계 셋은 하나가 실패해도 나머지를 돌고(`continue-on-error`), 실패하면 `light-test-reports` 산출물을 올린 뒤 맨 끝 "light 시험 판정"이 작업을 실패시킨다. **재시도는 없다**(아래 "light 작업 (T-160)").
- `test`: 위 둘을 합친 판정(점검 이름은 예전과 같은 `test`). **병합 판단은 `test` 하나만 본다** — `light` 는 1~2분이면 초록이 되지만 `test` 는 서버 시험 3조각이 모두 끝나야 생긴다.
- `push` 는 기준 브랜치(`claude/quirky-wright-lra834`, `main`)에서만 돈다. 작업 브랜치는 PR 을 열면 `pull_request` 로 한 번 돈다.
  충돌이 있는 PR 은 `pull_request` 실행이 만들어지지 않는다 — 기준 브랜치를 합쳐 충돌을 풀면 다시 돈다.
- Gradle 캐시는 기준 브랜치 실행에서만 저장하고 PR 은 읽기만 한다.
- 새 푸시가 오면 앞 실행을 취소하는 것은 PR 실행만이다. 기준 브랜치 push 실행은 병합이 잦아도 끝까지 돌아야 캐시·합친 user-todo 문서가 만들어진다.
- 재시도는 "시험 실패"로 끝난 조각에만 한다. 시험 JVM 이 죽었거나(`non-zero exit value`) 빌드 오류면 재시도하지 않고 실패로 둔다(일부 시험이 안 돈 채 초록이 되는 것을 막는다).

## 시험 제한시간 (2026-11-11, A19-01)
- 시험 한 개(`@BeforeAll` 같은 준비·정리 메서드 포함)의 기본 제한시간은 **5분**이다: 각 모듈 `src/test/resources/junit-platform.properties` 의 `junit.jupiter.execution.timeout.default=5m`.
  `thread.mode.default=SEPARATE_THREAD` 라 인터럽트로 안 멈추는 무한 반복도 5분에 실패로 끝난다(로컬 실측: 바쁜 루프 시험이 `TimeoutException: busy() timed out after 5 minutes` 로 실패하고 시험 JVM 이 정상 종료).
- 근거: 서버 시험 3854개 전체 실측(4코어 컨테이너, 2026-11-11)에서 한 시험 최장 67초(`TreeFeatureScanTest`), 한 클래스 최장 124초(`GeneratedChunkSaveTest`). 5분은 최장의 4배 남짓이다. 더 오래 걸려야 하는 시험은 그 시험에 `@Timeout` 을 붙인다(메서드의 `@Timeout` 이 기본값보다 우선).
- **주의(SEPARATE_THREAD)**: 준비(`@BeforeEach`)·시험·정리 메서드가 각각 다른 `junit-timeout-thread` 에서 돈다. `ThreadLocal` 에 넣은 값을 시험 메서드에서 읽거나 호출 스레드 이름·정체에 기대는 시험은 깨진다(현재 시험 3859개는 영향 없음 — 전체 실행으로 확인). 그런 시험은 값을 인스턴스 필드로 옮기거나 같은 메서드 안에서 처리한다.
- 제한시간 초과는 **재시도하지 않는다**(멈춘 시험이라 다시 돌려도 5분을 더 쓸 뿐이다).
- CI 단계 제한: 서버 시험 단계 `timeout-minutes: 20`(정상 조각 5~8분), 작업 제한 35분은 그대로. 단계 제한으로 끝나도 보고서·실행 로그(`shard-run.log`)가 올라간다(`always()`).
  단계가 `continue-on-error` 라 `outcome` 이 `failure` 가 아닌 값으로 남아도 "서버 시험 판정" 단계가 조각을 실패로 끝낸다(서버 시험도 재시도도 성공이 아니면).
  **작업 제한(35분)으로 취소된 경우에 `always()` 단계가 도는지는 실제 취소 실행으로 확인하지 못했다**(GitHub 문서 기준). 그래서 단계 제한을 먼저 둔다.

## 재시도 정책 (2026-11-11, A19-02)
재시도는 부하에서만 실패하는 시간 민감 시험을 흡수하려는 것이다. 그런데 클래스를 한 번 더 돌리면 **확률로 실패하는 시험의 회귀도 가린다**(실패율 p 면 두 번 다 실패할 확률은 p², 예: p=0.4 → 빨강 16%).
- **확률 실패를 잡는 시험**(난수·스레드 경합 회귀)은 `@NoRetry`(`org.domi.furnace.server.NoRetry`, 태그 `no-retry`)와 `@RepeatedTest(n)` 을 함께 붙인다: 한 번의 실행 안에서 n 번 돌리고, 한 번이라도 실패하면 재시도 없이 빨강이다. `NoRetryFailureListener` 가 실패를 `furnace-server/build/no-retry-failures.txt` 에 적고 재시도 단계가 이를 보고 건너뛴다. n 은 "회귀가 있을 때 한 번도 안 걸릴 확률"이 무시할 만큼(예: 실패율 0.17 이면 n=40 → 0.83^40≈6e-4) 되게 잡고, 정상 코드에서는 절대 실패하지 않아야 한다(흔들리면 시험을 고친다).
  가능하면 결정적 시험(난수 고정·장벽·수동 시계 `furnace-test`)이 낫다 — 위 방식은 결정적으로 만들 수 없을 때다.
- `@NoRetry` 가 붙은 시험이 CI 에서 흔들리면 재시도로 덮지 말고 시험(또는 서버)을 고친다. AGENTS.md 규칙 5(스킵·비활성화로 초록을 만들지 않는다)와 같은 방향이다.
- 재시도 로직은 `tools/ci/retry-failed-tests.sh`(워크플로에서 직접 부른다)에 있다: 시험 JVM 이상 종료·제한시간 초과·`@NoRetry` 실패면 재시도하지 않는다. 재시도에서 통과하면 시험마다 `::warning title=흔들린 시험::`, 실행 요약, 산출물 `flaky-tests-N` 을 남긴다.
- **흔들린 시험 기록 읽는 법**: 기준 브랜치 실행의 `flaky-tests-N` 산출물과 PR 의 주석을 본다. 같은 시험이 두 번 이상 나오면 위 표에 올리고 고친다. (기록을 자동으로 모아 표를 만드는 도구는 아직 없다.)
- 아직 없는 것: 시험 수 합계 출력, 조각 간 흔들림 비교. `@NoRetry` 를 기존 시험에 일괄로 붙이지 않았다(지금 `@RepeatedTest` 를 쓰는 시험은 `ChunkSendPacketOrderGoldenTest` 뿐이고 결정적 순서 시험이다).

## light 작업 실패 보기 (2026-11-14, T-160)
- 11-12~14 실행 400개 중 `light` 시험 실패 28건(호환 계층 23, 시험 서버 모듈 5). 로그에는 `AssertionFailedError at X.java:227` 한 줄뿐이라 원인을 볼 수 없었다
  (Gradle 기본 `exceptionFormat=SHORT`, 보고서 올리기 없음).
- 이제 모든 모듈의 시험 작업이 실패 때 **단언 메시지·기대값/실제값·원인·스택 전체**를 콘솔에 찍는다(루트 `build.gradle.kts` 의 `tasks.withType<Test>` — 로컬도 같다).
  `light` 는 실패하면 모듈별 HTML·XML 보고서(시험 표준 출력 포함)를 `light-test-reports`(7일)로 올린다.
- `light` 에 재시도를 넣지 않았다: 재시도는 흔들리는 시험이 숨긴 제품 경합(예: 패킷 리스너 안 엔티티 조회)을 조용히 초록으로 바꿔 그대로 병합되게 한다.
  `light` 는 3분 남짓이라 실패하면 그 실행의 메시지로 원인을 고친다. 고친 흔들림은 아래 표에 적는다.

| 시험 | 횟수(11-12~14) | 원인 | 조치 |
|---|---|---|---|
| `ProtocolLibBridgeTest.spawnListenersCanLookUpTheNewEntity` (:308) | 5 | **서버 결함**: 새 엔티티를 스폰 묶음을 보낸 뒤에야 번호 색인에 넣어, 패킷 리스너의 `getEntity(id)` 가 가끔 null(부하 반복 13/80) | `ChunkLevel.addFreshEntity`·`adoptEntity` 에서 색인 → 추적 순서(바닐라·Paper 와 같음), 회귀 `EntityIndexBeforeSpawnTest`. 고친 뒤 0/80 |
| `TabCompleteAndCommandSendTest.unhandledEvent…` (:123) | 1 | 시험 경합: `AsyncTabCompleteEvent` 는 전용 스레드라 장벽이 못 덮는데 처리기를 미리 바꿈(옛 시험 부하 반복 4/60) | 요청마다 처리기가 끝날 때까지 기다림(`suggestHandled`), 0/60 |
| `ChatAdapterTest.recipientsStartAsEveryoneOnline…` (:75) | 이 PR 의 첫 CI(새 FULL 형식으로 바로 원인이 보임: `[Dana, Kim]`) | 시험 경합: 채팅은 받는 사람마다 그 연결 이벤트 루프에서 기록되는데 보낸 사람 연결만 비우고 단언 — "안 받았다" 단언은 헛통과할 수 있었다 | 단언 전에 받는 사람 모두 `sync()`(`flushAll`) |
| `TestPlayerPacketsTest.afterKick…` (:86), `SchemaPacketBridgeTest` decodeFailures | 5, 3 | 가짜 플레이어 읽기 스레드·decodeFailures 경합 | 11-14 f700bee5e·6eb9eee32 에서 고쳐짐(그 뒤 재발 없음) |
| `JavaPluginWiringTest.permissionAndExceptions…` (:227) | 7 | 확정 못 함 — 11-13 17:43 UTC 뒤 약 110회 재발 없음, 부하 반복 0/60 | 다시 나오면 이제 로그에 받은 메시지 목록이 찍힌다 |

## 로그 없이 취소된 조각 (2026-11-14, T-160)
- PR #362 의 `server-tests (2/3)` 첫 시도(작업 132469604114): 서버 시험 단계가 13:57 에 시작해 **40분 뒤 취소**, 단계 로그는 404(올라가지 않음), 보고서 올리기(`always()`)도
  돌지 않았다. 주석은 "The job has exceeded the maximum execution time of 35m0s" 하나. 같은 커밋 두 번째 시도는 12.7분에 통과.
- 단계 제한(`timeout-minutes: 20`)은 러너 프로그램이 건다. 20분에 발동하지 않았고 로그도 못 올렸으니 **그때 러너 프로그램이 응답하지 못했다**
  (서버 쪽 작업 제한 35분 + 취소 유예 5분 = 40분에 GitHub 이 강제로 닫음). 같은 모양은 400개 실행 중 이 한 번뿐이다.
  러너가 멈추는 흔한 원인은 메모리 고갈(OOM 킬러가 러너를 죽이거나 스왑 없는 VM 이 멈춤)과 디스크 고갈(러너가 로그를 못 씀)이다 — 확인할 기록이 남지 않아 **원인은 확정하지 못했다**.
- 대응: 서버 시험 단계를 `tools/ci/run-guarded.sh 18 -- ./gradlew …` 로 돌린다.
  1. `oom_score_adj=1000` 을 gradle·시험 JVM 이 물려받는다 → 메모리가 모자라면 러너 대신 시험 JVM 이 죽고(`exit value 137`), 로그·보고서가 남는다.
  2. 30초마다 메모리·스왑·디스크 남은 칸·부하·java RSS 를 `/tmp/ci-resources.log` 에(2분마다 한 줄은 콘솔에도 `[자원]`) 적고, 끝에 가장 적었던 남은 메모리를 찍는다.
  3. 18분(단계 제한 20분보다 먼저)에 스스로 멈추며 시험 JVM·gradle 데몬 스레드 덤프를 `/tmp/ci-thread-dumps` 와 콘솔(시험 스레드만)에 남기고 종료 코드 124 로 끝난다.
     재시도하지 않는다(로그에 "There were failing tests" 가 없다). 자원 기록·덤프는 `server-test-reports-N` 산출물로 올라간다.
- 다시 생기면: 산출물이 있으면 `ci-resources.log` 의 마지막 줄들과 덤프를 본다. 산출물마저 없으면(러너가 또 멈춤) 이 문서에 날짜·작업 번호를 적는다 — 두 번 이상이면 시험 JVM 힙·조각 수를 다시 본다.

## 로컬에서
- 전체: `./gradlew -PskipOracle=true :furnace-api:test :furnace-server:test` (조각 속성 없음 = 전부).
- CI 와 같은 조각 하나만: `./gradlew -PskipOracle=true -PtestShard=2/3 :furnace-server:test` (조각 3개가 합쳐서 전체와 같다 — 4코어 로컬 기준 조각당 4~6분, 전체 13분).
- 조각 시간이 한쪽으로 쏠리면 `python3 -I tools/ci/shard-balance.py` 로 새 `shardSalt` 를 구해 `furnace-server/build.gradle.kts` 에 넣는다(안 해도 정확성은 같고 균형만 달라진다).
- 시험 하나가 흔들리면: `./gradlew -PskipOracle=true :furnace-server:test --tests '패키지.클래스'` 를 여러 번 돌려 재현한다.

## 기준 브랜치가 바뀌면
`ci.yml` 의 `on.push.branches` 와 `env.BASE_BRANCH` 두 곳을 새 이름으로 바꾼다.

## 커버리지(JaCoCo, 2026-11-11, A19-03 후속)
- **보고용이다. 게이트가 없다**(`test` 판정에 들어가지 않는다).
- 켜는 법: `-Pcoverage`(없으면 에이전트를 붙이지 않아 평소 시험 속도·시간 민감 시험에 영향이 없다).
  `./gradlew -PskipOracle=true -Pcoverage :furnace-server:test :furnace-server:jacocoTestReport` → `furnace-server/build/reports/jacoco/test/`. 서버 시험 전체는 4코어에서 오래 걸리니 `--tests` 로 좁혀 써도 된다.
- CI: **기준 브랜치 push 실행에서만** 별도 작업 `coverage-tests`(3조각, `continue-on-error`, `test` 판정과 무관)가 `-Pcoverage` 로 서버 시험을 돌려 `coverage-exec-N` 산출물(`test.exec`)을 올린다.
  판정용 조각에는 에이전트를 붙이지 않는다: 에이전트가 시험을 5배 가까이 느리게 해(`RegenerateChunkTest` 11초 → 59초) 제한시간 시험이 실패하고, 제한시간 실패는 재시도하지 않아 `test` 가 빨개진다(PR #205 검토 R1). 커버리지 작업의 시험 실패는 무시한다.
  `coverage` 작업이 3개를 합쳐(`:furnace-server:jacocoCoverageReport`) 패키지별 줄·가지 표를 실행 요약에, xml·html 을 `coverage-report` 산출물(14일)에 올린다. 표는 `tools/ci/coverage-summary.py` 로 로컬에서도 만든다.
- 한계: 느려져 제한시간으로 실패한 시험의 줄은 일부 빠질 수 있다. 서버 모듈만 잰다(`furnace-api`·호환·시험 서버 모듈은 아직 아님).
- 기준 브랜치 이름은 `coverage-tests`·`coverage` 작업의 `if` 에도 들어 있다(env 를 `if` 에서 못 써서 하드코딩). 기준 브랜치를 바꾸면 ci.yml 의 `on.push.branches`·`BASE_BRANCH` 와 이 두 `if` 를 같이 바꾼다.
- 이 표가 줄 커버리지로 A19-03 의 "이름 참조 근사"를 대체한다. 기준 브랜치 첫 실행 뒤 표를 보고 다음 시험 대상(`block/types`·`entity/ai`·`command/builtin`)을 정한다.
