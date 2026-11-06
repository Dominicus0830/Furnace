// 바닐라 CommandBlock: 명령 블록 3종(command_block 임펄스, repeating_command_block 반복, chain_command_block 체인). 바라보는 방향의 반대로 놓인다.
// 신호 → 1틱 뒤 예약 틱 → 명령 실행 → 뒤따르는 체인 블록들을 maxCommandChainLength 까지 차례로 실행 → 비교기 갱신.
// Furnace 구조: 명령은 전역 스레드에서 돌지만 performCommand 가 그 틱 안에서 결과를 기다려(Region.awaitUrgent) 바닐라처럼 체인이 한 틱에 끝난다.
// 틱 예산(기본 20ms)을 넘기면 그 칸부터는 명령이 끝나 소유 region 으로 돌아온 뒤 이어서 실행한다(예전 방식).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.block.entity.BaseCommandBlock;
import org.domi.furnace.server.block.entity.BlockEntity;
import org.domi.furnace.server.block.entity.CommandBlockEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommandBlock extends BaseEntityBlock {

    private static final Logger log = LoggerFactory.getLogger(CommandBlock.class);
    private static final Prop FACING = B.prop("facing");
    private static final int CHAIN = B.id("chain_command_block");

    private final boolean automatic;

    public CommandBlock(int block) {
        super(block);
        this.automatic = Blocks.get().params(block).get("automatic").getAsBoolean();
    }

    /** newBlockEntity: 이 블록의 automatic(체인 = 켜짐)을 가진 명령 블록 엔티티. 레벨이 아직 없어 예약은 하지 않는다. */
    @Override
    public BlockEntity newBlockEntity(Pos pos, int state) {
        CommandBlockEntity be = new CommandBlockEntity(pos, state);
        be.setAutomaticNoSchedule(automatic);
        return be;
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        return FACING.with(defaultState(), Dir.NAME[Dir.opposite(ctx.nearestLookingDirection())]);
    }

    /** neighborChanged: 신호가 새로 켜지고(자동이 아니고 체인이 아니면) 조건 판정을 해 두고 1틱 뒤 실행 예약. */
    @Override
    public void neighborChanged(int state, BlockLevel level, Pos pos, int sourceBlock, Pos sourcePos, boolean moved) {
        if (level.getBlockEntity(pos) instanceof CommandBlockEntity be) {
            boolean powered = level.hasNeighborSignal(pos);
            boolean was = be.isPowered();
            be.setPowered(powered);
            if (!was && !be.isAutomatic() && be.mode() != CommandBlockEntity.Mode.SEQUENCE) {
                if (powered) {
                    be.markConditionMet(level);
                    level.scheduleTick(pos, block, 1);
                }
            }
        }
    }

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof CommandBlockEntity be)) return;
        BaseCommandBlock logic = be.logic();
        boolean hasCommand = !logic.command().isEmpty();
        CommandBlockEntity.Mode mode = be.mode();
        boolean wasConditionMet = be.wasConditionMet();
        if (mode == CommandBlockEntity.Mode.AUTO) {
            be.markConditionMet(level);
            if (wasConditionMet) execute(state, level, pos, logic, hasCommand);
            else if (be.isConditional(level)) logic.setSuccessCount(0);
            if (be.isPowered() || be.isAutomatic()) level.scheduleTick(pos, block, 1);
        } else if (mode == CommandBlockEntity.Mode.REDSTONE) {
            if (wasConditionMet) execute(state, level, pos, logic, hasCommand);
            else if (be.isConditional(level)) logic.setSuccessCount(0);
        }
        level.updateNeighbourForOutputSignal(pos, block);
    }

    /** execute: 명령을 돌릴 수 있으면 돌리고(아니면 성공 0), 이어서 체인. 명령이 비동기로 도는 중이면 끝난 뒤 비교기를 갱신하고 체인을 잇는다. */
    private void execute(int state, BlockLevel level, Pos pos, BaseCommandBlock logic, boolean canTrigger) {
        int facing = facingOf(state);
        if (canTrigger) {
            var r = logic.performCommand(level, lv -> {
                lv.updateNeighbourForOutputSignal(pos, block);
                executeChain(lv, pos, facing);
            });
            if (r == BaseCommandBlock.Performed.PENDING || r == BaseCommandBlock.Performed.BUSY) return;   // BUSY: 앞 실행이 끝나기 전엔 겹쳐 돌리지 않는다
        } else {
            logic.setSuccessCount(0);
        }
        executeChain(level, pos, facing);
    }

    private static int facingOf(int state) { return Dir.byName(FACING.valueName(state)); }

    /** executeChain(level, pos, facing): 체인 블록들을 바라보는 방향으로 따라가며 실행. */
    public static void executeChain(BlockLevel level, Pos start, int facing) {
        runChain(level, start, facing, level.gameRuleInt("maxCommandChainLength"), level.gameRuleInt("maxCommandChainLength"));
    }

    /** limit: 남은 걸음(바닐라 while (limit-- > 0)), max: 경고에 쓰는 처음 값. 비동기 명령 뒤에는 남은 걸음을 그대로 이어받는다. */
    private static void runChain(BlockLevel level, Pos start, int facing, int limit, int max) {
        Pos pos = start;
        while (limit-- > 0) {
            pos = pos.relative(facing);
            int s = level.getBlockState(pos);
            if (!B.is(s, CHAIN) || !(level.getBlockEntity(pos) instanceof CommandBlockEntity be) || be.mode() != CommandBlockEntity.Mode.SEQUENCE) break;
            if (be.isPowered() || be.isAutomatic()) {
                BaseCommandBlock logic = be.logic();
                if (be.markConditionMet(level)) {
                    final Pos p = pos;
                    final int st = s, lim = limit;
                    var r = logic.performCommand(level, lv -> {
                        lv.updateNeighbourForOutputSignal(p, B.block(st));
                        runChain(lv, p, facingOf(st), lim, max);
                    });
                    if (r == BaseCommandBlock.Performed.PENDING) return;
                    if (r == BaseCommandBlock.Performed.BUSY) return;
                    if (r == BaseCommandBlock.Performed.NOT_RUN) break;
                    level.updateNeighbourForOutputSignal(pos, B.block(s));
                } else if (be.isConditional(level)) {
                    logic.setSuccessCount(0);
                }
            }
            facing = facingOf(s);
        }
        if (limit <= 0) log.warn("Command Block chain tried to execute more than {} steps!", Math.max(max, 0));
    }

    @Override
    public void setPlacedBy(BlockLevel level, Pos pos, int state, BlockActor placer, org.domi.furnace.server.entity.ItemStack stack) {
        if (level.getBlockEntity(pos) instanceof CommandBlockEntity be) {
            if (org.domi.furnace.server.item.ItemTools.component(stack, "minecraft:block_entity_data") == null) {
                be.logic().setTrackOutput(level.gameRule("sendCommandFeedback"));
                be.setAutomatic(automatic, level);
            }
            be.setPowered(level.hasNeighborSignal(pos));
        }
    }

    /** useWithoutItem: 크리에이티브 + 권한 2 면 편집 화면(블록 엔티티 데이터 전송), 아니면 넘긴다. */
    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        if (level.getBlockEntity(pos) instanceof CommandBlockEntity be && player.canUseGameMasterBlocks()) {
            player.openCommandBlock(pos, be.typeId(), be.screenTag());
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public boolean hasAnalogOutputSignal(int state) { return true; }

    /** 마지막 실행의 성공 횟수(그대로 — 15 로 자르지 않는다). */
    @Override
    public int getAnalogOutputSignal(int state, BlockLevel level, Pos pos) {
        return level.getBlockEntity(pos) instanceof CommandBlockEntity be ? be.logic().successCount() : 0;
    }
}
