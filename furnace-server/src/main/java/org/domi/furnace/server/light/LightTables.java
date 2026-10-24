// 빛 계산에 쓰는 블록 상태 표: getLightBlock(빛 막는 정도)·useShapeForLightOcclusion·canOcclude(바닐라에서 추출) + 면 가림 모양(Shapes.getFaceShape).
package org.domi.furnace.server.light;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.domi.furnace.server.block.BlockInfo;
import org.domi.furnace.server.block.Shape;

public final class LightTables {
    private LightTables() { }

    /** 면 가림 모양: null = 비었음, FULL = 면 전체, 그 밖은 [a0,a1,b0,b1] 사각형들(그 면의 두 축 좌표 0..1). */
    private static final double[] FULL = new double[0];

    private static final byte[] LIGHT_BLOCK;
    private static final boolean[] USE_SHAPE, CAN_OCCLUDE;
    private static final double[][][] FACE_CACHE;
    private static final boolean[][] FACE_DONE;

    static {
        try (var in = LightTables.class.getClassLoader().getResourceAsStream("blocks/blocklight_1.21.1.txt")) {
            var r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String lb = r.readLine(), us = r.readLine(), co = r.readLine();
            int n = lb.length();
            LIGHT_BLOCK = new byte[n];
            USE_SHAPE = new boolean[n];
            CAN_OCCLUDE = new boolean[n];
            for (int i = 0; i < n; i++) {
                LIGHT_BLOCK[i] = (byte) Character.digit(lb.charAt(i), 16);
                USE_SHAPE[i] = us.charAt(i) == '1';
                CAN_OCCLUDE[i] = co.charAt(i) == '1';
            }
            FACE_CACHE = new double[n][][];
            FACE_DONE = new boolean[n][];
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static int lightBlock(int state) { return LIGHT_BLOCK[state]; }

    public static int lightEmission(int state) { return BlockInfo.get().lightEmission(state); }

    public static boolean useShapeForLightOcclusion(int state) { return USE_SHAPE[state]; }

    /** LightEngine.isEmptyShape: 가리지 못하거나 모양을 안 쓰는 상태는 면 모양이 비었다. */
    public static boolean isEmptyShape(int state) { return !CAN_OCCLUDE[state] || !USE_SHAPE[state]; }

    /** LightEngine.hasDifferentLightProperties(old → new). */
    public static boolean hasDifferentLightProperties(int oldState, int newState) {
        return newState != oldState && (LIGHT_BLOCK[newState] != LIGHT_BLOCK[oldState] || lightEmission(newState) != lightEmission(oldState)
                || USE_SHAPE[newState] || USE_SHAPE[oldState]);
    }

    /** LightEngine.getOcclusionShape(level, pos, state, dir): 면 모양(비었으면 null). */
    public static double[] faceShape(int state, int dir) {
        if (isEmptyShape(state)) return null;
        synchronized (FACE_CACHE) {
            boolean[] done = FACE_DONE[state];
            if (done == null) {
                done = FACE_DONE[state] = new boolean[6];
                FACE_CACHE[state] = new double[6][];
            }
            if (!done[dir]) {
                FACE_CACHE[state][dir] = computeFace(BlockInfo.get().occlusionShape(state), dir);
                done[dir] = true;
            }
            return FACE_CACHE[state][dir];
        }
    }

    private static double[] computeFace(Shape shape, int dir) {
        if (shape.isEmpty()) return null;
        if (shape.sameAs(Shape.BLOCK)) return FULL;
        int axis = dir <= 1 ? 1 : dir <= 3 ? 2 : 0;   // DOWN/UP = Y, NORTH/SOUTH = Z, WEST/EAST = X
        boolean positive = (dir & 1) == 1;
        int a1 = axis == 0 ? 1 : 0, a2 = axis == 2 ? 1 : 2;   // 나머지 두 축
        double max = Double.NEGATIVE_INFINITY, min = Double.POSITIVE_INFINITY;
        for (int i = 0; i < shape.boxCount(); i++) {
            double[] b = shape.box(i);
            max = Math.max(max, b[axis + 3]);
            min = Math.min(min, b[axis]);
        }
        if (positive ? Math.abs(max - 1.0) > 1.0E-7 : Math.abs(min) > 1.0E-7) return null;
        java.util.ArrayList<double[]> rects = new java.util.ArrayList<>();
        for (int i = 0; i < shape.boxCount(); i++) {
            double[] b = shape.box(i);
            boolean touches = positive ? Math.abs(b[axis + 3] - 1.0) <= 1.0E-7 : Math.abs(b[axis]) <= 1.0E-7;
            if (touches) rects.add(new double[] {b[a1], b[a1 + 3], b[a2], b[a2 + 3]});
        }
        if (rects.isEmpty()) return null;
        double[] flat = new double[rects.size() * 4];
        for (int i = 0; i < rects.size(); i++) System.arraycopy(rects.get(i), 0, flat, i * 4, 4);
        return covers(flat, new double[0]) ? FULL : flat;
    }

    /** 두 사각형 목록의 합이 단위 정사각형을 덮는가(좌표 압축). */
    private static boolean covers(double[] a, double[] b) {
        java.util.TreeSet<Double> xs = new java.util.TreeSet<>(), ys = new java.util.TreeSet<>();
        xs.add(0.0);
        xs.add(1.0);
        ys.add(0.0);
        ys.add(1.0);
        for (double[] r : new double[][] {a, b}) {
            for (int i = 0; i < r.length; i += 4) {
                xs.add(Math.clamp(r[i], 0.0, 1.0));
                xs.add(Math.clamp(r[i + 1], 0.0, 1.0));
                ys.add(Math.clamp(r[i + 2], 0.0, 1.0));
                ys.add(Math.clamp(r[i + 3], 0.0, 1.0));
            }
        }
        Double[] X = xs.toArray(new Double[0]), Y = ys.toArray(new Double[0]);
        for (int i = 0; i + 1 < X.length; i++) {
            for (int j = 0; j + 1 < Y.length; j++) {
                double cx = (X[i] + X[i + 1]) / 2, cy = (Y[j] + Y[j + 1]) / 2;
                if (X[i + 1] - X[i] < 1.0E-7 || Y[j + 1] - Y[j] < 1.0E-7) continue;
                if (!contains(a, cx, cy) && !contains(b, cx, cy)) return false;
            }
        }
        return true;
    }

    private static boolean contains(double[] r, double x, double y) {
        for (int i = 0; i < r.length; i += 4) if (x >= r[i] && x <= r[i + 1] && y >= r[i + 2] && y <= r[i + 3]) return true;
        return false;
    }

    /** Shapes.faceShapeOccludes(a, b) (exs.b 2인자): 하나가 면 전체면 참, 둘 다 비면 거짓, 아니면 합이 면 전체를 덮는가. */
    public static boolean faceShapesOcclude(double[] a, double[] b) {
        if (a == FULL || b == FULL) return true;
        if (a == null && b == null) return false;
        return covers(a == null ? new double[0] : a, b == null ? new double[0] : b);
    }

    /** LightEngine.shapeOccludes(from 칸 상태, to 칸 상태, from→to 방향). */
    public static boolean shapeOccludes(int fromState, int toState, int dir) {
        return faceShapesOcclude(faceShape(fromState, dir), faceShape(toState, dir ^ 1));
    }

    /** LightEngine.getLightBlockInto(from, to, dir, lightBlockInto): 둘 다 빈 모양이 아니면 면이 가릴 때 16. */
    public static int lightBlockInto(int fromState, int toState, int dir, int lightBlockInto) {
        if (isEmptyShape(fromState) && isEmptyShape(toState)) return lightBlockInto;
        return shapeOccludes(fromState, toState, dir) ? 16 : lightBlockInto;
    }
}
