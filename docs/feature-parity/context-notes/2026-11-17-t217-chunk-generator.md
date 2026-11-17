## 2026-11-17 Bukkit 청크 생성기로 만드는 월드 (T-217)

설계: `docs/feature-parity/compat-t217-chunk-generator-worlds-2026-11-17.md`. 결정: `docs/user-todo/entries/2026-11-17-t217-chunk-generator.md`.

### Paper 대조 (Paper 저장소 원문 — `CustomChunkGenerator`·`CraftChunkData`·`OldCraftChunkData`·`CraftLimitedRegion`·`RandomSourceWrapper`·`ChunkGenerator.patch`)
- 생성기에 넘기는 난수는 `RandomWrapper(new WorldgenRandom(new LegacyRandomSource(0)))`. `RandomWrapper` 는 `java.util.Random` 하위 클래스로
  `setSeed`·`nextInt()`·`nextInt(n)`·`nextLong`·`nextBoolean`·`nextFloat`·`nextDouble`·`nextGaussian`·`nextInt(a, b)` 만 넘긴다. 나머지 메서드는
  Random 자체 상태(생성자의 `setSeed` 가 무시돼 0)를 쓴다 — Furnace `PaperRandom` 도 같은 묶음만 덮어쓴다.
- 씨앗: `shouldGenerate*` = `Mth.getSeed(x, "should-noise|surface|caves|structures|decoration|mobs".hashCode(), z) ^ 월드 시드`,
  `generateNoise/Surface/Bedrock`·`getBaseHeight`(청크 좌표)·옛 `generateChunkData`(`java.util.Random`) = `x*341873128712 + z*132897987541`,
  `generateBedrock` 은 같은 씨앗의 **새** 난수, `generateCaves` = `setDecorationSeed(시드, 0, 0)`(= 시드), BlockPopulator =
  `WorldgenRandom(LegacyRandomSource(seed)).setDecorationSeed(seed, 청크 x, 청크 z)`(블록 좌표가 아니라 청크 좌표 — CraftBukkit 그대로).
- `shouldGenerateBedrock` 은 1.21.1 에서 아무 효과가 없다(바닐라 기반암 줄이 주석 처리됨).
- `shouldGenerateDecorations=false` 는 `addVanillaDecorations` 통째(구조물 조각 + 지형지물)를 건너뛰고 BlockPopulator 는 그래도 돈다.
  Furnace W3 의 `FEATURES` 는 "지형지물만"이라 `DECORATION` 단계를 따로 더했다(`FeaturePlacer` 주석의 "CraftBukkit 과 같다"는 틀린 말이라 고침).
- 옛 API: 돌려준 `OldCraftChunkData` 의 null 아닌 섹션이 청크 섹션을 통째로 바꾸고(안 놓은 칸 공기) 바이옴은 옛 섹션 것을 복사한다. `BiomeGrid` 는
  청크에 바로 쓴다. 처음 `UnsupportedOperationException` 이면 그 생성기에는 다시 부르지 않는다. `isParallelCapable()` 이 거짓이면 생성기 단위로 잠근다.
- `CraftLimitedRegion`: 중심 청크 ±16칸(끝 제외), y 는 [월드 최소, 최대). `getHighestBlockYAt(x, z)` 는 상자 가운데 y 로 영역 검사 뒤
  `WorldGenRegion#getHeight`(첫 빈 칸 y, 기본 MOTION_BLOCKING). 영역 밖 `IllegalArgumentException("Coordinates x, y, z are not in the region")`,
  끝난 뒤 `IllegalStateException`.
- `createWorld`: 생성기가 있고 공급자가 없으면 `generator.getDefaultBiomeProvider(WorldInfo)`. `initWorld` 에서 `getDefaultPopulators(world)` 를
  월드 목록에 더하고(새 월드든 이미 있는 월드든) WorldInitEvent, 새 월드면 `getFixedSpawnLocation(world, new Random(seed))`
  (다른 월드면 `IllegalStateException("Cannot set spawn point for X to be in another world (Y)")`).
- **가정**(바닐라 디컴파일 기억, 확인 못 함): `WorldgenRandom#setSeed` 는 안쪽 원천만 다시 심고 바깥 `LegacyRandomSource` 의 가우스 보관값은 지우지 않는다.
  플러그인이 `nextGaussian` 을 홀수 번 부른 뒤 스스로 `setSeed` 할 때만 영향이 있다.

### 받아들인 차이
- 설계 3장 목록(묻기 횟수, 옛 API 섹션 교체 뒤 `*_WG` 높이맵, T-218·T-219 로 넘긴 것, 고정 스폰을 WorldInitEvent 전에 묻기, 묻기→만들기 사이 가우스 보관값,
  플러그인 예외를 기록하고 계속하기).
- Furnace 에는 bukkit.yml 이 없어 `worlds.<이름>.generator` 를 읽지 않는다(Paper 에서 그 항목이 없을 때와 같다).

### 함정
- 생성기·공급자의 `WorldInfo`(`LateWorldInfo`)는 월드가 생기기 전 `getDefaultBiomeProvider` 에 넘어가므로, 이미 있는 월드면 level.dat 시드로 만들어 둔다
  (CraftBukkit 은 저장된 레벨 데이터로 `CraftWorldInfo` 를 만든다).
- `CraftWorld` 는 같은 월드 핸들이면 하나라 BlockPopulator 목록을 그 객체에 둔다. 처음 `getPopulators()` 때 서버 월드 장식기 하나로 건다.
