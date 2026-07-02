package org.domi.furnace.server.world.gen.feature;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.carver.RandomTrace;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.feature.decorator.TreeDecorator;
import org.domi.furnace.server.world.gen.feature.trunk.TrunkPlacer;

public final class TreeFeature implements ConfiguredFeature {
    public static final Direction[] HORIZONTAL_DIRECTIONS = {
        Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST
    };

    private static final Direction[] ALL_DIRECTIONS = Direction.values();
    private static final int AIR = 0;
    private static final int CAVE_AIR = 12958;
    private static final int VOID_AIR = 12959;
    private static final int WATER_MIN_STATE = 80;
    private static final int WATER_MAX_STATE = 95;

    private final TreeConfig config;

    public TreeFeature(TreeConfig config) {
        this.config = config;
    }

    @Override
    public boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        BlockPos origin = new BlockPos(x, y, z);
        int treeHeight = config.trunkPlacer().getTreeHeight(random);
        int foliageHeight = config.foliagePlacer().foliageHeight(random, treeHeight, config);
        int trunkHeight = treeHeight - foliageHeight;
        int foliageRadius = config.foliagePlacer().foliageRadius(random, trunkHeight);
        RandomTrace.log("tree_begin x=" + x + " y=" + y + " z=" + z
            + " treeHeight=" + treeHeight
            + " foliageHeight=" + foliageHeight
            + " trunkHeight=" + trunkHeight
            + " foliageRadius=" + foliageRadius);
        int minY = origin.y();
        int maxY = origin.y() + treeHeight + 1;
        if (minY < ChunkColumn.MIN_Y + 1 || maxY > ChunkColumn.MIN_Y + ChunkColumn.SECTION_COUNT * 16) {
            RandomTrace.log("tree_fail height_bounds minY=" + minY + " maxY=" + maxY);
            return false;
        }

        int freeHeight = getMaxFreeTreeHeight(view, treeHeight, origin);
        int minClippedHeight = config.minimumSize().minClippedHeight();
        if (freeHeight < treeHeight && (minClippedHeight < 0 || freeHeight < minClippedHeight)) {
            RandomTrace.log("tree_fail free_height freeHeight=" + freeHeight + " treeHeight=" + treeHeight
                + " minClippedHeight=" + minClippedHeight);
            return false;
        }

        List<BlockPos> roots = new ArrayList<>();
        List<BlockPos> logs = new ArrayList<>();
        List<BlockPos> leaves = new ArrayList<>();
        List<BlockPos> deco = new ArrayList<>();

        List<TrunkPlacer.FoliageAttachment> attachments =
            config.trunkPlacer().placeTrunk(view, random, freeHeight, origin, config, logs);
        for (TrunkPlacer.FoliageAttachment attachment : attachments) {
            config.foliagePlacer().createFoliage(view, random, config, freeHeight, attachment, foliageHeight, foliageRadius, leaves);
        }

        if (logs.isEmpty() && leaves.isEmpty()) {
            RandomTrace.log("tree_fail empty_output");
            return false;
        }

        if (!config.decorators().isEmpty()) {
            logs.sort(Comparator.comparingInt(BlockPos::y).thenComparingInt(BlockPos::x).thenComparingInt(BlockPos::z));
            leaves.sort(Comparator.comparingInt(BlockPos::y).thenComparingInt(BlockPos::x).thenComparingInt(BlockPos::z));
            roots.sort(Comparator.comparingInt(BlockPos::y).thenComparingInt(BlockPos::x).thenComparingInt(BlockPos::z));
            TreeDecorator.Context ctx = new TreeDecorator.Context(
                view, random, roots, logs, leaves, deco, config);
            for (TreeDecorator decorator : config.decorators()) decorator.place(ctx);
        }

        if (RandomTrace.isActive()) {
            int treeOrdinal = RandomTrace.currentTreeOrdinal();
            for (BlockPos pos : logs) {
                System.out.printf("FURNACE_TREE_LOG tree=%d x=%d y=%d z=%d%n",
                    treeOrdinal, pos.x(), pos.y(), pos.z());
            }
            for (BlockPos pos : leaves) {
                System.out.printf("FURNACE_TREE_LEAF tree=%d x=%d y=%d z=%d%n",
                    treeOrdinal, pos.x(), pos.y(), pos.z());
            }
        }

        updateLeaves(view, logs, deco, leaves);
        RandomTrace.log("tree_success logs=" + logs.size() + " leaves=" + leaves.size() + " deco=" + deco.size());
        return true;
    }

    private int getMaxFreeTreeHeight(FeatureWorldView view, int trunkHeight, BlockPos origin) {
        for (int y = 0; y <= trunkHeight + 1; y++) {
            int size = config.minimumSize().getSizeAtHeight(trunkHeight, y);
            for (int dx = -size; dx <= size; dx++) {
                for (int dz = -size; dz <= size; dz++) {
                    BlockPos pos = new BlockPos(origin.x() + dx, origin.y() + y, origin.z() + dz);
                    if (!config.trunkPlacer().isFree(view, pos, config) || (!config.ignoreVines() && isVine(view.getBlock(pos.x(), pos.y(), pos.z())))) {
                        return y - 2;
                    }
                }
            }
        }
        return trunkHeight;
    }

    private void updateLeaves(FeatureWorldView view, List<BlockPos> trunkPositions, List<BlockPos> decorationPositions, List<BlockPos> foliagePositions) {
        Bounds bounds = Bounds.enclosing(trunkPositions, decorationPositions, foliagePositions);
        if (bounds == null) return;

        if (RandomTrace.isActive()) {
            System.out.printf(
                "FURNACE_BFS_BOX minX=%d maxX=%d minY=%d maxY=%d minZ=%d maxZ=%d%n",
                bounds.minX(), bounds.maxX(), bounds.minY(), bounds.maxY(), bounds.minZ(), bounds.maxZ());
            for (BlockPos pos : trunkPositions) {
                System.out.printf("FURNACE_BFS_SOURCE %d %d %d%n", pos.x(), pos.y(), pos.z());
            }
        }

        List<Set<BlockPos>> buckets = new ArrayList<>(7);
        VisitMask mask = new VisitMask(bounds);
        for (int i = 0; i < 7; i++) buckets.add(new HashSet<>());
        buckets.get(0).addAll(trunkPositions);
        for (BlockPos pos : decorationPositions) {
            if (bounds.contains(pos)) mask.fill(pos);
        }

        int distance = 0;
        while (distance < 7) {
            if (buckets.get(distance).isEmpty()) {
                distance++;
                continue;
            }

            BlockPos pos = buckets.get(distance).iterator().next();
            buckets.get(distance).remove(pos);
            if (!bounds.contains(pos)) continue;

            if (distance != 0) {
                int current = view.getBlock(pos.x(), pos.y(), pos.z());
                if (isLeaves(current)) {
                    view.setBlock(pos.x(), pos.y(), pos.z(), withLeafDistance(current, distance));
                }
            }
            mask.fill(pos);

            for (Direction dir : ALL_DIRECTIONS) {
                BlockPos next = new BlockPos(pos.x() + dir.dx(), pos.y() + dir.dy(), pos.z() + dir.dz());
                if (!bounds.contains(next) || mask.isFilled(next)) continue;
                int nextState = view.getBlock(next.x(), next.y(), next.z());
                if (!isLeaves(nextState)) continue;

                int nextDistance = Math.min(anyLeafDistance(nextState), distance + 1);
                if (nextDistance >= 7) continue;
                buckets.get(nextDistance).add(next);
                distance = Math.min(distance, nextDistance);
            }
        }
    }

    // Reads DISTANCE property from any leaf block state ID.
    // All leaf types span 28 states: base, base+28, ... Distance = (offset/4)+1.
    // Bases: oak=237, spruce=265, birch=293, jungle=321, acacia=349,
    //        cherry=377, dark_oak=405, mangrove=433, azalea=461, flower_azalea=489.
    private static int anyLeafDistance(int stateId) {
        if (stateId < 237 || stateId > 516) return 7;
        return (stateId - 237) % 28 / 4 + 1;
    }

    private static int withLeafDistance(int stateId, int distance) {
        return stateId - ((stateId - 237) % 28 / 4) * 4 + (distance - 1) * 4;
    }

    private record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        private static Bounds enclosing(List<BlockPos>... groups) {
            boolean found = false;
            int minX = 0;
            int minY = 0;
            int minZ = 0;
            int maxX = 0;
            int maxY = 0;
            int maxZ = 0;
            for (List<BlockPos> group : groups) {
                for (BlockPos pos : group) {
                    if (!found) {
                        minX = maxX = pos.x();
                        minY = maxY = pos.y();
                        minZ = maxZ = pos.z();
                        found = true;
                        continue;
                    }
                    minX = Math.min(minX, pos.x());
                    minY = Math.min(minY, pos.y());
                    minZ = Math.min(minZ, pos.z());
                    maxX = Math.max(maxX, pos.x());
                    maxY = Math.max(maxY, pos.y());
                    maxZ = Math.max(maxZ, pos.z());
                }
            }
            return found ? new Bounds(minX, minY, minZ, maxX, maxY, maxZ) : null;
        }

        private boolean contains(BlockPos pos) {
            return pos.x() >= minX && pos.x() <= maxX
                && pos.y() >= minY && pos.y() <= maxY
                && pos.z() >= minZ && pos.z() <= maxZ;
        }
    }

    private static final class VisitMask {
        private final Bounds bounds;
        private final boolean[] filled;
        private final int xSpan;
        private final int zSpan;

        private VisitMask(Bounds bounds) {
            this.bounds = bounds;
            this.xSpan = bounds.maxX() - bounds.minX() + 1;
            int ySpan = bounds.maxY() - bounds.minY() + 1;
            this.zSpan = bounds.maxZ() - bounds.minZ() + 1;
            this.filled = new boolean[xSpan * ySpan * zSpan];
        }

        private void fill(BlockPos pos) {
            filled[index(pos)] = true;
        }

        private boolean isFilled(BlockPos pos) {
            return filled[index(pos)];
        }

        private int index(BlockPos pos) {
            int x = pos.x() - bounds.minX();
            int y = pos.y() - bounds.minY();
            int z = pos.z() - bounds.minZ();
            return (y * zSpan + z) * xSpan + x;
        }
    }

    public static Direction getRandomDirection(WorldgenRandom random) {
        return HORIZONTAL_DIRECTIONS[random.nextInt(4)];
    }

    public static boolean validTreePos(FeatureWorldView view, BlockPos pos, TreeConfig config) {
        int state = view.getBlock(pos.x(), pos.y(), pos.z());
        return isAir(state)
            || state == 0
            || state == 31 || state == 32 || state == 192 || state == 193
            || state == 432 || state == 433 || state == 434 || state == 435
            || state == 460 || state == 461 || state == 462 || state == 463
            || state == 511 || state == 512 || state == 513 || state == 514;
    }

    public static boolean isWater(int stateId) {
        return stateId >= WATER_MIN_STATE && stateId <= WATER_MAX_STATE;
    }

    public static boolean isLeaves(int stateId) {
        return stateId >= 237 && stateId <= 516;
    }

    public static boolean isDirt(int stateId, TreeConfig config) {
        return config.dirtStates().contains(stateId) && !config.dirtReplaceExemptStates().contains(stateId);
    }

    public static boolean isLog(int stateId, TreeConfig config) {
        return stateId >= 130 && stateId <= 150 && stateId != 145 && stateId != 146 && stateId != 147;
    }

    private static boolean isVine(int stateId) {
        return stateId >= 4933 && stateId <= 4964;
    }

    private static boolean isAir(int stateId) {
        return stateId == AIR || stateId == CAVE_AIR || stateId == VOID_AIR;
    }

    public enum Direction {
        SOUTH(0, 0, 1, 0, "south"),
        WEST(-1, 0, 0, 1, "west"),
        NORTH(0, 0, -1, 2, "north"),
        EAST(1, 0, 0, 3, "east"),
        DOWN(0, -1, 0, -1, "down"),
        UP(0, 1, 0, -1, "up");

        private final int dx;
        private final int dy;
        private final int dz;
        private final int horizontalIndex;
        private final String name;

        Direction(int dx, int dy, int dz, int horizontalIndex, String name) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.horizontalIndex = horizontalIndex;
            this.name = name;
        }

        public int dx() { return dx; }
        public int dy() { return dy; }
        public int dz() { return dz; }
        public int horizontalIndex() { return horizontalIndex; }
        public String propertyName() { return name; }

        public Direction opposite() {
            return switch (this) {
                case SOUTH -> NORTH;
                case WEST -> EAST;
                case NORTH -> SOUTH;
                case EAST -> WEST;
                case DOWN -> UP;
                case UP -> DOWN;
            };
        }
    }
}
