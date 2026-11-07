# 명령 API (C1, 불만 표 59·60·61행)

`server.commands()` (`CommandRegistry`) 하나로 두 방식이 **같은 명령 트리**에 붙는다. 둘 다 영구 지원이다.

- **단순 명령** `register(plugin, "이름", BasicCommand)` — 인자를 공백으로 나눈 배열로 받는다.
- **트리 명령** `register(plugin, CommandBuilder.literal("이름").then(...))` — literal/argument(불리언·정수·실수·단어·문장·남은 줄) 노드, `executes`, `requires`/`requiresPermission`, `suggests`. 서버 내부 Brigadier 타입은 노출하지 않는다.
- 이름 충돌 규칙은 같다: 바닐라·다른 플러그인과 겹치면 `플러그인:이름` 만 등록된다. 별칭은 `register(..., aliases)`.

## 플레이어별 필터 (60행)
`requires`/`BasicCommand.canUse` 가 거짓이면 그 노드는 해당 플레이어에게 보내는 `commands` 패킷에서 빠져 자동완성에 안 나오고 실행도 거부된다(`CommandTreePacket.forSource` 가 소스별로 가지치기 — 이미 있던 동작에 시험 추가). 권한이 바뀐 뒤에는 `updateCommands(sender)` 로 그 한 명의 트리를 다시 보낸다.

## 런타임 등록·해제 (61행)
`register` 는 바로 접속자 전원에게 트리를 다시 보낸다. `unregister(plugin, 이름)` 은 한 이름만 빼고(없으면 false) 다시 보낸다. 플러그인이 꺼지면 `unregisterAll` 이 자동으로 호출된다(핫 리로드 정리 규칙 충족). `updateCommands()` 는 전원에게 수동 재전송.

시험: `CommandTreeApiTest`. 실제 클라이언트에서 자동완성이 갱신되는지는 사람이 접속해 확인(docs/user-todo.md).
