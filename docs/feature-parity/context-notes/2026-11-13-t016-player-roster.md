# T-016 차원별 플레이어 목록 + 청크 격자 근접 색인 (2026-11-13)

- 증거: `furnace/reviews/opt-review/O05-entities.md` P-O05-01·02, 반론 `wave1-rebuttal-core.md` 1.2절·K2, O05 답변(목록 순서는 바닐라 도착 순).
- 바닐라 대조(1.21.1 Mojang 이름 소스): `ServerLevel.players` 는 `ArrayList` — `EntityCallbacks.onTrackingStart` 에서 끝에 붙이고 `onTrackingEnd` 에서 뺀다.
  `ServerPlayer.changeDimension`(다른 차원) = `removePlayerImmediately` → `addDuringTeleport`(새 차원 끝), 같은 차원이면 순간이동만(목록 그대로).
  `PlayerList.respawn` = 옛 플레이어 `removePlayerImmediately` → 새 `ServerPlayer` 를 `addRespawnedPlayer`(같은 차원이어도 끝으로).
- 바꾼 것: ① `World.Online` → `PlayerRoster`(서버 전체 접속자 + 차원별 목록). 목록 **집합**은 예전 정의(접속 중 + `p.dimension()` 이 그 차원)와 같고,
  **순서**만 eid 해시 순(옛 `ConcurrentHashMap.values()`)에서 바닐라 도착 순으로 바뀌었다(K2 해결). `Player.dimension(String)` 이 목록을 바로 옮기고,
  리스폰(`finishRespawn`·`finishWinRespawn`)은 `World.playerRespawned` 로 끝으로 옮긴다.
  ② 차원별 목록마다 128블록 격자(칸 → 목록 번호 비트 집합, 불변 스냅샷). 위치를 쓰는 세 곳(`applyMove`·`setRidingPos`·`setPosRaw`)이 쓴 직후 칸을 맞춘다.
  ③ `ChunkLevel` 의 `hasNearbyAlivePlayer`·`getNearestPlayer`×3·`forEachPlayerIn` 이 거리·상자가 정해진 조회는 격자 후보만 본다(상위 집합 + 목록 순서 → 부르는 쪽 판정은 그대로).
  거리 제한 없는 `getNearestPlayer(…, -1)`(checkDespawn·스폰 거리)는 먼저 반경 128 후보를 보고, 그 안에서 찾으면 그것이 전체의 답(밖은 모두 더 멀다), 못 찾으면 전체를 훑는다.
  차원 인원 32명 미만은 격자 없이 목록 그대로(`-Dfurnace.playerGridMin`).
- 결과 동일 증명: `PlayerRosterIdentityTest`(IdentityKit Differential, 400 각본 — 접속·걸음·순간이동·탑승 위치·차원 이동·리스폰·퇴장·재접속·조회 5종 섞음)가
  "바닐라 목록 규칙 + 옛 조회 사본(`identity/legacy/players/LegacyPlayerQueries`, since c470effbd)" 과 목록 순서·고른 플레이어·상자 안 순서를 줄마다 비교. 각 단계에서 목록 집합 = 옛 정의도 확인.
  변이 점검: 옛 eid 순서, 2단계 반경 조건 빼기, 다중 칸 정렬 빼기, 위치 갱신 빼기 — 넷 다 잡힌다.
- 측정(`PlayerScanBench`, 4코어, 몹 3000, 접속 100(오버월드 75), 몹마다 checkDespawn + 밀기 조회, 틱당): 옛 모양 1.9~2.3ms → 차원별 목록 1.4~1.6ms → 격자 0.54ms(몹이 플레이어 둘레, 실서버 꼴)
  / 0.78ms(모두 흩어짐) / 1.15ms(모두 ±150 몰림). 접속 20명(15명)은 격자를 안 써 차원별 목록 값(옛 대비 −25%).
- 남은 바닐라 차이(이 작업 범위 밖, 보고): ① 바닐라·Paper 는 죽은 플레이어를 사망 20틱 뒤 `remove(KILLED)` 로 레벨 목록에서 뺀다
  (`LivingEntity.tickDeath` → `onTrackingEnd` → `updateSleepingPlayerList`, Paper `SPIGOT-6876` 줄도 KILLED 플레이어의 onTrackingEnd 를 전제). Furnace 는 리스폰 때까지 남긴다 —
  죽음 화면의 플레이어가 잠 비율·빈 차원 판정·`checkDespawn`(사라짐)·순찰·팬텀 스포너에 계속 잡힌다. 엔드 크레딧 중(`showEndCredits`)도 같다. 고치려면 목록 소비자 42곳을
  "레벨 목록"(바닐라 `level.players()`)과 "같은 차원 접속자"(`PlayerList.broadcast` 꼴 — 소리·입자 방송은 죽은 플레이어도 받는다)로 나눠야 한다 → 결정 항목 `docs/user-todo/entries/2026-11-13-t016-player-roster.md`.
  ② 서버 전체 순서(`PlayerList.players`, 탭 목록·`onlinePlayers()`)는 아직 eid 해시 순이다(바닐라는 접속 순, 리스폰 때 끝으로).
- 함정: 다른 region 스레드가 그 순간 위치를 바꾸는 플레이어는 예전에도 "언제 읽느냐"에 따라 답이 달랐다. 격자도 같은 성질이다(칸 갱신은 위치 쓰기 직후라 같은 스레드에서는 늘 새 칸).
