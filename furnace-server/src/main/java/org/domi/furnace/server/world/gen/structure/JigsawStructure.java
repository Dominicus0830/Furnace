// 바닐라 JigsawStructure + JigsawPlacement 이식 — 템플릿 풀을 직소 블록으로 이어 붙여 마을·전초기지·고대 도시 등을 조립한다.
package org.domi.furnace.server.world.gen.structure;

import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.domi.furnace.server.world.gen.carver.LegacyRandom;
import org.domi.furnace.server.world.gen.structure.JigsawPools.Element;
import org.domi.furnace.server.world.gen.structure.JigsawPools.Jig;
import org.domi.furnace.server.world.gen.structure.JigsawPools.Pool;
import org.domi.furnace.server.world.gen.structure.template.BlockStateTable;

public final class JigsawStructure extends StructureType {

    private final String startPool;
    private final String startJigsawName;   // null = 없음
    private final int maxDepth;
    private final JsonObject startHeight;
    private final boolean useExpansionHack;
    private final Integer projectHeightmap;   // null = 투영 없음
    private final int maxDistanceFromCenter;
    private final int paddingBottom, paddingTop;
    private final boolean keepLiquids;
    private final List<JsonObject> poolAliases;

    public JigsawStructure(StructureRegistry.Def def) {
        JsonObject j = def.json();
        startPool = j.get("start_pool").getAsString();
        startJigsawName = j.has("start_jigsaw_name") ? j.get("start_jigsaw_name").getAsString() : null;
        maxDepth = j.get("size").getAsInt();
        startHeight = j.getAsJsonObject("start_height");
        useExpansionHack = j.get("use_expansion_hack").getAsBoolean();
        projectHeightmap = j.has("project_start_to_heightmap")
            ? (j.get("project_start_to_heightmap").getAsString().equals("OCEAN_FLOOR_WG") ? TerrainAccess.HM_OCEAN_FLOOR_WG : TerrainAccess.HM_WORLD_SURFACE_WG)
            : null;
        maxDistanceFromCenter = j.has("max_distance_from_center") ? j.get("max_distance_from_center").getAsInt() : 80;
        if (j.has("dimension_padding")) {
            com.google.gson.JsonElement p = j.get("dimension_padding");
            if (p.isJsonPrimitive()) {
                paddingBottom = paddingTop = p.getAsInt();
            } else {
                JsonObject po = p.getAsJsonObject();
                paddingBottom = po.has("bottom") ? po.get("bottom").getAsInt() : 0;
                paddingTop = po.has("top") ? po.get("top").getAsInt() : 0;
            }
        } else {
            paddingBottom = paddingTop = 0;
        }
        keepLiquids = !j.has("liquid_settings") || j.get("liquid_settings").getAsString().equals("apply_waterlogging");
        poolAliases = new ArrayList<>();
        if (j.has("pool_aliases")) for (com.google.gson.JsonElement e : j.getAsJsonArray("pool_aliases")) poolAliases.add(e.getAsJsonObject());
    }

    /** HeightProvider(start_height) 표본 — constant(앵커), uniform. */
    private static int sampleHeight(JsonObject h, LegacyRandom random, int minY, int genDepth) {
        if (!h.has("type") || h.get("type").getAsString().equals("minecraft:constant")) {
            JsonObject anchor = h.has("value") ? h.getAsJsonObject("value") : h;
            return anchor(anchor, minY, genDepth);
        }
        String type = h.get("type").getAsString();
        if (type.equals("minecraft:uniform")) {
            int lo = anchor(h.getAsJsonObject("min_inclusive"), minY, genDepth);
            int hi = anchor(h.getAsJsonObject("max_inclusive"), minY, genDepth);
            if (lo > hi) return lo;
            return random.nextInt(hi - lo + 1) + lo;   // Mth.randomBetweenInclusive
        }
        throw new IllegalArgumentException("미지원 start_height: " + h);
    }

    private static int anchor(JsonObject a, int minY, int genDepth) {
        if (a.has("absolute")) return a.get("absolute").getAsInt();
        if (a.has("above_bottom")) return minY + a.get("above_bottom").getAsInt();
        if (a.has("below_top")) return minY + genDepth - 1 - a.get("below_top").getAsInt();
        throw new IllegalArgumentException("미지원 앵커: " + a);
    }

    @Override
    public Optional<Stub> findGenerationPoint(GenContext c) {
        LegacyRandom random = c.random;
        int y0 = sampleHeight(startHeight, random, c.terrain.minY(), c.terrain.genDepth());
        int px = c.minBlockX(), pz = c.minBlockZ();
        PoolAliases aliases = PoolAliases.create(poolAliases, px, y0, pz, c.seed);
        StructureEngine e = c.engine;
        BlockStateTable table = e.table();

        int rotation = random.nextInt(4);   // Rotation.getRandom
        Pool pool = e.pools().pool(aliases.lookup(startPool));
        if (pool == null) return Optional.empty();
        Element start = pool.randomTemplate(random);
        if (start == JigsawPools.EMPTY) return Optional.empty();

        int ax = px, ay = y0, az = pz;
        if (startJigsawName != null) {
            Jig found = null;
            for (Jig j : start.shuffledJigsaws(e, px, y0, pz, rotation, random)) {
                if (startJigsawName.equals(j.name())) { found = j; break; }
            }
            if (found == null) return Optional.empty();
            ax = found.x(); ay = found.y(); az = found.z();
        }
        int offX = ax - px, offY = ay - y0, offZ = az - pz;
        int sx = px - offX, sy = y0 - offY, sz = pz - offZ;
        JigsawPiece startPiece = new JigsawPiece(start, sx, sy, sz, start.groundLevelDelta(), rotation,
            start.boundingBox(e, sx, sy, sz, rotation), keepLiquids);
        BoundingBox bb = startPiece.boundingBox();
        int cx = (bb.maxX() + bb.minX()) / 2;
        int cz = (bb.maxZ() + bb.minZ()) / 2;
        int y = projectHeightmap != null ? y0 + c.terrain.firstFreeHeight(projectHeightmap, cx, cz) : sy;
        int bottom = bb.minY() + startPiece.groundLevelDelta;
        startPiece.move(0, y - bottom, 0);
        int stubY = y + offY;

        return Optional.of(new Stub(cx, stubY, cz, builder -> {
            List<JigsawPiece> pieces = new ArrayList<>();
            pieces.add(startPiece);
            if (maxDepth > 0) {
                int minBuild = c.terrain.minY(), maxBuild = c.terrain.minY() + c.terrain.genDepth();
                int[] outer = {cx - maxDistanceFromCenter, Math.max(stubY - maxDistanceFromCenter, minBuild + paddingBottom), cz - maxDistanceFromCenter,
                    cx + maxDistanceFromCenter + 1, Math.min(stubY + maxDistanceFromCenter + 1, maxBuild - paddingTop), cz + maxDistanceFromCenter + 1};
                FreeSpace free = new FreeSpace(outer);
                free.subtract(startPiece.boundingBox());
                Placer placer = new Placer(e, table, c.terrain, maxDepth, pieces, random, aliases).withExpansionHack(useExpansionHack);
                placer.tryPlacingChildren(startPiece, new FreeRef(free), 0);
                Placer.Item item;
                while ((item = placer.queue.next()) != null) {
                    placer.tryPlacingChildren(item.piece, item.free, item.depth);
                }
            }
            for (JigsawPiece p : pieces) builder.addPiece(p);
        }));
    }

    // ------------------------------------------------------------------ 빈 공간(VoxelShape 대체)

    /** 정수 상자들로 이뤄진 VoxelShape: base 상자(반열림 [min,max)) 에서 subtract 된 상자들을 뺀 영역. */
    static final class FreeSpace {
        final int[] base;   // minX,minY,minZ,maxX,maxY,maxZ (max 배타)
        final List<int[]> holes = new ArrayList<>();

        FreeSpace(int[] base) { this.base = base; }

        static FreeSpace of(BoundingBox b) {
            return new FreeSpace(new int[]{b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1});
        }

        /** Shapes.joinIsNotEmpty(free, box.deflate(0.25), ONLY_SECOND) 의 부정: 상자 칸 전체가 빈 공간 안. */
        boolean contains(BoundingBox b) {
            int x0 = b.minX(), y0 = b.minY(), z0 = b.minZ(), x1 = b.maxX() + 1, y1 = b.maxY() + 1, z1 = b.maxZ() + 1;
            if (x0 < base[0] || y0 < base[1] || z0 < base[2] || x1 > base[3] || y1 > base[4] || z1 > base[5]) return false;
            for (int[] h : holes) {
                if (x0 < h[3] && x1 > h[0] && y0 < h[4] && y1 > h[1] && z0 < h[5] && z1 > h[2]) return false;
            }
            return true;
        }

        void subtract(BoundingBox b) {
            holes.add(new int[]{b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1});
        }
    }

    /** MutableObject<VoxelShape> */
    static final class FreeRef {
        FreeSpace value;

        FreeRef(FreeSpace v) { value = v; }
    }

    // ------------------------------------------------------------------ 배치기

    static final class Placer {
        record Item(JigsawPiece piece, FreeRef free, int depth) {}

        /** 바닐라 SequencedPriorityIterator: 높은 우선순위 먼저, 같은 우선순위는 FIFO. */
        static final class PriorityQueue {
            private final Map<Integer, ArrayDeque<Item>> byPriority = new HashMap<>();
            private ArrayDeque<Item> current;
            private int currentPriority = Integer.MIN_VALUE;

            void add(Item item, int priority) {
                if (priority == currentPriority && current != null) {
                    current.addLast(item);
                } else {
                    ArrayDeque<Item> d = byPriority.computeIfAbsent(priority, k -> new ArrayDeque<>());
                    d.addLast(item);
                    if (priority >= currentPriority) {
                        current = d;
                        currentPriority = priority;
                    }
                }
            }

            Item next() {
                if (current == null) return null;
                Item it = current.pollFirst();
                if (it == null) return null;
                if (current.isEmpty()) {
                    int best = Integer.MIN_VALUE;
                    ArrayDeque<Item> bestD = null;
                    for (Map.Entry<Integer, ArrayDeque<Item>> en : byPriority.entrySet()) {
                        if (en.getKey() > best && !en.getValue().isEmpty()) {
                            best = en.getKey();
                            bestD = en.getValue();
                        }
                    }
                    currentPriority = best;
                    current = bestD;
                }
                return it;
            }
        }

        final StructureEngine e;
        final BlockStateTable table;
        final TerrainAccess terrain;
        final int maxDepth;
        final List<JigsawPiece> pieces;
        final LegacyRandom random;
        final PoolAliases aliases;
        final PriorityQueue queue = new PriorityQueue();

        Placer(StructureEngine e, BlockStateTable table, TerrainAccess terrain, int maxDepth, List<JigsawPiece> pieces,
               LegacyRandom random, PoolAliases aliases) {
            this.e = e;
            this.table = table;
            this.terrain = terrain;
            this.maxDepth = maxDepth;
            this.pieces = pieces;
            this.random = random;
            this.aliases = aliases;
        }

        private static final int[] STEP_X = {0, 0, 0, 0, -1, 1}, STEP_Y = {-1, 1, 0, 0, 0, 0}, STEP_Z = {0, 0, -1, 1, 0, 0};

        void tryPlacingChildren(JigsawPiece piece, FreeRef freeRef, int depth) {
            Element element = piece.element;
            boolean rigid = element.rigid();
            FreeRef inner = new FreeRef(null);
            BoundingBox bb = piece.boundingBox();
            int minY = bb.minY();

            outer:
            for (Jig jig : element.shuffledJigsaws(e, piece.px, piece.py, piece.pz, piece.rotation, random)) {
                int facing = JigsawPools.frontFacing(table, jig.state());
                int tx = jig.x() + STEP_X[facing], ty = jig.y() + STEP_Y[facing], tz = jig.z() + STEP_Z[facing];
                int relY = jig.y() - minY;
                int surfaceY = -1;
                String poolKey = aliases.lookup(jig.pool());
                Pool pool = e.pools().pool(poolKey);
                if (pool == null) continue;
                if (pool.size() == 0 && !poolKey.equals(JigsawPools.EMPTY_POOL)) continue;
                Pool fallback = e.pools().pool(pool.fallback);
                if (fallback == null) continue;
                if (fallback.size() == 0 && !pool.fallback.equals(JigsawPools.EMPTY_POOL)) continue;

                boolean inside = bb.isInside(tx, ty, tz);
                FreeRef free;
                if (inside) {
                    free = inner;
                    if (inner.value == null) inner.value = FreeSpace.of(bb);
                } else {
                    free = freeRef;
                }
                List<Element> candidates = new ArrayList<>();
                if (depth != maxDepth) candidates.addAll(pool.shuffledTemplates(random));
                candidates.addAll(fallback.shuffledTemplates(random));
                int placementPriority = jig.placementPriority();

                for (Element cand : candidates) {
                    if (cand == JigsawPools.EMPTY) break;
                    for (int rot2 : shuffledRotations(random)) {
                        List<Jig> candJigs = cand.shuffledJigsaws(e, 0, 0, 0, rot2, random);
                        BoundingBox candBox0 = cand.boundingBox(e, 0, 0, 0, rot2);
                        int expansion = 0;
                        if (useExpansionHack && candBox0.maxY() - candBox0.minY() + 1 <= 16) {
                            for (Jig cj : candJigs) {
                                int f = JigsawPools.frontFacing(table, cj.state());
                                int v;
                                if (!candBox0.isInside(cj.x() + STEP_X[f], cj.y() + STEP_Y[f], cj.z() + STEP_Z[f])) {
                                    v = 0;
                                } else {
                                    Pool p = e.pools().pool(aliases.lookup(cj.pool()));
                                    int a = p == null ? 0 : p.maxSize(e);
                                    Pool fb = p == null ? null : e.pools().pool(p.fallback);
                                    int b = fb == null ? 0 : fb.maxSize(e);
                                    v = Math.max(a, b);
                                }
                                expansion = Math.max(expansion, v);
                            }
                        }
                        for (Jig cj : candJigs) {
                            if (!JigsawPools.canAttach(table, jig, cj)) continue;
                            int dx = tx - cj.x(), dy0 = ty - cj.y(), dz = tz - cj.z();
                            BoundingBox candBox = cand.boundingBox(e, dx, dy0, dz, rot2);
                            int candMinY = candBox.minY();
                            boolean candRigid = cand.rigid();
                            int cjY = cj.y();
                            int dy = relY - cjY + STEP_Y[facing];
                            int targetY;
                            if (rigid && candRigid) {
                                targetY = minY + dy;
                            } else {
                                if (surfaceY == -1) surfaceY = terrain.firstFreeHeight(TerrainAccess.HM_WORLD_SURFACE_WG, jig.x(), jig.z());
                                targetY = surfaceY - cjY;
                            }
                            int shift = targetY - candMinY;
                            BoundingBox box2 = new BoundingBox(candBox.minX(), candBox.minY() + shift, candBox.minZ(),
                                candBox.maxX(), candBox.maxY() + shift, candBox.maxZ());
                            int nx = dx, ny = dy0 + shift, nz = dz;
                            if (expansion > 0) {
                                int h = Math.max(expansion + 1, box2.maxY() - box2.minY());
                                box2.encapsulate(box2.minX(), box2.minY() + h, box2.minZ(), box2.minX(), box2.minY() + h, box2.minZ());
                            }
                            if (!free.value.contains(box2)) continue;
                            free.value.subtract(box2);
                            int pieceGround = piece.groundLevelDelta;
                            int g2 = candRigid ? pieceGround - dy : cand.groundLevelDelta();
                            JigsawPiece np = new JigsawPiece(cand, nx, ny, nz, g2, rot2, box2, piece.keepLiquids);
                            int junctionY;
                            if (rigid) {
                                junctionY = minY + relY;
                            } else if (candRigid) {
                                junctionY = targetY + cjY;
                            } else {
                                if (surfaceY == -1) surfaceY = terrain.firstFreeHeight(TerrainAccess.HM_WORLD_SURFACE_WG, jig.x(), jig.z());
                                junctionY = surfaceY + dy / 2;
                            }
                            piece.junctions.add(new JigsawPiece.Junction(tx, junctionY - relY + pieceGround, tz, dy, cand.rigid()));
                            np.junctions.add(new JigsawPiece.Junction(jig.x(), junctionY - cjY + g2, jig.z(), -dy, element.rigid()));
                            pieces.add(np);
                            if (depth + 1 <= maxDepth) queue.add(new Item(np, free, depth + 1), placementPriority);
                            continue outer;
                        }
                    }
                }
            }
        }

        boolean useExpansionHack;

        Placer withExpansionHack(boolean v) { useExpansionHack = v; return this; }

        /** Rotation.getShuffled(random) */
        private static int[] shuffledRotations(LegacyRandom random) {
            List<Integer> l = new ArrayList<>(List.of(0, 1, 2, 3));
            JigsawPools.shuffle(l, random);
            return new int[]{l.get(0), l.get(1), l.get(2), l.get(3)};
        }
    }

    // ------------------------------------------------------------------ 풀 별칭(1.21 pool_aliases)

    static final class PoolAliases {
        private final Map<String, String> map;

        private PoolAliases(Map<String, String> map) { this.map = map; }

        String lookup(String id) {
            String key = id.contains(":") ? id : "minecraft:" + id;
            return map.getOrDefault(key, key);
        }

        static PoolAliases create(List<JsonObject> bindings, int x, int y, int z, long seed) {
            if (bindings.isEmpty()) return new PoolAliases(Map.of());
            // RandomSource.create(seed).forkPositional().at(pos)
            LegacyRandom base = new LegacyRandom(seed);
            long fork = base.nextLong();
            LegacyRandom r = new LegacyRandom(org.domi.furnace.server.world.gen.structure.template.PlaceSettings.getSeed(x, y, z) ^ fork);
            Map<String, String> m = new HashMap<>();
            for (JsonObject b : bindings) resolve(b, r, m);
            return new PoolAliases(m);
        }

        private static String ns(String s) { return s.contains(":") ? s : "minecraft:" + s; }

        private static void resolve(JsonObject b, LegacyRandom r, Map<String, String> out) {
            String type = b.get("type").getAsString();
            switch (type) {
                case "minecraft:direct" -> out.put(ns(b.get("alias").getAsString()), ns(b.get("target").getAsString()));
                case "minecraft:random" -> {
                    com.google.gson.JsonArray targets = b.getAsJsonArray("targets");
                    String chosen = weighted(targets, r).get("data").getAsString();
                    out.put(ns(b.get("alias").getAsString()), ns(chosen));
                }
                case "minecraft:random_group" -> {
                    com.google.gson.JsonArray groups = b.getAsJsonArray("groups");
                    JsonObject g = weighted(groups, r);
                    for (com.google.gson.JsonElement e : g.getAsJsonArray("data")) resolve(e.getAsJsonObject(), r, out);
                }
                default -> throw new IllegalArgumentException("미지원 pool alias: " + type);
            }
        }

        /** SimpleWeightedRandomList.getRandom(random): total 가중치에서 nextInt 후 누적 차감. */
        private static JsonObject weighted(com.google.gson.JsonArray arr, LegacyRandom r) {
            int total = 0;
            for (com.google.gson.JsonElement e : arr) total += e.getAsJsonObject().get("weight").getAsInt();
            int v = r.nextInt(total);
            for (com.google.gson.JsonElement e : arr) {
                v -= e.getAsJsonObject().get("weight").getAsInt();
                if (v < 0) return e.getAsJsonObject();
            }
            throw new IllegalStateException();
        }
    }
}
