// 바닐라 Path: 노드 목록과 현재 위치(nextNodeIndex), 목표 칸, 목표까지 남은 거리, 도달 여부.
package org.domi.furnace.server.entity.ai.navigation;

import java.util.List;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Entity;

public class Path {
    private final List<Node> nodes;
    private int nextNodeIndex;
    private final Pos target;
    private final float distToTarget;
    private final boolean reached;

    public Path(List<Node> nodes, Pos target, boolean reached) {
        this.nodes = nodes;
        this.target = target;
        this.distToTarget = nodes.isEmpty() ? Float.MAX_VALUE : nodes.get(nodes.size() - 1).distanceManhattan(target.x(), target.y(), target.z());
        this.reached = reached;
    }

    public void advance() { nextNodeIndex++; }

    public boolean notStarted() { return nextNodeIndex <= 0; }

    public boolean isDone() { return nextNodeIndex >= nodes.size(); }

    public Node getEndNode() { return nodes.isEmpty() ? null : nodes.get(nodes.size() - 1); }

    public Node getNode(int i) { return nodes.get(i); }

    public void truncateNodes(int n) {
        if (nodes.size() > n) nodes.subList(n, nodes.size()).clear();
    }

    public void replaceNode(int i, Node n) { nodes.set(i, n); }

    public int getNodeCount() { return nodes.size(); }

    public int getNextNodeIndex() { return nextNodeIndex; }

    public void setNextNodeIndex(int i) { nextNodeIndex = i; }

    /** getEntityPosAtNode: 노드 칸의 가운데(몹 너비에 따라 (int)(너비+1)*0.5 만큼 안쪽). */
    public double[] getEntityPosAtNode(Entity e, int i) {
        Node n = nodes.get(i);
        double half = (int) (e.bbWidth() + 1.0F) * 0.5;
        return new double[] {n.x + half, n.y, n.z + half};
    }

    public Pos getNodePos(int i) { return new Pos(nodes.get(i).x, nodes.get(i).y, nodes.get(i).z); }

    public double[] getNextEntityPos(Entity e) { return getEntityPosAtNode(e, nextNodeIndex); }

    public Pos getNextNodePos() { return getNodePos(nextNodeIndex); }

    public Node getNextNode() { return nodes.get(nextNodeIndex); }

    public Node getPreviousNode() { return nextNodeIndex > 0 ? nodes.get(nextNodeIndex - 1) : null; }

    public boolean sameAs(Path o) {
        if (o == null || o.nodes.size() != nodes.size()) return false;
        for (int i = 0; i < nodes.size(); i++) {
            Node a = nodes.get(i), b = o.nodes.get(i);
            if (a.x != b.x || a.y != b.y || a.z != b.z) return false;
        }
        return true;
    }

    public boolean canReach() { return reached; }

    public Pos getTarget() { return target; }

    public float getDistToTarget() { return distToTarget; }

    @Override public String toString() { return "Path(length=" + nodes.size() + ")"; }
}
