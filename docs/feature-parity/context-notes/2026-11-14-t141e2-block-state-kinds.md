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

## E2b: 블록 엔티티 NBT 스냅숏과 표지판
- `CraftBlockEntityState`(손으로 씀): 만들 때 `World#blockEntityNbtNow`(새 API, 소유 스레드 전용)로 블록 엔티티 NBT 사본을 뜬다(Paper 의 스냅숏 블록 엔티티 자리).
  `update` 는 블록을 쓴 뒤 그 칸의 블록 엔티티 id 가 같으면 사본을 `World#setBlockEntityNbtNow`(/data 쓰기와 같은 길)로 통째로 싣는다 — Paper applyTo·setChanged.
  놓이지 않은 상태와 블록 엔티티 없는 칸은 id 만 든 사본(읽으면 바닐라 기본값). id 가 있어야 update 때 새로 생긴 같은 종류 블록 엔티티에 실린다.
- `CraftSign`·`CraftSignSide`(손으로 씀): front_text·back_text 의 messages(줄마다 JSON 글)·color·has_glowing_text, is_waxed.
  줄은 처음 읽을 때 풀고, update 때 **바뀐 줄만** 적는다(Paper 와 같다 — 클릭 이벤트 같은 JSON 서식이 레거시 왕복으로 깎이지 않게). filtered_messages 가 있으면
  같은 줄을 같은 값으로(바닐라 SignText.setMessage), 모두 같아지면 지운다. 레거시 변환은 Adventure legacySection 과 같은 `LegacyTextSerializer.section()`.
- 아직 없는 것: Adventure 판(lines()·line(int)), getTargetSide·getInteractableSideFor(표지판 회전·히트박스 계산이 서버에 있다), getAllowedEditor·
  setAllowedEditorUniqueId.
- `Colorable` 은 getColor/setColor 를 가진다(손으로 씀). 표시 인터페이스로 두면 Paper 로 컴파일한 플러그인의 `Colorable#getColor` 호출이
  NoSuchMethodError 다(#374 Damageable 과 같은 함정). Paper 에서 이것을 구현하는 블록 상태는 Sign·SignSide·Bed 뿐이다. `CraftBed` 는 블록 종류에서 색을 내고
  setColor 는 Paper 처럼 `UnsupportedOperationException("Must set block type to appropriate bed colour")`.

## E2b 검토(PR #376)에서 정리한 Paper 와 다른 점
- `getSnapshotNBT()`: Paper 는 놓이지 않은 상태·블록 엔티티 없는 칸에서 새 블록 엔티티의 전체 NBT(id·x·y·z·기본 front_text 등)를 준다. Furnace 는 id 만.
- `BlockPlaceEvent#getBlockPlaced().getState()`: Furnace 는 놓기 전에 이벤트를 부르므로 그 칸은 아직 예전 블록이다. 거기서 표지판 글을 쓰고
  update() 하면 종류가 달라 false 이고 글은 사라진다(Paper 는 블록을 놓은 뒤 이벤트를 부르므로 남는다). 놓기 이벤트 시점 정렬(T-076 계열) 때 함께 시험한다.
- 블록 엔티티 없이 놓인 칸(FAST 편집·움직이는 피스톤): Paper 는 피스톤 말고는 "Tile is null, asynchronous access?" 로 던지고, Furnace 는 형 있는 상태에
  id 만 든 사본을 준다. 너그러운 쪽이라 둔다.
- `CraftSignSide.applyTo` 는 적은 뒤 원래 줄을 새로 맞춘다(Paper 는 안 맞춘다). 줄을 바꾸고 getSnapshotNBT 를 부른 뒤 원래 글로 되돌리고 update 하면
  Paper 는 중간 값이, Furnace 는 되돌린 값이 남는다. 드문 길이고 Furnace 쪽이 자연스럽다.
- 이벤트가 보여 주는 블록(`CompatBlock.showing`)의 `getState()` 도 표지판 같은 형이면 사본을 뜨므로 소유 스레드가 필요하다(Paper 도 비동기 접근은 막는다).
- `untouchedLinesKeepTheirJson` 은 클릭 이벤트 줄로 잰다. API Text 가 그 JSON 을 손실 없이 왕복하므로 "안 바뀐 줄도 적기" 변이는 못 잡는다(동등 변이에 가깝다).
  nbt·score·selector 성분은 API Text 로 표현되지 않아 시험 줄로 쓸 수 없다.
- 양조대(BrewingStand)는 E2c 에서 넣는다.

## 함정
- **바닐라 `setBlock` 은 같은 상태면 false 다.** `Block#set` 이 그 false 를 그대로 주므로 `CompatBlock#setBlockData` 가 "청크가 안 올라와 있다"로 던졌다
  (블록을 안 바꾼 상태의 `BlockState#update` 가 늘 실패). 이제 그 칸이 이미 그 상태면 조용히 끝난다(Paper 와 같다).
- compat 시험 서버는 플레이어가 없으면 시험 사이에 청크가 퇴출될 수 있다 — 블록을 놓는 시험은 @BeforeEach 에서 `loadChunk`.
