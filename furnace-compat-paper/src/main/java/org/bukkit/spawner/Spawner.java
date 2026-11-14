package org.bukkit.spawner;

/** Paper 1.21.1 Spawner. isActivated·resetTimer·setSpawnedItem(Paper 추가분)은 아직 없다. */
public interface Spawner extends BaseSpawner {

    @Override
    void setDelay(int delay);

    int getMinSpawnDelay();

    void setMinSpawnDelay(int delay);

    int getMaxSpawnDelay();

    void setMaxSpawnDelay(int delay);

    int getSpawnCount();

    void setSpawnCount(int spawnCount);

    int getMaxNearbyEntities();

    void setMaxNearbyEntities(int maxNearbyEntities);
}
