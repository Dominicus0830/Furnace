// 바닐라 SpawnerBlockEntity: BaseSpawner 하나를 감싼다. 다음 스폰 데이터가 바뀌면 블록 갱신(블록 엔티티 데이터를 다시 보낸다),
// 블록 이벤트 1 은 spawner 블록으로. 갱신 태그는 저장 내용에서 SpawnPotentials 를 뺀 것.
package org.domi.furnace.server.block.entity;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public class SpawnerBlockEntity extends BlockEntity {

    private final BaseSpawner spawner = new BaseSpawner() {
        @Override
        public void broadcastEvent(BlockLevel level, Pos p, int type) { level.blockEvent(p, B.id("spawner"), type, 0); }

        @Override
        protected void setNextSpawnData(BlockLevel level, Pos p, SpawnData d) {
            super.setNextSpawnData(level, p, d);
            if (level != null) level.sendBlockUpdated(p);   // sendBlockUpdated(pos, state, state, 4)
        }
    };

    public SpawnerBlockEntity(Pos pos, int state) { super("minecraft:mob_spawner", pos, state); }

    @Override public boolean isValid(int state) { return B.is(state, "spawner"); }

    public BaseSpawner spawner() { return spawner; }

    @Override public boolean ticking() { return true; }

    @Override
    public void tick(BlockLevel level) {
        if (level instanceof ChunkLevel cl) spawner.serverTick(cl, pos);
    }

    @Override
    public boolean triggerEvent(int type, int data) { return spawner.onEventTriggered(type) || super.triggerEvent(type, data); }

    /** setEntityId(type, random): 스폰 알을 생성기에 쓸 때. */
    public void setEntityId(String type, BlockLevel level) {
        spawner.setEntityId(type, level, level.random(), pos);
        setChanged(level);
    }

    @Override public void load(NbtCompound tag) { spawner.load(tag); }

    @Override protected void saveAdditional(NbtCompound tag) { spawner.save(tag); }

    @Override
    public NbtCompound updateTag() {
        NbtCompound t = saveCustomOnly();   // getUpdateTag = saveCustomOnly 에서 SpawnPotentials 를 뺀 것
        t.tags().remove("SpawnPotentials");
        return t;
    }

    @Override public boolean hasUpdatePacket() { return true; }

    public boolean onlyOpCanSetNbt() { return true; }
}
