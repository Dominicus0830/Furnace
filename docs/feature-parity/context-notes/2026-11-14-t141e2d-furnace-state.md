# T-141 E2d: 화로 상태와 FurnaceInventory (2026-11-14)

PR #378 검토 R6(후속): Paper 의 `Furnace#getInventory()` 는 공변 `FurnaceInventory` 를 돌려준다. compat `Furnace` 가 표시 인터페이스라
Paper 로 컴파일한 플러그인이 `((Furnace) state).getInventory()` 를 부르면 NoSuchMethodError 였다(BrewingStand·Colorable 과 같은 뿌리).

## 한 일
- `org.bukkit.block.Furnace`(손으로 씀, 화로·용광로·훈연기 공통): getBurnTime/setBurnTime(short), getCookTime/setCookTime(short),
  getCookTimeTotal/setCookTimeTotal(int), getCookSpeedMultiplier/setCookSpeedMultiplier, getRecipeUsedCount·hasRecipeUsedCount(NamespacedKey),
  getInventory·getSnapshotInventory → FurnaceInventory.
- `CraftFurnace`(손으로 씀): 사본 NBT `BurnTime`·`CookTime`·`CookTimeTotal`(바닐라 short 저장)을 int 로 들고 update 때 적는다.
  setBurnTime 은 Paper(SPIGOT-844)처럼 블록 상태 `lit` 도 burnTime>0 으로 맞춘다 — update 때 블록과 함께 실린다.
  RecipesUsed 는 바닐라 저장 꼴(레시피 키 → 수) 그대로 읽는다.
- `FurnaceInventory`(새): 0 굽는 것·1 연료·2 결과, isFuel·canSmelt(Paper 추가분), getHolder → Furnace.
  살아 있는 인벤토리는 `BlockInventories.Furnace`(화로·용광로·훈연기, 인벤토리 종류로 레시피 종류를 고른다).
  - isFuel: furnace-api `GameRegistries#fuelBurnTime(item)`(새) → 서버 `FuelRegistry`(바닐라 AbstractFurnaceBlockEntity 연료 표).
  - canSmelt: furnace-api `RecipeRegistry#canCook(kind, item)`(새) → 서버 `RecipeManager#findCooking`. 서버 화로가 굽는 조회 그대로라 정의상 같은 결과다
    (Paper 는 RecipeManager.getRecipeFor). 첫 판은 레시피 표 전부를 API 객체로 바꿔 훑었다 — 호출마다 수천 개 변환이라 검토(R1)에서 바꿨다.
    레시피 데이터 없이 뜬 서버면 거짓.
- `CompatBlockData.of(api BlockData)` 를 공개했다(블록 상태 클래스가 다른 패키지에서 데이터를 고쳐 만든다).

## Paper 와 다른 점·아직 없는 것
- setCookSpeedMultiplier: 검사(음수·200 초과 문구)는 Paper 와 같고 그 뒤 UnsupportedFeature — Furnace 서버 화로가 굽기 속도 배율(Paper.CookSpeedMultiplier)을
  아직 모른다. getCookSpeedMultiplier 는 NBT 에 Paper.CookSpeedMultiplier 가 있으면 그 값, 없으면 1.0.
- getRecipesUsed·setRecipeUsedCount·setRecipesUsed: compat 에 `CookingRecipe` 형이 없어 아직 선언하지 않았다.
- 시험 한계: 시험 서버는 데이터 팩 없이 떠서 레시피 표가 없고(플러그인 레시피도 못 건다) 아이템 태그(ItemContext)가 걸리지 않는다.
  그래서 compat 시험에서 canSmelt 는 빈 아이템만, isFuel 은 표의 낱개 아이템(석탄)만 잰다. 레시피 일치(원목→숯 태그 재료·종류 구분·플러그인 레시피)는
  바닐라 데이터 팩을 싣는 서버 시험 `PluginRecipeApiTest.canCookFollowsFurnaceLookup` 에서 잰다. 태그 연료(판자)는 실서버에서 볼 것(가정: 서버 표 그대로).

## 후속(#379 검토)
- 창 이벤트의 위쪽 인벤토리: `CompatInventories.typedView` 는 BREWING 만 BrewerInventory 로 만들고 화로류 창은 SnapshotInventory 라
  `instanceof FurnaceInventory` 가 거짓이다(Paper 는 CraftInventoryFurnace). EventInventories 에 화로 갈래를 넣을 것.
- setCookSpeedMultiplier 실제 구현: Paper 패치는 BE 필드 + NBT `Paper.CookSpeedMultiplier`(double) 읽기·저장 + `ceil(레시피 시간 / 배율)` + 완료 비교 `>=`.
  서버 FurnaceBlockEntity 에 같은 네 곳을 넣으면 된다(바닐라 화로는 키가 없어 1.0 → 관측 동작 그대로). 배율 0 은 Paper 에서 영영 안 구워진다. 지금은
  서버가 그 키를 저장하지 않아 Paper 월드에서 가져온 배율도 버려진다.
