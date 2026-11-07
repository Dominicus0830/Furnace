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
| `LandMobTest` (`goatRams…`, `frogBrain…`, `shulkerShoots…`) | 6 | 몹 AI 난수(엔티티 난수가 시드 없이 `nanoTime` 으로 만들어짐) | 서버 동작 변경 없이는 못 고침 → CI 재시도로 흡수 |
| `ConnectionStateApiTest.pluginPackBlocks…`, `GameplayFlowTest.gameModeExperience…`, `SpreadPlayersCommandTest.spreadsCowsInRealWorld` | 각 4 | 접속·틱 대기 10초 초과 | 대기 한도를 20~30초로 늘림 |
| `GeneratedVillageProfessionTest`, `DispenserBlockTest.arrowIsShotWithPickupAllowed` | 각 1 | 난수 | CI 재시도로 흡수 |

## 지금 구조
- `server-tests (1/3~3/3)`: 서버 시험을 시험 클래스 이름 해시로 3조각으로 나눠 동시에 돈다(`-PtestShard=i/3`, `furnace-server/build.gradle.kts`).
  한 조각이 실패하면 **처음에 실패한 시험 클래스만** 한 번 다시 돌린다(전체 재실행 12분 → 1분 안팎). 다시 돌려도 실패하면 진짜 실패다.
  재시도에서 통과한 시험은 실행 요약의 "흔들린 시험"에 이름이 남는다 — 이 목록이 흔들리는 시험을 고칠 우선순위다.
- `light`: API 시험, 샘플 플러그인 컴파일, 시험 서버 모듈, fatJar. 기준 브랜치 실행에서는 `user-todo-combined` 산출물도 올린다.
- `test`: 위 둘을 합친 판정(점검 이름은 예전과 같은 `test`).
- `push` 는 기준 브랜치(`claude/quirky-wright-lra834`, `main`)에서만 돈다. 작업 브랜치는 PR 을 열면 `pull_request` 로 한 번 돈다.
  충돌이 있는 PR 은 `pull_request` 실행이 만들어지지 않는다 — 기준 브랜치를 합쳐 충돌을 풀면 다시 돈다.
- Gradle 캐시는 기준 브랜치 실행에서만 저장하고 PR 은 읽기만 한다.

## 로컬에서
- 전체: `./gradlew -PskipOracle=true :furnace-api:test :furnace-server:test` (조각 속성 없음 = 전부).
- CI 와 같은 조각 하나만: `./gradlew -PskipOracle=true -PtestShard=2/3 :furnace-server:test` (조각 3개가 합쳐서 전체와 같다 — 4코어 로컬 기준 조각당 4~6분, 전체 13분).
- 조각 시간이 한쪽으로 쏠리면 `python3 -I tools/ci/shard-balance.py` 로 새 `shardSalt` 를 구해 `furnace-server/build.gradle.kts` 에 넣는다(안 해도 정확성은 같고 균형만 달라진다).
- 시험 하나가 흔들리면: `./gradlew -PskipOracle=true :furnace-server:test --tests '패키지.클래스'` 를 여러 번 돌려 재현한다.

## 기준 브랜치가 바뀌면
`ci.yml` 의 `on.push.branches` 와 `env.BASE_BRANCH` 두 곳을 새 이름으로 바꾼다.
