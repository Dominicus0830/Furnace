# 명령 큐 즉시 처리 (O11 P-O11-01, 2026-11-12)
- 원인: 전역 스레드의 명령 컨텍스트가 region 왕복을 보내면 그 region 의 "다음 틱 머리"에서야 돌았다. 즉시 처리(`Regionizer.urgentArrived` → drain 차례)는 `Region.URGENT_WAITERS>0`(명령 블록이 기다릴 때)일 때만 켜졌다.
- 수정: `world/CommandDrainGate`(명령 큐 컨텍스트가 도는 동안 열림, `CommandQueue.begin/끝`에서 enter/exit) + `Region.urgentOpen()`(= 명령 블록 대기자 있음 또는 문 열림). drain 차례 실행 시간과 명령 블록 기다림 시간을 같은 예산(`Region.URGENT_WAIT_BUDGET_NANOS`, 기본 20ms)에 더하고 `ServerCore.tick()` 머리에서 되살린다. 예산 소진 중에는 문이 닫혀 예전 경로(다음 틱)로 물러난다.
- 새 실행 순서를 만들지 않는다: 급한 작업(전역 스레드가 보낸 것)을 그 region 이 다음 틱 머리에서 돌릴 것을 앞당길 뿐이고, 줄 순서는 `ExecutionContext` 가 그대로 지킨다. 시험 `CommandRegionDrainTest` 가 켜고/끄고 같은 함수가 같은 점수·블록·엔티티 결과를 내는지 본다.
- 함정(시험): 데이터 팩 `tick.json` 은 `DataPackManager.start` 가 읽으므로 함수·태그 파일을 쓴 뒤에 start 해야 한다. `execute store result score … if entity @e[...]` 의 값은 일치한 엔티티 수다.
- 측정(4코어 헤드리스, 3초): 접근 3번 `#tick` 함수 61틱 중 61번(끄면 22번), 4번(setblock+if block) 61번, 40줄 `if block` 함수 4ms.
