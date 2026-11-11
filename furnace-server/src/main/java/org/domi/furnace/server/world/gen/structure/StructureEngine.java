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

    // FeatureWorldView 가 돌려주는 biome 정수 id → 이름(레지스트리 순서). 구조물 조각의 biome 태그 검사(광산 mineshaft_blocking)에 쓴다.
    private volatile String[] biomeRegistryNames = new String[0];

    public void setBiomeRegistryNames(String[] names) { this.biomeRegistryNames = names; }

    public String biomeName(int id) {
        String[] n = biomeRegistryNames;
        return id >= 0 && id < n.length ? n[id] : "";
    }

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

    private final JigsawPools pools = new JigsawPools(this);
    private volatile java.util.function.Function<String, org.domi.furnace.server.world.gen.feature.PlacedFeature> placedFeatures = id -> null;

    public JigsawPools pools() { return pools; }

    public void setPlacedFeatureSource(java.util.function.Function<String, org.domi.furnace.server.world.gen.feature.PlacedFeature> f) { placedFeatures = f; }

    public org.domi.furnace.server.world.gen.feature.PlacedFeature placedFeature(String id) { return placedFeatures.apply(id); }

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

    /**
     * 이 생성기가 쓰는 구조물 세트 id(null = 레지스트리 전부). 평지(FlatLevelSource.createState)는 structure_overrides 만 쓴다 —
     * 빈 집합이면 구조물이 없다.
     */
    private volatile Set<String> allowedSets;

    public void setAllowedSets(Set<String> ids) { this.allowedSets = ids == null ? null : Set.copyOf(ids); }

    /** 이 엔진의 월드(바이옴 소스)가 만들 수 있는 세트인가 — ChunkGeneratorStructureState.hasBiomesForStructureSet. */
    private boolean possibleSet(StructureRegistry.SetDef set) {
        Set<String> allowed = allowedSets;
        if (allowed != null && !allowed.contains(set.id())) return false;
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

    /** concentric_rings 배치(strongholds): ChunkGeneratorStructureState.getRingPositionsFor 결과에 들어 있는가. */
    private boolean ringsContain(StructureRegistry.SetDef set, int cx, int cz) {
        long[] ring = ringPositions(set);
        long key = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
        for (long k : ring) if (k == key) return true;
        return false;
    }

    private volatile TerrainAccess ringTerrain;
    private final Map<String, long[]> ringCache = new ConcurrentHashMap<>();

    /** 링 위치 계산에 쓸 바이옴 소스(월드 생성기). */
    public void setRingTerrain(TerrainAccess t) { this.ringTerrain = t; }

    /** ChunkGeneratorStructureState.generateRingPositions — 시드로 각도·거리, findBiomeHorizontal 로 선호 바이옴 쪽 보정. */
    public long[] ringPositions(StructureRegistry.SetDef set) {
        return ringCache.computeIfAbsent(set.id(), k -> computeRings(set.rings()));
    }

    private long[] computeRings(StructureRegistry.ConcentricRings p) {
        TerrainAccess terrain = ringTerrain;
        if (p.count() == 0 || terrain == null) return new long[0];
        int distance = p.distance(), count = p.count(), spread = p.spread();
        org.domi.furnace.server.world.gen.carver.LegacyRandom random = new org.domi.furnace.server.world.gen.carver.LegacyRandom(seed);
        double angle = random.nextDouble() * Math.PI * 2.0;
        int inRing = 0, ring = 0;
        long[] out = new long[count];
        int[] rxs = new int[count], rzs = new int[count];
        long[] forks = new long[count];
        for (int n = 0; n < count; n++) {
            double dist = (double) (4 * distance + distance * ring * 6) + (random.nextDouble() - 0.5) * (double) distance * 2.5;
            rxs[n] = (int) Math.round(Math.cos(angle) * dist);
            rzs[n] = (int) Math.round(Math.sin(angle) * dist);
            forks[n] = random.nextLong();   // randomsource.fork()
            angle += (Math.PI * 2.0) / (double) spread;
            inRing++;
            if (inRing == spread) {
                ring++;
                inRing = 0;
                spread += 2 * spread / (ring + 1);
                spread = Math.min(spread, count - n);
                angle += random.nextDouble() * Math.PI * 2.0;
            }
        }
        // 바이옴 탐색은 위치마다 독립(바닐라도 비동기 병렬) — 병렬로 계산
        java.util.stream.IntStream.range(0, count).parallel().forEach(n -> {
            org.domi.furnace.server.world.gen.carver.LegacyRandom fork = new org.domi.furnace.server.world.gen.carver.LegacyRandom(forks[n]);
            int[] found = findBiomeHorizontal(terrain, rxs[n] * 16 + 8, 0, rzs[n] * 16 + 8, 112, p.preferredBiomes(), fork);
            int fcx = found != null ? Math.floorDiv(found[0], 16) : rxs[n];
            int fcz = found != null ? Math.floorDiv(found[1], 16) : rzs[n];
            out[n] = ((long) fcx << 32) ^ (fcz & 0xFFFFFFFFL);
        });
        return out;
    }

    /** BiomeSource.findBiomeHorizontal(x, y, z, radius, 1, predicate, random, findClosest=false) → {blockX, blockZ} 또는 null. */
    private static int[] findBiomeHorizontal(TerrainAccess t, int x, int y, int z, int radius, Set<String> biomes,
                                             org.domi.furnace.server.world.gen.carver.LegacyRandom random) {
        String fixed = t.fixedBiome();
        if (fixed != null) {
            // FixedBiomeSource.findBiomeHorizontal(findClosest=false): 맞으면 반경 안 무작위 블록(x 먼저, 그다음 z)
            if (!biomes.contains(fixed)) return null;
            int bx = x - radius + random.nextInt(radius * 2 + 1);
            int bz = z - radius + random.nextInt(radius * 2 + 1);
            return new int[]{bx, bz};
        }
        int qx = x >> 2, qz = z >> 2, qr = radius >> 2, qy = y >> 2;
        int[] result = null;
        int found = 0;
        for (int dz = -qr; dz <= qr; dz++) {
            for (int dx = -qr; dx <= qr; dx++) {
                int sx = qx + dx, sz = qz + dz;
                if (biomes.contains(t.biomeAt(sx << 2, qy << 2, sz << 2))) {
                    if (result == null || random.nextInt(found + 1) == 0) result = new int[]{sx << 2, sz << 2};
                    found++;
                }
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ 시작점 생성

    // generate-structures=false 에 해당하는 스위치 — 구조물 없이 만든 순정 fixture 와 대조하는 테스트가 끈다.
    private volatile boolean structuresEnabled = true;

    public void setStructuresEnabled(boolean enabled) { this.structuresEnabled = enabled; }

    /** 이 청크가 시작점인 모든 구조물의 StructureStart (ChunkGenerator.createStructures). */
    public List<StructureStart> createStarts(int cx, int cz, TerrainAccess terrain) {
        List<StructureStart> out = new ArrayList<>();
        if (!structuresEnabled) return out;
        for (StructureRegistry.SetDef set : registry.sets().values()) {
            if (!possibleSet(set)) continue;
            if (!isStructureChunk(set, cx, cz)) continue;
            startsForSet(set, cx, cz, terrain, out);
        }
        return out;
    }

    /** createStructures 의 세트 하나 부분 — 가중치 순서로 하나를 고른다(세트마다 난수가 따로라 세트별로 떼어 계산해도 같다). */
    private void startsForSet(StructureRegistry.SetDef set, int cx, int cz, TerrainAccess terrain, List<StructureStart> out) {
        {
            List<StructureRegistry.Entry> list = set.entries();
            if (list.size() == 1) {
                tryGenerate(list.get(0), cx, cz, terrain, out);
                return;
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
    }

    /** 이 엔진의 월드가 이 구조물을 만들 수 있는 배치들(ChunkGeneratorStructureState.getPlacementsForStructure, 세트 id 순). */
    public List<StructureRegistry.SetDef> placementsFor(String structureId) {
        List<StructureRegistry.SetDef> out = new ArrayList<>();
        for (StructureRegistry.SetDef set : registry.sets().values()) {
            if (!possibleSet(set)) continue;
            for (StructureRegistry.Entry e : set.entries()) if (e.structureId().equals(structureId)) { out.add(set); break; }
        }
        return out;
    }

    private final Map<String, String> setStartCache = new ConcurrentHashMap<>();

    /**
     * 그 청크에서 이 세트가 실제로 만드는 구조물 id(없으면 null) — 배치 판정 + 생성과 같은 선택(StructureCheck + STRUCTURE_STARTS).
     * 위치만의 함수라 결과를 기억한다.
     */
    public String startOf(StructureRegistry.SetDef set, int cx, int cz, TerrainAccess terrain) {
        // 바닐라 ChunkStatusTasks.generateStructureStarts 는 generateStructures 가 꺼지면 시작점을 만들지 않는다 —
        // /locate·탐험 지도·엔더의 눈(StructureCheck 가 시작점 단계까지 만들어 읽음)도 찾지 못한다.
        // 단서: 바닐라는 예전에 구조물이 켜진 채 저장된 청크의 시작점은 디스크에서 읽어 찾는다(여기는 예측만 한다).
        if (!structuresEnabled) return null;
        if (!isStructureChunk(set, cx, cz)) return null;
        String key = set.id() + "/" + cx + "/" + cz;
        String cached = setStartCache.get(key);
        if (cached != null) return cached.isEmpty() ? null : cached;
        List<StructureStart> out = new ArrayList<>(1);
        startsForSet(set, cx, cz, terrain, out);
        String id = out.isEmpty() ? "" : out.get(0).structureId();
        if (setStartCache.size() > (1 << 16)) setStartCache.clear();
        setStartCache.put(key, id);
        return id.isEmpty() ? null : id;
    }

    private volatile Set<String> onlyStructures;

    /** 테스트/진단용: 지정한 구조물만 생성(null = 전부). 옛 오라클 픽스처(특정 구조물만 켠 순정)와 비교할 때 쓴다. */
    public void restrictTo(Set<String> ids) { this.onlyStructures = ids; }

    /** generateForCommand 결과: 구조물 정의가 없음 / 아직 구현 안 됨 / 생성 실패(start=null) / 성공. */
    public record CommandStart(boolean known, boolean supported, StructureStart start) {}

    /**
     * 바닐라 Structure.generate(..., chunkPos, references=0, level, biome -> true) — /place structure 용.
     * 배치(structure set)·바이옴 판정 없이 그 청크를 시작점으로 조각을 만든다.
     */
    public CommandStart generateForCommand(String structureId, int cx, int cz, TerrainAccess terrain) {
        StructureRegistry.Def def = registry.structure(structureId);
        if (def == null) return new CommandStart(false, false, null);
        StructureType type = typeOf(def);
        if (type == null) return new CommandStart(true, false, null);
        GenContext ctx = new GenContext(this, def, seed, cx, cz, terrain);
        Optional<StructureType.Stub> stub = type.findGenerationPoint(ctx);
        if (stub.isEmpty()) return new CommandStart(true, true, null);
        PiecesBuilder b = new PiecesBuilder();
        try {
            stub.get().generator().accept(b);
        } catch (StructureTemplate.MissingTemplateException e) {
            return new CommandStart(true, false, null);
        }
        if (b.isEmpty()) return new CommandStart(true, true, null);
        return new CommandStart(true, true, new StructureStart(def.id(), cx, cz, b.pieces(), def.terrainAdaptation(), type));
    }

    private boolean tryGenerate(StructureRegistry.Entry entry, int cx, int cz, TerrainAccess terrain, List<StructureStart> out) {
        StructureRegistry.Def def = registry.structure(entry.structureId());
        Set<String> only = onlyStructures;
        if (only != null && !only.contains(def.id())) return false;
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
