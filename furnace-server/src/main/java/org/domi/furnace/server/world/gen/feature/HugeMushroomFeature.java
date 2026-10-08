// huge_red_mushroom / huge_brown_mushroom — 바닐라 HugeMushroomFeature + 각 모자 모양 포팅
package org.domi.furnace.server.world.gen.feature;

import java.util.Map;
import java.util.Set;
import org.domi.furnace.server.world.BlockAttrs;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class HugeMushroomFeature implements ConfiguredFeature {

    public enum Kind { RED, BROWN }

    private final Kind kind;
    private final StateProvider cap;
    private final StateProvider stem;
    private final int radius;
    private final BlockRegistry registry;
    private final BlockAttrs attrs = BlockAttrs.get();
    private final Set<Integer> dirt;
    private final Set<Integer> mushroomGrow;
    private final Set<Integer> leaves;

    public HugeMushroomFeature(Kind kind, StateProvider cap, StateProvider stem, int radius, BlockRegistry registry) {
        this.kind = kind;
        this.cap = cap;
        this.stem = stem;
        this.radius = radius;
        this.registry = registry;
        this.dirt = tag("minecraft:dirt");
        this.mushroomGrow = tag("minecraft:mushroom_grow_block");
        this.leaves = tag("minecraft:leaves");
    }

    private Set<Integer> tag(String name) {
        java.util.HashSet<Integer> ids = new java.util.HashSet<>();
        for (String b : BlockTags.get().blocksOf(name)) ids.addAll(registry.stateIdsForBlock(b));
        return Set.copyOf(ids);
    }

    @Override
    public boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        int height = random.nextInt(3) + 4;
        if (random.nextInt(12) == 0) height *= 2;
        if (!isValidPosition(view, x, y, z, height)) return false;
        makeCap(view, random, x, y, z, height);
        // placeTrunk
        for (int i = 0; i < height; i++) {
            if (!attrs.isSolidRender(view.getBlock(x, y + i, z))) {
                view.setBlock(x, y + i, z, stem.getState(random, x, y, z));
            }
        }
        return true;
    }

    private boolean isValidPosition(FeatureWorldView view, int x, int y, int z, int height) {
        if (y >= ChunkColumn.MIN_Y + 1 && y + height + 1 < ChunkColumn.MAX_Y + 1) {
            int below = view.getBlock(x, y - 1, z);
            if (!dirt.contains(below) && !mushroomGrow.contains(below)) return false;
            for (int dy = 0; dy <= height; dy++) {
                int r = radiusAt(dy);
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        int state = view.getBlock(x + dx, y + dy, z + dz);
                        if (!attrs.isAir(state) && !leaves.contains(state)) return false;
                    }
                }
            }
            return true;
        }
        return false;
    }

    /**
     * getTreeRadiusForHeight(-1, -1, foliageRadius, yo) — 바닐라가 (trunkHeight 자리에) -1을 넘기므로 red는 항상 0,
     * brown은 yo>3일 때만 foliageRadius.
     */
    private int radiusAt(int yo) {
        return kind == Kind.RED ? 0 : (yo <= 3 ? 0 : radius);
    }

    private void putCap(FeatureWorldView view, int x, int y, int z, int state) {
        view.setBlock(x, y, z, state);
    }

    private int capState(WorldgenRandom random, int x, int y, int z, Map<String, String> props) {
        int base = cap.getState(random, x, y, z);
        int v = BlockStates.trySetValues(registry, base, props);
        return v < 0 ? base : v;       // 속성이 모두 있을 때만 바꾸고, 아니면 원본 그대로(hasProperty 검사와 동일)
    }

    private static String b(boolean v) { return v ? "true" : "false"; }

    private void makeCap(FeatureWorldView view, WorldgenRandom random, int x, int y, int z, int height) {
        if (kind == Kind.RED) {
            for (int ly = y + height - 3; ly <= y + height; ly++) {
                int r = ly < y + height ? radius : radius - 1;
                int inner = radius - 2;
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        boolean xMin = dx == -r, xMax = dx == r, zMin = dz == -r, zMax = dz == r;
                        boolean xEdge = xMin || xMax, zEdge = zMin || zMax;
                        if (ly >= y + height || xEdge != zEdge) {
                            int px = x + dx, pz = z + dz;
                            if (!attrs.isSolidRender(view.getBlock(px, ly, pz))) {
                                int state = capState(random, x, y, z, Map.of(
                                    "up", b(ly >= y + height - 1),
                                    "west", b(dx < -inner),
                                    "east", b(dx > inner),
                                    "north", b(dz < -inner),
                                    "south", b(dz > inner)));
                                putCap(view, px, ly, pz, state);
                            }
                        }
                    }
                }
            }
        } else {
            int r = radius;
            int ly = y + height;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    boolean xMin = dx == -r, xMax = dx == r, zMin = dz == -r, zMax = dz == r;
                    boolean xEdge = xMin || xMax, zEdge = zMin || zMax;
                    if (!xEdge || !zEdge) {
                        int px = x + dx, pz = z + dz;
                        if (!attrs.isSolidRender(view.getBlock(px, ly, pz))) {
                            boolean west = xMin || (zEdge && dx == 1 - r);
                            boolean east = xMax || (zEdge && dx == r - 1);
                            boolean north = zMin || (xEdge && dz == 1 - r);
                            boolean south = zMax || (xEdge && dz == r - 1);
                            int state = capState(random, x, y, z, Map.of(
                                "west", b(west), "east", b(east), "north", b(north), "south", b(south)));
                            putCap(view, px, ly, pz, state);
                        }
                    }
                }
            }
        }
    }
}
