# H4: helper 를 쓰는 플러그인의 다음 장벽 — 2026-11-08

PR #148 로 helper 5.6.14 가 켜졌다. 켜지는 것과 **helper 를 쓰는 플러그인이 helper 기능을 실제로 부를 수 있는 것**은 다르다. 그래서 helper 를
쓰는 플러그인을 흉내 낸 측정 도구를 만들고, 막힌 기능을 차례로 채웠다.

## 측정 방법

`./gradlew :furnace-compat-paper:helperProbe -PlibraryCache=<폴더>` (캐시 만드는 법은 `compat-h4-simplepluginmanager-2026-11-08.md`).
`HelperUsageProbe` 가 플러그인 "HelperUser"(`depend: [helper]`, `ExtendedJavaPlugin`)를 helper jar 에 맞춰 컴파일해 시험 서버에 올리고,
플레이어 "Steve" 를 접속시켜 `/hprobe` 를 친다. 명령은 helper `Schedulers.sync()` 안에서 기능을 하나씩 부르고 `[hu] 기능=ok|FAIL <처음 막힌 예외>` 를
찍는다. 마지막에 메뉴 첫 칸을 클라이언트 패킷(container_click)으로 누른다. 시험(CI)이 아니라 측정 도구다 — helper 가 Guava 를 써서 라이브러리 캐시가 있어야 켜진다.

## 결과

| 기능 | 고치기 전 | 고친 뒤 |
|---|---|---|
| `Events.subscribe` / `Commands.create` / `Schedulers.sync().runLater` / `Schedulers.async()` | ok | ok |
| `Metadata.provideForPlayer` / `Players.msg` / `Cooldown` | ok | ok |
| `ItemStackBuilder` | `NoSuchFieldError ItemFlag.HIDE_POTION_EFFECTS` | ok |
| `InventorySerialization` | `ClassNotFoundException BukkitObjectOutputStream` | ok |
| 보스바(`BukkitBossBarFactory`) | `ClassNotFoundException BarColor` | ok |
| 메뉴(`Gui`) 열기·클릭 | `UnsupportedFeature Bukkit.createInventory` | ok(다음 틱 `isValid()=true`, 첫 칸 클릭 처리기까지 불림) |
| 홀로그램(`Hologram.create().spawn()`) | `ClassNotFoundException MetadataValue` | ok(`isSpawned()=true`) — 상세·Paper 차이 16개는 `compat-hologram-entities-2026-11-08.md` |

helper 의 스코어보드는 ProtocolLib 이 있어야 해서(`PacketScoreboard`) helper 만으로는 장벽이 아니다 — `DisplaySlot` 열거형만 참조한다.
`Gui#open()` 은 1.16 이후 한 틱 뒤에 열린다(helper 코드). 그래서 `isValid()` 는 연 직후가 아니라 다음 틱에 잰다(Paper 에서도 같다).

## 무엇을 넣었나

### 메뉴 — `Bukkit.createInventory` 와 인벤토리 이벤트

| Bukkit | 내용 |
|---|---|
| `Server/Bukkit#createInventory(owner, size[, title])` | 9..54 의 9 배수(아니면 Paper 와 같은 `IllegalArgumentException("Size for custom inventory must be a multiple of 9 between 9 and 54 slots (got N)")`). 기본 제목 "Chest" |
| `createInventory(owner, type[, title])` | 상자 모양(CHEST·ENDER_CHEST·BARREL·SHULKER_BOX → 3줄), DISPENSER·DROPPER, HOPPER. 만들 수 없는 종류는 `IllegalArgumentException("Inventory Type '…' is not creatable")`, 나머지(ANVIL 등)는 `UnsupportedFeature` |
| `HumanEntity#openInventory(Inventory)` · `closeInventory()` · `getOpenInventory()` | 플러그인이 만든 인벤토리만 열 수 있다 |
| `Inventory#getViewers()` | 만든 인벤토리는 지금 보는 사람, 나머지는 빈 목록 |
| `InventoryView` | `getTopInventory`·`getBottomInventory`·`getType`·`getItem/setItem`·`convertSlot`·`getSlotType`·`countSlots`·`getTitle`·`getOriginalTitle`·`setTitle`·`close` (CraftBukkit 칸 번호 규칙) |
| `InventoryClickEvent` · `InventoryDragEvent` · `InventoryOpenEvent` · `InventoryCloseEvent` | Furnace 인벤토리 이벤트에서 다리. `ClickType`·`InventoryAction` 은 CraftBukkit `handleContainerClick` 의 고르기 규칙(창 밖 클릭도 LEFT/RIGHT), 끌어 나누기는 바닐라 `AbstractContainerMenu` 의 quick_craft 단계(둘 이상 칸일 때 끝에서 drag 이벤트 하나, quick_craft 단계는 click 이벤트를 내지 않는다). **한 칸 끌기**는 바닐라가 그 칸 PICKUP 으로 바꾸는데, 서버가 Paper 1.21.1(drag 수정 패치)처럼 그 클릭을 PICKUP 클릭으로 처음부터 다시 처리해 `InventoryClickEvent`(LEFT/RIGHT)가 나고 취소된다 |
| `InventoryType`(+`SlotType`)·`ClickType`·`InventoryAction`·`DragType`·`InventoryInteractEvent` | Paper 1.21.1 상수 |

### 보스바 — `Bukkit.createBossBar`

`CraftBossBar` 가 Furnace 보스바 위에 있다. 색·모양(SOLID↔PROGRESS, SEGMENTED_n↔NOTCHED_n)·효과(DARKEN_SKY↔DARKEN_SCREEN, CREATE_FOG↔CREATE_WORLD_FOG),
`setProgress` 범위 밖은 `IllegalArgumentException("Progress must be between 0.0 and 1.0 (x)")`, `addPlayer` 는 접속 중이 아니면
`IllegalArgumentException("player is not fully connected (wait for PlayerJoinEvent)")`, `setVisible(false)` 는 목록을 두고 화면에서만 뺀다.

### 옛 ItemFlag 이름

Paper 는 1.20.5 에서 이름이 바뀐 `ItemFlag.HIDE_POTION_EFFECTS` 를 플러그인 바이트코드에서 `HIDE_ADDITIONAL_TOOLTIP` 으로 바꿔 준다(FieldRename, 모든 api-version).
Furnace 는 NMS 변환기(`NmsClassTransformer`)에 같은 일을 붙였다: `GETSTATIC ItemFlag.HIDE_POTION_EFFECTS` 의 이름을 바꾸고, `ItemFlag.valueOf(String)` 은
`ApiRenames.valueOfItemFlag` 로 돌려 옛 이름도 받는다. `HIDE_STORED_ENCHANTS` 상수와 옛 별칭 `HIDE_ITEM_SPECIFICS` 도 더했다.

### 죽음 이벤트

Furnace `PlayerDeathEvent` → Bukkit `PlayerDeathEvent`(Bukkit 에서 `EntityDeathEvent` 와 처리기 목록을 함께 쓴다), 몹의 Furnace `EntityDeathEvent` → Bukkit `EntityDeathEvent`.
처리기가 바꾼 죽음 메시지·`keepInventory`·`keepLevel` 은 되쓴다.

### 아이템 직렬화

`BukkitObjectOutputStream`·`BukkitObjectInputStream`·`ConfigurationSerialization`·`ItemStack#serialize/deserialize`·YAML `getItemStack` — 상세와 Paper 와 다른 점은
`compat-item-serialization-2026-11-08.md`.

## Furnace API 에 더한 것

- `InventoryEvent#menu()`(클릭·열기·닫기 이벤트): 그 창이 플러그인 메뉴면 그 `Menu`.
- `Menu#storage(true)`·`isStorage()`: **보관함 메뉴.** 보통 메뉴는 보는 사람마다 사본이라 칸을 옮기는 클릭을 허용하면 사람마다 결과가 갈린다(복사 위험).
  보관함 메뉴는 클릭을 **모든 보관함 메뉴가 함께 쓰는 자물쇠 하나**로 줄 세우고(메뉴마다 두면 서로 상대 인벤토리에 쓰는 두 메뉴의 클릭이 연결 스레드끼리 교착한다 — 독립 검토 재현), 클릭 직전에 메뉴 내용으로 맞춘 뒤 클릭으로 바뀐 칸만 메뉴에 되쓴다 — 모두가 같은 칸을 쓴다(Bukkit 가상 인벤토리와 같다).
- `Player#closeContainer()`(어떤 창이든 닫기), `Player#openContainerType()`(지금 열린 창 종류 키).

## Paper 1.21.1 과 다른 점 (관측 가능한 것)

1. `openInventory` 는 연결 스레드에서 이뤄져 `InventoryOpenEvent` 가 취소돼도 창 객체를 돌려준다(Paper 는 null). 연 직후 같은 틱에 `getOpenInventory()` 는 아직 이전 창일 수 있다.
   클릭 처리기 안에서 부른 `closeInventory`·`openInventory` 도 클릭 뒤에 이뤄져, 취소하지 않은 그 클릭은 먼저 적용된다(Paper 는 처리기가 창을 바꾸면 클릭을 적용하지 않고 화면을 다시 보낸다 — 창을 넘기는 메뉴는 클릭을 취소할 것).
2. 클릭 처리기 안에서 `Inventory#setItem` 으로 바꾼 칸은 클릭이 끝난 뒤에 적용된다(Paper 는 곧바로). 취소하지 않은 클릭이 같은 칸을 바꾸면 클릭 결과가 이긴다.
3. `InventoryCloseEvent` 가 불릴 때 닫는 사람은 이미 `getViewers()` 에서 빠져 있다(Paper 는 아직 들어 있다). `getReason()` 은 늘 `UNKNOWN`.
4. 플러그인 인벤토리가 아닌 창(상자·화로 블록 등)의 위쪽 칸은 이벤트 순간의 읽기 전용 사본이다. 쓰기는 `UnsupportedFeature`, `getHolder()` 는 null, 상자 모양 창의 종류는 모두 `CHEST`, 제목은 기본 제목. 이벤트 밖 `getOpenInventory()` 로는 내용을 읽을 수 없다.
5. 블록·엔티티 보관함을 `openInventory` 로 여는 것은 `UnsupportedFeature`. `Inventory#getViewers()` 는 플러그인 인벤토리만 안다.
6. `InventoryView#setCursor`·`InventoryDragEvent#setCursor` 는 `UnsupportedFeature`(Furnace 이벤트가 커서를 되쓰지 않는다). `getCursor` 는 열기 이벤트에서 빈 스택, 닫기 이벤트와 이벤트 밖 `getOpenInventory()` 에서 `UnsupportedFeature`.
7. 칸이 그 아이템을 받거나 내줄 수 있는지(`mayPlace`·`mayPickup`)는 Furnace 이벤트가 알리지 않아 "비었거나 같은 아이템"으로 본다 — 끌어 나누기의 칸 목록과 클릭의 `InventoryAction`(갑옷 칸·결과 칸 등)이 Paper 와 다를 수 있다. `COLLECT_TO_CURSOR` 는 창에 보이는 칸만 보고 정한다(Paper 는 갑옷·왼손 칸도 본다). 끌어 나누기 도중 다른 종류 클릭의 action 은 Paper 의 `NOTHING` 대신 보통 규칙을 따른다.
8. 제작 결과 칸 꺼내기(Bukkit `CraftItemEvent`)는 다리가 없다 — 그 클릭은 `InventoryClickEvent` 도 내지 않는다.
9. `EntityDeathEvent`·`PlayerDeathEvent` 모두 `getDrops`·`getDroppedExp`·`setDroppedExp`·취소가 `UnsupportedFeature`(Furnace 사망 이벤트가 싣지 않음). 흔한 `setKeepInventory(true); getDrops().clear();` 는 keepInventory 는 되지만 그 줄에서 던진다 — 후속으로 Furnace 사망 이벤트에 떨어뜨릴 목록을 실어야 한다. `PlayerDeathEvent` 의 새 경험치·레벨 메서드도 같다.
10. 보스바 보는 사람 목록은 접속이 끊겨도 저절로 비지 않는다(Paper 와 같다). 다시 접속한 플레이어는 새 객체라 다시 `addPlayer` 해야 한다.
11. `ItemFlag` 옛 이름 바꾸기는 NMS 변환기가 켜져 있을 때(`plugins.compat.nms.enabled`, 기본 켜짐)만 된다. `HIDE_STORED_ENCHANTS` 는 아이템 컴포넌트로 옮겨지지 않는다.
12. `KeyedBossBar`(`createBossBar(NamespacedKey, …)`·`getBossBars`)는 없다.
13. 관전자의 창 클릭은 Paper 처럼 취소된 `InventoryClickEvent` 를 내지 않고, 이벤트 없이 화면만 다시 보낸다.

## 시험

- `BukkitMenuPluginTest`: 진짜 플러그인 jar 가 만든 인벤토리를 열고, 클라이언트 클릭(취소·허용)·플레이어 칸 클릭·끌어 나누기·플러그인이 닫기를 거친다. 보스바·죽음 메시지 되쓰기.
- `StorageMenuSafetyTest`(독립 검토 재현 시험): 모든 클릭·끌기를 막는 GUI 에 한 칸 끌기로 아이템을 넣을 수 없고(PICKUP 클릭 이벤트로 취소), 서로 상대 인벤토리에 쓰는 두 메뉴의 동시 클릭이 교착하지 않는다.
- `ApiRenamePluginTest`: 옛 이름 `HIDE_POTION_EFFECTS` 로 컴파일된 jar 가 Paper 처럼 새 상수를 받는다(`valueOf` 포함).
- `PluginUiApiTest#storageMenuWritesClickResultsBackToTheMenu`: 보관함 메뉴는 클릭 결과가 메뉴로 돌아가고 보통 메뉴는 그대로.
- `EventBridgeTest`: 새 다리 다섯(클릭·끌기·열기·닫기·죽음)이 처리기에 닿는다. `InventoryAdapterTest#virtualInventoriesFollowPaperRules`.
