// simple_block feature — provider가 고른 state를 생존 가능한 지면 위에 1칸 식재
package org.domi.furnace.server.world.gen.feature;

import java.util.Set;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class SimpleBlockFeature implements ConfiguredFeature {

    private final StateProvider provider;
    private final Set<Integer> validGround;

    public SimpleBlockFeature(StateProvider provider, Set<Integer> validGround) {
        this.provider = provider;
        this.validGround = Set.copyOf(validGround);
    }

    /** slice1 호환 — 상수 state. 기존 테스트·호출부 0수정 유지. */
    public SimpleBlockFeature(int stateId, Set<Integer> validGround) {
        this(new SimpleStateProvider(stateId), validGround);
    }

    @Override
    public boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        // 바닐라 SimpleBlockFeature.place: getState(random,pos)를 먼저 호출(provider별 random 소비) → 생존 판정 → 식재.
        // simple_state_provider는 0소비라 slice1과 동일. noise_threshold provider만 소비순서가 결정적.
        int stateId = provider.getState(random, x, y, z);
        if (!validGround.contains(view.getBlock(x, y - 1, z))) {
            return false;
        }
        view.setBlock(x, y, z, stateId);
        return true;
    }
}
