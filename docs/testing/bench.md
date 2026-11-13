# 벤치 소스셋과 실행기 (T-002 / O15 T-O15-03)

벤치는 `furnace-server/src/bench/java` 에 둔다. `src/test` 가 아니다. 평소 `:furnace-server:test` 에는 들어가지 않으므로 시험 요약에 "건너뜀"이 섞이지 않는다(옛 `assumeTrue(furnace.bench)` 약 16곳을 없앴다 — 이동한 벤치 파일 기준).

## 실행
```
./gradlew -PskipOracle=true :furnace-server:bench                                # 전부(약 8분)
./gradlew -PskipOracle=true :furnace-server:bench --tests '*RegionTickBench*'    # 하나만
./gradlew -PskipOracle=true :furnace-server:bench --tests '*RegionTickBench*' -Dfurnace.bench.scene=clock -Dfurnace.bench.ticks=600
```
- 명령줄의 모든 `-Dfurnace.*` 는 벤치 JVM 으로 그대로 넘어간다. 속성 이름은 `furnace.bench.*` 하나다(`furnace.profile`·`furnace.profile.nostruct`·`furnace.profile.pristine` 은 `furnace.bench`·`furnace.bench.nostruct`·`furnace.bench.pristine` 으로 바뀌었다).
- `PristineHashBench`(이동 전)는 아직 `furnace.profile` 을 읽는다. 옮길 때 같이 `furnace.bench` 로 바꾼다.
- `furnace.sweep` 은 벤치가 아니라 옵트인 정합 시험(`ChunkSweepParityTest`, `src/test`)이라 그대로다.
- 소스셋 클래스패스에는 `src/main` 과 `src/test` 출력이 들어 있다. 같은 패키지 이름을 쓰므로 패키지 비공개 멤버·시험 도우미에 닿는다.
- 시험(`test`)은 `compileBenchJava` 에 의존한다 — 벤치를 돌리지는 않지만 컴파일이 깨지면 바로 알린다.

## 고정된 JVM 옵션 (`furnace-server/build.gradle.kts`)
| 옵션 | 이유 |
|---|---|
| `-Xms3g -Xmx3g` (`-Pbench.heap=…` 로 변경) | 힙 크기가 GC 빈도를 바꿔 틱 시간이 흔들린다 |
| `-XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints` | 없으면 JFR 이 자기 시간을 엉뚱한 줄에 몰아 준다(O08 실측: 가짜 36% 가 실제 0.1%) |
| `-XX:FlightRecorderOptions:stackdepth=512` | 기록 스택 깊이. 읽을 때도 `jfr print --stack-depth 400` 을 준다(기본 5 면 단계 분류가 전부 "기타") |
| `-Dfurnace.tickstats=true` (`-Pbench.tickstats=false` 로 끔) | `Regionizer.STATS` 가 `static final` 이라 JVM 시작 때 켜야 한다. 꺼져 있으면 `RegionTickBench` 의 단계별 시간(phases)이 전부 0 으로 찍혔다 |
| `furnace.bench=true`, `furnace.bench.outDir`, `furnace.bench.commit` | 벤치 표시·JSON 위치·기준 커밋 |

`tickstats` 는 단계 사이 `nanoTime` 몇 번을 더 부른다(틱당 한두 µs). 켠 채 재는 것이 기본이고, 끈 값과 비교하려면 `-Pbench.tickstats=false`.

## JSON 출력
`build/bench/<벤치>[-<변형>].json`. 도우미 `org.domi.furnace.server.bench.BenchReport`. 공통 칸: `commit`, `bench`, `variant`, `scene`, `seed`, `jvm{version,vm,cpus,maxHeapBytes,os,args}`. 나머지는 벤치가 넣는다: `tick_mean_ms`·`tick_p50_ms`·`tick_p95_ms`·`tick_max_ms`, `allocBytesPerTick`, `phase_*_ms`, `hash` 등(단위는 키 끝).
지금 JSON 을 내는 벤치: `RegionTickBench`, `PlayerWalkTickBench`, `MultiDimensionTickBench`, `EntityPerfBench`. 나머지 벤치는 아직 `printf` 줄만 낸다(새 벤치와 고치는 벤치부터 `BenchReport` 로).

벤치 JVM 에는 JaCoCo 에이전트를 붙이지 않고(`-Pcoverage` 와 무관) G1 을 명시한다. `allocBytesPerTick` 은 결정적 값이 아니다: 걷는 장면에서 같은 커밋 연속 4회가 825K~1014K(±15%)였다. 게이트로 쓰려면 T-005 에서 예열·반복 규칙을 먼저 잰다.

**절대 시간으로 CI 를 막지 않는다**(`docs/ci.md`, O15 4.2절). 같은 커밋의 같은 벤치가 ±7~15% 흔들린다. 게이트는 해시·틱당 할당 같은 결정적 값만 후보다(T-005 에서 측정 먼저).

## `RegionTickBench` 의 바뀐 점
- 기본이 **실서버 모양 이벤트 입구**다: 플러그인 이벤트 처리기가 0개여도 `hasSubscribers` 확인을 한다(`GameEvents.NONE` 이면 건너뛰어 레드스톤 장면이 약 5% 싸게 나왔다 — O08 반론 B). 옛 길로 재려면 `-Dfurnace.bench.realEvents=false`. **이전 숫자와 직접 비교하지 말 것.**
- 속성(`scene`, `nospawn`, `runtick` 등)을 시작 때 한 번만 읽는다. 틱·게임규칙 질의마다 `System.getProperty` 를 부르던 비용(O07 이 지적한 `isRaining` 2.5%)이 사라진다.
- 틱당 region 스레드 할당 바이트(`allocBytesPerTick`)를 잰다.
- `tickstats` 가 꺼져 있으면 경고를 찍는다.

## 아직 옮기지 않은 것
- `NoiseIdentityBench`·`PristineHashBench`: IdentityKit(T-001) 이 같은 파일과 새 평소 시험 `NoiseIdentityGoldenTest` 를 고치므로 그 PR 이 병합된 뒤 옮긴다. 옮길 때는 장면 부분(`configs`·`positions`·`hashColumn`·`hashRouter`·`record`)을 `src/test` 도우미로 떼고 타이밍·`main` 만 `src/bench` 로.
- `-Dfurnace.bench=true` 로 시험 클래스 안에서 도는 비교 벤치(`PaletteWireParityTest`·`AnvilBulkDecodeParityTest`·`ChunkSnapshotCompactCopyTest`·`RegionDeflateOutsideLockTest`·`EnterPlayAsyncLoadTest`·`ClimateYGateParityTest`)는 정합 시험과 한 클래스라 그대로 둔다(`bench` 작업이 아니라 `test` 에서 `-Dfurnace.bench=true` 를 줘야 돈다).
- 벤치 JSON 나머지 9개, `tools/bench/` JFR 도우미 스크립트 이관, `bench.yml`(야간·수동)은 T-005 와 후속.
