# 엔드 관문 블록 엔티티와 순간이동 (2026-11-03)

Paper·Spigot 대비 미구현 목록(`paper-spigot-gap-2026-11-03.md` 3장·6장 5번)의 "엔드 관문 순간이동"을 구현했다.
바닐라 1.21.1 서버 jar(`main` 브랜치 `tools/vanilla-data/server-1.21.1/`)의 `TheEndGatewayBlockEntity`(난독 `dry`)·`EndGatewayBlock`(`dil`)·
`ThrowableProjectile.tick`(`cnx`)를 `javap -c -p` 바이트코드로 대조했다(Vineflower 실행은 거부돼 쓰지 않음).

## 구현한 것
| 바닐라 | Furnace | 대조 결과 |
|---|---|---|
| 저장 `Age`(long)·`exit_portal`(int[3])·`ExactTeleport`(참일 때만), 읽을 때 `Level.isInSpawnableBounds` 로 거름 | `EndGatewayBlockEntity.saveAdditional/load` | 일치 |
| `getUpdateTag = saveCustomOnly`, 블록 엔티티 데이터 패킷 있음 | `updateTag`·`hasUpdatePacket` | 일치 |
| `portalTick`: 나이 +1, 쿨다운 중이면 −1 아니면 2400틱마다 `triggerCooldown`, 생성 중(<200)·쿨다운 상태가 바뀌면 `setChanged` | `tick` | 일치 |
| `triggerCooldown`: 쿨다운 40, 블록 이벤트 (1, 0), `setChanged` / `triggerEvent(1)`: 쿨다운 40 | 같음 | 일치 |
| `EndGatewayBlock.entityInside`: `canUsePortal(false)` 이고 쿨다운이 아니면 `setAsInsidePortal` + `triggerCooldown` | `EndGatewayBlock.entityInside`, 플레이어는 `Region.checkInsidePortals` → `entityInsidePlayer` | 일치 |
| 포털 대기 시간 0(기본) — 다음 틱 `handlePortal` 에서 이동 | 기존 `PortalProcessor` | 일치 |
| `getPortalDestination`: `DimensionTransition(같은 레벨, getPortalPosition 의 getBottomCenter, 엔더 진주면 (0,−1,0) 아니면 지금 속도, yRot, xRot, PLACE_PORTAL_TICKET)` | `EndGatewayBlock.transition` | 일치(소리 없음, 도착 칸 포털 티켓) |
| `getPortalPosition`: 출구가 없고 엔드면 `findOrCreateValidTeleportPos` → 10 위에 `END_GATEWAY` 지물(`knownExit(관문 자리, false)`) → `setExitPosition(p, exactTeleport)`. 출구가 있으면 정확 이동이면 그 칸, 아니면 `findExitPosition` | `EndGatewayBlockEntity.getPortalPosition`(동기판)·`EndGatewayBlock.travelWithinLevel`(서버판) | 일치 |
| `findExitPortalXZPosTentative`: `(x,0,z).normalize()·1024` 에서 청크가 비지 않은 동안 16번까지 −16, 그다음 빈 동안 16번까지 +16 | `ExitWalk` | 일치 |
| `findValidSpawnInChunk`: (최소,30,최소)~(최대, 가장 높은 구역+15, 최대) x→y→z, 엔드 돌 + 위 두 칸이 가득 찬 충돌 블록 아님, (0,0,0) 가운데 거리 최소(같으면 먼저) | `findValidSpawnInChunk` | 일치 |
| 없으면 `(w.x+0.5, 75, w.z+0.5)` 에 `end_island` 를 `RandomSource.create(pos.asLong())` 로 | `placeEndIsland`(EndIslandFeature 모양 공용) | 일치 |
| `findTallestBlock(pos, r, allowBedrock)`: x→z, 가운데는 allowBedrock 일 때만, 꼭대기−1 부터 지금까지 찾은 높이 위까지 내려가며 가득 찬 충돌 블록(기반암 제외 옵션) | `findTallestBlock` | 일치 |
| `findExitPosition`: `findTallestBlock(pos+(0,2,0), 5, false).above()` | 같음 | 일치 |
| 드래곤 처치 관문(`spawnNewGateway`)은 출구 없는 관문 | `EndDragonFight.placeGateway` 가 공용 `placeGatewayPortal` 을 쓴다 | 일치 |
| `ThrowableProjectile.tick` 이 맞음 처리 뒤 `checkInsideBlocks()` | 빠져 있어 추가 — 엔더 진주·눈덩이 등이 관문·지옥문·덫줄 등 `entityInside` 를 받는다 | 일치(기존 누락 수정) |

## 바닐라와 다른 점 (Furnace 구조 때문)
- **비동기**: 바닐라는 서버 스레드에서 출구 쪽 청크(최대 33개, 생성 포함)를 그 자리에서 올린다. Furnace 는 출구 쪽이 다른 region 스레드 소유이므로
  `DimensionTravel.withLoadedChunks` 로 청크를 차례로 올리고 그 region 스레드에서 계산한다. 첫 사용(출구 만들기)은 청크 생성 시간만큼, 그 뒤는 몇 틱 늦게 도착한다.
- **이동 중 정지**: 그동안 플레이어는 region 에서 떼어 두고(지옥문과 같은 방식) 정해지면 떠난 자리로 되돌린 뒤 보정 텔레포트한다.
  엔티티는 다른 차원 이동처럼 이쪽에서 빼고 출구 region 에 같은 종류를 새로 만든다(UUID·저장 데이터 유지, **엔티티 번호는 바뀐다** — 클라이언트는 사라졌다 나타나는 것으로 본다. 바닐라는 같은 엔티티를 옮긴다). 승객은 내려놓는다(바닐라는 승객도 함께 보내 다시 태운다 — 기존 차원 이동과 같은 한계).
- **동시 사용**: 출구를 만드는 동안(쿨다운 40틱보다 길 수 있다) 같은 관문으로 들어온 것은 만드는 중인 출구를 기다렸다가 같은 출구로 간다(출구는 하나만 생긴다).
- **포털 티켓의 엔티티 틱**: 바닐라 포털 티켓(거리 3, 레벨 30)은 가운데 둘레 1청크를 엔티티 틱 단계로 만든다. Furnace 티켓은 청크를 올려 두기만 하고
  엔티티 틱은 플레이어 근처·강제 로드만 받았다 — 그래서 관문 너머로 간 엔더 진주가 멈춰 주인을 데려가지 못했다. 이제 티켓 거리 − 2 반경(포털 1, 드래곤 7)은
  플레이어가 없어도 엔티티·블록 엔티티 틱을 받는다(`World.entityTicketed`). 지옥문 너머로 던진 진주·아이템도 바닐라처럼 계속 움직인다.

## 남은 것
- `EndDragonFight.hasActiveExitPortal`: 바닐라는 (-8..8)² 청크에 `TheEndPortalBlockEntity`(관문 포함)가 있으면 참이다. 이제 관문 블록 엔티티가 있으므로 이식할 수 있으나
  이번에는 하지 않았다(받침대 end_portal 블록 판정 그대로). 관문 블록 엔티티가 반지름 96(청크 ±6)에 생기므로 바닐라에서는 첫 처치 뒤 늘 참이 된다는 점에 유의.
- 클라이언트 쪽(빔 연출·입자)은 서버가 보내는 `Age`·블록 이벤트 (1, 0)로 클라이언트가 그린다. 실제 화면은 접속 확인 필요.

## 시험
- 단위: `EndGatewayBlockEntityTest`(저장·월드젠 NBT·나이/쿨다운·walk·엔드 돌 칸·가장 높은 블록·첫 사용 섬과 출구 관문·정확 이동·엔드 밖·쿨다운·엔더 진주),
  `EndGatewayWorldTravelTest`(실제 `World` 의 비동기 경로: 출구 만들기, 동시 두 사용이 같은 출구, 두 번째 사용, 출구 관문에서 되돌아오기).
- 봇(오프라인 서버 + mineflayer, 방법은 `tools/bot-test/README.md` — `claude/project-thread-55b5gw` 브랜치): 아래 표.

### 봇 접속 시험 (2026-11-03, 평지 월드 + 엔드)
| 항목 | 한 것 | 결과 |
|---|---|---|
| 드래곤 처치 관문 | 엔드에서 `/kill @e[type=ender_dragon]`, 15초 뒤 반지름 96 원 위 20자리를 `/execute if block … end_gateway` 로 찾음 | 통과. (-78, 75, 56)(다른 실행에선 (29, 75, -92))에 관문, `Age` 증가 중, 출구 없음 |
| 플레이어 첫 사용 | `/tp` 로 관문 칸에 넣음 | 통과. 1초 안에 (-831.5, 57, 609.5) 로 이동. 원천 관문 `exit_portal` = [-832, 66, 608], 그 칸에 관문과 기반암 틀, 출구 관문의 `exit_portal` = [-78, 75, 56]. 도착은 출구 둘레 5 안 섬 위 |
| 되돌아오기 | 출구 관문에 넣음 | 통과. 원래 관문 옆 (-81.5, 71, 52.5)(관문 아래 깔아 둔 엔드 돌 위) |
| 돼지 | 관문 칸에 `/summon pig` | 통과. 0.3초 안에 (-831.5, 57, 609.5), `PortalCooldown` 300 에서 줄어드는 중 |
| 엔더 진주 | 관문 옆 발판에서 서쪽(관문 쪽)으로 진주를 던짐 | 통과. 진주가 관문을 지나 출구로 가서 떨어지고 던진 플레이어가 (-831.5, 57, 609.5) 로 이동(포털 티켓 청크의 엔티티 틱 덕분 — 고치기 전에는 진주가 출구에서 멈췄을 것) |
| 덤으로 본 것 | `/execute positioned <출구> if entity @e[type=pig,distance=..12]`·`/kill @e[type=pig]` 가 출구 쪽 돼지를 못 찾음(`/data get entity @e[type=pig,limit=1]` 은 찾음) | **기존 문제로 추정**: 명령 쪽 엔티티 선택자가 다른 region 의 엔티티를 거리 조건과 함께 못 보는 듯. 이번 작업 밖이라 고치지 않음 |
