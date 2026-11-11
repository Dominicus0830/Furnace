# A03-01·A03-02 수정 — 종료 저장 기준점, region 작업 순서 (2026-11-08)

2차 점검 A03 보고서(`docs/audit/findings/A03-scheduler-threads.md`)의 치명·높음 항목 수정 기록.

## A03-01 종료 저장 중 차원 간 엔티티 복제·소실

- 바닐라: `MinecraftServer.runServer` 루프가 끝난 뒤 `stopServer → saveAllChunks` — 저장 사이에 틱도 엔티티 이동도 없다.
- Furnace: `FLUSH_WORLDS` 머리에서 `World.holdForShutdown` 이 기준점을 잡는다.
  1. 전역(`ServerCore.holdGlobalTicks`)·모든 월드(`World.holdTicks`) 게임 틱 붙잡기 + `World.freezeTravel`(새 차원·region 사이 이동을 시작하지 않는다 — `Entity.teleportToDimension` 은 거짓, 포털·관문은 제자리).
  2. `quiesceGlobal`·각 `quiesce` 로 지금 도는 틱이 끝나기를 기다린다.
  3. `incomingTravels()` 합이 0 이 될 때까지(목적 청크를 읽는 중인 이동이 도착할 때까지) 기다린다.
  4. 한 번 더 `quiesce` — 도착이 남긴 작업까지 비운 뒤 저장 작업이 그 뒤에 선다.
  종료라 놓지 않는다. 붙잡혀도 region 은 작업 큐·지연 작업은 돌린다(퇴장 처리·저장이 그 길이다).
- 추가 월드 내리기(`WorldManager.unloadNow`): 틱을 끈 뒤 `freezeTravel` + 들어오는 이동이 도착할 때까지 기다린 다음 저장한다.
- 남은 차이: `/save-all`(틱이 도는 중 차원마다 저장)은 그대로다. 바닐라 save-all 은 주 스레드 틱 사이에 돌아 일관된 상태를 쓴다 — 크래시 뒤에만 보이는 차이라 따로 다룬다.
- 시험: `ShutdownConsistentCutTest`(저장 사이 이동 시도 → 각 소 1마리, 기준점 때 날아가던 이동 → 1마리).

## A03-02 region 합치기·나누기 때 같은 청크 작업 순서

- 계약(`FurnaceScheduler` 문서): 작업은 실행 시점 주인 region 에서 같은 스레드 순서로 돈다(Folia `RegionScheduler` 와 같다).
- 원인: 흡수·나누기가 구역 주인을 먼저 바꾸고 옛 큐를 나중에 새 큐 끝으로 옮겨, 그 사이 생산자가 넣은 새 작업이 옛 작업을 앞질렀다. 생산자 여럿이 죽은 큐를 함께 비우는 것도 순서를 섞었다.
- 수정: `Regionizer.route`(읽기/쓰기 잠금). 작업 넣기(주인 읽기 + 큐에 넣기)는 읽기, 주인 바꾸기(`absorb`·`split`·`kill`)와 옛 큐 옮기기는 쓰기. 불변식: 살아 있는 region 큐의 칸 작업은 그 region 이 주인인 칸 것뿐이다. 나누기에서 지운 구역 칸의 작업도 그때 새 주인에게 순서대로 보낸다(남겨 두면 나중에 그 칸의 새 작업 뒤로 밀린다).
- 잠금 순서: 쓰기는 늘 `lock` 안에서만 잡는다. 읽기를 쥔 채로는 다른 잠금을 잡지 않는다(구역 만들기는 읽기를 놓고 `lock` 으로 한 뒤 다시 넣는다). 그래서 교착이 없다.
- 비용: `RegionTaskOrderTest` 처리량은 수정 전과 같다(8초 181만 개 vs 181만 개, 20초 426만 vs 428만).
- 시험: `RegionTaskOrderTest`(기본 8초, `-Dfurnace.a03.toBridges=false`·`-Dfurnace.a03.durationMs=…` 로 바꿀 수 있다). 수정 전 2 588·3 865건 뒤바뀜, 수정 뒤 0.
