// 바닐라 FlyNodeEvaluator: 나는 몹의 노드 평가 — 3차원 26방향 이웃(대각 포함)을 만들고 공중 칸을 돌아다닌다.
package org.domi.furnace.server.entity.ai.navigation;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.AABB;
import org.domi.furnace.server.entity.Mob;

public class FlyNodeEvaluator extends WalkNodeEvaluator {

    @Override
    public void prepare(ChunkLevel level, Mob mob) {
        super.prepare(level, mob);
        mob.onPathfindingStart();
    }

    @Override
    public void done() {
        mob.onPathfindingDone();
        super.done();
    }

    @Override
    public Node getStart() {
        int y;
        if (canFloat() && mob.isInWater()) {
            y = mob.blockY();
            int bx = (int) Math.floor(mob.x()), bz = (int) Math.floor(mob.z());
            int state = level.getBlockState(new Pos(bx, y, bz));
            while (B.is(state, "minecraft:water")) state = level.getBlockState(new Pos(bx, ++y, bz));
        } else {
            y = (int) Math.floor(mob.y() + 0.5);
        }
        Pos p = new Pos((int) Math.floor(mob.x()), y, (int) Math.floor(mob.z()));
        if (!canStartAt(p)) {
            for (Pos c : iterateStartCandidates()) {
                if (canStartAt(c)) return getStartNode(c);
            }
        }
        return getStartNode(p);
    }

    @Override
    protected boolean canStartAt(Pos pos) {
        PathType t = getCachedPathType(pos.x(), pos.y(), pos.z());
        return mob.getPathfindingMalus(t) >= 0.0F;
    }

    @Override
    public Target getTarget(double x, double y, double z) { return getTargetNodeAt(x, y, z); }

    /** 큰 몸이면 네 모서리, 작은 몸이면 몸 주변 상자 안의 무작위 10곳(앞에서부터 하나씩 난수를 쓴다). */
    private Iterable<Pos> iterateStartCandidates() {
        AABB bb = mob.boundingBox();
        boolean small = (bb.getXsize() + bb.getYsize() + bb.getZsize()) / 3.0 < 1.0;
        int by = mob.blockY();
        if (!small) {
            return List.of(new Pos((int) Math.floor(bb.minX()), by, (int) Math.floor(bb.minZ())), new Pos((int) Math.floor(bb.minX()), by, (int) Math.floor(bb.maxZ())),
                    new Pos((int) Math.floor(bb.maxX()), by, (int) Math.floor(bb.minZ())), new Pos((int) Math.floor(bb.maxX()), by, (int) Math.floor(bb.maxZ())));
        }
        double dz = Math.max(0.0, 1.1F - bb.getZsize());
        double dx = Math.max(0.0, 1.1F - bb.getXsize());
        double dy = Math.max(0.0, 1.1F - bb.getYsize());
        AABB box = bb.inflate(dx, dy, dz);
        int minX = (int) Math.floor(box.minX()), minY = (int) Math.floor(box.minY()), minZ = (int) Math.floor(box.minZ());
        int maxX = (int) Math.floor(box.maxX()), maxY = (int) Math.floor(box.maxY()), maxZ = (int) Math.floor(box.maxZ());
        var r = mob.random();
        int w = maxX - minX + 1, h = maxY - minY + 1, d = maxZ - minZ + 1;
        return () -> new java.util.Iterator<>() {
            int counter = 10;

            public boolean hasNext() { return counter > 0; }

            public Pos next() {
                int x = r.nextInt(w) + minX, y = r.nextInt(h) + minY, z = r.nextInt(d) + minZ;
                counter--;
                return new Pos(x, y, z);
            }
        };
    }

    @Override
    public int getNeighbors(Node[] out, Node node) {
        int n = 0;
        int x = node.x, y = node.y, z = node.z;
        Node south = find(x, y, z + 1);
        if (valid(south)) out[n++] = south;
        Node west = find(x - 1, y, z);
        if (valid(west)) out[n++] = west;
        Node east = find(x + 1, y, z);
        if (valid(east)) out[n++] = east;
        Node north = find(x, y, z - 1);
        if (valid(north)) out[n++] = north;
        Node up = find(x, y + 1, z);
        if (valid(up)) out[n++] = up;
        Node down = find(x, y - 1, z);
        if (valid(down)) out[n++] = down;
        Node upS = find(x, y + 1, z + 1);
        if (valid(upS) && hasMalusOk(south) && hasMalusOk(up)) out[n++] = upS;
        Node upW = find(x - 1, y + 1, z);
        if (valid(upW) && hasMalusOk(west) && hasMalusOk(up)) out[n++] = upW;
        Node upE = find(x + 1, y + 1, z);
        if (valid(upE) && hasMalusOk(east) && hasMalusOk(up)) out[n++] = upE;
        Node upN = find(x, y + 1, z - 1);
        if (valid(upN) && hasMalusOk(north) && hasMalusOk(up)) out[n++] = upN;
        Node downS = find(x, y - 1, z + 1);
        if (valid(downS) && hasMalusOk(south) && hasMalusOk(down)) out[n++] = downS;
        Node downW = find(x - 1, y - 1, z);
        if (valid(downW) && hasMalusOk(west) && hasMalusOk(down)) out[n++] = downW;
        Node downE = find(x + 1, y - 1, z);
        if (valid(downE) && hasMalusOk(east) && hasMalusOk(down)) out[n++] = downE;
        Node downN = find(x, y - 1, z - 1);
        if (valid(downN) && hasMalusOk(north) && hasMalusOk(down)) out[n++] = downN;
        Node ne = find(x + 1, y, z - 1);
        if (valid(ne) && hasMalusOk(north) && hasMalusOk(east)) out[n++] = ne;
        Node se = find(x + 1, y, z + 1);
        if (valid(se) && hasMalusOk(south) && hasMalusOk(east)) out[n++] = se;
        Node nw = find(x - 1, y, z - 1);
        if (valid(nw) && hasMalusOk(north) && hasMalusOk(west)) out[n++] = nw;
        Node sw = find(x - 1, y, z + 1);
        if (valid(sw) && hasMalusOk(south) && hasMalusOk(west)) out[n++] = sw;
        Node upNE = find(x + 1, y + 1, z - 1);
        if (valid(upNE) && hasMalusOk(ne) && hasMalusOk(north) && hasMalusOk(east) && hasMalusOk(up) && hasMalusOk(upN) && hasMalusOk(upE)) out[n++] = upNE;
        Node upSE = find(x + 1, y + 1, z + 1);
        if (valid(upSE) && hasMalusOk(se) && hasMalusOk(south) && hasMalusOk(east) && hasMalusOk(up) && hasMalusOk(upS) && hasMalusOk(upE)) out[n++] = upSE;
        Node upNW = find(x - 1, y + 1, z - 1);
        if (valid(upNW) && hasMalusOk(nw) && hasMalusOk(north) && hasMalusOk(west) && hasMalusOk(up) && hasMalusOk(upN) && hasMalusOk(upW)) out[n++] = upNW;
        Node upSW = find(x - 1, y + 1, z + 1);
        if (valid(upSW) && hasMalusOk(sw) && hasMalusOk(south) && hasMalusOk(west) && hasMalusOk(up) && hasMalusOk(upS) && hasMalusOk(upW)) out[n++] = upSW;
        Node downNE = find(x + 1, y - 1, z - 1);
        if (valid(downNE) && hasMalusOk(ne) && hasMalusOk(north) && hasMalusOk(east) && hasMalusOk(down) && hasMalusOk(downN) && hasMalusOk(downE)) out[n++] = downNE;
        Node downSE = find(x + 1, y - 1, z + 1);
        if (valid(downSE) && hasMalusOk(se) && hasMalusOk(south) && hasMalusOk(east) && hasMalusOk(down) && hasMalusOk(downS) && hasMalusOk(downE)) out[n++] = downSE;
        Node downNW = find(x - 1, y - 1, z - 1);
        if (valid(downNW) && hasMalusOk(nw) && hasMalusOk(north) && hasMalusOk(west) && hasMalusOk(down) && hasMalusOk(downN) && hasMalusOk(downW)) out[n++] = downNW;
        Node downSW = find(x - 1, y - 1, z + 1);
        if (valid(downSW) && hasMalusOk(sw) && hasMalusOk(south) && hasMalusOk(west) && hasMalusOk(down) && hasMalusOk(downS) && hasMalusOk(downW)) out[n++] = downSW;
        return n;
    }

    private static boolean hasMalusOk(Node n) { return n != null && n.costMalus >= 0.0F; }

    private static boolean valid(Node n) { return n != null && !n.closed; }

    private Node find(int x, int y, int z) {
        Node node = null;
        PathType type = getCachedPathType(x, y, z);
        float malus = mob.getPathfindingMalus(type);
        if (malus >= 0.0F) {
            node = getNode(x, y, z);
            node.type = type;
            node.costMalus = Math.max(node.costMalus, malus);
            if (type == PathType.WALKABLE) node.costMalus++;
        }
        return node;
    }

    @Override
    public PathType getPathType(PathfindingContext ctx, int x, int y, int z) {
        PathType t = ctx.getPathTypeFromState(x, y, z);
        if (t == PathType.OPEN && y >= ctx.level().minBuildHeight() + 1) {
            PathType below = ctx.getPathTypeFromState(x, y - 1, z);
            if (below == PathType.DAMAGE_FIRE || below == PathType.LAVA) {
                t = PathType.DAMAGE_FIRE;
            } else if (below == PathType.DAMAGE_OTHER) {
                t = PathType.DAMAGE_OTHER;
            } else if (below == PathType.COCOA) {
                t = PathType.COCOA;
            } else if (below == PathType.FENCE) {
                if (!new Pos(x, y - 1, z).equals(ctx.mobPosition())) t = PathType.FENCE;
            } else {
                t = below != PathType.WALKABLE && below != PathType.OPEN && below != PathType.WATER ? PathType.WALKABLE : PathType.OPEN;
            }
        }
        if (t == PathType.WALKABLE || t == PathType.OPEN) t = checkNeighbourBlocks(ctx, x, y, z, t);
        return t;
    }
}
