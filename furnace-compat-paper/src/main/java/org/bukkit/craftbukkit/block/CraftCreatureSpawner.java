package org.bukkit.craftbukkit.block;

import org.bukkit.entity.EntityType;
import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.api.nbt.NbtList;

/**
 * Paper CraftCreatureSpawner: 생성기 블록 엔티티 NBT(바닐라 BaseSpawner — SpawnData·SpawnPotentials·Delay 등, 값은 short) 사본 위에서 읽고 쓴다.
 * 숫자 값은 int 필드로 들고 update 때 Paper 저장 규칙으로 적는다. 없는 값은 바닐라 BaseSpawner 의 처음 값(놓이지 않은 상태·빈 사본).
 */
public class CraftCreatureSpawner extends CraftBlockEntityState implements org.bukkit.block.CreatureSpawner {

    private int delay;
    private int minSpawnDelay;
    private int maxSpawnDelay;
    private int spawnCount;
    private int maxNearbyEntities;
    private int requiredPlayerRange;
    private int spawnRange;

    /** 값은 Paper 사본 BaseSpawner 처럼 int 로 든다(Paper 읽기 규칙: Paper.* int 키가 있으면 그것). 없는 값은 바닐라 처음 값. */
    public CraftCreatureSpawner(String dimension, org.domi.furnace.api.world.BlockPos pos, org.bukkit.block.data.BlockData data) {
        super(dimension, pos, data);
        NbtCompound n = snapshotNbt();
        delay = n.contains("Paper.Delay") ? n.getInt("Paper.Delay") : n.contains("Delay") ? n.getShort("Delay") : 20;
        if (n.contains("Paper.MinSpawnDelay")) {
            minSpawnDelay = n.getInt("Paper.MinSpawnDelay");
            maxSpawnDelay = n.getInt("Paper.MaxSpawnDelay");
        } else {
            minSpawnDelay = n.contains("MinSpawnDelay") ? n.getInt("MinSpawnDelay") : 200;
            maxSpawnDelay = n.contains("MaxSpawnDelay") ? n.getInt("MaxSpawnDelay") : 800;
        }
        spawnCount = n.contains("SpawnCount") ? n.getShort("SpawnCount") : 4;
        maxNearbyEntities = n.contains("MaxNearbyEntities") ? n.getShort("MaxNearbyEntities") : 6;
        requiredPlayerRange = n.contains("RequiredPlayerRange") ? n.getShort("RequiredPlayerRange") : 16;
        spawnRange = n.contains("SpawnRange") ? n.getShort("SpawnRange") : 4;
    }

    /** Paper 저장 규칙: 지연 셋은 Short.MAX 로 자르고 넘으면 Paper.* int 키도, 나머지는 short 로 접는다. */
    @Override
    protected void applyToSnapshot() {
        super.applyToSnapshot();
        NbtCompound n = snapshotNbt();
        n.remove("Paper.Delay");
        n.remove("Paper.MinSpawnDelay");
        n.remove("Paper.MaxSpawnDelay");
        if (delay > Short.MAX_VALUE) n.putInt("Paper.Delay", delay);
        n.putShort("Delay", (short) Math.min(Short.MAX_VALUE, delay));
        if (minSpawnDelay > Short.MAX_VALUE || maxSpawnDelay > Short.MAX_VALUE) {
            n.putInt("Paper.MinSpawnDelay", minSpawnDelay);
            n.putInt("Paper.MaxSpawnDelay", maxSpawnDelay);
        }
        n.putShort("MinSpawnDelay", (short) Math.min(Short.MAX_VALUE, minSpawnDelay));
        n.putShort("MaxSpawnDelay", (short) Math.min(Short.MAX_VALUE, maxSpawnDelay));
        n.putShort("SpawnCount", (short) spawnCount);
        n.putShort("MaxNearbyEntities", (short) maxNearbyEntities);
        n.putShort("RequiredPlayerRange", (short) requiredPlayerRange);
        n.putShort("SpawnRange", (short) spawnRange);
    }

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

    /** Paper CraftEntityType.bukkitToString: 키 전체("minecraft:zombie"). */
    @Override
    public String getCreatureTypeName() {
        EntityType t = getSpawnedType();
        return t == null ? null : t.getKey().toString();
    }

    /** Paper CraftEntityType.stringToBukkit: null 은 거절, "zombie"·"minecraft:zombie" 모두, 모르면 생성기를 비운다(setSpawnedType(null)). */
    @Override
    public void setCreatureTypeByName(String creatureType) {
        if (creatureType == null) throw new IllegalArgumentException();   // Paper: Preconditions.checkArgument(string != null), 문구 없음
        setSpawnedType(EntityType.fromKey(creatureType.toLowerCase(java.util.Locale.ROOT)));
    }

    @Override
    public int getDelay() { return delay; }

    @Override
    public void setDelay(int delay) { this.delay = delay; }

    @Override
    public int getMinSpawnDelay() { return minSpawnDelay; }

    @Override
    public void setMinSpawnDelay(int spawnDelay) {
        if (spawnDelay > getMaxSpawnDelay()) throw new IllegalArgumentException("Minimum Spawn Delay must be less than or equal to Maximum Spawn Delay");
        minSpawnDelay = spawnDelay;
    }

    @Override
    public int getMaxSpawnDelay() { return maxSpawnDelay; }

    @Override
    public void setMaxSpawnDelay(int spawnDelay) {
        if (spawnDelay <= 0) throw new IllegalArgumentException("Maximum Spawn Delay must be greater than 0.");
        if (spawnDelay < getMinSpawnDelay()) throw new IllegalArgumentException("Maximum Spawn Delay must be greater than or equal to Minimum Spawn Delay");
        maxSpawnDelay = spawnDelay;
    }

    @Override
    public int getMaxNearbyEntities() { return maxNearbyEntities; }

    @Override
    public void setMaxNearbyEntities(int maxNearbyEntities) { this.maxNearbyEntities = maxNearbyEntities; }

    @Override
    public int getSpawnCount() { return spawnCount; }

    @Override
    public void setSpawnCount(int count) { spawnCount = count; }

    @Override
    public int getRequiredPlayerRange() { return requiredPlayerRange; }

    @Override
    public void setRequiredPlayerRange(int requiredPlayerRange) { this.requiredPlayerRange = requiredPlayerRange; }

    @Override
    public int getSpawnRange() { return spawnRange; }

    @Override
    public void setSpawnRange(int spawnRange) { this.spawnRange = spawnRange; }
}
