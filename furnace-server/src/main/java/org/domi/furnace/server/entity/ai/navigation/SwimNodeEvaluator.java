// 바닐라 SwimNodeEvaluator: 물속 몹의 노드 평가 — 6방향 + 수평 대각 이웃, 물 칸(WATER)과 물 위 숨쉬는 칸(BREACH, 돌고래만)만 지나간다.
package org.domi.furnace.server.entity.ai.navigation;

import java.util.HashMap;
import java.util.Map;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.Pathfinding;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Mob;

public class SwimNodeEvaluator extends NodeEvaluator {
    /** Direction.values() 순서: 아래, 위, 북, 남, 서, 동. */
    private static final int[] DX = {0, 0, 0, 0, -1, 1}, DY = {-1, 1, 0, 0, 0, 0}, DZ = {0, 0, -1, 1, 0, 0};
    /** Direction.Plane.HORIZONTAL 순서(북, 동, 남, 서)를 위 배열 번호로. */
    private static final int[] HORIZONTAL = {2, 5, 3, 4};
    /** 같은 순서의 getClockWise 번호(북→동, 동→남, 남→서, 서→북). */
    private static final int[] CLOCKWISE = {5, 3, 4, 2};

    private final boolean allowBreaching;
    private final Map<Long, PathType> pathTypesByPosCache = new HashMap<>();

    public SwimNodeEvaluator(boolean allowBreaching) { this.allowBreaching = allowBreaching; }

    @Override
    public void prepare(ChunkLevel level, Mob mob) {
        super.prepare(level, mob);
        pathTypesByPosCache.clear();
    }

    @Override
    public void done() {
        super.done();
        pathTypesByPosCache.clear();
    }

    @Override
    public Node getStart() {
        var bb = mob.boundingBox();
        return getNode((int) Math.floor(bb.minX()), (int) Math.floor(bb.minY() + 0.5), (int) Math.floor(bb.minZ()));
    }

    @Override
    public Target getTarget(double x, double y, double z) { return getTargetNodeAt(x, y, z); }

    @Override
    public int getNeighbors(Node[] out, Node node) {
        int n = 0;
        Node[] byDir = new Node[6];
        for (int d = 0; d < 6; d++) {
            Node nb = findAcceptedNode(node.x + DX[d], node.y + DY[d], node.z + DZ[d]);
            byDir[d] = nb;
            if (isNodeValid(nb)) out[n++] = nb;
        }
        for (int i = 0; i < 4; i++) {
            int d = HORIZONTAL[i], cw = CLOCKWISE[i];
            if (hasMalus(byDir[d]) && hasMalus(byDir[cw])) {
                Node nb = findAcceptedNode(node.x + DX[d] + DX[cw], node.y, node.z + DZ[d] + DZ[cw]);
                if (isNodeValid(nb)) out[n++] = nb;
            }
        }
        return n;
    }

    private static boolean isNodeValid(Node nb) { return nb != null && !nb.closed; }

    private static boolean hasMalus(Node nb) { return nb != null && nb.costMalus >= 0.0F; }

    protected Node findAcceptedNode(int x, int y, int z) {
        Node node = null;
        PathType type = getCachedPathType(x, y, z);
        if (allowBreaching && type == PathType.BREACH || type == PathType.WATER) {
            float malus = mob.getPathfindingMalus(type);
            if (malus >= 0.0F) {
                node = getNode(x, y, z);
                node.type = type;
                node.costMalus = Math.max(node.costMalus, malus);
                if (Fluids.isEmpty(level.getFluidState(new Pos(x, y, z)))) node.costMalus += 8.0F;
            }
        }
        return node;
    }

    protected PathType getCachedPathType(int x, int y, int z) {
        return pathTypesByPosCache.computeIfAbsent(new Pos(x, y, z).asLong(), k -> getPathType(new PathfindingContext(level, mob), x, y, z));
    }

    @Override
    public PathType getPathType(PathfindingContext ctx, int x, int y, int z) { return getPathTypeOfMob(ctx, x, y, z, mob); }

    @Override
    public PathType getPathTypeOfMob(PathfindingContext ctx, int x, int y, int z, Mob mob) {
        int state = 0;
        for (int px = x; px < x + entityWidth; px++) {
            for (int py = y; py < y + entityHeight; py++) {
                for (int pz = z; pz < z + entityDepth; pz++) {
                    state = ctx.getBlockState(new Pos(px, py, pz));
                    int fluid = ctx.level().getFluidState(new Pos(px, py, pz));
                    if (Fluids.isEmpty(fluid) && Pathfinding.isPathfindable(state, Pathfinding.WATER) && B.isAir(state)) return PathType.BREACH;
                    if (!Fluids.isWater(fluid)) return PathType.BLOCKED;
                }
            }
        }
        return Pathfinding.isPathfindable(state, Pathfinding.WATER) ? PathType.WATER : PathType.BLOCKED;
    }
}
