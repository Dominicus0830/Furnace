# T-172 생성 조절기(worldgen.tick-guard) — 2026-11-14

결정 W-2(도미니코 확정): 생성 조절기는 **기본 꺼짐**, `furnace.properties` 의 `worldgen.tick-guard=true`(또는 `-Dfurnace.gen.tickGuard=true`, 시스템 속성이 이김)로 켠다.

## 무엇을 하나
- W60 시제품(`GenPriorityExecutor` 의 `-Dfurnace.proto.genThrottle`)을 T-170 생성 대기열(`GenQueue`)로 옮겼다. 시제품에는 있었지만 기준 브랜치에는 없었다 — T-172 는 "기본값 바꾸기"가 아니라 "꺼진 채로 들이기"다.
- 켜면 `furnace-gen-throttle` 스레드가 250ms 마다 모든 차원의 가장 느린 region 의 최근 1초 TPS(`Regionizer.slowestRegion(now, 1초, 1초)`)를 본다. 19 아래면 동시에 도는 생성 요청 상한을 하나 줄이고(1 까지), 19.8 이상이면 하나 늘린다(생성 스레드 수까지). 문턱은 시제품 값 그대로(`GenQueue.TICK_GUARD_LOW/HIGH`).
- 세는 것은 `gen` 목록에서 꺼낸 작업(위치를 단 생성 요청·미리 생성)뿐이다. 위치 없는 작업(`execute`, 빛 미리 계산 같은 이어 하기)은 상한에 막히지 않는다 — 시제품의 cls 0 과 같다.
- 닫는 중(`shutdown`)에는 상한을 보지 않는다. 남은 고정 요청이 상한 1 에 막혀 닫기가 늦어지지 않게.

## 끄면 예전과 같은가
- 꺼져 있으면 `genBlockedLocked()` 는 늘 거짓, `genRunning` 은 세지 않고, 조절기 스레드도 만들지 않는다. `adjustLimit` 은 아무것도 안 한다. 일꾼 대기 조건과 `pickLocked` 는 T-170 그대로다.
- 시험 `GenQueueTickGuardTest.offByDefault_allThreadsGenerate_sameAsUnthrottled`: 기본 생성자에서 TPS 1 을 넣어도 상한이 그대로, 생성 요청 8개 중 4개(스레드 수)가 동시에 돈다.

## 함정·알아 둘 것
- 상한에 막혀 모든 일꾼이 기다리는 동안은 대기열의 거리 다시 재기·시야 밖 취소(`reprioLocked`)가 돌지 않는다. 도는 생성 하나가 끝날 때마다 일꾼 하나가 깨어 다시 재므로 멈추지는 않지만, 상한 1 일 때 취소가 생성 한 개 시간만큼 늦다(수백 ms). 조절기를 켠 운영에서만 생기는 일이다.
- 숫자 근거는 W60 1판(4코어, 장면 R: TPS 최저 4~6 → 9.8, 생성 73 → 48개/초)뿐이다. T-170 큐 위에서 다시 잰 적은 없다 — 켜기 전에 8코어·반복 측정이 필요하다(optimization-tasks T-172 행).
- T-171(틱이 밀리면 바로 다음 틱) 뒤라 R 장면의 TPS 바닥이 이미 올랐다. 조절기의 남은 이득은 그만큼 작을 수 있다.
