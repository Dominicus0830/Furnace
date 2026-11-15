# 플레이어별 거리 (T-141 E4c)

Furnace 의 시야 거리(`view-distance`)와 시뮬레이션 거리(`simulation-distance`)는 서버 전체에 하나다(`Furnace#viewDistance`·`setViewDistance`,
`simulationDistance`·`setSimulationDistance`). 플러그인은 플레이어마다 거리를 **낮출** 수 있다. 규칙은 Paper 1.21.1(Moonrise
`RegionizedPlayerChunkLoader`)과 같다.

## 공식

플레이어 값이 없으면(-1) 월드 값을 쓴다. 월드 값: tick = 시뮬레이션 거리, load = 시야 거리 + 1.

| 값 | 계산 |
|---|---|
| tick | min(플레이어 tick 또는 월드 tick, 플레이어 load 또는 월드 load) |
| load | max(tick + 1, 플레이어 load 또는 월드 load) |
| 보내는 거리 | min(load − 1, 플레이어 send(정했으면)), 그리고 서버 시야 거리·클라이언트 렌더 거리 이하, 2 이상 |

| API (Furnace / Bukkit) | 뜻 |
|---|---|
| `setViewDistance(v)` / `Player#setViewDistance` | 플레이어 load = v + 1(Paper 와 같다 — 검사 없음, v ≤ −2 면 정하지 않음) |
| `setSendViewDistance(v)` | 플레이어 send = v(음수면 정하지 않음) |
| `setSimulationDistance(v)` | 플레이어 tick = v(음수면 정하지 않음) |
| `viewDistance()` / `getViewDistance` | Paper 1.21.1 그대로 load − 2(그래서 기본은 서버 시야 거리 − 1, `setViewDistance(v)` 뒤에는 v) |
| `sendViewDistance()` / `getSendViewDistance` | 지금 실제로 보내는 거리 |
| `simulationDistance()` / `getSimulationDistance` | 서버 시뮬레이션 거리(아래 "아직 안 되는 것") |
| `clientViewDistance()` / `getClientViewDistance` | 클라이언트가 알린 렌더 거리(2~32, 아직 모르면 32) |

보내는 거리가 바뀌면 그 플레이어에게 `set_chunk_cache_radius` 를 보내고, 다음 틱에 청크를 더 보내거나 내린다. 서버 값이 바뀌어도 플레이어 값은 남고 새 서버 값으로 다시 푼다.
FAWE 의 "dynamic chunk rendering"(움직이는 동안 플레이어 시야 줄이기)이 이 길을 쓴다.

Bukkit `World#getViewDistance`·`getSendViewDistance` 는 서버 시야 거리, `getSimulationDistance` 는 min(시뮬레이션 거리, 시야 거리 + 1)(Paper 공식). `World#setViewDistance`·`setSimulationDistance` 는 2~32 밖이면 Paper 와 같은 `IllegalArgumentException` 이고, 주 오버월드에서 부르면 서버 값(모든 월드)을 바꾸며 다른 월드에서는 경고 한 번 뒤 무시한다(Paper 는 월드마다). `World#setSendViewDistance` 는 월드별 값이 없어 경고 한 번 뒤 무시한다.

## 아직 안 되는 것 (후속 T-141v, `docs/roadmap/optimization-tasks.md`)

- **서버 값보다 크게**: 플레이어 값이 서버 시야 거리보다 크면 서버 값으로 잘리고 종류마다 경고를 한 번 남긴다(무엇을 올리면 되는지 함께). Paper 는 그 플레이어 둘레만 더 불러와 보낸다 — 청크 티켓·불러오기 반경이 플레이어마다 달라져야 해서 생성 대기열(T-170) 뒤에 한다.
- **플레이어별 틱 거리**: 블록·엔티티 틱 범위는 서버 시뮬레이션 거리 그대로다. 그래서 `getSimulationDistance` 도 서버 값이고, `setSimulationDistance` 로 서버 값과 다른 값을 주면 경고를 한 번 남긴다(보내는 거리 계산에는 Paper 처럼 쓰인다). Paper 는 `setViewDistance` 로도 틱 거리가 줄어든다(tick ≤ load).
- 플레이어가 시야를 낮춰도 서버가 불러 둔 청크(서버 시야 거리)는 그대로 남는다(보내지만 않는다). 바깥에서 보이는 것은 같고 메모리만 다르다.
- 클라이언트 몫: Paper 기본(`auto-config-send-distance`)은 클라이언트 렌더 거리 + 1 까지 보낸다. Furnace 는 바닐라처럼 렌더 거리까지다(원래 동작).
