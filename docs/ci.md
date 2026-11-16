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
- `server-tests (1/6~6/6)`: 서버 시험을 6조각으로 나눠 동시에 돈다(`-PtestShard=i/6`, `furnace-server/build.gradle.kts`). 배정은 클래스별 실측 시간으로 고르게 맞춘다(아래 "조각 균형").
  한 조각이 실패하면 **처음에 실패한 시험 클래스만** 한 번 다시 돌린다(전체 재실행 12분 → 1분 안팎). 다시 돌려도 실패하면 진짜 실패다.
  재시도에서 통과한 시험은 "흔들린 시험" 주석(PR 의 Checks 화면)·실행 요약·산출물 `flaky-tests-N`(30일 보관)에 이름이 남는다 — 이 목록이 흔들리는 시험을 고칠 우선순위다(아래 "재시도 정책").
- `light`: API 시험, 샘플 플러그인 컴파일, 시험 서버 모듈, 호환 계층 모듈, raw 상태 번호 검사(V-02, `tools/ci/check-raw-state-ids.py` — 코드에 블록 상태 번호를 숫자로 쓰면 실패), fatJar. 기준 브랜치 실행에서는 `user-todo-combined` 산출물도 올린다.
  시험 단계 셋은 하나가 실패해도 나머지를 돌고(`continue-on-error`), 실패하면 `light-test-reports` 산출물을 올린 뒤 맨 끝 "light 시험 판정"이 작업을 실패시킨다. **재시도는 없다**(아래 "light 작업 (T-160)").
- `select`(코드가 바뀐 PR): 이 PR 에서 **어느 서버 시험을 건너뛸지**와 조각 수를 정한다. 표가 없거나 위험한 변경이면 전체 — 아래 "시험 영향 선택".
- `test`: 위 둘을 합친 판정(점검 이름은 예전과 같은 `test`). **병합 판단은 `test` 하나만 본다** — `light` 는 1~2분이면 초록이 되지만 `test` 는 서버 시험 6조각이 모두 끝나야 생긴다.
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

## 재시도로 가려진 흔들림 모아 보기 (2026-11-15, T-161)
- 서버 조각 재시도에서 통과한 시험은 실행이 초록이라 주석(`… — 처음 실패, 재시도에서 통과`)만 남는다. `python3 -I tools/ci/flaky-history.py` 가
  산출물 `flaky-tests-N`(7일 보관)이 있는 실행의 주석을 모아 시험별 횟수·마지막 실행을 찍는다(`--logs 폴더` 를 주면 조각 로그를 받아 처음 실패 메시지도 붙인다).
- 11-11~15 실행 53개에서 54건을 모아 원인을 고친 기록은 `docs/feature-parity/context-notes/2026-11-15-t161-flaky.md`(제품 결함 2건: 공유 풀 선택기가 틱 중인 차원을 쉬는 차원으로 본 것,
  생성하지 않음 `loadChunkAsync` 완료 스레드). 두 번 넘게 나온 시험은 도구로 실패 메시지를 보고 원인부터 고친다.

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
- CI 와 같은 조각 하나만: `./gradlew -PskipOracle=true -PtestShard=2/6 :furnace-server:test` (조각 6개가 합쳐서 전체와 같다 — 4코어 로컬 기준 전체 13분).
- 시험 영향 선택과 같은 건너뛰기를 로컬에서: `./gradlew -PskipOracle=true -PtestSkip=<목록 파일> :furnace-server:test`(목록은 `select` 가 만든 `skip-tests.txt`, 아래 "시험 영향 선택").
- 조각 시간이 한쪽으로 쏠리면 아래 "조각 균형" 대로 시간 파일을 갱신한다.
- 시험 하나가 흔들리면: `./gradlew -PskipOracle=true :furnace-server:test --tests '패키지.클래스'` 를 여러 번 돌려 재현한다.

## 조각 6개와 문서만 바뀐 PR (2026-11-15)
- 조각을 3개 → 4개(PR #405) → **6개**로 늘렸다(근거: `furnace/reviews/ci-time-savings-2026-11-15.md`, 6조각 실측은 PR #410 본문). 같은 코드로 CI 에서 조각 수만 바꿔 잰 실측(작업 시간, 러너 준비·컴파일 포함):

  | 조각 | 가장 느린 서버 조각 | 시험 합(러너 분) | 비고 |
  |---|---|---|---|
  | 3 | 약 15분 | 약 38 | 해시 배정 시절 포함(#403 전) |
  | 4 | 11.6분 (PR) / 11.3~12.2분 (push) | 약 41 | 시간 파일 배정 |
  | 6 | 6.5~6.8분(느린 조각은 낡은 시간 파일 탓 8.3분) | 약 41 | 시간 파일 갱신 후 예상 약 7분 |
  | 8 | 5.0~5.9분(느린 조각 8.1분, 같은 이유) | 약 46 | 예상 약 5.5분 |

  모델: 조각 작업 시간 ≈ 50초(러너 준비·Gradle 시작·컴파일) + 그 조각의 클래스 시간 합. 갱신된 시간 파일(858클래스 합 2,219초)로 LPT 배정을 돌린 예상: 4조각 10.1분, 5조각 8.2분, **6조각 7.0분**,
  7조각 6.1분, 8조각 5.5분, 10조각 4.5분(가장 큰 클래스 `ContainerLockE2ETest` 223초가 하한이라 10조각 이상은 이득이 없다). 총 러너 분은 조각이 하나 늘 때 약 0.8분씩만 늘어난다(6조각 42분, 4조각 40분).
  `light` 작업은 3.8~4.3분이라 서버 조각이 6개일 때까지는 PR 전체 시간을 정하지 않고, 8조각부터 조각이 `light` 보다 빨리 끝난다(이득이 줄기 시작).
- 6조각을 고른 까닭: 4 → 6 에서 PR 한 건당 약 4분(11.6 → 7분대)을 얻고 러너 분은 5% 늘 뿐이다. 6 → 8 은 약 1.5분을 더 얻지만 PR 당 작업이 8+2 개가 되고 `light`(약 4분)가 곧 하한이 된다.
  동시 실행 한도(무료 20개)에서 PR 하나가 `changes` 1 + 조각 6 + `light` 1 + `test` 1 = 9개라 PR 둘까지는 대기가 없고 셋이 겹치면 일부가 기다린다. 총 러너 분은 거의 같아 처리량은 같고 PR 하나의 대기만 짧아진다.
  병합 대기가 CI 때문에 길어지면 여기서 다시 판단한다(조각 수는 `matrix.shard` 와 `-PtestShard=…/6`, `retry` 문구, `shard-balance.py --shards` 기본값).
- 이번 갱신으로 알게 된 것: `ContainerLockE2ETest` 가 82초 → 223초로 느려졌다(시험마다 `setUp` 에서 약 6초, 스택 출력이 있음). 시간 파일을 고치지 않았을 때 그 조각이 혼자 8분대로 튀었다. 이 시험 줄이기가 6 → 8조각보다 먼저다(후속).
- 커버리지(`coverage-tests`)는 3조각 그대로다(기준 브랜치 push 에서만 돌고 판정과 무관. 조각 번호가 `-PtestShard=i/3` 이라 판정용 6조각과 배정이 다르다).
- **문서만 바뀐 PR 은 서버 시험·`light` 를 건너뛴다.** 가장 앞의 `changes` 작업이 PR 의 바뀐 파일(`base...head`)을 보고, 전부 `docs/` 아래이거나 저장소 맨 위의 `*.md` 일 때만 `code=false` 로 둔다. 단 **시험이 읽는 문서는 코드로 본다**: `docs/api/`(`CancelContractTest` 가 계약 문서를 읽음), `docs/feature-parity/`(`OptionKeysTest` 키 표, 호환 모듈 `compatReport`·`nmsReport` 입출력). 이름 바꾸기는 풀어서 본다(`--no-renames`).
  이때 `server-tests`·`light` 는 `skipped` 이고 `test` 판정은 "둘 다 skipped" 일 때 초록이다(필수 점검 `test` 는 그대로 생긴다). 하나라도 실패·취소면 빨갛다.
  `.github/`·`tools/`·`*.gradle*`·다른 폴더의 `.md`(예: 모듈 안 README) 는 코드로 본다(안전 쪽). 파일 목록을 못 읽거나 비면 `code=true` 로 시험이 돈다. 기준 브랜치 push 는 항상 돈다.
- 가장 오래 걸리는 시험 클래스 5개(2026-11-15, 클래스 시간 실측 `test-shard-times.tsv`): `ContainerLockE2ETest` 223초, `CancelResyncE2ETest` 173초, `GeneratedChunkSaveTest` 124초, `ChunkCornerLoadFollowupTest` 117초, `A04InventoryLineTest` 89초(합 726초, 전체의 33%).
  이 시험들을 건드릴 때만 돌리고, 줄이는 것은 후속 작업이다(각 절반이면 전체 약 5분 감소).

## 시험 영향 선택 (2026-11-16, V-08)
PR 은 서버 시험 약 5,200개 중 **이 변경이 실제로 실행되는 시험만** 돈다. A 클래스를 고치면 A 를 실행한 시험만 돌고, A 와 상관없는 B 시험은 안 돈다.
전체는 기준 브랜치 push 때마다(병합 뒤) 그대로 돈다 — 고르기가 놓친 것은 병합 뒤 여기서 잡힌다.

### 어떻게 고르나
1. **영향 표 만들기**(`.github/workflows/test-impact.yml`, 기준 브랜치 push): 시험 클래스 **하나마다 새 JVM**에서 JaCoCo 에이전트를 붙여 돌리고(`-PtestImpact`, `forkEvery=1`),
   `TestImpactListener`(시험 소스, 켜는 속성이 없으면 아무것도 안 함)가 끝날 때 "이 시험이 실행한 클래스"를 에이전트에서 꺼내 적는다. `tools/ci/test-impact.py partial/merge` 가 클래스를
   소스 파일로 바꿔(클래스 파일의 `SourceFile` 속성) 한 장의 표 `test-impact.tsv`(산출물 `test-impact-map`, 14일)로 합친다. 표는 커밋하지 않는다.
   - 클래스마다 JVM 이 따로여야 하는 까닭: 같은 JVM 에서는 앞 시험이 만든 싱글턴·정적 초기화가 뒤 시험 구간에 안 잡혀, 그 코드를 고쳐도 뒤 시험이 선택되지 않는다.
   - 시험이 실패·중단했거나 한 JVM 에 둘 이상 들어갔으면 그 시험은 `unreliable` 로 적히고 **항상 돈다**(일부 코드를 안 밟았을 수 있다).
   - 에이전트는 시험을 느리게 하므로(전체의 수 배) 표는 최근 표가 4시간보다 새것이면 만들지 않는다(`MAP_MIN_AGE_HOURS`). 동시에 하나만 돌고 돌던 것은 취소하지 않는다.
     조각(8개)이 하나라도 빠지면 합치지 않아 이전 표가 계속 쓰인다. 믿을 수 있는 시험이 절반 미만이면 표를 쓰지 않는다.
   - 표 만드는 도구(`test-impact.yml`·`test-impact.py`·`TestImpactListener`·`test-selection-rules.txt`)를 고치는 PR 은 이 워크플로가 `pull_request` 로도 돌아 도구가 실제로 표를 만드는지 병합 전에 본다(PR 마다 따로 줄을 서서 기준 브랜치 표 만들기를 밀어내지 않는다). 그 표는 기준 브랜치에서 만든 것이 아니라 `select` 가 쓰지 않는다.
2. **PR 에서 고르기**(`ci.yml` 의 `select` 작업 → `tools/ci/test-impact.py select`): 가장 최근 표를 받아, **표를 만든 커밋 X 의 트리와 지금 검사하는 트리(PR 병합 결과)의 파일 차이 전체**를 바뀐 것으로 본다.
   이 PR 이 바꾼 것뿐 아니라 표를 만든 뒤 기준 브랜치에 들어온 변경도 포함한다 — 그 변경이 시험의 실행 경로를 바꿨을 수 있어서다(두 트리의 차이라 병합 기준이 어디든 성립한다).
   - 바뀐 소스 파일 → 그것을 실행한 시험 + 바뀐 시험 자신. 시험 소스(도우미 포함)도 표에 있어 `TestServers` 같은 도우미를 고치면 그것을 쓰는 시험이 돈다.
   - **표에 없는 시험(새 시험)은 건너뛰기 목록에 안 들어가므로 저절로 돈다.** 건너뛰는 것은 항상 이름으로 명시된 클래스뿐이다(`-PtestSkip=<파일>`, `furnace-server/build.gradle.kts`).
   - 컴파일 시간 상수: javac 는 `static final` 상수 값을 쓰는 쪽에 박아 넣어, 쓰는 파일이 안 바뀌어도 동작이 바뀐다. 바뀐 줄에서 상수 선언(private 이 아닌 `final` 필드, 인터페이스의 대문자 필드)을 찾아 그 이름을 쓰는 파일을 바뀐 것으로 본다(상수끼리 이어지면 끝까지, 사용처가 250 개를 넘으면 전체).
   - 어떤 시험도 실행한 적 없는 소스(인터페이스·상수 묶음·기록 클래스·실제로 안 쓰이는 코드)는 그 타입 이름을 쓰는 파일로 대신한다(최대 3 단계).
   - 새 소스 파일(표에 없음)은 같은 패키지의 시험 클래스를 돌린다(반사·등록으로만 닿는 경우를 위해). 새 시험 소스는 시험이라 저절로 돈다.
   - 읽는 쪽 규칙 `reads`: 시험이 데이터로 읽는 문서·도구 파일(`docs/api/**`, `tools/vanilla-data/**` 등)이 바뀌면 그 경로를 문자열로 쓰는 자바 소스를 바뀐 것으로 본다.
3. **전체로 넘어가는 경우**(조각 수도 최대, 이유가 `select` 작업 요약에 나온다):
   | 경우 | 까닭 |
   |---|---|
   | push·수동 실행 | 기준 브랜치는 언제나 전체 |
   | 영향 표가 없다 / 7일 넘음 / 표의 커밋이 기록에 없음 / 형식이 다름 | 낡은 표로 고르지 않는다 |
   | 표 이후 바뀐 파일이 400개 넘음 | 어차피 대부분이 선택된다 |
   | 빌드·CI 파일, 리소스(`src/*/resources/**` — 금 파일·정답표·언어 파일·서비스 목록), 동시성 핵심 | `tools/ci/test-selection-rules.txt` 의 `full` 규칙 |
   | 규칙 파일에 없는 종류의 파일이 바뀜 | 기본값이 전체(규칙을 아는 것만 건너뛴다) |
   | `reads` 규칙이 읽는 소스를 하나도 못 찾음 | 규칙이 낡았다 |
   | 선택분이 전체 시간의 70% 이상, 또는 건너뛸 시험이 없음 | 나눌 이득이 없다 |
   | 상수 사용처 250개 넘음, 실행된 적 없는 소스의 사용처가 3단계로 안 끝남 | 따라갈 수 없다 |
   | 선택 도구·영향 표 읽기 오류, 러너 오류 | 오류는 언제나 전체(`select` 작업은 어떤 경우에도 성공으로 끝난다) |
   | PR 라벨 `full-tests` | 사람이 강제(붙인 뒤 "Re-run all jobs" — 실행 시점의 라벨을 본다) |
4. 선택 실행은 조각 수를 줄인다: 추정 시험 시간(시간 파일 `test-shard-times.tsv`, 새 시험은 평균)을 300초로 나눠 올림, 1~`MAX_SHARDS`. 건너뛴 클래스를 뺀 나머지로 같은 LPT 규칙으로 배정한다.
   선택 실행에서는 `shard-times-N` 산출물을 올리지 않는다(일부 클래스만 돈 표본이 시간 파일을 치우치게 하므로).
5. `light`(API·호환·시험 서버 모듈 시험, fatJar)는 코드가 바뀐 PR 에서 늘 전부 돈다(3분 안팎, 서버 시험과 동시). 서버 시험이 0 개로 줄어드는 일은 없다: 새 시험·믿을 수 없는 시험·항상 도는 시험(`always`)이 남는다.

### 읽는 법
- PR 의 `select` 작업 요약: 전체/선택, 이유, 건너뛴 시험 수, 바뀐 소스별로 몇 개의 시험이 그것을 실행했는지, 도는 시험 목록. 산출물 `test-selection`(7일)에 `selection.json`·`skip-tests.txt`.
- 서버 시험 조각 이름의 `/n` 은 그 PR 의 조각 수다(전체 실행이면 `MAX_SHARDS`).
- 의심스러우면 라벨 `full-tests`. 고르기가 놓쳐서 병합 뒤 기준 브랜치 전체에서 빨강이 나오면 그 PR 을 되돌리거나 바로 고치고, **놓친 사례를 `tools/ci/test_test_impact.py` 에 시험으로 추가**한다(왜 놓쳤는지 이 문서에도 적는다).

### 유지
- 시험이 저장소 밖 파일(`../docs`, `../tools`)을 읽으면 `tools/ci/test-selection-rules.txt` 에 `reads` 규칙을 같이 넣는다(`test_test_impact.py` 의 점검이 빠진 폴더를 찾는다). 항상 돌릴 넓은 시험은 `always` 줄.
- 조각 수(`MAX_SHARDS`, 폴백 `|| 6` 과 `[1,…]` 목록)·표 조각 수(`IMPACT_SHARDS`)는 한 수로 맞아야 한다 — `test_test_impact.py` 가 확인한다.
- CI 도구 자체 시험: `python3 -I tools/ci/test_test_impact.py`(`light` 작업이 돈다).
- 로컬에서 표를 만들어 보기: `./gradlew -PskipOracle=true -PtestImpact -PtestForks=2 :furnace-server:test --tests '패키지.*'` → `python3 -I tools/ci/test-impact.py partial --raw furnace-server/build/test-impact/raw --out /tmp/part.tsv`.
  고르기 시험: `python3 -I tools/ci/test-impact.py select --map <표> --out-dir /tmp/sel --github-output ""`(현재 트리와 표의 커밋 사이 차이로 고른다).

### 알려진 한계(정직하게)
- 표는 **시험이 결정적**이라고 가정한다. 난수·경합으로 어떤 실행에서만 밟는 코드는 표에 빠질 수 있다 — 그런 시험의 실패는 병합 뒤 전체에서 잡힌다.
- 에이전트가 느려서 제한시간·시간 의존 단언으로 실패한 시험은 `unreliable` 이라 항상 돈다. 이런 시험이 많으면 선택 효과가 줄어든다(표 생성 요약의 "믿을 수 없는 시험 n 개").
- **다른 JVM 에서 서버 코드를 돌리는 시험은 에이전트가 못 본다**(장면 해시·IdentityKit 금 파일 시험이 자식 JVM 을 띄운다). 그래서 이런 시험 7개는 규칙 파일의 `always` 로 서버 코드가 바뀔 때마다 돈다(합쳐 1분 안팎). 새 시험이 `ProcessBuilder`·`SceneHash`·`ForkMain` 을 쓰면 `always` 에 넣어야 하고, 빠뜨리면 `test_test_impact.py` 가 실패한다.
- 인터페이스의 소문자 이름 상수 선언은 상수로 인식하지 못한다(자바 관례상 대문자).
- 상수 선언이 여러 줄에 걸치고 값이 있는 줄만 바뀐 경우는 인라인 상수로 못 잡는다(드물다).
- **다른 파일의 내용을 읽어 검사하는 시험은 `always`**: 소스·클래스 파일을 훑는 시험(`LangBundlesTest` 한글·Lang 키 검사, `CompatIsolationStaticTest` 금지 이름 검사, `CancelContractTest` 취소 가능 이벤트 대조)은 검사 대상 파일을 "실행"하지 않아 영향 표에 안 잡힌다. 규칙 파일 `always` 로 서버 코드가 바뀔 때마다 돌고(합쳐 몇 초),
  새 시험이 `src/main/java`·`../furnace-`·`getCodeSource()` 로 다른 파일을 읽으면 `test_test_impact.py` 의 `SourceScanTestsLintTest` 가 실패해 알려 준다(`Path.of(base, …)` 처럼 변수로 경로를 만드는 것은 점검이 못 본다).
- **표 만드는 도구를 고친 PR**: 병합되면 표(옛 도구로 만든 것)는 최대 4시간 남는다. 그 사이 PR 은 안전하게 전체로 돈다 — `tools/ci/**`·`.github/**`·`*.kts`·`TestImpactListener.java` 는 `full` 규칙이라 "표 이후 바뀐 파일"에 들어가면 전체다. 그리고 `test-impact.yml` 의 `gate` 가 도구 파일(`test-impact.py`·규칙 파일·`test-impact.yml`·`furnace-server/build.gradle.kts`·`TestImpactListener.java`)이 바뀐 push 에서는 나이와 상관없이 표를 다시 만들어 그 "전체" 기간을 줄인다.
  `furnace-server/build.gradle.kts` 만 바꾸는 PR 은 표 만들기를 PR 에서 돌지 않는다(조각 8개 × 최대 90분이라 빌드 파일 PR 마다 돌리기엔 비싸다) — 병합 뒤 기준 브랜치 push 에서 만들어 보고, 실패하면 표가 갱신되지 않을 뿐 PR 은 계속 안전하게(전체로) 돈다.
- 표 생성 비용: 기준 브랜치 push 가 있을 때 4시간에 한 번 조각 8개 × (시험 클래스당 JVM + 에이전트) — 처음 실행의 실측으로 조각 수·동시 JVM 수·제한시간을 조정한다(아래 "실측" 절에 기록).

### 실측 (2026-11-16, 로컬 4코어 15GB 컨테이너, 같은 코드)
- **표 만들기**: `-PtestImpact -PtestForks=3` 로 서버 시험 전체 53분 42초(시험 클래스당 JVM + 에이전트). 시험 878 클래스·소스 3,405 파일이 표에 들어갔고, 이 중 22 개는 `unreliable`(에이전트 아래에서 실패한 2개 `ExecuteStoreDataTest`·`CommandSelectorStageTest` 와 다른 JVM 을 띄우는 시험 등)이라 항상 돈다.
  조각 8개 × 동시 JVM 2 로 나누면 러너당 약 10~15분으로 추정한다(이 환경에서 잰 값이 아니라 추정).
- **선택 실행 vs 전체 실행** (표를 만든 커밋 위에서 `item/PotionBrewing`·`item/FuelRegistry`·`item/Snbt` 세 파일에 고의로 틀린 동작을 넣은 한 커밋):

  | | 시험 클래스 | 시험 수 | 실패 | 시간(컴파일 포함, 이 환경) |
  |---|---|---|---|---|
  | 전체 실행 | 885 | 5,169 | 12 | 38분 44초 (시험 시간 합 2,320초) |
  | 선택 실행 (`-PtestSkip`) | 69 (표 878 중 62 선택, 816 건너뜀) | 533 | 12 | 8분 2초 (시험 시간 합 462초) |

  **실패한 시험 12개가 두 실행에서 이름까지 같다**(전체에서만 실패한 시험 0, 선택에서만 실패한 시험 0): 고의로 깬 세 파일을 실행하는 시험 — 양조대·화로·`DataComponentsVanillaTest`·아이템 컴포넌트 API 등 — 이 선택에 모두 들어 있었다. 선택은 바뀐 파일별로 `PotionBrewing` 3, `FuelRegistry` 7, `Snbt` 24 개 시험을 골랐고, 나머지 선택분은 항상 돌리는 시험·믿을 수 없는 시험이다.
  이 비교는 "선택이 전체의 실패를 놓치지 않는다"는 사례 하나다 — 놓친 사례가 생기면 위 "읽는 법"대로 `test_test_impact.py` 에 추가한다.

## 조각 균형 (2026-11-15, T-162)
- 실행 시간은 가장 느린 조각이 정한다. 배정 규칙(`furnace-server/build.gradle.kts`, `tools/ci/shard-balance.py` 의 `assign()` 과 같다):
  `furnace-server/test-shard-times.tsv`(클래스별 시험 시간 실측)에 있는 클래스는 **오래 걸리는 것부터 지금 가장 가벼운 조각에** 넣고(LPT),
  파일에 없는 새 클래스만 이름 해시(`shardSalt`)로 나눈다. 배정은 정확성과 무관하다(조각을 합치면 전체와 같다).
- 해시 + `shardSalt` 고르기를 버린 까닭: 접미사 salt 는 조각 수 3 에서 **salt 길이마다 같은 나눔**(조각 이름표만 바뀜)을 낸다 —
  0~19999 를 뒤져도 서로 다른 나눔은 다섯 개뿐이었다. 그래서 큰 클래스(`CancelResyncE2ETest` 173초, `GeneratedChunkSaveTest` 124초 …)가
  몰린 조각을 풀 수 없었다. 11-15 기준 전체 1회 실측(851클래스, 2,101초)에서 해시 배정은 가장 느린 조각이 이상값의 1.23배,
  가장 나은 salt 도 1.13배였다. 시간 파일 배정은 **다른 날 표본으로 만든 파일로 재도** 1.07배였다(같은 표본이면 1.004배).
- 시간 기록: 한 번에 통과한 서버 시험 조각은 산출물 `shard-times-N`(클래스별 시간 TSV, 30일)을 올린다. CI 러너 실측이다.
- **갱신하는 법**(조각 작업 시간이 눈에 띄게 벌어지거나, 해시로 가는 새 클래스 몫이 커질 때):
  1. 최근 성공한 기준 브랜치 실행 몇 개에서 `shard-times-1~6` 을 받아 실행마다 한 폴더에 푼다(예: `runs/1617/`, `runs/1620/`).
     로컬 전체 시험 결과(`furnace-server/build/test-results/test`)도 표본이 된다.
     **조각 6개가 다 있는 실행만 쓴다** — 조각이 빠진 표본은 `--write` 가 합을 맞출 때 남은 클래스 시간이 부풀려진다
     (한 조각이 재시도로 실패한 실행은 그 조각 산출물이 없다).
  2. `python3 -I tools/ci/shard-balance.py runs/*` 로 지금 배정의 균형과 "시간 파일에 없는 클래스" 몫을 본다.
  3. `python3 -I tools/ci/shard-balance.py --write runs/*` 로 시간 파일을 새로 쓰고 커밋한다(표본마다 합을 맞춘 뒤 클래스별 평균).
- 안 해도 정확성은 같고 균형만 나빠진다. 시간 파일은 손으로 고치지 않는다.

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

## 태그 (2026-11-16)
- 태그를 올리면 `.github/workflows/tag-check.yml` 이 이름 형식을 검사한다(`tools/release/furnace-tag.sh check`). 시험 조각은 태그에서 돌지 않는다 — base·ms 태그는 기준 브랜치 `test` 가 이미 초록인 커밋에 찍는다. 규칙: `docs/ops/version-tags.md`.
- 서버 fatJar 의 `Implementation-Version` 은 가장 가까운 판 태그 `<MC>` 에서 만든다(없으면 `dev+<해시>`). 태그를 읽으려면 `git describe` 가 되어야 하므로 버전이 중요한 빌드(판 완성)는 `fetch-depth: 0` 로 받는다. 얕은 복제 CI 에서는 `dev+<해시>` 가 되어도 시험·빌드에는 영향이 없다.
