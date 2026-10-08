// 청크에서 확정된 구조물 시작점 — 구조물 id·시작 청크·피스 목록·전체 BoundingBox(피스 합집합, terrain_adaptation이면 12 확장).
package org.domi.furnace.server.world.gen.structure;

import java.util.List;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.noise.XoroshiroRandomSource;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;

public final class StructureStart {

    private final String structureId;
    private final int chunkX;
    private final int chunkZ;
    private final List<StructurePiece> pieces;
    private final BoundingBox rawBox;
    private final BoundingBox boundingBox;
    private final String terrainAdaptation;
    private final StructureType type;

    public StructureStart(String structureId, int chunkX, int chunkZ, List<StructurePiece> pieces) {
        this(structureId, chunkX, chunkZ, pieces, "none", null);
    }

    public StructureStart(String structureId, int chunkX, int chunkZ, List<StructurePiece> pieces, String terrainAdaptation, StructureType type) {
        if (pieces.isEmpty()) {
            throw new IllegalArgumentException("StructureStart requires at least one piece");
        }
        this.structureId = structureId;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.pieces = List.copyOf(pieces);
        BoundingBox bb = this.pieces.get(0).boundingBox();
        BoundingBox raw = new BoundingBox(bb.minX(), bb.minY(), bb.minZ(), bb.maxX(), bb.maxY(), bb.maxZ());
        for (int i = 1; i < this.pieces.size(); i++) {
            raw = BoundingBox.encapsulate(raw, this.pieces.get(i).boundingBox());
        }
        this.rawBox = raw;
        this.terrainAdaptation = terrainAdaptation;
        this.type = type;
        // Structure.adjustBoundingBox: terrain_adaptation != none → inflate(12)
        this.boundingBox = "none".equals(terrainAdaptation) ? raw
            : new BoundingBox(raw.minX() - 12, raw.minY() - 12, raw.minZ() - 12, raw.maxX() + 12, raw.maxY() + 12, raw.maxZ() + 12);
    }

    public String structureId() { return structureId; }
    public int chunkX() { return chunkX; }
    public int chunkZ() { return chunkZ; }
    public List<StructurePiece> pieces() { return pieces; }
    /** adjustBoundingBox 적용 후(구조물 참조 판정용). */
    public BoundingBox boundingBox() { return boundingBox; }
    /** 조각 합집합 원본 상자(지형 보정 Beardifier 등). */
    public BoundingBox rawBox() { return rawBox; }
    public String terrainAdaptation() { return terrainAdaptation; }

    /** 바닐라 StructureStart.placeInChunk. */
    public void placeInChunk(StructureEngine engine, FeatureWorldView view, WorldgenRandom random, XoroshiroRandomSource regionRandom,
                             BoundingBox writableBox, int cx, int cz) {
        if (pieces.isEmpty()) return;
        BoundingBox first = pieces.get(0).boundingBox();
        int pivotX = first.minX() + (first.maxX() - first.minX() + 1) / 2;
        int pivotZ = first.minZ() + (first.maxZ() - first.minZ() + 1) / 2;
        PostCtx ctx = new PostCtx(engine, view, random, regionRandom, writableBox, cx, cz, pivotX, first.minY(), pivotZ);
        for (StructurePiece p : pieces) {
            if (p.boundingBox().intersects(writableBox)) p.postProcess(ctx);
        }
        if (type != null) type.afterPlace(ctx, this);
    }
}
