package org.bukkit.craftbukkit.block;

import org.bukkit.entity.EntityType;
import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.api.nbt.NbtList;

/**
 * Paper CraftCreatureSpawner: 생성기 블록 엔티티 NBT(바닐라 BaseSpawner — SpawnData·SpawnPotentials·Delay 등, 값은 short) 사본 위에서 읽고 쓴다.
 * 없는 값은 바닐라 BaseSpawner 의 처음 값(놓이지 않은 상태·빈 사본).
 */
public class CraftCreatureSpawner extends CraftBlockEntityState implements org.bukkit.block.CreatureSpawner {

    public CraftCreatureSpawner(String dimension, org.domi.furnace.api.world.BlockPos pos, org.bukkit.block.data.BlockData data) {
        super(dimension, pos, data);
    }

    private int read(String key, int vanillaDefault) {
        return snapshotNbt().contains(key) ? snapshotNbt().getShort(key) : vanillaDefault;
    }

    private void write(String key, int value) { snapshotNbt().putShort(key, (short) value); }

    /** nextSpawnData 의 entity 칸(없으면 null). */
    private NbtCompound spawnEntity() {
        return snapshotNbt().get("SpawnData") instanceof NbtCompound sd ? sd.getCompound("entity") : null;
    }

    @Override
    public EntityType getSpawnedType() {
        NbtCompound e = spawnEntity();
        if (e == null || !e.contains("id")) return null;
        return EntityType.fromKey(e.getString("id"));
    }

    /**
     * Paper: null 이면 후보 목록을 비우고 빈 SpawnData. 아니면 바닐라 setEntityId — 다음 SpawnData(없으면 후보에서 고른 것, 그것도 없으면 빈 것)의
     * entity.id 만 바꾼다(후보 목록은 그대로).
     */
    @Override
    public void setSpawnedType(EntityType entityType) {
        if (entityType == null) {
            snapshotNbt().putList("SpawnPotentials", new NbtList());
            NbtCompound sd = new NbtCompound();
            sd.putCompound("entity", new NbtCompound());
            snapshotNbt().putCompound("SpawnData", sd);
            return;
        }
        if (entityType == EntityType.UNKNOWN) throw new IllegalArgumentException("Can't spawn EntityType " + entityType + " from mob spawners!");
        NbtCompound sd = snapshotNbt().get("SpawnData") instanceof NbtCompound c ? c : pickPotential();
        NbtCompound entity = sd.get("entity") instanceof NbtCompound e ? e : new NbtCompound();
        entity.putString("id", entityType.getKey().toString());
        sd.putCompound("entity", entity);
        snapshotNbt().putCompound("SpawnData", sd);
    }

    /** 바닐라 getOrCreateNextSpawnData: 후보 목록에서 무게대로 하나(목록이 비면 빈 SpawnData). 바닐라는 월드 난수를 쓴다 — 여기선 따로 굴린다(가정). */
    private NbtCompound pickPotential() {
        NbtList potentials = snapshotNbt().getList("SpawnPotentials");
        int total = 0;
        for (int i = 0; i < potentials.size(); i++) total += Math.max(0, potentials.getCompound(i).getInt("weight"));
        if (total > 0) {
            int roll = java.util.concurrent.ThreadLocalRandom.current().nextInt(total);
            for (int i = 0; i < potentials.size(); i++) {
                NbtCompound p = potentials.getCompound(i);
                roll -= Math.max(0, p.getInt("weight"));
                if (roll < 0) return p.getCompound("data").copy();
            }
        }
        NbtCompound sd = new NbtCompound();
        sd.putCompound("entity", new NbtCompound());
        return sd;
    }

    @Override
    public String getCreatureTypeName() {
        EntityType t = getSpawnedType();
        return t == null ? null : t.getName();
    }

    @Override
    public void setCreatureTypeByName(String creatureType) { setSpawnedType(EntityType.fromName(creatureType)); }

    @Override
    public int getDelay() { return read("Delay", 20); }

    @Override
    public void setDelay(int delay) { write("Delay", delay); }

    @Override
    public int getMinSpawnDelay() { return read("MinSpawnDelay", 200); }

    @Override
    public void setMinSpawnDelay(int spawnDelay) {
        if (spawnDelay > getMaxSpawnDelay()) throw new IllegalArgumentException("Minimum Spawn Delay must be less than or equal to Maximum Spawn Delay");
        write("MinSpawnDelay", spawnDelay);
    }

    @Override
    public int getMaxSpawnDelay() { return read("MaxSpawnDelay", 800); }

    @Override
    public void setMaxSpawnDelay(int spawnDelay) {
        if (spawnDelay <= 0) throw new IllegalArgumentException("Maximum Spawn Delay must be greater than 0.");
        if (spawnDelay < getMinSpawnDelay()) throw new IllegalArgumentException("Maximum Spawn Delay must be greater than or equal to Minimum Spawn Delay");
        write("MaxSpawnDelay", spawnDelay);
    }

    @Override
    public int getMaxNearbyEntities() { return read("MaxNearbyEntities", 6); }

    @Override
    public void setMaxNearbyEntities(int maxNearbyEntities) { write("MaxNearbyEntities", maxNearbyEntities); }

    @Override
    public int getSpawnCount() { return read("SpawnCount", 4); }

    @Override
    public void setSpawnCount(int count) { write("SpawnCount", count); }

    @Override
    public int getRequiredPlayerRange() { return read("RequiredPlayerRange", 16); }

    @Override
    public void setRequiredPlayerRange(int requiredPlayerRange) { write("RequiredPlayerRange", requiredPlayerRange); }

    @Override
    public int getSpawnRange() { return read("SpawnRange", 4); }

    @Override
    public void setSpawnRange(int spawnRange) { write("SpawnRange", spawnRange); }
}
