// 바닐라 StructureTemplate.updateShapeAtEdge + Block.updateFromNeighbourShapes.
package org.domi.furnace.server.world.block;

import org.domi.furnace.server.world.gen.feature.FeatureWorldView;

public final class ShapeUpdater {

    private ShapeUpdater() {}

    /** shape 안(filled) 여부 질의. 좌표는 월드 좌표. */
    public interface Shape { boolean filled(int x, int y, int z); }

    /**
     * DiscreteVoxelShape.forAllFaces 순서(Z축 선 → Y축 선 → X축 선, 각 선의 첫 칸은 음의 방향 면·마지막 칸은 양의 방향 면)로
     * 가장자리 면마다 양쪽 블록의 updateShape를 호출한다. 박스 크기 (sx,sy,sz), 최솟값 (minX,minY,minZ).
     */
    public static void updateShapeAtEdge(FeatureWorldView view, BlockShapes shapes, int minX, int minY, int minZ, int sx, int sy, int sz, Shape shape) {
        for (int pass = 0; pass < 3; pass++) {
            int sl = pass == 0 ? sx : pass == 1 ? sz : sy;
            int sm = pass == 0 ? sy : pass == 1 ? sx : sz;
            int sn = pass == 0 ? sz : pass == 1 ? sy : sx;
            int neg = pass == 0 ? BlockShapes.NORTH : pass == 1 ? BlockShapes.DOWN : BlockShapes.WEST;
            int pos = pass == 0 ? BlockShapes.SOUTH : pass == 1 ? BlockShapes.UP : BlockShapes.EAST;
            for (int l = 0; l < sl; l++) {
                for (int m = 0; m < sm; m++) {
                    boolean last = false;
                    for (int n = 0; n <= sn; n++) {
                        boolean present = n != sn && cellFilled(shape, minX, minY, minZ, pass, l, m, n);
                        if (!last && present) edgeFace(view, shapes, minX, minY, minZ, pass, l, m, n, neg);
                        if (last && !present) edgeFace(view, shapes, minX, minY, minZ, pass, l, m, n - 1, pos);
                        last = present;
                    }
                }
            }
        }
    }

    private static boolean cellFilled(Shape shape, int minX, int minY, int minZ, int pass, int l, int m, int n) {
        return switch (pass) {
            case 0 -> shape.filled(minX + l, minY + m, minZ + n);
            case 1 -> shape.filled(minX + m, minY + n, minZ + l);
            default -> shape.filled(minX + n, minY + l, minZ + m);
        };
    }

    private static void edgeFace(FeatureWorldView view, BlockShapes shapes, int minX, int minY, int minZ, int pass, int l, int m, int n, int dir) {
        int x, y, z;
        switch (pass) {
            case 0 -> { x = minX + l; y = minY + m; z = minZ + n; }
            case 1 -> { x = minX + m; y = minY + n; z = minZ + l; }
            default -> { x = minX + n; y = minY + l; z = minZ + m; }
        }
        int nx = x + BlockShapes.DX[dir];
        int ny = y + BlockShapes.DY[dir];
        int nz = z + BlockShapes.DZ[dir];
        int state = view.getBlock(x, y, z);
        int neighbor = view.getBlock(nx, ny, nz);
        int newState = shapes.updateShape(view, state, dir, neighbor, x, y, z);
        if (newState != state) view.trySetBlock(x, y, z, newState);
        int newNeighbor = shapes.updateShape(view, neighbor, BlockShapes.opposite(dir), newState, nx, ny, nz);
        if (newNeighbor != neighbor) view.trySetBlock(nx, ny, nz, newNeighbor);
    }

    /** 바닐라 Block.updateFromNeighbourShapes — 순서 WEST, EAST, NORTH, SOUTH, DOWN, UP. */
    public static int updateFromNeighbourShapes(ShapeLevel level, BlockShapes shapes, int state, int x, int y, int z) {
        final int[] order = {BlockShapes.WEST, BlockShapes.EAST, BlockShapes.NORTH, BlockShapes.SOUTH, BlockShapes.DOWN, BlockShapes.UP};
        int s = state;
        for (int d : order) {
            int nb = level.getBlock(x + BlockShapes.DX[d], y + BlockShapes.DY[d], z + BlockShapes.DZ[d]);
            s = shapes.updateShape(level, s, d, nb, x, y, z);
        }
        return s;
    }
}
