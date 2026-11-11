// 바닐라 StructureTemplatePool + StructurePoolElement(single/legacy/feature/list/empty) 이식 — 템플릿 풀 로드와 요소 배치.
package org.domi.furnace.server.world.gen.structure;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.world.gen.carver.LegacyRandom;
import org.domi.furnace.server.world.gen.feature.BlockPos;
import org.domi.furnace.server.world.gen.feature.PlacedFeature;
import org.domi.furnace.server.world.gen.structure.template.BlockStateTable;
import org.domi.furnace.server.world.gen.structure.template.PlaceSettings;
import org.domi.furnace.server.world.gen.structure.template.Processor;
import org.domi.furnace.server.world.gen.structure.template.Processors;
import org.domi.furnace.server.world.gen.structure.template.StructureTemplate;
import org.domi.furnace.server.world.gen.structure.template.TemplatePlacer;

public final class JigsawPools {

    public static final String EMPTY_POOL = "minecraft:empty";

    private final StructureEngine engine;
    private final Map<String, Pool> pools = new ConcurrentHashMap<>();

    JigsawPools(StructureEngine engine) {
        this.engine = engine;
    }

    /** 없는 풀이면 null(바닐라: "Empty or non-existent pool" 경고 후 건너뜀). */
    public Pool pool(String id) {
        String key = id.contains(":") ? id : "minecraft:" + id;
        Pool p = pools.get(key);
        if (p != null) return p == MISSING ? null : p;
        p = load(key);
        pools.put(key, p == null ? MISSING : p);
        return p;
    }

    private static final Pool MISSING = new Pool("", "", List.of());

    private Pool load(String id) {
        String path = id.substring(id.indexOf(':') + 1);
        String res = "worldgen/1.21.1/worldgen/template_pool/" + path + ".json";
        JsonObject root;
        try (InputStream in = JigsawPools.class.getClassLoader().getResourceAsStream(res)) {
            if (in == null) return null;
            root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<Element> expanded = new ArrayList<>();
        for (JsonElement e : root.getAsJsonArray("elements")) {
            JsonObject eo = e.getAsJsonObject();
            Element el = parseElement(eo.getAsJsonObject("element"), null);
            int w = eo.get("weight").getAsInt();
            for (int i = 0; i < w; i++) expanded.add(el);
        }
        return new Pool(id, root.get("fallback").getAsString(), List.copyOf(expanded));
    }

    private Element parseElement(JsonObject o, String inheritedProjection) {
        String type = o.get("element_type").getAsString();
        String projection = o.has("projection") ? o.get("projection").getAsString() : inheritedProjection;
        switch (type) {
            case "minecraft:empty_pool_element":
                return EMPTY;
            case "minecraft:single_pool_element":
            case "minecraft:legacy_single_pool_element": {
                List<Processor> procs = parseProcessors(o.get("processors"));
                Boolean liquid = null;
                if (o.has("override_liquid_settings")) liquid = "apply_waterlogging".equals(o.get("override_liquid_settings").getAsString());
                Single s = new Single(o.get("location").getAsString(), procs, projection, liquid, type.equals("minecraft:legacy_single_pool_element"));
                s.processorsJson = o.get("processors");
                return s;
            }
            case "minecraft:feature_pool_element":
                return new Feature(o.get("feature").getAsString(), projection);
            case "minecraft:list_pool_element": {
                List<Element> list = new ArrayList<>();
                for (JsonElement e : o.getAsJsonArray("elements")) list.add(parseElement(e.getAsJsonObject(), projection));
                return new ListElement(List.copyOf(list), projection);
            }
            default:
                throw new IllegalArgumentException("미지원 pool element: " + type);
        }
    }

    private List<Processor> parseProcessors(JsonElement e) {
        if (e == null) return List.of();
        if (e.isJsonPrimitive()) return engine.processorLists().get(e.getAsString());
        JsonObject o = e.getAsJsonObject();
        JsonArray arr = o.getAsJsonArray("processors");
        List<Processor> out = new ArrayList<>();
        for (JsonElement p : arr) out.add(engine.processorLists().parseProcessor(p.getAsJsonObject()));
        return List.copyOf(out);
    }

    // ------------------------------------------------------------------ 풀

    public static final class Pool {
        public final String id;
        public final String fallback;
        final List<Element> templates;   // 가중치만큼 반복된 목록(JSON 순서)
        private volatile int maxSize = Integer.MIN_VALUE;

        Pool(String id, String fallback, List<Element> templates) {
            this.id = id;
            this.fallback = fallback;
            this.templates = templates;
        }

        public int size() { return templates.size(); }

        /** getRandomTemplate */
        public Element randomTemplate(LegacyRandom random) {
            return templates.isEmpty() ? EMPTY : templates.get(random.nextInt(templates.size()));
        }

        /** getShuffledTemplates — Util.toShuffledList */
        public List<Element> shuffledTemplates(LegacyRandom random) {
            List<Element> copy = new ArrayList<>(templates);
            shuffle(copy, random);
            return copy;
        }

        /** getMaxSize: 빈 요소 제외, 회전 없음 상자의 YSpan 최댓값. */
        public int maxSize(StructureEngine e) {
            int m = maxSize;
            if (m == Integer.MIN_VALUE) {
                m = 0;
                for (Element el : templates) {
                    if (el == EMPTY) continue;
                    BoundingBox b = el.boundingBox(e, 0, 0, 0, BlockStateTable.ROT_NONE);
                    m = Math.max(m, b.maxY() - b.minY() + 1);
                }
                maxSize = m;
            }
            return m;
        }
    }

    /** Util.shuffle(list, random) */
    static <T> void shuffle(List<T> list, LegacyRandom random) {
        for (int i = list.size(); i > 1; i--) {
            int j = random.nextInt(i);
            list.set(i - 1, list.set(j, list.get(i - 1)));
        }
    }

    // ------------------------------------------------------------------ 요소

    /** 직소 블록 정보: 월드 좌표, 회전 적용 상태, NBT(name/target/pool/joint/final_state/…). */
    /**
     * 템플릿 안의 직소 블록. 조립 중 수없이 비교하므로 방향·NBT 값을 만들 때 한 번만 읽어 둔다
     * (예전에는 비교마다 orientation 문자열을 파싱하고 NBT 맵을 조회했다 — 결과는 같다).
     */
    public static final class Jig {
        private final int x, y, z, state;
        private final NbtCompound nbt;
        final int front, top, selectionPriority, placementPriority;
        final boolean rollable;
        final String name, target, pool;

        Jig(BlockStateTable t, int x, int y, int z, int state, NbtCompound nbt) {
            this.x = x; this.y = y; this.z = z; this.state = state; this.nbt = nbt;
            int o = orientation(t, state);
            this.front = o >> 4;
            this.top = o & 15;
            this.selectionPriority = nbt != null && nbt.contains("selection_priority") ? nbt.getInt("selection_priority") : 0;
            this.placementPriority = nbt != null && nbt.contains("placement_priority") ? nbt.getInt("placement_priority") : 0;
            String joint = nbt != null && nbt.contains("joint") ? nbt.getString("joint") : "";
            if (joint.equals("rollable")) this.rollable = true;
            else if (joint.equals("aligned")) this.rollable = false;
            else this.rollable = front <= 1;   // 기본: 수평 방향이면 aligned, 수직이면 rollable
            this.name = nbt != null ? nbt.getString("name") : null;
            this.target = nbt != null ? nbt.getString("target") : null;
            this.pool = nbt != null ? nbt.getString("pool") : null;
        }

        /** 같은 직소를 (dx,dy,dz) 만큼 옮긴 복사본 — 미리 읽어 둔 값은 그대로 쓴다. */
        private Jig(Jig o, int dx, int dy, int dz) {
            this.x = o.x + dx; this.y = o.y + dy; this.z = o.z + dz; this.state = o.state; this.nbt = o.nbt;
            this.front = o.front; this.top = o.top;
            this.selectionPriority = o.selectionPriority; this.placementPriority = o.placementPriority;
            this.rollable = o.rollable; this.name = o.name; this.target = o.target; this.pool = o.pool;
        }

        Jig moved(int dx, int dy, int dz) { return new Jig(this, dx, dy, dz); }

        public int x() { return x; }
        public int y() { return y; }
        public int z() { return z; }
        public int state() { return state; }
        public NbtCompound nbt() { return nbt; }
        public String name() { return name; }
        public String target() { return target; }
        public String pool() { return pool; }
        public int placementPriority() { return placementPriority; }
    }

    /** 직소 상태 → (front<<4 | top). 직소 방향 상태는 12개뿐이라 상태 id 별로 기억한다. */
    private static final java.util.concurrent.ConcurrentHashMap<Integer, Integer> ORIENTATION = new java.util.concurrent.ConcurrentHashMap<>();

    private static int orientation(BlockStateTable t, int state) {
        Integer c = ORIENTATION.get(state);
        if (c != null) return c;
        String o = t.prop(state, "orientation");
        int v = (dirIndex(o.substring(0, o.indexOf('_'))) << 4) | dirIndex(o.substring(o.indexOf('_') + 1));
        ORIENTATION.put(state, v);
        return v;
    }

    public abstract static class Element {
        final String projection;

        Element(String projection) { this.projection = projection; }

        public boolean rigid() { return !"terrain_matching".equals(projection); }

        public abstract List<Jig> shuffledJigsaws(StructureEngine e, int x, int y, int z, int rotation, LegacyRandom random);

        public abstract BoundingBox boundingBox(StructureEngine e, int x, int y, int z, int rotation);

        public abstract boolean place(PostCtx c, int x, int y, int z, int rotation, BoundingBox box, boolean keepLiquids, boolean keepJigsaws);

        public int groundLevelDelta() { return 1; }

        /**
         * 바닐라 StructurePoolElement.CODEC(ekz.f, element_type 로 나눔) 인코딩 — 직소 조각 저장의 pool_element.
         * projection: 목록 요소 안에서는 바닐라 ListPoolElement 가 안쪽 요소의 projection 을 목록의 것으로 바꾼다(setProjectionOnEachElement).
         */
        public abstract org.domi.furnace.server.net.io.nbt.NbtCompound encode(String projection);

        String projection() { return projection; }
    }

    /** ResourceLocation.toString: 이름공간이 없으면 minecraft. */
    static String rl(String id) { return id.indexOf(':') < 0 ? "minecraft:" + id : id; }

    public static final Element EMPTY = new Element("terrain_matching") {
        @Override
        public List<Jig> shuffledJigsaws(StructureEngine e, int x, int y, int z, int rotation, LegacyRandom random) { return List.of(); }

        @Override
        public BoundingBox boundingBox(StructureEngine e, int x, int y, int z, int rotation) {
            throw new IllegalStateException("Invalid call to EmptyPoolElement.getBoundingBox");
        }

        @Override
        public boolean place(PostCtx c, int x, int y, int z, int rotation, BoundingBox box, boolean keepLiquids, boolean keepJigsaws) { return true; }

        @Override
        public org.domi.furnace.server.net.io.nbt.NbtCompound encode(String projection) {
            org.domi.furnace.server.net.io.nbt.NbtCompound t = new org.domi.furnace.server.net.io.nbt.NbtCompound();
            t.putString("element_type", "minecraft:empty_pool_element");
            return t;
        }

        @Override
        public String toString() { return "Empty"; }
    };

    static final class Single extends Element {
        final String location;
        final List<Processor> processors;
        final Boolean overrideLiquid;
        final boolean legacy;
        /** 원본 JSON 의 processors(목록 id 문자열 또는 인라인 {processors:[…]}) — 저장 때 그대로 인코딩한다. */
        com.google.gson.JsonElement processorsJson;

        Single(String location, List<Processor> processors, String projection, Boolean overrideLiquid, boolean legacy) {
            super(projection);
            this.location = location;
            this.processors = processors;
            this.overrideLiquid = overrideLiquid;
            this.legacy = legacy;
        }

        @Override
        public List<Jig> shuffledJigsaws(StructureEngine e, int x, int y, int z, int rotation, LegacyRandom random) {
            StructureTemplate t = e.template(location);
            PlaceSettings st = new PlaceSettings();
            st.rotation = rotation;
            // filterBlocks(…, x, y, z) = 팔레트(위치로 정한 무작위) 의 직소 칸을 회전한 뒤 (x,y,z) 만큼 옮긴 것 — 경계 상자가 없어
            // 위치와 무관한 부분(회전·상태 변환·NBT 값)은 (팔레트, 회전) 별로 한 번만 만들어 두고 옮기기만 한다.
            int palettes = t.palettes().size();
            List<Jig> out;
            if (palettes == 0) {
                out = new ArrayList<>();
            } else {
                int paletteIndex = st.getRandom(x, y, z).nextInt(palettes);
                List<Jig> rel = relativeJigs(e, t, paletteIndex, rotation);
                out = new ArrayList<>(rel.size());
                for (Jig j : rel) out.add(j.moved(x, y, z));
            }
            shuffle(out, random);
            // selection_priority 내림차순(안정 정렬)
            out.sort((a, b) -> Integer.compare(b.selectionPriority, a.selectionPriority));
            return out;
        }

        private final java.util.concurrent.ConcurrentHashMap<Integer, List<Jig>> relJigs = new java.util.concurrent.ConcurrentHashMap<>();

        private List<Jig> relativeJigs(StructureEngine e, StructureTemplate t, int paletteIndex, int rotation) {
            return relJigs.computeIfAbsent(paletteIndex * 8 + rotation, k -> {
                PlaceSettings st = new PlaceSettings();
                st.rotation = rotation;
                List<Jig> rel = new ArrayList<>();
                for (StructureTemplate.BlockInfo info : t.blocksNamed(paletteIndex, "minecraft:jigsaw", e.table())) {
                    int[] p = StructureTemplate.transform(info.x(), info.y(), info.z(), st.mirror, st.rotation, st.pivotX, st.pivotZ);
                    rel.add(new Jig(e.table(), p[0], p[1], p[2],
                        e.table().transform(info.state(), BlockStateTable.NONE, st.rotation), info.nbt()));
                }
                return List.copyOf(rel);
            });
        }

        @Override
        public BoundingBox boundingBox(StructureEngine e, int x, int y, int z, int rotation) {
            int[] b = e.template(location).boundingBox(x, y, z, rotation, 0, 0, BlockStateTable.NONE);
            return new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]);
        }

        PlaceSettings settings(int rotation, BoundingBox box, boolean keepLiquids, boolean keepJigsaws) {
            PlaceSettings st = new PlaceSettings();
            st.boundingBox = new int[]{box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()};
            st.rotation = rotation;
            st.knownShape = true;
            st.ignoreEntities = false;
            Processor ignore = Processors.BlockIgnore.structureBlock();
            st.processors.add(ignore);
            st.finalizeEntities = true;
            st.keepLiquids = overrideLiquid != null ? overrideLiquid : keepLiquids;
            if (!keepJigsaws) st.processors.add(new Processors.JigsawReplacement());
            st.processors.addAll(processors);
            if (!rigid()) st.processors.add(new Processors.Gravity("world_surface_wg", -1));
            if (legacy) {
                st.processors.remove(ignore);
                st.processors.add(Processors.BlockIgnore.structureAndAir());
            }
            return st;
        }

        @Override
        public boolean place(PostCtx c, int x, int y, int z, int rotation, BoundingBox box, boolean keepLiquids, boolean keepJigsaws) {
            StructureTemplate t = c.engine.template(location);
            PlaceSettings st = settings(rotation, box, keepLiquids, keepJigsaws);
            return TemplatePlacer.placeInWorld(t, c.view, c.shapes(), c.table(), c.processorCtx(), x, y, z, c.pivotX, c.pivotY, c.pivotZ, st, c.random, 18);
        }

        /** SinglePoolElement 코덱: location, processors(Holder — 레지스트리 항목이면 id, 아니면 인라인), projection, override_liquid_settings(있을 때만). */
        @Override
        public org.domi.furnace.server.net.io.nbt.NbtCompound encode(String projection) {
            org.domi.furnace.server.net.io.nbt.NbtCompound t = new org.domi.furnace.server.net.io.nbt.NbtCompound();
            t.putString("element_type", legacy ? "minecraft:legacy_single_pool_element" : "minecraft:single_pool_element");
            t.putString("location", rl(location));
            com.google.gson.JsonElement pj = processorsJson;
            if (pj == null) t.putString("processors", "minecraft:empty");
            else if (pj.isJsonPrimitive()) t.putString("processors", rl(pj.getAsString()));
            else t.put("processors", org.domi.furnace.server.data.JsonNbt.convert(pj));
            t.putString("projection", projection);
            if (overrideLiquid != null) t.putString("override_liquid_settings", overrideLiquid ? "apply_waterlogging" : "ignore_waterlogging");
            return t;
        }

        @Override
        public String toString() { return (legacy ? "LegacySingle[" : "Single[") + location + "]"; }
    }

    static final class Feature extends Element {
        final String feature;

        Feature(String feature, String projection) {
            super(projection);
            this.feature = feature;
        }

        @Override
        public List<Jig> shuffledJigsaws(StructureEngine e, int x, int y, int z, int rotation, LegacyRandom random) {
            NbtCompound nbt = new NbtCompound();
            nbt.putString("name", "minecraft:bottom");
            nbt.putString("final_state", "minecraft:air");
            nbt.putString("pool", "minecraft:empty");
            nbt.putString("target", "minecraft:empty");
            nbt.putString("joint", "rollable");
            return List.of(new Jig(e.table(), x, y, z, e.state("minecraft:jigsaw[orientation=down_south]"), nbt));
        }

        @Override
        public BoundingBox boundingBox(StructureEngine e, int x, int y, int z, int rotation) {
            return new BoundingBox(x, y, z, x, y, z);
        }

        @Override
        public boolean place(PostCtx c, int x, int y, int z, int rotation, BoundingBox box, boolean keepLiquids, boolean keepJigsaws) {
            PlacedFeature pf = c.engine.placedFeature(feature);
            if (pf == null) return false;   // 미지원 feature — 배치하지 않는다
            return pf.placeAt(c.view, c.random, new BlockPos(x, y, z));
        }

        @Override
        public org.domi.furnace.server.net.io.nbt.NbtCompound encode(String projection) {
            org.domi.furnace.server.net.io.nbt.NbtCompound t = new org.domi.furnace.server.net.io.nbt.NbtCompound();
            t.putString("element_type", "minecraft:feature_pool_element");
            t.putString("feature", rl(feature));
            t.putString("projection", projection);
            return t;
        }

        @Override
        public String toString() { return "Feature[" + feature + "]"; }
    }

    static final class ListElement extends Element {
        final List<Element> elements;

        ListElement(List<Element> elements, String projection) {
            super(projection);
            if (elements.isEmpty()) throw new IllegalArgumentException("Elements are empty");
            this.elements = elements;
        }

        @Override
        public List<Jig> shuffledJigsaws(StructureEngine e, int x, int y, int z, int rotation, LegacyRandom random) {
            return elements.get(0).shuffledJigsaws(e, x, y, z, rotation, random);
        }

        @Override
        public BoundingBox boundingBox(StructureEngine e, int x, int y, int z, int rotation) {
            BoundingBox bb = null;
            for (Element el : elements) {
                if (el == EMPTY) continue;
                BoundingBox b = el.boundingBox(e, x, y, z, rotation);
                bb = bb == null ? b : BoundingBox.encapsulate(bb, b);
            }
            if (bb == null) throw new IllegalStateException("Unable to calculate boundingbox for ListPoolElement");
            return bb;
        }

        @Override
        public boolean place(PostCtx c, int x, int y, int z, int rotation, BoundingBox box, boolean keepLiquids, boolean keepJigsaws) {
            for (Element el : elements) if (!el.place(c, x, y, z, rotation, box, keepLiquids, keepJigsaws)) return false;
            return true;
        }

        @Override
        public org.domi.furnace.server.net.io.nbt.NbtCompound encode(String projection) {
            org.domi.furnace.server.net.io.nbt.NbtCompound t = new org.domi.furnace.server.net.io.nbt.NbtCompound();
            t.putString("element_type", "minecraft:list_pool_element");
            var list = new org.domi.furnace.server.net.io.nbt.NbtList(org.domi.furnace.server.net.io.nbt.NbtIo.TAG_COMPOUND);
            for (Element el : elements) list.add(el.encode(projection));
            t.putList("elements", list);
            t.putString("projection", projection);
            return t;
        }
    }

    // ------------------------------------------------------------------ 직소 블록 방향

    /** JigsawBlock.getFrontFacing: orientation 값 "front_top" 의 앞 방향. 반환: BlockShapes 방향 인덱스(DOWN0 UP1 N2 S3 W4 E5). */
    static int frontFacing(BlockStateTable t, int state) { return orientation(t, state) >> 4; }

    static int topFacing(BlockStateTable t, int state) { return orientation(t, state) & 15; }

    static int dirIndex(String n) {
        return switch (n) {
            case "down" -> 0;
            case "up" -> 1;
            case "north" -> 2;
            case "south" -> 3;
            case "west" -> 4;
            default -> 5;
        };
    }

    /** JigsawBlock.canAttach(source, target). */
    static boolean canAttach(BlockStateTable t, Jig source, Jig target) {
        return source.front == (target.front ^ 1) && (source.rollable || source.top == target.top)
            && source.target.equals(target.name);
    }

}
