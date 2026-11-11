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
- `light`: API 시험, 샘플 플러그인 컴파일, 시험 서버 모듈, fatJar. 기준 브랜치 실행에서는 `user-todo-combined` 산출물도 올린다.
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

## 로컬에서
- 전체: `./gradlew -PskipOracle=true :furnace-api:test :furnace-server:test` (조각 속성 없음 = 전부).
- CI 와 같은 조각 하나만: `./gradlew -PskipOracle=true -PtestShard=2/3 :furnace-server:test` (조각 3개가 합쳐서 전체와 같다 — 4코어 로컬 기준 조각당 4~6분, 전체 13분).
- 조각 시간이 한쪽으로 쏠리면 `python3 -I tools/ci/shard-balance.py` 로 새 `shardSalt` 를 구해 `furnace-server/build.gradle.kts` 에 넣는다(안 해도 정확성은 같고 균형만 달라진다).
- 시험 하나가 흔들리면: `./gradlew -PskipOracle=true :furnace-server:test --tests '패키지.클래스'` 를 여러 번 돌려 재현한다.

## 기준 브랜치가 바뀌면
`ci.yml` 의 `on.push.branches` 와 `env.BASE_BRANCH` 두 곳을 새 이름으로 바꾼다.
