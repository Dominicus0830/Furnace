// 청크에서 확정된 구조물 시작점 — 구조물 id·시작 청크·피스 목록·전체 BoundingBox(피스 합집합).
package org.domi.furnace.server.world.gen.structure;

import java.util.List;

public final class StructureStart {

    private final String structureId;
    private final int chunkX;
    private final int chunkZ;
    private final List<StructurePiece> pieces;
    private final BoundingBox boundingBox;

    public StructureStart(String structureId, int chunkX, int chunkZ, List<StructurePiece> pieces) {
        if (pieces.isEmpty()) {
            throw new IllegalArgumentException("StructureStart requires at least one piece");
        }
        this.structureId = structureId;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.pieces = List.copyOf(pieces);
        BoundingBox bb = this.pieces.get(0).boundingBox();
        for (int i = 1; i < this.pieces.size(); i++) {
            bb = BoundingBox.encapsulate(bb, this.pieces.get(i).boundingBox());
        }
        this.boundingBox = bb;
    }

    public String structureId() { return structureId; }
    public int chunkX() { return chunkX; }
    public int chunkZ() { return chunkZ; }
    public List<StructurePiece> pieces() { return pieces; }
    public BoundingBox boundingBox() { return boundingBox; }
}
