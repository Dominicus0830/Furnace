// simple_block feature — 지정 state를 생존 가능한 지면 위에 1칸 식재
package org.domi.furnace.server.world.gen.feature;

import java.util.Set;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class SimpleBlockFeature implements ConfiguredFeature {

    private final int stateId;
    private final Set<Integer> validGround;

    public SimpleBlockFeature(int stateId, Set<Integer> validGround) {
        this.stateId = stateId;
        this.validGround = Set.copyOf(validGround);
    }

    @Override
    public boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        // simple_state_provider는 random 0소비. weighted/randomized provider는 미지원(추후 슬라이스).
        if (!validGround.contains(view.getBlock(x, y - 1, z))) {
            return false;
        }
        view.setBlock(x, y, z, stateId);
        return true;
    }
}
