package org.domi.furnace.server.world.gen.feature;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import com.google.gson.JsonParser;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.junit.jupiter.api.Test;

class PlacementModifierTest {

    @Test
    void countPlacement_generatesNPositions() {
        PlacementModifier count = new PlacementModifier.CountPlacement(3);
        WorldgenRandom random = new WorldgenRandom();
        random.setSeed(42L);

        List<BlockPos> positions = count.filter(null, random, new BlockPos(0, 64, 0)).toList();

        assertEquals(List.of(
            new BlockPos(0, 64, 0),
            new BlockPos(0, 64, 0),
            new BlockPos(0, 64, 0)
        ), positions);
    }

    @Test
    void inSquarePlacement_offsetsWithinChunk() {
        PlacementModifier inSquare = new PlacementModifier.InSquarePlacement();
        WorldgenRandom random = new WorldgenRandom();
        random.setSeed(42L);

        BlockPos pos = inSquare.filter(null, random, new BlockPos(32, 10, -16))
            .findFirst()
            .orElseThrow();

        assertTrue(pos.x() >= 32 && pos.x() < 48);
        assertEquals(10, pos.y());
        assertTrue(pos.z() >= -16 && pos.z() < 0);
    }

    @Test
    void worldgenYOrDefault_keepsOutOfBoundsAnchorsForSampling() {
        assertEquals(ChunkColumn.MIN_Y - 80,
            PlacementModifier.worldgenYOrDefault(JsonParser.parseString("{\"above_bottom\":-80}"), 0, -64, 384));
        assertEquals(ChunkColumn.MIN_Y + ChunkColumn.SECTION_COUNT * 16 - 1 + 10,
            PlacementModifier.worldgenYOrDefault(JsonParser.parseString("{\"below_top\":-10}"), 0, -64, 384));
        assertEquals(-999,
            PlacementModifier.worldgenYOrDefault(JsonParser.parseString("-999"), 0, -64, 384));
        // 네더: WorldGenerationContext(minGenY 0, genDepth 128) — below_top 10 = 117
        assertEquals(117, PlacementModifier.worldgenYOrDefault(JsonParser.parseString("{\"below_top\":10}"), 0, 0, 128));
    }

    @Test
    void heightmapPlacement_usesConfiguredOceanFloorType() {
        ChunkColumn col = new ChunkColumn(0);
        int[] blockIds = new int[ChunkColumn.SECTION_COUNT * 16 * 16 * 16];
        int[] biomeIds = new int[ChunkColumn.SECTION_COUNT * 64];
        int floorY = 4;
        blockIds[(floorY - ChunkColumn.MIN_Y) * 256] = 1;
        blockIds[(floorY + 1 - ChunkColumn.MIN_Y) * 256] = 80;
        blockIds[(floorY + 2 - ChunkColumn.MIN_Y) * 256] = 80;
        col.loadGenerated(blockIds, biomeIds);

        FeatureWorldView view = new FeatureWorldView(col, 0, 0, biomeIds);
        BlockPos pos = new PlacementModifier.HeightmapPlacement(PlacementModifier.HeightmapPlacement.Type.OCEAN_FLOOR_WG)
            .filter(view, new WorldgenRandom(), new BlockPos(0, 0, 0))
            .findFirst()
            .orElseThrow();

        assertEquals(floorY + 1, pos.y());
    }
}
