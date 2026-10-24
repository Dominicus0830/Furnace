// 바닐라 AmphibiousNodeEvaluator: 물과 땅을 모두 다니는 몹(거북·익사한 좀비·개구리·아홀로틀)의 노드 평가 — 걷기 비용 6, 물가 비용 4, 위·아래 이웃도 만든다.
package org.domi.furnace.server.entity.ai.navigation;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Mob;

public class AmphibiousNodeEvaluator extends WalkNodeEvaluator {
    private final boolean prefersShallowSwimming;
    private float oldWalkableCost;
    private float oldWaterBorderCost;

    public AmphibiousNodeEvaluator(boolean prefersShallowSwimming) { this.prefersShallowSwimming = prefersShallowSwimming; }

    @Override
    public void prepare(ChunkLevel level, Mob mob) {
        super.prepare(level, mob);
        mob.setPathfindingMalus(PathType.WATER, 0.0F);
        oldWalkableCost = mob.getPathfindingMalus(PathType.WALKABLE);
        mob.setPathfindingMalus(PathType.WALKABLE, 6.0F);
        oldWaterBorderCost = mob.getPathfindingMalus(PathType.WATER_BORDER);
        mob.setPathfindingMalus(PathType.WATER_BORDER, 4.0F);
    }

    @Override
    public void done() {
        mob.setPathfindingMalus(PathType.WALKABLE, oldWalkableCost);
        mob.setPathfindingMalus(PathType.WATER_BORDER, oldWaterBorderCost);
        super.done();
    }

    @Override
    public Node getStart() {
        if (!mob.isInWater()) return super.getStart();
        var bb = mob.boundingBox();
        return getStartNode(new Pos((int) Math.floor(bb.minX()), (int) Math.floor(bb.minY() + 0.5), (int) Math.floor(bb.minZ())));
    }

    @Override
    public Target getTarget(double x, double y, double z) { return getTargetNodeAt(x, y + 0.5, z); }

    @Override
    public int getNeighbors(Node[] out, Node node) {
        int count = super.getNeighbors(out, node);
        PathType above = getCachedPathType(node.x, node.y + 1, node.z);
        PathType here = getCachedPathType(node.x, node.y, node.z);
        int maxYStep = mob.getPathfindingMalus(above) >= 0.0F && here != PathType.STICKY_HONEY ? (int) Math.floor(Math.max(1.0F, mob.getStepHeight())) : 0;
        double floor = getFloorLevel(new Pos(node.x, node.y, node.z));
        Node up = findAcceptedNode(node.x, node.y + 1, node.z, Math.max(0, maxYStep - 1), floor, -1, here);
        Node down = findAcceptedNode(node.x, node.y - 1, node.z, maxYStep, floor, -1, here);
        if (isValidWaterNeighbor(up, node)) out[count++] = up;
        if (isValidWaterNeighbor(down, node) && here != PathType.TRAPDOOR) out[count++] = down;
        for (int i = 0; i < count; i++) {
            Node n = out[i];
            if (n.type == PathType.WATER && prefersShallowSwimming && n.y < mob.level().seaLevel() - 10) n.costMalus++;
        }
        return count;
    }

    private boolean isValidWaterNeighbor(Node n, Node from) { return isNeighborValid(n, from) && n.type == PathType.WATER; }

    @Override
    protected boolean isAmphibious() { return true; }

    @Override
    public PathType getPathType(PathfindingContext ctx, int x, int y, int z) {
        PathType t = ctx.getPathTypeFromState(x, y, z);
        if (t == PathType.WATER) {
            for (int d : Dir.ALL) {
                PathType n = ctx.getPathTypeFromState(x + Dir.DX[d], y + Dir.DY[d], z + Dir.DZ[d]);
                if (n == PathType.BLOCKED) return PathType.WATER_BORDER;
            }
            return PathType.WATER;
        }
        return super.getPathType(ctx, x, y, z);
    }
}
