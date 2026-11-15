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
  청크 추적: 20틱째에 구독·`loadedChunks` 가 비고 ForgetLevelChunk 는 안 가며(바닐라), 리스폰하면 시야를 다시 구독하고 SetCenterChunk 를 다시 보낸다 / 모두 죽으면 그 자리 청크의 플레이어 티켓(`naturalSpawningAllowed`)이 사라지고
  관찰자의 `tracked()` 가 빈다 / 크레딧 중 플레이어는 추적 유지. 변이 점검: 제거 호출을 빼면 6개, 추적 해제·티켓·관찰자 3곳을 빼면 추적 시험 2개가 실패한다.
- **청크 추적(바닐라 `ChunkMap.removeEntity`) — 죽음(KILLED)만, 엔드 크레딧은 아님.** 바이트코드 대조(`aqu$a.onDestroyed` → `ServerChunkCache.removeEntity` → `ChunkMap.updatePlayerStatus(p, false)`):
  `playerMap` 제거 + `distanceManager.removePlayer`(플레이어 티켓) + `applyChunkTrackingView(p, EMPTY)` + 모든 `TrackedEntity.removePlayer`. `onDestroyed` 는 `RemovalReason.shouldDestroy()` 일 때만이라
  KILLED 는 부르고 크레딧의 CHANGED_DIMENSION 은 안 부른다. `PlayerChunkSender.dropChunk` 는 `player.isAlive()` 일 때만 ForgetLevelChunk 를 보내므로 **죽은 플레이어의 클라이언트는 청크를 잊지 않는다**(죽음 화면 뒤 세계 그대로).
  Furnace: `Player.chunkUntracked`(PlayerRoster 가 `removeFromLevel(p, destroyed=true)` 때 켜고 `attach`(리스폰·차원 이동) 때 끈다)를 두고
  - `Region` 틱: 켜진 플레이어는 청크 구독·시야·대기 목록을 비우고(`dropChunkTracking`, ForgetLevelChunk 없이) 청크 전송 단계를 건너뛴다 → 블록 변경·빛 방송이 멈추고, 그 구독만 붙들던 청크는 내려간다.
    리스폰하면 `lastCenter`·`queuedCenter` 가 비어 있어 시야 전체를 처음부터 다시 구독하고 SetCenterChunk·청크를 다시 보낸다(바닐라: 새 `ServerPlayer` 가 빈 시야에서 시작).
  - 플레이어 티켓·자연 스폰: `ticketPlayerWithin`(엔티티 틱·무작위 틱·블록 틱·시야 티켓 → `heldByPlayerTicket`·`naturalSpawningAllowed`·`entityTicking`·`inBlockTickingRange`),
    `naturalSpawnChunkCount`, `closeToPlayer`(anyPlayerCloseEnoughForSpawning)가 이 플레이어를 건너뛴다.
  - 플레이어끼리 보임(`updateVisibility`): 추적에서 빠진 관찰자는 보던 플레이어에게 RemoveEntities 를 받고 더는 새로 보지 못한다. 일반 엔티티는 `EntityTracker` 가 `chunkTracked`(구독) 를 보는데 구독이 풀려 같은 경로로 지워진다.
  - Spigot 활성 범위(`EntityActivation`)는 `level.players()` 라 `removedFromLevel`(죽음·크레딧 둘 다) 플레이어를 건너뛴다.
  - 크레딧 중 플레이어: 레벨 목록에서만 빠지고 추적은 유지(`detachForDimensionChange` 가 이미 Region 에서 떼었다).
- 남은 차이(없음 확인): 위 소비자 분류 밖의 `players.values()` 순회는 전송·정리용이라 게임 규칙에 영향이 없다.
- 독립 검토(F1·F2) 반영: ① 사망 제거는 `roster.map` 잠금 안에서 `isDeadOrDying` 을 다시 확인한다(region 이 `removedAfterDeath` 를 읽은 뒤 IO 스레드 리스폰이 끼면 살아난 플레이어를 빼지 않음).
  ② 제거된 플레이어의 차원 이동(`/tp`)은 새 차원 레벨 목록이 아니라 `removed` 쪽에 붙인다(제거·추적 해제 상태 유지, 리스폰 `relist` 가 올림). ③ 쓰기 순서 `removed` 추가 → `snap` 발행(방송을 못 받는 일 없음).
- 스레드: 제거는 플레이어 region 스레드(또는 크레딧 시 End region)에서, 목록 쓰기는 `roster.map` 잠금 안, 읽기는 불변 스냅샷/`volatile removed` 배열이라 다른 region 스레드의 조회와 경합하지 않는다.
