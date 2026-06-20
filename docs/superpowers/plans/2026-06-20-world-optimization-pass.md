# World Optimization Pass Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce world generation and world-management overhead without changing vanilla world parity or player-visible behavior.

**Architecture:** Keep the existing worldgen pipeline and ownership model, but remove repeated scans, repeated cloning, and repeated bookkeeping from the hottest paths. The work is split into four focused areas: `NoiseChunk/Aquifer`, `SurfaceSystem`, chunk snapshot/serialization, and region persistence/eviction bookkeeping.

**Tech Stack:** Java, Gradle, JUnit 5, existing parity/benchmark tests, Netty `ByteBuf`, existing region-thread ownership model

---

### Task 1: Reduce `NoiseChunk` per-cell overhead

**Files:**
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/world/gen/df/DensityFunctions.java`
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/world/gen/NoiseChunk.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/world/gen/df/DensityFunctionsTest.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/world/gen/NoiseChunkTest.java`

- [ ] **Step 1: Write the failing test**

```java
@Test void cellBoundsInto_matchesAllocatingVersion(){
    var f = DensityFunctions.mul(
        DensityFunctions.add(DensityFunctions.constant(-2.0), DensityFunctions.constant(5.0)),
        DensityFunctions.yClampedGradient(-64, 320, -1.0, 1.0).clamp(-0.5, 0.75)
    );
    double[] expected = DensityFunctions.cellBounds(f, df -> null, -16, 31);
    double[] actual = new double[2];
    DensityFunctions.cellBounds(f, (df, out) -> false, -16, 31, actual);
    assertEquals(expected[0], actual[0], 0.0);
    assertEquals(expected[1], actual[1], 0.0);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :furnace-server:test --tests "*DensityFunctionsTest"`
Expected: `compileTestJava` or test failure because the new `cellBounds(..., out)` overload does not exist yet.

- [ ] **Step 3: Write minimal implementation**

```java
public interface CellBoundsOracleInto {
    boolean boundsOf(DensityFunction df, double[] out);
}

public static void cellBounds(DensityFunction df, CellBoundsOracleInto oracle, int yMin, int yMax, double[] out) {
    // same semantics as existing cellBounds, but writes into out[0], out[1]
}
```

Then update `NoiseChunk` to keep a reusable `double[2] cellBoundsScratch` and call the new overload instead of allocating new arrays per cell.

- [ ] **Step 4: Run tests to verify correctness**

Run: `.\gradlew.bat :furnace-server:test --tests "*DensityFunctionsTest" --tests "*NoiseChunkTest" --tests "*AquiferParityTest" --tests "*SurfaceParityTest"`
Expected: PASS

---

### Task 2: Reduce `SurfaceSystem` column scanning

**Files:**
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/world/ChunkColumn.java`
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/world/gen/surface/SurfaceSystem.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/world/ChunkColumnTest.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/world/gen/SurfaceParityTest.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
void surfaceBlockY_readsPrecomputedSurface() throws Exception {
    ChunkColumn c = new ChunkColumn(6);
    c.setSurface(0, 0, 4);
    c.setSurface(1, 0, 0);

    assertEquals(ChunkColumn.MIN_Y + 3, c.surfaceBlockY(0, 0));
    assertEquals(ChunkColumn.MIN_Y - 1, c.surfaceBlockY(1, 0));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :furnace-server:test --tests "*ChunkColumnTest"`
Expected: failure because `surfaceBlockY` does not exist yet.

- [ ] **Step 3: Write minimal implementation**

```java
public int surfaceBlockY(int x, int z) {
    return MIN_Y + surface[z * 16 + x] - 1;
}
```

Then replace the initial top-down `getBlock` scan in `SurfaceSystem.buildSurface(...)` with `col.surfaceBlockY(lx, lz)`.

- [ ] **Step 4: Run tests to verify correctness**

Run: `.\gradlew.bat :furnace-server:test --tests "*ChunkColumnTest" --tests "*SurfaceParityTest"`
Expected: PASS

---

### Task 3: Cache heightmap/snapshot work

**Files:**
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/world/ChunkColumn.java`
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/world/World.java`
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/world/ChunkSnapshot.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/world/ChunkColumnTest.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/net/protocol/play/ChunkSerializerTest.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/world/anvil/AnvilChunkCodecTest.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
void heightmap_isCached_untilSurfaceChanges() {
    ChunkColumn c = new ChunkColumn(6);
    c.setSurface(0, 0, 4);

    long[] first = c.motionBlockingHeightmap();
    long[] second = c.motionBlockingHeightmap();
    assertSame(first, second);

    c.setSurface(0, 0, 7);
    long[] third = c.motionBlockingHeightmap();
    assertNotSame(first, third);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :furnace-server:test --tests "*ChunkColumnTest"`
Expected: failure because a new `long[]` is created every time.

- [ ] **Step 3: Write minimal implementation**

```java
private long[] cachedMotionBlockingHeightmap;

public long[] motionBlockingHeightmap() {
    if (cachedMotionBlockingHeightmap != null) return cachedMotionBlockingHeightmap;
    // rebuild once, store, return
}
```

Also keep snapshot/serialized-body paths using clean-chunk caches only when dirty state is still clear on the owner thread.

- [ ] **Step 4: Run tests to verify correctness**

Run: `.\gradlew.bat :furnace-server:test --tests "*ChunkColumnTest" --tests "*ChunkSerializerTest" --tests "*AnvilChunkCodecTest" --tests "*ChunkSaveTest"`
Expected: PASS

---

### Task 4: Track dirty chunks directly for autosave/eviction

**Files:**
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/world/ChunkColumn.java`
- Modify: `furnace-server/src/main/java/org/domi/furnace/server/world/Region.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/world/AutosaveTest.java`
- Test: `furnace-server/src/test/java/org/domi/furnace/server/world/ChunkSaveTest.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
void autosave_onlyTouchesDirtyChunks(@TempDir Path dir) throws Exception {
    // create world, load multiple chunks, dirty only one
    // trigger autosave
    // assert dirty chunk was saved and clean chunk state stayed untouched
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :furnace-server:test --tests "*AutosaveTest" --tests "*ChunkSaveTest"`
Expected: failure or missing coverage for direct dirty-set behavior.

- [ ] **Step 3: Write minimal implementation**

```java
private final Set<ChunkPos> dirtyChunks = new HashSet<>();

private void markDirty(ChunkPos cp, ChunkColumn col) {
    if (col.dirty()) dirtyChunks.add(cp);
}

public void saveDirtyChunks() {
    for (ChunkPos cp : new ArrayList<>(dirtyChunks)) {
        ChunkColumn col = chunks.get(cp);
        if (col != null && col.dirty()) { ... }
        dirtyChunks.remove(cp);
    }
}
```

Use the same direct dirty tracking in eviction so idle clean chunks do not require a full save scan.

- [ ] **Step 4: Run tests to verify correctness**

Run: `.\gradlew.bat :furnace-server:test --tests "*AutosaveTest" --tests "*ChunkSaveTest" --tests "*AsyncLoadTest"`
Expected: PASS

---

### Task 5: Run end-to-end verification and benchmark

**Files:**
- Test only

- [ ] **Step 1: Run focused parity and world tests**

Run: `.\gradlew.bat :furnace-server:test --tests "*SpawnBatchTest" --tests "*WorldSpawnTest" --tests "*SurfaceParityTest" --tests "*AquiferParityTest" --tests "*NoiseChunkTest"`
Expected: PASS

- [ ] **Step 2: Run benchmark**

Run: `.\gradlew.bat :furnace-server:test --tests "*ChunkGenBenchmark"`
Expected: PASS with fresh timing output in `furnace-server/build/test-results/test/TEST-org.domi.furnace.server.world.gen.ChunkGenBenchmark.xml`

- [ ] **Step 3: Summarize actual measured deltas**

Record:
- `generate() server path, no carvers`
- `generateBlockIds(final density + aquifer)`
- `SurfaceSystem.buildSurface`
- any observed world-management improvement that is not captured by the generation benchmark
