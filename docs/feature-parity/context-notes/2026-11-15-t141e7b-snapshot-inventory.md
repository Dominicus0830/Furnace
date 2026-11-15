# T-141 E7b — 사본 인벤토리·큰 상자 합친 인벤토리 (2026-11-15)

기준: Paper 1.21.1 `CraftChest`·`CraftBarrel` 등 `getSnapshotInventory`/`getBlockInventory`/`getInventory`, `CraftInventoryDoubleChest`, `DoubleChest`,
`DoubleChestInventory`(Youer 1.21.1 사본), 바닐라 `ChestBlock.getMenuProvider`·`DoubleBlockCombiner`·`CompoundContainer`·`ContainerHelper.saveAllItems`.

- 사본 인벤토리: 상태를 뜰 때의 블록 엔티티 NBT `Items`({Slot,id,count,components})를 메모리 칸으로 읽고(`ItemNbtCodec` — API 아이템 컴포넌트 SNBT 와 NBT 값을 그대로 옮김,
  `!키` 는 지운 컴포넌트), 고친 것은 `update` 때 `Items` 로 되쓴다(비지 않은 칸만). 전리품 표가 있는 사본은 바닐라 trySaveLootTable 처럼 아이템을 쓰지 않는다.
  Items 를 쓰는 종류만(상자·덫 상자·통·셜커·깔때기·발사기·공급기·화로류·양조대·제작기·조각된 책장) — 그 밖은 UnsupportedFeature.
  주인(getHolder)은 Paper 스냅숏 블록 엔티티처럼 null, 위치는 놓인 상태면 그 칸(가정 — Paper 스냅숏은 레벨이 없어 null 일 수 있다).
- 놓이지 않은 상태(BlockData#createBlockState)의 `getInventory` 는 Paper 처럼 사본 인벤토리.
- 큰 상자: `getInventory` 는 `DoubleChestInventory` 54칸. 바닐라 `CompoundContainer(첫째, 둘째)` 의 첫째는 type=right 상자라 Bukkit 왼쪽(칸 0..26)이 type=right 다
  (`ConnectionHandler.resolveOpenSpec` 의 화면 순서와 같다). 양쪽은 살아 있는 반쪽 인벤토리라 합친 것에 쓰면 곧바로 그 블록 엔티티에. 짝 칸은 같은 region 스레드에서 읽혀야 한다
  (아니면 ThreadOwnershipException — 예전에는 큰 상자면 UnsupportedFeature 였다). Paper 처럼 막힌 상자(고양이·위 블록)도 합친다(ignoreBlocked=true).
- 살아 있는 블록 인벤토리의 `getLocation` 은 이제 그 칸(Paper CraftInventory → 블록 엔티티 위치). 합친 것은 두 칸의 가운데.
- `Inventory#getHolder(boolean)` 을 기본 메서드로 넣었다(Paper DoubleChest#getLeftSide(boolean) 가 부른다).
