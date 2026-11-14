# T-150 명령 후속 단계의 명령 범위 (2026-11-14)

F2(O11 P-O11-01, PR #271)는 "명령 범위"(CommandDrainGate)에서 region 에 넣은 작업만 틱 사이 drain 으로 곧바로 돌린다. 그 뒤에도 엔티티를 고르는 명령이 단계·소스마다 한 틱(50ms)씩 밀린 까닭은 둘이었다.

1. **후속 단계의 전역 실행기가 범위 밖**: `EntityOps.thenOnGlobal`·`awaitAsync`, `ExecuteCommands.onGlobal`·`swallowSyntaxErrors` 가 `global().execute(r)` 로 돌아와, 거기서 넣는 다음 홉에 명령 표시가 없었다. → `EntityOps.commandGlobal(source)` 실행기(전역 + `inCommandScope`)로 통일. `spreadplayers` 의 땅 읽기 가상 스레드도 범위 안에서 돈다.
2. **drain 차례가 끝난 뒤 다시 보지 않음**: drain 중인 region 이 자기에게 넣은 다음 홉(`@s` 판정 → `data get entity @s` 처럼 region 스레드에서 이어 넣는 두 번째 홉)은 `urgentArrived` 가 "이미 도는 중"이라 건너뛰고, `runUrgentTasks` 의 `ConcurrentLinkedQueue` 반복자는 **마지막 칸을 꺼낼 때 다음 칸을 미리 정해 두므로** 그 뒤에 붙은 작업을 못 본다. 그래서 다음 region 틱(최대 50ms)까지 기다렸다. → `Regionizer.runDrainPass` 끝에서 틱 뒤(`finishTick`)와 같이 `drainWanted()` 면 drain 을 다시 준다. 문 예산을 다 쓰면 `drainWanted()` 가 거짓이라 끝없이 돌지 않는다.

함정: CLQ 반복자는 "약한 일관성"이라 반복 중 추가된 원소를 볼 수도 못 볼 수도 있다 — 마지막 원소 처리 중 추가된 것은 못 본다. 반복으로 "급한 것만 골라 꺼내는" 곳은 끝난 뒤 다시 확인해야 한다.

측정(4코어 컨테이너, 운영 예산 `-Dfurnace.commandBlockWaitMicros=20000`, 소 20마리 NoAI, 명령마다 5번 중앙값): 고치기 전 `tag @e` 50ms, `execute as @e run tag @s` 1050ms, `execute as @e at @s rotated as @s run tp @s ~ ~ ~ ~ ~` 3050ms, `execute as @e store result score @s … run data get entity @s …` 1050ms. 고친 뒤 명령 59종 모두 중앙값 0.1~17ms(`execute … summon` 16.8ms 가 최대). 시험 `CommandSelectorStageTest`(T-151).

결과는 바뀌지 않는다: 소스 적용 순서는 `AsyncStage`(BuildContexts)가 한 명씩 지키고, 바뀌는 것은 전역 스레드에서 넣던(원래도 급한) 작업에 붙는 명령 표시와 drain 시점뿐이다. 문을 끈 변형(`commands.region-drain=false`)과 엔티티별 결과(순서·점수·태그·NBT·좌표)가 같음을 시험으로 고정했다.

남은 것: `locate` 는 전용 작업 스레드라 범위 밖(명령 하나가 원래 수백 ms 라 영향 없음). 틱 중인 region 으로의 홉은 그 틱이 끝날 때까지 기다린다(F2 와 같음). 소스가 수백이면 문 예산(20ms/전역 틱) 때문에 2~3틱이 남는다(M-150).
