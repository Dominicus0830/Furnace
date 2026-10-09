// 바닐라 LegacyRandomSource.LegacyPositionalRandomFactory — at: Mth.getSeed(x,y,z)^seed, fromHashOf: String.hashCode()^seed.
package org.domi.furnace.server.world.gen.noise;

import org.domi.furnace.server.world.gen.MathHelper;
import org.domi.furnace.server.world.gen.carver.LegacyRandom;

public record LegacyPositionalRandom(long seed) implements PositionalRandom {
    @Override
    public LegacyRandom at(int x, int y, int z) { return new LegacyRandom(MathHelper.getSeed(x, y, z) ^ seed); }

    @Override
    public LegacyRandom fromHashOf(String name) { return new LegacyRandom((long) name.hashCode() ^ seed); }

    @Override
    public LegacyRandom fromSeed(long s) { return new LegacyRandom(s); }
}
