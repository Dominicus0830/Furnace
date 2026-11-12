# 스폰 청크(START 티켓)와 빈 차원 300 틱 규칙 (2026-11-12, O08 정확성 항목)

O08 레드스톤 검토가 "게임 규칙 `spawnChunkRadius` 가 읽히지 않아 플레이어가 없으면 스폰 청크 장치가 멈춘다"고 적었다. 1.20.5 부터 스폰 청크 방식이 바뀌었으므로 먼저 바닐라 1.21.1 서버를 직접 띄워 확인했다(번들러 jar `main:tools/vanilla-data/minecraft_server.1.21.1.jar`, 평지, 콘솔 명령, 접속자 없음).

## 바닐라에서 본 것
스폰을 `(8, -60, 8)`(청크 0,0)로 두고 청크 x = 0~4 에 관측기 시계 + 공급기 → 상자, 호퍼 → 상자를 놓았다.

| 청크 거리 | setblock | 관측기·공급기(예약 틱) | 호퍼(블록 엔티티) | 떨어지는 아이템(엔티티, /forceload 뒤) |
|---|---|---|---|---|
| 0, 1 | 됨 | 돈다(16초에 51개) | 빈 동안 멈춤 | 떨어진다 |
| 2 | 됨 | 돈다 | 빈 동안 멈춤 | 안 떨어진다 |
| 3 | 됨 | 멈춤 | 멈춤 | 안 떨어진다 |
| 4 | "That position is not loaded" | — | — | — |

- 다른 곳(62, 62)을 `/forceload` 하자 거리 0~2 호퍼가 곧바로 움직였다(13개). 풀고 17초 뒤 놓은 피스톤은 `moving_piston` 에 머물렀고 중계기는 켜졌다.
- 바이트코드(aqu = ServerLevel): `setDefaultSpawnPos`(aqu.a(jd, float))가 옛 START 티켓(거리 L &gt; 1)을 놓고 `거리 = spawnChunkRadius + 1` 이 1 보다 크면 스폰 청크에 `addRegionTicket(START, 청크, 거리)` — 레벨 33 − 거리. 규칙(dcs.Z, 0..32, 기본 2)의 콜백이 같은 자리로 다시 부른다. `MinecraftServer.prepareLevels` 는 오버월드에만 부른다.
- `ServerLevel.tick`: 예약 틱·유체 틱·습격·청크 소스(무작위 틱·스폰)·블록 이벤트 뒤 `flag = !players.isEmpty() || !getForcedChunks().isEmpty(); if (flag) resetEmptyTime(); if (flag || emptyTime++ < 300) { 드래곤 전투; 엔티티; tickBlockEntities }`. `Entity.changeDimension` 이 떠난·닿은 레벨 둘 다 `resetEmptyTime`. Paper 1.21.1 패치(33ff3dff)에 emptyTime 을 바꾸는 것은 없다.

## Furnace
- `World.enableSpawnChunks()`(운영 서버 `FurnaceMain`·`FurnaceTestServer` 의 주 오버월드만) → `SpawnTicket(가운데, 거리)`. 올라옴 ≤ 거리, 블록 틱 ≤ 거리 − 1, 엔티티 틱 ≤ 거리 − 2(포털 티켓과 같은 규칙). `defaultSpawn(...)`(/setworldspawn·Bukkit setSpawnLocation)과 게임 규칙 변경이 옮긴다.
- `World.tickEmptyTime()` 을 전역 틱(정지 중 포함)이 차원마다 부르고, `RegionLevel.tickBlocks` 가 `emptyIdle()` 이면 드래곤 전투·엔티티·블록 엔티티를 건너뛴다. 엔티티 차원 이동(명령 teleportTo 제외)이 두 차원을 되돌린다.
- furnace.properties `spawn-chunk-radius` 는 다른 것이다(접속 직후 한 묶음으로 보내는 청크 반경). 게임 규칙과 섞지 않는다.

## 함께 바뀐 동작(바닐라·Paper 쪽으로)
- 플레이어도 /forceload 청크도 없는 차원은 15초(300 틱) 뒤 엔티티·블록 엔티티가 쉰다. 포털 티켓·플러그인 티켓(`addPluginChunkTicket`)만 걸린 청크도 마찬가지다(Paper 와 같다 — `getForcedChunks` 는 /forceload 만).
- 시험: `SpawnChunkTicketTest`(바닐라에서 본 표를 그대로 단언).
