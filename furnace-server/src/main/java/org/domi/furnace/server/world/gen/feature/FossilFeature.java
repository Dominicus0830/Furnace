// fossil — 바닐라 FossilFeature 포팅(땅속 화석 뼈 + 석탄/다이아몬드 덧입힘 템플릿).
package org.domi.furnace.server.world.gen.feature;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.domi.furnace.server.world.BlockAttrs;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.block.BlockShapes;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.structure.template.BlockStateTable;
import org.domi.furnace.server.world.gen.structure.template.PlaceSettings;
import org.domi.furnace.server.world.gen.structure.template.Processor;
import org.domi.furnace.server.world.gen.structure.template.ProcessorLists;
import org.domi.furnace.server.world.gen.structure.template.StructureTemplate;
import org.domi.furnace.server.world.gen.structure.template.TemplatePlacer;

public final class FossilFeature implements ConfiguredFeature {

    private static final Map<String, StructureTemplate> TEMPLATES = new ConcurrentHashMap<>();
    private static volatile ProcessorLists processorLists;

    private final BlockRegistry registry;
    private final List<String> fossils, overlays;
    private final String fossilProcessors, overlayProcessors;
    private final int maxEmptyCorners;

    public FossilFeature(BlockRegistry registry, List<String> fossils, List<String> overlays, String fossilProcessors,
                         String overlayProcessors, int maxEmptyCorners) {
        this.registry = registry;
        this.fossils = fossils;
        this.overlays = overlays;
        this.fossilProcessors = fossilProcessors;
        this.overlayProcessors = overlayProcessors;
        this.maxEmptyCorners = maxEmptyCorners;
    }

    private StructureTemplate template(String id) {
        return TEMPLATES.computeIfAbsent(id, k -> StructureTemplate.load(registry, k));
    }

    private List<Processor> processors(String id) {
        ProcessorLists pl = processorLists;
        if (pl == null) processorLists = pl = new ProcessorLists(registry);
        return pl.get(id);
    }

    @Override
    public boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        int rotation = random.nextInt(4);   // Rotation.getRandom
        int i = random.nextInt(fossils.size());
        StructureTemplate fossil = template(fossils.get(i));
        StructureTemplate overlay = template(overlays.get(i));
        int cx = Math.floorDiv(x, 16), cz = Math.floorDiv(z, 16);
        PlaceSettings st = new PlaceSettings();
        st.rotation = rotation;
        st.boundingBox = new int[]{cx * 16 - 16, view.minY(), cz * 16 - 16, cx * 16 + 15 + 16, (view.maxY() + 1), cz * 16 + 15 + 16};
        st.random = random;
        int sx = fossil.sizeX(rotation), sz = fossil.sizeZ(rotation);
        int px = x - sx / 2, pz = z - sz / 2;
        int minH = y;
        for (int dx = 0; dx < sx; dx++)
            for (int dz = 0; dz < sz; dz++)
                minH = Math.min(minH, view.getWgOceanFloorHeight(px + dx, pz + dz));
        int fy = Math.max(minH - 15 - random.nextInt(10), view.minY() + 10);
        int[] pos = StructureTemplate.zeroPositionWithTransform(px, fy, pz, BlockStateTable.NONE, rotation, fossil.sizeX, fossil.sizeZ);
        int[] b = fossil.boundingBox(pos[0], pos[1], pos[2], rotation, 0, 0, BlockStateTable.NONE);
        if (countEmptyCorners(view, b) > maxEmptyCorners) return false;
        BlockStateTable table = BlockStateTable.of(registry);
        BlockShapes shapes = BlockShapes.of(registry);
        Processor.Ctx ctx = new Processor.Ctx(view, table);
        st.processors.clear();
        st.processors.addAll(processors(fossilProcessors));
        TemplatePlacer.placeInWorld(fossil, view, shapes, table, ctx, pos[0], pos[1], pos[2], pos[0], pos[1], pos[2], st, random, 4);
        st.processors.clear();
        st.processors.addAll(processors(overlayProcessors));
        TemplatePlacer.placeInWorld(overlay, view, shapes, table, ctx, pos[0], pos[1], pos[2], pos[0], pos[1], pos[2], st, random, 4);
        return true;
    }

    private int countEmptyCorners(FeatureWorldView view, int[] b) {
        BlockAttrs a = BlockAttrs.get();
        BlockStateTable t = BlockStateTable.of(registry);
        int n = 0;
        int[] xs = {b[0], b[3]}, ys = {b[1], b[4]}, zs = {b[2], b[5]};
        for (int xx : xs) for (int yy : ys) for (int zz : zs) {
            int s = view.getBlock(xx, yy, zz);
            String name = t.name(s);
            if (a.isAir(s) || name.equals("minecraft:lava") || name.equals("minecraft:water")) n++;
        }
        return n;
    }
}
