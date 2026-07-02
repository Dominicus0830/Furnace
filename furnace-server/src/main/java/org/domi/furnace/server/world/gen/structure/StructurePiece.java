// 구조물 조각 추상 베이스 — BoundingBox·회전만 보유. 실제 블록배치(postProcess)는 S2b.
package org.domi.furnace.server.world.gen.structure;

public abstract class StructurePiece {

    protected BoundingBox boundingBox;
    protected final Direction orientation;

    protected StructurePiece(BoundingBox boundingBox, Direction orientation) {
        this.boundingBox = boundingBox;
        this.orientation = orientation;
    }

    public BoundingBox boundingBox() {
        return boundingBox;
    }

    public Direction orientation() {
        return orientation;
    }

    /**
     * 바닐라 StructurePiece.makeBoundingBox — axis(X/Z)에 따라 offsetX/offsetZ 를 스왑한다.
     * width==depth 인 조각(예: 사막 피라미드)에선 수치상 영향 없음.
     */
    protected static BoundingBox makeBoundingBox(int x, int y, int z, Direction direction, int offsetX, int offsetY, int offsetZ) {
        return direction.isXAxis()
            ? new BoundingBox(x, y, z, x + offsetZ - 1, y + offsetY - 1, z + offsetX - 1)
            : new BoundingBox(x, y, z, x + offsetX - 1, y + offsetY - 1, z + offsetZ - 1);
    }

    /** 블록 배치 — S2b 구현 예정. */
    public void postProcess() {
        throw new UnsupportedOperationException("StructurePiece.postProcess() — S2b 미구현");
    }
}
