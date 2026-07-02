// 바닐라 RandomSpreadStructurePlacement byte-exact — region 시드 기반 구조물 후보 청크 판정.
package org.domi.furnace.server.world.gen.structure;

import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public record RandomSpreadStructurePlacement(
    int spacing,
    int separation,
    RandomSpreadType spreadType,
    int salt,
    FrequencyReductionMethod frequencyReductionMethod,
    float frequency
) {
    public RandomSpreadStructurePlacement {
        if (spacing <= separation) {
            throw new IllegalArgumentException("Spacing must be greater than separation");
        }
    }

    public ChunkPos getPotentialStructureChunk(long seed, int chunkX, int chunkZ) {
        int regionX = Math.floorDiv(chunkX, spacing);
        int regionZ = Math.floorDiv(chunkZ, spacing);
        WorldgenRandom random = new WorldgenRandom();
        random.setLargeFeatureWithSalt(seed, regionX, regionZ, salt);
        int spread = spacing - separation;
        int offsetX = spreadType.evaluate(random, spread);
        int offsetZ = spreadType.evaluate(random, spread);
        return new ChunkPos(regionX * spacing + offsetX, regionZ * spacing + offsetZ);
    }

    public boolean isPlacementChunk(long seed, int chunkX, int chunkZ) {
        ChunkPos candidate = getPotentialStructureChunk(seed, chunkX, chunkZ);
        return candidate.x() == chunkX && candidate.z() == chunkZ;
    }

    public boolean applyAdditionalChunkRestrictions(long seed, int chunkX, int chunkZ) {
        return frequency >= 1.0F || frequencyReductionMethod.shouldGenerate(seed, salt, chunkX, chunkZ, frequency);
    }

    public boolean isStructureChunk(long seed, int chunkX, int chunkZ) {
        return isPlacementChunk(seed, chunkX, chunkZ)
            && applyAdditionalChunkRestrictions(seed, chunkX, chunkZ);
    }
}
