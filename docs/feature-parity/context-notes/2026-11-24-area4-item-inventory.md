# 영역 4(아이템·인벤토리·음식) 손 shim 명세 이전 1차 (2026-11-24)

- `item-inventory.spec` 신설. 이전: FoodData·Slot·MenuType (조각은 `src/shims/java`). 머리는 서명표 잠금이 붙인다.
- 바이트코드 대조(`tools/ci/javap-same.sh`): 2,851개 중 달라진 것은 MenuType 하나 — 바닐라·Paper 처럼 `FeatureElement` 를 구현하게 됐다(기준선 type-missing 줄 삭제).
- Enchantment 는 바닐라가 레코드라 생성기가 아직 못 만든다(T-230) → 손 shim 유지. 레코드(Unbreakable·CustomModelData·RecipeHolder 등)·열거형 DyeColor(MapColor 인자)도 같은 이유/조건으로 보류.
- 남은 큰 것: Item·ItemStack·Items(1,347줄)·AbstractContainerMenu·InventoryMenu(메뉴 쓰기는 플레이어 스레드 소유 — 검사 종류를 줄마다 정해야 해서 별도 묶음).
- 연결 줄(.links)은 T-263 S2 뒤.
