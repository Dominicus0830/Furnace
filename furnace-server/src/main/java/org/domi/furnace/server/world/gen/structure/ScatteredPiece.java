// 바닐라 ScatteredFeaturePiece — 폭/높이/깊이 상자 + 지면 높이 보정(최초 1회).
package org.domi.furnace.server.world.gen.structure;

public abstract class ScatteredPiece extends StructurePiece {
    protected final int width, height, depth;
    protected int heightPosition = -1;

    protected ScatteredPiece(int x, int y, int z, int width, int height, int depth, Direction dir) {
        super(makeBoundingBox(x, y, z, dir, width, height, depth), dir);
        this.width = width;
        this.height = height;
        this.depth = depth;
    }

    /** updateAverageGroundLevel(level, box, offset) — 쓰기 상자 안 칸들의 MOTION_BLOCKING_NO_LEAVES 평균. */
    protected boolean updateAverageGroundLevel(PostCtx c, BoundingBox box, int offset) {
        if (heightPosition >= 0) return true;
        int sum = 0, n = 0;
        for (int z = boundingBox.minZ(); z <= boundingBox.maxZ(); z++) {
            for (int x = boundingBox.minX(); x <= boundingBox.maxX(); x++) {
                if (box.isInside(x, 64, z)) {
                    sum += c.view.getMotionBlockingNoLeavesY(x, z) + 1;
                    n++;
                }
            }
        }
        if (n == 0) return false;
        heightPosition = sum / n;
        boundingBox.move(0, heightPosition - boundingBox.minY() + offset, 0);
        return true;
    }

    /** updateHeightPositionToLowestGroundHeight(level, offset) — 상자 전체의 최소 높이. */
    protected boolean updateHeightPositionToLowestGroundHeight(PostCtx c, int offset) {
        if (heightPosition >= 0) return true;
        int min = 320;
        boolean any = false;
        for (int z = boundingBox.minZ(); z <= boundingBox.maxZ(); z++) {
            for (int x = boundingBox.minX(); x <= boundingBox.maxX(); x++) {
                min = Math.min(min, c.view.getMotionBlockingNoLeavesY(x, z) + 1);
                any = true;
            }
        }
        if (!any) return false;
        heightPosition = min;
        boundingBox.move(0, heightPosition - boundingBox.minY() + offset, 0);
        return true;
    }
}
