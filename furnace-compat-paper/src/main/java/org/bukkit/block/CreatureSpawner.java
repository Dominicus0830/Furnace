package org.bukkit.block;

/** Paper 1.21.1 CreatureSpawner. */
public interface CreatureSpawner extends TileState, org.bukkit.spawner.Spawner {

    @Deprecated
    void setCreatureTypeByName(String creatureType);

    @Deprecated
    String getCreatureTypeName();
}
