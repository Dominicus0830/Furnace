# T-235 바닐라 차분 시험에서 배운 것 (2026-11-23)

- 바닐라의 정수 내림은 거의 모두 `Mth.floor`(`(int) v` 뒤 `v < i ? i - 1 : i`)다. `(int) Math.floor(v)` 는 아주 큰 음수·음의 무한대에서 `Integer.MIN_VALUE` 에 머무르지만 `Mth.floor` 는 넘쳐 `Integer.MAX_VALUE` 가 된다. 손 shim 에서 `Math.floor` 를 쓰면 경계값 시험에서 바로 걸린다(`Direction.fromYRot`, `FloatTag`·`DoubleTag.getAsInt`).
- `Direction.getNearest(double,double,double)` 은 인자를 먼저 float 로 바꾼다. double 로 곱하면 큰 값에서 다른 방향이 나온다.
- 바닐라 배열 NBT 의 `add` 는 `ArrayUtils.add` 라 범위 밖이 `IndexOutOfBoundsException`(배열판 하위 예외가 아님).
- Paper 는 NBT 배열 길이를 할당량 계산 전에 `checkArgument(n < 1<<24)` 로 막는다 — 바닐라 1.21.1 에는 없다. shim 주석에 "바닐라 Preconditions" 라고 적혀 있었지만 실제로는 Paper 패치다. 바닐라와 Paper 중 어느 쪽인지는 두 jar 의 바이트코드를 같이 보고 적는다(Paper 개발 묶음 jar: `~/.gradle/caches/paperweight-userdev/*/…/setupCache/applyMojangMappedPaperclipPatch.jar`, 커밋 금지).
- 차분 시험이 메모리 모자람을 만나면(깨진 길이로 큰 배열) 그 입력은 관측값이 아니라 힙 크기 문제라 건너뛴다. 이 입력들이 NbtIo 시험 시간의 대부분이다.
- JUnit 기본 제한시간(5분)은 `@TestFactory` 메서드에만 걸린다 — 무거운 일은 동적 시험 안에서 해야 클래스 하나가 전체 제한을 먹지 않는다.
