// 바닐라 MinecartSpawner: 스포너를 실은 수레(부서지면 빈 수레를 떨어뜨린다).
// 매 틱 BaseSpawner.serverTick(수레가 있는 칸) — 근처 플레이어·지연·소환은 스포너 블록과 같다. 이벤트 1 은 엔티티 이벤트로 보낸다.
// 저장은 BaseSpawner.save/load 키(Delay, MinSpawnDelay, … SpawnPotentials)를 수레 태그에 그대로 쓴다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public class MinecartSpawner extends AbstractMinecart {
    @Override public boolean onlyOpCanSetNbt() { return true; }


    private final org.domi.furnace.server.block.entity.BaseSpawner spawner = new org.domi.furnace.server.block.entity.BaseSpawner() {
        @Override
        public void broadcastEvent(org.domi.furnace.server.block.BlockLevel l, org.domi.furnace.server.block.Pos p, int type) {
            broadcastEntityEvent((byte) type);
        }
    };

    public MinecartSpawner(ChunkLevel level, int id) { super(EntityType.SPAWNER_MINECART, level, id); }

    @Override public Type getMinecartType() { return Type.SPAWNER; }

    @Override protected String dropItem() { return "minecraft:minecart"; }

    @Override public int getDefaultDisplayBlockState() { return B.defaultState("spawner"); }

    public org.domi.furnace.server.block.entity.BaseSpawner spawner() { return spawner; }

    /** MinecartSpawner.tick: 수레 틱 뒤 스포너 틱(수레가 서 있는 칸 기준). */
    @Override
    public void tick() {
        super.tick();
        if (isAlive()) spawner.serverTick(level, blockPosition());
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        spawner.save(tag);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        spawner.load(tag);
    }
}
