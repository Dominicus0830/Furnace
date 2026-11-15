# T-141 E5: Bukkit 나무 생성 API (2026-11-15)

FAWE 2.16.2 남은 구멍 `World#generateTree(Location, TreeType)`·`generateTree(Location, TreeType, BlockChangeDelegate)`·`TreeType`(B6 코퍼스에서도 FAWE 3곳).
기준은 Paper 1.21.1 `CraftWorld#generateTree` 두 판, `CraftRegionAccessor#generateTree`(TreeType → TreeFeatures 표), `RandomSourceWrapper`(Youer 1.21.1 사본).

## 무엇을
- furnace-api `World#placeFeatureNow(id, pos, Random, apply)`·`growChorusPlantNow(pos, Random, apply)` → `FeaturePlacement(placed, writes)`.
  - 서버는 묘목 성장과 같은 `LiveFeatures` 경로(3×3 청크 사본에 월드젠 코드를 돌리고 쓴 칸을 옮김)를 쓴다. 공통 부분을 `run` 으로 묶었고 묘목 경로 동작은 그대로다.
  - 난수: 플러그인이 준 `java.util.Random` 을 `JavaRandomWorldgen`(WorldgenRandom 하위)이 그대로 쓴다. Paper `RandomSourceWrapper` 와 같이 모든 뽑기·setSeed 를 넘기고,
    consumeCount 는 바닐라 RandomSource 기본(nextInt() 반복), forkPositional 은 UnsupportedOperationException. 그래서 플러그인 Random 의 상태도 Paper 처럼 나아간다.
  - apply=false(기록 판): 월드를 바꾸지 않고 칸마다 처음 쓴 차례로 마지막 상태. 바뀌지 않은 쓰기도 남긴다(CraftBukkit `capturedBlockStates` 와 같은 모양).
- 호환 `org.bukkit.TreeType`(25 상수, Paper 순서), `World#generateTree` 3종(`(loc, type)`, `(loc, type, delegate)`, `(loc, Random, type)`).
  - 표: BIG_TREE=fancy_oak, REDWOOD=spruce, TALL_REDWOOD=pine, JUNGLE=mega_jungle_tree, SMALL_JUNGLE=jungle_tree_no_vine, COCOA_TREE=jungle_tree,
    SWAMP=swamp_oak, MEGA_REDWOOD=mega_spruce, TALL_BIRCH=super_birch_bees_0002, 균류=*_fungus_planted(플러그인 월드는 늘 "보통 월드"), TREE·CUSTOM=oak 등.
  - CHORUS_PLANT: `ChorusFlowerBlock.generatePlant(…, 8)` 를 flag 2 로, 바닥 검사 없이 늘 true(Paper 와 같다).
  - `(loc, type)` 는 서버 공용 `java.util.Random`(CraftWorld.rand, 시드 없음).
  - delegate 판: 기록 판으로 키우고, 키웠으면 기록한 칸마다 `delegate.setBlockData`, true. getBlockData·getHeight·isEmpty 는 Paper 처럼 부르지 않는다.
- 미구현(Noop) feature 면 다른 나무로 바꾸지 않고 false + 종류마다 경고 한 줄(AGENTS.md 규칙 3). 지금 1.21.1 configured feature 는 Noop 이 없다(NoopFeatureAuditTest).

## 함정
- **잎 거리는 자리마다 다를 수 있다.** 바닐라 `TreeFeature.updateLeaves` 는 HashSet 순서로 거리를 매기고, 이미 채운 칸을 더 큰 거리로 다시 쓰는 일이 있어
  같은 시드라도 자리(BlockPos 해시)가 다르면 잎 distance 가 다르다. Furnace 도 같은 결과를 낸다(묘목 경로도 같다). 시험은 같은 자리를 비우고 두 번 키워 비교한다.
- 노이즈 오버월드 시험에서 y=200~216 은 산 지형이 닿을 수 있다. 나무 비교 시험은 y 250 이상에서 한다.

## Paper 와 다른 점(알고 둔 것)
- **닿지 못한 청크**: Paper 는 ServerLevel 에 바로 써서 로드 안 된 청크를 동기로 불러온다. Furnace 는 그 칸 3×3 청크 밖·로드 안 된·다른 region 청크에 닿으면
  아무것도 하지 않고 false(묘목 성장과 같은 규칙). 그 칸 청크가 없으면 IllegalStateException, 소유 스레드가 아니면 ThreadOwnershipException.
- **delegate 판 뒤 물리 갱신**: Paper 는 delegate.setBlockData 뒤 실제 월드 칸에 `notifyAndUpdatePhysics`(대개 delegate 가 아직 월드를 안 바꿔 옛 상태=새 상태)를 부른다.
  Furnace 는 부르지 않는다. 바뀌지 않은 칸의 이웃 갱신이라 보이는 차이는 거의 없다.
- **delegate 판 가장자리 모양 갱신**: Paper 는 기록 중에도 TreeFeature 의 `updateShapeAtEdge` 를 기록에 담는다(잎은 틱 예약만, 덩굴만 실제로 바뀐다).
  Furnace 기록 판은 이 갱신을 빼고(블록 엔티티 NBT 도 빠진다), apply 판은 실제 월드에서 한다.
- Paper 기록 중 "부술 수 없는 블록(기반암 등) 보호"는 나무 feature 가 그런 칸을 바꾸지 않으므로 따로 두지 않았다.

## 뒤 조각
- `generateTree(Location, Random, TreeType, Consumer<? super BlockState>)`·`Predicate` 판(RegionAccessor): Paper `BlockStateListPopulator` 는
  **마지막에 쓴 차례**(remove 뒤 put)로 상태를 모으고 `update(true, true)` 로 놓는다. FAWE 는 쓰지 않아 이번에 넣지 않았다.
- E4c `Player#setViewDistance`, E4d `WorldCreator#createWorld`.
