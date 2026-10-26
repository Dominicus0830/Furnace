// 바닐라 MinecartCommandBlock: 명령 블록 수레(부서지면 빈 수레). 14 명령 문자열, 15 마지막 출력을 동기화한다.
// TODO(명령 블록): 켜진 활성화 레일에서 4 틱마다 명령 실행·성공 수, 우클릭 편집 화면. 지금은 저장 값만 보존한다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class MinecartCommandBlock extends AbstractMinecart {

    public static final int DATA_ID_COMMAND_NAME = 14, DATA_ID_LAST_OUTPUT = 15;
    private static final String[] KEYS = {"Command", "SuccessCount", "CustomName", "TrackOutput", "LastOutput", "UpdateLastExecution", "LastExecution"};

    private final NbtCompound block = new NbtCompound();

    public MinecartCommandBlock(ChunkLevel level, int id) { super(EntityType.COMMAND_BLOCK_MINECART, level, id); }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_ID_COMMAND_NAME, SetEntityData.SERIALIZER_STRING, "");
        entityData.define(DATA_ID_LAST_OUTPUT, SetEntityData.SERIALIZER_COMPONENT, org.domi.furnace.api.text.Text.empty());
    }

    @Override public Type getMinecartType() { return Type.COMMAND_BLOCK; }

    @Override protected String dropItem() { return "minecraft:minecart"; }

    @Override public int getDefaultDisplayBlockState() { return B.defaultState("command_block"); }

    public String command() { return block.tags().get("Command") instanceof String s ? s : ""; }

    /** BaseCommandBlock.getSuccessCount(감지 레일 비교기 출력). */
    public int successCount() { return block.getIntOr0("SuccessCount"); }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Command", command());
        tag.putInt("SuccessCount", successCount());
        tag.putBoolean("TrackOutput", !block.contains("TrackOutput") || block.getBoolean("TrackOutput"));
        tag.putBoolean("UpdateLastExecution", !block.contains("UpdateLastExecution") || block.getBoolean("UpdateLastExecution"));
        for (String k : new String[] {"CustomName", "LastOutput", "LastExecution"}) if (block.contains(k)) tag.put(k, block.tags().get(k));
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        block.tags().clear();
        for (String k : KEYS) if (tag.contains(k)) block.put(k, tag.tags().get(k));
        entityData.set(DATA_ID_COMMAND_NAME, command());   // TODO(텍스트): LastOutput 을 데이터 15 로
    }
}
