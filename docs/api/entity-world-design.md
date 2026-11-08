# 엔티티·월드·아이템·인벤토리 플러그인 API 설계 (1·2단계)

> 2026-11-03: 2단계를 더했다(8장). 1~7장은 1단계 기록이며, 7장 "다음 단계" 목록의 처리 상태는 8.6 을 본다.

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
- 비동기 메서드의 Future 는 `Future.get()`/`join()` 으로 region·전역 스레드에서 기다리면 안 된다. 데드락 시나리오: region A 의 처리기가 `world.blockAt(B 의 칸).get()` 으로 기다리는 동안 A 스레드는 멈추고, B 의 처리기도 A 의 칸을 `get()` 으로 기다리면 둘 다 영영 못 돈다(scheduler.md 규칙 6). 서버가 돌려주는 Future 는 `RegionFuture` 라서 서버 스레드(스레드 이름 `furnace-region*`/`furnace-global`)에서 **미완료 상태로** `get()`/`join()` 하면 `IllegalStateException` 으로 거부한다. `thenAccept`/`thenApply` 로 잇는다(파생 Future 도 같은 가드). 일반(비서버) 스레드에서는 기다려도 된다.
- 이벤트 처리기가 던진 예외는 이벤트 버스가 로그만 남기고 다음 처리기로 넘어간다 — `EntityDeathEvent` 리스너가 죽어도 사망 처리는 계속된다(시험으로 확인).
- 플레이어 인벤토리: 읽기는 `PlayerInventory` 가 내부 `synchronized` 라 아무 스레드에서 즉시 완료. **쓰기는 `Player.runOnOwner` 로 소유 스레드에서** 적용하고 그때 Future 가 완료된다(접속이 끊겼으면 실패). 클라이언트 동기화는 다음 틱 창 broadcastChanges.
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
| `EntityDamageCalculateEvent` | X | 맞는 엔티티의 region | `LivingEntity.actuallyHurt`·`PlayerVitals.actuallyHurt` (체력을 깎기 직전, 피해 > 0 이고 구독자가 있을 때만). 방어력→저항→보호 인챈트→엔티티 고유(마녀)→흡수 체력 단계별로 input/output/줄인 양을 읽고, 줄이는 양·적용 여부·시작 값(base)을 바꾼다. 기본은 바닐라와 비트 단위로 같다. 상세는 아래 4.1 |
| `EntityDeathEvent` | X | 죽는 엔티티의 region | `LivingEntity.die` 의 `dead = true` 직후 |
| `EntitySpawnEvent` | O | 넣는 레벨의 region | `ChunkLevel.addFreshEntity` — 목록에 넣은 **뒤** 발화하고 취소하면 목록에서 빼고 레벨을 원래대로(추적 시작 전). 승객 묶음(`addFreshEntityWithPassengers`)은 하나라도 취소하면 전부 넣지 않는다(전부 아니면 전무). 청크 로드로 되살린 엔티티는 제외 |
| `PlayerMoveEvent` | O | 플레이어의 region | `Region.processMoves` 검증 통과 직후, CraftBukkit/Paper 간격으로만: 마지막으로 알린 자리에서 거리² > 1/256 이거나 \|Δyaw\|+\|Δpitch\| > 10° (시선만 돌려도 발화, 작은 떨림은 무시, 텔레포트 뒤에는 도착 자리부터 다시 센다). 취소 = 서버 위치·시선을 알린 자리로 되돌리고 검증 거부와 같은 방식으로 보정 텔레포트 `Player.correctTo` — `PlayerTeleportEvent` 를 거치지 않는다 |
| `PlayerTeleportEvent` | O | 호출한 스레드 | `Player.teleportTo` (같은 차원). 차원 이동은 다음 단계 |
| `PlayerInteractEvent` | O | 클릭한 칸의 region | `handleUseItemOn`(RIGHT_CLICK_BLOCK), `Region.handleUseItem`(RIGHT_CLICK_AIR). 구독자가 없으면 블록 상태 조회도 하지 않는다. 취소하면 클라이언트 예측을 되돌린다: 블록/이웃 상태 재전송(기존 sendBlockState) + 손 슬롯 `ContainerSetSlot` 재전송 |
| `PlayerDropItemEvent` | O | 플레이어의 region | `Region.playerDrop` 시작 (취소 = 인벤토리로 돌려줌) |
| `PlayerPickupItemEvent` | O | 아이템이 있는 region | `ItemEntity.playerTouch` 시작 |
| `InventoryClickEvent` | O | 메뉴 처리 스레드(블록 메뉴는 그 블록의 region) | `applyContainerClick` (취소 = 서버가 클릭을 재현하지 않고 전체 재전송) |
| `InventoryOpenEvent` | O | 연결 이벤트 루프 | 제작대·블록 메뉴 열기 직전 (취소 = 열지 않음) |
| `InventoryCloseEvent` | X | 닫기를 처리하는 스레드 | `doCloseContainer` |
| `ChunkLoadEvent` | X | 그 청크의 region | `Region.finishLoad` 설치 직후 |
| `ChunkUnloadEvent` | X | 그 청크의 region | `Region.evictIdleChunks` 에서 내리기 직전 이후 |

공통 규칙: 서버는 `hasSubscribers(type)` 를 먼저 묻고 없으면 이벤트 객체·핸들을 만들지 않는다. `gameEvents()` 는 구독자가 없으면 정적 상수를 돌려주는 `GameEvents.NONE` 이라 호출 지점 비용은 가상 호출 한 번과 boolean 하나다.

### 4.1 피해 계산 단계 (`EntityDamageCalculateEvent`)

```
base ─ARMOR→ ─RESISTANCE→ ─ENCHANTMENT→ ─ENTITY→ ─ABSORPTION→ finalDamage(체력에서 깎이는 값)
```
- `EntityDamageEvent`(취소·원본 값 수정)는 그대로다. 그 뒤, 무적 시간·난이도 배율·방패를 거친 값이 `base` 로 들어와 이 이벤트가 불린다. 방패가 전부 막아 피해가 0 이거나 늑대 갑옷이 대신 받는 경우는 불리지 않는다.
- 단계마다 `input`·`output`·`reduction`(= input − output)·`applies`(바닐라 조건: bypasses_armor·bypasses_effects·bypasses_resistance·bypasses_enchantments 태그, 저항 효과·보호 인챈트 유무)를 읽는다. `ENTITY` 는 엔티티 고유 경감(1.21.1 은 마녀의 마법 85% 감소), `ABSORPTION` 의 reduction 이 흡수 체력에서 빠지는 양이다.
- 바꾸기: `setBase`(모든 단계 다시 계산), `setReduction`(그 단계가 줄이는 양을 고정), `scaleReduction`(바닐라 양에 배율), `setApplied`/`skip`(적용 여부 강제 — 바닐라가 건너뛰는 피해에 방어력을 쳐 줄 수도 있다), `reset`. 바꾼 단계 뒤는 새 input 으로 바닐라 식을 다시 계산한다. 흡수 단계는 가진 흡수 체력을 넘게 줄이지 못한다.
- 아무것도 안 바꾸면 바닐라 경로와 **비트 단위로 같다**(시험 `DamageStagesTest` 가 피해 종류·양 조합으로 비교). 구독자가 없으면 규칙 객체도 만들지 않는다.
- 방어구 내구도는 이벤트 전에 이미 닳았다(바닐라 순서 — 방어력은 닳은 뒤 값). `base` 를 바꿔도 내구도 소모는 안 바뀐다. 플레이어는 이 이벤트가 `PlayerVitals` 잠금 안에서 불리므로 처리기에서 오래 걸리는 일을 하지 말 것.
- 버전 중립: 단계 이름은 개념이고 식은 서버가 넘기는 `DamageRules` 가 가진다. 마인크래프트 버전이 바뀌어 식이 달라져도 API 는 그대로다.
- 시험: `EntityDamageCalculateEventTest`(api 8), `DamageStagesTest`(server 8), `EntityWorldApiTest.damageCalculateEventExposesStagesAndAppliesChanges`, 봇 `tools/bot-test/round9/damage.js`(8).

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

후속 검토(2026-11-02)로 해결한 것: 이동 취소 보정 어긋남(B1), 스폰 취소 후 레벨/목록 잔재와 승객 부분 추가(B2), 플레이어 인벤토리 쓰기 소유 스레드, Interact 취소 재동기화, 구독자 없을 때 인자 평가 비용, 서버 스레드 `get()` 가드.
남은 한계: `PlayerMoveEvent` 취소 직후 서버가 보정 ack 를 기다리는 동안(moveRejectHold) 도착한 이동 패킷은 버려진다(검증 거부와 같은 동작). 가드는 스레드 이름 규약에 기대므로 이름을 바꾸면 `RegionFuture.onServerThread` 도 고쳐야 한다. `CompletableFuture` 를 직접 만든 사용자 코드(`new CompletableFuture`)의 `get()` 은 막지 못한다.

다음 단계(미구현):
1. `EntityScheduler`(엔티티를 따라가는 스케줄러) — 지금은 핸들 시점 위치의 region 스케줄러.
2. 엔티티 조회·스폰 API(`World.spawn`, `World.entities(region)`), 엔티티 AI/속성/효과/탑승.
3. `LivingEntity` 의 `Player` 외 구현이 `damage()`/`heal()`/`getEquipment()` 를 갖게 하기. `Mob`, `Item`(드롭 엔티티) 타입.
4. 블록 엔티티·상자 컨테이너를 `Inventory` 로 노출, `InventoryView`/`InventoryHolder`, 클릭 후 결과 아이템 수정.
5. ~~`EntityDamageEvent` 최종 피해(방어/인챈트 적용 후)~~ — `EntityDamageCalculateEvent`(단계별 계산, 2026-11-06)로 해결. `DamageCause` 별 하위 이벤트(`EntityDamageByEntityEvent` 등)는 남음(계산 이벤트에 `attacker()`·`directEntity()` 가 있다).
6. `PlayerTeleportEvent` 차원 이동 경로·`TeleportCause` 구분, 플레이어 핸들 `Player.world()` 정확한 차원(지금은 `Location.world` 사용).
7. `InventoryOpenEvent` 를 상자·엔티티 컨테이너·상인·말 인벤토리 경로에도 연결.
8. `PlayerInteractEvent` 의 LEFT_CLICK, 엔티티 상호작용(`PlayerInteractEntityEvent`).
9. `PersistentDataContainer`(원칙 7), `Chunk` 객체(`World.chunkAt`), `ChunkLoadEvent.isNewChunk` 의 정확한 판정.
10. 월드 쓰기: `setBlock` 의 `BlockPlaceEvent/BlockBreakEvent` 우회 여부 옵션, 블록 엔티티 데이터.
11. 기존 `BlockBreakEvent/BlockPlaceEvent` 의 하드코딩된 `"minecraft:overworld"` 를 실제 차원으로(이 작업에서 발견, 이 단계에서는 건드리지 않음).

## 8. 2단계 (2026-11-03)

원칙은 1장 그대로다. 새로 생긴 동기 접근은 없다 — 모든 살아 있는 상태는 소유 스레드에서 실행한 결과를 `RegionFuture` 로 받거나, 핸들을 만든 시점의 사본이다.

### 8.1 EntityScheduler (엔티티를 따라가는 스케줄러)
- `Entity.entityScheduler()` → `EntityScheduler { execute(task, retired, delay), runDelayed, runAtFixedRate, isRetired }` (Folia 와 같은 뜻).
- 서버 쪽은 엔티티 객체에 붙은 대기열 `server.entity.EntityTasks` 다. 엔티티가 다른 region 으로 넘어가면(`handOffEntities` → `adoptEntity`) 대기열도 같이 넘어가고, 그 엔티티를 가진 region 이 매 틱 `ChunkLevel.runEntityTasks()` 로 돌린다(엔티티 틱 청크 밖이어도, `/tick freeze` 중에도). 플레이어는 `Player.drainOwnerTasks()` 끝에서.
- 대기 작업이 있는 대기열이 서버에 하나도 없으면(`EntityTasks.anyPending()`) 레벨이 엔티티를 훑지 않는다 — 플러그인이 안 쓰면 비용 0. 쓰면 그 틱에 region 의 엔티티 수만큼 필드 읽기.
- 엔티티가 사라지면(`Entity.setRemoved` 의 모든 사유: 죽음·제거·청크와 함께 내림·차원 이동, 플레이어는 `markDisconnected`) 남은 작업 대신 `retired` 를 한 번 부른다 — **그 엔티티를 내린 스레드에서**(플레이어는 접속 종료를 처리한 스레드). retire 뒤 예약은 거부(false/null). 대기열은 retire 때 엔티티 참조를 놓는다.
- 지연은 엔티티가 있는 region 의 틱. 0 과 1 은 다음 틱. 반복은 실행이 끝난 뒤 다음을 잰다. 예외는 로그만 남기고 반복은 이어진다.

### 8.2 영속 데이터 (PersistentDataContainer)
- API: `api.persistence.{PersistentDataType, PersistentDataView, PersistentDataContainer}`. 키는 `namespace:path`(Bukkit `NamespacedKey` 규칙). 종류: BYTE·SHORT·INTEGER·LONG·FLOAT·DOUBLE·STRING·BYTE/INTEGER/LONG_ARRAY·BOOLEAN(byte 0/1, Bukkit 과 같은 저장 꼴)·TAG_CONTAINER.
- 저장 위치는 Paper 와 같다: 엔티티 NBT `BukkitValues`, 청크 NBT `ChunkBukkitValues`, 플레이어 파일 `BukkitValues`. 그래서 Paper 월드의 값을 그대로 읽고, Paper 로 돌아가도 남는다. 모르는 꼴의 값(Paper 플러그인이 쓴 목록 등)은 건드리지 않고 보존한다.
- 읽기: `Entity.persistentData()` 는 **핸들 시점 사본**(플레이어는 지금 값의 사본). `Chunk.persistentData()` 는 Future.
- 쓰기: `editPersistentData(Consumer)` — 엔티티는 EntityScheduler 대기열로 엔티티를 가진 region 에서(넘어가도 따라간다), 청크는 그 청크 region 에서, 플레이어는 `runOnOwner`. 콜백이 받은 컨테이너를 밖으로 가지고 나가지 않는다. 청크는 고친 뒤 저장 대상으로 표시하고, 비면 키를 지운다.
- 플레이어 영속 데이터는 연결 스레드가 저장 스냅샷을 뜨므로 `Player.withPersistentData` 모니터로 보호한다(엔티티·청크는 소유 스레드 전용이라 잠금 없음).
- 아이템의 영속 데이터(`PublicBukkitValues`)는 아직 없다(`ItemStack.components` 의 `minecraft:custom_data` SNBT 를 직접 다루면 된다).

### 8.3 Chunk·엔티티 조회·스폰
- `World.chunkAt(x, z)` → `Chunk`: 좌표뿐인 불변 핸들(청크를 로드하지 않는다). `isLoaded()`(최근 값), `scheduler()`, `entities()`(플레이어 포함), 영속 데이터. 로드되지 않은 청크는 빈 값/false 로 끝나고 청크를 만들지 않는다. `ChunkLoadEvent/ChunkUnloadEvent.chunk()` 편의.
- `World.isChunkLoaded(x, z)`.
- `World.spawnEntity(type, Location)` — `/summon` 과 같은 경로(`Entities.load` + 몹 `finalizeSpawn`(COMMAND)). Bukkit `spawnEntity` 도 COMMAND 로 finalizeSpawn 한다. `EntitySpawnEvent` 취소 → null. 모르는 종류·플레이어·낚시찌 → IllegalArgumentException, 청크 미로드 → IllegalStateException(청크를 만들지 않는다).
- `World.nearbyEntities(center, radius)` — center 칸 region 에서 상자 질의 후 거리 제곱 비교, 플레이어 포함. **그 region 밖(다른 region 이 가진 청크)의 엔티티는 빠진다**(다른 region 을 동기로 읽지 않는다는 원칙). radius 0..128.
- UUID 로 서버 전체에서 엔티티 찾기(`World.entity(UUID)`)는 아직 없다 — 전역 색인이 없다.

### 8.4 블록·엔티티 칸, 상인
- `World.inventoryAt(BlockPos)` → `Optional<Inventory>`: 용기 블록 엔티티(`ContainerBlockEntity` — 상자·통·셜커 상자·깔때기·발사기·공급기·화로류·양조기·제작기·조각된 책장·장식 단지; 모닥불은 Bukkit 처럼 제외). 쓰기는 `MenuBlockContext.runIn` 안에서 `BlockEntityContainer.set` — 메뉴 클릭과 같은 부수 효과(저장 표시·비교기·제작기 잠금 풀기). 큰 상자는 반쪽만. 그 사이 블록 엔티티가 바뀌거나 부서졌으면 IllegalStateException. 열지 않은 전리품 상자는 채우지 않는다(빈 칸으로 보인다).
- `Entity.container()` → `Optional<Inventory>`: 상자 배·상자/깔때기 수레(`ContainerEntity`, `EntityContainer.set` 의 칸 최대 개수 자르기 포함), 주민·알레이·피글린 소지품(`InventoryCarrier`), 플레이어는 `inventory()`. 엔티티 대기열로 실행되므로 엔티티를 따라가고, 다음 틱에 완료된다.
- `Merchant extends LivingEntity`(주민·떠돌이 상인 핸들): `trades()`/`setTrades(List<MerchantRecipe>)`/`isTrading()`. `MerchantRecipe` = 재료 1·2, 결과, uses/maxUses, xp, priceMultiplier, rewardExp. 재료는 바닐라 `ItemCost` 로 바뀐다 — 넣은 컴포넌트가 조건이 되고 지운 컴포넌트는 조건이 아니다. 수요(demand)·특가(specialPrice)는 0 으로 새로 시작한다. `trades()` 는 바닐라 `getOffers` 처럼 거래가 아직 없는 주민이면 거래표를 만든다(난수 소비, Bukkit 도 같다). `setTrades` 는 그 경로를 타지 않는다. 열려 있는 거래 창은 갱신하지 않는다.
- 말·당나귀 인벤토리, 엔더 상자, 큰 상자 합친 보기, `InventoryHolder`/`InventoryView` 는 아직 없다.

### 8.5 이벤트
| 이벤트 | 취소 | 발화 스레드 | 서버 발화 지점 |
|---|---|---|---|
| `PlayerInteractEvent` LEFT_CLICK_BLOCK | O | 플레이어의 region | `BlockBreaking.handleAction` START — 거리·높이·스폰 보호 검사 뒤, 크리에이티브 즉시 파괴 앞(Paper `ServerPlayerGameMode` 와 같은 자리). 취소 = 부수기 시작 안 함 + 블록 재전송 |
| `PlayerInteractEvent` LEFT_CLICK_AIR | O | 플레이어의 region | swing 패킷(`Region.swingLeftClick`) — 눈에서 4.5칸(크리에이티브 5) 안에 블록(OUTLINE, 유체 무시)도 집을 수 있는 엔티티·다른 플레이어도 없을 때(Spigot handleAnimate 의 rayTrace). 구독자가 없으면 시선 추적도 안 한다. 취소해도 휘두르기는 보인다 |
| `PlayerInteractEntityEvent` | O | 대상 엔티티의 region | `handleInteractEntity` 의 INTERACT(0), 거리 검사 뒤 모든 상호작용 앞. 취소 = 아무 상호작용 없음 + 손 슬롯 재전송 |

`Action.isLeftClick()/isRightClick()` 편의를 더했다.

### 8.6 7장 "다음 단계" 처리 상태
1 EntityScheduler — **완료**(8.1). 2 엔티티 조회·스폰 — **스폰·주변·청크별 조회 완료**, UUID 전역 조회·AI/속성/효과/탑승은 남음. 3 `damage()/heal()/getEquipment()`·`Mob`·`Item` 타입 — 남음. 4 블록·엔티티 컨테이너 Inventory — **완료**(8.4), `InventoryView/Holder`·클릭 후 결과 수정은 남음. 5 최종 피해·`DamageCause` 하위 이벤트 — 남음. 6 텔레포트 원인·차원 — 남음. 7 `InventoryOpenEvent` 경로 확장 — 남음. 8 LEFT_CLICK·`PlayerInteractEntityEvent` — **완료**(8.5). 9 `PersistentDataContainer`·`Chunk` — **완료**(8.2·8.3), `ChunkLoadEvent.isNewChunk` 는 남음. 10·11 — 남음. 그 밖에 `PlayerMoveEvent` 의 회전만 바뀐 이동은 이제 CraftBukkit 규칙(아래 표)대로 발화한다.


## 9. NBT 데이터 API·내부 접근 다리 (2026-11-06)

`Entity.readNbt/editNbt/mergeNbt`, `World.blockEntityNbt/editBlockEntityNbt/mergeBlockEntityNbt`(`/data` 와 같은 경로, 버전 중립 `org.domi.furnace.api.nbt` 타입)와 서버 내부 객체 다리 `org.domi.furnace.api.internal.Internals` 는 `docs/api/internals.md` 에 정리했다. 스레드 규칙은 이 문서 1·2장을 그대로 따른다(`*Now` 는 소유 스레드만, 나머지는 Future).
