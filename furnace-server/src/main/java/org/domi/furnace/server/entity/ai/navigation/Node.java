// 바닐라 Node(길찾기 노드): 칸 좌표와 A* 값(g=cost, h=heuristic, f), 부모, 닫힘 여부, 걸어온 거리, 칸 비용(malus)과 종류.
package org.domi.furnace.server.entity.ai.navigation;

public class Node {
    public final int x, y, z;
    private final int hash;
    public int heapIdx = -1;
    public float g, h, f;
    public Node cameFrom;
    public boolean closed;
    public float walkedDistance;
    public float costMalus;
    public PathType type = PathType.BLOCKED;

    public Node(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.hash = createHash(x, y, z);
    }

    public Node cloneAndMove(int nx, int ny, int nz) {
        Node n = new Node(nx, ny, nz);
        n.heapIdx = heapIdx;
        n.g = g;
        n.h = h;
        n.f = f;
        n.cameFrom = cameFrom;
        n.closed = closed;
        n.walkedDistance = walkedDistance;
        n.costMalus = costMalus;
        n.type = type;
        return n;
    }

    public static int createHash(int x, int y, int z) {
        return y & 0xFF | (x & 32767) << 8 | (z & 32767) << 24 | (x < 0 ? Integer.MIN_VALUE : 0) | (z < 0 ? 32768 : 0);
    }

    public float distanceTo(Node o) {
        float dx = o.x - x, dy = o.y - y, dz = o.z - z;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public float distanceToXZ(Node o) {
        float dx = o.x - x, dz = o.z - z;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    public float distanceTo(int bx, int by, int bz) {
        float dx = bx - x, dy = by - y, dz = bz - z;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public float distanceToSqr(Node o) {
        float dx = o.x - x, dy = o.y - y, dz = o.z - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public float distanceToSqr(int bx, int by, int bz) {
        float dx = bx - x, dy = by - y, dz = bz - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public float distanceManhattan(Node o) {
        return Math.abs(o.x - x) + (float) Math.abs(o.y - y) + Math.abs(o.z - z);
    }

    public float distanceManhattan(int bx, int by, int bz) {
        return Math.abs(bx - x) + (float) Math.abs(by - y) + Math.abs(bz - z);
    }

    public boolean inOpenSet() { return heapIdx >= 0; }

    @Override
    public boolean equals(Object o) {
        return o instanceof Node n && hash == n.hash && x == n.x && y == n.y && z == n.z;
    }

    @Override public int hashCode() { return hash; }

    @Override public String toString() { return "Node{x=" + x + ", y=" + y + ", z=" + z + "}"; }
}
