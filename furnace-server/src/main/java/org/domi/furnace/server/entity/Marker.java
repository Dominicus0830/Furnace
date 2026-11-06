// 바닐라 Marker: 크기 없는 보이지 않는 엔티티. 데이터 팩이 쓰는 "data" 태그만 가지고, 클라이언트에는 보내지 않는다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public class Marker extends Entity {

    /** getPistonPushReaction = IGNORE. */
    @Override
    public boolean ignoresPistonPush() { return true; }

    private NbtCompound data = new NbtCompound();

    public Marker(ChunkLevel level, int id) {
        super(EntityType.MARKER, level, id);
        noPhysics = true;
    }

    @Override public void tick() {}

    @Override public boolean broadcastToPlayer(Player p) { return false; }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) { tag.putCompound("data", data.copy()); }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        data = tag.tags().get("data") instanceof NbtCompound c ? c : new NbtCompound();
    }
}
