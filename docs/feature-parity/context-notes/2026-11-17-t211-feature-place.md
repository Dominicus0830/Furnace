# T-211 1단계 — 플러그인 레벨 지형지물 배치 함정 (2026-11-17)

Paper 1.21.1 jar(`Level`·`BlockStateListPopulator`·`DummyGeneratorAccess`·`CapturedBlockState`)와 FAWE 2.16 `PaperweightFaweAdapter` 원문 대조에서 나온 것.

- `Level#setBlock` 의 `captureTreeGeneration` 갈래는 **높이 밖 검사보다 먼저**다(높이 밖 칸도 `VOID_AIR` 라 부술 수 있음 → 기록된다). 거절은 `getBlockState(pos).isDestroyable()`
  하나 — 기록을 먼저 보는 `getBlockState` 로 묻는다.
- `capturedBlockStates` 는 처음 쓴 차례(같은 칸 다시 쓰기는 상태·플래그만 바뀜), `BlockStateListPopulator` 의 목록은 `remove` 뒤 `put` 이라 **마지막 쓴 차례**다.
  나무는 잎 거리 갱신이 잎을 다시 써서 두 차례가 실제로 다르다.
- FAWE populator 는 `setBlock`·`getBlockState` 만 서버 레벨로 돌리고 `getFluidState`·`getBlockEntity` 는 안 돌린다 → 유체는 실제 월드, 블록 엔티티는 자기 표(빔) 다음 실제 월드.
  나무 기록은 블록 엔티티를 만들지 않으므로 벌집 장식·전리품 상자 씨앗 같은 "블록 엔티티가 있을 때만 뽑는 난수"는 FAWE 길에서 뽑히지 않는다.
- 블록 엔티티가 있을 때만 난수를 뽑는 바닐라 자리(1.21.1): `BeehiveDecorator`(nextInt(2) + 벌마다 nextInt(599)), `RandomizableContainer#setBlockEntityLootTable`
  (nextLong — chest·trapped_chest·barrel·dispenser·dropper·hopper·shulker_box·decorated_pot·crafter, paper jar javap 로 확인), `MonsterRoomFeature` 스포너(nextInt(4)).
  `EndGatewayFeature`·`DesertWellFeature` 는 블록 엔티티가 있을 때만 값을 쓰고 난수는 안 뽑는다.
- `PlacedFeature#place(level, gen, random, pos)` 는 top feature 가 없어 `BiomeFilter` 가 `IllegalStateException("Tried to biome check an unregistered feature, or a feature that should not restrict the biome")`
  을 위치마다 늦게 던진다(앞 modifier 의 난수는 이미 뽑혔다).
