// 바닐라 Climate.RTree 1:1 포팅 — 멀티노이즈 바이옴 탐색. 최근접(거리 동률 포함) 결과가 바닐라와 같도록
// 트리 구성(7차원 정렬·6분기 버킷화)과 탐색 순서(엄격한 '>' 비교)를 그대로 따른다.
// 바닐라는 스레드별로 "직전 결과 leaf"를 탐색 출발점으로 넘겨서, 거리가 같은 후보가 여럿이면 직전 leaf가 있는 쪽이 이긴다.
// 호출자가 Leaf 하나를 들고 다니며(search의 last 인자) 바닐라의 쿼리 순서를 똑같이 재현해야 동률이 같아진다.
package org.domi.furnace.server.world.gen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

final class ClimateRTree {

    private static final int DIMS = 7;
    private static final int CHILDREN_PER_NODE = 6;

    abstract static class Node {
        final long[] min = new long[DIMS];
        final long[] max = new long[DIMS];

        /** 바닐라 Node.distance(long[] target): 7차원 (구간 거리)² 합. */
        final long distance(long[] target) {
            long sum = 0;
            for (int i = 0; i < DIMS; i++) {
                long d = paramDistance(min[i], max[i], target[i]);
                sum += d * d;
            }
            return sum;
        }

        abstract Leaf search(long[] target, Leaf best);
    }

    static final class Leaf extends Node {
        final int value;

        Leaf(long[] mins, long[] maxs, int value) {
            System.arraycopy(mins, 0, min, 0, DIMS);
            System.arraycopy(maxs, 0, max, 0, DIMS);
            this.value = value;
        }

        @Override
        Leaf search(long[] target, Leaf best) {
            return this;
        }
    }

    static final class SubTree extends Node {
        final Node[] children;

        SubTree(List<? extends Node> list) {
            this.children = list.toArray(new Node[0]);
            Arrays.fill(min, Long.MAX_VALUE);
            Arrays.fill(max, Long.MIN_VALUE);
            for (Node child : children) {
                for (int i = 0; i < DIMS; i++) {
                    min[i] = Math.min(min[i], child.min[i]);
                    max[i] = Math.max(max[i], child.max[i]);
                }
            }
        }

        @Override
        Leaf search(long[] target, Leaf best) {
            long minDist = best == null ? Long.MAX_VALUE : best.distance(target);
            Leaf result = best;
            for (Node child : children) {
                long childDist = child.distance(target);
                if (minDist > childDist) {
                    Leaf found = child.search(target, result);
                    long foundDist = child == found ? childDist : found.distance(target);
                    if (minDist > foundDist) {
                        minDist = foundDist;
                        result = found;
                    }
                }
            }
            return result;
        }
    }

    private final Node root;

    ClimateRTree(List<Leaf> leaves) {
        if (leaves.isEmpty()) throw new IllegalArgumentException("Need at least one value to build the search tree.");
        this.root = build(new ArrayList<>(leaves));
    }

    /** target: 기후 6개(온도·습도·대륙성·침식·깊이·기이함). 7번째(offset) 차원 목표값은 0. */
    Leaf search(long[] target6, Leaf last) {
        long[] t = new long[DIMS];
        System.arraycopy(target6, 0, t, 0, 6);
        return root.search(t, last);
    }

    /**
     * 직전 결과 힌트와 상관없이 답이 하나로 정해질 때만 그 값, 아니면 Integer.MIN_VALUE.
     * 힌트는 거리 동률일 때만 답을 바꾸므로(탐색은 언제나 최근접), 최근접 거리에 값이 다른 leaf 가 또 있으면 힌트에 따라 답이 갈린다.
     */
    int searchUnique(long[] target6) {
        long[] t = new long[DIMS];
        System.arraycopy(target6, 0, t, 0, 6);
        Leaf best = root.search(t, null);
        long d = best.distance(t);
        return otherAt(root, t, d, best.value) ? Integer.MIN_VALUE : best.value;
    }

    private static boolean otherAt(Node n, long[] t, long d, int value) {
        if (n instanceof Leaf l) return l.value != value && l.distance(t) == d;
        for (Node c : ((SubTree) n).children) {
            if (c.distance(t) <= d && otherAt(c, t, d, value)) return true;
        }
        return false;
    }

    static long paramDistance(long min, long max, long v) {
        long above = v - max;
        long below = min - v;
        return above > 0 ? above : Math.max(below, 0L);
    }

    // ---- 트리 구성 (바닐라 RTree.build) ----

    private static Node build(List<? extends Node> children) {
        if (children.size() == 1) return children.get(0);
        if (children.size() <= CHILDREN_PER_NODE) {
            children.sort(Comparator.comparingLong(node -> {
                long sum = 0;
                for (int i = 0; i < DIMS; i++) sum += Math.abs((node.min[i] + node.max[i]) / 2L);
                return sum;
            }));
            return new SubTree(children);
        }
        long minCost = Long.MAX_VALUE;
        int bestDim = -1;
        List<SubTree> bestBuckets = null;
        for (int dim = 0; dim < DIMS; dim++) {
            sort(children, dim, false);
            List<SubTree> buckets = bucketize(children);
            long cost = 0;
            for (SubTree bucket : buckets) cost += cost(bucket);
            if (minCost > cost) {
                minCost = cost;
                bestDim = dim;
                bestBuckets = buckets;
            }
        }
        sort(bestBuckets, bestDim, true);
        List<Node> built = new ArrayList<>(bestBuckets.size());
        for (SubTree bucket : bestBuckets) built.add(build(new ArrayList<>(Arrays.asList(bucket.children))));
        return new SubTree(built);
    }

    private static void sort(List<? extends Node> nodes, int dim, boolean abs) {
        Comparator<Node> comparator = comparator(dim, abs);
        for (int i = 1; i < DIMS; i++) comparator = comparator.thenComparing(comparator((dim + i) % DIMS, abs));
        nodes.sort(comparator);
    }

    private static Comparator<Node> comparator(int dim, boolean abs) {
        return Comparator.comparingLong(node -> {
            long mid = (node.min[dim] + node.max[dim]) / 2L;
            return abs ? Math.abs(mid) : mid;
        });
    }

    private static List<SubTree> bucketize(List<? extends Node> nodes) {
        List<SubTree> buckets = new ArrayList<>();
        List<Node> current = new ArrayList<>();
        int perBucket = (int) Math.pow(6.0, Math.floor(Math.log((double) nodes.size() - 0.01) / Math.log(6.0)));
        for (Node node : nodes) {
            current.add(node);
            if (current.size() >= perBucket) {
                buckets.add(new SubTree(current));
                current = new ArrayList<>();
            }
        }
        if (!current.isEmpty()) buckets.add(new SubTree(current));
        return buckets;
    }

    private static long cost(Node space) {
        long sum = 0;
        for (int i = 0; i < DIMS; i++) sum += Math.abs(space.max[i] - space.min[i]);
        return sum;
    }
}
