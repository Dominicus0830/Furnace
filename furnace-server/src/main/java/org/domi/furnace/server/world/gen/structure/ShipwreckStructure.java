// 난파선 / 해변 난파선 — 바닐라 ShipwreckStructure + ShipwreckPieces.
package org.domi.furnace.server.world.gen.structure;

import java.util.Map;
import java.util.Optional;
import org.domi.furnace.server.world.gen.carver.LegacyRandom;
import org.domi.furnace.server.world.gen.structure.template.BlockStateTable;
import org.domi.furnace.server.world.gen.structure.template.PlaceSettings;
import org.domi.furnace.server.world.gen.structure.template.Processors;
import org.domi.furnace.server.world.gen.structure.template.StructureTemplate;

public final class ShipwreckStructure extends StructureType {

    private static final String[] BEACHED = {
        "with_mast", "sideways_full", "sideways_fronthalf", "sideways_backhalf", "rightsideup_full", "rightsideup_fronthalf",
        "rightsideup_backhalf", "with_mast_degraded", "rightsideup_full_degraded", "rightsideup_fronthalf_degraded",
        "rightsideup_backhalf_degraded"};
    private static final String[] OCEAN = {
        "with_mast", "upsidedown_full", "upsidedown_fronthalf", "upsidedown_backhalf", "sideways_full", "sideways_fronthalf",
        "sideways_backhalf", "rightsideup_full", "rightsideup_fronthalf", "rightsideup_backhalf", "with_mast_degraded",
        "upsidedown_full_degraded", "upsidedown_fronthalf_degraded", "upsidedown_backhalf_degraded", "sideways_full_degraded",
        "sideways_fronthalf_degraded", "sideways_backhalf_degraded", "rightsideup_full_degraded", "rightsideup_fronthalf_degraded",
        "rightsideup_backhalf_degraded"};
    private static final Map<String, String> LOOT = Map.of(
        "map_chest", "minecraft:chests/shipwreck_map", "treasure_chest", "minecraft:chests/shipwreck_treasure",
        "supply_chest", "minecraft:chests/shipwreck_supply");

    private final boolean beached;

    public ShipwreckStructure(StructureRegistry.Def def) {
        this.beached = def.json().get("is_beached").getAsBoolean();
    }

    @Override
    public Optional<Stub> findGenerationPoint(GenContext c) {
        int hm = beached ? TerrainAccess.HM_WORLD_SURFACE_WG : TerrainAccess.HM_OCEAN_FLOOR_WG;
        return onTopOfChunkCenter(c, hm, b -> {
            int rotation = c.random.nextInt(4);
            String[] arr = beached ? BEACHED : OCEAN;
            String name = "minecraft:shipwreck/" + arr[c.random.nextInt(arr.length)];
            b.addPiece(new Piece(c.engine, name, c.minBlockX(), 90, c.minBlockZ(), rotation, beached));
        });
    }

    static final class Piece extends TemplatePiece {
        @Override
        public String pieceType() { return "shipwreck"; }

        @Override
        protected void saveAdditional(org.domi.furnace.server.net.io.nbt.NbtCompound t) {
            super.saveAdditional(t);
            t.putBoolean("isBeached", beached);
            t.putString("Rot", rotationName(settings.rotation));
        }

        private final boolean beached;

        Piece(StructureEngine e, String name, int x, int y, int z, int rotation, boolean beached) {
            super(name, e.template(name), settings(rotation), x, y, z);
            this.beached = beached;
        }

        private static PlaceSettings settings(int rotation) {
            PlaceSettings st = new PlaceSettings();
            st.rotation = rotation;
            st.mirror = BlockStateTable.NONE;
            st.pivotX = 4; st.pivotY = 0; st.pivotZ = 15;
            st.processors.add(Processors.BlockIgnore.structureAndAir());
            return st;
        }

        @Override
        protected void handleDataMarker(String marker, int x, int y, int z, PostCtx c) {
            String table = LOOT.get(marker);
            if (table != null) setChestLoot(c, x, y - 1, z, table);
        }

        @Override
        public void postProcess(PostCtx c) {
            String hm = beached ? "world_surface_wg" : "ocean_floor_wg";
            int minH = 320;   // level.getMaxBuildHeight()
            int sum = 0;
            int area = template.sizeX * template.sizeZ;
            int h;
            if (area == 0) {
                h = c.processorCtx().heightFirstFree(hm, tx, tz);
            } else {
                for (int xx = tx; xx <= tx + template.sizeX - 1; xx++) {
                    for (int zz = tz; zz <= tz + template.sizeZ - 1; zz++) {
                        int hh = c.processorCtx().heightFirstFree(hm, xx, zz);
                        sum += hh;
                        minH = Math.min(minH, hh);
                    }
                }
                h = sum / area;
            }
            int y = beached ? minH - template.sizeY / 2 - c.random.nextInt(3) : h;
            ty = y;
            super.postProcess(c);
        }
    }
}
