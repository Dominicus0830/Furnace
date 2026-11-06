// 바닐라 BinaryHeap: 노드의 f 값(Node.f)으로 정렬되는 최소 힙. 노드는 자기 힙 위치(heapIdx)를 안다.
package org.domi.furnace.server.entity.ai.navigation;

import java.util.Arrays;

public class BinaryHeap {
    private Node[] heap = new Node[128];
    private int size;

    public Node insert(Node n) {
        if (n.heapIdx >= 0) throw new IllegalStateException("OW KNOWS!");
        if (size == heap.length) heap = Arrays.copyOf(heap, size << 1);
        heap[size] = n;
        n.heapIdx = size;
        upHeap(size++);
        return n;
    }

    public void clear() { size = 0; }

    public Node peek() { return heap[0]; }

    public Node pop() {
        Node top = heap[0];
        heap[0] = heap[--size];
        heap[size] = null;
        if (size > 0) downHeap(0);
        top.heapIdx = -1;
        return top;
    }

    public void remove(Node n) {
        heap[n.heapIdx] = heap[--size];
        heap[size] = null;
        if (size > n.heapIdx) {
            if (heap[n.heapIdx].f < n.f) upHeap(n.heapIdx);
            else downHeap(n.heapIdx);
        }
        n.heapIdx = -1;
    }

    public void changeCost(Node n, float f) {
        float old = n.f;
        n.f = f;
        if (f < old) upHeap(n.heapIdx);
        else downHeap(n.heapIdx);
    }

    public int size() { return size; }

    private void upHeap(int i) {
        Node n = heap[i];
        float f = n.f;
        while (i > 0) {
            int parent = i - 1 >> 1;
            Node p = heap[parent];
            if (!(f < p.f)) break;
            heap[i] = p;
            p.heapIdx = i;
            i = parent;
        }
        heap[i] = n;
        n.heapIdx = i;
    }

    private void downHeap(int i) {
        Node n = heap[i];
        float f = n.f;
        while (true) {
            int l = 1 + (i << 1), r = l + 1;
            if (l >= size) break;
            Node ln = heap[l];
            float lf = ln.f;
            Node rn;
            float rf;
            if (r >= size) {
                rn = null;
                rf = Float.POSITIVE_INFINITY;
            } else {
                rn = heap[r];
                rf = rn.f;
            }
            if (lf < rf) {
                if (!(lf < f)) break;
                heap[i] = ln;
                ln.heapIdx = i;
                i = l;
            } else {
                if (!(rf < f)) break;
                heap[i] = rn;
                rn.heapIdx = i;
                i = r;
            }
        }
        heap[i] = n;
        n.heapIdx = i;
    }

    public boolean isEmpty() { return size == 0; }
}
