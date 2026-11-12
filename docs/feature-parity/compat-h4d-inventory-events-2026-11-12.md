# H4 묶음 D — 인벤토리 이벤트 (2026-11-12)

H4 측정(`compat-h4-2026-11-07.md`)의 묶음 D 는 "인벤토리 이벤트·메뉴"였다. 그 가운데 창(`InventoryView`)·클릭·끌기·열기·닫기·`createInventory` 는
helper 장벽 묶음(PR #161, `compat-h4-helper-barriers-2026-11-08.md`)에서 먼저 들어갔다. 이 문서는 남은 부분이다: **결과를 정하는 인벤토리 이벤트**와
**닫힌 까닭**. Furnace 서버 이벤트는 이미 있었고(3단계 둘째·셋째 묶음), Bukkit 쪽 클래스와 다리가 없었다.

## 무엇을 넣었나

| Bukkit 이벤트 | Furnace 이벤트 | 처리기가 바꿀 수 있는 것 |
|---|---|---|
| `CraftItemEvent` | `CraftItemEvent` | 취소 |
| `PrepareItemCraftEvent` | `PrepareItemCraftEvent` | 결과 칸(`getInventory().setResult`) |
| `PrepareAnvilEvent`(+ `PrepareInventoryResultEvent`, Paper `PrepareResultEvent`) | `PrepareAnvilEvent` | 결과(`setResult`), 비용(`getView().setRepairCost`, 옛 `AnvilInventory#setRepairCost`) |
| `enchantment.PrepareItemEnchantEvent` | `PrepareItemEnchantEvent` | 제안 세 칸(`getOffers()[i]`, `EnchantmentView#setOffers`), 취소 |
| `enchantment.EnchantItemEvent` | `EnchantItemEvent` | 부여할 마법(`getEnchantsToAdd`), 비용, 취소 |
| `BrewEvent` | `BrewEvent` | 결과 목록(`getResults`), 취소 |
| `BrewingStandFuelEvent` | `BrewingStandFuelEvent` | 연료 횟수, 소모 여부, 취소 |
| `InventoryMoveItemEvent` | `InventoryMoveItemEvent` | 옮길 아이템(`setItem`), 취소 |
| `InventoryCloseEvent#getReason()` | `InventoryCloseEvent#reason()`(새) | — |

같이 더한 Bukkit 타입: `Recipe`, `CraftingInventory`, `AnvilInventory`, `BrewerInventory`, `EnchantingInventory`, `inventory.view.AnvilView`·`EnchantmentView`,
`EnchantmentOffer`, `block.BrewingStand`(서명만), `Inventory#getLocation()`.

처리기 목록은 Paper 1.21.1 과 같다:
- `CraftItemEvent` 는 자기 `HandlerList` 가 없어 **`InventoryClickEvent` 처리기도 `CraftItemEvent` 를 받는다**(PR #161 의 주석은 반대로 적혀 있었다 — Paper 원문 확인).
- `PrepareAnvilEvent`·`PrepareResultEvent` 는 `PrepareInventoryResultEvent` 의 목록을 쓴다.

창 종류별 위쪽 인벤토리: 이벤트가 주는 제작대·2×2 제작 창은 늘 `CraftingInventory`, 모루는 `AnvilInventory` + `AnvilView`, 마법 부여대는
`EnchantingInventory` + `EnchantmentView`, 양조대는 `BrewerInventory` 다(클릭·열기·닫기 이벤트와 이벤트 밖 `getOpenInventory()` 포함) — 플러그인이
`instanceof` 로 가르는 쓰임이 Paper 와 같게 된다.

### Furnace 서버·API 에 더한 것
- `InventoryCloseEvent.Reason`·`reason()`: 클라이언트 닫기 `PLAYER`, 다른 창을 열며 `OPEN_NEW`, 플러그인 `closeMenu/closeContainer` `PLUGIN`, 플러그인
  텔레포트 `TELEPORT`, 죽음 `DEATH`, 접속 끊김 `DISCONNECT`, `stillValid` 실패(상자 사라짐·멀어짐) `CANT_USE`, 창이 매인 블록의 청크가 없음 `UNLOADED`.
  서버 안의 창 닫기 경로(`ConnectionHandler#closeContainer/doCloseContainer`, `Player.InventoryHost#closeContainer`)가 까닭을 함께 넘긴다.
- 닫기 이벤트 동안 닫는 사람은 아직 그 플러그인 메뉴의 보는 사람이다(Paper 와 같다) — 메뉴 세션을 이벤트 **뒤에** 끝낸다. 그래서 Furnace `Menu#onClose`
  처리기는 이제 `InventoryCloseEvent` 뒤에 불린다.
- `BrewEvent#results()`: 서버가 양조 결과를 먼저 계산해 이벤트에 싣고, 처리기가 고친 목록이 병 칸 0~2 에 들어간다(목록이 짧으면 남는 칸은 빈다 — Paper 와 같다).
- `EnchantItemEvent#enchantmentHint()`·`levelHint()`: 눌린 제안의 단서(마법 부여대가 보여 준 마법·레벨).

## Paper 1.21.1 과 다른 점 (관측 가능한 것)

1. 이벤트의 위쪽 인벤토리는 그 순간의 **읽기 전용 사본**이다(PR #161 과 같은 원칙). 쓸 수 있는 칸은 `PrepareItemCraftEvent` 의 결과 칸 하나뿐이고,
   `CraftingInventory#setMatrix`·`BrewEvent#getContents()` 쓰기·`InventoryMoveItemEvent` 의 두 용기 쓰기는 `UnsupportedFeature` 다.
   `getHolder()` 는 null(블록 상태 구현이 없다). `getLocation()` 은 깔때기 이벤트·양조 이벤트에서만 안다(블록이면 블록 좌표, 수레면 엔티티 위치).
2. `Recipe` 는 키(`Keyed#getKey`)와 결과만 안다 — `ShapedRecipe`·`ShapelessRecipe` 같은 하위 타입이 아니다. `PrepareItemCraftEvent#getRecipe().getResult()` 는
   그 이벤트 순간의 결과(데이터 팩 레시피의 기본 결과와 같다).
3. `CraftItemEvent` 는 결과 칸이 비어 있지 않고 레시피를 알 때만 온다. 결과 칸 더블 클릭(`PICKUP_ALL`)은 Paper 에서는 `CraftItemEvent` 지만 Furnace 에서는
   보통 `InventoryClickEvent` 다. 레시피를 모르면(Furnace `recipe()==null`) Paper 처럼 보통 클릭이다.
4. `AnvilView`: 재료 소모 개수(`getRepairItemCountCost`)는 모르고(`UnsupportedFeature`), 최대 비용은 바닐라 40 고정(다른 값으로 바꾸기·`bypassEnchantmentLevelRestriction(true)`
   는 `UnsupportedFeature`). 비용·이름은 `PrepareAnvilEvent` 안에서만 알고, 다른 이벤트의 모루 창에서 읽으면 `UnsupportedFeature`.
5. `EnchantmentView`: 마법 부여 씨앗은 읽기·쓰기 모두 `UnsupportedFeature`. 제안(`getOffers`)은 `PrepareItemEnchantEvent` 안에서만 알고, 그 이벤트의 배열 그대로다.
6. `EnchantItemEvent#setItem` 은 다른 아이템이면 `UnsupportedFeature`. Bukkit `Enchantment` 가 모르는 마법 부여(데이터 팩)는 `getEnchantsToAdd()` 에
   보이지 않지만 처리기가 맵을 바꿔도 Furnace 쪽에 남는다.
7. `PrepareItemEnchantEvent`: 단서 없이 비용만 있는 제안은 Bukkit 에서 null 이다(CraftBukkit 과 같다). 처리기가 그 칸을 바꾸지 않으면 Furnace 제안은 그대로 둔다.
8. 깔때기 이벤트의 주크박스·퇴비통은 Furnace 용기 키가 없어 종류가 `CHEST` 로 보인다.
9. 닫힌 까닭: 블록 창의 청크가 내려가 있을 때 `UNLOADED`, 블록이 사라지거나 멀어지면 `CANT_USE` 다. 바닐라 이동(포털 등)은 창을 닫지 않으므로 `TELEPORT` 는
   플러그인 텔레포트에서만 온다.
10. `InventoryView#setCursor` 등 PR #161 문서의 나머지 차이는 그대로다.

## 시험
- `InventoryResultEventsTest`(호환 계층): 이벤트마다 Bukkit 처리기가 받는 값(레시피 키·창 종류·결과 칸 규칙 `PICKUP_ALL/NOTHING`·제안·단서·결과 목록·용기 종류·위치)과
  되돌려 쓰기, 읽기 전용 사본 쓰기의 `UnsupportedFeature`, 닫힌 까닭.
- `EventBridgeTest#everyBridgedEventReachesTheBukkitHandler`: 새 다리 일곱이 표에 들어갔다.
- `BukkitMenuPluginTest`: 진짜 플러그인 jar 에서 플러그인 닫기 `PLUGIN`, 다시 열기 `OPEN_NEW`, 클라이언트 닫기 `PLAYER`, 닫는 동안 보는 사람 1명.
- `PluginApiFormEventsTest#brewEventResultsAreComputedAndWrittenBack`(서버): 양조 결과가 미리 계산돼 오고 고친 목록이 칸에 들어간다.
