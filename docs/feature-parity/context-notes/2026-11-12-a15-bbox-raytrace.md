# A15 후속: 충돌 상자·주변 엔티티·광선 판정 (2026-11-12)

PR #253 검토 R4 가 후속 후보로 꼽은 `Entity#getBoundingBox`, `World#getNearbyEntities(BoundingBox)`, `World#rayTrace*`. 기대 동작은 Paper 1.21.1
`CraftWorld`·`CraftEntity`·`BlockGetter.clip` 패치(Add-predicate-for-blocks-when-raytracing, Prevent raytrace from loading chunks)에서 옮겼다.

## 한 것
- **Furnace API**: `world.Aabb`(충돌 상자), `world.BlockHit`, `world.FluidPick`, `Entity#boundingBox()`(핸들 시점, 플레이어는 최근 값),
  `EntityState#boundingBox()`(지금 값), `World#entitiesInBoxNow(Aabb)`, `World#rayTraceBlocksNow(...)`.
- **호환 계층**: `Entity#getBoundingBox`, `World#getNearbyEntities(Location,x,y,z,Predicate)`·`(BoundingBox[,Predicate])`,
  `World#rayTraceEntities`(5꼴)·`rayTraceBlocks`(4꼴, `Position`+`canCollide` 포함)·`rayTrace`(2꼴), `Position#isFinite`·`toVector`.
- **바뀐 동작**: `Entity#getNearbyEntities(x,y,z)`·`World#getNearbyEntities(Location,x,y,z)` 가 **위치**가 아니라 **충돌 상자가 겹치는지**(바닐라 `AABB.intersects`,
  경계만 닿으면 제외)로 판단한다 — Paper 와 같아졌다. 전에는 가장자리에서 Bukkit 보다 적게 잡혔다.
- **서버 `Clip` 바로잡음**: 충돌 모양(COLLIDER)·시각 모양(VISUAL) 판정이 유체 모드를 무시하고 상호작용 모양 면 덮어쓰기도 안 했다. 바닐라 `BlockGetter.clip(ClipContext, BlockPos)` 는
  모든 모드에서 둘 다 한다 — `FallingBlockEntity`(콘크리트 가루가 물 원천에 닿는 검사, SOURCE_ONLY)·`WaterBoundPathNavigation`(ANY)이 이 경로를 쓰므로 그쪽도 바닐라 쪽으로 바뀐다.

## Paper 와 같게 맞춘 것(함정)
- 로드되지 않은 청크에 닿으면 광선은 거기서 **빗나간 것으로 끝난다**(Paper "Prevent raytrace from loading chunks"; 바닐라는 공기로 보고 계속). Furnace 는 그 region 이 갖지 않은 청크도 같게 본다.
- `canCollide` 가 false 인 칸은 블록뿐 아니라 **유체도** 건너뛴다. 공기 칸은 묻지 않는다(`isAir` 가 먼저).
- `ignorePassableBlocks` 는 COLLIDER, 아니면 OUTLINE. 유체 모드는 별개로 적용된다.
- `rayTrace`(블록+엔티티)는 블록을 맞으면 엔티티 탐색 거리를 그 거리까지로 줄이고, 엔티티가 더 가까울 때만(엄격 부등호) 엔티티를 돌려준다.
- 검사 문구는 Paper 와 같다(`Location start cannot be null`, `Direction's magnitude (…) need to be greater than 0` 등). `FluidCollisionMode cannot be null` 은 블록 광선에만.
- `Aabb`·`BoundingBox.of(Location,x,y,z)`: 경계가 같으면 겹침이 아니다.

## 알려진 차이(의도)
- **region 경계**: `getNearbyEntities(BoundingBox)` 는 상자 **가운데 칸**을 가진 region 에서만 되고 다른 region 이 가진 청크의 엔티티는 빠진다. 블록 광선은 **시작점 칸**을 가진 region 에서만
  되고 그 region 이 갖지 않은 청크에 닿으면 끝난다(Paper 는 로드된 청크면 어디든 본다). 소유 스레드가 아니면 `ThreadOwnershipException`(Paper 의 주 스레드 검사에 해당).
- 엔더 드래곤 부위(`EnderDragonPart`)는 엔티티 목록에 없다(드래곤 본체만).
- `Entity#getBoundingBox` 를 소유 스레드가 아닌 곳에서 부르면 핸들을 만든 시점 값이다(플레이어는 어느 스레드에서나 최근 값).
- `LivingEntity#rayTraceBlocks`·`getTargetBlock*`·`Block#getBoundingBox`/`rayTrace`·`Block#getCollisionShape` 는 아직 없다(`getEyeLocation` 도 없다) — 후속.

## 시험
`A15BoundingBoxRayTraceTest`(헤드리스 서버, 하늘 높이에 블록·엔티티를 놓고 대조): 충돌 상자 크기(좀비 0.6×1.95·돼지 0.9·플레이어 0.6×1.8), 경계 접촉 제외, 상자 판정으로 바뀐 getNearbyEntities, raySize, 블록 면·유체 모드·ignorePassable·canCollide,
블록/엔티티 중 가까운 쪽, Paper 문구 검사.
