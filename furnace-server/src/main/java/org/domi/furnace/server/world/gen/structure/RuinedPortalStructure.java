// 파괴된 포탈 — 바닐라 RuinedPortalStructure + RuinedPortalPiece.
package org.domi.furnace.server.world.gen.structure;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.domi.furnace.server.world.BlockAttrs;
import org.domi.furnace.server.world.gen.carver.LegacyRandom;
import org.domi.furnace.server.world.gen.structure.template.BlockStateTable;
import org.domi.furnace.server.world.gen.structure.template.PlaceSettings;
import org.domi.furnace.server.world.gen.structure.template.Processors;
import org.domi.furnace.server.world.gen.structure.template.StructureTemplate;

public final class RuinedPortalStructure extends StructureType {

    enum VP { ON_LAND_SURFACE, PARTLY_BURIED, ON_OCEAN_FLOOR, IN_MOUNTAIN, UNDERGROUND, IN_NETHER;
        static VP of(String s) { return valueOf(s.toUpperCase()); } }

    record Setup(VP placement, float airPocketProbability, float mossiness, boolean overgrown, boolean vines, boolean canBeCold,
                 boolean replaceWithBlackstone, float weight) {}

    static final class Props {
        boolean cold, airPocket, overgrown, vines, replaceWithBlackstone;
        float mossiness;
    }

    private static final String[] PORTALS = new String[10];
    private static final String[] GIANT = new String[3];
    static {
        for (int i = 0; i < 10; i++) PORTALS[i] = "minecraft:ruined_portal/portal_" + (i + 1);
        for (int i = 0; i < 3; i++) GIANT[i] = "minecraft:ruined_portal/giant_portal_" + (i + 1);
    }

    private final List<Setup> setups = new ArrayList<>();

    public RuinedPortalStructure(StructureRegistry.Def def) {
        for (JsonElement e : def.json().getAsJsonArray("setups")) {
            JsonObject o = e.getAsJsonObject();
            setups.add(new Setup(VP.of(o.get("placement").getAsString()), o.get("air_pocket_probability").getAsFloat(),
                o.get("mossiness").getAsFloat(), o.get("overgrown").getAsBoolean(), o.get("vines").getAsBoolean(),
                o.get("can_be_cold").getAsBoolean(), o.get("replace_with_blackstone").getAsBoolean(), o.get("weight").getAsFloat()));
        }
    }

    private static boolean sample(LegacyRandom r, float p) {
        if (p == 0.0F) return false;
        return p == 1.0F || r.nextFloat() < p;
    }

    @Override
    public Optional<Stub> findGenerationPoint(GenContext c) {
        Props props = new Props();
        LegacyRandom random = c.random;
        Setup setup = null;
        if (setups.size() > 1) {
            float total = 0.0F;
            for (Setup s : setups) total += s.weight();
            float r = random.nextFloat();
            for (Setup s : setups) {
                r -= s.weight() / total;
                if (r < 0.0F) { setup = s; break; }
            }
        } else {
            setup = setups.get(0);
        }
        if (setup == null) throw new IllegalStateException();
        props.airPocket = sample(random, setup.airPocketProbability());
        props.mossiness = setup.mossiness();
        props.overgrown = setup.overgrown();
        props.vines = setup.vines();
        props.replaceWithBlackstone = setup.replaceWithBlackstone();
        String name;
        if (random.nextFloat() < 0.05F) name = GIANT[random.nextInt(GIANT.length)];
        else name = PORTALS[random.nextInt(PORTALS.length)];
        StructureTemplate t = c.engine.template(name);
        int rotation = random.nextInt(4);
        int mirror = random.nextFloat() < 0.5F ? BlockStateTable.NONE : BlockStateTable.FRONT_BACK;
        int pivX = t.sizeX / 2, pivZ = t.sizeZ / 2;
        int px = c.minBlockX(), pz = c.minBlockZ();
        int[] bb = t.boundingBox(px, 0, pz, rotation, pivX, pivZ, mirror);
        int centerX = bb[0] + (bb[3] - bb[0] + 1) / 2;
        int centerZ = bb[2] + (bb[5] - bb[2] + 1) / 2;
        int hm = setup.placement() == VP.ON_OCEAN_FLOOR ? TerrainAccess.HM_OCEAN_FLOOR_WG : TerrainAccess.HM_WORLD_SURFACE_WG;
        int surfaceY = c.terrain.firstOccupiedHeight(hm, centerX, centerZ);
        int ySpan = bb[4] - bb[1] + 1;
        int y = findSuitableY(c, random, setup.placement(), props.airPocket, surfaceY, ySpan, bb);
        final Setup fs = setup;
        final String fname = name;
        final int frot = rotation, fmir = mirror, fpx = pivX, fpz = pivZ;
        return Optional.of(new Stub(px, y, pz, b -> {
            if (fs.canBeCold()) props.cold = c.terrain.coldEnoughToSnow(c.terrain.biomeAt(px, y, pz), px, y, pz);
            b.addPiece(new Piece(c.engine, px, y, pz, fs.placement(), props, fname, t, frot, fmir, fpx, fpz));
        }));
    }

    private static int randomBetween(LegacyRandom r, int a, int b) { return r.nextInt(b - a + 1) + a; }

    private static int findBetween(LegacyRandom r, int a, int b) { return a < b ? randomBetween(r, a, b) : b; }

    private int findSuitableY(GenContext c, LegacyRandom random, VP vp, boolean airPocket, int surfaceY, int ySpan, int[] bb) {
        int minY15 = c.terrain.minY() + 15;
        int y;
        if (vp == VP.IN_NETHER) {
            if (airPocket) y = randomBetween(random, 32, 100);
            else if (random.nextFloat() < 0.5F) y = randomBetween(random, 27, 29);
            else y = randomBetween(random, 29, 100);
        } else if (vp == VP.IN_MOUNTAIN) {
            y = findBetween(random, 70, surfaceY - ySpan);
        } else if (vp == VP.UNDERGROUND) {
            y = findBetween(random, minY15, surfaceY - ySpan);
        } else if (vp == VP.PARTLY_BURIED) {
            y = surfaceY - ySpan + randomBetween(random, 2, 8);
        } else {
            y = surfaceY;
        }
        int[][] corners = {{bb[0], bb[2]}, {bb[3], bb[2]}, {bb[0], bb[5]}, {bb[3], bb[5]}};
        BlockAttrs a = BlockAttrs.get();
        boolean ocean = vp == VP.ON_OCEAN_FLOOR;
        int yy;
        for (yy = y; yy > minY15; yy--) {
            int count = 0;
            for (int[] p : corners) {
                int s = c.terrain.baseBlock(p[0], yy, p[1]);
                boolean opaque = ocean ? a.blocksMotion(s) : !a.isAir(s);
                if (opaque && ++count == 3) return yy;
            }
        }
        return yy;
    }

    static final class Piece extends TemplatePiece {
        private final VP vp;
        private final Props props;

        Piece(StructureEngine e, int x, int y, int z, VP vp, Props props, String name, StructureTemplate t, int rotation, int mirror, int pivX, int pivZ) {
            super(name, t, settings(e, mirror, rotation, vp, pivX, pivZ, props), x, y, z);
            this.vp = vp;
            this.props = props;
        }

        private static Processors.Rule rule(StructureEngine e, String in, float p, String out) {
            return new Processors.Rule(Processors.randomBlockMatch(in, p), Processors.alwaysTrue(), Processors.alwaysTruePos(),
                e.blocks().defaultStateId(out), Processors.passthrough());
        }

        private static Processors.Rule rule(StructureEngine e, String in, String out) {
            return new Processors.Rule(Processors.blockMatch(in), Processors.alwaysTrue(), Processors.alwaysTruePos(),
                e.blocks().defaultStateId(out), Processors.passthrough());
        }

        private static PlaceSettings settings(StructureEngine e, int mirror, int rotation, VP vp, int pivX, int pivZ, Props props) {
            PlaceSettings st = new PlaceSettings();
            st.rotation = rotation;
            st.mirror = mirror;
            st.pivotX = pivX; st.pivotY = 0; st.pivotZ = pivZ;
            st.processors.add(props.airPocket ? Processors.BlockIgnore.structureBlock() : Processors.BlockIgnore.structureAndAir());
            List<Processors.Rule> rules = new ArrayList<>();
            rules.add(rule(e, "minecraft:gold_block", 0.3F, "minecraft:air"));
            if (vp == VP.ON_OCEAN_FLOOR) rules.add(rule(e, "minecraft:lava", "minecraft:magma_block"));
            else rules.add(props.cold ? rule(e, "minecraft:lava", "minecraft:netherrack") : rule(e, "minecraft:lava", 0.2F, "minecraft:magma_block"));
            if (!props.cold) rules.add(rule(e, "minecraft:netherrack", 0.07F, "minecraft:magma_block"));
            st.processors.add(new Processors.RuleProcessor(rules));
            st.processors.add(new Processors.BlockAge(props.mossiness));
            st.processors.add(new Processors.ProtectedBlocks(e.blockTagSet("minecraft:features_cannot_replace")));
            st.processors.add(new Processors.LavaSubmerged());
            if (props.replaceWithBlackstone) st.processors.add(new Processors.BlackstoneReplace());
            return st;
        }

        @Override
        protected void handleDataMarker(String marker, int x, int y, int z, PostCtx c) {}

        @Override
        public void postProcess(PostCtx c) {
            int[] b = template.boundingBox(tx, ty, tz, settings.rotation, settings.pivotX, settings.pivotZ, settings.mirror);
            int cx = b[0] + (b[3] - b[0] + 1) / 2, cy = b[1] + (b[4] - b[1] + 1) / 2, cz = b[2] + (b[5] - b[2] + 1) / 2;
            BoundingBox wb = c.writableBox;
            if (!wb.isInside(cx, cy, cz)) return;
            wb.encapsulate(b[0], b[1], b[2], b[3], b[4], b[5]);
            super.postProcess(c);
            spreadNetherrack(c);
            addNetherrackDripColumnsBelowPortal(c);
            if (props.vines || props.overgrown) {
                BoundingBox bb = boundingBox;
                for (int z = bb.minZ(); z <= bb.maxZ(); z++)
                    for (int y = bb.minY(); y <= bb.maxY(); y++)
                        for (int x = bb.minX(); x <= bb.maxX(); x++) {
                            if (props.vines) maybeAddVines(c, x, y, z);
                            if (props.overgrown) maybeAddLeavesAbove(c, x, y, z);
                        }
            }
        }

        private void maybeAddVines(PostCtx c, int x, int y, int z) {
            int state = c.view.getBlock(x, y, z);
            BlockAttrs a = BlockAttrs.get();
            if (a.isAir(state) || c.table().name(state).equals("minecraft:vine")) return;
            int d = c.random.nextInt(4);   // NORTH, EAST, SOUTH, WEST
            int[][] step = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
            int nx = x + step[d][0], nz = z + step[d][1];
            if (!a.isAir(c.view.getBlock(nx, y, nz))) return;
            // Block.isFaceFull(state.getCollisionShape, d): 충돌 모양의 해당 면이 가득 참
            int[] faceIdx = {BlockAttrs.NORTH, BlockAttrs.EAST, BlockAttrs.SOUTH, BlockAttrs.WEST};
            if (!a.isCollisionFaceFull(state, faceIdx[d])) return;
            String[] opp = {"south", "west", "north", "east"};   // getOpposite
            int vine = c.table().with(c.blocks().defaultStateId("minecraft:vine"), opp[d], true);
            c.view.setBlock(nx, y, nz, vine);
        }

        private void maybeAddLeavesAbove(PostCtx c, int x, int y, int z) {
            if (c.random.nextFloat() < 0.5F && c.table().name(c.view.getBlock(x, y, z)).equals("minecraft:netherrack")
                && BlockAttrs.get().isAir(c.view.getBlock(x, y + 1, z))) {
                int leaves = c.table().with(c.blocks().defaultStateId("minecraft:jungle_leaves"), "persistent", true);
                c.view.setBlock(x, y + 1, z, leaves);
            }
        }

        private void addNetherrackDripColumnsBelowPortal(PostCtx c) {
            for (int x = boundingBox.minX() + 1; x < boundingBox.maxX(); x++)
                for (int z = boundingBox.minZ() + 1; z < boundingBox.maxZ(); z++)
                    if (c.table().name(c.view.getBlock(x, boundingBox.minY(), z)).equals("minecraft:netherrack"))
                        addNetherrackDripColumn(c, x, boundingBox.minY() - 1, z);
        }

        private void addNetherrackDripColumn(PostCtx c, int x, int y, int z) {
            placeNetherrackOrMagma(c, x, y, z);
            int i = 8;
            while (i > 0 && c.random.nextFloat() < 0.5F) {
                y--;
                i--;
                placeNetherrackOrMagma(c, x, y, z);
            }
        }

        private void placeNetherrackOrMagma(PostCtx c, int x, int y, int z) {
            if (!props.cold && c.random.nextFloat() < 0.07F) c.view.setBlock(x, y, z, c.blocks().defaultStateId("minecraft:magma_block"));
            else c.view.setBlock(x, y, z, c.blocks().defaultStateId("minecraft:netherrack"));
        }

        private static final float[] PROBS = {1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.9F, 0.9F, 0.8F, 0.7F, 0.6F, 0.4F, 0.2F};

        private void spreadNetherrack(PostCtx c) {
            boolean flag = vp == VP.ON_LAND_SURFACE || vp == VP.ON_OCEAN_FLOOR;
            int cx = boundingBox.minX() + (boundingBox.maxX() - boundingBox.minX() + 1) / 2;
            int cz = boundingBox.minZ() + (boundingBox.maxZ() - boundingBox.minZ() + 1) / 2;
            int n = PROBS.length;
            int size = ((boundingBox.maxX() - boundingBox.minX() + 1) + (boundingBox.maxZ() - boundingBox.minZ() + 1)) / 2;
            int offset = c.random.nextInt(Math.max(1, 8 - size / 2));
            for (int x = cx - n; x <= cx + n; x++) {
                for (int z = cz - n; z <= cz + n; z++) {
                    int dist = Math.abs(x - cx) + Math.abs(z - cz);
                    int idx = Math.max(0, dist + offset);
                    if (idx < n) {
                        float p = PROBS[idx];
                        if (c.random.nextDouble() < p) {
                            int y = vp == VP.ON_OCEAN_FLOOR ? c.view.getWgOceanFloorHeight(x, z) - 1 : c.view.getWorldSurfaceWgY(x, z);
                            int y2 = flag ? y : Math.min(boundingBox.minY(), y);
                            if (Math.abs(y2 - boundingBox.minY()) <= 3 && canReplace(c, x, y2, z)) {
                                placeNetherrackOrMagma(c, x, y2, z);
                                if (props.overgrown) maybeAddLeavesAbove(c, x, y2, z);
                                addNetherrackDripColumn(c, x, y2 - 1, z);
                            }
                        }
                    }
                }
            }
        }

        private boolean canReplace(PostCtx c, int x, int y, int z) {
            int s = c.view.getBlock(x, y, z);
            String n = c.table().name(s);
            // 바닐라: Blocks.AIR 만 제외(cave_air 는 덮어쓴다 — 네더 동굴 위 포털)
            return !n.equals("minecraft:air") && !n.equals("minecraft:obsidian") && !c.engine.blockTagSet("minecraft:features_cannot_replace").contains(n)
                && (vp == VP.IN_NETHER || !n.equals("minecraft:lava"));
        }
    }
}
