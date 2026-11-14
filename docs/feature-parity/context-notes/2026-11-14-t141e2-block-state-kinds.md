# T-141 E2: 블록 엔티티 블록 상태의 Paper 형 (2026-11-14)

## 한 일
- `Block#getState()` 와 `BlockData#createBlockState()` 가 블록 엔티티가 있는 블록이면 Paper 1.21.1 과 같은 형을 준다:
  `org.bukkit.craftbukkit.block.Craft*`(CraftChest·CraftSign·CraftHangingSign·CraftCreatureSpawner 등 42 클래스, 블록 엔티티 43종) →
  `org.bukkit.block.Chest`·`Sign`·`TileState`·`Container`, `org.bukkit.loot.Lootable` 등. 인터페이스는 지금은 표시 인터페이스(메서드 없음).
- 표: `tools/compat/bukkit-block-state-hierarchy-1.21.1.txt`(Paper CraftBlockStates 등록표·Craft*State 선언·paper-api 인터페이스 선언에서 이름·계층만).
  생성기: `tools/compat/gen-bukkit-block-state-classes.py`. 손으로 메서드를 넣을 파일은 첫 머리말 줄을 지운다.
- 블록 → 블록 엔티티 종류: `GameRegistries#blockEntityType(block)`(새 API). 서버는 블록 행동의 `newBlockEntity` 로 표를 만들고, 바닐라와 다른 세 곳을 손으로 맞춘다:
  상자·덫 상자(Furnace 는 ChestBlockEntity 별도 길), 움직이는 피스톤(바닐라도 newBlockEntity 가 null 이고 BlockEntityType.PISTON 의 유효 블록), 마법 부여대(아래).
- `CompatBlockState` 는 이제 상속 가능하고 놓이지 않은 상태(dimension null)를 다룬다. Paper CraftBlockState 와 같게:
  getWorld·getBlock·getChunk 는 `IllegalStateException("The blockState must be placed to call this method")`, getLocation 은 월드 null, update 는 아무것도 안 하고 참,
  equals 는 같은 클래스끼리만.

## Paper 와 다른 점(알고 둔 것)
- 양조대: Paper 는 CraftBrewingStand(BrewingStand 구현). 손으로 쓴 `org.bukkit.block.BrewingStand` 에 메서드(getBrewingTime 등)가 있어 아직 일반 상태를 준다.
- 움직이는 피스톤 칸에 블록 엔티티가 없으면 Paper 는 일반 상태(isTileEntityOptional), Furnace 는 언제나 MovingPiston 형(블록 엔티티 유무를 호환 계층에서 동기로 못 본다).
- 이벤트의 상태(BlockGrowEvent 의 새 상태, BlockPlaceEvent 의 바뀐 상태 등)는 그대로 일반 상태 — Paper 도 대부분 `getBlockState(world, pos, flag)`(블록 엔티티 무시)로 만든다.
  단 Paper 의 BlockPlaceEvent replacedState 는 형이 있다(남은 차이).
- 블록 엔티티 내용(상자 인벤토리·표지판 글·TileState PDC·이름)은 아직 없다 → E2b.

## 함정
- **Furnace 서버는 마법 부여대 블록 엔티티를 만들지 않는다**(EnchantingTableBlock.newBlockEntity 없음). 바닐라는 EnchantingTableBlockEntity(사용자 이름 저장)를 둔다.
  마법 부여대에 이름 붙인 아이템을 놓아도 화면 이름이 남지 않는 서버 쪽 구멍이다(EnchantingTableBlock 의 TODO). 표는 바닐라대로 enchanting_table 을 준다.
- Paper 의 HangingSign 은 Sign 의 하위 형이고 CraftHangingSign 은 CraftSign 을 상속한다 — `instanceof Sign` 으로 매다는 표지판도 걸린다.
