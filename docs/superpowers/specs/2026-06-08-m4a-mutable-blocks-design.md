# M4a — 가변 블록 (파괴) Design

**상태:** 설계 승인 → plan → 구현. M4 분해(a/b/c)의 첫 서브. M3d(main) 위에 쌓음.

## 1. 배경 / M4 분해

M4(상호작용 + 게임플레이 코어 + 영속성)는 독립 서브시스템 3개라 분해한다.
- **M4a — 가변 블록(본 spec):** 블록 파괴 + region 청크 소유. 토대.
- **M4b — 영속성:** Anvil read+write, 청크 퇴출 정책.
- **M4c — 인벤토리/아이템/블록엔티티:** 블록 설치(place)는 여기서(아이템 컨텍스트 필요).

순서: M4a → M4b → M4c.

## 2. 범위

**포함:** region 청크 소유(로드→보관→변형), 블록 **파괴**(break), 교차region 블록 브로드캐스트(구독 레지스트리), BlockUpdate/Player Action 패킷.

**제외:**
- **블록 설치(place)** → M4c(아이템 없이는 가짜). break만.
- **블록 물리 전파**(중력·유체·이웃 업데이트·울타리연결) → M5. 단일 블록 파괴는 한 region서 끝나 §4.5 교차region 원자연산 불필요.
- **영속성**(재시작 후 유지) → M4b. M4a는 세션 내만.
- **청크 퇴출** → M4b. M4a는 보관만(무한증가 = 알려진 한계).

## 3. 아키텍처

### 3.1 청크 소유권

현재 `Region.streamLoads`는 청크를 매번 `worldSource.generate()`→직렬화→버림. 가변 블록엔 region이 보관해야 한다.

- `Region`에 `Map<ChunkPos, ChunkColumn> chunks`(region 스레드 전용, 동기화 없음).
- `chunkAt(ChunkPos)` = get-or-load: 없으면 `worldSource.generate()` 1회 후 보관.
- `streamLoads`가 버리는 청크 대신 `chunkAt`(소유 청크)서 직렬화.

**직렬화 레이스:** 소유 청크를 공유하면 chunkPool(워커)서 직렬화 중 다른 플레이어 break가 같은 청크 변형 → 레이스. **해법 = 스냅샷.** region 스레드가 청크의 섹션 블록 배열을 불변 카피(`ChunkSnapshot`)로 떠서 pool에 넘겨 직렬화. 복사비용 « 직렬화비용(M2 측정 27ms/청크). M2 병렬 직렬화 최적화 유지 + 정확성. 블록 변경은 통째 재직렬화 안 하고 BlockUpdate 단건만.

### 3.2 교차region 블록 브로드캐스트

청크 렌더 거리(view 20) > region 크기(4) → region A 플레이어가 region B 소유 청크를 로드. B가 블록 깨면 A 플레이어도 봐야 함. B 스레드는 A 플레이어 상태를 직접 못 읽음(소유권). M3c 엔티티 가시성과 같은 교차region 문제.

**해법 = 청크 구독 레지스트리**(M3c 스냅샷 패턴의 블록판):
- `World`에 `ConcurrentHashMap<ChunkPos, Set<Player>> chunkSubscribers`(Set = `ConcurrentHashMap.newKeySet()`).
- `subscribeChunk(cp, player)` / `unsubscribeChunk(cp, player)` / `subscribers(cp)`.
- 구독/해제는 플레이어 소유 region 스레드, 읽기는 블록 소유 region 스레드 → ConcurrentHashMap이 안전.
- 등록 = `streamLoads`(로드 시), 해제 = `updateChunks` 언로드 루프 + 핸드오프/퇴장 정리(`broadcastRemove`/`markDisconnected` 경로).
- 브로드캐스트 = `for (Player p : world.subscribers(cp)) p.send(blockUpdate)`. `Player.send`는 채널 eventLoop 마샬이라 교차스레드 안전.

서버 권위: 클라는 dig 요청만, 블록 상태·검증·브로드캐스트 전부 서버. 구독 레지스트리 = "누가 어느 청크를 보나" 서버 인덱스(전체 브로드캐스트 회피 = 최적화).

### 3.3 블록 파괴 흐름

```
클라: Player Action(status 0, pos) → 서버
IO 스레드(ConnectionHandler): 파싱 → 블록 소유 region 마샬
  RegionPos br = RegionGrid.regionPos(bx, bz, regionSize);
  scheduler.region(br).execute(() -> world.regionAt(br).breakBlock(bx, by, bz));
region 스레드(Region.breakBlock):
  ChunkPos cp = ChunkPos.ofBlock(bx, bz);
  chunkAt(cp).setBlock(bx & 15, by, bz & 15, AIR);   // by = y 절대좌표
  BlockUpdate pkt = new BlockUpdate(bx, by, bz, AIR);
  for (Player p : world.subscribers(cp)) p.send(pkt);   // digger 포함(권위 확정)
```

게임모드 = 크리에이티브(LoginPlay gamemode=1, 기존값) → dig status 0 즉시 파괴.

## 4. 패킷

ID는 M1 관례대로 server.jar 데이터제너레이터(`--reports` → packets.json)로 **구현 시 확정**. 아래는 예상값.

### 4.1 인바운드 — Player Action (`player_action`, ~0x24)
```
VarInt  status    (0=start digging[크리=즉시파괴], 2=finish digging, ...)
Position pos      (packed long)
Byte    face
VarInt  sequence  (1.19+ 예측 ack; 읽고 무시)
```
`ConnectionHandler.handlePlay`에 case 추가. status 0만 처리(나머지 무시).

### 4.2 클라이언트바운드 — BlockUpdate (`block_update`, ~0x09)
```
Position  pos          (packed long)
VarInt    blockStateId (파괴 = 0 = air)
```
신규 `BlockUpdate.java` (Packet).

### 4.3 Position 패킹(바닐라 표준)
`((x & 0x3FFFFFF) << 38) | ((z & 0x3FFFFFF) << 12) | (y & 0xFFF)`. 음수 y는 12비트 2의보수.
`Types`에 `writeBlockPos(buf, x, y, z)` + `readBlockPos`(언패킹 → int[3]) 헬퍼.

## 5. 컴포넌트 변경

| 파일 | 변경 |
|---|---|
| `world/ChunkColumn.java` | (modify) `getBlock(x, worldY, z)` 추가. `ChunkSnapshot` 스냅샷 메서드 |
| `world/ChunkSnapshot.java` | (create) 직렬화용 불변 섹션 블록 카피 |
| `world/Region.java` | (modify) `chunks` 맵, `chunkAt`, `breakBlock`, `streamLoads`가 소유청크+스냅샷 직렬화, 구독 등록/해제 |
| `world/World.java` | (modify) `chunkSubscribers` + `subscribeChunk`/`unsubscribeChunk`/`subscribers` |
| `net/io/Types.java` | (modify) `writeBlockPos`/`readBlockPos` |
| `net/protocol/play/BlockUpdate.java` | (create) clientbound 패킷 |
| `net/protocol/play/ChunkSerializer.java` | (modify) `ChunkSnapshot` 입력 받게(또는 스냅샷서 직렬화) |
| `net/ConnectionHandler.java` | (modify) handlePlay에 Player Action case + 블록 region 마샬 |

> `ChunkSerializer`가 현재 `ChunkColumn` 직렬화 → 스냅샷 경유로 바꾸되 와이어 포맷 무변경.

## 6. 데이터 흐름 / 동시성

- **청크 소유:** region 스레드만 `chunks` 맵·ChunkColumn 변형. 직렬화는 스냅샷(불변 카피) 경유로 pool서.
- **구독 레지스트리:** ConcurrentHashMap. 쓰기(구독/해제)=플레이어 region 스레드, 읽기(브로드캐스트)=블록 region 스레드. 1틱 stale 허용(M3c와 동일 — 막 언로드한 플레이어가 BlockUpdate 1개 더 받아도 무해, 멱등).
- **전송:** `Player.send` = 채널 eventLoop 마샬, 교차스레드 안전.

## 7. 테스트

**JUnit:**
- `TypesTest`: Position 패킹/언패킹 라운드트립(음수 y 포함).
- `BlockUpdateTest`: write → 버퍼서 pos·stateId 되읽어 일치.
- `ChunkColumnTest`: setBlock 후 getBlock 일치.
- `RegionChunkOwnershipTest`: `chunkAt` 1회 로드 후 보관(2번째 호출 동일 인스턴스), break 후 보관 청크에 air 반영.
- `ChunkSubscriberTest`: subscribe/unsubscribe/subscribers 동작.
- `BlockBreakTest`: `breakBlock` → 소유 청크 air + 구독자 BlockUpdate 수신(EmbeddedChannel 디코드).
- `CrossRegionBlockTest`: region A 플레이어가 region B 청크 구독, B.breakBlock → A 플레이어 수신.

**수동 verify(2클라, superflat):**
1. 블록 dig(크리에이티브) → 양쪽서 사라짐.
2. 교차region — 경계 근처 같은 청크를 다른 region 두 명이 봄, 한 명이 깸 → 둘 다 봄.
3. 세션 내 영속 — 멀리 가 청크 언로드 후 복귀(재로드) → 깬 블록 여전히 깸(편집 청크 보관·재직렬화).
4. 재시작 미보존(= M4b, 명시).

## 8. 향후

- **M4b:** Anvil read+write 영속성, 청크 퇴출 정책(편집 청크 저장 후 퇴출).
- **M4c:** 블록 설치(place) + 인벤토리/아이템/블록엔티티.
- **M5:** 블록 물리 전파(중력·유체·레드스톤) + 교차region 원자연산(§4.5) — 틱 물리 대상 생길 때.
