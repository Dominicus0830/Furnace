// 바닐라 PositionalRandomFactory — 위치/이름에서 독립 난수를 파생한다.
package org.domi.furnace.server.world.gen.noise;

@org.domi.furnace.server.VanillaPort("net.minecraft.world.level.levelgen.PositionalRandomFactory")
public interface PositionalRandom {
    NoiseRandom at(int x, int y, int z);

    NoiseRandom fromHashOf(String name);

    NoiseRandom fromSeed(long seed);
}
