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

## 검토 반영(PR #392 R1~R3, S1·S2)
- R1: 명령 블록 편집 화면(`openCommandBlock`, 바닐라 `ClientboundBlockEntityDataPacket.create(be, saveCustomOnly)`)도 Paper 처럼 영속 데이터를 뺀다. 클라이언트로 블록 엔티티 태그를 보내는 곳은
  모두 `BlockEntity.sanitizeSentNbt` 한 곳을 거친다(`sentUpdateTag`·`CommandBlockEntity.screenTag`). 새로 보내는 곳을 만들면 이것을 부를 것.
- R2(결정 A): `Block#getState(false)` 의 TileState 는 `isSnapshot()=false` 이고 PDC 가 살아 있는 블록 엔티티의 것(Paper `API-to-get-a-BlockState-without-a-snapshot`).
  furnace-api `World#editBlockEntityPersistentData(Now)` 로 영속 데이터만 고친다(전체 NBT 를 다시 싣지 않음, 바뀌었을 때만 청크 저장 표시, 비면 키를 뗀다).
  소유 스레드 밖 읽기는 ThreadOwnershipException, 쓰기는 예약(엔티티 PDC 와 같은 `LivePdc`). 블록 엔티티가 사라지면 떨어져 나간 메모리 컨테이너(Paper 와 같이 블록에 안 남음, 경고 한 번).
  다른 값(이름·표지판 글)은 Furnace 상태가 NBT 사본 위라 여전히 `update` 가 필요 — `setCustomName` 에 경고 한 번. 살아 있는 상태의 `update`·`getSnapshotNBT` 는 지금 블록 엔티티의 영속 데이터를 싣는다.
- R3: 실제 패킷 길 시험 — 청크 패킷·되돌림(`BukkitValuesNotSentE2ETest`), block_entity_data 방송·명령 블록 화면·아이템 block_entity_data(`BlockEntityBukkitValuesTest`). 각 길을 updateTag/plain load 로 되돌리면 실패하는 것을 확인했다.
- S1: 구조물 템플릿 상자(`ChunkColumn.adoptGeneratedChest`)도 CustomName·Lock·components·PublicBukkitValues 를 옮긴다. S2: update 뒤에도 블록 엔티티가 없으면(마법 부여대) 경고 한 번.
- 재검토 N1: 살아 있는 PDC 는 좌표 기준이라, 그 칸이 다른 종류의 블록으로 바뀌면 떨어져 나간 것으로 본다(만들 때 블록 종류를 기억, 예약 쓰기도 돌 때 확인).
  같은 종류로 다시 놓인 경우는 구별하지 못한다 — 서버 블록 엔티티 세대 번호가 필요한 후속(T-141 후속 목록).
