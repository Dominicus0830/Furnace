// 바닐라 MinecartCommandBlock: 명령 블록 수레(부서지면 빈 수레). 14 명령 문자열, 15 마지막 출력을 동기화한다.
// 켜진 활성화 레일 위에서 4 틱에 한 번 명령을 실행한다(BaseCommandBlock 공유 — 성공 횟수는 감지 레일 비교기 출력).
// 바닐라의 onSyncedDataUpdated 도 그대로 옮겼다: 14 가 (서버에서도) 바뀌면 setCommand 가 불려 성공 횟수가 0 이 된다.
package org.domi.furnace.server.entity;

import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.entity.BaseCommandBlock;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class MinecartCommandBlock extends AbstractMinecart {

    public static final int DATA_ID_COMMAND_NAME = 14, DATA_ID_LAST_OUTPUT = 15;
    /** 활성화 레일 실행 간격(틱). */
    private static final int ACTIVATION_DELAY = 4;

    private int lastActivated;

    private final BaseCommandBlock logic = new BaseCommandBlock() {
        @Override protected Pos blockPos() { return new Pos((int) Math.floor(x()), (int) Math.floor(y()), (int) Math.floor(z())); }

        @Override protected boolean isValid() { return !isRemoved(); }

        /** onUpdated(): 엔티티 데이터 14·15 를 갱신 — 엔티티를 가진 region 에서만 만진다. */
        @Override
        protected void onUpdated(BlockLevel level) {
            if (level instanceof ChunkLevel c && c.entities().contains(MinecartCommandBlock.this)) syncData();
        }

        @Override public double x() { return MinecartCommandBlock.this.x(); }

        @Override public double y() { return MinecartCommandBlock.this.y(); }

        @Override public double z() { return MinecartCommandBlock.this.z(); }

        @Override public float xRot() { return MinecartCommandBlock.this.xRot(); }

        @Override public float yRot() { return MinecartCommandBlock.this.yRot(); }

        @Override public Rider entity() { return MinecartCommandBlock.this; }

        /** 표시 이름은 엔티티 것(S_)이고, textName 은 명령 블록 이름("@" 또는 사용자 지정). */
        @Override public Text displayName() { return MinecartCommandBlock.this.displayName(); }
    };

    public MinecartCommandBlock(ChunkLevel level, int id) { super(EntityType.COMMAND_BLOCK_MINECART, level, id); }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_ID_COMMAND_NAME, SetEntityData.SERIALIZER_STRING, "");
        entityData.define(DATA_ID_LAST_OUTPUT, SetEntityData.SERIALIZER_COMPONENT, Text.empty());
    }

    @Override public Type getMinecartType() { return Type.COMMAND_BLOCK; }

    @Override protected String dropItem() { return "minecraft:minecart"; }

    @Override public int getDefaultDisplayBlockState() { return B.defaultState("command_block"); }

    public BaseCommandBlock commandBlock() { return logic; }

    public String command() { return logic.command(); }

    /** BaseCommandBlock.getSuccessCount(감지 레일 비교기 출력). */
    public int successCount() { return logic.successCount(); }

    /**
     * 데이터 14·15 에 쓰기 + onSyncedDataUpdated: 값이 바뀌면 14 는 setCommand(성공 횟수 0), 15 는 setLastOutput 이 불린다.
     * 엔티티를 가진 region 스레드에서만.
     */
    private void syncData() {
        String cmd = logic.command();
        if (!cmd.equals(entityData.get(DATA_ID_COMMAND_NAME))) {
            entityData.set(DATA_ID_COMMAND_NAME, cmd);
            logic.setCommand(cmd);
        }
        Text out = logic.lastOutput();
        if (!out.equals(entityData.get(DATA_ID_LAST_OUTPUT))) {
            entityData.set(DATA_ID_LAST_OUTPUT, out);
            logic.setLastOutput(out);
        }
    }

    /** set_command_minecart 적용(소유 region 스레드): 명령·출력 기록을 바꾸고 데이터를 동기화. */
    public void applyEdit(String command, boolean trackOutput) {
        logic.setCommand(command);
        logic.setTrackOutput(trackOutput);
        if (!trackOutput) logic.setLastOutput(null);
        syncData();
    }

    /** activateMinecart: 켜진 활성화 레일 위에서 마지막 실행 후 4 틱이 지났으면 명령 실행. */
    @Override
    public void activateMinecart(int x, int y, int z, boolean powered) {
        if (powered && tickCount - lastActivated >= ACTIVATION_DELAY) {
            logic.performCommand(level(), lv -> {});
            lastActivated = tickCount;
        }
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        logic.save(tag);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        logic.load(tag);
        syncData();
    }
}
