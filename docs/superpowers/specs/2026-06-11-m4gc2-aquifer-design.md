# M4g-c2 Aquifer System Design

## Overview

Implement vanilla-compatible aquifer system for Furnace worldgen.
Aquifer fills cave voids (density ≤ 0 zones) with water or lava instead of leaving them as air.
Goal: 0-mismatch parity with vanilla oracle on full-chunk block state IDs, including underground fluid placement.

---

## Section 1: Architecture

### Sealed Interface

```java
public sealed interface Aquifer permits DisabledAquifer, NoiseBasedAquifer {
    /**
     * Called for every block where density <= 0.
     * Returns block state ID: 0=air, water id, lava id.
     */
    int computeSubstance(NoiseChunk.FillContext ctx, double density);
}
```

### DisabledAquifer

Simple sea-level fill — current Furnace behavior, used for superflat / aquifersEnabled=false:

```java
public record DisabledAquifer(int seaLevel, int waterStateId) implements Aquifer {
    @Override
    public int computeSubstance(NoiseChunk.FillContext ctx, double density) {
        return ctx.blockY() <= seaLevel ? waterStateId : 0;
    }
}
```

### NoiseBasedAquifer

Full vanilla port. Used for overworld (aquifersEnabled=true).
Determines underwater fluid type and level via source-grid randomization + pressure calculation.

### NoiseSettings flag

```java
public record NoiseSettings(/* existing fields */ boolean aquifersEnabled) {}
```

Overworld factory sets `aquifersEnabled=true`. Superflat/custom pass `false`.

---

## Section 2: NoiseBasedAquifer Internals

### Source Grid

- Spacing: X=16, Z=16, Y=12 blocks
- Grid cell `(gx, gy, gz)` = `(floorDiv(blockX, 16), floorDiv(blockY, 12), floorDiv(blockZ, 16))`
- `aquiferLocationCache[idx]` — lazy-computed per grid cell, stores source `(blockX, blockY, blockZ)` randomized ±offset within cell
- Seeding: `positionalRandom.at(gx, gy, gz)` per source

Constants (from vanilla):
```java
static final int X_SPACING = 16, Y_SPACING = 12, Z_SPACING = 16;
static final int X_RANGE = 10, Y_RANGE = 9, Z_RANGE = 10;
```

Cache size: `(X_RANGE*2+1) * (Y_RANGE*2+1) * (Z_RANGE*2+1)` entries per chunk.

### computeFluid(gx, gy, gz) → FluidStatus

Returns `(fluidLevel, fluidBlockId)` for grid source. Three-stage:

1. **Floodedness check**: if `fluidLevel ≥ preliminarySurfaceLevel(gx*16, gz*16)` → `WAY_BELOW_MIN_Y` (no fluid, dry)
2. **Randomized level**: base level derived from `gy*12`, offset by RNG within ±Y_SPACING/2
3. **Fluid type**: call `computeFluidType(gx, gy, gz, fluidLevel)`

`WAY_BELOW_MIN_Y = -2048` (sentinel = "no fluid").

### computeFluidType(gx, gy, gz, fluidLevel) → blockId

```java
if (fluidLevel <= -10) {
    double lavaNoise = ...; // sample lava noise
    if (Math.abs(lavaNoise) > 0.3) return LAVA_STATE_ID;
}
return WATER_STATE_ID;
```

Lava noise = `NoiseBasedAquifer`'s dedicated NormalNoise keyed `"minecraft:aquifer_lava"`.

### computeSubstance(ctx, density)

1. Find 4 nearest source grid cells (by squared distance)
2. For each pair (d1, d2): `similarity = 1.0 - Math.abs(d2-d1) / 25.0`
3. If `similarity > 0`: compute `barrierNoise` (sampled at block pos) → check pressure
4. If pressure passes → fluid fills this block; type from nearest source's `FluidStatus`
5. Otherwise → air (0)

`shouldScheduleFluidUpdate`: keep field in FluidStatus, always return false (Furnace has no fluid tick yet).

### preliminarySurfaceLevel(worldX, worldZ) → int

Samples column density at 13 candidate Y positions (vanilla: every 8 blocks from minY to maxY).
Returns highest Y where density transitions positive→negative. Used by `computeFluid` to detect flooded zones.

---

## Section 3: Testing Strategy

### Existing Fixtures Already Cover Aquifer

Oracle `surface:cx:cz` runs vanilla `fillFromNoise` (includes aquifer). Current 3 surface fixtures already have aquifer-correct output. They pass now because those chunks have simple aquifer behavior (matches sea-level fill). They will continue to pass after correct aquifer implementation.

### New Fixtures: Aquifer-Challenging Locations

Need 2–3 new fixture locations where aquifer ≠ simple sea-level fill:

| Target behavior | Example terrain |
|----------------|-----------------|
| Underground lava pocket | y < −54, isolated cave, high lavaNoise |
| Isolated water body below sea-level | sealed chamber, barrier-separated |
| Deep dark / dry region | erosion > 0.4 && depth < −0.75 |

**Fixture selection process (pre-implementation task):**

1. Run oracle on candidate chunks: `./gradlew :worldgen-oracle:run --args="<seed> surface:cx:cz"`
2. Compare oracle output vs Furnace without aquifer (count fluid block mismatches)
3. Pick 2–3 chunks with ≥ 100 fluid mismatches

**Candidate coordinates (seed=0):** `cx=−20,cz=10` / `cx=15,cz=−30` / `cx=0,cz=−40`

### Test Structure

No new fixture format, no oracle changes. Add `@Test` methods to existing `SurfaceParityTest`:

```java
@Test void seed0_aquifer_lava()  throws Exception { assertSurfaceParity(0L, -20, 10); }
@Test void seed0_aquifer_water() throws Exception { assertSurfaceParity(0L, 15, -30); }
```

TDD order: find coordinates → dump fixtures → add tests (fail) → implement → 0 mismatch.

---

## Section 4: NoiseChunk Integration + RandomState

### Fill Loop Change

```java
// Before (NoiseChunk sliceFill):
int block = density > 0 ? stoneId : 0;

// After:
int block;
if (density > 0) {
    block = stoneId;
} else {
    block = aquifer.computeSubstance(ctx, density); // 0=air, water id, lava id
}
```

### RandomState

Add `aquiferRandom` field:

```java
// RandomState.java
public final XoroshiroPositionalRandomFactory aquiferRandom =
    random.fromHashOf("minecraft:aquifer").forkPositional();
```

Same `fromHashOf().forkPositional()` pattern as existing `evalVerticalGradient` seeding.

### NoiseChunk Constructor

```java
this.aquifer = settings.aquifersEnabled()
    ? new NoiseBasedAquifer(chunkPos, randomState.aquiferRandom,
                            router, seaLevel, minY, height)
    : new DisabledAquifer(seaLevel, waterStateId);
```

### NoiseSettings

```java
public record NoiseSettings(
    int minY, int height, int seaLevel,
    // ... existing ...
    boolean aquifersEnabled
) {}
```

Overworld: `aquifersEnabled=true`. Superflat: `aquifersEnabled=false` (DisabledAquifer = current behavior, no regression).

### SuperflatWorldSource

No change needed. SuperflatWorldSource doesn't use NoiseChunk at all.

---

## Scope Boundary

**M4g-c2:** Aquifer only.  
**M4g-c3 (later):** Ore vein generator (`OreVeinifier` in vanilla). User explicitly flagged this for future work.  
Carvers (cave carving) = separate milestone after c2/c3.

---

## Success Criteria

- All existing 189 tests pass
- 3 existing surface fixtures: 0 mismatch (no regression)
- 2–3 new aquifer-challenging fixtures: 0 mismatch
- Overworld seed=0 in-game: underground water/lava visible, no regression on surface
