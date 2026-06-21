// simple_block to_place provider 추상화 — getState의 random 소비순서가 vanilla와 정확히 일치해야 함
package org.domi.furnace.server.world.gen.feature;

import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public interface StateProvider {
    int getState(WorldgenRandom random, int x, int y, int z);
}
