package org.domi.furnace.server.world.gen;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.ChunkSection;
import org.domi.furnace.server.world.SpawnPoint;
import org.junit.jupiter.api.Test;

class WorldSpawnTest {

    @Test
    void defaultSpawn_isDeterministicAndSafe() {
        BlockRegistry blocks = BlockRegistry.load("blocks/1.21.1.txt");
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");

        NoiseWorldSource a = NoiseWorldSource.overworld(0L, blocks, registries);
        NoiseWorldSource b = NoiseWorldSource.overworld(0L, blocks, registries);

        SpawnPoint spawn = a.defaultSpawn();
        assertEquals(spawn, b.defaultSpawn());

        ChunkPos cp = ChunkPos.ofBlock(spawn.x(), spawn.z());
        ChunkColumn col = a.generate(cp.x(), cp.z());
        int lx = spawn.x() & 15;
        int lz = spawn.z() & 15;
        assertNotEquals(ChunkSection.AIR, col.getBlock(lx, spawn.y() - 1, lz));
        assertEquals(ChunkSection.AIR, col.getBlock(lx, spawn.y(), lz));
        assertEquals(ChunkSection.AIR, col.getBlock(lx, spawn.y() + 1, lz));
    }
}
