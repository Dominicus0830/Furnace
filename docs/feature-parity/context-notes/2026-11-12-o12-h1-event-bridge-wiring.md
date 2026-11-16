# O12 H1 — 서버가 내던 여섯 이벤트를 Bukkit 다리에 잇기 (2026-11-12)

O12 보고서(H1): `EventBridge.NOT_FIRED` 가 낡아 `BlockPlaceEvent`·`PlayerDropItemEvent`·`BlockGrowEvent`·`BlockFadeEvent`·`BlockExplodeEvent`·`EntitySpawnEvent` 가 Bukkit 쪽에 안 닿았다(보호 플러그인이 설치를 못 막음). 이 여섯을 잇고 목록에서 뺐다. 새 짝은 `WorldChangeMappings`(Mappings 와 분리 — 다른 호환 PR 과 같은 파일을 안 건드린다).

## 기준(CraftBukkit/Paper 1.21.1 기억 기반 — Paper 원문 대조 불가, 가정)
- BlockPlace: 막는 것은 `isCancelled() || !canBuild()`(`CraftEventFactory.callBlockPlaceEvent`). CraftBukkit 은 놓은 *뒤* 호출, Furnace 는 *전* 호출 → `getBlockPlaced()` 는 "놓일 상태"를 보이는 `CompatBlock.showing`. 이벤트 뒤에도 그 상태를 보인다(취소돼도). 월드 좌표로 다시 읽으면 아직 원래 블록(차이, 문서에 적음).
- Furnace 이벤트가 모자란 값은 서버가 채웠다: `BlockPlaceEvent` 에 `placedData`·`replacedData`·`item`·`isMainHand`(allowPlace 가 소유 스레드에서 읽음), `PlayerDropItemEvent` 에 `entity()`, `BlockExplodeEvent` 에 `yield`·`result`, `Entity#droppedItem()`·`pickupDelay()`. 옛 생성자는 그대로.
- PlayerDropItem: CraftBukkit 순서(엔티티·속도를 먼저 만들고 이벤트) — 구독자가 있을 때만(`GameEvents#wantsPlayerDrop`). 구독자 없으면 난수·엔티티 번호 소비는 이전과 같다. 취소하면 엔티티를 버리고 아이템은 인벤토리로(이전 경로).
- EntitySpawn: 서버의 하위(아이템·살아 있는 것·투사체)가 같은 처리기 목록으로 온다. 아이템 → `ItemSpawnEvent`(`CraftItem`), 살아 있는 것 → `CreatureSpawnEvent`(새 클래스, EntitySpawnEvent 목록 공유), 그 밖 → `EntitySpawnEvent`. 플레이어는 제외.
- `CompatBlockState`(스냅숏)·`Block#getState()`·`CraftItem` 추가. 쓰기(`Item#setItemStack`·`setPickupDelay`)는 `UnsupportedFeature`.

## 알려진 구멍
- `BlockMultiPlaceEvent`(침대·문) 없음 — 한 칸 이벤트로 온다.
- `BlockExplodeEvent#setYield` 는 서버가 적용 안 함. 터진 블록 상태는 종류 기본값.
- 블록 엔티티 내용(표지판 글·상자)은 `BlockState` 에 없음.
- 비동기 스레드에서 옛 생성자 `BlockPlaceEvent` 를 받으면 교체될 블록은 공기로 본다(소유 스레드면 월드 값).

## 시험
`WorldChangeEventsTest`(6: 설치 값·취소·canBuild, 실제 설치 경로, 자람·사라짐, 폭발 목록 편집·취소, 스폰 3종·취소, 버리기 취소·허용), `EventBridgeTest` 표에 여섯 추가, `CancelResyncE2ETest` 2개(서버 이벤트가 상태·손·아이템·드롭 엔티티를 보임).
스폰 보호 반경(16) 안에서 일반 접속자는 놓을 수 없어 실제 경로 시험은 `spawn-protection=0` 으로 돌린다.

## 독립 검토 반영 (PR #276)

- **R1 반영:** EntitySpawn 매핑이 투사체(`ProjectileLaunchEvent`)와 탈것(`*boat`·`*raft`·`*minecart`)·번개(`lightning_bolt`)를 건너뛴다. Paper 는 이들을 각각 ProjectileLaunch·VehicleCreate·LightningStrike 로 내고 EntitySpawnEvent 는 그 밖의 것만 받는다. 시험: `projectilesVehiclesAndLightningAreNotBukkitEntitySpawnEvents`.
- **후속(이번 PR 밖):**
  - R2 `CompatBlock.showing` 이 이벤트 뒤에도 남음, 처리기 안의 놓인 칸 쓰기가 설치에 덮임 — 이벤트 끝 고리 필요.
  - R3 BlockGrow `getNewState()` 수정이 무시됨(API 에 새 상태 쓰기 필요).
  - R4 `canBuild` 초기값이 늘 true(놓일 칸 기준 `mayInteract`).
  - R5 `CraftItem#setItemStack`·`setPickupDelay` 미지원.
  - R6 BlockExplode `getExplodedBlockState()` 가 기본 상태.
  - R7 취소 뒤 부분 반환의 남은 것을 새 엔티티로 다시 던져 id·난수를 더 씀(구독자 있을 때만, 기존 동작).
