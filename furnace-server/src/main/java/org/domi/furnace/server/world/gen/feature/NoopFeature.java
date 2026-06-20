// 미구현 feature 자리표시자 — index parity 유지를 위해 목록엔 남되 생성은 하지 않는다.
package org.domi.furnace.server.world.gen.feature;

import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class NoopFeature implements ConfiguredFeature {

    public static final NoopFeature INSTANCE = new NoopFeature();

    private NoopFeature() {}

    @Override
    public boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        return false;
    }
}
