// 구조물 조각 postProcess에 넘기는 문맥 — 월드 뷰, 난수, 쓰기 상자, 피벗, 변환 도구.
package org.domi.furnace.server.world.gen.structure;

import org.domi.furnace.server.world.gen.noise.NoiseRandom;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.block.BlockShapes;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;
import org.domi.furnace.server.world.gen.structure.template.BlockStateTable;
import org.domi.furnace.server.world.gen.structure.template.Processor;

public final class PostCtx {
    public final StructureEngine engine;
    public final FeatureWorldView view;
    public final WorldgenRandom random;
    public final NoiseRandom regionRandom;
    public final BoundingBox writableBox;
    public final int chunkX, chunkZ;
    /** StructureStart.placeInChunk가 만든 피벗: (첫 조각 상자 중심 x, minY, 중심 z). */
    public final int pivotX, pivotY, pivotZ;

    public PostCtx(StructureEngine engine, FeatureWorldView view, WorldgenRandom random, NoiseRandom regionRandom,
                   BoundingBox writableBox, int chunkX, int chunkZ, int pivotX, int pivotY, int pivotZ) {
        this.engine = engine;
        this.view = view;
        this.random = random;
        this.regionRandom = regionRandom;
        this.writableBox = writableBox;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.pivotX = pivotX;
        this.pivotY = pivotY;
        this.pivotZ = pivotZ;
    }

    public BlockRegistry blocks() { return engine.blocks(); }

    public BlockStateTable table() { return engine.table(); }

    public BlockShapes shapes() { return engine.shapes(); }

    public Processor.Ctx processorCtx() { return new Processor.Ctx(view, engine.table()); }
}
