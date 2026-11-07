# 데이터 팩 함수·명령 훅 (C2, 불만 표 67행)

데이터 팩 함수가 도는 것을 플러그인이 보거나 막을 수 있다. 둘 다 `server.events()` 로 구독하고, 전역 스레드(명령 큐)에서 불린다. 구독자가 없으면 서버는 이벤트를 만들지 않는다.

## `FunctionExecuteEvent` — 함수 하나

함수가 시작되기 직전. 취소하면 그 함수 본문을 통째로 건너뛴다(호출한 쪽의 나머지 줄은 그대로 돈다).

| 필드 | 뜻 |
|---|---|
| `function()` | 함수 ID(`namespace:path`) |
| `trigger()` | `TICK`(#minecraft:tick) · `LOAD`(#minecraft:load) · `SCHEDULED`(/schedule) · `COMMAND`(/function, execute if function 등 최상위 호출) · `NESTED`(함수 안에서 부른 호출) |
| `sender()` | 명령 소스. tick·load·schedule 은 권한 2 서버 소스 |
| `depth()` | 호출 깊이(최상위 0) |

## `FunctionCommandEvent` — 함수 안의 줄 하나

함수 안 명령 줄 실행 직전. 취소하면 그 줄만 건너뛴다. `command()` 는 파일에 적힌 줄('/' 없음, 매크로는 값이 채워진 뒤), `sender()` 는 줄을 돌리는 소스, `depth()` 는 그 줄이 든 함수의 호출 깊이(최상위 함수 안 1).
함수 줄은 틱마다 수천 번 돌 수 있으니 핸들러는 가볍게 쓴다. 이 이벤트는 명령을 바꿀 수 없다(취소만) — 바꾸려면 취소하고 다른 줄을 `server.dispatchCommand` 로 실행한다.

플레이어·콘솔이 직접 친 명령은 이 이벤트가 아니라 `PlayerCommandPreprocessEvent`·`ServerCommandEvent` 로 본다.

시험: `FunctionHookTest`.
