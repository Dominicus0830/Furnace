package org.domi.furnace.server.world.gen.pipeline;

@org.domi.furnace.server.VanillaPort("net.minecraft.world.level.chunk.status.ChunkStatus")
enum GenStatus {
    EMPTY,
    BIOMES,
    NOISE,
    SURFACE,
    CARVERS,
    STRUCTURE_STARTS, // M4g-e placeholder — writeRadius=8, currently no-op
    FEATURES,
    FULL
}
