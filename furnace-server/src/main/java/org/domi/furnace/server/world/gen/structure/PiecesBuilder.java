// 바닐라 StructurePiecesBuilder — 조각 누적 + 수직 이동 보정.
package org.domi.furnace.server.world.gen.structure;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.world.gen.carver.LegacyRandom;

public final class PiecesBuilder {
    private final List<StructurePiece> pieces = new ArrayList<>();

    public void addPiece(StructurePiece piece) { pieces.add(piece); }

    public List<StructurePiece> pieces() { return pieces; }

    /** 바닐라 StructurePiece.findCollisionPiece(pieces, box) — box 와 겹치는 첫 조각(없으면 null). */
    public StructurePiece findCollisionPiece(BoundingBox box) {
        for (StructurePiece p : pieces) {
            if (p.boundingBox().intersects(box)) return p;
        }
        return null;
    }

    public boolean isEmpty() { return pieces.isEmpty(); }

    public void clear() { pieces.clear(); }

    public BoundingBox boundingBox() {
        BoundingBox bb = null;
        for (StructurePiece p : pieces) bb = bb == null ? copy(p.boundingBox()) : BoundingBox.encapsulate(bb, p.boundingBox());
        return bb;
    }

    private static BoundingBox copy(BoundingBox b) {
        return new BoundingBox(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
    }

    /** offsetPiecesVertically */
    public void offsetPiecesVertically(int dy) {
        for (StructurePiece p : pieces) p.move(0, dy, 0);
    }

    /** moveBelowSeaLevel(seaLevel, minY, random, offset) — 이동량 반환. */
    public int moveBelowSeaLevel(int seaLevel, int minY, LegacyRandom random, int offset) {
        int i = seaLevel - offset;
        BoundingBox bb = boundingBox();
        int j = (bb.maxY() - bb.minY() + 1) + minY + 1;
        if (j < i) j += random.nextInt(i - j);
        int k = j - bb.maxY();
        offsetPiecesVertically(k);
        return k;
    }

    /** moveInsideHeights(random, minY, maxY) */
    public void moveInsideHeights(LegacyRandom random, int minY, int maxY) {
        BoundingBox bb = boundingBox();
        int i = maxY - minY + 1 - (bb.maxY() - bb.minY() + 1);
        int j;
        if (i > 1) j = minY + random.nextInt(i);
        else j = minY;
        int k = j - bb.minY();
        offsetPiecesVertically(k);
    }
}
