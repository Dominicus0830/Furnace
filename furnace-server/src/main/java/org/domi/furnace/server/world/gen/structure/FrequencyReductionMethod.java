// 바닐라 frequency reducer 4종 — frequency<1 구조물 세트의 생성 확률 판정.
package org.domi.furnace.server.world.gen.structure;

import org.domi.furnace.server.world.gen.carver.LegacyRandom;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public enum FrequencyReductionMethod {
    DEFAULT {
        @Override
        public boolean shouldGenerate(long levelSeed, int salt, int chunkX, int chunkZ, float probability) {
            WorldgenRandom random = new WorldgenRandom();
            random.setLargeFeatureWithSalt(levelSeed, chunkX, chunkZ, salt);
            return random.nextFloat() < probability;
        }
    },
    LEGACY_TYPE_1 {
        @Override
        public boolean shouldGenerate(long levelSeed, int salt, int chunkX, int chunkZ, float probability) {
            int regionX = chunkX >> 4;
            int regionZ = chunkZ >> 4;
            LegacyRandom random = new LegacyRandom(regionX ^ ((long) regionZ << 4) ^ levelSeed);
            random.nextInt();
            return random.nextInt((int) (1.0F / probability)) == 0;
        }
    },
    LEGACY_TYPE_2 {
        @Override
        public boolean shouldGenerate(long levelSeed, int salt, int chunkX, int chunkZ, float probability) {
            WorldgenRandom random = new WorldgenRandom();
            random.setLargeFeatureWithSalt(levelSeed, chunkX, chunkZ, 10387320);
            return random.nextFloat() < probability;
        }
    },
    LEGACY_TYPE_3 {
        @Override
        public boolean shouldGenerate(long levelSeed, int salt, int chunkX, int chunkZ, float probability) {
            WorldgenRandom random = new WorldgenRandom();
            random.setLargeFeatureSeed(levelSeed, chunkX, chunkZ);
            return random.nextDouble() < probability;
        }
    };

    public abstract boolean shouldGenerate(long levelSeed, int salt, int chunkX, int chunkZ, float probability);

    public static FrequencyReductionMethod fromSerializedName(String name) {
        return switch (name) {
            case "default" -> DEFAULT;
            case "legacy_type_1" -> LEGACY_TYPE_1;
            case "legacy_type_2" -> LEGACY_TYPE_2;
            case "legacy_type_3" -> LEGACY_TYPE_3;
            default -> throw new IllegalArgumentException("Unsupported frequency reduction method: " + name);
        };
    }
}
