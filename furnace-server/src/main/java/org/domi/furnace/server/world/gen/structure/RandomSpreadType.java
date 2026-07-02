// 바닐라 RandomSpreadType — region 내 구조물 오프셋 분포(LINEAR/TRIANGULAR).
package org.domi.furnace.server.world.gen.structure;

import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public enum RandomSpreadType {
    LINEAR,
    TRIANGULAR;

    public int evaluate(WorldgenRandom random, int bound) {
        return switch (this) {
            case LINEAR -> random.nextInt(bound);
            case TRIANGULAR -> (random.nextInt(bound) + random.nextInt(bound)) / 2;
        };
    }
}
