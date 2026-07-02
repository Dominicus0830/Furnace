package org.domi.furnace.server.world.gen.pipeline;

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
