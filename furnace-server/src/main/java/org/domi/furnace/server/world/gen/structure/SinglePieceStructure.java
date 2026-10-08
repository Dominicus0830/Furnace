// 바닐라 SinglePieceStructure — 청크 중심에 조각 1개(desert_pyramid, jungle_temple, swamp_hut …).
package org.domi.furnace.server.world.gen.structure;

import java.util.Optional;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class SinglePieceStructure extends StructureType {

    @FunctionalInterface
    public interface PieceFactory {
        StructurePiece construct(StructureEngine engine, WorldgenRandom random, int minBlockX, int minBlockZ);
    }

    private final PieceFactory factory;
    private final int width, depth;

    public SinglePieceStructure(PieceFactory factory, int width, int depth) {
        this.factory = factory;
        this.width = width;
        this.depth = depth;
    }

    @Override
    public Optional<Stub> findGenerationPoint(GenContext c) {
        if (c.lowestY(width, depth) < c.terrain.seaLevel()) return Optional.empty();
        return onTopOfChunkCenter(c, TerrainAccess.HM_WORLD_SURFACE_WG,
            b -> b.addPiece(factory.construct(c.engine, c.random, c.minBlockX(), c.minBlockZ())));
    }
}
