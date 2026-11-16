// simple_state_provider — 상수 state, random 0소비
package org.domi.furnace.server.world.gen.feature;

import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

@org.domi.furnace.server.VanillaPort("net.minecraft.world.level.levelgen.feature.stateproviders.SimpleStateProvider")
public final class SimpleStateProvider implements StateProvider {

    private final int stateId;

    public SimpleStateProvider(int stateId) {
        this.stateId = stateId;
    }

    @Override
    public int getState(WorldgenRandom random, int x, int y, int z) {
        return stateId;
    }
}
