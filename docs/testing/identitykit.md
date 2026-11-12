# IdentityKit — "옛 구현과 결과가 같다" 증명 장치

최적화 PR 이 결과(관측 가능한 동작)를 바꾸지 않았다는 것을 **저장소 안의 시험으로** 남기기 위한 공용 도구다(O15 검토 T-O15-02, `furnace/reviews/opt-review/O15-tests-ci.md` 3장). 위치는 시험 소스 `furnace-server/src/test/java/org/domi/furnace/server/identity/` 뿐이라 운영 jar 에 들어가지 않는다.

이 장치는 **"우리 옛 커밋 == 새 커밋"** 만 증명한다. 바닐라와의 동일성은 기존 oracle fixture·바닐라 jar 대조 하네스의 몫이다. 스레드 경쟁(동시성)도 장면 해시로는 못 잡으므로 지금처럼 `@NoRetry` + `@RepeatedTest`·결정적 장벽 시험으로 한다.

## 부품

| 부품 | 쓰는 곳 | 핵심 |
|---|---|---|
| `IdentityHash` | 모든 해시 | `h = (h ^ v) * FNV_PRIME`. 기존 `NoiseIdentityBench`·O04·O07 도구와 같은 식이라 그 값들과 이어진다 |
| `Differential` | 순수 함수 차분(옛 함수 vs 새 함수) | 반복마다 (시드, 번호)에서 따로 만든 `SplittableRandom` 입력, 실패 시 시드·번호·입력 요약·재현 명령, 변이 점검 `assertDetects` |
| `SceneHash` | 장면 상태 해시 | 장면 줄을 쌓고 끝에 `TOTAL`. **결정성 자기 점검**(같은 JVM 에서 두 번 돌려 다르면 실패) 뒤 금 파일 비교 |
| `Golden` | 금 파일 비교 | `src/test/resources/identity/<이름>.txt` 와 줄 단위 비교, 불일치 시 `build/identity/actual/<이름>.txt` 에 지금 결과 |
| `PacketTape` | 나가는 패킷 대조 | 시험 채널에서 (id, 본문) 열을 모은다. 압축·프레임은 풀어서 비교. `assertSame`·`hash`·`lines` |
| `Mca` | `.mca` 바이트 대조 | `assertSameBytes(dirA, dirB[, 시각 무시])`, `fixClock(RegionFile, 초)` |
| `@LegacyOracle` | 옛 구현 사본 | 아래 "옛 구현 보관" 규칙, `LegacyOracleRulesTest` 가 검사 |

본보기:
- 차분 + 레거시 오라클: `world/ChunkIoFastPathParityTest`(+ `identity/legacy/chunkio/LegacyColumnScans`)
- 장면 해시 + 금 파일: `world/gen/NoiseIdentityGoldenTest`(평소 시험, `worldgen-noise-quick`), `NoiseIdentityBench.identityHash`(`-Dfurnace.bench=true`, `worldgen-noise-full`), `PristineHashBench`(`-Dfurnace.profile=true`, `worldgen-pristine`·`worldgen-structure-starts`)
- 패킷 녹음: `world/ChunkSendPacketOrderGoldenTest`

## 차분 시험 쓰는 법

```java
Differential.<Input, Result>of("이름", r -> 입력생성(r))   // r 만 써서 결정적으로
        .seed(0x1234).iterations(500)
        .describe(Input::summary)                         // 실패 메시지용 짧은 요약
        .check(LegacyFoo::compute, foo::compute);         // 옛 구현, 새 구현
```

- 옛·새 구현은 각자 새로 만든 입력을 받는다(한쪽이 입력을 고쳐도 섞이지 않는다).
- 결과 비교 기본은 `Objects.deepEquals`(배열은 내용). 둘 다 같은 종류의 예외를 던지면 같은 결과다.
- 시험 하나에 변이 점검 하나를 권한다: `assertDetects(옛, 일부러틀린것)` — 생성기가 그 틀린 경우를 만들지 못하면 실패한다.
- 실패 재현: 메시지의 `-Dfurnace.identity.seed=… -Dfurnace.identity.iter=…` 를 붙여 그 시험 하나만 돌린다(`--tests`).

속성(Gradle 이 `furnace.*` 를 시험 JVM 에 넘긴다):

| 속성 | 기본 | 뜻 |
|---|---|---|
| `furnace.identity.scale` | 1 | 차분 반복 수 배수(야간·수동 실행에서 100 권장) |
| `furnace.identity.randomSeeds` | 0 | 고정 시드 뒤 무작위 시드 N 개 추가, 시드를 출력. 실패한 시드는 고정 시드 시험에 추가한다 |
| `furnace.identity.seed` / `.iter` | — | 재현 모드: 그 시드(·반복 하나)만 |
| `furnace.identity.determinismRuns` | 2 | 장면 결정성 점검 반복 수 |
| `furnace.identity.bless` | — | 금 파일 갱신(이름을 쉼표로 하나씩. `*`·`all` 거부) |

평소 CI 는 고정 시드만 쓴다 — 결정적이라 CI 재시도에 가려지는 확률 실패가 아니다.

## 장면 해시 쓰는 법

```java
SceneHash.assertGolden("scene-name", rec -> {
    World w = 새월드(고정시드);            // 매번 새 상태에서 시작
    for (int t = 0; t < 200; t++) { 틱(); rec.line("tick " + t + " mobs", 엔티티해시(w)); }
});
```

- 줄은 "무엇 어디 해시" 꼴로(금 파일 diff 를 사람이 읽는다). `TOTAL` 줄은 자동.
- 해시에 넣지 않는 것: 정체 해시(`System.identityHashCode`·기본 `hashCode`), `HashMap<객체>`·`ConcurrentHashMap` 순회 순서, 시각, `nanoTime` 씨앗 난수. 결정성 점검이 대부분을 잡지만 JVM 사이에서만 달라지는 것은 못 잡는다.
- **같은 JVM 모드(`assertGolden`)와 새 JVM 모드(`assertGoldenInFreshJvm`)**: 엔티티가 든 장면은 새 JVM 모드를 쓴다. 엔티티·레벨 난수는 T-O15-01(PR #272) 이음매로 `-Dfurnace.test.seeded=true` 일 때 주인 열쇠에서 결정되지만, 시험 레벨의 엔티티 id 카운터(`ChunkLevel.FALLBACK_IDS`)와 정적 난수(`Sensor.RANDOM`·`ShufflingList.RANDOM`)는 JVM 전역이라 같은 JVM 의 두 번째 실행이나 앞서 돈 다른 시험(시험 조각 구성)에 따라 UUID·난수가 달라진다. 새 JVM 모드는 실행마다 시험 클래스패스로 새 JVM 을 띄우고(씨앗 켜짐), 장면 클래스(public, 인자 없는 생성자)를 돌린다 — 한 번에 JVM 시작 비용(약 1~2초)이 든다. 본보기: `identity/EntitySelftestScene` + `IdentityKitTest.FreshJvm`. 같은 JVM 모드는 정적 상태를 건드리지 않는 월드젠·순수 계산 장면용이다.
- 새 JVM 이어도 이음매 밖 난수(`ThreadLocalRandom`·`Math.random`·`UUID.randomUUID`·`WorldClock` 의 `new Random()` 등 — 목록은 `docs/feature-parity/context-notes/2026-11-12-random-seeds-seam.md`)를 거치는 장면은 결정적이지 않다. 결정성 점검이 잡으면 그 지점을 같은 이음매로 옮기는 것이 먼저다.
- region 은 하나, 시험 스레드가 직접 틱을 돌리는 장면으로 한정한다(여러 region 스레드 순서는 비결정).

## 금 파일 갱신 (bless)

금 파일은 결과 동일의 기준이다. **최적화 PR 은 금 파일(`src/test/resources/identity/*.txt`)을 바꾸지 않는다.** 금 파일이 바뀌었다는 것은 결과가 달라졌다는 뜻이다.

갱신이 허용되는 경우:
1. **의도한 정확성 수정**(바닐라와 달랐던 동작을 바로잡음) — 바닐라 근거가 있어야 한다.
2. **장면 자체를 바꿈**(새 장면 추가, 장면 입력·범위 변경) — 결과가 아니라 질문이 바뀐 것.

절차(도미니코 승인 11-12 — `docs/user-todo/entries/2026-11-12-identitykit-golden-update.md`):
1. 갱신 명령: `./gradlew :furnace-server:test --tests '<시험>' -Dfurnace.identity.bless=<이름>[,<이름>…]`(bench 장면은 해당 `-Dfurnace.bench=true`/`-Dfurnace.profile=true` 도). 이름을 하나씩 적는다.
2. PR 본문에 **"금 파일 갱신"** 절: 바뀐 파일, 바뀐 줄 수, 왜 결과가 달라지는지(위 1·2 중 무엇), 바닐라 근거(정확성 수정이면).
3. **독립 검토자 스레드가 바뀐 줄을 확인하고 승인**해야 병합한다(기존 병합 규칙: CI `test` + 독립 검토 위에 이 확인을 더한다). 금 파일 변경과 최적화를 한 PR 에 섞지 않는다.
4. 금 파일 변경 PR 에 CI 알림을 다는 것은 T-O15-05(CI) 몫이다.

`ChunkSendPacketOrderGoldenTest` 의 기대 열처럼 시험 코드 안에 적은 기대값도 같은 규칙을 따른다.

## 옛 구현 보관 (레거시 오라클)

차분 시험의 옛 구현은 기준 커밋에서 **복사해 얼린 사본**이다. 오래 두면 운영 코드와 따로 놀아 "거짓 오라클"이 되므로 수명을 둔다. `LegacyOracleRulesTest` 가 1·3·4 를 검사한다.

1. 파일 첫 줄 `// LEGACY-ORACLE since <커밋>: <무엇의 사본>`, 바깥 클래스에 `@LegacyOracle(since = "<같은 커밋>", copyOf = "클래스#메서드", coveredBy = "<장면 이름, 선택>")`.
2. 계산은 그 커밋의 운영 코드 그대로. 클래스 밖에서 돌게 하는 각색(필드 → 인자, private 판정 → 넘겨받는 함수)만 허용하고 머리에 적는다.
3. 차분 시험 하나 이상이 써야 한다. 아무도 안 쓰면 지운다.
4. `coveredBy` 의 장면 금 파일이 생기면 그 영역은 장면 해시가 지키므로 지운다.
5. 위치: 공개 API 만 쓰면 `identity/legacy/<파트>/`, 패키지 비공개 접근이 필요하면 운영 코드와 같은 패키지에 `Legacy<이름>` 으로.

옛 구현을 따로 두지 않아도 되는 경우(새 구현이 옛 함수를 지우지 않고 옆에 더하는 경우)에는 운영 코드의 옛 함수를 그대로 오라클로 쓴다 — 사본이 없으니 이 규칙도 필요 없다.

## 금 파일 안정성 (확인한 것과 가설)

- `worldgen-noise-quick` 은 4코어 클라우드 컨테이너에서 만들었고, 같은 파일이 GitHub Actions 러너(CI `test`)에서 통과하는지로 환경 간 안정성을 확인한다(이 PR 의 CI).
- double 연산은 Java 17+ 에서 항상 strict 라 플랫폼 무관하다고 본다. `Math.sin` 류 내장 함수가 장면에 들어가면 `StrictMath` 와 결과가 다를 수 있다 — 가설, 그런 장면을 만들 때 두 환경에서 확인한다.
