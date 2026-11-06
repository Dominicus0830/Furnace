// 바닐라 MobSpawnType: 몹이 만들어진 까닭 — 자연 스폰 검사가 갈린다.
package org.domi.furnace.server.entity.spawn;

public enum SpawnReason {
    NATURAL, CHUNK_GENERATION, SPAWNER, STRUCTURE, BREEDING, MOB_SUMMONED, JOCKEY, EVENT, CONVERSION, REINFORCEMENT, TRIGGERED, BUCKET, SPAWN_EGG, COMMAND,
    DISPENSER, PATROL, TRIAL_SPAWNER;

    /** MobSpawnType.isSpawner. */
    public boolean isSpawner() { return this == SPAWNER || this == TRIAL_SPAWNER; }

    /** MobSpawnType.ignoresLightRequirements. */
    public boolean ignoresLight() { return this == TRIAL_SPAWNER; }
}
