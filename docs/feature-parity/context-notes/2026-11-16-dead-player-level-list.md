# 죽은 플레이어를 20틱 뒤 차원 레벨 목록에서 빼기 (2026-11-16, open item #7)

- 배경: T-016 노트의 "남은 바닐라 차이 ①"(`2026-11-13-t016-player-roster.md`). 도미니코 11-16 승인("7번 진행").
- 바닐라(1.21.1 Mojang 이름): `LivingEntity.tickDeath` 가 `deathTime >= 20` 이면 이벤트 60 방송 + `remove(KILLED)` → `ServerLevel.EntityCallbacks.onTrackingEnd` 가
  `players` 에서 빼고 `updateSleepingPlayerList()`. 서버 전체 `PlayerList.players` 는 그대로(접속 유지), 리스폰(`PlayerList.respawn`)이 새 플레이어를 `addRespawnedPlayer` 로 끝에 올린다.
  엔드 크레딧(`ServerPlayer.showEndCredits`)은 `removePlayerImmediately(CHANGED_DIMENSION)` — 20틱 기다리지 않고 즉시 뺀다. Paper 도 같다.
- 바꾼 것:
  - `PlayerRoster.removeFromLevel(Player)`: 그 차원 레벨 목록(`Level.snap`)에서만 빼고 `Level.removed` 에 둔다. 서버 전체 목록·접속은 그대로. 처음 뺄 때만 참(멱등).
    리스폰(`relist`)·차원 이동·퇴장(`detach`)이 `removed` 에서도 치운다. 격자·근접 조회·`size/isEmpty/toArray/forEach` 는 모두 레벨 목록만 본다.
  - `Level.forEachConnected` / `LevelServices.forEachConnectedPlayer` / `World.forEachConnectedPlayerHere`: 레벨 목록 + 제거된 접속자. **방송용**(바닐라 `PlayerList.broadcast` 가 서버 전체 목록을 봄).
    기본 구현은 `forEachPlayer` 와 같다(시험용 서비스).
  - `World.playerRemovedFromLevel(p)`: 제거 + `updateSleepingPlayerList()`. 호출처: `Region` 플레이어 틱(`vitals().tick` 전후로 `removedAfterDeath` 가 거짓→참이 되는 틱 한 번),
    `Region.showEndCredits`(즉시).
- 소비자 분류(`LevelServices.forEachPlayer` 사용처 전수):
  - **레벨 목록(죽은 사람 제외)** = 기존 `forEachPlayer`·`playerIndex`·`playersHere`: 잠 비율(`SleepStatus`·`tickSleep`), 사라짐/가장 가까운 플레이어(`getNearestPlayer`·`hasNearbyAlivePlayer`),
    순찰·팬텀·방랑 상인·습격 스포너(`DimensionSpawners.players()`·`tickCustomSpawners`), 입자(`sendParticles` = `this.players`), 폭발 패킷, 센서(`PlayerSensor`·`TemptingSensor`), 트라이얼 스포너,
    엔더 드래곤 전투, 엘더 가디언, 번개, 워든, 습격, `getPlayerByUUID`·`getEntity(uuid)`, 압력판 계수, `isUnobstructed`, 빈 차원 판정(`tickEmptyTime` → `emptyTime`), `World.getPlayers`(플러그인 API), GenQueue 우선순위.
  - **방송(죽음 화면·크레딧 중도 받음)** = `forEachConnectedPlayer`: 소리(`playSeededSound`·엔티티 소리), `levelEvent`, `globalLevelEvent`, `blockEvent`, `destroyBlockProgress`
    (모두 바닐라 `PlayerList.broadcast`/`getPlayers()` 순회), 플러그인 `World.playSound`, 기본 추적 대상 `trackingPlayers`(관찰자 쪽 추적은 이번에 건드리지 않음 — 아래).
- 시험 `DeadPlayerWorldListTest`(region 틱을 돌려 실제 경로): 19틱까진 목록에 있고 20틱째에 빠짐·접속 유지, 4명 중 3명 취침+1 사망 → 제거 뒤 3/3 이라 100% 에서 밤을 넘길 수 있음(대조: 제거 전 3/4),
  `getNearestPlayer` 가 죽은 플레이어를 건너뜀(사라짐 판정), 리스폰은 끝에 다시 올림, 죽음 화면 중 퇴장 정리, 레벨 이벤트 패킷은 죽은 플레이어도 받음, 모두 죽으면 차원이 빔.
  변이 점검: `Region` 의 제거 호출을 빼면 6개가 실패한다.
- 이 PR 에 포함하지 않은 것(바닐라와 아직 다름, 별도 후속 후보):
  - 바닐라는 제거 때 `ChunkMap.removeEntity` 로 죽은 플레이어를 청크 추적(`playerMap`)에서도 뺀다 — 블록 변경·엔티티 추적 방송을 더는 받지 않는다. Furnace 의 청크 구독·엔티티 추적은 그대로다.
  - 제거된 플레이어의 per-tick 스캔(예: `Region` 이 `players` 맵으로 도는 자기 틱)은 그대로다.
- 스레드: 제거는 플레이어 region 스레드(또는 크레딧 시 End region)에서, 목록 쓰기는 `roster.map` 잠금 안, 읽기는 불변 스냅샷/`volatile removed` 배열이라 다른 region 스레드의 조회와 경합하지 않는다.
