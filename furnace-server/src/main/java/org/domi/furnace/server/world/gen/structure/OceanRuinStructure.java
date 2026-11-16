// 해저 폐허(cold/warm) — 바닐라 OceanRuinStructure + OceanRuinPieces.
package org.domi.furnace.server.world.gen.structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.domi.furnace.server.world.gen.carver.LegacyRandom;
import org.domi.furnace.server.world.gen.structure.template.BlockStateTable;
import org.domi.furnace.server.world.gen.structure.template.PlaceSettings;
import org.domi.furnace.server.world.gen.structure.template.Processors;
import org.domi.furnace.server.world.gen.structure.template.StructureTemplate;

public final class OceanRuinStructure extends StructureType {

    private final boolean cold;
    private final float largeProbability, clusterProbability;

    public OceanRuinStructure(StructureRegistry.Def def) {
        this.cold = def.json().get("biome_temp").getAsString().equals("cold");
        this.largeProbability = def.json().get("large_probability").getAsFloat();
        this.clusterProbability = def.json().get("cluster_probability").getAsFloat();
    }

    @Override
    public Optional<Stub> findGenerationPoint(GenContext c) {
        return onTopOfChunkCenter(c, TerrainAccess.HM_OCEAN_FLOOR_WG, b -> {
            int rotation = c.random.nextInt(4);
            addPieces(c.engine, c.minBlockX(), 90, c.minBlockZ(), rotation, b, c.random);
        });
    }

    private static String[] names(String prefix, int[] nums) {
        String[] out = new String[nums.length];
        for (int i = 0; i < nums.length; i++) out[i] = "minecraft:underwater_ruin/" + prefix + "_" + nums[i];
        return out;
    }

    private static final int[] N8 = {1, 2, 3, 4, 5, 6, 7, 8};
    private static final int[] NB = {1, 2, 3, 8};
    private static final String[] WARM = names("warm", N8);
    private static final String[] BRICK = names("brick", N8);
    private static final String[] CRACKED = names("cracked", N8);
    private static final String[] MOSSY = names("mossy", N8);
    private static final String[] BIG_BRICK = names("big_brick", NB);
    private static final String[] BIG_MOSSY = names("big_mossy", NB);
    private static final String[] BIG_CRACKED = names("big_cracked", NB);
    private static final String[] BIG_WARM = names("big_warm", new int[]{4, 5, 6, 7});

    private static int nextInt(LegacyRandom r, int min, int max) {
        return min >= max ? min : r.nextInt(max - min + 1) + min;
    }

    private void addPieces(StructureEngine e, int x, int y, int z, int rotation, PiecesBuilder b, LegacyRandom random) {
        boolean large = random.nextFloat() <= largeProbability;
        float integrity = large ? 0.9F : 0.8F;
        addPiece(e, x, y, z, rotation, b, random, large, integrity);
        if (large && random.nextFloat() <= clusterProbability) addCluster(e, random, rotation, x, z, b);
    }

    private void addCluster(StructureEngine e, LegacyRandom random, int rotation, int px, int pz, PiecesBuilder pieces) {
        int[] r1 = StructureTemplate.transform(15, 0, 15, BlockStateTable.NONE, rotation, 0, 0);
        int x0 = px, z0 = pz, x1 = px + r1[0], z1 = pz + r1[2];
        int[] box = {Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1)};   // y 는 90 고정으로 동일하므로 xz 만 비교
        int bx = Math.min(x0, x1), bz = Math.min(z0, z1);
        List<int[]> list = allPositions(random, bx, bz);
        int n = nextInt(random, 4, 8);
        for (int i = 0; i < n; i++) {
            if (list.isEmpty()) continue;
            int j = random.nextInt(list.size());
            int[] p = list.remove(j);
            int rot2 = random.nextInt(4);
            int[] t = StructureTemplate.transform(5, 0, 6, BlockStateTable.NONE, rot2, 0, 0);
            int ax1 = p[0] + t[0], az1 = p[1] + t[2];
            int[] box2 = {Math.min(p[0], ax1), Math.min(p[1], az1), Math.max(p[0], ax1), Math.max(p[1], az1)};
            boolean intersects = box2[2] >= box[0] && box2[0] <= box[2] && box2[3] >= box[1] && box2[1] <= box[3];
            if (!intersects) addPiece(e, p[0], 90, p[1], rot2, pieces, random, false, 0.8F);
        }
    }

    private static List<int[]> allPositions(LegacyRandom r, int bx, int bz) {
        List<int[]> l = new ArrayList<>();
        int a, b;
        a = -16 + nextInt(r, 1, 8); b = 16 + nextInt(r, 1, 7); l.add(new int[]{bx + a, bz + b});
        a = -16 + nextInt(r, 1, 8); b = nextInt(r, 1, 7); l.add(new int[]{bx + a, bz + b});
        a = -16 + nextInt(r, 1, 8); b = -16 + nextInt(r, 4, 8); l.add(new int[]{bx + a, bz + b});
        a = nextInt(r, 1, 7); b = 16 + nextInt(r, 1, 7); l.add(new int[]{bx + a, bz + b});
        a = nextInt(r, 1, 7); b = -16 + nextInt(r, 4, 6); l.add(new int[]{bx + a, bz + b});
        a = 16 + nextInt(r, 1, 7); b = 16 + nextInt(r, 3, 8); l.add(new int[]{bx + a, bz + b});
        a = 16 + nextInt(r, 1, 7); b = nextInt(r, 1, 7); l.add(new int[]{bx + a, bz + b});
        a = 16 + nextInt(r, 1, 7); b = -16 + nextInt(r, 4, 8); l.add(new int[]{bx + a, bz + b});
        return l;
    }

    private void addPiece(StructureEngine e, int x, int y, int z, int rotation, PiecesBuilder b, LegacyRandom random, boolean large, float integrity) {
        if (!cold) {
            String[] arr = large ? BIG_WARM : WARM;
            b.addPiece(new Piece(e, arr[random.nextInt(arr.length)], x, y, z, rotation, integrity, false, large));
        } else {
            String[] a1 = large ? BIG_BRICK : BRICK;
            String[] a2 = large ? BIG_CRACKED : CRACKED;
            String[] a3 = large ? BIG_MOSSY : MOSSY;
            int i = random.nextInt(a1.length);
            b.addPiece(new Piece(e, a1[i], x, y, z, rotation, integrity, true, large));
            b.addPiece(new Piece(e, a2[i], x, y, z, rotation, 0.7F, true, large));
            b.addPiece(new Piece(e, a3[i], x, y, z, rotation, 0.5F, true, large));
        }
    }

    static final class Piece extends TemplatePiece {
        @Override
        public String pieceType() { return "orp"; }

        @Override
        protected void saveAdditional(org.domi.furnace.server.net.io.nbt.NbtCompound t) {
            super.saveAdditional(t);
            t.putString("Rot", rotationName(settings.rotation));
            t.putFloat("Integrity", integrity);
            t.putString("BiomeType", cold ? "COLD" : "WARM");
            t.putBoolean("IsLarge", large);
        }

        private final boolean large;
        private final float integrity;
        private final boolean cold;

        Piece(StructureEngine e, String name, int x, int y, int z, int rotation, float integrity, boolean cold, boolean large) {
            super(name, e.template(name), settings(e, rotation, integrity, cold), x, y, z);
            this.large = large;
            this.integrity = integrity;
            this.cold = cold;
        }

        private static PlaceSettings settings(StructureEngine e, int rotation, float integrity, boolean cold) {
            PlaceSettings st = new PlaceSettings();
            st.rotation = rotation;
            st.mirror = BlockStateTable.NONE;
            st.processors.add(new Processors.BlockRot(null, integrity));
            st.processors.add(Processors.BlockIgnore.structureAndAir());
            String from = cold ? "minecraft:gravel" : "minecraft:sand";
            String to = cold ? "minecraft:suspicious_gravel" : "minecraft:suspicious_sand";
            String loot = cold ? "minecraft:archaeology/ocean_ruin_cold" : "minecraft:archaeology/ocean_ruin_warm";
            Processors.Rule rule = new Processors.Rule(Processors.blockMatch(from), Processors.alwaysTrue(), Processors.alwaysTruePos(),
                e.blocks().defaultStateId(to), Processors.appendLoot(loot));
            st.processors.add(new Processors.Capped(new Processors.RuleProcessor(List.of(rule)), 5, 5));
            return st;
        }

        @Override
        protected void handleDataMarker(String marker, int x, int y, int z, PostCtx c) {
            if ("chest".equals(marker)) {
                boolean water = c.engine.hasWaterFluid(c.view.getBlock(x, y, z));
                int chest = c.table().with(c.blocks().defaultStateId("minecraft:chest"), "waterlogged", water);
                c.view.setBlock(x, y, z, chest);
                long seed = c.random.nextLong();
                org.domi.furnace.server.net.io.nbt.NbtCompound nbt = new org.domi.furnace.server.net.io.nbt.NbtCompound();
                nbt.putString("id", "minecraft:chest");
                nbt.putInt("x", x); nbt.putInt("y", y); nbt.putInt("z", z);
                nbt.putString("LootTable", large ? "minecraft:chests/underwater_ruin_big" : "minecraft:chests/underwater_ruin_small");
                nbt.putLong("LootTableSeed", seed);
                c.view.putGeneratedBlockEntity(x, y, z, nbt);
            } else if ("drowned".equals(marker)) {
                c.view.addEntity("minecraft:drowned", x + 0.5, y, z + 0.5, 0.0F, 0.0F);   // moveTo(pos, 0, 0) = 블록 중심
                c.view.setBlock(x, y, z, y > 63 ? 0 : c.blocks().defaultStateId("minecraft:water"));
            }
        }

        @Override
        public void postProcess(PostCtx c) {
            int h = c.view.getWgOceanFloorHeight(tx, tz);
            ty = h;
            int[] rel = StructureTemplate.transform(template.sizeX - 1, 0, template.sizeZ - 1, BlockStateTable.NONE, settings.rotation, 0, 0);
            int cornerX = tx + rel[0], cornerZ = tz + rel[2];
            ty = heightFor(c, tx, ty, tz, cornerX, cornerZ);
            super.postProcess(c);
        }

        private int heightFor(PostCtx c, int px, int py, int pz, int cornerX, int cornerZ) {
            int y = py;
            int minY = 512;
            int refY = y - 1;
            int cnt = 0;
            org.domi.furnace.server.world.BlockAttrs a = org.domi.furnace.server.world.BlockAttrs.get();
            for (int xx = Math.min(px, cornerX); xx <= Math.max(px, cornerX); xx++) {
                for (int zz = Math.min(pz, cornerZ); zz <= Math.max(pz, cornerZ); zz++) {
                    int yy = py - 1;
                    int s = c.view.getBlock(xx, yy, zz);
                    while ((a.isAir(s) || c.engine.hasWaterFluid(s) || isIce(c, s)) && yy > c.view.minY() + 1) {
                        yy--;
                        s = c.view.getBlock(xx, yy, zz);
                    }
                    minY = Math.min(minY, yy);
                    if (yy < refY - 2) cnt++;
                }
            }
            int dx = Math.abs(px - cornerX);
            if (refY - minY > 2 && cnt > dx - 2) y = minY + 1;
            return y;
        }

        private static boolean isIce(PostCtx c, int s) {
            String n = c.table().name(s);
            return n.equals("minecraft:ice") || n.equals("minecraft:packed_ice") || n.equals("minecraft:blue_ice") || n.equals("minecraft:frosted_ice");
        }
    }
}
