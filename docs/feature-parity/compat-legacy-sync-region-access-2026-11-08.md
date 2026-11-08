# 옛 동기 작업이 월드를 만질 때: 만진 region 만 빌려 오기 — 2026-11-08

도미니코 결정(11-08 결정 6): JavaPlugin 연결(#138)에서 남긴 문제 — 옛 "동기 작업"(`BukkitScheduler#runTask*`·`callSyncMethod`)과 옛 명령은 전역 스레드에서 도는데, 그 안에서 블록·엔티티를 읽으면 `ThreadOwnershipException` 이 났다 — 를 **성능과 호환성 둘 다** 잡는 방향으로 푼다.

## 고른 설계: region 빌림(RegionBorrow)

옛 코드가 도는 전역 스레드 구간(동기 작업 박동·옛 명령·자동완성·전역 스레드에서 온 Bukkit 이벤트)을 **빌림 구간**으로 표시한다. 구간 안에서 소유 스레드 전용 동기 API 가 남의 region 을 만지면:

1. 그 region 이 지금 도는 틱을 **마칠 때까지** 기다린다(틱 중간에는 끼어들지 않는다 — 바닐라 틱 순서가 그대로다).
2. 틱을 마친 그 자리에서 전역 스레드가 그 region 을 **쥔다**: 워커가 틱을 꺼낼 때처럼 `ticking`·`tickThread` 를 세우고 대기열에서 뺀다. 그래서 그 뒤의 모든 소유 검사(`isOwnerThread`·`assertOwns`)가 그대로 통과하고, 어댑터는 고칠 것이 없다.
3. 구간이 닫힐 때(한 박동의 동기 작업이 다 돈 뒤) 쥔 region 을 모두 **돌려준다**. 틱을 돌지 않았으니 다음 틱 시각은 그대로이고, 늦었으면 곧바로 돈다.

만지지 않은 region 은 계속 병렬로 돈다. 빌림은 만질 때만 일어나므로, 월드를 만지지 않는 옛 작업(대부분의 타이머·DB·스코어보드 계산)과 Folia 를 아는 플러그인은 비용이 0 이다.

| 경우 | 동작 |
|---|---|
| 동기 작업·`callSyncMethod` | 한 박동의 작업 전체가 한 구간. 같은 박동의 작업들은 한 번 빌린 region 을 같이 쓴다(작업마다 넘겨주고 받지 않는다) |
| 옛 명령 실행·자동완성(전역) | 명령 하나가 한 구간(`PrimaryThread.enter` 가 연다) |
| 전역 스레드에서 오는 Bukkit 이벤트 | 리스너 호출 묶음이 한 구간. region 스레드의 이벤트는 그 region 이 이미 소유라 빌릴 일이 없다 |
| 비동기 작업·Furnace 전역 작업·다른 스레드 | 빌리지 않는다 — 예전처럼 `ThreadOwnershipException`(Bukkit 에서도 비동기에서 월드를 만지면 안 된다, Furnace 플러그인 규칙은 그대로) |
| Paper `GlobalRegionScheduler` | 빌리지 않는다 — Folia 규칙 1:1 그대로(아래 결정 항목) |

빌림이 걸리는 동기 API: `Block#data`·`Block#set`(=`getType`·`setType`·`getBlockData`·`setBlockData`), `World#blockNow`·`setNow`·`spawnEntityNow`·`nearbyEntitiesNow`(=`World#spawnEntity`·`getNearbyEntities`·`Entity#getNearbyEntities`), `Entity#editPersistentDataNow`(=엔티티 PDC), `aiNow`·`MobAi`·`MobBrain`, `Internals#entityNow`·`levelNow`·`wrapEntity`(=`getHandle()` NMS shim), 그리고 region 스케줄러의 `isOwnerThread()`.

### 왜 이것인가 (선택지 비교)

| 선택지 | 호환성 | 성능 | 문제 |
|---|---|---|---|
| ① 지금처럼(예외) | 낮음 — 옛 플러그인 대부분이 동기 작업에서 월드를 만진다 | 최고 | 플러그인이 안 돈다 |
| ② 접근마다 소유 region 에 넘기고 기다리기 | 높음 | 낮음 — 읽기 한 번마다 그 region 의 다음 틱 사이(수 ms)를 기다린다. 블록 1000칸을 읽는 루프는 수 초 | 교착 위험이 크다(기다리는 동안 다른 것을 쥐고 있으면) |
| ③ 작업을 만든 플레이어·위치의 region 에서 돌리기 | 중간 — 위치를 모르는 작업이 많고, 여러 region 을 만지면 다시 예외 | 좋음 | 옛 코드의 "모두 한 스레드" 가정(정적 필드·HashMap)을 region 스레드 사이 경쟁으로 깨뜨린다 |
| ④ 박동마다 모든 region 을 멈추고(stop-the-world) 돌리기 | 최고 — Bukkit 의 주 스레드와 똑같다 | 나쁨 — 옛 반복 작업이 하나라도 있으면 매 틱 전 서버가 멈춘다(병렬성 0) | — |
| **⑤ 만진 region 만 빌리기(고름)** | 높음 — 동기 읽기·쓰기가 곧바로 되고, 쓴 값이 곧바로 읽힌다 | 좋음 — 만진 region 만, 한 박동에 한 번, 다른 region 은 계속 | 여러 region 사이의 한순간 일관성은 없다(아래 차이 1) |

②와 달리 빌린 뒤의 접근은 비용이 없다(그 region 은 박동이 끝날 때까지 이 스레드 것). ④와 달리 만지지 않은 region 은 멈추지 않는다.

## 교착이 없는 이유

- 빌리는 쪽은 **전역 스레드만**이다. region 스레드가 남의 region 틱을 기다리면 둘이 서로 기다릴 수 있어 막았다(`GlobalThread.openRegionAccess` 는 전역이 아니면 빈 손잡이).
- region 틱이 전역 스레드를 기다리는 길은 명령 블록의 `awaitUrgent` 뿐이고 예산(기본 20ms)이 있어 풀린다. 플러그인 Future 는 서버 스레드에서 `join` 할 수 없다(`RegionFuture`).
- 빌린 region 에 **다른 region 이 합치기를 양보하고 기다리면**, 그 region 을 빌리려 할 때 쥔 쪽이 그 자리에서 흡수한다(`absorbIntoBorrowed` — 쥔 region 은 틱 사이라 흡수해도 된다). 쥔 region 이 틱 중인 상대에게 양보하는 일은 없다(돌려줄 때 대기열에 못 돌아오기 때문). 돌려줄 때 나머지 병합을 끝낸다.
- 차원 잠금을 쥔 채로는 빌리지 않는다(틱을 마치는 워커가 그 잠금을 잡아야 한다).
- 안전판: 한 region 을 **5초**(`-Dfurnace.regionBorrowWaitMillis`) 안에 못 빌리면(틱이 멈춘 region 등) 빌리지 않고 예전처럼 `ThreadOwnershipException` 을 내며 경고 한 줄을 남긴다. 기다리는 동안 다른 모니터를 쥐고 있어 그 region 틱이 그 모니터를 기다리는 경우(호환 어댑터 안에서는 없다)도 이 한도로 풀린다.
- 빌리려고 기다리는 동안 워커는 그 region 의 새 틱을 꺼내지 않는다(`borrowWanted`) — 늦은 region 이 마치자마자 다음 틱을 잡아 빌리는 쪽이 굶는 일을 막는다.

## 비용

- 아무도 빌리지 않으면: 소유 검사의 원자 정수 읽기 하나(`RegionBorrow.OPEN`). region 틱·작업 큐 경로는 그대로다.
- 빌리면: 전역 스레드가 그 region 의 **남은 틱 시간**만큼 기다린다(틱 사이에 있었으면 바로). 그 region 은 박동 구간 동안 틱을 쉬고, 끝나면 곧바로 따라잡는다. 다른 region 은 영향이 없다.
- 관측: `RegionBorrow.stats()` — 빌린 횟수, 기다린 시간 합, 한도 초과 횟수.

## Bukkit 과 다른 점·한계

1. **여러 region 의 한순간 일관성은 없다.** Bukkit 은 동기 작업이 도는 동안 온 월드가 멈춰 있다. 여기서는 region 마다 빌린 순간이 다를 수 있다(A 를 읽고 B 를 읽는 사이 B 가 한 틱 더 갔을 수 있다). 한 번 빌린 region 은 그 박동이 끝날 때까지 멈춰 있다. Furnace 는 원래 region 마다 틱이 따로라(동시에 한 틱이 아니다) 이것이 더 나빠지지는 않는다.
2. **비동기로만 있는 API 는 여전히 늦게 된다.** 동기 작업 안의 `Entity#teleport`(`teleportAsync` 와 같은 길)·블록 용기 인벤토리처럼 Furnace API 가 비동기뿐인 것은 빌린 region 을 돌려준 뒤에 처리된다. 같은 작업 안에서 바로 다시 읽으면 옛 값이다. 그런 API 를 동기 판으로 넓히는 것은 따로(H 묶음 C·D).
3. **로드 안 된 청크는 동기로 불러오지 않는다**(예전과 같다 — `IllegalStateException`/빈 값). Bukkit 은 `getBlockAt` 이 청크를 불러온다.
4. **D1(옛 리스너와 옛 동기 작업의 동시 실행)은 그대로다.** region 스레드에서 도는 옛 리스너와 전역의 옛 동기 작업은 여전히 동시에 돌 수 있다(빌린 region 의 리스너만 그 동안 쉰다). 옛 플러그인의 정적 필드 경쟁은 이 작업 범위 밖이다.
5. **Paper `GlobalRegionScheduler` 작업은 빌리지 않는다**(Folia 규칙). Paper(비 Folia)에서는 그 작업이 주 스레드라 월드를 만질 수 있다 — 결정 항목.
6. 시험 서버(수동 시계)에서는 전역 박동이 region 틱보다 먼저 돌아 빌림이 바로 되고, 동기 작업이 본 월드는 그 틱의 region 틱 **전** 상태다(Bukkit 도 스케줄러가 월드 틱보다 먼저 돈다).

## 구성

- API: `GlobalScheduler#openRegionAccess()` → `RegionAccess`(try-with-resources, 겹쳐 열기 가능, 전역이 아니면 `NONE`).
- 서버: `world/RegionBorrow`(구간·`claimChunk`·`claim`·`claimEntity`), `Regionizer#borrow`·`giveBack`·`absorbIntoBorrowed`, `Region.borrowWanted`·`borrowed`, `GlobalThread#openRegionAccess`, `ProfiledScheduling.Global` 위임. 소유 검사 연결: `World.ChunkScheduler#isOwnerThread`, `EntityHandle`·`InternalsImpl`·`MobAiImpl`·`MobBrainImpl`.
- 호환: `PrimaryThread.enter()` 가 전역 스레드에서 가장 바깥 표시일 때 구간을 연다(닫힐 때 돌려준다). `LegacyScheduler` 박동이 한 박동 전체를 감싼다. `EventBridge` 가 전역에서 온 이벤트의 리스너 묶음을 감싼다.

## 시험

- `furnace-server` `RegionBorrowTest`(3): 도는 틱이 끝날 때까지 기다렸다 쥐고 그 동안 다른 region 은 돌고 빌린 region 작업은 밀렸다가 돌려준 뒤 돈다 · 구간 밖·전역 아닌 스레드·주인 없는 청크는 빌리지 않고 겹친 구간은 가장 바깥에서 돌려준다 · 빌린 사이 합치기를 요청한 region 도 교착 없이 빌리고 돌려준 뒤 합쳐진다.
- `furnace-compat-paper` `LegacySyncRegionAccessTest`(4): 동기 작업에서 `getType`/`setType`/`getBlockData` 가 곧바로 맞고 region 스레드에서도 같은 값 · 한 박동의 작업 셋이 한 번만 빌리고 그 틱의 게임 틱도 돈다 · `callSyncMethod` 로 엔티티 만들기·근처 찾기·엔티티 PDC · 시험 스레드·Furnace 전역 작업·비동기 작업은 예전처럼 예외.
