// 바닐라 UniformInt: [min, max] 균등 정수 분포(sample 은 min + nextInt(max - min + 1)).
package org.domi.furnace.server.entity.ai.behavior;

import org.domi.furnace.server.block.RandomSource;

public record UniformInt(int min, int max) {
    public static UniformInt of(int min, int max) { return new UniformInt(min, max); }

    public int sample(RandomSource r) { return min + r.nextInt(max - min + 1); }
}
