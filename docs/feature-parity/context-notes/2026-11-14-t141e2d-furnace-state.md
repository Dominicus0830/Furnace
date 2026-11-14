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
  - canSmelt: `Furnace.recipes()` 의 그 종류 Cooking 레시피 가운데 재료(아이템·태그·묶음)가 맞는 것이 있는지. Paper 는 RecipeManager.getRecipeFor.
- `CompatBlockData.of(api BlockData)` 를 공개했다(블록 상태 클래스가 다른 패키지에서 데이터를 고쳐 만든다).

## Paper 와 다른 점·아직 없는 것
- setCookSpeedMultiplier: 검사(음수·200 초과 문구)는 Paper 와 같고 그 뒤 UnsupportedFeature — Furnace 서버 화로가 굽기 속도 배율(Paper.CookSpeedMultiplier)을
  아직 모른다. getCookSpeedMultiplier 는 NBT 에 Paper.CookSpeedMultiplier 가 있으면 그 값, 없으면 1.0.
- getRecipesUsed·setRecipeUsedCount·setRecipesUsed: compat 에 `CookingRecipe` 형이 없어 아직 선언하지 않았다.
- 시험 한계: 시험 서버는 데이터 팩 없이 떠서 레시피 표가 없고(플러그인 레시피도 못 건다) 아이템 태그(ItemContext)가 걸리지 않는다.
  그래서 canSmelt 는 빈 아이템만, isFuel 은 표의 낱개 아이템(석탄)만 시험한다. 태그 연료(판자)·레시피 일치는 실서버에서 볼 것(가정: 서버 표를 그대로 쓰므로 같다).
