// 바닐라 CommandBlockEntity: 명령 블록 3종의 블록 엔티티. 명령 한 줄(BaseCommandBlock) + powered(신호 있음) + conditionMet(조건 충족) + auto(항상 활성).
// 저장 키: Command, SuccessCount, CustomName, TrackOutput, LastOutput, UpdateLastExecution, LastExecution + powered, conditionMet, auto.
// 모드는 칸의 블록에서 나온다: command_block = REDSTONE(임펄스), repeating_command_block = AUTO(반복), chain_command_block = SEQUENCE(체인).
package org.domi.furnace.server.block.entity;

import java.util.Map;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.types.CommandBlock;
import org.domi.furnace.server.item.FlatText;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.entity.Rider;

public class CommandBlockEntity extends BlockEntity {

    /** 패킷 열거 순서(SEQUENCE, AUTO, REDSTONE)와 같다 — 서버 수신 set_command_block 의 모드 번호. */
    public enum Mode { SEQUENCE, AUTO, REDSTONE }

    private static final Prop FACING = B.prop("facing"), CONDITIONAL = B.prop("conditional");
    private static final int COMMAND_BLOCK = B.id("command_block"), REPEATING = B.id("repeating_command_block"), CHAIN = B.id("chain_command_block");

    private boolean powered, conditionMet, auto;

    private final BaseCommandBlock logic = new BaseCommandBlock() {
        @Override protected Pos blockPos() { return pos; }

        @Override protected boolean isValid() { return !isRemoved(); }

        @Override
        protected void onUpdated(BlockLevel level) { level.sendBlockUpdated(pos); }   // f(): sendBlockUpdated(pos, state, state, 3)

        @Override public double x() { return pos.x() + 0.5; }

        @Override public double y() { return pos.y() + 0.5; }

        @Override public double z() { return pos.z() + 0.5; }

        @Override public float xRot() { return 0f; }

        /** Direction.toYRot = (2D 번호 & 3) * 90 (위·아래는 번호 -1 이라 270). */
        @Override public float yRot() { return (Dir.to2D(facing()) & 3) * 90; }

        @Override public Rider entity() { return null; }
    };

    public CommandBlockEntity(Pos pos, int state) { super("minecraft:command_block", pos, state); }

    @Override
    public boolean isValid(int state) {
        int b = B.block(state);
        return b == COMMAND_BLOCK || b == REPEATING || b == CHAIN;
    }

    public BaseCommandBlock logic() { return logic; }

    /** 칸 블록 상태(BE 가 가진 사본)의 방향. */
    private int facing() { return Dir.byName(FACING.valueName(blockState)); }

    public boolean isPowered() { return powered; }

    public void setPowered(boolean b) { powered = b; }

    public boolean isAutomatic() { return auto; }

    /** 로드·생성 때(레벨이 아직 없을 때)의 setAutomatic: 예약 없이 값만. */
    public void setAutomaticNoSchedule(boolean b) { auto = b; }

    /** setAutomatic(auto): 꺼져 있던 항상 활성이 켜지고 신호가 없고 체인이 아니면 한 틱 뒤 실행 예약. */
    public void setAutomatic(boolean b, BlockLevel level) {
        boolean was = auto;
        auto = b;
        if (!was && b && !powered && level != null && mode() != Mode.SEQUENCE) scheduleTick(level);
    }

    /** onModeSwitch: 반복 모드로 바뀌었고 신호나 항상 활성이 있으면 예약. */
    public void onModeSwitch(BlockLevel level) {
        if (mode() == Mode.AUTO && (powered || auto) && level != null) scheduleTick(level);
    }

    private void scheduleTick(BlockLevel level) {
        if (org.domi.furnace.server.block.BlockBehaviors.of(blockState) instanceof CommandBlock cb) {
            markConditionMet(level);
            level.scheduleTick(pos, cb.block(), 1);
        }
    }

    public boolean wasConditionMet() { return conditionMet; }

    /**
     * markConditionMet: 조건부가 아니면 항상 충족. 조건부면 뒤쪽(바라보는 방향의 반대) 칸이 명령 블록이고 그 성공 횟수가 0 보다 커야 충족.
     * 칸의 지금 블록 상태를 레벨에서 읽는다(바닐라와 같이).
     */
    public boolean markConditionMet(BlockLevel level) {
        conditionMet = true;
        if (isConditional(level)) {
            int cur = level.getBlockState(pos);
            Pos behind = pos.relative(Dir.opposite(Dir.byName(FACING.valueName(cur))));
            if (isCommandBlock(level.getBlockState(behind))) {
                conditionMet = level.getBlockEntity(behind) instanceof CommandBlockEntity c && c.logic.successCount() > 0;
            } else {
                conditionMet = false;
            }
        }
        return conditionMet;
    }

    private static boolean isCommandBlock(int state) {
        return org.domi.furnace.server.block.BlockBehaviors.of(state) instanceof CommandBlock;
    }

    /** getMode: 블록 종류에서. */
    public Mode mode() {
        int b = B.block(blockState);
        if (b == COMMAND_BLOCK) return Mode.REDSTONE;
        if (b == REPEATING) return Mode.AUTO;
        return b == CHAIN ? Mode.SEQUENCE : Mode.REDSTONE;
    }

    /** isConditional: 칸의 지금 상태가 명령 블록이고 conditional 이 켜져 있다. */
    public boolean isConditional(BlockLevel level) {
        int s = level.getBlockState(pos);
        return isCommandBlock(s) && CONDITIONAL.is(s);
    }

    // ------------------------------------------------------------------ 저장

    @Override
    protected void saveAdditional(NbtCompound tag) {
        logic.save(tag);
        tag.putBoolean("powered", powered);
        tag.putBoolean("conditionMet", conditionMet);
        tag.putBoolean("auto", auto);
    }

    @Override
    public void load(NbtCompound tag) {
        logic.load(tag);
        powered = tag.getBoolean("powered");
        conditionMet = tag.getBoolean("conditionMet");
        auto = tag.getBoolean("auto");   // 로드 때는 레벨이 없어 예약하지 않는다
    }

    /** 사용자 지정 이름 컴포넌트. */
    @Override
    protected void applyImplicitComponents(ComponentInput in) {
        Text n = null;
        if (in.get("minecraft:custom_name") instanceof String s) {
            try {
                n = FlatText.parse(s);
            } catch (RuntimeException e) {
                n = null;
            }
        }
        logic.setCustomName(n);
    }

    @Override
    protected void collectImplicitComponents(Map<String, Object> out) {
        out.put("minecraft:custom_name", logic.customName() == null ? null : FlatText.stable(logic.customName()));
    }

    /**
     * 서버 수신 set_command_block 의 적용(바닐라 handleSetCommandBlock 본문): 모드에 맞는 블록(방향 유지, 조건부 설정)으로 바꾸고
     * 블록 엔티티는 옛 것을 되살린 뒤 명령·출력 기록·항상 활성을 반영한다. 블록 엔티티가 없으면 false.
     */
    public static boolean applyEdit(BlockLevel level, Pos pos, String command, Mode mode, boolean trackOutput, boolean conditional, boolean automatic) {
        if (!(level.getBlockEntity(pos) instanceof CommandBlockEntity be)) return false;
        Mode oldMode = be.mode();
        int state = level.getBlockState(pos);
        int base = switch (mode) {
            case SEQUENCE -> B.defaultState(CHAIN);
            case AUTO -> B.defaultState(REPEATING);
            default -> B.defaultState(COMMAND_BLOCK);
        };
        int newState = CONDITIONAL.with(FACING.with(base, FACING.valueName(state)), conditional);
        if (newState != state) {
            level.setBlock(pos, newState, 2);
            be.setBlockState(newState);
            level.setBlockEntity(be);   // 블록이 바뀌며 새로 생긴 블록 엔티티 대신 옛 것을 되돌린다
        }
        be.logic.setCommand(command);
        be.setChanged(level);
        be.logic.setTrackOutput(trackOutput);
        if (!trackOutput) be.logic.setLastOutput(null);
        be.setAutomatic(automatic, level);
        if (oldMode != mode) be.onModeSwitch(level);
        level.sendBlockUpdated(pos);
        return true;
    }

    /** openCommandBlock 이 보내는 태그와 같다(saveCustomOnly). */
    public NbtCompound screenTag() { return saveCustomOnly(); }
}
