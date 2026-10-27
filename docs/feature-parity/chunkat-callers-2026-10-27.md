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
| `ConnectionHandler.java:1797-1798` 마법부여대 책장 | getBlock | region | 거의 없음 | ±2 칸이 이웃 청크로 넘을 수 있으나 플레이어 곁이라 로드됨 |
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

## 5. 바뀐 API
- `chunkAt(cp)`: 로드된 청크 또는 null. 미로드면 `loadChunk` 로 비동기 로드만 건다(다른 region 구역이면 요청 없이 null — 옛 경로는 예외).
- `withChunk(cp, (region, col) -> ..)`: 로드돼 있으면 즉시, 아니면 로드 뒤 그때의 주인 region 스레드에서(인박스 1회 경유).
- `chunkAtBlocking(cp)`: 옛 동기 경로. 시험·도구 전용. 호출 수를 `Region.BLOCKING_LOADS` 로 센다.
- `World.awaitChunk(cp)`: 외부 스레드가 기다려서 받는 경로. region 스레드에서 부르면 `IllegalStateException`.

## 6. 남은 위험
- `placeBlock(int..)`/`breakBlock(int..)` 는 여전히 동기 생성한다(시험·도구 전용, main 호출처 0). 운영 코드에서 부르면 다시 R2 가 생긴다.
- `getBlock` 은 다른 region 이 가진 **로드된** 청크도 그대로 읽는다(기존 동작, 소유권 경합 부채). 이번에 바꾸지 않았다.
- `withChunk` 콜백은 로드 실패 시 불리지 않는다(로그만).
