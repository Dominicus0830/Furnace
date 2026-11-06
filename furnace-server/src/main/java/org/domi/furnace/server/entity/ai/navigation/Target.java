// 바닐라 Target: 길찾기 목표 칸 — 지금까지 가장 가까이 간 거리(bestHeuristic)와 그 노드, 도달 여부.
package org.domi.furnace.server.entity.ai.navigation;

public class Target extends Node {
    private float bestHeuristic = Float.MAX_VALUE;
    private Node bestNode;
    private boolean reached;

    public Target(Node n) { super(n.x, n.y, n.z); }

    public Target(int x, int y, int z) { super(x, y, z); }

    public void updateBest(float h, Node n) {
        if (h < bestHeuristic) {
            bestHeuristic = h;
            bestNode = n;
        }
    }

    public Node getBestNode() { return bestNode; }

    public void setReached() { reached = true; }

    public boolean isReached() { return reached; }
}
