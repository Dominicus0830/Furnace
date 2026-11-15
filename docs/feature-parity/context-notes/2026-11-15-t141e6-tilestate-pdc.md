# T-141 E6 — TileState 영속 데이터·Nameable (2026-11-15)

기준: Paper 1.21.1(33ff3dff) `Sanitize-sent-BlockEntity-NBT`, CraftBukkit `BlockEntity` 패치(Youer 1.21.1 사본 — `loadAdditional` 에서 PublicBukkitValues 읽기,
`saveWithoutMetadata`·`saveCustomOnly` 에 싣기), `CraftBlockEntityState#getPersistentDataContainer`·`isSnapshot`, `CraftContainer`·`CraftBanner`·`CraftBeacon`·`CraftEnchantingTable` 의 이름.

- 서버 블록 엔티티: 플러그인 영속 데이터를 NBT `PublicBukkitValues` 로 들고 있다. CraftBukkit 은 기본 `loadAdditional` 에서 읽는데 Furnace 종류별 `load` 는 super 를 부르지 않는 것이 있어,
  바깥 진입점(`loadStatic`·`loadWithComponentsInLevel`·`loadCustomDataInto`·구조물·상자 코덱)이 `loadWithBukkitValues` 를 부른다. 저장은 `saveCustomOnly`(그래서 `saveWithoutMetadata`·`/data`·아이템 block_entity_data 에도).
- 클라이언트로 가는 갱신 태그(청크 패킷·block_entity_data)에서는 뺀다(`sentUpdateTag`). 표지판·구조물 블록처럼 갱신 태그가 saveCustomOnly 인 종류가 그대로 보내지 않게.
- **함정(고침)**: 상자의 `CustomName`·`Lock`·남은 컴포넌트는 `ChunkSnapshot.forSave` 스냅숏에만 들어가고 `AnvilChunkCodec.chestNbt` 가 디스크에 적지 않았다(이름 붙인 상자가 다시 불러오면 이름을 잃음). 이제 그 키들과 PublicBukkitValues 를 적는다(`AnvilChunkCodecTest.roundTrip_preservesChestNameLockAndBukkitValues`).
- 호환 `TileState`: 스냅숏 NBT 사본의 PublicBukkitValues 를 메모리 컨테이너(ItemPdc)로 들고 있다가 `update` 때 사본에 되써 싣는다(Paper 와 같은 스냅숏 규칙 — update 전에는 블록에 없다). `isSnapshot()` 은 늘 true.
- `Nameable`: 메서드 없는 표시 인터페이스였던 것을 Paper 처럼 `getCustomName`·`setCustomName` 을 가진 인터페이스로(표시 인터페이스는 invokeinterface 에서 NoSuchMethodError — #374·#376 함정). `Entity` 도 Paper 처럼 Nameable.
  블록 상태 몸은 `CraftBlockEntityState`(사본 CustomName JSON ↔ § 레거시, null·빈 문자열이면 뗌). Adventure 판 `customName(Component)` 은 빠짐.
- 남은 것: 마법 부여대는 Furnace 가 블록 엔티티를 만들지 않아 이름이 실리지 않는다(기존 구멍). `getSnapshotInventory`, 큰 상자 합친 인벤토리, `Lockable` 은 다음 조각.
