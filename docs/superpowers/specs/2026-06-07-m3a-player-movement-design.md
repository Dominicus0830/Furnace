# Furnace M3a — 플레이어 이동 + 멀티플레이 가시성 설계서

> 부모 설계서: `docs/superpowers/specs/2026-06-06-furnace-multithreaded-mc-server-design.md` (M0~M6, §4 동시성 모델).
> 선행: M0(스레딩 골격·RegionThread), M1(네트워킹), M2(월드모델·청크전송), 전부 main 머지.
> M3 분해: 본 문서는 **M3a**(플레이어 이동). 후속 = M3b(region 분할·병렬 틱), M3c(교차지역 핸드오프·원자연산).

## 1. 목표

여러 바닐라 1.21.1 클라가 접속해 **걸어 다니고, 서로를 보고**(스폰·이동·디스폰), 이동에 따라 **청크가 따라 로드/언로드**된다. 이로써 M0이 만든 `RegionThread`가 처음으로 플레이어/엔티티를 소유하고 20Hz로 틱한다 — **설계서 S1→S2 전환 실현**.

## 2. 비목표 (M3a 제외)

- region N개 분할·병렬 틱 → M3b.
- 교차지역 경계·핸드오프·원자연산(§4.5) → M3c.
- 몹/AI → M5. 블록 상호작용·파괴/설치 → M4.
- 서버측 정밀 물리(충돌·중력) → 위치는 클라 권위, 서버는 중계만. 정밀 물리는 후속.
- 진짜 지형 생성(노이즈) → 로드맵 밖. superflat 유지. (진짜 지형은 M4 Anvil 로드가 지름길.)

## 3. 확정된 핵심 결정

| 항목 | 결정 | 근거 |
|---|---|---|
| 멀티플레이 가시성 | 포함(엔티티 트래킹) | "플레이어끼리 보임"이 핵심 체감 |
| 소유권 | region 틱 스레드가 World/Player/엔티티 소유 | 공유 가변상태를 한 스레드가 소유 = 락 없음(§4.1). M0 RegionThread 가동. M3b는 이걸 N개로 쪼갬 |
| 처리 트리거 | 틱 기반(20Hz), 이동 패킷은 위치만 저장 | 빠른 이동 패킷 dedupe, 작업 bounded, idle 시 거의 0. 바닐라 방식 |
| IO↔region | 큐(`scheduler.region().execute`)로만 marshal | 소유권 경계. IO 스레드는 파싱+전송만, region 스레드가 상태 소유 |
| 물리 | 클라 권위 위치, 서버 중계 | M3a 범위 최소화 |

## 4. 아키텍처

```
[IO 스레드 N개]  이동패킷(0x1a/1b/1c) 파싱
                   → scheduler.region().execute(() -> player.applyMove(...))
                        │ (큐)
[region 틱 스레드] ──RegionThread.perTick (20Hz)──> World.tick():
   for each Player p:
     1. 청크 diff:  센터청크 변경 시 새 청크 로드(worker 직렬화 → send) / 떠난 청크 Unload
     2. 가시성 diff: 시야 내 타 플레이어 집합 계산 → 든 자 spawn / 난 자 remove
     3. 이동 broadcast: 관찰자에게 위치 델타(Update Entity Position / Teleport Entity)
   각 전송 → 대상Player.channel.eventLoop().execute(send)
                        │
[chunk worker pool] 청크 직렬화 (M2 ChunkWorkerPool 재사용)
```

**소유권 불변식.** `World`·`Player`·엔티티 상태는 region 스레드만 만진다. IO 스레드는 (a) 인바운드를 파싱해 region 스레드로 execute, (b) region 스레드가 만든 패킷을 자기 채널로 write. 디버그 가드: `World.tick`/`Player` 변경 진입 시 `assert scheduler.region().isOwnerThread()`.

**RegionThread 연결.** M0 `RegionThread(name, tickNanos, perTick)`의 `perTick`에 `world::tick`을 연결한다. 현재 `FurnaceSchedulerImpl`이 빈 perTick으로 RegionThread를 만든다면, World가 scheduler에 의존하는 순환을 피하려 **settable tick 훅** 또는 **World를 먼저 만들고 scheduler에 주입**하는 배선을 쓴다(plan서 확정).

## 5. 컴포넌트 상세

### 5.1 EntityIdAllocator
서버 전역 엔티티 id 발급(`AtomicInteger`, region 스레드서만 호출하면 단순 int여도 무방하나 join이 IO서 올 수 있어 atomic). int 1부터 증가.

### 5.2 Player (region 소유)
상태: `int entityId`, `UUID uuid`, `String name`, `double x,y,z`, `float yaw,pitch`, `Channel channel`, `ChunkPos lastCenter`, `Set<ChunkPos> loadedChunks`, `Map<Integer,LastSent> tracked`(관찰 중인 엔티티별 마지막 전송 위치).
- `applyMove(x,y,z,yaw,pitch,onGround)` — 위치/회전 갱신(region 스레드).
- 채널 ref로 전송. `channel.eventLoop().execute(...)`로 마샬.

### 5.3 World (region 소유자, M3a 1개)
- `Map<Integer,Player> players`, `WorldSource worldSource`, `ChunkWorkerPool chunkPool`(NetworkServer서 이동), `BlockRegistry`·뷰거리·direct비트 보유.
- `addPlayer(Player)` / `removePlayer(int entityId)` — region 스레드서 실행(IO서 execute로 마샬).
- `tick()` — §4의 3단계. 매 틱 전체 플레이어 순회.
  - 청크 diff: 플레이어 센터청크 계산(floor(x/16), floor(z/16)). `lastCenter`와 다르면 로드/언로드 diff + `SetCenterChunk` 갱신, 같으면 skip.
  - 가시성 diff: O(n²) 플레이어 쌍 거리(시야거리 청크 환산). 대칭 처리(A가 B를 보면 B도 A를).
  - 이동 broadcast: tracked 관찰자에게 델타.

### 5.4 인바운드 이동 처리 (ConnectionHandler)
- play 진입(`onEnterPlay`) 시: entityId 발급, `Player` 생성, `region().execute(() -> world.addPlayer(p))`. 자기 자신 스폰 청크는 기존 M2 로직 유지(첫 로드).
- `handlePlay`에 추가:
  - `0x00` accept_teleportation — 기존(무시 유지).
  - `0x1a` move_player_pos — x,y,z,onGround 파싱 → `region().execute(p.applyMove(...))`.
  - `0x1b` move_player_pos_rot — x,y,z,yaw,pitch,onGround.
  - `0x1c` move_player_rot — yaw,pitch,onGround.
- 채널 inactive(`channelInactive`) → `region().execute(world.removePlayer(entityId))` + 관찰자에게 Remove Entities.

### 5.5 신규 clientbound 패킷 (id는 plan서 packets.json 확정)
| 패킷 | 용도 |
|---|---|
| Player Info Update (add_player action) | 스폰 전 클라 플레이어 레지스트리 등록(1.20.2+ 필수) |
| Spawn Entity | 플레이어 엔티티 스폰(타입=player, entityId, uuid, pos, rot) |
| Update Entity Position | 작은 이동 델타(short: (new-old)*4096) |
| Update Entity Position and Rotation | 델타 + 회전 |
| Update Entity Rotation | 회전만 |
| Teleport Entity | 큰 이동(델타 범위 초과) 절대좌표 |
| Set Head Rotation | 머리 yaw(별도 패킷) |
| Remove Entities | 디스폰(entityId 목록) |
| Unload Chunk | 떠난 청크 언로드(chunkX, chunkZ) |

> 델타 인코딩: Update Entity Position의 델타 = `(newPos*4096 - oldPos*4096)` short. 범위 ±8블록 초과 시 Teleport Entity. tracked의 LastSent로 기준 유지.

## 6. 데이터 흐름 — 가시성 diff 상세

매 틱, 각 플레이어 A에 대해:
1. 시야거리 내 타 플레이어 집합 `visible` 계산.
2. `A.tracked`에 없는데 `visible`에 있음 → 신규: Player Info Update(B) + Spawn Entity(B) → A 채널. `A.tracked`에 B 추가(LastSent=B 현재 위치).
3. `A.tracked`에 있는데 `visible`에 없음 → 제거: Remove Entities([B]) → A 채널. tracked서 B 제거.
4. `A.tracked` 유지 중인 B가 이동 → 델타/텔레포트 + Set Head Rotation → A 채널. LastSent 갱신.

n² 비용은 M3a 소수 플레이어 전제(수~수십). 공간 분할 최적화는 M3b region 분할서 자연 해결.

## 7. 위험 / 검증

- **패킷 ID·필드 순서** — M1·M2 교훈. `packets.json` 권위 대조(plan). Spawn Entity·Player Info Update 필드 복잡 → 단위 테스트로 와이어 검증.
- **스레드 소유권 위반** — IO 스레드가 World/Player를 직접 만지면 레이스. 모든 변경을 `region().execute`로 마샬. `isOwnerThread` assert로 가드.
- **delta 인코딩 범위** — ±8블록 초과를 Teleport로 안 빠지면 위치 튐. 경계 테스트.
- **자기 자신 트래킹 제외** — A가 자기를 spawn하지 않게(entityId 비교).

✅ **verify:** 두 클라 접속 → 서로 스폰·이동 보임, 나가면 사라짐. 걸으면 청크 따라 로드/언로드. 처리가 region 틱 스레드서 도는 것 스레드 id 단언.

## 8. 파일 구조

| 파일 | 책임 |
|---|---|
| `entity/EntityIdAllocator.java` | 전역 엔티티 id 발급 |
| `entity/Player.java` | 플레이어 상태(region 소유) |
| `world/World.java` | region 소유자, players, tick() |
| `world/ChunkPos.java` | 청크 좌표 value type(diff용) |
| `net/protocol/play/PlayerInfoUpdate.java` | 플레이어 레지스트리 등록 |
| `net/protocol/play/SpawnEntity.java` | 엔티티 스폰 |
| `net/protocol/play/UpdateEntityPosition*.java` | 이동 델타 3종 |
| `net/protocol/play/TeleportEntity.java` | 큰 이동 절대좌표 |
| `net/protocol/play/SetHeadRotation.java` | 머리 회전 |
| `net/protocol/play/RemoveEntities.java` | 디스폰 |
| `net/protocol/play/UnloadChunk.java` | 청크 언로드 |
| `net/ConnectionHandler.java` | (modify) addPlayer, 이동 marshal, 인액티브 정리 |
| `net/NetworkServer.java` | (modify) World 주입, pool/worldSource → World로 이동 |
| `sched/FurnaceSchedulerImpl.java` | (modify) perTick에 world.tick 연결 |
| `FurnaceMain.java` | (modify) World 생성·배선 |

각 신규 패킷·컴포넌트는 단위 테스트 동반(TDD). 와이어 패킷은 라운드트립/필드 검증, 통합은 2클라 실접속(verify).
