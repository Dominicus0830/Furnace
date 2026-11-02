# 엔티티·월드·아이템·인벤토리 플러그인 API 설계 (1단계)

대상: `furnace-api` 의 `org.domi.furnace.api.{world,entity,item,inventory,event.*}`. 서버 구현은 `furnace-server/.../plugin/`.
근거: `docs/handoff/...` 2.3.3 어댑터 대비 원칙 9개, `.claude/rules/scheduler.md`, `docs/api/text.md`(텍스트는 자체 `Text`).

## 1. 한 줄 원칙

> **다른 region 의 상태를 동기로 읽거나 쓰는 메서드는 만들지 않는다.** 읽기는 (a) 이벤트가 만든 불변 스냅샷, (b) `CompletableFuture` 로 소유 스레드에서 실행한 결과, 둘 중 하나뿐이다.
> 소유 스레드에서만 의미 있는 동기 접근은 이름에 `Now` 를 붙이고, 호출 스레드가 소유 스레드가 아니면 `ThreadOwnershipException` 으로 **거부**한다.

Bukkit 은 "메인 스레드 한 개"를 가정하지만 Furnace 는 region 스레드 풀이다. API 가 이 가정을 새로 들여오면 어댑터가 아무리 감싸도 거짓이 되므로, 위반 가능한 형태 자체를 API 에 두지 않는다.

## 2. 스레드 규칙 표

| 종류 | 예 | 어느 스레드에서 호출 | 값의 신선도 |
|---|---|---|---|
| 불변 값 객체 | `BlockData`, `Block`, `ItemStack`, `Location`, `BlockPos`, `InventorySnapshot` | 아무 스레드 | 만든 시점 |
| 월드 상수 | `World.name()/dimension()/seed()` | 아무 스레드 | 불변 |
| 월드 설정 스냅샷 | `World.gameRule(String)` | 아무 스레드 | 최근 값(volatile 읽기) |
| 비동기 읽기/쓰기 | `World.blockAt`, `World.setBlock`, `Entity.refresh/teleport/remove`, `LivingEntity.updateHealth`, `Inventory.get/set/contents` | 아무 스레드 | 소유 region 에서 실행된 시점. `CompletableFuture` 로 결과 |
| 소유 스레드 동기 | `World.blockNow(pos)` | **그 칸을 가진 region 스레드만**. 아니면 `ThreadOwnershipException` | 즉시 |
| 이벤트 핸들 | `event.entity()`, `event.player()` | 이벤트를 받은 스레드에서 읽기 | **이벤트 시점 스냅샷** (`Entity.location()` 등은 낡을 수 있다) |
| 플레이어 핸들 | `Player.health()` 등 기존 | 아무 스레드 | 최근 값(기존 계약 유지) |

- 이벤트마다 "어느 스레드에서 불리는가"를 Javadoc 첫 줄에 적는다. 이벤트 처리기는 **발화한 스레드**에서 돈다. 처리기 안에서 같은 region 의 상태는 `*Now` 로 읽어도 되고, 다른 region 은 반드시 비동기 메서드로.
- 비동기 메서드의 Future 는 `Future.get()` 으로 region 스레드에서 기다리면 안 된다(데드락 위험, scheduler.md 규칙 6). `thenAccept` 로 잇는다.
- 로드되지 않은 청크: `blockAt` 은 `Optional.empty()`, `setBlock` 은 `false`. 청크를 새로 만들게 하지 않는다(임의 좌표 요청으로 region 이 늘어나는 것을 막는다).
- 엔티티 이동: `Entity.scheduler()` 는 **핸들을 만든 시점 위치**의 region 스케줄러다. 그 사이 다른 region 으로 넘어갔으면 `refresh()` 는 `null` 로 끝난다(`isValid` 가 거짓). 엔티티별 스케줄러(Folia `EntityScheduler`)는 다음 단계.

## 3. 타입 목록

### world
- `BlockData` — 불변 record: `type`(레지스트리 키 `minecraft:oak_stairs`) + 속성 맵(정렬). `BlockData.parse("minecraft:oak_stairs[facing=north]")`, `toString()` 은 같은 형식. **숫자 state id 를 노출하지 않는다**(원칙 3, AGENTS 규칙 6). 서버 안에서 canonical key(`name;k=v`)로 `BlockRegistry` 에 조회한다. 잘못된 이름·속성은 쓰기 때 `IllegalArgumentException`.
- `Block` — 불변 record: `world`(차원 키) + `BlockPos` + `BlockData`. 읽은 시점 스냅샷이며 살아 있는 칸이 아니다.
- `World` — 인터페이스.
  - `name()` 레벨 이름(오버월드 `world`, 그 밖 `world_nether`/`world_the_end` 규칙은 Paper 와 같게 — 1단계는 level.dat 이름 + 접미사), `dimension()` 차원 키, `seed()`, `gameRule(String)`(문자열, 없으면 null), `loadedChunkCount()`.
  - `blockAt(pos)` → `CompletableFuture<Optional<Block>>`, `setBlock(pos, BlockData)` → `CompletableFuture<Boolean>`(플래그 11: 이웃 갱신 + 클라이언트 방송).
  - `blockNow(pos)` → `Optional<Block>`, 소유 스레드 전용.
  - `scheduler(BlockPos)` → 그 칸의 region `TaskScheduler`.
- `Furnace.worlds()`, `Furnace.world(String dimensionKey)`.

### entity
- `Entity` — `uniqueId()`, `type()`(`minecraft:zombie`), `location()`/`customName()`/`isValid()` (**핸들 시점 스냅샷**), `world()`, `scheduler()`, `refresh()`, `teleport(Location)`, `remove()`.
- `LivingEntity extends Entity` — `health()`, `maxHealth()`(스냅샷), `updateHealth(float)`(소유 스레드에서 적용).
- `Player extends LivingEntity` — 기존 `Player` 가 이 아래로 들어간다. 기존 메서드(`health()`, `setHealth(float)` 등 "최근 값 + 스레드 안전")는 **계약을 바꾸지 않는다**. `type/refresh/...` 는 기본 구현.
- 서버 엔티티(`org.domi.furnace.server.entity.Entity`)는 노출하지 않는다. 핸들은 이벤트가 만들 때 필드를 복사한다(원칙 9).

### item / inventory
- `ItemStack` — 불변 record: `type`(`minecraft:diamond_sword`), `amount`, `components`(컴포넌트 id → SNBT 문자열, 바뀐 것만), `removedComponents`(기본값을 지운 컴포넌트 id). 편의: `customName()`/`damage()`. 빈 스택은 `ItemStack.EMPTY`(amount 0). `withAmount`, `withComponent`, `withoutComponent` 는 새 객체. 서버 내부 `ItemStack`/`ComponentPatch` 와는 변환기에서만 만난다.
- `InventorySnapshot` — 불변 record: `type`(`minecraft:player`, `minecraft:crafting` …) + `List<ItemStack>`. 이벤트가 들고 다닌다.
- `Inventory` — 살아 있는 인벤토리 핸들. `size()`, `get(slot)`/`set(slot, item)`/`contents()` 모두 `CompletableFuture`. 플레이어 인벤토리는 서버 쪽이 이미 동기화돼 있어 어느 스레드에서든 즉시 완료되지만, **API 계약은 비동기**로 둔다(상자 등 블록 컨테이너가 같은 인터페이스로 올라올 때를 위해). 슬롯 번호는 Bukkit `PlayerInventory` 와 같다: 0..8 핫바, 9..35 주, 36..39 발·다리·가슴·머리, 40 왼손.
- `Player.inventory()` — 위 `Inventory`.

## 4. 이벤트 목록

| 이벤트 | 취소 | 발화 스레드 | 서버 발화 지점 |
|---|---|---|---|
| `EntityDamageEvent` | O | 맞는 엔티티의 region | `LivingEntity.hurtBy` 시작 (취소 = 피해 안 받음, `false` 반환). `damage` 는 방어 계산 **전** 원본 값 |
| `EntityDeathEvent` | X | 죽는 엔티티의 region | `LivingEntity.die` 의 `dead = true` 직후 |
| `EntitySpawnEvent` | O | 넣는 레벨의 region | `ChunkLevel.addFreshEntity` (취소 = 넣지 않음). 청크 로드로 되살린 엔티티는 제외 |
| `PlayerMoveEvent` | O | 플레이어의 region | `Region.processMoves` 검증 통과 직후 (취소 = 이전 자리로 보정 텔레포트) |
| `PlayerTeleportEvent` | O | 호출한 스레드 | `Player.teleportTo` (같은 차원). 차원 이동은 다음 단계 |
| `PlayerInteractEvent` | O | 클릭한 칸의 region | `handleUseItemOn`(RIGHT_CLICK_BLOCK), `Region.handleUseItem`(RIGHT_CLICK_AIR) |
| `PlayerDropItemEvent` | O | 플레이어의 region | `Region.playerDrop` 시작 (취소 = 인벤토리로 돌려줌) |
| `PlayerPickupItemEvent` | O | 아이템이 있는 region | `ItemEntity.playerTouch` 시작 |
| `InventoryClickEvent` | O | 메뉴 처리 스레드(블록 메뉴는 그 블록의 region) | `applyContainerClick` (취소 = 서버가 클릭을 재현하지 않고 전체 재전송) |
| `InventoryOpenEvent` | O | 연결 이벤트 루프 | 제작대·블록 메뉴 열기 직전 (취소 = 열지 않음) |
| `InventoryCloseEvent` | X | 닫기를 처리하는 스레드 | `doCloseContainer` |
| `ChunkLoadEvent` | X | 그 청크의 region | `Region.finishLoad` 설치 직후 |
| `ChunkUnloadEvent` | X | 그 청크의 region | `Region.evictIdleChunks` 에서 내리기 직전 이후 |

공통 규칙: 서버는 `hasSubscribers(type)` 를 먼저 묻고 없으면 이벤트 객체·핸들을 만들지 않는다. `gameEvents()` 는 구독자가 없으면 정적 상수를 돌려주는 `GameEvents.NONE` 이라 호출 지점 비용은 가상 호출 한 번과 boolean 하나다.

## 5. 서버 구현 구조

```
LevelServices.gameEvents()  ──default NONE──►  ServerCore 가 PluginGameEvents 로 대체
   ▲ 엔티티/Region/ItemEntity 는 level.services() 로 접근 (플러그인 패키지에 의존하지 않음)
PluginGameEvents(host)  : hasSubscribers → 핸들 생성 → events.call → 결과 반환
PluginHost.world(dim)   : WorldHandle (캐시)
EntityHandles           : 서버 Entity 필드 복사 → API Entity/LivingEntity
ApiConvert              : 서버 ItemStack ↔ API ItemStack, 서버 Player → API Player
```

- 호출 지점의 변경은 `if (gameEvents.xxx(...)) return;` 한 줄 수준으로 제한한다.
- `furnace-api` 는 서버 클래스를 알지 못한다.

## 6. Paper/Bukkit 어댑터(`furnace-compat-paper`) 매핑 방향

| Bukkit | Furnace API | 어댑터가 할 일 |
|---|---|---|
| `Material`/`BlockData` | `BlockData.type` 키 문자열 | 키 ↔ enum 표 |
| `World#getBlockAt(x,y,z)` (동기) | `blockAt` Future | 어댑터가 "메인 스레드 에뮬레이션" 안에서 소유 region 에 submit 후 결과를 기다리지 않도록, 호출 스레드가 소유 스레드면 `blockNow`, 아니면 예외/지연 안내. 이 에뮬레이션은 어댑터 몫 |
| `Entity#teleport` | `teleport` Future | `Paper.teleportAsync` 와 1:1. 동기판은 어댑터 정책 |
| `ItemStack` (가변) | `ItemStack` (불변) | 어댑터가 가변 래퍼를 쥐고 변경 시 새 값으로 교체 |
| `Inventory` | `Inventory`(비동기) + `InventorySnapshot` | 이벤트 안에서는 스냅샷 기반 읽기 |
| 이벤트 이름·필드 | 같은 이름 | `Cancellable` ↔ `Cancellable` |

## 7. 1단계 구현 범위와 다음 단계

구현(1단계): 위 3장 타입 전부, 4장 이벤트 13종 전부의 API 와 서버 발화. 단 아래 **한계**가 있다(바닐라/Paper 와 다른 점은 `docs/feature-parity/context-notes.md` 2026-11-02 에 기록).

다음 단계(미구현):
1. `EntityScheduler`(엔티티를 따라가는 스케줄러) — 지금은 핸들 시점 위치의 region 스케줄러.
2. 엔티티 조회·스폰 API(`World.spawn`, `World.entities(region)`), 엔티티 AI/속성/효과/탑승.
3. `LivingEntity` 의 `Player` 외 구현이 `damage()`/`heal()`/`getEquipment()` 를 갖게 하기. `Mob`, `Item`(드롭 엔티티) 타입.
4. 블록 엔티티·상자 컨테이너를 `Inventory` 로 노출, `InventoryView`/`InventoryHolder`, 클릭 후 결과 아이템 수정.
5. `EntityDamageEvent` 최종 피해(방어/인챈트 적용 후)와 `DamageCause` 별 하위 이벤트(`EntityDamageByEntityEvent` 등).
6. `PlayerTeleportEvent` 차원 이동 경로·`TeleportCause` 구분, 플레이어 핸들 `Player.world()` 정확한 차원(지금은 `Location.world` 사용).
7. `InventoryOpenEvent` 를 상자·엔티티 컨테이너·상인·말 인벤토리 경로에도 연결.
8. `PlayerInteractEvent` 의 LEFT_CLICK, 엔티티 상호작용(`PlayerInteractEntityEvent`).
9. `PersistentDataContainer`(원칙 7), `Chunk` 객체(`World.chunkAt`), `ChunkLoadEvent.isNewChunk` 의 정확한 판정.
10. 월드 쓰기: `setBlock` 의 `BlockPlaceEvent/BlockBreakEvent` 우회 여부 옵션, 블록 엔티티 데이터.
11. 기존 `BlockBreakEvent/BlockPlaceEvent` 의 하드코딩된 `"minecraft:overworld"` 를 실제 차원으로(이 작업에서 발견, 이 단계에서는 건드리지 않음).
