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
        // vanilla doPlace: rootPlacer가 있으면 trunkOrigin = rootPlacer.getTrunkOrigin(pos, random)
        // (trunk_offset_y 샘플로 RNG 소비) — 몸통이 origin보다 위에서 시작하고 그 사이를 뿌리가 채운다.
        BlockPos trunkOrigin = config.rootPlacer() != null
            ? config.rootPlacer().getTrunkOrigin(origin, random)
            : origin;
        if (RandomTrace.isActive()) RandomTrace.log("tree_begin x=" + x + " y=" + y + " z=" + z
            + " treeHeight=" + treeHeight
            + " foliageHeight=" + foliageHeight
            + " trunkHeight=" + trunkHeight
            + " foliageRadius=" + foliageRadius + " col=" + view.getBlock(x, y - 1, z) + "," + view.getBlock(x, y, z) + "," + view.getBlock(x, y + 1, z) + "," + view.getBlock(x, y + 2, z));
        int minY = Math.min(origin.y(), trunkOrigin.y());
        int maxY = Math.max(origin.y(), trunkOrigin.y()) + treeHeight + 1;
        if (minY < ChunkColumn.MIN_Y + 1 || maxY > ChunkColumn.MIN_Y + ChunkColumn.SECTION_COUNT * 16) {
            if (RandomTrace.isActive()) RandomTrace.log("tree_fail height_bounds minY=" + minY + " maxY=" + maxY);
            return false;
        }

        int freeHeight = getMaxFreeTreeHeight(view, treeHeight, trunkOrigin);
        int minClippedHeight = config.minimumSize().minClippedHeight();
        if (freeHeight < treeHeight && (minClippedHeight < 0 || freeHeight < minClippedHeight)) {
            if (RandomTrace.isActive()) RandomTrace.log("tree_fail free_height freeHeight=" + freeHeight + " treeHeight=" + treeHeight
                + " minClippedHeight=" + minClippedHeight);
            return false;
        }

        List<BlockPos> roots = new ArrayList<>();
        List<BlockPos> logs = new ArrayList<>();
        List<BlockPos> leaves = new ArrayList<>();
        List<BlockPos> deco = new ArrayList<>();

        if (config.rootPlacer() != null
                && !config.rootPlacer().placeRoots(view, random, origin, trunkOrigin, config, roots)) {
            if (RandomTrace.isActive()) RandomTrace.log("tree_fail root_placement");
            return false;
        }

        List<TrunkPlacer.FoliageAttachment> attachments =
            config.trunkPlacer().placeTrunk(view, random, freeHeight, trunkOrigin, config, logs);
        for (TrunkPlacer.FoliageAttachment attachment : attachments) {
            config.foliagePlacer().createFoliage(view, random, config, freeHeight, attachment, foliageHeight, foliageRadius, leaves);
        }

        if (logs.isEmpty() && leaves.isEmpty()) {
            if (RandomTrace.isActive()) RandomTrace.log("tree_fail empty_output");
            return false;
        }

        if (!config.decorators().isEmpty()) {
            // vanilla TreeDecorator.Context — HashSet(중복 붕괴 + 삽입 순서에 따른 해시 순회 순서) 복사본을
            // Y만으로 stable sort한 리스트. Furnace BlockPos.hashCode가 vanilla Vec3i.hashCode와 동일하고
            // 삽입 순서(배치 순서)도 동일하므로 HashSet 순회 순서가 vanilla와 일치한다. 이전의 (y,x,z) 전체
            // 정렬은 같은 y 안의 순서가 vanilla와 달라 RNG 소비형 decorator(leave_vine·alter_ground·
            // attached_to_leaves 등)의 대상-draw 매핑이 어긋났다(mega-tree-placers task에서 수정).
            TreeDecorator.Context ctx = new TreeDecorator.Context(
                view, random, vanillaContextOrder(roots), vanillaContextOrder(logs), vanillaContextOrder(leaves), deco, config);
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

        updateLeaves(view, roots, logs, deco, leaves);
        if (RandomTrace.isActive()) RandomTrace.log("tree_success logs=" + logs.size() + " leaves=" + leaves.size() + " deco=" + deco.size());
        return true;
    }

    // vanilla Sets.newHashSet() 증분 삽입(용량 16에서 시작해 요소별 add)을 재현한 뒤 Y만으로 stable sort.
    // new HashSet<>(collection) 생성자는 초기 테이블 크기가 달라져 순회 순서가 어긋나므로 쓰지 않는다.
    private static List<BlockPos> vanillaContextOrder(List<BlockPos> placed) {
        HashSet<BlockPos> set = new HashSet<>();
        for (BlockPos pos : placed) set.add(pos);
        List<BlockPos> list = new ArrayList<>(set);
        list.sort(Comparator.comparingInt(BlockPos::y));
        return list;
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

    // vanilla updateLeaves 대응 — BFS 소스는 logs, 사전 방문 처리(fill)는 decorations ∪ roots
    // (vanilla place()의 updateLeaves(level, box, set1=logs, set3=deco, set=roots) 인자 매핑).
    private void updateLeaves(FeatureWorldView view, List<BlockPos> rootPositions, List<BlockPos> trunkPositions,
                              List<BlockPos> decorationPositions, List<BlockPos> foliagePositions) {
        Bounds bounds = Bounds.enclosing(rootPositions, trunkPositions, decorationPositions, foliagePositions);
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
        for (BlockPos pos : rootPositions) {
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
                // vanilla LeavesBlock.getOptionalDistanceAt: 통나무(#logs)는 거리 0, DISTANCE 속성이 있는 잎은 그 값, 나머지는 건너뜀.
                // 이 나무 것이 아닌 통나무(이웃 나무)도 거리 0 원천으로 합류한다.
                int base;
                if (isLog(nextState)) base = 0;
                else if (isLeaves(nextState)) base = anyLeafDistance(nextState);
                else continue;

                int nextDistance = Math.min(base, distance + 1);
                if (nextDistance >= 7) continue;
                buckets.get(nextDistance).add(next);
                distance = Math.min(distance, nextDistance);
            }
        }

        updateShapeAtEdge(view, bounds, mask);
    }

    // vanilla StructureTemplate.updateShapeAtEdge(level, 3, shape, ...) — DiscreteVoxelShape.forAllFaces 순서(Z축 선 →
    // Y축 선 → X축 선, 선의 첫 칸은 음의 방향 면·마지막 칸은 양의 방향 면)로 shape 가장자리 면마다 양쪽 블록의
    // updateShape를 호출한다. 이 포팅에서는 상태를 바꾸는 vine(VineBlock.updateShape)만 반영한다
    // (잎은 틱 예약만, 통나무는 변화 없음). 다른 나무가 먼저 늘어뜨린 vine이 새 잎에 덮여 지지를 잃으면 여기서 사라진다.
    private void updateShapeAtEdge(FeatureWorldView view, Bounds b, VisitMask mask) {
        int sx = b.maxX() - b.minX() + 1;
        int sy = b.maxY() - b.minY() + 1;
        int sz = b.maxZ() - b.minZ() + 1;
        // pass 0: l=x, m=y, n=z (NORTH/SOUTH) / pass 1: l=z, m=x, n=y (DOWN/UP) / pass 2: l=y, m=z, n=x (WEST/EAST)
        for (int pass = 0; pass < 3; pass++) {
            int sl = pass == 0 ? sx : pass == 1 ? sz : sy;
            int sm = pass == 0 ? sy : pass == 1 ? sx : sz;
            int sn = pass == 0 ? sz : pass == 1 ? sy : sx;
            Direction neg = pass == 0 ? Direction.NORTH : pass == 1 ? Direction.DOWN : Direction.WEST;
            Direction pos = pass == 0 ? Direction.SOUTH : pass == 1 ? Direction.UP : Direction.EAST;
            for (int l = 0; l < sl; l++) {
                for (int m = 0; m < sm; m++) {
                    boolean last = false;
                    for (int n = 0; n <= sn; n++) {
                        boolean present = n != sn && maskAt(mask, b, pass, l, m, n);
                        if (!last && present) edgeFace(view, b, pass, l, m, n, neg);
                        if (last && !present) edgeFace(view, b, pass, l, m, n - 1, pos);
                        last = present;
                    }
                }
            }
        }
    }

    private static int[] cell(int pass, int l, int m, int n) {
        return switch (pass) {
            case 0 -> new int[]{l, m, n};
            case 1 -> new int[]{m, n, l};
            default -> new int[]{n, l, m};
        };
    }

    private static boolean maskAt(VisitMask mask, Bounds b, int pass, int l, int m, int n) {
        int[] c = cell(pass, l, m, n);
        return mask.isFilled(new BlockPos(b.minX() + c[0], b.minY() + c[1], b.minZ() + c[2]));
    }

    private static void edgeFace(FeatureWorldView view, Bounds b, int pass, int l, int m, int n, Direction dir) {
        int[] c = cell(pass, l, m, n);
        int x = b.minX() + c[0];
        int y = b.minY() + c[1];
        int z = b.minZ() + c[2];
        int nx = x + dir.dx();
        int ny = y + dir.dy();
        int nz = z + dir.dz();
        int state = view.getBlock(x, y, z);
        int neighbor = view.getBlock(nx, ny, nz);
        int newState = VineSupport.updateShape(view, state, dir, x, y, z);
        if (newState != state) view.setBlock(x, y, z, newState);
        int newNeighbor = VineSupport.updateShape(view, neighbor, dir.opposite(), nx, ny, nz);
        if (newNeighbor != neighbor) view.setBlock(nx, ny, nz, newNeighbor);
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

    // vanilla Direction.Plane.HORIZONTAL faces 순서 (NORTH, EAST, SOUTH, WEST) — 1.21.1 trunk placer 전종이
    // Plane.HORIZONTAL.getRandomDirection을 쓴다. HORIZONTAL_DIRECTIONS(2D-data 순서 SOUTH,WEST,NORTH,EAST)와
    // 다름에 주의 — 이전에는 getRandomDirection이 HORIZONTAL_DIRECTIONS를 인덱싱해 방향 매핑이 vanilla와
    // 달랐다(acacia/dark oak가 assert되는 fixture가 없어 미검출이던 잠재 버그, mega-tree-placers task에서 수정).
    private static final Direction[] PLANE_HORIZONTAL = {
        Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    public static Direction getRandomDirection(WorldgenRandom random) {
        return PLANE_HORIZONTAL[random.nextInt(4)];
    }

    // vanilla TreeFeature.validTreePos == isAir || is(#minecraft:replaceable_by_trees).
    // 태그 구성원(data/minecraft/tags/block/replaceable_by_trees.json, 1.21.1 —
    // worldgen-oracle/OracleMain 바인딩과 동일 목록): leaves(전종)·water·short_grass·fern·
    // dead_bush·seagrass·tall_seagrass·vine·glow_lichen·sunflower/lilac/rose_bush/peony/
    // tall_grass/large_fern·pitcher_plant·warped_roots·nether_sprouts·crimson_roots·hanging_roots.
    // leaves는 의도적으로 제외 — isLeaves(state)를 추가하면 (51532,5,7) 169→364, (1234,-49,-20)
    // 541→692로 fixture mismatch가 오히려 증가함을 A/B 측정으로 확인(round 5, round7에서 round6의
    // 9-grid spill 순서 수정 이후 재측정해도 동일 회귀 재현 — round5 가설이었던 "그 순서 버그"는
    // 9-grid spill 순서가 아닌 다른 원인이므로 아직 미해결. result7.md 참조). 그 근본 원인을
    // 먼저 잡기 전까지는 leaves 포함을 보류한다.
    public static boolean validTreePos(FeatureWorldView view, BlockPos pos, TreeConfig config) {
        int state = view.getBlock(pos.x(), pos.y(), pos.z());
        return isAir(state)
            || isWater(state)
            || isLeaves(state)
            || isReplaceableByTreesPlant(state);
    }

    private static boolean isReplaceableByTreesPlant(int stateId) {
        return stateId == 2005 // short_grass
            || stateId == 2006 // fern
            || stateId == 2007 // dead_bush
            || stateId == 2008 // seagrass
            || (stateId >= 2009 && stateId <= 2010) // tall_seagrass
            || (stateId >= 6837 && stateId <= 6868) // vine
            || (stateId >= 6869 && stateId <= 6996) // glow_lichen
            || (stateId >= 10747 && stateId <= 10758) // sunflower·lilac·rose_bush·peony·tall_grass·large_fern
            || (stateId >= 12507 && stateId <= 12508) // pitcher_plant
            || stateId == 18594 // warped_roots
            || stateId == 18595 // nether_sprouts
            || stateId == 18665 // crimson_roots
            || (stateId >= 24900 && stateId <= 24901); // hanging_roots
    }

    public static boolean isWater(int stateId) {
        return stateId >= WATER_MIN_STATE && stateId <= WATER_MAX_STATE;
    }

    private static volatile boolean[] logTable;

    /** BlockTags.LOGS 소속 state인가(상태 id 표는 첫 사용 때 한 번 만든다). */
    private static boolean isLog(int stateId) {
        boolean[] t = logTable;
        if (t == null) {
            org.domi.furnace.server.world.BlockRegistry r = org.domi.furnace.server.world.BlockRegistry.load("blocks/1.21.1.txt");
            t = new boolean[r.totalStateCount()];
            for (String b : BlockTags.get().blocksOf("minecraft:logs")) {
                for (int s : r.stateIdsForBlock(b)) t[s] = true;
            }
            logTable = t;
        }
        return stateId >= 0 && stateId < t.length && t[stateId];
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

    // 기존 4933-4964는 실제로는 spruce/birch_hanging_sign 상태 id 범위였음(blockstates_1.21.1.txt
    // 대조로 확인) — vine의 실제 범위(6837-6868)로 수정. isFree/ignoreVines 판정과 무관하게(vine이
    // fixture에서 거의 등장하지 않아) mismatch 카운트엔 영향 없었으나, 의미상 확실한 버그라 고침.
    private static boolean isVine(int stateId) {
        return stateId >= 6837 && stateId <= 6868;
    }

    public static boolean isAir(int stateId) {
        return stateId == AIR || stateId == CAVE_AIR || stateId == VOID_AIR;
    }

    // vanilla net.minecraft.core.Direction.values() 순서(DOWN,UP,NORTH,SOUTH,WEST,EAST)와 일치시킨다 —
    // TreeFeature.updateLeaves의 BFS가 이 순서로 이웃을 순회·enqueue하므로, values() 순서가 다르면
    // 동일 좌표가 이중 버킷에 들어가는 경합(양쪽 엔진 모두 구조적으로 존재) 발생 시 최종 distance 값이 갈린다.
    public enum Direction {
        DOWN(0, -1, 0, -1, "down"),
        UP(0, 1, 0, -1, "up"),
        NORTH(0, 0, -1, 2, "north"),
        SOUTH(0, 0, 1, 0, "south"),
        WEST(-1, 0, 0, 1, "west"),
        EAST(1, 0, 0, 3, "east");

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
