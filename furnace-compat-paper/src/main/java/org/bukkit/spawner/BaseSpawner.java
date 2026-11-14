package org.bukkit.spawner;

import org.bukkit.entity.EntityType;

/** Paper 1.21.1 BaseSpawner. EntitySnapshot·SpawnerEntry 판(getSpawnedEntity·addPotentialSpawn 등)은 아직 없다. */
public interface BaseSpawner {

    EntityType getSpawnedType();

    void setSpawnedType(EntityType creatureType);

    int getDelay();

    void setDelay(int delay);

    int getRequiredPlayerRange();

    void setRequiredPlayerRange(int requiredPlayerRange);

    int getSpawnRange();

    void setSpawnRange(int spawnRange);
}
