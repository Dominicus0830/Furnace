# M4g-d Feature Placement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port vanilla 1.21.1 feature decoration (ore blobs, springs, disks, geodes, freeze) to Furnace for bit-exact parity with oracle output.

**Architecture:** After carvers, `FeaturePlacer.decorate()` gathers unique biomes from the chunk, builds per-step merged feature lists, seeds `WorldgenRandom` via `setDecorationSeed`+`setFeatureSeed`, then calls each `PlacedFeature.place()` through a `FeatureWorldView` facade. See spec: `docs/superpowers/specs/2026-06-20-m4gd-feature-placement-design.md`.

**Tech Stack:** Java 21, existing WorldgenRandom/LegacyRandom stack, Gson JSON loading, JUnit 5 parity tests.

---

## Key Facts Before Starting (vanilla 소스 확인 완료)

- **Pipeline position:** `generateWithFeatures()` = generate() + applyCarvers() + collectNeighborBiomes() + featurePlacer.decorate()
- **Feature step count:** **11 steps** (ordinal 0~10, RAW_GENERATION~TOP_LAYER_MODIFICATION) — 확인 완료.
- **setDecorationSeed:** `| 1L` 있음, 공식 `((x*a) + (z*b)) ^ seed` — carver와 다름. 확인 완료.
- **setFeatureSeed:** `setSeed(decorationSeed + featureIndex + 10000L * stepOrdinal)` — 확인 완료.
- **featureIndex:** running counter 아님. `FeatureSorter`가 모든 바이옴 통합 위상 정렬로 부여한 **step별 리스트 내 위치**. step 간 독립.
- **바이옴 수집 범위:** 현재 청크만이 아니라 **3×3 이웃 청크** 전체 섹션 바이옴. 블록 생성 없이 biome quart 샘플만 필요.
- **FeatureSorter:** NoiseWorldSource init 시 1회 빌드, 재사용. DFS 위상 정렬 + step별
  index는 **identity 룩업**(`Reference2IntOpenHashMap`, 바닐라 `Util.createIndexIdentityLookup`).
  placed_feature는 id로 interning한 단일 인스턴스 공유 (Task 5/5b).
- **Data path:** `worldgen/1.21.1/worldgen/biome/*.json` → `"features":[step0list, ..., step10list]`.
- **OreFeature algorithm:** Scatter `size` ellipsoids around random center; replace target blocks.
- **Fixture format:** magic `0x46454154` ("FEAT") + 20B header + HEIGHT×16×16×int16 state IDs.
- **Oracle:** Must add `feature:cx:cz` command to OracleMain. Needs `WorldGenLevel`.

## File Map

| Action | Path |
|--------|------|
| Modify | `furnace-server/src/main/java/org/domi/furnace/server/world/gen/carver/WorldgenRandom.java` |
| Create | `furnace-server/src/main/java/org/domi/furnace/server/world/gen/feature/FeatureWorldView.java` |
| Create | `furnace-server/src/main/java/org/domi/furnace/server/world/gen/feature/PlacementModifier.java` |
| Create | `furnace-server/src/main/java/org/domi/furnace/server/world/gen/feature/PlacedFeature.java` |
| Create | `furnace-server/src/main/java/org/domi/furnace/server/world/gen/feature/ConfiguredFeature.java` |
| Create | `furnace-server/src/main/java/org/domi/furnace/server/world/gen/feature/BiomeFeatureData.java` |
| Create | `furnace-server/src/main/java/org/domi/furnace/server/world/gen/feature/FeatureSorter.java` |
| Create | `furnace-server/src/main/java/org/domi/furnace/server/world/gen/feature/FeaturePlacer.java` |
| Create | `furnace-server/src/main/java/org/domi/furnace/server/world/gen/feature/OreFeature.java` |
| Create | `furnace-server/src/main/java/org/domi/furnace/server/world/gen/feature/SpringFeature.java` |
| Create | `furnace-server/src/main/java/org/domi/furnace/server/world/gen/feature/DiskFeature.java` |
| Create | `furnace-server/src/main/java/org/domi/furnace/server/world/gen/feature/FreezeTopLayerFeature.java` |
| Modify | `furnace-server/src/main/java/org/domi/furnace/server/world/gen/NoiseWorldSource.java` |
| Modify | `worldgen-oracle/src/main/java/org/domi/oracle/OracleMain.java` |
| Create | `furnace-server/src/test/java/org/domi/furnace/server/world/gen/FeatureParityTest.java` |
| Create | `furnace-server/src/test/resources/worldgen/fixtures/feature_*.bin` (3 fixtures) |

---

## Task 1: Verify Vanilla Seeding & Step Count

**Goal:** Pin down the 3 uncertain facts before implementation.

- [x] **Step 1: Check GenerationStep.Decoration in vanilla source**

  Decompile 또는 paperweight 캐시에서 확인:
  ```
  grep -r "GenerationStep" ~/.gradle/caches/paperweight/
  ```
  또는 OracleMain에 임시 print 추가:
  ```java
  for (GenerationStep.Decoration s : GenerationStep.Decoration.values()) {
      System.out.println(s.ordinal() + " " + s.name());
  }
  ```
  Run: `.\gradlew.bat :worldgen-oracle:run --args="0 dumpsurfacerule"` (임시로 steps dump로 교체)
  Expected: 몇 개 step인지 + 이름 목록 출력.

- [x] **Step 2: Read setDecorationSeed source**

  paperweight 캐시에서 WorldgenRandom.java 찾기:
  ```
  find ~/.gradle -name "WorldgenRandom.java" -path "*/sources/*"
  ```
  `setDecorationSeed` 메서드 전체 복사 후 결과 기록.
  특히 `| 1L` 유무, `^` vs `+` 연산 확인.

- [x] **Step 3: Read applyBiomeDecoration counter logic**

  `ChunkGenerator.java` 에서 `applyBiomeDecoration` 찾아 decoratorCount/seedModifier 루프 확인.
  질문: step 시작마다 리셋? 아니면 전체 누적?

- [x] **Step 4: 결과 이 문서 Key Facts에 업데이트**

  확인된 값으로 ⚠️ 항목 수정.

---

## Task 2: Oracle Extension — dumpFeatureChunk

**Files:**
- Modify: `worldgen-oracle/src/main/java/org/domi/oracle/OracleMain.java`

- [ ] **Step 1: 실제 WorldGenRegion 구성 (stub 금지)**

  > **[리뷰 반영 — oracle 충실도 = 최대 리스크]** fixture가 ground truth라 oracle이
  > 틀리면 Furnace가 틀린 정답을 쫓는다. 손으로 깎은 `WorldGenLevel` stub은 biome
  > 룩업·height accessor 한 군데만 어긋나도 fixture 오염 → **stub 새로 짜지 말고 실제
  > `WorldGenRegion`(9-chunk ProtoChunk) 재사용**.

  OracleMain에서 `applyBiomeDecoration` 호출 경로 찾기. 실제 `WorldGenRegion` 생성:
  ```java
  // 목표: feature:cx:cz 생성 시 아래 호출
  generator.applyBiomeDecoration(worldGenLevel, chunk, structureManager);
  ```
  9개 이웃 ProtoChunk(biome 섹션 채워짐) + registryAccess(STRUCTURE/PLACED_FEATURE) 필요.
  `StructureManager`는 `shouldGenerateStructures()=false` stub 가능(구조물 M4g-e 연기).

- [ ] **Step 1b: oracle 자가검증 (fixture 박제 전 선행)**

  같은 seed로 vanilla 서버가 실제 생성한 청크 1개를 dump해 oracle dump와 대조.
  일치해야 oracle 신뢰 → fixture 생성 진행. 불일치 시 WorldGenRegion 구성 수정.

- [ ] **Step 2: dumpFeatureChunk 구현**

  ```java
  private static void dumpFeatureChunk(
      long seed, int cx, int cz,
      NoiseBasedChunkGenerator generator,
      RandomState randomState,
      NoiseGeneratorSettings settings,
      Aquifer.FluidPicker fluidPicker,
      Registry<Biome> biomeRegistry,
      MultiNoiseBiomeSource biomeSource,
      RegistryAccess registryAccess,
      Path outDir
  ) throws Exception {
      // 1. fillFromNoise
      // 2. buildSurface
      // 3. carvers (reuse dumpCarverChunk 로직)
      // 4. applyBiomeDecoration (feature 배치)
      // 5. dump uint16 state IDs
      // magic: 0x46454154 ("FEAT")
  }
  ```

  `args[i].startsWith("feature:")` 분기 추가.

- [ ] **Step 3: 빌드 확인**

  Run: `.\gradlew.bat :worldgen-oracle:build`
  Expected: BUILD SUCCESS

---

## Task 3: Generate Fixtures

**Files:**
- Create: `furnace-server/src/test/resources/worldgen/fixtures/feature_{seed}_{cx}_{cz}.bin`

- [ ] **Step 1: 3개 fixture 생성**

  ```
  .\gradlew.bat :worldgen-oracle:run --args="0 feature:0:0"
  .\gradlew.bat :worldgen-oracle:run --args="0 feature:0:5"
  .\gradlew.bat :worldgen-oracle:run --args="123456789 feature:0:0"
  ```

- [ ] **Step 2: 파일 존재 확인**

  ```
  ls furnace-server/src/test/resources/worldgen/fixtures/feature_*.bin
  ```
  Expected: 3 files.

---

## Task 4: WorldgenRandom — setDecorationSeed + setFeatureSeed

**Files:**
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/world/gen/carver/WorldgenRandom.java`

- [ ] **Step 1: Write failing test**

  ```java
  @Test void decorationSeed_matchesVanilla() {
      // Task 1에서 확인한 vanilla 값과 대조
      WorldgenRandom r = new WorldgenRandom();
      long decorSeed = r.setDecorationSeed(0L, 0, 0);
      assertEquals(<VANILLA_VALUE>, decorSeed);
  }

  @Test void featureSeed_deterministic() {
      WorldgenRandom r = new WorldgenRandom();
      r.setFeatureSeed(12345L, 3, 6);
      long first = r.nextLong();
      r.setFeatureSeed(12345L, 3, 6);
      assertEquals(first, r.nextLong());
  }
  ```

  Run: `.\gradlew.bat :furnace-server:test --tests "*WorldgenRandomTest"`
  Expected: compile fail (메서드 없음).

- [ ] **Step 2: Implement**

  ```java
  // 확인 완료: | 1L 있음, * 후 + 합산, 마지막 ^ worldSeed (연산자 우선순위 * > + > ^)
  public long setDecorationSeed(long worldSeed, int blockX, int blockZ) {
      setSeed(worldSeed);
      long a = nextLong() | 1L;
      long b = nextLong() | 1L;
      return ((long) blockX * a + (long) blockZ * b) ^ worldSeed;
  }

  // featureIndex = FeatureSorter step별 전역 index (러닝 카운터 아님 — Task 5b 참조)
  public void setFeatureSeed(long decorationSeed, int featureIndex, int stepOrdinal) {
      setSeed(decorationSeed + featureIndex + 10000L * stepOrdinal);
  }
  ```

- [ ] **Step 3: Run test**

  Run: `.\gradlew.bat :furnace-server:test --tests "*WorldgenRandomTest"`
  Expected: PASS

---

## Task 5: BiomeFeatureData — 데이터 로딩

**Files:**
- Create: `furnace-server/src/main/java/org/domi/furnace/server/world/gen/feature/BiomeFeatureData.java`

- [ ] **Step 1: Write failing test**

  ```java
  @Test void biomeFeatureData_plainsHasOres() {
      WorldgenData data = WorldgenData.load("worldgen/1.21.1");
      BiomeFeatureData bfd = BiomeFeatureData.load(data);
      List<PlacedFeature> ores = bfd.featuresForStep("minecraft:plains", 6);
      assertFalse(ores.isEmpty(), "plains step 6 should have ores");
  }
  ```

  Run: `.\gradlew.bat :furnace-server:test --tests "*BiomeFeatureDataTest"`
  Expected: compile fail.

- [ ] **Step 2: Implement**

  ```java
  public final class BiomeFeatureData {
      // biomeId → List<List<PlacedFeature>> (outer=step, inner=features)
      private final Map<String, List<List<PlacedFeature>>> biomeFeatures;

      public static BiomeFeatureData load(WorldgenData data) {
          // 각 biome JSON 로드
          // "features" 배열 파싱 → PlacedFeature 객체 생성
          // placed_feature.json, configured_feature.json 재귀 로드
      }

      public List<PlacedFeature> featuresForStep(String biomeId, int step) { ... }
  }
  ```

  Gson으로 `worldgen/1.21.1/worldgen/biome/*.json` 로드.
  없는 referenced resource는 warn + skip (복잡한 feature 타입 점진 추가).

  > **[리뷰 반영 — interning 필수]** placed_feature를 **id 키로 인터닝**해 인스턴스
  > 1개만 캐시(`Map<String, PlacedFeature>`)하고, 모든 바이옴이 그 단일 인스턴스를
  > 참조하게 한다. 같은 id를 바이옴마다 재파싱해 별개 인스턴스를 만들면 Task 5b의
  > identity 룩업이 전부 부재 → 조용히 feature 누락. spec의 FeatureSorter §,
  > Known Traps 참조.

- [ ] **Step 3: Run test**

  Run: `.\gradlew.bat :furnace-server:test --tests "*BiomeFeatureDataTest"`
  Expected: PASS

---

## Task 5b: FeatureSorter — 전역 위상 정렬 + identity index

**Files:**
- Create: `furnace-server/src/main/java/org/domi/furnace/server/world/gen/feature/FeatureSorter.java`

> 누락됐던 핵심 단계. 바닐라 `FeatureSorter.buildFeaturesPerStep` 포팅. featureIndex의
> 출처 — 이게 없으면 seeding 전부 틀림.

- [ ] **Step 1: Write failing test**

  ```java
  @Test void featureSorter_assignsStablePerStepIndex() {
      BiomeFeatureData bfd = BiomeFeatureData.load(WorldgenData.load("worldgen/1.21.1"));
      FeatureSorter sorter = FeatureSorter.build(bfd);
      // 두 바이옴이 공유하는 동일 placed_feature는 같은 step·같은 index
      var step6 = sorter.featuresPerStep().get(6);
      assertTrue(step6.features().size() > 0);
      // interning 검증: bfd가 준 인스턴스를 identity 룩업으로 찾음
      PlacedFeature f = bfd.featuresForStep("minecraft:plains", 6).get(0);
      assertTrue(step6.indexMap().getInt(f) >= 0);
  }
  ```

- [ ] **Step 2: Implement — 바닐라 buildFeaturesPerStep DFS 포팅**

  ```java
  public record StepFeatureData(
      List<PlacedFeature> features,
      Reference2IntMap<PlacedFeature> indexMap  // IDENTITY (Util.createIndexIdentityLookup 대응)
  ) {}
  ```
  - 각 바이옴 feature 리스트 순서로 "A→B" 의존 엣지 추가 (TreeMap + comparator: step, featureIndex)
  - `Graph.depthFirstSearch` 동등 DFS → 역순 = 실행 순서, 사이클 시 IllegalStateException
  - step별 `StepFeatureData` 빌드, indexMap은 **`Reference2IntOpenHashMap`(identity)** 로
    리스트 위치 매핑

  > **[리뷰 반영]** indexMap은 equals 기반(`Object2IntOpenHashMap`)이 아니라 **identity**.
  > Task 5의 interning과 짝. 부재(-1)는 interning 깨짐 신호 → collect 단계에서 throw.

- [ ] **Step 3: Run test**

  Run: `.\gradlew.bat :furnace-server:test --tests "*FeatureSorterTest"`
  Expected: PASS

---

## Task 6: PlacementModifier Core

**Files:**
- Create: `furnace-server/src/main/java/org/domi/furnace/server/world/gen/feature/PlacementModifier.java`
- Create: `furnace-server/src/main/java/org/domi/furnace/server/world/gen/feature/PlacedFeature.java`

- [ ] **Step 1: Write failing test**

  ```java
  @Test void countPlacement_generatesNPositions() {
      CountPlacement count = new CountPlacement(3);
      WorldgenRandom r = new WorldgenRandom(); r.setSeed(42L);
      List<BlockPos> positions = count.getPositions(r, new BlockPos(0, 64, 0));
      assertEquals(3, positions.size());
  }
  ```

- [ ] **Step 2: Implement PlacementModifier sealed interface**

  우선 구현 대상:
  - `CountPlacement(int count)` — count번 반복
  - `RarityFilterPlacement(int chance)` — 1/chance 확률
  - `InSquarePlacement` — nextInt(16) XZ 오프셋
  - `HeightRangePlacement(HeightProvider provider)` — Y 범위 샘플
  - `BiomeFilter` — 위치 바이옴 확인 (항상 pass, strict 모드는 나중)
  - `SurfaceRelativeThresholdFilter(Heightmap type, int minInclusive, int maxInclusive)`

  각 modifier는 `Stream<BlockPos> filter(WorldgenRandom, BlockPos)` 반환.
  체인: `positions = startPositions; for mod in placement: positions = positions.flatMap(pos -> mod.filter(r, pos))`

  > **[리뷰 반영 — Stream 게으름 보존 필수]** 단일 공유 `random`을 modifier 체인과
  > feature.place가 함께 소비. 게으른 flatMap+forEach는 시작점당 **깊이우선으로
  > modifier-random과 feature-random을 교차 소비**(바닐라 `PlacedFeature.placeWithContext`).
  > **절대 금지**: positions를 `List`로 모아(eager) "전체 modifier 후 전체 배치"로 바꾸기 →
  > random 순서 역전 → 전 청크 parity 붕괴. 각 modifier의 random 소비 시점도 바닐라
  > 해당 modifier와 동일하게(호출 즉시 vs 원소당) 복제 — modifier별 단위테스트로 검증.

- [ ] **Step 3: Run test**

  Run: `.\gradlew.bat :furnace-server:test --tests "*PlacementModifierTest"`
  Expected: PASS

---

## Task 7: FeatureWorldView

**Files:**
- Create: `furnace-server/src/main/java/org/domi/furnace/server/world/gen/feature/FeatureWorldView.java`

- [ ] **Step 1: Write failing test**

  ```java
  @Test void featureWorldView_getSetBlock() {
      ChunkColumn col = new ChunkColumn(6);
      col.setBlock(0, 0, 0, 1); // stone
      FeatureWorldView view = new FeatureWorldView(col, 0, 0);
      assertEquals(1, view.getBlock(0, -64, 0));
      view.setBlock(0, -64, 0, 2);
      assertEquals(2, col.getBlock(0, -64, 0));
  }
  ```

- [ ] **Step 2: Implement**

  ```java
  public final class FeatureWorldView {
      private final ChunkColumn col;
      private final int cx, cz;
      private final int[] biomeIds; // 1536 quart biome ids

      public int getBlock(int worldX, int worldY, int worldZ) { ... }
      public void setBlock(int worldX, int worldY, int worldZ, int stateId) {
          int lx = worldX - cx * 16; int lz = worldZ - cz * 16;
          if (lx < 0 || lx >= 16 || lz < 0 || lz >= 16) return; // 이웃 청크 무시
          col.setBlock(lx, worldY, lz, stateId);
      }
      public int getSurfaceY(int worldX, int worldZ) { ... }
      public int getBiomeId(int worldX, int worldY, int worldZ) { ... }
  }
  ```

- [ ] **Step 3: Run test**

  Run: `.\gradlew.bat :furnace-server:test --tests "*FeatureWorldViewTest"`
  Expected: PASS

---

## Task 8: OreFeature

**Files:**
- Create: `furnace-server/src/main/java/org/domi/furnace/server/world/gen/feature/OreFeature.java`

- [ ] **Step 1: Write failing test**

  ```java
  @Test void oreFeature_placesOreBlocks() {
      ChunkColumn col = filledWithStone();
      FeatureWorldView view = new FeatureWorldView(col, 0, 0);
      OreFeature ore = new OreFeature(stoneToIron, 9, 0.0f); // iron ore, size=9, no discard
      WorldgenRandom r = new WorldgenRandom(); r.setSeed(42L);
      ore.place(view, r, 0, 0, 0);
      long ironCount = countBlock(col, ironOreId);
      assertTrue(ironCount > 0 && ironCount <= 9);
  }
  ```

- [ ] **Step 2: Implement OreFeature**

  바닐라 알고리즘:
  ```
  // size개 블록 랜덤 산포 (ellipsoid scatter)
  // xzScale, yScale → 타원 계수
  // discard_chance_on_air_exposure: 각 target 블록 인접 공기 확인 후 random < chance면 skip
  // target: List<OreTarget> (targetBlock → replaceBlock)
  ```

  `OreConfiguration` (vanilla JSON config) 파싱:
  ```json
  {"size": 9, "discard_chance_on_air_exposure": 0.0,
   "targets": [{"target": {"predicate_type":"...", "blocks":["minecraft:stone"]},
                "state": {"Name":"minecraft:iron_ore"}}]}
  ```

- [ ] **Step 3: Run test**

  Run: `.\gradlew.bat :furnace-server:test --tests "*OreFeatureTest"`
  Expected: PASS

---

## Task 9: FeaturePlacer + NoiseWorldSource Integration

**Files:**
- Create: `furnace-server/src/main/java/org/domi/furnace/server/world/gen/feature/FeaturePlacer.java`
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/world/gen/NoiseWorldSource.java`

- [ ] **Step 1: Write failing test (parity test stub)**

  ```java
  @Test void featureChunk_seed0_cx0_cz0() throws Exception {
      // fixture 로드, generateWithFeatures() 호출, 비교
      // 처음엔 대규모 mismatch 예상 — 출력 보고 iterative fix
  }
  ```

- [ ] **Step 2: Implement FeaturePlacer.decorate()**

  > **[리뷰 반영 — 러닝 카운터 금지]** featureIndex는 청크별 누적 카운터가 **아니라**
  > Task 5b `FeatureSorter`가 부여한 **step별 전역 index**다. 바닐라는 이 청크의 unique
  > biome들이 가진 feature의 sorter index를 모아 정렬 후, 각 index를 그대로 setFeatureSeed에
  > 넘긴다 (다른 바이옴이 같은 feature 공유 시 같은 seed). 아래 코드가 정본.

  ```java
  public void decorate(FeatureWorldView view, Set<String> biomeSet, int cx, int cz) {
      WorldgenRandom random = new WorldgenRandom();
      long decorSeed = random.setDecorationSeed(seed, cx * 16, cz * 16);
      WorldgenRandom featRandom = new WorldgenRandom();

      for (int step = 0; step < 11; step++) {
          FeatureSorter.StepFeatureData stepData = sorter.featuresPerStep().get(step);
          // 이 step·biomeSet에 등장하는 feature의 sorter index 수집 (정렬·중복제거)
          IntSortedSet indices = new IntRBTreeSet();
          for (String biome : biomeSet) {
              for (PlacedFeature f : biomeData.featuresForStep(biome, step)) {
                  int idx = stepData.indexMap().getInt(f);  // identity 룩업
                  if (idx < 0) throw new IllegalStateException("not interned: " + f);
                  indices.add(idx);
              }
          }
          for (int idx : indices) {  // 오름차순
              featRandom.setFeatureSeed(decorSeed, idx, step);
              stepData.features().get(idx).place(view, featRandom, cx * 16, cz * 16);
          }
      }
  }
  ```

- [ ] **Step 3: generateWithFeatures() 추가**

  ```java
  public ChunkColumn generateWithFeatures(int chunkX, int chunkZ) {
      ChunkColumn col = generate(chunkX, chunkZ);
      NoiseChunk nc = new NoiseChunk(chunkX, chunkZ, router, settings);
      applyCarvers(col, chunkX, chunkZ, nc);
      int[] biomeIds = biomeSampler.sampleChunk(chunkX, chunkZ, nc); // center, FeatureWorldView용
      Set<String> biomeSet = collectNeighborBiomes(chunkX, chunkZ);   // 3×3 이웃 (spec 참조)
      FeatureWorldView view = new FeatureWorldView(col, chunkX, chunkZ, biomeIds);
      featurePlacer.decorate(view, biomeSet, chunkX, chunkZ);
      col.clearDirty();
      return col;
  }
  // collectNeighborBiomes: 9개 NoiseChunk로 biomeId 수집 → registryBiomeNames 매핑 → Set.
  // 주의: dx=0,dz=0(center) nc 재사용. spec Integration § + "비용 ~18ms" 최적화 노트 참조.
  ```

- [ ] **Step 4: Run parity test, observe mismatches**

  Run: `.\gradlew.bat :furnace-server:test --tests "*FeatureParityTest"`
  Expected: 처음엔 mismatch. 출력 분석 후 Task 10~11로 fix.

---

## Task 10: SpringFeature + DiskFeature

**Files:**
- Create: `furnace-server/src/main/java/org/domi/furnace/server/world/gen/feature/SpringFeature.java`
- Create: `furnace-server/src/main/java/org/domi/furnace/server/world/gen/feature/DiskFeature.java`

- [ ] **Step 1: SpringFeature 구현**

  조건: 인접 6면 중 rock 면 4개 이상, target fluid 미설치. 단일 블록.

- [ ] **Step 2: DiskFeature 구현**

  원형 disk: center XZ + radius, Y=0..1. gravel/clay/sand 패치.

- [ ] **Step 3: Run parity test**

  Run: `.\gradlew.bat :furnace-server:test --tests "*FeatureParityTest"`
  Expected: mismatch 감소.

---

## Task 11: FreezeTopLayerFeature

**Files:**
- Create: `furnace-server/src/main/java/org/domi/furnace/server/world/gen/feature/FreezeTopLayerFeature.java`

- [ ] **Step 1: Implement FreezeTopLayerFeature**

  - 청크 전체 XZ 순회
  - 각 컬럼 최상단 블록 찾기
  - 물 표면 → ice(79), 공기 + 바이옴 cold → snow_layer

- [ ] **Step 2: Run parity test**

  Run: `.\gradlew.bat :furnace-server:test --tests "*FeatureParityTest"`
  Expected: 추가 mismatch 감소.

---

## Task 12: Full Parity — 0 mismatch

- [ ] **Step 1: Run all 3 fixture parity tests**

  Run: `.\gradlew.bat :furnace-server:test --tests "*FeatureParityTest"`
  Expected: 3 fixtures 0 mismatch.

  미달 시: mismatch 출력 분석 → 누락 feature 타입 구현 또는 버그 픽스.

- [ ] **Step 2: Run full test suite**

  Run: `.\gradlew.bat :furnace-server:test`
  Expected: ALL PASS (기존 CarverParityTest, AquiferParityTest, SurfaceParityTest 등 회귀 없음)

- [ ] **Step 3: Run benchmark**

  Run: `.\gradlew.bat :furnace-server:test --tests "*ChunkGenBenchmark"`
  Expected: PASS + timing 출력 (feature 추가로 인한 지연 측정)

---

## Task 13: Commit

- [ ] **Step 1: Stage relevant files**

  ```
  git add furnace-server/src/main/java/org/domi/furnace/server/world/gen/feature/
  git add furnace-server/src/main/java/org/domi/furnace/server/world/gen/carver/WorldgenRandom.java
  git add furnace-server/src/main/java/org/domi/furnace/server/world/gen/NoiseWorldSource.java
  git add furnace-server/src/test/java/org/domi/furnace/server/world/gen/FeatureParityTest.java
  git add furnace-server/src/test/resources/worldgen/fixtures/feature_*.bin
  git add worldgen-oracle/src/main/java/org/domi/oracle/OracleMain.java
  ```

- [ ] **Step 2: Commit**

  ```
  git commit -m "M4g-d feature placement: ore blobs + springs + disks + freeze, 3 fixture 0 mismatch"
  ```
