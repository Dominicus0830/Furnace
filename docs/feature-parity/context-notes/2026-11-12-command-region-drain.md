# 명령 큐 즉시 처리 (O11 P-O11-01, 2026-11-12)
- 원인: 전역 스레드의 명령 컨텍스트가 region 왕복을 보내면 그 region 의 "다음 틱 머리"에서야 돌았다. 즉시 처리(`Regionizer.urgentArrived` → drain 차례)는 `Region.URGENT_WAITERS>0`(명령 블록이 기다릴 때)일 때만 켜졌다.
- 수정: `world/CommandDrainGate`. **명령 범위**(`CommandQueue.drive` 가 도는 전역 스레드, 그리고 명령 작업이 도는 region 스레드)에서 `Regionizer.enqueue` 로 넣은 작업에 `Task.command` 를 단다. 명령 작업은 문이 열려 있으면(옵션 켬·이번 틱 예산 남음) `urgentArrived` 가 drain 차례를 곧바로 준다. 명령과 무관한 전역 작업(플러그인 전역 스케줄러·전역 틱 몸통)은 범위 밖이라 예전처럼 다음 틱 — 검토 R2(문이 컨텍스트 수명 동안 열려 무관한 전역 작업까지 앞지르던 것)를 이 표시로 고쳤다. 컨텍스트 수명에 매인 짝(enter/exit)이 없어져 검토 R1(close 시간 초과 때 문이 영구히 열림)도 사라졌다.
- **두 번째 홉(R4)**: `findRiders().thenCompose(runOnOwner)` 의 이어지는 부분은 region 스레드에서 돈다. 명령 작업은 `Region.runTask(Task)` 가 그동안 명령 범위를 세우므로 그 안에서 넣은 작업도 `command`·`urgent` 가 된다(`data get entity @e[..]`, `attribute`, `item` …). `urgent` 는 "전역 스레드가 보냈거나 명령 범위"가 되어 명령 블록 `awaitUrgent` 도 이 두 번째 홉을 기다리는 동안 돌려 준다.
- 예산: `Region.runUrgentTasks` 가 바깥 호출 하나의 실행 시간만 한 곳에서 `CommandDrainGate.charge`(명령 블록 기다림과 drain 차례가 한 예산 — 20ms, `ServerCore.tick()` 머리에서 되살림). 명령 블록이 기다리지 않는 drain 차례(문 전용)는 남은 예산을 마감으로 받아 넘으면 멈춘다(R3), 나머지는 region 틱 머리에서(틱 머리 마감 25ms). 예전에 `awaitUrgent` 와 drain 이 같은 시간을 두 번 세던 것도 한 곳으로(R8).
- 순서: 줄 순서는 `ExecutionContext` 가 지킨다. 명령 작업은 같은 region 큐에 먼저 들어온 **다른 출처의** 비급한 작업을 앞지를 수 있다(명령 블록 대기 때 이미 그랬다). 출처가 다르면 바닐라에도 순서가 없다.
- 교착: 전역 스레드는 막히지 않고 홉으로 비동기 재개한다. drain 은 `ticking`·`yielded`·`borrowWanted` 인 region 을 건너뛰므로 GlobalWait 중인 region 에 기다리지 않는다 — 안 돌면 예전처럼 그 region 의 다음 틱에 돈다.
- 미측정(R6): RegionBorrow 와의 비교는 하지 않았다(첫 단계 지시가 기존 드레인 문 확장). `#tick` 전역 틱 대기(도미니코 결정)와 함께 후속에서 잰다.
- 함정(시험): 데이터 팩 `tick.json` 은 `DataPackManager.start` 가 읽으므로 함수·태그 파일을 쓴 뒤에 start 해야 한다. `execute store result score … if entity @e[...]` 의 값은 일치한 엔티티 수다. 시험 JVM 은 문 예산을 500ms 로 둔다(`furnace-server/build.gradle.kts`) — 예산 시험은 `CommandDrainGate.charge` 로 남은 예산을 줄여서 본다.
- 측정(4코어 헤드리스, 3초): 접근 3번 `#tick` 함수 61틱 중 61번(끄면 22번), 40줄 `if block` 함수 4ms.
