# minecraft: 접두 바닐라 명령 (namespaced-commands) — 2026-11-08

`/minecraft:give`·`/minecraft:tp` 처럼 바닐라 명령마다 접두 별칭을 Paper 1.21.1 대로 추가했다. 구조와 남은 차이: `docs/feature-parity/context-notes/2026-11-08-namespaced-commands.md`.

- **직접 확인(클라이언트, 선택):** 채팅창에 `/minecraft:` 까지 치면 `minecraft:give` 등이 자동완성에 뜨는지, `/minecraft:give @s diamond` 가 `/give` 처럼 도는지. 권한 없는 계정(OP 아님)에게는 `minecraft:gamemode` 같은 별칭이 안 뜨는지.
- **결정(작음):** 새 옵션 `commands.send-namespaced`(furnace.properties)의 기본값. 지금은 Paper 와 같게 `true`(자동완성에 보임). 후보: ① 지금처럼(추천), ② 기본 `false` — 자동완성이 깔끔해지지만 Paper 와 다르다. 답이 없으면 ①.
