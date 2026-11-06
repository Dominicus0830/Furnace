# `Region.chunkAt` 호출처 전수 조사 (2026-10-27)

배경: `shared-scheduler-review-2026-10-27.md` R2·7.5-4항. `Region.chunkAt` 이 미로드 청크를 **region 스레드에서 동기 생성·디스크 읽기**해
틱을 막았다(scheduler 규칙 6 위반). 조사 방법: `grep -rn "chunkAt(" furnace-server/src` 와, `chunkAt` 을 쓰는 Region 메서드
(`getBlock`·`chestAt`·`setChestItem`·`markBlockEntityChanged`·`markChestChanged`·`breakBlock`·`placeBlock`)의 main 호출처 grep.

전제: `chunkAt` 과 위 메서드는 모두 `world.assertOwns(this)` 로 시작한다. owner 는 `Region.tickThread`(Regionizer 워커가 그 region 을
틱하는 동안만 설정) 이므로 **외부 스레드에서 직접 부르는 운영 호출처는 없다**. 시험도 `sched.region(..).execute` / `submitAtChunk` 로
region 스레드에 들어가 부른다.

## 1. `Region.chunkAt` 직접 호출 (main)

| 위치 | 호출 스레드 | 항상 로드된 청크인가 | 미로드 가능 | 조치 |
|---|---|---|---|---|
| `Region.breakBlock(Player,..)` | region | 아님(호출처 없음, 도구 경로가 위임) | 예 | 미로드면 `false` + 비동기 로드 요청 |
| `Region.getBlock` | region | 거의(아래 2장) | 이론상 | 미로드면 공기 + 비동기 로드 요청 |
| `Region.chestAt` | region | 시험만 | 예 | 미로드면 null |
| `Region.setChestItem` | region | 시험만 | 예 | 미로드면 무시(기존 "없으면 무시"와 같음) |
| `Region.markBlockEntityChanged` | region | 예(블록 엔티티가 있다 = 청크 로드) | 내린 뒤 늦은 호출만 | `loadedChunk` 만 봄, 없으면 무시 |
| `Region.markChestChanged` | region | 호출처 없음 | 예 | `loadedChunk` 만 봄, 없으면 무시 |
| `Region.placeBlock(int..)` | region | 시험·도구만 | 예 | **바꾸지 않음** → `chunkAtBlocking`(동기) |
| `Region.breakBlock(int..)` | region | 시험·도구만 | 예 | **바꾸지 않음** → `chunkAtBlocking` 후 위임 |

`ChunkLevel.chunkAt(Pos)` 는 이름만 같은 private 메서드(`loadedChunk` 기반, 생성 안 함) — 무관.

## 2. 위 메서드의 운영 호출처 (main)

| 위치 | 메서드 | 호출 스레드 | 미로드 가능성 | 비고 |
|---|---|---|---|---|
| `ConnectionHandler.java:1709` 제작대 `stillValid` | getBlock | region(`MenuBlockContext`) | 사실상 없음 | 메뉴는 거리 8 블록 안에서만 유효 → 플레이어 시야 청크 |
| `ConnectionHandler.java:1735` 블록 메뉴 `stillBlock` | getBlock | region | 사실상 없음 | 같음 |
| `ConnectionHandler.java:1757` 모루 `anvilUsed` | getBlock | region | 사실상 없음 | 같음 |
| `ConnectionHandler.java:1797-1798` 마법부여대 책장 | getBlock | region | **있을 수 있음(가정)** | ±2 칸이 청크 경계를 넘는다. 그 청크가 다른 region 소유면 예전에도 `claim` 예외였고(로드된 경우는 그대로 읽음), 미로드면 이제 공기 → 책장 수·마법부여 비용이 달라질 수 있다. 플레이어가 거리 8 안이고 시야 청크는 로드돼 있어 실제로 미로드일 가능성은 낮다고 보나 확인 안 함 |
| `ConnectionHandler.java:2899` 신호기 `stillBlock` | getBlock | region | 사실상 없음 | |
| `ConnectionHandler.java:2974` 전리품 상자 열기 | markBlockEntityChanged | region | 없음 | 방금 그 블록 엔티티를 열었다 |
| `LecternContainer.java:47`, `BeaconMenu.java:72` | markBlockEntityChanged | region | 없음 | 열린 메뉴의 블록 엔티티 |

결론: 운영 경로는 모두 로드된 청크만 부른다. 그래도 미로드면 예전엔 **틱 안에서 월드젠/디스크 읽기**가 일어났고, 이제는
"로드 안 됨"으로 본다. 미로드면 `stillValid` 가 거짓 → 화면이 닫히는데, 바닐라라면 거리 8 안의 블록 청크가 미로드인 상황 자체가 없다(**가정**: 관측 차이 없음).

## 3. 명령(`/setblock`, `/fill`, `/summon`, `/locate`)

`Region.chunkAt` 을 쓰지 않는다. `BlockCommands.java:120,161` 은 `level.hasChunkAt` 으로 확인하고 미로드면 `argument.pos.unloaded`
로 실패한다 — 바닐라 `BlockPosArgument.getLoadedBlockPos` 와 같은 관측이다. "로드 뒤 적용"으로 바꾸면 오히려 바닐라와 달라지므로
**바꾸지 않았다**. `/summon`·`/locate` 도 이 경로와 무관.

## 4. 시험 (25곳, 15파일)

`AutosaveTest`, `BlockBreakTest`, `BlockPlaceTest`, `ChestInteractionTest`, `ChunkBodyCacheRaceTest`, `ChunkEvictionTest`, `ConnectionInputTest`,
`EntityRegionTest`, `MapRegionTest`, `MultiDimensionTickBench`, `RegionChunkOwnershipTest`, `RegionPlaceBlockTest`, `RegionTickBench`, `RegionizerTest`,
`SaveFailureRetryTest`. 모두 region 스레드에서 동기 생성을 기대하므로 `chunkAtBlocking` 으로 이름만 바꿨다(동작 같음).

## 4.1 (검토 반영) 운영 경로 옮김
`ChestInteractionTest`·`AutosaveTest`·`SaveFailureRetryTest` 는 `World.awaitChunk`(운영 `finishLoad` 경로: 블록 엔티티 등록·예약 틱·POI·빛 미리 계산)로 청크를 올리게 바꿨다.
`ChunkEvictionTest` 는 바꾸지 않았다 — 외부에서 미리 올리면 lambda 실행 전에 퇴출 유예(3틱)가 지나 청크가 내려갈 수 있어 간헐 실패가 생긴다.
나머지 시험은 `chunkAtBlocking` 그대로(다른 설치 경로라 운영 회귀를 잡지 못함 — 남은 위험).

## 5. 바뀐 API
- `chunkAt(cp)`: 로드된 청크 또는 null. 미로드면 `loadChunk` 로 비동기 로드만 건다(다른 region 구역이면 요청 없이 null — 옛 경로는 예외).
- `getBlock`·`chestAt`·`setChestItem`·`breakBlock(Player,..)` 는 `loadedChunk` 만 보고 **로드 요청도 걸지 않는다**(매 틱 `stillValid` 가 claim 으로 region 을 넓히지 않게).
- (검토 반영) `withChunk` 는 제거했다 — main 호출처 0, 남의 구역·전역 `loadedChunk` 소유권·실패 신호 문제가 있었다.
- `chunkAtBlocking(cp)`: 옛 동기 경로. 시험·도구 전용. 호출 수를 `World.blockingLoads()` 로 센다(월드별).
- `World.awaitChunk(cp)`: 외부 스레드가 기다려서 받는 경로. 어느 region 워커 스레드에서든(다른 region·차원 포함, `Regionizer.isRegionWorkerThread`) 부르면 `IllegalStateException`.

## 6. 남은 위험
- `placeBlock(int..)`/`breakBlock(int..)` 는 여전히 동기 생성한다(시험·도구 전용, main 호출처 0). 운영 코드에서 부르면 다시 R2 가 생긴다.
- `getBlock` 은 다른 region 이 가진 **로드된** 청크도 그대로 읽는다(기존 동작, 소유권 경합 부채). 이번에 바꾸지 않았다.
