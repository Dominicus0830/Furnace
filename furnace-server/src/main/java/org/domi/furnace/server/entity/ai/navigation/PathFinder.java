// 바닐라 PathFinder: A*. 시작 노드에서 열린 집합(BinaryHeap)을 확장해 목표들 중 가장 가까운(도달했으면 가장 짧은) 경로를 만든다.
package org.domi.furnace.server.entity.ai.navigation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Mob;

public class PathFinder {
    private static final float FUDGING = 1.5F;
    private final Node[] neighbors = new Node[32];
    private final int maxVisitedNodes;
    private final NodeEvaluator nodeEvaluator;
    private final BinaryHeap openSet = new BinaryHeap();

    public PathFinder(NodeEvaluator evaluator, int maxVisitedNodes) {
        this.nodeEvaluator = evaluator;
        this.maxVisitedNodes = maxVisitedNodes;
    }

    public Path findPath(ChunkLevel level, Mob mob, Set<Pos> targets, float maxRange, int accuracy, float searchDepthMultiplier) {
        openSet.clear();
        nodeEvaluator.prepare(level, mob);
        Node start = nodeEvaluator.getStart();
        if (start == null) return null;
        // 바닐라는 Collectors.toMap 이라 순서가 해시 순서 — 후보가 하나일 때가 대부분이고, 같은 거리일 때만 결과가 갈린다
        Map<Target, Pos> map = new LinkedHashMap<>();
        for (Pos p : targets) map.put(nodeEvaluator.getTarget(p.x(), p.y(), p.z()), p);
        Path path = findPath(start, map, maxRange, accuracy, searchDepthMultiplier);
        nodeEvaluator.done();
        return path;
    }

    private Path findPath(Node start, Map<Target, Pos> targets, float maxRange, int accuracy, float searchDepthMultiplier) {
        Set<Target> targetSet = targets.keySet();
        start.g = 0.0F;
        start.h = getBestH(start, targetSet);
        start.f = start.h;
        openSet.clear();
        openSet.insert(start);
        int visited = 0;
        Set<Target> reached = new HashSet<>();
        int maxVisits = (int) (maxVisitedNodes * searchDepthMultiplier);
        while (!openSet.isEmpty()) {
            if (++visited >= maxVisits) break;
            Node cur = openSet.pop();
            cur.closed = true;
            for (Target t : targetSet) {
                if (cur.distanceManhattan(t) <= accuracy) {
                    t.setReached();
                    reached.add(t);
                }
            }
            if (!reached.isEmpty()) break;
            if (!(cur.distanceTo(start) >= maxRange)) {
                int n = nodeEvaluator.getNeighbors(neighbors, cur);
                for (int i = 0; i < n; i++) {
                    Node nb = neighbors[i];
                    float dist = distance(cur, nb);
                    nb.walkedDistance = cur.walkedDistance + dist;
                    float g = cur.g + dist + nb.costMalus;
                    if (nb.walkedDistance < maxRange && (!nb.inOpenSet() || g < nb.g)) {
                        nb.cameFrom = cur;
                        nb.g = g;
                        nb.h = getBestH(nb, targetSet) * FUDGING;
                        if (nb.inOpenSet()) {
                            openSet.changeCost(nb, nb.g + nb.h);
                        } else {
                            nb.f = nb.g + nb.h;
                            openSet.insert(nb);
                        }
                    }
                }
            }
        }
        Path best = null;
        if (!reached.isEmpty()) {
            for (Target t : reached) {
                Path p = reconstructPath(t.getBestNode(), targets.get(t), true);
                if (best == null || p.getNodeCount() < best.getNodeCount()) best = p;
            }
        } else {
            for (Target t : targetSet) {
                Path p = reconstructPath(t.getBestNode(), targets.get(t), false);
                if (best == null || Float.compare(p.getDistToTarget(), best.getDistToTarget()) < 0
                        || Float.compare(p.getDistToTarget(), best.getDistToTarget()) == 0 && p.getNodeCount() < best.getNodeCount()) best = p;
            }
        }
        return best;
    }

    protected float distance(Node a, Node b) { return a.distanceTo(b); }

    private float getBestH(Node node, Set<Target> targets) {
        float best = Float.MAX_VALUE;
        for (Target t : targets) {
            float d = node.distanceTo(t);
            t.updateBest(d, node);
            best = Math.min(d, best);
        }
        return best;
    }

    private Path reconstructPath(Node end, Pos target, boolean reached) {
        List<Node> nodes = new ArrayList<>();
        Node n = end;
        nodes.add(0, end);
        while (n.cameFrom != null) {
            n = n.cameFrom;
            nodes.add(0, n);
        }
        return new Path(nodes, target, reached);
    }

}
