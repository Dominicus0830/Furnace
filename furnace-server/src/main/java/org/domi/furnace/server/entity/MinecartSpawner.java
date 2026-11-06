// 바닐라 MinecartSpawner: 스포너를 실은 수레(부서지면 빈 수레를 떨어뜨린다).
// TODO(스포너): BaseSpawner 틱(근처 플레이어·지연·소환) — Furnace 에 스포너 로직이 아직 없어 움직이지 않는다. 스포너 저장 값은 그대로 보존한다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public class MinecartSpawner extends AbstractMinecart {

    /** BaseSpawner.save 가 쓰는 키. */
    private static final String[] SPAWNER_KEYS = {"Delay", "MinSpawnDelay", "MaxSpawnDelay", "SpawnCount", "MaxNearbyEntities", "RequiredPlayerRange",
            "SpawnRange", "SpawnData", "SpawnPotentials"};

    private final NbtCompound spawner = new NbtCompound();

    public MinecartSpawner(ChunkLevel level, int id) { super(EntityType.SPAWNER_MINECART, level, id); }

    @Override public Type getMinecartType() { return Type.SPAWNER; }

    @Override protected String dropItem() { return "minecraft:minecart"; }

    @Override public int getDefaultDisplayBlockState() { return B.defaultState("spawner"); }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        for (var e : spawner.tags().entrySet()) tag.put(e.getKey(), e.getValue());
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        spawner.tags().clear();
        for (String k : SPAWNER_KEYS) if (tag.contains(k)) spawner.put(k, tag.tags().get(k));
    }
}
