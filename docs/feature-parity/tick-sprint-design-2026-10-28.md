# /tick sprint 설계 (2026-10-28)

바닐라 1.21.1 `ServerTickRateManager` 의 sprint 를 region 별 틱 구조에서 어떻게 옮겼는지 적는다.

## 1. 바닐라 동작 (서버 jar 디컴파일에서 확인)
- `requestGameToSprint(ticks)`: 이미 sprint 중이었는지를 돌려준다. 남은 틱·요청 틱을 `ticks` 로, 작업 시간 합을 0 으로,
  지금 정지 여부를 기억해 둔 뒤 정지를 푼다(`setFrozen(false)` → ticking_state 방송). 진행 중인 step 수는 건드리지 않는다.
- 서버 루프: sprint 중이면 다음 틱 시각을 기다리지 않는다(`checkShouldSprintThisTick` 이 참이면 nextTickTime = now).
  틱마다 남은 수를 하나 줄이고, 틱 작업 시간(`startTickWork` ~ `endTickWork`)을 더한다. 남은 수가 0 이 되면 끝낸다.
- `finishTickSprint`: 진행 틱 = 요청 − 남은 수, ms = max(1, 작업 시간 합 ns) / 1e6, 초당 틱 = (int)(1000 · 틱 / ms),
  틱당 ms = 틱이 0 이면 지금 틱 주기(ms), 아니면 ms / 틱 을 `%.2f` 로. `commands.tick.sprint.report` 를 서버 명령 소스로
  (운영자 알림 포함) 보내고, 기억해 둔 정지 상태로 되돌린다(방송). 끝으로 자동 저장 간격을 다시 계산한다(`MinecraftServer.F`).
- 명령: `/tick sprint <time>` → (이미 sprint 중이었으면 `commands.tick.sprint.stop.success`) + `commands.tick.status.sprinting`,
  값 1. `/tick sprint stop` → sprint 중이면 끝내고(보고 포함) `stop.success`·1, 아니면 실패 `stop.fail`·0.
  `/tick freeze` 는 sprint 중이면 먼저 끝낸다(보고 포함). `/tick query` 는 sprint 중이면 `status.sprinting` +
  `query.rate.sprinting(속도, 평균 ms)`. sprint 중에는 정지가 풀려 있어 `/tick step` 은 실패한다.

## 2. Furnace 의 틱 구조 (코드로 확인)
- 전역 시계(`gameTime`·`dayTime`·날씨·보더·함수)는 `ServerCore.tick()` 하나다. 전역 스레드가 50ms 마다
  `ServerCore.scheduledTick()` 을 부르고, `/tick rate` 는 그 한 번에 몇 틱을 낼지(credit)로 반영된다.
- region 은 월드마다 `Regionizer` 의 마감 시각 큐(`nextTickNanos`)로 돈다. 틱이 끝나면 `afterTick` 이 다음 마감을
  `nextTickNanos += tickNanos` 로 잡는다. 예약 틱 기준 시계는 region 마다 따로(Folia 의 redstoneTime 과 같은 방식)라 region 끼리,
  그리고 전역 시계와 한 줄로 묶이지 않는다. `/tick step` 도 이미 "전역 N 틱 + region 마다 N 틱"으로 나눠 구현돼 있다.

## 3. 설계
step 과 같은 방식으로 "전역 N 틱 + 각 region N 틱"을 기다림 없이 돌리고, 모두 끝났을 때 한 번 보고한다.
- `TickState`: sprint 세대·진행 여부·요청 틱·남은 전역 틱·시작 시각·기억한 정지 상태. `startSprint` 는 정지를 직접 풀고
  (step 은 그대로 — 바닐라와 같다) 세대를 올린다. `endSprint` 는 진행 틱(전역 기준)과 벽시계 시간을 돌려주고 정지를 되돌린다.
- region(`Region.tick`): sprint 세대가 바뀌면 남은 sprint 틱을 다시 채운다(sprint 중이면 N, 아니면 0). 남았으면 이번 틱을
  sprint 틱으로 쓰고(정지여도 월드를 진행), `Regionizer.afterTick` 은 남은 sprint 틱이 있으면 다음 마감을 지금으로 잡는다
  (같은 풀의 다른 region 과는 마감 순서로 번갈아 돈다 — 한 region 이 풀을 독점하지 않는다). 다 쓰면 원래 주기로 돌아간다
  (`nextTickNanos < now` 면 now + tickNanos 로 재동기 — 따라잡기 폭주 없음).
  - 틱 뒤 잠금 안에서 "본 세대·남은 sprint 틱 있음"을 요약해 둔다. 나뉜 조각은 부모의 세대·남은 틱을 물려받고, 새로 생긴
    region 은 생성 시점 세대에 남은 틱 0(step 과 같은 규칙). 흡수된 region 의 남은 틱은 사라진다(흡수한 쪽은 자기 것을 잇는다).
- 전역(`ServerCore.scheduledTick`): sprint 중이면 보통 credit 대신, 한 번 호출에 최대 40ms 동안 남은 전역 sprint 틱을
  연달아 돌린다(그 사이 전역 인박스·명령이 돈다). 전역 틱을 다 썼고 모든 차원의 모든 region 이 그 세대를 보고 sprint 틱을
  다 돌았으면(`Regionizer.sprintDone`, 잠금 안에서 요약만 읽는다) `finishSprint` 로 보고·정지 복구·ticking_state 방송.
- 플레이어 패킷: region 틱이 빨라지므로 플레이어 처리·엔티티 갱신 방송도 같은 비율로 빨라진다(바닐라도 매 틱 보낸다).
  클라이언트에 sprint 를 알리는 별도 패킷은 바닐라에도 없다(정지 해제·복구의 ticking_state 만).
- 시계: 전역 시계는 정확히 N 틱 간다. region 은 각자 N 틱(나뉜 조각은 부모의 남은 수만큼).

## 4. 바닐라와의 차이 (의도·가정)
- 보고의 시간: 바닐라는 한 스레드의 틱 작업 시간 합이다. Furnace 는 region 이 병렬이라 "작업 시간 합"이 서버 진행 시간과
  다르다(스레드 수만큼 부풀거나 겹친다). 그래서 sprint 시작부터 전역·모든 region 이 끝날 때까지의 **벽시계 시간**을 쓴다.
  바닐라의 한 스레드 서버에서는 sprint 중 대기가 없어 두 값이 거의 같다(가정: 인박스 처리 시간만큼 차이).
- 진행 틱 수(보고·stop 의 값)는 전역 틱 기준이다. stop 때 region 마다 실제로 돈 수는 조금씩 다를 수 있다.
- sprint 도중 새로 생긴 region(새로 올라온 먼 청크)은 sprint 틱을 받지 않는다(step 과 같은 규칙).
- region 이 끝나기를 기다리므로, 무거운 region 이 있으면 전역 시계가 먼저 N 틱을 채우고 region 을 기다린다.
- 자동 저장 간격 재계산(`MinecraftServer.F`)은 하지 않는다 — Furnace 자동 저장은 틱 수가 아니라 별도 주기다(가정, 미확인).
