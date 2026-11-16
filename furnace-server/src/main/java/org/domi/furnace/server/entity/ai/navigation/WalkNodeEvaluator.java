// 바닐라 WalkNodeEvaluator: 걷는 몹의 노드 평가 — 시작 칸 찾기, 이웃(4방+대각) 만들기, 올라가기/떨어지기/물/울타리 판정, 칸 종류 계산.
package org.domi.furnace.server.entity.ai.navigation;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehaviors;
import org.domi.furnace.server.block.BlockSetType;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.Pathfinding;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.entity.AABB;
import org.domi.furnace.server.entity.Mob;

public class WalkNodeEvaluator extends NodeEvaluator {
    public static final double SPACE_BETWEEN_WALL_POSTS = 0.5;
    private static final double DEFAULT_MOB_JUMP_HEIGHT = 1.125;
    /** Direction.Plane.HORIZONTAL 순서: 북, 동, 남, 서. */
    private static final int[] DX = {0, 1, 0, -1}, DZ = {-1, 0, 1, 0};
    private static final Prop OPEN = B.prop("open");

    private final Map<Long, PathType> pathTypesByPosCache = new HashMap<>();
    private final Map<AABB, Boolean> collisionCache = new HashMap<>();
    private final Node[] reusableNeighbors = new Node[4];

    @Override
    public void prepare(org.domi.furnace.server.block.ChunkLevel level, Mob mob) {
        super.prepare(level, mob);
        mob.onPathfindingStart();   // eqa.prepare: 길찾기 앞 훅(스니퍼의 물 비용)
    }

    @Override
    public void done() {
        mob.onPathfindingDone();   // eqa.done: 캐시를 비우기 전에 부른다
        pathTypesByPosCache.clear();
        collisionCache.clear();
        super.done();
    }

    @Override
    public Node getStart() {
        int y = mob.blockY();
        int bx = floor(mob.x()), bz = floor(mob.z());
        int state = level.getBlockState(new Pos(bx, y, bz));
        if (!canStandOnFluid(BlockBehaviors.of(state).getFluidState(state))) {
            if (canFloat() && mob.isInWater()) {
                while (true) {
                    if (!B.is(state, "minecraft:water") && !Fluids.isWaterSource(BlockBehaviors.of(state).getFluidState(state))) {
                        y--;
                        break;
                    }
                    state = level.getBlockState(new Pos(bx, ++y, bz));
                }
            } else if (mob.onGround()) {
                y = floor(mob.y() + 0.5);
            } else {
                int py = floor(mob.y() + 1.0);
                while (py > level.minBuildHeight()) {
                    y = py;
                    py--;
                    int s = level.getBlockState(new Pos(bx, py, bz));
                    if (!B.isAir(s) && !Pathfinding.isPathfindable(s, Pathfinding.LAND)) break;
                }
            }
        } else {
            while (canStandOnFluid(BlockBehaviors.of(state).getFluidState(state))) {
                state = level.getBlockState(new Pos(bx, ++y, bz));
            }
            y--;
        }
        Pos mobPos = mob.blockPosition();
        if (!canStartAt(new Pos(mobPos.x(), y, mobPos.z()))) {
            AABB bb = mob.boundingBox();
            Pos[] corners = {new Pos(floor(bb.minX()), y, floor(bb.minZ())), new Pos(floor(bb.minX()), y, floor(bb.maxZ())),
                    new Pos(floor(bb.maxX()), y, floor(bb.minZ())), new Pos(floor(bb.maxX()), y, floor(bb.maxZ()))};
            for (Pos c : corners) if (canStartAt(c)) return getStartNode(c);
        }
        return getStartNode(new Pos(mobPos.x(), y, mobPos.z()));
    }

    protected boolean canStandOnFluid(int fluid) { return mob.canStandOnFluid(fluid); }

    protected Node getStartNode(Pos pos) {
        Node n = getNode(pos);
        n.type = getCachedPathType(n.x, n.y, n.z);
        n.costMalus = mob.getPathfindingMalus(n.type);
        return n;
    }

    protected boolean canStartAt(Pos pos) {
        PathType t = getCachedPathType(pos.x(), pos.y(), pos.z());
        return t != PathType.OPEN && mob.getPathfindingMalus(t) >= 0.0F;
    }

    @Override
    public Target getTarget(double x, double y, double z) { return getTargetNodeAt(x, y, z); }

    @Override
    public int getNeighbors(Node[] out, Node node) {
        int count = 0;
        int maxYStep = 0;
        PathType above = getCachedPathType(node.x, node.y + 1, node.z);
        PathType here = getCachedPathType(node.x, node.y, node.z);
        if (mob.getPathfindingMalus(above) >= 0.0F && here != PathType.STICKY_HONEY) {
            maxYStep = (int) Math.floor(Math.max(1.0F, mob.getStepHeight()));
        }
        double floor = getFloorLevel(new Pos(node.x, node.y, node.z));
        for (int d = 0; d < 4; d++) {
            Node n = findAcceptedNode(node.x + DX[d], node.y, node.z + DZ[d], maxYStep, floor, d, here);
            reusableNeighbors[d] = n;
            if (isNeighborValid(n, node)) out[count++] = n;
        }
        for (int d = 0; d < 4; d++) {
            int c = (d + 1) & 3;   // getClockWise
            if (isDiagonalValid(node, reusableNeighbors[d], reusableNeighbors[c])) {
                Node diag = findAcceptedNode(node.x + DX[d] + DX[c], node.y, node.z + DZ[d] + DZ[c], maxYStep, floor, d, here);
                if (isDiagonalValid(diag)) out[count++] = diag;
            }
        }
        return count;
    }

    protected boolean isNeighborValid(Node n, Node from) { return n != null && !n.closed && (n.costMalus >= 0.0F || from.costMalus < 0.0F); }

    protected boolean isDiagonalValid(Node from, Node a, Node b) {
        if (b == null || a == null || b.y > from.y || a.y > from.y) return false;
        if (a.type != PathType.WALKABLE_DOOR && b.type != PathType.WALKABLE_DOOR) {
            boolean fence = b.type == PathType.FENCE && a.type == PathType.FENCE && mob.bbWidth() < 0.5;
            return (b.y < from.y || b.costMalus >= 0.0F || fence) && (a.y < from.y || a.costMalus >= 0.0F || fence);
        }
        return false;
    }

    protected boolean isDiagonalValid(Node n) {
        if (n == null || n.closed) return false;
        return n.type == PathType.WALKABLE_DOOR ? false : n.costMalus >= 0.0F;
    }

    private static boolean doesBlockHavePartialCollision(PathType t) { return t == PathType.FENCE || t == PathType.DOOR_WOOD_CLOSED || t == PathType.DOOR_IRON_CLOSED; }

    private boolean canReachWithoutCollision(Node node) {
        AABB bb = mob.boundingBox();
        double vx = node.x - mob.x() + bb.getXsize() / 2.0, vy = node.y - mob.y() + bb.getYsize() / 2.0, vz = node.z - mob.z() + bb.getZsize() / 2.0;
        double size = (bb.getXsize() + bb.getYsize() + bb.getZsize()) / 3.0;
        int n = (int) Math.ceil(Math.sqrt(vx * vx + vy * vy + vz * vz) / size);
        double s = 1.0F / n;
        vx *= s;
        vy *= s;
        vz *= s;
        for (int i = 1; i <= n; i++) {
            bb = bb.move(vx, vy, vz);
            if (hasCollisions(bb)) return false;
        }
        return true;
    }

    protected double getFloorLevel(Pos pos) {
        return (canFloat() || isAmphibious()) && Fluids.isWater(level.getFluidState(pos)) ? pos.y() + 0.5 : getFloorLevel(level, pos);
    }

    public static double getFloorLevel(org.domi.furnace.server.block.ChunkLevel level, Pos pos) {
        Pos below = pos.below();
        int s = level.getBlockState(below);
        double[] b = BlockBehaviors.of(s).getCollisionShape(s, level, below, org.domi.furnace.server.block.CollisionContext.EMPTY).bounds();
        return below.y() + (b == null ? 0.0 : b[4]);
    }

    protected boolean isAmphibious() { return false; }

    protected Node findAcceptedNode(int x, int y, int z, int maxYStep, double nodeFloorLevel, int dir, PathType pathTypeOfCurrent) {
        Node result = null;
        double floor = getFloorLevel(new Pos(x, y, z));
        if (floor - nodeFloorLevel > getMobJumpHeight()) return null;
        PathType type = getCachedPathType(x, y, z);
        float malus = mob.getPathfindingMalus(type);
        if (malus >= 0.0F) result = getNodeAndUpdateCostToMax(x, y, z, type, malus);
        if (doesBlockHavePartialCollision(pathTypeOfCurrent) && result != null && result.costMalus >= 0.0F && !canReachWithoutCollision(result)) result = null;
        if (type != PathType.WALKABLE && (!isAmphibious() || type != PathType.WATER)) {
            if ((result == null || result.costMalus < 0.0F) && maxYStep > 0 && (type != PathType.FENCE || canWalkOverFences())
                    && type != PathType.UNPASSABLE_RAIL && type != PathType.TRAPDOOR && type != PathType.POWDER_SNOW) {
                result = tryJumpOn(x, y, z, maxYStep, nodeFloorLevel, dir, pathTypeOfCurrent);
            } else if (!isAmphibious() && type == PathType.WATER && !canFloat()) {
                result = tryFindFirstNonWaterBelow(x, y, z, result);
            } else if (type == PathType.OPEN) {
                result = tryFindFirstGroundNodeBelow(x, y, z);
            } else if (doesBlockHavePartialCollision(type) && result == null) {
                result = getClosedNode(x, y, z, type);
            }
        }
        return result;
    }

    private double getMobJumpHeight() { return Math.max(DEFAULT_MOB_JUMP_HEIGHT, mob.getStepHeight()); }

    private Node getNodeAndUpdateCostToMax(int x, int y, int z, PathType type, float malus) {
        Node n = getNode(x, y, z);
        n.type = type;
        n.costMalus = Math.max(n.costMalus, malus);
        return n;
    }

    private Node getBlockedNode(int x, int y, int z) {
        Node n = getNode(x, y, z);
        n.type = PathType.BLOCKED;
        n.costMalus = -1.0F;
        return n;
    }

    private Node getClosedNode(int x, int y, int z, PathType type) {
        Node n = getNode(x, y, z);
        n.closed = true;
        n.type = type;
        n.costMalus = type.malus();
        return n;
    }

    private Node tryJumpOn(int x, int y, int z, int maxYStep, double nodeFloorLevel, int dir, PathType pathTypeOfCurrent) {
        Node up = findAcceptedNode(x, y + 1, z, maxYStep - 1, nodeFloorLevel, dir, pathTypeOfCurrent);
        if (up == null) return null;
        if (mob.bbWidth() >= 1.0F) return up;
        if (up.type != PathType.OPEN && up.type != PathType.WALKABLE) return up;
        double cx = x - (dir < 0 ? 0 : DX[dir]) + 0.5, cz = z - (dir < 0 ? 0 : DZ[dir]) + 0.5;   // 위·아래(dir < 0)는 걸음 폭이 0
        double half = mob.bbWidth() / 2.0;
        AABB bb = new AABB(cx - half, getFloorLevel(new Pos(floor(cx), y + 1, floor(cz))) + 0.001, cz - half,
                cx + half, mob.bbHeight() + getFloorLevel(new Pos(up.x, up.y, up.z)) - 0.002, cz + half);
        return hasCollisions(bb) ? null : up;
    }

    private Node tryFindFirstNonWaterBelow(int x, int y, int z, Node node) {
        y--;
        while (y > level.minBuildHeight()) {
            PathType t = getCachedPathType(x, y, z);
            if (t != PathType.WATER) return node;
            node = getNodeAndUpdateCostToMax(x, y, z, t, mob.getPathfindingMalus(t));
            y--;
        }
        return node;
    }

    private Node tryFindFirstGroundNodeBelow(int x, int y, int z) {
        for (int i = y - 1; i >= level.minBuildHeight(); i--) {
            if (y - i > mob.getMaxFallDistance()) return getBlockedNode(x, i, z);
            PathType t = getCachedPathType(x, i, z);
            float malus = mob.getPathfindingMalus(t);
            if (t != PathType.OPEN) {
                if (malus >= 0.0F) return getNodeAndUpdateCostToMax(x, i, z, t, malus);
                return getBlockedNode(x, i, z);
            }
        }
        return getBlockedNode(x, y, z);
    }

    /** 바닐라 noCollision(mob, box) — PathNavigationRegion 위라 엔티티 충돌은 없고 블록(mob 문맥)·월드 보더만 본다. */
    private boolean hasCollisions(AABB box) { return collisionCache.computeIfAbsent(box, b -> !level.noCollisionIgnoringEntities(mob, b)); }

    protected PathType getCachedPathType(int x, int y, int z) {
        long key = new Pos(x, y, z).asLong();
        PathType cached = pathTypesByPosCache.get(key);
        if (cached == null) {
            cached = hooked(x, y, z, getPathTypeOfMob(new PathfindingContext(level, mob), x, y, z, mob));
            pathTypesByPosCache.put(key, cached);
        }
        return cached;
    }

    @Override
    public PathType getPathTypeOfMob(PathfindingContext ctx, int x, int y, int z, Mob mob) {
        Set<PathType> types = getPathTypeWithinMobBB(ctx, x, y, z);
        if (types.contains(PathType.FENCE)) return PathType.FENCE;
        if (types.contains(PathType.UNPASSABLE_RAIL)) return PathType.UNPASSABLE_RAIL;
        PathType best = PathType.BLOCKED;
        for (PathType t : types) {
            if (mob.getPathfindingMalus(t) < 0.0F) return t;
            if (mob.getPathfindingMalus(t) >= mob.getPathfindingMalus(best)) best = t;
        }
        return entityWidth <= 1 && best != PathType.OPEN && mob.getPathfindingMalus(best) == 0.0F && getPathType(ctx, x, y, z) == PathType.OPEN ? PathType.OPEN : best;
    }

    public Set<PathType> getPathTypeWithinMobBB(PathfindingContext ctx, int x, int y, int z) {
        EnumSet<PathType> set = EnumSet.noneOf(PathType.class);
        for (int dx = 0; dx < entityWidth; dx++) {
            for (int dy = 0; dy < entityHeight; dy++) {
                for (int dz = 0; dz < entityDepth; dz++) {
                    int px = dx + x, py = dy + y, pz = dz + z;
                    PathType t = getPathType(ctx, px, py, pz);
                    Pos mobPos = mob.blockPosition();
                    boolean doors = canPassDoors();
                    if (t == PathType.DOOR_WOOD_CLOSED && canOpenDoors() && doors) t = PathType.WALKABLE_DOOR;
                    if (t == PathType.DOOR_OPEN && !doors) t = PathType.BLOCKED;
                    if (t == PathType.RAIL && getPathType(ctx, mobPos.x(), mobPos.y(), mobPos.z()) != PathType.RAIL
                            && getPathType(ctx, mobPos.x(), mobPos.y() - 1, mobPos.z()) != PathType.RAIL) {
                        t = PathType.UNPASSABLE_RAIL;
                    }
                    set.add(t);
                }
            }
        }
        return set;
    }

    @Override
    public PathType getPathType(PathfindingContext ctx, int x, int y, int z) { return getPathTypeStatic(ctx, new Pos(x, y, z)); }

    public static PathType getPathTypeStatic(PathfindingContext ctx, Pos pos) {
        int x = pos.x(), y = pos.y(), z = pos.z();
        PathType t = ctx.getPathTypeFromState(x, y, z);
        if (t == PathType.OPEN && y >= ctx.level().minBuildHeight() + 1) {
            return switch (ctx.getPathTypeFromState(x, y - 1, z)) {
                case OPEN, WATER, LAVA, WALKABLE -> PathType.OPEN;
                case DAMAGE_FIRE -> PathType.DAMAGE_FIRE;
                case DAMAGE_OTHER -> PathType.DAMAGE_OTHER;
                case STICKY_HONEY -> PathType.STICKY_HONEY;
                case POWDER_SNOW -> PathType.DANGER_POWDER_SNOW;
                case DAMAGE_CAUTIOUS -> PathType.DAMAGE_CAUTIOUS;
                case TRAPDOOR -> PathType.DANGER_TRAPDOOR;
                default -> checkNeighbourBlocks(ctx, x, y, z, PathType.WALKABLE);
            };
        }
        return t;
    }

    public static PathType checkNeighbourBlocks(PathfindingContext ctx, int x, int y, int z, PathType def) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dz != 0) {
                        PathType t = ctx.getPathTypeFromState(x + dx, y + dy, z + dz);
                        if (t == PathType.DAMAGE_OTHER) return PathType.DANGER_OTHER;
                        if (t == PathType.DAMAGE_FIRE || t == PathType.LAVA) return PathType.DANGER_FIRE;
                        if (t == PathType.WATER) return PathType.WATER_BORDER;
                        if (t == PathType.DAMAGE_CAUTIOUS) return PathType.DAMAGE_CAUTIOUS;
                    }
                }
            }
        }
        return def;
    }

    /** WalkNodeEvaluator.getPathTypeFromState(level, pos): 칸 하나의 블록으로 정해지는 종류. */
    public static PathType getPathTypeFromState(PathfindingContext ctx, Pos pos) { return pathTypeOfState(ctx.getBlockState(pos)); }

    /**
     * 결과는 블록 상태 하나로만 정해진다(태그·유체·LAND 길찾기 가능 여부 모두 상태의 성질) — 상태별로 한 번만 계산해 둔다.
     * 바닐라 PathTypeCache 와 같은 목적(칸마다 문자열 태그 조회 13번을 배열 한 번으로). 여러 region 스레드가 같은 값을 써도 무해하다.
     */
    private static volatile PathType[] stateMemo;

    static PathType pathTypeOfState(int s) {
        PathType[] m = stateMemo;
        if (m == null) stateMemo = m = new PathType[B.BLOCKS.stateCount()];
        if (s < 0 || s >= m.length) return computePathTypeOfState(s);
        PathType t = m[s];
        if (t == null) m[s] = t = computePathTypeOfState(s);
        return t;
    }

    private static PathType computePathTypeOfState(int s) {
        if (B.isAir(s)) return PathType.OPEN;
        if (B.isTag(s, "minecraft:trapdoors") || B.is(s, "minecraft:lily_pad") || B.is(s, "minecraft:big_dripleaf")) return PathType.TRAPDOOR;
        if (B.is(s, "minecraft:powder_snow")) return PathType.POWDER_SNOW;
        if (B.is(s, "minecraft:cactus") || B.is(s, "minecraft:sweet_berry_bush")) return PathType.DAMAGE_OTHER;
        if (B.is(s, "minecraft:honey_block")) return PathType.STICKY_HONEY;
        if (B.is(s, "minecraft:cocoa")) return PathType.COCOA;
        if (B.is(s, "minecraft:wither_rose") || B.is(s, "minecraft:pointed_dripstone")) return PathType.DAMAGE_CAUTIOUS;
        int fluid = BlockBehaviors.of(s).getFluidState(s);
        if (Fluids.isLava(fluid)) return PathType.LAVA;
        if (isBurningBlock(s)) return PathType.DAMAGE_FIRE;
        if (B.isTag(s, "minecraft:doors")) {
            if (OPEN.is(s)) return PathType.DOOR_OPEN;
            return BlockSetType.of(B.block(s)).canOpenByHand() ? PathType.DOOR_WOOD_CLOSED : PathType.DOOR_IRON_CLOSED;
        }
        if (B.isTag(s, "minecraft:rails")) return PathType.RAIL;
        if (B.isTag(s, "minecraft:leaves")) return PathType.LEAVES;
        if (B.isTag(s, "minecraft:fences") || B.isTag(s, "minecraft:walls") || B.isTag(s, "minecraft:fence_gates") && !OPEN.is(s)) return PathType.FENCE;
        if (!Pathfinding.isPathfindable(s, Pathfinding.LAND)) return PathType.BLOCKED;
        return Fluids.isWater(fluid) ? PathType.WATER : PathType.OPEN;
    }

    /** NodeEvaluator.isBurningBlock: 불 태그, 용암, 마그마 블록, 켜진 모닥불, 용암 가마솥. */
    public static boolean isBurningBlock(int s) {
        return B.isTag(s, "minecraft:fire") || B.is(s, "minecraft:lava") || B.is(s, "minecraft:magma_block")
                || B.isTag(s, "minecraft:campfires") && B.prop("lit").is(s) || B.is(s, "minecraft:lava_cauldron");
    }

    private static int floor(double v) { return (int) Math.floor(v); }
}
