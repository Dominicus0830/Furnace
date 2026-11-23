# T-235 바닐라 차분 시험에서 배운 것 (2026-11-23)

- 바닐라의 정수 내림은 거의 모두 `Mth.floor`(`(int) v` 뒤 `v < i ? i - 1 : i`)다. `(int) Math.floor(v)` 는 아주 큰 음수·음의 무한대에서 `Integer.MIN_VALUE` 에 머무르지만 `Mth.floor` 는 넘쳐 `Integer.MAX_VALUE` 가 된다. 손 shim 에서 `Math.floor` 를 쓰면 경계값 시험에서 바로 걸린다(`Direction.fromYRot`, `FloatTag`·`DoubleTag.getAsInt`).
- `Direction.getNearest(double,double,double)` 은 인자를 먼저 float 로 바꾼다. double 로 곱하면 큰 값에서 다른 방향이 나온다.
- 바닐라 배열 NBT 의 `add` 는 `ArrayUtils.add` 라 범위 밖이 `IndexOutOfBoundsException`(배열판 하위 예외가 아님).
- Paper 는 NBT 배열 길이를 할당량 계산 전에 `checkArgument(n < 1<<24)` 로 막는다 — 바닐라 1.21.1 에는 없다. shim 주석에 "바닐라 Preconditions" 라고 적혀 있었지만 실제로는 Paper 패치다. 바닐라와 Paper 중 어느 쪽인지는 두 jar 의 바이트코드를 같이 보고 적는다(Paper 개발 묶음 jar: `~/.gradle/caches/paperweight-userdev/*/…/setupCache/applyMojangMappedPaperclipPatch.jar`, 커밋 금지).
- 차분 시험이 메모리 모자람을 만나면(깨진 길이로 큰 배열) 그 입력은 관측값이 아니라 힙 크기 문제라 건너뛴다. 이 입력들이 NbtIo 시험 시간의 대부분이다.
- JUnit 기본 제한시간(5분)은 `@TestFactory` 메서드에만 걸린다 — 무거운 일은 동적 시험 안에서 해야 클래스 하나가 전체 제한을 먹지 않는다.

- **CI 는 이 환경보다 약 7배 느리다(11-23 PR 559).** 바닐라 NbtIo 읽기는 실패마다 `ReportedNbtException` 에 CrashReport(시스템 정보 수집)를 붙여 입력당 약 10ms 가 든다. 입력 3000 이면 CI 에서 읽기 멤버가 감시 시간 120초를 넘겨 "timeout" 다름으로 실패했다 → NbtIo 만 입력 1000(이 환경 20초). 무거운 예외를 던지는 클래스를 대상에 더할 때는 `-PvdiffClasses=<클래스>` 로 보고서의 클래스 시간을 보고, 이 환경에서 멤버당 15초 아래로 입력 수를 정한다.
- **입력 1000 으로도 CI 에서 120초를 넘었다(11-23 PR 563).** CI 에서는 바닐라 NBT 읽기 실패 한 번이 수십~백 ms 다 — CrashReport 의 SystemReport 가 매번 OSHI 로 하드웨어를 훑는다(바닐라 바이트코드 `SystemReport.putHardware(oshi.SystemInfo)`). 입력 수를 더 깎는 대신 **느린 클래스에만** 시간 예산을 켠다(대상 파일 class 줄 `budget=<초>`, 지금은 NbtIo 45초, `-PvdiffBudget=<초>` 로 바꿈). 경계값 입력 400개(고정 훑기 포함)는 예산과 무관하게 다 돌고, 그 뒤 난수 입력에서 예산을 넘으면 멈추고 "ran N of M inputs" 를 멤버 결과와 보고서 머리 줄("예산으로 멈춘 멤버", CI 요약에 보임)에 적는다. 예산이 없는 클래스(값 클래스)는 멈추지 않고 300초 감시를 넘기면 실패다(벽시계 예산이 값 클래스의 시험 깊이를 흔들지 않게 — PR 563 재검토).
