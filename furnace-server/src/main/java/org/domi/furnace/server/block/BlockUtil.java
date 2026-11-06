// 바닐라 BlockUtil.getLargestRectangleAround: 한 칸에서 두 축으로 조건을 만족하는 가장 큰 직사각형(지옥문 사각형 찾기).
package org.domi.furnace.server.block;

import java.util.function.Predicate;

public final class BlockUtil {
    private BlockUtil() {}

    /** FoundRectangle: 최소 모서리와 두 축 길이. */
    public record FoundRectangle(Pos minCorner, int axis1Size, int axis2Size) {}

    private record IntBounds(int min, int max) {}

    private static int negative(int axis) { return axis == Dir.AXIS_X ? Dir.WEST : axis == Dir.AXIS_Y ? Dir.DOWN : Dir.NORTH; }

    private static Pos relative(Pos p, int axis, int n) {
        return switch (axis) {
            case Dir.AXIS_X -> new Pos(p.x() + n, p.y(), p.z());
            case Dir.AXIS_Y -> new Pos(p.x(), p.y() + n, p.z());
            default -> new Pos(p.x(), p.y(), p.z() + n);
        };
    }

    /** getLimit: 시작에서 dir 로 한 칸씩 가며 조건이 맞는 동안 센다(최대 max). */
    private static int limit(Predicate<Pos> pred, Pos start, int dir, int max) {
        int i = 0;
        Pos m = start;
        while (i < max) {
            m = m.relative(dir);
            if (!pred.test(m)) break;
            i++;
        }
        return i;
    }

    public static FoundRectangle getLargestRectangleAround(Pos center, int axis1, int max1, int axis2, int max2, Predicate<Pos> pred) {
        int d1 = negative(axis1), d1p = Dir.opposite(d1);
        int d2 = negative(axis2), d2p = Dir.opposite(d2);
        int neg = limit(pred, center, d1, max1);
        int pos = limit(pred, center, d1p, max1);
        int mid = neg;
        IntBounds[] cols = new IntBounds[neg + 1 + pos];
        cols[neg] = new IntBounds(limit(pred, center, d2, max2), limit(pred, center, d2p, max2));
        int base = cols[neg].min;
        for (int k = 1; k <= neg; k++) {
            IntBounds prev = cols[mid - (k - 1)];
            Pos c = center.relative(d1, k);
            cols[mid - k] = new IntBounds(limit(pred, c, d2, prev.min), limit(pred, c, d2p, prev.max));
        }
        for (int k = 1; k <= pos; k++) {
            IntBounds prev = cols[mid + k - 1];
            Pos c = center.relative(d1p, k);
            cols[mid + k] = new IntBounds(limit(pred, c, d2, prev.min), limit(pred, c, d2p, prev.max));
        }
        int bestStart = 0, bestRow = 0, bestW = 0, bestH = 0;
        int[] heights = new int[cols.length];
        for (int row = base; row >= 0; row--) {
            for (int i = 0; i < cols.length; i++) {
                IntBounds b = cols[i];
                int lo = base - b.min, hi = base + b.max;
                heights[i] = row >= lo && row <= hi ? hi + 1 - row : 0;
            }
            int[] r = getMaxRectangleLocation(heights);
            int w = 1 + r[1] - r[0];
            int h = r[2];
            if (w * h > bestW * bestH) {
                bestStart = r[0];
                bestRow = row;
                bestW = w;
                bestH = h;
            }
        }
        return new FoundRectangle(relative(relative(center, axis1, bestStart - mid), axis2, bestRow - base), bestW, bestH);
    }

    /** getMaxRectangleLocation: 막대그래프 최대 직사각형 {시작, 끝, 높이}(스택, 바닐라와 같은 동점 처리). */
    static int[] getMaxRectangleLocation(int[] heights) {
        int start = 0, end = 0, height = 0;
        int[] stack = new int[heights.length + 1];
        int sp = 0;
        stack[sp++] = 0;
        for (int i = 1; i <= heights.length; i++) {
            int h = i == heights.length ? 0 : heights[i];
            while (sp > 0) {
                int top = heights[stack[sp - 1]];
                if (h >= top) {
                    stack[sp++] = i;
                    break;
                }
                sp--;
                int left = sp == 0 ? 0 : stack[sp - 1] + 1;
                if (top * (i - left) > height * (end - start)) {
                    end = i;
                    start = left;
                    height = top;
                }
            }
            if (sp == 0) stack[sp++] = i;
        }
        return new int[] {start, end - 1, height};
    }
}
