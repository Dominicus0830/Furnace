# 컨테이너(칸 저장소)·인벤토리 창(2026-11-23, 서명표 잠금 재생성). 바닐라(Paper)에서 추상인 메서드는 이름만 적어 추상으로 둔다 —
# 상자 수레·상자 배 같은 생성 클래스는 던지는 몸통과 색인 한 줄을 받고(링크 때 "없음"), 손 구현(Inventory 등)은 모두 직접 채운다.
# default 메서드(바닐라 규칙)는 조각 src/shims/java/net/minecraft/world/Container.java 에 있다.
class net.minecraft.world.Container
  DEFAULT_DISTANCE_BUFFER = 4.0F
  MAX_STACK = 99
  getContainerSize
  isEmpty
  getItem
  removeItem
  removeItemNoUpdate
  setItem
  setChanged
  stillValid
  getContents
  getMaxStackSize()I
  setMaxStackSize
  getLocation
  getOwner
  getViewers
  onOpen
  onClose
# 인벤토리 창의 상위(레시피 책 창). 바닐라처럼 추상 메서드는 이름만, 손 shim InventoryMenu 가 구현한다
class net.minecraft.world.inventory.RecipeBookMenu
  getRecipeBookType
  fillCraftSlotsStackedContents
  clearCraftingContent
  recipeMatches
  getResultSlotIndex
  getGridWidth
  getGridHeight
  getSize
  shouldMoveToInventory
# 위 메서드의 서술자에 나오는 형식(선언만)
class net.minecraft.world.entity.player.StackedContents
class net.minecraft.world.inventory.RecipeBookType
  CRAFTING
  FURNACE
  BLAST_FURNACE
  SMOKER
class net.minecraft.world.item.crafting.RecipeInput
class net.minecraft.world.item.crafting.Recipe
class net.minecraft.world.item.crafting.CraftingInput
class net.minecraft.world.item.crafting.CraftingRecipe
