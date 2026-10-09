// 구조물 엔진 — structure_set 배치 판정, 가중치 선택, StructureStart 생성, 템플릿/프로세서/블록 상태 도구 보관.
package org.domi.furnace.server.world.gen.structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.block.BlockShapes;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.structure.template.BlockStateTable;
import org.domi.furnace.server.world.gen.structure.template.ProcessorLists;
import org.domi.furnace.server.world.gen.structure.template.StructureTemplate;

public final class StructureEngine {

    private final BlockRegistry blocks;
    private final BlockStateTable table;
    private final BlockShapes shapes;
    private final ProcessorLists processorLists;
    private final StructureRegistry registry;
    private final long seed;
    private final Set<String> possibleBiomes;
    private final Map<String, StructureTemplate> templates = new ConcurrentHashMap<>();
    private final Set<String> warned = ConcurrentHashMap.newKeySet();
    private final Map<String, StructureType> types = new ConcurrentHashMap<>();
    private final Map<String, Function<StructureRegistry.Def, StructureType>> factories = new ConcurrentHashMap<>();

    public StructureEngine(BlockRegistry blocks, long seed, Set<String> possibleBiomes) {
        this.blocks = blocks;
        this.table = BlockStateTable.of(blocks);
        this.shapes = BlockShapes.of(blocks);
        this.processorLists = new ProcessorLists(blocks);
        this.registry = new StructureRegistry();
        this.seed = seed;
        this.possibleBiomes = possibleBiomes;
        StructureTypes.registerAll(this);
    }

    public BlockRegistry blocks() { return blocks; }

    public BlockStateTable table() { return table; }

    public BlockShapes shapes() { return shapes; }

    public ProcessorLists processorLists() { return processorLists; }

    public StructureRegistry registry() { return registry; }

    public long seed() { return seed; }

    /** FluidState.is(FluidTags.WATER) — 물 블록, 물을 가진 식물, waterlogged 블록. */
    public boolean hasWaterFluid(int state) {
        String n = table.name(state);
        if (n.equals("minecraft:water") || n.equals("minecraft:bubble_column") || n.equals("minecraft:kelp") || n.equals("minecraft:kelp_plant")
            || n.equals("minecraft:seagrass") || n.equals("minecraft:tall_seagrass")) return true;
        return "true".equals(table.prop(state, "waterlogged"));
    }

    private final Map<String, Set<String>> tagSets = new ConcurrentHashMap<>();

    /** 블록 태그("minecraft:xxx") → 블록 이름 집합. */
    public Set<String> blockTagSet(String tag) {
        return tagSets.computeIfAbsent(tag, t -> new java.util.HashSet<>(org.domi.furnace.server.world.gen.feature.BlockTags.get().blocksOf(t)));
    }

    private final Map<String, Integer> stateCache = new ConcurrentHashMap<>();

    /** "minecraft:name[prop=value,...]" → 기본 상태에 지정한 속성만 바꾼 상태 id. */
    public int state(String spec) {
        return stateCache.computeIfAbsent(spec, sp -> {
            String name = sp;
            int br = sp.indexOf('[');
            Map<String, String> props = new java.util.LinkedHashMap<>();
            if (br >= 0) {
                name = sp.substring(0, br);
                String body = sp.substring(br + 1, sp.lastIndexOf(']'));
                if (!body.isBlank()) for (String kv : body.split(",")) {
                    int eq = kv.indexOf('=');
                    props.put(kv.substring(0, eq).strip(), kv.substring(eq + 1).strip());
                }
            }
            if (!name.contains(":")) name = "minecraft:" + name;
            int st = blocks.defaultStateId(name);
            for (Map.Entry<String, String> e : props.entrySet()) {
                if (!table.has(st, e.getKey())) throw new IllegalArgumentException("속성 없음: " + sp);
                st = table.with(st, e.getKey(), e.getValue());
            }
            return st;
        });
    }

    public StructureTemplate template(String id) {
        return templates.computeIfAbsent(id, k -> StructureTemplate.load(blocks, k));
    }

    public void registerType(String typeName, Function<StructureRegistry.Def, StructureType> factory) {
        factories.put(typeName, factory);
    }

    public StructureType typeOf(StructureRegistry.Def def) {
        return types.computeIfAbsent(def.id(), k -> {
            Function<StructureRegistry.Def, StructureType> f = factories.get(def.type());
            if (f == null) return null;
            return f.apply(def);
        });
    }

    // ------------------------------------------------------------------ 배치 판정

    /** 이 엔진의 월드(바이옴 소스)가 만들 수 있는 세트인가 — ChunkGeneratorStructureState.hasBiomesForStructureSet. */
    private boolean possibleSet(StructureRegistry.SetDef set) {
        for (StructureRegistry.Entry e : set.entries()) {
            StructureRegistry.Def d = registry.structure(e.structureId());
            for (String b : d.biomes()) if (possibleBiomes.contains(b)) return true;
        }
        return false;
    }

    /** StructurePlacement.isStructureChunk(state, x, z) */
    public boolean isStructureChunk(StructureRegistry.SetDef set, int cx, int cz) {
        if (set.spread() != null) {
            if (!set.spread().isStructureChunk(seed, cx, cz)) return false;
        } else {
            if (!ringsContain(set, cx, cz)) return false;
        }
        StructureRegistry.ExclusionZone ez = set.exclusion();
        if (ez != null) {
            StructureRegistry.SetDef other = registry.sets().get(ez.otherSet());
            int r = ez.chunkCount();
            for (int i = cx - r; i <= cx + r; i++) {
                for (int j = cz - r; j <= cz + r; j++) {
                    if (isStructureChunk(other, i, j)) return false;
                }
            }
        }
        return true;
    }

    /** concentric_rings 배치(strongholds) — StrongholdPlacement 모듈이 채운다. */
    private boolean ringsContain(StructureRegistry.SetDef set, int cx, int cz) {
        return strongholdProvider != null && strongholdProvider.contains(set, cx, cz);
    }

    public interface RingProvider { boolean contains(StructureRegistry.SetDef set, int cx, int cz); }

    private volatile RingProvider strongholdProvider;

    public void setRingProvider(RingProvider p) { this.strongholdProvider = p; }

    // ------------------------------------------------------------------ 시작점 생성

    /** 이 청크가 시작점인 모든 구조물의 StructureStart (ChunkGenerator.createStructures). */
    public List<StructureStart> createStarts(int cx, int cz, TerrainAccess terrain) {
        List<StructureStart> out = new ArrayList<>();
        for (StructureRegistry.SetDef set : registry.sets().values()) {
            if (!possibleSet(set)) continue;
            if (!isStructureChunk(set, cx, cz)) continue;
            List<StructureRegistry.Entry> list = set.entries();
            if (list.size() == 1) {
                tryGenerate(list.get(0), cx, cz, terrain, out);
                continue;
            }
            ArrayList<StructureRegistry.Entry> pool = new ArrayList<>(list);
            WorldgenRandom r = new WorldgenRandom();
            r.setLargeFeatureSeed(seed, cx, cz);
            int total = 0;
            for (StructureRegistry.Entry e : pool) total += e.weight();
            while (!pool.isEmpty()) {
                int v = r.nextInt(total);
                int idx = 0;
                for (StructureRegistry.Entry e : pool) {
                    v -= e.weight();
                    if (v < 0) break;
                    idx++;
                }
                StructureRegistry.Entry chosen = pool.get(idx);
                if (tryGenerate(chosen, cx, cz, terrain, out)) break;
                pool.remove(idx);
                total -= chosen.weight();
            }
        }
        return out;
    }

    private boolean tryGenerate(StructureRegistry.Entry entry, int cx, int cz, TerrainAccess terrain, List<StructureStart> out) {
        StructureRegistry.Def def = registry.structure(entry.structureId());
        StructureType type = typeOf(def);
        if (type == null) return false;   // 미구현 구조물 — 구현될 때까지 생성하지 않는다
        GenContext ctx = new GenContext(this, def, seed, cx, cz, terrain);
        Optional<StructureType.Stub> stub = type.findGenerationPoint(ctx);
        if (stub.isEmpty()) return false;
        StructureType.Stub s = stub.get();
        String biome = terrain.biomeAt(s.x(), s.y(), s.z());
        if (!def.biomes().contains(biome)) return false;
        PiecesBuilder b = new PiecesBuilder();
        try {
            s.generator().accept(b);
        } catch (StructureTemplate.MissingTemplateException e) {
            if (warned.add(def.id())) System.err.println("[structures] " + def.id() + " 건너뜀: " + e.getMessage());
            return false;
        }
        if (b.isEmpty()) return false;
        out.add(new StructureStart(def.id(), cx, cz, b.pieces(), def.terrainAdaptation(), type));
        return true;
    }
}
