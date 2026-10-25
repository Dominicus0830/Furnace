// 바닐라 Interaction: 크기(width, height)만 가진 보이지 않는 상호작용 상자. 마지막으로 때린/우클릭한 플레이어와 시각을 NBT 로 가진다.
// TODO(플레이어 입력): 플레이어의 공격·우클릭이 이 엔티티에 attack/interaction 기록을 남기도록 연결(Entity 쪽 플레이어 경로 필요).
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class Interaction extends Entity {
    public static final int DATA_WIDTH = 8, DATA_HEIGHT = 9, DATA_RESPONSE = 10;
    private int[] attackPlayer, interactionPlayer;
    private long attackTime, interactionTime;

    public Interaction(ChunkLevel level, int id) {
        super(EntityType.INTERACTION, level, id);
        noPhysics = true;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_WIDTH, SetEntityData.SERIALIZER_FLOAT, 1.0F);
        entityData.define(DATA_HEIGHT, SetEntityData.SERIALIZER_FLOAT, 1.0F);
        entityData.define(DATA_RESPONSE, SetEntityData.SERIALIZER_BOOLEAN, false);
    }

    private void updateSize() { setDimensions((Float) entityData.get(DATA_WIDTH), (Float) entityData.get(DATA_HEIGHT), dimEye); }

    public void setWidth(float w) { entityData.set(DATA_WIDTH, w); updateSize(); }

    public void setHeight(float h) { entityData.set(DATA_HEIGHT, h); updateSize(); }

    public void setResponse(boolean r) { entityData.set(DATA_RESPONSE, r); }

    public boolean response() { return (Boolean) entityData.get(DATA_RESPONSE); }

    @Override public void tick() {}

    @Override public boolean isPickable() { return true; }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        tag.putFloat("width", (Float) entityData.get(DATA_WIDTH));
        tag.putFloat("height", (Float) entityData.get(DATA_HEIGHT));
        if (attackPlayer != null) tag.putCompound("attack", record(attackPlayer, attackTime));
        if (interactionPlayer != null) tag.putCompound("interaction", record(interactionPlayer, interactionTime));
        tag.putBoolean("response", response());
    }

    private static NbtCompound record(int[] player, long time) {
        NbtCompound c = new NbtCompound();
        c.putIntArray("player", player);
        c.putLong("timestamp", time);
        return c;
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        if (tag.contains("width")) entityData.set(DATA_WIDTH, tag.getFloat("width"));
        if (tag.contains("height")) entityData.set(DATA_HEIGHT, tag.getFloat("height"));
        attackPlayer = null;
        interactionPlayer = null;
        if (tag.tags().get("attack") instanceof NbtCompound c && c.tags().get("player") instanceof int[] p) {
            attackPlayer = p;
            attackTime = c.getLong("timestamp");
        }
        if (tag.tags().get("interaction") instanceof NbtCompound c && c.tags().get("player") instanceof int[] p) {
            interactionPlayer = p;
            interactionTime = c.getLong("timestamp");
        }
        entityData.set(DATA_RESPONSE, tag.getBoolean("response"));
        updateSize();
    }
}
