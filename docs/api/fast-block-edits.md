# 빠른 블록 쓰기 (E7, 계획 G6)

큰 범위 편집·미니게임 맵 복구가 NMS `LevelChunkSection.setBlockState` 로 가지 않아도 되게 하는 API. 바탕: [easy-nms-packets-plan-2026-11-12.md](easy-nms-packets-plan-2026-11-12.md) 4.5.

원칙: **바닐라에 있는 뜻만 연다.** 옵션은 바닐라 `Level.setBlock` 플래그(1 이웃 갱신, 2 클라이언트 알림, 16 모양 갱신 건너뛰기)와 `/fill`·`/setblock` 의 방식(놓은 뒤 이웃 갱신, `Clearable.tryClear`)뿐이다. 칸마다 서버의 바닐라 `setBlock` 길을 그대로 지나므로 결과는 같은 플래그의 `setBlock` 을 같은 순서로 부른 것과 같다(시험이 칸 단위로 대조한다). 기본값은 `World.setBlock` 과 같다.

## 1. 쓰는 법

```java
World world = Furnace.server().world("minecraft:overworld");

// 기본: World.setBlock 을 칸마다 부른 것과 같은 결과를 한꺼번에(이웃·모양 갱신, 칸 갱신 패킷)
world.editBlocks(ed -> {
    ed.fill(new BlockPos(0, 64, 0), new BlockPos(31, 70, 31), BlockData.of("stone"));
    ed.set(5, 71, 5, BlockData.parse("oak_stairs[facing=east]"));
}).thenAccept(r -> log.info("바뀜 {} 그대로 {} 건너뜀 {}", r.changed(), r.unchanged(), r.skipped()));

// 맵 복구: 이웃·모양 갱신 없음, 상자를 덮어써도 쏟지 않음, 청크째 다시 보내기, 한 틱에 청크 8개씩
world.editBlocks(BlockEditOptions.FAST.chunksPerTick(8), ed -> snapshot.forEach(ed::set));

// /fill 과 같은 방식: 다 놓은 뒤 놓은 칸마다 이웃 갱신(중간 상태로 레드스톤·모래가 반응하지 않는다)
world.editBlocks(BlockEditOptions.DEFAULT.neighborUpdates(NeighborUpdates.AFTER_EDIT).clearContainers(true), ed -> ...);

// 이벤트 처리기 안(그 region 스레드)에서 바로
BlockEditResult r = world.editBlocksNow(BlockEditOptions.DEFAULT, ed -> ed.set(pos, BlockData.AIR));
```

## 2. 옵션

| 옵션 | 값 | 바닐라 뜻 |
|---|---|---|
| `neighborUpdates` | `IMMEDIATE`(기본) | 플래그 1 — 칸마다 바로 `neighborChanged`(World.setBlock 의 11과 같다) |
| | `AFTER_EDIT` | 플래그 1 없이 놓고, 다 놓은 뒤 바뀐 칸마다 `blockUpdated(pos, 지금 블록)` — `FillCommand`·`CloneCommand` 와 같다. region 이 여럿이면 region 묶음(틱 묶음)마다 |
| | `NONE` | 플래그 1 없음 |
| `shapeUpdates` | `true`(기본) / `false` | false 면 플래그 16(`UPDATE_KNOWN_SHAPE`) — 계단·울타리 잇기, 지지대 잃은 블록 부수기를 하지 않는다 |
| `clientUpdates` | `BLOCKS`(기본) | 플래그 2 — 칸·섹션 갱신 패킷 |
| | `CHUNKS` | 플래그 2 는 그대로(바꾼 청크 밖으로 번진 모양 갱신은 칸 갱신으로 간다), 바꾼 청크는 빛까지 계산한 뒤 보는 사람에게 청크째 한 번 다시 보내고 그 청크의 칸 갱신은 버린다. 클라이언트가 보는 결과는 같다 |
| | `NONE` | 플래그 2 없음(번진 모양 갱신도 알리지 않는다). 플레이어별 보기를 따로 보낼 때만 |
| `clearContainers` | `false`(기본) / `true` | true 면 다른 상태로 바꾸는 칸의 내용물을 먼저 비운다(`Clearable.tryClear`, `/fill`·`/setblock`). 같은 상태인 칸은 건드리지 않는다 |
| `chunksPerTick` | `0`(기본, 한 번에) / n | region 한 틱에 청크 n 개까지, 나머지는 다음 틱(합치기·나누기를 따라간다) |

어느 옵션이든 일어나는 것(바닐라 `LevelChunk.setBlockState` 몫): 블록의 `onRemove`·`onPlace`(예약 틱 걸기, 블록 엔티티 만들기·지우기, 레드스톤 선 세기, `clearContainers=false` 면 담긴 아이템 쏟기), 빛 다시 계산(틱 끝에 한 번 묶어서 — CHUNKS 는 재전송 전에), 마을 POI, 저장 표시. 이벤트(BlockPlace 등)는 거치지 않는다(World.setBlock 과 같다).

프리셋: `DEFAULT` = World.setBlock. `FAST` = `NONE`·모양 끔·`CHUNKS`·내용물 비우기 — 놓은 그대로 남는다. 다만 놓일 때 스스로 반응하는 블록(모래·자갈은 예약 틱으로 떨어진다)은 바닐라대로다.

## 3. 스레드·region·순서

- `editBlocks(options, edit)`: `edit` 은 **부른 스레드에서 바로** 불린다. 월드를 읽지 않고 목록만 쌓는다(청크 → 섹션별 4096칸 배열, 같은 칸은 마지막 값). 함수가 끝나면 `BlockEditor` 는 닫힌다.
- 적용은 청크마다 그 청크를 가진 region 스레드에서 한다. 지금 주인별로 묶어 각 region 작업 큐에 하나씩 넣고, 묶음이 돌 때 주인이 바뀐 청크는 그때의 주인에게 다시 보낸다. 여러 region 은 동시에 돈다.
- 순서: 한 청크 안에서 아래 섹션부터, 섹션 안에서 y·z·x 오름차순(적은 순서가 아니다). 순서가 결과를 바꾸는 편집(IMMEDIATE 로 지지대와 그 위를 같이 바꾸기 등)은 나누거나 `AFTER_EDIT` 를 쓴다.
- 로드 안 된 청크·높이 밖 칸은 건너뛰고 `skipped` 로 센다. 청크를 불러오거나 만들지 않는다.
- 알 수 없는 블록·속성은 `edit` 안에서 `IllegalArgumentException` → future 가 그 예외로 끝나고 아무 칸도 바뀌지 않는다.
- `editBlocksNow`: 모든 청크가 이 스레드가 돌리는 region 것이어야 한다. 하나라도 아니면 아무것도 바꾸지 않고 `ThreadOwnershipException`. `chunksPerTick` 은 무시.

## 4. 측정 (2026-11-12, 4코어 컨테이너, 평지, 4×4 청크, 보는 플레이어 1명)

돌 ↔ 참나무 판자를 번갈아 채운 3회 중 뒤 2회. 바이트는 그 플레이어에게 간 패킷(zlib 은 패킷마다 압축, 256바이트 미만은 그대로 — 바닐라 압축 문턱과 같다).

| 칸 수 | World.setBlock 칸마다 | editBlocks DEFAULT | 이웃·모양 끔 + BLOCKS | FAST (CHUNKS) |
|---|---|---|---|---|
| 65,536 (높이 16) | 205~220 ms, 655 KB | 43~44 ms, 111~128 KB | 55~60 ms, 111~128 KB | 57~61 ms, **4.6 KB** |
| 262,144 (높이 64) | 805~1,156 ms, 2.6 MB | 183~189 ms, 457~524 KB | 73~75 ms | 85~86 ms, **4.5 KB** |
| 524,288 (높이 128) | 2,013~2,353 ms, 5.2 MB | 391~424 ms, 0.9~1.05 MB | 119~124 ms | 117~124 ms, **4.2 KB** |

- 시간은 부른 스레드에서 future 가 끝날 때까지(region 작업 큐 왕복 포함). World.setBlock 은 칸마다 작업 하나·future 하나·블록 갱신 패킷 하나라 느리다. editBlocks 는 region 마다 작업 하나, 섹션마다 패킷 하나.
- 이번에 함께 고친 것: 틱 끝 변경 방송의 칸 집합을 `TreeSet<Short>` 에서 4096비트 배열로(같은 오름차순, 상자 없음), 블록 엔티티 없는 청크는 칸마다 블록 엔티티를 찾지 않는다. 바뀐 칸이 많은 모든 블록 쓰기(폭발·피스톤·/fill 포함)가 빨라진다 — 이웃·모양 끔 52만 칸이 211~289 ms → 119~124 ms.
- CHUNKS 는 압축 전에는 청크 패킷(빛 배열 포함)이 더 크지만 압축하면 섹션 갱신보다 수십 배 작고, 클라이언트도 칸마다 setBlock·빛 계산을 하지 않는다. 평지라 압축이 특히 잘 된 값이다 — 실제 지형은 더 크다(가정).

## 5. 뒤로 미룬 것

- 블록 엔티티 NBT 함께 쓰기(`/setblock ... {Items:[...]}` 처럼) — 지금은 `editBlockEntityNbt` 를 따로.
- 로드 안 된 청크 불러와 쓰기(티켓 걸기) — 지금은 건너뛰고 센다.
- 칸 순서를 적은 순서대로 지키는 옵션.
