# T-141 E2e: 굽기 속도 배율과 창 이벤트 화로 인벤토리 (2026-11-14)

PR #379 검토 후속 R3·R4.

## 굽기 속도 배율(Paper Implement-furnace-cook-speed-multiplier-API)
- 서버 `FurnaceBlockEntity.cookSpeedMultiplier`(기본 1.0): 굽는 총 시간 = `ceil(레시피 시간(없으면 200) / 배율)`. 1.0 이면 바닐라 값 그대로,
  0 이면 Integer.MAX_VALUE 라 영영 안 구워진다(Paper 와 같다).
- NBT `Paper.CookSpeedMultiplier`(double): 있으면 읽는다(Paper 월드를 가져오면 배율이 남는다). 저장은 **1.0 이 아닐 때만** — Paper 는 늘 적지만
  바닐라 저장물과 바이트까지 같게 하려고 다르게 뒀다(바닐라 화로에는 관측 차이 없음).
- 굽기 완료 비교: Paper 는 `==` 를 `>=` 로 바꿨다(배율을 바꾸면 총 시간이 진행보다 작아질 수 있다). Furnace 는 배율이 1.0 이 아닐 때만 `>`
  도 완료로 본다. 배율 1.0 에서 /data 로 총 시간을 진행보다 작게 적은 경우 바닐라처럼 멈춘다(Paper 는 곧 굽는다) — 바닐라를 따른 차이.
- compat `CraftFurnace.setCookSpeedMultiplier`: Paper 처럼 검사 뒤 배율을 바꾸고 사본의 총 시간을 다시 잰다. 재료는 사본 NBT Items 의 0 번 칸
  (아이템 종류만 — 바닐라 Ingredient 도 종류만 본다), 레시피 시간은 furnace-api `RecipeRegistry#cookingTime(kind, item)`(새, 서버 findCooking).
  update 때 배율이 1.0 이 아니면 `Paper.CookSpeedMultiplier` 를 적고 1.0 이면 지운다.

## 창 이벤트의 위쪽 화로 인벤토리
- `CompatInventories.typedView`·`InventoryResultMappings.container` 가 화로·용광로·훈연기면 `EventInventories.Furnace`(FurnaceInventory, 사본) 를 준다.
  Paper 는 InventoryOpenEvent·InventoryClickEvent 의 위쪽 인벤토리가 CraftInventoryFurnace 라 플러그인이 `instanceof FurnaceInventory` 로 가른다.
  getHolder 는 양조대 사본(Brewer)과 같이 null(이벤트 사본엔 블록 상태가 없다 — 가정, 지금까지와 같은 한계).
