# T-141 E2c: 생성기 상태와 용기 인벤토리 (2026-11-14)

## 생성기(CreatureSpawner) — B6 코퍼스 5개 플러그인
- `CraftCreatureSpawner` 는 블록 엔티티 NBT 사본(바닐라 BaseSpawner 저장 꼴: SpawnData{entity}, SpawnPotentials[{data, weight}],
  Delay·MinSpawnDelay·MaxSpawnDelay·SpawnCount·MaxNearbyEntities·RequiredPlayerRange·SpawnRange 는 short) 위에서 읽고 쓴다.
  값이 없으면 바닐라 BaseSpawner 처음 값(20·200·800·4·6·16·4).
- Paper 와 같은 것: getSpawnedType 은 다음 SpawnData 가 없거나 id 가 없으면 null. setSpawnedType(null) 은 후보 목록을 비우고 빈 SpawnData.
  setSpawnedType(종류) 는 바닐라 setEntityId — 다음 SpawnData 의 entity.id 만 바꾸고 후보 목록은 그대로. UNKNOWN 은 같은 문구로 거절.
  setMin/MaxSpawnDelay 의 검사·문구. getCreatureTypeName 은 Paper bukkitToString 처럼 키 전체("minecraft:zombie"),
  setCreatureTypeByName 은 stringToBukkit 처럼 null 이면 IllegalArgumentException, "zombie"·"minecraft:zombie" 모두, 모르면 생성기를 비운다.
- 숫자 값은 Paper 사본 BaseSpawner 처럼 int 필드로 든다(setDelay(40000) 뒤 getDelay()=40000). update 때 Paper 저장 규칙으로 적는다: Delay·MinSpawnDelay·
  MaxSpawnDelay 는 Short.MAX 로 자르고 넘으면 `Paper.Delay`·`Paper.MinSpawnDelay`·`Paper.MaxSpawnDelay` int 키도, 나머지 넷은 short 로 접는다.
  서버 `BaseSpawner.load/save` 도 Paper 패치(Mob-Spawner-API-Enhancements)의 `Paper.*` 키를 읽고 쓴다 — 범위 안이면 바닐라와 바이트까지 같고,
  Paper 월드를 가져올 때 큰 지연이 보존된다(11-08 Paper 월드 가져오기 결정 범위). 바닐라 키는 그대로 short 로 읽는다(Paper 는 MinSpawnDelay 를 getInt 로 읽어
  int 형으로 적힌 값에서만 다르다 — 가정, 바닐라 쪽을 따름).
- short 로 바로 접던 첫 판은 setDelay(40000) 이 −25536 이 되어 update 뒤 곧바로 스폰했다(PR #378 검토 R1).
- 다른 점(가정): 다음 SpawnData 가 없고 후보가 있으면 바닐라는 월드 난수로 후보를 고른다 — 여기선 따로 굴린다(바닐라 저장은 nextSpawnData 가 있으면 늘 SpawnData 를 적어 드물다).
  또 바닐라는 고른 후보의 data 를 복사 없이 다음 SpawnData 로 쓰므로 그 후보의 entity id 도 함께 바뀐다. 여기선 복사본을 바꿔 후보는 그대로다(같은 드문 길).
- 아직 없음: EntitySnapshot·SpawnerEntry 판(getSpawnedEntity·addPotentialSpawn·setPotentialSpawns), isActivated·resetTimer·setSpawnedItem.

## 용기 인벤토리(Container·Chest)
- `TileStateInventoryHolder`(손으로 씀)가 `BlockInventoryHolder`(→ InventoryHolder)를 넓히고 getInventory·getSnapshotInventory 를 선언한다.
  몸은 `CraftBlockEntityState` 에 있다(인터페이스를 단 형만 드러난다).
- getInventory: 놓인 상태면 그 칸 블록 엔티티의 **살아 있는** 인벤토리(Paper `new CraftInventory(tileEntity)`). furnace-api `World#inventoryAtNow`(새, 소유 스레드 전용).
  getHolder() 는 Paper 처럼 그 칸의 새 블록 상태.
- 서버: 블록 용기 인벤토리(`inventoryAt`·`inventoryAtNow` 가 준 것)의 읽기·쓰기는 **그 칸 region 스레드에서 부르면 곧바로** 돈다(끝난 Future). 예전에는 늘 대기열에
  넣어 같은 스레드에서도 그 틱 뒤에 끝났다 — 다른 스레드에서 부르면 그대로 대기열.
- 큰 상자: Paper getInventory 는 두 칸을 합친 CraftInventoryDoubleChest. 아직 없어 **UnsupportedFeature**(반쪽만 주면 상점 플러그인이 재고를 틀리게 센다 —
  조용히 틀리지 않는다). `Chest#getBlockInventory`(이 칸 반쪽)는 Paper 와 같다. 큰 상자 판정은 바닐라 DoubleBlockCombiner 처럼 type=left/right 이고
  이은 쪽 칸(left 는 facing 시계 방향)이 같은 블록·같은 facing·반대 type 일 때만이다. 짝 없는 반쪽(붙여넣기 등)은 Paper 처럼 단일 27칸.
  짝 칸이 다른 region 이라 못 읽으면 큰 상자로 본다(UnsupportedFeature).
- 부서진 뒤 들고 있던 인벤토리를 쓰면 IllegalStateException("그 칸의 용기가 사라졌다"). Paper 는 고아 타일에 조용히 쓴다 — 조용한 유실보다 알리는 쪽으로 둔다.
- 아직 없음: 스냅숏 인벤토리(getSnapshotInventory, 놓이지 않은 상태의 getInventory) — NBT Items ↔ 아이템 변환이 api 에 없다. UnsupportedFeature.
  Furnace 가 용기로 다루지 않는 블록 엔티티(조각된 책장·주크박스·독서대·장식 단지 등)의 getInventory 도 UnsupportedFeature.
- 다음 조각: `Furnace#getInventory()` 의 공변 `FurnaceInventory` 서술자가 없다 — Paper 로 컴파일한 플러그인이 부르면 NoSuchMethodError
  (BrewingStand 와 같은 꼴, 표시 인터페이스 함정과 같은 뿌리).

## 양조대(PR #376 검토 R2)
- `CraftBrewingStand`(손으로 씀, CraftContainer 아래): 양조 시간·연료는 사본 NBT `BrewTime`(short)·`Fuel`(byte) — 바닐라 저장 형식. 값은 int 로 들고 update 때 적는다.
- `recipeBrewTime` 은 Paper 가 블록 엔티티 필드로만 두고 NBT 에 저장하지 않는다. Paper 사본은 NBT 왕복으로 뜨므로 상태에서는 늘 400 에서 시작하고 update 로 실리지 않는다.
  Furnace 도 상태 안의 값(기본 400, 0 이하면 "recipeBrewTime must be positive")으로 같게 둔다.
- `BrewingStand` 인터페이스는 이제 Paper 처럼 `Container` 를 넓힌다(예전엔 이벤트 서명용으로 BlockState·InventoryHolder 만).
- 살아 있는 인벤토리는 `BlockInventories.Brewer`(BrewerInventory: 3 재료·4 연료). 금 파일 `scene-compat-block-reads` 의 state-classes 줄에 CraftBrewingStand 가 더해졌다.
